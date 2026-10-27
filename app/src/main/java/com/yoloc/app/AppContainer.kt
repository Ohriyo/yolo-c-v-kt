package com.yoloc.app

import android.content.Context
import com.yoloc.app.db.AppDatabaseHolder
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.DevRepository
import com.yoloc.app.db.repositories.PlotRepository
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.detection.ModelLoader
import com.yoloc.app.features.scans.AndroidScanFileStore
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.features.scans.TempImages
import com.yoloc.app.features.scans.ThumbnailBackfill

/**
 * Manual DI, built once in [YoloCApp.onCreate]. Plays the role of RN's module-level singletons
 * (`getDb()`, the Zustand stores). Stores (Step 4.3) are added when they are ported.
 */
class AppContainer(private val context: Context) {
    val modelConfig: ModelConfig by lazy { ModelConfig.load(context) }
    val database: AppDatabaseHolder by lazy { AppDatabaseHolder(context, modelConfig) } // getDb()
    val files: ScanFileStore by lazy { AndroidScanFileStore(context) } // expoScanFileStore
    val tempImages: TempImages by lazy { TempImages(context) } // tempImage.ts
    val modelLoader: ModelLoader by lazy { ModelLoader(context, modelConfig) } // modelLoader.ts
    val scanRepository: ScanRepository by lazy { ScanRepository(database) }
    val plotRepository: PlotRepository by lazy { PlotRepository(database) }
    val classRepository: ClassRepository by lazy { ClassRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(database) }
    val devRepository: DevRepository by lazy { DevRepository(database) }
    val thumbnails: ThumbnailBackfill by lazy { ThumbnailBackfill(scanRepository, files) } // thumbnails.ts
}
