package com.quickodds.app.data.remote

import com.quickodds.app.data.remote.api.SportsApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network module providing Retrofit instance and API service.
 * Follows singleton pattern for efficiency.
 */
object NetworkModule {

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L

    /**
     * OkHttp client with logging and timeouts.
     */
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit instance configured for The-Odds-API.
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SportsApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Sports API service instance.
     */
    val sportsApiService: SportsApiService by lazy {
        retrofit.create(SportsApiService::class.java)
    }
}
