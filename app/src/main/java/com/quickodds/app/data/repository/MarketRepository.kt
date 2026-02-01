package com.quickodds.app.data.repository

import android.util.Log
import com.quickodds.app.data.local.dao.CachedMarketDao
import com.quickodds.app.data.local.dao.FavoriteMarketDao
import com.quickodds.app.data.local.entity.CacheMetadata
import com.quickodds.app.data.local.entity.CachedMarketEntity
import com.quickodds.app.data.local.entity.FavoriteMarketEntity
import com.quickodds.app.data.remote.MockDataSource
import com.quickodds.app.data.remote.api.OddsCloudFunctionService
import com.quickodds.app.data.remote.dto.OddsEvent
import com.quickodds.app.domain.model.Market
import com.quickodds.app.domain.model.MatchStatus
import com.quickodds.app.domain.model.Odds
import com.quickodds.app.domain.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for market data with cache-first strategy.
 *
 * Caching Policy:
 * - HTTP Cache (OkHttp): 15-minute max-age for network requests
 * - Room Database Cache: 30-minute stale time for markets list
 *
 * Flow:
 * 1. When app opens, show cached data immediately
 * 2. If cache is stale (>30 minutes), fetch fresh data in background
 * 3. Update UI when fresh data arrives
 */
@Singleton
class MarketRepository @Inject constructor(
    private val cachedMarketDao: CachedMarketDao,
    private val favoriteMarketDao: FavoriteMarketDao,
    private val oddsService: OddsCloudFunctionService
) {
    companion object {
        private const val TAG = "MarketRepository"
        private const val CACHE_STALE_TIME_MS = 15 * 60 * 1000L  // 15 minutes for API credit savings
    }

    // ============ SPORTS ============

    /**
     * Get available sports list.
     * Uses mock data for now - can be extended to API call.
     */
    suspend fun getSports(): Result<List<Sport>> {
        return try {
            Result.success(MockDataSource.getSports())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============ MARKETS - CACHE-FIRST STRATEGY ============

    /**
     * Get markets for a sport with cache-first strategy.
     *
     * 1. Return cached data immediately if available
     * 2. If cache is stale (>30 min) or empty, fetch from network
     * 3. Update cache with fresh data
     */
    suspend fun getMarkets(sportId: String): Result<List<Market>> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Check cache freshness
            val cacheMetadata = cachedMarketDao.getCacheMetadata(sportId)
            val isCacheStale = cacheMetadata?.isStale() ?: true
            val cachedMarkets = cachedMarketDao.getMarketsBySport(sportId)

            Log.d(TAG, "Cache check for $sportId: stale=$isCacheStale, cachedCount=${cachedMarkets.size}")

            // Step 2: If cache is fresh and not empty, return cached data
            if (!isCacheStale && cachedMarkets.isNotEmpty()) {
                Log.d(TAG, "Returning fresh cached data for $sportId")
                return@withContext Result.success(cachedMarkets.map { it.toDomain() })
            }

            // Step 3: Cache is stale or empty - fetch from network
            // But if we have cached data, return it first (optimistic UI)
            if (cachedMarkets.isNotEmpty()) {
                Log.d(TAG, "Returning stale cache while fetching fresh data for $sportId")
                // Trigger background refresh (fire-and-forget)
                refreshMarketsFromNetwork(sportId)
                return@withContext Result.success(cachedMarkets.map { it.toDomain() })
            }

            // Step 4: No cache - must fetch from network
            Log.d(TAG, "No cache available, fetching from network for $sportId")
            val networkResult = fetchMarketsFromNetwork(sportId)

            if (networkResult.isSuccess) {
                val markets = networkResult.getOrThrow()
                // Cache the results
                cacheMarkets(sportId, markets)
                Result.success(markets)
            } else {
                // Network failed and no cache - try mock data as fallback
                Log.w(TAG, "Network failed, falling back to mock data for $sportId")
                Result.success(MockDataSource.getMarkets(sportId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting markets for $sportId", e)
            // Fallback to mock data
            try {
                Result.success(MockDataSource.getMarkets(sportId))
            } catch (mockError: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get markets as Flow for reactive updates.
     */
    fun getMarketsFlow(sportId: String): Flow<List<Market>> {
        return cachedMarketDao.getMarketsBySportFlow(sportId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    /**
     * Force refresh markets from network (bypasses cache).
     */
    suspend fun refreshMarkets(sportId: String): Result<List<Market>> = withContext(Dispatchers.IO) {
        try {
            val networkResult = fetchMarketsFromNetwork(sportId)
            if (networkResult.isSuccess) {
                val markets = networkResult.getOrThrow()
                cacheMarkets(sportId, markets)
                Result.success(markets)
            } else {
                networkResult
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing markets for $sportId", e)
            Result.failure(e)
        }
    }

    /**
     * Background refresh without blocking the caller.
     */
    private suspend fun refreshMarketsFromNetwork(sportId: String) {
        try {
            val networkResult = fetchMarketsFromNetwork(sportId)
            if (networkResult.isSuccess) {
                val markets = networkResult.getOrThrow()
                cacheMarkets(sportId, markets)
                Log.d(TAG, "Background refresh complete for $sportId, cached ${markets.size} markets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background refresh failed for $sportId", e)
        }
    }

    /**
     * Fetch markets from network API.
     */
    private suspend fun fetchMarketsFromNetwork(sportId: String): Result<List<Market>> {
        return try {
            val response = oddsService.getUpcomingOdds(
                sportKey = sportId,
                markets = "h2h",
                oddsFormat = "decimal"
            )

            if (response.isSuccessful) {
                val oddsEvents = response.body() ?: emptyList()
                val markets = oddsEvents.map { event -> event.toMarket(sportId) }
                Log.d(TAG, "Fetched ${markets.size} markets from network for $sportId")
                Result.success(markets)
            } else {
                Log.e(TAG, "API error: ${response.code()} - ${response.message()}")
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching markets", e)
            Result.failure(e)
        }
    }

    /**
     * Cache markets in Room database.
     */
    private suspend fun cacheMarkets(sportId: String, markets: List<Market>) {
        try {
            val entities = markets.map { CachedMarketEntity.fromDomain(it) }
            cachedMarketDao.refreshMarkets(sportId, entities)
            Log.d(TAG, "Cached ${entities.size} markets for $sportId")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching markets", e)
        }
    }

    // ============ SINGLE MARKET ============

    /**
     * Get a single market by ID with cache-first strategy.
     */
    suspend fun getMarketById(marketId: String): Result<Market> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cachedMarket = cachedMarketDao.getMarketById(marketId)
            if (cachedMarket != null) {
                Log.d(TAG, "Returning cached market $marketId")
                return@withContext Result.success(cachedMarket.toDomain())
            }

            // Fallback to mock data
            val market = MockDataSource.getMarketById(marketId)
            if (market != null) {
                Result.success(market)
            } else {
                Result.failure(Exception("Market not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting market $marketId", e)
            Result.failure(e)
        }
    }

    /**
     * Get market by ID as Flow.
     */
    fun getMarketByIdFlow(marketId: String): Flow<Market?> {
        return cachedMarketDao.getMarketByIdFlow(marketId)
            .map { it?.toDomain() }
    }

    // ============ FAVORITES ============

    fun getFavoriteMarketIds(): Flow<List<String>> {
        return favoriteMarketDao.getAllFavorites().map { favorites ->
            favorites.map { it.marketId }
        }
    }

    suspend fun toggleFavorite(marketId: String) {
        if (favoriteMarketDao.isFavorite(marketId)) {
            favoriteMarketDao.removeFavorite(marketId)
        } else {
            favoriteMarketDao.addFavorite(FavoriteMarketEntity(marketId))
        }
    }

    suspend fun isFavorite(marketId: String): Boolean {
        return favoriteMarketDao.isFavorite(marketId)
    }

    // ============ CACHE MANAGEMENT ============

    /**
     * Clear all cached markets.
     */
    suspend fun clearCache() {
        cachedMarketDao.deleteAllMarkets()
    }

    /**
     * Clear expired cache entries.
     */
    suspend fun clearExpiredCache() {
        cachedMarketDao.deleteExpiredMarkets()
    }

    /**
     * Check if cache is stale for a sport.
     */
    suspend fun isCacheStale(sportId: String): Boolean {
        val metadata = cachedMarketDao.getCacheMetadata(sportId)
        return metadata?.isStale() ?: true
    }
}

/**
 * Extension function to convert OddsEvent to Market domain model.
 */
private fun OddsEvent.toMarket(sportId: String): Market {
    // Get first bookmaker's odds as default
    val firstBookmaker = bookmakers.firstOrNull()
    val moneylineOdds = firstBookmaker?.getMoneylineOdds()

    // Parse commence time
    val startTime = try {
        Instant.parse(commenceTime).toEpochMilli()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    return Market(
        id = id,
        sportId = sportId,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        league = sportTitle,
        startTime = startTime,
        status = MatchStatus.UPCOMING,
        odds = Odds(
            home = moneylineOdds?.homeTeamOdds ?: 0.0,
            draw = moneylineOdds?.drawOdds ?: 0.0,
            away = moneylineOdds?.awayTeamOdds ?: 0.0,
            lastUpdated = System.currentTimeMillis()
        ),
        stats = null
    )
}
