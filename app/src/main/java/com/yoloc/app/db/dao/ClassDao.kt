package com.yoloc.app.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import com.yoloc.app.db.ClassEntity

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY id")
    suspend fun listClasses(): List<ClassEntity>
}
