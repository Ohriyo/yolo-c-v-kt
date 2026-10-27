package com.yoloc.app.features.detection

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LoadedModel(
    private val interpreter: Interpreter,
    val spec: ModelSpec,
    val loadMs: Long,
    override val backend: String = "cpu",
) : LiveRunner {
    private val outputSize = spec.outputShape.fold(1) { a, b -> a * b }

    /** Interpreter is not thread-safe; one run at a time. */
    override fun run(input: FloatArray): FloatArray = synchronized(interpreter) {
        val inBuf = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
        inBuf.asFloatBuffer().put(input)
        val outBuf = ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
        interpreter.run(inBuf, outBuf)
        outBuf.rewind()
        FloatArray(outputSize).also { outBuf.asFloatBuffer().get(it) }
    }
}

/** A GPU runner and how long it took to create (compile for the GPU). */
class LoadedGpu(val runner: GpuRunner, val loadMs: Long)

/**
 * Loads the bundled model named in model.config.json once and inspects its real tensors. Throws a
 * readable error if the model does not match the config. A failed load is not cached, so the
 * caller can retry. Photo scans use the CPU Interpreter with default options (react-native-fast-tflite
 * uses defaults too): it is the path the golden test verifies.
 */
class ModelLoader(private val context: Context, private val config: ModelConfig) {
    private val mutex = Mutex()
    private val cpu = mutableMapOf<Int?, LoadedModel>()
    private var gpu: LoadedGpu? = null

    private val assetPath get() = "models/${config.file}"

    /**
     * `numThreads` null keeps Interpreter.Options at defaults (the parity run). Other values are
     * the separate, labelled thread-count experiment, cached per value.
     */
    suspend fun loadCpu(numThreads: Int? = null): LoadedModel = mutex.withLock {
        cpu[numThreads] ?: inStage(DetectionErrorKind.Model) {
            withContext(Dispatchers.IO) {
                val start = SystemClock.elapsedRealtime()
                val options = Interpreter.Options().apply { numThreads?.let { setNumThreads(it) } }
                val interpreter = Interpreter(mapAsset(assetPath), options)
                val inputs = (0 until interpreter.inputTensorCount).map { interpreter.getInputTensor(it).info() }
                val outputs = (0 until interpreter.outputTensorCount).map { interpreter.getOutputTensor(it).info() }
                val spec = try {
                    describeModel(inputs, outputs, config)
                } catch (e: IllegalArgumentException) {
                    interpreter.close()
                    throw e
                }
                val backend = if (numThreads == null) "cpu" else "cpu-$numThreads"
                LoadedModel(interpreter, spec, SystemClock.elapsedRealtime() - start, backend)
            }
        }.also { cpu[numThreads] = it }
    }

    /** GPU via CompiledModel (live mode). Throws if the GPU cannot run the model; not cached then. */
    suspend fun loadGpu(): LoadedGpu = mutex.withLock {
        gpu ?: inStage(DetectionErrorKind.Model) {
            withContext(Dispatchers.IO) {
                val start = SystemClock.elapsedRealtime()
                val runner = GpuRunner.create(context.assets, assetPath)
                LoadedGpu(runner, SystemClock.elapsedRealtime() - start)
            }
        }.also { gpu = it }
    }

    /** Needs the .tflite stored uncompressed (androidResources.noCompress), or openFd() fails. */
    private fun mapAsset(path: String): MappedByteBuffer =
        context.assets.openFd(path).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
}

private fun Tensor.info() = TensorInfo(name(), dataType().name.lowercase(), shape().toList())
