package com.yoloc.app.screens.home

import com.yoloc.app.store.USER_NAME_MAX
import com.yoloc.app.store.cleanUserName
import org.junit.Assert.assertEquals
import org.junit.Test

/** Redesign 2026-10-28 (Kotlin only): the Home greeting and the name it uses. */
class GreetingTest {
    @Test
    fun picksThePartOfTheDayByLocalHour() {
        assertEquals(DayPart.Morning, dayPart(0))
        assertEquals(DayPart.Morning, dayPart(11))
        assertEquals(DayPart.Afternoon, dayPart(12))
        assertEquals(DayPart.Afternoon, dayPart(17))
        assertEquals(DayPart.Evening, dayPart(18))
        assertEquals(DayPart.Evening, dayPart(23))
    }

    @Test
    fun cleansTheNameBeforeItIsSaved() {
        assertEquals("Juan", cleanUserName("  Juan "))
        assertEquals("Ana Maria", cleanUserName("Ana \t  Maria"))
        assertEquals("", cleanUserName("   "))
        assertEquals(USER_NAME_MAX, cleanUserName("x".repeat(50)).length)
        // A cut that ends on a space does not leave it at the end.
        assertEquals("a".repeat(29), cleanUserName("a".repeat(29) + " b"))
    }
}
