package com.yoloc.app.features.scans.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.BottomSheet
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.StatusChip
import com.yoloc.app.components.Tone
import com.yoloc.app.components.rememberPress
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: features/scans/CorrectLabelSheet.tsx. "Correct a label" (CLAUDE.md 3.1): pick the right
 * class for one pod. Each option pairs the status color with its icon and name (never color
 * alone). The model's own answer stays labelled so the user can undo a correction by picking it
 * again.
 *
 * @param classes All classes from the classes table, in model order.
 * @param currentClassId The class currently shown (corrected or model).
 */
@Composable
fun CorrectLabelSheet(
    classes: List<DetectionClass>,
    modelClassId: Int,
    currentClassId: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = YoloCTheme.colors
    val modelLabel = classes.firstOrNull { it.id == modelClassId }?.displayName.orEmpty()
    val modelSaid = stringResource(R.string.result_model_said, modelLabel)
    BottomSheet(visible = true, title = stringResource(R.string.result_correct_title), onClose = onClose) {
        AppText(stringResource(R.string.result_correct_body), tone = Tone.Muted)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (c in classes) {
                val selected = c.id == currentClassId
                val isModel = c.id == modelClassId
                val press = rememberPress()
                val shape = RoundedCornerShape(Radii.md)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget + 8.dp)
                        .alpha(if (press.pressed.value) 0.85f else 1f)
                        .clip(shape)
                        .background(if (selected) colors.surfaceRaised else colors.surface)
                        .border(2.dp, if (selected) colors.text else colors.border, shape)
                        .clearAndSetSemantics {
                            contentDescription = if (isModel) "${c.displayName}, $modelSaid" else c.displayName
                            role = Role.RadioButton
                            this.selected = selected
                            onClick { onSelect(c.id); true }
                        }
                        .selectable(selected, interactionSource = press.source, indication = null, role = Role.RadioButton) { onSelect(c.id) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(StatusKey.fromToken(c.colorToken), label = c.displayName)
                    Box(Modifier.weight(1f)) {
                        if (isModel) AppText(modelSaid, variant = TypeVariant.Caption, tone = Tone.Muted)
                    }
                    Icon(if (selected) IconName.CheckCircle else IconName.Circle, color = if (selected) colors.text else colors.border)
                }
            }
        }
    }
}
