package com.voxcommander.app.domain.intent.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FastMapRule: L1 Trigger rule stored in database.
 * Extended to support full entity extraction aligned with Triple AI architecture.
 */
@Entity(tableName = "fast_map_rules")
data class FastMapRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val actionType: String,
    val triggerPattern: String,
    // New fields for hybrid alignment
    val artist: String? = null,
    val track: String? = null,
    val album: String? = null,
    val destination: String? = null
)
