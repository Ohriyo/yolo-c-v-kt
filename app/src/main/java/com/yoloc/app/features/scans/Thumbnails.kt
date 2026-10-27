package com.yoloc.app.features.scans

import android.util.Log
import com.yoloc.app.db.repositories.ScanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * RN: thumbnails.ts backfillThumbnails. Makes thumbnails for scans saved before thumbnails existed
 * (or whose thumbnail failed), one at a time so it never competes with scrolling. Scans that fail
 * are skipped for this run. Concurrent calls share one run, which lives in its own scope so a
 * caller leaving the screen does not cancel it for the others.
 */
class ThumbnailBackfill(
    private val scans: ScanRepository,
    private val files: ScanFileStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()
    private var running: Deferred<Int>? = null

    /** Returns how many thumbnails were written. */
    suspend fun run(maxScans: Int = 200): Int {
        val job = mutex.withLock {
            running ?: scope.async {
                try {
                    backfill(maxScans)
                } finally {
                    mutex.withLock { running = null }
                }
            }.also { running = it }
        }
        return job.await()
    }

    private suspend fun backfill(maxScans: Int): Int {
        var written = 0
        for (scan in scans.listScansWithoutThumbnail(maxScans)) {
            val path = scanThumbnailPath(scan.id)
            try {
                files.makeThumbnail(scan.imagePath, path, scan.imageWidth, scan.imageHeight)
                scans.setScanThumbnail(scan.id, path)
                written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Thumbnail backfill skipped scan ${scan.id}", e)
            }
        }
        return written
    }

    private companion object {
        const val TAG = "ThumbnailBackfill"
    }
}
