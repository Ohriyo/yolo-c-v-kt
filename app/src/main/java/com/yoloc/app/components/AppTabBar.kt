package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

data class TabBarItem<K>(val key: K, val label: String, val icon: IconName)

/** RN: fontFamilies.semibold on the active label (caption is Barlow Medium). */
private val strongLabel = TextStyle(fontWeight = FontWeight.SemiBold)

/**
 * RN: components/TabBar.tsx. Material navigation bar. The active tab gets an indicator behind its
 * icon and a heavier label, not color alone. Scan ([centerKey]) sits in line with the tabs, set
 * apart by a filled indicator in the primary color, so the main action is unmistakable without
 * floating above the bar. It is an action button, never "selected".
 */
@Composable
fun <K> AppTabBar(
    items: List<TabBarItem<K>>,
    activeKey: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    centerKey: K? = null,
) {
    val colors = YoloCTheme.colors
    val t = colors.tabBar
    Column(modifier.fillMaxWidth().background(t.bg)) {
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .selectableGroup()
                .padding(top = Spacing.xs, start = Spacing.xxs, end = Spacing.xxs),
        ) {
            for (item in items) {
                val active = item.key == activeKey
                val isCenter = item.key == centerKey
                val press = rememberPress()
                val pressed = press.pressed.value
                val indicator = when {
                    isCenter -> t.scanBg
                    active -> t.indicator
                    pressed -> colors.surface
                    else -> Color.Transparent
                }
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = TouchTarget + Spacing.md)
                        .a11yAction(
                            item.label,
                            { onSelect(item.key) },
                            role = if (isCenter) Role.Button else Role.Tab,
                            selected = if (isCenter) null else active,
                        )
                        .clickable(interactionSource = press.source, indication = null) { onSelect(item.key) }
                        .padding(bottom = Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 32.dp)
                            .alpha(if (isCenter && pressed) 0.8f else 1f)
                            .background(indicator, RoundedCornerShape(Radii.full)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            item.icon,
                            size = if (isCenter) 24.dp else 22.dp,
                            color = when {
                                isCenter -> t.scanIcon
                                active -> t.active
                                else -> t.label
                            },
                        )
                    }
                    AppText(
                        item.label,
                        variant = TypeVariant.Caption,
                        color = if (active || isCenter) t.active else t.label,
                        style = if (active || isCenter) strongLabel else null,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
