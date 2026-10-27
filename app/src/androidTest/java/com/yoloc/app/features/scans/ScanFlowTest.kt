package com.yoloc.app.features.scans

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.R
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.features.detection.ModelLoader
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.dev.GoldenSamples
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * RN: scan.logic.test.ts (verdictTitle), db.scans.test.ts and settings.logic.test.ts (saveResult),
 * plus analyzePhoto on a golden image, which RN only exercised through the screens.
 */
@RunWith(AndroidJUnit4::class)
class ScanFlowTest {
    private val context = TestDb.context
    private val config = TestDb.config
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val files = MemoryFileStore()

    @After
    fun close() = holder.close()

    @Test
    fun verdictTitleStatesCountsForTheVerdictClass() {
        val res = context.resources
        assertEquals("Early-stage signs detected in 2 of 5 pods", verdictTitle(res, Verdict.EarlyStage, 5, 2))
        assertEquals("Advanced infection detected in 1 of 1 pod", verdictTitle(res, Verdict.Infected, 1, 1))
        assertEquals("No signs of Frosty Pod Rot on 3 pods", verdictTitle(res, Verdict.Healthy, 3, 3))
        assertEquals("No signs of Frosty Pod Rot on this pod", verdictTitle(res, Verdict.Healthy, 1, 1))
        assertEquals(context.getString(R.string.verdict_no_pod_title), verdictTitle(res, Verdict.NoPod, 0, 0))
    }

    private fun result(
        photo: PendingPhoto,
        detections: List<ResultDetection>,
        verdict: Verdict,
        confThreshold: Double = 0.25,
        iouThreshold: Double = 0.45,
        inferenceMs: Int = 1,
    ) = ScanResult(
        photo = photo,
        imageWidth = photo.width,
        imageHeight = photo.height,
        detections = detections,
        verdict = verdict,
        totalPods = detections.size,
        lowConfidence = false,
        modelVersionId = config.id,
        confThreshold = confThreshold,
        iouThreshold = iouThreshold,
        inferenceMs = inferenceMs,
        totalMs = 1,
        analyzedAt = 0,
    )

    @Test
    fun savesTheUnsavedResultWithPlotTrimmedNotesAndCorrections() = runBlocking {
        PlotRepository(holder).createPlot("North", id = "p-north")
        val photo = PendingPhoto(File("/cache/Camera/a.jpg"), 4000, 3000, ScanSource.Camera)
        val r = result(
            photo,
            listOf(
                ResultDetection(HEALTHY, 0.7, 0.1, 0.2, 0.3, 0.4, correctedClassId = EARLY),
                ResultDetection(HEALTHY, 0.6, 0.1, 0.2, 0.3, 0.4, correctedClassId = null),
            ),
            Verdict.EarlyStage,
            confThreshold = 0.3,
            iouThreshold = 0.5,
            inferenceMs = 1400,
        )
        val id = saveResult(scans, files, r, config.lowConfidenceThreshold, SaveResultOptions("p-north", "  Tree 7  ", createdAt = NOW))
        val saved = scans.getScan(id)!!
        with(saved.scan) {
            assertEquals("North", plotName)
            assertEquals("Tree 7", notes)
            assertEquals(ScanSource.Camera, source)
            assertEquals(0.3, confThreshold, 0.0)
            assertEquals(0.5, iouThreshold, 0.0)
            assertEquals(1400, inferenceMs)
            assertEquals(Verdict.EarlyStage, verdict)
            assertEquals(4000, imageWidth)
            assertEquals(3000, imageHeight)
            assertEquals(NOW, createdAt)
        }
        assertEquals("/cache/Camera/a.jpg", files.files["scans/$id.jpg"])
        assertEquals(1, saved.detections.count { it.correctedClassId == EARLY })
    }

    @Test
    fun storesEmptyNotesAsNull() = runBlocking {
        val photo = PendingPhoto(File("/cache/b.jpg"), 10, 10, ScanSource.Gallery)
        val id = saveResult(scans, files, result(photo, emptyList(), Verdict.NoPod), config.lowConfidenceThreshold, SaveResultOptions(null, "   "))
        assertNull(scans.getScan(id)!!.scan.notes)
    }

    @Test
    fun storesTheOptInLocationWithTheScan() = runBlocking {
        val photo = PendingPhoto(File("/cache/p.jpg"), 800, 600, ScanSource.Camera)
        val r = result(photo, listOf(ResultDetection(HEALTHY, 0.9, 0.1, 0.1, 0.2, 0.2)), Verdict.Healthy, inferenceMs = 900)
        val id = saveResult(
            scans, files, r, config.lowConfidenceThreshold,
            SaveResultOptions(null, "", location = ScanLocation(7.1, -73.1)),
        )
        val saved = scans.getScan(id)!!.scan
        assertEquals(7.1, saved.latitude!!, 0.0)
        assertEquals(-73.1, saved.longitude!!, 0.0)
    }

    @Test
    fun analyzesAGoldenPhotoWithTheUsersThresholdsAndWritesNothing() = runBlocking {
        val analyze = AnalyzePhoto(SettingsRepository(holder), ClassRepository(holder), ModelLoader(context, config), config)
        val file = GoldenSamples(context).file("golden_01.jpg")
        val r = analyze(PendingPhoto(file, 1024, 1024, ScanSource.Gallery))

        assertEquals(1024, r.imageWidth)
        assertEquals(Verdict.EarlyStage, r.verdict) // golden_01: two early-stage pods
        assertEquals(2, r.totalPods)
        assertTrue(r.detections.all { it.classId == EARLY && it.correctedClassId == null })
        assertEquals(config.confThreshold, r.confThreshold, 0.0)
        assertEquals(config.id, r.modelVersionId)
        assertTrue(r.inferenceMs > 0 && r.totalMs >= r.inferenceMs)
        assertEquals(0, scans.countScans())
    }

    @Test
    fun deviceDateFormatsHandleIcuOnlyPatternLetters() {
        // Some locales' 12-hour patterns use "B" (day period, e.g. zh-Hant "Bh:mm" in newer ICU
        // data). java.time rejects it before API 34; the ICU formatter DayFormat uses does not.
        if (android.os.Build.VERSION.SDK_INT < 34) {
            assertThrows(IllegalArgumentException::class.java) { java.time.format.DateTimeFormatter.ofPattern("Bh:mm") }
        }
        val icu = android.icu.text.SimpleDateFormat("Bh:mm", Locale.TAIWAN)
        assertTrue(icu.format(java.util.Date(NOW)).isNotBlank())

        val locales = listOf("en-US", "en-GB", "fil-PH", "zh-TW", "zh-HK", "ja-JP", "ar-EG", "hi-IN", "es-CO", "pt-BR")
        for (tag in locales) {
            for (is24Hour in listOf(false, true)) {
                val format = DayFormat.forLocale(Locale.forLanguageTag(tag), java.time.ZoneId.of("Asia/Manila"), is24Hour, "Today", "Yesterday")
                for (text in listOf(format.time(NOW), format.weekdayDate(NOW), format.fullDate(NOW))) {
                    assertTrue("$tag: '$text'", text.isNotBlank())
                }
            }
        }
        // The real entry point, with this phone's settings.
        assertTrue(DayFormat.forDevice(context).time(NOW).isNotBlank())
    }

    private companion object {
        const val EARLY = 0
        const val HEALTHY = 2
        const val NOW = 1_792_000_000_000L
    }
}
