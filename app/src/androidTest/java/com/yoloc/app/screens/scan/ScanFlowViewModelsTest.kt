package com.yoloc.app.screens.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.detection.DetectionError
import com.yoloc.app.features.detection.DetectionErrorKind
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.CameraAccess
import com.yoloc.app.features.scans.CapturedPhotos
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.features.scans.ResultDetection
import com.yoloc.app.features.scans.ScanResult
import com.yoloc.app.features.scans.TempImages
import com.yoloc.app.features.settings.LocationFix
import com.yoloc.app.features.settings.LocationSource
import com.yoloc.app.store.PermissionHistory
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.ScanSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Step 4.4 part 2 ViewModels (Processing, Result, Camera, Preview) against the real Room database. */
@RunWith(AndroidJUnit4::class)
class ScanFlowViewModelsTest {
    private val context = TestDb.context
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val classes = ClassRepository(holder)
    private val files = MemoryFileStore()
    private val library = ScanLibrary(scans)
    private val session = ScanSession(TempImages(context))
    private val config = TestDb.config

    private val noLocation = object : LocationSource {
        override fun hasPermission() = true
        override suspend fun lastKnown(maxAgeMs: Long) = LocationFix(7.1, -73.1, 10f)
        override suspend fun current(): LocationFix? = null
    }

    @Before
    fun open() = runBlocking {
        // The app's own use on this phone may have left "camera asked" behind.
        context.getSharedPreferences("permissions", 0).edit().clear().commit()
        holder.get()
        Unit
    }

    @After
    fun close() {
        session.clear()
        holder.close()
        context.getSharedPreferences("permissions", 0).edit().clear().commit()
    }

    private fun photo() = PendingPhoto(File(context.cacheDir, "test-photo.jpg").apply { writeText("jpeg") }, 800, 600, ScanSource.Camera)

    private fun result(photo: PendingPhoto, vararg classIds: Int) = ScanResult(
        photo, 800, 600, classIds.map { ResultDetection(it, 0.9, 0.1, 0.1, 0.2, 0.2) },
        if (classIds.isEmpty()) Verdict.NoPod else Verdict.Healthy, classIds.size, false, config.id, 0.25, 0.45, 900, 1200, 0,
    )

    private suspend fun <T> StateFlow<T>.await(what: String, predicate: (T) -> Boolean): T =
        try {
            withTimeout(5_000) { first(predicate) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for $what; last value $value", e)
        }

    private fun resultVm(gps: Boolean = false) =
        ResultViewModel(session, scans, plots, classes, files, library, config, gps, noLocation)

    @Test
    fun processingPutsTheResultInTheSessionOrSaysWhichStageFailed() = runBlocking {
        val p = photo()
        session.setPhoto(p)
        val ok = ProcessingViewModel(session) { result(it, 2) }
        ok.state.await("done") { it == ProcessingViewModel.State.Done }
        assertEquals(p, session.state.value.result?.photo)

        val failing = ProcessingViewModel(session) { throw DetectionError(DetectionErrorKind.Image, RuntimeException("bad")) }
        val failed = failing.state.await("failed") { it is ProcessingViewModel.State.Failed } as ProcessingViewModel.State.Failed
        assertEquals(DetectionErrorKind.Image, failed.error.kind)

        session.clear()
        assertEquals(ProcessingViewModel.State.Missing, ProcessingViewModel(session) { result(it) }.state.value)
    }

    @Test
    fun resultSavesWithTheOptedInLocationThenExitsToTheSavedScan() = runBlocking {
        val p = photo()
        session.setPhoto(p)
        session.setResult(result(p, 2, 0))
        val vm = resultVm(gps = true)
        assertTrue(vm.state.value.guarded)

        vm.state.await("classes") { it.classes != null }
        vm.correct(0, 1) // healthy -> infected
        assertEquals(Verdict.Infected, vm.state.value.result?.verdict)
        assertEquals(1, session.state.value.result?.detections?.get(0)?.correctedClassId)

        vm.openSave()
        val plotId = vm.addPlot("North")
        assertNotNull(plotId)
        vm.save(plotId, "  Tree 7 ")
        val exit = vm.state.await("saved") { it.exit != null }.exit as ResultViewModel.Exit.Saved
        val saved = scans.getScan(exit.scanId)!!
        assertEquals("Tree 7", saved.scan.notes)
        assertEquals(7.1, saved.scan.latitude!!, 0.0)
        assertEquals(Verdict.Infected, saved.scan.verdict)
        assertEquals(1, library.revision.value)
        assertTrue(!vm.state.value.guarded)

        vm.exited()
        assertNull(session.state.value.photo)
        assertNotNull(vm.state.value.result) // kept for the way out
    }

    @Test
    fun leavingWithPodsAsksFirstAndDiscardExitsToTheCamera() {
        val p = photo()
        session.setPhoto(p)
        session.setResult(result(p, 2))
        val vm = resultVm()
        vm.onLeaveRequested()
        assertTrue(vm.state.value.confirmLeave)
        vm.stay()
        assertTrue(!vm.state.value.confirmLeave)
        vm.onLeaveRequested()
        vm.discard()
        assertEquals(ResultViewModel.Exit.Discard, vm.state.value.exit)
        vm.exited()
        assertTrue(!p.file.exists())
    }

    @Test
    fun cameraTreatsADismissedFirstPromptAsStillAskable() {
        val camera = CameraViewModel(session, scans, files, library, PermissionHistory(context), CapturedPhotos(context))
        camera.onPermissionChecked(granted = false, showRationale = false)
        assertEquals(CameraAccess.Ask, camera.state.value.access)
        camera.onPermissionResult(granted = false, showRationale = false) // tapped outside the dialog
        assertEquals(CameraAccess.Ask, camera.state.value.access)
        camera.onPermissionResult(granted = false, showRationale = true) // refused
        assertEquals(CameraAccess.Denied, camera.state.value.access)
        camera.onPermissionResult(granted = false, showRationale = false) // refused again: blocked
        assertEquals(CameraAccess.Blocked, camera.state.value.access)
        camera.onPermissionChecked(granted = true, showRationale = false) // allowed in system settings
        assertEquals(CameraAccess.Granted, camera.state.value.access)
    }

    @Test
    fun cameraShowsTheLatestSavedScanAndCaptureNeedsAReadyPreview() = runBlocking {
        val camera = CameraViewModel(session, scans, files, library, PermissionHistory(context), CapturedPhotos(context))
        assertNull(camera.startCapture()) // not ready yet
        camera.onCameraReady(true)
        assertNotNull(camera.startCapture())
        assertNull(camera.startCapture()) // one at a time
        camera.onCaptureFailed(RuntimeException("no photo"))
        assertEquals(CameraViewModel.Notice.CaptureFailed, camera.state.value.notice)
        assertTrue(!camera.state.value.capturing)
        assertTrue(camera.startGallery())
        camera.onGalleryResult(null) // cancelled: stay, no message
        assertTrue(!camera.state.value.picking)
        assertNull(camera.state.value.latestImage)
    }

    @Test
    fun previewRetakeClearsACameraPhotoAndPicksAgainForAGalleryOne() {
        val preview = PreviewViewModel(session, CapturedPhotos(context))
        val p = photo()
        session.setPhoto(p)
        assertEquals(PreviewViewModel.Retake.BackToCamera, preview.retake())
        assertNull(session.state.value.photo)
        session.setPhoto(photo().copy(source = ScanSource.Gallery))
        assertEquals(PreviewViewModel.Retake.PickAnother, preview.retake())
        assertNotNull(session.state.value.photo)
        preview.onGalleryResult(null) // cancelled: keeps the photo
        assertNotNull(session.state.value.photo)
    }
}
