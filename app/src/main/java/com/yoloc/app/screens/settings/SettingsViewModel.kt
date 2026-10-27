package com.yoloc.app.screens.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.features.export.ExportResult
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.features.settings.LocationAccess
import com.yoloc.app.features.settings.StorageSummary
import com.yoloc.app.features.settings.clearAllData
import com.yoloc.app.features.settings.getStorageSummary
import com.yoloc.app.features.settings.locationAccessFrom
import com.yoloc.app.features.settings.shareScansCsv
import com.yoloc.app.features.settings.snapThreshold
import com.yoloc.app.features.settings.thresholdPercent
import com.yoloc.app.screens.app
import com.yoloc.app.screens.container
import com.yoloc.app.store.PermissionHistory
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.store.countsAsAsked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RN: app/(tabs)/settings.tsx (with LocationSetting and DataSettings). Detection threshold,
 * theme, opt-in location, and the user's data. Every change is saved at once; nothing needs a
 * Save button.
 */
class SettingsViewModel(
    /** The Application (sharing and free space need a Context); it outlives the ViewModel, so nothing leaks. */
    private val context: Application,
    private val settings: SettingsStore,
    private val scans: ScanRepository,
    private val plots: PlotRepository,
    private val files: ScanFileStore,
    private val library: ScanLibrary,
    private val permissions: PermissionHistory,
    /** model.config.json's confThreshold: the slider's marker and Reset target. */
    private val defaultThreshold: Double,
) : ViewModel() {
    /** What the location row says. */
    enum class LocationMessage { On, Off, Ask, Blocked, Revoked }

    sealed interface Toast {
        val success: Boolean get() = false

        data object ExportEmpty : Toast

        data object ExportFailed : Toast

        data object ClearNothing : Toast

        data object Cleared : Toast {
            override val success get() = true
        }

        data object ClearFailed : Toast

        /** "N scans exported", plus "M skipped" when photos were missing. */
        data class RetrainDone(val scans: Int, val skipped: Int) : Toast {
            override val success get() = true
        }

        data object RetrainFailed : Toast
    }

    data class Local(
        /** The slider while dragging; null shows the saved value. */
        val thresholdDraft: Double? = null,
        /** Null until the first permission check. */
        val locationAccess: LocationAccess? = null,
        /** The last request was refused; the row explains and offers Try again / Open settings. */
        val locationRefused: Boolean = false,
        val summary: StorageSummary? = null,
        val summaryFailed: Boolean = false,
        val exporting: Boolean = false,
        val clearing: Boolean = false,
        /** The Clear all data dialog, with the counts it names. */
        val confirmingClear: Boolean = false,
        val retrainOpen: Boolean = false,
        val toast: Toast? = null,
    )

    data class State(
        val local: Local,
        val confThreshold: Double,
        val defaultThreshold: Double,
        val theme: ThemeSetting,
        val gpsOptIn: Boolean,
    ) {
        val shownThreshold: Double get() = local.thresholdDraft ?: confThreshold
        val isDefaultThreshold: Boolean get() = thresholdPercent(confThreshold) == thresholdPercent(defaultThreshold)

        /** On, but Android permission is gone (turned off in system settings). */
        val locationLost: Boolean get() = gpsOptIn && local.locationAccess != null && local.locationAccess != LocationAccess.Granted

        /** The row shows Try again or Open settings. */
        val locationProblem: Boolean get() = locationLost || (!gpsOptIn && local.locationRefused)

        val locationMessage: LocationMessage
            get() = when {
                locationLost -> LocationMessage.Revoked
                locationProblem -> if (local.locationAccess == LocationAccess.Blocked) LocationMessage.Blocked else LocationMessage.Ask
                gpsOptIn -> LocationMessage.On
                else -> LocationMessage.Off
            }
    }

    private val local = MutableStateFlow(Local())
    private val resumes = MutableStateFlow(0)
    private var toastJob: Job? = null

    val state: StateFlow<State> = combine(local, settings.state) { l, s ->
        State(l, s.confThreshold, defaultThreshold, s.theme, s.gpsOptIn)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.state.value.let { State(Local(), it.confThreshold, defaultThreshold, it.theme, it.gpsOptIn) })

    init {
        // Storage numbers reload when saved scans change and each time the screen is shown again
        // (plots can change on the Plots screen).
        viewModelScope.launch {
            combine(library.revision, resumes) { _, _ -> }.collectLatest { loadSummary() }
        }
    }

    private suspend fun loadSummary() {
        try {
            val summary = getStorageSummary(scans, plots, files)
            local.update { it.copy(summary = summary, summaryFailed = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read storage", e)
            local.update { it.copy(summaryFailed = true) }
        }
    }

    /** ON_RESUME: recount storage and plots. The screen also re-checks location permission then. */
    fun onResume() = resumes.update { it + 1 }

    // Detection

    fun onThresholdChange(value: Double) = local.update { it.copy(thresholdDraft = value) }

    fun onThresholdChangeEnd(value: Double) {
        local.update { it.copy(thresholdDraft = null) }
        viewModelScope.launch { settings.setConfThreshold(snapThreshold(value)) }
    }

    fun resetThreshold() {
        viewModelScope.launch { settings.setConfThreshold(defaultThreshold) }
    }

    // Appearance

    fun setTheme(theme: ThemeSetting) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    // Location

    /** On start and ON_RESUME, so permission changed in system settings shows at once. */
    fun onLocationChecked(granted: Boolean, showRationale: Boolean) =
        local.update { it.copy(locationAccess = locationAccessFrom(granted, permissions.locationAsked, showRationale)) }

    /**
     * The switch row. Turning on with permission already granted saves at once and returns false;
     * otherwise it returns true and the screen shows the system prompt ([onLocationResult]).
     */
    fun toggleGps(on: Boolean): Boolean {
        if (!on) {
            local.update { it.copy(locationRefused = false) }
            viewModelScope.launch { settings.setGpsOptIn(false) }
            return false
        }
        if (local.value.locationAccess == LocationAccess.Granted) {
            local.update { it.copy(locationRefused = false) }
            viewModelScope.launch { settings.setGpsOptIn(true) }
            return false
        }
        return true
    }

    /** The prompt's answer (coarse + fine together); granted if either was allowed. */
    fun onLocationResult(granted: Boolean, showRationale: Boolean) {
        val refusedBefore = local.value.locationRefused || local.value.locationAccess == LocationAccess.Blocked
        if (countsAsAsked(granted, showRationale, refusedBefore)) permissions.locationAsked = true
        onLocationChecked(granted, showRationale)
        local.update { it.copy(locationRefused = !granted) }
        if (granted) viewModelScope.launch { settings.setGpsOptIn(true) }
    }

    // Data

    fun exportCsv() {
        if (local.value.exporting) return
        local.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                if (!shareScansCsv(context, scans)) showToast(Toast.ExportEmpty)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                showToast(Toast.ExportFailed)
            } finally {
                local.update { it.copy(exporting = false) }
            }
        }
    }

    /** Clear all data asks first, naming what it removes; with nothing to remove it only says so. */
    fun askClear() {
        val summary = local.value.summary
        if (summary == null || summary.scans + summary.plots == 0) {
            showToast(Toast.ClearNothing)
            return
        }
        local.update { it.copy(confirmingClear = true) }
    }

    fun cancelClear() = local.update { it.copy(confirmingClear = false) }

    fun confirmClear() {
        if (local.value.clearing) return
        local.update { it.copy(confirmingClear = false, clearing = true) }
        viewModelScope.launch {
            try {
                clearAllData(scans, files)
                // A pending Undo would point at a scan that no longer exists.
                library.clearUndo()
                library.bump()
                showToast(Toast.Cleared)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Clear all data failed", e)
                showToast(Toast.ClearFailed)
            } finally {
                local.update { it.copy(clearing = false) }
            }
        }
    }

    fun openRetrain() = local.update { it.copy(retrainOpen = true) }

    fun closeRetrain() = local.update { it.copy(retrainOpen = false) }

    fun onRetrainDone(result: ExportResult) {
        local.update { it.copy(retrainOpen = false) }
        showToast(Toast.RetrainDone(result.scans, result.skipped))
    }

    fun onRetrainError() = showToast(Toast.RetrainFailed)

    // App

    fun showOnboardingAgain() {
        viewModelScope.launch { settings.resetOnboarding() }
    }

    private fun showToast(toast: Toast) {
        local.update { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MS)
            local.update { it.copy(toast = null) }
        }
    }

    companion object {
        private const val TAG = "settings"
        const val TOAST_MS = 3500L

        val Factory = viewModelFactory {
            initializer {
                val c = container
                SettingsViewModel(
                    app, c.settingsStore, c.scanRepository, c.plotRepository, c.files, c.scanLibrary,
                    c.permissionHistory, c.modelConfig.confThreshold,
                )
            }
        }
    }
}
