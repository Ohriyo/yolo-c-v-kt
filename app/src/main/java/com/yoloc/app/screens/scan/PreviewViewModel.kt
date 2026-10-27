package com.yoloc.app.screens.scan

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.scans.CapturedPhotos
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.screens.container
import com.yoloc.app.store.ScanSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * RN: app/scan/preview.tsx. The unsaved photo. Retake goes back to capture: the camera for a
 * camera photo, the picker again for a gallery photo (a cancelled pick keeps the current one).
 */
class PreviewViewModel(private val session: ScanSession, private val photos: CapturedPhotos) : ViewModel() {
    enum class Retake {
        /** The session is cleared; the screen returns to the camera. */
        BackToCamera,

        /** The screen opens the photo picker and reports with [onGalleryResult]. */
        PickAnother,
    }

    data class State(
        /** Null when the session has none (the screen shows "photo missing" with Back to scan). */
        val photo: PendingPhoto? = null,
        /** The image could not be shown; Use photo is disabled. */
        val loadFailed: Boolean = false,
    )

    private val loadFailed = MutableStateFlow(false)

    val state: StateFlow<State> = combine(session.state, loadFailed) { s, failed -> State(s.photo, failed) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, State(session.state.value.photo))

    fun onImageError() {
        loadFailed.value = true
    }

    fun retake(): Retake? {
        val photo = session.state.value.photo ?: return null
        return if (photo.source == ScanSource.Camera) {
            session.clear()
            Retake.BackToCamera
        } else {
            Retake.PickAnother
        }
    }

    /** Null is a cancelled pick: keep the current photo. */
    fun onGalleryResult(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val next = photos.fromGallery(uri)
                loadFailed.value = false
                session.setPhoto(next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // RN logs and stays on the current photo, with no message.
                Log.w(TAG, "Gallery failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "preview"

        val Factory = viewModelFactory {
            initializer { PreviewViewModel(container.scanSession, container.capturedPhotos) }
        }
    }
}
