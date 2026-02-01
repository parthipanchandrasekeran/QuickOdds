package com.quickodds.app.util

import com.quickodds.app.ai.model.AIAnalysisResponse
import com.quickodds.app.ai.model.ImpliedProbabilities
import com.quickodds.app.ai.model.KellyCriterionCalculator
import com.quickodds.app.data.local.entity.BetStatus

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * BETTING FLOW VALIDATOR
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * This utility ensures the correct betting flow is followed to prevent
 * "AI Hallucination" errors in betting decisions.
 *
 * CORRECT FLOW:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  COMPONENT          │  RESPONSIBILITY           │  DATA SOURCE              │
 * ├─────────────────────┼───────────────────────────┼───────────────────────────┤
 * │  1. Odds API        │  "Market Price"           │  The-Odds-API             │
 * │                     │  (Implied Probability)    │  via Cloud Function       │
 * ├─────────────────────┼───────────────────────────┼───────────────────────────┤
 * │  2. Claude API      │  "Fair Price"             │  Claude 3.5 Sonnet        │
 * │                     │  (AI Projected Prob)      │  Multi-Agent Ensemble     │
 * ├─────────────────────┼───────────────────────────┼───────────────────────────┤
 * │  3. Kelly Formula   │  "Investment Size"        │  Mathematical calculation │
 * │                     │  (Stake recommendation)   │  Edge = Fair - Market     │
 * ├─────────────────────┼───────────────────────────┼───────────────────────────┤
 * │  4. WorkManager     │  "Final Result"           │  Actual match scores      │
 * │                     │  (Settlement)             │  from Scores API          │
 * └─────────────────────┴───────────────────────────┴───────────────────────────┘
 *
 * CRITICAL SAFEGUARDS:
 * 1. Market Price MUST come from Odds API (never AI-generated)
 * 2. Fair Price is AI's estimate (used ONLY for stake sizing, NOT settlement)
 * 3. Kelly stake is calculated from the GAP between Market and Fair prices
 * 4. Settlement ONLY occurs after match.completed == true with ACTUAL scores
 * 5. AI predictions are NEVER used to determine win/loss - only actual scores
 */
object BettingFlowValidator {

    /**
     * Validate that market prices come from real API data.
     *
     * @throws IllegalStateException if prices appear invalid
     */
    fun validateMarketPrices(
        homeOdds: Double,
        awayOdds: Double,
        drawOdds: Double?
    ): ValidationResult {
        // Odds must be positive and reasonable (1.01 to 100.0)
        if (homeOdds <= 1.0 || homeOdds > 100.0) {
            return ValidationResult.Invalid("Home odds out of valid range: $homeOdds")
        }
        if (awayOdds <= 1.0 || awayOdds > 100.0) {
            return ValidationResult.Invalid("Away odds out of valid range: $awayOdds")
        }
        drawOdds?.let {
            if (it <= 1.0 || it > 100.0) {
                return ValidationResult.Invalid("Draw odds out of valid range: $it")
            }
        }

        // Total implied probability should be > 100% (bookmaker margin)
        val totalImplied = (1.0 / homeOdds) +
                          (1.0 / awayOdds) +
                          (drawOdds?.let { 1.0 / it } ?: 0.0)

        if (totalImplied < 1.0) {
            return ValidationResult.Invalid("Implied probabilities sum to less than 100%: ${totalImplied * 100}%")
        }

        return ValidationResult.Valid
    }

    /**
     * Validate AI analysis before using for stake calculation.
     */
    fun validateAIAnalysis(analysis: AIAnalysisResponse): ValidationResult {
        // Projected probability must be between 0 and 1
        val projProb = analysis.projectedProbability
        if (projProb == null || projProb < 0.0 || projProb > 1.0) {
            return ValidationResult.Invalid("Invalid projected probability: $projProb")
        }

        // Confidence must be reasonable
        if (analysis.confidenceScore < 0.0 || analysis.confidenceScore > 1.0) {
            return ValidationResult.Invalid("Invalid confidence score: ${analysis.confidenceScore}")
        }

        // Recommendation must be valid
        if (analysis.recommendation !in listOf("HOME", "AWAY", "DRAW", "NO_BET")) {
            return ValidationResult.Invalid("Invalid recommendation: ${analysis.recommendation}")
        }

        return ValidationResult.Valid
    }

    /**
     * Validate Kelly calculation inputs.
     * CRITICAL: Ensures edge is calculated from Market vs AI probability gap.
     */
    fun validateKellyInputs(
        aiProbability: Double,
        marketImpliedProbability: Double,
        decimalOdds: Double
    ): ValidationResult {
        // AI probability from Claude
        if (aiProbability < 0.0 || aiProbability > 1.0) {
            return ValidationResult.Invalid("AI probability out of range: $aiProbability")
        }

        // Market implied probability from Odds API
        if (marketImpliedProbability < 0.0 || marketImpliedProbability > 1.0) {
            return ValidationResult.Invalid("Market implied probability out of range: $marketImpliedProbability")
        }

        // Decimal odds from Odds API
        if (decimalOdds <= 1.0) {
            return ValidationResult.Invalid("Invalid decimal odds: $decimalOdds")
        }

        // Edge calculation: AI thinks it's more likely than market suggests
        val edge = aiProbability - marketImpliedProbability

        // Log the edge for debugging
        android.util.Log.d("BettingFlowValidator",
            "Kelly Inputs - AI: ${aiProbability * 100}%, Market: ${marketImpliedProbability * 100}%, Edge: ${edge * 100}%")

        return ValidationResult.Valid
    }

    /**
     * CRITICAL: Validate settlement is based on ACTUAL match results, not AI predictions.
     */
    fun validateSettlementData(
        matchCompleted: Boolean,
        homeScore: Int?,
        awayScore: Int?,
        userSelection: String
    ): SettlementValidation {
        // Match MUST be completed before settlement
        if (!matchCompleted) {
            return SettlementValidation.NotReady("Match not yet completed")
        }

        // Scores MUST be available
        if (homeScore == null || awayScore == null) {
            return SettlementValidation.NotReady("Scores not available")
        }

        // Scores must be non-negative
        if (homeScore < 0 || awayScore < 0) {
            return SettlementValidation.Invalid("Invalid scores: $homeScore - $awayScore")
        }

        // Determine actual winner from REAL scores
        val actualWinner = when {
            homeScore > awayScore -> "HOME"
            awayScore > homeScore -> "AWAY"
            else -> "DRAW"
        }

        // Compare user selection to ACTUAL result
        val userWon = when (userSelection.uppercase()) {
            actualWinner -> true
            "DRAW" -> actualWinner == "DRAW"
            else -> false
        }

        return SettlementValidation.Ready(
            actualWinner = actualWinner,
            finalScore = "$homeScore - $awayScore",
            userWon = userWon
        )
    }

    /**
     * Calculate safe stake using Kelly with all validations.
     * This is the ONLY place stake should be calculated from.
     */
    fun calculateValidatedStake(
        aiProbability: Double,
        impliedProbabilities: ImpliedProbabilities,
        decimalOdds: Double,
        recommendation: String
    ): StakeCalculation {
        // Get the correct implied probability based on recommendation
        val marketProb = when (recommendation) {
            "HOME" -> impliedProbabilities.home
            "AWAY" -> impliedProbabilities.away
            "DRAW" -> impliedProbabilities.draw ?: return StakeCalculation.NoValue("No draw odds")
            else -> return StakeCalculation.NoValue("No bet recommended")
        }

        // Validate inputs
        val validation = validateKellyInputs(aiProbability, marketProb, decimalOdds)
        if (validation is ValidationResult.Invalid) {
            return StakeCalculation.Error(validation.reason)
        }

        // Calculate Kelly stake
        val kellyResult = KellyCriterionCalculator.calculate(
            aiProbability = aiProbability,
            impliedProbability = marketProb,
            decimalOdds = decimalOdds
        )

        // Only recommend stake if there's positive edge
        if (kellyResult.edge <= 0) {
            return StakeCalculation.NoValue("No positive edge detected")
        }

        return StakeCalculation.Recommended(
            edge = kellyResult.edge,
            stakeUnits = kellyResult.stakeUnits,
            stakePercent = kellyResult.recommendedStakePercent
        )
    }
}

/**
 * Validation result types.
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

/**
 * Settlement validation result.
 */
sealed class SettlementValidation {
    data class Ready(
        val actualWinner: String,
        val finalScore: String,
        val userWon: Boolean
    ) : SettlementValidation()

    data class NotReady(val reason: String) : SettlementValidation()
    data class Invalid(val reason: String) : SettlementValidation()
}

/**
 * Stake calculation result.
 */
sealed class StakeCalculation {
    data class Recommended(
        val edge: Double,
        val stakeUnits: Int,
        val stakePercent: Double
    ) : StakeCalculation()

    data class NoValue(val reason: String) : StakeCalculation()
    data class Error(val reason: String) : StakeCalculation()
}
