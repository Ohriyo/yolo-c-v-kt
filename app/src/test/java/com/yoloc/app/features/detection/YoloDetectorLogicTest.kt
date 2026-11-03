package com.yoloc.app.features.detection

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The device-independent parts of [YoloDetector] and its golden comparison. */
class YoloDetectorLogicTest {
    private val config = TestAssets.modelConfig
    private val spec = specFor(3, OutputLayout.FeaturesFirst)

    private fun detection(classId: Int, confidence: Double, x1: Double, y1: Double, x2: Double, y2: Double) =
        Detection(classId, confidence, x1, y1, x2 - x1, y2 - y1)

    @Test
    fun theBundledConfigDeclaresTheConfirmedModelFacts() {
        val modelSpec = config.modelSpec()
        assertEquals(InputLayout.NCHW, modelSpec.inputLayout)
        assertEquals(OutputLayout.FeaturesFirst, modelSpec.outputLayout)
        assertEquals(8400, modelSpec.numAnchors)
        assertEquals(3, modelSpec.numClasses)
        assertEquals(114, config.letterboxPad)
        assertEquals(0.45, config.iouThreshold, 0.0)
        assertArrayEquals(doubleArrayOf(0.5, 0.4, 0.5), config.confThresholds, 0.0)
    }

    @Test
    fun aMissingThresholdOrMismatchedLabelsFailAtLoad() {
        val text = TestAssets.main(ModelConfig.ASSET_PATH).readText()
        val labels = TestAssets.main(ModelConfig.LABELS_ASSET).readText()
        assertThrows(IllegalArgumentException::class.java) { ModelConfig.parse(text, "healthy_pod\n") }
        assertThrows(IllegalArgumentException::class.java) {
            ModelConfig.parse(text.replace("\"early_stage\": 0.5,", ""), labels)
        }
    }

    @Test
    fun standardIsExactlyTheConfigsThresholds() {
        assertArrayEquals(config.confThresholds, config.confThresholdsFor(DetectionSensitivity.Standard), 0.0)
    }

    @Test
    fun strictAndSensitiveMoveEveryClassTogetherWithoutFloatNoise() {
        assertArrayEquals(doubleArrayOf(0.65, 0.55, 0.65), config.confThresholdsFor(DetectionSensitivity.Strict), 0.0)
        assertArrayEquals(doubleArrayOf(0.35, 0.25, 0.35), config.confThresholdsFor(DetectionSensitivity.Sensitive), 0.0)
    }

    @Test
    fun noClassGoesBelowTheMinimumHoweverFarSensitiveMoves() {
        val wide = config.copy(sensitivity = config.sensitivity.copy(shift = 0.4))
        assertArrayEquals(doubleArrayOf(0.25, 0.25, 0.25), wide.confThresholdsFor(DetectionSensitivity.Sensitive), 0.0)
    }

    @Test
    fun whatIsStoredOnAScanIsKeyedByClass() {
        assertEquals(
            mapOf("early_stage" to 0.35, "fully_infected_pod" to 0.25, "healthy_pod" to 0.35),
            config.confThresholdsByClass(DetectionSensitivity.Sensitive),
        )
    }

    @Test
    fun eachClassUsesItsOwnThreshold() {
        // Three anchors whose best scores are all 0.45: only class 1's threshold (0.4) lets it pass.
        val output = packOutput(
            listOf(
                listOf(0.5, 0.5, 0.2, 0.2, 0.45, 0.0, 0.0),
                listOf(0.5, 0.5, 0.2, 0.2, 0.0, 0.45, 0.0),
                listOf(0.5, 0.5, 0.2, 0.2, 0.0, 0.0, 0.45),
            ),
            OutputLayout.FeaturesFirst,
        )
        val result = decodeOutput(output, spec, doubleArrayOf(0.5, 0.4, 0.5))
        assertEquals(listOf(1), result.candidates.map { it.classId })
    }

    @Test
    fun readsTheOutputAsChannelsNotRows() {
        val output = FloatArray(7 * 3)
        // Anchor 2: index is c * 3 + 2.
        listOf(0.5f, 0.25f, 0.5f, 0.25f, 0f, 0f, 0.9f).forEachIndexed { c, v -> output[c * 3 + 2] = v }
        val only = decodeOutput(output, spec, doubleArrayOf(0.5, 0.5, 0.5)).candidates.single()
        assertEquals(2, only.classId)
        assertEquals(0.25 * 640, only.box.x1, 1e-3)
        assertEquals(0.125 * 640, only.box.y1, 1e-3)
    }

    @Test
    fun undoesTheAppliedPaddingOnAPortraitImage() {
        // 452x678 scales to 427x640 with 106 px of padding on the left.
        val lb = computeLetterbox(452, 678, 640)
        assertEquals(427, lb.resizedWidth)
        assertEquals(106, lb.padLeft)
        assertEquals(0, lb.padTop)
        val box = toNormalizedOriginal(PixelBox(106.0, 0.0, 106.0 + 0.5 * 452 * lb.scale, 320.0), lb)
        assertEquals(0.0, box.x1, 1e-9)
        assertEquals(0.0, box.y1, 1e-9)
        assertEquals(0.5, box.x2, 1e-9)
        assertEquals(0.5, box.y2, 1e-9)
    }

    @Test
    fun clampsToTheImageAndDropsBoxesOutsideIt() {
        val lb = computeLetterbox(100, 200, 640) // 320 px of padding in total: 160 on the left.
        val output = packOutput(
            listOf(
                // Reaches past the left edge of the image, into the padding.
                listOf(0.25, 0.5, 0.1, 0.2, 0.9, 0.0, 0.0),
                // Entirely inside the left padding: nothing left after clamping.
                listOf(0.1, 0.5, 0.05, 0.2, 0.0, 0.9, 0.0),
                listOf(0.5, 0.5, 0.1, 0.1, 0.0, 0.0, 0.1),
            ),
            OutputLayout.FeaturesFirst,
        )
        val result = postprocessByClass(output, spec, lb, doubleArrayOf(0.5, 0.5, 0.5), 0.45)
        val only = result.detections.single()
        assertEquals(0, only.classId)
        assertEquals(0.0, only.x, 1e-9)
        assertTrue(only.x + only.w <= 1.0)
    }

    @Test
    fun nmsKeepsOverlapsAcrossClassesButNotWithinOne() {
        val lb = computeLetterbox(640, 640, 640)
        val output = packOutput(
            listOf(
                listOf(0.5, 0.5, 0.2, 0.2, 0.9, 0.0, 0.0),
                listOf(0.5, 0.5, 0.2, 0.2, 0.8, 0.0, 0.0),
                listOf(0.5, 0.5, 0.2, 0.2, 0.0, 0.7, 0.0),
            ),
            OutputLayout.FeaturesFirst,
        )
        val result = postprocessByClass(output, spec, lb, doubleArrayOf(0.25, 0.25, 0.25), 0.45)
        assertEquals(listOf(0 to 0.9, 1 to 0.7), result.detections.map { it.classId to round2(it.confidence) })
    }

    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    @Test
    fun fallsBackToTheNextOptionAndReportsTheFailure() {
        val failed = mutableListOf<String>()
        val opened = openFirstWorking(listOf("gpu", "cpu"), { option, _ -> failed += option }) { option ->
            check(option != "gpu") { "no GPU" }
            option
        }
        assertEquals("cpu", opened)
        assertEquals(listOf("gpu"), failed)
    }

    @Test
    fun rethrowsWhenNoOptionWorks() {
        val error = assertThrows(IllegalStateException::class.java) {
            openFirstWorking<String, String>(listOf("gpu", "cpu"), { _, _ -> }) { error("$it failed") }
        }
        assertEquals("cpu failed", error.message)
        assertEquals("gpu failed", error.suppressed.single().message)
    }

    private val reference = ReferenceBox(0, 0.9, 0.1, 0.1, 0.5, 0.5)

    @Test
    fun comparisonPassesInsideEveryTolerance() {
        val actual = listOf(detection(0, 0.86, 0.115, 0.085, 0.515, 0.485))
        assertTrue(compareToReferenceBoxes(actual, listOf(reference)).pass)
    }

    @Test
    fun comparisonFailsOnEachCriterion() {
        fun passes(vararg actual: Detection) = compareToReferenceBoxes(actual.toList(), listOf(reference)).pass
        assertFalse("confidence", passes(detection(0, 0.84, 0.1, 0.1, 0.5, 0.5)))
        assertFalse("coordinate", passes(detection(0, 0.9, 0.1, 0.1, 0.525, 0.5)))
        assertFalse("class", passes(detection(1, 0.9, 0.1, 0.1, 0.5, 0.5)))
        assertFalse("missing", passes())
        assertFalse("extra", passes(detection(0, 0.9, 0.1, 0.1, 0.5, 0.5), detection(0, 0.3, 0.7, 0.7, 0.9, 0.9)))
    }

    @Test
    fun anImageExpectedToBeEmptyPassesOnlyWhenEmpty() {
        assertTrue(compareToReferenceBoxes(emptyList(), emptyList()).pass)
        assertFalse(compareToReferenceBoxes(listOf(detection(0, 0.9, 0.1, 0.1, 0.5, 0.5)), emptyList()).pass)
    }
}
