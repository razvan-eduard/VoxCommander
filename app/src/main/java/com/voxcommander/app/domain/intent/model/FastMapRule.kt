package com.voxcommander.app.domain.intent.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fast_map_rules")
data class FastMapRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val actionType: String,
    val target: String,
    val triggerPattern: String
)