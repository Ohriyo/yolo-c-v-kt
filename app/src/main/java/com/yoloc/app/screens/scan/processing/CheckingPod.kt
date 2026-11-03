package com.yoloc.app.screens.scan.processing

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Precision
import coil3.transform.Transformation
import coil3.size.Size as CoilSize
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Card
import com.yoloc.app.components.Tone
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.util.rememberReduceMotion
import kotlinx.coroutines.delay

/** A fast result never flashes the card: it shows only after this. */
private const val CARD_DELAY_MS = 150L

/** After this, the line says the first check of a launch is slower (usually the GPU's first run). */
private const val SLOW_HINT_MS = 3_000L

private val MARK_SIZE = 120.dp

/** Modifier.blur, Android 12+. */
private val PHOTO_BLUR = 32.dp

/** Before Android 12: the photo decoded this small and drawn back at full size, filtered. */
private const val FALLBACK_PHOTO_PX = 24

/** The pod in yoloc_mark, as fractions of the mark (design/logo/yoloc_mark.svg, path "pod"). */
private val POD = Rect(0.346f, 0.378f, 0.657f, 0.819f)

/** The leaves breathe from where they meet the pod. */
private val LEAVES_PIVOT = TransformOrigin(0.502f, 0.402f)

private const val BREATH = 0.03f

/**
 * Redesign 2026-10-31: what Processing shows while the photo is checked. The photo fills the
 * screen, blurred under a scrim (decorative: Result shows it again). After [CARD_DELAY_MS] a card
 * shows the mark with a scan line sweeping over the pod, "Checking your pod…", and a line saying
 * it runs on the phone, which becomes a "first check is slower" note after [SLOW_HINT_MS]. With
 * reduce motion the mark is still and a progress bar runs under the title.
 */
@Composable
fun CheckingPod(photo: PendingPhoto?) {
    val colors = YoloCTheme.colors
    val reduceMotion = rememberReduceMotion()
    var showCard by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(CARD_DELAY_MS)
        showCard = true
        delay(SLOW_HINT_MS - CARD_DELAY_MS)
        slow = true
    }

    Box(Modifier.fillMaxSize()) {
        if (photo != null) BlurredPhoto(photo, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(colors.photoScrim))
        AnimatedVisibility(
            showCard,
            Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.screen),
            enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(Motion.FAST)),
        ) {
            Card(Modifier.widthIn(max = 400.dp), padding = Spacing.lg, spacing = Spacing.sm) {
                ScanMark(animate = !reduceMotion, Modifier.size(MARK_SIZE).align(Alignment.CenterHorizontally))
                val announce = stringResource(R.string.processing_checking_a11y)
                AppText(
                    stringResource(R.string.processing_checking),
                    Modifier
                        .fillMaxWidth()
                        // Announced once: the line below changes, this never does.
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = announce
                        },
                    variant = TypeVariant.Verdict,
                    textAlign = TextAlign.Center,
                )
                if (reduceMotion) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
                        color = colors.primary.bg,
                        trackColor = colors.surface,
                    )
                }
                Crossfade(slow, animationSpec = tween(Motion.BASE), label = "line") { isSlow ->
                    AppText(
                        stringResource(if (isSlow) R.string.processing_first_run else R.string.processing_on_phone),
                        Modifier.fillMaxWidth(),
                        tone = Tone.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Modifier.blur needs Android 12. Before it, a copy about [FALLBACK_PHOTO_PX] wide drawn back at
 * full size with filtering reads as a blur ([SoftenSmallCopy] smooths the creases between the
 * stretched pixels).
 */
@Composable
private fun BlurredPhoto(photo: PendingPhoto, modifier: Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AsyncImage(
            model = photo.file,
            contentDescription = null,
            modifier = modifier.blur(PHOTO_BLUR, BlurredEdgeTreatment.Rectangle),
            contentScale = ContentScale.Crop,
        )
    } else {
        val context = LocalContext.current
        val request = remember(photo.file) {
            ImageRequest.Builder(context)
                .data(photo.file)
                .size(FALLBACK_PHOTO_PX, FALLBACK_PHOTO_PX)
                .precision(Precision.EXACT)
                .transformations(SoftenSmallCopy)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
        )
    }
}

/**
 * Box-blurs the small copy a few times (a few hundred pixels), so neighbouring pixels differ
 * little: stretched with bilinear filtering, they then blend instead of showing hard creases.
 */
private object SoftenSmallCopy : Transformation() {
    override val cacheKey = "processing.soften"

    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(3) {
            boxBlur(pixels, w, h, horizontal = true)
            boxBlur(pixels, w, h, horizontal = false)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Each pixel becomes the mean of itself and its two neighbours along one axis (edges clamped). */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val line = IntArray(length)
        for (l in 0 until lines) {
            fun at(i: Int) = if (horizontal) l * w + i else i * w + l
            for (i in 0 until length) line[i] = pixels[at(i)]
            for (i in 0 until length) {
                val a = line[(i - 1).coerceAtLeast(0)]
                val b = line[i]
                val c = line[(i + 1).coerceAtMost(length - 1)]
                fun mean(shift: Int) = (((a shr shift) and 0xFF) + ((b shr shift) and 0xFF) + ((c shr shift) and 0xFF)) / 3
                pixels[at(i)] = (mean(24) shl 24) or (mean(16) shl 16) or (mean(8) shl 8) or mean(0)
            }
        }
    }
}

/**
 * yoloc_mark, with a thin line sweeping down the pod and back ([Motion.SCAN_SWEEP] each way, ease
 * in-out), clipped to the pod, and the leaves breathing (1 → 1.03) on the same cycle. Decorative.
 */
@Composable
private fun ScanMark(animate: Boolean, modifier: Modifier) {
    val colors = YoloCTheme.colors.scanMark
    val mark = ImageVector.vectorResource(R.drawable.yoloc_mark)
    val pod = remember(mark) { mark.only { it != "leaves" } }
    val leaves = remember(mark) { mark.only { it == "leaves" } }
    val sweep: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(Motion.SCAN_SWEEP, easing = EaseInOut), RepeatMode.Reverse),
            label = "sweep",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(modifier) {
        Image(
            rememberVectorPainter(leaves),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = 1f + BREATH * sweep.value
                scaleX = s
                scaleY = s
                transformOrigin = LEAVES_PIVOT
            },
            colorFilter = ColorFilter.tint(colors.leaves),
        )
        Image(
            rememberVectorPainter(pod),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().then(if (animate) Modifier.scanLine(sweep, colors.line, colors.glow) else Modifier),
            colorFilter = ColorFilter.tint(colors.pod),
        )
    }
}

private fun Modifier.scanLine(sweep: State<Float>, line: Color, glow: Color) = drawWithContent {
    drawContent()
    val pod = Rect(POD.left * size.width, POD.top * size.height, POD.right * size.width, POD.bottom * size.height)
    val glowHalf = 8.dp.toPx()
    clipPath(Path().apply { addOval(pod) }) {
        val y = pod.top + pod.height * sweep.value
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, glow, Color.Transparent), startY = y - glowHalf, endY = y + glowHalf),
            topLeft = Offset(pod.left, y - glowHalf),
            size = Size(pod.width, glowHalf * 2),
        )
        drawLine(line, Offset(pod.left, y), Offset(pod.right, y), strokeWidth = 2.dp.toPx())
    }
}

/** The mark with only the paths whose names pass [keep] (yoloc_mark names "pod", "ridges", "leaves"). */
internal fun ImageVector.only(keep: (String) -> Boolean): ImageVector {
    val builder = ImageVector.Builder(name, defaultWidth, defaultHeight, viewportWidth, viewportHeight)
    fun add(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorGroup -> {
                    builder.addGroup(
                        node.name, node.rotation, node.pivotX, node.pivotY, node.scaleX, node.scaleY,
                        node.translationX, node.translationY, node.clipPathData,
                    )
                    add(node)
                    builder.clearGroup()
                }
                is VectorPath -> if (keep(node.name)) {
                    builder.addPath(
                        node.pathData, node.pathFillType, node.name, node.fill, node.fillAlpha, node.stroke,
                        node.strokeAlpha, node.strokeLineWidth, node.strokeLineCap, node.strokeLineJoin,
                        node.strokeLineMiter, node.trimPathStart, node.trimPathEnd, node.trimPathOffset,
                    )
                }
            }
        }
    }
    add(root)
    return builder.build()
}
