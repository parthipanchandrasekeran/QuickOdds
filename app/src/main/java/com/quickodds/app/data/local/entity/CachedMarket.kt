package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quickodds.app.domain.model.Market
import com.quickodds.app.domain.model.MatchStats
import com.quickodds.app.domain.model.MatchStatus
import com.quickodds.app.domain.model.Odds

/**
 * Room entity for caching Market data.
 * Implements cache-first strategy with 30-minute stale time.
 */
@Entity(tableName = "cached_markets")
data class CachedMarketEntity(
    @PrimaryKey
    val id: String,
    val sportId: String,
    val homeTeam: String,
    val awayTeam: String,
    val league: String,
    val startTime: Long,
    val status: String,
    val oddsHome: Double,
    val oddsDraw: Double,
    val oddsAway: Double,
    val oddsLastUpdated: Long,
    val statsJson: String?,  // Serialized MatchStats as JSON
    val cachedAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()  // Track when data was last updated from API
) {
    companion object {
        const val CACHE_STALE_TIME_MS = 15 * 60 * 1000L  // 15 minutes for API credit savings

        fun fromDomain(market: Market): CachedMarketEntity {
            val now = System.currentTimeMillis()
            return CachedMarketEntity(
                id = market.id,
                sportId = market.sportId,
                homeTeam = market.homeTeam,
                awayTeam = market.awayTeam,
                league = market.league,
                startTime = market.startTime,
                status = market.status.name,
                oddsHome = market.odds.home,
                oddsDraw = market.odds.draw,
                oddsAway = market.odds.away,
                oddsLastUpdated = market.odds.lastUpdated,
                statsJson = market.stats?.let { Gson().toJson(it) },
                cachedAt = now,
                lastUpdated = now
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

    fun toDomain(): Market {
        val stats: MatchStats? = statsJson?.let {
            try {
                Gson().fromJson(it, MatchStats::class.java)
            } catch (e: Exception) {
                null
            }
        }

        return Market(
            id = id,
            sportId = sportId,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            league = league,
            startTime = startTime,
            status = MatchStatus.valueOf(status),
            odds = Odds(
                home = oddsHome,
                draw = oddsDraw,
                away = oddsAway,
                lastUpdated = oddsLastUpdated
            ),
            stats = stats
        )
    }

    fun isStale(): Boolean {
        return System.currentTimeMillis() - cachedAt > CACHE_STALE_TIME_MS
    }
}

/**
 * Metadata entity to track when each sport's markets were last refreshed.
 */
@Entity(tableName = "cache_metadata")
data class CacheMetadata(
    @PrimaryKey
    val sportId: String,
    val lastRefreshTime: Long = System.currentTimeMillis()
) {
    companion object {
        const val CACHE_STALE_TIME_MS = 15 * 60 * 1000L  // 15 minutes for API credit savings
    }

    fun isStale(): Boolean {
        return System.currentTimeMillis() - lastRefreshTime > CACHE_STALE_TIME_MS
    }

    /**
     * Check if we should fetch from API based on last refresh time.
     */
    fun shouldFetchFromApi(): Boolean = isStale()
}
