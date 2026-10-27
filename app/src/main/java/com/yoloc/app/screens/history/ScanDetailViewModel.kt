package com.yoloc.app.screens.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanWithDetections
import com.yoloc.app.features.scans.DayFormat
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.screens.app
import com.yoloc.app.screens.container
import com.yoloc.app.screens.refreshedNow
import com.yoloc.app.store.ScanLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * RN: app/history/[id].tsx. The same report as Result for a saved scan, plus notes, the scan's
 * details, and delete with Undo. Label corrections are written straight to the database (kept for
 * retraining) and the verdict follows them. `saved` is true when arriving straight from saving.
 */
class ScanDetailViewModel(
    private val scanId: String,
    saved: Boolean,
    private val scans: ScanRepository,
    private val classRepository: ClassRepository,
    private val library: ScanLibrary,
    private val files: ScanFileStore,
    val dayFormat: DayFormat,
) : ViewModel() {
    sealed interface Load {
        data object Loading : Load

        data object Error : Load

        data object Missing : Load

        data class Ready(val scan: ScanWithDetections) : Load
    }

    enum class Toast(val success: Boolean) {
        Saved(true),
        NotesSaved(true),
        CorrectionFailed(false),
        DeleteFailed(false),
    }

    /** Notes with an explicit Save, shown only once something changed. */
    data class Notes(
        val text: String = "",
        val savedText: String = "",
        val saving: Boolean = false,
        val failed: Boolean = false,
    ) {
        val dirty: Boolean get() = text.trim() != savedText.trim()
    }

    data class State(
        val load: Load = Load.Loading,
        /** From the classes table, keyed by id; null until loaded. */
        val classes: Map<Int, DetectionClass>? = null,
        val notes: Notes = Notes(),
        val toast: Toast? = null,
        val confirmingDelete: Boolean = false,
        val deleting: Boolean = false,
        /** Set once the scan is deleted: the screen goes back (Undo shows where it lands). */
        val closed: Boolean = false,
        val now: Long = System.currentTimeMillis(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var toastJob: Job? = null

    init {
        if (saved) showToast(Toast.Saved)
        // Reloads after any change to saved scans (corrections and notes here bump the revision).
        viewModelScope.launch { library.revision.collectLatest { load() } }
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

    private suspend fun load() {
        try {
            val scan = scans.getScan(scanId)
            _state.update { s ->
                val firstLoad = (s.load as? Load.Ready)?.scan?.scan?.id != scan?.scan?.id
                s.copy(
                    load = if (scan != null) Load.Ready(scan) else Load.Missing,
                    // The editor starts from the stored notes once; later reloads keep the draft.
                    notes = if (scan != null && firstLoad) Notes(scan.scan.notes.orEmpty(), scan.scan.notes.orEmpty()) else s.notes,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not load scan", e)
            // A failed refresh keeps the scan on screen.
            _state.update { s -> if (s.load is Load.Ready) s else s.copy(load = Load.Error) }
        }
    }

    /** The error state's Retry (RN: bump, which also refreshes the other screens). */
    fun retry() = library.bump()

    fun imageFile(scan: ScanWithDetections): File = files.fileFor(scan.scan.imagePath)

    /** "Correct label" on the pod at `index`; choosing the model's own class clears the correction. */
    fun correct(index: Int, classId: Int) {
        val scan = (_state.value.load as? Load.Ready)?.scan ?: return
        val detection = scan.detections.getOrNull(index) ?: return
        viewModelScope.launch {
            try {
                scans.correctDetection(detection.id, classId)
                library.bump()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Correction failed", e)
                showToast(Toast.CorrectionFailed)
            }
        }
    }

    fun setNotes(text: String) = _state.update { it.copy(notes = it.notes.copy(text = text.take(NOTES_MAX))) }

    fun saveNotes() {
        val notes = _state.value.notes
        if (notes.saving) return
        _state.update { it.copy(notes = it.notes.copy(saving = true, failed = false)) }
        viewModelScope.launch {
            try {
                scans.updateScanNotes(scanId, notes.text)
                _state.update { it.copy(notes = it.notes.copy(savedText = notes.text, saving = false)) }
                library.bump()
                showToast(Toast.NotesSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Notes save failed", e)
                _state.update { it.copy(notes = it.notes.copy(saving = false, failed = true)) }
            }
        }
    }

    /** Delete asks first (RN: Alert.alert). */
    fun askDelete() = _state.update { it.copy(confirmingDelete = true) }

    fun cancelDelete() = _state.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        if (_state.value.deleting) return
        _state.update { it.copy(confirmingDelete = false, deleting = true) }
        viewModelScope.launch {
            try {
                library.deleteScan(scanId)
                _state.update { it.copy(closed = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Delete failed", e)
                _state.update { it.copy(deleting = false) }
                showToast(Toast.DeleteFailed)
            }
        }
    }

    /** ON_RESUME (RN: useNow), for "Saved on Today, 14:32". */
    fun refreshNow() = _state.update { it.copy(now = refreshedNow(it.now)) }

    private fun showToast(toast: Toast) {
        _state.update { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MS)
            _state.update { it.copy(toast = null) }
        }
    }

    companion object {
        private const val TAG = "scanDetail"
        const val NOTES_MAX = 500
        const val TOAST_MS = 3000L

        val Factory = viewModelFactory {
            initializer {
                // Route arguments by name, as the type-safe ScanDetail(id, saved) route (Step 5.3) stores them.
                val args = createSavedStateHandle()
                val c = container
                ScanDetailViewModel(
                    args.get<String>("id").orEmpty(), args.get<Boolean>("saved") ?: false,
                    c.scanRepository, c.classRepository, c.scanLibrary, c.files, DayFormat.forDevice(app),
                )
            }
        }
    }
}
