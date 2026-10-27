package com.yoloc.app.screens.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.Plot
import com.yoloc.app.db.repositories.PlotFilter
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanCursor
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSummary
import com.yoloc.app.features.scans.DateRange
import com.yoloc.app.features.scans.DayFormat
import com.yoloc.app.features.scans.HistoryFilter
import com.yoloc.app.features.scans.HistoryItem
import com.yoloc.app.features.scans.ThumbnailBackfill
import com.yoloc.app.features.scans.groupByDay
import com.yoloc.app.features.scans.toScanFilters
import com.yoloc.app.screens.app
import com.yoloc.app.screens.container
import com.yoloc.app.screens.refreshedNow
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.ScanLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RN: app/(tabs)/history.tsx, useScanList and HistoryFilterBar's state. Every saved scan, newest
 * first and grouped by day, paged 40 at a time with a keyset cursor so a delete or restore never
 * shifts a page. Reloads when the filter or the library revision changes, keeping as many rows as
 * were loaded so the scroll position holds.
 */
class HistoryViewModel(
    private val scans: ScanRepository,
    private val plots: PlotRepository,
    private val library: ScanLibrary,
    private val historyRequests: HistoryRequests,
    private val thumbnails: ThumbnailBackfill,
    private val dayFormat: DayFormat,
) : ViewModel() {
    enum class Status { Loading, Error, Ready }

    data class State(
        val filter: HistoryFilter = HistoryFilter(),
        /** The search field as typed; it reaches `filter` 250 ms after the last keystroke. */
        val searchText: String = "",
        /** Name of the selected plot, for the pill label (the filter holds only the id). */
        val plotName: String? = null,
        /** Day headers and rows, without rows hidden by a delete in progress. */
        val items: List<HistoryItem> = emptyList(),
        /** Matching scans in the database, not just the loaded pages. */
        val total: Int = 0,
        /**
         * Loading only before the first page of a new filter; reloads keep rows visible. Error with
         * rows means a refresh failed: keep showing the rows (RN shows the error state only when
         * there are none).
         */
        val status: Status = Status.Loading,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = false,
        /** The scan whose options sheet (Open / Delete) is open. */
        val optionsFor: String? = null,
        /** The "could not delete" toast, shown for 4 s. */
        val deleteError: Boolean = false,
        /** Plots for the Plot sheet; null until loaded. */
        val plots: List<Plot>? = null,
        val plotsFailed: Boolean = false,
    )

    /** Loaded rows and the filter they belong to; rows of an older filter are never shown. */
    private data class Loaded(
        val filter: HistoryFilter,
        val scans: List<ScanSummary>,
        val total: Int,
        val error: Boolean = false,
        val loadingMore: Boolean = false,
    )

    private data class Inner(
        val filter: HistoryFilter = HistoryFilter(),
        val searchText: String = "",
        val plotName: String? = null,
        val loaded: Loaded? = null,
        /** Rows deleted but not yet gone from a reload, hidden at once so a swipe never leaves a gap. */
        val hidden: Set<String> = emptySet(),
        /** Deletes still writing; a reload that lands before one finishes keeps its row hidden. */
        val deleting: Set<String> = emptySet(),
        val optionsFor: String? = null,
        val deleteError: Boolean = false,
        val plots: List<Plot>? = null,
        val plotsFailed: Boolean = false,
        val now: Long = System.currentTimeMillis(),
    )

    private val inner = MutableStateFlow(Inner())
    private val attempt = MutableStateFlow(0)
    private var searchJob: Job? = null
    private var deleteErrorJob: Job? = null

    val state: StateFlow<State> = inner.map { it.toState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, State())

    init {
        viewModelScope.launch {
            combine(inner.map { it.filter }.distinctUntilChanged(), library.revision, attempt) { filter, _, _ -> filter }
                .collectLatest { load(it) }
        }
        // Home's count chips and the Plots manager open History pre-filtered.
        viewModelScope.launch {
            historyRequests.request.filterNotNull().collect { historyRequests.consume()?.let(::apply) }
        }
        // Scans saved before thumbnails existed get one in the background, then the list reloads.
        viewModelScope.launch {
            try {
                if (thumbnails.run() > 0) library.bump()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Thumbnail backfill failed", e)
            }
        }
    }

    /** Grouping inputs and result, so a keystroke or a sheet toggle does not regroup every row. */
    private var grouped: Triple<List<ScanSummary>?, Set<String>, Long>? = null
    private var groupedItems: List<HistoryItem> = emptyList()

    private fun items(scans: List<ScanSummary>?, hidden: Set<String>, now: Long): List<HistoryItem> {
        val last = grouped
        // Rows by identity: a new list only comes from a load, so equal contents never need a deep compare.
        if (last != null && last.first === scans && last.second == hidden && last.third == now) return groupedItems
        val key = Triple(scans, hidden, now)
        val visible = when {
            scans == null -> emptyList()
            hidden.isEmpty() -> scans
            else -> scans.filter { it.id !in hidden }
        }
        return groupByDay(visible, now, dayFormat).also {
            grouped = key
            groupedItems = it
        }
    }

    private fun Inner.toState(): State {
        val current = loaded?.takeIf { it.filter == filter }
        return State(
            filter = filter,
            searchText = searchText,
            plotName = plotName,
            items = items(current?.scans, hidden, now),
            total = current?.total ?: 0,
            status = when {
                current == null -> Status.Loading
                current.error -> Status.Error
                else -> Status.Ready
            },
            loadingMore = current?.loadingMore ?: false,
            hasMore = current != null && current.scans.size < current.total,
            optionsFor = optionsFor,
            deleteError = deleteError,
            plots = plots,
            plotsFailed = plotsFailed,
        )
    }

    private suspend fun load(filter: HistoryFilter) {
        val previous = inner.value.loaded
        // Same filter (a revision change): reload as many rows as are on screen.
        val limit = if (previous?.filter == filter) maxOf(PAGE_SIZE, previous.scans.size) else PAGE_SIZE
        val filters = toScanFilters(filter, System.currentTimeMillis())
        try {
            val (rows, total) = coroutineScope {
                val rows = async { scans.listScans(filters.copy(limit = limit)) }
                val total = async { scans.countScans(filters) }
                rows.await() to total.await()
            }
            // A reload reflects the database, so optimistic hides are no longer needed (and an
            // Undo brings the row back).
            inner.update { it.copy(loaded = Loaded(filter, rows, total), hidden = it.deleting) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not load scans", e)
            inner.update {
                val same = it.loaded?.takeIf { l -> l.filter == filter }
                it.copy(loaded = same?.copy(error = true) ?: Loaded(filter, emptyList(), 0, error = true))
            }
        }
    }

    /** Near the end of the list: the next 40 after the last loaded row. */
    fun loadMore() {
        val s = inner.value
        val l = s.loaded ?: return
        val last = l.scans.lastOrNull() ?: return
        if (l.filter != s.filter || l.loadingMore || l.scans.size >= l.total) return
        inner.update { it.copy(loaded = it.loaded?.copy(loadingMore = true)) }
        viewModelScope.launch {
            try {
                val filters = toScanFilters(l.filter, System.currentTimeMillis())
                val page = scans.listScans(filters.copy(after = ScanCursor(last.createdAt, last.id), limit = PAGE_SIZE))
                inner.update {
                    val current = it.loaded?.takeIf { c -> c.filter == l.filter } ?: return@update it
                    // A reload landed meanwhile and its list no longer ends at this page's cursor:
                    // appending would skip the rows in between. Drop the page; the next scroll asks again.
                    if (current.scans.lastOrNull()?.id != last.id) return@update it.copy(loaded = current.copy(loadingMore = false))
                    it.copy(loaded = current.copy(scans = mergePage(current.scans, page), loadingMore = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load more scans", e)
                inner.update {
                    val current = it.loaded?.takeIf { c -> c.filter == l.filter } ?: return@update it
                    it.copy(loaded = current.copy(loadingMore = false))
                }
            }
        }
    }

    fun retry() = attempt.update { it + 1 }

    /** The Status, Date and Plot pills, and Clear on the search field. */
    fun setFilter(filter: HistoryFilter) {
        val searchChanged = filter.search != inner.value.filter.search
        // A search set from outside the field (cleared) replaces the typed text and any pending
        // keystrokes; a pill change keeps them.
        if (searchChanged) searchJob?.cancel()
        inner.update { it.copy(filter = filter, searchText = if (searchChanged) filter.search else it.searchText) }
    }

    fun setSearchText(text: String) {
        inner.update { it.copy(searchText = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            inner.update { it.copy(filter = it.filter.copy(search = text)) }
        }
    }

    /** The Plot sheet: Any (null name), "No plot", or one plot. */
    fun selectPlot(plot: PlotFilter, name: String?) {
        inner.update { it.copy(plotName = name) }
        setFilter(inner.value.filter.copy(plot = plot))
    }

    /** The no-match state's Clear filters. */
    fun clearFilters() {
        resetSearch()
        inner.update { it.copy(plotName = null) }
        setFilter(HistoryFilter())
    }

    /** A filter set from outside replaces any search still being typed. */
    private fun resetSearch() {
        searchJob?.cancel()
        inner.update { it.copy(searchText = "") }
    }

    /** Called each time the Plot sheet opens (RN reloads plots whenever it becomes visible). */
    fun loadPlots() {
        viewModelScope.launch {
            try {
                val rows = plots.listPlots()
                inner.update { it.copy(plots = rows, plotsFailed = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load plots", e)
                inner.update { it.copy(plotsFailed = true) }
            }
        }
    }

    fun openOptions(scanId: String) = inner.update { it.copy(optionsFor = scanId) }

    fun closeOptions() = inner.update { it.copy(optionsFor = null) }

    /** Swipe, the options sheet, or the accessibility action. The row hides at once; Undo comes from the library. */
    fun delete(scanId: String) {
        inner.update { it.copy(optionsFor = null, hidden = it.hidden + scanId, deleting = it.deleting + scanId) }
        viewModelScope.launch {
            try {
                library.deleteScan(scanId)
                inner.update { it.copy(deleting = it.deleting - scanId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Delete failed", e)
                inner.update { it.copy(hidden = it.hidden - scanId, deleting = it.deleting - scanId, deleteError = true) }
                deleteErrorJob?.cancel()
                deleteErrorJob = launch {
                    delay(DELETE_ERROR_MS)
                    inner.update { it.copy(deleteError = false) }
                }
            }
        }
    }

    /** ON_RESUME (RN: useNow on focus), so day headers stay right after midnight. */
    fun refreshNow() = inner.update { it.copy(now = refreshedNow(it.now)) }

    private fun apply(request: HistoryRequest) = when (request) {
        is HistoryRequest.ByVerdict -> {
            resetSearch()
            inner.update { it.copy(plotName = null) }
            // Home's chips count the last 30 days, so the list they open covers the same days.
            setFilter(HistoryFilter(verdicts = listOf(request.verdict), range = DateRange.Month))
        }
        is HistoryRequest.ByPlot -> {
            resetSearch()
            inner.update { it.copy(plotName = request.name) }
            setFilter(HistoryFilter(plot = PlotFilter.Id(request.id)))
        }
    }

    companion object {
        private const val TAG = "history"
        const val PAGE_SIZE = 40
        const val SEARCH_DEBOUNCE_MS = 250L
        const val DELETE_ERROR_MS = 4000L

        val Factory = viewModelFactory {
            initializer {
                val c = container
                HistoryViewModel(
                    c.scanRepository, c.plotRepository, c.scanLibrary, c.historyRequests, c.thumbnails,
                    DayFormat.forDevice(app),
                )
            }
        }
    }
}

/** A reload may have landed while a page was loading; never list a scan twice. */
internal fun mergePage(loaded: List<ScanSummary>, page: List<ScanSummary>): List<ScanSummary> {
    val seen = loaded.mapTo(HashSet()) { it.id }
    return loaded + page.filter { it.id !in seen }
}
