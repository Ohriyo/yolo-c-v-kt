package com.yoloc.app.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.RoomWarnings

/*
 * The schema of RN's migration 001 (src/db/migrations/001_initial.ts): same tables, columns, names
 * and indexes. Verdicts and sources are stored as String and booleans as Int (0/1), exactly as in
 * SQLite; repositories map them to Kotlin types. Room cannot declare CHECK constraints, so
 * ScanRepository enforces them (Verdict and ScanSource enums, validateNewScan).
 */

@Entity(tableName = "model_versions")
data class ModelVersionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "input_size") val inputSize: Int,
    @ColumnInfo(name = "num_classes") val numClasses: Int,
    @ColumnInfo(name = "val_map50") val valMap50: Double?,
    @ColumnInfo(name = "test_map50") val testMap50: Double?,
    @ColumnInfo(name = "early_stage_recall") val earlyStageRecall: Double?,
    @ColumnInfo(name = "is_active", defaultValue = "0") val isActive: Int,
    @ColumnInfo(name = "installed_at") val installedAt: Long,
)

@Entity(tableName = "classes", indices = [Index(value = ["key"], unique = true)])
data class ClassEntity(
    /** Model output index. Referenced by saved detections, so never renumbered or deleted. */
    @PrimaryKey val id: Int,
    val key: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val severity: Int,
    @ColumnInfo(name = "color_token") val colorToken: String,
    val summary: String,
    val symptoms: String?,
    /** JSON array of strings. */
    @ColumnInfo(name = "recommended_actions") val recommendedActions: String?,
)

@Entity(tableName = "plots")
data class PlotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

// RN has no index on model_version_id; kept identical, so Room's index warning is suppressed.
@Suppress(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(
    tableName = "scans",
    foreignKeys = [
        ForeignKey(PlotEntity::class, ["id"], ["plot_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ModelVersionEntity::class, ["id"], ["model_version_id"]),
    ],
    indices = [
        Index(value = ["created_at"], name = "idx_scans_created", orders = [Index.Order.DESC]),
        Index(value = ["verdict"], name = "idx_scans_verdict"),
        Index(value = ["plot_id"], name = "idx_scans_plot"),
    ],
)
data class ScanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plot_id") val plotId: String?,
    @ColumnInfo(name = "model_version_id") val modelVersionId: String,
    /** Relative to filesDir, e.g. scans/<id>.jpg. */
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String?,
    @ColumnInfo(name = "image_width") val imageWidth: Int,
    @ColumnInfo(name = "image_height") val imageHeight: Int,
    /** camera | gallery */
    val source: String,
    @ColumnInfo(name = "conf_threshold") val confThreshold: Double,
    @ColumnInfo(name = "iou_threshold") val iouThreshold: Double,
    @ColumnInfo(name = "inference_ms") val inferenceMs: Int?,
    @ColumnInfo(name = "total_pods", defaultValue = "0") val totalPods: Int,
    /** healthy | early_stage | infected | no_pod */
    val verdict: String,
    @ColumnInfo(name = "low_confidence", defaultValue = "0") val lowConfidence: Int,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

// RN has no index on corrected_class_id; kept identical, so Room's index warning is suppressed.
@Suppress(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(
    tableName = "detections",
    foreignKeys = [
        ForeignKey(ScanEntity::class, ["id"], ["scan_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ClassEntity::class, ["id"], ["class_id"]),
        ForeignKey(ClassEntity::class, ["id"], ["corrected_class_id"]),
    ],
    indices = [
        Index(value = ["scan_id"], name = "idx_detections_scan"),
        Index(value = ["class_id"], name = "idx_detections_class"),
    ],
)
data class DetectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "scan_id") val scanId: String,
    @ColumnInfo(name = "class_id") val classId: Int,
    val confidence: Double,
    // Normalized 0-1, top-left origin, relative to the original image.
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    @ColumnInfo(name = "corrected_class_id") val correctedClassId: Int?,
    @ColumnInfo(name = "corrected_at") val correctedAt: Long?,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
