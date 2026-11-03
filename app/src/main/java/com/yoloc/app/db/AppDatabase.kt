package com.yoloc.app.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.yoloc.app.db.dao.ClassDao
import com.yoloc.app.db.dao.DevDao
import com.yoloc.app.db.dao.PlotDao
import com.yoloc.app.db.dao.ScanDao
import com.yoloc.app.db.dao.SeedDao
import com.yoloc.app.db.dao.SettingsDao
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.detection.classCatalog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * RN schema 001 is Room version 1; [MIGRATION_1_2] is Kotlin only. Room records the version in
 * SQLite's user_version instead of RN's schema_migrations table. The exported schema is in
 * app/schemas.
 */
@Database(
    entities = [
        ModelVersionEntity::class, ClassEntity::class, PlotEntity::class,
        ScanEntity::class, DetectionEntity::class, SettingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun plotDao(): PlotDao
    abstract fun classDao(): ClassDao
    abstract fun settingsDao(): SettingsDao
    abstract fun seedDao(): SeedDao
    abstract fun devDao(): DevDao

    companion object {
        const val NAME = "yolo-c.db"
    }
}

/**
 * Scans store the threshold each class ran with, not one number. An old scan's single threshold
 * was applied to every class, so that is what it records. The slider's old setting and v2's
 * metrics go. The bundled SQLite has DROP COLUMN and JSON, whatever the phone's own version.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE scans ADD COLUMN conf_thresholds TEXT NOT NULL DEFAULT '{}'")
        connection.execSQL(
            """UPDATE scans SET conf_thresholds = COALESCE(
                 (SELECT json_group_object(key, scans.conf_threshold) FROM classes), '{}')""",
        )
        connection.execSQL("ALTER TABLE scans DROP COLUMN conf_threshold")
        connection.execSQL("DELETE FROM settings WHERE key = 'conf_threshold'")
        connection.execSQL(
            "UPDATE model_versions SET val_map50 = NULL, test_map50 = NULL, early_stage_recall = NULL WHERE id = 'yolo-c-v2-640'",
        )
    }
}

/** Defaults that do not depend on the model (RN: the INSERT OR IGNORE at the end of 001). */
private val defaultsCallback = object : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        connection.execSQL(
            """INSERT OR IGNORE INTO settings (key, value, updated_at) VALUES
               ('onboarding_done', '0', CAST(strftime('%s','now') AS INTEGER) * 1000),
               ('theme', 'system', CAST(strftime('%s','now') AS INTEGER) * 1000),
               ('gps_opt_in', '0', CAST(strftime('%s','now') AS INTEGER) * 1000),
               ('keep_original_images', '1', CAST(strftime('%s','now') AS INTEGER) * 1000)""",
        )
    }
}

/**
 * RN: getDb(). The app database, opened, created and seeded once per launch. A failed open is not
 * cached, so the startup Retry button can call [get] again. `name` null opens an in-memory
 * database (tests).
 */
class AppDatabaseHolder(
    private val context: Context,
    private val config: ModelConfig,
    private val name: String? = AppDatabase.NAME,
) {
    private val mutex = Mutex()
    private var ready: AppDatabase? = null

    suspend fun get(): AppDatabase = mutex.withLock {
        ready ?: open().also { ready = it }
    }

    private suspend fun open(): AppDatabase {
        val db = buildAppDatabase(context, name)
        try {
            // The first query opens the file and creates the schema; the seed then runs once.
            seedFromModelConfig(db, config, classCatalog(context))
        } catch (e: Throwable) {
            db.close()
            throw e
        }
        return db
    }

    /**
     * Redesign 2026-10-28: seeds again with [localized]'s strings, so the stored class names follow
     * the app language. The seed's fingerprint covers the names, so this writes only when they
     * changed. Opens the database first if needed.
     */
    suspend fun syncCatalog(localized: Context) {
        val db = get()
        mutex.withLock { seedFromModelConfig(db, config, classCatalog(localized)) }
    }

    fun close() {
        ready?.close()
        ready = null
    }
}

/** The configured database, not yet opened or seeded. `name` null builds an in-memory one. */
internal fun buildAppDatabase(context: Context, name: String?): AppDatabase {
    val builder = if (name == null) {
        Room.inMemoryDatabaseBuilder<AppDatabase>(context)
    } else {
        Room.databaseBuilder<AppDatabase>(context, name)
    }
    // Bundled: ships its own SQLite, like expo-sqlite, for a like-for-like comparison.
    // WAL as RN sets it; Room turns on foreign keys because the entities declare them.
    return builder
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(MIGRATION_1_2)
        .addCallback(defaultsCallback)
        .build()
}
