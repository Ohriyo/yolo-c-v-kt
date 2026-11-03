package com.yoloc.app.features.detection

import com.yoloc.app.features.dev.GoldenSamples
import com.yoloc.app.features.dev.ReferenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RN: __tests__/detection.math.test.ts (compareToReference) and golden.references.test.ts (the
 * reference files themselves). The pipeline run on the golden images is YoloDetectorGoldenTest.
 */
class GoldenCompareTest {
    private fun det(classId: Int, confidence: Double, x: Double) = Detection(classId, confidence, x, 0.2, 0.3, 0.5)

    @Test
    fun passesWhenEveryReferenceBoxHasACloseMatch() {
        val report = compareToReference(
            listOf(det(0, 0.81, 0.101), det(2, 0.5, 0.6)),
            listOf(det(2, 0.52, 0.6), det(0, 0.8, 0.1)),
        )
        assertTrue(report.pass)
        assertTrue(report.pairs.all { it.iou > 0.95 })
    }

    @Test
    fun failsOnAWrongClassAShiftedBoxAConfidenceGapOrAnExtraBox() {
        assertFalse(compareToReference(listOf(det(1, 0.8, 0.1)), listOf(det(0, 0.8, 0.1))).pass)
        assertFalse(compareToReference(listOf(det(0, 0.8, 0.2)), listOf(det(0, 0.8, 0.1))).pass)
        assertFalse(compareToReference(listOf(det(0, 0.6, 0.1)), listOf(det(0, 0.8, 0.1))).pass)
        val extra = compareToReference(listOf(det(0, 0.8, 0.1), det(2, 0.4, 0.6)), listOf(det(0, 0.8, 0.1)))
        assertFalse(extra.pass)
        assertEquals(1, extra.extra.size)
    }

    private fun references(source: ReferenceSource) =
        ReferenceImage.parseAll(File("src/androidTest/assets/${source.asset}").readText())

    @Test
    fun referenceFilesHaveOneEntryPerBundledImageAndOneImageWithNothing() {
        for (source in ReferenceSource.entries) {
            val images = references(source)
            assertEquals(source.asset, GoldenSamples.FILES.sorted(), images.map { it.image }.sorted())
            assertEquals(source.asset, 1, images.count { it.detections.isEmpty() })
            for (name in GoldenSamples.FILES) {
                assertTrue(name, File("src/androidTest/assets/${GoldenSamples.GOLDEN_DIR}/$name").exists())
            }
        }
    }

    @Test
    fun referenceFilesUseValidClassesAndNormalizedBoxesInsideTheImage() {
        val config = TestAssets.modelConfig
        for (source in ReferenceSource.entries) {
            for (image in references(source)) {
                assertEquals(image.image, GoldenSamples.REFERENCE_CONF, image.confThreshold, 0.0)
                assertEquals(image.image, config.iouThreshold, image.iouThreshold, 0.0)
                for (b in image.detections) {
                    val where = "${source.asset} ${image.image} $b"
                    assertTrue(where, b.classId in config.classes.indices)
                    assertTrue(where, b.confidence > GoldenSamples.REFERENCE_CONF)
                    assertTrue(where, b.x1 >= 0 && b.y1 >= 0 && b.x2 <= 1 && b.y2 <= 1)
                    assertTrue(where, b.x2 > b.x1 && b.y2 > b.y1)
                }
            }
        }
    }
}
