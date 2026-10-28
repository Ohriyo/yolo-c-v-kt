package com.yoloc.app.components

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * Redesign 2026-10-28 (the prototype; Kotlin only, docs/PARITY.md): the bar over every tab
 * screen. The tagline, an "Offline ready" badge that states the normal state positively, and the
 * dark mode switch.
 */
@Composable
fun AppTopBar(dark: Boolean, onToggleDark: () -> Unit, modifier: Modifier = Modifier) {
    val colors = YoloCTheme.colors
    Column(modifier.fillMaxWidth().background(colors.card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .heightIn(min = 56.dp)
                .padding(start = Spacing.screen, end = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Large text leaves no room for both; the badge says more, so the tagline gives way.
            if (LocalDensity.current.fontScale > 1.15f) {
                Spacer(Modifier.weight(1f))
            } else {
                AppText(stringResource(R.string.topbar_tagline), Modifier.weight(1f), variant = TypeVariant.Caption, tone = Tone.Muted, maxLines = 1)
            }
            OfflineBadge()
            val press = rememberPress()
            Box(
                Modifier
                    .size(TouchTarget)
                    .clip(CircleShape)
                    .background(if (press.pressed.value) colors.surface else colors.card)
                    .a11yAction(stringResource(R.string.topbar_dark_mode), onToggleDark, role = Role.Switch, checked = dark)
                    .clickable(interactionSource = press.source, indication = null, onClick = onToggleDark),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (dark) IconName.Moon else IconName.Sun, size = 22.dp, color = colors.text)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}

@Composable
private fun OfflineBadge() {
    val colors = YoloCTheme.colors
    val shape = RoundedCornerShape(Radii.sm)
    Row(
        Modifier
            .a11yLabel(stringResource(R.string.topbar_offline_a11y))
            .border(1.dp, colors.border, shape)
            .heightIn(min = 28.dp)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(colors.healthy.ink, CircleShape))
        AppText(stringResource(R.string.topbar_offline), variant = TypeVariant.Caption, tone = Tone.Muted, maxLines = 1)
    }
}
