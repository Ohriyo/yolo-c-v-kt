package com.yoloc.app.screens.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.features.detection.DetectionErrorView
import com.yoloc.app.features.detection.describeDetectionError
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.features.scans.ScanResult
import com.yoloc.app.screens.container
import com.yoloc.app.store.ScanSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RN: app/scan/processing.tsx. Runs on-device detection on the previewed photo, then the screen
 * replaces itself with Result. Leaving while analyzing clears this ViewModel, which cancels the
 * run; a result that still arrives is ignored (and the session ignores a result for a photo it no
 * longer holds). Failures say which stage went wrong, with Try again / Go back.
 */
class ProcessingViewModel(
    private val session: ScanSession,
    private val analyze: suspend (PendingPhoto) -> ScanResult,
) : ViewModel() {
    sealed interface State {
        /** No photo in the session (the screen shows "photo missing" with Back to scan). */
        data object Missing : State

        data object Running : State

        data class Failed(val error: DetectionErrorView) : State

        /** The result is in the session: the screen replaces itself with Result. */
        data object Done : State
    }

    /** The photo being analyzed, shown dimmed while it runs. */
    val photo: PendingPhoto? = session.state.value.photo

    private val _state = MutableStateFlow<State>(State.Running)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    private fun start() {
        val photo = session.state.value.photo
        if (photo == null) {
            _state.value = State.Missing
            return
        }
        _state.value = State.Running
        job?.cancel()
        job = viewModelScope.launch {
            try {
                session.setResult(analyze(photo))
                // The session ignores a result for a photo it no longer holds; then analyze the
                // current one (or report it missing) instead of opening Result without a result.
                if (session.state.value.result?.photo != photo) {
                    start()
                    return@launch
                }
                _state.value = State.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Analysis failed", e)
                _state.value = State.Failed(describeDetectionError(e))
            }
        }
    }

    fun retry() = start()

    companion object {
        private const val TAG = "processing"

        val Factory = viewModelFactory {
            initializer {
                val analyze = container.analyzePhoto
                ProcessingViewModel(container.scanSession) { analyze(it) }
            }
        }
    }
}
