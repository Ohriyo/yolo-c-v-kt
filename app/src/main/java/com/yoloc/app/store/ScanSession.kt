package com.yoloc.app.store

import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.features.scans.ScanResult
import com.yoloc.app.features.scans.TempImages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * RN: scanSessionStore.ts. The photo and result of the scan in progress; nothing is in the
 * database until the user saves. Camera, Preview, Processing and Result share it.
 */
class ScanSession(private val temp: TempImages) {
    data class State(val photo: PendingPhoto? = null, val result: ScanResult? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** New photo for the flow; replaces (and deletes) any previous unsaved photo. */
    fun setPhoto(photo: PendingPhoto) {
        val previous = _state.getAndUpdate { State(photo = photo) }.photo
        if (previous != null && previous.file != photo.file) temp.delete(previous.file)
    }

    /**
     * Ignored when the result is for a photo the session no longer holds (cleared or replaced while
     * it was analyzed), so a late result can never pair with the wrong photo.
     */
    fun setResult(result: ScanResult) = _state.update { if (it.photo == result.photo) it.copy(result = result) else it }

    /** Ends the session. Deletes the temp photo unless `keepPhoto` (e.g. it was just saved). */
    fun clear(keepPhoto: Boolean = false) {
        val previous = _state.getAndUpdate { State() }.photo
        if (!keepPhoto) temp.delete(previous?.file)
    }
}
