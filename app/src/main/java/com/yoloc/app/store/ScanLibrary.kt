package com.yoloc.app.store

import com.yoloc.app.db.repositories.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/** RN: scanLibraryStore.ts. Saved-scan changes the lists react to, and the delete Undo offer. */
class ScanLibrary(private val scans: ScanRepository) {
    data class PendingUndo(val scanId: String, val token: Int)

    private val nextToken = AtomicInteger(1)

    private val _revision = MutableStateFlow(0)

    /**
     * Bumped after any change to saved scans (save, delete, restore, notes, corrections). Lists and
     * counts reload when it changes; the database stays the source of truth.
     */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)

    /** The last deleted scan while its Undo is still offered. */
    val pendingUndo: StateFlow<PendingUndo?> = _pendingUndo.asStateFlow()

    fun bump() = _revision.update { it + 1 }

    /**
     * Soft-deletes a scan and offers Undo. Throws if the database write fails. A scan that was
     * already gone (a stale row, a double tap) only refreshes the lists, so it cannot replace a
     * real Undo offer with an empty one.
     */
    suspend fun deleteScan(scanId: String) {
        if (scans.softDeleteScan(scanId)) _pendingUndo.value = PendingUndo(scanId, nextToken.getAndIncrement())
        bump()
    }

    /** Restores the pending deleted scan. The offer ends first, so a double tap restores once. */
    suspend fun undoDelete() {
        val pending = _pendingUndo.getAndUpdate { null } ?: return
        scans.restoreScan(pending.scanId)
        bump()
    }

    /** Ends the Undo offer; the scan stays deleted. Ignored if a newer delete replaced it. */
    fun dismissUndo(token: Int) = _pendingUndo.update { if (it?.token == token) null else it }

    /** After Clear all data nothing is left to restore (RN: settings.tsx sets pendingUndo to null). */
    fun clearUndo() {
        _pendingUndo.value = null
    }

    companion object {
        /** How long the "Scan deleted · Undo" snackbar stays up. */
        const val UNDO_WINDOW_MS = 6000L
    }
}
