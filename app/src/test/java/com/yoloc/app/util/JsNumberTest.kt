package com.yoloc.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected values printed by JS toFixed under Node. */
class JsNumberTest {
    @Test
    fun toFixedMatchesJs() {
        val cases = listOf(
            (1450 / 1000.0 to 1) to "1.4",
            (1250 / 1000.0 to 1) to "1.3",
            (1050 / 1000.0 to 1) to "1.1",
            (0.05 to 1) to "0.1",
            (2.675 to 2) to "2.67",
            (14.123456 to 5) to "14.12346",
            (121.0000049 to 5) to "121.00000",
            (-12.3456789 to 5) to "-12.34568",
            (0.0 to 1) to "0.0",
        )
        for ((args, expected) in cases) assertEquals("toFixed$args", expected, toFixed(args.first, args.second))
    }
}
