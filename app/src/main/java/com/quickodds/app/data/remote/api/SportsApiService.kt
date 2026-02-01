package com.quickodds.app.data.remote.api

import com.quickodds.app.data.remote.dto.OddsEvent
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface for The-Odds-API.
 *
 * API Documentation: https://the-odds-api.com/liveapi/guides/v4/
 * Base URL: https://api.the-odds-api.com/v4/
 *
 * Note: Requires API key passed as query parameter.
 */
interface SportsApiService {

    companion object {
        const val BASE_URL = "https://api.the-odds-api.com/v4/"

        // Available sport keys
        const val SPORT_MLS = "soccer_usa_mls"
        const val SPORT_NFL = "americanfootball_nfl"
        const val SPORT_NBA = "basketball_nba"
        const val SPORT_EPL = "soccer_epl"
        const val SPORT_NHL = "icehockey_nhl"
        const val SPORT_MLB = "baseball_mlb"

        // Market types
        const val MARKET_H2H = "h2h"           // Moneyline
        const val MARKET_SPREADS = "spreads"   // Point spreads
        const val MARKET_TOTALS = "totals"     // Over/Under

        // Odds format
        const val FORMAT_DECIMAL = "decimal"
        const val FORMAT_AMERICAN = "american"
    }

    // ============ FETCH UPCOMING MATCHES WITH ODDS ============

    /**
     * Get upcoming matches with odds for a specific sport.
     *
     * @param sportKey Sport identifier (e.g., "soccer_usa_mls", "americanfootball_nfl")
     * @param apiKey Your API key from the-odds-api.com
     * @param regions Bookmaker regions (us, uk, eu, au) - comma separated
     * @param markets Market types (h2h, spreads, totals) - comma separated
     * @param oddsFormat Format for odds (decimal, american)
     *
     * Example: GET /sports/soccer_usa_mls/odds?apiKey=xxx&regions=us&markets=h2h
     */
    @GET("sports/{sport}/odds")
    suspend fun getUpcomingOdds(
        @Path("sport") sportKey: String,
        @Query("apiKey") apiKey: String,
        @Query("regions") regions: String = "us",
        @Query("markets") markets: String = MARKET_H2H,
        @Query("oddsFormat") oddsFormat: String = FORMAT_DECIMAL
    ): Response<List<OddsEvent>>

    /**
     * Get MLS (Major League Soccer) upcoming matches with moneyline odds.
     */
    @GET("sports/soccer_usa_mls/odds")
    suspend fun getMLSOdds(
        @Query("apiKey") apiKey: String,
        @Query("regions") regions: String = "us",
        @Query("markets") markets: String = MARKET_H2H,
        @Query("oddsFormat") oddsFormat: String = FORMAT_DECIMAL
    ): Response<List<OddsEvent>>

    /**
     * Get NFL (National Football League) upcoming matches with moneyline odds.
     */
    @GET("sports/americanfootball_nfl/odds")
    suspend fun getNFLOdds(
        @Query("apiKey") apiKey: String,
        @Query("regions") regions: String = "us",
        @Query("markets") markets: String = MARKET_H2H,
        @Query("oddsFormat") oddsFormat: String = FORMAT_DECIMAL
    ): Response<List<OddsEvent>>

    // ============ LIST AVAILABLE SPORTS ============

    /**
     * Get list of all available sports.
     */
    @GET("sports")
    suspend fun getSports(
        @Query("apiKey") apiKey: String,
        @Query("all") includeAll: Boolean = false   // Include out-of-season sports
    ): Response<List<SportInfo>>

    // ============ HISTORICAL ODDS (if needed) ============

    /**
     * Get historical odds for a specific event.
     */
    @GET("sports/{sport}/odds-history")
    suspend fun getHistoricalOdds(
        @Path("sport") sportKey: String,
        @Query("apiKey") apiKey: String,
        @Query("eventIds") eventIds: String,
        @Query("regions") regions: String = "us",
        @Query("markets") markets: String = MARKET_H2H,
        @Query("oddsFormat") oddsFormat: String = FORMAT_DECIMAL
    ): Response<List<OddsEvent>>

    // ============ SCORES ============

    /**
     * Get scores for completed and in-progress events.
     */
    @GET("sports/{sport}/scores")
    suspend fun getScores(
        @Path("sport") sportKey: String,
        @Query("apiKey") apiKey: String,
        @Query("daysFrom") daysFrom: Int = 1    // Days in the past to include
    ): Response<List<ScoreEvent>>
}

/**
 * Sport information from the API.
 */
data class SportInfo(
    val key: String,                    // e.g., "soccer_usa_mls"
    val group: String,                  // e.g., "Soccer"
    val title: String,                  // e.g., "MLS"
    val description: String,
    val active: Boolean,                // Is the sport currently in season
    val has_outrights: Boolean          // Supports outright/futures betting
)

/**
 * Score/result information for an event.
 */
data class ScoreEvent(
    val id: String,
    val sport_key: String,
    val sport_title: String,
    val commence_time: String,
    val completed: Boolean,
    val home_team: String,
    val away_team: String,
    val scores: List<TeamScore>?,
    val last_update: String?
)

data class TeamScore(
    val name: String,
    val score: String
)
