package com.yoloc.app.features.live

import com.yoloc.app.features.detection.Detection
import com.yoloc.app.features.detection.TensorInfo
import com.yoloc.app.features.detection.TestAssets
import com.yoloc.app.features.detection.computeLetterbox
import com.yoloc.app.features.detection.describeModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** RN: __tests__/liveMath.test.ts. */
class LiveMathTest {
    // The real model's tensors: input [1,3,640,640] float32, output [1,7,8400] (normalized boxes).
    private val spec = describeModel(
        listOf(TensorInfo("images", "float32", listOf(1, 3, 640, 640))),
        listOf(TensorInfo("output0", "float32", listOf(1, 7, 8400))),
        TestAssets.modelConfig,
    )

    /** A [1,7,8400] output with one confident candidate, box given in input pixels (cx, cy, w, h). */
    private fun outputWith(classId: Int, score: Float, cx: Float, cy: Float, w: Float, h: Float): FloatArray {
        val n = 8400
        val out = FloatArray(7 * n)
        // Normalized boxes (0-1), as this model exports them.
        out[0 * n] = cx / 640
        out[1 * n] = cy / 640
        out[2 * n] = w / 640
        out[3 * n] = h / 640
        out[(4 + classId) * n] = score
        return out
    }

    @Test
    fun frameLetterboxMatchesThePhotoPipeline() {
        assertEquals(computeLetterbox(768, 1024, 640), frameLetterbox(768, 1024, 640))
        val lb = frameLetterbox(768, 1024, 640)
        assertEquals(480, lb.resizedWidth)
        assertEquals(80, lb.padLeft)
        assertEquals(0, lb.padTop)
    }

    @Test
    fun mapsABoxFromTheContainedInputBackToTheUprightFrame() {
        // A pod centered in the frame, a quarter of its width and height.
        val d = liveDetections(outputWith(0, 0.9f, 320f, 320f, 120f, 160f), spec, 768, 1024, 0.25, 0.45).single()
        assertEquals(0, d.classId)
        assertEquals(0.9, d.confidence, 1e-5)
        assertEquals(0.5, d.x + d.w / 2, 1e-3)
        assertEquals(0.5, d.y + d.h / 2, 1e-3)
        assertEquals(0.25, d.w, 1e-3)
        assertEquals(0.25, d.h, 1e-3)
    }

    @Test
    fun respectsTheConfidenceThresholdFromSettings() {
        val out = outputWith(2, 0.3f, 320f, 320f, 100f, 100f)
        assertEquals(1, liveDetections(out, spec, 768, 1024, 0.25, 0.45).size)
        assertEquals(0, liveDetections(out, spec, 768, 1024, 0.5, 0.45).size)
    }

    @Test
    fun keepsAtMostTwentyBoxes() {
        val n = 8400
        val out = FloatArray(7 * n)
        // 30 small, separate pods in a grid, so NMS keeps them all.
        for (i in 0 until 30) {
            out[0 * n + i] = (20f + (i % 6) * 100f) / 640
            out[1 * n + i] = (20f + (i / 6) * 100f) / 640
            out[2 * n + i] = 20f / 640
            out[3 * n + i] = 20f / 640
            out[4 * n + i] = 0.5f + i / 100f
        }
        assertEquals(LIVE_MAX_DETECTIONS, liveDetections(out, spec, 640, 640, 0.25, 0.45).size)
    }

    @Test
    fun scalesToCoverTheViewAndCropsTheOverflowEqually() {
        // 3:4 frame on a 360x800 portrait view: height decides, sides are cropped.
        val t = previewTransform(768, 1024, 360.0, 800.0)
        assertEquals(800.0 / 1024, t.scale, 1e-6)
        assertEquals(0.0, t.offsetY, 1e-6)
        assertEquals((360 - 768 * (800.0 / 1024)) / 2, t.offsetX, 1e-6)
        val box = toViewBox(Detection(0, 0.9, 0.5, 0.5, 0.1, 0.1), 768, 1024, t)
        assertEquals(180 + 0.05 * 768 * t.scale, box.left + box.width / 2, 1e-3)
        assertEquals(400.0, box.top, 1e-3)
    }

    @Test
    fun reportsCameraFpsDetectionRatePercentilesAndTheSkippedShare() {
        val stats = LiveStats(10_000)
        // 30 fps for 4 s; an inference every 500 ms taking 400-480 ms.
        for (s in 0..4) stats.markFrames(s * 1000L, s * 30L)
        for (i in 0..8) stats.addSample(LiveSample(i * 500L, 5.0, 400.0 + i * 10, 3.0))
        val snap = stats.snapshot(4000)
        assertEquals(30.0, snap.cameraFps, 1e-5)
        assertEquals(2.0, snap.detectionsPerSecond, 1e-5)
        assertEquals(440.0, snap.inferenceP50, 0.0)
        assertEquals(480.0, snap.inferenceP90, 0.0)
        assertEquals(1 - 2.0 / 30, snap.skippedRatio, 1e-5)
    }

    @Test
    fun forgetsSamplesOutsideTheWindow() {
        val stats = LiveStats(1000)
        stats.addSample(LiveSample(0, 1.0, 999.0, 1.0))
        stats.addSample(LiveSample(5000, 1.0, 100.0, 1.0))
        assertEquals(100.0, stats.snapshot(5000).inferenceP50, 0.0)
    }

    @Test
    fun emptyStatsReportZeros() {
        val snap = LiveStats().snapshot(0)
        assertEquals(0.0, snap.cameraFps, 0.0)
        assertEquals(0.0, snap.inferenceP50, 0.0)
        assertEquals(0.0, snap.skippedRatio, 0.0)
    }
}
