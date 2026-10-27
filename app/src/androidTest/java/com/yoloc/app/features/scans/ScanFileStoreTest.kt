package com.yoloc.app.features.scans

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yoloc.app.features.dev.GoldenSamples
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The real file store on the phone: copy, thumbnail from a real photo, sizes, removal. */
@RunWith(AndroidJUnit4::class)
class ScanFileStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AndroidScanFileStore(context)
    private val temp = TempImages(context)

    @After
    fun cleanUp() = runBlocking { store.removeAll() }

    @Test
    fun copiesAPhotoAndMakesAThumbnailWithA240PxShortSide() = runBlocking {
        val photo = GoldenSamples(context).file("golden_01.jpg") // 1024 x 1024
        store.copyIn(photo, scanImagePath("t1"))
        store.makeThumbnail(scanImagePath("t1"), scanThumbnailPath("t1"), 1024, 1024)

        val thumb = store.fileFor(scanThumbnailPath("t1"))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(thumb.path, bounds)
        assertEquals(THUMBNAIL_SHORT_SIDE, minOf(bounds.outWidth, bounds.outHeight))
        assertEquals(photo.length(), store.sizeOf(scanImagePath("t1")))
        assertEquals(photo.length() + thumb.length(), store.usedBytes())
    }

    @Test
    fun removeIgnoresMissingFilesAndRemoveAllEmptiesTheStore() = runBlocking {
        store.remove("scans/missing.jpg")
        store.copyIn(GoldenSamples(context).file("golden_02.jpg"), scanImagePath("t2"))
        assertTrue(store.sizeOf(scanImagePath("t2")) > 0)
        store.removeAll()
        assertEquals(0L, store.sizeOf(scanImagePath("t2")))
        assertEquals(0L, store.usedBytes())
    }

    @Test
    fun tempImagesDeletesOnlyFilesInsideTheCache() {
        val inCache = File(context.cacheDir, "capture-test.jpg").apply { writeText("x") }
        val outside = File(context.filesDir, "keep-test.jpg").apply { writeText("x") }
        try {
            temp.delete(inCache)
            temp.delete(outside)
            temp.delete(File(context.cacheDir, "../files/keep-test.jpg"))
            temp.delete(null)
            assertFalse(inCache.exists())
            assertTrue(outside.exists())
        } finally {
            outside.delete()
        }
    }
}
