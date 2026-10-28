package com.yoloc.app.components

import com.yoloc.app.util.rememberReduceMotion
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

enum class ToastTone(val icon: IconName) {
    Info(IconName.Info),
    Success(IconName.Check),
    Error(IconName.AlertCircle),
}

/**
 * RN: components/Toast.tsx. Snackbar: a short confirmation on an inverse surface such as "Scan
 * saved", or "Scan deleted" with Undo. Tone is carried by the icon, not color. Placement and
 * timing belong to the screen. It fades in moving down a little each time it appears (RN:
 * FadeInDown), and simply appears when the system removes animations; wrap it in [ToastSlot] for
 * the fade out.
 */
@Composable
fun Toast(message: String, modifier: Modifier = Modifier, tone: ToastTone = ToastTone.Info, action: TextAction? = null) {
    val colors = YoloCTheme.colors
    val shape = RoundedCornerShape(Radii.sm)
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val reduceMotion = rememberReduceMotion()
    AnimatedVisibility(
        visibleState = shown,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(Motion.BASE)) + slideInVertically(tween(Motion.BASE)) { -it / 3 },
    ) {
        Row(
            modifier
                .fillMaxWidth()
                // RN: accessibilityRole="alert", accessibilityLiveRegion="polite"
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .floatingShadow(shape)
                .background(colors.inverse.bg, shape)
                .heightIn(min = 56.dp)
                .padding(start = Spacing.md, end = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(tone.icon, size = 20.dp, color = colors.inverse.icon)
            AppText(
                message,
                Modifier.weight(1f).padding(vertical = Spacing.sm),
                variant = TypeVariant.BodyStrong,
                color = colors.inverse.text,
            )
            if (action != null) {
                Box(
                    Modifier
                        .heightIn(min = TouchTarget)
                        .widthIn(min = TouchTarget)
                        .a11yAction(action.label, action.onClick)
                        .clickable(onClick = action.onClick)
                        .padding(horizontal = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        action.label,
                        variant = TypeVariant.Label,
                        color = colors.inverse.text,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
        }
    }
}

/**
 * Shows [content] while [value] is non-null, fading out over 150ms when it clears (RN: Toast's
 * exiting={FadeOut}). The last value stays drawn while it fades.
 */
@Composable
fun <T : Any> ToastSlot(value: T?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    val reduceMotion = rememberReduceMotion()
    AnimatedVisibility(
        visible = value != null,
        modifier = modifier,
        enter = fadeIn(tween(0)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(Motion.FAST)),
    ) {
        @Suppress("UNCHECKED_CAST")
        (last[0] as T?)?.let { content(it) }
    }
}
