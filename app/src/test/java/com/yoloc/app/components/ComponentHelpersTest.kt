package com.yoloc.app.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected values printed by RN's own formatConfidence and Slider snap under Node. */
class ComponentHelpersTest {
    @Test
    fun formatConfidenceMatchesRn() {
        val inputs = listOf(0.0, 0.004, 0.005, 0.125, 0.255, 0.995, 1.0, 1.2, -0.1, 0.6666)
        assertEquals(listOf(0, 0, 1, 13, 26, 100, 100, 100, 0, 67), inputs.map(::formatConfidence))
    }

    @Test
    fun snapToStepMatchesRn() {
        val cases = listOf(
            listOf(0.27, 0.05, 0.95, 0.05) to 0.25,
            listOf(0.325, 0.05, 0.95, 0.05) to 0.35,
            listOf(0.9999, 0.05, 0.95, 0.05) to 0.95,
            listOf(0.0, 0.05, 0.95, 0.05) to 0.05,
            listOf(0.6, 0.05, 0.95, 0.05) to 0.6,
            listOf(0.15, 0.1, 0.9, 0.01) to 0.15,
            listOf(0.123456, 0.0, 1.0, 0.0001) to 0.1235,
        )
        for ((args, expected) in cases) {
            val (v, min, max, step) = args
            assertEquals("snap $args", expected, snapToStep(v, min, max, step), 0.0)
        }
    }

    @Test
    fun limitEditKeepsTheExistingTextAndCutsWhatWasInserted() {
        // Typing at the limit adds nothing.
        assertEquals(LimitedEdit("abcde", 5), limitEdit("abcde", "abcdeX", 5))
        // A paste near the start keeps the end of the note and as much of the paste as fits; the
        // cursor goes after the kept part, not into the old text.
        assertEquals(LimitedEdit("a12bcd", 3), limitEdit("abcd", "a12345bcd", 6))
        // Under the limit nothing changes, and deleting is never cut.
        assertEquals("ab1cd", limitEdit("abcd", "ab1cd", 6).text)
        assertEquals("abc", limitEdit("abcdefg", "abc", 5).text)
        // Repeated letters: the unchanged end is not counted twice.
        assertEquals("aaaa", limitEdit("aaa", "aaaaaa", 4).text)
    }
}
