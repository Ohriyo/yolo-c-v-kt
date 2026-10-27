package com.yoloc.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.Tone
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.a11yLabel
import com.yoloc.app.components.rememberPress
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

private val ICON_DISC = 40.dp

/** The rows of a [SettingsGroup], in order; `if` leaves a row out. */
class SettingsGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** RN: SettingsList.tsx SettingsGroup. One rounded surface holding related rows, separated by hairlines. */
@Composable
fun SettingsGroup(build: SettingsGroupScope.() -> Unit) {
    val colors = YoloCTheme.colors
    val rows = SettingsGroupScope().apply(build).rows
    val hairline = (1f / LocalDensity.current.density).dp
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(colors.surface)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                Box(Modifier.fillMaxWidth().padding(start = Spacing.md + ICON_DISC + Spacing.sm).height(hairline).background(colors.border))
            }
            row()
        }
    }
}

/**
 * RN: SettingsList.tsx SettingsRow. Icon disc, label and an optional second line (the current
 * value or a short explanation), then a chevron when pressable, or [trailing].
 *
 * @param destructive Only for actions that delete data; uses the inverse surface's weight, not a
 *   status color.
 * @param checked Set for a switch row (the row is the control).
 * @param chevron False hides the default chevron (RN: `trailing={null}`).
 */
@Composable
fun SettingsRow(
    icon: IconName,
    label: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    checked: Boolean? = null,
    enabled: Boolean = true,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = YoloCTheme.colors
    val press = rememberPress()
    // RN reads the label, then the detail as the hint.
    val spoken = listOfNotNull(label, detail).joinToString(", ")
    val tap = if (onClick == null) {
        Modifier.a11yLabel(spoken)
    } else {
        Modifier
            .a11yAction(spoken, onClick, role = if (checked != null) Role.Switch else Role.Button, enabled = enabled, checked = checked)
            .clickable(enabled = enabled, interactionSource = press.source, indication = null, onClick = onClick)
            .background(if (press.pressed.value && enabled) colors.surfaceRaised else Color.Transparent)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget + Spacing.md)
            .alpha(if (enabled) 1f else 0.55f)
            .then(tap)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ICON_DISC).background(if (destructive) colors.inverse.bg else colors.surfaceRaised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, size = 20.dp, color = if (destructive) colors.inverse.text else colors.icon)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(label, variant = TypeVariant.BodyStrong)
            if (detail != null) AppText(detail, variant = TypeVariant.Caption, tone = Tone.Muted)
        }
        when {
            trailing != null -> trailing()
            onClick != null && chevron -> Icon(IconName.ChevronRight, size = 22.dp, color = colors.icon)
        }
    }
}
