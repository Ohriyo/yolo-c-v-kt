package com.yoloc.app.features.dev

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.DevRepository
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanFilters
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.db.rowCount
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.saveScan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** RN: db.scans.test.ts "dev fake scans". */
@RunWith(AndroidJUnit4::class)
class SeedFakeScansTest {
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val classes = ClassRepository(holder)
    private val settings = SettingsRepository(holder)
    private val dev = DevRepository(holder)
    private val files = MemoryFileStore()
    private val config = TestDb.config

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() = holder.close()

    @Test
    fun seedsTwentyScansThroughTheRealSavePathThenRemovesThemWithTheirFiles() = runBlocking {
        // A real scan that must survive removal.
        saveScan(
            scans,
            files,
            SaveScanInput(
                sourceImage = File("/cache/photo.jpg"),
                modelVersionId = config.id,
                imageWidth = 800,
                imageHeight = 600,
                source = ScanSource.Camera,
                confThreshold = 0.25,
                iouThreshold = 0.45,
                detections = listOf(NewDetection(2, 0.9, 0.1, 0.1, 0.2, 0.2)),
            ),
            config.lowConfidenceThreshold,
        )
        val image = SampleImage(File("/cache/sample_04.jpg"), 365, 547)

        assertEquals(20, seedFakeScans(scans, plots, classes, settings, files, image, config, 20, NOW))
        assertEquals(20, dev.countScansWithNotePrefix(FAKE_SCAN_TAG))
        // A photo and a thumbnail per scan.
        assertEquals(42, files.files.size)
        val fakes = scans.listScans(ScanFilters(search = FAKE_SCAN_TAG, limit = 100))
        assertEquals(20, fakes.size)
        assertTrue(fakes.map { it.verdict }.toSet().size > 1)
        assertTrue(fakes.any { it.plotName?.startsWith(FAKE_PLOT_PREFIX) == true })

        assertEquals(20, removeFakeScans(dev, files))
        assertEquals(0, dev.countScansWithNotePrefix(FAKE_SCAN_TAG))
        assertEquals(1, holder.get().rowCount("scans"))
        assertEquals(2, files.files.size)
        assertEquals(0, holder.get().rowCount("plots"))
    }

    private companion object {
        const val NOW = 1_792_227_600_000L
    }
}
