package com.yoloc.app.screens.intro

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.WindowCompat
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Tone
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.util.rememberReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Redesign 2026-10-31: where the system splash left its icon, so the intro's mark starts on it.
 * MainActivity hands it the splash's exit callback ([takeOver]).
 */
class SplashHandoff {
    /** Centre of the splash icon in window pixels, and the size its 108dp canvas was drawn at. */
    var iconCenter by mutableStateOf<Offset?>(null)
        private set
    var iconCanvasPx by mutableStateOf(0f)
        private set

    /** True once the splash is gone: the intro may start moving. */
    var released by mutableStateOf(false)
        private set

    fun takeOver(provider: SplashScreenViewProvider) {
        try {
            val icon = provider.iconView
            val at = IntArray(2).also(icon::getLocationInWindow)
            iconCenter = Offset(at[0] + icon.width / 2f, at[1] + icon.height / 2f)
            iconCanvasPx = icon.width * SPLASH_CANVAS_PER_ICON_VIEW
        } catch (e: RuntimeException) {
            // No icon view (some launches show the splash without one): the intro starts centred.
            Log.w("intro", "Splash icon not available", e)
        }
        // Next frame: the mark moves onto the icon and the splash goes, drawn together.
        provider.view.postOnAnimation {
            provider.remove()
            released = true
        }
    }
}

/**
 * The splash draws the icon's whole 108dp canvas at 1.5× its icon view on Android 12+ (the view is
 * the 192dp visible circle of a 288dp canvas); the library's own splash before 12 draws it at the
 * view's size (288dp).
 */
private val SPLASH_CANVAS_PER_ICON_VIEW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 1.5f else 1f

/** The canvas the splash draws when there is no icon view to measure (library default). */
private val DEFAULT_SPLASH_CANVAS = 288.dp

/** The mark starts at the splash icon's size, which is this fraction of its final size. */
private const val MARK_START_SCALE = 0.88f

/** The artwork's lower edge below the canvas centre, as a fraction of the canvas (design/logo). */
private const val MARK_ART_BOTTOM = 0.1875f

/** The −8° dip of the mark's settle, as the spring's starting speed in degrees per second. */
private const val MARK_TILT_VELOCITY = -230f

/** If the splash never calls back (a launch without one), start anyway after this. */
private const val HANDOFF_TIMEOUT_MS = 500L

private const val SWAY_DEGREES = 2.5f

/** One decorative leaf: its corner, size, resting angle, how far it hangs off-screen, its tint. */
private data class Leaf(
    val corner: Alignment,
    val width: Dp,
    val angle: Float,
    val rest: DpOffset,
    val mirrored: Boolean,
    val soft: Boolean,
    /** Which way it came from (−1 or 1 per axis) and its sway direction. */
    val fromX: Int,
    val fromY: Int,
)

private val leaves = listOf(
    Leaf(Alignment.TopStart, 190.dp, 32f, DpOffset((-64).dp, (-12).dp), mirrored = false, soft = false, fromX = -1, fromY = -1),
    Leaf(Alignment.TopEnd, 150.dp, -28f, DpOffset(52.dp, 36.dp), mirrored = true, soft = true, fromX = 1, fromY = -1),
    Leaf(Alignment.BottomStart, 140.dp, -24f, DpOffset((-44).dp, (-56).dp), mirrored = false, soft = true, fromX = -1, fromY = 1),
    Leaf(Alignment.BottomEnd, 200.dp, 152f, DpOffset(64.dp, 20.dp), mirrored = false, soft = false, fromX = 1, fromY = 1),
)

/**
 * Redesign 2026-10-31 (inspired by a Plantum launch recording the owner shared; our own assets
 * only): on a cold start, the splash icon settles into place, leaves drift in from the corners, the
 * name and tagline rise, then [onDone] crossfades to the first screen. A tap skips it. With reduce
 * motion everything appears in place for a short hold.
 *
 * It sits on the splash's ground (window_background, the phone's light or dark), so the hand-off
 * from the splash is seamless.
 */
@Composable
fun IntroScreen(handoff: SplashHandoff, onDone: () -> Unit) {
    val appDark = YoloCTheme.colors.isDark
    YoloCTheme(preference = ThemeSetting.System, systemBars = false) {
        IntroBars(dark = YoloCTheme.colors.isDark, appDark = appDark)
        IntroContent(handoff, onDone)
    }
}

/**
 * The app's own Dark mode can differ from the phone's, and the splash follows the phone. While the
 * intro shows, the bar icons follow its ground; the app's are put back when it leaves.
 */
@Composable
private fun IntroBars(dark: Boolean, appDark: Boolean) {
    val view = LocalView.current
    // Launched, so it runs after the app theme's own bar effect (YoloCTheme) in the same frame.
    LaunchedEffect(view, dark) { view.lightBars(!dark) }
    val leaving by rememberUpdatedState(appDark)
    DisposableEffect(view) { onDispose { view.lightBars(!leaving) } }
}

private fun android.view.View.lightBars(light: Boolean) {
    val window = (context as? Activity)?.window ?: return
    WindowCompat.getInsetsController(window, this).apply {
        isAppearanceLightStatusBars = light
        isAppearanceLightNavigationBars = light
    }
}

@Composable
private fun IntroContent(handoff: SplashHandoff, onDone: () -> Unit) {
    val colors = YoloCTheme.colors
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    val scale = remember { Animatable(MARK_START_SCALE) }
    val tilt = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val leavesIn = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        withTimeoutOrNull(HANDOFF_TIMEOUT_MS) { snapshotFlow { handoff.released }.first { it } }
        if (reduceMotion) {
            listOf(scale, lift, leavesIn, name, tagline).forEach { it.snapTo(1f) }
            delay(Motion.INTRO_REDUCED_HOLD.toLong())
            finish()
            return@LaunchedEffect
        }
        coroutineScope {
            val settle = spring<Float>(dampingRatio = Motion.INTRO_SPRING_DAMPING, stiffness = Spring.StiffnessLow)
            launch { scale.animateTo(1f, settle) }
            // From the splash's upright icon: a dip to about −8°, then the spring brings it back to 0.
            launch { tilt.animateTo(0f, settle, initialVelocity = MARK_TILT_VELOCITY) }
            launch { lift.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)) }
            launch { leavesIn.animateTo(1f, tween(Motion.INTRO_LEAVES, easing = EaseOutCubic)) }
            launch {
                delay(Motion.INTRO_TEXT_DELAY.toLong())
                name.animateTo(1f, tween(Motion.INTRO_TEXT, easing = EaseOutCubic))
            }
            launch {
                delay((Motion.INTRO_TEXT_DELAY + Motion.INTRO_TEXT_STAGGER).toLong())
                tagline.animateTo(1f, tween(Motion.INTRO_TEXT, easing = EaseOutCubic))
            }
            // On the clock, not when the last spring settles.
            delay(Motion.INTRO_LENGTH.toLong())
            finish()
        }
    }

    val sway = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "sway")
        val s by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(Motion.INTRO_SWAY), RepeatMode.Reverse),
            label = "sway",
        )
        s
    }

    val skip = stringResource(R.string.intro_skip)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.window_background))
            // One node for TalkBack ("YOLO-C, Your field companion"); a tap anywhere skips.
            .clickable(interactionSource = null, indication = null, onClickLabel = skip, onClick = finish),
    ) {
        // Above the navigation bar, whose scrim would hide the lower leaves.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            for (leaf in leaves) {
                IntroLeaf(leaf, progress = leavesIn.value, sway = sway, tint = if (leaf.soft) colors.decorLeafSoft else colors.decorLeaf)
            }
        }

        val center = handoff.iconCenter ?: with(density) { Offset(maxWidth.toPx() / 2, maxHeight.toPx() / 2) }
        val startCanvasPx = handoff.iconCanvasPx.takeIf { it > 0f } ?: with(density) { DEFAULT_SPLASH_CANVAS.toPx() }
        val canvasPx = startCanvasPx / MARK_START_SCALE
        val liftPx = with(density) { Spacing.xxl.toPx() }
        val markCenterY = center.y - liftPx * lift.value
        Image(
            painter = painterResource(markDrawable(colors.isDark)),
            contentDescription = stringResource(R.string.intro_name),
            modifier = Modifier
                .offset { IntOffset((center.x - canvasPx / 2).roundToInt(), (markCenterY - canvasPx / 2).roundToInt()) }
                .size(with(density) { canvasPx.toDp() })
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = tilt.value
                },
        )

        // Under the mark's final place, so the text never moves with the spring.
        val textTopPx = center.y - liftPx + canvasPx * MARK_ART_BOTTOM + with(density) { Spacing.lg.toPx() }
        val risePx = with(density) { Spacing.sm.toPx() }
        Column(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, textTopPx.roundToInt()) }
                .padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            // The mark already reads "YOLO-C", so the name is not announced twice.
            Box(Modifier.clearAndSetSemantics {}.rise(name.value, risePx)) {
                AppText(stringResource(R.string.intro_name), variant = TypeVariant.Headline, textAlign = TextAlign.Center)
            }
            AppText(
                stringResource(R.string.topbar_tagline),
                modifier = Modifier.rise(tagline.value, risePx),
                tone = Tone.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The launcher foreground, or its dark-tile colors on a dark ground (as the dark splash). */
@DrawableRes
private fun markDrawable(dark: Boolean) = if (dark) R.drawable.ic_splash_dark else R.drawable.ic_launcher_foreground

/** Fades in while rising [risePx] into place. */
private fun Modifier.rise(progress: Float, risePx: Float) = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * risePx
}

@Composable
private fun BoxScope.IntroLeaf(leaf: Leaf, progress: Float, sway: Float, tint: Color) {
    val density = LocalDensity.current
    // From further out past its corner, turning into its resting angle as it arrives.
    val travelPx = with(density) { leaf.width.toPx() } * (1f - progress)
    Image(
        painter = painterResource(R.drawable.yoloc_leaf),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier
            .align(leaf.corner)
            .offset(leaf.rest.x, leaf.rest.y)
            .size(leaf.width, leaf.width * LEAF_ASPECT)
            .graphicsLayer {
                translationX = leaf.fromX * travelPx
                translationY = leaf.fromY * travelPx
                rotationZ = leaf.angle + leaf.fromX * (1f - progress) * 20f + leaf.fromY * sway * SWAY_DEGREES
                scaleX = if (leaf.mirrored) -1f else 1f
            },
    )
}

/** yoloc_leaf's height over width (design/logo/leaf_left.svg, 127 × 70). */
private const val LEAF_ASPECT = 70f / 127f
