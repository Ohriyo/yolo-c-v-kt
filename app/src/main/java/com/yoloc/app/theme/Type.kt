package com.yoloc.app.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.yoloc.app.R

/**
 * Redesign 2026-10-28: Inter carries all text (res/font, OFL; licence in assets/licenses), as in
 * the prototype. Inter Display, the cut drawn for large sizes, carries headlines and big numbers.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val InterDisplay = FontFamily(Font(R.font.inter_display_semibold, FontWeight.SemiBold))

/** RN: typeScale variant names. */
enum class TypeVariant { Headline, Verdict, Title, BodyStrong, Body, Label, Caption, Overline, Data, Figure, Readout }

/** RN's line boxes centre the glyphs and keep the full line height on the first and last line. */
private val lineBox = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: TextUnit,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing,
    lineHeightStyle = lineBox,
    // RN fontVariant: ['tabular-nums'], so digits line up in columns of numbers.
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/**
 * RN: theme/tokens.ts typeScale. Sizes in sp, so text follows the system font size. Line heights
 * sit on the 4dp grid. Body is the minimum size for running text.
 */
object YoloCType {
    /** Screen title and the Home greeting. */
    val headline = style(InterDisplay, FontWeight.SemiBold, 28, 34, (-0.5).sp)

    /** The verdict line on Result and Scan Detail, and the Home hero line. */
    val verdict = style(InterDisplay, FontWeight.SemiBold, 24, 30, (-0.4).sp)

    /** Section and card title. */
    val title = style(Inter, FontWeight.SemiBold, 18, 24, (-0.2).sp)
    val bodyStrong = style(Inter, FontWeight.Medium, 16, 24, (-0.1).sp)
    val body = style(Inter, FontWeight.Normal, 16, 24, (-0.1).sp)

    /** Buttons, chips, tabs. */
    val label = style(Inter, FontWeight.SemiBold, 15, 20, 0.sp)
    val caption = style(Inter, FontWeight.Medium, 13, 18, 0.sp)

    /** RN's overline style, kept with the ported type scale. The redesign uses it nowhere. */
    val overline = style(Inter, FontWeight.SemiBold, 12, 16, 1.1.sp)

    /** A number in running rows: confidence %, counts, times. */
    val data = style(Inter, FontWeight.Medium, 17, 24, 0.sp, tabular = true)

    /** Readout strip value. */
    val figure = style(InterDisplay, FontWeight.SemiBold, 28, 32, (-0.5).sp, tabular = true)

    /** Large count (Home's stat cards). */
    val readout = style(InterDisplay, FontWeight.SemiBold, 34, 40, (-0.8).sp, tabular = true)

    fun of(variant: TypeVariant): TextStyle = when (variant) {
        TypeVariant.Headline -> headline
        TypeVariant.Verdict -> verdict
        TypeVariant.Title -> title
        TypeVariant.BodyStrong -> bodyStrong
        TypeVariant.Body -> body
        TypeVariant.Label -> label
        TypeVariant.Caption -> caption
        TypeVariant.Overline -> overline
        TypeVariant.Data -> data
        TypeVariant.Figure -> figure
        TypeVariant.Readout -> readout
    }
}
