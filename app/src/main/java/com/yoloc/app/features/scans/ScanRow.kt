package com.yoloc.app.features.scans

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.Tone
import com.yoloc.app.components.VerdictChip
import com.yoloc.app.components.rememberPress
import com.yoloc.app.components.verdictShortName
import com.yoloc.app.db.repositories.ScanSummary
import com.yoloc.app.screens.appContainer
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

val THUMB_SIZE = 72.dp

/** RN: scanSubtitle. Second line of a row: "3 pods found · North block". */
@Composable
fun scanSubtitle(scan: ScanSummary): String {
    val pods = if (scan.totalPods > 0) {
        pluralStringResource(R.plurals.result_pods_found, scan.totalPods, scan.totalPods)
    } else {
        stringResource(R.string.history_no_pods_short)
    }
    return listOfNotNull(pods, scan.plotName?.takeIf { it.isNotEmpty() }).joinToString(" · ")
}

/**
 * RN: features/scans/ScanRow.tsx. One saved scan: thumbnail, verdict chip, pods and plot, time,
 * and the first line of the notes. One spoken label for the whole row.
 *
 * @param time Time shown on the row. History has day headers, so it passes the time only.
 * @param onLongPress With [longPressLabel], opens the row's options (History).
 * @param onDelete Adds the screen-reader "Delete" action; History also wraps the row in
 *   [SwipeToDelete].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScanRow(
    scan: ScanSummary,
    time: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((String) -> Unit)? = null,
    longPressLabel: String? = null,
    onDelete: ((String) -> Unit)? = null,
) {
    val colors = YoloCTheme.colors
    val subtitle = scanSubtitle(scan)
    val notes = scan.notes?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    val uncertain = stringResource(R.string.history_uncertain)
    val a11y = listOfNotNull(
        verdictShortName(scan.verdict),
        subtitle,
        time,
        uncertain.takeIf { scan.lowConfidence },
        notes?.let { stringResource(R.string.history_notes_a11y, it) },
    ).joinToString(", ")
    val deleteLabel = stringResource(R.string.history_delete)
    val press = rememberPress()

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(if (press.pressed.value) colors.surfaceRaised else colors.surface)
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.Button
                onClick { onOpen(scan.id); true }
                if (onLongPress != null) onLongClick(longPressLabel) { onLongPress(scan.id); true }
                if (onDelete != null) customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(scan.id); true })
            }
            .combinedClickable(
                interactionSource = press.source,
                indication = null,
                onLongClick = onLongPress?.let { { it(scan.id) } },
                onClick = { onOpen(scan.id) },
            )
            .heightIn(min = THUMB_SIZE + Spacing.sm * 2)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(scan)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VerdictChip(scan.verdict)
                // Long times ("Yesterday" on Home) shrink and ellipsize instead of overflowing.
                AppText(
                    time,
                    Modifier.weight(1f),
                    variant = TypeVariant.Caption,
                    tone = Tone.Muted,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                )
            }
            AppText(subtitle, variant = TypeVariant.BodyStrong, maxLines = 1)
            if (scan.lowConfidence || notes != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    if (scan.lowConfidence) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(IconName.Sun, size = 14.dp, color = colors.icon)
                            AppText(uncertain, variant = TypeVariant.Caption, tone = Tone.Muted)
                        }
                    }
                    if (notes != null) AppText(notes, Modifier.weight(1f), variant = TypeVariant.Caption, tone = Tone.Muted, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The list thumbnail. Uses the small saved thumbnail, or the full photo decoded at view size for
 * scans saved before thumbnails existed. A missing file shows an icon.
 */
@Composable
private fun Thumbnail(scan: ScanSummary) {
    val colors = YoloCTheme.colors
    val files = appContainer().files
    val file = remember(scan.id, scan.thumbnailPath, scan.imagePath) { files.fileFor(scan.thumbnailPath ?: scan.imagePath) }
    var failed by remember(file) { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radii.md)
    Box(
        Modifier
            .size(THUMB_SIZE)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(Dp.Hairline, colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Icon(IconName.Image, size = 24.dp, color = colors.icon)
        } else {
            AsyncImage(
                model = file,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        }
    }
}
