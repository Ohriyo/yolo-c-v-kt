package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: components/RecallPods.tsx. Ten cells, [found] of them marked found: recall made countable.
 * Found cells use the early-stage status fill with a check; missed cells are dashed outlines with
 * a dash, so it reads without color.
 *
 * @param found Pods out of 10 the model finds (early-stage recall × 10, rounded).
 */
@Composable
fun RecallPods(found: Int, accessibilityLabel: String, modifier: Modifier = Modifier) {
    val colors = YoloCTheme.colors
    val early = colors.early
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.xs).a11yLabel(accessibilityLabel),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(10) { i ->
            val hit = i < found
            val outline = colors.outline
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(max = 40.dp)
                    .aspectRatio(0.7f)
                    .then(
                        if (hit) {
                            Modifier.background(early.fill, RoundedCornerShape(Radii.sm))
                        } else {
                            Modifier.drawBehind {
                                val w = 1.5.dp.toPx()
                                drawRoundRect(
                                    color = outline,
                                    topLeft = Offset(w / 2, w / 2),
                                    size = Size(size.width - w, size.height - w),
                                    cornerRadius = CornerRadius(Radii.sm.toPx()),
                                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 3 * w))),
                                )
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (hit) IconName.Check else IconName.Minus, size = 14.dp, color = if (hit) early.onFill else colors.textMuted)
            }
        }
    }
}
