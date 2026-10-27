package com.yoloc.app.features.camera

import android.util.Log
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

private const val TAG = "camera"

/**
 * 4:3 streams, nearest to [target] (RN: expo-camera's default 4:3 ratio, vision-camera's
 * `CommonResolutions.*_4_3`). Preview, analysis and capture use the same aspect, so they show the
 * same field of view.
 */
fun ratio43(target: Size? = null): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .apply {
        if (target != null) {
            setResolutionStrategy(ResolutionStrategy(target, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        }
    }
    .build()

/**
 * The back camera's preview with [others] bound to this screen's lifecycle while composed. CameraX
 * stops the camera when the screen stops and starts it again on return, which covers RN's
 * "only while focused and the app is in the foreground"; leaving composition unbinds it.
 * Fills [modifier] and crops the edges (RN: resizeMode "cover").
 *
 * @param onReady True once the preview has a surface (the camera can take a photo); false when
 *   the preview goes away.
 * @param onBindError Binding threw (no back camera, camera in use, ...).
 */
@Composable
fun BoundCamera(
    preview: Preview,
    others: List<UseCase>,
    modifier: Modifier = Modifier,
    onReady: (Boolean) -> Unit = {},
    onBindError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnBindError by rememberUpdatedState(onBindError)

    LaunchedEffect(preview, others, owner) {
        preview.setSurfaceProvider { request = it }
        val useCases = (listOf(preview) + others).toTypedArray()
        var provider: ProcessCameraProvider? = null
        try {
            provider = ProcessCameraProvider.awaitInstance(context)
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Camera failed to start", e)
            currentOnBindError(e)
        } finally {
            provider?.unbind(*useCases)
            preview.setSurfaceProvider(null)
            request = null
            currentOnReady(false)
        }
    }

    val surface = request ?: return
    LaunchedEffect(surface) { currentOnReady(true) }
    CameraXViewfinder(surfaceRequest = surface, modifier = modifier, contentScale = ContentScale.Crop)
}
