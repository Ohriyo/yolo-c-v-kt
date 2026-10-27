package com.yoloc.app.features.scans

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.util.uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Photos entering the scan flow (RN: takePictureAsync, pickGalleryPhoto and openImage). Every one
 * is a file in the cache, which is what keeps TempImages.delete safe: a gallery pick is copied in
 * first, like RN's picker hands back a cache copy.
 */
class CapturedPhotos(context: Context) {
    private val cacheDir = context.cacheDir
    private val resolver = context.contentResolver

    /** Where CameraX writes the next photo. */
    fun newCaptureFile(): File = File(cacheDir, "capture-${uuid()}.jpg")

    /** A photo CameraX wrote, with the upright size the pipeline will see. Deletes the file if it is not an image. */
    suspend fun fromCamera(file: File): PendingPhoto = withContext(Dispatchers.IO) {
        try {
            val (width, height) = orientedSize(file)
            PendingPhoto(file, width, height, ScanSource.Camera)
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }

    /** Copies a photo picker result into the cache. Throws if it cannot be read. */
    suspend fun fromGallery(uri: Uri): PendingPhoto = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "picked-${uuid()}.jpg")
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
            input.use { src -> file.outputStream().use { src.copyTo(it) } }
            val (width, height) = orientedSize(file)
            PendingPhoto(file, width, height, ScanSource.Gallery)
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }
}

/** Width and height after EXIF rotation, read from the header only (no full decode). */
fun orientedSize(file: File): Pair<Int, Int> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: ${file.name}")
    val turned = ExifInterface(file).rotationDegrees.let { it == 90 || it == 270 }
    return if (turned) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
}
