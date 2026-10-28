package com.yoloc.app.store

import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.DateRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** A filter another screen asks History to open with (RN: History's route params). */
sealed interface HistoryRequest {
    /**
     * Home's stat cards (redesign 2026-10-28: the last 7 days; RN's count chips used 30). A null
     * verdict is every result.
     */
    data class ByVerdict(val verdict: Verdict?, val range: DateRange = DateRange.Month) : HistoryRequest

    /** The Plots manager. */
    data class ByPlot(val id: String, val name: String) : HistoryRequest
}

/**
 * History is a tab, so it cannot take arguments the way RN's `navigate('/history', params)` does.
 * The sender posts a request and selects the tab; History applies it once and clears it (RN:
 * applies the params, then `setParams(undefined)`), so the same chip works again later.
 */
class HistoryRequests {
    private val _request = MutableStateFlow<HistoryRequest?>(null)
    val request: StateFlow<HistoryRequest?> = _request.asStateFlow()

    fun send(request: HistoryRequest) {
        _request.value = request
    }

    /** The pending request, removed so it is applied only once. */
    fun consume(): HistoryRequest? = _request.getAndUpdate { null }
}
