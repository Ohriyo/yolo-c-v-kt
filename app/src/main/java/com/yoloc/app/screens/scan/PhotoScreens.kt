package com.yoloc.app.screens.scan

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                YButton(
                    retakeLabel,
                    retake,
                    Modifier.weight(2f),
                    variant = ButtonVariant.Secondary,
                    icon = if (fromCamera) IconName.RotateCcw else IconName.Image,
                    size = ButtonSize.Lg,
                    fullWidth = true,
                )
                YButton(
                    stringResource(R.string.preview_use_photo),
                    onUsePhoto,
                    Modifier.weight(3f),
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
 * then replaces itself with Result so back from Result skips this screen. Android back while
 * analyzing cancels to Preview; a late result is ignored. Failures show what went wrong with
 * Try again / Go back.
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
    val colors = YoloCTheme.colors
    when (val state = s) {
        ProcessingViewModel.State.Missing -> PhotoMissing(onBackToCamera)
        is ProcessingViewModel.State.Failed -> ScreenContainer(scroll = false) {
            ErrorState(
                title = stringResource(state.error.title),
                message = stringResource(state.error.message),
                onRetry = vm::retry,
                onBack = onBack,
            )
        }
        // The photo stays on screen while it is analyzed, so the wait is about something visible.
        ProcessingViewModel.State.Running, ProcessingViewModel.State.Done -> ScreenContainer(scroll = false) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val maxPhoto = maxHeight * 0.7f
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    val photo = vm.photo
                    if (photo != null) {
                        AsyncImage(
                            model = photo.file,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxPhoto)
                                .aspectRatio(photo.ratio(), matchHeightConstraintsFirst = true)
                                .alpha(0.6f)
                                .clip(RoundedCornerShape(Radii.md))
                                .background(colors.surface),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = colors.text, strokeWidth = 2.dp)
                            AppText(
                                stringResource(R.string.processing_body),
                                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                variant = TypeVariant.Title,
                            )
                        }
                        AppText(stringResource(R.string.processing_detail), tone = Tone.Muted)
                    }
                }
            }
        }
    }
}
