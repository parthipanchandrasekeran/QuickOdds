package com.quickodds.app.data.repository

import com.quickodds.app.data.remote.NetworkModule
import com.quickodds.app.data.remote.api.SportsApiService
import com.quickodds.app.data.remote.dto.OddsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for fetching odds data from The-Odds-API.
 * Handles API calls and error handling.
 */
class OddsRepository(
    private val apiService: SportsApiService = NetworkModule.sportsApiService,
    private val apiKey: String = "" // TODO: Add your API key
) {

    /**
     * Fetch upcoming matches with odds for a specific sport.
     */
    suspend fun getUpcomingOdds(
        sportKey: String,
        regions: String = "us",
        markets: String = SportsApiService.MARKET_H2H
    ): Result<List<OddsEvent>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUpcomingOdds(
                sportKey = sportKey,
                apiKey = apiKey,
                regions = regions,
                markets = markets
            )

            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(
                    Exception("API Error: ${response.code()} - ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch MLS (Major League Soccer) upcoming matches.
     */
    suspend fun getMLSOdds(): Result<List<OddsEvent>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMLSOdds(apiKey = apiKey)

            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(
                    Exception("API Error: ${response.code()} - ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch NFL (National Football League) upcoming matches.
     */
    suspend fun getNFLOdds(): Result<List<OddsEvent>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getNFLOdds(apiKey = apiKey)

            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(
                    Exception("API Error: ${response.code()} - ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch available sports list.
     */
    suspend fun getAvailableSports(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSports(apiKey = apiKey)

            if (response.isSuccessful) {
                val sports = response.body()?.map { it.key } ?: emptyList()
                Result.success(sports)
            } else {
                Result.failure(
                    Exception("API Error: ${response.code()} - ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
