package com.yoloc.app.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.Card
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.HeaderedSection
import com.yoloc.app.components.TextAction
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.components.statusIcon
import com.yoloc.app.components.verdictShortName
import com.yoloc.app.components.verdictStatus
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.ScanRow
import com.yoloc.app.features.scans.UndoDeleteSnackbar
import com.yoloc.app.features.scans.formatShortWhen
import com.yoloc.app.screens.appContainer
import com.yoloc.app.store.ScanLibrary
import kotlinx.coroutines.CoroutineScope
import com.yoloc.app.screens.rememberDayFormat
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

private val COUNT_VERDICTS = listOf(Verdict.Healthy, Verdict.EarlyStage, Verdict.Infected)

/**
 * RN: app/(tabs)/index.tsx. Task first (CLAUDE.md 3.4): the scan call to action leads, then the
 * last 30 days at a glance, the 3 most recent scans, and a photo tip that changes daily.
 */
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onOpenScan: (String) -> Unit,
    onOpenHistory: () -> Unit,
    /** A count chip: History filtered to that result (NavActions.openHistory). */
    onOpenCount: (Verdict) -> Unit,
    onOpenLearn: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    /** The Undo snackbar's store and the scope its restore runs in (tests pass their own). */
    library: ScanLibrary = appContainer().scanLibrary,
    undoScope: CoroutineScope = appContainer().appScope,
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refreshNow()
        onPauseOrDispose {}
    }
    val dayFormat = rememberDayFormat()
    val tips = stringArrayResource(R.array.home_tips)
    val tip = tips[dailyTipIndex(now, tips.size)]

    Box(Modifier.fillMaxSize()) {
        // Bottom room so the Undo snackbar never covers the tip.
        ScreenContainer(contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.screen, end = Spacing.screen, bottom = 96.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AppText(stringResource(R.string.home_hero_title), variant = TypeVariant.Headline)
                YButton(stringResource(R.string.home_scan_cta), onScan, icon = IconName.Camera, size = ButtonSize.Lg, fullWidth = true)
            }

            HeaderedSection(stringResource(R.string.home_counts_title), subtitle = stringResource(R.string.home_counts_subtitle)) {
                // Three equal capsules in one row, so no status is ever orphaned on a second line.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    for (v in COUNT_VERDICTS) {
                        val count = (summary as? HomeViewModel.Summary.Ready)?.stats?.scansByVerdict?.get(v)
                        CountChip(v, if (summary is HomeViewModel.Summary.Ready) count ?: 0 else null, Modifier.weight(1f)) { onOpenCount(v) }
                    }
                }
            }

            val s = summary
            HeaderedSection(
                stringResource(R.string.home_recent_title),
                action = if (s is HomeViewModel.Summary.Ready && s.recent.isNotEmpty()) {
                    TextAction(stringResource(R.string.home_see_all), onOpenHistory)
                } else {
                    null
                },
            ) {
                when (s) {
                    HomeViewModel.Summary.Loading -> RecentSkeleton()
                    HomeViewModel.Summary.Error -> Card {
                        AppText(stringResource(R.string.home_load_failed))
                        YButton(stringResource(R.string.error_retry), vm::reload, icon = IconName.RefreshCw, variant = ButtonVariant.Secondary)
                    }
                    is HomeViewModel.Summary.Ready -> if (s.recent.isEmpty()) {
                        Card {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Icon(IconName.Clock, size = 24.dp)
                                Column(Modifier.weight(1f)) {
                                    AppText(stringResource(R.string.home_no_scans_title), variant = TypeVariant.BodyStrong)
                                    AppText(stringResource(R.string.home_no_scans_body), tone = Tone.Muted)
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            for (scan in s.recent) {
                                key(scan.id) { ScanRow(scan, formatShortWhen(scan.createdAt, now, dayFormat), onOpenScan) }
                            }
                        }
                    }
                }
            }

            HeaderedSection(stringResource(R.string.home_tip_title), action = TextAction(stringResource(R.string.home_learn_more), onOpenLearn)) {
                Card {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(IconName.Camera, size = 22.dp)
                        AppText(tip, Modifier.weight(1f))
                    }
                }
            }
        }
        UndoDeleteSnackbar(library, undoScope, Modifier.align(Alignment.BottomCenter))
    }
}

/** A 30-day count that opens History filtered to that result. Status fill + icon + label. */
@Composable
private fun CountChip(verdict: Verdict, count: Int?, modifier: Modifier, onClick: () -> Unit) {
    val status = verdictStatus(verdict) ?: return
    val s = YoloCTheme.colors.status(status)
    val label = verdictShortName(verdict)
    // While the count is loading or failed, only the result is spoken: "0 scans" would be wrong.
    val a11y = if (count == null) label else pluralStringResource(R.plurals.home_count_a11y, count, label, count)
    val press = rememberPress()
    Column(
        modifier
            .heightIn(min = TouchTarget + Spacing.md)
            .alpha(if (press.pressed.value) 0.85f else 1f)
            .clip(RoundedCornerShape(Radii.md))
            .background(s.fill)
            .a11yAction(a11y, onClick)
            .clickable(interactionSource = press.source, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(statusIcon.getValue(status), size = 20.dp, color = s.onFill)
            AppText(count?.toString() ?: "–", variant = TypeVariant.Title, color = s.onFill)
        }
        AppText(label, variant = TypeVariant.Caption, color = s.onFill, maxLines = 2)
    }
}

@Composable
private fun RecentSkeleton() {
    val colors = YoloCTheme.colors
    // RN: accessibilityState={{ busy: true }}
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(96.dp).alpha(0.7f).background(colors.surface, RoundedCornerShape(Radii.md)))
        }
    }
}
