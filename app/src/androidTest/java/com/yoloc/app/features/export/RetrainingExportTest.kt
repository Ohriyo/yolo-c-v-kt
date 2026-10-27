package com.yoloc.app.features.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.saveScan
import com.yoloc.app.util.uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** RN: retrainingExport.test.ts, with the zip written into memory against the real Room database. */
@RunWith(AndroidJUnit4::class)
class RetrainingExportTest {
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val classes = ClassRepository(holder)
    private val files = MemoryFileStore()
    private val config = TestDb.config

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() = holder.close()

    private fun pod(classId: Int, x: Double = 0.1, y: Double = 0.2, w: Double = 0.3, h: Double = 0.4) =
        NewDetection(classId, 0.8, x, y, w, h)

    private suspend fun save(detections: List<NewDetection>, plotId: String? = null, notes: String? = null) = saveScan(
        scans,
        files,
        SaveScanInput(
            sourceImage = File("/cache/${uuid()}.jpg"),
            plotId = plotId,
            modelVersionId = config.id,
            imageWidth = 4000,
            imageHeight = 3000,
            source = ScanSource.Camera,
            confThreshold = 0.25,
            iouThreshold = 0.45,
            notes = notes,
            createdAt = NOW,
            detections = detections,
        ),
        0.4,
    )

    private suspend fun correct(scanId: String, modelClass: Int, to: Int) {
        val detection = scans.getScan(scanId)!!.detections.first { it.classId == modelClass }
        scans.correctDetection(detection.id, to)
    }

    private class Unzipped(val result: ExportResult, val entries: Map<String, ByteArray>, val methods: Map<String, Int>) {
        fun text(name: String) = entries.getValue(name).decodeToString()
    }

    /** Runs the export into memory and unzips it. */
    private suspend fun exportZip(scope: ExportScope): Unzipped {
        val out = ByteArrayOutputStream()
        val result = writeRetrainingZip(scans, classes, files, out, scope, config, "0.1.0", NOW)
        val entries = mutableMapOf<String, ByteArray>()
        val methods = mutableMapOf<String, Int>()
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                methods[entry.name] = entry.method
            }
        }
        return Unzipped(result, entries, methods)
    }

    @Test
    fun writesTheCorrectedClassIntoTheLabelFile() = runBlocking {
        val id = save(listOf(pod(EARLY, 0.1, 0.2, 0.3, 0.4), pod(HEALTHY, 0.5, 0.5, 0.2, 0.2)))
        correct(id, EARLY, INFECTED)
        val lines = yoloLabels(scans.getScan(id)!!).split("\n").sorted()
        assertEquals(listOf("1 0.250000 0.400000 0.300000 0.400000", "2 0.600000 0.600000 0.200000 0.200000"), lines)
    }

    @Test
    fun exportsOnlyCorrectedScansWithPhotosLabelsAndManifest() = runBlocking {
        val plot = plots.createPlot("North")
        val corrected = save(listOf(pod(HEALTHY), pod(EARLY, 0.6, 0.1, 0.2, 0.3)), plotId = plot.id, notes = "Tree 7")
        save(listOf(pod(HEALTHY))) // never corrected: not in this scope
        correct(corrected, HEALTHY, EARLY)

        val zip = exportZip(ExportScope.Corrected)
        assertEquals(ExportResult(scans = 1, pods = 2, correctedPods = 1, skipped = 0), zip.result)
        assertEquals(
            listOf("README.txt", "data.yaml", "images/$corrected.jpg", "labels/$corrected.txt", "manifest.json"),
            zip.entries.keys.sorted(),
        )
        // Photos go in byte for byte, uncompressed; text is deflated.
        assertTrue(zip.text("images/$corrected.jpg").startsWith("jpeg:/cache/"))
        assertEquals(ZipEntry.STORED, zip.methods["images/$corrected.jpg"])
        assertEquals(ZipEntry.DEFLATED, zip.methods["manifest.json"])

        val manifest = Json.parseToJsonElement(zip.text("manifest.json")).jsonObject
        assertEquals("yolo-c-retraining-export/1", manifest["format"]!!.jsonPrimitive.content)
        assertEquals("corrected", manifest["scope"]!!.jsonPrimitive.content)
        assertEquals(config.classes, manifest["classes"]!!.jsonArray.map { it.jsonPrimitive.content })
        val scan = manifest["scans"]!!.jsonArray.single().jsonObject
        assertEquals(corrected, scan["id"]!!.jsonPrimitive.content)
        assertEquals("North", scan["plot"]!!.jsonPrimitive.content)
        assertEquals("Tree 7", scan["notes"]!!.jsonPrimitive.content)
        assertEquals(4000, scan["imageWidth"]!!.jsonPrimitive.int)
        assertEquals("null", scan["location"].toString())
        assertEquals("0.25", scan["confThreshold"].toString()) // JSON.stringify form
        val dets = scan["detections"]!!.jsonArray.map { it.jsonObject }
        assertTrue(
            dets.any {
                it["modelClassId"]!!.jsonPrimitive.int == HEALTHY && it["correctedClassId"]!!.jsonPrimitive.intOrNull == EARLY
            },
        )
        assertTrue(zip.text("README.txt").contains("not checked by a person"))
    }

    @Test
    fun selectsEarlyStageScansAfterCorrectionsAndAllScans() = runBlocking {
        val early = save(listOf(pod(EARLY)))
        save(listOf(pod(HEALTHY)))
        val becameEarly = save(listOf(pod(HEALTHY)))
        correct(becameEarly, HEALTHY, EARLY)

        val earlyImages = exportZip(ExportScope.Early).entries.keys.filter { it.startsWith("images/") }.sorted()
        assertEquals(listOf("images/$becameEarly.jpg", "images/$early.jpg").sorted(), earlyImages)
        assertEquals(3, exportZip(ExportScope.All).result.scans)
    }

    @Test
    fun skipsScansWhosePhotoFileIsGoneAndSaysSo() = runBlocking {
        val id = save(listOf(pod(EARLY)))
        files.files.remove("scans/$id.jpg")
        val zip = exportZip(ExportScope.All)
        assertEquals(0, zip.result.scans)
        assertEquals(1, zip.result.skipped)
        assertNull(zip.entries["labels/$id.txt"])
    }

    @Test
    fun countsScansAndEstimatesSizePerScope() = runBlocking {
        save(listOf(pod(EARLY)))
        save(listOf(pod(HEALTHY)))
        val all = summarizeExport(scans, classes, files, ExportScope.All)
        assertEquals(2, all.scans)
        assertTrue(all.bytes > 0)
        assertEquals(ExportSummary(0, 0), summarizeExport(scans, classes, files, ExportScope.Corrected))
    }

    private companion object {
        // Class ids follow model.config.json order.
        const val EARLY = 0
        const val INFECTED = 1
        const val HEALTHY = 2
        const val NOW = 1_792_227_600_000L // 2026-10-17T09:00:00Z
    }
}
