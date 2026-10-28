package com.yoloc.app.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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

    /**
     * Redesign 2026-10-28 (docs/PARITY.md): the prototype's leaf-tinted ground with white cards
     * replaces RN's white ground and neutral surfaces (CLAUDE.md 4.2).
     */
    @Test
    fun usesThePrototypeGroundWithWhiteCards() {
        assertEquals(Color(0xFFF6F8F5), lightColors.background)
        assertEquals(Color(0xFFFFFFFF), lightColors.card)
    }

    /** Cards must read as a separate layer on the ground, in both themes. */
    @Test
    fun keepsCardsDistinctFromTheGround() {
        for (c in listOf(lightColors, darkColors)) {
            val (r1, g1, b1) = listOf(c.card.red, c.card.green, c.card.blue).map { (it * 255).roundToInt() }
            val (r2, g2, b2) = listOf(c.background.red, c.background.green, c.background.blue).map { (it * 255).roundToInt() }
            assertTrue(maxOf(abs(r1 - r2), abs(g1 - g2), abs(b1 - b2)) >= 8)
        }
    }

    @Test
    fun compositeMatchesRn() {
        // RN: composite('#191B1A', '#111312', 0.94)
        assertEquals("#191B1A", hex(composite(Palette.dark900, Palette.dark950, CAMERA_BAR_OPACITY)))
    }

    private fun hex(c: Color) = "#%02X%02X%02X".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())
}
