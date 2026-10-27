package com.yoloc.app.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.PlotFilter
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanCursor
import com.yoloc.app.db.repositories.ScanFilters
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.ThumbnailBackfill
import com.yoloc.app.features.scans.saveScan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * RN: __tests__/db.scans.test.ts, against the real Room database (in memory) on the phone.
 * The dev fake-scan seeder is in SeedFakeScansTest, saveResult in ScanFlowTest.
 */
@RunWith(AndroidJUnit4::class)
class ScanRepositoryTest {
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val files = MemoryFileStore()
    private val config = TestDb.config

    private val base = SaveScanInput(
        sourceImage = File("/cache/photo.jpg"),
        modelVersionId = config.id,
        imageWidth = 3072,
        imageHeight = 3072,
        source = ScanSource.Camera,
        confThreshold = 0.25,
        iouThreshold = 0.45,
        inferenceMs = 900,
        createdAt = NOW,
        detections = listOf(pod(HEALTHY)),
    )

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() = holder.close()

    private suspend fun save(input: SaveScanInput = base) = saveScan(scans, files, input, config.lowConfidenceThreshold)

    private suspend fun rowCount(table: String) = holder.get().rowCount(table)

    private suspend fun assertFailsWith(text: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            assertTrue("'${e.message}' should contain '$text'", e.message.orEmpty().contains(text))
            return
        }
        fail("Expected a failure containing '$text'")
    }

    // saveScan (atomic)

    @Test
    fun copiesTheImageAndWritesTheScanWithItsDetections() = runBlocking {
        val id = save(base.copy(detections = listOf(pod(HEALTHY, 0.8), pod(EARLY, 0.7))))
        assertEquals("/cache/photo.jpg", files.files["scans/$id.jpg"])
        val scan = scans.getScan(id)!!
        assertEquals("scans/$id.jpg", scan.scan.imagePath)
        assertEquals(Verdict.EarlyStage, scan.scan.verdict)
        assertEquals(2, scan.scan.totalPods)
        assertFalse(scan.scan.lowConfidence)
        assertEquals(NOW, scan.scan.createdAt)
        assertEquals(listOf(HEALTHY to 0.8, EARLY to 0.7), scan.detections.map { it.classId to it.confidence })
    }

    @Test
    fun derivesTheVerdictFromTheMostSevereClassAndFlagsLowConfidence() = runBlocking {
        val infected = save(base.copy(detections = listOf(pod(HEALTHY), pod(INFECTED), pod(EARLY))))
        val uncertain = save(base.copy(detections = listOf(pod(HEALTHY, 0.3), pod(HEALTHY, 0.35))))
        val empty = save(base.copy(detections = emptyList()))

        scans.getScan(infected)!!.scan.let {
            assertEquals(Verdict.Infected, it.verdict)
            assertEquals(3, it.totalPods)
        }
        scans.getScan(uncertain)!!.scan.let {
            assertEquals(Verdict.Healthy, it.verdict)
            assertTrue(it.lowConfidence)
        }
        // No pods is NoPod, never Healthy.
        scans.getScan(empty)!!.scan.let {
            assertEquals(Verdict.NoPod, it.verdict)
            assertEquals(0, it.totalPods)
        }
    }

    @Test
    fun rollsBackTheScanRowAndDeletesTheCopiedImageWhenADetectionInsertFails() = runBlocking {
        // Make the second detection insert fail after the scan row was already written.
        holder.get().exec(
            """CREATE TRIGGER fail_second_detection BEFORE INSERT ON detections
               WHEN (SELECT COUNT(*) FROM detections WHERE scan_id = NEW.scan_id) >= 1
               BEGIN SELECT RAISE(ABORT, 'simulated failure'); END;""",
        )
        assertFailsWith("simulated failure") { save(base.copy(detections = listOf(pod(HEALTHY), pod(EARLY)))) }
        assertEquals(0, rowCount("scans"))
        assertEquals(0, rowCount("detections"))
        assertTrue(files.files.isEmpty())
        // Both the copied photo and its thumbnail are cleaned up.
        assertEquals(2, files.removed.size)
        assertTrue(files.removed.any { it.endsWith("_thumb.jpg") })
    }

    @Test
    fun makesAListThumbnailNextToThePhotoAndRecordsIt() = runBlocking {
        val id = save()
        assertEquals("thumb:/cache/photo.jpg", files.files["scans/${id}_thumb.jpg"])
        assertEquals("scans/${id}_thumb.jpg", scans.getScan(id)!!.scan.thumbnailPath)
    }

    @Test
    fun stillSavesWhenTheThumbnailFailsLeavingItForTheBackfill() = runBlocking {
        files.failNextThumbnail = true
        val id = save()
        assertNull(scans.getScan(id)!!.scan.thumbnailPath)

        val backfill = ThumbnailBackfill(scans, files)
        assertEquals(1, backfill.run())
        assertEquals("scans/${id}_thumb.jpg", scans.getScan(id)!!.scan.thumbnailPath)
        // Nothing left to do on the next run.
        assertEquals(0, backfill.run())
    }

    @Test
    fun writesNothingAndRemovesTheFileForAnUnknownClass() = runBlocking {
        assertFailsWith("Unknown class id 99") { save(base.copy(detections = listOf(pod(99)))) }
        assertEquals(0, rowCount("scans"))
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun cleansUpAPartialFileWhenTheImageCopyFails() = runBlocking {
        files.failNextCopy = true
        assertFailsWith("disk full") { save() }
        assertEquals(0, rowCount("scans"))
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun rejectsBoxesThatAreNotNormalized() = runBlocking {
        assertFailsWith("normalized") { save(base.copy(detections = listOf(pod(HEALTHY).copy(x = 120.0, w = 300.0)))) }
        assertEquals(0, rowCount("scans"))
        assertTrue(files.files.isEmpty())
    }

    // listScans

    private suspend fun seedList(): Map<String, String> {
        val north = plots.createPlot("North 100% block", id = "plot-north")
        val river = plots.createPlot("River", id = "plot-river")
        return mapOf(
            "healthyToday" to save(base.copy(detections = listOf(pod(HEALTHY)), plotId = north.id, notes = "Row 4")),
            "earlyYesterday" to save(
                base.copy(detections = listOf(pod(HEALTHY), pod(EARLY)), plotId = river.id, createdAt = NOW - DAY),
            ),
            "infectedLastWeek" to save(
                base.copy(detections = listOf(pod(INFECTED)), createdAt = NOW - 7 * DAY, notes = "near the creek"),
            ),
            "noPodLastMonth" to save(base.copy(detections = emptyList(), createdAt = NOW - 40 * DAY)),
        )
    }

    private suspend fun listIds(filters: ScanFilters) = scans.listScans(filters).map { it.id }

    @Test
    fun listsNewestFirst() = runBlocking {
        val ids = seedList()
        assertEquals(
            listOf(ids["healthyToday"], ids["earlyYesterday"], ids["infectedLastWeek"], ids["noPodLastMonth"]),
            listIds(ScanFilters()),
        )
    }

    @Test
    fun filtersByVerdict() = runBlocking {
        val ids = seedList()
        assertEquals(
            listOf(ids["earlyYesterday"], ids["infectedLastWeek"]),
            listIds(ScanFilters(verdicts = listOf(Verdict.Infected, Verdict.EarlyStage))),
        )
    }

    @Test
    fun filtersByClassPresentUsingCorrectedLabels() = runBlocking {
        val ids = seedList()
        assertEquals(listOf(ids["earlyYesterday"]), listIds(ScanFilters(classIds = listOf(EARLY))))

        val scan = scans.getScan(ids.getValue("healthyToday"))!!
        scans.correctDetection(scan.detections[0].id, EARLY)
        assertEquals(listOf(ids["healthyToday"], ids["earlyYesterday"]), listIds(ScanFilters(classIds = listOf(EARLY))))
    }

    @Test
    fun filtersByPlotIncludingScansWithoutAPlot() = runBlocking {
        val ids = seedList()
        assertEquals(listOf(ids["earlyYesterday"]), listIds(ScanFilters(plot = PlotFilter.Id("plot-river"))))
        assertEquals(listOf(ids["infectedLastWeek"], ids["noPodLastMonth"]), listIds(ScanFilters(plot = PlotFilter.None)))
    }

    @Test
    fun filtersByDateRangeInclusive() = runBlocking {
        val ids = seedList()
        assertEquals(
            listOf(ids["earlyYesterday"], ids["infectedLastWeek"]),
            listIds(ScanFilters(from = NOW - 7 * DAY, to = NOW - DAY)),
        )
    }

    @Test
    fun searchesNotesAndPlotNamesTreatingPercentLiterally() = runBlocking {
        val ids = seedList()
        assertEquals(listOf(ids["infectedLastWeek"]), listIds(ScanFilters(search = "CREEK")))
        assertEquals(listOf(ids["earlyYesterday"]), listIds(ScanFilters(search = "river")))
        assertEquals(listOf(ids["healthyToday"]), listIds(ScanFilters(search = "100%")))
        assertEquals(listOf(ids["healthyToday"]), listIds(ScanFilters(search = "%")))
    }

    @Test
    fun pagesWithLimitAndOffset() = runBlocking {
        val ids = seedList()
        assertEquals(listOf(ids["healthyToday"], ids["earlyYesterday"]), listIds(ScanFilters(limit = 2)))
        assertEquals(listOf(ids["infectedLastWeek"], ids["noPodLastMonth"]), listIds(ScanFilters(limit = 2, offset = 2)))
    }

    @Test
    fun pagesWithAKeysetCursorThatSurvivesDeletesAboveIt() = runBlocking {
        val ids = seedList()
        val last = scans.listScans(ScanFilters(limit = 2)).last()
        // Deleting a scan already shown must not skip or repeat one on the next page.
        scans.softDeleteScan(ids.getValue("healthyToday"))
        assertEquals(
            listOf(ids["infectedLastWeek"], ids["noPodLastMonth"]),
            listIds(ScanFilters(after = ScanCursor(last.createdAt, last.id), limit = 2)),
        )
    }

    @Test
    fun ordersScansSavedInTheSameMillisecondByIdSoTheCursorNeverSkipsOne() = runBlocking {
        seedList()
        val a = save(base.copy(createdAt = NOW + DAY))
        val b = save(base.copy(createdAt = NOW + DAY))
        val (x, y) = listOf(a, b).sorted()
        assertEquals(listOf(x), listIds(ScanFilters(limit = 1)))
        assertEquals(listOf(y), listIds(ScanFilters(after = ScanCursor(NOW + DAY, x), limit = 1)))
    }

    @Test
    fun countsMatchingScansIgnoringPaging() = runBlocking {
        seedList()
        assertEquals(4, scans.countScans())
        assertEquals(2, scans.countScans(ScanFilters(verdicts = listOf(Verdict.Infected, Verdict.EarlyStage), limit = 1)))
        assertEquals(1, scans.countScans(ScanFilters(search = "river")))
    }

    @Test
    fun includesThePlotNameInSummaries() = runBlocking {
        seedList()
        val first = scans.listScans(ScanFilters(limit = 1)).single()
        assertEquals("plot-north", first.plotId)
        assertEquals("North 100% block", first.plotName)
    }

    // softDeleteScan, restoreScan, updateScanNotes

    @Test
    fun softDeleteHidesTheScanFromGetListsAndStatsButKeepsTheRow() = runBlocking {
        val keep = save()
        val gone = save(base.copy(detections = listOf(pod(INFECTED))))

        assertTrue(scans.softDeleteScan(gone, NOW))
        assertNull(scans.getScan(gone))
        assertEquals(listOf(keep), listIds(ScanFilters()))
        assertEquals(0, scans.getStats(0).scansByVerdict[Verdict.Infected])
        assertEquals(2, rowCount("scans"))
    }

    @Test
    fun softDeleteReturnsFalseForAnUnknownOrAlreadyDeletedScan() = runBlocking {
        val id = save()
        scans.softDeleteScan(id)
        assertFalse(scans.softDeleteScan(id))
        assertFalse(scans.softDeleteScan("nope"))
    }

    @Test
    fun restoreUndoesASoftDeleteOnce() = runBlocking {
        val id = save()
        scans.softDeleteScan(id)
        assertTrue(scans.restoreScan(id))
        assertNotNull(scans.getScan(id))
        assertFalse(scans.restoreScan(id))
    }

    @Test
    fun updateNotesTrimsStoresBlankAsNullAndRefusesDeletedScans() = runBlocking {
        val id = save(base.copy(notes = "old"))
        scans.updateScanNotes(id, "  Tree 12, row 3  ")
        assertEquals("Tree 12, row 3", scans.getScan(id)!!.scan.notes)
        scans.updateScanNotes(id, "   ")
        assertNull(scans.getScan(id)!!.scan.notes)

        scans.softDeleteScan(id)
        assertFailsWith("not found") { scans.updateScanNotes(id, "x") }
    }

    // getStats, correctDetection

    @Test
    fun countsScansByVerdictAndPodsByClassSinceADateAfterCorrections() = runBlocking {
        save(base.copy(detections = listOf(pod(HEALTHY), pod(HEALTHY))))
        val early = save(base.copy(detections = listOf(pod(EARLY), pod(HEALTHY)), createdAt = NOW - 5 * DAY))
        save(base.copy(detections = emptyList()))
        save(base.copy(detections = listOf(pod(INFECTED)), createdAt = NOW - 45 * DAY)) // outside 30 days

        // The user says the early pod is actually infected.
        val earlyPod = scans.getScan(early)!!.detections.first { it.classId == EARLY }
        scans.correctDetection(earlyPod.id, INFECTED)

        val stats = scans.getStats(NOW - 30 * DAY)
        assertEquals(3, stats.totalScans)
        assertEquals(
            mapOf(Verdict.Healthy to 1, Verdict.EarlyStage to 0, Verdict.Infected to 1, Verdict.NoPod to 1),
            stats.scansByVerdict,
        )
        assertEquals(mapOf("healthy_pod" to 3, "fully_infected_pod" to 1), stats.podsByClass)
    }

    @Test
    fun correctionUpdatesTheVerdictAndClearsWhenSetBack() = runBlocking {
        val id = save(base.copy(detections = listOf(pod(EARLY))))
        val detectionId = scans.getScan(id)!!.detections[0].id

        scans.correctDetection(detectionId, HEALTHY, NOW)
        var scan = scans.getScan(id)!!
        assertEquals(Verdict.Healthy, scan.scan.verdict)
        scan.detections[0].let {
            assertEquals(EARLY, it.classId)
            assertEquals(HEALTHY, it.correctedClassId)
            assertEquals(NOW, it.correctedAt)
            assertEquals(HEALTHY, it.effectiveClassId)
        }

        scans.correctDetection(detectionId, EARLY)
        scan = scans.getScan(id)!!
        assertEquals(Verdict.EarlyStage, scan.scan.verdict)
        assertNull(scan.detections[0].correctedClassId)
        assertNull(scan.detections[0].correctedAt)
    }

    @Test
    fun correctionThrowsForAnUnknownDetection() = runBlocking {
        assertFailsWith("not found") { scans.correctDetection("nope", HEALTHY) }
    }

    // exportScansCsv

    @Test
    fun csvHasAHeaderPerClassCountsAndEscapesRiskyCells() = runBlocking {
        plots.createPlot("Block \"A\", east", id = "p1")
        save(
            base.copy(
                detections = listOf(pod(EARLY), pod(HEALTHY), pod(HEALTHY)),
                plotId = "p1",
                notes = "=HYPERLINK(\"x\")",
                latitude = -1.25,
            ),
        )
        val (header, row, end) = scans.exportScansCsv().split("\r\n")
        assertEquals(
            "id,created_at,verdict,total_pods,early_stage_count,fully_infected_pod_count,healthy_pod_count," +
                "low_confidence,plot,source,model_version,inference_ms,latitude,longitude,notes",
            header,
        )
        assertTrue(row, row.contains(",early_stage,3,1,0,2,0,\"Block \"\"A\"\", east\",camera,"))
        assertTrue(row, row.contains(",-1.25,,"))
        // JS toISOString(): always milliseconds and a Z.
        assertTrue(row, row.contains(",2026-10-15T12:00:00.000Z,"))
        // Formula-looking text is defused for spreadsheets.
        assertTrue(row, row.endsWith(",\"'=HYPERLINK(\"\"x\"\")\""))
        assertEquals("", end)
    }

    // createScan with corrections made before saving

    @Test
    fun storesACorrectionMadeBeforeSavingAndBasesTheVerdictOnIt() = runBlocking {
        val id = save(base.copy(detections = listOf(pod(EARLY, 0.9).copy(correctedClassId = INFECTED), pod(HEALTHY, 0.8))))
        val scan = scans.getScan(id)!!
        assertEquals(Verdict.Infected, scan.scan.verdict)
        val corrected = scan.detections.first { it.classId == EARLY }
        assertEquals(INFECTED, corrected.correctedClassId)
        assertEquals(NOW, corrected.correctedAt)
    }

    @Test
    fun ignoresACorrectionToTheModelsOwnClass() = runBlocking {
        val id = save(base.copy(detections = listOf(pod(EARLY).copy(correctedClassId = EARLY))))
        val scan = scans.getScan(id)!!
        assertNull(scan.detections[0].correctedClassId)
        assertNull(scan.detections[0].correctedAt)
        assertEquals(Verdict.EarlyStage, scan.scan.verdict)
    }

    @Test
    fun rejectsAnUnknownCorrectedClassAndWritesNothing() = runBlocking {
        assertFailsWith("Unknown class id 42") { save(base.copy(detections = listOf(pod(EARLY).copy(correctedClassId = 42)))) }
        assertEquals(0, rowCount("scans"))
        assertTrue(files.files.isEmpty())
    }

    private companion object {
        // Class ids follow model.config.json order.
        const val EARLY = 0
        const val INFECTED = 1
        const val HEALTHY = 2
        const val DAY = 24 * 60 * 60 * 1000L
        val NOW = LocalDateTime.of(2026, 10, 15, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        fun pod(classId: Int, confidence: Double = 0.9) = NewDetection(classId, confidence, 0.1, 0.2, 0.3, 0.4)
    }
}
