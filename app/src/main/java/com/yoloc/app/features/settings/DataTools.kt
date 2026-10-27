package com.yoloc.app.features.settings

import android.content.Context
import android.util.Log
import com.yoloc.app.R
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.util.localDateStamp
import com.yoloc.app.util.shareFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.ZoneId
import java.util.Locale

private const val TAG = "dataTools"

/** U+FEFF in UTF-8 (as bytes: lint rejects a BOM inside a string literal). */
private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

data class StorageSummary(val scans: Int, val plots: Int, val bytes: Long)

/** RN: settings/dataTools.ts. What Settings shows under "Storage" and what Clear all data would remove. */
suspend fun getStorageSummary(scans: ScanRepository, plots: PlotRepository, files: ScanFileStore): StorageSummary =
    coroutineScope {
        val scanCount = async { scans.countScans() }
        val plotCount = async { plots.countPlots() }
        val bytes = async {
            try {
                files.usedBytes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0L
            }
        }
        StorageSummary(scanCount.await(), plotCount.await(), bytes.await())
    }

/** "0 KB", "830 KB", "48.2 MB", "1.3 GB", in the device locale so it is i18n-ready. */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val units = listOf("KB", "MB", "GB")
    var value = maxOf(0L, bytes) / 1024.0
    var unit = units[0]
    for (next in units.drop(1)) {
        if (value < 1024) break
        value /= 1024
        unit = next
    }
    // Built per call: NumberFormat is not thread-safe. HALF_UP is Intl.NumberFormat's rounding, and
    // Math.round is JS's (Kotlin's round() would round half to even).
    val decimal = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }
    return "${decimal.format(if (unit == "KB") Math.round(value).toDouble() else value)} $unit"
}

/** yolo-c-scans-2026-10-16.csv, in local time. */
fun csvFileName(now: Long, zone: ZoneId = ZoneId.systemDefault()): String = "yolo-c-scans-${localDateStamp(now, zone)}.csv"

/**
 * Writes every saved scan to a CSV in the app cache and opens the Android share sheet (Drive,
 * email, a messaging app, Files). The file starts with a byte-order mark so spreadsheet apps read
 * accented notes correctly. Returns false when there is nothing to export.
 */
suspend fun shareScansCsv(context: Context, scans: ScanRepository, now: Long = System.currentTimeMillis()): Boolean {
    if (scans.countScans() == 0) return false
    val csv = scans.exportScansCsv()
    val file = File(context.cacheDir, csvFileName(now))
    withContext(Dispatchers.IO) { file.writeBytes(UTF8_BOM + csv.encodeToByteArray()) } // replaces an earlier export today
    shareFile(context, file, "text/csv", context.getString(R.string.settings_export_dialog_title))
    return true
}

/**
 * Deletes every scan, plot and scan image. Settings (theme, threshold, location choice) stay.
 * Rows go first in one transaction; a failed file cleanup leaves only unreachable files behind,
 * never rows pointing at missing photos. Returns how many scans were deleted.
 */
suspend fun clearAllData(scans: ScanRepository, files: ScanFileStore): Int {
    val removed = scans.clearAllScanData()
    try {
        files.removeAll()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Some scan images could not be deleted", e)
    }
    return removed
}
