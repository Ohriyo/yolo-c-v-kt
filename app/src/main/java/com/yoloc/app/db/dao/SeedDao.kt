package com.yoloc.app.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import com.yoloc.app.db.ClassEntity
import com.yoloc.app.db.SettingEntity

/** SQL for db/Seed.kt (RN: src/db/seed.ts). */
@Dao
interface SeedDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun setting(key: String): String?

    @Query("UPDATE model_versions SET is_active = 0 WHERE id <> :activeId")
    suspend fun deactivateOthers(activeId: String)

    /**
     * Copied from seed.ts so installed_at is kept on conflict (it is not in the update list).
     * @Upsert would replace the whole row.
     */
    @Query(
        """INSERT INTO model_versions (id, display_name, file_name, input_size, num_classes,
             val_map50, test_map50, early_stage_recall, is_active, installed_at)
           VALUES (:id, :displayName, :fileName, :inputSize, :numClasses, :valMap50, :testMap50,
             :earlyStageRecall, 1, :installedAt)
           ON CONFLICT(id) DO UPDATE SET
             display_name = excluded.display_name, file_name = excluded.file_name,
             input_size = excluded.input_size, num_classes = excluded.num_classes,
             val_map50 = excluded.val_map50, test_map50 = excluded.test_map50,
             early_stage_recall = excluded.early_stage_recall, is_active = 1""",
    )
    suspend fun upsertActiveModel(
        id: String,
        displayName: String,
        fileName: String,
        inputSize: Int,
        numClasses: Int,
        valMap50: Double?,
        testMap50: Double?,
        earlyStageRecall: Double?,
        installedAt: Long,
    )

    /** Upserted by id, never deleted: class ids are referenced by saved detections. */
    @Upsert
    suspend fun upsertClasses(classes: List<ClassEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(setting: SettingEntity)

    @Upsert
    suspend fun upsertSetting(setting: SettingEntity)
}
