package com.yoloc.app.features.scans

import android.content.Context
import android.util.Log
import java.io.File

/** Unsaved photos (camera captures and picker copies) in the cache (RN: tempImage.ts). */
class TempImages(context: Context) {
    private val cacheDir = context.cacheDir

    /**
     * Deletes an unsaved photo. Only files inside the app's cache are touched, so a file that is
     * the user's own photo can never be deleted. Never throws: cleanup must not turn a discard into
     * an error.
     */
    fun delete(file: File?) {
        if (file == null) return
        try {
            if (!isInsideCache(file, cacheDir)) return
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete temporary photo", e)
        }
    }

    private companion object {
        const val TAG = "TempImages"
    }
}

/** Compares canonical paths, so `cache/../files/x.jpg` does not count as inside the cache. */
internal fun isInsideCache(file: File, cacheDir: File): Boolean =
    file.canonicalPath.startsWith(cacheDir.canonicalPath + File.separator)
