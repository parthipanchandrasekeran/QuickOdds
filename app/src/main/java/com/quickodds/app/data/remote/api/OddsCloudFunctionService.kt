package com.quickodds.app.data.remote.api

import com.quickodds.app.data.remote.dto.OddsEvent
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for the Firebase Cloud Function proxy.
 *
 * The Cloud Function handles the API key securely on the server side,
 * so no API key is needed in the Android app.
 *
 * IMPORTANT: Replace CLOUD_FUNCTION_BASE_URL with your actual Firebase
 * Cloud Function URL after deployment.
 */
interface OddsCloudFunctionService {

    companion object {
        // Firebase Cloud Function URL
        const val BASE_URL = "https://us-central1-quick-odds.cloudfunctions.net/"
    }

    /**
     * Get upcoming matches with odds for a specific sport.
     * Calls the Cloud Function which proxies to The-Odds-API.
     *
     * @param sportKey Sport identifier (e.g., "soccer_epl", "americanfootball_nfl")
     * @param regions Bookmaker regions (us, uk, eu, au) - comma separated
     * @param markets Market types (h2h, spreads, totals) - comma separated
     * @param oddsFormat Format for odds (decimal, american)
     */
    @GET("getOdds")
    suspend fun getUpcomingOdds(
        @Query("sport") sportKey: String,
        @Query("regions") regions: String = "us,uk,eu",
        @Query("markets") markets: String = "h2h",
        @Query("oddsFormat") oddsFormat: String = "decimal"
    ): Response<List<OddsEvent>>

    /**
     * Get list of all available sports.
     */
    @GET("getSports")
    suspend fun getSports(): Response<List<SportInfo>>

    /**
     * Get scores/results for matches.
     * Used for bet settlement to determine match outcomes.
     *
     * @param sportKey Sport identifier (e.g., "soccer_epl")
     * @param eventIds Comma-separated list of event IDs to filter
     * @param daysFrom Number of days to look back for completed matches
     */
    /**
     * Get scores/results for matches.
     * Used for bet settlement to determine match outcomes.
     *
     * @param sportKey Sport identifier (e.g., "soccer_epl")
     * @param eventIds Comma-separated list of event IDs to filter
     * @param daysFrom Number of days to look back for completed matches
     */
    @GET("getScores")
    suspend fun getScores(
        @Query("sport") sportKey: String,
        @Query("eventIds") eventIds: String = "",
        @Query("daysFrom") daysFrom: Int = 3
    ): Response<List<ScoreEvent>>
}
// Note: ScoreEvent and TeamScore are defined in SportsApiService.kt
