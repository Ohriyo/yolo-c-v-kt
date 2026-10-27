package com.yoloc.app.features.detection

import kotlinx.serialization.Serializable

/** A final detection, in the DB convention: normalized 0–1, top-left origin, original image. */
@Serializable
data class Detection(
    val classId: Int,
    val confidence: Double,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
)

data class Thresholds(val confThreshold: Double, val iouThreshold: Double, val maxDetections: Int = 300)

data class PostprocessResult(
    val detections: List<Detection>,
    val boxUnits: BoxUnits,
    /** Candidates above the confidence threshold before NMS (diagnostics). */
    val candidateCount: Int,
)

/**
 * Raw model output -> final detections: decode and filter by confidence, class-aware NMS, map
 * from the letterboxed input back to the original image, normalize. Best first. Boxes that
 * collapse to nothing after clipping to the image are dropped.
 */
fun postprocess(output: FloatArray, spec: ModelSpec, lb: Letterbox, thresholds: Thresholds): PostprocessResult {
    val (candidates, boxUnits) = decodeOutput(output, spec, thresholds.confThreshold)
    val kept = nonMaxSuppression(candidates, thresholds.iouThreshold, thresholds.maxDetections)

    val detections = ArrayList<Detection>()
    for (c in kept) {
        val box = toOriginalPixels(c.box, lb)
        val w = box.x2 - box.x1
        val h = box.y2 - box.y1
        if (w <= 0 || h <= 0) continue
        detections += Detection(
            classId = c.classId,
            confidence = c.score,
            x = box.x1 / lb.srcWidth,
            y = box.y1 / lb.srcHeight,
            w = w / lb.srcWidth,
            h = h / lb.srcHeight,
        )
    }
    return PostprocessResult(detections, boxUnits, candidates.size)
}
