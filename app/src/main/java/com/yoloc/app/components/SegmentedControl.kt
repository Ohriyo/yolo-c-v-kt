package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

data class SegmentedOption<T>(val value: T, val label: String, val icon: IconName? = null)

/**
 * RN: components/SegmentedControl.tsx. Material segmented buttons (e.g. theme: System / Light /
 * Dark). The chosen segment gets the selection fill and a check mark in place of its icon, a
 * change of shape as well as color, and is announced as checked.
 */
@Composable
fun <T> SegmentedControl(
    options: List<SegmentedOption<T>>,
    value: T,
    onChange: (T) -> Unit,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = YoloCTheme.colors
    // With large system text the labels need the room; the icons are only a visual aid.
    val showIcons = LocalDensity.current.fontScale <= 1.2f
    val shape = RoundedCornerShape(Radii.sm)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics { contentDescription = accessibilityLabel }
            .selectableGroup()
            .clip(shape)
            .border(1.dp, colors.outline, shape),
    ) {
        options.forEachIndexed { i, o ->
            val selected = o.value == value
            val icon = if (selected) IconName.Check else o.icon
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outline))
            val press = rememberPress()
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = TouchTarget)
                    .selectable(selected, interactionSource = press.source, indication = null, role = Role.RadioButton) { onChange(o.value) }
                    .background(
                        when {
                            selected -> colors.selected
                            press.pressed.value -> colors.surface
                            else -> Color.Transparent
                        },
                    )
                    .padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null && (showIcons || selected)) Icon(icon, size = 18.dp, color = colors.text)
                AppText(o.label, variant = TypeVariant.Label, maxLines = 1)
            }
        }
    }
}
