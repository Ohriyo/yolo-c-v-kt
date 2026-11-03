package com.yoloc.app.theme

import androidx.compose.ui.graphics.Color
import com.yoloc.app.theme.Palette as p

/** RN: theme/themes.ts StatusKey. Matches the classes table's color_token. */
enum class StatusKey(val token: String) {
    Healthy("healthy"),
    Early("early"),
    Infected("infected"),
    ;

    companion object {
        /** A class's color_token; an unknown token reads as healthy, as RN's `?? 'healthy'`. */
        fun fromToken(token: String?): StatusKey = entries.firstOrNull { it.token == token } ?: Healthy
    }
}

data class StatusColors(
    /** Bounding-box stroke on photos. Exact brief color. */
    val stroke: Color,
    /** Status icon on this theme's background and surfaces (passes 3:1). */
    val ink: Color,
    /** Solid fill behind text on photos (box labels), matching the stroke. AA-safe with [onFill]. */
    val fill: Color,
    val onFill: Color,
    /** Pale fill behind chip text on the app's own surfaces. AA-safe with [onTint]. */
    val tint: Color,
    val onTint: Color,
)

data class ButtonColors(
    val bg: Color,
    val text: Color,
    val pressedBg: Color,
    val pressedText: Color,
    val border: Color,
)

/** Processing's "Checking your pod" mark (redesign 2026-10-31). Decorative. */
data class ScanMarkColors(
    val pod: Color,
    val leaves: Color,
    /** The line that sweeps over the pod, and its soft glow. */
    val line: Color,
    val glow: Color,
)

/** The onboarding panels' ground and decoration (redesign 2026-10-31). All decorative. */
data class OnboardingColors(
    /** The ground's vertical gradient, top to bottom. Text sits on it, so ContrastTest checks both ends. */
    val groundTop: Color,
    val groundBottom: Color,
    /** The organic blob behind the photo. Translucent. */
    val blob: Color,
    /** The faint dot grid in one corner. Translucent. */
    val dots: Color,
    /** The photo's "pebble" print: its 4dp border. */
    val printBorder: Color,
    /** The segmented progress bar: the current step (wider) and the others. */
    val progressActive: Color,
    val progressIdle: Color,
    /** The scan panel's viewfinder brackets and sweeping line over the photo. */
    val bracket: Color,
    val scanLine: Color,
    val scanGlow: Color,
)

data class CameraColors(
    /** Whole camera screen, dark in both themes. */
    val screen: Color,
    /** Inside the viewfinder before the preview starts, and behind permission/error panels. */
    val lens: Color,
    val lensText: Color,
    val lensTextMuted: Color,
    /** Thin viewfinder outline. Decorative. */
    val frameBorder: Color,
    /** Corner guide marks over the preview. Decorative. */
    val guide: Color,
    /** Live mode's chips and the Photo / Live switch. Opaque, so contrast can be checked. */
    val bar: Color,
    val barEdge: Color,
    /** Icons, shutter ring and outlines on the bar. */
    val control: Color,
    /** Quiet circle behind an off toggle. Decorative. */
    val controlFill: Color,
    /** The ring around the white shutter (redesign 2026-10-28: the prototype's soft sage). */
    val shutterRing: Color,
    /**
     * Redesign 2026-10-28: the dark wash behind the photo camera's top row and controls, over the
     * live picture. Translucent; Contrast.kt checks it over a white scene, the worst case.
     */
    val scrim: Color,
    val galleryFill: Color,
)

data class InverseColors(val bg: Color, val text: Color, val icon: Color)

data class DisabledColors(val bg: Color, val text: Color)

data class TabBarColors(
    val bg: Color,
    val label: Color,
    val active: Color,
    val indicator: Color,
)

/** RN: Theme.colors. Same fields, same values. */
data class YoloCColors(
    val isDark: Boolean,
    val background: Color,
    /** Cards on the ground. */
    val card: Color,
    /** The soft low shadow under cards; transparent in dark (the hairline carries depth). */
    val cardShadow: Color,
    /** Grouped areas, inputs, skeletons. */
    val surface: Color,
    /** Sheets and dialogs. */
    val surfaceRaised: Color,
    /** Selected row or chip. */
    val selected: Color,
    val text: Color,
    /** Secondary text. */
    val textMuted: Color,
    /** Meta text: times, captions under readouts. */
    val textFaint: Color,
    val icon: Color,
    /** Hairline dividers and panel borders. Decorative grouping, not a control boundary. */
    val border: Color,
    /** Boundaries of controls (inputs, outlined buttons, toggles): passes 3:1. */
    val outline: Color,
    val focus: Color,
    val link: Color,
    val scrim: Color,
    val boxHalo: Color,
    /** Snackbar surface. */
    val inverse: InverseColors,
    val primary: ButtonColors,
    val secondary: ButtonColors,
    val ghost: ButtonColors,
    /** Button on a dark field (camera lens panels). Same in both themes. */
    val onDark: ButtonColors,
    val disabled: DisabledColors,
    val tabBar: TabBarColors,
    val camera: CameraColors,
    val healthy: StatusColors,
    val early: StatusColors,
    val infected: StatusColors,
    /** Only floating elements cast a shadow: the bottom sheet and the snackbar. */
    val floatingShadow: Color,
    /** Decorative leaves (the launch intro): Sage and Mist, faint so they never compete. */
    val decorLeaf: Color,
    val decorLeafSoft: Color,
    /** Over the blurred photo behind Processing's card. */
    val photoScrim: Color,
    val scanMark: ScanMarkColors,
    val onboarding: OnboardingColors,
) {
    /** RN: theme.status[key]. */
    fun status(key: StatusKey): StatusColors = when (key) {
        StatusKey.Healthy -> healthy
        StatusKey.Early -> early
        StatusKey.Infected -> infected
    }
}

private val camera = CameraColors(
    screen = p.dark950,
    lens = p.dark900,
    lensText = p.dark50,
    lensTextMuted = p.dark300,
    frameBorder = p.white.copy(alpha = 0.32f),
    guide = p.white,
    bar = p.dark900,
    barEdge = p.white.copy(alpha = 0.08f),
    control = p.white,
    controlFill = p.white.copy(alpha = 0.1f),
    shutterRing = p.mist300,
    scrim = p.black.copy(alpha = 0.6f),
    galleryFill = p.dark850,
)

private val onDarkButton = ButtonColors(
    bg = p.white,
    text = p.dark950,
    pressedBg = p.light100,
    pressedText = p.dark950,
    border = p.white,
)

private fun healthy(ink: Color, tint: Color, onTint: Color) =
    StatusColors(stroke = p.statusHealthy, ink = ink, fill = p.sageChip, onFill = p.forest900, tint = tint, onTint = onTint)

private fun early(ink: Color, tint: Color, onTint: Color) =
    StatusColors(stroke = p.statusEarly, ink = ink, fill = p.statusEarly, onFill = p.forest900, tint = tint, onTint = onTint)

private fun infected(ink: Color, tint: Color, onTint: Color) =
    StatusColors(stroke = p.statusInfected, ink = ink, fill = p.statusInfected, onFill = p.white, tint = tint, onTint = onTint)

/**
 * Redesign 2026-10-28: the prototype's leaf-tinted ground, white cards with a hairline and a soft
 * shadow, the forest green for actions and the active tab, pale status chips. The brand and
 * status hex values are unchanged.
 */
val lightColors = YoloCColors(
    isDark = false,
    background = p.ground,
    card = p.white,
    cardShadow = p.forest900.copy(alpha = 0.06f),
    surface = p.pressLight,
    surfaceRaised = p.white,
    selected = p.leaf,
    text = p.forest900,
    textMuted = p.inkMuted,
    textFaint = p.inkFaint,
    icon = p.sageInk,
    border = p.hairline,
    outline = p.light400,
    focus = p.forest700,
    link = p.forest700,
    scrim = p.dark950.copy(alpha = 0.45f),
    boxHalo = p.white,
    inverse = InverseColors(bg = p.light900, text = p.white, icon = p.mist300),
    primary = ButtonColors(bg = p.forest700, text = p.white, pressedBg = p.forest900, pressedText = p.white, border = p.forest700),
    secondary = ButtonColors(bg = p.white, text = p.forest700, pressedBg = p.leaf, pressedText = p.forest900, border = p.controlEdge),
    ghost = ButtonColors(bg = Color.Transparent, text = p.forest700, pressedBg = p.leaf, pressedText = p.forest900, border = Color.Transparent),
    onDark = onDarkButton,
    disabled = DisabledColors(bg = p.light100, text = p.light600),
    tabBar = TabBarColors(bg = p.white, label = p.inkFaint, active = p.forest700, indicator = p.leaf),
    camera = camera,
    healthy = healthy(ink = p.sageInk, tint = p.healthyTint, onTint = p.forest700),
    early = early(ink = p.amberInk, tint = p.earlyTint, onTint = p.earlyTintInk),
    infected = infected(ink = p.statusInfected, tint = p.infectedTint, onTint = p.infectedTintInk),
    floatingShadow = p.dark950.copy(alpha = 0.18f),
    decorLeaf = p.sage500.copy(alpha = 0.32f),
    decorLeafSoft = p.mist300.copy(alpha = 0.5f),
    photoScrim = p.ground.copy(alpha = 0.7f),
    scanMark = ScanMarkColors(pod = p.forest700, leaves = p.sage500, line = p.forest700, glow = p.sage500.copy(alpha = 0.35f)),
    onboarding = OnboardingColors(
        groundTop = p.leaf,
        groundBottom = p.ground,
        blob = p.mist300.copy(alpha = 0.25f),
        dots = p.forest900.copy(alpha = 0.06f),
        printBorder = p.white,
        progressActive = p.forest700,
        progressIdle = p.mist300,
        bracket = p.white,
        scanLine = p.cream100,
        scanGlow = p.sage500.copy(alpha = 0.5f),
    ),
)

val darkColors = YoloCColors(
    isDark = true,
    background = p.groundDark,
    card = p.cardDark,
    cardShadow = Color.Transparent,
    surface = p.pressDark,
    surfaceRaised = p.cardDark,
    selected = p.leafDark,
    text = p.inkDark,
    textMuted = p.inkMutedDark,
    textFaint = p.inkFaintDark,
    icon = p.sage500,
    border = p.hairlineDark,
    outline = p.dark500,
    focus = p.mist300,
    link = p.mist300,
    scrim = p.black.copy(alpha = 0.6f),
    boxHalo = p.white,
    inverse = InverseColors(bg = p.dark50, text = p.dark950, icon = p.forest700),
    primary = ButtonColors(bg = p.sage500, text = p.dark950, pressedBg = p.mist300, pressedText = p.dark950, border = p.sage500),
    secondary = ButtonColors(bg = p.cardDark, text = p.mist300, pressedBg = p.leafDark, pressedText = p.dark50, border = p.dark800),
    ghost = ButtonColors(bg = Color.Transparent, text = p.mist300, pressedBg = p.leafDark, pressedText = p.dark50, border = Color.Transparent),
    onDark = onDarkButton,
    disabled = DisabledColors(bg = p.dark850, text = p.dark300),
    tabBar = TabBarColors(bg = p.cardDark, label = p.inkFaintDark, active = p.mist300, indicator = p.leafDark),
    camera = camera,
    healthy = healthy(ink = p.statusHealthy, tint = p.healthyTintDark, onTint = p.healthyTintInkDark),
    early = early(ink = p.statusEarly, tint = p.earlyTintDark, onTint = p.earlyTintInkDark),
    infected = infected(ink = p.brickLight, tint = p.infectedTintDark, onTint = p.infectedTintInkDark),
    floatingShadow = p.black.copy(alpha = 0.5f),
    decorLeaf = p.sage500.copy(alpha = 0.16f),
    decorLeafSoft = p.mist300.copy(alpha = 0.1f),
    photoScrim = p.forest900.copy(alpha = 0.7f),
    // The logo's dark tile: a Cream pod, so the mark holds on the dark card (Moss is 2.2:1 there).
    scanMark = ScanMarkColors(pod = p.cream100, leaves = p.sage500, line = p.mist300, glow = p.sage500.copy(alpha = 0.45f)),
    // Deep Forest Ink at the top fading to the dark ground (never pure black); a card-dark frame,
    // since a white one glares on the dark ground.
    onboarding = OnboardingColors(
        groundTop = p.forest900,
        groundBottom = p.groundDark,
        blob = p.forest700.copy(alpha = 0.2f),
        dots = p.inkDark.copy(alpha = 0.06f),
        printBorder = p.cardDark,
        progressActive = p.sage500,
        progressIdle = p.forest700,
        bracket = p.white,
        scanLine = p.cream100,
        scanGlow = p.sage500.copy(alpha = 0.5f),
    ),
)
