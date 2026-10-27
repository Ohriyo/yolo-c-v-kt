package com.yoloc.app.db.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.yoloc.app.db.PlotEntity

data class PlotWithCountRow(
    val id: String,
    val name: String,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "scan_count") val scanCount: Int,
)

@Dao
interface PlotDao {
    @Insert
    suspend fun insert(plot: PlotEntity)

    @Query("SELECT * FROM plots WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE")
    suspend fun listPlots(): List<PlotEntity>

    @Query(
        """SELECT p.id, p.name, p.notes, p.created_at,
             (SELECT COUNT(*) FROM scans s WHERE s.plot_id = p.id AND s.deleted_at IS NULL) AS scan_count
           FROM plots p WHERE p.deleted_at IS NULL ORDER BY p.name COLLATE NOCASE""",
    )
    suspend fun listPlotsWithCounts(): List<PlotWithCountRow>

    @Query("UPDATE plots SET deleted_at = :now WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDelete(id: String, now: Long): Int

    @Query("UPDATE plots SET name = :name WHERE id = :id AND deleted_at IS NULL")
    suspend fun rename(id: String, name: String): Int

    @Query("UPDATE scans SET plot_id = NULL WHERE plot_id = :id")
    suspend fun detachScans(id: String)

    @Query("SELECT COUNT(*) FROM plots WHERE deleted_at IS NULL")
    suspend fun count(): Int
}
