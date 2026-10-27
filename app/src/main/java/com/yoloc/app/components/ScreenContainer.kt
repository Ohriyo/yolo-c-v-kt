package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: components/ScreenContainer.tsx. Themed background, 20dp screen padding, inset aware.
 *
 * @param scroll Scrollable content (default). False for screens that manage their own lists.
 * @param insets The system areas to keep clear (RN: edges). Tab screens pass top and sides only,
 *   since the tab bar keeps the navigation bar clear.
 * @param footer Pinned at the bottom, outside the scroll area (e.g. Save / Discard).
 */
@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    insets: WindowInsets = WindowInsets.safeDrawing,
    contentPadding: PaddingValues = PaddingValues(Spacing.screen),
    /** Gap between the screen's blocks (RN: contentStyle gap). */
    spacing: Dp = Spacing.lg,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = YoloCTheme.colors
    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(insets)) {
        val body = Modifier.weight(1f).fillMaxWidth()
        Column(
            modifier = (if (scroll) body.verticalScroll(rememberScrollState()) else body).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
        if (footer != null) {
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                content = footer,
            )
        }
    }
}
