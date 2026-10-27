package com.yoloc.app.theme

import androidx.compose.ui.graphics.Color

/**
 * RN: theme/palette.ts. Raw palette (CLAUDE.md 4.2 / 4.3). Brand and status hex values are the
 * brief's exact colors; the brand greens are accents only. Grounds and surfaces come from the
 * neutral ramps.
 *
 * Derived entries exist because the brief's exact colors fail WCAG AA in some roles (Contrast.kt
 * and ContrastTest):
 * - amber on white is 2.25:1, so early-stage icons and text on light grounds use amberInk.
 * - brick on near-black is 3.49:1, so infected text and icons on dark grounds use brickLight.
 * - forest900 on sage500 is 4.27:1, so the healthy chip fill is sageChip.
 * - sage500 on cream100 (selected rows) is 2.98:1, so light-mode icons use sageInk.
 * Bounding-box strokes always keep the exact status colors.
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
