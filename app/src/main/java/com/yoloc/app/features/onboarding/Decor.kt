package com.yoloc.app.features.onboarding

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.YoloCTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * Redesign 2026-10-31: the onboarding panels' ground. A soft vertical gradient, one organic blob
 * behind the photo that morphs from panel to panel, faint leaves at the corners, and a dot grid in
 * one corner (PanelPrint). All decorative: none of it has semantics.
 */

/**
 * Each blob: eight radii around its centre (as a fraction of the base radius), joined by a smooth
 * closed curve, plus where its centre sits on the panel. Three shapes, taken in turn per step.
 */
private class BlobShape(val radii: FloatArray, val centerX: Float, val centerY: Float, val turn: Float)

private val blobs = listOf(
    BlobShape(floatArrayOf(1.00f, 0.86f, 1.08f, 0.92f, 1.04f, 0.80f, 0.98f, 0.90f), centerX = 0.86f, centerY = 0.16f, turn = 0f),
    BlobShape(floatArrayOf(0.90f, 1.06f, 0.84f, 1.02f, 0.88f, 1.10f, 0.86f, 0.98f), centerX = 0.10f, centerY = 0.22f, turn = 0.4f),
    BlobShape(floatArrayOf(1.06f, 0.92f, 0.98f, 0.82f, 1.10f, 0.94f, 0.84f, 1.04f), centerX = 0.70f, centerY = 0.30f, turn = 0.9f),
)

/** The blob's base radius, as a fraction of the panel width. */
private const val BLOB_RADIUS = 0.62f

private const val LEAF_SWAY_DEGREES = 2.5f

/** yoloc_leaf's height over width (design/logo/leaf_left.svg, 127 × 70). */
internal const val LEAF_ASPECT = 70f / 127f

/** A corner leaf: where it rests, how big, and its angle on the first panel. Each step turns it. */
private class CornerLeaf(val corner: Alignment, val width: Dp, val rest: DpOffset, val angle: Float, val perStep: Float, val soft: Boolean, val mirrored: Boolean)

private val cornerLeaves = listOf(
    CornerLeaf(Alignment.TopStart, 150.dp, DpOffset((-70).dp, 40.dp), angle = 34f, perStep = -9f, soft = false, mirrored = false),
    CornerLeaf(Alignment.BottomEnd, 170.dp, DpOffset(70.dp, (-90).dp), angle = 150f, perStep = 11f, soft = false, mirrored = false),
    CornerLeaf(Alignment.BottomStart, 120.dp, DpOffset((-56).dp, (-150).dp), angle = -26f, perStep = 7f, soft = true, mirrored = true),
)

/**
 * The ground behind every onboarding step. The blob morphs from the previous step's shape over
 * [Motion.ONBOARDING_MORPH] and the leaves sway; with reduce motion both stay still.
 *
 * @param showLeaves False once space runs out (OnboardingFrame's fit levels).
 */
@Composable
fun OnboardingBackground(step: Int, showLeaves: Boolean, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val ob = YoloCTheme.colors.onboarding
    val phase by animateFloatAsState(
        step.toFloat(),
        animationSpec = if (reduceMotion) snap() else tween(Motion.ONBOARDING_MORPH, easing = EaseInOut),
        label = "blob",
    )
    val sway = if (reduceMotion || !showLeaves) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "sway")
        val s by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(Motion.ONBOARDING_SWAY, easing = EaseInOut), RepeatMode.Reverse), label = "sway")
        s
    }
    Box(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
            .background(Brush.verticalGradient(listOf(ob.groundTop, ob.groundBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawPath(blobPath(phase, size), ob.blob) }
        if (showLeaves) {
            val colors = YoloCTheme.colors
            for (leaf in cornerLeaves) {
                DecorLeaf(
                    width = leaf.width,
                    tint = if (leaf.soft) colors.decorLeafSoft else colors.decorLeaf,
                    angle = leaf.angle + leaf.perStep * step + sway * LEAF_SWAY_DEGREES,
                    mirrored = leaf.mirrored,
                    modifier = Modifier.align(leaf.corner).offset(leaf.rest.x, leaf.rest.y),
                )
            }
        }
    }
}

/** One yoloc_leaf, tinted and turned. */
@Composable
internal fun DecorLeaf(width: Dp, tint: Color, angle: Float, modifier: Modifier = Modifier, mirrored: Boolean = false) {
    Image(
        painter = painterResource(R.drawable.yoloc_leaf),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
            .size(width, width * LEAF_ASPECT)
            .graphicsLayer {
                rotationZ = angle
                scaleX = if (mirrored) -1f else 1f
            },
    )
}

/** The blob at [phase]: between two steps' shapes, their radii, centres and turns blend. */
private fun blobPath(phase: Float, size: Size): Path {
    val from = floor(phase).toInt().coerceAtLeast(0)
    val t = phase - from
    val a = blobs[from % blobs.size]
    val b = blobs[(from + 1) % blobs.size]
    fun lerp(x: Float, y: Float) = x + (y - x) * t
    val cx = lerp(a.centerX, b.centerX) * size.width
    val cy = lerp(a.centerY, b.centerY) * size.height
    val turn = lerp(a.turn, b.turn)
    val base = BLOB_RADIUS * size.width
    val n = a.radii.size
    val points = List(n) { i ->
        val angle = turn + 2 * PI.toFloat() * i / n
        val r = base * lerp(a.radii[i], b.radii[i])
        Offset(cx + r * cos(angle), cy + r * sin(angle))
    }
    return smoothClosed(points)
}

/** A closed Catmull-Rom curve through [points], as cubic Béziers. */
private fun smoothClosed(points: List<Offset>): Path = Path().apply {
    val n = points.size
    moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        val c1 = p1 + (p2 - p0) / 6f
        val c2 = p2 - (p3 - p1) / 6f
        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    close()
}

/**
 * A faint grid of 2dp dots 16dp apart, fading out from its [Alignment] corner with a radial mask.
 * Placed by PanelPrint beside the photo, where no text ever sits.
 */
@Composable
internal fun BoxScope.DotGrid(corner: Alignment, size: Dp, modifier: Modifier = Modifier) {
    val dots = YoloCTheme.colors.onboarding.dots
    Canvas(
        modifier
            .align(corner)
            .size(size)
            // Offscreen, so the mask fades the dots and not what is behind them.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val step = 16.dp.toPx()
        val r = 1.dp.toPx()
        var y = r
        while (y < this.size.height) {
            var x = r
            while (x < this.size.width) {
                drawCircle(dots.copy(alpha = 1f), r, Offset(x, y))
                x += step
            }
            y += step
        }
        val origin = Offset(
            if (corner == Alignment.TopEnd || corner == Alignment.BottomEnd) this.size.width else 0f,
            if (corner == Alignment.BottomStart || corner == Alignment.BottomEnd) this.size.height else 0f,
        )
        drawRect(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = dots.alpha), Color.Transparent), center = origin, radius = this.size.minDimension),
            blendMode = BlendMode.DstIn,
        )
    }
}
