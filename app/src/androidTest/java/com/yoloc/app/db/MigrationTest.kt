package com.yoloc.app.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The schema change from one threshold per scan to one per class. Validates against app/schemas. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = File(instrumentation.targetContext.cacheDir, "migration-test.db").apply {
        parentFile?.listFiles { f -> f.name.startsWith(name) }?.forEach { it.delete() }
    }

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, file, BundledSQLiteDriver(), AppDatabase::class)

    private fun SQLiteConnection.text(sql: String): String? = prepare(sql).use { if (it.step()) it.getText(0) else null }

    private fun SQLiteConnection.isNull(sql: String): Boolean = prepare(sql).use { it.step() && it.isNull(0) }

    @Test
    fun anOldScansThresholdBecomesEachClassesAndV2sMetricsAndTheOldSettingGo() = runBlocking {
        helper.createDatabase(1).use { db ->
            db.execSQL(
                """INSERT INTO model_versions (id, display_name, file_name, input_size, num_classes, val_map50,
                   test_map50, early_stage_recall, is_active, installed_at)
                   VALUES ('yolo-c-v2-640', 'v2', 'v2.tflite', 640, 3, 0.746, 0.727, 0.596, 1, 1)""",
            )
            for ((id, key) in listOf(0 to "early_stage", 1 to "fully_infected_pod", 2 to "healthy_pod")) {
                db.execSQL(
                    """INSERT INTO classes (id, key, display_name, severity, color_token, summary)
                       VALUES ($id, '$key', '$key', $id, 'status', 'summary')""",
                )
            }
            db.execSQL(
                """INSERT INTO scans (id, model_version_id, image_path, image_width, image_height, source,
                   conf_threshold, iou_threshold, verdict, created_at)
                   VALUES ('old', 'yolo-c-v2-640', 'scans/old.jpg', 10, 10, 'camera', 0.3, 0.45, 'healthy', 1)""",
            )
            db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('conf_threshold', '0.3', 1)")
            db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('theme', 'dark', 1)")
        }

        // Also fails if the result differs from the schema Room expects for version 2.
        helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).use { db ->
            val thresholds = Json.parseToJsonElement(db.text("SELECT conf_thresholds FROM scans WHERE id = 'old'")!!).jsonObject
            assertEquals(setOf("early_stage", "fully_infected_pod", "healthy_pod"), thresholds.keys)
            assertTrue(thresholds.values.all { it.jsonPrimitive.double == 0.3 })

            assertEquals(null, db.text("SELECT value FROM settings WHERE key = 'conf_threshold'"))
            assertEquals("dark", db.text("SELECT value FROM settings WHERE key = 'theme'"))

            for (column in listOf("val_map50", "test_map50", "early_stage_recall")) {
                assertTrue(column, db.isNull("SELECT $column FROM model_versions WHERE id = 'yolo-c-v2-640'"))
            }
        }
    }
}
