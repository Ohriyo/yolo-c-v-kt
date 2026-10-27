package com.yoloc.app.features.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.YoloCTheme

/** Space kept clear around the frame; also how far the corner mask reaches past it. */
private val MARGIN = Spacing.md
private val GUIDE_SIZE = 24.dp
private val GUIDE_INSET = Spacing.sm
private val GUIDE_STROKE = 2.dp
private val GUIDE_RADIUS = 2.dp
private val FRAME_STROKE = 1.dp

/**
 * RN: features/camera/Viewfinder.tsx. The framed camera area: a 3:4 frame centered in the space
 * it is given, with rounded corners, a thin outline and four square reticle ticks.
 *
 * Android camera previews can be drawn on a separate surface that ignores rounded clipping, so
 * the corners are not clipped: a ring in the screen color is drawn over them instead (its inner
 * edge is the frame's rounded outline). This works whatever the preview's implementation.
 *
 * @param content The camera preview, or a panel shown in its place. Fills the frame.
 */
@Composable
fun Viewfinder(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val cam = YoloCTheme.colors.camera
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val frame = fitAspect((maxWidth - MARGIN * 2).value, (maxHeight - MARGIN * 2).value, VIEWFINDER_ASPECT)
        if (frame.width <= 0f) return@BoxWithConstraints
        val shape = RoundedCornerShape(Radii.md)
        Box(
            Modifier
                .size(frame.width.dp, frame.height.dp)
                .background(cam.lens, shape)
                .drawWithContent {
                    drawContent()
                    // The corner mask: the frame inflated by MARGIN, minus the frame's rounded outline.
                    val m = MARGIN.toPx()
                    val r = Radii.md.toPx()
                    val ring = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRoundRect(RoundRect(-m, -m, size.width + m, size.height + m, CornerRadius(r + m)))
                        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                    }
                    drawPath(ring, cam.screen)
                    drawCornerGuides(cam.guide, GUIDE_SIZE.toPx(), GUIDE_INSET.toPx(), GUIDE_STROKE.toPx(), GUIDE_RADIUS.toPx())
                }
                .border(FRAME_STROKE, cam.frameBorder, shape),
        ) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

/** Thin L-shaped reticle ticks just inside the frame's corners. Decorative. */
private fun DrawScope.drawCornerGuides(
    color: Color,
    guide: Float,
    inset: Float,
    stroke: Float,
    radius: Float,
) {
    // RN draws the ticks as borders, so the stroke sits inside the guide's box.
    val half = stroke / 2
    val corners = listOf(
        Offset(inset, inset) to Offset(1f, 1f),
        Offset(size.width - inset, inset) to Offset(-1f, 1f),
        Offset(inset, size.height - inset) to Offset(1f, -1f),
        Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
    )
    for ((corner, dir) in corners) {
        val x = corner.x + dir.x * half
        val y = corner.y + dir.y * half
        val path = Path().apply {
            moveTo(x, y + dir.y * (guide - half))
            lineTo(x, y + dir.y * radius)
            quadraticTo(x, y, x + dir.x * radius, y)
            lineTo(x + dir.x * (guide - half), y)
        }
        drawPath(path, color, style = Stroke(stroke))
    }
}
