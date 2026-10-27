package com.yoloc.app.screens.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.YButton
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.features.camera.BoundCamera
import com.yoloc.app.features.camera.CameraControlBar
import com.yoloc.app.features.camera.Viewfinder
import com.yoloc.app.features.camera.ratio43
import com.yoloc.app.features.live.CameraMode
import com.yoloc.app.features.live.ModeSwitch
import com.yoloc.app.features.scans.CameraAccess
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.util.rememberReduceMotion

/** RN: takePictureAsync({ quality: 0.9 }). */
private const val JPEG_QUALITY = 90

/**
 * RN: app/scan/camera.tsx (CLAUDE.md 3.1/3.2). The Scan button and Home's "Scan a pod" open this
 * screen directly. The camera is asked for here, with a short explanation first; the gallery
 * button works whatever the camera permission. Capture and gallery both open Preview, so back,
 * Retake and Discard return here. The camera runs only while this screen is started (see
 * [BoundCamera]).
 */
@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onOpenPreview: () -> Unit,
    onLive: () -> Unit,
    vm: CameraViewModel = viewModel(factory = CameraViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val cam = YoloCTheme.colors.camera
    val context = LocalContext.current
    val activity = LocalActivity.current
    val rationale = {
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    // RN: useCameraAccess re-checks when the app returns, so access turned on in settings applies at once.
    LifecycleResumeEffect(vm) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        vm.onPermissionChecked(granted, rationale())
        onPauseOrDispose {}
    }
    val askCamera = rememberLauncherForActivityResult(RequestPermission()) { vm.onPermissionResult(it, rationale()) }
    val pick = rememberLauncherForActivityResult(PickVisualMedia(), vm::onGalleryResult)

    LaunchedEffect(s.openPreview) {
        if (s.openPreview) {
            onOpenPreview()
            vm.previewOpened()
        }
    }

    val preview = remember { Preview.Builder().setResolutionSelector(ratio43()).build() }
    val capture = remember {
        ImageCapture.Builder().setResolutionSelector(ratio43()).setJpegQuality(JPEG_QUALITY).build()
    }
    SideEffect { capture.flashMode = if (s.flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }
    val others = remember(capture) { listOf(capture) }
    val previewOn = s.granted && !s.mountError

    // RN: animateShutter. A dark flash over the preview as the photo is taken.
    val reduceMotion = rememberReduceMotion()
    val shutter = remember { Animatable(0f) }
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
    LaunchedEffect(s.capturing) {
        if (s.capturing && !reduceMotion) {
            shutter.snapTo(1f)
            shutter.animateTo(0f, tween(Motion.BASE))
        }
    }

    Column(Modifier.fillMaxSize().background(cam.screen)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = Spacing.xs, start = Spacing.xs, end = Spacing.xs)
                .heightIn(min = TouchTarget),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val press = rememberPress()
            val close = stringResource(R.string.camera_close)
            Box(
                Modifier
                    .size(TouchTarget)
                    .alpha(if (press.pressed.value) 0.6f else 1f)
                    .a11yAction(close, onClose)
                    .clickable(interactionSource = press.source, indication = null, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IconName.X, size = 26.dp, color = cam.control)
            }
            if (s.granted) ModeSwitch(CameraMode.Photo) { onLive() }
        }

        Viewfinder(Modifier.weight(1f)) {
            when {
                s.mountError -> LensPanel(
                    IconName.CameraOff,
                    stringResource(R.string.camera_unavailable_title),
                    stringResource(R.string.camera_unavailable_body),
                    stringResource(R.string.error_retry),
                    IconName.RotateCcw,
                    vm::retryMount,
                )
                s.access == CameraAccess.Ask -> LensPanel(
                    IconName.Camera,
                    stringResource(R.string.camera_ask_title),
                    stringResource(R.string.camera_ask_body),
                    stringResource(R.string.camera_allow),
                    IconName.Camera,
                ) { askCamera.launch(Manifest.permission.CAMERA) }
                s.access == CameraAccess.Denied -> LensPanel(
                    IconName.CameraOff,
                    stringResource(R.string.camera_denied_title),
                    stringResource(R.string.camera_denied_body),
                    stringResource(R.string.camera_allow),
                    IconName.Camera,
                ) { askCamera.launch(Manifest.permission.CAMERA) }
                s.access == CameraAccess.Blocked -> LensPanel(
                    IconName.CameraOff,
                    stringResource(R.string.camera_denied_title),
                    stringResource(R.string.camera_blocked_body),
                    stringResource(R.string.camera_open_settings),
                    IconName.Settings,
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }
                previewOn -> {
                    BoundCamera(preview, others, Modifier.fillMaxSize(), onReady = vm::onCameraReady, onBindError = { vm.onMountError() })
                    Box(Modifier.fillMaxSize().alpha(shutter.value).background(Color.Black))
                }
            }
        }

        val notice = when (s.notice) {
            CameraViewModel.Notice.CaptureFailed -> stringResource(R.string.camera_capture_failed)
            CameraViewModel.Notice.GalleryFailed -> stringResource(R.string.camera_gallery_failed)
            null -> null
        }
        // Aligned with the viewfinder's left edge (its 16dp margin).
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp)
                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (notice != null) Icon(IconName.AlertCircle, size = 18.dp, color = cam.lensText)
            AppText(
                notice ?: if (s.granted) stringResource(R.string.camera_hint) else " ",
                Modifier.weight(1f),
                color = if (notice != null) cam.lensText else cam.lensTextMuted,
            )
        }

        CameraControlBar(
            galleryImage = s.latestImage,
            galleryBusy = s.picking,
            onGallery = { if (vm.startGallery()) pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            canCapture = previewOn && s.ready,
            capturing = s.capturing,
            onCapture = takePhoto,
            flashAvailable = s.granted && !s.mountError,
            flashOn = s.flashOn,
            onToggleFlash = vm::toggleFlash,
        )
    }
}

/** Explanation inside the viewfinder when there is no preview, anchored to its lower left. */
@Composable
private fun LensPanel(
    icon: IconName,
    title: String,
    body: String,
    actionLabel: String,
    actionIcon: IconName,
    onAction: () -> Unit,
) {
    val cam = YoloCTheme.colors.camera
    Column(
        Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.Bottom),
    ) {
        Icon(icon, size = 28.dp, color = cam.lensTextMuted)
        AppText(title, Modifier.semantics { heading() }, variant = TypeVariant.Verdict, color = cam.lensText)
        AppText(body, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = cam.lensTextMuted)
        YButton(actionLabel, onAction, Modifier.padding(top = Spacing.sm), variant = ButtonVariant.OnDark, icon = actionIcon)
    }
}
