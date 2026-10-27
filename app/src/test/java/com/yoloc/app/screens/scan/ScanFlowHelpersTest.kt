package com.yoloc.app.screens.scan

import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.detection.Detection
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.live.LiveStatsSnapshot
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.features.scans.ResultDetection
import com.yoloc.app.features.scans.ScanResult
import com.yoloc.app.store.countsAsAsked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The pure parts of the Step 4.4 part 2 ViewModels; the rest runs on the phone. */
class ScanFlowHelpersTest {
    @Test
    fun aDismissedFirstPromptDoesNotCountAsAsked() {
        assertTrue(countsAsAsked(granted = true, showRationale = false, refusedBefore = false))
        assertTrue(countsAsAsked(granted = false, showRationale = true, refusedBefore = false)) // a real refusal
        assertFalse(countsAsAsked(granted = false, showRationale = false, refusedBefore = false)) // tapped outside
        assertTrue(countsAsAsked(granted = false, showRationale = false, refusedBefore = true)) // don't ask again
    }

    @Test
    fun liveResultsGoStaleAfterOneAndAHalfSeconds() {
        val pod = Detection(0, 0.9, 0.1, 0.1, 0.2, 0.2)
        val result = LiveViewModel.FrameResult(listOf(pod), 768, 1024, at = 10_000)
        assertEquals(listOf(pod), LiveViewModel.State(result = result, now = 11_499).shown)
        assertTrue(LiveViewModel.State(result = result, now = 11_500).shown.isEmpty())
        assertTrue(LiveViewModel.State(now = 0).shown.isEmpty())
    }

    @Test
    fun theBenchLineMatchesRnsJson() {
        val snap = LiveStatsSnapshot(10.0, 29.987, 2.0, 440.456, 480.0, 5.5, 3.0, 0.9333333)
        assertEquals(
            """{"backend":"gpu","seconds":10,"cameraFps":29.99,"detectionsPerSecond":2,"inferenceP50":440.46,""" +
                """"inferenceP90":480,"resizeP50":5.5,"postprocessP50":3,"skippedRatio":0.93}""",
            benchLine(LiveViewModel.Backend.Gpu, snap),
        )
    }

    @Test
    fun leavingIsGuardedOnlyWithUnsavedPods() {
        val photo = PendingPhoto(File("/cache/a.jpg"), 800, 600, ScanSource.Camera)
        fun result(pods: Int) = ScanResult(
            photo, 800, 600, List(pods) { ResultDetection(2, 0.9, 0.1, 0.1, 0.2, 0.2) }, Verdict.Healthy, pods, false,
            "m", 0.25, 0.45, 1, 2, 0,
        )
        assertTrue(ResultViewModel.State(result = result(1)).guarded)
        assertFalse(ResultViewModel.State(result = result(0)).guarded)
        assertFalse(ResultViewModel.State(result = result(1), exit = ResultViewModel.Exit.Discard).guarded)
        assertFalse(ResultViewModel.State().guarded)
    }
}
