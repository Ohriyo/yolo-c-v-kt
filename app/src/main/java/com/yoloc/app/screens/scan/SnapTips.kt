package com.yoloc.app.screens.scan

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ArrowLink
import com.yoloc.app.components.BottomSheet
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.YButton
import com.yoloc.app.screens.appContainer
import com.yoloc.app.screens.scan.processing.only
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

private val PICTOGRAM = 64.dp

/** How each tip's pictogram is drawn around the mark. */
private enum class Pictogram { Oval, Shade, Close, Lens }

private class SnapTip(val pictogram: Pictogram, @param:StringRes val lead: Int, @param:StringRes val line: Int)

/** Kept consistent with the Guide's photo tips (LearnContent.photoTips); the Guide wins. */
private val snapTips = listOf(
    SnapTip(Pictogram.Oval, R.string.snap_tip_centered_lead, R.string.snap_tip_centered_line),
    SnapTip(Pictogram.Shade, R.string.snap_tip_shade_lead, R.string.snap_tip_shade_line),
    SnapTip(Pictogram.Close, R.string.snap_tip_close_lead, R.string.snap_tip_close_line),
    SnapTip(Pictogram.Lens, R.string.snap_tip_lens_lead, R.string.snap_tip_lens_line),
)

/**
 * Whether the Snap tips sheet is open. It opens by itself once after install, the first time
 * [autoOpen] holds (the camera is showing, in either mode); "View introduction" does not reset
 * that. The ⓘ button sets it at any time.
 */
@Composable
fun rememberSnapTipsOpen(autoOpen: Boolean): MutableState<Boolean> {
    val settings = appContainer().settingsStore
    val open = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoOpen) {
        val s = settings.state.value
        if (autoOpen && s.status == SettingsStore.Status.Ready && !s.snapTipsSeen) {
            open.value = true
            settings.markSnapTipsSeen()
        }
    }
    return open
}

/**
 * Redesign 2026-10-31 (Kotlin only): the camera's Snap tips, four short photo tips with a drawn
 * pictogram each, a link to the Guide's photo topic and "Got it". The camera keeps running under
 * it. TalkBack starts on the title and reads each tip as "lead. line."; Back closes the sheet.
 */
@Composable
fun SnapTipsSheet(visible: Boolean, onClose: () -> Unit, onMorePhotoTips: () -> Unit) {
    BottomSheet(visible, stringResource(R.string.snap_tips_title), onClose, titleFirst = true) {
        snapTips.forEach { TipRow(it) }
        ArrowLink(stringResource(R.string.snap_tips_more), onMorePhotoTips)
        YButton(stringResource(R.string.snap_tips_done), onClose, size = ButtonSize.Lg, fullWidth = true)
    }
}

@Composable
private fun TipRow(tip: SnapTip) {
    val lead = stringResource(tip.lead)
    val line = stringResource(tip.line)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$lead. $line" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PictogramTile(tip.pictogram)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            AppText(lead, variant = TypeVariant.BodyStrong)
            AppText(line, color = YoloCTheme.colors.textMuted)
        }
    }
}

/** A 64dp leaf-tint tile: the mark with Feather icons around it. Decorative. */
@Composable
private fun PictogramTile(pictogram: Pictogram) {
    val colors = YoloCTheme.colors
    Box(
        Modifier.size(PICTOGRAM).background(colors.selected, RoundedCornerShape(Radii.md)),
        contentAlignment = Alignment.Center,
    ) {
        when (pictogram) {
            Pictogram.Oval -> {
                Canvas(Modifier.size(width = 46.dp, height = 58.dp)) {
                    val w = 1.75.dp.toPx()
                    drawOval(
                        colors.icon,
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                    )
                }
                Mark(42.dp)
            }
            Pictogram.Shade -> {
                Icon(IconName.Umbrella, Modifier.align(Alignment.TopCenter).padding(top = 5.dp), size = 24.dp)
                Mark(40.dp, Modifier.align(Alignment.BottomCenter).offset(y = 3.dp))
            }
            Pictogram.Close -> {
                Icon(IconName.Maximize, size = 52.dp)
                Mark(40.dp)
            }
            Pictogram.Lens -> {
                Mark(44.dp, Modifier.offset(x = (-3).dp, y = 2.dp))
                Badge(IconName.Droplet)
            }
        }
    }
}

@Composable
private fun BoxScope.Badge(icon: IconName) {
    Icon(icon, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp), size = 18.dp)
}

/** yoloc_mark in the theme's mark colours (Cream pod in dark, as on Processing). */
@Composable
private fun Mark(size: Dp, modifier: Modifier = Modifier) {
    val colors = YoloCTheme.colors.scanMark
    val mark = ImageVector.vectorResource(R.drawable.yoloc_mark)
    val pod = remember(mark) { mark.only { it != "leaves" } }
    val leaves = remember(mark) { mark.only { it == "leaves" } }
    Box(modifier.size(size)) {
        Image(rememberVectorPainter(leaves), null, Modifier.matchParentSize(), colorFilter = ColorFilter.tint(colors.leaves))
        Image(rememberVectorPainter(pod), null, Modifier.matchParentSize(), colorFilter = ColorFilter.tint(colors.pod))
    }
}
