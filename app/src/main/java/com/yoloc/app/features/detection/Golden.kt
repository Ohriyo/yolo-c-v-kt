package com.yoloc.app.features.detection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

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

/** One detection in the v3 reference file: corner coordinates, normalized to the original image. */
@Serializable
data class ReferenceBox(
    @SerialName("class_id") val classId: Int,
    val confidence: Double,
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
) {
    fun toDetection() = Detection(classId, confidence, x1, y1, x2 - x1, y2 - y1)
}

/** One image in the v3 reference file (reference/expected_tflite.json). */
@Serializable
data class ReferenceImage(
    val image: String,
    val width: Int,
    val height: Int,
    @SerialName("conf_threshold") val confThreshold: Double,
    @SerialName("iou_threshold") val iouThreshold: Double,
    val detections: List<ReferenceBox>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseAll(text: String): List<ReferenceImage> = json.decodeFromString(text)
    }
}

data class ReferenceTolerance(val maxConfidenceDelta: Double = 0.05, val maxCoordinateDelta: Double = 0.02)

data class ReferenceMatch(
    val expected: ReferenceBox,
    val actual: Detection?,
    val iou: Double,
    val confidenceDelta: Double,
    /** Largest absolute difference across x1, y1, x2, y2. */
    val coordinateDelta: Double,
    val ok: Boolean,
)

data class ReferenceComparison(
    val pass: Boolean,
    val matches: List<ReferenceMatch>,
    /** Detections with no reference counterpart: a count or class mismatch. */
    val extra: List<Detection>,
)

/**
 * Greedily matches each reference box, highest confidence first, to the unused detection of the same
 * class with the best IoU. Passes when every reference box has a match within tolerance and no
 * detection is left over, which also means the same count and classes.
 */
fun compareToReferenceBoxes(
    actual: List<Detection>,
    expected: List<ReferenceBox>,
    tolerance: ReferenceTolerance = ReferenceTolerance(),
): ReferenceComparison {
    val unused = actual.indices.toMutableSet()
    val matches = expected.sortedByDescending { it.confidence }.map { exp ->
        val box = PixelBox(exp.x1, exp.y1, exp.x2, exp.y2)
        var bestIndex = -1
        var bestIou = 0.0
        for (i in unused) {
            if (actual[i].classId != exp.classId) continue
            val overlap = iou(box, corners(actual[i]))
            if (overlap > bestIou) {
                bestIou = overlap
                bestIndex = i
            }
        }
        if (bestIndex < 0) {
            return@map ReferenceMatch(exp, null, 0.0, Double.NaN, Double.NaN, ok = false)
        }
        unused -= bestIndex
        val match = actual[bestIndex]
        val c = corners(match)
        val confidenceDelta = abs(match.confidence - exp.confidence)
        val coordinateDelta = maxOf(abs(c.x1 - exp.x1), abs(c.y1 - exp.y1), abs(c.x2 - exp.x2), abs(c.y2 - exp.y2))
        ReferenceMatch(
            expected = exp,
            actual = match,
            iou = bestIou,
            confidenceDelta = confidenceDelta,
            coordinateDelta = coordinateDelta,
            ok = confidenceDelta <= tolerance.maxConfidenceDelta && coordinateDelta <= tolerance.maxCoordinateDelta,
        )
    }
    val extra = unused.map { actual[it] }
    return ReferenceComparison(matches.all { it.ok } && extra.isEmpty(), matches, extra)
}
