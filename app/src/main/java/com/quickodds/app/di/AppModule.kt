package com.quickodds.app.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quickodds.app.BuildConfig
import com.quickodds.app.ai.AIAnalysisService
import com.quickodds.app.ai.api.AnthropicApi
import com.quickodds.app.data.local.AppDatabase
import com.quickodds.app.data.local.dao.CachedAnalysisDao
import com.quickodds.app.data.local.dao.CachedMarketDao
import com.quickodds.app.data.local.dao.CachedOddsEventDao
import com.quickodds.app.data.local.dao.FavoriteMarketDao
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

/**
 * Qualifier annotations for different API keys.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OddsApiKey

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnthropicApiKey

/**
 * Qualifier for different Retrofit instances.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OddsRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnthropicRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudFunctionRetrofit

/**
 * Hilt Module providing all app dependencies.
 *
 * Provides:
 * - Room Database and DAOs
 * - Retrofit services (Sports Odds API, Anthropic Claude API)
 * - AI Analysis Service
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ============ API KEYS ============

    @Provides
    @OddsApiKey
    fun provideOddsApiKey(): String {
        return BuildConfig.ODDS_API_KEY.ifEmpty {
            // Fallback for development - replace with your key or use local.properties
            ""
        }
    }

    @Provides
    @AnthropicApiKey
    fun provideAnthropicApiKey(): String {
        return BuildConfig.ANTHROPIC_API_KEY.ifEmpty {
            // Fallback for development - replace with your key or use local.properties
            ""
        }
    }

    // ============ OKHTTP CACHE ============

    private const val CACHE_SIZE = 10L * 1024 * 1024  // 10 MB
    private const val ODDS_CACHE_MAX_AGE_MINUTES = 15  // 15 minutes for odds requests

    @Provides
    @Singleton
    fun provideCache(@ApplicationContext context: Context): Cache {
        val cacheDir = File(context.cacheDir, "http_cache")
        return Cache(cacheDir, CACHE_SIZE)
    }

    // ============ OKHTTP CLIENT ============

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

        // Caching interceptor - adds cache headers to odds responses
        val cachingInterceptor = Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            // Apply caching for odds endpoints
            if (request.url.toString().contains("/odds") ||
                request.url.toString().contains("/sports")) {
                val cacheControl = CacheControl.Builder()
                    .maxAge(ODDS_CACHE_MAX_AGE_MINUTES, TimeUnit.MINUTES)
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

        // Offline cache interceptor - serve from cache when offline
        val offlineCacheInterceptor = Interceptor { chain ->
            var request = chain.request()

            // If no network, force cache
            if (!isNetworkAvailable(context)) {
                val cacheControl = CacheControl.Builder()
                    .maxStale(24, TimeUnit.HOURS)
                    .build()

                request = request.newBuilder()
                    .cacheControl(cacheControl)
                    .build()
            }

            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(offlineCacheInterceptor)  // Request interceptor (before network)
            .addNetworkInterceptor(cachingInterceptor)  // Response interceptor (after network)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Check if network is available.
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ============ RETROFIT - SPORTS ODDS API ============

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

    // ============ RETROFIT - CLOUD FUNCTION (SECURE PROXY) ============

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

    // ============ RETROFIT - ANTHROPIC CLAUDE API ============

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
    fun provideAIAnalysisService(): AIAnalysisService {
        // API key is now stored securely in Firebase, no local key needed
        return AIAnalysisService()
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
            "quickodds_db"
        )
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Initialize default wallet on first database creation
                    CoroutineScope(Dispatchers.IO).launch {
                        // Note: We need to get the DAO after the database is created
                        // This is handled by the database callback mechanism
                    }
                }
            })
            .fallbackToDestructiveMigration() // Dev only - use proper migrations in production
            .build()
    }

    @Provides
    @Singleton
    fun provideUserWalletDao(database: AppDatabase): UserWalletDao {
        return database.userWalletDao()
    }

    @Provides
    @Singleton
    fun provideVirtualBetDao(database: AppDatabase): VirtualBetDao {
        return database.virtualBetDao()
    }

    @Provides
    @Singleton
    fun provideCachedMarketDao(database: AppDatabase): CachedMarketDao {
        return database.cachedMarketDao()
    }

    @Provides
    @Singleton
    fun provideFavoriteMarketDao(database: AppDatabase): FavoriteMarketDao {
        return database.favoriteMarketDao()
    }

    @Provides
    @Singleton
    fun provideCachedOddsEventDao(database: AppDatabase): CachedOddsEventDao {
        return database.cachedOddsEventDao()
    }

    @Provides
    @Singleton
    fun provideCachedAnalysisDao(database: AppDatabase): CachedAnalysisDao {
        return database.cachedAnalysisDao()
    }
}

/**
 * Module for initializing database data.
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
 * Helper class to initialize database with default data.
 */
class DatabaseInitializer(
    private val userWalletDao: UserWalletDao
) {
    suspend fun initializeWallet() {
        val existingWallet = userWalletDao.getWallet()
        if (existingWallet == null) {
            userWalletDao.insertWallet(
                UserWallet(
                    balance = 10000.0,
                    currency = "USD"
                )
            )
        }
    }
}
