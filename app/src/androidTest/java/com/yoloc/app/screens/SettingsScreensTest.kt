package com.yoloc.app.screens

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.R
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.saveScan
import com.yoloc.app.screens.about.AboutScreen
import com.yoloc.app.screens.about.AboutViewModel
import com.yoloc.app.screens.plots.PlotsScreen
import com.yoloc.app.screens.plots.PlotsViewModel
import com.yoloc.app.screens.settings.SettingsScreen
import com.yoloc.app.screens.settings.SettingsViewModel
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.PermissionHistory
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.theme.YoloCTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Step 5 screens, group 4 (Settings, Plots, About), against the real Room database in memory.
 * RN: settings.screens.test.tsx. The two location cases mock the permission prompt, which a
 * device test cannot answer; they are in RN's docs/TESTING.md manual checklist, which the guide
 * applies to both apps. The CSV export opens the
 * share sheet, so only its "nothing to export" case runs here.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = TestDb.context
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val plots = PlotRepository(holder)
    private val classes = ClassRepository(holder)
    private val files = MemoryFileStore()
    private val library = ScanLibrary(scans)
    private val config = TestDb.config
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = SettingsStore(SettingsRepository(holder), config, scope)

    @Before
    fun open() = runBlocking {
        holder.get()
        store.hydrate()
    }

    @After
    fun close() {
        scope.cancel()
        holder.close()
    }

    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun plural(id: Int, n: Int, vararg args: Any) = context.resources.getQuantityString(id, n, *args)

    private fun waitForText(value: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForLabel(value: String, substring: Boolean = false) = compose.waitUntil(5_000) {
        compose.onAllNodesWithContentDescription(value, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun seed(plotId: String? = null): String = runBlocking {
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
                detections = listOf(NewDetection(2, 0.9, 0.1, 0.1, 0.2, 0.2)),
            ),
            config.lowConfidenceThreshold,
        )
    }

    private fun showSettings() {
        val vm = SettingsViewModel(
            context.applicationContext as Application, store, scans, plots, files, library, PermissionHistory(context), config.confThreshold,
        )
        compose.setContent {
            YoloCTheme { SettingsScreen(onOpenPlots = {}, onOpenAbout = {}, onOpenGallery = {}, onOpenSeed = {}, onOpenInference = {}, vm = vm) }
        }
    }

    @Test
    fun savesTheConfidenceThresholdAndCanResetIt() {
        showSettings()
        val slider = text(R.string.settings_threshold)
        waitForLabel(slider)
        compose.onNodeWithContentDescription(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
        compose.waitUntil(5_000) { runBlocking { SettingsRepository(holder).getSettings().confThreshold } == 0.3 }
        compose.onNodeWithText(text(R.string.settings_threshold_value, 30)).assertExists()

        val reset = text(R.string.settings_threshold_reset, 25)
        compose.onNodeWithContentDescription(reset).performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { SettingsRepository(holder).getSettings().confThreshold } == 0.25 }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(reset).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun switchesTheThemeAndSavesIt() {
        showSettings()
        val dark = text(R.string.settings_theme_dark)
        compose.onNodeWithText(dark).performScrollTo().performClick()
        compose.waitUntil(5_000) { store.state.value.theme == ThemeSetting.Dark }
        compose.waitUntil(5_000) { runBlocking { SettingsRepository(holder).getSettings().theme } == ThemeSetting.Dark }
        compose.onNodeWithText(dark).assertIsSelected()
    }

    @Test
    fun showsStorageAndClearsAllData() {
        val north = runBlocking { plots.createPlot("North") }
        seed(north.id)
        seed()
        showSettings()
        // Each scan stores a photo and a thumbnail; the memory store counts 100 KB per file.
        val scanCount = plural(R.plurals.settings_scan_count, 2, 2)
        waitForLabel(text(R.string.settings_storage_detail, scanCount, "391 KB"), substring = true)

        val clear = text(R.string.settings_clear)
        compose.onNodeWithContentDescription(clear).performScrollTo().performClick()
        waitForText(text(R.string.settings_clear_body, scanCount, plural(R.plurals.settings_plot_count, 1, 1)))
        compose.onNodeWithText(text(R.string.settings_clear_confirm)).performClick()
        waitForText(text(R.string.settings_cleared))
        assertEquals(0, runBlocking { scans.listScans().size })
        waitForLabel(text(R.string.settings_storage_detail, plural(R.plurals.settings_scan_count, 0, 0), "0 KB"), substring = true)
    }

    @Test
    fun saysThereIsNothingToExportWithoutScans() {
        showSettings()
        val export = text(R.string.settings_export)
        waitForLabel(export, substring = true)
        compose.onNodeWithContentDescription(export, substring = true).performScrollTo().performClick()
        waitForText(text(R.string.settings_export_empty))
    }

    private fun showPlots(requests: HistoryRequests = HistoryRequests(), onOpen: () -> Unit = {}) {
        val vm = PlotsViewModel(plots, library, requests)
        compose.setContent { YoloCTheme { PlotsScreen(onOpenPlot = onOpen, vm = vm) } }
    }

    @Test
    fun addsRejectsDuplicatesRenamesAndDeletesAPlotKeepingItsScans() {
        showPlots()
        waitForText(text(R.string.plots_empty_title))
        val field = text(R.string.plots_new_placeholder)
        compose.onNodeWithContentDescription(field).performTextInput("North")
        compose.onNodeWithContentDescription(text(R.string.plots_add)).performClick()
        waitForText("North")

        compose.onNodeWithContentDescription(field).performTextInput("north")
        compose.onNodeWithContentDescription(text(R.string.plots_add)).performClick()
        waitForText(text(R.string.plots_duplicate))

        compose.onNodeWithContentDescription(text(R.string.plots_rename_a11y, "North")).performClick()
        waitForLabel(text(R.string.plots_rename_title))
        compose.onNodeWithContentDescription(text(R.string.plots_rename_title)).performTextReplacement("North block")
        compose.onNodeWithContentDescription(text(R.string.plots_save)).performClick()
        waitForLabel(text(R.string.plots_delete_a11y, "North block"))

        val plot = runBlocking { plots.listPlots() }.single()
        val scanId = seed(plot.id)
        compose.onNodeWithContentDescription(text(R.string.plots_delete_a11y, "North block")).performClick()
        compose.onNodeWithText(text(R.string.plots_delete)).performClick()
        waitForText(text(R.string.plots_empty_title))
        assertNull(runBlocking { scans.getScan(scanId) }!!.scan.plotId)
    }

    @Test
    fun opensHistoryFilteredToTheTappedPlot() {
        val north = runBlocking { plots.createPlot("North") }
        seed(north.id)
        val requests = HistoryRequests()
        var opened = 0
        showPlots(requests) { opened++ }
        val row = "North, ${plural(R.plurals.plots_scan_count, 1, 1)}"
        waitForLabel(row)
        compose.onNodeWithContentDescription(row).performClick()
        compose.waitUntil(5_000) { opened == 1 }
        assertEquals(HistoryRequest.ByPlot(north.id, "North"), requests.request.value)
    }

    @Test
    fun aboutShowsTheModelItsMetricsLimitationsAndTheDisclaimer() {
        val vm = AboutViewModel(config, classes, "1.0")
        compose.setContent { YoloCTheme { AboutScreen(vm) } }
        waitForText(text(R.string.about_disclaimer))
        compose.onNodeWithText(config.displayName).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.about_recall_headline, 6)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.about_recall_a11y, 6)).assertExists()
        compose.onNodeWithContentDescription("72.7%", substring = true).assertExists()
        compose.onNodeWithText(text(R.string.about_limitation_misses, 4)).performScrollTo().assertExists()
    }
}
