package com.yoloc.app.features.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yoloc.app.R
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.util.rememberReduceMotion
import java.io.File

private val SHUTTER = 76.dp
private val SHUTTER_RING = 4.dp
private val SHUTTER_GAP = 4.dp
private val SHUTTER_INNER = SHUTTER - (SHUTTER_RING + SHUTTER_GAP) * 2
private val GALLERY = 52.dp

/**
 * RN: features/camera/CameraControlBar.tsx. The camera's controls: gallery (left), shutter
 * (center), flash (right). Redesign 2026-10-28: no bar of its own; the camera screen's bottom
 * scrim carries it, and it no longer pads for the navigation bar (the screen does).
 *
 * @param galleryImage Newest saved scan photo for the gallery button, or null for the icon.
 * @param canCapture False until the camera is ready (or when there is no camera access).
 * @param flashAvailable The flash toggle is hidden when the camera is not in use.
 */
@Composable
fun CameraControlBar(
    galleryImage: File?,
    galleryBusy: Boolean,
    onGallery: () -> Unit,
    canCapture: Boolean,
    capturing: Boolean,
    onCapture: () -> Unit,
    flashAvailable: Boolean,
    flashOn: Boolean,
    onToggleFlash: () -> Unit,
) {
    val cam = YoloCTheme.colors.camera
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).heightIn(min = TouchTarget), contentAlignment = Alignment.Center) {
            GalleryButton(galleryImage, galleryBusy, onGallery)
        }
        ShutterButton(canCapture, capturing, onCapture)
        Box(Modifier.weight(1f).heightIn(min = TouchTarget), contentAlignment = Alignment.Center) {
            if (flashAvailable) FlashToggle(flashOn, onToggleFlash)
        }
    }
}

@Composable
private fun GalleryButton(image: File?, busy: Boolean, onPress: () -> Unit) {
    val cam = YoloCTheme.colors.camera
    val press = rememberPress()
    // A thumbnail that fails to load (file removed) falls back to the icon.
    var failed by remember { mutableStateOf<File?>(null) }
    val label = stringResource(R.string.camera_gallery)
    val shape = RoundedCornerShape(Radii.sm)
    Box(
        Modifier
            .size(GALLERY)
            .alpha(if (press.pressed.value || busy) 0.7f else 1f)
            .clip(shape)
            .background(cam.galleryFill)
            .border(1.dp, cam.frameBorder, shape)
            .a11yAction(label, onPress, enabled = !busy)
            .clickable(enabled = !busy, interactionSource = press.source, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null && image != failed) {
            AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = image })
        } else {
            Icon(IconName.Image, size = 24.dp, color = cam.control)
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, capturing: Boolean, onPress: () -> Unit) {
    val cam = YoloCTheme.colors.camera
    val press = rememberPress()
    val reduceMotion = rememberReduceMotion()
    // Reduced motion: the inner circle still changes size, just without the animation.
    val scale by animateFloatAsState(
        if (press.pressed.value) 0.86f else 1f,
        if (reduceMotion) snap() else tween(Motion.PRESS),
        label = "shutter",
    )
    val disabled = !enabled || capturing
    val label = stringResource(R.string.camera_shutter)
    Box(
        Modifier
            .size(SHUTTER)
            .alpha(if (enabled) 1f else 0.45f)
            .border(SHUTTER_RING, cam.shutterRing, CircleShape)
            .a11yAction(label, onPress, enabled = !disabled)
            .clickable(enabled = !disabled, interactionSource = press.source, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(SHUTTER_INNER).scale(scale).background(cam.control, CircleShape), contentAlignment = Alignment.Center) {
            if (capturing) CircularProgressIndicator(Modifier.size(24.dp), color = cam.lens, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun FlashToggle(on: Boolean, onToggle: () -> Unit) {
    val cam = YoloCTheme.colors.camera
    val press = rememberPress()
    val label = stringResource(R.string.camera_flash)
    Box(
        Modifier
            .size(TouchTarget)
            .alpha(if (press.pressed.value) 0.7f else 1f)
            .clip(RoundedCornerShape(Radii.sm))
            .background(if (on) cam.control else cam.controlFill)
            .a11yAction(label, onToggle, role = Role.Switch, checked = on)
            .toggleable(on, interactionSource = press.source, indication = null, role = Role.Switch, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        // On and off differ in shape too (zap vs. struck-out zap), not only in color.
        Icon(if (on) IconName.Zap else IconName.ZapOff, size = 22.dp, color = if (on) cam.lens else cam.control)
    }
}
