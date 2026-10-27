package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

data class ReadoutCell(
    val key: String,
    /** Short label: "Pods", "Highest confidence". */
    val label: String,
    /** The figure, already formatted ("5", "91%", "1.4 s"). */
    val value: String,
    /** Optional icon beside the label (status counts pair icon + label, never color alone). */
    val icon: IconName? = null,
    val iconColor: Color? = null,
    /** Full spoken text, e.g. "Pods found: 5". Defaults to "label: value". */
    val accessibilityLabel: String? = null,
    val accessibilityHint: String? = null,
    val onClick: (() -> Unit)? = null,
)

/** Md: 28sp figures (Result, Scan Detail, Home's last scan); Lg: 44sp (Home's counts). */
enum class ReadoutSize { Md, Lg }

/**
 * RN: components/ReadoutStrip.tsx. A full-width row of 2–3 readings between hairlines, each a
 * small label with a large condensed tabular figure. Cells are separated by vertical hairlines.
 */
@Composable
fun ReadoutStrip(cells: List<ReadoutCell>, modifier: Modifier = Modifier, size: ReadoutSize = ReadoutSize.Md) {
    val colors = YoloCTheme.colors
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            cells.forEachIndexed { i, cell ->
                if (i > 0) VerticalDivider(Modifier.fillMaxHeight().padding(vertical = Spacing.sm), thickness = 1.dp, color = colors.border)
                val a11y = cell.accessibilityLabel ?: "${cell.label}: ${cell.value}"
                val press = rememberPress()
                val onClick = cell.onClick
                val tap = if (onClick == null) {
                    Modifier.a11yLabel(a11y)
                } else {
                    Modifier
                        .a11yAction(a11y, onClick, hint = cell.accessibilityHint)
                        .clickable(interactionSource = press.source, indication = null, onClick = onClick)
                        .background(if (press.pressed.value) colors.surface else Color.Transparent)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = TouchTarget)
                        .then(tap)
                        .padding(start = if (i > 0) Spacing.sm else 0.dp, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                        if (cell.icon != null) Icon(cell.icon, size = 14.dp, color = cell.iconColor ?: colors.icon)
                        AppText(cell.label, Modifier.weight(1f, fill = false), variant = TypeVariant.Caption, tone = Tone.Faint, maxLines = 2)
                    }
                    AppText(
                        cell.value,
                        Modifier.padding(top = Spacing.xxs),
                        variant = if (size == ReadoutSize.Lg) TypeVariant.Readout else TypeVariant.Figure,
                        maxLines = 1,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}
