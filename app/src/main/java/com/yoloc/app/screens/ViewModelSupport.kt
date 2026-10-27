package com.yoloc.app.screens

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.yoloc.app.AppContainer
import com.yoloc.app.YoloCApp

/** The app's container, for ViewModel factories (no Hilt; guide 4.4). */
internal val CreationExtras.app: YoloCApp get() = this[APPLICATION_KEY] as YoloCApp

internal val CreationExtras.container: AppContainer get() = app.container

/** "Now" changes only when it moved by more than a minute (RN: useNow), so a resume never re-renders for nothing. */
internal fun refreshedNow(previous: Long, now: Long = System.currentTimeMillis()): Long =
    if (now - previous > 60_000) now else previous
