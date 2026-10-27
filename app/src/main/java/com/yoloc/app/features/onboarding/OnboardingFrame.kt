package com.yoloc.app.features.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
 * RN: features/onboarding/OnboardingFrame.tsx. One first-run step. The brand colors live only in
 * the illustration panel (CLAUDE.md 4.2), so all text sits on the theme background and keeps AA
 * contrast in light and dark. Progress is a row of capsules (the palette's pill motif) rather than
 * a "Step 1 of 4" label. Each step fades in; with "Remove animations" on it simply appears.
 *
 * @param step 0-based position in the whole first-run flow (slides plus the permission primer).
 * @param extra Content under the body (tips, notes).
 */
@Composable
fun OnboardingFrame(
    step: Int,
    total: Int,
    title: String,
    body: String,
    primary: FrameAction,
    illustration: @Composable () -> Unit,
    onSkip: (() -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = YoloCTheme.colors
    val reduceMotion = rememberReduceMotion()
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val counter = stringResource(R.string.onboarding_slide_counter, step + 1, total)
            Row(
                Modifier
                    // RN: accessibilityRole="progressbar" with min 1, max total, now step + 1.
                    .clearAndSetSemantics {
                        contentDescription = counter
                        progressBarRangeInfo = ProgressBarRangeInfo((step + 1).toFloat(), 1f..total.toFloat(), steps = total - 2)
                    }
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(total) { i ->
                    Box(
                        Modifier
                            .size(width = if (i == step) 28.dp else 8.dp, height = 8.dp)
                            .background(if (i <= step) colors.text else colors.border, RoundedCornerShape(Radii.full)),
                    )
                }
            }
            if (onSkip != null) {
                YButton(stringResource(R.string.onboarding_skip), onSkip, variant = ButtonVariant.Ghost)
            } else {
                Spacer(Modifier.height(TouchTarget))
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.lg),
        ) {
            key(step) {
                val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
                LaunchedEffect(Unit) { alpha.animateTo(1f, tween(Motion.SLOW)) }
                Column(Modifier.graphicsLayer { this.alpha = alpha.value }, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                    AppText(title, variant = TypeVariant.Headline)
                    AppText(body)
                    extra()
                }
            }
        }

        Box(Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.md)) {
            YButton(primary.label, primary.onClick, size = ButtonSize.Lg, fullWidth = true)
        }
    }
}
