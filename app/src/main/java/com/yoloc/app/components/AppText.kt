package com.yoloc.app.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.theme.YoloCType

enum class Tone { Default, Muted, Faint }

/** RN: maxFontSizeMultiplier={1.6}. Text grows with the system font size up to 1.6×, no further. */
private const val MAX_FONT_SCALE = 1.6f

/**
 * RN: components/AppText.tsx. Text in the Barlow type scale and theme colors. Use instead of raw
 * Text. Headline and title are announced as headings. [color] is for text on a known fill
 * (status chips, buttons).
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    variant: TypeVariant = TypeVariant.Body,
    tone: Tone = Tone.Default,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    textDecoration: TextDecoration? = null,
    /** Extra style on top of the variant (e.g. a heavier weight for the active tab). */
    style: TextStyle? = null,
) {
    val colors = YoloCTheme.colors
    val toneColor = when (tone) {
        Tone.Default -> colors.text
        Tone.Muted -> colors.textMuted
        Tone.Faint -> colors.textFaint
    }
    val base = YoloCType.of(variant).let { if (style != null) it.merge(style) else it }
    val heading = variant == TypeVariant.Headline || variant == TypeVariant.Title
    Text(
        text = text,
        modifier = if (heading) modifier.semantics { heading() } else modifier,
        color = if (color.isSpecified) color else toneColor,
        style = capFontScale(base),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = textAlign,
        textDecoration = textDecoration,
    )
}

@Composable
private fun capFontScale(style: TextStyle): TextStyle {
    val scale = LocalDensity.current.fontScale
    if (scale <= MAX_FONT_SCALE) return style
    val k = MAX_FONT_SCALE / scale
    fun TextUnit.capped() = if (isSpecified) (value * k).sp else this
    return style.copy(fontSize = style.fontSize.capped(), lineHeight = style.lineHeight.capped(), letterSpacing = style.letterSpacing.capped())
}
