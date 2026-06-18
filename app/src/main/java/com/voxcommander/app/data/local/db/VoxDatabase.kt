package com.voxcommander.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.FastMapRule

@Database(entities = [FastMapRule::class], version = 2)
abstract class VoxDatabase : RoomDatabase() {
    abstract fun fastMapDao(): FastMapDao
}