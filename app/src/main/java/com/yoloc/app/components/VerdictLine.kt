package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: components/VerdictLine.tsx. The verdict as one restrained line: status icon, the counts
 * sentence in the condensed face, and an optional detail. The photo above it carries the result;
 * this names it.
 *
 * @param title Counts sentence, e.g. "Early-stage signs detected in 2 of 5 pods".
 * @param detail Optional second line, e.g. the generic status name or a short instruction.
 * @param lowConfidence Highest confidence below the low-confidence threshold: show the uncertain
 *   notice above.
 */
@Composable
fun VerdictLine(
    verdict: Verdict,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    lowConfidence: Boolean = false,
) {
    val colors = YoloCTheme.colors
    val status = verdictStatus(verdict)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (lowConfidence) {
            val uncertain = stringResource(R.string.verdict_uncertain)
            val shape = RoundedCornerShape(Radii.sm)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // RN: accessibilityRole="alert"
                    .clearAndSetSemantics {
                        contentDescription = uncertain
                        liveRegion = LiveRegionMode.Polite
                    }
                    .heightIn(min = TouchTarget)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.outline, shape)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IconName.Sun, size = 20.dp, color = colors.text)
                AppText(uncertain, Modifier.weight(1f), variant = TypeVariant.BodyStrong)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().a11yLabel(if (detail != null) "$title. $detail" else title),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Centers the 24dp icon on the verdict's first 28dp line.
            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (status == null) IconName.Search else statusIcon.getValue(status),
                    size = 24.dp,
                    color = status?.let { colors.status(it).ink } ?: colors.textMuted,
                )
            }
            Column(Modifier.weight(1f)) {
                AppText(title, variant = TypeVariant.Verdict)
                if (detail != null) AppText(detail, tone = Tone.Muted)
            }
        }
    }
}
