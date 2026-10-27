package com.yoloc.app.features.detection

import androidx.annotation.StringRes
import com.yoloc.app.R
import kotlin.coroutines.cancellation.CancellationException

/** Which pipeline stage failed, so the user gets a message that says what to do next. */
enum class DetectionErrorKind { Image, Model, Inference, Memory }

class DetectionError(kind: DetectionErrorKind, cause: Throwable) : Exception(cause.message, cause) {
    val kind: DetectionErrorKind =
        if (cause is OutOfMemoryError || isOutOfMemory(cause.message)) DetectionErrorKind.Memory else kind
}

private val outOfMemoryPattern =
    Regex("""out ?of ?memory|OutOfMemoryError|\bOOM\b|allocation failed|bad_alloc""", RegexOption.IGNORE_CASE)

private fun isOutOfMemory(message: String?) = message != null && outOfMemoryPattern.containsMatchIn(message)

/** Runs `task`, tagging any failure with the stage it happened in. */
suspend fun <T> inStage(kind: DetectionErrorKind, task: suspend () -> T): T =
    try {
        task()
    } catch (e: CancellationException) {
        throw e // never wrap coroutine cancellation
    } catch (e: DetectionError) {
        throw e
    } catch (e: Throwable) {
        // Throwable, not Exception: OutOfMemoryError is an Error.
        throw DetectionError(kind, e)
    }

data class DetectionErrorView(
    val kind: DetectionErrorKind,
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
)

/** User-facing title and next step for any error thrown while analyzing a photo. */
fun describeDetectionError(error: Throwable): DetectionErrorView {
    val kind = when {
        error is DetectionError -> error.kind
        error is OutOfMemoryError || isOutOfMemory(error.message) -> DetectionErrorKind.Memory
        else -> DetectionErrorKind.Inference
    }
    return when (kind) {
        DetectionErrorKind.Image ->
            DetectionErrorView(kind, R.string.processing_image_error_title, R.string.processing_image_error_body)
        DetectionErrorKind.Model ->
            DetectionErrorView(kind, R.string.processing_model_error_title, R.string.processing_model_error_body)
        DetectionErrorKind.Memory ->
            DetectionErrorView(kind, R.string.processing_memory_error_title, R.string.processing_memory_error_body)
        DetectionErrorKind.Inference ->
            DetectionErrorView(kind, R.string.processing_inference_error_title, R.string.processing_inference_error_body)
    }
}
