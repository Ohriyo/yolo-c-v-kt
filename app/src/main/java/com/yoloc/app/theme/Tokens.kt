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
}

/** Bounding box drawing (CLAUDE.md 4.3). */
object BoxStroke {
    val width = 3.dp
    val haloWidth = 1.dp
}
