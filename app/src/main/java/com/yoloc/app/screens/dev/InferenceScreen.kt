package com.yoloc.app.screens.dev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.features.dev.GoldenSamples

/**
 * Dev-only Model test (RN: app/dev/inference.tsx). Plain Material widgets for now: the app's
 * components and ImageWithBoxes arrive in Step 5.
 */
@Composable
fun InferenceScreen(
    modifier: Modifier = Modifier,
    vm: DevInferenceViewModel = viewModel(factory = DevInferenceViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val loaded = s.loaded
    val caption = MaterialTheme.typography.bodySmall

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Model test (dev)", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(if (loaded != null) "Model loaded" else if (s.loadFailed) "Model failed to load." else "Loading model...")
                if (loaded != null) {
                    Text(
                        s.backendLoadMs.entries.joinToString("\n") { (b, ms) -> "${b.label}: loaded in $ms ms" } +
                            "\nInput ${loaded.spec.inputShape} ${loaded.spec.inputLayout}\n" +
                            "Output ${loaded.spec.outputShape} ${loaded.spec.outputLayout}",
                        style = caption,
                    )
                }
            }
        }
        s.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

        Text("Backend", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DevBackend.entries.forEach { b ->
                val enabled = loaded != null && !s.busy
                if (b == s.backend) {
                    Button(onClick = { vm.selectBackend(b) }, enabled = enabled) { Text(b.label) }
                } else {
                    OutlinedButton(onClick = { vm.selectBackend(b) }, enabled = enabled) { Text(b.label) }
                }
            }
        }

        Text("Sample", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldenSamples.FILES.forEachIndexed { i, _ ->
                if (i == s.selected) {
                    Button(onClick = { vm.select(i) }) { Text("${i + 1}") }
                } else {
                    OutlinedButton(onClick = { vm.select(i) }) { Text("${i + 1}") }
                }
            }
        }
        val ready = loaded != null && !s.busy
        Button(onClick = vm::runSelected, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.busy) "Running..." else "Run model")
        }
        OutlinedButton(onClick = vm::runBenchmark, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Benchmark (${DevInferenceViewModel.BENCHMARK_RUNS} runs)")
        }
        OutlinedButton(onClick = vm::runAll, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Run all golden images")
        }
        s.benchmark?.let { Text(it, style = caption) }
        s.allRuns.forEach { Text(it, style = caption) }

        s.result?.let { r ->
            val run = r.run
            Box(Modifier.fillMaxWidth().aspectRatio(run.imageWidth.toFloat() / run.imageHeight)) {
                Image(r.photo.asImageBitmap(), r.file, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(Modifier.fillMaxSize()) {
                    run.detections.forEach { d ->
                        drawRect(
                            color = classColor(vm.className(d.classId)),
                            topLeft = Offset((d.x * size.width).toFloat(), (d.y * size.height).toFloat()),
                            size = Size((d.w * size.width).toFloat(), (d.h * size.height).toFloat()),
                            style = Stroke(3.dp.toPx()),
                        )
                    }
                }
            }
            Text(
                "[${r.backend}] ${r.file} ${run.imageWidth}x${run.imageHeight}, boxes in ${run.boxUnits}, " +
                    "${run.candidateCount} candidates -> ${run.detections.size} after NMS\n" +
                    "Verdict ${r.verdict.verdict.key}${if (r.verdict.lowConfidence) " (low confidence)" else ""}\n" +
                    "Preprocess ${ms(run.timings.preprocessMs)}, inference ${ms(run.timings.inferenceMs)}, " +
                    "postprocess ${ms(run.timings.postprocessMs)}, total ${ms(run.timings.totalMs)}",
                style = caption,
            )
            run.detections.forEach { d ->
                Text(
                    "${vm.className(d.classId)} ${"%.1f".format(d.confidence * 100)}%  " +
                        "x ${"%.3f".format(d.x)} y ${"%.3f".format(d.y)} " +
                        "w ${"%.3f".format(d.w)} h ${"%.3f".format(d.h)}",
                    style = caption,
                )
            }
            Text("Golden vs .tflite in Python: ${r.tflite.summary()}", style = caption)
            Text("Golden vs best.pt: ${r.pt.summary()}", style = caption)
        }
    }
}

/** Placeholder box colors until the status palette is ported (Step 5.1). */
private fun classColor(key: String) = when (key) {
    "early_stage" -> Color(0xFFE0A030)
    "fully_infected_pod" -> Color(0xFFC0392B)
    else -> Color(0xFF2E8B57)
}
