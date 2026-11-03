package com.yoloc.app.theme

import androidx.compose.ui.unit.dp

/** RN: theme/tokens.ts. Layout, shape and motion tokens shared by both themes (CLAUDE.md 4.4). */

/** 4dp grid. Use these instead of raw numbers. */
object Spacing {
    val none = 0.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp

    /** Screen padding. */
    val screen = 20.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/** The only three corner radii (redesign 2026-10-28: the prototype's softer corners). */
object Radii {
    /** Buttons, inputs, chips, the tab indicator. */
    val sm = 8.dp

    /** Cards, photos, panels, sheets. */
    val md = 16.dp

    /** The capture button, round icon buttons, the status dot. */
    val full = 999.dp
}

/** Minimum touch target (CLAUDE.md 2.2). */
val TouchTarget = 48.dp

/** Motion: purposeful only, 150–250 ms. Components must respect reduce-motion (rememberReduceMotion). */
object Motion {
    const val FAST = 150
    const val BASE = 200
    const val SLOW = 250

    /** Press feedback is quicker than transitions so a tap feels immediate. */
    const val PRESS = 100

    /**
     * Redesign 2026-10-31: the launch intro, the one sequence longer than a transition. About
     * 1.4 s: the mark settles and the leaves drift in, the name and tagline rise (done at 870 ms),
     * a short hold, then a [BASE] crossfade at [INTRO_LENGTH]. The first screen's first frame
     * holds the last intro frame a little longer before the crossfade (PARITY.md, Step 6).
     */
    const val INTRO_LEAVES = 700
    const val INTRO_TEXT_DELAY = 350
    const val INTRO_TEXT = 400
    const val INTRO_TEXT_STAGGER = 120
    const val INTRO_LENGTH = 1050

    /** One side of the leaves' slow sway, while the intro is visible. */
    const val INTRO_SWAY = 2400

    /** With reduce motion: everything in place, this hold, then the crossfade. */
    const val INTRO_REDUCED_HOLD = 400

    /** The mark's settle: a spring with a little overshoot. */
    const val INTRO_SPRING_DAMPING = 0.6f

    /**
     * Redesign 2026-10-31: Processing's scan line, one sweep down the pod (and the same back, so
     * 1.6 s per cycle). The leaves breathe on the same cycle.
     */
    const val SCAN_SWEEP = 800

    /**
     * Redesign 2026-10-31, onboarding panels: the blob morphs to the next panel's shape, chips pop
     * in [ONBOARDING_CHIP_STAGGER] apart with a small spring, feature rows fade in
     * [ONBOARDING_ROW_STAGGER] apart, the leaves sway one side per [ONBOARDING_SWAY], and the scan
     * panel's line crosses the photo once per [ONBOARDING_SCAN_PASS].
     */
    const val ONBOARDING_MORPH = 450
    const val ONBOARDING_CHIP_DELAY = 120
    const val ONBOARDING_CHIP_STAGGER = 80
    const val ONBOARDING_CHIP_DAMPING = 0.55f
    const val ONBOARDING_ROW_DELAY = 180
    const val ONBOARDING_ROW_STAGGER = 60
    const val ONBOARDING_SWAY = 3200
    const val ONBOARDING_SCAN_PASS = 2400
}

/**
 * Redesign 2026-10-31: the onboarding photo's "pebble" print, the one asymmetric shape. Three
 * [corner] corners and a large [tail] at the bottom end, so it reads as a framed print, not a
 * banner. Only for decorative photos on a panel; everything else keeps [Radii].
 */
object Pebble {
    val corner = 32.dp
    val tail = 96.dp
    val border = 4.dp
}

/** Bounding box drawing (CLAUDE.md 4.3). */
object BoxStroke {
    val width = 3.dp
    val haloWidth = 1.dp
}
