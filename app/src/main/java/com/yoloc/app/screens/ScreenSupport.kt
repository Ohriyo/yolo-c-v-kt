package com.yoloc.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.yoloc.app.AppContainer
import com.yoloc.app.YoloCApp
import com.yoloc.app.features.scans.DayFormat

/** The app's container, for composables that need a store directly (RN: importing the store). */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as YoloCApp).container

/** Day and time labels in the device's language and 12/24-hour setting; rebuilt when they change. */
@Composable
fun rememberDayFormat(): DayFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { DayFormat.forDevice(context) }
}
