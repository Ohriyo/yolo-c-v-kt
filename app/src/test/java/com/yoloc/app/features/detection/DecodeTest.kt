package com.yoloc.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** RN: __tests__/detection.math.test.ts (decodeOutput). */
class DecodeTest {
    // Anchor A: early_stage 0.9 at center (320, 320), 100x200. Anchor B: below threshold.
    private val pixelAnchors = listOf(
        listOf(320.0, 320.0, 100.0, 200.0, 0.9, 0.1, 0.05),
        listOf(100.0, 100.0, 50.0, 50.0, 0.2, 0.1, 0.25),
        listOf(600.0, 40.0, 20.0, 20.0, 0.0, 0.0, 0.3),
    )

    @Test
    fun decodesPixelBoxesToCornersAndKeepsTheBestClassInBothLayouts() {
        for (layout in OutputLayout.entries) {
            val (candidates, boxUnits) = decodeOutput(packOutput(pixelAnchors, layout), specFor(3, layout), 0.25)
            assertEquals(BoxUnits.Pixels, boxUnits)
            // Score 0.25 is not > 0.25, so anchor B is dropped (Ultralytics uses a strict >).
            assertEquals("$layout", 2, candidates.size)
            assertEquals(PixelBox(270.0, 220.0, 370.0, 420.0), candidates[0].box)
            assertEquals(0, candidates[0].classId)
            assertEquals(0.9, candidates[0].score, 0.005)
            assertEquals(2, candidates[1].classId)
        }
    }

    @Test
    fun detectsNormalizedBoxesAndScalesThemToInputPixels() {
        val normalized = pixelAnchors.map { a -> a.take(4).map { it / 640 } + a.drop(4) }
        val (candidates, boxUnits) = decodeOutput(
            packOutput(normalized, OutputLayout.FeaturesFirst),
            specFor(3, OutputLayout.FeaturesFirst),
            0.25,
        )
        assertEquals(BoxUnits.Normalized, boxUnits)
        assertEquals(270.0, candidates[0].box.x1, 1e-3)
        assertEquals(420.0, candidates[0].box.y2, 1e-3)
    }

    @Test
    fun rejectsAnOutputOfTheWrongLength() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            decodeOutput(FloatArray(10), specFor(3, OutputLayout.FeaturesFirst), 0.25)
        }
        assertTrue(e.message!!.contains("expected 21"))
    }
}
