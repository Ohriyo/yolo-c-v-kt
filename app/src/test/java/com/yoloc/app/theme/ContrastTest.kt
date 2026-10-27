package com.yoloc.app.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min
import kotlin.math.roundToInt

/** RN: __tests__/contrast.test.ts. Every pair the components use passes WCAG AA in both themes. */
class ContrastTest {
    private val pairs = contrastPairs(lightColors) + contrastPairs(darkColors)

    @Test
    fun coversBothThemes() {
        assertTrue(pairs.any { it.name.startsWith("light:") })
        assertTrue(pairs.any { it.name.startsWith("dark:") })
    }

    @Test
    fun everyPairPassesAa() {
        val failures = pairs.mapNotNull { p ->
            val ratio = contrastRatio(p.fg, p.bg)
            if (ratio < p.kind.minimum) {
                "${p.name}: ${hex(p.fg)} on ${hex(p.bg)} is ${"%.2f".format(ratio)}:1, needs ${p.kind.minimum}:1 (${p.kind})"
            } else {
                null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun computesKnownReferenceRatios() {
        assertEquals(21.0, contrastRatio(Palette.black, Palette.white), 1e-5)
        assertEquals(1.0, contrastRatio(Palette.white, Palette.white), 1e-5)
    }

    /** The brief's colors that must never carry text in these roles (why the derived inks exist). */
    @Test
    fun confirmsThePairsThePaletteAvoidsReallyFail() {
        assertTrue(contrastRatio(Palette.sage500, Palette.white) < ContrastKind.Text.minimum)
        assertTrue(contrastRatio(Palette.statusEarly, Palette.white) < ContrastKind.NonText.minimum)
        assertTrue(contrastRatio(Palette.statusInfected, Palette.dark950) < ContrastKind.Text.minimum)
        assertTrue(contrastRatio(Palette.forest700, Palette.dark950) < ContrastKind.NonText.minimum)
    }

    @Test
    fun usesAWhiteLightBackground() {
        assertEquals(Color(0xFFFFFFFF), lightColors.background)
    }

    /** CLAUDE.md 4.2: grounds stay neutral, not green-tinted. */
    @Test
    fun keepsTheGroundsNeutral() {
        for ((name, c) in listOf(
            "light surface" to lightColors.surface,
            "dark background" to darkColors.background,
            "dark surface" to darkColors.surface,
            "dark raised surface" to darkColors.surfaceRaised,
        )) {
            val (r, g, b) = listOf(c.red, c.green, c.blue).map { (it * 255).roundToInt() }
            assertTrue("$name is green-tinted", g - min(r, b) <= 2)
        }
    }

    @Test
    fun compositeMatchesRn() {
        // RN: composite('#191B1A', '#111312', 0.94)
        assertEquals("#191B1A", hex(composite(Palette.dark900, Palette.dark950, CAMERA_BAR_OPACITY)))
    }

    private fun hex(c: Color) = "#%02X%02X%02X".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())
}
