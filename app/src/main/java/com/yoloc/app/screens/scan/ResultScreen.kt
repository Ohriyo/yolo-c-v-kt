package com.yoloc.app.screens.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.EmptyState
import com.yoloc.app.components.EmptyTip
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.StateAction
import com.yoloc.app.components.YButton
import com.yoloc.app.features.scans.ui.ReportDetection
import com.yoloc.app.features.scans.ui.SaveScanSheet
import com.yoloc.app.features.scans.ui.ScanReport
import com.yoloc.app.screens.appContainer
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: app/scan/result.tsx (CLAUDE.md 3.4). The photo carries the result: tap a pod in the photo
 * or the list to highlight it, and correct its label if the model got it wrong. Save opens the
 * plot sheet and writes everything atomically; leaving with unsaved pods asks first (3.2).
 *
 * The ViewModel comes from the NavHost, whose header back arrow asks the same question
 * ([ResultViewModel.onLeaveRequested]) as Android back here.
 *
 * @param onDiscarded RN: router.dismissTo('/scan/camera').
 * @param onSaved RN: dismissTo('/history') then push /history/[id]?saved=1.
 */
@Composable
fun ResultScreen(
    vm: ResultViewModel,
    onDiscarded: () -> Unit,
    onSaved: (scanId: String) -> Unit,
    onManagePlots: () -> Unit,
    onBackToCamera: () -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    // RN: usePreventRemove(guarded, ...). Also blocks back while a save writes (onLeaveRequested
    // ignores it then).
    BackHandler(enabled = s.guarded) { vm.onLeaveRequested() }

    // Navigate only once the guard is off, so the screen's own exit is never blocked.
    LaunchedEffect(s.exit) {
        when (val exit = s.exit) {
            null -> return@LaunchedEffect
            is ResultViewModel.Exit.Saved -> onSaved(exit.scanId)
            ResultViewModel.Exit.Discard -> onDiscarded()
        }
        vm.exited()
    }

    val result = s.result
    if (result == null) {
        ScreenContainer(scroll = false) {
            ErrorState(
                title = stringResource(R.string.result_missing_title),
                message = stringResource(R.string.result_missing_body),
                backLabel = stringResource(R.string.preview_back_to_scan),
                onBack = onBackToCamera,
            )
        }
        return
    }

    val modelClasses = appContainer().modelConfig.classes
    val detections = remember(result) {
        result.detections.map { ReportDetection(it.classId, it.correctedClassId, it.confidence, it.x, it.y, it.w, it.h) }
    }
    ScreenContainer(
        footer = if (s.hasPods) {
            {
                YButton(stringResource(R.string.result_save_scan), vm::openSave, icon = IconName.Save, size = ButtonSize.Lg, fullWidth = true)
                YButton(stringResource(R.string.result_discard), vm::discard, variant = ButtonVariant.Secondary, icon = IconName.Trash2, fullWidth = true)
            }
        } else {
            null
        },
    ) {
        ScanReport(
            image = result.photo.file,
            imageWidth = result.imageWidth,
            imageHeight = result.imageHeight,
            detections = detections,
            verdict = result.verdict,
            totalPods = result.totalPods,
            lowConfidence = result.lowConfidence,
            classes = s.classes,
            modelClasses = modelClasses,
            onCorrect = vm::correct,
            noPods = {
                EmptyState(
                    IconName.Search,
                    stringResource(R.string.verdict_no_pod_title),
                    body = stringResource(R.string.result_no_pod_body),
                    tips = listOf(
                        EmptyTip(IconName.Maximize, stringResource(R.string.result_no_pod_tip_fill)),
                        EmptyTip(IconName.Sun, stringResource(R.string.result_no_pod_tip_shade)),
                        EmptyTip(IconName.Camera, stringResource(R.string.result_no_pod_tip_steady)),
                    ),
                    action = StateAction(stringResource(R.string.result_retake), vm::discard, IconName.RotateCcw),
                )
            },
        )
    }

    SaveScanSheet(
        visible = s.saveOpen,
        saving = s.saving,
        error = s.saveError,
        plotError = s.plotError,
        plots = s.plots,
        onAddPlot = vm::addPlot,
        onSave = vm::save,
        onClose = vm::closeSave,
        onManagePlots = {
            vm.closeSave()
            onManagePlots()
        },
    )

    if (s.confirmLeave) {
        val link = YoloCTheme.colors.link
        // RN: Alert.alert with Stay (cancel), Discard (destructive) and Save.
        AlertDialog(
            onDismissRequest = vm::stay,
            title = { AppText(stringResource(R.string.result_leave_title), variant = TypeVariant.Title) },
            text = { AppText(stringResource(R.string.result_leave_body)) },
            dismissButton = {
                TextButton(onClick = vm::stay) { AppText(stringResource(R.string.result_stay), variant = TypeVariant.Label, color = link) }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = vm::discard) { AppText(stringResource(R.string.result_discard), variant = TypeVariant.Label, color = link) }
                    TextButton(onClick = vm::openSave) { AppText(stringResource(R.string.result_save), variant = TypeVariant.Label, color = link) }
                }
            },
            containerColor = YoloCTheme.colors.surfaceRaised,
        )
    }
}
