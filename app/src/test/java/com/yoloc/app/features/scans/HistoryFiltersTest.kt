package com.yoloc.app.features.scans

import com.yoloc.app.db.repositories.PlotFilter
import com.yoloc.app.db.repositories.ScanFilters
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.ScanSummary
import com.yoloc.app.features.detection.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** RN: __tests__/historyFilters.test.ts, with a fixed zone and locale as the guide asks. */
class HistoryFiltersTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val format = DayFormat(
        today = "Today",
        yesterday = "Yesterday",
        weekdayDate = DayFormat.formatter(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK), zone),
        fullDate = DayFormat.formatter(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK), zone),
        time = DayFormat.formatter(DateTimeFormatter.ofPattern("HH:mm", Locale.UK), zone),
        zone = zone,
    )

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    // Local time, so day boundaries match the device's midnight.
    private val now = at(2026, 10, 15, 14, 30)
    private val hour = 60 * 60 * 1000L

    private fun scanAt(id: String, createdAt: Long) = ScanSummary(
        id = id, plotId = null, plotName = null, modelVersionId = "m", imagePath = "scans/$id.jpg",
        thumbnailPath = null, imageWidth = 1, imageHeight = 1, source = ScanSource.Camera,
        confThreshold = 0.25, iouThreshold = 0.45, inferenceMs = null, totalPods = 0,
        verdict = Verdict.NoPod, lowConfidence = false, latitude = null, longitude = null,
        notes = null, createdAt = createdAt,
    )

    @Test
    fun rangeStartCountsWholeLocalDaysIncludingToday() {
        assertNull(rangeStart(DateRange.All, now, zone))
        assertEquals(day(2026, 10, 15), rangeStart(DateRange.Today, now, zone))
        assertEquals(day(2026, 10, 9), rangeStart(DateRange.Week, now, zone))
        assertEquals(day(2026, 9, 16), rangeStart(DateRange.Month, now, zone))
    }

    @Test
    fun rangeStartCountsCalendarDaysAcrossADaylightSavingChange() {
        // New York springs forward on Sun 8 Mar 2026. Just after midnight on Fri 13 Mar, "last 7
        // days" must start on Sat 7 Mar; subtracting 6 x 24 h (as RN does) lands on Fri 6 Mar.
        val ny = ZoneId.of("America/New_York")
        val now = LocalDateTime.of(2026, 3, 13, 0, 30).atZone(ny).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 3, 7).atStartOfDay(ny).toInstant().toEpochMilli()
        assertEquals(expected, rangeStart(DateRange.Week, now, ny))
    }

    @Test
    fun mapsAnEmptyFilterToNoConditions() {
        assertEquals(ScanFilters(), toScanFilters(HistoryFilter(), now, zone))
    }

    @Test
    fun keepsNoPlotDistinctFromAnyPlotAndTrimsSearch() {
        val f = toScanFilters(
            HistoryFilter(listOf(Verdict.EarlyStage), DateRange.Today, PlotFilter.None, "  row 4 "),
            now,
            zone,
        )
        assertEquals(
            ScanFilters(verdicts = listOf(Verdict.EarlyStage), plot = PlotFilter.None, from = startOfDay(now, zone), search = "row 4"),
            f,
        )
    }

    @Test
    fun countsVerdictDateAndPlotFiltersAndSearchAloneStillCountsAsFiltered() {
        val empty = HistoryFilter()
        assertEquals(0, activeFilterCount(empty))
        assertFalse(isFiltered(empty))
        assertEquals(3, activeFilterCount(HistoryFilter(listOf(Verdict.Infected), DateRange.Week, PlotFilter.None)))
        assertEquals(0, activeFilterCount(empty.copy(search = "creek")))
        assertTrue(isFiltered(empty.copy(search = "creek")))
        assertFalse(isFiltered(empty.copy(search = "   ")))
    }

    @Test
    fun groupByDayInsertsOneHeaderPerLocalDayNewestFirst() {
        val items = groupByDay(
            listOf(
                scanAt("a", now - hour),
                scanAt("b", now - 2 * hour),
                scanAt("c", now - DAY_MS),
                scanAt("d", now - 3 * DAY_MS),
            ),
            now,
            format,
        )
        assertEquals(
            listOf("#Today", "a", "b", "#Yesterday", "c", "#Mon 12 Oct", "d"),
            items.map { if (it is HistoryItem.Day) "#${it.label}" else it.key },
        )
        assertEquals(listOf("day", "scan", "scan", "day", "scan", "day", "scan"), items.map { it.type })
    }

    @Test
    fun groupByDayReturnsNothingForNoScans() {
        assertEquals(emptyList<HistoryItem>(), groupByDay(emptyList(), now, format))
    }

    @Test
    fun shortWhenShowsTheTimeForTodayAndOnlyTheDayBeforeThat() {
        assertEquals("13:30", formatShortWhen(now - hour, now, format))
        assertEquals("Yesterday", formatShortWhen(now - DAY_MS, now, format))
    }

    @Test
    fun dayLabelNamesTodayAndYesterdayAndAddsTheYearOnlyForOtherYears() {
        assertEquals("Today", dayLabel(startOfDay(now, zone), now, format))
        assertEquals("Yesterday", dayLabel(startOfDay(now - DAY_MS, zone), now, format))
        assertTrue(dayLabel(day(2025, 10, 1), now, format).contains("2025"))
        assertFalse(dayLabel(day(2026, 6, 1), now, format).contains("2026"))
    }

    @Test
    fun formatDateTimeJoinsDayAndTime() {
        assertEquals("Today, 13:30", formatDateTime(now - hour, now, format))
    }
}
