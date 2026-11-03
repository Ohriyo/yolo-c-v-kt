package com.yoloc.app.features.detection

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.CRC32

class LoadedModel(
    private val interpreter: Interpreter,
    val spec: ModelSpec,
    val loadMs: Long,
    override val backend: String = "cpu",
) : LiveRunner, AutoCloseable {
    private val outputSize = spec.outputShape.fold(1) { a, b -> a * b }

    /** Interpreter is not thread-safe; one run at a time. Throws once closed. */
    override fun run(input: FloatArray): FloatArray = synchronized(interpreter) {
        val inBuf = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
        inBuf.asFloatBuffer().put(input)
        val outBuf = ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
        interpreter.run(inBuf, outBuf)
        outBuf.rewind()
        FloatArray(outputSize).also { outBuf.asFloatBuffer().get(it) }
    }

    /** Waits for a run in progress. */
    override fun close() = synchronized(interpreter) { interpreter.close() }
}

/** A GPU runner and how long it took to create (compile for the GPU). */
class LoadedGpu(val runner: CompiledModelRunner, val loadMs: Long)

/** The photo pipeline, for the Scan screen's "Preparing model…". */
enum class ModelState { Idle, Preparing, Ready, Failed }

/**
 * Loads the bundled model named in model_config.json when asked and inspects its real tensors.
 * Nothing compiles at launch: the Scan screen calls [prepare], and any scan that gets here first
 * waits for the same load. [release] frees it all when the app goes to the background or memory
 * runs out; the next use loads again, helped by the GPU program cache. A failed load is not cached,
 * so the caller can retry. Live and the Model test also use the CPU Interpreter with
 * [PARITY_THREADS] threads and XNNPACK off, which is faster here than CompiledModel's CPU path.
 */
class ModelLoader(
    private val context: Context,
    private val config: ModelConfig,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()
    private val cpu = mutableMapOf<Boolean, LoadedModel>()
    private var gpu: LoadedGpu? = null
    private var detector: YoloDetector? = null
    private var gpuCache: GpuProgramCache? = null

    private val _state = MutableStateFlow(ModelState.Idle)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _generation = MutableStateFlow(0)

    /** Moves on when the model is released, so whoever holds a runner knows to load again. */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** Compile and warm up in the background. Does nothing if that is done or under way. */
    fun prepare() {
        var start = false
        _state.update {
            start = it == ModelState.Idle || it == ModelState.Failed
            if (start) ModelState.Preparing else it
        }
        if (!start) return
        scope.launch {
            try {
                loadDetector()
            } catch (e: Exception) {
                // Already in the state; a scan reports the error when it tries to load.
                Log.w(TAG, "Could not prepare the model", e)
            }
        }
    }

    /** The photo pipeline: GPU with CPU fallback. Shares its compiled GPU model with [loadGpu]. */
    suspend fun loadDetector(): YoloDetector = mutex.withLock { openDetector() }

    /** Runs `block` with the detector held, so [release] cannot take it away part way through. */
    suspend fun <R> withDetector(block: suspend (YoloDetector) -> R): R = mutex.withLock { block(openDetector()) }

    /** Frees every loaded model once whatever is running finishes. Safe to call at any time. */
    fun release(): Job = scope.launch {
        mutex.withLock {
            val loaded = detector != null || gpu != null || cpu.isNotEmpty()
            detector?.close()
            gpu?.runner?.close()
            cpu.values.forEach { it.close() }
            detector = null
            gpu = null
            cpu.clear()
            _state.value = ModelState.Idle
            if (loaded) {
                _generation.update { it + 1 }
                Log.i(TAG, "Released the model")
            }
        }
    }

    /** Call with [mutex] held. */
    private suspend fun openDetector(): YoloDetector = detector ?: try {
        _state.value = ModelState.Preparing
        YoloDetector.create(context.assets, config) { accelerator ->
            if (accelerator == Accelerator.GPU) {
                openGpu().runner
            } else {
                YoloDetector.openChecked(context.assets, config, accelerator)
            }
        }.also {
            detector = it
            _state.value = ModelState.Ready
        }
    } catch (e: Throwable) {
        _state.value = ModelState.Failed
        throw e
    }

    /**
     * The parity run: XNNPACK off and [PARITY_THREADS] threads, which is what
     * react-native-fast-tflite's default C API options run on this phone (docs/PARITY.md, "CPU
     * inference gap"). The Java Interpreter's own defaults turn XNNPACK on, and on the TECNO BG6
     * XNNPACK ignores the thread count (about 2.2 s instead of 1.2 s). `liteRtDefaults` keeps those
     * defaults: the separate, labelled experiment. Cached per choice.
     */
    suspend fun loadCpu(liteRtDefaults: Boolean = false): LoadedModel = mutex.withLock {
        cpu[liteRtDefaults] ?: inStage(DetectionErrorKind.Model) {
            withContext(Dispatchers.IO) {
                val start = SystemClock.elapsedRealtime()
                val options = Interpreter.Options().apply {
                    if (!liteRtDefaults) setUseXNNPACK(false).setNumThreads(PARITY_THREADS)
                }
                val interpreter = Interpreter(mapAsset(config.file), options)
                val inputs = (0 until interpreter.inputTensorCount).map { interpreter.getInputTensor(it).info() }
                val outputs = (0 until interpreter.outputTensorCount).map { interpreter.getOutputTensor(it).info() }
                val spec = try {
                    describeModel(inputs, outputs, config)
                } catch (e: IllegalArgumentException) {
                    interpreter.close()
                    throw e
                }
                val backend = if (liteRtDefaults) "cpu-xnnpack" else "cpu"
                LoadedModel(interpreter, spec, SystemClock.elapsedRealtime() - start, backend)
            }
        }.also { cpu[liteRtDefaults] = it }
    }

    /** GPU via CompiledModel (live mode). Throws if the GPU cannot run the model; not cached then. */
    suspend fun loadGpu(): LoadedGpu = mutex.withLock {
        inStage(DetectionErrorKind.Model) { withContext(Dispatchers.IO) { openGpu() } }
    }

    /** Call with [mutex] held, off the main thread. */
    private fun openGpu(): LoadedGpu = gpu ?: run {
        val start = SystemClock.elapsedRealtime()
        val cache = gpuCache ?: programCache().also { gpuCache = it }
        val runner = YoloDetector.openChecked(context.assets, config, Accelerator.GPU, cache)
        LoadedGpu(runner, SystemClock.elapsedRealtime() - start).also {
            gpu = it
            Log.i(TAG, "GPU model ready in ${it.loadMs} ms")
        }
    }

    /** Google's advice: codeCacheDir, private to the app and cleared by the system on an update. */
    private fun programCache(): GpuProgramCache {
        val directory = File(context.codeCacheDir, GPU_CACHE_DIR).apply { mkdirs() }
        val crc = CRC32()
        context.assets.open(config.file).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return GpuProgramCache(directory.path, "${config.id}-${crc.value.toString(16)}")
    }

    companion object {
        private const val TAG = "ModelLoader"
        const val GPU_CACHE_DIR = "litert_gpu"

        /** RN's effective CPU thread count (LiteRT 1.4.0's C API default on this phone). */
        const val PARITY_THREADS = 4
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
