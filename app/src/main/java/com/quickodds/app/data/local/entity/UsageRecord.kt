package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_records")
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String, // "SCAN_ALL" or "ANALYZE"
    val dateString: String, // e.g. "2026-02-10"
    val timestamp: Long = System.currentTimeMillis()
)
