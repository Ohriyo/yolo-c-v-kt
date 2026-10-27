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
    /** "cpu", "cpu-4" or "gpu": shown in the dev screens and logs. */
    val backend: String

    fun run(input: FloatArray): FloatArray
}

/**
 * GPU inference (RN: loadDetectionModel(config, 'gpu'), the android-gpu delegate). LiteRT 2.x only
 * offers GPU through the CompiledModel API; the Interpreter GPU delegate is v1 only. Creating it
 * throws when the GPU cannot run the model, and callers fall back to the CPU [LoadedModel].
 */
class GpuRunner private constructor(
    private val model: CompiledModel,
    private val inputs: List<TensorBuffer>,
    private val outputs: List<TensorBuffer>,
) : LiveRunner, AutoCloseable {
    override val backend = "gpu"

    /** Buffers are reused between runs, so one run at a time. */
    override fun run(input: FloatArray): FloatArray = synchronized(model) {
        inputs[0].writeFloat(input)
        model.run(inputs, outputs)
        outputs[0].readFloat()
    }

    override fun close() {
        inputs.forEach { it.close() }
        outputs.forEach { it.close() }
        model.close()
    }

    companion object {
        fun create(assets: AssetManager, assetPath: String): GpuRunner {
            val model = CompiledModel.create(assets, assetPath, CompiledModel.Options(Accelerator.GPU))
            return try {
                GpuRunner(model, model.createInputBuffers(), model.createOutputBuffers())
            } catch (e: Throwable) {
                model.close()
                throw e
            }
        }
    }
}
