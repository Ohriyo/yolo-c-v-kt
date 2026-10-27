package com.yoloc.app.screens

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.R
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.DayFormat
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.ThumbnailBackfill
import com.yoloc.app.features.scans.saveScan
import com.yoloc.app.screens.history.HistoryScreen
import com.yoloc.app.screens.history.HistoryViewModel
import com.yoloc.app.screens.history.ScanDetailScreen
import com.yoloc.app.screens.history.ScanDetailViewModel
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.theme.YoloCTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Step 5 screens, group 2 (History, Scan Detail), against the real Room database in memory.
 * RN: history.test.tsx "Empty states", "History" and "Scan Detail".
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val classes = ClassRepository(holder)
    private val files = MemoryFileStore()
    private val library = ScanLibrary(scans)
    private val config = TestDb.config
    private val dayFormat = DayFormat.forDevice(TestDb.context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() {
        scope.cancel()
        holder.close()
    }

    private fun text(@StringRes id: Int, vararg args: Any) = TestDb.context.getString(id, *args)

    private fun seed(classIds: List<Int>, hoursAgo: Int = 1, notes: String? = null, plotId: String? = null): String = runBlocking {
        saveScan(
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
                notes = notes,
                createdAt = System.currentTimeMillis() - hoursAgo * 3_600_000L,
                detections = classIds.mapIndexed { i, c -> NewDetection(c, 0.9 - i * 0.1, 0.1, 0.1, 0.2, 0.2) },
            ),
            config.lowConfidenceThreshold,
        )
    }

    /** Rows are read as one label, notes last ("…, Notes: Row 4"). */
    private fun row(notes: String): SemanticsNodeInteraction = compose.onNodeWithContentDescription("Notes: $notes", substring = true)

    private fun hasRow(notes: String) = compose.onAllNodesWithContentDescription("Notes: $notes", substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(what: () -> Boolean) = compose.waitUntil(5_000, what)

    private fun showHistory(): HistoryViewModel {
        val vm = HistoryViewModel(scans, plots, library, HistoryRequests(), ThumbnailBackfill(scans, files), dayFormat)
        compose.setContent { YoloCTheme { HistoryScreen(onOpenScan = {}, onScan = {}, vm = vm, library = library, undoScope = scope) } }
        return vm
    }

    @Test
    fun anEmptyHistoryInvitesAFirstScanWithNothingToFilter() {
        showHistory()
        waitFor { compose.onAllNodesWithText(text(R.string.history_empty_title)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodesWithContentDescription(text(R.string.history_search_label)).fetchSemanticsNodes().size)
    }

    @Test
    fun listsScansSearchesNotesAndClearsFilters() {
        seed(listOf(1), hoursAgo = 1, notes = "Near the creek")
        seed(listOf(2), hoursAgo = 2, notes = "Row 4")
        showHistory()
        waitFor { hasRow("Row 4") && hasRow("Near the creek") }
        val count = TestDb.context.resources.getQuantityString(R.plurals.history_count, 2, 2)
        compose.onNodeWithText(count).assertExists()

        compose.onNodeWithContentDescription(text(R.string.history_search_label)).performTextInput("CREEK")
        waitFor { !hasRow("Row 4") }
        row("Near the creek").assertExists()

        compose.onNodeWithContentDescription(text(R.string.history_search_label)).performTextReplacement("nothing like this")
        waitFor { compose.onAllNodesWithText(text(R.string.history_no_match_title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(text(R.string.history_clear_filters))[0].performClick()
        waitFor { hasRow("Row 4") }
    }

    @Test
    fun filtersByPlotIncludingScansWithoutAPlot() {
        val north = runBlocking { plots.createPlot("North block") }
        seed(listOf(2), hoursAgo = 1, notes = "In plot", plotId = north.id)
        seed(listOf(2), hoursAgo = 2, notes = "No plot")
        showHistory()
        waitFor { hasRow("No plot") }

        compose.onNodeWithContentDescription(text(R.string.history_filter_plot)).performClick()
        waitFor { compose.onAllNodesWithContentDescription("North block").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("North block").performClick()
        waitFor { !hasRow("No plot") }
        row("In plot").assertExists()
        // The active filter shows on its pill.
        compose.onNodeWithContentDescription("North block").assertExists()
    }

    @Test
    fun deletesFromALongPressAndBringsTheScanBackWithUndo() {
        val id = seed(listOf(0), notes = "Tree 3")
        showHistory()
        waitFor { hasRow("Tree 3") }

        row("Tree 3").performTouchInput { longClick() }
        waitFor { compose.onAllNodesWithContentDescription(text(R.string.history_delete_scan)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.history_delete_scan)).performClick()
        waitFor { compose.onAllNodesWithText(text(R.string.history_deleted)).fetchSemanticsNodes().isNotEmpty() }
        waitFor { !hasRow("Tree 3") }
        assertNull(runBlocking { scans.getScan(id) })

        compose.onNodeWithContentDescription(text(R.string.history_undo)).performClick()
        waitFor { hasRow("Tree 3") }
        assertNotNull(runBlocking { scans.getScan(id) })
    }

    @Test
    fun offersDeleteAsAnAccessibilityActionOnEachRow() {
        val id = seed(listOf(2), notes = "Tree 9")
        showHistory()
        waitFor { hasRow("Tree 9") }
        val delete = row("Tree 9").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
            ?.first { it.label == text(R.string.history_delete) }
        compose.runOnIdle { delete!!.action() }
        waitFor { compose.onAllNodesWithText(text(R.string.history_deleted)).fetchSemanticsNodes().isNotEmpty() }
        assertNull(runBlocking { scans.getScan(id) })
    }

    private fun showDetail(id: String, onBack: () -> Unit = {}): ScanDetailViewModel {
        val vm = ScanDetailViewModel(id, false, scans, classes, library, files, dayFormat)
        compose.setContent { YoloCTheme { ScanDetailScreen(onBack = onBack, vm = vm) } }
        return vm
    }

    private fun verdictTitle(plural: Int, n: Int, total: Int) = TestDb.context.resources.getQuantityString(plural, total, n, total)

    @Test
    fun scanDetailShowsTheResultSavesNotesAndKeepsACorrection() {
        val id = seed(listOf(0, 2), notes = "Tree 7")
        showDetail(id)
        val early = verdictTitle(R.plurals.result_verdict_early, 1, 2)
        waitFor { compose.onAllNodesWithContentDescription(early, substring = true).fetchSemanticsNodes().isNotEmpty() }
        // The classes table (names, advice) is in before acting on pods.
        waitFor { compose.onAllNodesWithText(text(R.string.result_advice_title)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription(text(R.string.scan_detail_notes_label)).performScrollTo().performTextReplacement("Tree 7, sprayed")
        compose.onNodeWithContentDescription(text(R.string.scan_detail_save_notes)).performScrollTo().performClick()
        waitFor { compose.onAllNodesWithText(text(R.string.scan_detail_notes_saved)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Tree 7, sprayed", runBlocking { scans.getScan(id) }?.scan?.notes)

        compose.onNodeWithContentDescription(text(R.string.detection_pod_number, 1), substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.result_correct_label)).performScrollTo().performClick()
        waitFor { compose.onAllNodesWithContentDescription(text(R.string.status_infected)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.status_infected)).performClick()
        val infected = verdictTitle(R.plurals.result_verdict_infected, 1, 2)
        waitFor { compose.onAllNodesWithContentDescription(infected, substring = true).fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { scans.getScan(id) }!!
        assertEquals(Verdict.Infected, saved.scan.verdict)
        assertEquals(1, saved.detections.first { it.classId == 0 }.correctedClassId)
    }

    @Test
    fun scanDetailDeletesAfterConfirmationAndGoesBack() {
        val id = seed(listOf(2))
        var back = 0
        showDetail(id, onBack = { back++ })
        waitFor { compose.onAllNodesWithContentDescription(text(R.string.scan_detail_delete_scan)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.scan_detail_delete_scan)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.scan_detail_delete_title)).assertExists()
        compose.onNodeWithText(text(R.string.scan_detail_delete)).performClick()
        waitFor { back == 1 }
        assertNull(runBlocking { scans.getScan(id) })
        // Undo is offered on the screen the user returns to.
        assertNotNull(library.pendingUndo.value)
    }

    @Test
    fun scanDetailSaysSoWhenTheScanDoesNotExist() {
        showDetail("missing-id")
        waitFor { compose.onAllNodesWithText(text(R.string.scan_detail_not_found_title)).fetchSemanticsNodes().isNotEmpty() }
    }
}
