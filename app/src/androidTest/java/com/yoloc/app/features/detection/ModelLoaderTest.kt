package com.yoloc.app.features.detection

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yoloc.app.features.dev.GoldenReference
import com.yoloc.app.features.dev.GoldenSamples
import com.yoloc.app.features.dev.ReferenceSource
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The model's lifecycle on the phone: prepared on request, released, and loaded again. Timings are
 * logged under the "model-cache" tag. On the TECNO, run each method on its own (docs/PARITY.md).
 */
@RunWith(AndroidJUnit4::class)
class ModelLoaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = ModelConfig.load(context)
    private val samples = GoldenSamples(context)
    private val image = GoldenSamples.FILES[0]
    private val atReference = DoubleArray(config.classes.size) { GoldenSamples.REFERENCE_CONF }

    private fun reference() =
        (samples.reference(ReferenceSource.Tflite, image) as GoldenReference.Found).image.detections

    private fun cacheDir() = File(context.codeCacheDir, ModelLoader.GPU_CACHE_DIR)

    private suspend fun timedLoad(loader: ModelLoader): Long {
        val start = SystemClock.elapsedRealtime()
        loader.loadDetector()
        return SystemClock.elapsedRealtime() - start
    }

    @Test
    fun theSecondLoadUsesTheProgramCacheAndGivesTheSameDetections() = runBlocking {
        cacheDir().deleteRecursively()
        val loader = ModelLoader(context, config)

        val coldMs = timedLoad(loader)
        val cold = loader.withDetector { it.detect(samples.file(image), atReference) }
        val cacheBytes = cacheDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
        loader.release().join()

        val warmMs = timedLoad(loader)
        val warm = loader.withDetector { it.detect(samples.file(image), atReference) }
        loader.release().join()
        val warmAgainMs = timedLoad(loader)
        loader.release().join()

        Log.i(TAG, "accelerator=gpu coldMs=$coldMs warmMs=$warmMs warmAgainMs=$warmAgainMs cacheBytes=$cacheBytes")
        assertTrue("Cache files were written", cacheBytes > 0)
        assertTrue("Cold ${cold.detections}", compareToReferenceBoxes(cold.detections, reference()).pass)
        assertTrue("Warm ${warm.detections}", compareToReferenceBoxes(warm.detections, reference()).pass)
    }

    @Test
    fun prepareEndsReadyInTheBackgroundAndReleaseGoesBackToIdle() = runBlocking {
        val loader = ModelLoader(context, config)
        assertEquals(ModelState.Idle, loader.state.value)

        loader.prepare()
        assertEquals(ModelState.Preparing, loader.state.value)
        loader.prepare() // Already under way: no second load.
        withTimeout(TIMEOUT_MS) { loader.state.first { it == ModelState.Ready } }

        val before = loader.generation.value
        loader.release().join()
        assertEquals(ModelState.Idle, loader.state.value)
        assertNotEquals(before, loader.generation.value)

        loader.prepare()
        withTimeout(TIMEOUT_MS) { loader.state.first { it == ModelState.Ready } }
        loader.release().join()
    }

    @Test
    fun releaseWaitsForARunningScanAndTheNextScanLoadsAgain() = runBlocking {
        val loader = ModelLoader(context, config)
        loader.loadDetector()

        val scan = async { loader.withDetector { it.detect(samples.file(image), atReference) } }
        val release = loader.release()
        assertTrue(compareToReferenceBoxes(scan.await().detections, reference()).pass)
        release.join()

        val again = loader.withDetector { it.detect(samples.file(image), atReference) }
        assertTrue(compareToReferenceBoxes(again.detections, reference()).pass)
        loader.release().join()
    }

    @Test
    fun aRunnerHeldAcrossAReleaseFailsCleanlyInsteadOfCrashing() = runBlocking {
        val loader = ModelLoader(context, config)
        val runner = loader.loadGpu().runner
        loader.release().join()
        val blank = FloatArray(config.inputShape.fold(1) { a, b -> a * b })
        val failure = runCatching { runner.run(blank) }.exceptionOrNull()
        assertTrue("$failure", failure is IllegalStateException)
    }

    private companion object {
        const val TAG = "model-cache"
        const val TIMEOUT_MS = 60_000L
    }
}
