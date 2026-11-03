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
    fun flagsUncertainWhenTheDetectionThatDecidesTheVerdictIsBelowTheLimit() {
        // A weak infected pod decides, however sure the model is about the healthy one beside it.
        assertTrue(summarizeDetections(listOf(d(0, 0.95), d(2, 0.45)), 0.6).lowConfidence)
        // A strong infected pod decides, whatever else is in the photo.
        assertFalse(summarizeDetections(listOf(d(0, 0.3), d(2, 0.6)), 0.6).lowConfidence)
    }

    @Test
    fun theStrongestPodOfTheMostSevereClassIsTheOneThatDecides() {
        assertFalse(summarizeDetections(listOf(d(2, 0.3), d(2, 0.8), d(0, 0.1)), 0.6).lowConfidence)
        assertTrue(summarizeDetections(listOf(d(2, 0.3), d(2, 0.5), d(1, 0.99)), 0.6).lowConfidence)
    }

    @Test
    fun rejectsAnUnknownSeverity() {
        val e = assertThrows(IllegalStateException::class.java) { summarizeDetections(listOf(d(7)), 0.4) }
        assertTrue(e.message!!.contains("Unknown severity"))
    }
}
