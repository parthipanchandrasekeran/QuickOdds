package com.quickodds.app.data.remote.api

import com.quickodds.app.data.remote.dto.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OddsApi {

    @GET("sports")
    suspend fun getSports(): List<SportDto>

    @GET("sports/{sportId}/markets")
    suspend fun getMarkets(
        @Path("sportId") sportId: String,
        @Query("status") status: String? = null
    ): List<MarketDto>

    @GET("markets/{marketId}")
    suspend fun getMarketDetails(
        @Path("marketId") marketId: String
    ): MarketDto

    @GET("markets/{marketId}/stats")
    suspend fun getMatchStats(
        @Path("marketId") marketId: String
    ): MatchStatsDto

    companion object {
        const val BASE_URL = "https://api.quickodds.example.com/v1/"
    }
}
