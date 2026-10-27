package com.yoloc.app.screens.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.db.repositories.Plot
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.scans.SaveResultOptions
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.features.scans.ScanLocation
import com.yoloc.app.features.scans.ScanResult
import com.yoloc.app.features.scans.correctResultDetection
import com.yoloc.app.features.scans.saveResult
import com.yoloc.app.features.settings.LocationSource
import com.yoloc.app.features.settings.captureScanLocation
import com.yoloc.app.screens.container
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.ScanSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * RN: app/scan/result.tsx and SaveScanSheet's data. The unsaved scan: tap a pod to highlight it
 * (screen state), correct its label, then Save (plot sheet, atomic write) or Discard. Leaving with
 * unsaved pods asks Stay / Discard / Save. With location opted in, the GPS fix starts as Result
 * opens, so Save rarely waits for it.
 */
class ResultViewModel(
    private val session: ScanSession,
    private val scans: ScanRepository,
    private val plots: PlotRepository,
    private val classRepository: ClassRepository,
    private val files: ScanFileStore,
    private val library: ScanLibrary,
    private val config: ModelConfig,
    gpsOptIn: Boolean,
    locationSource: LocationSource,
) : ViewModel() {
    sealed interface Exit {
        /** Back to the camera. */
        data object Discard : Exit

        /** To History, then Scan Detail with the "Saved" toast. */
        data class Saved(val scanId: String) : Exit
    }

    data class State(
        /**
         * The scan, kept here once leaving starts: the session is cleared after navigating, and
         * the screen must not flash "result missing" on the way out.
         */
        val result: ScanResult? = null,
        val classes: Map<Int, DetectionClass>? = null,
        val saveOpen: Boolean = false,
        val saving: Boolean = false,
        val saveError: Boolean = false,
        /** The Stay / Discard / Save dialog. */
        val confirmLeave: Boolean = false,
        /** Plots for the save sheet, alphabetical. */
        val plots: List<Plot> = emptyList(),
        val plotError: Boolean = false,
        /** Set once: the screen navigates, then calls [exited]. */
        val exit: Exit? = null,
    ) {
        val hasPods: Boolean get() = (result?.detections?.size ?: 0) > 0

        /** Leaving with unsaved pods asks first; no pods, no prompt. */
        val guarded: Boolean get() = result != null && hasPods && exit == null
    }

    private val _state = MutableStateFlow(State(result = session.state.value.result))
    val state: StateFlow<State> = _state.asStateFlow()

    private val location: Deferred<ScanLocation?> =
        if (gpsOptIn) viewModelScope.async { captureScanLocation(locationSource) } else CompletableDeferred(null)

    init {
        viewModelScope.launch {
            try {
                val rows = classRepository.listClasses()
                _state.update { s -> s.copy(classes = rows.associateBy { it.id }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load classes", e)
            }
        }
    }

    /** "Correct label"; picking the model's own class clears the correction, and the verdict follows. */
    fun correct(index: Int, classId: Int) {
        val s = _state.value
        val result = s.result ?: return
        val classes = s.classes ?: return
        val corrected = correctResultDetection(result, index, classId, classes.mapValues { it.value.severity })
        session.setResult(corrected)
        _state.update { it.copy(result = corrected) }
    }

    /** Opens the save sheet and (re)loads its plots, as RN does each time the sheet opens. */
    fun openSave() {
        _state.update { it.copy(saveOpen = true, confirmLeave = false, saveError = false, plotError = false) }
        viewModelScope.launch {
            try {
                val rows = plots.listPlots()
                _state.update { it.copy(plots = rows) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load plots", e)
            }
        }
    }

    /** The sheet closes only when not saving. */
    fun closeSave() = _state.update { if (it.saving) it else it.copy(saveOpen = false) }

    /** "Add a plot" in the sheet. Returns the new plot's id for the sheet to select, or null on failure. */
    suspend fun addPlot(name: String): String? {
        _state.update { it.copy(plotError = false) }
        return try {
            val plot = plots.createPlot(name)
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY } // RN: sensitivity 'base'
            _state.update { s -> s.copy(plots = (s.plots + plot).sortedWith { a, b -> collator.compare(a.name, b.name) }) }
            plot.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not add plot", e)
            _state.update { it.copy(plotError = true) }
            null
        }
    }

    fun save(plotId: String?, notes: String) {
        val result = _state.value.result ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, saveError = false) }
        viewModelScope.launch {
            try {
                val id = saveResult(
                    scans, files, result, config.lowConfidenceThreshold,
                    SaveResultOptions(plotId = plotId, notes = notes, location = location.await()),
                )
                library.bump()
                _state.update { it.copy(saving = false, saveOpen = false, exit = Exit.Saved(id)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Save failed", e)
                _state.update { it.copy(saving = false, saveError = true) }
            }
        }
    }

    /** Discard, the no-pods Retake, and the leave dialog's Discard. */
    fun discard() = _state.update { it.copy(confirmLeave = false, exit = it.exit ?: Exit.Discard) }

    /** Back (system or top bar) while guarded. */
    fun onLeaveRequested() = _state.update { if (it.guarded) it.copy(confirmLeave = true) else it }

    fun stay() = _state.update { it.copy(confirmLeave = false) }

    /** After navigating away: the photo was copied on save, so the temp file can go either way. */
    fun exited() = session.clear()

    companion object {
        private const val TAG = "result"

        val Factory = viewModelFactory {
            initializer {
                val c = container
                ResultViewModel(
                    c.scanSession, c.scanRepository, c.plotRepository, c.classRepository, c.files, c.scanLibrary,
                    c.modelConfig, c.settingsStore.state.value.gpsOptIn, c.locationSource,
                )
            }
        }
    }
}
