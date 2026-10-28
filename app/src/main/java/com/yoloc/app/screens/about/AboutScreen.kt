package com.yoloc.app.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Card
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.RecallPods
import com.yoloc.app.components.HeaderedSection
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.StatusChip
import com.yoloc.app.components.Tone
import com.yoloc.app.components.a11yLabel
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.util.toFixed

/** RN: `${(v * 100).toFixed(1)}%`. */
private fun percent(v: Double) = "${toFixed(v * 100, 1)}%"

/**
 * RN: app/about.tsx (CLAUDE.md 3.4). Everything about the model is read from model.config.json
 * and the classes table, so a retrained model updates this screen with no code change. The
 * numbers are shown honestly: the headline is the weakest one that matters most, early-stage
 * recall.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(vm: AboutViewModel = viewModel(factory = AboutViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    val colors = YoloCTheme.colors
    val config = vm.config
    val metrics = config.metrics
    val recall = metrics.earlyStageRecall
    val found = vm.found
    val missed = vm.missed

    ScreenContainer {
        // The disclaimer leads, verbatim (CLAUDE.md 3.4).
        val disclaimer = stringResource(R.string.about_disclaimer)
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.inverse.bg, RoundedCornerShape(Radii.md))
                .a11yLabel(disclaimer)
                .padding(Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IconName.Shield, size = 24.dp, color = colors.inverse.icon)
            AppText(disclaimer, Modifier.weight(1f), variant = TypeVariant.BodyStrong, color = colors.inverse.text)
        }

        if (found != null && missed != null && recall != null) {
            HeaderedSection(stringResource(R.string.about_accuracy_title)) {
                Card {
                    AppText(stringResource(R.string.about_recall_headline, found), variant = TypeVariant.Title)
                    RecallPods(found, stringResource(R.string.about_recall_a11y, found))
                    AppText(stringResource(R.string.about_recall_body, percent(recall), missed))
                }
                Card {
                    listOf(
                        R.string.about_metric_test_map50 to metrics.testMap50,
                        R.string.about_metric_early_test_map50 to metrics.earlyStageTestMap50,
                        R.string.about_metric_val_map50 to metrics.valMap50,
                    ).forEach { (label, value) -> if (value != null) Metric(stringResource(label), value) }
                    AppText(stringResource(R.string.about_metrics_note), variant = TypeVariant.Caption, tone = Tone.Muted)
                }
            }
        }

        HeaderedSection(stringResource(R.string.about_limitations_title)) {
            Card {
                listOf(
                    stringResource(R.string.about_limitation_aid),
                    stringResource(R.string.about_limitation_misses, missed ?: 4),
                    stringResource(R.string.about_limitation_conditions),
                    stringResource(R.string.about_limitation_labels),
                    stringResource(R.string.about_limitation_retrain),
                ).forEach { Bullet(IconName.AlertCircle, it) }
            }
        }

        HeaderedSection(stringResource(R.string.about_model_title)) {
            Card {
                InfoRow(stringResource(R.string.about_model), config.displayName)
                InfoRow(stringResource(R.string.about_version), config.id)
                InfoRow(stringResource(R.string.about_input_size), stringResource(R.string.about_input_size_value, config.inputSize))
                AppText(stringResource(R.string.about_labels), tone = Tone.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    s.classes.orEmpty().forEach { StatusChip(StatusKey.fromToken(it.colorToken), label = it.displayName) }
                }
            }
        }

        HeaderedSection(stringResource(R.string.about_privacy_title)) {
            Card { Bullet(IconName.Lock, stringResource(R.string.about_privacy_body)) }
        }

        if (vm.appVersion.isNotEmpty()) {
            AppText(
                stringResource(R.string.about_app_version, vm.appVersion),
                Modifier.fillMaxWidth(),
                variant = TypeVariant.Caption,
                tone = Tone.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: Double) {
    val colors = YoloCTheme.colors
    val shown = percent(value)
    Column(
        Modifier.a11yLabel("$label: $shown"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs + 2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
            AppText(label, Modifier.weight(1f).alignByBaseline())
            AppText(shown, Modifier.alignByBaseline(), variant = TypeVariant.BodyStrong)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radii.full)).background(colors.border)) {
            Box(Modifier.fillMaxWidth(value.toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(Radii.full)).background(colors.text))
        }
    }
}

@Composable
private fun Bullet(icon: IconName, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
        Icon(icon, Modifier.padding(top = 2.dp), size = 18.dp)
        AppText(text, Modifier.weight(1f))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppText(label, tone = Tone.Muted)
        AppText(value, Modifier.weight(1f), variant = TypeVariant.BodyStrong, textAlign = TextAlign.End)
    }
}
