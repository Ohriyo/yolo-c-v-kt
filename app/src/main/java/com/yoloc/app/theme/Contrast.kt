package com.yoloc.app.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** RN: theme/contrast.ts. WCAG 2.x minimums. */
enum class ContrastKind(val minimum: Double) {
    /** Body text and anything under 18.66px bold / 24px regular. */
    Text(4.5),

    /** Large text (>= 24px regular or >= 18.66px bold). */
    LargeText(3.0),

    /** Non-text UI that conveys meaning: icons, focus rings (WCAG 1.4.11). */
    NonText(3.0),
}

data class ContrastPair(val name: String, val fg: Color, val bg: Color, val kind: ContrastKind)

/** One 8-bit sRGB channel, linearized as WCAG does (RN parses the same 8-bit hex values). */
private fun channel(v: Float): Double {
    val c = (v * 255).roundToInt() / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/** Relative luminance of an opaque color. */
fun luminance(color: Color): Double {
    require(color.alpha == 1f) { "Contrast check needs an opaque color, got $color" }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

fun contrastRatio(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** RN: color.ts composite. The opaque color seen when `fg` at `alpha` is drawn over `bg`. */
fun composite(fg: Color, bg: Color, alpha: Float): Color {
    fun mix(f: Float, b: Float) = (f * 255 * alpha + b * 255 * (1 - alpha)).roundToInt() / 255f
    return Color(mix(fg.red, bg.red), mix(fg.green, bg.green), mix(fg.blue, bg.blue))
}

/**
 * Every text/background (and meaningful icon/background) pair the components actually use. When
 * a component starts using a new pair, add it here so ContrastTest covers it. Disabled controls
 * are exempt from WCAG contrast and are intentionally not listed.
 */
fun contrastPairs(c: YoloCColors): List<ContrastPair> {
    val themeName = if (c.isDark) "dark" else "light"
    val pairs = mutableListOf<ContrastPair>()
    fun add(name: String, fg: Color, bg: Color, kind: ContrastKind = ContrastKind.Text) {
        pairs += ContrastPair("$themeName: $name", fg, bg, kind)
    }

    val surfaces = listOf(
        "background" to c.background,
        "card" to c.card,
        "surface" to c.surface,
        "surfaceRaised" to c.surfaceRaised,
        "selected" to c.selected,
    )
    for ((surfaceName, bg) in surfaces) {
        add("text on $surfaceName", c.text, bg)
        add("textMuted on $surfaceName", c.textMuted, bg)
        add("textFaint on $surfaceName", c.textFaint, bg)
        add("link on $surfaceName", c.link, bg)
        add("ghost button text on $surfaceName", c.ghost.text, bg)
        add("icon on $surfaceName", c.icon, bg, ContrastKind.NonText)
        add("control outline on $surfaceName", c.outline, bg, ContrastKind.NonText)
        add("focus ring on $surfaceName", c.focus, bg, ContrastKind.NonText)
        for (key in StatusKey.entries) {
            add("${key.token} status icon on $surfaceName", c.status(key).ink, bg, ContrastKind.NonText)
        }
    }

    for ((variant, b) in listOf("primary" to c.primary, "secondary" to c.secondary, "ghost" to c.ghost, "onDark" to c.onDark)) {
        if (b.bg != Color.Transparent) add("$variant button text", b.text, b.bg)
        add("$variant button pressed text", b.pressedText, b.pressedBg)
    }

    // Snackbar; History swipe-to-delete reveals the same inverse surface.
    add("snackbar text", c.inverse.text, c.inverse.bg)
    add("snackbar icon", c.inverse.icon, c.inverse.bg, ContrastKind.NonText)

    add("tab label", c.tabBar.label, c.tabBar.bg)
    add("active tab label", c.tabBar.active, c.tabBar.bg)
    add("active tab icon on indicator", c.tabBar.active, c.tabBar.indicator, ContrastKind.NonText)
    add("active tab label on indicator", c.tabBar.active, c.tabBar.indicator)
    add("scan tab icon on its pill", c.primary.text, c.primary.bg, ContrastKind.NonText)
    add("scan tab pill against the bar", c.primary.bg, c.tabBar.bg, ContrastKind.NonText)

    for (key in StatusKey.entries) {
        add("${key.token} label on status fill", c.status(key).onFill, c.status(key).fill)
        add("${key.token} label on status tint", c.status(key).onTint, c.status(key).tint)
    }

    // Camera screen (dark in both themes). The bar is translucent over the camera screen.
    val cam = c.camera
    val bar = composite(cam.bar, cam.screen, CAMERA_BAR_OPACITY)
    add("camera control icon on bar", cam.control, bar)
    add("camera shutter ring on bar", cam.control, bar, ContrastKind.NonText)
    add("camera active toggle icon", cam.lens, cam.control, ContrastKind.NonText)
    add("camera gallery placeholder icon", cam.control, cam.galleryFill, ContrastKind.NonText)
    add("camera text on screen", cam.lensText, cam.screen)
    add("camera lens text", cam.lensText, cam.lens)
    add("camera lens muted text", cam.lensTextMuted, cam.lens)
    add("camera lens button", c.onDark.text, c.onDark.bg)

    return pairs
}
