package com.yoloc.app.features.scans

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yoloc.app.R
import com.yoloc.app.components.TextAction
import com.yoloc.app.components.Toast
import com.yoloc.app.components.ToastSlot
import com.yoloc.app.components.ToastTone
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface UndoBar {
    data class Offer(val token: Int) : UndoBar
    data object Failed : UndoBar
}

/**
 * RN: features/scans/UndoDeleteSnackbar.tsx. "Scan deleted · Undo", pinned above the tab bar on
 * the screen that shows it (only the focused screen does, as RN's useIsFocused). Driven by the
 * scan library, so a delete on Scan Detail still offers Undo on the screen the user returns to.
 *
 * @param scope Where the restore runs: the app scope, so leaving the screen mid-restore cannot
 *   cancel it after the offer has already been taken.
 */
@Composable
fun UndoDeleteSnackbar(library: ScanLibrary, scope: CoroutineScope, modifier: Modifier = Modifier) {
    val pending by library.pendingUndo.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(pending) {
        val offer = pending ?: return@LaunchedEffect
        delay(ScanLibrary.UNDO_WINDOW_MS)
        library.dismissUndo(offer.token)
    }
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        delay(ScanLibrary.UNDO_WINDOW_MS)
        failed = false
    }

    val bar = pending?.let { UndoBar.Offer(it.token) } ?: if (failed) UndoBar.Failed else null
    val undo = stringResource(R.string.history_undo)
    ToastSlot(bar, modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.md)) { shown ->
        when (shown) {
            // A new delete replaces the bar, so it appears (and animates in) again.
            is UndoBar.Offer -> key(shown.token) {
                Toast(
                    stringResource(R.string.history_deleted),
                    action = TextAction(undo) {
                        scope.launch {
                            try {
                                library.undoDelete()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w("undoDelete", "Undo failed", e)
                                failed = true
                            }
                        }
                    },
                )
            }
            UndoBar.Failed -> Toast(stringResource(R.string.history_undo_failed), tone = ToastTone.Error)
        }
    }
}
