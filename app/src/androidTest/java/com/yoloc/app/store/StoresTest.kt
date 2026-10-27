package com.yoloc.app.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.SettingCodecs
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.saveScan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** ScanLibrary and SettingsStore against the real Room database (in memory); RN covers them through its screen tests. */
@RunWith(AndroidJUnit4::class)
class StoresTest {
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val settings = SettingsRepository(holder)
    private val files = MemoryFileStore()
    private val config = TestDb.config
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() {
        scope.cancel()
        holder.close()
    }

    private suspend fun save() = saveScan(
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
            detections = listOf(NewDetection(2, 0.9, 0.1, 0.1, 0.2, 0.2)),
        ),
        config.lowConfidenceThreshold,
    )

    // ScanLibrary

    @Test
    fun deleteOffersUndoAndUndoRestoresTheScanOnce() = runBlocking {
        val library = ScanLibrary(scans)
        val id = save()
        library.deleteScan(id)
        assertNull(scans.getScan(id))
        assertEquals(id, library.pendingUndo.value?.scanId)
        assertEquals(1, library.revision.value)

        coroutineScope { repeat(2) { launch { library.undoDelete() } } } // a double tap
        assertNotNull(scans.getScan(id))
        assertNull(library.pendingUndo.value)
        assertEquals(2, library.revision.value)
    }

    @Test
    fun dismissingAnOlderUndoKeepsTheNewerOffer() = runBlocking {
        val library = ScanLibrary(scans)
        val first = save()
        val second = save()
        library.deleteScan(first)
        val oldToken = library.pendingUndo.value!!.token
        library.deleteScan(second)
        library.dismissUndo(oldToken)
        assertEquals(second, library.pendingUndo.value?.scanId)
        library.dismissUndo(library.pendingUndo.value!!.token)
        assertNull(library.pendingUndo.value)
        assertNull(scans.getScan(second)) // dismissing never restores
    }

    @Test
    fun deletingAScanThatIsAlreadyGoneKeepsTheRealUndoOffer() = runBlocking {
        val library = ScanLibrary(scans)
        val first = save()
        val second = save()
        library.deleteScan(first)
        library.deleteScan(second)
        library.deleteScan(second) // a double tap or a stale row
        assertEquals(second, library.pendingUndo.value?.scanId)
        assertEquals(3, library.revision.value) // the lists still refresh
        library.undoDelete()
        assertNotNull(scans.getScan(second))
    }

    // SettingsStore

    @Test
    fun startsIdleWithTheModelDefaultThenHydratesFromTheTable() = runBlocking {
        settings.setSetting(SettingCodecs.theme, ThemeSetting.Dark)
        val store = SettingsStore(settings, config, scope)
        assertEquals(SettingsStore.Status.Idle, store.state.value.status)
        assertEquals(config.confThreshold, store.state.value.confThreshold, 0.0)

        // Several callers at once share one load and all see it finish.
        (1..3).map { async { store.hydrate() } }.awaitAll()
        assertEquals(SettingsStore.Status.Ready, store.state.value.status)
        assertEquals(ThemeSetting.Dark, store.state.value.theme)
    }

    @Test
    fun settersUpdateTheCacheThenPersistTheLatestValue() = runBlocking {
        val store = SettingsStore(settings, config, scope)
        store.hydrate()
        store.setTheme(ThemeSetting.Light)
        store.completeOnboarding()
        store.setGpsOptIn(true)
        // A slider drag: many quick changes, written concurrently.
        coroutineScope { listOf(0.3, 0.35, 0.4, 0.45).forEach { v -> launch { store.setConfThreshold(v) } } }

        val cached = store.state.value
        val saved = settings.getSettings()
        assertEquals(ThemeSetting.Light, saved.theme)
        assertEquals(true, saved.onboardingDone)
        assertEquals(true, saved.gpsOptIn)
        assertEquals(cached.confThreshold, saved.confThreshold, 0.0)

        store.resetOnboarding()
        assertEquals(false, store.state.value.onboardingDone)
        assertEquals(false, settings.getSettings().onboardingDone)
    }
}
