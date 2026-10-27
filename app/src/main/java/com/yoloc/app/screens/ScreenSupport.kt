package com.yoloc.app.screens

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yoloc.app.AppContainer
import com.yoloc.app.YoloCApp
import com.yoloc.app.features.scans.DayFormat
import java.time.ZoneId

/** The app's container, for composables that need a store directly (RN: importing the store). */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as YoloCApp).container

/**
 * Day and time labels in the device's language, 12/24-hour setting and time zone. The last two
 * are not part of Configuration, so they are read again each time the screen resumes (RN formats
 * on every render).
 */
@Composable
fun rememberDayFormat(): DayFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var clock by remember { mutableStateOf(clockSettings(context)) }
    LifecycleResumeEffect(context) {
        clock = clockSettings(context)
        onPauseOrDispose {}
    }
    return remember(configuration, clock) { DayFormat.forDevice(context) }
}

private fun clockSettings(context: Context) = DateFormat.is24HourFormat(context) to ZoneId.systemDefault()
