package com.yoloc.app.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.DayFormat
import com.yoloc.app.features.scans.HistoryItem
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.ThumbnailBackfill
import com.yoloc.app.features.scans.saveScan
import com.yoloc.app.screens.history.HistoryViewModel
import com.yoloc.app.screens.history.ScanDetailViewModel
import com.yoloc.app.screens.home.HomeViewModel
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.ScanLibrary
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Step 4.4 part 1 ViewModels (Home, History, Scan Detail) against the real Room database. */
@RunWith(AndroidJUnit4::class)
class LibraryViewModelsTest {
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val classes = ClassRepository(holder)
    private val files = MemoryFileStore()
    private val library = ScanLibrary(scans)
    private val requests = HistoryRequests()
    private val config = TestDb.config
    private val dayFormat = DayFormat.forDevice(TestDb.context)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() = holder.close()

    private suspend fun save(verdictClass: Int = 2, createdAt: Long = NOW, notes: String? = null) = saveScan(
        scans,
        files,
        SaveScanInput(
            sourceImage = File("/cache/photo.jpg"),
            modelVersionId = config.id,
            imageWidth = 800,
            imageHeight = 600,
            source = ScanSource.Camera,
            confThreshold = 0.25,
            iouThreshold = 0.45,
            notes = notes,
            createdAt = createdAt,
            detections = listOf(NewDetection(verdictClass, 0.9, 0.1, 0.1, 0.2, 0.2)),
        ),
        config.lowConfidenceThreshold,
    )

    private suspend fun <T> StateFlow<T>.await(what: String, predicate: (T) -> Boolean): T =
        try {
            withTimeout(5_000) { first(predicate) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for $what; last value $value", e)
        }

    private fun history() = HistoryViewModel(scans, plots, library, requests, ThumbnailBackfill(scans, files), dayFormat)

    private fun HistoryViewModel.State.scanIds() = items.filterIsInstance<HistoryItem.Scan>().map { it.scan.id }

    @Test
    fun homeShowsTheLastThreeScansAndThirtyDayCountsAndReloadsOnRevision(): Unit = runBlocking {
        repeat(4) { save(createdAt = System.currentTimeMillis() - it * 1000L) }
        val home = HomeViewModel(scans, library)
        val ready = home.summary.await("Home ready") { it is HomeViewModel.Summary.Ready } as HomeViewModel.Summary.Ready
        assertEquals(3, ready.recent.size)
        assertEquals(4, ready.stats.scansByVerdict[Verdict.Healthy])

        save(verdictClass = 1, createdAt = System.currentTimeMillis())
        library.bump()
        home.summary.await("Home reloaded") { (it as? HomeViewModel.Summary.Ready)?.stats?.totalScans == 5 }
    }

    @Test
    fun historyPagesFortyAtATimeWithoutDuplicates() = runBlocking {
        repeat(45) { save(createdAt = NOW - it * 60_000L) }
        val vm = history()
        val first = vm.state.await("first page") { it.status == HistoryViewModel.Status.Ready }
        assertEquals(40, first.scanIds().size)
        assertEquals(45, first.total)
        assertTrue(first.hasMore)

        vm.loadMore()
        val all = vm.state.await("second page") { it.scanIds().size == 45 }
        assertEquals(45, all.scanIds().toSet().size)
        assertTrue(!all.hasMore)
    }

    @Test
    fun historyAppliesARequestFromHomeOnceAndDebouncesSearch() = runBlocking {
        save(verdictClass = 1, notes = "North tree")
        save(verdictClass = 2, notes = "South tree")
        requests.send(HistoryRequest.ByVerdict(Verdict.Infected))
        val vm = history()
        val filtered = vm.state.await("verdict filter") {
            it.filter.verdicts == listOf(Verdict.Infected) && it.status == HistoryViewModel.Status.Ready
        }
        assertEquals(1, filtered.total)
        assertNull(requests.request.value)

        vm.clearFilters()
        vm.setSearchText("Sou")
        assertEquals("", vm.state.value.filter.search) // not yet: 250 ms debounce
        val searched = vm.state.await("search") { it.filter.search == "Sou" && it.status == HistoryViewModel.Status.Ready && it.total == 1 }
        assertEquals("Sou", searched.searchText)
    }

    @Test
    fun historyHidesADeletedRowAtOnceAndUndoBringsItBack(): Unit = runBlocking {
        val id = save()
        val vm = history()
        vm.state.await("loaded") { id in it.scanIds() }
        vm.delete(id)
        assertTrue(id !in vm.state.value.scanIds())
        vm.state.await("reload after delete") { it.total == 0 }
        library.undoDelete()
        vm.state.await("restored") { id in it.scanIds() }
    }

    @Test
    fun scanDetailLoadsSavesNotesAndClosesAfterDelete() = runBlocking {
        val id = save(notes = "Tree 7")
        val vm = ScanDetailViewModel(id, saved = true, scans, classes, library, files, dayFormat, scope)
        assertEquals(ScanDetailViewModel.Toast.Saved, vm.state.value.toast)
        val ready = vm.state.await("detail ready") { it.load is ScanDetailViewModel.Load.Ready }
        assertEquals("Tree 7", ready.notes.text)
        assertTrue(!ready.notes.dirty)

        vm.setNotes("x".repeat(600))
        assertEquals(ScanDetailViewModel.NOTES_MAX, vm.state.value.notes.text.length)
        vm.setNotes("  Tree 8 ")
        vm.saveNotes()
        vm.state.await("notes saved") { it.toast == ScanDetailViewModel.Toast.NotesSaved }
        assertEquals("Tree 8", scans.getScan(id)!!.scan.notes)
        assertTrue(!vm.state.value.notes.dirty)

        vm.askDelete()
        vm.confirmDelete()
        vm.state.await("closed") { it.closed }
        assertEquals(id, library.pendingUndo.value?.scanId)
    }

    @Test
    fun scanDetailReportsAMissingScan() = runBlocking {
        val vm = ScanDetailViewModel("no-such-id", saved = false, scans, classes, library, files, dayFormat, scope)
        vm.state.await("missing") { it.load == ScanDetailViewModel.Load.Missing }
        Unit
    }

    private companion object {
        const val NOW = 1_792_227_600_000L
    }
}
