package com.yoloc.app.features.live

import com.yoloc.app.features.detection.Detection
import com.yoloc.app.features.detection.Letterbox
import com.yoloc.app.features.detection.ModelSpec
import com.yoloc.app.features.detection.Thresholds
import com.yoloc.app.features.detection.computeLetterbox
import com.yoloc.app.features.detection.postprocess
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * RN: live/liveMath.ts. Live mode math, kept free of camera code so it can be tested on the JVM.
 *
 * The live frame is scaled to fit the square model input and centered with padding ("contain"),
 * which is the same geometry as our Ultralytics letterbox (only the pad color may differ). So the
 * photo pipeline's tested postprocess maps live boxes back to the frame unchanged.
 */

fun frameLetterbox(frameWidth: Int, frameHeight: Int, size: Int): Letterbox =
    computeLetterbox(frameWidth, frameHeight, size)

/** At most this many boxes are drawn over the live preview. */
const val LIVE_MAX_DETECTIONS = 20

/** Raw live model output -> detections normalized to the upright frame. */
fun liveDetections(
    output: FloatArray,
    spec: ModelSpec,
    frameWidth: Int,
    frameHeight: Int,
    confThreshold: Double,
    iouThreshold: Double,
): List<Detection> {
    val lb = frameLetterbox(frameWidth, frameHeight, spec.inputSize)
    return postprocess(output, spec, lb, Thresholds(confThreshold, iouThreshold, LIVE_MAX_DETECTIONS)).detections
}

data class PreviewTransform(val scale: Double, val offsetX: Double, val offsetY: Double)

/**
 * Maps normalized frame coordinates onto the preview view, which shows the same camera image
 * scaled to cover the view (cropped at the edges). Both come from the same sensor and are
 * upright, so the difference is only a uniform scale plus a centered crop.
 */
fun previewTransform(frameWidth: Int, frameHeight: Int, viewWidth: Double, viewHeight: Double): PreviewTransform {
    val scale = max(viewWidth / frameWidth, viewHeight / frameHeight)
    return PreviewTransform(
        scale = scale,
        offsetX = (viewWidth - frameWidth * scale) / 2,
        offsetY = (viewHeight - frameHeight * scale) / 2,
    )
}

data class ViewBox(val left: Double, val top: Double, val width: Double, val height: Double)

fun toViewBox(d: Detection, frameWidth: Int, frameHeight: Int, t: PreviewTransform): ViewBox = ViewBox(
    left = d.x * frameWidth * t.scale + t.offsetX,
    top = d.y * frameHeight * t.scale + t.offsetY,
    width = d.w * frameWidth * t.scale,
    height = d.h * frameHeight * t.scale,
)

/** Nearest-rank on an ascending list; 0 when empty. */
private fun percentile(sorted: List<Double>, p: Int): Double =
    if (sorted.isEmpty()) 0.0 else sorted[min(sorted.size - 1, floor(p / 100.0 * sorted.size).toInt())]

data class LiveSample(
    /** When the result reached the UI side (ms, monotonic). */
    val at: Long,
    val resizeMs: Double,
    val inferenceMs: Double,
    val postprocessMs: Double,
)

data class LiveStatsSnapshot(
    val seconds: Double,
    /** Frames the camera delivered to the analyzer per second. */
    val cameraFps: Double,
    /** Completed inferences per second. */
    val detectionsPerSecond: Double,
    val inferenceP50: Double,
    val inferenceP90: Double,
    val resizeP50: Double,
    val postprocessP50: Double,
    /** Share of delivered frames skipped because a run was in progress or throttled. */
    val skippedRatio: Double,
)

/**
 * Rolling benchmark numbers for the live HUD and the benchmark log, over the last `windowMs`.
 * Camera frames are counted by the analyzer; inference samples arrive with results. Not
 * thread-safe: call it from one thread (the live ViewModel's).
 */
class LiveStats(private val windowMs: Long = LIVE_STATS_WINDOW_MS) {
    private data class FrameMark(val at: Long, val total: Long)

    private val samples = ArrayDeque<LiveSample>()
    private val frameMarks = ArrayDeque<FrameMark>()

    fun addSample(sample: LiveSample) {
        samples.addLast(sample)
        trim(sample.at)
    }

    /** Records the analyzer's running frame count at time `at`. */
    fun markFrames(at: Long, totalFrames: Long) {
        frameMarks.addLast(FrameMark(at, totalFrames))
        trim(at)
    }

    fun snapshot(now: Long): LiveStatsSnapshot {
        trim(now)
        val first = frameMarks.firstOrNull()
        val last = frameMarks.lastOrNull()
        val frameSpan = if (first != null && last != null) (last.at - first.at) / 1000.0 else 0.0
        val frames = if (first != null && last != null) (last.total - first.total).toDouble() else 0.0
        val sampleSpan = if (samples.size > 1) (samples.last().at - samples.first().at) / 1000.0 else 0.0
        fun sorted(value: (LiveSample) -> Double) = samples.map(value).sorted()
        val inference = sorted { it.inferenceMs }
        val cameraFps = if (frameSpan > 0) frames / frameSpan else 0.0
        val detectionsPerSecond = if (sampleSpan > 0) (samples.size - 1) / sampleSpan else 0.0
        return LiveStatsSnapshot(
            seconds = max(frameSpan, sampleSpan),
            cameraFps = cameraFps,
            detectionsPerSecond = detectionsPerSecond,
            inferenceP50 = percentile(inference, 50),
            inferenceP90 = percentile(inference, 90),
            resizeP50 = percentile(sorted { it.resizeMs }, 50),
            postprocessP50 = percentile(sorted { it.postprocessMs }, 50),
            skippedRatio = if (cameraFps > 0) max(0.0, 1 - detectionsPerSecond / cameraFps) else 0.0,
        )
    }

    private fun trim(now: Long) {
        val cutoff = now - windowMs
        while (samples.isNotEmpty() && samples.first().at < cutoff) samples.removeFirst()
        // The newest mark always stays, so the next one has a frame count to measure from.
        while (frameMarks.size > 1 && frameMarks.first().at < cutoff) frameMarks.removeFirst()
    }
}

/** The HUD and the `[live-bench]` log cover the last 10 s. */
const val LIVE_STATS_WINDOW_MS = 10_000L

/**
 * Practicality bar for live mode on the target phone, fixed before measuring (see the proposal):
 * fast enough to feel live, a smooth preview, and sustainable for a field session.
 */
object LivePractical {
    const val MIN_DETECTIONS_PER_SECOND = 2
    const val MIN_CAMERA_FPS = 24
    const val MAX_BATTERY_PERCENT_PER_HOUR = 15
}
