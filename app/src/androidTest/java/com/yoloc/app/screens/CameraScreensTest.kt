package com.yoloc.app.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.R
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.features.scans.CapturedPhotos
import com.yoloc.app.features.scans.TempImages
import com.yoloc.app.screens.scan.CameraScreen
import com.yoloc.app.screens.scan.CameraViewModel
import com.yoloc.app.screens.scan.LiveScreen
import com.yoloc.app.store.PermissionHistory
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.ScanSession
import com.yoloc.app.theme.YoloCTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 5 screens, group 3 (Camera, Live) without camera access. RN: navigation.test.tsx "Scan
 * flow", the permission cases. A real capture needs the camera and is checked by hand on the phone.
 * Skipped when the test install already has camera access.
 */
@RunWith(AndroidJUnit4::class)
class CameraScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = TestDb.context
    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val permissions = PermissionHistory(context)

    @Before
    fun open() {
        assumeFalse(
            "The camera is already allowed",
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
        runBlocking { holder.get() }
    }

    @After
    fun close() {
        holder.close()
        context.getSharedPreferences("permissions", 0).edit().clear().commit()
    }

    private fun text(@StringRes id: Int) = context.getString(id)

    private fun waitForText(@StringRes id: Int) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty() }

    private fun showCamera() {
        val vm = CameraViewModel(
            ScanSession(TempImages(context)), scans, MemoryFileStore(), ScanLibrary(scans), permissions, CapturedPhotos(context),
        )
        compose.setContent { YoloCTheme { CameraScreen(onClose = {}, onOpenPreview = {}, onLive = {}, vm = vm) } }
    }

    @Test
    fun explainsBeforeAskingForTheCameraAndTheGalleryStillWorks() {
        permissions.cameraAsked = false
        showCamera()
        waitForText(R.string.camera_ask_title)
        compose.onNodeWithContentDescription(text(R.string.camera_allow)).assertIsEnabled()
        compose.onNodeWithContentDescription(text(R.string.camera_gallery)).assertIsEnabled()
        // No Photo | Live switch and no flash without the camera.
        assertEquals(0, compose.onAllNodesWithContentDescription(text(R.string.live_mode_label)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription(text(R.string.camera_flash)).fetchSemanticsNodes().size)
    }

    @Test
    fun offersSystemSettingsWhenTheCameraIsPermanentlyDenied() {
        // Asked before, and Android no longer shows a rationale: blocked.
        permissions.cameraAsked = true
        showCamera()
        waitForText(R.string.camera_blocked_body)
        compose.onNodeWithContentDescription(text(R.string.camera_open_settings)).assertIsEnabled()
        compose.onNodeWithContentDescription(text(R.string.camera_gallery)).assertIsEnabled()
    }

    @Test
    fun liveWithoutCameraAccessExplainsAndGoesBack() {
        var back = 0
        compose.setContent { YoloCTheme { LiveScreen(onClose = {}, onPhotoMode = {}, onBack = { back++ }, onOpenPreview = {}) } }
        waitForText(R.string.live_no_permission_body)
        compose.onNodeWithContentDescription(text(R.string.error_back)).performClick()
        compose.waitUntil(5_000) { back == 1 }
    }
}
