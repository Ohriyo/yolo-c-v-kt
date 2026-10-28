package com.yoloc.app.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
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
import com.yoloc.app.components.ArrowLink
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.Card
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.SectionHeader
import com.yoloc.app.components.TextAction
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.components.a11yLabel
import com.yoloc.app.components.cardShadow
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.learn.LearnTopic
import com.yoloc.app.features.scans.ScanRow
import com.yoloc.app.features.scans.UndoDeleteSnackbar
import com.yoloc.app.features.scans.formatShortWhen
import com.yoloc.app.screens.appContainer
import com.yoloc.app.screens.rememberDayFormat
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * RN: app/(tabs)/index.tsx, redesigned 2026-10-28 (the prototype; docs/PARITY.md). Task first
 * (CLAUDE.md 3.4): a greeting, then the hero card with the scan action, the last 7 days at a
 * glance, the 3 most recent scans, the guide, a photo tip that changes daily, and what the app
 * runs on.
 */
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onOpenScan: (String) -> Unit,
    onOpenHistory: () -> Unit,
    /** A stat card: History over the last 7 days, filtered to that result (null: every result). */
    onOpenWeek: (Verdict?) -> Unit,
    onOpenTopic: (LearnTopic) -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    /** The Undo snackbar's store and the scope its restore runs in (tests pass their own). */
    library: ScanLibrary = appContainer().scanLibrary,
    undoScope: CoroutineScope = appContainer().appScope,
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val name by vm.userName.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refreshNow()
        onPauseOrDispose {}
    }
    val dayFormat = rememberDayFormat()
    val tips = stringArrayResource(R.array.home_tips)
    val tip = tips[dailyTipIndex(now, tips.size)]
    val stats = (summary as? HomeViewModel.Summary.Ready)?.stats

    Box(Modifier.fillMaxSize()) {
        // Bottom room so the Undo snackbar never covers the footer.
        ScreenContainer(contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.lg, end = Spacing.screen, bottom = 96.dp)) {
            Greeting(now, name)
            Hero(onScan)
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard(
                    icon = IconName.ScanLine,
                    iconColor = YoloCTheme.colors.icon,
                    label = stringResource(R.string.home_stat_total),
                    count = stats?.totalScans,
                    a11yPlural = R.plurals.home_stat_total_a11y,
                    onClick = { onOpenWeek(null) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatCard(
                    icon = IconName.AlertTriangle,
                    iconColor = YoloCTheme.colors.early.ink,
                    label = stringResource(R.string.home_stat_early),
                    count = stats?.let { it.scansByVerdict[Verdict.EarlyStage] ?: 0 },
                    a11yPlural = R.plurals.home_stat_early_a11y,
                    onClick = { onOpenWeek(Verdict.EarlyStage) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            RecentCard(summary, now, dayFormat, onOpenScan, onOpenHistory, vm::reload)
            LearnCard { onOpenTopic(LearnTopic.Stages) }
            PhotoTip(tip) { onOpenTopic(LearnTopic.Photos) }
            Footer()
        }
        UndoDeleteSnackbar(library, undoScope, Modifier.align(Alignment.BottomCenter))
    }
}

/** The date, the greeting by part of the day (with the name when given) and the line under it. */
@Composable
private fun Greeting(now: Long, name: String) {
    val locale = LocalConfiguration.current.locales[0]
    val zoned = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()) }
    val date = remember(zoned, locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(zoned) }
    val part = dayPart(zoned.hour)
    val greeting = if (name.isBlank()) {
        stringResource(
            when (part) {
                DayPart.Morning -> R.string.home_greeting_morning
                DayPart.Afternoon -> R.string.home_greeting_afternoon
                DayPart.Evening -> R.string.home_greeting_evening
            },
        )
    } else {
        stringResource(
            when (part) {
                DayPart.Morning -> R.string.home_greeting_morning_name
                DayPart.Afternoon -> R.string.home_greeting_afternoon_name
                DayPart.Evening -> R.string.home_greeting_evening_name
            },
            name,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Overline(date)
        AppText(greeting, variant = TypeVariant.Headline)
        AppText(stringResource(R.string.home_subtitle), tone = Tone.Muted)
    }
}

/** The small spaced line over a heading (the prototype's). Spoken as written, not spelled. */
@Composable
private fun Overline(text: String, color: Color = YoloCTheme.colors.textFaint) {
    val locale = LocalConfiguration.current.locales[0]
    AppText(text.uppercase(locale), Modifier.a11yLabel(text), variant = TypeVariant.Overline, color = color)
}

/** The leaf-tint hero: what the app is for and the scan action, with a pod photo at the side. */
@Composable
private fun Hero(onScan: () -> Unit) {
    val colors = YoloCTheme.colors
    val shape = RoundedCornerShape(Radii.md)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .cardShadow(shape)
            .clip(shape)
            .background(colors.selected),
    ) {
        Column(Modifier.weight(1f).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Overline(stringResource(R.string.home_hero_overline), colors.textMuted)
            AppText(stringResource(R.string.home_hero_title), variant = TypeVariant.Verdict)
            AppText(stringResource(R.string.home_hero_body), variant = TypeVariant.Caption, tone = Tone.Muted)
            YButton(stringResource(R.string.home_scan_cta), onScan, Modifier.padding(top = Spacing.xxs), icon = IconName.ScanLine)
        }
        // Decorative (a healthy pod, not an example of a stage), so screen readers skip it.
        Image(
            painterResource(R.drawable.photo_cacao_single),
            contentDescription = null,
            modifier = Modifier.width(108.dp).fillMaxHeight(),
            contentScale = ContentScale.Crop,
            // The pod sits left of centre in the photo.
            alignment = BiasAlignment(-0.2f, 0f),
        )
    }
}

@Composable
private fun StatCard(
    icon: IconName,
    iconColor: Color,
    label: String,
    count: Int?,
    a11yPlural: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    // While the count is loading or failed, only the label is spoken: "0 scans" would be wrong.
    val a11y = if (count == null) label else pluralStringResource(a11yPlural, count, count)
    Card(modifier, onClick = onClick, accessibilityLabel = a11y, spacing = Spacing.xxs) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, size = 18.dp, color = iconColor)
            AppText(label, variant = TypeVariant.Caption, tone = Tone.Muted, maxLines = 2)
        }
        // The numbers line up across the two cards even when one label wraps.
        Spacer(Modifier.weight(1f))
        AppText(count?.toString() ?: "–", Modifier.padding(top = Spacing.xs), variant = TypeVariant.Readout)
        AppText(stringResource(R.string.home_stat_window), variant = TypeVariant.Caption, tone = Tone.Faint)
    }
}

/** The 3 most recent scans in one card, rows edge to edge. */
@Composable
private fun RecentCard(
    summary: HomeViewModel.Summary,
    now: Long,
    dayFormat: com.yoloc.app.features.scans.DayFormat,
    onOpenScan: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = YoloCTheme.colors
    Card(padding = 0.dp, spacing = 0.dp) {
        SectionHeader(
            stringResource(R.string.home_recent_title),
            Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.xs),
            action = if (summary is HomeViewModel.Summary.Ready && summary.recent.isNotEmpty()) {
                TextAction(stringResource(R.string.home_see_all), onOpenHistory)
            } else {
                null
            },
        )
        when (summary) {
            HomeViewModel.Summary.Loading -> RecentSkeleton()
            HomeViewModel.Summary.Error -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppText(stringResource(R.string.home_load_failed))
                YButton(stringResource(R.string.error_retry), onRetry, icon = IconName.RefreshCw, variant = ButtonVariant.Secondary)
            }
            is HomeViewModel.Summary.Ready -> if (summary.recent.isEmpty()) {
                Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(IconName.Clock, size = 22.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        AppText(stringResource(R.string.home_no_scans_title), variant = TypeVariant.BodyStrong)
                        AppText(stringResource(R.string.home_no_scans_body), tone = Tone.Muted)
                    }
                }
            } else {
                Column(Modifier.padding(bottom = Spacing.xs)) {
                    summary.recent.forEachIndexed { i, scan ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = Spacing.md), thickness = 1.dp, color = colors.border)
                        key(scan.id) { ScanRow(scan, formatShortWhen(scan.createdAt, now, dayFormat), onOpenScan) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSkeleton() {
    val colors = YoloCTheme.colors
    // RN: accessibilityState={{ busy: true }}
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Spacing.md)
            .clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(56.dp).alpha(0.7f).background(colors.surface, RoundedCornerShape(Radii.sm)))
        }
    }
}

/** The guide's way in: the stages topic, with a drawn pod. */
@Composable
private fun LearnCard(onOpen: () -> Unit) {
    Card(padding = 20.dp, spacing = Spacing.sm) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Overline(stringResource(R.string.home_learn_overline))
                AppText(stringResource(R.string.home_learn_title), variant = TypeVariant.Verdict)
            }
            PodMark()
        }
        AppText(stringResource(R.string.home_learn_body), tone = Tone.Muted)
        ArrowLink(stringResource(R.string.home_learn_link), onOpen)
    }
}

/** A cacao pod in outline on a tint disc: the guide card's mark. Decorative. */
@Composable
private fun PodMark() {
    val colors = YoloCTheme.colors
    val ink = colors.icon
    Canvas(Modifier.size(76.dp).background(colors.selected, CircleShape)) {
        val w = size.width
        val h = size.height
        val line = 1.75.dp.toPx()
        rotate(-28f) {
            val podW = w * 0.40f
            val podH = h * 0.62f
            val left = (w - podW) / 2
            val top = (h - podH) / 2 + h * 0.03f
            drawOval(ink, Offset(left, top), Size(podW, podH), style = Stroke(line))
            // Four ridges, tip to tip (no midrib, so it never reads as a leaf).
            for (bend in listOf(-0.36f, -0.14f, 0.14f, 0.36f)) {
                val ridge = Path().apply {
                    moveTo(w / 2, top + line * 2)
                    quadraticTo(w / 2 + podW * bend, h / 2 + h * 0.03f, w / 2, top + podH - line * 2)
                }
                drawPath(ridge, ink, style = Stroke(line * 0.85f, cap = StrokeCap.Round))
            }
            drawLine(ink, Offset(w / 2, top), Offset(w / 2, top - h * 0.09f), line, cap = StrokeCap.Round)
        }
    }
}

/** Today's photo tip, on the ground (no card), and the way to the full photo guide. */
@Composable
private fun PhotoTip(tip: String, onOpen: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(IconName.Sun, Modifier.padding(top = 2.dp), size = 22.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            AppText(stringResource(R.string.home_tip_title), variant = TypeVariant.BodyStrong)
            AppText(tip, tone = Tone.Muted)
            ArrowLink(stringResource(R.string.home_tip_link), onOpen)
        }
    }
}

/** What the app runs on, and that it is a screening aid. */
@Composable
private fun Footer() {
    val colors = YoloCTheme.colors
    val model = appContainer().modelConfig.displayName
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(IconName.WifiOff, size = 20.dp)
            Column(Modifier.weight(1f)) {
                AppText(stringResource(R.string.home_footer_title), variant = TypeVariant.BodyStrong)
                AppText(stringResource(R.string.home_footer_runs, model), variant = TypeVariant.Caption, tone = Tone.Muted)
            }
        }
        AppText(stringResource(R.string.home_footer_aid), variant = TypeVariant.Caption, tone = Tone.Faint)
    }
}
