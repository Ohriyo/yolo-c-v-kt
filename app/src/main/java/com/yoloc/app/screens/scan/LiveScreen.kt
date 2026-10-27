package com.yoloc.app.screens.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.components.statusIcon
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.features.camera.BoundCamera
import com.yoloc.app.features.camera.ratio43
import com.yoloc.app.features.detection.Detection
import com.yoloc.app.features.detection.VerdictInput
import com.yoloc.app.features.detection.summarizeDetections
import com.yoloc.app.features.live.CameraMode
import com.yoloc.app.features.live.LiveStatsSnapshot
import com.yoloc.app.features.live.ModeSwitch
import com.yoloc.app.features.live.ViewBox
import com.yoloc.app.features.live.previewTransform
import com.yoloc.app.features.live.toViewBox
import com.yoloc.app.features.scans.verdictTitle
import com.yoloc.app.screens.appContainer
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.util.toFixed
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** RN: frame output CommonResolutions.HD_4_3 (768 × 1024 upright); CameraX sizes are in sensor orientation. */
private val ANALYSIS_SIZE = Size(1024, 768)

/** RN: photo output CommonResolutions.UHD_4_3 (3024 × 4032 upright). */
private val CAPTURE_SIZE = Size(4032, 3024)

/**
 * RN: app/scan/live.tsx (proposal 2026-10-17). The preview runs at the camera's own rate while
 * the analyzer (keep-only-latest, one background thread) runs the model on the newest frame as
 * often as it allows, throttled, and drops frames that arrive meanwhile. Live results are a
 * preview: Capture takes a full photo through the normal Preview -> Processing -> Result path.
 */
@Composable
fun LiveScreen(
    onClose: () -> Unit,
    onPhotoMode: () -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    vm: LiveViewModel = viewModel(factory = LiveViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cam = YoloCTheme.colors.camera
    // RN: useCameraPermission, which follows changes made in system settings.
    var hasPermission by remember { mutableStateOf(granted(context)) }
    LifecycleResumeEffect(Unit) {
        hasPermission = granted(context)
        onPauseOrDispose {}
    }
    var noCamera by remember { mutableStateOf(false) }

    LaunchedEffect(s.openPreview) {
        if (s.openPreview) {
            onOpenPreview()
            vm.previewOpened()
        }
    }

    if (!hasPermission) {
        ScreenContainer(scroll = false) {
            ErrorState(
                title = stringResource(R.string.live_load_failed_title),
                message = stringResource(R.string.live_no_permission_body),
                onBack = onBack,
            )
        }
        return
    }
    if (s.loadError || noCamera) {
        ScreenContainer(scroll = false) {
            ErrorState(
                title = stringResource(R.string.live_load_failed_title),
                message = stringResource(R.string.live_load_failed_body),
                backLabel = stringResource(R.string.live_use_photo),
                onBack = onPhotoMode,
            )
        }
        return
    }

    val preview = remember { Preview.Builder().setResolutionSelector(ratio43()).build() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(ratio43(ANALYSIS_SIZE))
            .build()
    }
    val capture = remember { ImageCapture.Builder().setResolutionSelector(ratio43(CAPTURE_SIZE)).build() }
    DisposableEffect(analysis, vm) {
        val executor = Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(executor) { proxy -> proxy.use { vm.analyze { it.uprightBitmap() } } }
        onDispose {
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }
    val others = remember(analysis, capture) { listOf(analysis, capture) }
    val takePhoto = {
        val file = vm.startCapture()
        if (file != null) {
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = vm.onCaptured(file)
                    override fun onError(exception: ImageCaptureException) = vm.onCaptureFailed(exception, file)
                },
            )
        }
    }

    var view by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier.fillMaxSize().background(cam.screen)) {
        BoundCamera(
            preview,
            others,
            Modifier.fillMaxSize().onSizeChanged { view = it },
            onBindError = { noCamera = true },
        )

        // Boxes are decorative here; the verdict capsule announces the result.
        val result = s.result
        if (result != null && view.width > 0) {
            val t = previewTransform(result.frameWidth, result.frameHeight, view.width.toDouble(), view.height.toDouble())
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                s.shown.forEachIndexed { i, d ->
                    val c = s.classes?.get(d.classId)
                    key(i) {
                        LiveBox(
                            box = toViewBox(d, result.frameWidth, result.frameHeight, t),
                            status = StatusKey.fromToken(c?.colorToken),
                            label = c?.displayName.orEmpty(),
                            confidence = d.confidence,
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(IconName.X, stringResource(R.string.camera_close), onClose)
                    ModeSwitch(CameraMode.Live) { onPhotoMode() }
                }
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (s.notice) {
                        LiveViewModel.Notice.GpuFallback -> Pill(stringResource(R.string.live_gpu_fallback))
                        LiveViewModel.Notice.CaptureFailed -> Pill(stringResource(R.string.live_capture_failed))
                        null -> Unit
                    }
                    LiveVerdict(s.loaded, s.shown, s.classes)
                    AppText(
                        stringResource(R.string.live_preview_note),
                        Modifier.fillMaxWidth(),
                        variant = TypeVariant.Caption,
                        color = cam.lensText,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset.Zero, 4f * LocalDensity.current.density)),
                    )
                    LiveShutter(s.capturing, takePhoto)
                }
            }
            s.stats?.let { BenchHud(it, s.backend, vm::switchBackend) }
        }
    }
}

private fun granted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** The frame as the model should see it: rotated upright, as RN's enablePhysicalBufferRotation. */
private fun ImageProxy.uprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return bitmap
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
}

@Composable
private fun LiveBox(box: ViewBox, status: StatusKey, label: String, confidence: Double) {
    val colors = YoloCTheme.colors
    val s = colors.status(status)
    val density = LocalDensity.current
    with(density) {
        Box(
            Modifier
                .offset { IntOffset(box.left.roundToInt(), box.top.roundToInt()) }
                .size(box.width.toFloat().toDp(), box.height.toFloat().toDp())
                .border(1.dp, colors.boxHalo, RoundedCornerShape(Radii.sm)),
        ) {
            Box(Modifier.fillMaxSize().padding(1.dp).border(3.dp, s.stroke, RoundedCornerShape(Radii.sm - 1.dp)))
            Row(
                Modifier
                    .offset(x = (-1).dp, y = (-26).dp)
                    .height(24.dp)
                    .background(s.fill, RoundedCornerShape(Radii.full))
                    .padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(statusIcon.getValue(status), size = 12.dp, color = s.onFill)
                AppText("$label ${(confidence * 100).roundToInt()}%", variant = TypeVariant.Caption, color = s.onFill, maxLines = 1)
            }
        }
    }
}

/** The live verdict: most severe class in view, with counts, never color alone. */
@Composable
private fun LiveVerdict(ready: Boolean, detections: List<Detection>, classes: Map<Int, DetectionClass>?) {
    if (!ready) return Pill(stringResource(R.string.live_starting), busy = true)
    if (detections.isEmpty() || classes == null) return Pill(stringResource(R.string.live_looking), icon = IconName.Search)
    val config = appContainer().modelConfig
    val pods = detections.map { d ->
        val c = classes[d.classId]
        Triple(c?.severity ?: 0, d.confidence, StatusKey.fromToken(c?.colorToken))
    }
    val summary = summarizeDetections(pods.map { VerdictInput(it.first, it.second) }, config.lowConfidenceThreshold)
    val top = pods.maxOf { it.first }
    val atTop = pods.filter { it.first == top }
    val status = atTop.first().third
    val s = YoloCTheme.colors.status(status)
    val title = verdictTitle(LocalResources.current, summary.verdict, summary.totalPods, atTop.size)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(s.fill, RoundedCornerShape(Radii.sm))
            .clearAndSetSemantics {
                contentDescription = title
                liveRegion = LiveRegionMode.Polite
            }
            .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(statusIcon.getValue(status), size = 22.dp, color = s.onFill)
        AppText(title, Modifier.weight(1f), variant = TypeVariant.BodyStrong, color = s.onFill)
    }
}

@Composable
private fun Pill(text: String, icon: IconName? = null, busy: Boolean = false) {
    val cam = YoloCTheme.colors.camera
    val shape = RoundedCornerShape(Radii.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(cam.bar, shape)
            .border(1.dp, cam.barEdge, shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = cam.lensText, strokeWidth = 2.dp)
        if (icon != null) Icon(icon, size = 20.dp, color = cam.lensTextMuted)
        AppText(text, Modifier.weight(1f), variant = TypeVariant.BodyStrong, color = cam.lensText)
    }
}

@Composable
private fun RoundButton(icon: IconName, label: String, onPress: () -> Unit) {
    val cam = YoloCTheme.colors.camera
    Box(
        Modifier
            .size(TouchTarget)
            .background(cam.bar, RoundedCornerShape(Radii.sm))
            .a11yAction(label, onPress)
            .clickable(interactionSource = null, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, size = 24.dp, color = cam.control)
    }
}

@Composable
private fun LiveShutter(capturing: Boolean, onPress: () -> Unit) {
    val cam = YoloCTheme.colors.camera
    val press = rememberPress()
    Box(
        Modifier
            .size(80.dp)
            .alpha(if (press.pressed.value) 0.7f else 1f)
            .border(4.dp, cam.control, CircleShape)
            .a11yAction(stringResource(R.string.live_capture_a11y), onPress, enabled = !capturing)
            .clickable(enabled = !capturing, interactionSource = press.source, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        if (capturing) {
            CircularProgressIndicator(Modifier.size(24.dp), color = cam.control, strokeWidth = 2.dp)
        } else {
            Box(Modifier.size(60.dp).background(cam.control, CircleShape))
        }
    }
}

/** Development-only benchmark readout and CPU/GPU switch (measurement for the thesis). */
@Composable
private fun BenchHud(snapshot: LiveStatsSnapshot, backend: LiveViewModel.Backend, onBackend: (LiveViewModel.Backend) -> Unit) {
    val cam = YoloCTheme.colors.camera
    val other = if (backend == LiveViewModel.Backend.Gpu) LiveViewModel.Backend.Cpu else LiveViewModel.Backend.Gpu
    Column(
        Modifier
            .offset(x = Spacing.md, y = 96.dp)
            .alpha(0.85f)
            .background(cam.bar, RoundedCornerShape(Radii.md))
            .padding(Spacing.sm),
    ) {
        @Composable
        fun line(k: String, v: String) = AppText("$k: $v", variant = TypeVariant.Caption, color = cam.lensText)
        line("backend", backend.key)
        line("camera fps", toFixed(snapshot.cameraFps, 1))
        line("detections/s", toFixed(snapshot.detectionsPerSecond, 2))
        line("infer p50/p90", "${toFixed(snapshot.inferenceP50, 0)}/${toFixed(snapshot.inferenceP90, 0)} ms")
        line("resize p50", "${toFixed(snapshot.resizeP50, 0)} ms")
        line("post p50", "${toFixed(snapshot.postprocessP50, 0)} ms")
        line("skipped", "${Math.round(snapshot.skippedRatio * 100)}%")
        val name = other.key.uppercase()
        Box(
            Modifier
                .heightIn(min = 40.dp)
                .a11yAction("Switch to $name", { onBackend(other) })
                .clickable(interactionSource = null, indication = null) { onBackend(other) },
            contentAlignment = Alignment.CenterStart,
        ) {
            AppText("Use $name", variant = TypeVariant.Label, color = cam.lensText)
        }
    }
}
