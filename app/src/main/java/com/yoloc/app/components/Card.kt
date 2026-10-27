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
 * RN: components/Card.tsx. A bordered panel, for content that needs grouping (a form, an export
 * summary). Lists use divided rows instead. No shadow: depth comes from the hairline and spacing.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accessibilityLabel: String? = null,
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
            .clip(shape)
            .background(if (onClick != null && press.pressed.value) colors.surface else colors.background)
            .border(1.dp, colors.border, shape)
            .then(tap)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = content,
    )
}
