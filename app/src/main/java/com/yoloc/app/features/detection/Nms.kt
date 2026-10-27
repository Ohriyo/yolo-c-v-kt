package com.yoloc.app.features.detection

/** Intersection over union of two corner boxes (no +1 pixel convention, as torchvision). */
fun iou(a: PixelBox, b: PixelBox): Double {
    val iw = minOf(a.x2, b.x2) - maxOf(a.x1, b.x1)
    val ih = minOf(a.y2, b.y2) - maxOf(a.y1, b.y1)
    if (iw <= 0 || ih <= 0) return 0.0
    val inter = iw * ih
    val union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter
    return if (union > 0) inter / union else 0.0
}

/**
 * Class-aware non-maximum suppression: highest score first, a box is dropped when it overlaps a
 * kept box of the same class by more than `iouThreshold`. Boxes of different classes never
 * suppress each other (Ultralytics agnostic=False). Returns at most `maxDetections`, best first.
 */
fun nonMaxSuppression(candidates: List<Candidate>, iouThreshold: Double, maxDetections: Int = 300): List<Candidate> {
    val kept = ArrayList<Candidate>()
    // sortedByDescending is stable, like JS Array.sort.
    for (candidate in candidates.sortedByDescending { it.score }) {
        if (kept.size >= maxDetections) break
        val overlaps = kept.any { it.classId == candidate.classId && iou(it.box, candidate.box) > iouThreshold }
        if (!overlaps) kept += candidate
    }
    return kept
}
