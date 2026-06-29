package com.voxcommander.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.FastMapRule

@Database(entities = [FastMapRule::class], version = 11)
@TypeConverters(StringListConverter::class)
abstract class VoxDatabase : RoomDatabase() {
    abstract fun fastMapDao(): FastMapDao
}

class StringListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value == null) return "[]"
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val type = TypeToken.getParameterized(List::class.java, String::class.java).type
            gson.fromJson(value, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}