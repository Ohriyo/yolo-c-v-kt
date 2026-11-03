package com.yoloc.app.features.detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litert.Accelerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The v3 golden test on the phone: the real model and pipeline on every reference image, compared
 * with reference/expected_tflite.json (same count and classes, confidence within 0.05, each box
 * coordinate within 0.02). The table and timings are logged under the "golden-v3" tag.
 */
@RunWith(AndroidJUnit4::class)
class YoloDetectorGoldenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext = instrumentation.targetContext
    private val testAssets = instrumentation.context.assets

    private val references: List<ReferenceImage> =
        testAssets.open("reference/expected_tflite.json").use {
            ReferenceImage.parseAll(it.readBytes().decodeToString())
        }

    private fun referenceFile(name: String): File {
        val dest = File(appContext.cacheDir, "reference/$name")
        dest.parentFile?.mkdirs()
        testAssets.open("reference/images/$name").use { input -> dest.outputStream().use { input.copyTo(it) } }
        return dest
    }

    /** The GPU with CPU fallback: what the app gets. */
    @Test
    fun defaultAcceleratorMatchesTheReference() = goldenRun(YoloDetector.DEFAULT_ACCELERATORS)

    /** Forces the fallback path, so it is verified even on a phone whose GPU works. */
    @Test
    fun cpuMatchesTheReference() = goldenRun(listOf(Accelerator.CPU))

    private fun goldenRun(accelerators: List<Accelerator>) = runBlocking {
        val detector = YoloDetector.create(appContext.assets, ModelConfig.load(appContext), accelerators)
        detector.use {
            val config = detector.config
            log("=== accelerator=${detector.accelerator} requested=$accelerators loadMs=${detector.loadMs}")
            // The reference was generated at one threshold; this test alone overrides the config's.
            val thresholds = DoubleArray(config.classes.size) { GOLDEN_CONF }
            references.forEach {
                assertEquals(it.image, GOLDEN_CONF, it.confThreshold, 0.0)
                assertEquals(it.image, config.iouThreshold, it.iouThreshold, 0.0)
            }

            // Warm-up, not timed.
            repeat(WARM_UP_RUNS) { detector.detect(referenceFile(references.first().image), thresholds) }

            val failures = ArrayList<String>()
            val medians = ArrayList<Double>()
            for (ref in references) {
                val file = referenceFile(ref.image)
                val runs = List(TIMED_RUNS) { detector.detect(file, thresholds) }
                val run = runs.first()
                assertEquals(ref.image, ref.width, run.imageWidth)
                assertEquals(ref.image, ref.height, run.imageHeight)
                assertEquals(ref.image, BoxUnits.Normalized, run.boxUnits)

                val comparison = compareToReferenceBoxes(run.detections, ref.detections)
                val inference = median(runs.map { it.timings.inferenceMs })
                medians += inference
                logTable(ref, run, comparison, config.classes, inference, median(runs.map { it.timings.totalMs }))
                if (!comparison.pass) failures += ref.image

                // Every timed run has to give the same answer, not just the first.
                for (other in runs) {
                    if (!compareToReferenceBoxes(other.detections, ref.detections).pass) {
                        failures += "${ref.image} (a repeat run differed)"
                        break
                    }
                }
            }
            log("=== accelerator=${detector.accelerator} medianInferenceMs=${format1(median(medians))} failures=$failures")
            assertTrue("Did not match the reference: $failures", failures.isEmpty())
        }
    }

    /**
     * No reference image has an EXIF orientation, so the table cannot catch a missing rotation. This
     * stores a reference image sideways with a tag saying so; it must give the upright image's boxes.
     * PNG, because re-encoding as JPEG changes the pixels enough to move this image's confidences.
     */
    @Test
    fun exifRotationIsAppliedBeforeLetterboxing() = runBlocking {
        val ref = references.first { it.width != it.height && it.detections.isNotEmpty() }
        val upright = BitmapFactory.decodeFile(referenceFile(ref.image).path)
        val sideways = Bitmap.createBitmap(
            upright, 0, 0, upright.width, upright.height, Matrix().apply { postRotate(-90f) }, true,
        )
        val rotated = File(appContext.cacheDir, "reference/exif_rotated.png")
        rotated.outputStream().use { assertTrue(sideways.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        ExifInterface(rotated).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        YoloDetector.create(appContext.assets, ModelConfig.load(appContext)).use { detector ->
            val thresholds = DoubleArray(detector.config.classes.size) { GOLDEN_CONF }
            val run = detector.detect(rotated, thresholds)
            assertEquals(ref.width, run.imageWidth)
            assertEquals(ref.height, run.imageHeight)
            val comparison = compareToReferenceBoxes(run.detections, ref.detections)
            logTable(ref, run, comparison, detector.config.classes, run.timings.inferenceMs, run.timings.totalMs)
            assertTrue("EXIF-rotated copy of ${ref.image} did not match", comparison.pass)
        }
    }

    private fun logTable(
        ref: ReferenceImage,
        run: DetectionRun,
        comparison: ReferenceComparison,
        classes: List<String>,
        inferenceMs: Double,
        totalMs: Double,
    ) {
        log(
            "${ref.image.take(MAX_NAME)} | ${ref.width}x${ref.height} | expected ${ref.detections.size} | " +
                "actual ${run.detections.size} | ${if (comparison.pass) "PASS" else "FAIL"} | " +
                "inference ${format1(inferenceMs)} ms | total ${format1(totalMs)} ms",
        )
        for (m in comparison.matches) {
            val c = classes[m.expected.classId]
            log(
                "    ${if (m.ok) "ok  " else "BAD "} $c exp=${format4(m.expected.confidence)} " +
                    "act=${m.actual?.confidence?.let(::format4) ?: "none"} " +
                    "dConf=${format4(m.confidenceDelta)} dCoord=${format4(m.coordinateDelta)} iou=${format4(m.iou)}",
            )
        }
        for (d in comparison.extra) {
            log("    EXTRA ${classes[d.classId]} act=${format4(d.confidence)} x=${format4(d.x)} y=${format4(d.y)} w=${format4(d.w)} h=${format4(d.h)}")
        }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun log(line: String) {
        Log.i(TAG, line)
    }

    private fun format1(v: Double) = "%.1f".format(v)

    private fun format4(v: Double) = "%.4f".format(v)

    private companion object {
        const val TAG = "golden-v3"
        const val GOLDEN_CONF = 0.25
        const val WARM_UP_RUNS = 3
        const val TIMED_RUNS = 10
        const val MAX_NAME = 28
    }
}
