package com.yoloc.app.features.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.BottomSheet
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.features.settings.formatBytes
import com.yoloc.app.screens.settings.RetrainingExportViewModel
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

private fun ExportScope.label() = when (this) {
    ExportScope.Corrected -> R.string.settings_retrain_scope_corrected
    ExportScope.Early -> R.string.settings_retrain_scope_early
    ExportScope.All -> R.string.settings_retrain_scope_all
}

private fun ExportScope.hint() = when (this) {
    ExportScope.Corrected -> R.string.settings_retrain_hint_corrected
    ExportScope.Early -> R.string.settings_retrain_hint_early
    ExportScope.All -> R.string.settings_retrain_hint_all
}

/**
 * RN: features/export/RetrainingExportSheet.tsx. Choose what to export for retraining, see how
 * many photos and how big, then create and share the zip. Corrected scans come first: they are the
 * only labels a person has checked. The sheet cannot be closed while the zip is being made.
 */
@Composable
fun RetrainingExportSheet(
    visible: Boolean,
    onClose: () -> Unit,
    onDone: (ExportResult) -> Unit,
    onError: () -> Unit,
    vm: RetrainingExportViewModel = viewModel(factory = RetrainingExportViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val colors = YoloCTheme.colors
    LaunchedEffect(visible) { if (visible) vm.onOpened() }
    LaunchedEffect(s.outcome) {
        when (val outcome = s.outcome) {
            null -> return@LaunchedEffect
            is RetrainingExportViewModel.Outcome.Done -> onDone(outcome.result)
            RetrainingExportViewModel.Outcome.Failed -> onError()
        }
        vm.outcomeHandled()
    }

    BottomSheet(visible, stringResource(R.string.settings_retrain), onClose = onClose, dismissable = !s.working) {
        AppText(stringResource(R.string.settings_retrain_body), tone = Tone.Muted)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (scope in ExportScope.entries) {
                val selected = scope == s.scope
                val count = s.summaries?.get(scope)?.scans
                val label = stringResource(scope.label())
                val hint = stringResource(scope.hint())
                val press = rememberPress()
                val shape = RoundedCornerShape(Radii.md)
                val choose = { vm.choose(scope) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(shape)
                        .background(if (press.pressed.value || selected) colors.surface else colors.surfaceRaised)
                        .border(1.5.dp, if (selected) colors.text else colors.border, shape)
                        // RN reads the label, then the hint.
                        .a11yAction("$label, $hint", choose, role = Role.RadioButton, selected = selected, enabled = !s.working)
                        .clickable(enabled = !s.working, interactionSource = press.source, indication = null, onClick = choose)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (selected) IconName.Disc else IconName.Circle, size = 22.dp, color = colors.icon)
                    Column(Modifier.weight(1f)) {
                        AppText(label, variant = TypeVariant.BodyStrong)
                        AppText(hint, variant = TypeVariant.Caption, tone = Tone.Muted)
                    }
                    when {
                        count != null -> AppText(count.toString(), variant = TypeVariant.BodyStrong)
                        // A failed count stops the spinners; the line below says why.
                        !s.sizingFailed -> CircularProgressIndicator(Modifier.size(20.dp), color = colors.icon, strokeWidth = 2.dp)
                    }
                }
            }
        }

        val chosen = s.chosen
        val res = LocalResources.current
        val status = when {
            s.sizingFailed -> stringResource(R.string.settings_retrain_sizing_failed)
            chosen == null -> null
            chosen.scans == 0 -> stringResource(R.string.settings_retrain_none)
            s.noSpace -> stringResource(R.string.settings_retrain_no_space, formatBytes(chosen.bytes))
            else -> res.getQuantityString(R.plurals.settings_retrain_size, chosen.scans, chosen.scans, formatBytes(chosen.bytes))
        }
        if (status != null) {
            val alert = s.noSpace || s.sizingFailed
            AppText(status, if (alert) Modifier.semantics { liveRegion = LiveRegionMode.Assertive } else Modifier)
        }

        YButton(
            stringResource(R.string.settings_retrain_export),
            vm::export,
            icon = IconName.Package,
            size = ButtonSize.Lg,
            fullWidth = true,
            loading = s.working,
            enabled = s.canExport || s.working,
        )
    }
}
