package com.yoloc.app.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.yoloc.app.db.repositories.ThemeSetting

/** RN: useTheme(). Read with `YoloCTheme.colors`. */
val LocalYoloCColors = staticCompositionLocalOf { lightColors }

object YoloCTheme {
    val colors: YoloCColors
        @Composable get() = LocalYoloCColors.current
}

/**
 * RN: ThemeProvider. "System" (the default until settings load) follows the phone. Material 3
 * widgets (Slider, Switch, ModalBottomSheet, AlertDialog) get a ColorScheme built from the same
 * tokens, so they match the app's own components.
 */
@Composable
fun YoloCTheme(
    preference: ThemeSetting = ThemeSetting.System,
    /** False for a theme nested inside the app's (the dev gallery): the app's theme owns the bars. */
    systemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemeSetting.System -> isSystemInDarkTheme()
        ThemeSetting.Light -> false
        ThemeSetting.Dark -> true
    }
    val colors = if (dark) darkColors else lightColors
    if (systemBars && !LocalInspectionMode.current) SystemBars(colors)
    CompositionLocalProvider(LocalYoloCColors provides colors) {
        MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, typography = materialType, content = content)
    }
}

/**
 * Bar icons follow the app theme, not the system theme (RN: StatusBar and NavigationBar styles),
 * and the window behind the app matches the background, so dark mode never flashes white during
 * transitions or behind the keyboard (RN: SystemUI.setBackgroundColorAsync). The window theme
 * (res/values, values-night) already paints the system theme's background before the first frame.
 * Runs only when light/dark changes, so a camera screen's light icons survive other recompositions.
 */
@Composable
private fun SystemBars(colors: YoloCColors) {
    val view = LocalView.current
    LaunchedEffect(view, colors.isDark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        window.decorView.setBackgroundColor(colors.background.toArgb())
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !colors.isDark
            isAppearanceLightNavigationBars = !colors.isDark
        }
    }
}

/** Every role is set from the app's tokens, so no Material default (purple) can show through. */
private fun materialColors(c: YoloCColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    val error = c.status(StatusKey.Infected).ink
    return base.copy(
        primary = c.primary.bg,
        onPrimary = c.primary.text,
        primaryContainer = c.selected,
        onPrimaryContainer = c.text,
        secondary = c.primary.bg,
        onSecondary = c.primary.text,
        secondaryContainer = c.selected,
        onSecondaryContainer = c.text,
        tertiary = c.primary.bg,
        onTertiary = c.primary.text,
        tertiaryContainer = c.selected,
        onTertiaryContainer = c.text,
        inversePrimary = c.inverse.text,
        // No tonal tint: depth comes from hairlines and the floating shadow (CLAUDE.md 4.4).
        surfaceTint = Color.Transparent,
        surfaceBright = c.surfaceRaised,
        surfaceDim = c.surface,
        background = c.background,
        onBackground = c.text,
        surface = c.surfaceRaised,
        onSurface = c.text,
        surfaceVariant = c.surface,
        onSurfaceVariant = c.textMuted,
        surfaceContainerLowest = c.surfaceRaised,
        surfaceContainerLow = c.surfaceRaised,
        surfaceContainer = c.surfaceRaised,
        surfaceContainerHigh = c.surfaceRaised,
        surfaceContainerHighest = c.surface,
        inverseSurface = c.inverse.bg,
        inverseOnSurface = c.inverse.text,
        outline = c.outline,
        outlineVariant = c.border,
        scrim = c.scrim,
        error = error,
        onError = c.background,
        errorContainer = c.surface,
        onErrorContainer = c.text,
    )
}

private val lightScheme = materialColors(lightColors)
private val darkScheme = materialColors(darkColors)

/** Material widgets that draw their own text use the app's faces too. */
private val materialType = Typography(
    displayLarge = YoloCType.readout,
    displayMedium = YoloCType.readout,
    displaySmall = YoloCType.figure,
    headlineLarge = YoloCType.headline,
    headlineMedium = YoloCType.headline,
    headlineSmall = YoloCType.verdict,
    titleLarge = YoloCType.title,
    titleMedium = YoloCType.bodyStrong,
    titleSmall = YoloCType.label,
    bodyLarge = YoloCType.body,
    bodyMedium = YoloCType.body,
    bodySmall = YoloCType.caption,
    labelLarge = YoloCType.label,
    labelMedium = YoloCType.caption,
    labelSmall = YoloCType.caption,
)
