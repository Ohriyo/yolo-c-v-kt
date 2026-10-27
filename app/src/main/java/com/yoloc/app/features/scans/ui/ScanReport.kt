package com.yoloc.app.features.scans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.BoxDetection
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.Card
import com.yoloc.app.components.DetectionListItem
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ImageWithBoxes
import com.yoloc.app.components.SectionHeader
import com.yoloc.app.components.Tone
import com.yoloc.app.components.VerdictLine
import com.yoloc.app.components.YButton
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.verdictTitle
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TypeVariant

/** RN: ReportDetection. A pod as both screens hold it: the model's class plus the user's correction. */
data class ReportDetection(
    val classId: Int,
    val correctedClassId: Int?,
    val confidence: Double,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
) {
    val effectiveClassId: Int get() = correctedClassId ?: classId
}

/** RN: classView's result. */
data class ClassView(
    val label: String,
    val status: StatusKey,
    val severity: Int,
    val summary: String?,
    val actions: List<String>,
)

/**
 * RN: classView. Display data for a class id; falls back to the model's raw class key (then
 * "Class n") while the classes table loads.
 */
fun classView(classes: Map<Int, DetectionClass>?, classId: Int, modelClasses: List<String>): ClassView {
    val c = classes?.get(classId)
    return ClassView(
        label = c?.displayName ?: modelClasses.getOrNull(classId) ?: "Class $classId",
        status = StatusKey.fromToken(c?.colorToken),
        severity = c?.severity ?: 0,
        summary = c?.summary,
        actions = c?.recommendedActions.orEmpty(),
    )
}

/** One pod row in the report. `id` is its position, as RN's String(i). */
data class ReportPod(val id: String, val index: Int, val detection: ReportDetection, val view: ClassView, val corrected: Boolean)

/** The pods, how many share the most severe class (for the verdict sentence), and that class's advice. */
data class ReportSummary(val pods: List<ReportPod>, val countAtVerdict: Int, val advice: ClassView?)

/** RN: the pods / maxSeverity / atVerdict / advice computed at the top of ScanReport. */
fun summarizeReport(detections: List<ReportDetection>, classes: Map<Int, DetectionClass>?, modelClasses: List<String>): ReportSummary {
    val pods = detections.mapIndexed { i, d ->
        ReportPod(
            id = i.toString(),
            index = i,
            detection = d,
            view = classView(classes, d.effectiveClassId, modelClasses),
            corrected = d.correctedClassId != null && d.correctedClassId != d.classId,
        )
    }
    val maxSeverity = pods.maxOfOrNull { it.view.severity } ?: -1
    val atVerdict = pods.filter { it.view.severity == maxSeverity }
    return ReportSummary(pods, atVerdict.size, atVerdict.firstOrNull()?.view)
}

/**
 * RN: features/scans/ScanReport.tsx. The scan result layout shared by Result (unsaved) and Scan
 * Detail (saved), CLAUDE.md 3.4: annotated photo, verdict line with counts, per-pod list (tap to
 * highlight, correct a label) and the advice card. Class names, colors and advice come from the
 * classes table. Emits its blocks into the screen's column, like RN's fragment.
 *
 * @param onCorrect Records the user's label for pod `index`. Null hides "Correct label".
 * @param noPods Shown under the photo when no pods were found.
 */
@Composable
fun ScanReport(
    image: Any?,
    imageWidth: Int,
    imageHeight: Int,
    detections: List<ReportDetection>,
    verdict: Verdict,
    totalPods: Int,
    lowConfidence: Boolean,
    classes: Map<Int, DetectionClass>?,
    modelClasses: List<String>,
    onCorrect: ((index: Int, classId: Int) -> Unit)?,
    noPods: @Composable () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var correcting by rememberSaveable { mutableStateOf<Int?>(null) }
    val report = summarizeReport(detections, classes, modelClasses)
    val toggle = { id: String -> selectedId = if (selectedId == id) null else id }
    val podsFound = pluralStringResource(R.plurals.result_pods_found, totalPods, totalPods)

    ImageWithBoxes(
        model = image,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        detections = report.pods.map { p ->
            BoxDetection(p.id, p.view.status, p.view.label, p.detection.confidence, p.detection.x, p.detection.y, p.detection.w, p.detection.h)
        },
        selectedId = selectedId,
        onSelect = toggle,
        accessibilityLabel = podsFound,
    )

    if (report.pods.isEmpty()) {
        noPods()
    } else {
        VerdictLine(
            verdict = verdict,
            title = verdictTitle(LocalResources.current, verdict, totalPods, report.countAtVerdict),
            detail = stringResource(R.string.result_screening_note),
            lowConfidence = lowConfidence,
        )
        SectionHeader(stringResource(R.string.result_detections_title), subtitle = podsFound)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (p in report.pods) {
                key(p.id) {
                    DetectionListItem(
                        index = p.index + 1,
                        status = p.view.status,
                        label = p.view.label,
                        confidence = p.detection.confidence,
                        selected = selectedId == p.id,
                        correctedFrom = if (p.corrected) classView(classes, p.detection.classId, modelClasses).label else null,
                        onClick = { toggle(p.id) },
                    )
                    if (selectedId == p.id && onCorrect != null) {
                        YButton(
                            stringResource(R.string.result_correct_label),
                            { correcting = p.index },
                            variant = ButtonVariant.Ghost,
                            icon = IconName.Edit3,
                            enabled = classes != null,
                        )
                    }
                }
            }
        }
        if (selectedId == null) {
            AppText(stringResource(R.string.result_tap_pod_hint), variant = TypeVariant.Caption, tone = Tone.Muted)
        }
        val advice = report.advice
        if (advice != null) {
            Card {
                AppText(stringResource(R.string.result_advice_title), variant = TypeVariant.Title)
                if (advice.summary != null) AppText(advice.summary)
                for (action in advice.actions) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        AppText("•")
                        AppText(action, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    val index = correcting
    val pod = index?.let { detections.getOrNull(it) }
    if (classes != null && pod != null && onCorrect != null) {
        CorrectLabelSheet(
            classes = classes.values.sortedBy { it.id },
            modelClassId = pod.classId,
            currentClassId = pod.effectiveClassId,
            onSelect = { classId ->
                onCorrect(index, classId)
                correcting = null
            },
            onClose = { correcting = null },
        )
    }
}
