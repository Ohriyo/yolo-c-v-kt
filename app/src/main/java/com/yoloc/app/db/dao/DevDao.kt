package com.yoloc.app.db.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Query

data class ScanFilesRow(
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String?,
)

/**
 * Development-only helpers for fake data (RN: devRepository.ts). `pattern` is an already escaped
 * LIKE pattern. Fake scans are removed for good so repeated seeding does not pile up hidden rows.
 */
@Dao
interface DevDao {
    @Query("SELECT COUNT(*) FROM scans WHERE deleted_at IS NULL AND notes LIKE :pattern ESCAPE '\\'")
    suspend fun countScansWithNotes(pattern: String): Int

    @Query("SELECT image_path, thumbnail_path FROM scans WHERE notes LIKE :pattern ESCAPE '\\'")
    suspend fun scanFilesWithNotes(pattern: String): List<ScanFilesRow>

    @Query("DELETE FROM scans WHERE notes LIKE :pattern ESCAPE '\\'")
    suspend fun deleteScansWithNotes(pattern: String)

    @Query("DELETE FROM plots WHERE name LIKE :pattern ESCAPE '\\'")
    suspend fun deletePlotsWithName(pattern: String): Int
}
