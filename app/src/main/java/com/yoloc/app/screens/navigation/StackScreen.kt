package com.yoloc.app.screens.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.a11yAction
import com.yoloc.app.components.rememberPress
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/** RN's native header title: the app's SemiBold at the platform's 20sp. */
private val headerTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp)

/**
 * RN: withHeader(title) in app/_layout.tsx. Stack screens (everything outside the tabs) get a
 * header with a back arrow, so there is always an on-screen way back as well as Android's back.
 * Same background as the screen, no shadow. [onBack] is usually the navigator's up; Result
 * passes its own to ask before leaving an unsaved scan.
 *
 * Redesign 2026-10-31: [floating] puts the content under the whole screen (Processing's photo)
 * with the back arrow in the same place, as a round secondary button over it and no title (the
 * content names the screen).
 */
@Composable
fun StackScreen(title: String, onBack: () -> Unit, floating: Boolean = false, content: @Composable () -> Unit) {
    val colors = YoloCTheme.colors
    val topEdges = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    if (floating) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            content()
            Header(title = null, onBack = onBack, topEdges = topEdges)
        }
        return
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Header(title, onBack, topEdges)
        // The header already keeps the status bar clear; the screen below must not pad for it again.
        Box(Modifier.weight(1f).consumeWindowInsets(topEdges)) { content() }
    }
}

@Composable
private fun Header(title: String?, onBack: () -> Unit, topEdges: WindowInsets) {
    val colors = YoloCTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(topEdges)
            .height(56.dp)
            .padding(horizontal = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val press = rememberPress()
        val up = stringResource(R.string.nav_up)
        // Over content, the secondary button's colors and edge keep it clear of what is behind.
        val button = colors.secondary
        val floating = title == null
        Box(
            Modifier
                .size(TouchTarget)
                .clip(CircleShape)
                .background(
                    when {
                        floating -> if (press.pressed.value) button.pressedBg else button.bg
                        press.pressed.value -> colors.surface
                        else -> Color.Transparent
                    },
                )
                .then(if (floating) Modifier.border(1.dp, button.border, CircleShape) else Modifier)
                .a11yAction(up, onBack)
                .clickable(interactionSource = press.source, indication = null, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                IconName.ArrowLeft,
                size = 24.dp,
                color = when {
                    !floating -> colors.text
                    press.pressed.value -> button.pressedText
                    else -> button.text
                },
            )
        }
        if (title != null) AppText(title, Modifier.weight(1f), variant = TypeVariant.Title, style = headerTitle, maxLines = 1)
    }
}
