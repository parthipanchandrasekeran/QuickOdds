package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.quickodds.app.ai.model.AIAnalysisResponse

/**
 * Entity for caching AI analysis results.
 * Analysis is cached for 1 hour to avoid redundant API calls.
 */
@Entity(tableName = "cached_analysis")
data class CachedAnalysisEntity(
    @PrimaryKey
    val eventId: String,
    val analysisJson: String,
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        // Cache is valid for 1 hour
        const val CACHE_STALE_TIME_MS = 60 * 60 * 1000L

        private val gson = Gson()

        fun fromAnalysis(eventId: String, analysis: AIAnalysisResponse): CachedAnalysisEntity {
            return CachedAnalysisEntity(
                eventId = eventId,
                analysisJson = gson.toJson(analysis)
            )
        }
    }

    fun isStale(): Boolean {
        return System.currentTimeMillis() - cachedAt > CACHE_STALE_TIME_MS
    }

    fun toAnalysis(): AIAnalysisResponse? {
        return try {
            Gson().fromJson(analysisJson, AIAnalysisResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
