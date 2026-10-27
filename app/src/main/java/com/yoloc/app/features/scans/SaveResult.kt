package com.yoloc.app.features.scans

import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.ScanRepository

/** A GPS fix stored with a scan, only when the user opted in to location (Settings). */
data class ScanLocation(val latitude: Double, val longitude: Double)

data class SaveResultOptions(
    val plotId: String?,
    val notes: String,
    /** Defaults to now. */
    val createdAt: Long? = null,
    /** Null stores none. */
    val location: ScanLocation? = null,
)

/**
 * RN: saveResult.ts. Saves the Result screen's unsaved scan through the atomic path (saveScan):
 * the temp photo is copied into app storage as scans/<id>.jpg, then the scan, its detections and
 * any label corrections are written in one transaction. Returns the new scan id.
 */
suspend fun saveResult(
    scans: ScanRepository,
    files: ScanFileStore,
    result: ScanResult,
    lowConfidenceThreshold: Double,
    options: SaveResultOptions,
): String {
    val notes = options.notes.trim()
    return saveScan(
        scans,
        files,
        SaveScanInput(
            sourceImage = result.photo.file,
            plotId = options.plotId,
            modelVersionId = result.modelVersionId,
            imageWidth = result.imageWidth,
            imageHeight = result.imageHeight,
            source = result.photo.source,
            confThreshold = result.confThreshold,
            iouThreshold = result.iouThreshold,
            inferenceMs = result.inferenceMs,
            notes = notes.ifEmpty { null },
            latitude = options.location?.latitude,
            longitude = options.location?.longitude,
            createdAt = options.createdAt ?: System.currentTimeMillis(),
            detections = result.detections.map {
                NewDetection(it.classId, it.confidence, it.x, it.y, it.w, it.h, it.correctedClassId)
            },
        ),
        lowConfidenceThreshold,
    )
}
