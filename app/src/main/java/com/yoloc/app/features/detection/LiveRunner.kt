package com.yoloc.app.features.detection

import android.content.res.AssetManager
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer

/**
 * One way to run the model on an input tensor. The CPU [LoadedModel] and the GPU runner share the
 * same model file, so the tensor layout always comes from the CPU spec (describeModel runs once).
 */
interface LiveRunner {
    /** "cpu", "cpu-xnnpack" or "gpu": shown in the dev screens and logs. */
    val backend: String

    fun run(input: FloatArray): FloatArray
}

/**
 * Where LiteRT keeps the compiled GPU program between launches. `key` must change with the model's
 * contents: LiteRT's own fingerprint only covers tensor sizes, and v2 and v3 are the same size.
 */
class GpuProgramCache(val directory: String, val key: String)

/**
 * LiteRT's CompiledModel on one accelerator. GPU is RN's loadDetectionModel(config, 'gpu'), the
 * android-gpu delegate; LiteRT 2.x only offers GPU through this API. Creating it throws when the
 * accelerator cannot run the model, and callers fall back to the CPU.
 */
class CompiledModelRunner private constructor(
    private val model: CompiledModel,
    private val inputs: List<TensorBuffer>,
    private val outputs: List<TensorBuffer>,
    override val backend: String,
) : LiveRunner, AutoCloseable {
    private var closed = false

    /**
     * Buffers are reused between runs, so one run at a time. After [close] this throws rather than
     * reaching freed native memory, which a Live frame already in flight can do.
     */
    override fun run(input: FloatArray): FloatArray = synchronized(model) {
        check(!closed) { "The $backend model was released" }
        inputs[0].writeFloat(input)
        model.run(inputs, outputs)
        outputs[0].readFloat()
    }

    /** Waits for a run in progress. The detector and Live share a runner, so closing twice is fine. */
    override fun close() = synchronized(model) {
        if (closed) return@synchronized
        closed = true
        inputs.forEach { it.close() }
        outputs.forEach { it.close() }
        model.close()
    }

    companion object {
        fun create(
            assets: AssetManager,
            assetPath: String,
            accelerator: Accelerator,
            gpuCache: GpuProgramCache? = null,
        ): CompiledModelRunner {
            val options = CompiledModel.Options(accelerator)
            if (accelerator == Accelerator.GPU && gpuCache != null) {
                options.gpuOptions = CompiledModel.GpuOptions(
                    serializationDir = gpuCache.directory,
                    modelCacheKey = gpuCache.key,
                    serializeProgramCache = true,
                )
            }
            val model = CompiledModel.create(assets, assetPath, options)
            return try {
                CompiledModelRunner(
                    model,
                    model.createInputBuffers(),
                    model.createOutputBuffers(),
                    accelerator.name.lowercase(),
                )
            } catch (e: Throwable) {
                model.close()
                throw e
            }
        }
    }
}
