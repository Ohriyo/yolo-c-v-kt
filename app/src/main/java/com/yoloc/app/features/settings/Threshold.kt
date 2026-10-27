package com.yoloc.app.features.settings

import java.util.Locale
import kotlin.math.roundToInt

/*
 * RN: settings/threshold.ts. Range offered for the confidence threshold. Below 10% the screen fills
 * with noise; above 90% the detector would hide most real pods, including the early-stage ones it
 * already struggles with (recall about 0.6). The default is model.config.json's confThreshold.
 */
const val THRESHOLD_MIN = 0.1
const val THRESHOLD_MAX = 0.9
const val THRESHOLD_STEP = 0.05

/** Clamps to the range and snaps to 5% steps, without float noise (0.30000000000000004). */
fun snapThreshold(value: Double): Double {
    if (value.isNaN()) return THRESHOLD_MIN // RN returns NaN here; Kotlin's roundToInt would throw
    val clamped = value.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
    val steps = ((clamped - THRESHOLD_MIN) / THRESHOLD_STEP).roundToInt()
    // Locale.ROOT: a comma-decimal phone would otherwise format "0,35" and fail to parse.
    return "%.2f".format(Locale.ROOT, THRESHOLD_MIN + steps * THRESHOLD_STEP).toDouble()
}

fun thresholdPercent(value: Double): Int = (value * 100).roundToInt()
