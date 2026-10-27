package com.yoloc.app.screens.scan

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.ScanFilters
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.features.scans.CameraAccess
import com.yoloc.app.features.scans.CapturedPhotos
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.features.scans.cameraAccessFrom
import com.yoloc.app.screens.container
import com.yoloc.app.store.PermissionHistory
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.ScanSession
import com.yoloc.app.store.countsAsAsked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * RN: app/scan/camera.tsx, useCameraAccess and useLatestScanImage. The camera is asked for here;
 * the gallery works whatever the camera permission. Capture and gallery both put the photo in the
 * scan session and open Preview. CameraX itself lives in the screen (Step 5); it reports back here.
 */
class CameraViewModel(
    private val session: ScanSession,
    private val scans: ScanRepository,
    private val files: ScanFileStore,
    library: ScanLibrary,
    private val permissions: PermissionHistory,
    private val photos: CapturedPhotos,
) : ViewModel() {
    enum class Notice { CaptureFailed, GalleryFailed }

    data class State(
        /** Null until the first permission check. */
        val access: CameraAccess? = null,
        val flashOn: Boolean = false,
        val capturing: Boolean = false,
        val picking: Boolean = false,
        val notice: Notice? = null,
        /** The newest saved scan's thumbnail (or photo) for the gallery button; null when none. */
        val latestImage: File? = null,
        /** The camera failed to start; Retry mounts it again. */
        val mountError: Boolean = false,
        /** The preview is running and can take a photo. */
        val ready: Boolean = false,
        /** Set when a photo is in the session: the screen opens Preview, then calls [previewOpened]. */
        val openPreview: Boolean = false,
    ) {
        val granted: Boolean get() = access == CameraAccess.Granted
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { library.revision.collectLatest { loadLatestImage() } }
    }

    private suspend fun loadLatestImage() {
        val image = try {
            scans.listScans(ScanFilters(limit = 1)).firstOrNull()?.let { files.fileFor(it.thumbnailPath ?: it.imagePath) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not load the latest scan image", e)
            null
        }
        _state.update { it.copy(latestImage = image) }
    }

    /** On start and on every ON_RESUME, so access granted in system settings applies at once. */
    fun onPermissionChecked(granted: Boolean, showRationale: Boolean) =
        _state.update { it.copy(access = cameraAccessFrom(granted, permissions.cameraAsked, showRationale)) }

    /** The system prompt's answer. */
    fun onPermissionResult(granted: Boolean, showRationale: Boolean) {
        if (countsAsAsked(granted, showRationale, refusedBefore = _state.value.access == CameraAccess.Denied)) {
            permissions.cameraAsked = true
        }
        onPermissionChecked(granted, showRationale)
    }

    fun onCameraReady(ready: Boolean) = _state.update { it.copy(ready = ready) }

    /** Binding the camera threw. */
    fun onMountError() = _state.update { it.copy(mountError = true, ready = false) }

    fun retryMount() = _state.update { it.copy(mountError = false) }

    fun toggleFlash() = _state.update { it.copy(flashOn = !it.flashOn) }

    /** The file for the next capture, or null while one is running or the preview is not ready. */
    fun startCapture(): File? {
        val s = _state.value
        if (!s.ready || s.capturing) return null
        _state.update { it.copy(capturing = true, notice = null) }
        return photos.newCaptureFile()
    }

    fun onCaptured(file: File) {
        viewModelScope.launch {
            try {
                session.setPhoto(photos.fromCamera(file))
                _state.update { it.copy(capturing = false, openPreview = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onCaptureFailed(e)
            }
        }
    }

    fun onCaptureFailed(error: Throwable) {
        Log.w(TAG, "Capture failed", error)
        _state.update { it.copy(capturing = false, notice = Notice.CaptureFailed) }
    }

    /** True if the screen should launch the photo picker now. */
    fun startGallery(): Boolean {
        if (_state.value.picking) return false
        _state.update { it.copy(picking = true, notice = null) }
        return true
    }

    /** Null is a cancelled pick, a normal choice: stay on the camera, no message. */
    fun onGalleryResult(uri: Uri?) {
        if (uri == null) {
            _state.update { it.copy(picking = false) }
            return
        }
        viewModelScope.launch {
            try {
                session.setPhoto(photos.fromGallery(uri))
                _state.update { it.copy(picking = false, openPreview = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gallery failed", e)
                _state.update { it.copy(picking = false, notice = Notice.GalleryFailed) }
            }
        }
    }

    fun previewOpened() = _state.update { it.copy(openPreview = false) }

    companion object {
        private const val TAG = "camera"

        val Factory = viewModelFactory {
            initializer {
                val c = container
                CameraViewModel(c.scanSession, c.scanRepository, c.files, c.scanLibrary, c.permissionHistory, c.capturedPhotos)
            }
        }
    }
}
