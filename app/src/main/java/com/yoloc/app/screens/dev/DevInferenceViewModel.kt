package com.yoloc.app.screens.dev

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.YoloCApp
import com.yoloc.app.features.detection.DetectionRun
import com.yoloc.app.features.detection.GoldenReport
import com.yoloc.app.features.detection.LiveRunner
import com.yoloc.app.features.detection.LoadedModel
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.detection.ModelLoader
import com.yoloc.app.features.detection.ModelSpec
import com.yoloc.app.features.detection.Thresholds
import com.yoloc.app.features.detection.VerdictInput
import com.yoloc.app.features.detection.VerdictSummary
import com.yoloc.app.features.detection.classCatalog
import com.yoloc.app.features.detection.compareToReference
import com.yoloc.app.features.detection.runDetection
import com.yoloc.app.features.detection.summarizeDetections
import com.yoloc.app.features.dev.GoldenReference
import com.yoloc.app.features.dev.GoldenSamples
import com.yoloc.app.features.dev.ReferenceSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Golden result against one reference: a report, or why there is none. */
sealed interface GoldenCheck {
    data object Pending : GoldenCheck
    data object Missing : GoldenCheck
    data class Checked(val report: GoldenReport) : GoldenCheck
}

/**
 * Where the Model test runs inference. Cpu (default options) is the parity run; Cpu4Threads is the
 * guide's separate setNumThreads(4) experiment; Gpu is the live-mode backend (CompiledModel).
 */
enum class DevBackend(val label: String) {
    Cpu("CPU (default)"),
    Cpu4Threads("CPU, 4 threads"),
    Gpu("GPU"),
}

data class SampleRun(
    val file: String,
    val backend: String,
    val photo: Bitmap,
    val run: DetectionRun,
    val verdict: VerdictSummary,
    val tflite: GoldenCheck,
    val pt: GoldenCheck,
)

data class DevInferenceState(
    /** The default CPU model: its spec describes the tensors for every backend. */
    val loaded: LoadedModel? = null,
    val loadFailed: Boolean = false,
    val error: String? = null,
    val backend: DevBackend = DevBackend.Cpu,
    /** Load (or GPU compile) time per backend once it has been loaded. */
    val backendLoadMs: Map<DevBackend, Long> = emptyMap(),
    val selected: Int = 0,
    val busy: Boolean = false,
    val result: SampleRun? = null,
    val benchmark: String? = null,
    /** One line per golden image after "Run all": the table to compare with RN's device results. */
    val allRuns: List<String> = emptyList(),
)

/**
 * RN: app/dev/inference.tsx. Runs the real model on the bundled golden samples, shows timings and
 * tensor shapes, and compares against the Python reference. Every result is also logged
 * (Logcat tag "inference"), as RN logs to Metro.
 */
class DevInferenceViewModel(
    private val config: ModelConfig,
    private val loader: ModelLoader,
    private val samples: GoldenSamples,
    /** Severity by class key, from the class catalog. */
    private val severities: Map<String, Int>,
) : ViewModel() {
    private val thresholds = Thresholds(config.confThreshold, config.iouThreshold)

    private val _state = MutableStateFlow(DevInferenceState())
    val state: StateFlow<DevInferenceState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val m = loader.loadCpu()
                _state.update { it.copy(loaded = m, backendLoadMs = mapOf(DevBackend.Cpu to m.loadMs)) }
                Log.i(
                    TAG,
                    "model file=${config.file} loadMs=${m.loadMs} input=${m.spec.inputShape} " +
                        "${m.spec.inputLayout} output=${m.spec.outputShape} ${m.spec.outputLayout}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "model failed to load", e)
                _state.update { it.copy(loadFailed = true, error = e.message ?: e.toString()) }
            }
        }
    }

    fun className(classId: Int) = config.classes.getOrNull(classId) ?: "class $classId"

    fun select(index: Int) = _state.update { it.copy(selected = index) }

    /** Loads the backend on first use. A GPU that cannot run the model leaves the CPU selected. */
    fun selectBackend(backend: DevBackend) {
        if (backend == _state.value.backend || _state.value.busy || _state.value.loaded == null) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val loadMs = when (backend) {
                    DevBackend.Cpu -> loader.loadCpu().loadMs
                    DevBackend.Cpu4Threads -> loader.loadCpu(numThreads = 4).loadMs
                    DevBackend.Gpu -> loader.loadGpu().loadMs
                }
                Log.i(TAG, "backend ${backend.label} loadMs=$loadMs")
                _state.update { it.copy(backend = backend, backendLoadMs = it.backendLoadMs + (backend to loadMs)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "backend ${backend.label} failed to load", e)
                _state.update { it.copy(error = "${backend.label} could not start: ${e.message ?: e}") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun runSelected() = guarded { runner, spec ->
        val sampleRun = runSample(runner, spec, GoldenSamples.FILES[_state.value.selected])
        _state.update { it.copy(result = sampleRun) }
    }

    fun runAll() = guarded { runner, spec ->
        val lines = GoldenSamples.FILES.map { file ->
            val s = runSample(runner, spec, file)
            "[${runner.backend}] $file  inference ${ms(s.run.timings.inferenceMs)}, total ${ms(s.run.timings.totalMs)}  " +
                "tflite ${s.tflite.summary()}  pt ${s.pt.summary()}"
        }
        _state.update { it.copy(allRuns = lines) }
    }

    fun runBenchmark() = guarded { runner, spec ->
        val file = GoldenSamples.FILES[_state.value.selected]
        val runs = (1..BENCHMARK_RUNS).map {
            runDetection(runner, spec, samples.file(file), config.letterboxPadValue, thresholds)
        }
        val inference = runs.map { it.timings.inferenceMs }
        val total = runs.map { it.timings.totalMs }
        val text = "[${runner.backend}] $file, $BENCHMARK_RUNS runs: median inference ${ms(median(inference))}, " +
            "median total ${ms(median(total))} (inference: ${inference.joinToString { it.roundToInt().toString() }})"
        Log.i(TAG, "benchmark $text")
        _state.update { it.copy(benchmark = text) }
    }

    private suspend fun currentRunner(): LiveRunner = when (_state.value.backend) {
        DevBackend.Cpu -> loader.loadCpu()
        DevBackend.Cpu4Threads -> loader.loadCpu(numThreads = 4)
        DevBackend.Gpu -> loader.loadGpu().runner
    }

    private suspend fun runSample(runner: LiveRunner, spec: ModelSpec, file: String): SampleRun {
        val photoFile = withContext(Dispatchers.IO) { samples.file(file) }
        val run = runDetection(runner, spec, photoFile, config.letterboxPadValue, thresholds)
        val photo = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(photoFile.path) ?: error("Could not decode $file")
        }
        val verdict = summarizeDetections(
            run.detections.map { VerdictInput(severities[className(it.classId)] ?: 0, it.confidence) },
            config.lowConfidenceThreshold,
        )
        val sampleRun = SampleRun(
            file = file,
            backend = runner.backend,
            photo = photo,
            run = run,
            verdict = verdict,
            tflite = check(ReferenceSource.Tflite, file, run),
            pt = check(ReferenceSource.Pt, file, run),
        )
        Log.i(
            TAG,
            "run backend=${runner.backend} file=$file size=${run.imageWidth}x${run.imageHeight} " +
                "boxUnits=${run.boxUnits} candidates=${run.candidateCount} timings=${run.timings} " +
                "detections=${run.detections.map { "${className(it.classId)} $it" }} " +
                "golden tflite=${sampleRun.tflite.summary()} pt=${sampleRun.pt.summary()}",
        )
        return sampleRun
    }

    private suspend fun check(source: ReferenceSource, file: String, run: DetectionRun): GoldenCheck =
        when (val ref = withContext(Dispatchers.IO) { samples.reference(source, file) }) {
            GoldenReference.Pending -> GoldenCheck.Pending
            GoldenReference.Missing -> GoldenCheck.Missing
            is GoldenReference.Found -> GoldenCheck.Checked(compareToReference(run.detections, ref.image.detections))
        }

    private fun guarded(task: suspend (LiveRunner, ModelSpec) -> Unit) {
        val loaded = _state.value.loaded ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                task(currentRunner(), loaded.spec)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "failed", e)
                _state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        private const val TAG = "inference"
        const val BENCHMARK_RUNS = 5

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as YoloCApp
                DevInferenceViewModel(
                    config = app.container.modelConfig,
                    loader = app.container.modelLoader,
                    samples = GoldenSamples(app),
                    severities = classCatalog(app).mapValues { it.value.severity },
                )
            }
        }
    }
}

fun ms(v: Double) = "${v.roundToInt()} ms"

private fun median(values: List<Double>) = values.sorted()[values.size / 2]

/** "PASS: 3/3 matched, worst IoU 0.995, 0 extra", as RN's GoldenLine. */
fun GoldenCheck.summary(): String = when (this) {
    GoldenCheck.Pending -> "reference not added yet"
    GoldenCheck.Missing -> "no reference for this image"
    is GoldenCheck.Checked -> {
        val worst = report.pairs.minOfOrNull { it.iou } ?: 1.0
        "${if (report.pass) "PASS" else "FAIL"}: ${report.pairs.count { it.ok }}/${report.pairs.size} matched, " +
            "worst IoU ${"%.3f".format(worst)}, ${report.extra.size} extra"
    }
}
