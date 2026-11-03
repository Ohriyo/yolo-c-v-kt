package com.yoloc.app.features.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.YButton
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.util.rememberReduceMotion

/** A step's main button. */
data class FrameAction(val label: String, val onClick: () -> Unit)

/**
 * Redesign 2026-10-31: what goes when a step does not fit, in this order. The dot grid and the
 * corner leaves free no space (they sit behind), so an overflow drops them together with the third
 * feature row; if it still does not fit, the print shrinks. Text and the button are never cut: the
 * middle scrolls.
 */
private object Fit {
    const val ALL = 0
    const val NO_DOTS = 1
    const val NO_LEAVES = 2
    const val TWO_ROWS = 3
    const val SMALL_PRINT = 4
}

private val PRINT_HEIGHT = 200.dp
private val PRINT_HEIGHT_SMALL = 152.dp

/**
 * RN: features/onboarding/OnboardingFrame.tsx. One first-run step. All text sits on the onboarding
 * ground (a soft gradient, checked for AA at both ends) over a blob, faint leaves and a dot grid
 * that are pure decoration. Progress is a segmented bar, the current step wider, rather than a
 * "Step 1 of 4" label. Each step fades in; with "Remove animations" on it simply appears.
 *
 * Redesign 2026-10-31: the welcome slides pass [pebble] for the framed print, [chips] over its
 * lower edge, an [overlay] on the picture and [features] under the text. The permission primer and
 * the name step keep their plain panel and share only the ground.
 *
 * @param step 0-based position in the whole first-run flow (slides plus the permission primer).
 * @param extra Content under the body (tips, the legend, notes), before [features].
 */
@Composable
fun OnboardingFrame(
    step: Int,
    total: Int,
    title: String,
    body: String,
    primary: FrameAction,
    illustration: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
    pebble: Boolean = false,
    chips: List<PanelChip> = emptyList(),
    overlay: @Composable BoxScope.() -> Unit = {},
    features: List<PanelFeature> = emptyList(),
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = YoloCTheme.colors
    val reduceMotion = rememberReduceMotion()

    // Per step: the middle's height against what its content needs.
    var fit by remember(step) { mutableIntStateOf(Fit.ALL) }
    var viewportPx by remember { mutableIntStateOf(0) }
    var contentPx by remember(step) { mutableIntStateOf(0) }
    LaunchedEffect(step) {
        snapshotFlow { viewportPx to contentPx }.collect {
            while (viewportPx > 0 && contentPx > viewportPx && fit < Fit.SMALL_PRINT) {
                fit = if (fit < Fit.TWO_ROWS) Fit.TWO_ROWS else fit + 1
                // Let the step lay out again before measuring what it needs now.
                withFrameNanos {}
                withFrameNanos {}
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        OnboardingBackground(step, showLeaves = fit < Fit.NO_LEAVES, reduceMotion = reduceMotion)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val counter = stringResource(R.string.onboarding_slide_counter, step + 1, total)
                SegmentedProgress(
                    step,
                    total,
                    reduceMotion,
                    Modifier
                        // RN: accessibilityRole="progressbar" with min 1, max total, now step + 1.
                        .clearAndSetSemantics {
                            contentDescription = counter
                            progressBarRangeInfo = ProgressBarRangeInfo((step + 1).toFloat(), 1f..total.toFloat(), steps = total - 2)
                        }
                        .padding(vertical = 21.dp),
                )
                if (onSkip != null) {
                    YButton(stringResource(R.string.onboarding_skip), onSkip, variant = ButtonVariant.Ghost)
                } else {
                    Spacer(Modifier.height(TouchTarget))
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .onSizeChanged { viewportPx = it.height }
                    .verticalScroll(rememberScrollState())
                    .onSizeChanged { contentPx = it.height }
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.md),
            ) {
                key(step) {
                    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
                    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(Motion.SLOW)) }
                    if (pebble) {
                        // Its own entrance: it rises as it fades in.
                        PanelPrint(
                            height = if (fit >= Fit.SMALL_PRINT) PRINT_HEIGHT_SMALL else PRINT_HEIGHT,
                            showDots = fit < Fit.NO_DOTS,
                            showLeaf = fit < Fit.NO_LEAVES,
                            reduceMotion = reduceMotion,
                            chips = chips,
                            overlay = overlay,
                            picture = illustration,
                        )
                        Spacer(Modifier.height(Spacing.md))
                    }
                    Column(Modifier.graphicsLayer { this.alpha = alpha.value }, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (!pebble) {
                            // Decorative: the text carries the meaning.
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Spacing.sm)
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(Radii.md))
                                    .background(colors.surface)
                                    .clearAndSetSemantics {},
                                contentAlignment = Alignment.Center,
                            ) { illustration() }
                        }
                        AppText(title, variant = TypeVariant.Headline)
                        AppText(body)
                        extra()
                        if (features.isNotEmpty()) {
                            FeatureRows(features.take(if (fit >= Fit.TWO_ROWS) 2 else 3), reduceMotion, Modifier.padding(top = Spacing.xs))
                        }
                    }
                }
            }

            Box(Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.md)) {
                YButton(primary.label, primary.onClick, size = ButtonSize.Lg, fullWidth = true)
            }
        }
    }
}
