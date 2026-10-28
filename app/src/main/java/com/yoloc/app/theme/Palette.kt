package com.yoloc.app.theme

import androidx.compose.ui.graphics.Color

/**
 * RN: theme/palette.ts. Raw palette (CLAUDE.md 4.2 / 4.3). Brand and status hex values are the
 * brief's exact colors; the brand greens are accents only. Grounds and surfaces come from the
 * neutral ramps.
 *
 * Derived entries exist because the brief's exact colors fail WCAG AA in some roles (Contrast.kt
 * and ContrastTest):
 * - amber on white is 2.25:1, so early-stage icons on light grounds use amberInk.
 * - brick on near-black is 3.49:1, so infected icons on dark grounds use brickLight.
 * - forest900 on sage500 is 4.27:1, so the healthy chip fill is sageChip.
 * - sage500 on cream100 (selected rows) is 2.98:1, so light-mode icons use sageInk.
 * Bounding-box strokes always keep the exact status colors. Status inks color icons only, never
 * text (status text sits on the status fill), so ContrastTest checks them at the 3:1 icon minimum.
 */
object Palette {
    // Brand (4.2): accents, never page fills
    val forest900 = Color(0xFF0F2A1D)
    val forest700 = Color(0xFF375534)
    val sage500 = Color(0xFF6B9071)
    val mist300 = Color(0xFFAEC3B0)
    val cream100 = Color(0xFFE3EED4)

    // Status (4.3)
    val statusHealthy = Color(0xFF6B9071)
    val statusEarly = Color(0xFFD9A441)
    val statusInfected = Color(0xFFB5473A)

    // Derived for AA (see note above)
    val amberInk = Color(0xFF8A6516)
    val sageInk = Color(0xFF5F8465)
    val brickLight = Color(0xFFDE7A68)
    val sageChip = Color(0xFF7D9E80)

    // Redesign 2026-10-28 (the prototype recording): the leaf-tinted ground and its tints
    val ground = Color(0xFFF6F8F5)
    val leaf = Color(0xFFEBF1E6)
    val hairline = Color(0xFFE8ECE6)
    val pressLight = Color(0xFFF0F3EE)
    val inkMuted = Color(0xFF4F5E54)
    val inkFaint = Color(0xFF606A64)
    val controlEdge = Color(0xFFCAD2C8)

    // Pale status tints behind chip text (AA with their inks, ContrastTest)
    val healthyTint = Color(0xFFE6F0E1)
    val earlyTint = Color(0xFFFAF0DA)
    val earlyTintInk = Color(0xFF7A5810)
    val infectedTint = Color(0xFFFBE7E3)
    val infectedTintInk = Color(0xFF9C3A2F)

    // Dark counterparts
    val groundDark = Color(0xFF0F1310)
    val cardDark = Color(0xFF171D18)
    val pressDark = Color(0xFF1F2721)
    val leafDark = Color(0xFF223125)
    val hairlineDark = Color(0xFF273029)
    val inkDark = Color(0xFFE8EDE8)
    val inkMutedDark = Color(0xFFB0BAB2)
    val inkFaintDark = Color(0xFF9AA59D)
    val healthyTintDark = Color(0xFF1E2E22)
    val healthyTintInkDark = Color(0xFFA9CDAA)
    val earlyTintDark = Color(0xFF352B17)
    val earlyTintInkDark = Color(0xFFEBC36F)
    val infectedTintDark = Color(0xFF3B211E)
    val infectedTintInkDark = Color(0xFFF29A8E)

    // Light neutrals
    val white = Color(0xFFFFFFFF)
    val light50 = Color(0xFFF5F6F5)
    val light100 = Color(0xFFEDEFED)
    val light400 = Color(0xFF7A7E7C)
    val light600 = Color(0xFF5B5F5D)
    val light900 = Color(0xFF16191A)

    // Dark neutrals (green channel at most 2 levels above red/blue, so they read as neutral)
    val dark950 = Color(0xFF111312)
    val dark900 = Color(0xFF191B1A)
    val dark850 = Color(0xFF222423)
    val dark800 = Color(0xFF2B2D2C)
    val dark500 = Color(0xFF777A79)
    val dark300 = Color(0xFFA6A9A7)
    val dark50 = Color(0xFFECEDEC)

    val black = Color(0xFF000000)
}
