package com.yoloc.app.features.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LastLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.yoloc.app.features.scans.ScanLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/*
 * RN: settings/location.ts. Location for scans is opt-in (CLAUDE.md 2.2): off by default, asked for
 * only when the user turns it on in Settings, and only "while using the app". Coordinates are
 * stored on the phone with the scan and nowhere else. GPS works without internet; the fix may just
 * take longer.
 */

private const val TAG = "location"

/** Granted: on. Ask: the system prompt will still appear. Blocked: only system settings can fix it. */
enum class LocationAccess { Granted, Ask, Blocked }

/** The two permissions Settings requests together (RN: requestForegroundPermissionsAsync). */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

/** Expo counts "approximate only" as granted, so either permission is enough. */
fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Android has no Expo-style `canAskAgain`; it is derived as for the camera (cameraAccessFrom): never
 * asked, or refused with a rationale still shown, means the prompt will appear again.
 */
fun locationAccessFrom(granted: Boolean, everAsked: Boolean, showRationale: Boolean): LocationAccess = when {
    granted -> LocationAccess.Granted
    !everAsked || showRationale -> LocationAccess.Ask
    else -> LocationAccess.Blocked
}

/** A fix as the platform reports it; accuracy in meters (0 when unknown, as on Android). */
data class LocationFix(val latitude: Double, val longitude: Double, val accuracyM: Float)

/** Where fixes come from: Play services on the phone, a fake in tests (RN mocks expo-location). */
interface LocationSource {
    fun hasPermission(): Boolean

    /** The last known fix no older than [maxAgeMs], or null. */
    suspend fun lastKnown(maxAgeMs: Long): LocationFix?

    /** A fresh fix, or null; the request stops when the coroutine is cancelled. */
    suspend fun current(): LocationFix?
}

/** A recent fix this close is good enough for "which part of the farm". */
const val LAST_KNOWN_MAX_AGE_MS = 5 * 60 * 1000L
const val LAST_KNOWN_ACCURACY_M = 100f

/** Never hold up a save for long; a scan without a location is still a good scan. */
const val FRESH_FIX_TIMEOUT_MS = 8000L

/**
 * Best-effort position for a scan: a recent last-known fix, else a fresh one within the timeout.
 * Returns null instead of throwing (permission revoked, location off, no fix in time).
 */
suspend fun captureScanLocation(source: LocationSource, freshTimeoutMs: Long = FRESH_FIX_TIMEOUT_MS): ScanLocation? =
    try {
        if (!source.hasPermission()) {
            null
        } else {
            val fix = source.lastKnown(LAST_KNOWN_MAX_AGE_MS)?.takeIf { it.accuracyM <= LAST_KNOWN_ACCURACY_M }
                ?: withTimeoutOrNull(freshTimeoutMs) { source.current() }
            fix?.let { ScanLocation(it.latitude, it.longitude) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No location for this scan", e)
        null
    }

/** RN: expo-location, which also uses the fused provider on Android. */
class FusedLocationSource(private val context: Context) : LocationSource {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasPermission() = hasLocationPermission(context)

    @SuppressLint("MissingPermission") // captureScanLocation checks first; a revoke in between throws, and is caught
    override suspend fun lastKnown(maxAgeMs: Long): LocationFix? =
        client.getLastLocation(LastLocationRequest.Builder().setMaxUpdateAgeMillis(maxAgeMs).build())
            .await()
            ?.let { LocationFix(it.latitude, it.longitude, it.accuracy) }

    @SuppressLint("MissingPermission")
    override suspend fun current(): LocationFix? {
        val cancel = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY).build()
        return client.getCurrentLocation(request, cancel.token)
            .await(cancel) // cancelling the coroutine (the timeout) cancels the request too
            ?.let { LocationFix(it.latitude, it.longitude, it.accuracy) }
    }
}
