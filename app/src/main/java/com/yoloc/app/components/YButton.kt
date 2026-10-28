package com.yoloc.app.components

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * Material roles: primary = filled, secondary = outlined, ghost = text button, onDark = filled
 * white on the camera's dark field.
 */
enum class ButtonVariant { Primary, Secondary, Ghost, OnDark }

/** Lg (56dp) for the one main action on a screen; Md (48dp) otherwise. */
enum class ButtonSize { Md, Lg }

/** RN: components/Button.tsx. Pressed state is a color change, as in RN (no ripple). */
@Composable
fun YButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: IconName? = null,
    size: ButtonSize = ButtonSize.Md,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
    accessibilityHint: String? = null,
    /** Dev gallery only: render the pressed state statically. */
    previewPressed: Boolean = false,
) {
    val colors = YoloCTheme.colors
    val c = when (variant) {
        ButtonVariant.Primary -> colors.primary
        ButtonVariant.Secondary -> colors.secondary
        ButtonVariant.Ghost -> colors.ghost
        ButtonVariant.OnDark -> colors.onDark
    }
    val inactive = !enabled || loading
    val filled = variant == ButtonVariant.Primary || variant == ButtonVariant.OnDark
    val press = rememberPress()
    val pressed = (press.pressed.value || previewPressed) && !inactive
    val bg = when {
        !enabled && filled -> colors.disabled.bg
        pressed -> c.pressedBg
        else -> c.bg
    }
    val fg = when {
        !enabled -> colors.disabled.text
        pressed -> c.pressedText
        else -> c.text
    }
    val shape = RoundedCornerShape(Radii.sm)
    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = if (size == ButtonSize.Lg) 56.dp else TouchTarget)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (!enabled) colors.disabled.bg else c.border, shape)
            .a11yAction(label, onClick, enabled = !inactive, hint = accessibilityHint)
            .clickable(interactionSource = press.source, indication = null, enabled = !inactive, onClick = onClick)
            .padding(horizontal = if (variant == ButtonVariant.Ghost) Spacing.sm else Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, size = 20.dp, color = fg)
        }
        AppText(label, variant = TypeVariant.Label, color = fg, maxLines = 2, textAlign = TextAlign.Center)
    }
}
