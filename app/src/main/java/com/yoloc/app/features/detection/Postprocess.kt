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

data class PostprocessResult(
    val detections: List<Detection>,
    val boxUnits: BoxUnits,
    /** Candidates above the confidence threshold before NMS (diagnostics). */
    val candidateCount: Int,
)

/**
 * Raw model output -> final detections: best class and per-class threshold, corners, undo the
 * letterbox, normalize and clamp to the original image, then class-aware NMS. Best first. Boxes
 * that collapse to nothing after clamping are dropped.
 */
fun postprocessByClass(
    output: FloatArray,
    spec: ModelSpec,
    lb: Letterbox,
    confThresholds: DoubleArray,
    iouThreshold: Double,
    maxDetections: Int = 300,
): PostprocessResult {
    val (candidates, boxUnits) = decodeOutput(output, spec, confThresholds)
    val onImage = candidates.mapNotNull { c ->
        val box = toNormalizedOriginal(c.box, lb)
        if (box.x2 > box.x1 && box.y2 > box.y1) c.copy(box = box) else null
    }
    val detections = nonMaxSuppression(onImage, iouThreshold, maxDetections).map { c ->
        Detection(c.classId, c.score, c.box.x1, c.box.y1, c.box.x2 - c.box.x1, c.box.y2 - c.box.y1)
    }
    return PostprocessResult(detections, boxUnits, candidates.size)
}
