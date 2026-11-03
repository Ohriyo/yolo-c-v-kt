package com.yoloc.app.features.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import com.yoloc.app.components.ChipSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.InfoChip
import com.yoloc.app.components.StatusChip
import com.yoloc.app.components.cardShadow
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.Pebble
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.YoloCTheme
import kotlinx.coroutines.delay

/*
 * Redesign 2026-10-31: the parts of a rich onboarding panel. The photo as a framed "pebble" print
 * with neutral chips over its lower edge, the feature rows that fill the middle, the class legend
 * and the segmented progress bar. Each part makes its own entrance; with reduce motion it simply
 * appears.
 */

/** A neutral fact on a chip over the photo's lower edge. Never a result class. */
data class PanelChip(val icon: IconName, val label: String)

/** One short line with its icon, under a panel's body text. */
data class PanelFeature(val icon: IconName, val text: String)

/** How far the chips overlap the photo's lower edge: half a Md chip. */
private val CHIP_OVERLAP = 16.dp

/** The photo rises this far as it fades in. */
private val PRINT_RISE = 16.dp

/**
 * 0 → 1 after [delayMillis], or 1 at once with reduce motion. Read it in a graphicsLayer so only
 * the layer redraws.
 */
@Composable
internal fun rememberEntrance(
    reduceMotion: Boolean,
    delayMillis: Int = 0,
    spec: AnimationSpec<Float> = tween(Motion.SLOW, easing = EaseOutCubic),
): Animatable<Float, *> {
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        delay(delayMillis.toLong())
        progress.animateTo(1f, spec)
    }
    return progress
}

/**
 * The panel's picture as a framed print: the pebble mask, a 4dp border and the card shadow, with a
 * dot grid and a small leaf peeking from behind it, and [chips] overlapping its lower edge. The
 * photo rises [PRINT_RISE] as it fades in; the chips pop in one by one.
 *
 * @param height The print's height; OnboardingFrame shrinks it when space runs out.
 * @param showDots False once space runs out (the first thing dropped).
 * @param overlay Drawn over the picture, inside the mask (the scan panel's brackets).
 */
@Composable
fun PanelPrint(
    height: Dp,
    showDots: Boolean,
    showLeaf: Boolean,
    reduceMotion: Boolean,
    chips: List<PanelChip> = emptyList(),
    overlay: @Composable BoxScope.() -> Unit = {},
    picture: @Composable () -> Unit,
) {
    val colors = YoloCTheme.colors
    val shape = RoundedCornerShape(topStart = Pebble.corner, topEnd = Pebble.corner, bottomEnd = Pebble.tail, bottomStart = Pebble.corner)
    val rise = rememberEntrance(reduceMotion)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = rise.value
                    translationY = (1f - rise.value) * PRINT_RISE.toPx()
                },
        ) {
            // Behind the print, reaching into the screen margin and the gap above it: never under text.
            if (showDots) DotGrid(Alignment.TopStart, 112.dp, Modifier.offset((-Spacing.screen), (-Spacing.md)))
            if (showLeaf) {
                DecorLeaf(
                    width = 72.dp,
                    tint = colors.decorLeaf,
                    angle = -38f,
                    modifier = Modifier.align(Alignment.TopEnd).offset(Spacing.md, (-Spacing.xs)),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .cardShadow(shape)
                    .clip(shape)
                    .background(colors.surface)
                    // Decorative: the text carries the meaning.
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                picture()
                overlay()
                Box(Modifier.fillMaxSize().border(Pebble.border, colors.onboarding.printBorder, shape))
            }
        }
        if (chips.isNotEmpty()) {
            FlowRow(
                Modifier
                    .padding(start = Spacing.sm)
                    .overlapAbove(CHIP_OVERLAP),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                chips.forEachIndexed { i, chip ->
                    val pop = rememberEntrance(
                        reduceMotion,
                        delayMillis = Motion.ONBOARDING_CHIP_DELAY + i * Motion.ONBOARDING_CHIP_STAGGER,
                        spec = spring(dampingRatio = Motion.ONBOARDING_CHIP_DAMPING, stiffness = Spring.StiffnessMediumLow),
                    )
                    InfoChip(
                        chip.icon,
                        chip.label,
                        Modifier
                            .graphicsLayer {
                                val p = pop.value
                                alpha = p.coerceIn(0f, 1f)
                                scaleX = 0.8f + 0.2f * p
                                scaleY = 0.8f + 0.2f * p
                            }
                            .cardShadow(RoundedCornerShape(Radii.sm)),
                    )
                }
            }
        }
    }
}

/** Placed [overlap] higher than its slot, and that much shorter, so it sits over what is above. */
private fun Modifier.overlapAbove(overlap: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val o = overlap.roundToPx()
    layout(placeable.width, (placeable.height - o).coerceAtLeast(0)) { placeable.place(0, -o) }
}

/**
 * The scan panel's hint of scanning, over the photo: white viewfinder brackets and a Sage-glow
 * line sweeping across, one pass per [Motion.ONBOARDING_SCAN_PASS]. It claims no result. With
 * reduce motion only the brackets show.
 */
@Composable
fun BoxScope.ScanOverlay(reduceMotion: Boolean) {
    val ob = YoloCTheme.colors.onboarding
    val sweep = if (reduceMotion) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "scan")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(Motion.ONBOARDING_SCAN_PASS, easing = EaseInOut), RepeatMode.Reverse), label = "scan")
    }
    Canvas(Modifier.matchParentSize().padding(28.dp)) {
        val stroke = 3.dp.toPx()
        val arm = 28.dp.toPx()
        val w = size.width
        val h = size.height
        fun bracket(corner: Offset, dx: Float, dy: Float) {
            drawLine(ob.bracket, corner, corner + Offset(dx * arm, 0f), stroke, StrokeCap.Round)
            drawLine(ob.bracket, corner, corner + Offset(0f, dy * arm), stroke, StrokeCap.Round)
        }
        bracket(Offset(0f, 0f), 1f, 1f)
        bracket(Offset(w, 0f), -1f, 1f)
        bracket(Offset(0f, h), 1f, -1f)
        bracket(Offset(w, h), -1f, -1f)
        if (sweep != null) {
            val y = sweep.value * h
            val glow = 14.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, ob.scanGlow, Color.Transparent), startY = y - glow, endY = y + glow),
                topLeft = Offset(0f, y - glow),
                size = Size(w, glow * 2),
            )
            drawLine(ob.scanLine, Offset(stroke, y), Offset(w - stroke, y), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/**
 * Short feature lines: a 24dp icon in a 40dp leaf-tint disc and one line each, read as text. They
 * fade in [Motion.ONBOARDING_ROW_STAGGER] apart.
 */
@Composable
fun FeatureRows(features: List<PanelFeature>, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val colors = YoloCTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        features.forEachIndexed { i, feature ->
            val fade = rememberEntrance(reduceMotion, delayMillis = Motion.ONBOARDING_ROW_DELAY + i * Motion.ONBOARDING_ROW_STAGGER, spec = tween(Motion.BASE))
            Row(
                Modifier
                    .graphicsLayer { alpha = fade.value }
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(colors.selected, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(feature.icon, size = 24.dp, color = colors.icon)
                }
                AppText(feature.text, Modifier.weight(1f))
            }
        }
    }
}

/**
 * The three classes as the app draws them in scan rows (small status chips), as a legend under the
 * panel's text (never on a photo), with the line saying every pod gets one of them. Always one
 * row ([LegendRow]): a long label (Filipino, large text) wraps inside its chip rather than pushing
 * a chip onto a second row.
 */
@Composable
fun ClassLegend(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AppText(stringResource(R.string.onboarding_legend_line))
        LegendRow(Spacing.xs) {
            StatusKey.entries.forEachIndexed { i, status ->
                val pop = rememberEntrance(
                    reduceMotion,
                    delayMillis = Motion.ONBOARDING_CHIP_DELAY + i * Motion.ONBOARDING_CHIP_STAGGER,
                    spec = spring(dampingRatio = Motion.ONBOARDING_CHIP_DAMPING, stiffness = Spring.StiffnessMediumLow),
                )
                StatusChip(
                    status,
                    Modifier.graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.8f + 0.2f * p
                        scaleY = 0.8f + 0.2f * p
                    },
                    size = ChipSize.Sm,
                    maxLines = Int.MAX_VALUE,
                )
            }
        }
    }
}

/**
 * Its children in one row, [gap] apart, all as tall as the tallest. Each keeps its natural width
 * when they fit; when not, the widest give way first (their labels wrap between words) and the
 * narrow ones keep theirs. No chip gets narrower than its longest word, so a word is never split:
 * only if even that does not fit (long words at very large text) do they wrap onto more rows.
 */
@Composable
private fun LegendRow(gap: Dp, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val maxWidth = constraints.maxWidth
        val available = (maxWidth - gapPx * (measurables.size - 1)).coerceAtLeast(0)
        val least = measurables.map { it.minIntrinsicWidth(Constraints.Infinity) }
        val wanted = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        if (least.sum() > available) {
            // Last resort: natural widths, wrapping onto more rows.
            val placeables = measurables.map { it.measure(Constraints(maxWidth = maxWidth)) }
            val rows = mutableListOf(mutableListOf<Placeable>())
            var x = 0
            for (p in placeables) {
                if (x > 0 && x + p.width > maxWidth) {
                    rows += mutableListOf<Placeable>()
                    x = 0
                }
                rows.last() += p
                x += p.width + gapPx
            }
            val height = rows.sumOf { row -> row.maxOf { it.height } } + gapPx * (rows.size - 1)
            return@Layout layout(maxWidth, height) {
                var y = 0
                for (row in rows) {
                    var rx = 0
                    for (p in row) {
                        p.place(rx, y)
                        rx += p.width + gapPx
                    }
                    y += row.maxOf { it.height } + gapPx
                }
            }
        }
        // Everyone gets their longest word; the rest goes to who still wants it, narrowest first.
        val extra = fairShare(wanted.indices.map { wanted[it] - least[it] }, available - least.sum())
        val widths = least.indices.map { least[it] + extra[it] }
        val height = measurables.indices.maxOf { measurables[it].minIntrinsicHeight(widths[it]) }
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], height)) }
        layout(maxWidth, height) {
            var x = 0
            for (p in placeables) {
                p.place(x, 0)
                x += p.width + gapPx
            }
        }
    }
}

/** Splits [available] so no one gets more than it wants, the smallest want served first. */
private fun fairShare(wanted: List<Int>, available: Int): List<Int> {
    val shares = IntArray(wanted.size)
    var left = available
    wanted.indices.sortedBy { wanted[it] }.forEachIndexed { served, i ->
        shares[i] = minOf(wanted[i], left / (wanted.size - served))
        left -= shares[i]
    }
    return shares.toList()
}

/**
 * The first-run progress as pill segments: the current step wider, in Forest, the others Mist. The
 * width moves between steps over [Motion.SLOW]. Shape carries it as well as colour.
 */
@Composable
fun SegmentedProgress(step: Int, total: Int, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val ob = YoloCTheme.colors.onboarding
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val width by animateDpAsState(
                if (i == step) 28.dp else 12.dp,
                animationSpec = if (reduceMotion) snap() else tween(Motion.SLOW, easing = EaseInOut),
                label = "segment",
            )
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .background(if (i == step) ob.progressActive else ob.progressIdle, RoundedCornerShape(Radii.full)),
            )
        }
    }
}
