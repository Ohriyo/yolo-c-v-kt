package com.yoloc.app.features.dev

import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.DevRepository
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.features.scans.saveScan
import kotlinx.coroutines.CancellationException
import java.io.File

/** An image the fake scans copy, with its upright size. */
data class SampleImage(val file: File, val width: Int, val height: Int)

/**
 * RN: dev/seedFakeScans.ts. Adds `count` fake scans through the real save path (image copy +
 * transaction), so History is tested against the same rows and files a real scan produces.
 * Returns the number added.
 */
suspend fun seedFakeScans(
    scans: ScanRepository,
    plots: PlotRepository,
    classes: ClassRepository,
    settings: SettingsRepository,
    files: ScanFileStore,
    image: SampleImage,
    config: ModelConfig,
    count: Int = 20,
    now: Long = System.currentTimeMillis(),
): Int {
    val classIds = classes.listClasses().map { it.id }
    val current = settings.getSettings()
    val plotIds = ensureFakePlots(plots)

    val fakes = buildFakeScans(
        FakeScanOptions(
            count = count,
            now = now,
            modelVersionId = current.activeModelId.ifEmpty { config.id },
            classIds = classIds,
            plotIds = plotIds + null,
            imageWidth = image.width,
            imageHeight = image.height,
            confThreshold = current.confThreshold,
            iouThreshold = current.iouThreshold,
            seed = now,
        ),
        image.file,
    )
    for (scan in fakes) saveScan(scans, files, scan, config.lowConfidenceThreshold)
    return fakes.size
}

/** Removes every fake scan, its image files and the fake plots. Returns scans removed. */
suspend fun removeFakeScans(dev: DevRepository, files: ScanFileStore): Int {
    val removed = dev.purgeScansWithNotePrefix(FAKE_SCAN_TAG)
    for (scan in removed) {
        for (path in listOfNotNull(scan.imagePath, scan.thumbnailPath)) {
            try {
                files.remove(path)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // An orphan file is harmless; the rows are already gone.
            }
        }
    }
    dev.purgePlotsWithNamePrefix(FAKE_PLOT_PREFIX)
    return removed.size
}

private suspend fun ensureFakePlots(plots: PlotRepository): List<String> {
    val names = listOf("$FAKE_PLOT_PREFIX North block", "$FAKE_PLOT_PREFIX River block")
    val existing = plots.listPlots()
    return names.map { name -> existing.firstOrNull { it.name == name }?.id ?: plots.createPlot(name).id }
}
