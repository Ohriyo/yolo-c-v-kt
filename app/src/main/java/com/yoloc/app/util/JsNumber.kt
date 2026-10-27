package com.yoloc.app.util

import java.math.BigDecimal

/**
 * A number as JavaScript's String(n) prints it: 7 not 7.0, 0.0001 not 1.0E-4. Settings values and
 * CSV cells must read the same as RN's, which wrote them with String(n).
 */
fun Double.toJsString(): String = BigDecimal(toString()).stripTrailingZeros().toPlainString()
