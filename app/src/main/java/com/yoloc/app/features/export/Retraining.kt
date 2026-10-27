package com.yoloc.app.features.export

import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.ScanFilters
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSummary
import com.yoloc.app.db.repositories.ScanWithDetections
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.util.isoString
import com.yoloc.app.util.toJsString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.OutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * RN: export/retraining.ts. "Export for retraining" (CLAUDE.md part 5): one zip of saved photos plus
 * labels a training pipeline can read. Labels are the model's detections with the user's
 * corrections applied; only corrected labels were checked by a person, which the manifest and
 * README make explicit.
 *
 * Layout (Ultralytics YOLO):
 *   images/<scanId>.jpg   original saved photo
 *   labels/<scanId>.txt   one line per pod: "<class> <cx> <cy> <w> <h>", normalized 0–1
 *   data.yaml             class names in model index order
 *   manifest.json         per scan: model, thresholds, every detection with model class,
 *                         correction, confidence; per export: scope, counts, app/model versions
 *   README.txt            how to use it, and what was and was not reviewed by a person
 */

/** `key` is what the manifest, README and file name say. */
enum class ExportScope(val key: String) {
    Corrected("corrected"),
    Early("early"),
    All("all"),
}

data class ExportSummary(
    val scans: Int,
    /** Approximate zip size: photos are stored as-is; text adds a little. */
    val bytes: Long,
)

data class ExportResult(
    val scans: Int = 0,
    val pods: Int = 0,
    val correctedPods: Int = 0,
    /** Scans whose photo file was missing, skipped. */
    val skipped: Int = 0,
)

private const val PAGE = 100

/** Severity 1 is "early stage" in the classes table, whatever its key in a future model. */
private suspend fun scopeFilters(classes: ClassRepository, scope: ExportScope): ScanFilters = when (scope) {
    ExportScope.Corrected -> ScanFilters(corrected = true)
    ExportScope.Early -> ScanFilters(classIds = classes.listClasses().filter { it.severity == 1 }.map { it.id })
    ExportScope.All -> ScanFilters()
}

/** Every scan in scope (summaries only), newest first, read a page at a time. */
private suspend fun scansInScope(scans: ScanRepository, classes: ClassRepository, scope: ExportScope): List<ScanSummary> {
    val filters = scopeFilters(classes, scope)
    val all = mutableListOf<ScanSummary>()
    var offset = 0
    while (true) {
        val page = scans.listScans(filters.copy(limit = PAGE, offset = offset))
        all += page
        if (page.size < PAGE) return all
        offset += PAGE
    }
}

/** What the export would contain, for the confirmation sheet (count and size). */
suspend fun summarizeExport(
    scans: ScanRepository,
    classes: ClassRepository,
    files: ScanFileStore,
    scope: ExportScope,
): ExportSummary {
    val inScope = scansInScope(scans, classes, scope)
    val bytes = withContext(Dispatchers.IO) { inScope.sumOf { files.sizeOf(it.imagePath) + 512 } }
    return ExportSummary(inScope.size, if (inScope.isNotEmpty()) bytes + 4096 else 0)
}

/** Locale.ROOT: on a comma-decimal phone "%.6f" would write 0,250000 and break every label. */
private fun fixed(v: Double) = "%.6f".format(Locale.ROOT, v)

/** YOLO label lines for one scan: effective (corrected) class, box center and size. */
fun yoloLabels(scan: ScanWithDetections): String = scan.detections.joinToString("\n") { d ->
    listOf(d.effectiveClassId.toString(), fixed(d.x + d.w / 2), fixed(d.y + d.h / 2), fixed(d.w), fixed(d.h))
        .joinToString(" ")
}

fun dataYaml(config: ModelConfig): String = (
    listOf(
        "# YOLO-C retraining export, classes from model ${config.id}",
        "path: .",
        "train: images",
        "val: images",
        "names:",
    ) + config.classes.mapIndexed { i, name -> "  $i: $name" } + ""
    ).joinToString("\n")

fun readme(scope: ExportScope, result: ExportResult, exportedAt: String): String = listOf(
    "YOLO-C export for retraining",
    "Exported $exportedAt. Scope: ${scope.key}. Scans: ${result.scans}. Pods: ${result.pods}.",
    "Pods whose label a person corrected in the app: ${result.correctedPods}.",
    "",
    "images/ and labels/ follow the Ultralytics YOLO layout; data.yaml lists the classes.",
    "Each label line is: class center_x center_y width height (all 0-1).",
    "",
    "IMPORTANT: a label is the model's own prediction unless manifest.json shows a",
    "\"correctedClassId\" for that pod. Uncorrected labels were not checked by a person, and the",
    "model misses some pods entirely (no label at all). Review every image in a labeling tool",
    "(for example Label Studio or Roboflow) before training on it.",
    "",
    "Location is included in manifest.json only for scans saved with location turned on.",
    "",
).joinToString("\n")

/** JSON.stringify(value, null, 2). */
@OptIn(ExperimentalSerializationApi::class)
private val manifestJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** A number as JSON.stringify writes it: 1 not 1.0 (Kotlin's JsonPrimitive(1.0) prints 1.0). */
@OptIn(ExperimentalSerializationApi::class)
private fun jsNumber(v: Double): JsonElement = JsonUnquotedLiteral(v.toJsString())

private fun scanManifest(scan: ScanWithDetections): JsonObject {
    val s = scan.scan
    return buildJsonObject {
        put("id", s.id)
        put("image", "images/${s.id}.jpg")
        put("labels", "labels/${s.id}.txt")
        put("createdAt", isoString(s.createdAt))
        put("imageWidth", s.imageWidth)
        put("imageHeight", s.imageHeight)
        put("source", s.source.key)
        put("modelVersionId", s.modelVersionId)
        put("confThreshold", jsNumber(s.confThreshold))
        put("iouThreshold", jsNumber(s.iouThreshold))
        put("verdict", s.verdict.key)
        put("plot", s.plotName)
        put("notes", s.notes)
        put(
            "location",
            if (s.latitude != null && s.longitude != null) {
                buildJsonObject {
                    put("latitude", jsNumber(s.latitude))
                    put("longitude", jsNumber(s.longitude))
                }
            } else {
                JsonNull
            },
        )
        put(
            "detections",
            buildJsonArray {
                for (d in scan.detections) {
                    add(
                        buildJsonObject {
                            put("modelClassId", d.classId)
                            put("correctedClassId", d.correctedClassId)
                            put("correctedAt", d.correctedAt?.let { isoString(it) })
                            put("confidence", jsNumber(d.confidence))
                            put(
                                "box",
                                buildJsonObject {
                                    put("x", jsNumber(d.x))
                                    put("y", jsNumber(d.y))
                                    put("w", jsNumber(d.w))
                                    put("h", jsNumber(d.h))
                                },
                            )
                        },
                    )
                }
            },
        )
    }
}

/**
 * Key order follows RN's `{ ..., ...result, scans: manifestScans }`: the spread puts the counts in
 * place and `scans` then replaces the count with the list, keeping its position.
 */
internal fun manifestText(
    scope: ExportScope,
    config: ModelConfig,
    appVersion: String,
    exportedAt: String,
    result: ExportResult,
    scans: List<JsonObject>,
): String = manifestJson.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put("format", "yolo-c-retraining-export/1")
        put("exportedAt", exportedAt)
        put("appVersion", appVersion)
        put("scope", scope.key)
        put("classes", JsonArray(config.classes.map { JsonPrimitive(it) }))
        put("boxConvention", "normalized 0-1, top-left origin, relative to the original image")
        put("scans", JsonArray(scans))
        put("pods", result.pods)
        put("correctedPods", result.correctedPods)
        put("skipped", result.skipped)
    },
)

/**
 * Streams the export into `out` and finishes the zip; closing `out` is the caller's. Photos are
 * stored without recompression (they are already JPEG; RN: ZipPassThrough), text files are
 * deflated at level 6. Only one photo is in memory at a time, so hundreds of scans do not exhaust
 * the phone's memory. Scans whose photo is missing are skipped and counted.
 */
suspend fun writeRetrainingZip(
    scans: ScanRepository,
    classes: ClassRepository,
    files: ScanFileStore,
    out: OutputStream,
    scope: ExportScope,
    config: ModelConfig,
    appVersion: String,
    now: Long = System.currentTimeMillis(),
): ExportResult = withContext(Dispatchers.IO) {
    val exportedAt = isoString(now)
    val zip = ZipOutputStream(out)
    zip.setLevel(6)
    fun addText(name: String, text: String) {
        zip.putNextEntry(ZipEntry(name)) // DEFLATED, the stream's default method
        zip.write(text.encodeToByteArray())
        zip.closeEntry()
    }

    var result = ExportResult()
    val manifestScans = mutableListOf<JsonObject>()

    for (summary in scansInScope(scans, classes, scope)) {
        val scan = scans.getScan(summary.id) ?: continue
        val photo = try {
            files.readBytes(scan.scan.imagePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            result = result.copy(skipped = result.skipped + 1)
            continue
        }
        // STORED needs size and CRC before the data, which is why the photo is read whole.
        zip.putNextEntry(
            ZipEntry("images/${scan.scan.id}.jpg").apply {
                method = ZipEntry.STORED
                size = photo.size.toLong()
                compressedSize = photo.size.toLong()
                crc = CRC32().apply { update(photo) }.value
            },
        )
        zip.write(photo)
        zip.closeEntry()
        addText("labels/${scan.scan.id}.txt", yoloLabels(scan))

        result = result.copy(
            scans = result.scans + 1,
            pods = result.pods + scan.detections.size,
            correctedPods = result.correctedPods + scan.detections.count { it.correctedClassId != null },
        )
        manifestScans += scanManifest(scan)
    }

    addText("data.yaml", dataYaml(config))
    addText("manifest.json", manifestText(scope, config, appVersion, exportedAt, result, manifestScans))
    addText("README.txt", readme(scope, result, exportedAt))
    zip.finish()
    result
}
