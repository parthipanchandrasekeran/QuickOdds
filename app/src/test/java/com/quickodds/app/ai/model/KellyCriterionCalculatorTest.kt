package com.quickodds.app.ai.model

import org.junit.Assert.*
import org.junit.Test

class KellyCriterionCalculatorTest {

    @Test
    fun `calculate returns zero stake when no edge exists`() {
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.50,
            impliedProbability = 0.50,
            decimalOdds = 2.0
        )

        assertEquals(0.0, result.edge, 0.001)
        assertEquals(0, result.stakeUnits)
    }

    @Test
    fun `calculate returns zero stake when negative edge`() {
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.40,
            impliedProbability = 0.50,
            decimalOdds = 2.0
        )

        assertTrue(result.edge < 0)
        assertEquals(0, result.stakeUnits)
    }

    @Test
    fun `calculate returns positive stake when positive edge`() {
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.60,
            impliedProbability = 0.50,
            decimalOdds = 2.0
        )

        assertTrue(result.edge > 0)
        assertTrue(result.stakeUnits > 0)
        assertTrue(result.halfKellyFraction > 0)
    }

    @Test
    fun `calculate uses half kelly multiplier`() {
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.60,
            impliedProbability = 0.50,
            decimalOdds = 2.0
        )

        assertEquals(result.kellyFraction * 0.5, result.halfKellyFraction, 0.001)
    }

    @Test
    fun `calculate caps stake at 10 percent`() {
        // Very high edge scenario
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.90,
            impliedProbability = 0.10,
            decimalOdds = 10.0
        )

        // recommendedStakePercent is in percentage (multiplied by 100)
        assertTrue(result.recommendedStakePercent <= 10.0)
    }

    @Test
    fun `calculate returns correct unit scale`() {
        // 1 unit: 0-2%
        val lowEdge = KellyCriterionCalculator.calculate(
            aiProbability = 0.55,
            impliedProbability = 0.50,
            decimalOdds = 2.0
        )
        assertTrue(lowEdge.stakeUnits in 0..2)

        // Higher edge should give more units
        val highEdge = KellyCriterionCalculator.calculate(
            aiProbability = 0.75,
            impliedProbability = 0.50,
            decimalOdds = 2.0
        )
        assertTrue(highEdge.stakeUnits >= lowEdge.stakeUnits)
    }

    @Test
    fun `calculate handles odds of 1 correctly`() {
        // b = decimalOdds - 1 = 0, should handle gracefully
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.60,
            impliedProbability = 0.50,
            decimalOdds = 1.0
        )

        assertEquals(0.0, result.kellyFraction, 0.001)
        assertEquals(0, result.stakeUnits)
    }

    @Test
    fun `edge calculation is correct`() {
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.60,
            impliedProbability = 0.45,
            decimalOdds = 2.2
        )

        assertEquals(0.15, result.edge, 0.001)
    }

    @Test
    fun `hasPositiveEdge returns true when ai probability higher`() {
        assertTrue(KellyCriterionCalculator.hasPositiveEdge(0.55, 0.50))
    }

    @Test
    fun `hasPositiveEdge returns false when ai probability lower`() {
        assertFalse(KellyCriterionCalculator.hasPositiveEdge(0.45, 0.50))
    }

    @Test
    fun `hasPositiveEdge returns false when equal`() {
        assertFalse(KellyCriterionCalculator.hasPositiveEdge(0.50, 0.50))
    }

    @Test
    fun `calculateEdge returns edge as percentage`() {
        val edge = KellyCriterionCalculator.calculateEdge(0.60, 0.50)
        assertEquals(10.0, edge, 0.001)
    }

    @Test
    fun `calculateEdge returns negative percentage when no edge`() {
        val edge = KellyCriterionCalculator.calculateEdge(0.40, 0.50)
        assertEquals(-10.0, edge, 0.001)
    }

    @Test
    fun `kelly fraction formula is correct`() {
        // f* = (bp - q) / b
        // b = 2.0 - 1.0 = 1.0
        // p = 0.60, q = 0.40
        // f* = (1.0 * 0.60 - 0.40) / 1.0 = 0.20
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.60,
            impliedProbability = 0.50,
            decimalOdds = 2.0
        )

        assertEquals(0.20, result.kellyFraction, 0.001)
        assertEquals(0.10, result.halfKellyFraction, 0.001)
    }

    @Test
    fun `stakeUnits max is 5`() {
        val result = KellyCriterionCalculator.calculate(
            aiProbability = 0.95,
            impliedProbability = 0.10,
            decimalOdds = 10.0
        )

        assertTrue(result.stakeUnits <= 5)
    }
}
