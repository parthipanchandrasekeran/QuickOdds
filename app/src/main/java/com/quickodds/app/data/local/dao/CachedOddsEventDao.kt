package com.quickodds.app.data.local.dao

import androidx.room.*
import com.quickodds.app.data.local.entity.CachedOddsEventEntity
import com.quickodds.app.data.local.entity.OddsCacheMetadata
import kotlinx.coroutines.flow.Flow

/**
 * DAO for cached OddsEvent data.
 * Provides cache-first strategy with 15-minute stale time to save API credits.
 */
@Dao
interface CachedOddsEventDao {

    // ============ CACHED EVENTS ============

    /**
     * Get all cached events for a specific sport.
     */
    @Query("SELECT * FROM cached_odds_events WHERE sportKey = :sportKey ORDER BY cachedAt DESC")
    suspend fun getEventsBySport(sportKey: String): List<CachedOddsEventEntity>

    /**
     * Get all cached events for a specific sport as Flow (reactive).
     */
    @Query("SELECT * FROM cached_odds_events WHERE sportKey = :sportKey ORDER BY cachedAt DESC")
    fun getEventsBySportFlow(sportKey: String): Flow<List<CachedOddsEventEntity>>

    /**
     * Get a specific event by ID.
     */
    @Query("SELECT * FROM cached_odds_events WHERE id = :eventId")
    suspend fun getEventById(eventId: String): CachedOddsEventEntity?

    /**
     * Insert or update a single event.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CachedOddsEventEntity)

    /**
     * Insert or update multiple events.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CachedOddsEventEntity>)

    /**
     * Delete all events for a specific sport.
     */
    @Query("DELETE FROM cached_odds_events WHERE sportKey = :sportKey")
    suspend fun deleteEventsBySport(sportKey: String)

    /**
     * Delete all cached events.
     */
    @Query("DELETE FROM cached_odds_events")
    suspend fun deleteAllEvents()

    /**
     * Delete expired events (older than 1 hour for cleanup).
     */
    @Query("DELETE FROM cached_odds_events WHERE cachedAt < :expiryTime")
    suspend fun deleteExpiredEvents(expiryTime: Long = System.currentTimeMillis() - 60 * 60 * 1000)

    // ============ CACHE METADATA ============

    /**
     * Get cache metadata for a specific sport.
     */
    @Query("SELECT * FROM odds_cache_metadata WHERE sportKey = :sportKey")
    suspend fun getCacheMetadata(sportKey: String): OddsCacheMetadata?

    /**
     * Insert or update cache metadata.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheMetadata(metadata: OddsCacheMetadata)

    /**
     * Get the last fetch time for a sport's cache.
     */
    @Query("SELECT lastFetchTime FROM odds_cache_metadata WHERE sportKey = :sportKey")
    suspend fun getLastFetchTime(sportKey: String): Long?

    /**
     * Check if cache is fresh (less than 15 minutes old).
     * Returns metadata only if cache is still fresh.
     */
    @Query("SELECT * FROM odds_cache_metadata WHERE sportKey = :sportKey AND lastFetchTime > :freshTime")
    suspend fun getFreshCacheMetadata(
        sportKey: String,
        freshTime: Long = System.currentTimeMillis() - 15 * 60 * 1000  // 15 minutes
    ): OddsCacheMetadata?

    // ============ COMBINED OPERATIONS ============

    /**
     * Update cache: delete old events and insert new ones.
     */
    @Transaction
    suspend fun refreshEvents(sportKey: String, events: List<CachedOddsEventEntity>) {
        deleteEventsBySport(sportKey)
        insertEvents(events)
        insertCacheMetadata(OddsCacheMetadata(sportKey, eventCount = events.size))
    }

    /**
     * Check if we should fetch from API (cache is stale or doesn't exist).
     */
    suspend fun shouldFetchFromApi(sportKey: String): Boolean {
        val metadata = getCacheMetadata(sportKey)
        return metadata?.shouldFetchFromApi() ?: true
    }
}
