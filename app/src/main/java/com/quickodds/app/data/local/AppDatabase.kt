package com.quickodds.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quickodds.app.data.local.dao.*
import com.quickodds.app.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room Database for Quick Odds app.
 * Manages virtual wallet, betting data, and cached markets.
 */
@Database(
    entities = [
        UserWallet::class,
        VirtualBet::class,
        CachedMarketEntity::class,
        CacheMetadata::class,
        FavoriteMarketEntity::class,
        CachedOddsEventEntity::class,
        OddsCacheMetadata::class,
        CachedAnalysisEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userWalletDao(): UserWalletDao
    abstract fun virtualBetDao(): VirtualBetDao
    abstract fun cachedMarketDao(): CachedMarketDao
    abstract fun favoriteMarketDao(): FavoriteMarketDao
    abstract fun cachedOddsEventDao(): CachedOddsEventDao
    abstract fun cachedAnalysisDao(): CachedAnalysisDao

    companion object {
        private const val DATABASE_NAME = "quickodds_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration() // Dev only - use migrations in production
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Callback to initialize database with default data.
         */
        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        // Initialize default wallet
                        database.userWalletDao().insertWallet(
                            UserWallet.createDefault(
                                initialBalance = 10000.0,
                                currency = "USD"
                            )
                        )
                    }
                }
            }
        }
    }
}
