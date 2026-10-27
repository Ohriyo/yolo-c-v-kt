package com.yoloc.app.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.yoloc.app.db.SettingEntity

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntity>

    @Upsert
    suspend fun upsert(setting: SettingEntity)
}
