package com.yoloc.app.db.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import com.yoloc.app.db.DetectionEntity
import com.yoloc.app.db.ScanEntity

/** Scan columns plus the plot name (RN: SCAN_COLUMNS in scanRepository.ts). */
const val SCAN_COLUMNS = """s.id, s.plot_id, p.name AS plot_name, s.model_version_id, s.image_path,
  s.thumbnail_path, s.image_width, s.image_height, s.source, s.conf_threshold, s.iou_threshold,
  s.inference_ms, s.total_pods, s.verdict, s.low_confidence, s.latitude, s.longitude, s.notes,
  s.created_at"""

data class ScanRow(
    val id: String,
    @ColumnInfo(name = "plot_id") val plotId: String?,
    @ColumnInfo(name = "plot_name") val plotName: String?,
    @ColumnInfo(name = "model_version_id") val modelVersionId: String,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String?,
    @ColumnInfo(name = "image_width") val imageWidth: Int,
    @ColumnInfo(name = "image_height") val imageHeight: Int,
    val source: String,
    @ColumnInfo(name = "conf_threshold") val confThreshold: Double,
    @ColumnInfo(name = "iou_threshold") val iouThreshold: Double,
    @ColumnInfo(name = "inference_ms") val inferenceMs: Int?,
    @ColumnInfo(name = "total_pods") val totalPods: Int,
    val verdict: String,
    @ColumnInfo(name = "low_confidence") val lowConfidence: Int,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class CountRow(val n: Int)

data class ClassSeverityRow(val id: Int, val severity: Int)

data class ScanImageRow(
    val id: String,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "image_width") val imageWidth: Int,
    @ColumnInfo(name = "image_height") val imageHeight: Int,
)

data class DetectionRefRow(@ColumnInfo(name = "scan_id") val scanId: String, @ColumnInfo(name = "class_id") val classId: Int)

data class SeverityConfidenceRow(val severity: Int, val confidence: Double)

data class VerdictCountRow(val verdict: String, val n: Int)

data class KeyCountRow(val key: String, val n: Int)

data class ClassKeyRow(val id: Int, val key: String)

data class ScanClassCountRow(@ColumnInfo(name = "scan_id") val scanId: String, @ColumnInfo(name = "class_id") val classId: Int, val n: Int)

/** SQL for scans and detections. Rules (validation, verdicts, transactions) live in ScanRepository. */
@Dao
interface ScanDao {
    /** Dynamic filters (RN: whereClause): built by ScanRepository. */
    @RawQuery
    suspend fun scanRows(query: RoomRawQuery): List<ScanRow>

    @RawQuery
    suspend fun count(query: RoomRawQuery): CountRow

    @Query("SELECT $SCAN_COLUMNS FROM scans s LEFT JOIN plots p ON p.id = s.plot_id WHERE s.id = :id AND s.deleted_at IS NULL")
    suspend fun scanRow(id: String): ScanRow?

    @Query("SELECT * FROM detections WHERE scan_id = :scanId ORDER BY confidence DESC, id")
    suspend fun detections(scanId: String): List<DetectionEntity>

    @Query("SELECT id, severity FROM classes")
    suspend fun classSeverities(): List<ClassSeverityRow>

    @Insert
    suspend fun insertScan(scan: ScanEntity)

    @Insert
    suspend fun insertDetections(detections: List<DetectionEntity>)

    @Query("UPDATE scans SET deleted_at = :now WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDelete(id: String, now: Long): Int

    @Query("UPDATE scans SET deleted_at = NULL WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun restore(id: String): Int

    @Query("UPDATE scans SET notes = :notes WHERE id = :id AND deleted_at IS NULL")
    suspend fun updateNotes(id: String, notes: String?): Int

    @Query("UPDATE scans SET thumbnail_path = :thumbnailPath WHERE id = :id")
    suspend fun setThumbnail(id: String, thumbnailPath: String)

    @Query(
        """SELECT id, image_path, image_width, image_height FROM scans
           WHERE thumbnail_path IS NULL AND deleted_at IS NULL
           ORDER BY created_at DESC, id LIMIT :limit""",
    )
    suspend fun scansWithoutThumbnail(limit: Int): List<ScanImageRow>

    @Query("SELECT scan_id, class_id FROM detections WHERE id = :detectionId")
    suspend fun detectionRef(detectionId: String): DetectionRefRow?

    @Query("UPDATE detections SET corrected_class_id = :classId, corrected_at = :correctedAt WHERE id = :detectionId")
    suspend fun setCorrection(detectionId: String, classId: Int?, correctedAt: Long?)

    @Query(
        """SELECT c.severity, d.confidence FROM detections d
           JOIN classes c ON c.id = COALESCE(d.corrected_class_id, d.class_id)
           WHERE d.scan_id = :scanId""",
    )
    suspend fun effectiveSeverities(scanId: String): List<SeverityConfidenceRow>

    @Query("UPDATE scans SET verdict = :verdict WHERE id = :scanId")
    suspend fun setVerdict(scanId: String, verdict: String)

    @Query(
        """SELECT verdict, COUNT(*) AS n FROM scans
           WHERE deleted_at IS NULL AND created_at >= :since GROUP BY verdict""",
    )
    suspend fun verdictCounts(since: Long): List<VerdictCountRow>

    @Query(
        """SELECT c.key, COUNT(*) AS n FROM detections d
           JOIN scans s ON s.id = d.scan_id
           JOIN classes c ON c.id = COALESCE(d.corrected_class_id, d.class_id)
           WHERE s.deleted_at IS NULL AND s.created_at >= :since
           GROUP BY c.key""",
    )
    suspend fun podsByClass(since: Long): List<KeyCountRow>

    @Query("SELECT id, key FROM classes ORDER BY id")
    suspend fun classKeys(): List<ClassKeyRow>

    @Query(
        """SELECT $SCAN_COLUMNS FROM scans s LEFT JOIN plots p ON p.id = s.plot_id
           WHERE s.deleted_at IS NULL ORDER BY s.created_at DESC, s.id""",
    )
    suspend fun allVisibleScanRows(): List<ScanRow>

    @Query(
        """SELECT d.scan_id, COALESCE(d.corrected_class_id, d.class_id) AS class_id, COUNT(*) AS n
           FROM detections d JOIN scans s ON s.id = d.scan_id
           WHERE s.deleted_at IS NULL GROUP BY d.scan_id, class_id""",
    )
    suspend fun podCountsByScanAndClass(): List<ScanClassCountRow>

    @Query("SELECT COUNT(*) FROM scans")
    suspend fun countAllScans(): Int

    @Query("DELETE FROM detections")
    suspend fun deleteAllDetections()

    @Query("DELETE FROM scans")
    suspend fun deleteAllScans()

    @Query("DELETE FROM plots")
    suspend fun deleteAllPlots()
}
