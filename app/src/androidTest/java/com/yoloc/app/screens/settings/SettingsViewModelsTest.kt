package com.yoloc.app.screens.settings

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.features.export.ExportScope
import com.yoloc.app.features.learn.LearnTopic
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.saveScan
import com.yoloc.app.features.settings.LocationAccess
import com.yoloc.app.screens.dev.DevSeedViewModel
import com.yoloc.app.screens.learn.LearnTopicViewModel
import com.yoloc.app.screens.plots.PlotsViewModel
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.PermissionHistory
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

/** Step 4.4 part 3 ViewModels (Settings, Retraining export, Plots, Learn, Dev seed) against Room. */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelsTest {
    private val context = TestDb.context
    private val application = context.applicationContext as Application
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val classes = ClassRepository(holder)
    private val settingsRepo = SettingsRepository(holder)
    private val files = MemoryFileStore()
    private val library = ScanLibrary(scans)
    private val requests = HistoryRequests()
    private val config = TestDb.config
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = SettingsStore(settingsRepo, config, scope)

    @Before
    fun open() = runBlocking {
        holder.get()
        store.hydrate()
    }

    @After
    fun close() {
        scope.cancel()
        holder.close()
        context.getSharedPreferences("permissions", 0).edit().clear().commit()
    }

    private suspend fun save(plotId: String? = null) = saveScan(
        scans,
        files,
        SaveScanInput(
            sourceImage = File("/cache/photo.jpg"),
            plotId = plotId,
            modelVersionId = config.id,
            imageWidth = 800,
            imageHeight = 600,
            source = ScanSource.Camera,
            confThreshold = 0.25,
            iouThreshold = 0.45,
            detections = listOf(NewDetection(0, 0.9, 0.1, 0.1, 0.2, 0.2)),
        ),
        config.lowConfidenceThreshold,
    )

    private suspend fun <T> StateFlow<T>.await(what: String, predicate: (T) -> Boolean): T =
        try {
            withTimeout(5_000) { first(predicate) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for $what; last value $value", e)
        }

    private fun settingsVm() =
        SettingsViewModel(application, store, scans, plots, files, library, PermissionHistory(context), config.confThreshold)

    @Test
    fun theSliderSnapsAndSavesOnReleaseAndResetGoesBackToTheDefault() = runBlocking {
        val vm = settingsVm()
        vm.onThresholdChange(0.4123)
        assertEquals(0.4123, vm.state.value.shownThreshold, 0.0)
        vm.onThresholdChangeEnd(0.4123)
        vm.state.await("saved 0.4") { it.confThreshold == 0.4 && it.local.thresholdDraft == null }
        store.state.await("stored") { it.confThreshold == 0.4 }
        assertEquals(0.4, settingsRepo.getSettings().confThreshold, 0.0)
        vm.resetThreshold()
        vm.state.await("default") { it.isDefaultThreshold }
    }

    @Test
    fun locationTurnsOnOnlyWithPermission() = runBlocking {
        val vm = settingsVm()
        vm.onLocationChecked(granted = false, showRationale = false)
        assertEquals(LocationAccess.Ask, vm.state.value.local.locationAccess)
        assertTrue(vm.toggleGps(true)) // the screen shows the prompt
        vm.onLocationResult(granted = false, showRationale = true) // refused
        assertEquals(SettingsViewModel.LocationMessage.Ask, vm.state.value.locationMessage)
        vm.onLocationResult(granted = false, showRationale = false) // refused again: blocked
        assertEquals(SettingsViewModel.LocationMessage.Blocked, vm.state.value.locationMessage)
        vm.onLocationResult(granted = true, showRationale = false) // allowed in system settings
        vm.state.await("gps on") { it.gpsOptIn }
        assertEquals(SettingsViewModel.LocationMessage.On, vm.state.value.locationMessage)
        assertTrue(!vm.toggleGps(false))
        vm.state.await("gps off") { !it.gpsOptIn }
    }

    @Test
    fun clearAllDataAsksFirstThenRemovesScansPlotsAndTheUndoOffer() = runBlocking {
        val vm = settingsVm()
        vm.state.await("counted empty") { it.local.summary?.scans == 0 }
        vm.askClear()
        vm.state.await("nothing to clear") { it.local.toast == SettingsViewModel.Toast.ClearNothing }

        val plot = plots.createPlot("North")
        val id = save(plot.id)
        save()
        library.deleteScan(id)
        vm.state.await("counted") { it.local.summary?.scans == 1 && it.local.summary?.plots == 1 }
        vm.askClear()
        assertTrue(vm.state.value.local.confirmingClear)
        vm.confirmClear()
        vm.state.await("cleared") { it.local.toast == SettingsViewModel.Toast.Cleared }
        assertNull(library.pendingUndo.value)
        assertEquals(0, scans.countScans())
        assertEquals(0, plots.countPlots())
        vm.state.await("recounted") { it.local.summary?.scans == 0 }
        Unit
    }

    @Test
    fun retrainingExportSizesEachScope() = runBlocking {
        save()
        val vm = RetrainingExportViewModel(application, scans, classes, files, config)
        vm.onOpened()
        val sized = vm.state.await("sized") { it.summaries != null }
        assertEquals(0, sized.summaries!![ExportScope.Corrected]!!.scans)
        assertEquals(1, sized.summaries!![ExportScope.Early]!!.scans)
        assertEquals(1, sized.summaries!![ExportScope.All]!!.scans)
        assertTrue(!sized.canExport) // Corrected is chosen and empty
        vm.choose(ExportScope.All)
        assertTrue(vm.state.value.canExport)
    }

    @Test
    fun plotsRejectDuplicatesRenameDeleteAndOpenHistory() = runBlocking {
        val vm = PlotsViewModel(plots, library, requests)
        vm.state.await("loaded") { it.load is PlotsViewModel.Load.Ready }
        vm.setName("North")
        vm.add()
        vm.state.await("added") { it.plots.size == 1 && it.name == "" }
        vm.setName(" north ")
        vm.add()
        assertEquals(PlotsViewModel.Problem.Duplicate, vm.state.value.problem)

        val north = vm.state.value.plots.single()
        vm.startRename(north)
        vm.rename("River")
        vm.state.await("renamed") { it.plots.singleOrNull()?.name == "River" && it.renaming == null }

        vm.open(vm.state.value.plots.single())
        assertEquals(HistoryRequest.ByPlot(north.id, "River"), requests.request.value)

        vm.askDelete(vm.state.value.plots.single())
        vm.confirmDelete()
        vm.state.await("deleted") { it.plots.isEmpty() }
        Unit
    }

    @Test
    fun learnStagesListClassesMildestFirstAndAnUnknownTopicIsNull() = runBlocking {
        val stages = LearnTopicViewModel("stages", classes)
        val ready = stages.state.await("classes") { it.classes != null }
        assertEquals(LearnTopic.Stages, ready.topic)
        assertEquals(ready.classes!!.map { it.severity }.sorted(), ready.classes!!.map { it.severity })
        assertNull(LearnTopicViewModel("nope", classes).state.value.topic)
    }

    @Test
    fun devSeedDoesNothingOutsideDebugBuilds() = runBlocking {
        var seeded = 0
        val off = DevSeedViewModel({ n -> seeded += n; n }, { 0 }, { 0 }, library, enabled = false)
        off.seed(20)
        assertEquals(0, seeded)
        val on = DevSeedViewModel({ n -> seeded += n; n }, { 0 }, { seeded }, library, enabled = true)
        on.seed(20)
        on.state.await("seeded") { it.message == DevSeedViewModel.Message.Added(20) && it.count == 20 }
        Unit
    }
}
