package com.yoloc.app.screens.scan

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.BuildConfig
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.features.detection.Detection
import com.yoloc.app.features.detection.LiveRunner
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.detection.ModelLoader
import com.yoloc.app.features.detection.ModelSpec
import com.yoloc.app.features.detection.buildInputTensor
import com.yoloc.app.features.detection.resizedPixels
import com.yoloc.app.features.live.LiveSample
import com.yoloc.app.features.live.LiveStats
import com.yoloc.app.features.live.LiveStatsSnapshot
import com.yoloc.app.features.live.frameLetterbox
import com.yoloc.app.features.live.liveDetections
import com.yoloc.app.features.scans.CapturedPhotos
import com.yoloc.app.screens.container
import com.yoloc.app.store.ScanSession
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.util.toJsString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * RN: app/scan/live.tsx. The preview runs at the camera's own rate while CameraX's analyzer
 * (keep-only-latest) hands the newest frame to [analyze], which runs the model as often as it
 * allows (throttled) and drops frames that arrive while it is busy. Live results are a preview:
 * capture takes a full photo through the normal, verified Preview -> Processing -> Result path.
 */
class LiveViewModel(
    private val loader: ModelLoader,
    private val config: ModelConfig,
    private val settings: SettingsStore,
    private val session: ScanSession,
    private val photos: CapturedPhotos,
    private val classRepository: ClassRepository,
    /** Dev builds show the benchmark HUD and log `[live-bench]` every 10 s. */
    private val benchmark: Boolean = BuildConfig.DEBUG,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    enum class Backend(val key: String) { Gpu("gpu"), Cpu("cpu") }

    enum class Notice { GpuFallback, CaptureFailed }

    /** Detections normalized to the upright frame, and when they arrived (ms, monotonic). */
    data class FrameResult(val detections: List<Detection>, val frameWidth: Int, val frameHeight: Int, val at: Long)

    data class State(
        val backend: Backend = Backend.Gpu,
        /** The model is ready for frames. */
        val loaded: Boolean = false,
        /** Neither backend could load: the screen offers "Use photo" instead. */
        val loadError: Boolean = false,
        val notice: Notice? = null,
        val result: FrameResult? = null,
        /** Ticks once a second, for staleness. */
        val now: Long = 0,
        /** The dev HUD's numbers; null outside dev builds. */
        val stats: LiveStatsSnapshot? = null,
        val capturing: Boolean = false,
        val classes: Map<Int, DetectionClass>? = null,
        /** Set when the captured photo is in the session: the screen replaces itself with Preview. */
        val openPreview: Boolean = false,
    ) {
        /** Results older than [STALE_MS] are hidden (the phone moved on). */
        val shown: List<Detection>
            get() = result?.takeIf { now - it.at < STALE_MS }?.detections.orEmpty()
    }

    private val _state = MutableStateFlow(State(now = clock()))
    val state: StateFlow<State> = _state.asStateFlow()

    /** Spec from the CPU model, which reads the same file as the GPU runner. */
    private class Model(val runner: LiveRunner, val spec: ModelSpec)

    @Volatile
    private var model: Model? = null
    private val frameCount = AtomicLong(0)
    private val lastRunEnd = AtomicLong(Long.MIN_VALUE / 2)
    private val stats = LiveStats()
    private var lastLog = Long.MIN_VALUE / 2

    init {
        viewModelScope.launch { _state.map { it.backend }.distinctUntilChanged().collectLatest { load(it) } }
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
        // Once a second: camera fps from the frame counter, staleness, and the benchmark log.
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    /** Load the model for the chosen backend; if the GPU cannot start, fall back to the CPU. */
    private suspend fun load(backend: Backend) {
        model = null
        _state.update { it.copy(loaded = false) }
        // The CPU model comes first either way (it gives the tensor layout); if it fails, the GPU
        // cannot help, so that is a load error, not a GPU fallback.
        val cpu = try {
            loader.loadCpu()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Live model failed to load", e)
            _state.update { it.copy(loadError = true) }
            return
        }
        try {
            val runner = if (backend == Backend.Gpu) loader.loadGpu().runner else cpu
            model = Model(runner, cpu.spec)
            _state.update { it.copy(loaded = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Live model failed on ${backend.key}", e)
            _state.update { it.copy(notice = Notice.GpuFallback, backend = Backend.Cpu) }
        }
    }

    /**
     * Called on the analyzer thread for each frame. [frame] gives it upright, and is only called
     * for a frame that will be run (a skipped frame is just counted). Blocks while the model runs,
     * which is what makes CameraX drop the frames that arrive meanwhile. Does not keep the bitmap.
     * The resize time includes getting the frame, as RN's resizer reads the camera frame itself.
     */
    fun analyze(frame: () -> Bitmap) {
        frameCount.incrementAndGet()
        val m = model ?: return
        // Battery throttle: a gap between the end of one run and the start of the next.
        if (clock() - lastRunEnd.get() < MIN_INTERVAL_MS) return
        try {
            val t0 = System.nanoTime()
            val bitmap = frame()
            val lb = frameLetterbox(bitmap.width, bitmap.height, m.spec.inputSize)
            // The photo pipeline's own letterbox and pad value, so live boxes map back exactly.
            val input = buildInputTensor(resizedPixels(bitmap, lb.resizedWidth, lb.resizedHeight), lb, config.letterboxPadValue, m.spec.inputLayout)
            val t1 = System.nanoTime()
            val output = m.runner.run(input)
            val t2 = System.nanoTime()
            lastRunEnd.set(clock())
            val detections = liveDetections(output, m.spec, bitmap.width, bitmap.height, settings.state.value.confThreshold, config.iouThreshold)
            val t3 = System.nanoTime()
            val at = clock()
            synchronized(stats) { stats.addSample(LiveSample(at, ms(t0, t1), ms(t1, t2), ms(t2, t3))) }
            _state.update { it.copy(result = FrameResult(detections, bitmap.width, bitmap.height, at)) }
        } catch (e: Exception) {
            // One bad frame (or a backend that died) must not stop the preview; the next frame tries again.
            lastRunEnd.set(clock())
            Log.w(TAG, "Live frame failed", e)
        }
    }

    private fun tick() {
        val at = clock()
        val snap = synchronized(stats) {
            stats.markFrames(at, frameCount.get())
            stats.snapshot(at)
        }
        _state.update { it.copy(now = at, stats = if (benchmark) snap else null) }
        if (benchmark && at - lastLog >= BENCH_LOG_EVERY_MS) {
            lastLog = at
            Log.i(BENCH_TAG, "[live-bench] ${benchLine(_state.value.backend, snap)}")
        }
    }

    /** Dev HUD: switch backend to compare GPU and CPU. */
    fun switchBackend(backend: Backend) = _state.update { it.copy(backend = backend, loadError = false) }

    /** The file for the full-resolution capture, or null while one is running. */
    fun startCapture(): File? {
        if (_state.value.capturing) return null
        _state.update { it.copy(capturing = true) }
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
                onCaptureFailed(e) // fromCamera already deleted the file
            }
        }
    }

    /** `file` is the one [startCapture] gave out; anything CameraX left in it is deleted. */
    fun onCaptureFailed(error: Throwable, file: File? = null) {
        file?.delete()
        Log.w(TAG, "Live capture failed", error)
        _state.update { it.copy(capturing = false, notice = Notice.CaptureFailed) }
    }

    fun previewOpened() = _state.update { it.copy(openPreview = false) }

    companion object {
        private const val TAG = "live"
        private const val BENCH_TAG = "live-bench"

        /** Minimum gap between the end of one inference and the start of the next (battery throttle). */
        const val MIN_INTERVAL_MS = 150L

        /** Results older than this are treated as stale. */
        const val STALE_MS = 1500L
        const val BENCH_LOG_EVERY_MS = 10_000L

        private fun ms(from: Long, to: Long) = (to - from) / 1e6

        val Factory = viewModelFactory {
            initializer {
                val c = container
                LiveViewModel(c.modelLoader, c.modelConfig, c.settingsStore, c.scanSession, c.capturedPhotos, c.classRepository)
            }
        }
    }
}

/** RN's `JSON.stringify({ backend, ...round(snap) })`, each number rounded to 2 decimals. */
internal fun benchLine(backend: LiveViewModel.Backend, s: LiveStatsSnapshot): String {
    fun r(v: Double) = (Math.round(v * 100) / 100.0).toJsString()
    return listOf(
        "\"backend\":\"${backend.key}\"",
        "\"seconds\":${r(s.seconds)}",
        "\"cameraFps\":${r(s.cameraFps)}",
        "\"detectionsPerSecond\":${r(s.detectionsPerSecond)}",
        "\"inferenceP50\":${r(s.inferenceP50)}",
        "\"inferenceP90\":${r(s.inferenceP90)}",
        "\"resizeP50\":${r(s.resizeP50)}",
        "\"postprocessP50\":${r(s.postprocessP50)}",
        "\"skippedRatio\":${r(s.skippedRatio)}",
    ).joinToString(",", "{", "}")
}
