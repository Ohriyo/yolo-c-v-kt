package com.yoloc.app.features.detection

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** RN: __tests__/detection.math.test.ts (buildInputTensor). */
class PreprocessTest {
    // A 2x4 image letterboxed into 4x4: one pad column on each side.
    private val lb = computeLetterbox(2, 4, 4)
    private val pad = 114 / 255f
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val white = 0xFFFFFFFF.toInt()

    // row 0: red, green; rows 1-3: blue, white
    private val image = DecodedImage(2, 4, intArrayOf(red, green, blue, white, blue, white, blue, white))

    @Test
    fun placesPixelsAtTheLetterboxOffsetInPlanarNchwOrder() {
        assertEquals(listOf(2, 4, 1, 0), listOf(lb.resizedWidth, lb.resizedHeight, lb.padLeft, lb.padTop))
        val t = buildInputTensor(image, lb, 114, InputLayout.NCHW)
        assertEquals(3 * 16, t.size)
        val plane = 16
        // Pixel (x=1, y=0) is red; (x=2, y=0) is green; column 0 is padding.
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), floatArrayOf(t[1], t[plane + 1], t[2 * plane + 1]), 0f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), floatArrayOf(t[2], t[plane + 2], t[2 * plane + 2]), 0f)
        assertEquals(pad, t[0], 1e-6f)
        assertEquals(pad, t[3], 1e-6f)
        assertEquals(1f, t[2 * plane + 4 + 1]) // (x=1, y=1) blue channel
    }

    @Test
    fun writesInterleavedNhwc() {
        val t = buildInputTensor(image, lb, 114, InputLayout.NHWC)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), t.copyOfRange(3, 6), 0f) // (x=1, y=0)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), t.copyOfRange(6, 9), 0f) // (x=2, y=0)
        assertEquals(pad, t[0], 1e-6f)
    }

    @Test
    fun rejectsAnImageThatIsNotTheResizedSize() {
        val wrong = DecodedImage(3, 4, IntArray(12))
        val e = assertThrows(IllegalArgumentException::class.java) {
            buildInputTensor(wrong, lb, 114, InputLayout.NCHW)
        }
        assertTrue(e.message!!.contains("expected 2x4"))
    }
}
