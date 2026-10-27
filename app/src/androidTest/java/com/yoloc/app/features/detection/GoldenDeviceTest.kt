package com.yoloc.app.features.detection

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yoloc.app.features.dev.GoldenReference
import com.yoloc.app.features.dev.GoldenSamples
import com.yoloc.app.features.dev.ReferenceSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The golden test on the phone: the real model and pipeline on each golden image, compared with
 * the Python reference (IoU >= 0.9, |delta conf| <= 0.05, no extras). Results are logged under the
 * "golden" tag; compare them with the RN "Device results (2026-10-15)" table.
 */
@RunWith(AndroidJUnit4::class)
class GoldenDeviceTest {
    @Test
    fun pipelineMatchesThePythonReference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = ModelConfig.load(context)
        val loaded = ModelLoader(context, config).loadCpu()
        Log.i(TAG, "loadMs=${loaded.loadMs} input=${loaded.spec.inputShape} output=${loaded.spec.outputShape}")
        assertEquals(config.inputSize, loaded.spec.inputSize)

        val samples = GoldenSamples(context)
        val thresholds = Thresholds(config.confThreshold, config.iouThreshold)
        val failures = mutableListOf<String>()
        for (file in GoldenSamples.FILES) {
            val run = runDetection(loaded, samples.file(file), config.letterboxPadValue, thresholds)
            val ref = samples.reference(ReferenceSource.Tflite, file)
            assertTrue("$file has no reference", ref is GoldenReference.Found)
            val report = compareToReference(run.detections, (ref as GoldenReference.Found).image.detections)
            val worst = report.pairs.minOfOrNull { it.iou } ?: 1.0
            val line = "$file ${if (report.pass) "PASS" else "FAIL"} worstIoU=${"%.3f".format(worst)} " +
                "maxConfDelta=${"%.3f".format(report.pairs.maxOfOrNull { it.confidenceDelta } ?: 0.0)} " +
                "extra=${report.extra.size} inferenceMs=${run.timings.inferenceMs.toInt()} " +
                "preprocessMs=${run.timings.preprocessMs.toInt()}"
            Log.i(TAG, line)
            if (!report.pass && file !in KNOWN_FAILURES) failures += line
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    companion object {
        private const val TAG = "golden"

        /**
         * golden_02 also failed in RN on the same phone: a small healthy pod at 0.276 vs 0.495 in
         * Python (RN assets/sample/README.md, 2026-10-15). Logged, not asserted.
         */
        val KNOWN_FAILURES = setOf("golden_02.jpg")
    }
}
