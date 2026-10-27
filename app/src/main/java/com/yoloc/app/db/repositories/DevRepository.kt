package com.yoloc.app.db.repositories

import androidx.room3.withWriteTransaction
import com.yoloc.app.db.AppDatabaseHolder

data class ScanFiles(val imagePath: String, val thumbnailPath: String?)

/**
 * Development-only helpers for fake data (RN: devRepository.ts). Real scans are soft-deleted;
 * fake ones are removed for good so repeated seeding does not pile up hidden rows.
 */
class DevRepository(private val database: AppDatabaseHolder) {

    suspend fun countScansWithNotePrefix(prefix: String): Int =
        database.get().devDao().countScansWithNotes(likePrefix(prefix))

    /** Deletes matching scans (detections cascade) and returns their files for cleanup. */
    suspend fun purgeScansWithNotePrefix(prefix: String): List<ScanFiles> {
        val db = database.get()
        val dao = db.devDao()
        return db.withWriteTransaction {
            val rows = dao.scanFilesWithNotes(likePrefix(prefix))
            dao.deleteScansWithNotes(likePrefix(prefix))
            rows.map { ScanFiles(it.imagePath, it.thumbnailPath) }
        }
    }

    suspend fun purgePlotsWithNamePrefix(prefix: String): Int =
        database.get().devDao().deletePlotsWithName(likePrefix(prefix))

    private fun likePrefix(prefix: String) = escapeLike(prefix) + "%"
}
