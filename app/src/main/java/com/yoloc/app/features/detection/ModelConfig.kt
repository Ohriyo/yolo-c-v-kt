package com.yoloc.app.features.detection

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/** The Settings slider, left to right. Standard is the config's own thresholds. */
enum class DetectionSensitivity(val key: String, internal val direction: Int) {
    Strict("strict", 1),
    Standard("standard", 0),
    Sensitive("sensitive", -1),
    ;

    companion object {
        fun fromKey(key: String?): DetectionSensitivity = entries.firstOrNull { it.key == key } ?: Standard
    }
}

/** Shape of assets/model_config.json, the single source of truth for the model. */
@Serializable
data class ModelConfig(
    val id: String,
    val displayName: String,
    val file: String,
    val inputSize: Int,
    val inputShape: List<Int>,
    val outputShape: List<Int>,
    /** Class keys in model output index order. */
    val classes: List<String>,
    val iouThreshold: Double,
    /** The recommended thresholds: what [DetectionSensitivity.Standard] uses. */
    val confThresholdByClass: Map<String, Double>,
    val sensitivity: SensitivityRange,
    /** A verdict whose deciding detection scores below this shows "Uncertain result". */
    val uncertainBelow: Double,
    val letterboxPad: Int,
    val metrics: Metrics = Metrics(),
) {
    @Serializable
    data class SensitivityRange(
        /** How far Strict and Sensitive move every class's threshold from Standard. */
        val shift: Double,
        /** No class's threshold goes below this. */
        val minConfThreshold: Double,
    )

    @Serializable
    data class Scores(
        val precision: Double,
        val recall: Double,
        val map50: Double,
        @SerialName("map50_95") val map5095: Double,
    )

    /** One evaluation run. `label` says what it measured, e.g. "validation (243 images)". */
    @Serializable
    data class Evaluation(val label: String, val all: Scores, val classes: Map<String, Scores>)

    @Serializable
    data class Metrics(val validation: Evaluation? = null, val test: Evaluation? = null) {
        /** The evaluation the app's plain-language claims quote: test once there is one. */
        val headline: Evaluation? get() = test ?: validation
        val earlyStageRecall: Double? get() = headline?.classes?.get(EARLY_STAGE)?.recall
    }

    /** Confidence threshold per class id, in output order. */
    val confThresholds: DoubleArray
        get() = DoubleArray(classes.size) { confThresholdByClass.getValue(classes[it]) }

    /** The thresholds a position of the slider means, per class id. */
    fun confThresholdsFor(level: DetectionSensitivity): DoubleArray = DoubleArray(classes.size) {
        val moved = confThresholds[it] + level.direction * sensitivity.shift
        // Rounded so saved values read 0.25, not 0.25000000000000006.
        (moved.coerceAtLeast(sensitivity.minConfThreshold) * 10_000).roundToLong() / 10_000.0
    }

    /** The same by class key: what is stored on a scan. */
    fun confThresholdsByClass(level: DetectionSensitivity): Map<String, Double> =
        classes.zip(confThresholdsFor(level).asList()).toMap()

    /**
     * The tensor layout this config declares: planar NCHW in, channels first out. Anything else
     * fails here, rather than producing wrong boxes later.
     */
    fun modelSpec(): ModelSpec {
        require(inputShape == listOf(1, 3, inputSize, inputSize)) {
            "Expected input [1, 3, $inputSize, $inputSize], config says $inputShape"
        }
        require(outputShape.size == 3 && outputShape[0] == 1 && outputShape[1] == 4 + classes.size) {
            "Expected output [1, ${4 + classes.size}, n], config says $outputShape"
        }
        return ModelSpec(
            inputLayout = InputLayout.NCHW,
            inputSize = inputSize,
            inputShape = inputShape,
            outputLayout = OutputLayout.FeaturesFirst,
            outputShape = outputShape,
            numClasses = classes.size,
            numAnchors = outputShape[2],
        )
    }

    companion object {
        const val ASSET_PATH = "model_config.json"
        const val LABELS_ASSET = "labels.txt"
        private const val EARLY_STAGE = "early_stage"

        // The file also has prose fields (preprocess, outputLayout, boxCoordinates).
        private val json = Json { ignoreUnknownKeys = true }

        /** Throws when a class has no threshold or labels.txt disagrees with the class order. */
        fun parse(configText: String, labelsText: String): ModelConfig {
            val config = json.decodeFromString(serializer(), configText)
            val missing = config.classes.filterNot { it in config.confThresholdByClass }
            require(missing.isEmpty()) { "$ASSET_PATH has no threshold for $missing" }
            val labels = labelsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            require(labels == config.classes) { "$LABELS_ASSET lists $labels but $ASSET_PATH lists ${config.classes}" }
            return config
        }

        /** RN imports the JSON at bundle time; Kotlin reads it from assets at startup. */
        fun load(context: Context): ModelConfig = parse(
            context.assets.open(ASSET_PATH).use { it.readBytes().decodeToString() },
            context.assets.open(LABELS_ASSET).use { it.readBytes().decodeToString() },
        )
    }
}
