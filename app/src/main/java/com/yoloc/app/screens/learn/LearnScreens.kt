package com.yoloc.app.screens.learn

import androidx.compose.runtime.getValue
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.features.learn.photo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.Card
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.StatusChip
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.features.learn.howSteps
import com.yoloc.app.features.learn.photoTips
import com.yoloc.app.features.learn.teaser
import com.yoloc.app.features.learn.title
import com.yoloc.app.screens.appContainer
import com.yoloc.app.screens.onboarding.recallOutOfTen
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.features.learn.LearnTopic as Topic

/**
 * RN: app/(tabs)/learn.tsx, redesigned 2026-10-28 as the Guide (the prototype): one photo card per
 * topic, in reading order. Disease content is a draft until an agronomist reviews it, and the
 * screen says so plainly. The photos are decorative (healthy pods, not examples of a stage).
 */
@Composable
fun LearnScreen(onOpenTopic: (Topic) -> Unit) {
    val colors = YoloCTheme.colors
    ScreenContainer(contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.lg, end = Spacing.screen, bottom = Spacing.xxl)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            AppText(stringResource(R.string.learn_heading), variant = TypeVariant.Headline)
            AppText(stringResource(R.string.learn_subtitle), tone = Tone.Muted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Topic.entries.forEach { topic ->
                val title = stringResource(topic.title)
                Card(onClick = { onOpenTopic(topic) }, accessibilityLabel = title, spacing = Spacing.sm) {
                    Image(
                        painterResource(topic.photo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Radii.sm)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.padding(top = Spacing.xxs, start = Spacing.xxs, end = Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        AppText(title, variant = TypeVariant.Title)
                        AppText(stringResource(topic.teaser), tone = Tone.Muted)
                        // The whole card opens the topic; this only says so.
                        Row(
                            Modifier.heightIn(min = TouchTarget),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(stringResource(R.string.learn_read_topic), variant = TypeVariant.Label, color = colors.link)
                            Icon(IconName.ArrowRight, size = 16.dp, color = colors.link)
                        }
                    }
                }
            }
        }

        val border = colors.border
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        color = border,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(Radii.md.toPx()),
                        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 3 * w))),
                    )
                }
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IconName.Info, size = 20.dp)
            AppText(stringResource(R.string.learn_draft_note), Modifier.weight(1f), variant = TypeVariant.Caption, tone = Tone.Muted)
        }
    }
}

/** RN: the learn/[topic] header title: the topic's own, or "Learn" for an unknown one. */
@Composable
fun learnTopicTitle(key: String): String = stringResource(Topic.fromKey(key)?.title ?: R.string.learn_title)

/**
 * RN: app/learn/[topic].tsx. One Learn topic. Short, scannable blocks for reading in the field;
 * no network needed.
 */
@Composable
fun LearnTopicScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: LearnTopicViewModel = viewModel(factory = LearnTopicViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val topic = s.topic
    if (topic == null) {
        ScreenContainer(scroll = false) {
            ErrorState(message = stringResource(R.string.error_default_title), onBack = onBack)
        }
        return
    }
    ScreenContainer(
        contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        spacing = Spacing.md,
    ) {
        when (topic) {
            Topic.Stages -> Stages(s)
            Topic.Photos -> Photos()
            Topic.How -> HowItWorks(onOpenAbout, onOpenSettings)
        }
    }
}

/** Stages come from the classes table (names, colors, text), mildest first. */
@Composable
private fun Stages(s: LearnTopicViewModel.State) {
    AppText(stringResource(R.string.learn_stages_intro))
    val classes = s.classes
    if (classes == null) {
        CircularProgressIndicator(Modifier.size(36.dp), color = YoloCTheme.colors.text)
        return
    }
    for (c in classes) {
        Card {
            StatusChip(StatusKey.fromToken(c.colorToken), label = c.displayName)
            AppText(c.summary)
            if (!c.symptoms.isNullOrEmpty()) {
                Column(Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    AppText(stringResource(R.string.learn_signs_title), variant = TypeVariant.BodyStrong)
                    AppText(c.symptoms)
                }
            }
            if (c.recommendedActions.isNotEmpty()) {
                Column(Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    AppText(stringResource(R.string.learn_actions_title), variant = TypeVariant.BodyStrong)
                    for (action in c.recommendedActions) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            AppText("•")
                            AppText(action, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Photos() {
    val colors = YoloCTheme.colors
    Card {
        for (tip in photoTips) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(colors.surfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(tip.icon, size = 20.dp)
                }
                AppText(stringResource(tip.text), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HowItWorks(onOpenAbout: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = YoloCTheme.colors
    val missed = recallOutOfTen(appContainer().modelConfig.metrics.earlyStageRecall)?.let { 10 - it }
    Column {
        howSteps.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // The rail: a disc per step, joined by a line to the next.
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp).background(colors.primary.bg, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(step.icon, size = 20.dp, color = colors.primary.text)
                    }
                    if (i < howSteps.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .weight(1f)
                                .heightIn(min = Spacing.md)
                                .background(colors.border, RoundedCornerShape(Radii.full)),
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).padding(top = Spacing.xs, bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    AppText(stringResource(step.title), variant = TypeVariant.BodyStrong)
                    AppText(stringResource(step.body))
                }
            }
        }
    }
    if (missed != null) {
        Card {
            AppText(stringResource(R.string.learn_how_trust_title), variant = TypeVariant.Title)
            AppText(stringResource(R.string.learn_how_trust_body, missed))
            YButton(stringResource(R.string.learn_see_accuracy), onOpenAbout, variant = ButtonVariant.Secondary, icon = IconName.BarChart2)
            YButton(stringResource(R.string.learn_threshold_link), onOpenSettings, variant = ButtonVariant.Ghost, icon = IconName.Sliders)
        }
    }
}
