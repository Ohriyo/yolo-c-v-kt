package com.yoloc.app.features.detection

/** One candidate box in model-input pixels, before NMS. */
data class Candidate(val box: PixelBox, val classId: Int, val score: Double)

/** Whether the model emitted boxes in 0–1 units or in input pixels. */
enum class BoxUnits { Normalized, Pixels }

data class DecodeResult(val candidates: List<Candidate>, val boxUnits: BoxUnits)

/**
 * Decodes raw YOLO output (center-x, center-y, w, h, then one score per class for each anchor)
 * into corner boxes in input pixels, keeping each anchor's best class if its score passes
 * `confThreshold` (Ultralytics predict, multi_label=False).
 *
 * TFLite exports may emit boxes normalized to 0–1 or in pixels. The units are detected from the
 * data: anchor centers span the whole input, so in pixel units their maximum is far above 1.
 */
fun decodeOutput(data: FloatArray, spec: ModelSpec, confThreshold: Double): DecodeResult {
    val anchors = spec.numAnchors
    val classes = spec.numClasses
    val features = 4 + classes
    require(data.size == anchors * features) {
        "Output has ${data.size} values, expected ${anchors * features}"
    }
    // Index of feature f for anchor i in either memory layout.
    val at: (Int, Int) -> Double =
        if (spec.outputLayout == OutputLayout.FeaturesFirst) {
            { f, i -> data[f * anchors + i].toDouble() }
        } else {
            { f, i -> data[i * features + f].toDouble() }
        }

    var maxCenter = 0.0
    for (i in 0 until anchors) maxCenter = maxOf(maxCenter, at(0, i), at(1, i))
    val boxUnits = if (maxCenter <= 2) BoxUnits.Normalized else BoxUnits.Pixels
    val k = if (boxUnits == BoxUnits.Normalized) spec.inputSize.toDouble() else 1.0

    val candidates = ArrayList<Candidate>()
    for (i in 0 until anchors) {
        var best = 0.0
        var classId = -1
        for (c in 0 until classes) {
            val s = at(4 + c, i)
            if (s > best) {
                best = s
                classId = c
            }
        }
        if (classId < 0 || best <= confThreshold) continue
        val cx = at(0, i) * k
        val cy = at(1, i) * k
        val w = at(2, i) * k
        val h = at(3, i) * k
        candidates += Candidate(PixelBox(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), classId, best)
    }
    return DecodeResult(candidates, boxUnits)
}
