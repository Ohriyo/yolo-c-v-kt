package com.yoloc.app.screens.plots

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.BottomSheet
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ConfirmDialog
import com.yoloc.app.components.EmptyState
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.components.YTextField
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.db.repositories.PlotWithCount
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/** RN: maxLength={60} on the plot name fields. */
private const val NAME_MAX = 60

/**
 * RN: app/plots.tsx (CLAUDE.md 3.2). Add, rename and delete the farm areas scans are grouped by.
 * Tapping a plot opens History filtered to it. Deleting a plot keeps its scans.
 *
 * @param onOpenPlot Gets the action that sends the History request; the NavHost runs it (or not, if
 *   leaving is stopped) together with showing History.
 */
@Composable
fun PlotsScreen(onOpenPlot: (sendRequest: () -> Unit) -> Unit, vm: PlotsViewModel = viewModel(factory = PlotsViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    val colors = YoloCTheme.colors
    ScreenContainer {
        AppText(stringResource(R.string.plots_intro), tone = Tone.Muted)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            val placeholder = stringResource(R.string.plots_new_placeholder)
            YTextField(
                s.name,
                vm::setName,
                placeholder = placeholder,
                accessibilityLabel = placeholder,
                modifier = Modifier.weight(1f),
                minHeight = 52.dp,
                maxLength = NAME_MAX,
                pill = true,
                background = colors.surfaceRaised,
                onDone = vm::add,
            )
            // The duplicate check needs the list, so Add waits for it.
            YButton(stringResource(R.string.plots_add), vm::add, icon = IconName.Plus, enabled = s.canAdd)
        }
        when (s.problem) {
            PlotsViewModel.Problem.Duplicate -> Alert(stringResource(R.string.plots_duplicate))
            PlotsViewModel.Problem.Failed -> Alert(stringResource(R.string.plots_failed))
            null -> Unit
        }

        when (val load = s.load) {
            PlotsViewModel.Load.Loading -> {
                val title = stringResource(R.string.plots_title)
                Box(Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.semantics { contentDescription = title }, color = colors.text)
                }
            }
            PlotsViewModel.Load.Error -> ErrorState(stringResource(R.string.plots_load_failed), onRetry = vm::retry)
            is PlotsViewModel.Load.Ready -> if (load.plots.isEmpty()) {
                EmptyState(IconName.Map, stringResource(R.string.plots_empty_title), body = stringResource(R.string.plots_empty_body))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    for (plot in load.plots) {
                        key(plot.id) {
                            PlotRow(
                                plot,
                                onOpen = { onOpenPlot { vm.open(plot) } },
                                onRename = { vm.startRename(plot) },
                                onDelete = { vm.askDelete(plot) },
                            )
                        }
                    }
                }
            }
        }
    }

    s.renaming?.let { plot ->
        key(plot.id) {
            RenameSheet(plot, s.renameSaving, isDuplicate = { vm.isDuplicate(it, plot.id) }, onSave = vm::rename, onClose = vm::cancelRename)
        }
    }
    s.deleting?.let { plot ->
        ConfirmDialog(
            title = stringResource(R.string.plots_delete_title, plot.name),
            body = if (plot.scanCount == 0) {
                stringResource(R.string.plots_delete_body_none)
            } else {
                pluralStringResource(R.plurals.plots_delete_body, plot.scanCount, plot.scanCount)
            },
            confirmLabel = stringResource(R.string.plots_delete),
            onConfirm = vm::confirmDelete,
            cancelLabel = stringResource(R.string.plots_cancel),
            onCancel = vm::cancelDelete,
        )
    }
}

/** RN: accessibilityRole="alert". */
@Composable
private fun Alert(text: String) = AppText(text, Modifier.semantics { liveRegion = LiveRegionMode.Assertive })

@Composable
private fun PlotRow(plot: PlotWithCount, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val colors = YoloCTheme.colors
    val count = pluralStringResource(R.plurals.plots_scan_count, plot.scanCount, plot.scanCount)
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(Radii.md)).padding(end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .alpha(if (press.pressed.value) 0.7f else 1f)
                .a11yAction("${plot.name}, $count", onOpen)
                .clickable(interactionSource = press.source, indication = null, onClick = onOpen)
                .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IconName.Map, size = 22.dp, color = colors.icon)
            Column(Modifier.weight(1f)) {
                AppText(plot.name, variant = TypeVariant.BodyStrong, maxLines = 2)
                AppText(count, variant = TypeVariant.Caption, tone = Tone.Muted)
            }
        }
        IconButton(IconName.Edit3, stringResource(R.string.plots_rename_a11y, plot.name), onRename)
        IconButton(IconName.Trash2, stringResource(R.string.plots_delete_a11y, plot.name), onDelete)
    }
}

@Composable
private fun IconButton(icon: IconName, label: String, onPress: () -> Unit) {
    val colors = YoloCTheme.colors
    val press = rememberPress()
    Box(
        Modifier
            .size(TouchTarget)
            .clip(CircleShape)
            .background(if (press.pressed.value) colors.surfaceRaised else Color.Transparent)
            .a11yAction(label, onPress)
            .clickable(interactionSource = press.source, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, size = 20.dp, color = colors.icon)
    }
}

@Composable
private fun RenameSheet(plot: PlotWithCount, saving: Boolean, isDuplicate: (String) -> Boolean, onSave: (String) -> Unit, onClose: () -> Unit) {
    var value by remember { mutableStateOf(plot.name) }
    val duplicate = isDuplicate(value)
    val unchanged = value.trim() == plot.name
    val title = stringResource(R.string.plots_rename_title)
    // RN: autoFocus and selectTextOnFocus.
    val focus = remember { FocusRequester() }
    BottomSheet(visible = true, title = title, onClose = onClose) {
        YTextField(
            value,
            { value = it },
            placeholder = "",
            accessibilityLabel = title,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            minHeight = 52.dp,
            maxLength = NAME_MAX,
            pill = true,
            selectAllOnFocus = true,
        )
        if (duplicate) Alert(stringResource(R.string.plots_duplicate))
        YButton(
            stringResource(R.string.plots_save),
            { onSave(value) },
            icon = IconName.Check,
            size = ButtonSize.Lg,
            fullWidth = true,
            loading = saving,
            enabled = value.isNotBlank() && !duplicate && !unchanged,
        )
        // Inside the sheet, so the field is attached when focus is asked for.
        LaunchedEffect(focus) { focus.requestFocus() }
    }
}
