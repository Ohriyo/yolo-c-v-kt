package com.yoloc.app

import android.content.Context
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.detection.ModelLoader

/**
 * Manual DI, built once in [YoloCApp.onCreate]. Plays the role of RN's module-level singletons
 * (`getDb()`, the Zustand stores). Members are added as each step ports them: database and file
 * stores (Step 3), repositories and stores (Step 4.3).
 */
class AppContainer(private val context: Context) {
    val modelConfig: ModelConfig by lazy { ModelConfig.load(context) }
    val modelLoader: ModelLoader by lazy { ModelLoader(context, modelConfig) } // modelLoader.ts
}
