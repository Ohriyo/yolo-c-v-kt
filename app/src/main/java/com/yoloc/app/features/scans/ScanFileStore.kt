package com.yoloc.app.features.scans

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.yoloc.app.features.detection.decodeOriented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where scan images live (RN: scanFileStore.ts). The DB stores only paths relative to filesDir,
 * so the app keeps working if the absolute path changes between installs.
 */
interface ScanFileStore {
    /** Copies a temp/picked image into the store at `relativePath`. */
    suspend fun copyIn(source: File, relativePath: String)

    /**
     * Writes a small JPEG of a stored image whose shorter side is [THUMBNAIL_SHORT_SIDE] px, for
     * lists. `width` and `height` are the stored image's upright size.
     */
    suspend fun makeThumbnail(relativeSource: String, relativeDest: String, width: Int, height: Int)

    /** Deletes a stored file; a missing file is not an error. */
    suspend fun remove(relativePath: String)

    /** Deletes every stored scan image and thumbnail (Clear all data). */
    suspend fun removeAll()

    /** Bytes used by stored scan images and thumbnails. */
    suspend fun usedBytes(): Long

    /** Reads a stored file whole (one photo at a time, for exports). */
    suspend fun readBytes(relativePath: String): ByteArray

    /** Size of a stored file in bytes; 0 if it is missing. */
    fun sizeOf(relativePath: String): Long

    /** Absolute file for display (RN: uriFor). */
    fun fileFor(relativePath: String): File
}

const val SCANS_DIR = "scans"

/**
 * Shorter side of a thumbnail in px. History shows thumbnails at 72 dp, so this covers about 3x
 * density while keeping each file near 15 KB, which keeps a 500-scan list light on memory.
 */
const val THUMBNAIL_SHORT_SIDE = 240

/** RN compresses thumbnails at 0.7. */
private const val THUMBNAIL_JPEG_QUALITY = 70

fun scanImagePath(id: String) = "$SCANS_DIR/$id.jpg"

fun scanThumbnailPath(id: String) = "$SCANS_DIR/${id}_thumb.jpg"

/** Root is context.filesDir (RN: Paths.document). */
class AndroidScanFileStore(context: Context) : ScanFileStore {
    private val root = context.filesDir

    override suspend fun copyIn(source: File, relativePath: String) = withContext(Dispatchers.IO) {
        val dest = fileFor(relativePath)
        dest.parentFile?.mkdirs()
        source.copyTo(dest, overwrite = true)
        Unit
    }

    override suspend fun makeThumbnail(relativeSource: String, relativeDest: String, width: Int, height: Int) =
        withContext(Dispatchers.IO) {
            // Sample down while decoding so a 12 MP photo never loads whole for a 240 px thumbnail.
            // Rotation does not change the short side, so the upright size picks the sample size.
            val oriented = decodeOriented(fileFor(relativeSource), sampleSizeFor(min(width, height)))
            val scale = min(1.0, THUMBNAIL_SHORT_SIDE.toDouble() / min(oriented.width, oriented.height))
            val thumb = oriented.scale(
                maxOf(1, (oriented.width * scale).roundToInt()),
                maxOf(1, (oriented.height * scale).roundToInt()),
            )
            try {
                // Write next to the destination, then rename, so a half-written file never shows.
                val dest = fileFor(relativeDest)
                val temp = File(dest.parentFile, "${dest.name}.tmp")
                temp.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, it) }
                if (dest.exists()) dest.delete()
                check(temp.renameTo(dest)) { "Could not write thumbnail $relativeDest" }
            } finally {
                if (thumb !== oriented) thumb.recycle()
                oriented.recycle()
            }
        }

    override suspend fun remove(relativePath: String) = withContext(Dispatchers.IO) {
        fileFor(relativePath).delete()
        Unit
    }

    override suspend fun removeAll() = withContext(Dispatchers.IO) {
        File(root, SCANS_DIR).deleteRecursively()
        Unit
    }

    override suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        File(root, SCANS_DIR).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    override suspend fun readBytes(relativePath: String): ByteArray =
        withContext(Dispatchers.IO) { fileFor(relativePath).readBytes() }

    override fun sizeOf(relativePath: String): Long = fileFor(relativePath).let { if (it.isFile) it.length() else 0L }

    override fun fileFor(relativePath: String): File = File(root, relativePath)
}

/** Largest power of two that keeps the decoded short side at or above the thumbnail size. */
internal fun sampleSizeFor(shortSide: Int): Int {
    var sample = 1
    while (shortSide / (sample * 2) >= THUMBNAIL_SHORT_SIDE) sample *= 2
    return sample
}
