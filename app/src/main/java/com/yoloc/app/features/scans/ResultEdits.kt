package com.yoloc.app.features.scans

import com.yoloc.app.features.detection.VerdictInput
import com.yoloc.app.features.detection.summarizeDetections

/** The class shown for a pod: the user's correction if any, else the model's class. */
val ResultDetection.effectiveClassId: Int get() = correctedClassId ?: classId

/**
 * RN: resultEdits.ts. Returns a copy of an unsaved result with pod `index` relabelled to
 * `classId`, and the verdict recomputed from the corrected labels. Picking the model's own class
 * clears the correction. A new label can change which pod decides the verdict, so the uncertain
 * flag is recomputed too. `severities` maps class id to severity.
 */
fun correctResultDetection(
    result: ScanResult,
    index: Int,
    classId: Int,
    severities: Map<Int, Int>,
    uncertainBelow: Double,
): ScanResult {
    require(index in result.detections.indices) { "No pod at index $index" }
    val detections = result.detections.mapIndexed { i, d ->
        if (i == index) d.copy(correctedClassId = if (classId == d.classId) null else classId) else d
    }
    // Unknown classes fail instead of counting as healthy (RN: `?? 0`), as in AnalyzePhoto.
    val summary = summarizeDetections(
        detections.map {
            val severity = requireNotNull(severities[it.effectiveClassId]) { "Unknown class id ${it.effectiveClassId}" }
            VerdictInput(severity, it.confidence)
        },
        uncertainBelow,
    )
    return result.copy(detections = detections, verdict = summary.verdict, lowConfidence = summary.lowConfidence)
}
