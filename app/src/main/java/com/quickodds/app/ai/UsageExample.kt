package com.quickodds.app.ai

import com.quickodds.app.ai.model.*
import kotlinx.coroutines.runBlocking

/**
 * Usage examples for AIAnalysisService.
 *
 * These examples demonstrate how to use the AI analysis service
 * to get betting recommendations.
 */
object UsageExample {

    /**
     * Example: Analyze a Premier League match.
     */
    fun exampleAnalysis() = runBlocking {
        // Initialize the service (API key is handled by Firebase Function)
        val analysisService = AIAnalysisService()

        // Create match data with odds
        val matchData = MatchData(
            eventId = "match_001",
            homeTeam = "Manchester United",
            awayTeam = "Liverpool",
            homeOdds = 2.45,        // Decimal odds
            drawOdds = 3.40,
            awayOdds = 2.90,
            league = "Premier League",
            commenceTime = "2024-01-15T15:00:00Z"
        )

        // Create recent statistics
        val recentStats = RecentStats(
            homeTeamForm = "WWLDW",              // Last 5: Win, Win, Loss, Draw, Win
            awayTeamForm = "WDWWL",              // Last 5: Win, Draw, Win, Win, Loss
            homeGoalsScored = 12,                // Goals in last 5 games
            homeGoalsConceded = 6,
            awayGoalsScored = 14,
            awayGoalsConceded = 8,
            headToHead = "Man Utd: 2W, Liverpool: 3W, Draws: 1 (last 6 meetings)",
            injuries = listOf(
                "Marcus Rashford (doubtful)",
                "Liverpool: No major injuries"
            ),
            additionalNotes = "Manchester United playing at Old Trafford"
        )

        // Get AI analysis
        when (val result = analysisService.analyzeMatch(matchData, recentStats)) {
            is AnalysisResult.Success -> {
                val analysis = result.result

                println("=== AI ANALYSIS RESULT ===")
                println("Match: ${analysis.matchData.matchName}")
                println()
                println("RECOMMENDATION: ${analysis.aiAnalysis.recommendation}")
                println("Confidence: ${(analysis.aiAnalysis.confidenceScore * 100).toInt()}%")
                println("Is Value Bet: ${analysis.aiAnalysis.isValueBet}")
                println("Rationale: ${analysis.aiAnalysis.rationale}")
                println()
                println("Implied Probabilities:")
                println("  Home: ${String.format("%.1f", analysis.impliedProbabilities.home * 100)}%")
                println("  Draw: ${analysis.impliedProbabilities.draw?.let { String.format("%.1f", it * 100) } ?: "N/A"}%")
                println("  Away: ${String.format("%.1f", analysis.impliedProbabilities.away * 100)}%")
                println()
                analysis.aiAnalysis.projectedProbability?.let {
                    println("Projected Probability: ${String.format("%.1f", it * 100)}%")
                }
                analysis.aiAnalysis.edgePercentage?.let {
                    println("Edge: ${String.format("%.1f", it)}%")
                }
                analysis.aiAnalysis.suggestedStake?.let {
                    println("Suggested Stake: $it units")
                }
            }

            is AnalysisResult.Error -> {
                println("Analysis failed: ${result.message}")
                result.exception?.printStackTrace()
            }

            is AnalysisResult.Loading -> {
                println("Loading...")
            }
        }
    }

    /**
     * Example: Using the repository for simpler access.
     */
    fun exampleWithRepository() = runBlocking {
        // API key is handled by Firebase Function
        val service = AIAnalysisService()
        val repository = AIAnalysisRepository(service)

        // Simple analysis with just form data
        val result = repository.analyze(
            homeTeam = "Arsenal",
            awayTeam = "Chelsea",
            homeOdds = 1.95,
            drawOdds = 3.60,
            awayOdds = 3.80,
            league = "Premier League",
            stats = RecentStats(
                homeTeamForm = "WWWDW",
                awayTeamForm = "LDWLW",
                headToHead = "Arsenal 3W, Chelsea 1W, 2D"
            )
        )

        when (result) {
            is AnalysisResult.Success -> {
                val ai = result.result.aiAnalysis

                if (ai.isValueBet) {
                    println("VALUE BET FOUND!")
                    println("Bet on: ${ai.recommendation}")
                    println("Confidence: ${(ai.confidenceScore * 100).toInt()}%")
                    println("Stake: ${ai.suggestedStake} units")
                } else {
                    println("No value bet identified")
                    println("Reason: ${ai.rationale}")
                }
            }
            is AnalysisResult.Error -> println("Error: ${result.message}")
            is AnalysisResult.Loading -> println("Loading...")
        }
    }

    /**
     * Example JSON response from Claude (for reference):
     *
     * {
     *     "recommendation": "HOME",
     *     "confidence_score": 0.72,
     *     "is_value_bet": true,
     *     "rationale": "Arsenal's strong home form (WWWDW) and superior head-to-head record suggest a 52% true probability vs 47% implied, providing a 5% edge.",
     *     "projected_probability": 0.52,
     *     "edge_percentage": 5.1,
     *     "suggested_stake": 2
     * }
     */
}
