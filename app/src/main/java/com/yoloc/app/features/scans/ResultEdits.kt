package com.yoloc.app.features.scans

import com.yoloc.app.features.detection.VerdictInput
import com.yoloc.app.features.detection.summarizeDetections

/** The class shown for a pod: the user's correction if any, else the model's class. */
val ResultDetection.effectiveClassId: Int get() = correctedClassId ?: classId

/**
 * RN: resultEdits.ts. Returns a copy of an unsaved result with pod `index` relabelled to
 * `classId`, and the verdict recomputed from the corrected labels. Picking the model's own class
 * clears the correction. Confidence is the model's and does not change, so neither does the
 * low-confidence flag. `severities` maps class id to severity.
 */
fun correctResultDetection(result: ScanResult, index: Int, classId: Int, severities: Map<Int, Int>): ScanResult {
    require(index in result.detections.indices) { "No pod at index $index" }
    val detections = result.detections.mapIndexed { i, d ->
        if (i == index) d.copy(correctedClassId = if (classId == d.classId) null else classId) else d
    }
    // Unknown classes fail instead of counting as healthy (RN: `?? 0`), as in AnalyzePhoto.
    val verdict = summarizeDetections(
        detections.map {
            val severity = requireNotNull(severities[it.effectiveClassId]) { "Unknown class id ${it.effectiveClassId}" }
            VerdictInput(severity, it.confidence)
        },
        0.0,
    ).verdict
    return result.copy(detections = detections, verdict = verdict)
}
