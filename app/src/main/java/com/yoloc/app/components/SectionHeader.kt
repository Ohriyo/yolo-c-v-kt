package com.yoloc.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/** A text action at a section's right edge ("See all"). */
data class TextAction(val label: String, val onClick: () -> Unit)

/**
 * RN: components/SectionHeader.tsx. Section title with an optional text action at the right edge.
 * [subtitle] only when it says something the title does not.
 */
/** A [SectionHeader] over its content, `Spacing.sm` apart (RN: a section View with gap sm). */
@Composable
fun HeaderedSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: TextAction? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(title, subtitle = subtitle, action = action)
        content()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: TextAction? = null) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(title, variant = TypeVariant.Title)
            if (subtitle != null) AppText(subtitle, variant = TypeVariant.Caption, tone = Tone.Faint)
        }
        if (action != null) ArrowLink(action.label, action.onClick)
    }
}

/** Redesign 2026-10-28 (the prototype): a text link with a trailing arrow, "View history ->". */
@Composable
fun ArrowLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = YoloCTheme.colors
    val press = rememberPress()
    Row(
        modifier
            .heightIn(min = TouchTarget)
            .a11yAction(label, onClick)
            .clickable(interactionSource = press.source, indication = null, onClick = onClick)
            .alpha(if (press.pressed.value) 0.6f else 1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(label, variant = TypeVariant.Label, color = colors.link, maxLines = 1)
        Icon(IconName.ArrowRight, size = 16.dp, color = colors.link)
    }
}
