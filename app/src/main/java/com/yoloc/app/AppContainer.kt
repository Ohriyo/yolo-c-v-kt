package com.yoloc.app

import android.content.Context

/**
 * Manual DI, built once in [YoloCApp.onCreate]. Plays the role of RN's module-level singletons
 * (`getDb()`, the Zustand stores). Members are added as each step ports them: model config,
 * database, file stores and model loader (Steps 3 and 4.1), repositories and stores (Step 4.3).
 */
class AppContainer(private val context: Context)
