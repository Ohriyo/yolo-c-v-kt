package com.yoloc.app.features.scans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/** Fraction of the row width past which a released swipe deletes (RN: COMMIT_FRACTION). */
private const val COMMIT_FRACTION = 0.4f

/**
 * RN: features/scans/SwipeToDelete.tsx, on Material 3's SwipeToDismissBox. Swipe left to delete;
 * a fast flick deletes even when short. Only clearly horizontal drags move the row, so vertical
 * scrolling and taps behave as usual. Swiping is a shortcut: rows must also offer delete through
 * long-press and an accessibility action, and the caller shows Undo.
 *
 * @param rowKey Identity of the row. The swipe resets when it changes, so a reused row never
 *   appears half-swiped for the next scan.
 */
@Composable
fun SwipeToDelete(rowKey: Any, onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = YoloCTheme.colors
    key(rowKey) {
        val state = rememberSwipeToDismissBoxState(positionalThreshold = { width -> width * COMMIT_FRACTION })
        SwipeToDismissBox(
            state = state,
            modifier = modifier,
            enableDismissFromStartToEnd = false,
            onDismiss = { onDelete() },
            backgroundContent = {
                // Brand inverse colors, not a status color: deleting is not a detection status
                // (CLAUDE.md 4.3). Hidden from screen readers; they delete through the row's action.
                Row(
                    Modifier
                        .fillMaxSize()
                        .clearAndSetSemantics {}
                        .background(colors.inverse.bg, RoundedCornerShape(Radii.md))
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(IconName.Trash2, size = 22.dp, color = colors.inverse.text)
                    AppText(stringResource(R.string.history_delete), variant = TypeVariant.Label, color = colors.inverse.text)
                }
            },
        ) { content() }
    }
}
