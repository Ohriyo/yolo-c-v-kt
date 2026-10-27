package com.yoloc.app.features.detection

import java.io.File

/** Unit tests run with the app module as working directory, so assets are read from source. */
object TestAssets {
    val modelConfig: ModelConfig by lazy { ModelConfig.parse(main("models/model.config.json").readText()) }

    fun main(path: String) = File("src/main/assets/$path")

    fun debug(path: String) = File("src/debug/assets/$path")
}

/** Packs anchors [cx, cy, w, h, ...scores] into a tensor in the given layout. */
fun packOutput(anchors: List<List<Double>>, layout: OutputLayout): FloatArray {
    val features = anchors[0].size
    val out = FloatArray(anchors.size * features)
    anchors.forEachIndexed { i, a ->
        a.forEachIndexed { f, v ->
            val index = if (layout == OutputLayout.FeaturesFirst) f * anchors.size + i else i * features + f
            out[index] = v.toFloat()
        }
    }
    return out
}

fun specFor(numAnchors: Int, layout: OutputLayout) = ModelSpec(
    inputLayout = InputLayout.NCHW,
    inputSize = 640,
    inputShape = listOf(1, 3, 640, 640),
    outputLayout = layout,
    outputShape = if (layout == OutputLayout.FeaturesFirst) listOf(1, 7, numAnchors) else listOf(1, numAnchors, 7),
    numClasses = 3,
    numAnchors = numAnchors,
)

fun candidate(x1: Double, y1: Double, x2: Double, y2: Double, classId: Int, score: Double) =
    Candidate(PixelBox(x1, y1, x2, y2), classId, score)
