package com.yoloc.app.features.detection

/** Tensor metadata as the LiteRT Interpreter reports it. */
data class TensorInfo(val name: String, val dataType: String, val shape: List<Int>)

/**
 * FeaturesFirst = [1, 4 + classes, anchors] (Ultralytics default, e.g. [1, 7, 8400]);
 * AnchorsFirst  = [1, anchors, 4 + classes] (some exports transpose it).
 */
enum class OutputLayout { FeaturesFirst, AnchorsFirst }

/** What the pipeline needs to know about the loaded model, read from its real tensors. */
data class ModelSpec(
    val inputLayout: InputLayout,
    val inputSize: Int,
    val inputShape: List<Int>,
    val outputLayout: OutputLayout,
    val outputShape: List<Int>,
    val numClasses: Int,
    val numAnchors: Int,
)

/**
 * Inspects the model's actual input/output tensors and checks them against model.config.json,
 * so a swapped model with a different layout still works, and a mismatched one fails clearly
 * at load time instead of producing wrong boxes.
 */
fun describeModel(inputs: List<TensorInfo>, outputs: List<TensorInfo>, config: ModelConfig): ModelSpec {
    require(inputs.size == 1) { "Expected 1 input tensor, got ${inputs.size}" }
    val input = inputs[0]
    val output = outputs.firstOrNull() ?: throw IllegalArgumentException("Model has no output tensor")
    require(input.dataType == "float32" && output.dataType == "float32") {
        "Expected float32 tensors, got input ${input.dataType} and output ${output.dataType}"
    }

    val shape = input.shape
    val inputLayout: InputLayout
    val height: Int
    val width: Int
    if (shape.size == 4 && shape[0] == 1 && shape[1] == 3) {
        inputLayout = InputLayout.NCHW
        height = shape[2]
        width = shape[3]
    } else if (shape.size == 4 && shape[0] == 1 && shape[3] == 3) {
        inputLayout = InputLayout.NHWC
        height = shape[1]
        width = shape[2]
    } else {
        throw IllegalArgumentException("Unsupported input shape $shape")
    }
    require(height == width && height == config.inputSize) {
        "Model input is ${width}x$height but model.config.json says inputSize ${config.inputSize}"
    }

    val features = 4 + config.classes.size
    val out = output.shape
    require(out.size == 3 && out[0] == 1) { "Unsupported output shape $out" }
    val outputLayout: OutputLayout
    val numAnchors: Int
    if (out[1] == features) {
        outputLayout = OutputLayout.FeaturesFirst
        numAnchors = out[2]
    } else if (out[2] == features) {
        outputLayout = OutputLayout.AnchorsFirst
        numAnchors = out[1]
    } else {
        throw IllegalArgumentException(
            "Output $out does not fit ${config.classes.size} classes " +
                "(expected a dimension of $features); check the class list in model.config.json",
        )
    }

    return ModelSpec(
        inputLayout = inputLayout,
        inputSize = config.inputSize,
        inputShape = shape,
        outputLayout = outputLayout,
        outputShape = out,
        numClasses = config.classes.size,
        numAnchors = numAnchors,
    )
}
