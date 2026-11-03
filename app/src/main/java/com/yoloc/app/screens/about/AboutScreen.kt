package com.yoloc.app.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.util.toFixed

/** RN: `${(v * 100).toFixed(1)}%`. */
private fun percent(v: Double) = "${toFixed(v * 100, 1)}%"

/**
 * RN: app/about.tsx (CLAUDE.md 3.4). Everything about the model is read from model_config.json
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
                    AppText(stringResource(R.string.about_recall_body, metrics.headline?.label.orEmpty(), percent(recall), missed))
                }
                listOfNotNull(metrics.test, metrics.validation).forEach { evaluation ->
                    Card { MetricsTable(evaluation, s.classes.orEmpty()) }
                }
            }
        }

        HeaderedSection(stringResource(R.string.about_limitations_title)) {
            Card {
                listOfNotNull(
                    stringResource(R.string.about_limitation_aid),
                    // Only with a measured recall: no number from another model stands in for it.
                    missed?.let { stringResource(R.string.about_limitation_misses, it) },
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

/** Every score of one evaluation: all classes first, then each class. */
@Composable
private fun MetricsTable(evaluation: ModelConfig.Evaluation, classes: List<DetectionClass>) {
    val precision = stringResource(R.string.about_metric_precision)
    val recall = stringResource(R.string.about_metric_recall)
    val map50 = stringResource(R.string.about_metric_map50)
    val map5095 = stringResource(R.string.about_metric_map50_95)
    AppText(stringResource(R.string.about_metrics_source, evaluation.label), variant = TypeVariant.BodyStrong)
    // Each row below says its own column names to TalkBack, so the header stays out of its way.
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.weight(1f))
        listOf(precision, recall, map50, map5095).forEachIndexed { i, name -> MetricCell(name, i, Tone.Muted) }
    }
    @Composable
    fun row(name: String, scores: ModelConfig.Scores) {
        val cells = listOf(scores.precision, scores.recall, scores.map50, scores.map5095).map(::percent)
        val spoken = "$name: $precision ${cells[0]}, $recall ${cells[1]}, $map50 ${cells[2]}, $map5095 ${cells[3]}"
        Row(Modifier.fillMaxWidth().a11yLabel(spoken), verticalAlignment = Alignment.CenterVertically) {
            AppText(name, Modifier.weight(1f), variant = TypeVariant.Caption)
            cells.forEachIndexed { i, cell -> MetricCell(cell, i) }
        }
    }
    row(stringResource(R.string.about_metrics_all), evaluation.all)
    classes.forEach { c -> evaluation.classes[c.key]?.let { row(c.displayName, it) } }
    AppText(stringResource(R.string.about_metrics_note), variant = TypeVariant.Caption, tone = Tone.Muted)
}

/** Wide enough that "Precision" stays on one line on a 360dp phone; "mAP50-95" wraps at its hyphen. */
private val METRIC_COLUMNS = listOf(60.dp, 46.dp, 50.dp, 52.dp)

@Composable
private fun MetricCell(text: String, column: Int, tone: Tone = Tone.Default) {
    AppText(text, Modifier.width(METRIC_COLUMNS[column]), variant = TypeVariant.Caption, tone = tone, textAlign = TextAlign.End)
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
