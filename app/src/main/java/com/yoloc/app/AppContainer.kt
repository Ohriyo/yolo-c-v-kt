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
import com.yoloc.app.features.scans.AnalyzePhoto
import com.yoloc.app.features.scans.AndroidScanFileStore
import com.yoloc.app.features.scans.CapturedPhotos
import com.yoloc.app.features.scans.ScanFileStore
import com.yoloc.app.features.scans.TempImages
import com.yoloc.app.features.scans.ThumbnailBackfill
import com.yoloc.app.features.settings.FusedLocationSource
import com.yoloc.app.features.settings.LocationSource
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.PermissionHistory
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.ScanSession
import com.yoloc.app.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI, built once in [YoloCApp.onCreate]. Plays the role of RN's module-level singletons
 * (`getDb()`, the Zustand stores).
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
    val locationSource: LocationSource by lazy { FusedLocationSource(context) } // expo-location
    val analyzePhoto: AnalyzePhoto by lazy {
        AnalyzePhoto(settingsRepository, classRepository, modelLoader, modelConfig) // analyzePhoto.ts
    }

    /**
     * Lives as long as the process; work here outlasts any one screen (RN: un-awaited promises).
     * IO: what runs here opens the database and reads assets.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settingsStore: SettingsStore by lazy { SettingsStore(settingsRepository, modelConfig, appScope) } // settingsStore.ts
    val scanLibrary: ScanLibrary by lazy { ScanLibrary(scanRepository) } // scanLibraryStore.ts
    val scanSession: ScanSession by lazy { ScanSession(tempImages) } // scanSessionStore.ts
    val historyRequests: HistoryRequests by lazy { HistoryRequests() } // History's route params
    val permissionHistory: PermissionHistory by lazy { PermissionHistory(context) } // Expo's canAskAgain
    val capturedPhotos: CapturedPhotos by lazy { CapturedPhotos(context) } // takePictureAsync / pickGalleryPhoto
}
