package com.yoloc.app.util

import java.math.BigDecimal

/**
 * A number as JavaScript's String(n) prints it: 7 not 7.0, 0.0001 not 1.0E-4. Settings values and
 * CSV cells must read the same as RN's, which wrote them with String(n).
 */
fun Double.toJsString(): String = BigDecimal(toString()).stripTrailingZeros().toPlainString()

/**
 * JS Number.prototype.toFixed: the double's exact value rounded half up (away from zero) to
 * [digits] decimals, always with a '.' whatever the locale. String.format can round the same
 * double differently and uses the locale's separator.
 */
fun toFixed(value: Double, digits: Int): String =
    BigDecimal(value).setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString()
