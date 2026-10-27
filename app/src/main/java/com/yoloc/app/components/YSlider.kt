package com.yoloc.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.YoloCTheme
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

private val THUMB = 24.dp
private val TRACK = 6.dp

/** RN: Slider's snap. The step nearest [v], rounded to 4 decimals so 0.05 steps stay exact. */
internal fun snapToStep(v: Double, min: Double, max: Double, step: Double): Double {
    val clamped = v.coerceIn(min, max)
    val snapped = min + Math.round((clamped - min) / step) * step
    return BigDecimal(snapped).setScale(4, RoundingMode.HALF_UP).toDouble()
}

/**
 * RN: components/Slider.tsx, on the Material 3 Slider (as the guide says) with RN's look: a 6dp
 * outlined track with the primary fill, a 24dp thumb that grows while dragged, and an optional
 * reference mark (the model's default). Drag or tap anywhere on the 48dp-tall track. Screen
 * readers adjust it one step at a time and hear [formatValue].
 *
 * @param onChange Called while dragging, once per step change.
 * @param onChangeEnd Called once when the finger lifts (or on a screen-reader adjustment): save here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YSlider(
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    onChangeEnd: (Double) -> Unit,
    formatValue: (Double) -> String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    onChange: ((Double) -> Unit)? = null,
    marker: Double? = null,
) {
    val colors = YoloCTheme.colors
    // While a finger is down the slider shows its own value; otherwise it follows `value`.
    var dragValue by remember { mutableStateOf<Double?>(null) }
    val shown = dragValue ?: value
    fun fraction(v: Double) = if (max == min) 0f else ((v - min) / (max - min)).toFloat()
    val steps = (((max - min) / step).roundToInt() - 1).coerceAtLeast(0)

    Slider(
        value = shown.toFloat(),
        onValueChange = { raw ->
            val next = snapToStep(raw.toDouble(), min, max, step)
            if (next != shown) {
                dragValue = next
                onChange?.invoke(next)
            }
        },
        onValueChangeFinished = {
            val end = dragValue ?: value
            dragValue = null
            onChangeEnd(end)
        },
        valueRange = min.toFloat()..max.toFloat(),
        steps = steps,
        modifier = modifier
            .fillMaxWidth()
            .height(TouchTarget)
            .semantics {
                contentDescription = accessibilityLabel
                stateDescription = formatValue(shown)
            },
        thumb = {
            Box(
                Modifier
                    .size(THUMB)
                    .scale(if (dragValue == null) 1f else 1.15f)
                    .background(colors.primary.bg, CircleShape)
                    .border(2.dp, colors.background, CircleShape),
            )
        },
        track = {
            val fill = colors.primary.bg
            val trackBg = colors.surface
            val outline = colors.outline
            val markColor = colors.text
            Canvas(Modifier.fillMaxWidth().height(TRACK + 10.dp)) {
                val h = TRACK.toPx()
                val top = (size.height - h) / 2
                val radius = CornerRadius(h / 2)
                drawRoundRect(trackBg, Offset(0f, top), Size(size.width, h), radius)
                drawRoundRect(fill, Offset(0f, top), Size(size.width * fraction(shown), h), radius)
                val line = 1.dp.toPx()
                drawRoundRect(outline, Offset(line / 2, top + line / 2), Size(size.width - line, h - line), radius, style = Stroke(line))
                if (marker != null) {
                    val w = 3.dp.toPx()
                    drawRoundRect(markColor, Offset(size.width * fraction(marker) - w / 2, 0f), Size(w, size.height), CornerRadius(w / 2))
                }
            }
        },
    )
}
