package com.yoloc.app.screens.settings

import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.features.export.ExportScope
import com.yoloc.app.features.export.ExportSummary
import com.yoloc.app.features.export.SPACE_MARGIN_BYTES
import com.yoloc.app.features.settings.LocationAccess
import com.yoloc.app.screens.plots.sameName
import com.yoloc.app.screens.settings.SettingsViewModel.LocationMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of the Step 4.4 part 3 ViewModels; the rest runs on the phone. */
class SettingsHelpersTest {
    private fun settings(gps: Boolean, access: LocationAccess?, refused: Boolean = false, threshold: Double = 0.25) =
        SettingsViewModel.State(
            SettingsViewModel.Local(locationAccess = access, locationRefused = refused), threshold, 0.25, ThemeSetting.System, gps,
        )

    @Test
    fun theLocationRowSaysWhatIsWrongAndWhatToDo() {
        assertEquals(LocationMessage.Off, settings(gps = false, access = LocationAccess.Ask).locationMessage)
        assertEquals(LocationMessage.On, settings(gps = true, access = LocationAccess.Granted).locationMessage)
        assertEquals(LocationMessage.Ask, settings(gps = false, access = LocationAccess.Ask, refused = true).locationMessage)
        assertEquals(LocationMessage.Blocked, settings(gps = false, access = LocationAccess.Blocked, refused = true).locationMessage)
        // On in the app, but permission removed in system settings.
        assertEquals(LocationMessage.Revoked, settings(gps = true, access = LocationAccess.Blocked).locationMessage)
        assertTrue(settings(gps = true, access = LocationAccess.Ask).locationProblem)
        // Unknown permission (not checked yet) is not a problem.
        assertFalse(settings(gps = true, access = null).locationProblem)
    }

    @Test
    fun resetIsOfferedOnlyAwayFromTheDefault() {
        assertTrue(settings(gps = false, access = null, threshold = 0.25).isDefaultThreshold)
        assertFalse(settings(gps = false, access = null, threshold = 0.4).isDefaultThreshold)
    }

    @Test
    fun exportNeedsScansAndRoom() {
        val sums = mapOf(ExportScope.Corrected to ExportSummary(0, 0), ExportScope.All to ExportSummary(3, 1_000_000))
        val none = RetrainingExportViewModel.State(scope = ExportScope.Corrected, summaries = sums, available = null)
        assertFalse(none.canExport)
        val all = none.copy(scope = ExportScope.All)
        assertTrue(all.canExport)
        val full = all.copy(available = 1_000_000 + SPACE_MARGIN_BYTES - 1)
        assertTrue(full.noSpace)
        assertFalse(full.canExport)
        assertFalse(all.copy(working = true).canExport)
        assertFalse(RetrainingExportViewModel.State().canExport) // still counting
    }

    @Test
    fun plotNamesMatchIgnoringCaseAccentsAndSpaces() {
        assertTrue(sameName("North block", "  north BLOCK "))
        assertTrue(sameName("Río", "rio"))
        assertFalse(sameName("North", "North 2"))
    }
}
