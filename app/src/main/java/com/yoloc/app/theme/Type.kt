package com.yoloc.app.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.yoloc.app.R

/** Barlow carries text (res/font, OFL; licence in assets/licenses). */
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

/** Barlow Semi Condensed carries screen titles, the verdict and every readout number. */
val BarlowSemiCondensed = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
)

/** RN: typeScale variant names. */
enum class TypeVariant { Headline, Verdict, Title, BodyStrong, Body, Label, Caption, Data, Figure, Readout }

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
    /** Screen title. */
    val headline = style(BarlowSemiCondensed, FontWeight.SemiBold, 28, 32, (-0.2).sp)

    /** The verdict line on Result and Scan Detail. */
    val verdict = style(BarlowSemiCondensed, FontWeight.SemiBold, 22, 28, (-0.1).sp)

    /** Section title. */
    val title = style(Barlow, FontWeight.SemiBold, 18, 24, 0.sp)
    val bodyStrong = style(Barlow, FontWeight.Medium, 16, 24, 0.sp)
    val body = style(Barlow, FontWeight.Normal, 16, 24, 0.sp)

    /** Buttons, chips, tabs. */
    val label = style(Barlow, FontWeight.SemiBold, 16, 20, 0.1.sp)
    val caption = style(Barlow, FontWeight.Medium, 13, 16, 0.2.sp)

    /** A number in running rows: confidence %, counts, times. */
    val data = style(BarlowSemiCondensed, FontWeight.Medium, 18, 24, 0.sp, tabular = true)

    /** Readout strip value. */
    val figure = style(BarlowSemiCondensed, FontWeight.SemiBold, 28, 32, (-0.2).sp, tabular = true)

    /** Large count (Home's 30-day totals). */
    val readout = style(BarlowSemiCondensed, FontWeight.SemiBold, 44, 48, (-0.5).sp, tabular = true)

    fun of(variant: TypeVariant): TextStyle = when (variant) {
        TypeVariant.Headline -> headline
        TypeVariant.Verdict -> verdict
        TypeVariant.Title -> title
        TypeVariant.BodyStrong -> bodyStrong
        TypeVariant.Body -> body
        TypeVariant.Label -> label
        TypeVariant.Caption -> caption
        TypeVariant.Data -> data
        TypeVariant.Figure -> figure
        TypeVariant.Readout -> readout
    }
}
