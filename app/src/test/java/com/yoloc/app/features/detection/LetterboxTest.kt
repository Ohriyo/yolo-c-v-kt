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
    fun roundTripsBoxesOriginalToInputToOriginal() {
        for ((w, h) in listOf(3072 to 3072, 365 to 547, 167 to 278, 1920 to 1080)) {
            val lb = computeLetterbox(w, h, 640)
            for ((fx1, fy1, fx2, fy2) in listOf(
                listOf(0.0, 0.0, 1.0, 1.0),
                listOf(0.1, 0.2, 0.4, 0.9),
                listOf(0.5, 0.5, 0.51, 0.52),
            )) {
                val box = PixelBox(fx1 * w, fy1 * h, fx2 * w, fy2 * h)
                val back = toOriginalPixels(toInputPixels(box, lb), lb)
                assertEquals(box.x1, back.x1, 1e-6)
                assertEquals(box.y1, back.y1, 1e-6)
                assertEquals(box.x2, back.x2, 1e-6)
                assertEquals(box.y2, back.y2, 1e-6)
            }
        }
    }

    @Test
    fun mapsPaddedImageCornersToOriginalCorners() {
        val lb = computeLetterbox(365, 547, 640)
        val full = PixelBox(lb.padLeft.toDouble(), 0.0, lb.padLeft + 365 * lb.scale, 640.0)
        val box = toOriginalPixels(full, lb)
        assertEquals(0.0, box.x1, 1e-6)
        assertEquals(365.0, box.x2, 1e-6)
        assertEquals(547.0, box.y2, 1e-6)
    }

    @Test
    fun clipsBoxesThatSpillIntoThePadding() {
        val lb = computeLetterbox(365, 547, 640)
        val box = toOriginalPixels(PixelBox(0.0, -20.0, 640.0, 700.0), lb)
        assertEquals(PixelBox(0.0, 0.0, 365.0, 547.0), box)
    }
}

private operator fun IntArray.component6() = this[5]
