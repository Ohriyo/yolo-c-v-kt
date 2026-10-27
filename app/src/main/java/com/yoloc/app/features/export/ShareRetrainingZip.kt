package com.yoloc.app.features.export

import android.content.Context
import com.yoloc.app.BuildConfig
import com.yoloc.app.R
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.util.localDateStamp
import com.yoloc.app.util.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

/** yolo-c-retraining-<scope>-2026-10-17.zip, in local time. */
fun retrainingZipName(scope: ExportScope, now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "yolo-c-retraining-${scope.key}-${localDateStamp(now, zone)}.zip"

/** RN: RetrainingExportSheet. Leave room for Android and other apps; never fill the phone to the last megabyte. */
const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024

/** Free space where the zip is written (RN: Paths.availableDiskSpace), or null when Android cannot say. */
fun availableBytes(context: Context): Long? =
    runCatching { context.cacheDir.usableSpace }.getOrNull()?.takeIf { it > 0 }

/** The sheet's "not enough space" state; unknown free space does not block the export. */
fun lacksSpaceFor(summary: ExportSummary, available: Long?): Boolean =
    available != null && summary.bytes + SPACE_MARGIN_BYTES > available

/**
 * RN: export/shareRetrainingZip.ts. Writes the retraining zip into the app cache, streaming it to
 * disk, then opens the share sheet. The cached file is replaced by the next export of the same
 * scope and day, and Android may clear the cache when space runs low, so exports never pile up on
 * the phone. A failed or cancelled export deletes its partial file.
 */
suspend fun shareRetrainingZip(
    context: Context,
    scans: ScanRepository,
    classes: ClassRepository,
    files: ScanFileStore,
    config: ModelConfig,
    scope: ExportScope,
    now: Long = System.currentTimeMillis(),
): ExportResult {
    val file = File(context.cacheDir, retrainingZipName(scope, now))
    val result = withContext(Dispatchers.IO) {
        file.delete()
        try {
            file.outputStream().buffered().use {
                writeRetrainingZip(scans, classes, files, it, scope, config, BuildConfig.VERSION_NAME, now)
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }
    shareFile(context, file, "application/zip", context.getString(R.string.settings_retrain_dialog_title))
    return result
}
