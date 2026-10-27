package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.theme.YoloCType

/**
 * RN: the app's TextInput style (sheets, Plots): Barlow body text on the surface, a 1dp border,
 * the muted placeholder. [maxLength] cuts what is typed, as RN's maxLength ([limitEdit]).
 *
 * @param onDone The keyboard's Done (single line only; RN: onSubmitEditing).
 * @param pill Fully rounded ends (RN: Plots' fields).
 * @param background Defaults to the surface.
 * @param selectAllOnFocus RN: selectTextOnFocus. The starting text is selected, so typing replaces it.
 */
@Composable
fun YTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = TouchTarget,
    maxLength: Int = Int.MAX_VALUE,
    onDone: (() -> Unit)? = null,
    pill: Boolean = false,
    background: Color = Color.Unspecified,
    selectAllOnFocus: Boolean = false,
) {
    val colors = YoloCTheme.colors
    val shape = RoundedCornerShape(if (pill) Radii.full else Radii.md)
    var field by remember {
        mutableStateOf(TextFieldValue(value, if (selectAllOnFocus) TextRange(0, value.length) else TextRange(value.length)))
    }
    // The caller owns the text: a change from outside (cleared after Add) replaces the field's.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    Box(
        modifier
            .heightIn(min = minHeight)
            .background(if (background.isSpecified) background else colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = Spacing.md, vertical = if (singleLine) 0.dp else Spacing.sm),
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
    ) {
        if (value.isEmpty()) AppText(placeholder, Modifier.clearAndSetSemantics {}, tone = Tone.Muted)
        BasicTextField(
            value = field,
            onValueChange = { next ->
                val limited = limitEdit(field.text, next.text, maxLength)
                field = if (limited == next.text) next else TextFieldValue(limited, TextRange(minOf(next.selection.end, limited.length)))
                if (limited != value) onValueChange(limited)
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = accessibilityLabel },
            textStyle = YoloCType.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.focus),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        )
    }
}

/**
 * RN maxLength (Android's LengthFilter): an edit that goes past [max] keeps as much of the typed
 * or pasted text as fits; the text that was already there is never cut.
 */
internal fun limitEdit(old: String, new: String, max: Int): String {
    if (new.length <= max) return new
    // The edit replaced old's middle with new's middle; find the unchanged start and end.
    var prefix = 0
    while (prefix < old.length && prefix < new.length && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < old.length - prefix && suffix < new.length - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
    val inserted = new.substring(prefix, new.length - suffix)
    val room = (max - prefix - suffix).coerceAtLeast(0)
    return new.substring(0, prefix) + inserted.take(room) + new.substring(new.length - suffix)
}
