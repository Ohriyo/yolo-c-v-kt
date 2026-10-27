package com.yoloc.app.features.export

import com.yoloc.app.db.repositories.SavedDetection
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.ScanSummary
import com.yoloc.app.db.repositories.ScanWithDetections
import com.yoloc.app.features.detection.TestAssets
import com.yoloc.app.features.detection.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * RN: retrainingExport.test.ts, the parts that need no database (label lines, data.yaml, file
 * name). The zip itself is tested against Room in androidTest (RetrainingExportTest).
 */
class RetrainingFormatTest {
    private val config = TestAssets.modelConfig

    private fun detection(classId: Int, x: Double, y: Double, w: Double, h: Double, corrected: Int? = null) =
        SavedDetection("d$classId", "s1", classId, 0.8, x, y, w, h, corrected, corrected?.let { 1L }, corrected ?: classId)

    private fun scan(detections: List<SavedDetection>) = ScanWithDetections(
        ScanSummary(
            id = "s1", plotId = null, plotName = null, modelVersionId = config.id, imagePath = "scans/s1.jpg",
            thumbnailPath = null, imageWidth = 4000, imageHeight = 3000, source = ScanSource.Camera,
            confThreshold = 0.25, iouThreshold = 0.45, inferenceMs = 900, totalPods = detections.size,
            verdict = Verdict.Healthy, lowConfidence = false, latitude = null, longitude = null, notes = null,
            createdAt = 0,
        ),
        detections,
    )

    @Test
    fun writesCenterBasedNormalizedBoxesWithTheCorrectedClass() {
        val labels = yoloLabels(
            scan(listOf(detection(0, 0.1, 0.2, 0.3, 0.4, corrected = 1), detection(2, 0.5, 0.5, 0.2, 0.2))),
        )
        assertEquals("1 0.250000 0.400000 0.300000 0.400000\n2 0.600000 0.600000 0.200000 0.200000", labels)
    }

    @Test
    fun labelsUseADecimalPointOnCommaDecimalPhones() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("2 0.600000 0.600000 0.200000 0.200000", yoloLabels(scan(listOf(detection(2, 0.5, 0.5, 0.2, 0.2)))))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun listsClassesInModelIndexOrder() {
        assertTrue(dataYaml(config).contains("names:\n  0: early_stage\n  1: fully_infected_pod\n  2: healthy_pod"))
        assertTrue(dataYaml(config).endsWith("healthy_pod\n"))
    }

    @Test
    fun namesTheZipByScopeAndLocalDate() {
        val zone = ZoneId.of("Asia/Manila")
        val now = LocalDate.of(2026, 10, 7).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals("yolo-c-retraining-corrected-2026-10-07.zip", retrainingZipName(ExportScope.Corrected, now, zone))
    }

    @Test
    fun manifestIsIndentedLikeJsonStringify() {
        val text = manifestText(ExportScope.All, config, "0.1.0", "2026-10-17T09:00:00.000Z", ExportResult(), emptyList())
        assertTrue(text, text.startsWith("{\n  \"format\": \"yolo-c-retraining-export/1\",\n  \"exportedAt\""))
        assertTrue(text, text.contains("\"classes\": [\n    \"early_stage\","))
        assertTrue(text, text.contains("\"scans\": [],\n  \"pods\": 0,\n  \"correctedPods\": 0,\n  \"skipped\": 0\n}"))
    }

    @Test
    fun blocksTheExportOnlyWhenFreeSpaceIsKnownAndShort() {
        val summary = ExportSummary(scans = 3, bytes = 10L * 1024 * 1024)
        assertFalse(lacksSpaceFor(summary, null))
        assertFalse(lacksSpaceFor(summary, summary.bytes + SPACE_MARGIN_BYTES))
        assertTrue(lacksSpaceFor(summary, summary.bytes + SPACE_MARGIN_BYTES - 1))
    }
}
