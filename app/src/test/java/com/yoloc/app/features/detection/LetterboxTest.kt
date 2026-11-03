package com.yoloc.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** RN: __tests__/detection.math.test.ts (pythonRound, computeLetterbox, coordinate mapping). */
class LetterboxTest {
    @Test
    fun pythonRoundRoundsHalvesToEvenLikePython3() {
        val cases = listOf(
            0.5 to 0, 1.5 to 2, 2.5 to 2, 3.5 to 4, 2.4999 to 2, 2.5001 to 3,
            -0.5 to 0, -1.5 to -2, 106.4 to 106,
        )
        for ((value, expected) in cases) assertEquals("round($value)", expected, pythonRound(value))
    }

    @Test
    fun computesLetterboxForRealSampleSizes() {
        // [width, height, resizedW, resizedH, padLeft, padTop]
        val cases = listOf(
            listOf(3072, 3072, 640, 640, 0, 0),
            listOf(365, 547, 427, 640, 106, 0),
            listOf(167, 278, 384, 640, 128, 0),
            listOf(1920, 1080, 640, 360, 0, 140),
            listOf(4000, 3000, 640, 480, 0, 80),
        )
        for ((w, h, rw, rh, left, top) in cases.map { it.toIntArray() }) {
            val lb = computeLetterbox(w, h, 640)
            assertEquals("${w}x$h", listOf(rw, rh, left, top), listOf(lb.resizedWidth, lb.resizedHeight, lb.padLeft, lb.padTop))
            assertEquals(minOf(640.0 / w, 640.0 / h), lb.scale, 1e-12)
        }
    }

    @Test
    fun splitsOddPaddingLikeUltralytics() {
        // 640 - 427 = 213 -> left round(106.4) = 106, right 107.
        val lb = computeLetterbox(365, 547, 640)
        assertEquals(106, lb.padLeft)
        assertEquals(107, 640 - lb.resizedWidth - lb.padLeft)
    }

    @Test
    fun rejectsEmptySizes() {
        val e = assertThrows(IllegalArgumentException::class.java) { computeLetterbox(0, 100, 640) }
        assertTrue(e.message!!.contains("Invalid"))
    }

    @Test
    fun mapsPaddedImageCornersToTheWholeImage() {
        val lb = computeLetterbox(365, 547, 640)
        val full = PixelBox(lb.padLeft.toDouble(), 0.0, lb.padLeft + 365 * lb.scale, 640.0)
        val box = toNormalizedOriginal(full, lb)
        assertEquals(0.0, box.x1, 1e-6)
        assertEquals(1.0, box.x2, 1e-6)
        assertEquals(1.0, box.y2, 1e-6)
    }

    @Test
    fun clampsBoxesThatSpillIntoThePadding() {
        val lb = computeLetterbox(365, 547, 640)
        assertEquals(PixelBox(0.0, 0.0, 1.0, 1.0), toNormalizedOriginal(PixelBox(0.0, -20.0, 640.0, 700.0), lb))
    }
}

private operator fun IntArray.component6() = this[5]
