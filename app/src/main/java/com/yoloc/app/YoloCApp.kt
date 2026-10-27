package com.yoloc.app

import android.app.Application

/** Builds the [AppContainer] once per process (RN: Expo did this). */
class YoloCApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
