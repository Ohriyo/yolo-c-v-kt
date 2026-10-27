package com.yoloc.app.screens.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.export.ExportResult
import com.yoloc.app.features.export.ExportScope
import com.yoloc.app.features.export.ExportSummary
import com.yoloc.app.features.export.availableBytes
import com.yoloc.app.features.export.lacksSpaceFor
import com.yoloc.app.features.export.shareRetrainingZip
import com.yoloc.app.features.export.summarizeExport
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.screens.app
import com.yoloc.app.screens.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RN: features/export/RetrainingExportSheet.tsx. Choose what to export for retraining, see how
 * many photos and how big, then create and share the zip. Corrected scans come first: they are the
 * only labels a person has checked.
 */
class RetrainingExportViewModel(
    /** The Application (sharing and free space need a Context); it outlives the ViewModel, so nothing leaks. */
    private val context: Application,
    private val scans: ScanRepository,
    private val classes: ClassRepository,
    private val files: ScanFileStore,
    private val config: ModelConfig,
) : ViewModel() {
    /** How an export ended, for Settings' toast; the sheet hands it on and calls [outcomeHandled]. */
    sealed interface Outcome {
        data class Done(val result: ExportResult) : Outcome

        data object Failed : Outcome
    }

    data class State(
        val scope: ExportScope = ExportScope.Corrected,
        /** Per scope; null while counting. */
        val summaries: Map<ExportScope, ExportSummary>? = null,
        /** Free space where the zip is written; null when Android cannot say. */
        val available: Long? = null,
        /** Counting failed: the sheet says so instead of counting forever (reopen to retry). */
        val sizingFailed: Boolean = false,
        /** The sheet cannot be closed while this is true (RN: onClose ignored while working). */
        val working: Boolean = false,
        val outcome: Outcome? = null,
    ) {
        val chosen: ExportSummary? get() = summaries?.get(scope)

        /** Leaves a 200 MB margin; unknown free space never blocks. */
        val noSpace: Boolean get() = chosen?.let { lacksSpaceFor(it, available) } ?: false

        val canExport: Boolean get() = !working && (chosen?.scans ?: 0) > 0 && !noSpace
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var sizing: Job? = null

    /**
     * Each time the sheet opens (RN sizes the export whenever it becomes visible). Ignored while an
     * export runs, so its numbers and its outcome are not wiped.
     */
    fun onOpened() {
        if (_state.value.working) return
        _state.update { it.copy(summaries = null, sizingFailed = false, outcome = null) }
        sizing?.cancel()
        sizing = viewModelScope.launch {
            try {
                val available = withContext(Dispatchers.IO) { availableBytes(context) }
                val sums = coroutineScope {
                    ExportScope.entries.map { sc -> async { sc to summarizeExport(scans, classes, files, sc) } }.awaitAll()
                }
                _state.update { it.copy(summaries = sums.toMap(), available = available) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not size the export", e)
                _state.update { it.copy(sizingFailed = true) }
            }
        }
    }

    fun choose(scope: ExportScope) = _state.update { if (it.working) it else it.copy(scope = scope) }

    fun export() {
        val s = _state.value
        if (!s.canExport) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val outcome = try {
                Outcome.Done(shareRetrainingZip(context, scans, classes, files, config, s.scope))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Retraining export failed", e)
                Outcome.Failed
            }
            _state.update { it.copy(working = false, outcome = outcome) }
        }
    }

    fun outcomeHandled() = _state.update { it.copy(outcome = null) }

    companion object {
        private const val TAG = "retrainExport"

        val Factory = viewModelFactory {
            initializer {
                val c = container
                RetrainingExportViewModel(app, c.scanRepository, c.classRepository, c.files, c.modelConfig)
            }
        }
    }
}
