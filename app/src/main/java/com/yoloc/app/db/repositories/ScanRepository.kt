package com.yoloc.app.db.repositories

import androidx.room3.RoomRawQuery
import androidx.room3.withWriteTransaction
import com.yoloc.app.db.AppDatabaseHolder
import com.yoloc.app.db.DetectionEntity
import com.yoloc.app.db.ScanEntity
import com.yoloc.app.db.dao.SCAN_COLUMNS
import com.yoloc.app.db.dao.ScanRow
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.detection.VerdictInput
import com.yoloc.app.features.detection.summarizeDetections
import com.yoloc.app.util.isoString
import com.yoloc.app.util.toJsString
import com.yoloc.app.util.uuid

enum class ScanSource(val key: String) {
    Camera("camera"),
    Gallery("gallery"),
    ;

    companion object {
        fun fromKey(key: String): ScanSource = entries.firstOrNull { it.key == key } ?: error("Unknown source $key")
    }
}

/** One detection to save. Box is normalized (0-1), top-left origin, relative to the original. */
data class NewDetection(
    val classId: Int,
    val confidence: Double,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    /** The user's label if they corrected the model before saving (kept for retraining). */
    val correctedClassId: Int? = null,
)

data class NewScan(
    val id: String? = null,
    val plotId: String? = null,
    val modelVersionId: String,
    /** Relative to filesDir, e.g. scans/<id>.jpg. */
    val imagePath: String,
    val thumbnailPath: String? = null,
    val imageWidth: Int,
    val imageHeight: Int,
    val source: ScanSource,
    val confThreshold: Double,
    val iouThreshold: Double,
    val inferenceMs: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val createdAt: Long? = null,
    val detections: List<NewDetection>,
)

data class ScanSummary(
    val id: String,
    val plotId: String?,
    val plotName: String?,
    val modelVersionId: String,
    val imagePath: String,
    val thumbnailPath: String?,
    val imageWidth: Int,
    val imageHeight: Int,
    val source: ScanSource,
    val confThreshold: Double,
    val iouThreshold: Double,
    val inferenceMs: Int?,
    val totalPods: Int,
    val verdict: Verdict,
    val lowConfidence: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    val createdAt: Long,
)

data class SavedDetection(
    val id: String,
    val scanId: String,
    val classId: Int,
    val confidence: Double,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val correctedClassId: Int?,
    val correctedAt: Long?,
    /** Corrected class if the user changed it, else the model's class. */
    val effectiveClassId: Int,
)

data class ScanWithDetections(val scan: ScanSummary, val detections: List<SavedDetection>)

/** Unset = any plot; None = scans without a plot; Id = one plot. */
sealed interface PlotFilter {
    data object Any : PlotFilter
    data object None : PlotFilter
    data class Id(val id: String) : PlotFilter
}

/** Position of a scan in the newest-first order (`created_at DESC, id`). */
data class ScanCursor(val createdAt: Long, val id: String)

data class ScanFilters(
    /** Match any of these verdicts. */
    val verdicts: List<Verdict>? = null,
    /** Scans with at least one pod of any of these classes (after corrections). */
    val classIds: List<Int>? = null,
    /** Only scans where the user corrected at least one pod's label (retraining export). */
    val corrected: Boolean = false,
    val plot: PlotFilter = PlotFilter.Any,
    /** Inclusive epoch-ms range on created_at. */
    val from: Long? = null,
    val to: Long? = null,
    /** Case-insensitive match on notes or plot name. */
    val search: String? = null,
    /**
     * Keyset paging: only scans that sort after this one (newest first). Unlike `offset`, pages
     * stay correct when a scan above the cursor is deleted or restored while the user scrolls.
     */
    val after: ScanCursor? = null,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0,
) {
    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

data class ScanStats(
    val totalScans: Int,
    val scansByVerdict: Map<Verdict, Int>,
    /** Pods counted by class key, after corrections. */
    val podsByClass: Map<String, Int>,
)

data class ScanImage(val id: String, val imagePath: String, val imageWidth: Int, val imageHeight: Int)

/** RN: src/db/repositories/scanRepository.ts. Same functions and rules; SQL is in ScanDao. */
class ScanRepository(private val database: AppDatabaseHolder) {

    /**
     * Inserts a scan and its detections in one transaction and derives verdict, total_pods and
     * low_confidence from the detections, so stored summaries always match the stored pods.
     * Writes nothing if any part fails. Image files are handled by features/scans/SaveScan.
     */
    suspend fun createScan(input: NewScan, lowConfidenceThreshold: Double): String {
        validateNewScan(input)
        val id = input.id ?: uuid()
        val createdAt = input.createdAt ?: System.currentTimeMillis()
        val db = database.get()
        val dao = db.scanDao()

        db.withWriteTransaction {
            val severities = dao.classSeverities().associate { it.id to it.severity }
            // Verdict follows the user's corrections, as correctDetection does after saving.
            val summary = summarizeDetections(
                input.detections.map { d ->
                    for (classId in listOfNotNull(d.classId, d.correctedClassId)) {
                        require(classId in severities) { "Unknown class id $classId" }
                    }
                    VerdictInput(severities.getValue(d.correctedClassId ?: d.classId), d.confidence)
                },
                lowConfidenceThreshold,
            )

            dao.insertScan(
                ScanEntity(
                    id = id,
                    plotId = input.plotId,
                    modelVersionId = input.modelVersionId,
                    imagePath = input.imagePath,
                    thumbnailPath = input.thumbnailPath,
                    imageWidth = input.imageWidth,
                    imageHeight = input.imageHeight,
                    source = input.source.key,
                    confThreshold = input.confThreshold,
                    iouThreshold = input.iouThreshold,
                    inferenceMs = input.inferenceMs,
                    totalPods = summary.totalPods,
                    verdict = summary.verdict.key,
                    lowConfidence = if (summary.lowConfidence) 1 else 0,
                    latitude = input.latitude,
                    longitude = input.longitude,
                    notes = input.notes,
                    createdAt = createdAt,
                ),
            )
            dao.insertDetections(
                input.detections.map { d ->
                    // A "correction" back to the model's own class is no correction.
                    val corrected = d.correctedClassId?.takeIf { it != d.classId }
                    DetectionEntity(
                        id = uuid(),
                        scanId = id,
                        classId = d.classId,
                        confidence = d.confidence,
                        x = d.x,
                        y = d.y,
                        w = d.w,
                        h = d.h,
                        correctedClassId = corrected,
                        correctedAt = if (corrected == null) null else createdAt,
                    )
                },
            )
        }
        return id
    }

    /** Newest first; soft-deleted scans are never returned. */
    suspend fun listScans(filters: ScanFilters = ScanFilters()): List<ScanSummary> {
        val (where, args) = whereClause(filters)
        filters.after?.let {
            where += "(s.created_at < ? OR (s.created_at = ? AND s.id > ?))"
            args += it.createdAt
            args += it.createdAt
            args += it.id
        }
        args += filters.limit
        args += filters.offset
        val sql = """SELECT $SCAN_COLUMNS FROM scans s LEFT JOIN plots p ON p.id = s.plot_id
            WHERE ${where.joinToString(" AND ")}
            ORDER BY s.created_at DESC, s.id
            LIMIT ? OFFSET ?"""
        return database.get().scanDao().scanRows(rawQuery(sql, args)).map { it.toSummary() }
    }

    /** How many visible scans match the filters (paging and cursor are ignored). */
    suspend fun countScans(filters: ScanFilters = ScanFilters()): Int {
        val (where, args) = whereClause(filters)
        val sql = """SELECT COUNT(*) AS n FROM scans s LEFT JOIN plots p ON p.id = s.plot_id
            WHERE ${where.joinToString(" AND ")}"""
        return database.get().scanDao().count(rawQuery(sql, args)).n
    }

    /** A scan with its detections, or null if missing or soft-deleted. */
    suspend fun getScan(id: String): ScanWithDetections? {
        val dao = database.get().scanDao()
        val row = dao.scanRow(id) ?: return null
        return ScanWithDetections(row.toSummary(), dao.detections(id).map { it.toSaved() })
    }

    /** Hides a scan from lists and stats. The row and image stay until a later purge. */
    suspend fun softDeleteScan(id: String, now: Long = System.currentTimeMillis()): Boolean =
        database.get().scanDao().softDelete(id, now) > 0

    /** Whether a row with this id exists at all (soft-deleted or not). */
    suspend fun scanExists(id: String): Boolean = database.get().scanDao().exists(id)

    /** Undo for softDeleteScan. Returns false if the scan is missing or not deleted. */
    suspend fun restoreScan(id: String): Boolean = database.get().scanDao().restore(id) > 0

    /** Replaces a scan's notes. Blank notes are stored as null. */
    suspend fun updateScanNotes(id: String, notes: String?) {
        val trimmed = notes?.trim().orEmpty()
        val changed = database.get().scanDao().updateNotes(id, trimmed.ifEmpty { null })
        if (changed == 0) throw NoSuchElementException("Scan $id not found")
    }

    /** Records a generated thumbnail (relative to filesDir). */
    suspend fun setScanThumbnail(id: String, thumbnailPath: String) =
        database.get().scanDao().setThumbnail(id, thumbnailPath)

    /** Visible scans saved before thumbnails existed, newest first, for a background backfill. */
    suspend fun listScansWithoutThumbnail(limit: Int): List<ScanImage> =
        database.get().scanDao().scansWithoutThumbnail(limit).map {
            ScanImage(it.id, it.imagePath, it.imageWidth, it.imageHeight)
        }

    /**
     * Records the user's label for a detection (kept for future training data) and recomputes the
     * scan verdict from the corrected labels. Choosing the model's own class clears the correction.
     */
    suspend fun correctDetection(detectionId: String, classId: Int, now: Long = System.currentTimeMillis()) {
        val db = database.get()
        val dao = db.scanDao()
        db.withWriteTransaction {
            val detection = dao.detectionRef(detectionId) ?: throw NoSuchElementException("Detection $detectionId not found")
            val cleared = classId == detection.classId
            dao.setCorrection(detectionId, if (cleared) null else classId, if (cleared) null else now)
            val effective = dao.effectiveSeverities(detection.scanId).map { VerdictInput(it.severity, it.confidence) }
            // Confidence is unchanged by a correction, so only the verdict can move.
            dao.setVerdict(detection.scanId, summarizeDetections(effective, 0.0).verdict.key)
        }
    }

    /** Counts for scans created at or after `since` (Home's last-30-days chips). */
    suspend fun getStats(since: Long): ScanStats {
        val dao = database.get().scanDao()
        val verdictRows = dao.verdictCounts(since)
        val classRows = dao.podsByClass(since)
        val byVerdict = Verdict.entries.associateWith { 0 }.toMutableMap()
        for (r in verdictRows) byVerdict[Verdict.fromKey(r.verdict)] = r.n
        return ScanStats(
            totalScans = verdictRows.sumOf { it.n },
            scansByVerdict = byVerdict,
            podsByClass = classRows.associate { it.key to it.n },
        )
    }

    /**
     * All visible scans as CSV, one row per scan, with pod counts per class after corrections.
     * CRLF line endings with a trailing CRLF; the UTF-8 BOM is added when the file is written.
     */
    suspend fun exportScansCsv(): String {
        val dao = database.get().scanDao()
        val classes = dao.classKeys()
        val scans = dao.allVisibleScanRows()
        val countOf = dao.podCountsByScanAndClass().associate { "${it.scanId}:${it.classId}" to it.n }

        val header = listOf("id", "created_at", "verdict", "total_pods") +
            classes.map { "${it.key}_count" } +
            listOf(
                "low_confidence", "plot", "source", "model_version", "inference_ms",
                "latitude", "longitude", "notes",
            )
        val lines = scans.map { s ->
            (
                listOf<Any?>(s.id, isoString(s.createdAt), s.verdict, s.totalPods) +
                    classes.map { countOf["${s.id}:${it.id}"] ?: 0 } +
                    listOf<Any?>(
                        s.lowConfidence, s.plotName, s.source, s.modelVersionId, s.inferenceMs,
                        s.latitude, s.longitude, s.notes,
                    )
                ).joinToString(",") { csvCell(it) }
        }
        return (listOf(header.joinToString(",")) + lines).joinToString("\r\n") + "\r\n"
    }

    /**
     * "Clear all data": deletes every scan (soft-deleted ones too), its detections and every plot,
     * for good, in one transaction. Settings and the model tables stay. Image files are removed by
     * the caller. Returns how many scans were deleted.
     */
    suspend fun clearAllScanData(): Int {
        val db = database.get()
        val dao = db.scanDao()
        return db.withWriteTransaction {
            val n = dao.countAllScans()
            dao.deleteAllDetections()
            dao.deleteAllScans()
            dao.deleteAllPlots()
            n
        }
    }
}

/** Before any storage or transaction (RN: validateNewScan). */
private fun validateNewScan(input: NewScan) {
    require(input.imagePath.isNotEmpty()) { "imagePath is required" }
    require(input.imageWidth > 0 && input.imageHeight > 0) { "Invalid image size" }
    input.detections.forEachIndexed { i, d ->
        fun inUnit(v: Double) = v.isFinite() && v >= 0 && v <= 1
        require(listOf(d.confidence, d.x, d.y, d.w, d.h).all(::inUnit) && d.x + d.w <= 1.0001 && d.y + d.h <= 1.0001) {
            "Detection $i is out of range; boxes and confidence must be normalized"
        }
    }
}

/** RN: whereClause. Returns mutable lists so listScans can add the cursor condition. */
private fun whereClause(f: ScanFilters): Pair<MutableList<String>, MutableList<Any>> {
    val where = mutableListOf("s.deleted_at IS NULL")
    val args = mutableListOf<Any>()
    f.verdicts?.takeIf { it.isNotEmpty() }?.let { v ->
        where += "s.verdict IN (${v.joinToString { "?" }})"
        args.addAll(v.map { it.key })
    }
    f.classIds?.takeIf { it.isNotEmpty() }?.let { ids ->
        where += """EXISTS (SELECT 1 FROM detections d WHERE d.scan_id = s.id
            AND COALESCE(d.corrected_class_id, d.class_id) IN (${ids.joinToString { "?" }}))"""
        args.addAll(ids)
    }
    if (f.corrected) {
        where += "EXISTS (SELECT 1 FROM detections d WHERE d.scan_id = s.id AND d.corrected_class_id IS NOT NULL)"
    }
    when (val p = f.plot) {
        PlotFilter.Any -> Unit
        PlotFilter.None -> where += "s.plot_id IS NULL"
        is PlotFilter.Id -> {
            where += "s.plot_id = ?"
            args += p.id
        }
    }
    f.from?.let {
        where += "s.created_at >= ?"
        args += it
    }
    f.to?.let {
        where += "s.created_at <= ?"
        args += it
    }
    f.search?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
        where += "(s.notes LIKE ? ESCAPE '\\' OR p.name LIKE ? ESCAPE '\\')"
        val pattern = "%" + escapeLike(s) + "%"
        args += pattern
        args += pattern
    }
    return where to args
}

/** Escapes LIKE wildcards so `%` and `_` in user text match literally (ESCAPE '\'). */
internal fun escapeLike(text: String) = text.replace(Regex("""[\\%_]""")) { "\\" + it.value }

private fun rawQuery(sql: String, args: List<Any>) = RoomRawQuery(sql) { stmt ->
    args.forEachIndexed { i, a ->
        when (a) {
            is String -> stmt.bindText(i + 1, a)
            is Int -> stmt.bindLong(i + 1, a.toLong())
            is Long -> stmt.bindLong(i + 1, a)
            is Double -> stmt.bindDouble(i + 1, a)
            else -> error("Unsupported arg $a")
        }
    }
}

private fun ScanRow.toSummary() = ScanSummary(
    id = id,
    plotId = plotId,
    plotName = plotName,
    modelVersionId = modelVersionId,
    imagePath = imagePath,
    thumbnailPath = thumbnailPath,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
    source = ScanSource.fromKey(source),
    confThreshold = confThreshold,
    iouThreshold = iouThreshold,
    inferenceMs = inferenceMs,
    totalPods = totalPods,
    verdict = Verdict.fromKey(verdict),
    lowConfidence = lowConfidence == 1,
    latitude = latitude,
    longitude = longitude,
    notes = notes,
    createdAt = createdAt,
)

private fun DetectionEntity.toSaved() = SavedDetection(
    id = id,
    scanId = scanId,
    classId = classId,
    confidence = confidence,
    x = x,
    y = y,
    w = w,
    h = h,
    correctedClassId = correctedClassId,
    correctedAt = correctedAt,
    effectiveClassId = correctedClassId ?: classId,
)

/**
 * One CSV cell, as RN's csvCell. Spreadsheets run text starting with = + - @ tab or CR as a
 * formula; user text (notes, plot names) could carry one, so it gets a ' prefix. Numbers are left
 * alone so negative coordinates stay numeric, and print as JS does (7, not 7.0).
 */
private fun csvCell(value: Any?): String {
    if (value == null) return ""
    var text = when (value) {
        is Double -> value.toJsString()
        else -> value.toString()
    }
    if (value is String && text.isNotEmpty() && text[0] in "=+-@\t\r") text = "'$text"
    return if (text.any { it in "\",\r\n" }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
