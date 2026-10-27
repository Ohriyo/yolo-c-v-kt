package com.yoloc.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** RN: __tests__/verdict.test.ts. */
class VerdictTest {
    private fun d(severity: Int, confidence: Double = 0.9) = VerdictInput(severity, confidence)

    @Test
    fun isNoPodWithNoDetectionsNeverHealthy() {
        assertEquals(VerdictSummary(Verdict.NoPod, 0, false), summarizeDetections(emptyList(), 0.4))
    }

    @Test
    fun takesTheMostSevereClass() {
        assertEquals(Verdict.Healthy, summarizeDetections(listOf(d(0), d(0)), 0.4).verdict)
        assertEquals(Verdict.EarlyStage, summarizeDetections(listOf(d(0), d(1)), 0.4).verdict)
        assertEquals(Verdict.Infected, summarizeDetections(listOf(d(1), d(2), d(0)), 0.4).verdict)
    }

    @Test
    fun flagsLowConfidenceOnlyWhenTheBestDetectionIsBelowTheThreshold() {
        assertTrue(summarizeDetections(listOf(d(0, 0.39), d(1, 0.3)), 0.4).lowConfidence)
        assertFalse(summarizeDetections(listOf(d(0, 0.39), d(1, 0.4)), 0.4).lowConfidence)
    }

    @Test
    fun rejectsAnUnknownSeverity() {
        val e = assertThrows(IllegalStateException::class.java) { summarizeDetections(listOf(d(7)), 0.4) }
        assertTrue(e.message!!.contains("Unknown severity"))
    }
}
