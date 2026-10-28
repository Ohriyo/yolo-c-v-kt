package com.yoloc.app.features.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.YoloCTheme

/** The guide's share of the space it is given, and its shape (a pod is taller than wide). */
private const val GUIDE_WIDTH = 0.62f
private const val GUIDE_ASPECT = 0.62f

/**
 * RN: features/camera/Viewfinder.tsx drew a 3:4 frame with corner ticks. Redesign 2026-10-28 (the
 * prototype; docs/PARITY.md): the preview fills the screen and a dashed oval shows where one pod
 * goes. Decorative: the hint under it says the same in words.
 */
@Composable
fun OvalGuide(modifier: Modifier = Modifier) {
    val color = YoloCTheme.colors.camera.guide.copy(alpha = 0.85f)
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width * GUIDE_WIDTH
        val h = (w / GUIDE_ASPECT).coerceAtMost(size.height * 0.9f)
        val stroke = 1.5.dp.toPx()
        drawOval(
            color,
            topLeft = Offset((size.width - w) / 2, (size.height - h) / 2),
            size = Size(w, h),
            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
    }
}
