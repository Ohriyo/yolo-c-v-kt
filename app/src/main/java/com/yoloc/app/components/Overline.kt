package com.yoloc.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * Redesign 2026-10-28 (the prototype): the small spaced line over a heading ("YOUR FIELD
 * RECORDS"). Drawn in capitals, spoken as written so screen readers do not spell it out.
 */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = YoloCTheme.colors.textFaint) {
    val locale = LocalConfiguration.current.locales[0]
    AppText(text.uppercase(locale), modifier.a11yLabel(text), variant = TypeVariant.Overline, color = color)
}
