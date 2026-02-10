package com.quickodds.app.data.local.dao

import androidx.room.*
import com.quickodds.app.data.local.entity.CachedAnalysisEntity

/**
 * DAO for cached AI analysis results.
 */
@Dao
interface CachedAnalysisDao {

    @Query("SELECT * FROM cached_analysis WHERE eventId = :eventId")
    suspend fun getAnalysis(eventId: String): CachedAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: CachedAnalysisEntity)

    @Query("DELETE FROM cached_analysis WHERE eventId = :eventId")
    suspend fun deleteAnalysis(eventId: String)

    @Query("DELETE FROM cached_analysis WHERE cachedAt < :cutoffTime")
    suspend fun deleteStaleAnalyses(cutoffTime: Long)

    @Query("SELECT * FROM cached_analysis WHERE eventId IN (:eventIds)")
    suspend fun getAnalysesByIds(eventIds: List<String>): List<CachedAnalysisEntity>

    @Query("DELETE FROM cached_analysis")
    suspend fun clearAll()
}
