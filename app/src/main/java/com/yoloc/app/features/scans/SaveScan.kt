package com.yoloc.app.features.scans

import android.util.Log
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.NewScan
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.util.uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/** A scan to save: everything in [NewScan] except the paths, which saveScan chooses. */
data class SaveScanInput(
    /** The unsaved image (camera temp file or picked photo). */
    val sourceImage: File,
    val plotId: String? = null,
    val modelVersionId: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val source: ScanSource,
    val confThreshold: Double,
    val iouThreshold: Double,
    val inferenceMs: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val createdAt: Long? = null,
    val detections: List<NewDetection>,
)

/**
 * Saves a scan atomically (RN: saveScan.ts): copy the image, make its list thumbnail, then write
 * the scan and its detections in one transaction. If the write fails the copied files are deleted,
 * so a failed save leaves neither a row nor an orphan file. A failed thumbnail does not fail the
 * save: History falls back to the full image and a later backfill retries. Returns the new scan id.
 */
suspend fun saveScan(
    scans: ScanRepository,
    files: ScanFileStore,
    input: SaveScanInput,
    lowConfidenceThreshold: Double,
): String {
    val id = uuid()
    val imagePath = scanImagePath(id)
    val thumbnailPath = scanThumbnailPath(id)

    try {
        files.copyIn(input.sourceImage, imagePath)
        val storedThumbnail = try {
            files.makeThumbnail(imagePath, thumbnailPath, input.imageWidth, input.imageHeight)
            thumbnailPath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable: an OutOfMemoryError on a big photo must not cost the user the scan.
            Log.w(TAG, "Thumbnail failed; History will use the full image", e)
            null
        }
        scans.createScan(input.toNewScan(id, imagePath, storedThumbnail), lowConfidenceThreshold)
    } catch (e: Throwable) {
        // Covers a failed DB write, a copy that died halfway, and cancellation (the user left the
        // screen). Runs even when cancelled. If the row was committed just before a cancellation,
        // the files belong to a saved scan and stay; if the check itself fails, the files stay too
        // (an orphan file is better than a saved scan without its photo). Best effort: the
        // original error matters more than a failed cleanup.
        withContext(NonCancellable) {
            val saved = runCatching { scans.scanExists(id) }.getOrDefault(true)
            if (!saved) {
                runCatching { files.remove(imagePath) }
                runCatching { files.remove(thumbnailPath) }
            }
        }
        throw e
    }
    return id
}

private fun SaveScanInput.toNewScan(id: String, imagePath: String, thumbnailPath: String?) = NewScan(
    id = id,
    plotId = plotId,
    modelVersionId = modelVersionId,
    imagePath = imagePath,
    thumbnailPath = thumbnailPath,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
    source = source,
    confThreshold = confThreshold,
    iouThreshold = iouThreshold,
    inferenceMs = inferenceMs,
    latitude = latitude,
    longitude = longitude,
    notes = notes,
    createdAt = createdAt,
    detections = detections,
)

private const val TAG = "SaveScan"
