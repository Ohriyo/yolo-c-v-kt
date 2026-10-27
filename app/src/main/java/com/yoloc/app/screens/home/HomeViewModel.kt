package com.yoloc.app.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.ScanFilters
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanStats
import com.yoloc.app.db.repositories.ScanSummary
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.DAY_MS
import com.yoloc.app.features.scans.DateRange
import com.yoloc.app.features.scans.rangeStart
import com.yoloc.app.features.scans.startOfDay
import com.yoloc.app.screens.container
import com.yoloc.app.screens.refreshedNow
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.ScanLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** RN: app/(tabs)/index.tsx, useHomeSummary and useNow. */
class HomeViewModel(
    private val scans: ScanRepository,
    library: ScanLibrary,
    private val historyRequests: HistoryRequests,
) : ViewModel() {
    sealed interface Summary {
        data object Loading : Summary

        data object Error : Summary

        data class Ready(val recent: List<ScanSummary>, val stats: ScanStats) : Summary
    }

    private val attempt = MutableStateFlow(0)

    /**
     * The last 3 scans and the last 30 days' counts (the same window as History's "Last 30 days",
     * so a count chip and the list it opens agree). Reloads on revision or Retry; a failed refresh
     * keeps what is shown.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<Summary> = combine(library.revision, attempt) { _, _ -> }
        .mapLatest { fetch() }
        .runningFold(Summary.Loading as Summary) { previous, next -> next ?: previous as? Summary.Ready ?: Summary.Error }
        // Eagerly, like RN's tab that stays mounted: coming back to Home never restarts at Loading.
        .stateIn(viewModelScope, SharingStarted.Eagerly, Summary.Loading)

    private val _now = MutableStateFlow(System.currentTimeMillis())

    /** For relative dates and the tip of the day; refreshed when the screen resumes. */
    val now: StateFlow<Long> = _now.asStateFlow()

    private suspend fun fetch(): Summary.Ready? = try {
        coroutineScope {
            val recent = async { scans.listScans(ScanFilters(limit = RECENT_COUNT)) }
            val stats = async { scans.getStats(rangeStart(DateRange.Month, System.currentTimeMillis()) ?: 0) }
            Summary.Ready(recent.await(), stats.await())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not load Home summary", e)
        null
    }

    fun reload() = attempt.update { it + 1 }

    /** ON_RESUME (RN: useNow on focus), so a tab left open overnight relabels its days. */
    fun refreshNow() = _now.update { refreshedNow(it) }

    /** A count chip: History opens filtered to that result (the screen then selects the tab). */
    fun openCount(verdict: Verdict) = historyRequests.send(HistoryRequest.ByVerdict(verdict))

    companion object {
        private const val TAG = "home"
        const val RECENT_COUNT = 3

        val Factory = viewModelFactory {
            initializer { HomeViewModel(container.scanRepository, container.scanLibrary, container.historyRequests) }
        }
    }
}

/** The tip shown today: changes at local midnight, cycling through all of them. */
fun dailyTipIndex(now: Long, tipCount: Int): Int = (Math.floorDiv(startOfDay(now), DAY_MS) % tipCount).toInt()
