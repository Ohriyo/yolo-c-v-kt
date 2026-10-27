package com.yoloc.app.screens.plots

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.PlotWithCount
import com.yoloc.app.screens.container
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.ScanLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * RN: app/plots.tsx. Add, rename and delete the farm areas scans are grouped by. Tapping a plot
 * opens History filtered to it. Deleting a plot keeps its scans (they lose the plot).
 */
class PlotsViewModel(
    private val plots: PlotRepository,
    private val library: ScanLibrary,
    private val historyRequests: HistoryRequests,
) : ViewModel() {
    sealed interface Load {
        data object Loading : Load

        data object Error : Load

        data class Ready(val plots: List<PlotWithCount>) : Load
    }

    enum class Problem { Duplicate, Failed }

    data class State(
        val load: Load = Load.Loading,
        /** The "new plot" field. */
        val name: String = "",
        val problem: Problem? = null,
        /** The plot whose rename sheet is open. */
        val renaming: PlotWithCount? = null,
        /** The plot whose delete dialog is open. */
        val deleting: PlotWithCount? = null,
    ) {
        val plots: List<PlotWithCount> get() = (load as? Load.Ready)?.plots.orEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val attempt = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(library.revision, attempt) { _, _ -> }.collectLatest {
                try {
                    val rows = plots.listPlotsWithCounts()
                    _state.update { it.copy(load = Load.Ready(rows)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load plots", e)
                    // A failed refresh keeps the list.
                    _state.update { if (it.load is Load.Ready) it else it.copy(load = Load.Error) }
                }
            }
        }
    }

    fun retry() = attempt.update { it + 1 }

    /** Another plot (not `exceptId`) already has this name, ignoring case and accents. */
    fun isDuplicate(candidate: String, exceptId: String? = null): Boolean =
        _state.value.plots.any { it.id != exceptId && sameName(it.name, candidate) }

    fun setName(name: String) = _state.update { it.copy(name = name, problem = null) }

    fun add() {
        val name = _state.value.name
        if (name.isBlank()) return
        if (isDuplicate(name)) {
            _state.update { it.copy(problem = Problem.Duplicate) }
            return
        }
        viewModelScope.launch {
            if (change { plots.createPlot(name) }) _state.update { it.copy(name = "") }
        }
    }

    fun startRename(plot: PlotWithCount) = _state.update { it.copy(renaming = plot) }

    fun cancelRename() = _state.update { it.copy(renaming = null) }

    /** The rename sheet's Save; the sheet itself shows the duplicate message ([isDuplicate]). */
    fun rename(newName: String) {
        val plot = _state.value.renaming ?: return
        if (newName.isBlank() || isDuplicate(newName, plot.id)) return
        viewModelScope.launch {
            if (change { plots.renamePlot(plot.id, newName) }) _state.update { it.copy(renaming = null) }
        }
    }

    fun askDelete(plot: PlotWithCount) = _state.update { it.copy(deleting = plot) }

    fun cancelDelete() = _state.update { it.copy(deleting = null) }

    fun confirmDelete() {
        val plot = _state.value.deleting ?: return
        _state.update { it.copy(deleting = null) }
        viewModelScope.launch { change { plots.deletePlot(plot.id) } }
    }

    /** A plot row: History opens filtered to it (the screen then selects the History tab). */
    fun open(plot: PlotWithCount) = historyRequests.send(HistoryRequest.ByPlot(plot.id, plot.name))

    /** Plot changes bump the library revision: History, Home and Settings show plot names and counts. */
    private suspend fun change(task: suspend () -> Unit): Boolean {
        _state.update { it.copy(problem = null) }
        return try {
            task()
            library.bump()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Plot change failed", e)
            _state.update { it.copy(problem = Problem.Failed) }
            false
        }
    }

    companion object {
        private const val TAG = "plots"

        val Factory = viewModelFactory {
            initializer { PlotsViewModel(container.plotRepository, container.scanLibrary, container.historyRequests) }
        }
    }
}

/** RN: `a.trim().localeCompare(b.trim(), undefined, { sensitivity: 'base' }) === 0`. */
fun sameName(a: String, b: String): Boolean =
    Collator.getInstance().apply { strength = Collator.PRIMARY }.compare(a.trim(), b.trim()) == 0
