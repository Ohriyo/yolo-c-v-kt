package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: components/Card.tsx, redesigned 2026-10-28 (the prototype): a white card on the tinted
 * ground, with a hairline and a soft low shadow (none in dark, where the hairline carries depth).
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accessibilityLabel: String? = null,
    /** Dev gallery only: render the pressed state statically. */
    previewPressed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = YoloCTheme.colors
    val press = rememberPress()
    val shape = RoundedCornerShape(Radii.md)
    val tap = when {
        onClick == null -> Modifier
        accessibilityLabel != null -> Modifier
            .a11yAction(accessibilityLabel, onClick)
            .clickable(interactionSource = press.source, indication = null, onClick = onClick)
        else -> Modifier
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(interactionSource = press.source, indication = null, onClick = onClick)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardShadow(shape)
            .clip(shape)
            .background(if (onClick != null && (press.pressed.value || previewPressed)) colors.surface else colors.card)
            .border(1.dp, colors.border, shape)
            .then(tap)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = content,
    )
}
