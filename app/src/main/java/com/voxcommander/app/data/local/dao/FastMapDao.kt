package com.voxcommander.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voxcommander.app.data.local.FastMapRule

@Dao
interface FastMapDao {
    @Query("SELECT * FROM fast_map_rules")
    suspend fun getAllRules(): List<FastMapRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FastMapRule)

    @Delete
    suspend fun deleteRule(rule: FastMapRule)
}