package com.quickodds.app.data.local

import androidx.room.TypeConverter
import com.quickodds.app.data.local.entity.BetStatus

/**
 * Type converters for Room database.
 * Converts custom types to/from database-compatible formats.
 */
class Converters {

    @TypeConverter
    fun fromBetStatus(status: BetStatus): String {
        return status.name
    }

    @TypeConverter
    fun toBetStatus(value: String): BetStatus {
        return BetStatus.valueOf(value)
    }
}
