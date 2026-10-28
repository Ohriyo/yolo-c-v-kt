package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: components/DetectionListItem.tsx. One row of the pod table: status icon, class and pod
 * number, and the confidence as a right-aligned tabular figure. Selection (its box highlighted on
 * the photo) fills the row and shows an eye icon, so it never relies on color alone.
 *
 * @param index 1-based position shown to the user ("Pod 2").
 * @param label Class display name from the classes table.
 * @param correctedFrom Display name of the model's original class when the user corrected it.
 */
@Composable
fun DetectionListItem(
    index: Int,
    status: StatusKey,
    label: String,
    confidence: Double,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    correctedFrom: String? = null,
    onClick: (() -> Unit)? = null,
    /** False on the last row of a card, whose edge already closes the list. */
    divider: Boolean = true,
) {
    val colors = YoloCTheme.colors
    val pct = formatConfidence(confidence)
    val podNumber = stringResource(R.string.detection_pod_number, index)
    val corrected = correctedFrom?.let { stringResource(R.string.detection_corrected_from, it) }
    val a11y = listOfNotNull(podNumber, label, stringResource(R.string.common_confidence, pct), corrected).joinToString(", ")
    val press = rememberPress()
    val bg = when {
        selected -> colors.selected
        onClick != null && press.pressed.value -> colors.surface
        else -> Color.Transparent
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .a11yAction(a11y, onClick ?: {}, selected = selected, enabled = onClick != null)
                .clickable(interactionSource = press.source, indication = null, enabled = onClick != null) { onClick?.invoke() }
                .background(bg)
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(statusIcon.getValue(status), size = 22.dp, color = colors.status(status).ink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppText(label, variant = TypeVariant.BodyStrong, maxLines = 1)
                AppText(podNumber, variant = TypeVariant.Caption, tone = Tone.Faint)
                if (corrected != null) {
                    Row(
                        Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(IconName.Edit2, size = 13.dp, color = colors.textMuted)
                        AppText(corrected, variant = TypeVariant.Caption, tone = Tone.Muted)
                    }
                }
            }
            AppText("$pct%", Modifier.widthIn(min = 48.dp), variant = TypeVariant.Data, textAlign = TextAlign.End)
            Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(IconName.Eye, size = 18.dp, color = colors.text)
            }
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}
