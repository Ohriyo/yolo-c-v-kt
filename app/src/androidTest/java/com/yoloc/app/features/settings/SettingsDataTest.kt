package com.yoloc.app.features.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.exec
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.saveScan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** RN: settings.logic.test.ts (clearAllData, getStorageSummary). */
@RunWith(AndroidJUnit4::class)
class SettingsDataTest {
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val settings = SettingsRepository(holder)
    private val files = MemoryFileStore()
    private val config = TestDb.config

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() = holder.close()

    private suspend fun save(plotId: String? = null) = saveScan(
        scans,
        files,
        SaveScanInput(
            sourceImage = File("/cache/photo.jpg"),
            plotId = plotId,
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

    @Test
    fun removesEveryScanPlotAndImageButKeepsSettings() = runBlocking {
        val plot = plots.createPlot("North")
        save(plot.id)
        save()
        holder.get().exec("UPDATE settings SET value = 'dark' WHERE key = 'theme'")

        // Each scan stores a photo and a thumbnail; the fake store counts 100 KB per file.
        assertEquals(StorageSummary(scans = 2, plots = 1, bytes = 400_000), getStorageSummary(scans, plots, files))
        assertEquals(2, clearAllData(scans, files))
        assertEquals(StorageSummary(scans = 0, plots = 0, bytes = 0), getStorageSummary(scans, plots, files))
        assertEquals(0, files.files.size)
        assertEquals("dark", settings.getSettings().theme.key)
    }
}
