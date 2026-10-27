package com.yoloc.app.features.detection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DetectionTimings(
    /** Decode, orient, resize and build the input tensor. */
    val preprocessMs: Double,
    /** Interpreter.run only: the number stored as scans.inference_ms. */
    val inferenceMs: Double,
    val postprocessMs: Double,
    val totalMs: Double,
)

data class DetectionRun(
    val detections: List<Detection>,
    val boxUnits: BoxUnits,
    val candidateCount: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val timings: DetectionTimings,
)

/** Runs the full pipeline on one still image with the CPU model (photo scans). */
suspend fun runDetection(loaded: LoadedModel, image: File, padValue: Int, thresholds: Thresholds): DetectionRun =
    runDetection(loaded, loaded.spec, image, padValue, thresholds)

/** Same pipeline on any backend; `spec` comes from the CPU model, which reads the same file. */
suspend fun runDetection(
    runner: LiveRunner,
    spec: ModelSpec,
    image: File,
    padValue: Int,
    thresholds: Thresholds,
): DetectionRun =
    withContext(Dispatchers.Default) {
        val t0 = System.nanoTime()

        // Failures are tagged by stage so the UI can say what went wrong (DetectionErrors.kt).
        val prepared = inStage(DetectionErrorKind.Image) {
            val bitmap = decodeOriented(image)
            try {
                val lb = computeLetterbox(bitmap.width, bitmap.height, spec.inputSize)
                val pixels = resizedPixels(bitmap, lb.resizedWidth, lb.resizedHeight)
                lb to buildInputTensor(pixels, lb, padValue, spec.inputLayout)
            } finally {
                bitmap.recycle()
            }
        }
        val (lb, input) = prepared
        val t1 = System.nanoTime()

        val rawOutput = inStage(DetectionErrorKind.Inference) { runner.run(input) }
        val t2 = System.nanoTime()

        val result = inStage(DetectionErrorKind.Inference) { postprocess(rawOutput, spec, lb, thresholds) }
        val t3 = System.nanoTime()

        fun ms(from: Long, to: Long) = (to - from) / 1e6
        DetectionRun(
            detections = result.detections,
            boxUnits = result.boxUnits,
            candidateCount = result.candidateCount,
            imageWidth = lb.srcWidth,
            imageHeight = lb.srcHeight,
            timings = DetectionTimings(ms(t0, t1), ms(t1, t2), ms(t2, t3), ms(t0, t3)),
        )
    }
