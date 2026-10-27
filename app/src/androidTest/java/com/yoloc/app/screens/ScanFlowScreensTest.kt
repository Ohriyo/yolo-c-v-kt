package com.yoloc.app.screens

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.R
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.detection.DetectionError
import com.yoloc.app.features.detection.DetectionErrorKind
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.features.scans.ResultDetection
import com.yoloc.app.features.scans.ScanResult
import com.yoloc.app.features.scans.TempImages
import com.yoloc.app.features.scans.ui.SAVE_SCAN_CONFIRM_TAG
import com.yoloc.app.features.settings.LocationFix
import com.yoloc.app.features.settings.LocationSource
import com.yoloc.app.screens.scan.ProcessingScreen
import com.yoloc.app.screens.scan.ProcessingViewModel
import com.yoloc.app.screens.scan.ResultScreen
import com.yoloc.app.screens.scan.ResultViewModel
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.ScanSession
import com.yoloc.app.theme.YoloCTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Step 5 screens, group 3 (Processing, Result, the save sheet), against the real Room database in
 * memory. RN: navigation.test.tsx "Scan flow" (the parts after the camera) and "Result: save,
 * correct, plots". Navigation itself is the NavHost's; here the exits are counted.
 */
@RunWith(AndroidJUnit4::class)
class ScanFlowScreensTest {
    @get:Rule
    val compose = createComposeRule()

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
        override fun hasPermission() = false
        override suspend fun lastKnown(maxAgeMs: Long): LocationFix? = null
        override suspend fun current(): LocationFix? = null
    }

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() {
        session.clear()
        holder.close()
    }

    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitForText(value: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForLabel(value: String, substring: Boolean = false) = compose.waitUntil(5_000) {
        compose.onAllNodesWithContentDescription(value, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun photo() = PendingPhoto(File(context.cacheDir, "test-photo.jpg").apply { writeText("jpeg") }, 1024, 768, ScanSource.Gallery)

    /** RN's analyze mock: an early-stage pod and a healthy one. */
    private fun twoPods(photo: PendingPhoto, lowConfidence: Boolean = false) = ScanResult(
        photo, 1024, 768,
        listOf(ResultDetection(0, 0.91, 0.1, 0.2, 0.3, 0.4), ResultDetection(2, 0.8, 0.5, 0.2, 0.3, 0.4)),
        Verdict.EarlyStage, 2, lowConfidence, config.id, 0.25, 0.45, 1375, 3800, 0,
    )

    private fun noPods(photo: PendingPhoto) =
        ScanResult(photo, 1024, 768, emptyList(), Verdict.NoPod, 0, false, config.id, 0.25, 0.45, 900, 1200, 0)

    private class Exits {
        var discarded = 0
        var saved: String? = null
        var plots = 0
    }

    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun showResult(make: (PendingPhoto) -> ScanResult = { twoPods(it) }): Pair<ResultViewModel, Exits> {
        val p = photo()
        session.setPhoto(p)
        session.setResult(make(p))
        val vm = ResultViewModel(session, scans, plots, classes, files, library, config, false, noLocation)
        val exits = Exits()
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            YoloCTheme {
                ResultScreen(
                    vm = vm,
                    onDiscarded = { exits.discarded++ },
                    onSaved = { exits.saved = it },
                    onManagePlots = { exits.plots++ },
                    onBackToCamera = {},
                )
            }
        }
        return vm to exits
    }

    private fun verdictEarly() = context.resources.getQuantityString(R.plurals.result_verdict_early, 2, 1, 2)

    /** RN: openResult(). Classes (names, advice) load from the database; wait for them before acting. */
    private fun openResult(): Pair<ResultViewModel, Exits> {
        val shown = showResult()
        waitForLabel(verdictEarly(), substring = true)
        waitForText(text(R.string.result_advice_title))
        return shown
    }

    private fun openSaveSheet() {
        compose.onNodeWithContentDescription(text(R.string.result_save_scan)).performClick()
        waitForText(text(R.string.result_save_title))
    }

    @Test
    fun processingSaysWhichStageFailedAndTryAgainRecovers() {
        val p = photo()
        session.setPhoto(p)
        var calls = 0
        val vm = ProcessingViewModel(session) {
            calls++
            if (calls == 1) throw DetectionError(DetectionErrorKind.Image, RuntimeException("bad JPEG"))
            twoPods(it)
        }
        var done = 0
        compose.setContent { YoloCTheme { ProcessingScreen(onDone = { done++ }, onBack = {}, onBackToCamera = {}, vm = vm) } }
        waitForText(text(R.string.processing_image_error_title))
        compose.onNodeWithContentDescription(text(R.string.error_retry)).performClick()
        compose.waitUntil(5_000) { done == 1 }
        assertEquals(2, calls)
    }

    @Test
    fun aNoPodResultShowsTipsNeverHealthyAndLeavesWithoutAsking() {
        val (_, exits) = showResult { noPods(it) }
        waitForText(text(R.string.verdict_no_pod_title))
        compose.onNodeWithText(text(R.string.result_no_pod_tip_fill)).assertExists()
        assertEquals(0, compose.onAllNodesWithText(text(R.string.status_healthy)).fetchSemanticsNodes().size)
        // No unsaved pods: back is not intercepted.
        compose.runOnIdle { assertFalse(dispatcher.hasEnabledCallbacks()) }

        compose.onNodeWithContentDescription(text(R.string.result_retake)).performScrollTo().performClick()
        compose.waitUntil(5_000) { exits.discarded == 1 }
        compose.waitUntil(5_000) { session.state.value.result == null }
    }

    @Test
    fun aLowConfidenceResultShowsTheUncertainBanner() {
        showResult { twoPods(it, lowConfidence = true) }
        waitForLabel(text(R.string.verdict_uncertain), substring = true)
    }

    @Test
    fun backOnAnUnsavedResultAsksFirstStayKeepsItDiscardLeaves() {
        val (_, exits) = openResult()
        Espresso.pressBack()
        waitForText(text(R.string.result_leave_title))
        compose.onNodeWithText(text(R.string.result_stay)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.result_leave_title)).fetchSemanticsNodes().isEmpty() }
        assertEquals(0, exits.discarded)

        Espresso.pressBack()
        waitForText(text(R.string.result_leave_title))
        compose.onNodeWithText(text(R.string.result_discard)).performClick()
        compose.waitUntil(5_000) { exits.discarded == 1 }
        // Nothing is persisted, and discarding clears the unsaved session.
        compose.waitUntil(5_000) { session.state.value.photo == null && session.state.value.result == null }
        assertEquals(0, runBlocking { scans.listScans().size })
    }

    @Test
    fun saveFromTheLeavePromptOpensTheSaveSheetInsteadOfLeaving() {
        val (_, exits) = openResult()
        Espresso.pressBack()
        waitForText(text(R.string.result_leave_title))
        compose.onNodeWithText(text(R.string.result_save)).performClick()
        waitForText(text(R.string.result_save_title))
        assertEquals(0, exits.discarded)
    }

    @Test
    fun discardLeavesWithoutAPromptAndClearsTheSession() {
        val (_, exits) = openResult()
        compose.onNodeWithContentDescription(text(R.string.result_discard)).performClick()
        compose.waitUntil(5_000) { exits.discarded == 1 }
        assertEquals(0, compose.onAllNodesWithText(text(R.string.result_leave_title)).fetchSemanticsNodes().size)
        compose.waitUntil(5_000) { session.state.value.result == null }
    }

    @Test
    fun savesAtomicallyCopyingThePhotoAndWritingTheScanAndPods() {
        val (_, exits) = openResult()
        openSaveSheet()
        compose.onNodeWithTag(SAVE_SCAN_CONFIRM_TAG).performScrollTo().performClick()
        compose.waitUntil(5_000) { exits.saved != null }

        val saved = runBlocking { scans.getScan(exits.saved!!) }!!
        assertEquals(Verdict.EarlyStage, saved.scan.verdict)
        assertEquals(2, saved.scan.totalPods)
        assertEquals(ScanSource.Gallery, saved.scan.source)
        assertEquals("scans/${saved.scan.id}.jpg", saved.scan.imagePath)
        assertEquals(1375, saved.scan.inferenceMs)
        assertNull(saved.scan.plotId)
        assertEquals(2, saved.detections.size)
        assertEquals(File(context.cacheDir, "test-photo.jpg").path, files.files["scans/${saved.scan.id}.jpg"])
        compose.waitUntil(5_000) { session.state.value.result == null }
    }

    @Test
    fun correctsALabelSoTheVerdictFollowsAndTheCorrectionIsSaved() {
        val (_, exits) = openResult()
        // Select pod 1 (early stage) from the list, then correct it to fully infected.
        compose.onNodeWithContentDescription(text(R.string.detection_pod_number, 1), substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.result_correct_label)).performScrollTo().performClick()
        waitForText(text(R.string.result_correct_title))
        compose.onNodeWithContentDescription(text(R.string.status_infected)).performClick()
        waitForLabel(context.resources.getQuantityString(R.plurals.result_verdict_infected, 2, 1, 2), substring = true)
        waitForLabel(text(R.string.detection_corrected_from, text(R.string.status_early)), substring = true)

        openSaveSheet()
        compose.onNodeWithTag(SAVE_SCAN_CONFIRM_TAG).performScrollTo().performClick()
        compose.waitUntil(5_000) { exits.saved != null }
        val saved = runBlocking { scans.getScan(exits.saved!!) }!!
        assertEquals(Verdict.Infected, saved.scan.verdict)
        val corrected = saved.detections.first { it.classId == 0 }
        assertEquals(1, corrected.correctedClassId)
        assertNotNull(corrected.correctedAt)
    }

    @Test
    fun addsANewPlotInTheSheetAndSavesTheScanToIt() {
        val (_, exits) = openResult()
        openSaveSheet()
        compose.onNodeWithContentDescription(text(R.string.result_new_plot_placeholder)).performTextInput("River block")
        compose.onNodeWithContentDescription(text(R.string.result_add_plot)).performClick()
        waitForLabel("River block")
        compose.onNodeWithContentDescription(text(R.string.result_notes_label)).performScrollTo().performTextInput("Tree 14")
        compose.onNodeWithTag(SAVE_SCAN_CONFIRM_TAG).performScrollTo().performClick()
        compose.waitUntil(5_000) { exits.saved != null }

        val latest = runBlocking { scans.listScans() }.first()
        assertEquals("River block", latest.plotName)
        assertEquals("Tree 14", latest.notes)
    }

    @Test
    fun keepsTheResultAndShowsAnErrorWhenSavingFails() {
        files.failNextCopy = true
        val (_, exits) = openResult()
        openSaveSheet()
        compose.onNodeWithTag(SAVE_SCAN_CONFIRM_TAG).performScrollTo().performClick()
        waitForText(text(R.string.result_save_failed))
        assertNull(exits.saved)
        assertEquals(0, runBlocking { scans.listScans().size })
        assertEquals(0, files.files.size)
        assertNotNull(session.state.value.result)
    }

    @Test
    fun managePlotsClosesTheSheetAndOpensPlotsWithoutAsking() {
        val (_, exits) = openResult()
        openSaveSheet()
        compose.onNodeWithContentDescription(text(R.string.result_manage_plots)).performScrollTo().performClick()
        compose.waitUntil(5_000) { exits.plots == 1 }
        assertEquals(0, compose.onAllNodesWithText(text(R.string.result_leave_title)).fetchSemanticsNodes().size)
        assertNotNull(session.state.value.result)
    }
}
