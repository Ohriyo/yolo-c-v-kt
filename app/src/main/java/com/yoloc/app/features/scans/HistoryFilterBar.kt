package com.yoloc.app.features.scans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.BottomSheet
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.ChipSize
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.Tone
import com.yoloc.app.components.VerdictChip
import com.yoloc.app.components.YButton
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.components.verdictShortName
import com.yoloc.app.db.repositories.Plot
import com.yoloc.app.db.repositories.PlotFilter
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.theme.YoloCType

private enum class Sheet { Status, Date, Plot }

@Composable
private fun rangeName(r: DateRange): String = stringResource(
    when (r) {
        DateRange.All -> R.string.history_range_all
        DateRange.Today -> R.string.history_range_today
        DateRange.Week -> R.string.history_range_week
        DateRange.Month -> R.string.history_range_month
    },
)

/**
 * RN: features/scans/HistoryFilterBar.tsx. Search plus three filter pills (Status, Date, Plot). A
 * pill shows its current value and fills when active, so the user can always see why the list is
 * shorter than expected. The search text and its 250 ms debounce live in HistoryViewModel.
 *
 * @param plots For the Plot sheet; null until [onPlotSheet] has loaded them.
 * @param onPlotSheet Called each time the Plot sheet opens (RN reloads plots whenever it shows).
 */
@Composable
fun HistoryFilterBar(
    filter: HistoryFilter,
    searchText: String,
    plotName: String?,
    plots: List<Plot>?,
    plotsFailed: Boolean,
    onSearchText: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilter: (HistoryFilter) -> Unit,
    onPlot: (PlotFilter, String?) -> Unit,
    onClearPills: () -> Unit,
    onPlotSheet: () -> Unit,
) {
    val colors = YoloCTheme.colors
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    LaunchedEffect(sheet) { if (sheet == Sheet.Plot) onPlotSheet() }

    val statusLabel = when (filter.verdicts.size) {
        0 -> stringResource(R.string.history_filter_status)
        1 -> verdictShortName(filter.verdicts.first())
        else -> pluralStringResource(R.plurals.history_filter_status_many, filter.verdicts.size, filter.verdicts.size)
    }
    val dateLabel = if (filter.range == DateRange.All) stringResource(R.string.history_filter_date) else rangeName(filter.range)
    val plotLabel = when (val p = filter.plot) {
        PlotFilter.Any -> stringResource(R.string.history_filter_plot)
        PlotFilter.None -> stringResource(R.string.history_no_plot)
        is PlotFilter.Id -> plotName ?: stringResource(R.string.history_filter_plot)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val searchLabel = stringResource(R.string.history_search_label)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(Radii.full))
                .background(colors.surfaceRaised)
                .border(1.dp, colors.border, RoundedCornerShape(Radii.full))
                .padding(start = Spacing.md, end = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IconName.Search, size = 20.dp)
            Box(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                if (searchText.isEmpty()) AppText(stringResource(R.string.history_search_placeholder), Modifier.clearAndSetSemantics {}, tone = Tone.Muted)
                BasicTextField(
                    value = searchText,
                    onValueChange = onSearchText,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
                    textStyle = YoloCType.body.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.focus),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                )
            }
            if (searchText.isNotEmpty()) {
                val clear = stringResource(R.string.history_clear_search)
                Box(
                    Modifier
                        .size(TouchTarget)
                        .a11yAction(clear, onClearSearch)
                        .clickable(onClick = onClearSearch),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(IconName.X, size = 20.dp)
                }
            }
        }

        // One scrolling row of filter chips (Material pattern), so the fixed header stays short and
        // more of the list is visible. It bleeds to the screen edges.
        Row(
            Modifier
                .bleed(Spacing.screen)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterPill(statusLabel, filter.verdicts.isNotEmpty()) { sheet = Sheet.Status }
            FilterPill(dateLabel, filter.range != DateRange.All) { sheet = Sheet.Date }
            FilterPill(plotLabel, filter.plot != PlotFilter.Any) { sheet = Sheet.Plot }
            if (activeFilterCount(filter) > 0) {
                YButton(stringResource(R.string.history_clear_filters), onClearPills, variant = ButtonVariant.Ghost)
            }
        }
    }

    BottomSheet(sheet == Sheet.Status, stringResource(R.string.history_status_sheet_title), onClose = { sheet = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (v in VERDICT_ORDER) {
                val selected = v in filter.verdicts
                Option(checkbox = true, label = verdictShortName(v), selected = selected, onClick = {
                    val next = if (selected) filter.verdicts - v else filter.verdicts + v
                    onFilter(filter.copy(verdicts = VERDICT_ORDER.filter { it in next }))
                }) { VerdictChip(v, size = ChipSize.Md) }
            }
        }
        YButton(stringResource(R.string.history_done), { sheet = null }, size = ButtonSize.Lg, fullWidth = true)
    }

    BottomSheet(sheet == Sheet.Date, stringResource(R.string.history_date_sheet_title), onClose = { sheet = null }) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (r in DateRange.entries) {
                Option(checkbox = false, label = rangeName(r), selected = filter.range == r, onClick = {
                    onFilter(filter.copy(range = r))
                    sheet = null
                })
            }
        }
    }

    BottomSheet(sheet == Sheet.Plot, stringResource(R.string.history_plot_sheet_title), onClose = { sheet = null }) {
        val choose = { plot: PlotFilter, name: String? ->
            onPlot(plot, name)
            sheet = null
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Option(false, stringResource(R.string.history_all_plots), filter.plot == PlotFilter.Any, { choose(PlotFilter.Any, null) })
            Option(false, stringResource(R.string.history_no_plot), filter.plot == PlotFilter.None, { choose(PlotFilter.None, null) })
            for (p in plots.orEmpty()) {
                Option(false, p.name, filter.plot == PlotFilter.Id(p.id), { choose(PlotFilter.Id(p.id), p.name) })
            }
        }
        when {
            plotsFailed -> AppText(stringResource(R.string.history_plots_failed), tone = Tone.Muted)
            plots?.isEmpty() == true -> AppText(stringResource(R.string.history_no_plots_yet), tone = Tone.Muted)
        }
    }
}

/** RN: pillScroller's negative margin. Lets a row extend [by] past the parent's side padding. */
private fun Modifier.bleed(by: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = YoloCTheme.colors
    val fg = if (active) colors.primary.text else colors.text
    val press = rememberPress()
    val shape = RoundedCornerShape(Radii.full)
    Row(
        Modifier
            .heightIn(min = TouchTarget)
            .clip(shape)
            .background(
                when {
                    active -> colors.primary.bg
                    press.pressed.value -> colors.surfaceRaised
                    else -> colors.surface
                },
            )
            .border(1.5.dp, if (active) colors.primary.border else colors.border, shape)
            .a11yAction(label, onClick, selected = active, hint = stringResource(R.string.history_filter_hint))
            .clickable(interactionSource = press.source, indication = null, onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(label, variant = TypeVariant.Label, color = fg, maxLines = 1)
        Icon(IconName.ChevronDown, size = 18.dp, color = fg)
    }
}

/** A checkbox or radio row in a filter sheet. [content] replaces the plain label (status chips). */
@Composable
private fun Option(
    checkbox: Boolean,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = YoloCTheme.colors
    val press = rememberPress()
    val shape = RoundedCornerShape(Radii.md)
    val mark = when {
        checkbox && selected -> IconName.CheckSquare
        checkbox -> IconName.Square
        selected -> IconName.Disc
        else -> IconName.Circle
    }
    val interaction = if (checkbox) {
        Modifier
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Checkbox
                toggleableState = ToggleableState(selected)
                onClick { onClick(); true }
            }
            .toggleable(selected, interactionSource = press.source, indication = null, role = Role.Checkbox) { onClick() }
    } else {
        Modifier
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.RadioButton
                this.selected = selected
                onClick { onClick(); true }
            }
            .selectable(selected, interactionSource = press.source, indication = null, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (press.pressed.value || selected) colors.surface else colors.surfaceRaised)
            .border(1.5.dp, if (selected) colors.text else colors.border, shape)
            .then(interaction)
            .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(mark, size = 22.dp)
        if (content != null) content() else AppText(label, Modifier.weight(1f), variant = TypeVariant.BodyStrong)
    }
}
