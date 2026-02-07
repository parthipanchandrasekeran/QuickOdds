package com.quickodds.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quickodds.app.data.local.dao.*
import com.quickodds.app.data.local.entity.*

/**
 * Room Database for Quick Odds app.
 * Manages virtual wallet, betting data, and cached markets.
 *
 * NOTE: The database instance is provided via Hilt in AppModule.
 * Do NOT use a manual singleton companion object here.
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
    version = 5,
    exportSchema = true
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
        const val DATABASE_NAME = "quickodds_db"

        /**
         * Migration from v4 to v5: No schema changes, only code-level additions (VOID status).
         * Room stores enums as strings, so adding a new enum value requires no SQL changes.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes needed - VOID is a new enum value stored as string
            }
        }
    }
}
