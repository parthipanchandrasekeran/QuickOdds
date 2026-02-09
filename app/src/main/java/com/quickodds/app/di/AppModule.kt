package com.quickodds.app.di

import android.content.Context
import androidx.room.Room
import com.quickodds.app.AppConfig
import com.quickodds.app.BuildConfig
import com.quickodds.app.ai.AIAnalysisService
import com.quickodds.app.ai.api.AnthropicApi
import com.quickodds.app.data.local.AppDatabase
import com.quickodds.app.data.local.dao.CachedAnalysisDao
import com.quickodds.app.data.local.dao.CachedMarketDao
import com.quickodds.app.data.local.dao.CachedOddsEventDao
import com.quickodds.app.data.local.dao.FavoriteMarketDao
import com.quickodds.app.data.local.dao.PredictionRecordDao
import com.quickodds.app.data.local.dao.UserWalletDao
import com.quickodds.app.data.local.dao.VirtualBetDao
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.data.remote.api.OddsCloudFunctionService
import com.quickodds.app.data.remote.api.SportsApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OddsApiKey

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnthropicApiKey

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OddsRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnthropicRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudFunctionRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ============ API KEYS ============

    @Provides
    @OddsApiKey
    fun provideOddsApiKey(): String = BuildConfig.ODDS_API_KEY.ifEmpty { "" }

    @Provides
    @AnthropicApiKey
    fun provideAnthropicApiKey(): String = BuildConfig.ANTHROPIC_API_KEY.ifEmpty { "" }

    // ============ OKHTTP ============

    @Provides
    @Singleton
    fun provideCache(@ApplicationContext context: Context): Cache {
        val cacheDir = File(context.cacheDir, "http_cache")
        return Cache(cacheDir, AppConfig.HTTP_CACHE_SIZE_BYTES)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        cache: Cache
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val cachingInterceptor = Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (request.url.toString().contains("/odds") ||
                request.url.toString().contains("/sports")) {
                val cacheControl = CacheControl.Builder()
                    .maxAge(AppConfig.CACHE_STALE_MINUTES, TimeUnit.MINUTES)
                    .build()

                response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Cache-Control")
                    .header("Cache-Control", cacheControl.toString())
                    .build()
            } else {
                response
            }
        }

        val offlineCacheInterceptor = Interceptor { chain ->
            var request = chain.request()

            if (!isNetworkAvailable(context)) {
                val cacheControl = CacheControl.Builder()
                    .maxStale(AppConfig.OFFLINE_CACHE_HOURS, TimeUnit.HOURS)
                    .build()

                request = request.newBuilder()
                    .cacheControl(cacheControl)
                    .build()
            }

            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(offlineCacheInterceptor)
            .addNetworkInterceptor(cachingInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(AppConfig.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ============ RETROFIT INSTANCES ============

    @Provides
    @Singleton
    @OddsRetrofit
    fun provideOddsRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SportsApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSportsApiService(@OddsRetrofit retrofit: Retrofit): SportsApiService {
        return retrofit.create(SportsApiService::class.java)
    }

    @Provides
    @Singleton
    @CloudFunctionRetrofit
    fun provideCloudFunctionRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OddsCloudFunctionService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOddsCloudFunctionService(@CloudFunctionRetrofit retrofit: Retrofit): OddsCloudFunctionService {
        return retrofit.create(OddsCloudFunctionService::class.java)
    }

    @Provides
    @Singleton
    @AnthropicRetrofit
    fun provideAnthropicRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(AnthropicApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAnthropicApi(@AnthropicRetrofit retrofit: Retrofit): AnthropicApi {
        return retrofit.create(AnthropicApi::class.java)
    }

    // ============ AI ANALYSIS SERVICE ============

    @Provides
    @Singleton
    fun provideAIAnalysisService(api: AnthropicApi): AIAnalysisService {
        return AIAnalysisService(api)
    }

    // ============ ROOM DATABASE ============

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideUserWalletDao(database: AppDatabase): UserWalletDao = database.userWalletDao()

    @Provides
    @Singleton
    fun provideVirtualBetDao(database: AppDatabase): VirtualBetDao = database.virtualBetDao()

    @Provides
    @Singleton
    fun provideCachedMarketDao(database: AppDatabase): CachedMarketDao = database.cachedMarketDao()

    @Provides
    @Singleton
    fun provideFavoriteMarketDao(database: AppDatabase): FavoriteMarketDao = database.favoriteMarketDao()

    @Provides
    @Singleton
    fun provideCachedOddsEventDao(database: AppDatabase): CachedOddsEventDao = database.cachedOddsEventDao()

    @Provides
    @Singleton
    fun provideCachedAnalysisDao(database: AppDatabase): CachedAnalysisDao = database.cachedAnalysisDao()

    @Provides
    @Singleton
    fun providePredictionRecordDao(database: AppDatabase): PredictionRecordDao = database.predictionRecordDao()
}

/**
 * Module for database initialization.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseInitModule {

    @Provides
    @Singleton
    fun provideDatabaseInitializer(
        userWalletDao: UserWalletDao
    ): DatabaseInitializer {
        return DatabaseInitializer(userWalletDao)
    }
}

/**
 * Initializes database with default data on first run.
 * This is the SINGLE source of truth for wallet initialization.
 */
class DatabaseInitializer(
    private val userWalletDao: UserWalletDao
) {
    suspend fun initializeWallet() {
        val existingWallet = userWalletDao.getWallet()
        if (existingWallet == null) {
            userWalletDao.insertWallet(
                UserWallet(
                    balance = AppConfig.INITIAL_WALLET_BALANCE,
                    currency = AppConfig.DEFAULT_CURRENCY
                )
            )
        } else if (existingWallet.balance == 10000.0) {
            // One-time fix: reset balance from old default to new default
            userWalletDao.setBalance(AppConfig.INITIAL_WALLET_BALANCE)
        }
    }
}
