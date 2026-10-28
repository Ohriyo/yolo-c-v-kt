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
import androidx.compose.ui.focus.onFocusChanged
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
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // The caller owns the text. Only a change made outside the field (cleared after Add) replaces
    // it: a caller that shows the typed text a frame late must not move the cursor or break the
    // word being composed.
    val seen = remember { arrayOf(value) }
    if (value != seen[0]) {
        seen[0] = value
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
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
                val edit = limitEdit(field.text, next.text, maxLength)
                field = if (edit.text == next.text) next else TextFieldValue(edit.text, TextRange(edit.cursor))
                if (edit.text != value) onValueChange(edit.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityLabel }
                .onFocusChanged { if (selectAllOnFocus && it.isFocused) field = field.copy(selection = TextRange(0, field.text.length)) },
            textStyle = YoloCType.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.focus),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        )
    }
}

/** The text after [limitEdit], and where the cursor goes when the insert was cut: after what was kept. */
internal data class LimitedEdit(val text: String, val cursor: Int)

/**
 * RN maxLength (Android's LengthFilter): an edit that goes past [max] keeps as much of the typed
 * or pasted text as fits; the text that was already there is never cut.
 */
internal fun limitEdit(old: String, new: String, max: Int): LimitedEdit {
    if (new.length <= max) return LimitedEdit(new, new.length)
    // The edit replaced old's middle with new's middle; find the unchanged start and end.
    var prefix = 0
    while (prefix < old.length && prefix < new.length && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < old.length - prefix && suffix < new.length - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
    val kept = new.substring(prefix, new.length - suffix).take((max - prefix - suffix).coerceAtLeast(0))
    return LimitedEdit(new.substring(0, prefix) + kept + new.substring(new.length - suffix), prefix + kept.length)
}
