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

/** The only three corner radii. */
object Radii {
    /** Buttons, inputs, small controls. */
    val sm = 4.dp

    /** Photos, panels, sheets. */
    val md = 10.dp

    /** Status and filter chips, the capture button, the tab indicator. */
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

/** The camera control bar sits at this opacity over the camera screen. */
const val CAMERA_BAR_OPACITY = 0.94f

/** Bounding box drawing (CLAUDE.md 4.3). */
object BoxStroke {
    val width = 3.dp
    val haloWidth = 1.dp
}
