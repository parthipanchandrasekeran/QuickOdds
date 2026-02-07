package com.quickodds.app.util

import org.junit.Assert.*
import org.junit.Test

class BettingFlowValidatorTest {

    // ═══════════════════════════════════════════════════════════════
    // validateMarketPrices tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `validateMarketPrices accepts valid odds`() {
        val result = BettingFlowValidator.validateMarketPrices(
            homeOdds = 2.10,
            awayOdds = 3.50,
            drawOdds = 3.25
        )
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateMarketPrices accepts valid odds without draw`() {
        val result = BettingFlowValidator.validateMarketPrices(
            homeOdds = 1.85,
            awayOdds = 2.05,
            drawOdds = null
        )
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateMarketPrices rejects home odds below 1`() {
        val result = BettingFlowValidator.validateMarketPrices(
            homeOdds = 0.95,
            awayOdds = 3.50,
            drawOdds = 3.25
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateMarketPrices rejects home odds of exactly 1`() {
        val result = BettingFlowValidator.validateMarketPrices(
            homeOdds = 1.0,
            awayOdds = 3.50,
            drawOdds = 3.25
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateMarketPrices rejects home odds above 100`() {
        val result = BettingFlowValidator.validateMarketPrices(
            homeOdds = 101.0,
            awayOdds = 1.01,
            drawOdds = null
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateMarketPrices rejects away odds below 1`() {
        val result = BettingFlowValidator.validateMarketPrices(
            homeOdds = 2.10,
            awayOdds = 0.50,
            drawOdds = null
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateMarketPrices rejects draw odds below 1`() {
        val result = BettingFlowValidator.validateMarketPrices(
            homeOdds = 2.10,
            awayOdds = 3.50,
            drawOdds = 0.80
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateMarketPrices rejects implied probabilities below 100 percent`() {
        // These odds imply less than 100% probability total (no bookmaker margin)
        // 1/50 + 1/50 = 0.04 < 1.0
        val result = BettingFlowValidator.validateMarketPrices(
            homeOdds = 50.0,
            awayOdds = 50.0,
            drawOdds = null
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    // ═══════════════════════════════════════════════════════════════
    // validateSettlementData tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `settlement returns NotReady when match not completed`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = false,
            homeScore = 2,
            awayScore = 1,
            userSelection = "HOME"
        )
        assertTrue(result is SettlementValidation.NotReady)
    }

    @Test
    fun `settlement returns NotReady when home score null`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = null,
            awayScore = 1,
            userSelection = "HOME"
        )
        assertTrue(result is SettlementValidation.NotReady)
    }

    @Test
    fun `settlement returns NotReady when away score null`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = 2,
            awayScore = null,
            userSelection = "HOME"
        )
        assertTrue(result is SettlementValidation.NotReady)
    }

    @Test
    fun `settlement returns Invalid for negative scores`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = -1,
            awayScore = 2,
            userSelection = "HOME"
        )
        assertTrue(result is SettlementValidation.Invalid)
    }

    @Test
    fun `settlement returns Ready with correct winner when home wins`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = 3,
            awayScore = 1,
            userSelection = "HOME"
        )
        assertTrue(result is SettlementValidation.Ready)
        val ready = result as SettlementValidation.Ready
        assertEquals("HOME", ready.actualWinner)
        assertEquals("3 - 1", ready.finalScore)
        assertTrue(ready.userWon)
    }

    @Test
    fun `settlement returns Ready with correct winner when away wins`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = 0,
            awayScore = 2,
            userSelection = "AWAY"
        )
        assertTrue(result is SettlementValidation.Ready)
        val ready = result as SettlementValidation.Ready
        assertEquals("AWAY", ready.actualWinner)
        assertTrue(ready.userWon)
    }

    @Test
    fun `settlement returns Ready with draw when scores equal`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = 1,
            awayScore = 1,
            userSelection = "DRAW"
        )
        assertTrue(result is SettlementValidation.Ready)
        val ready = result as SettlementValidation.Ready
        assertEquals("DRAW", ready.actualWinner)
        assertTrue(ready.userWon)
    }

    @Test
    fun `settlement user loses when selection wrong`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = 3,
            awayScore = 1,
            userSelection = "AWAY"
        )
        assertTrue(result is SettlementValidation.Ready)
        val ready = result as SettlementValidation.Ready
        assertEquals("HOME", ready.actualWinner)
        assertFalse(ready.userWon)
    }

    @Test
    fun `settlement handles case insensitive selection`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = 1,
            awayScore = 1,
            userSelection = "draw"
        )
        assertTrue(result is SettlementValidation.Ready)
        val ready = result as SettlementValidation.Ready
        assertTrue(ready.userWon)
    }

    @Test
    fun `settlement 0-0 draw is valid`() {
        val result = BettingFlowValidator.validateSettlementData(
            matchCompleted = true,
            homeScore = 0,
            awayScore = 0,
            userSelection = "HOME"
        )
        assertTrue(result is SettlementValidation.Ready)
        val ready = result as SettlementValidation.Ready
        assertEquals("DRAW", ready.actualWinner)
        assertEquals("0 - 0", ready.finalScore)
        assertFalse(ready.userWon)
    }

    // ═══════════════════════════════════════════════════════════════
    // validateAIAnalysis tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `validateAIAnalysis accepts valid analysis`() {
        val analysis = createTestAnalysis(
            recommendation = "HOME",
            confidenceScore = 0.75,
            projectedProbability = 0.60
        )
        val result = BettingFlowValidator.validateAIAnalysis(analysis)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateAIAnalysis rejects null projected probability`() {
        val analysis = createTestAnalysis(
            recommendation = "HOME",
            confidenceScore = 0.75,
            projectedProbability = null
        )
        val result = BettingFlowValidator.validateAIAnalysis(analysis)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateAIAnalysis rejects probability above 1`() {
        val analysis = createTestAnalysis(
            recommendation = "HOME",
            confidenceScore = 0.75,
            projectedProbability = 1.5
        )
        val result = BettingFlowValidator.validateAIAnalysis(analysis)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateAIAnalysis rejects negative probability`() {
        val analysis = createTestAnalysis(
            recommendation = "HOME",
            confidenceScore = 0.75,
            projectedProbability = -0.1
        )
        val result = BettingFlowValidator.validateAIAnalysis(analysis)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateAIAnalysis rejects invalid recommendation`() {
        val analysis = createTestAnalysis(
            recommendation = "INVALID",
            confidenceScore = 0.75,
            projectedProbability = 0.60
        )
        val result = BettingFlowValidator.validateAIAnalysis(analysis)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateAIAnalysis accepts NO_BET recommendation`() {
        val analysis = createTestAnalysis(
            recommendation = "NO_BET",
            confidenceScore = 0.30,
            projectedProbability = 0.50
        )
        val result = BettingFlowValidator.validateAIAnalysis(analysis)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateAIAnalysis rejects confidence above 1`() {
        val analysis = createTestAnalysis(
            recommendation = "HOME",
            confidenceScore = 1.5,
            projectedProbability = 0.60
        )
        val result = BettingFlowValidator.validateAIAnalysis(analysis)
        assertTrue(result is ValidationResult.Invalid)
    }

    // ═══════════════════════════════════════════════════════════════
    // validateKellyInputs tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `validateKellyInputs accepts valid inputs`() {
        val result = BettingFlowValidator.validateKellyInputs(
            aiProbability = 0.60,
            marketImpliedProbability = 0.50,
            decimalOdds = 2.0
        )
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateKellyInputs rejects ai probability above 1`() {
        val result = BettingFlowValidator.validateKellyInputs(
            aiProbability = 1.1,
            marketImpliedProbability = 0.50,
            decimalOdds = 2.0
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateKellyInputs rejects market probability below 0`() {
        val result = BettingFlowValidator.validateKellyInputs(
            aiProbability = 0.60,
            marketImpliedProbability = -0.1,
            decimalOdds = 2.0
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateKellyInputs rejects decimal odds of 1 or less`() {
        val result = BettingFlowValidator.validateKellyInputs(
            aiProbability = 0.60,
            marketImpliedProbability = 0.50,
            decimalOdds = 1.0
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════

    private fun createTestAnalysis(
        recommendation: String,
        confidenceScore: Double,
        projectedProbability: Double?
    ) = AIAnalysisResponse(
        recommendation = recommendation,
        confidenceScore = confidenceScore,
        isValueBet = false,
        rationale = "Test rationale",
        projectedProbability = projectedProbability
    )
}
