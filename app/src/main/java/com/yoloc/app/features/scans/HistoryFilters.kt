package com.yoloc.app.features.scans

import android.content.Context
import com.yoloc.app.R
import com.yoloc.app.db.repositories.PlotFilter
import com.yoloc.app.db.repositories.ScanFilters
import com.yoloc.app.db.repositories.ScanSummary
import com.yoloc.app.features.detection.Verdict
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* RN: historyFilters.ts. Times are epoch ms, as in the database; days are local days in `zone`. */

enum class DateRange { All, Today, Week, Month }

/** What the History filter bar controls. */
data class HistoryFilter(
    val verdicts: List<Verdict> = emptyList(),
    val range: DateRange = DateRange.All,
    val plot: PlotFilter = PlotFilter.Any,
    val search: String = "",
)

/** Verdicts in the order the filter shows them, most severe first. */
val VERDICT_ORDER = listOf(Verdict.Infected, Verdict.EarlyStage, Verdict.Healthy, Verdict.NoPod)

const val DAY_MS = 24 * 60 * 60 * 1000L

/** Local midnight of the day containing `t`. */
fun startOfDay(t: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Earliest created_at for a range, counted in whole local days including today. Counts calendar
 * days; RN subtracts 24-hour multiples, which is a day off right after a daylight-saving change.
 */
fun rangeStart(range: DateRange, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val daysBack = when (range) {
        DateRange.All -> return null
        DateRange.Today -> 0L
        DateRange.Week -> 6L
        DateRange.Month -> 29L
    }
    return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(daysBack)
        .atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Repository filters for a History filter (paging is added by the caller). */
fun toScanFilters(filter: HistoryFilter, now: Long, zone: ZoneId = ZoneId.systemDefault()): ScanFilters =
    ScanFilters(
        verdicts = filter.verdicts.ifEmpty { null },
        plot = filter.plot,
        from = rangeStart(filter.range, now, zone),
        search = filter.search.trim().ifEmpty { null },
    )

/** Number of filters (not counting search) that narrow the list, for the "Clear" affordance. */
fun activeFilterCount(filter: HistoryFilter): Int =
    (if (filter.verdicts.isNotEmpty()) 1 else 0) +
        (if (filter.range != DateRange.All) 1 else 0) +
        (if (filter.plot != PlotFilter.Any) 1 else 0)

fun isFiltered(filter: HistoryFilter) = activeFilterCount(filter) > 0 || filter.search.isNotBlank()

/** A row in the History list: a day header or a scan. `key` and `type` feed LazyColumn. */
sealed interface HistoryItem {
    val key: String
    val type: String

    data class Day(override val key: String, val label: String) : HistoryItem {
        override val type get() = "day"
    }

    data class Scan(override val key: String, val scan: ScanSummary) : HistoryItem {
        override val type get() = "scan"
    }
}

/**
 * How days and times read on this device. RN builds Intl.DateTimeFormat once; the app builds this
 * once from the device locale ([forDevice]); tests pass a fixed zone and formatters.
 */
class DayFormat(
    val today: String,
    val yesterday: String,
    /** "Mon 12 Oct" */
    val weekdayDate: (Long) -> String,
    /** "12 Oct 2025" */
    val fullDate: (Long) -> String,
    /** "14:32" or "2:32 PM", following the device's 12/24-hour setting */
    val time: (Long) -> String,
    val zone: ZoneId,
) {
    companion object {
        /** A java.time formatter as a DayFormat function (tests, and any fixed pattern). */
        fun formatter(f: DateTimeFormatter, zone: ZoneId): (Long) -> String =
            { t -> f.format(Instant.ofEpochMilli(t).atZone(zone)) }

        /**
         * Device patterns come from ICU (getBestDateTimePattern) and are formatted with ICU too:
         * java.time on API 26-33 rejects ICU-only letters some locales use, such as `B` (day period).
         */
        fun forDevice(context: Context): DayFormat = forLocale(
            locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
            zone = ZoneId.systemDefault(),
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
            today = context.getString(R.string.history_today),
            yesterday = context.getString(R.string.history_yesterday),
        )

        fun forLocale(locale: Locale, zone: ZoneId, is24Hour: Boolean, today: String, yesterday: String): DayFormat {
            fun best(skeleton: String): (Long) -> String {
                val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
                val icu = android.icu.text.SimpleDateFormat(pattern, locale).apply {
                    timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
                }
                return { t -> synchronized(icu) { icu.format(java.util.Date(t)) } }
            }
            return DayFormat(
                today = today,
                yesterday = yesterday,
                weekdayDate = best("EEEdMMM"),
                fullDate = best("dMMMyyyy"),
                time = best(if (is24Hour) "Hmm" else "hmm"),
                zone = zone,
            )
        }
    }
}

/**
 * Interleaves day headers ("Today", "Yesterday", "Mon 12 Oct") into a newest-first scan list,
 * so a long history reads as a field log instead of an undifferentiated column.
 */
fun groupByDay(scans: List<ScanSummary>, now: Long, format: DayFormat): List<HistoryItem> {
    val items = ArrayList<HistoryItem>()
    var currentDay: Long? = null
    for (scan in scans) {
        val day = startOfDay(scan.createdAt, format.zone)
        if (day != currentDay) {
            currentDay = day
            items += HistoryItem.Day("day-$day", dayLabel(day, now, format))
        }
        items += HistoryItem.Scan(scan.id, scan)
    }
    return items
}

fun dayLabel(dayStart: Long, now: Long, format: DayFormat): String {
    val zone = format.zone
    val today = startOfDay(now, zone)
    if (dayStart == today) return format.today
    if (dayStart == startOfDay(today - DAY_MS / 2, zone)) return format.yesterday
    val sameYear = Instant.ofEpochMilli(dayStart).atZone(zone).year == Instant.ofEpochMilli(now).atZone(zone).year
    return if (sameYear) format.weekdayDate(dayStart) else format.fullDate(dayStart)
}

fun formatTime(t: Long, format: DayFormat) = format.time(t)

/** Fits a row's corner: the time for today's scans, otherwise just the day (Home). */
fun formatShortWhen(t: Long, now: Long, format: DayFormat): String {
    val day = startOfDay(t, format.zone)
    return if (day == startOfDay(now, format.zone)) format.time(t) else dayLabel(day, now, format)
}

/** "Today, 14:32" / "Mon 12 Oct, 09:05": for places without a day header (Scan Detail). */
fun formatDateTime(t: Long, now: Long, format: DayFormat): String =
    "${dayLabel(startOfDay(t, format.zone), now, format)}, ${format.time(t)}"
