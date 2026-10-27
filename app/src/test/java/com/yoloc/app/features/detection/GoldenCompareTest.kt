package com.yoloc.app.features.detection

import com.yoloc.app.features.dev.GoldenSamples
import com.yoloc.app.features.dev.ReferenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RN: __tests__/detection.math.test.ts (compareToReference) and golden.references.test.ts (the
 * reference files themselves). The pipeline run on the golden images is GoldenDeviceTest.
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

    @Test
    fun referenceFilesAreRealDataWithOneEntryPerBundledGoldenImage() {
        for (source in ReferenceSource.entries) {
            val file = ExpectedFile.parse(TestAssets.debug(source.asset).readText())
            assertNull(source.asset, file.pending)
            assertEquals(source.asset, GoldenSamples.FILES.sorted(), file.images.map { it.file }.sorted())
            for (name in GoldenSamples.FILES) {
                assertTrue(name, TestAssets.debug("${GoldenSamples.GOLDEN_DIR}/$name").exists())
            }
        }
    }

    @Test
    fun referenceFilesUseValidClassesAndNormalizedBoxesInsideTheImage() {
        val config = TestAssets.modelConfig
        for (source in ReferenceSource.entries) {
            for (image in ExpectedFile.parse(TestAssets.debug(source.asset).readText()).images) {
                assertTrue(image.file, image.detections.isNotEmpty())
                for (d in image.detections) {
                    val where = "${source.asset} ${image.file} $d"
                    assertTrue(where, d.classId in config.classes.indices)
                    assertTrue(where, d.confidence > config.confThreshold)
                    assertTrue(where, d.x >= 0 && d.y >= 0 && d.w > 0 && d.h > 0)
                    assertTrue(where, d.x + d.w <= 1.0001 && d.y + d.h <= 1.0001)
                }
            }
        }
    }
}
