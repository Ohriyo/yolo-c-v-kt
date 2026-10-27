package com.yoloc.app.features.scans

import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.features.scans.ui.ReportDetection
import com.yoloc.app.features.scans.ui.classView
import com.yoloc.app.features.scans.ui.summarizeReport
import com.yoloc.app.theme.StatusKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RN: ScanReport's classView and its pods / atVerdict / advice computation. */
class ScanReportTest {
    private val modelClasses = listOf("early_stage", "fully_infected_pod", "healthy_pod")
    private val classes = mapOf(
        0 to DetectionClass(0, "early_stage", "Early stage", 1, "early", "Early summary", null, listOf("Remove the pod")),
        1 to DetectionClass(1, "fully_infected_pod", "Infected", 2, "infected", "Infected summary", "White powder", listOf("Bury it", "Clean tools")),
        2 to DetectionClass(2, "healthy_pod", "Healthy", 0, "healthy", "Healthy summary", null, emptyList()),
    )

    private fun pod(classId: Int, corrected: Int? = null) = ReportDetection(classId, corrected, 0.9, 0.1, 0.1, 0.2, 0.2)

    @Test
    fun classViewFallsBackToTheModelKeyThenAClassNumber() {
        assertEquals("Infected", classView(classes, 1, modelClasses).label)
        assertEquals(StatusKey.Infected, classView(classes, 1, modelClasses).status)
        val loading = classView(null, 1, modelClasses)
        assertEquals("fully_infected_pod", loading.label)
        assertEquals(StatusKey.Healthy, loading.status)
        assertEquals(0, loading.severity)
        assertEquals("Class 7", classView(null, 7, modelClasses).label)
    }

    @Test
    fun theVerdictCountsPodsAtTheHighestSeverityAndTakesItsAdvice() {
        val r = summarizeReport(listOf(pod(2), pod(0), pod(1), pod(1)), classes, modelClasses)
        assertEquals(4, r.pods.size)
        assertEquals(2, r.countAtVerdict)
        assertEquals("Infected summary", r.advice?.summary)
        assertEquals(listOf("Bury it", "Clean tools"), r.advice?.actions)
        assertEquals(listOf("0", "1", "2", "3"), r.pods.map { it.id })
    }

    @Test
    fun aCorrectionDecidesTheClassAndIsMarkedOnlyWhenItDiffers() {
        val r = summarizeReport(listOf(pod(0, corrected = 1), pod(2, corrected = 2)), classes, modelClasses)
        assertEquals("Infected", r.pods[0].view.label)
        assertTrue(r.pods[0].corrected)
        // Picking the model's own class again is not a correction.
        assertFalse(r.pods[1].corrected)
        assertEquals(1, r.countAtVerdict)
    }

    @Test
    fun noPodsMeansNoAdvice() {
        val r = summarizeReport(emptyList(), classes, modelClasses)
        assertEquals(0, r.countAtVerdict)
        assertNull(r.advice)
    }
}
