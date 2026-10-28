package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

enum class ChipSize { Sm, Md }

/** RN: formatConfidence. 0–1 as a whole percentage (halves round up, as Math.round). */
fun formatConfidence(confidence: Double): Int = Math.round(confidence.coerceIn(0.0, 1.0) * 100).toInt()

/** RN: strings.status. The generic status names. */
@Composable
fun statusName(status: StatusKey): String = stringResource(
    when (status) {
        StatusKey.Healthy -> R.string.status_healthy
        StatusKey.Early -> R.string.status_early
        StatusKey.Infected -> R.string.status_infected
    },
)

/** RN: strings.verdict.short. Saved-scan verdicts as chip labels. */
@Composable
fun verdictShortName(verdict: Verdict): String = stringResource(
    when (verdict) {
        Verdict.Healthy -> R.string.verdict_short_healthy
        Verdict.EarlyStage -> R.string.verdict_short_early_stage
        Verdict.Infected -> R.string.verdict_short_infected
        Verdict.NoPod -> R.string.verdict_short_no_pod
    },
)

/** RN: verdictStatus. The status a verdict with pods is drawn in; "no pod" has none. */
fun verdictStatus(verdict: Verdict): StatusKey? = when (verdict) {
    Verdict.Healthy -> StatusKey.Healthy
    Verdict.EarlyStage -> StatusKey.Early
    Verdict.Infected -> StatusKey.Infected
    Verdict.NoPod -> null
}

@Composable
private fun ChipRow(size: ChipSize, modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .heightIn(min = if (size == ChipSize.Sm) 24.dp else 32.dp)
            .padding(horizontal = if (size == ChipSize.Sm) Spacing.xs else Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * RN: components/StatusChip.tsx. Status tint + icon + label. Never color alone. Redesign
 * 2026-10-28: a pale tint with the status ink (the prototype), not RN's solid fill.
 *
 * @param label Defaults to the generic status name; pass the class display name when known.
 * @param confidence 0–1. Shown as a whole percentage when given.
 */
@Composable
fun StatusChip(
    status: StatusKey,
    modifier: Modifier = Modifier,
    label: String? = null,
    confidence: Double? = null,
    size: ChipSize = ChipSize.Md,
) {
    val s = YoloCTheme.colors.status(status)
    val text = label ?: statusName(status)
    val pct = confidence?.let(::formatConfidence)
    val a11y = if (pct == null) text else "$text, ${stringResource(R.string.common_confidence, pct)}"
    ChipRow(size, modifier.a11yLabel(a11y).clip(RoundedCornerShape(Radii.sm)).background(s.tint)) {
        Icon(statusIcon.getValue(status), size = if (size == ChipSize.Sm) 14.dp else 16.dp, color = s.onTint)
        AppText(
            if (pct == null) text else "$text $pct%",
            variant = if (size == ChipSize.Sm) TypeVariant.Caption else TypeVariant.Label,
            color = s.onTint,
            maxLines = 1,
        )
    }
}

/**
 * RN: components/VerdictChip.tsx. A saved scan's verdict as a chip. Pod statuses use the pale
 * status tint + icon (redesign 2026-10-28; RN: the status fill); "no pods" is deliberately neutral
 * (outlined, search icon) so it never reads as healthy (CLAUDE.md 3.3).
 */
@Composable
fun VerdictChip(verdict: Verdict, modifier: Modifier = Modifier, size: ChipSize = ChipSize.Sm) {
    val status = verdictStatus(verdict)
    if (status != null) {
        StatusChip(status, modifier, label = verdictShortName(verdict), size = size)
        return
    }
    val colors = YoloCTheme.colors
    val shape = RoundedCornerShape(Radii.sm)
    val label = verdictShortName(verdict)
    ChipRow(size, modifier.a11yLabel(label).clip(shape).background(colors.card).border(1.dp, colors.outline, shape)) {
        Icon(IconName.Search, size = if (size == ChipSize.Sm) 14.dp else 16.dp, color = colors.textMuted)
        AppText(label, variant = if (size == ChipSize.Sm) TypeVariant.Caption else TypeVariant.Label, maxLines = 1)
    }
}
