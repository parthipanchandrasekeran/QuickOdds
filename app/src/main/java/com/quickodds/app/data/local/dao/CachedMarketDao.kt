package com.quickodds.app.data.local.dao

import androidx.room.*
import com.quickodds.app.data.local.entity.CacheMetadata
import com.quickodds.app.data.local.entity.CachedMarketEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for cached market data.
 * Provides cache-first strategy with 15-minute stale time to save API credits.
 */
@Dao
interface CachedMarketDao {

    // ============ CACHED MARKETS ============

    /**
     * Get all cached markets for a specific sport.
     */
    @Query("SELECT * FROM cached_markets WHERE sportId = :sportId ORDER BY startTime ASC")
    suspend fun getMarketsBySport(sportId: String): List<CachedMarketEntity>

    /**
     * Get all cached markets for a specific sport as Flow (reactive).
     */
    @Query("SELECT * FROM cached_markets WHERE sportId = :sportId ORDER BY startTime ASC")
    fun getMarketsBySportFlow(sportId: String): Flow<List<CachedMarketEntity>>

    /**
     * Get a specific market by ID.
     */
    @Query("SELECT * FROM cached_markets WHERE id = :marketId")
    suspend fun getMarketById(marketId: String): CachedMarketEntity?

    /**
     * Get a specific market by ID as Flow.
     */
    @Query("SELECT * FROM cached_markets WHERE id = :marketId")
    fun getMarketByIdFlow(marketId: String): Flow<CachedMarketEntity?>

    /**
     * Insert or update a single market.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarket(market: CachedMarketEntity)

    /**
     * Insert or update multiple markets.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarkets(markets: List<CachedMarketEntity>)

    /**
     * Delete all markets for a specific sport.
     */
    @Query("DELETE FROM cached_markets WHERE sportId = :sportId")
    suspend fun deleteMarketsBySport(sportId: String)

    /**
     * Delete all cached markets.
     */
    @Query("DELETE FROM cached_markets")
    suspend fun deleteAllMarkets()

    /**
     * Delete expired markets (older than 24 hours).
     */
    @Query("DELETE FROM cached_markets WHERE cachedAt < :expiryTime")
    suspend fun deleteExpiredMarkets(expiryTime: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000)

    // ============ CACHE METADATA ============

    /**
     * Get cache metadata for a specific sport.
     */
    @Query("SELECT * FROM cache_metadata WHERE sportId = :sportId")
    suspend fun getCacheMetadata(sportId: String): CacheMetadata?

    /**
     * Insert or update cache metadata.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheMetadata(metadata: CacheMetadata)

    /**
     * Delete cache metadata for a specific sport.
     */
    @Query("DELETE FROM cache_metadata WHERE sportId = :sportId")
    suspend fun deleteCacheMetadata(sportId: String)

    /**
     * Check if cache is stale (older than 15 minutes).
     * Returns metadata only if cache is still fresh.
     */
    @Query("SELECT * FROM cache_metadata WHERE sportId = :sportId AND lastRefreshTime > :freshTime")
    suspend fun getFreshCacheMetadata(
        sportId: String,
        freshTime: Long = System.currentTimeMillis() - 15 * 60 * 1000  // 15 minutes
    ): CacheMetadata?

    /**
     * Get the last update time for a sport's cache.
     */
    @Query("SELECT lastRefreshTime FROM cache_metadata WHERE sportId = :sportId")
    suspend fun getLastUpdateTime(sportId: String): Long?

    // ============ COMBINED OPERATIONS ============

    /**
     * Update cache: delete old markets and insert new ones.
     */
    @Transaction
    suspend fun refreshMarkets(sportId: String, markets: List<CachedMarketEntity>) {
        deleteMarketsBySport(sportId)
        insertMarkets(markets)
        insertCacheMetadata(CacheMetadata(sportId))
    }
}
