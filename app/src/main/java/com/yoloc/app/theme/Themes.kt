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
    /** Fill behind text (chips on photos and lists). AA-safe with [onFill]. */
    val fill: Color,
    val onFill: Color,
)

data class ButtonColors(
    val bg: Color,
    val text: Color,
    val pressedBg: Color,
    val pressedText: Color,
    val border: Color,
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
    /** Control bar, drawn at [CAMERA_BAR_OPACITY]. Opaque so contrast can be checked. */
    val bar: Color,
    val barEdge: Color,
    /** Icons, shutter ring and outlines on the bar. */
    val control: Color,
    /** Quiet circle behind an off toggle. Decorative. */
    val controlFill: Color,
    val galleryFill: Color,
)

data class InverseColors(val bg: Color, val text: Color, val icon: Color)

data class DisabledColors(val bg: Color, val text: Color)

data class TabBarColors(
    val bg: Color,
    val label: Color,
    val active: Color,
    val indicator: Color,
    val scanBg: Color,
    val scanIcon: Color,
)

/** RN: Theme.colors. Same fields, same values. */
data class YoloCColors(
    val isDark: Boolean,
    val background: Color,
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
    val status: Map<StatusKey, StatusColors>,
    /** Only floating elements cast a shadow: bottom sheet, snackbar, camera controls. */
    val floatingShadow: Color,
) {
    fun status(key: StatusKey): StatusColors = status.getValue(key)
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
    galleryFill = p.dark850,
)

private val onDarkButton = ButtonColors(
    bg = p.white,
    text = p.dark950,
    pressedBg = p.light100,
    pressedText = p.dark950,
    border = p.white,
)

private fun healthy(ink: Color) = StatusColors(stroke = p.statusHealthy, ink = ink, fill = p.sageChip, onFill = p.forest900)

private fun early(ink: Color) = StatusColors(stroke = p.statusEarly, ink = ink, fill = p.statusEarly, onFill = p.forest900)

private fun infected(ink: Color) = StatusColors(stroke = p.statusInfected, ink = ink, fill = p.statusInfected, onFill = p.white)

val lightColors = YoloCColors(
    isDark = false,
    background = p.white,
    surface = p.light50,
    surfaceRaised = p.white,
    selected = p.cream100,
    text = p.forest900,
    textMuted = p.forest700,
    textFaint = p.light600,
    icon = p.sageInk,
    border = p.mist300.copy(alpha = 0.45f),
    outline = p.light400,
    focus = p.forest700,
    link = p.forest700,
    scrim = p.dark950.copy(alpha = 0.45f),
    boxHalo = p.white,
    inverse = InverseColors(bg = p.light900, text = p.white, icon = p.mist300),
    primary = ButtonColors(bg = p.forest700, text = p.white, pressedBg = p.forest900, pressedText = p.white, border = p.forest700),
    secondary = ButtonColors(bg = Color.Transparent, text = p.forest700, pressedBg = p.light100, pressedText = p.forest900, border = p.light400),
    ghost = ButtonColors(bg = Color.Transparent, text = p.forest700, pressedBg = p.light100, pressedText = p.forest900, border = Color.Transparent),
    onDark = onDarkButton,
    disabled = DisabledColors(bg = p.light100, text = p.light600),
    tabBar = TabBarColors(bg = p.white, label = p.light600, active = p.forest900, indicator = p.cream100, scanBg = p.forest700, scanIcon = p.white),
    camera = camera,
    status = mapOf(
        StatusKey.Healthy to healthy(ink = p.sageInk),
        StatusKey.Early to early(ink = p.amberInk),
        StatusKey.Infected to infected(ink = p.statusInfected),
    ),
    floatingShadow = p.dark950.copy(alpha = 0.18f),
)

val darkColors = YoloCColors(
    isDark = true,
    background = p.dark950,
    surface = p.dark900,
    surfaceRaised = p.dark850,
    selected = p.dark800,
    text = p.dark50,
    textMuted = p.dark300,
    textFaint = p.dark300,
    icon = p.sage500,
    border = p.mist300.copy(alpha = 0.18f),
    outline = p.dark500,
    focus = p.mist300,
    link = p.mist300,
    scrim = p.black.copy(alpha = 0.6f),
    boxHalo = p.white,
    inverse = InverseColors(bg = p.dark50, text = p.dark950, icon = p.forest700),
    primary = ButtonColors(bg = p.sage500, text = p.dark950, pressedBg = p.mist300, pressedText = p.dark950, border = p.sage500),
    secondary = ButtonColors(bg = Color.Transparent, text = p.mist300, pressedBg = p.dark850, pressedText = p.dark50, border = p.dark500),
    ghost = ButtonColors(bg = Color.Transparent, text = p.mist300, pressedBg = p.dark850, pressedText = p.dark50, border = Color.Transparent),
    onDark = onDarkButton,
    disabled = DisabledColors(bg = p.dark850, text = p.dark300),
    tabBar = TabBarColors(bg = p.dark900, label = p.dark300, active = p.dark50, indicator = p.forest700, scanBg = p.sage500, scanIcon = p.dark950),
    camera = camera,
    status = mapOf(
        StatusKey.Healthy to healthy(ink = p.statusHealthy),
        StatusKey.Early to early(ink = p.statusEarly),
        StatusKey.Infected to infected(ink = p.brickLight),
    ),
    floatingShadow = p.black.copy(alpha = 0.5f),
)
