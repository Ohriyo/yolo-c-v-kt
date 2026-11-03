package com.yoloc.app.features.detection

/** The overall result of one scan (scans.verdict). `key` is the value stored in the DB. */
enum class Verdict(val key: String) {
    Healthy("healthy"),
    EarlyStage("early_stage"),
    Infected("infected"),
    NoPod("no_pod"),
    ;

    companion object {
        fun fromKey(key: String): Verdict = entries.firstOrNull { it.key == key } ?: error("Unknown verdict $key")
    }
}

data class VerdictInput(val severity: Int, val confidence: Double)

data class VerdictSummary(
    val verdict: Verdict,
    val totalPods: Int,
    /** True when the detection that decides the verdict scores below the `uncertainBelow` limit. */
    val lowConfidence: Boolean,
)

/**
 * The verdict is the most severe class present. No detections is NoPod, never Healthy. The
 * detection that decides it is the highest-scoring one of that severity, so a weak infected pod
 * is uncertain however sure the model is about a healthy pod beside it.
 * Detections are assumed already filtered by the confidence threshold.
 */
fun summarizeDetections(detections: List<VerdictInput>, uncertainBelow: Double): VerdictSummary {
    if (detections.isEmpty()) return VerdictSummary(Verdict.NoPod, 0, false)

    val verdict = when (val maxSeverity = detections.maxOf { it.severity }) {
        0 -> Verdict.Healthy
        1 -> Verdict.EarlyStage
        2 -> Verdict.Infected
        else -> error("Unknown severity $maxSeverity")
    }
    val maxSeverity = detections.maxOf { it.severity }
    val deciding = detections.filter { it.severity == maxSeverity }.maxOf { it.confidence }
    return VerdictSummary(verdict, detections.size, deciding < uncertainBelow)
}
