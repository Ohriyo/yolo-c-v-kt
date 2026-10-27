package com.yoloc.app.features.detection

/** The overall result of one scan (scans.verdict). `key` is the value stored in the DB. */
enum class Verdict(val key: String) {
    Healthy("healthy"),
    EarlyStage("early_stage"),
    Infected("infected"),
    NoPod("no_pod"),
}

data class VerdictInput(val severity: Int, val confidence: Double)

data class VerdictSummary(
    val verdict: Verdict,
    val totalPods: Int,
    /** True when the best detection is below the low-confidence threshold. */
    val lowConfidence: Boolean,
)

/**
 * The verdict is the most severe class present. No detections is NoPod, never Healthy.
 * Detections are assumed already filtered by the confidence threshold.
 */
fun summarizeDetections(detections: List<VerdictInput>, lowConfidenceThreshold: Double): VerdictSummary {
    if (detections.isEmpty()) return VerdictSummary(Verdict.NoPod, 0, false)

    val verdict = when (val maxSeverity = detections.maxOf { it.severity }) {
        0 -> Verdict.Healthy
        1 -> Verdict.EarlyStage
        2 -> Verdict.Infected
        else -> error("Unknown severity $maxSeverity")
    }
    val bestConfidence = detections.maxOf { it.confidence }
    return VerdictSummary(verdict, detections.size, bestConfidence < lowConfidenceThreshold)
}
