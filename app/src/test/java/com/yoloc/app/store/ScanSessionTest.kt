package com.yoloc.app.store

import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.scans.PendingPhoto
import com.yoloc.app.features.scans.ScanResult
import com.yoloc.app.features.scans.TempImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** RN: scan.logic.test.ts "scan session store", with real files in a stand-in cache folder. */
class ScanSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cache by lazy { folder.newFolder("cache") }
    private val session by lazy { ScanSession(TempImages(cache)) }

    private fun photo(name: String) = PendingPhoto(File(cache, name).apply { writeText("jpeg") }, 800, 600, ScanSource.Camera)

    private fun result(photo: PendingPhoto) = ScanResult(
        photo = photo, imageWidth = 800, imageHeight = 600, detections = emptyList(), verdict = Verdict.NoPod,
        totalPods = 0, lowConfidence = false, modelVersionId = "m", confThreshold = 0.25, iouThreshold = 0.45,
        inferenceMs = 1, totalMs = 2, analyzedAt = 0,
    )

    @Test
    fun deletesThePreviousUnsavedPhotoWhenANewOneReplacesIt() {
        val a = photo("a.jpg")
        val b = photo("b.jpg")
        session.setPhoto(a)
        session.setPhoto(b)
        assertFalse(a.file.exists())
        assertTrue(b.file.exists())
        assertEquals(b, session.state.value.photo)
    }

    @Test
    fun settingTheSamePhotoAgainKeepsItsFile() {
        val a = photo("a.jpg")
        session.setPhoto(a)
        session.setPhoto(a.copy(width = 600, height = 800))
        assertTrue(a.file.exists())
    }

    @Test
    fun aNewPhotoDropsTheOldResult() {
        val a = photo("a.jpg")
        session.setPhoto(a)
        session.setResult(result(a))
        assertEquals(a, session.state.value.result?.photo)
        session.setPhoto(photo("b.jpg"))
        assertNull(session.state.value.result)
    }

    @Test
    fun ignoresALateResultForAPhotoTheSessionNoLongerHolds() {
        val a = photo("a.jpg")
        session.setPhoto(a)
        val b = photo("b.jpg")
        session.setPhoto(b) // the user picked another photo while A was analyzed
        session.setResult(result(a))
        assertNull(session.state.value.result)
        assertEquals(b, session.state.value.photo)

        session.clear() // the user went back while B was analyzed
        session.setResult(result(b))
        assertEquals(ScanSession.State(), session.state.value)
    }

    @Test
    fun clearDeletesTheTempPhotoUnlessAskedToKeepIt() {
        val a = photo("a.jpg")
        session.setPhoto(a)
        session.clear(keepPhoto = true)
        assertTrue(a.file.exists())
        assertEquals(ScanSession.State(), session.state.value)

        val b = photo("b.jpg")
        session.setPhoto(b)
        session.clear()
        assertFalse(b.file.exists())
        assertEquals(ScanSession.State(), session.state.value)
    }

    @Test
    fun neverDeletesAFileOutsideTheCache() {
        val outside = PendingPhoto(folder.newFile("users-own.jpg"), 800, 600, ScanSource.Gallery)
        session.setPhoto(outside)
        session.clear()
        assertTrue(outside.file.exists())
    }
}
