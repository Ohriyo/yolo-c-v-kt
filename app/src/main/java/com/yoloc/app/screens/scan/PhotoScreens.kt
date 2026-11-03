package com.yoloc.app.screens.scan

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yoloc.app.R
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.YButton
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.screens.navigation.StackScreen
import com.yoloc.app.screens.scan.processing.CheckingPod
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing

private fun PendingPhoto.ratio(): Float = if (height > 0 && width > 0) width.toFloat() / height else 1f

/** RN: the "photo missing" state of Preview and Processing. */
@Composable
private fun PhotoMissing(onBackToCamera: () -> Unit) {
    ScreenContainer(scroll = false) {
        ErrorState(
            title = stringResource(R.string.preview_missing_title),
            message = stringResource(R.string.preview_missing_body),
            backLabel = stringResource(R.string.preview_back_to_scan),
            onBack = onBackToCamera,
        )
    }
}

/**
 * RN: app/scan/preview.tsx. Preview the unsaved photo. Retake goes back to capture: the camera
 * for camera photos, the picker for gallery photos. Android back returns to the camera.
 *
 * @param onBackToCamera RN: router.dismissTo('/scan/camera').
 */
@Composable
fun PreviewScreen(onBackToCamera: () -> Unit, onUsePhoto: () -> Unit, vm: PreviewViewModel = viewModel(factory = PreviewViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(PickVisualMedia(), vm::onGalleryResult)
    // Retake clears the session before the screen leaves: keep showing the photo on the way out.
    var shown by remember { mutableStateOf(s.photo) }
    if (s.photo != null) shown = s.photo
    val photo = shown
    if (photo == null) {
        PhotoMissing(onBackToCamera)
        return
    }

    val fromCamera = photo.source == ScanSource.Camera
    val retakeLabel = stringResource(if (fromCamera) R.string.preview_retake else R.string.preview_choose_another)
    val retake: () -> Unit = {
        when (vm.retake()) {
            PreviewViewModel.Retake.BackToCamera -> onBackToCamera()
            PreviewViewModel.Retake.PickAnother -> try {
                pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            } catch (e: ActivityNotFoundException) {
                // RN logs and keeps the current photo.
                Log.w("preview", "Gallery failed", e)
            }
            null -> Unit
        }
    }

    ScreenContainer(
        footer = {
            // Retake on the left, the primary action on the right where the thumb lands.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                YButton(
                    retakeLabel,
                    retake,
                    Modifier.weight(1f).fillMaxHeight(),
                    variant = ButtonVariant.Secondary,
                    icon = if (fromCamera) IconName.RotateCcw else IconName.Image,
                    size = ButtonSize.Lg,
                    fullWidth = true,
                )
                YButton(
                    stringResource(R.string.preview_use_photo),
                    onUsePhoto,
                    Modifier.weight(1f).fillMaxHeight(),
                    icon = IconName.Check,
                    size = ButtonSize.Lg,
                    fullWidth = true,
                    enabled = !s.loadFailed,
                )
            }
        },
    ) {
        if (s.loadFailed) {
            ErrorState(
                title = stringResource(R.string.image_load_failed_title),
                message = stringResource(R.string.image_load_failed_body),
                onRetry = retake,
                retryLabel = retakeLabel,
            )
        } else {
            // Coil decodes at view size; a full-resolution camera bitmap can exhaust memory.
            AsyncImage(
                model = photo.file,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(photo.ratio()).clip(RoundedCornerShape(Radii.md)),
                contentScale = ContentScale.Fit,
                onError = { vm.onImageError() },
            )
        }
    }
}

/**
 * RN: app/scan/processing.tsx. Runs on-device detection (CLAUDE.md 2.5) on the previewed photo,
 * then replaces itself with Result so back from Result skips this screen. Android back (or the
 * header's back) while analyzing cancels to Preview; a late result is ignored. Failures show what
 * went wrong with Try again / Go back.
 *
 * Redesign 2026-10-31: while it runs, the photo fills the screen blurred, with the "Checking your
 * pod" card over it ([CheckingPod]) and the back arrow floating in its usual place.
 */
@Composable
fun ProcessingScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    onBackToCamera: () -> Unit,
    vm: ProcessingViewModel = viewModel(factory = ProcessingViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s) { if (s == ProcessingViewModel.State.Done) onDone() }
    val title = stringResource(R.string.processing_title)
    when (val state = s) {
        ProcessingViewModel.State.Missing -> StackScreen(title, onBack) { PhotoMissing(onBackToCamera) }
        is ProcessingViewModel.State.Failed -> StackScreen(title, onBack) {
            ScreenContainer(scroll = false) {
                ErrorState(
                    title = stringResource(state.error.title),
                    message = stringResource(state.error.message),
                    onRetry = vm::retry,
                    onBack = onBack,
                )
            }
        }
        ProcessingViewModel.State.Running, ProcessingViewModel.State.Done ->
            StackScreen(title, onBack, floating = true) { CheckingPod(vm.photo) }
    }
}
