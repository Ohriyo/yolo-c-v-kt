package com.yoloc.app.screens

import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.ScanSummary
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.screens.history.mergePage
import com.yoloc.app.screens.home.dailyTipIndex
import com.yoloc.app.screens.onboarding.recallOutOfTen
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** The pure parts of the Step 4.4 ViewModels; the rest runs against Room in androidTest. */
class ViewModelHelpersTest {
    @Test
    fun theTipChangesAtLocalMidnightAndCycles() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))
        try {
            val zone = ZoneId.of("Asia/Manila")
            fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
            assertEquals(dailyTipIndex(at(27, 0), 4), dailyTipIndex(at(27, 23), 4))
            assertEquals((dailyTipIndex(at(27, 12), 4) + 1) % 4, dailyTipIndex(at(28, 1), 4))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun recallReadsAsPodsFoundOutOfTen() {
        assertEquals(6, recallOutOfTen(0.6))
        assertEquals(6, recallOutOfTen(0.55)) // JS Math.round rounds halves up
        assertNull(recallOutOfTen(null))
    }

    @Test
    fun nowMovesOnlyAfterAMinute() {
        assertEquals(1_000L, refreshedNow(1_000, now = 50_000))
        assertEquals(70_000L, refreshedNow(1_000, now = 70_000))
    }

    @Test
    fun aPageNeverListsAScanTwice() {
        fun scan(id: String) = ScanSummary(
            id, null, null, "m", "scans/$id.jpg", null, 1, 1, ScanSource.Camera, 0.25, 0.45, null, 0,
            Verdict.NoPod, false, null, null, null, 0,
        )
        assertEquals(listOf("a", "b", "c"), mergePage(listOf(scan("a"), scan("b")), listOf(scan("b"), scan("c"))).map { it.id })
    }

    @Test
    fun aHistoryRequestIsAppliedOnce() {
        val requests = HistoryRequests()
        requests.send(HistoryRequest.ByVerdict(Verdict.Infected))
        assertEquals(HistoryRequest.ByVerdict(Verdict.Infected), requests.consume())
        assertNull(requests.consume())
        assertNull(requests.request.value)
    }
}
