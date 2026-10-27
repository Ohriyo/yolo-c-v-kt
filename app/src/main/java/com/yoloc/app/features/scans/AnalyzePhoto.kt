package com.yoloc.app.features.scans

import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.features.detection.DetectionErrorKind
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.detection.ModelLoader
import com.yoloc.app.features.detection.Thresholds
import com.yoloc.app.features.detection.VerdictInput
import com.yoloc.app.features.detection.inStage
import com.yoloc.app.features.detection.runDetection
import com.yoloc.app.features.detection.summarizeDetections
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

/**
 * RN: analyzePhoto.ts. Runs on-device detection on an unsaved photo and builds the Result
 * screen's data. Thresholds come from the user's settings (seeded from model.config.json) and
 * class severities from the classes table. Writes nothing. Throws DetectionError
 * (see describeDetectionError).
 */
class AnalyzePhoto(
    private val settings: SettingsRepository,
    private val classes: ClassRepository,
    private val loader: ModelLoader,
    private val config: ModelConfig,
) {
    suspend operator fun invoke(photo: PendingPhoto): ScanResult {
        val (s, severities) = inStage(DetectionErrorKind.Model) {
            coroutineScope {
                val s = async { settings.getSettings() }
                val c = async { classes.listClasses() }
                s.await() to c.await().associate { it.id to it.severity }
            }
        }
        val loaded = loader.loadCpu()
        val thresholds = Thresholds(s.confThreshold, s.iouThreshold)
        val run = runDetection(loaded, photo.file, config.letterboxPadValue, thresholds)

        // RN falls back to severity 0 (healthy) for a class the table lacks. That can only happen
        // if the model and the seeded classes disagree, and a screening aid must never report a
        // false all-clear, so it fails here instead (a known difference from RN).
        inStage(DetectionErrorKind.Model) {
            val unknown = run.detections.firstOrNull { it.classId !in severities }
            check(unknown == null) { "Model class ${unknown?.classId} is missing from the classes table" }
        }
        val summary = summarizeDetections(
            run.detections.map { VerdictInput(severities.getValue(it.classId), it.confidence) },
            config.lowConfidenceThreshold,
        )
        return ScanResult(
            photo = photo,
            imageWidth = run.imageWidth,
            imageHeight = run.imageHeight,
            detections = run.detections.map(ResultDetection::from),
            verdict = summary.verdict,
            totalPods = summary.totalPods,
            lowConfidence = summary.lowConfidence,
            modelVersionId = s.activeModelId.ifEmpty { config.id },
            confThreshold = thresholds.confThreshold,
            iouThreshold = thresholds.iouThreshold,
            inferenceMs = run.timings.inferenceMs.roundToInt(),
            totalMs = run.timings.totalMs.roundToInt(),
            analyzedAt = System.currentTimeMillis(),
        )
    }
}
