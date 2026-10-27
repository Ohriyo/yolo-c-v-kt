package com.yoloc.app.features.settings

import com.yoloc.app.features.scans.ScanLocation
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * RN: settings.logic.test.ts (formatBytes, csvFileName, captureScanLocation). clearAllData and
 * getStorageSummary need the database, so they are in androidTest (SettingsDataTest).
 */
class DataToolsTest {
    private val zone = ZoneId.of("America/Bogota")

    @Test
    fun formatsBytesInKbBelowOneMbAndOneDecimalAbove() {
        assertEquals("0 KB", formatBytes(0, Locale.US))
        assertEquals("850 KB", formatBytes(850L * 1024, Locale.US))
        assertEquals("48.2 MB", formatBytes((48.24 * 1024 * 1024).toLong(), Locale.US))
        assertEquals("1.3 GB", formatBytes((1.3 * 1024 * 1024 * 1024).toLong(), Locale.US))
        assertEquals("0 KB", formatBytes(-5, Locale.US))
    }

    @Test
    fun formatsBytesInTheDeviceLocale() {
        assertEquals("48,2 MB", formatBytes((48.24 * 1024 * 1024).toLong(), Locale.GERMANY))
    }

    @Test
    fun namesTheCsvByLocalDate() {
        val lateEvening = LocalDateTime.of(2026, 10, 6, 23, 59).atZone(zone).toInstant().toEpochMilli()
        assertEquals("yolo-c-scans-2026-10-06.csv", csvFileName(lateEvening, zone))
    }

    @Test
    fun locationAccessFollowsThePermissionState() {
        assertEquals(LocationAccess.Granted, locationAccessFrom(granted = true, everAsked = true, showRationale = false))
        assertEquals(LocationAccess.Ask, locationAccessFrom(granted = false, everAsked = false, showRationale = false))
        assertEquals(LocationAccess.Ask, locationAccessFrom(granted = false, everAsked = true, showRationale = true))
        assertEquals(LocationAccess.Blocked, locationAccessFrom(granted = false, everAsked = true, showRationale = false))
    }

    /** RN mocks expo-location; this fake records what was asked. */
    private class FakeLocation(
        val permission: Boolean = true,
        val last: LocationFix? = null,
        val fresh: (suspend () -> LocationFix?) = { null },
    ) : LocationSource {
        var currentCalled = false

        override fun hasPermission() = permission

        override suspend fun lastKnown(maxAgeMs: Long): LocationFix? {
            assertEquals(LAST_KNOWN_MAX_AGE_MS, maxAgeMs)
            return last
        }

        override suspend fun current(): LocationFix? {
            currentCalled = true
            return fresh()
        }
    }

    @Test
    fun returnsNothingWithoutPermission() = runBlocking {
        val source = FakeLocation(permission = false, last = LocationFix(1.0, 2.0, 5f))
        assertNull(captureScanLocation(source))
        assertFalse(source.currentCalled)
    }

    @Test
    fun prefersARecentLastKnownFix() = runBlocking {
        val source = FakeLocation(last = LocationFix(10.5, -75.2, 20f))
        assertEquals(ScanLocation(10.5, -75.2), captureScanLocation(source))
        assertFalse(source.currentCalled)
    }

    @Test
    fun asksForAFreshFixWhenTheLastOneIsTooRough() = runBlocking {
        val source = FakeLocation(last = LocationFix(10.5, -75.2, 500f), fresh = { LocationFix(7.1, -73.1, 30f) })
        assertEquals(ScanLocation(7.1, -73.1), captureScanLocation(source))
    }

    @Test
    fun givesUpOnASlowFreshFixInsteadOfHoldingTheSave() = runBlocking {
        // The real timeout is 8 s; a short one keeps the test fast.
        val source = FakeLocation(fresh = { awaitCancellation() })
        assertNull(captureScanLocation(source, freshTimeoutMs = 50))
    }
}
