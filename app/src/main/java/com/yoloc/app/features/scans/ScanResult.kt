package com.yoloc.app.features.scans

import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.detection.Detection
import com.yoloc.app.features.detection.Verdict
import java.io.File

/** A photo in the scan flow (RN: PendingPhoto in scanSessionStore.ts). */
data class PendingPhoto(
    /** Temporary file in the app cache; deleted when the scan is discarded. */
    val file: File,
    val width: Int,
    val height: Int,
    val source: ScanSource,
)

/** A detection on the Result screen; `correctedClassId` is set by "Correct label". */
data class ResultDetection(
    val classId: Int,
    val confidence: Double,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val correctedClassId: Int? = null,
) {
    companion object {
        fun from(d: Detection) = ResultDetection(d.classId, d.confidence, d.x, d.y, d.w, d.h)
    }
}

/** An analyzed but unsaved scan. Nothing is in the database until the user saves. */
data class ScanResult(
    val photo: PendingPhoto,
    /** Size the pipeline saw (EXIF-rotated); boxes are normalized to this. */
    val imageWidth: Int,
    val imageHeight: Int,
    val detections: List<ResultDetection>,
    val verdict: Verdict,
    val totalPods: Int,
    val lowConfidence: Boolean,
    val modelVersionId: String,
    val confThreshold: Double,
    val iouThreshold: Double,
    val inferenceMs: Int,
    val totalMs: Int,
    val analyzedAt: Long,
)
