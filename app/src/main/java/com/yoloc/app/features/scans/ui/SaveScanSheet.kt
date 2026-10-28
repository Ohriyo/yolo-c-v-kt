package com.yoloc.app.features.scans.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.BottomSheet
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.YButton
import com.yoloc.app.components.YTextField
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.db.repositories.Plot
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import kotlinx.coroutines.launch

private const val NOTES_MAX = 500

/** RN: testID="save-scan-confirm" (the footer has a "Save scan" button too). */
const val SAVE_SCAN_CONFIRM_TAG = "save-scan-confirm"

/**
 * RN: features/scans/SaveScanSheet.tsx. Save-to-plot (CLAUDE.md 3.2): choose a plot (or none),
 * optionally add one on the spot, add a note, then save. The choice and note are kept while the
 * sheet is closed and reopened, as RN's Modal keeps its state. Saving itself is the caller's
 * atomic save path; the sheet cannot be closed while it runs.
 *
 * @param onAddPlot Creates the plot; returns its id to select, or null when it failed.
 */
@Composable
fun SaveScanSheet(
    visible: Boolean,
    saving: Boolean,
    error: Boolean,
    plotError: Boolean,
    plots: List<Plot>,
    onAddPlot: suspend (String) -> String?,
    onSave: (plotId: String?, notes: String) -> Unit,
    onClose: () -> Unit,
    onManagePlots: () -> Unit,
) {
    val colors = YoloCTheme.colors
    var plotId by rememberSaveable { mutableStateOf<String?>(null) }
    var newPlot by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    // A plot deleted in Manage plots is no longer a choice (saving to it would fail).
    val selectedId = plotId?.takeIf { id -> plots.any { it.id == id } }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val addPlot = {
        // One add at a time: a double tap (or Done, then Add) must not create the plot twice.
        if (!adding) {
            adding = true
            val name = newPlot
            scope.launch {
                try {
                    val id = onAddPlot(name)
                    if (id != null) {
                        plotId = id
                        newPlot = ""
                    }
                } finally {
                    adding = false
                }
            }
        }
    }

    BottomSheet(visible, stringResource(R.string.result_save_title), onClose = onClose, dismissable = !saving) {
        AppText(stringResource(R.string.result_plot_label), variant = TypeVariant.BodyStrong)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            val options = listOf<Pair<String?, String>>(null to stringResource(R.string.result_no_plot)) + plots.map { it.id to it.name }
            for ((id, name) in options) {
                val selected = id == selectedId
                val press = rememberPress()
                val shape = RoundedCornerShape(Radii.full)
                val choose = { plotId = id }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget)
                        .alpha(if (press.pressed.value) 0.85f else 1f)
                        .clip(shape)
                        .background(if (selected) colors.surfaceRaised else colors.surface)
                        .border(2.dp, if (selected) colors.text else colors.border, shape)
                        .a11yAction(name, choose, role = Role.RadioButton, selected = selected)
                        .selectable(selected, interactionSource = press.source, indication = null, role = Role.RadioButton, onClick = choose)
                        .padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (id == null) IconName.Slash else IconName.MapPin, size = 20.dp, color = if (selected) colors.text else colors.icon)
                    AppText(name, Modifier.weight(1f), maxLines = 1)
                    if (selected) Icon(IconName.Check, size = 20.dp, color = colors.text)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            val placeholder = stringResource(R.string.result_new_plot_placeholder)
            YTextField(
                newPlot,
                { newPlot = it },
                placeholder = placeholder,
                accessibilityLabel = placeholder,
                modifier = Modifier.weight(1f),
                onDone = { if (newPlot.isNotBlank()) addPlot() },
            )
            YButton(
                stringResource(R.string.result_add_plot),
                addPlot,
                variant = ButtonVariant.Secondary,
                icon = IconName.Plus,
                enabled = newPlot.isNotBlank() && !adding,
            )
        }
        if (plotError) {
            AppText(stringResource(R.string.result_plot_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        YButton(stringResource(R.string.result_manage_plots), onManagePlots, variant = ButtonVariant.Ghost, icon = IconName.Map, enabled = !saving)

        val notesLabel = stringResource(R.string.result_notes_label)
        AppText(notesLabel, variant = TypeVariant.BodyStrong)
        YTextField(
            notes,
            { notes = it },
            placeholder = stringResource(R.string.result_notes_placeholder),
            accessibilityLabel = notesLabel,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minHeight = 88.dp,
            maxLength = NOTES_MAX,
        )

        if (error) {
            AppText(stringResource(R.string.result_save_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        YButton(
            stringResource(if (saving) R.string.result_saving else R.string.result_save_scan),
            { onSave(selectedId, notes) },
            Modifier.testTag(SAVE_SCAN_CONFIRM_TAG),
            icon = IconName.Save,
            size = ButtonSize.Lg,
            fullWidth = true,
            loading = saving,
            enabled = !saving,
        )
    }
}
