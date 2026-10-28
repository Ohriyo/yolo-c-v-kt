package com.yoloc.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(title, variant = TypeVariant.Title)
            if (subtitle != null) AppText(subtitle, variant = TypeVariant.Caption, tone = Tone.Faint)
        }
        if (action != null) {
            val press = rememberPress()
            Box(
                Modifier
                    .heightIn(min = TouchTarget)
                    .a11yAction(action.label, action.onClick)
                    .clickable(interactionSource = press.source, indication = null, onClick = action.onClick)
                    .alpha(if (press.pressed.value) 0.6f else 1f)
                    .padding(bottom = 2.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                AppText(action.label, variant = TypeVariant.Label, color = YoloCTheme.colors.link)
            }
        }
    }
}
