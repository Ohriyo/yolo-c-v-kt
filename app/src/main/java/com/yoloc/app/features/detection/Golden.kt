package com.yoloc.app.features.detection

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** One image's reference detections, as written by the Colab script (RN assets/sample/README.md). */
@Serializable
data class ExpectedImage(
    val file: String,
    val width: Int,
    val height: Int,
    val detections: List<Detection>,
)

@Serializable
data class ExpectedFile(
    val weights: String? = null,
    /** Placeholder files set this until the real Python output is added. */
    val pending: Boolean? = null,
    val images: List<ExpectedImage> = emptyList(),
) {
    companion object {
        // The files also carry source, args, className and originalName, which the test ignores.
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ExpectedFile = json.decodeFromString(serializer(), text)
    }
}

data class GoldenTolerance(
    /** Minimum IoU (in normalized space) for two boxes to count as the same pod. */
    val minIou: Double,
    /** Maximum absolute confidence difference. */
    val maxConfidenceDelta: Double,
)

val DEFAULT_TOLERANCE = GoldenTolerance(minIou = 0.9, maxConfidenceDelta = 0.05)

data class GoldenPair(
    val expected: Detection,
    val actual: Detection?,
    val iou: Double,
    val confidenceDelta: Double,
    val ok: Boolean,
)

data class GoldenReport(
    val pass: Boolean,
    val pairs: List<GoldenPair>,
    /** App detections with no reference counterpart. */
    val extra: List<Detection>,
)

private fun corners(d: Detection) = PixelBox(d.x, d.y, d.x + d.w, d.y + d.h)

/**
 * Compares app detections to reference detections (the golden test). Each expected box is greedily
 * matched to the unused app box of the same class with the highest IoU. Passes when every expected
 * box has a match within tolerance and the app found nothing extra.
 */
fun compareToReference(
    actual: List<Detection>,
    expected: List<Detection>,
    tolerance: GoldenTolerance = DEFAULT_TOLERANCE,
): GoldenReport {
    val unused = actual.indices.toMutableSet() // LinkedHashSet: keeps index order, like a JS Set
    val pairs = expected.sortedByDescending { it.confidence }.map { exp ->
        var bestIndex = -1
        var bestIou = 0.0
        for (i in unused) {
            val act = actual[i]
            if (act.classId != exp.classId) continue
            val overlap = iou(corners(exp), corners(act))
            if (overlap > bestIou) {
                bestIou = overlap
                bestIndex = i
            }
        }
        val match = if (bestIndex >= 0) actual[bestIndex] else null
        if (match != null) unused -= bestIndex
        val confidenceDelta = if (match != null) abs(match.confidence - exp.confidence) else 1.0
        GoldenPair(
            expected = exp,
            actual = match,
            iou = bestIou,
            confidenceDelta = confidenceDelta,
            ok = match != null && bestIou >= tolerance.minIou && confidenceDelta <= tolerance.maxConfidenceDelta,
        )
    }
    val extra = unused.map { actual[it] }
    return GoldenReport(pairs.all { it.ok } && extra.isEmpty(), pairs, extra)
}
