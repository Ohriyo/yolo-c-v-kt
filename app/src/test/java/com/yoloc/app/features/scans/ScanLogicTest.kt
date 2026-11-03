package com.yoloc.app.features.scans

import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.camera.FrameSize
import com.yoloc.app.features.camera.VIEWFINDER_ASPECT
import com.yoloc.app.features.camera.fitAspect
import com.yoloc.app.features.detection.TestAssets
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.learn.LearnTopic
import com.yoloc.app.features.onboarding.ONBOARDING_STEPS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * RN: scan.logic.test.ts (camera access, result edits), camera.logic.test.ts (viewfinder) and
 * settings.logic.test.ts (threshold). verdictTitle and saveResult need Android resources or the
 * database, so they are in androidTest (ScanFlowTest).
 */
class ScanLogicTest {
    @Test
    fun cameraAccessFollowsThePermissionState() {
        assertEquals(CameraAccess.Granted, cameraAccessFrom(granted = true, everAsked = true, showRationale = false))
        assertEquals(CameraAccess.Ask, cameraAccessFrom(granted = false, everAsked = false, showRationale = false))
        assertEquals(CameraAccess.Denied, cameraAccessFrom(granted = false, everAsked = true, showRationale = true))
        assertEquals(CameraAccess.Blocked, cameraAccessFrom(granted = false, everAsked = true, showRationale = false))
    }

    // correctResultDetection: early 0 -> severity 1, infected 1 -> 2, healthy 2 -> 0
    private val severities = mapOf(0 to 1, 1 to 2, 2 to 0)
    private val base = ScanResult(
        photo = PendingPhoto(File("/cache/a.jpg"), 10, 10, ScanSource.Camera),
        imageWidth = 10,
        imageHeight = 10,
        detections = listOf(
            ResultDetection(2, 0.9, 0.0, 0.0, 0.5, 0.5),
            ResultDetection(2, 0.3, 0.5, 0.5, 0.5, 0.5),
        ),
        verdict = Verdict.Healthy,
        totalPods = 2,
        lowConfidence = false,
        modelVersionId = "m",
        confThresholds = mapOf("early_stage" to 0.25),
        iouThreshold = 0.45,
        inferenceMs = 1,
        totalMs = 1,
        analyzedAt = 0,
    )

    @Test
    fun relabelsOnePodAndRecomputesTheVerdictWithoutChangingTheInput() {
        val next = correctResultDetection(base, 1, 0, severities, 0.6)
        assertEquals(Verdict.EarlyStage, next.verdict)
        assertEquals(0, next.detections[1].correctedClassId)
        assertNull(next.detections[0].correctedClassId)
        assertNull(base.detections[1].correctedClassId)
        assertEquals(0, next.detections[1].effectiveClassId)
    }

    @Test
    fun clearsTheCorrectionWhenTheModelClassIsPickedAgain() {
        val corrected = correctResultDetection(base, 0, 1, severities, 0.6)
        assertEquals(Verdict.Infected, corrected.verdict)
        val undone = correctResultDetection(corrected, 0, 2, severities, 0.6)
        assertNull(undone.detections[0].correctedClassId)
        assertEquals(Verdict.Healthy, undone.verdict)
    }

    @Test
    fun rejectsAPodIndexThatDoesNotExist() {
        val e = assertThrows(IllegalArgumentException::class.java) { correctResultDetection(base, 5, 0, severities, 0.6) }
        assertTrue(e.message!!.contains("No pod"))
    }

    @Test
    fun rejectsAClassWithNoKnownSeverityInsteadOfCountingItHealthy() {
        assertThrows(IllegalArgumentException::class.java) { correctResultDetection(base, 0, 9, severities, 0.6) }
    }

    @Test
    fun aCorrectionThatChangesWhichPodDecidesAlsoMovesTheUncertainFlag() {
        val weak = ResultDetection(2, 0.45, 0.1, 0.1, 0.2, 0.2)
        val strong = ResultDetection(2, 0.95, 0.5, 0.5, 0.2, 0.2)
        val photo = base.copy(detections = listOf(weak, strong))
        // Relabelling the weak pod as infected makes it the most severe, so it decides.
        val corrected = correctResultDetection(photo, 0, 1, severities, 0.6)
        assertEquals(Verdict.Infected, corrected.verdict)
        assertTrue(corrected.lowConfidence)
        // Undoing it hands the decision back to the strong healthy pod.
        assertFalse(correctResultDetection(corrected, 0, 2, severities, 0.6).lowConfidence)
    }

    @Test
    fun viewfinderFillsTheWidthWhenTheAreaIsTallAndTheHeightWhenItIsShort() {
        assertEquals(FrameSize(300f, 400f), fitAspect(300f, 800f, VIEWFINDER_ASPECT))
        assertEquals(FrameSize(300f, 400f), fitAspect(400f, 400f, VIEWFINDER_ASPECT))
    }

    @Test
    fun viewfinderKeepsThe3To4PhotoShapeOnARealPhoneLayout() {
        val (width, height) = fitAspect(328f, 560f, VIEWFINDER_ASPECT)
        assertEquals(328f, width)
        assertTrue(abs(width / height - 3f / 4f) < 0.01f)
    }

    @Test
    fun viewfinderReturnsAnEmptyFrameForAnUnusableArea() {
        for ((w, h) in listOf(0f to 500f, 500f to 0f, -20f to 500f)) {
            assertEquals(FrameSize(0f, 0f), fitAspect(w, h, VIEWFINDER_ASPECT))
        }
    }

    @Test
    fun learnTopicsAndOnboardingStepsMatchRn() {
        assertEquals(listOf("stages", "photos", "how"), LearnTopic.entries.map { it.key })
        assertEquals(LearnTopic.Photos, LearnTopic.fromKey("photos"))
        assertNull(LearnTopic.fromKey("nope"))
        assertNull(LearnTopic.fromKey(null))
        // 4 welcome slides + the permission primer, + the name step (Kotlin only, redesign).
        assertEquals(6, ONBOARDING_STEPS)
    }
}
