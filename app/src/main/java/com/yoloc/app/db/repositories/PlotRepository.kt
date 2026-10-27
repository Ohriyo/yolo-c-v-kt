package com.yoloc.app.db.repositories

import androidx.room3.withWriteTransaction
import com.yoloc.app.db.AppDatabaseHolder
import com.yoloc.app.db.PlotEntity
import com.yoloc.app.util.uuid

data class Plot(val id: String, val name: String, val notes: String?, val createdAt: Long)

data class PlotWithCount(
    val id: String,
    val name: String,
    val notes: String?,
    val createdAt: Long,
    /** Visible (not deleted) scans in this plot. */
    val scanCount: Int,
)

/** RN: src/db/repositories/plotRepository.ts. Duplicate names are checked by the Plots screen. */
class PlotRepository(private val database: AppDatabaseHolder) {

    suspend fun createPlot(
        name: String,
        notes: String? = null,
        id: String? = null,
        createdAt: Long? = null,
    ): Plot {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Plot name is required" }
        val plot = Plot(id ?: uuid(), trimmed, notes, createdAt ?: System.currentTimeMillis())
        database.get().plotDao().insert(PlotEntity(plot.id, plot.name, plot.notes, plot.createdAt))
        return plot
    }

    /** Plots that are not soft-deleted, alphabetically. */
    suspend fun listPlots(): List<Plot> =
        database.get().plotDao().listPlots().map { Plot(it.id, it.name, it.notes, it.createdAt) }

    suspend fun softDeletePlot(id: String, now: Long = System.currentTimeMillis()): Boolean =
        database.get().plotDao().softDelete(id, now) > 0

    /** Plots for the Plots manager, alphabetically, with how many scans each holds. */
    suspend fun listPlotsWithCounts(): List<PlotWithCount> =
        database.get().plotDao().listPlotsWithCounts().map {
            PlotWithCount(it.id, it.name, it.notes, it.createdAt, it.scanCount)
        }

    suspend fun renamePlot(id: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Plot name is required" }
        if (database.get().plotDao().rename(id, trimmed) == 0) throw NoSuchElementException("Plot $id not found")
    }

    /**
     * Removes a plot from the app. Its scans stay, with their photos and results, but no longer
     * belong to a plot, so History's plot filter and names never point at a deleted plot.
     */
    suspend fun deletePlot(id: String, now: Long = System.currentTimeMillis()) {
        val db = database.get()
        val dao = db.plotDao()
        db.withWriteTransaction {
            dao.detachScans(id)
            dao.softDelete(id, now)
        }
    }

    suspend fun countPlots(): Int = database.get().plotDao().count()
}
