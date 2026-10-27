package com.yoloc.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Test

/** RN: __tests__/detection.math.test.ts (iou and nonMaxSuppression). */
class NmsTest {
    private fun box(x1: Double, y1: Double, x2: Double, y2: Double) = PixelBox(x1, y1, x2, y2)

    @Test
    fun computesIou() {
        assertEquals(1.0, iou(box(0.0, 0.0, 10.0, 10.0), box(0.0, 0.0, 10.0, 10.0)), 0.0)
        assertEquals(50.0 / 150, iou(box(0.0, 0.0, 10.0, 10.0), box(5.0, 0.0, 15.0, 10.0)), 1e-9)
        assertEquals(0.0, iou(box(0.0, 0.0, 10.0, 10.0), box(10.0, 10.0, 20.0, 20.0)), 0.0)
    }

    @Test
    fun suppressesOverlappingBoxesOfTheSameClassKeepingTheBest() {
        val kept = nonMaxSuppression(
            listOf(
                candidate(0.0, 0.0, 10.0, 10.0, 0, 0.6),
                candidate(1.0, 0.0, 11.0, 10.0, 0, 0.9),
                candidate(50.0, 50.0, 60.0, 60.0, 0, 0.5),
            ),
            0.45,
        )
        assertEquals(listOf(0.9, 0.5), kept.map { it.score })
    }

    @Test
    fun neverLetsDifferentClassesSuppressEachOther() {
        val kept = nonMaxSuppression(
            listOf(candidate(0.0, 0.0, 10.0, 10.0, 0, 0.9), candidate(0.0, 0.0, 10.0, 10.0, 2, 0.8)),
            0.45,
        )
        assertEquals(listOf(0, 2), kept.map { it.classId })
    }

    @Test
    fun suppressesOnlyAboveTheIouThresholdAndCapsTheCount() {
        // IoU = 50 / 150 = 0.333: kept at threshold 0.5, suppressed at 0.3.
        val pair = listOf(candidate(0.0, 0.0, 10.0, 10.0, 1, 0.9), candidate(5.0, 0.0, 15.0, 10.0, 1, 0.8))
        assertEquals(2, nonMaxSuppression(pair, 0.5).size)
        assertEquals(1, nonMaxSuppression(pair, 0.3).size)
        val many = (0 until 10).map { i -> candidate(i * 20.0, 0.0, i * 20.0 + 10, 10.0, 0, 1 - i / 100.0) }
        assertEquals(3, nonMaxSuppression(many, 0.45, 3).size)
    }
}
