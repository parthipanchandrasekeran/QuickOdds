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
        CachedAnalysisEntity::class,
        PredictionRecord::class,
        UsageRecord::class
    ],
    version = 7,
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
    abstract fun predictionRecordDao(): PredictionRecordDao
    abstract fun usageRecordDao(): UsageRecordDao

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

        /**
         * Migration from v5 to v6: Add prediction_records table for accuracy tracking.
         */
        /**
         * Migration from v6 to v7: Add usage_records table for daily usage tracking.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS usage_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        actionType TEXT NOT NULL,
                        dateString TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS prediction_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        betId INTEGER NOT NULL,
                        eventId TEXT NOT NULL,
                        sportKey TEXT NOT NULL,
                        matchName TEXT NOT NULL,
                        recommendation TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        projectedProbability REAL,
                        edgePercentage REAL,
                        isValueBet INTEGER NOT NULL,
                        statModelerRecommendation TEXT,
                        statModelerEdge REAL,
                        statModelerFindsValue INTEGER,
                        proScoutRecommendation TEXT,
                        proScoutEdge REAL,
                        proScoutFindsValue INTEGER,
                        marketSharpRecommendation TEXT,
                        marketSharpEdge REAL,
                        marketSharpFindsValue INTEGER,
                        selectedTeam TEXT NOT NULL,
                        actualOutcome TEXT,
                        settledAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
