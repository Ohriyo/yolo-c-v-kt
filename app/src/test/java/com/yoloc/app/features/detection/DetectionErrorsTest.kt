package com.yoloc.app.features.detection

import com.yoloc.app.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** RN: __tests__/scan.logic.test.ts (detection errors). */
class DetectionErrorsTest {
    private suspend fun failureOf(block: suspend () -> Unit): DetectionError {
        try {
            block()
        } catch (e: DetectionError) {
            return e
        }
        fail("Expected a DetectionError")
        error("unreachable")
    }

    @Test
    fun tagsFailuresWithTheStageTheyHappenedIn() = runBlocking {
        val e = failureOf { inStage(DetectionErrorKind.Image) { throw IllegalStateException("bad") } }
        assertEquals(DetectionErrorKind.Image, e.kind)
        assertEquals("bad", e.message)
    }

    @Test
    fun keepsTheFirstStageWhenErrorsAreNested() = runBlocking {
        val e = failureOf {
            inStage(DetectionErrorKind.Inference) { inStage(DetectionErrorKind.Image) { throw IllegalStateException("x") } }
        }
        assertEquals(DetectionErrorKind.Image, e.kind)
    }

    @Test
    fun recognizesOutOfMemoryInAnyStage() {
        assertEquals(
            DetectionErrorKind.Memory,
            DetectionError(DetectionErrorKind.Inference, RuntimeException("java.lang.OutOfMemoryError")).kind,
        )
        assertEquals(DetectionErrorKind.Memory, DetectionError(DetectionErrorKind.Image, OutOfMemoryError()).kind)
        assertEquals(DetectionErrorKind.Memory, describeDetectionError(RuntimeException("std::bad_alloc")).kind)
    }

    @Test
    fun describesEachKindWithItsOwnTitleAndNextStep() {
        val titles = mapOf(
            DetectionErrorKind.Image to R.string.processing_image_error_title,
            DetectionErrorKind.Model to R.string.processing_model_error_title,
            DetectionErrorKind.Inference to R.string.processing_inference_error_title,
            DetectionErrorKind.Memory to R.string.processing_memory_error_title,
        )
        for ((kind, title) in titles) {
            val view = describeDetectionError(DetectionError(kind, RuntimeException("x")))
            assertEquals(kind, view.kind)
            assertEquals(title, view.title)
        }
    }

    @Test
    fun treatsUnknownErrorsAsAnAnalysisFailure() {
        assertEquals(R.string.processing_inference_error_title, describeDetectionError(RuntimeException("weird")).title)
    }
}
