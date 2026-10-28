package com.yoloc.app.screens.history

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.getValue
import com.yoloc.app.util.rememberReduceMotion
import com.yoloc.app.theme.Motion
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.BottomSheet
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.EmptyState
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.IconName
import com.yoloc.app.components.StateAction
import com.yoloc.app.components.Toast
import com.yoloc.app.components.ToastSlot
import com.yoloc.app.components.ToastTone
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.features.scans.HistoryFilterBar
import com.yoloc.app.features.scans.HistoryItem
import com.yoloc.app.features.scans.ScanRow
import com.yoloc.app.features.scans.SwipeToDelete
import com.yoloc.app.features.scans.THUMB_SIZE
import com.yoloc.app.features.scans.UndoDeleteSnackbar
import com.yoloc.app.features.scans.formatTime
import com.yoloc.app.features.scans.isFiltered
import com.yoloc.app.screens.appContainer
import com.yoloc.app.store.ScanLibrary
import kotlinx.coroutines.CoroutineScope
import com.yoloc.app.screens.rememberDayFormat
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/** RN: FlashList onEndReachedThreshold={0.6}: load more within 0.6 screens of the end. */
private const val END_REACHED_THRESHOLD = 0.6f

/**
 * RN: app/(tabs)/history.tsx (CLAUDE.md 3.2). Every saved scan, newest first and grouped by day,
 * with search and Result / Date / Plot filters. Pages 40 scans at a time into a lazy list, so it
 * stays smooth with hundreds of scans. Delete by swiping left, by long-press, or from Scan Detail;
 * each offers Undo.
 */
@Composable
fun HistoryScreen(
    onOpenScan: (String) -> Unit,
    onScan: () -> Unit,
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
    /** The Undo snackbar's store and the scope its restore runs in (tests pass their own). */
    library: ScanLibrary = appContainer().scanLibrary,
    undoScope: CoroutineScope = appContainer().appScope,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refreshNow()
        onPauseOrDispose {}
    }
    val colors = YoloCTheme.colors
    val filtered = isFiltered(s.filter)
    val ready = s.status == HistoryViewModel.Status.Ready
    val hasRows = s.items.isNotEmpty()
    val list = rememberLazyListState()
    // Redesign 2026-10-28: the records title folds away while the list is scrolled, so the fixed
    // search and chips leave the screen to the scans. Back at the top it returns.
    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 } }
    val reduceMotion = rememberReduceMotion()

    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // Redesign 2026-10-28 (the prototype): the records header with its own New scan.
                AnimatedVisibility(
                    atTop,
                    enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(Motion.BASE)) + fadeIn(tween(Motion.BASE)),
                    exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(Motion.BASE)) + fadeOut(tween(Motion.FAST)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        HistoryTitle(onScan)
                        AppText(stringResource(R.string.history_subtitle), tone = Tone.Muted)
                    }
                }
                // Hidden only when there is nothing to filter yet (the empty state takes over).
                if (filtered || s.total > 0 || !ready) {
                    HistoryFilterBar(
                        filter = s.filter,
                        searchText = s.searchText,
                        plotName = s.plotName,
                        plots = s.plots,
                        plotsFailed = s.plotsFailed,
                        onSearchText = vm::setSearchText,
                        onClearSearch = vm::clearSearch,
                        onFilter = vm::setFilter,
                        onPlot = vm::selectPlot,
                        onClearPills = vm::clearPills,
                        onPlotSheet = vm::loadPlots,
                    )
                }
                if (ready && (s.total > 0 || filtered)) {
                    AppText(
                        if (filtered) {
                            pluralStringResource(R.plurals.history_count_filtered, s.total, s.total)
                        } else {
                            pluralStringResource(R.plurals.history_count, s.total, s.total)
                        },
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        variant = TypeVariant.Caption,
                        tone = Tone.Muted,
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                when {
                    s.status == HistoryViewModel.Status.Error && !hasRows -> Column(Modifier.padding(horizontal = Spacing.screen)) {
                        ErrorState(stringResource(R.string.history_load_failed), onRetry = vm::retry)
                    }
                    s.status == HistoryViewModel.Status.Loading && !hasRows -> SkeletonRows()
                    s.total == 0 && !filtered -> Column(Modifier.padding(horizontal = Spacing.screen)) {
                        EmptyState(
                            IconName.Clock,
                            stringResource(R.string.history_empty_title),
                            body = stringResource(R.string.history_empty_body),
                            action = StateAction(stringResource(R.string.home_scan_cta), onScan, IconName.Camera),
                        )
                    }
                    else -> ScanList(
                        list = list,
                        items = s.items,
                        loadingMore = s.loadingMore,
                        hasMore = s.hasMore,
                        onOpenScan = onOpenScan,
                        onLoadMore = vm::loadMore,
                        onOptions = vm::openOptions,
                        onDelete = vm::delete,
                        onClearFilters = vm::clearFilters,
                    )
                }
            }
        }

        val optionsFor = s.optionsFor
        BottomSheet(optionsFor != null, stringResource(R.string.history_options_title), onClose = vm::closeOptions) {
            YButton(
                stringResource(R.string.history_open_scan),
                {
                    vm.closeOptions()
                    if (optionsFor != null) onOpenScan(optionsFor)
                },
                variant = ButtonVariant.Secondary,
                icon = IconName.ArrowRight,
                size = ButtonSize.Lg,
                fullWidth = true,
            )
            YButton(
                stringResource(R.string.history_delete_scan),
                { if (optionsFor != null) vm.delete(optionsFor) },
                icon = IconName.Trash2,
                size = ButtonSize.Lg,
                fullWidth = true,
            )
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            ToastSlot(s.deleteError.takeIf { it }, Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.md)) {
                Toast(stringResource(R.string.history_delete_failed), tone = ToastTone.Error)
            }
            if (!s.deleteError) UndoDeleteSnackbar(library, undoScope)
        }
    }
}

@Composable
private fun ScanList(
    list: LazyListState,
    items: List<HistoryItem>,
    loadingMore: Boolean,
    hasMore: Boolean,
    onOpenScan: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOptions: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    val dayFormat = rememberDayFormat()
    val focus = LocalFocusManager.current
    val optionsLabel = stringResource(R.string.history_options_title)
    // RN: keyboardDismissMode="on-drag".
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { if (it) focus.clearFocus() }
    }
    // RN's onEndReached fires again when the content changes, so a dropped or failed page is asked
    // for again even while the list stays near its end.
    LaunchedEffect(list, items.size, loadingMore, hasMore) {
        snapshotFlow { nearEnd(list) }.collect { if (it) onLoadMore() }
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the Undo snackbar never covers the last row.
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
    ) {
        if (items.isEmpty()) {
            item(key = "no-match") {
                EmptyState(
                    IconName.Search,
                    stringResource(R.string.history_no_match_title),
                    body = stringResource(R.string.history_no_match_body),
                    action = StateAction(stringResource(R.string.history_clear_filters), onClearFilters, IconName.X),
                )
            }
        }
        itemsIndexed(items, key = { _, it -> it.key }, contentType = { _, it -> it.type }) { i, item ->
            when (item) {
                is HistoryItem.Day -> AppText(
                    item.label,
                    Modifier.padding(top = Spacing.lg, bottom = Spacing.xs).semantics { heading() },
                    variant = TypeVariant.Label,
                    tone = Tone.Muted,
                )
                // Redesign 2026-10-28: each day's scans read as one card (the prototype's list card).
                is HistoryItem.Scan -> Box(
                    Modifier.dayGroup(
                        first = items.getOrNull(i - 1) !is HistoryItem.Scan,
                        last = items.getOrNull(i + 1) !is HistoryItem.Scan,
                    ),
                ) {
                    SwipeToDelete(rowKey = item.scan.id, onDelete = { onDelete(item.scan.id) }) {
                        ScanRow(
                            item.scan,
                            formatTime(item.scan.createdAt, dayFormat),
                            onOpenScan,
                            onLongPress = onOptions,
                            longPressLabel = optionsLabel,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
        if (loadingMore) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = YoloCTheme.colors.icon, strokeWidth = 2.dp)
                }
            }
        }
    }
}

/**
 * One row's part of its day card: the card's top corners and edge on the first row, the bottom
 * ones on the last, the sides on every row, and an inset hairline under all but the last.
 */
@Composable
private fun Modifier.dayGroup(first: Boolean, last: Boolean): Modifier {
    val colors = YoloCTheme.colors
    val r = Radii.md
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp,
        topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp,
        bottomEnd = if (last) r else 0.dp,
    )
    val edge = colors.border
    val inset = Spacing.md
    return this
        .clip(shape)
        .background(colors.card)
        .drawWithContent {
            drawContent()
            val w = 1.dp.toPx()
            // Only this row's part of the card's edge: no line where it meets its neighbours.
            clipRect(top = if (first) 0f else w, bottom = if (last) size.height else size.height - w) {
                drawOutline(shape.createOutline(size, layoutDirection, this), edge, style = Stroke(w * 2))
            }
            if (!last) drawLine(edge, Offset(inset.toPx(), size.height - w / 2), Offset(size.width, size.height - w / 2), w)
        }
}

/** Whether the list end is within [END_REACHED_THRESHOLD] viewport heights (rows not yet laid out are estimated). */
private fun nearEnd(list: LazyListState): Boolean {
    val info = list.layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return false
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    if (viewport <= 0) return false
    val average = info.visibleItemsInfo.sumOf { it.size } / info.visibleItemsInfo.size
    val remaining = (last.offset + last.size - info.viewportEndOffset) + (info.totalItemsCount - 1 - last.index) * average
    return remaining < viewport * END_REACHED_THRESHOLD
}

/** First-load placeholder in the shape of real rows, so the layout does not jump. */
@Composable
private fun SkeletonRows() {
    val colors = YoloCTheme.colors
    val label = stringResource(R.string.tabs_history)
    Column(
        Modifier
            .padding(horizontal = Spacing.screen)
            // RN: accessibilityLabel "History", accessibilityState busy.
            .clearAndSetSemantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        repeat(5) {
            Row(
                Modifier
                    .padding(top = Spacing.xs)
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(Radii.md))
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(THUMB_SIZE).alpha(0.5f).background(colors.border, RoundedCornerShape(Radii.md)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(Modifier.fillMaxWidth(0.4f).height(14.dp).alpha(0.5f).background(colors.border, RoundedCornerShape(Radii.full)))
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).alpha(0.5f).background(colors.border, RoundedCornerShape(Radii.full)))
                }
            }
        }
    }
}

/**
 * The records title with its own New scan. With large text or a longer language the button moves
 * under the title instead of breaking it (finish review, 2026-10-28).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTitle(onScan: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(stringResource(R.string.history_title), Modifier.padding(end = Spacing.sm), variant = TypeVariant.Headline)
        YButton(stringResource(R.string.history_new_scan), onScan, icon = IconName.ScanLine)
    }
}
