package com.yoloc.app.features.detection

import android.content.res.AssetManager
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tries each option in order and returns the first that opens. Rethrows the last failure, with the
 * earlier ones attached, when none does.
 */
internal fun <A, R> openFirstWorking(options: List<A>, onFailure: (A, Throwable) -> Unit, open: (A) -> R): R {
    require(options.isNotEmpty()) { "No options to try" }
    val failures = ArrayList<Throwable>()
    for (option in options) {
        try {
            return open(option)
        } catch (e: Exception) {
            onFailure(option, e)
            failures += e
        }
    }
    val last = failures.last()
    failures.dropLast(1).forEach { last.addSuppressed(it) }
    throw last
}

/**
 * The on-device detection pipeline for still photos: LiteRT's CompiledModel on the GPU with CPU as
 * the fallback, returning boxes on the original image. Only [CompiledModelRunner] touches LiteRT,
 * so the runtime can be swapped behind [LiveRunner]. Everything runs off the main thread; one
 * inference at a time.
 */
class YoloDetector private constructor(
    val config: ModelConfig,
    private val spec: ModelSpec,
    private val runner: CompiledModelRunner,
    val loadMs: Long,
) : AutoCloseable {
    /** The accelerator that compiled the model and passed the test run: "gpu" or "cpu". */
    val accelerator: String get() = runner.backend

    /** Runs one still image. Thresholds default to the config's. */
    suspend fun detect(
        image: File,
        confThresholds: DoubleArray = config.confThresholds,
        iouThreshold: Double = config.iouThreshold,
    ): DetectionRun =
        runDetection(runner, spec, image, config.letterboxPad) { output, letterbox ->
            postprocessByClass(output, spec, letterbox, confThresholds, iouThreshold)
        }

    override fun close() = runner.close()

    companion object {
        private const val TAG = "YoloDetector"
        val DEFAULT_ACCELERATORS = listOf(Accelerator.GPU, Accelerator.CPU)

        /** `open` lets [ModelLoader] share one compiled GPU model between photos and Live. */
        suspend fun create(
            assets: AssetManager,
            config: ModelConfig,
            accelerators: List<Accelerator> = DEFAULT_ACCELERATORS,
            open: (Accelerator) -> CompiledModelRunner = { openChecked(assets, config, it) },
        ): YoloDetector = inStage(DetectionErrorKind.Model) {
            withContext(Dispatchers.IO) {
                val spec = config.modelSpec()
                val start = SystemClock.elapsedRealtime()
                val runner = openFirstWorking(
                    accelerators,
                    onFailure = { accelerator, e -> Log.w(TAG, "$accelerator failed, trying the next option", e) },
                    open = open,
                )
                val loadMs = SystemClock.elapsedRealtime() - start
                Log.i(TAG, "Using ${runner.backend} for ${config.file} (load $loadMs ms, requested $accelerators)")
                YoloDetector(config, spec, runner, loadMs)
            }
        }

        /**
         * A GPU can compile a model and still fail or return NaN when it runs, so one blank
         * inference has to pass before this accelerator counts as working. It also checks the real
         * tensors against the shapes in the config.
         */
        fun openChecked(
            assets: AssetManager,
            config: ModelConfig,
            accelerator: Accelerator,
            gpuCache: GpuProgramCache? = null,
        ): CompiledModelRunner {
            val runner = CompiledModelRunner.create(assets, config.file, accelerator, gpuCache)
            try {
                val output = runner.run(FloatArray(config.inputShape.fold(1) { a, b -> a * b }))
                val expected = config.outputShape.fold(1) { a, b -> a * b }
                check(output.size == expected) { "Model output has ${output.size} values, config implies $expected" }
                check(output.none { it.isNaN() }) { "$accelerator returned NaN" }
                return runner
            } catch (e: Throwable) {
                runner.close()
                throw e
            }
        }
    }
}
