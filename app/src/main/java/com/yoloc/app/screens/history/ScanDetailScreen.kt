package com.yoloc.app.screens.history

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.Card
import com.yoloc.app.components.EmptyState
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.SectionHeader
import com.yoloc.app.components.Toast
import com.yoloc.app.components.ToastSlot
import com.yoloc.app.components.ToastTone
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.ScanWithDetections
import com.yoloc.app.features.scans.formatDateTime
import com.yoloc.app.features.scans.ui.ReportDetection
import com.yoloc.app.features.scans.ui.ScanReport
import com.yoloc.app.screens.appContainer
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.theme.YoloCType
import com.yoloc.app.util.toFixed
import kotlin.math.roundToInt

/**
 * RN: app/history/[id].tsx (CLAUDE.md 3.2). The same report as Result for a saved scan, plus
 * notes, the scan's details, and delete with Undo (offered on the screen the user returns to).
 *
 * @param onBack RN: `canGoBack() ? back() : navigate('/history')`.
 */
@Composable
fun ScanDetailScreen(onBack: () -> Unit, vm: ScanDetailViewModel = viewModel(factory = ScanDetailViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refreshNow()
        onPauseOrDispose {}
    }
    LaunchedEffect(s.closed) { if (s.closed) onBack() }

    when (val load = s.load) {
        ScanDetailViewModel.Load.Loading -> ScreenContainer(scroll = false) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(36.dp), color = YoloCTheme.colors.icon)
            }
        }
        ScanDetailViewModel.Load.Missing, ScanDetailViewModel.Load.Error -> ScreenContainer(scroll = false) {
            val missing = load == ScanDetailViewModel.Load.Missing
            ErrorState(
                title = if (missing) stringResource(R.string.scan_detail_not_found_title) else stringResource(R.string.error_default_title),
                message = stringResource(if (missing) R.string.scan_detail_not_found_body else R.string.scan_detail_load_failed),
                onRetry = if (missing) null else vm::retry,
                backLabel = stringResource(R.string.scan_detail_back_to_history),
                onBack = onBack,
            )
        }
        is ScanDetailViewModel.Load.Ready -> Ready(load.scan, s, vm)
    }

    if (s.confirmingDelete) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { AppText(stringResource(R.string.scan_detail_delete_title), variant = TypeVariant.Title) },
            text = { AppText(stringResource(R.string.scan_detail_delete_body)) },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { AppText(stringResource(R.string.scan_detail_delete), variant = TypeVariant.Label, color = YoloCTheme.colors.link) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { AppText(stringResource(R.string.scan_detail_cancel), variant = TypeVariant.Label, color = YoloCTheme.colors.link) } },
            containerColor = YoloCTheme.colors.surfaceRaised,
        )
    }
}

@Composable
private fun Ready(scan: ScanWithDetections, s: ScanDetailViewModel.State, vm: ScanDetailViewModel) {
    val summary = scan.scan
    val modelClasses = appContainer().modelConfig.classes
    Box(Modifier.fillMaxSize()) {
        ScreenContainer {
            AppText(
                stringResource(R.string.scan_detail_saved_on, formatDateTime(summary.createdAt, s.now, vm.dayFormat)),
                variant = TypeVariant.Caption,
                tone = Tone.Muted,
            )

            ScanReport(
                image = vm.imageFile(scan),
                imageWidth = summary.imageWidth,
                imageHeight = summary.imageHeight,
                detections = remember(scan) { scan.detections.map { ReportDetection(it.classId, it.correctedClassId, it.confidence, it.x, it.y, it.w, it.h) } },
                verdict = summary.verdict,
                totalPods = summary.totalPods,
                lowConfidence = summary.lowConfidence,
                classes = s.classes,
                modelClasses = modelClasses,
                onCorrect = vm::correct,
                noPods = {
                    EmptyState(IconName.Search, stringResource(R.string.verdict_no_pod_title), body = stringResource(R.string.scan_detail_no_pod_body))
                },
            )

            NotesEditor(s.notes, onText = vm::setNotes, onSave = vm::saveNotes)

            SectionHeader(stringResource(R.string.scan_detail_details_title))
            Card {
                Detail(stringResource(R.string.scan_detail_plot), summary.plotName ?: stringResource(R.string.history_no_plot))
                Detail(
                    stringResource(R.string.scan_detail_source),
                    stringResource(if (summary.source == ScanSource.Camera) R.string.scan_detail_source_camera else R.string.scan_detail_source_gallery),
                )
                Detail(stringResource(R.string.scan_detail_model), summary.modelVersionId)
                summary.inferenceMs?.let { ms ->
                    Detail(stringResource(R.string.scan_detail_analysis_time), stringResource(R.string.scan_detail_analysis_seconds, toFixed(ms / 1000.0, 1)))
                }
                val lat = summary.latitude
                val lon = summary.longitude
                if (lat != null && lon != null) {
                    Detail(stringResource(R.string.scan_detail_location), "${toFixed(lat, 5)}, ${toFixed(lon, 5)}")
                }
                Detail(stringResource(R.string.scan_detail_threshold), "${(summary.confThreshold * 100).roundToInt()}%")
            }

            YButton(
                stringResource(R.string.scan_detail_delete_scan),
                vm::askDelete,
                variant = ButtonVariant.Secondary,
                icon = IconName.Trash2,
                fullWidth = true,
                loading = s.deleting,
            )
        }

        ToastSlot(s.toast, Modifier.align(Alignment.BottomCenter).padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl)) { toast ->
            Toast(
                stringResource(
                    when (toast) {
                        ScanDetailViewModel.Toast.Saved -> R.string.result_saved
                        ScanDetailViewModel.Toast.NotesSaved -> R.string.scan_detail_notes_saved
                        ScanDetailViewModel.Toast.CorrectionFailed -> R.string.scan_detail_correction_failed
                        ScanDetailViewModel.Toast.DeleteFailed -> R.string.scan_detail_delete_failed
                    },
                ),
                tone = if (toast.success) ToastTone.Success else ToastTone.Error,
            )
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppText(label, tone = Tone.Muted)
        AppText(value, Modifier.weight(1f), variant = TypeVariant.BodyStrong, maxLines = 2, textAlign = TextAlign.End)
    }
}

/** RN: NotesEditor. Notes with an explicit Save, shown only once something changed. */
@Composable
private fun NotesEditor(notes: ScanDetailViewModel.Notes, onText: (String) -> Unit, onSave: () -> Unit) {
    val colors = YoloCTheme.colors
    val label = stringResource(R.string.scan_detail_notes_label)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.scan_detail_notes_title))
        val shape = RoundedCornerShape(Radii.md)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .background(colors.surfaceRaised, shape)
                .border(1.5.dp, if (notes.dirty) colors.focus else colors.border, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            if (notes.text.isEmpty()) AppText(stringResource(R.string.scan_detail_notes_placeholder), Modifier.clearAndSetSemantics {}, tone = Tone.Muted)
            BasicTextField(
                value = notes.text,
                onValueChange = onText,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                textStyle = YoloCType.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.focus),
            )
        }
        if (notes.failed) {
            AppText(stringResource(R.string.scan_detail_notes_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (notes.dirty) {
            YButton(stringResource(R.string.scan_detail_save_notes), onSave, icon = IconName.Check, fullWidth = true, loading = notes.saving)
        }
    }
}
