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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
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
fun YoloCTheme(preference: ThemeSetting = ThemeSetting.System, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemeSetting.System -> isSystemInDarkTheme()
        ThemeSetting.Light -> false
        ThemeSetting.Dark -> true
    }
    val colors = if (dark) darkColors else lightColors
    if (!LocalInspectionMode.current) SystemBars(colors)
    CompositionLocalProvider(LocalYoloCColors provides colors) {
        MaterialTheme(colorScheme = materialColors(colors), typography = materialType, content = content)
    }
}

/**
 * Bar icons follow the app theme, not the system theme (RN: StatusBar and NavigationBar styles),
 * and the window behind the app matches the background, so dark mode never flashes white during
 * transitions or behind the keyboard (RN: SystemUI.setBackgroundColorAsync). Camera screens
 * force light icons while they are shown.
 */
@Composable
private fun SystemBars(colors: YoloCColors) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.decorView.setBackgroundColor(colors.background.toArgb())
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !colors.isDark
            isAppearanceLightNavigationBars = !colors.isDark
        }
    }
}

private fun materialColors(c: YoloCColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primary.bg,
        onPrimary = c.primary.text,
        primaryContainer = c.selected,
        onPrimaryContainer = c.text,
        secondary = c.primary.bg,
        onSecondary = c.primary.text,
        secondaryContainer = c.selected,
        onSecondaryContainer = c.text,
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
        error = c.status(StatusKey.Infected).ink,
        onError = c.background,
    )
}

/** Material widgets that draw their own text use the app's faces too. */
private val materialType = Typography(
    displayLarge = YoloCType.readout,
    headlineMedium = YoloCType.headline,
    headlineSmall = YoloCType.verdict,
    titleLarge = YoloCType.title,
    titleMedium = YoloCType.bodyStrong,
    bodyLarge = YoloCType.body,
    bodyMedium = YoloCType.body,
    bodySmall = YoloCType.caption,
    labelLarge = YoloCType.label,
    labelMedium = YoloCType.caption,
    labelSmall = YoloCType.caption,
)
