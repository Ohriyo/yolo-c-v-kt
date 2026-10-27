package com.yoloc.app.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.dropShadow
import com.yoloc.app.theme.YoloCTheme

/** RN: accessible + accessibilityLabel on a View. One spoken label; the children are not read. */
fun Modifier.a11yLabel(label: String): Modifier = clearAndSetSemantics { contentDescription = label }

/**
 * RN: a Pressable with accessibilityRole and accessibilityLabel. One spoken label and the click
 * action; the children are not read separately. [hint] is spoken as the action ("double tap to …").
 */
fun Modifier.a11yAction(
    label: String,
    onClick: () -> Unit,
    role: Role = Role.Button,
    selected: Boolean? = null,
    enabled: Boolean = true,
    hint: String? = null,
): Modifier = clearAndSetSemantics {
    contentDescription = label
    this.role = role
    if (selected != null) this.selected = selected
    if (!enabled) disabled()
    onClick(label = hint) {
        if (enabled) onClick()
        enabled
    }
}

/** RN: theme.shadow.floating (0 6 20). Only floating elements cast one: sheet, snackbar, camera bar. */
@Composable
fun Modifier.floatingShadow(shape: Shape): Modifier =
    dropShadow(shape, Shadow(radius = 20.dp, color = YoloCTheme.colors.floatingShadow, offset = DpOffset(0.dp, 6.dp)))

/** A press source plus its pressed state, for RN-style pressed colors (no ripple). */
class Press(val source: MutableInteractionSource, val pressed: State<Boolean>)

@Composable
fun rememberPress(): Press {
    val source = remember { MutableInteractionSource() }
    return Press(source, source.collectIsPressedAsState())
}
