package com.yoloc.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Test

/** RN: __tests__/detection.math.test.ts (postprocess, end to end on a synthetic output). */
class PostprocessTest {
    @Test
    fun mapsAModelBoxBackToNormalizedCoordinatesOnTheOriginalPhoto() {
        // A 365x547 photo (sample_04): scale 640/547, 106 px padding on the left.
        val lb = computeLetterbox(365, 547, 640)
        // A pod covering original pixels x 50..250, y 100..500, expressed in input pixels.
        val inp = toInputPixels(PixelBox(50.0, 100.0, 250.0, 500.0), lb)
        val cx = (inp.x1 + inp.x2) / 2
        val cy = (inp.y1 + inp.y2) / 2
        val w = inp.x2 - inp.x1
        val h = inp.y2 - inp.y1
        val anchors = listOf(
            listOf(cx, cy, w, h, 0.05, 0.85, 0.1), // fully_infected_pod
            listOf(cx + 2, cy, w, h, 0.05, 0.6, 0.1), // duplicate, suppressed by NMS
            listOf(cx, cy, w, h, 0.1, 0.05, 0.3), // healthy at the same place: other class, kept
        )
        val result = postprocess(
            packOutput(anchors, OutputLayout.FeaturesFirst),
            specFor(3, OutputLayout.FeaturesFirst),
            lb,
            Thresholds(confThreshold = 0.25, iouThreshold = 0.45),
        )
        assertEquals(BoxUnits.Pixels, result.boxUnits)
        assertEquals(3, result.candidateCount)
        assertEquals(listOf(1, 2), result.detections.map { it.classId })
        val d = result.detections[0]
        assertEquals(0.85, d.confidence, 0.005)
        assertEquals(50.0 / 365, d.x, 5e-6)
        assertEquals(100.0 / 547, d.y, 5e-6)
        assertEquals(200.0 / 365, d.w, 5e-6)
        assertEquals(400.0 / 547, d.h, 5e-6)
    }
}
