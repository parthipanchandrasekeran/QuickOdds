package com.quickodds.app.domain

import com.quickodds.app.domain.model.*
import kotlin.math.roundToInt

class BetAnalyzer {

    fun analyze(market: Market): BetAnalysis {
        val implied = calculateImpliedProbabilities(market.odds)
        val projected = projectProbabilities(market)
        val edges = calculateEdges(implied, projected)

        return findValueBet(market, implied, projected, edges)
    }

    private fun calculateImpliedProbabilities(odds: Odds): Probabilities {
        val homeProb = 1 / odds.home
        val drawProb = if (odds.draw > 0) 1 / odds.draw else 0.0
        val awayProb = 1 / odds.away

        // Remove bookmaker margin (normalize to 100%)
        val totalMargin = homeProb + drawProb + awayProb

        return Probabilities(
            home = homeProb / totalMargin,
            draw = drawProb / totalMargin,
            away = awayProb / totalMargin
        )
    }

    private fun projectProbabilities(market: Market): Probabilities {
        val stats = market.stats

        // Base probabilities (slightly favor home team)
        var homeScore = 0.40
        var drawScore = 0.25
        var awayScore = 0.35

        if (stats != null) {
            // Analyze home team form
            val homeWins = stats.homeForm.count { it == 'W' }
            val homeLosses = stats.homeForm.count { it == 'L' }
            val homeFormDiff = (homeWins - homeLosses) * 0.03
            homeScore += homeFormDiff

            // Analyze away team form
            val awayWins = stats.awayForm.count { it == 'W' }
            val awayLosses = stats.awayForm.count { it == 'L' }
            val awayFormDiff = (awayWins - awayLosses) * 0.03
            awayScore += awayFormDiff

            // Goal difference impact
            val homeGD = stats.homeGoalsScored - stats.homeGoalsConceded
            val awayGD = stats.awayGoalsScored - stats.awayGoalsConceded
            homeScore += (homeGD * 0.005).coerceIn(-0.1, 0.1)
            awayScore += (awayGD * 0.005).coerceIn(-0.1, 0.1)

            // Injury impact
            for (injury in stats.injuries) {
                val injuryLower = injury.lowercase()
                when {
                    injuryLower.contains(market.homeTeam.lowercase()) ||
                    injuryLower.contains("home") -> homeScore -= 0.08

                    injuryLower.contains(market.awayTeam.lowercase()) ||
                    injuryLower.contains("away") -> awayScore -= 0.08
                }
            }

            // H2H slight adjustment
            val h2h = stats.headToHead.lowercase()
            if (h2h.contains("${market.homeTeam.take(4).lowercase()}") &&
                h2h.contains("w")) {
                homeScore += 0.02
            }
        }

        // Normalize
        val total = homeScore + drawScore + awayScore
        return Probabilities(
            home = (homeScore / total).coerceIn(0.10, 0.85),
            draw = (drawScore / total).coerceIn(0.05, 0.40),
            away = (awayScore / total).coerceIn(0.10, 0.85)
        )
    }

    private fun calculateEdges(
        implied: Probabilities,
        projected: Probabilities
    ): Probabilities {
        return Probabilities(
            home = projected.home - implied.home,
            draw = projected.draw - implied.draw,
            away = projected.away - implied.away
        )
    }

    private fun findValueBet(
        market: Market,
        implied: Probabilities,
        projected: Probabilities,
        edges: Probabilities
    ): BetAnalysis {
        val maxEdge = maxOf(edges.home, edges.draw, edges.away)
        val isValueBet = maxEdge > 0.05 // 5% edge threshold

        val (recommendation, confidence, rationale) = when {
            edges.home == maxEdge && isValueBet -> Triple(
                market.homeTeam,
                (0.5 + edges.home).coerceIn(0.0, 1.0),
                "Statistical edge of ${(edges.home * 100).roundToInt()}% on ${market.homeTeam}"
            )
            edges.away == maxEdge && isValueBet -> Triple(
                market.awayTeam,
                (0.5 + edges.away).coerceIn(0.0, 1.0),
                "Statistical edge of ${(edges.away * 100).roundToInt()}% on ${market.awayTeam}"
            )
            edges.draw == maxEdge && isValueBet -> Triple(
                "Draw",
                (0.5 + edges.draw).coerceIn(0.0, 1.0),
                "Statistical edge of ${(edges.draw * 100).roundToInt()}% on Draw"
            )
            else -> Triple(
                "No Value",
                0.3,
                "No significant edge found - odds are fairly priced"
            )
        }

        val suggestedStake = when {
            !isValueBet -> 0
            confidence >= 0.75 -> 5
            confidence >= 0.65 -> 4
            confidence >= 0.60 -> 3
            confidence >= 0.55 -> 2
            else -> 1
        }

        return BetAnalysis(
            marketId = market.id,
            recommendation = recommendation,
            confidenceScore = confidence,
            rationale = rationale,
            suggestedStake = suggestedStake,
            isValueBet = isValueBet,
            impliedProbabilities = implied,
            projectedProbabilities = projected,
            edges = edges
        )
    }
}
