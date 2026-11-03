package com.yoloc.app.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.features.detection.DetectionSensitivity
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.SettingCodecs
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.features.detection.classCatalog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RN: __tests__/db.migrations.test.ts. RN's runMigrations tests (schema_migrations, rollback of a
 * broken migration, version ordering) have no Room equivalent: Room versions the schema itself.
 * The schema change itself is covered by MigrationTest.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val holder = TestDb.holder()
    private val config = TestDb.config

    @After
    fun close() = holder.close()

    @Test
    fun createsTheFullSchemaWithRnTableAndIndexNames() = runBlocking {
        val db = holder.get()
        assertEquals(
            listOf("classes", "detections", "model_versions", "plots", "scans", "settings"),
            db.column(
                """SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
                   AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name""",
            ),
        )
        assertEquals(
            listOf("idx_detections_class", "idx_detections_scan", "idx_scans_created", "idx_scans_plot", "idx_scans_verdict"),
            db.column("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%' ORDER BY name"),
        )
    }

    @Test
    fun writesTheModelIndependentDefaultSettings() = runBlocking {
        val settings = SettingsRepository(holder).getSettings()
        assertFalse(settings.onboardingDone)
        assertEquals(ThemeSetting.System, settings.theme)
        assertFalse(settings.gpsOptIn)
        assertTrue(settings.keepOriginalImages)
    }

    @Test
    fun keepsDataWhenOpenedAgain() = runBlocking {
        // RN: "is idempotent". Seeding again with the same config writes nothing and keeps data.
        SettingsRepository(holder).setSetting(SettingCodecs.onboardingDone, true)
        assertFalse(seedFromModelConfig(holder.get(), config, classCatalog(TestDb.context)))
        assertTrue(SettingsRepository(holder).getSettings().onboardingDone)
    }

    @Test
    fun enforcesForeignKeys() = runBlocking {
        try {
            holder.get().exec(
                """INSERT INTO detections (id, scan_id, class_id, confidence, x, y, w, h)
                   VALUES ('d1', 'no-such-scan', 0, 0.9, 0, 0, 0.1, 0.1)""",
            )
            fail("Insert without a scan should fail")
        } catch (e: Exception) {
            assertTrue(e.message, e.message!!.contains("FOREIGN KEY"))
        }
    }

    @Test
    fun seedsClassesInModelIndexOrderWithCatalogSeverityAndColors() = runBlocking {
        val classes = ClassRepository(holder).listClasses()
        assertEquals(
            listOf(
                listOf(0, "early_stage", 1, "early"),
                listOf(1, "fully_infected_pod", 2, "infected"),
                listOf(2, "healthy_pod", 0, "healthy"),
            ),
            classes.map { listOf(it.id, it.key, it.severity, it.colorToken) },
        )
        // Agronomic text is placeholder until reviewed.
        for (c in classes) {
            assertTrue(c.summary.contains("TODO(verify with agronomist)"))
            assertTrue(c.recommendedActions.isNotEmpty())
        }
    }

    @Test
    fun activatesTheConfiguredModelAndFillsModelDerivedSettings() = runBlocking {
        assertEquals(
            listOf(listOf(config.id, config.file, config.inputSize.toString(), "3", "1")),
            holder.get().rows("SELECT id, file_name, input_size, num_classes, is_active FROM model_versions"),
        )
        val settings = SettingsRepository(holder).getSettings()
        assertEquals(DetectionSensitivity.Standard, settings.sensitivity)
        assertEquals(config.iouThreshold, settings.iouThreshold, 0.0)
        assertEquals(config.id, settings.activeModelId)
    }

    @Test
    fun skipsWorkWhenTheConfigIsUnchanged() = runBlocking {
        assertFalse(seedFromModelConfig(holder.get(), config, classCatalog(TestDb.context)))
    }

    @Test
    fun aNewModelBecomesActiveAndKeepsTheUsersSensitivity() = runBlocking {
        val settings = SettingsRepository(holder)
        settings.setSetting(SettingCodecs.sensitivity, DetectionSensitivity.Strict)
        val retrained = config.copy(
            id = "yolo-c-v9-640",
            displayName = "YOLO-C v9 (640)",
            file = "yolo_c_v9_640.tflite",
        )
        assertTrue(seedFromModelConfig(holder.get(), retrained, classCatalog(TestDb.context)))
        assertEquals(
            listOf(listOf(config.id, "0"), listOf("yolo-c-v9-640", "1")),
            holder.get().rows("SELECT id, is_active FROM model_versions ORDER BY id"),
        )
        // A level means the same for any model: Standard is always that model's own thresholds.
        assertEquals(DetectionSensitivity.Strict, settings.getSettings().sensitivity)
        assertEquals("yolo-c-v9-640", settings.getSettings().activeModelId)
    }

    @Test
    fun failsWithoutWritingAnythingWhenAClassHasNoCatalogEntry() = runBlocking {
        val db = TestDb.unseeded()
        try {
            val unknown = config.copy(classes = listOf("early_stage", "mystery_class"))
            try {
                seedFromModelConfig(db, unknown, classCatalog(TestDb.context))
                fail("Seeding an unknown class should fail")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("mystery_class"))
            }
            assertEquals(0, db.rowCount("classes"))
            assertEquals(0, db.rowCount("model_versions"))
        } finally {
            db.close()
        }
    }

    @Test
    fun everyClassInTheModelConfigHasCatalogMetadata() {
        val catalog = classCatalog(TestDb.context)
        for (key in config.classes) assertTrue(key, key in catalog)
    }
}
