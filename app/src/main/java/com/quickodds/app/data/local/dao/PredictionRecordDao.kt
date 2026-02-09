package com.quickodds.app.data.local.dao

import androidx.room.*
import com.quickodds.app.data.local.entity.PredictionRecord
import kotlinx.coroutines.flow.Flow

/**
 * DAO for prediction accuracy tracking.
 * Provides queries for overall, per-sport, per-confidence-tier, and per-agent accuracy.
 */
@Dao
interface PredictionRecordDao {

    // ============ INSERT / UPDATE ============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PredictionRecord): Long

    /**
     * Record the actual outcome when a bet settles.
     */
    @Query("UPDATE prediction_records SET actualOutcome = :outcome, settledAt = :settledAt WHERE betId = :betId")
    suspend fun recordOutcome(betId: Long, outcome: String, settledAt: Long = System.currentTimeMillis())

    // ============ OVERALL ACCURACY (FLOW) ============

    @Query("SELECT COUNT(*) FROM prediction_records WHERE actualOutcome IS NOT NULL")
    fun observeSettledCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM prediction_records WHERE actualOutcome = 'WON'")
    fun observeCorrectCount(): Flow<Int>

    // ============ PER-SPORT ACCURACY (FLOW) ============

    @Query("""
        SELECT sportKey,
               COUNT(*) AS total,
               SUM(CASE WHEN actualOutcome = 'WON' THEN 1 ELSE 0 END) AS correct
        FROM prediction_records
        WHERE actualOutcome IS NOT NULL
        GROUP BY sportKey
    """)
    fun observeAccuracyBySport(): Flow<List<SportAccuracy>>

    // ============ PER-CONFIDENCE-TIER ACCURACY (FLOW) ============

    @Query("""
        SELECT
            CASE
                WHEN confidence >= 0.90 THEN 'VERY_HIGH'
                WHEN confidence >= 0.75 THEN 'HIGH'
                WHEN confidence >= 0.60 THEN 'MEDIUM'
                ELSE 'LOW'
            END AS tier,
            COUNT(*) AS total,
            SUM(CASE WHEN actualOutcome = 'WON' THEN 1 ELSE 0 END) AS correct,
            AVG(confidence) AS avgConfidence
        FROM prediction_records
        WHERE actualOutcome IS NOT NULL
        GROUP BY tier
    """)
    fun observeAccuracyByConfidenceTier(): Flow<List<TierAccuracy>>

    // ============ PER-AGENT ACCURACY (SUSPEND) ============

    /**
     * Count how many times Stat Modeler's recommendation matched the selected team AND the bet won.
     */
    @Query("""
        SELECT COUNT(*) FROM prediction_records
        WHERE actualOutcome IS NOT NULL
          AND statModelerRecommendation IS NOT NULL
    """)
    suspend fun getStatModelerTotal(): Int

    @Query("""
        SELECT COUNT(*) FROM prediction_records
        WHERE actualOutcome = 'WON'
          AND statModelerRecommendation IS NOT NULL
          AND statModelerRecommendation = recommendation
    """)
    suspend fun getStatModelerCorrect(): Int

    @Query("""
        SELECT COUNT(*) FROM prediction_records
        WHERE actualOutcome IS NOT NULL
          AND proScoutRecommendation IS NOT NULL
    """)
    suspend fun getProScoutTotal(): Int

    @Query("""
        SELECT COUNT(*) FROM prediction_records
        WHERE actualOutcome = 'WON'
          AND proScoutRecommendation IS NOT NULL
          AND proScoutRecommendation = recommendation
    """)
    suspend fun getProScoutCorrect(): Int

    @Query("""
        SELECT COUNT(*) FROM prediction_records
        WHERE actualOutcome IS NOT NULL
          AND marketSharpRecommendation IS NOT NULL
    """)
    suspend fun getMarketSharpTotal(): Int

    @Query("""
        SELECT COUNT(*) FROM prediction_records
        WHERE actualOutcome = 'WON'
          AND marketSharpRecommendation IS NOT NULL
          AND marketSharpRecommendation = recommendation
    """)
    suspend fun getMarketSharpCorrect(): Int

    // ============ RECENT PREDICTIONS ============

    @Query("""
        SELECT * FROM prediction_records
        WHERE actualOutcome IS NOT NULL
        ORDER BY settledAt DESC
        LIMIT :limit
    """)
    fun observeRecentSettled(limit: Int = 20): Flow<List<PredictionRecord>>

    // ============ TOTAL PREDICTIONS ============

    @Query("SELECT COUNT(*) FROM prediction_records")
    fun observeTotalCount(): Flow<Int>
}

/**
 * Result for per-sport accuracy query.
 */
data class SportAccuracy(
    val sportKey: String,
    val total: Int,
    val correct: Int
) {
    val accuracy: Double get() = if (total > 0) correct.toDouble() / total else 0.0
}

/**
 * Result for per-confidence-tier accuracy query.
 */
data class TierAccuracy(
    val tier: String,
    val total: Int,
    val correct: Int,
    val avgConfidence: Double
) {
    val accuracy: Double get() = if (total > 0) correct.toDouble() / total else 0.0
}
