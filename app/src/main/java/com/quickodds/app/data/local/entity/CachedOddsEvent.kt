package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quickodds.app.data.remote.dto.OddsEvent

/**
 * Room entity for caching OddsEvent API responses.
 * Stores serialized JSON to preserve full bookmaker/market data.
 * Implements 15-minute stale time to save API credits.
 */
@Entity(tableName = "cached_odds_events")
data class CachedOddsEventEntity(
    @PrimaryKey
    val id: String,
    val sportKey: String,
    val eventJson: String,  // Serialized OddsEvent as JSON
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CACHE_STALE_TIME_MS = 15 * 60 * 1000L  // 15 minutes for API credit savings
        private val gson = Gson()

        fun fromOddsEvent(event: OddsEvent): CachedOddsEventEntity {
            return CachedOddsEventEntity(
                id = event.id,
                sportKey = event.sportKey,
                eventJson = gson.toJson(event),
                cachedAt = System.currentTimeMillis()
            )
        }

        /**
         * Check if cache should be refreshed from API.
         * Returns true if data is older than 15 minutes.
         */
        fun shouldFetchFromApi(lastUpdated: Long?): Boolean {
            if (lastUpdated == null) return true
            return System.currentTimeMillis() - lastUpdated > CACHE_STALE_TIME_MS
        }
    }

    fun toOddsEvent(): OddsEvent? {
        return try {
            gson.fromJson(eventJson, OddsEvent::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun isStale(): Boolean {
        return System.currentTimeMillis() - cachedAt > CACHE_STALE_TIME_MS
    }
}

/**
 * Metadata for tracking when each sport's odds events were last fetched.
 */
@Entity(tableName = "odds_cache_metadata")
data class OddsCacheMetadata(
    @PrimaryKey
    val sportKey: String,
    val lastFetchTime: Long = System.currentTimeMillis(),
    val eventCount: Int = 0
) {
    companion object {
        const val CACHE_STALE_TIME_MS = 15 * 60 * 1000L  // 15 minutes
    }

    fun isStale(): Boolean {
        return System.currentTimeMillis() - lastFetchTime > CACHE_STALE_TIME_MS
    }

    fun shouldFetchFromApi(): Boolean = isStale()
}
