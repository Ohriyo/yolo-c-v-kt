package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.yoloc.app.R
import com.yoloc.app.theme.BoxStroke
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

data class BoxDetection(
    val id: String,
    val status: StatusKey,
    val label: String,
    val confidence: Double,
    /** Normalized 0–1, top-left origin, relative to the original image (DB convention). */
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
)

/** Boxes this close to the top edge put their label inside, where the frame cannot cut it off. */
private const val LABEL_FLIP_THRESHOLD = 0.08

private fun clamp01(v: Double) = v.coerceIn(0.0, 1.0).toFloat()

/**
 * RN: components/ImageWithBoxes.tsx. The photo with a box per pod: 3dp stroke in the exact status
 * color inside a 1dp white halo, a "class + %" label chip, tap a box to select it (the selected
 * box is thicker, the others dimmed).
 *
 * @param model The photo (a File or Uri). Coil decodes it at view size, not at the camera's
 *   12+ MP (RN: resizeMethod="resize"); boxes are normalized, so the downsample never shifts them.
 * @param imageWidth Original upright image size, used for the aspect ratio so boxes line up exactly.
 */
@Composable
fun ImageWithBoxes(
    model: Any?,
    imageWidth: Int,
    imageHeight: Int,
    detections: List<BoxDetection>,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onSelect: ((String) -> Unit)? = null,
    showLabels: Boolean = true,
    accessibilityLabel: String? = null,
) {
    val colors = YoloCTheme.colors
    var failed by remember(model) { mutableStateOf(false) }
    val ratio = if (imageHeight > 0) imageWidth.toFloat() / imageHeight else 1f
    val frame = modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(Radii.md)).background(colors.surface)

    if (failed) {
        val title = stringResource(R.string.image_load_failed_title)
        val body = stringResource(R.string.image_load_failed_body)
        Column(
            frame.a11yLabel("$title. $body").padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(IconName.Image, size = 28.dp, color = colors.textMuted)
            AppText(title, variant = TypeVariant.BodyStrong)
            AppText(body, variant = TypeVariant.Caption, tone = Tone.Muted, textAlign = TextAlign.Center)
        }
        return
    }

    BoxWithConstraints(if (accessibilityLabel != null) frame.semantics { contentDescription = accessibilityLabel } else frame) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            onError = { failed = true },
        )
        for (d in detections) {
            val s = colors.status(d.status)
            val isSelected = d.id == selectedId
            val dimmed = selectedId != null && !isSelected
            val left = maxWidth * clamp01(d.x)
            val top = maxHeight * clamp01(d.y)
            val pct = formatConfidence(d.confidence)
            val spoken = "${d.label}, ${stringResource(R.string.common_confidence, pct)}"
            val tap = if (onSelect != null) {
                Modifier
                    .a11yAction(spoken, { onSelect(d.id) }, selected = isSelected)
                    .clickable(indication = null, interactionSource = null) { onSelect(d.id) }
            } else {
                Modifier.a11yLabel(spoken)
            }
            Box(
                Modifier
                    .zIndex(if (isSelected) 2f else 1f)
                    .offset(left, top)
                    .size(maxWidth * clamp01(d.w), maxHeight * clamp01(d.h))
                    .alpha(if (dimmed) 0.45f else 1f)
                    .then(tap),
            ) {
                // Halo, then the status stroke inside it.
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(BoxStroke.haloWidth, colors.boxHalo, RoundedCornerShape(Radii.sm + 1.dp))
                        .padding(BoxStroke.haloWidth)
                        .border(if (isSelected) BoxStroke.width + 2.dp else BoxStroke.width, s.stroke, RoundedCornerShape(Radii.sm)),
                )
                if (showLabels) {
                    val inside = d.y < LABEL_FLIP_THRESHOLD
                    val gap = 2.dp
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .layout { measurable, constraints ->
                                val p = measurable.measure(constraints)
                                layout(p.width, p.height) {
                                    if (inside) {
                                        val inset = (BoxStroke.width + gap).roundToPx()
                                        p.place(inset, inset)
                                    } else {
                                        p.place(-BoxStroke.haloWidth.roundToPx(), -p.height - gap.roundToPx())
                                    }
                                }
                            }
                            .widthIn(max = 220.dp)
                            .background(s.fill, RoundedCornerShape(Radii.sm))
                            .border(BoxStroke.haloWidth, colors.boxHalo, RoundedCornerShape(Radii.sm))
                            .padding(horizontal = Spacing.xs, vertical = 2.dp),
                    ) {
                        AppText("${d.label} $pct%", variant = TypeVariant.Caption, color = s.onFill, maxLines = 1)
                    }
                }
            }
        }
    }
}
