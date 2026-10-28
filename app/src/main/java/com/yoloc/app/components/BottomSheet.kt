package com.yoloc.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TouchTarget
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: components/BottomSheet.tsx. Modal bottom sheet: Android back, a tap on the scrim and the
 * close button all call [onClose]. Long content (plot lists, forms) scrolls inside the sheet
 * instead of pushing the primary action off screen. Opens fully, never half way (RN has no
 * half-open state).
 *
 * @param dismissable False while the sheet must stay (saving): drag, scrim, back and the close
 *   button then do nothing, as RN's `onClose={saving ? () => {} : onClose}`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    visible: Boolean,
    title: String,
    onClose: () -> Unit,
    dismissable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val colors = YoloCTheme.colors
    val currentDismissable by rememberUpdatedState(dismissable)
    val shape = RoundedCornerShape(topStart = Radii.md, topEnd = Radii.md)
    ModalBottomSheet(
        onDismissRequest = { if (currentDismissable) onClose() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || currentDismissable },
        ),
        shape = shape,
        containerColor = colors.surfaceRaised,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.xs)
                    .size(width = 32.dp, height = 4.dp)
                    .background(colors.outline, RoundedCornerShape(Radii.full)),
            )
        },
    ) {
        SheetBody(title, { if (currentDismissable) onClose() }, content, Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

/**
 * RN: BottomSheetPanel. The sheet's surface, handle, header and body drawn in place, without the
 * modal. Dev gallery only (an inline preview next to the real sheet).
 */
@Composable
fun BottomSheetPanel(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = YoloCTheme.colors
    val shape = RoundedCornerShape(topStart = Radii.md, topEnd = Radii.md)
    Column(
        modifier.fillMaxWidth().floatingShadow(shape).background(colors.surfaceRaised, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = Spacing.xs)
                .size(width = 32.dp, height = 4.dp)
                .background(colors.outline, RoundedCornerShape(Radii.full)),
        )
        SheetBody(title, onClose, content)
    }
}

/** Header (title, close) and the scrolling body: long content scrolls inside the sheet. */
@Composable
private fun SheetBody(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit, modifier: Modifier = Modifier) {
    val colors = YoloCTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.md)
            .then(modifier)
            .padding(bottom = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppText(title, Modifier.weight(1f), variant = TypeVariant.Title)
            val press = rememberPress()
            val close = stringResource(R.string.common_close)
            Box(
                Modifier
                    .size(TouchTarget)
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(if (press.pressed.value) colors.surface else Color.Transparent)
                    .a11yAction(close, onClose)
                    .clickable(interactionSource = press.source, indication = null, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IconName.X, size = 24.dp, color = colors.text)
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}
