package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity storing AI prediction data at bet placement and actual outcome at settlement.
 * Used for tracking prediction accuracy by agent, sport, and confidence tier.
 */
@Entity(tableName = "prediction_records")
data class PredictionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Bet reference
    val betId: Long,
    val eventId: String,
    val sportKey: String,
    val matchName: String,

    // AI snapshot (ensemble recommendation)
    val recommendation: String,           // "HOME", "AWAY", "DRAW", "NO_BET"
    val confidence: Double,               // 0.0 to 1.0
    val projectedProbability: Double?,
    val edgePercentage: Double?,
    val isValueBet: Boolean,

    // Per-agent data (flat columns)
    val statModelerRecommendation: String?,
    val statModelerEdge: Double?,
    val statModelerFindsValue: Boolean?,

    val proScoutRecommendation: String?,
    val proScoutEdge: Double?,
    val proScoutFindsValue: Boolean?,

    val marketSharpRecommendation: String?,
    val marketSharpEdge: Double?,
    val marketSharpFindsValue: Boolean?,

    // User's pick (actual team name, not "HOME"/"AWAY")
    val selectedTeam: String,

    // Outcome (filled at settlement)
    val actualOutcome: String? = null,    // "WON" or "LOST"
    val settledAt: Long? = null,

    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Confidence tier for grouping accuracy stats.
     */
    val confidenceTier: String
        get() = when {
            confidence >= 0.90 -> "VERY_HIGH"
            confidence >= 0.75 -> "HIGH"
            confidence >= 0.60 -> "MEDIUM"
            else -> "LOW"
        }
}
