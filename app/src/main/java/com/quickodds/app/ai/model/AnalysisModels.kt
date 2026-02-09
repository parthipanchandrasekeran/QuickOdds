package com.quickodds.app.ai.model

import com.google.gson.annotations.SerializedName

/**
 * Input data for AI analysis.
 */
data class MatchData(
    val eventId: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeOdds: Double,               // Best available home odds across all bookmakers
    val drawOdds: Double?,              // Null for sports without draws (NBA, NFL)
    val awayOdds: Double,               // Best available away odds
    val league: String,
    val commenceTime: String,
    // Multi-bookmaker aggregation
    val bestHomeBookmaker: String? = null,   // Which bookmaker has best home odds
    val bestDrawBookmaker: String? = null,
    val bestAwayBookmaker: String? = null,
    val avgHomeOdds: Double? = null,         // Market consensus (average across books)
    val avgDrawOdds: Double? = null,
    val avgAwayOdds: Double? = null,
    val minHomeOdds: Double? = null,         // Odds spread (min/max range)
    val maxHomeOdds: Double? = null,
    val minAwayOdds: Double? = null,
    val maxAwayOdds: Double? = null,
    val bookmakerCount: Int = 1              // How many bookmakers provided odds
) {
    val matchName: String
        get() = "$homeTeam vs $awayTeam"

    /**
     * Calculate implied probability from best available decimal odds.
     * Formula: P = 1 / Decimal Odds
     */
    fun getImpliedProbabilities(): ImpliedProbabilities {
        val totalMargin = (1 / homeOdds) +
                          (drawOdds?.let { 1 / it } ?: 0.0) +
                          (1 / awayOdds)

        return ImpliedProbabilities(
            home = (1 / homeOdds) / totalMargin,
            draw = drawOdds?.let { (1 / it) / totalMargin },
            away = (1 / awayOdds) / totalMargin,
            bookmakerMargin = (totalMargin - 1) * 100  // Margin as percentage
        )
    }

    /**
     * Calculate market consensus implied probabilities from average odds.
     * This removes individual bookmaker bias.
     */
    fun getConsensusImpliedProbabilities(): ImpliedProbabilities? {
        val avgHome = avgHomeOdds ?: return null
        val avgAway = avgAwayOdds ?: return null

        val totalMargin = (1 / avgHome) +
                          (avgDrawOdds?.let { 1 / it } ?: 0.0) +
                          (1 / avgAway)

        return ImpliedProbabilities(
            home = (1 / avgHome) / totalMargin,
            draw = avgDrawOdds?.let { (1 / it) / totalMargin },
            away = (1 / avgAway) / totalMargin,
            bookmakerMargin = (totalMargin - 1) * 100
        )
    }

    /**
     * Calculate odds spread (max - min) as a measure of bookmaker disagreement.
     * Wider spread = more disagreement = potential value opportunity.
     */
    fun getOddsSpread(): Triple<Double, Double, Double>? {
        val homeSpread = if (minHomeOdds != null && maxHomeOdds != null) maxHomeOdds - minHomeOdds else return null
        val awaySpread = if (minAwayOdds != null && maxAwayOdds != null) maxAwayOdds - minAwayOdds else return null
        val drawSpread = 0.0 // Optional, not critical
        return Triple(homeSpread, drawSpread, awaySpread)
    }
}

/**
 * Implied probabilities calculated from odds.
 */
data class ImpliedProbabilities(
    val home: Double,
    val draw: Double?,
    val away: Double,
    val bookmakerMargin: Double
)

/**
 * Team trend data for momentum analysis.
 * Compares recent performance (L3) against longer-term performance (L10).
 */
data class TeamTrendData(
    val teamName: String,
    val l3Record: GameRecord? = null,          // Last 3 games
    val l10Record: GameRecord? = null,         // Last 10 games
    val seasonRecord: GameRecord? = null,      // Season totals
    val isBackToBack: Boolean = false,         // Playing on consecutive days
    val isThirdInFourNights: Boolean = false,  // 3rd game in 4 nights
    val lastGameDate: String? = null           // For fatigue calculation
) {
    /**
     * Calculate momentum score: L3 win% - L10 win%
     * Positive = hot streak, Negative = cold streak
     */
    fun calculateMomentumScore(): Double? {
        val l3WinPct = l3Record?.winPercentage ?: return null
        val l10WinPct = l10Record?.winPercentage ?: return null
        return l3WinPct - l10WinPct
    }

    /**
     * Calculate momentum divergence from season average.
     * Positive = playing better than season average
     */
    fun calculateSeasonDivergence(): Double? {
        val l3WinPct = l3Record?.winPercentage ?: return null
        val seasonWinPct = seasonRecord?.winPercentage ?: return null
        return l3WinPct - seasonWinPct
    }

    /**
     * Check if team has fatigue factor.
     */
    val hasFatigue: Boolean
        get() = isBackToBack || isThirdInFourNights
}

/**
 * Game record for a period (L3, L10, Season).
 */
data class GameRecord(
    val wins: Int,
    val losses: Int,
    val draws: Int = 0,
    val pointsScored: Int? = null,
    val pointsAllowed: Int? = null
) {
    val totalGames: Int
        get() = wins + losses + draws

    val winPercentage: Double
        get() = if (totalGames > 0) wins.toDouble() / totalGames else 0.0

    val pointDifferential: Double?
        get() = if (pointsScored != null && pointsAllowed != null && totalGames > 0) {
            (pointsScored - pointsAllowed).toDouble() / totalGames
        } else null
}

// ═══════════════════════════════════════════════════════════════════════════════
// LINE MOVEMENT INTELLIGENCE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Line Movement data for sharp money detection.
 *
 * Key Concepts:
 * - Public Percentage: % of total bets on each side
 * - Money Percentage: % of total dollars wagered on each side
 * - Reverse Line Movement (RLM): When line moves opposite to public betting
 * - Sharp Alert: When public >75% on one side but line moves the other way
 *
 * Sharp money indicator: Money % significantly higher than Bet %
 * (e.g., 30% of bets but 60% of money = sharp action)
 */
data class LineMovementData(
    // Opening vs Current Line
    val openingHomeOdds: Double,
    val openingAwayOdds: Double,
    val openingDrawOdds: Double? = null,
    val currentHomeOdds: Double,
    val currentAwayOdds: Double,
    val currentDrawOdds: Double? = null,

    // Public Betting Percentages (% of total bets)
    val publicBetPercentHome: Double,      // e.g., 0.75 = 75% of bets on Home
    val publicBetPercentAway: Double,      // e.g., 0.25 = 25% of bets on Away
    val publicBetPercentDraw: Double? = null,

    // Money Percentages (% of total dollars wagered)
    val moneyPercentHome: Double,          // e.g., 0.40 = 40% of money on Home
    val moneyPercentAway: Double,          // e.g., 0.60 = 60% of money on Away
    val moneyPercentDraw: Double? = null,

    // Timestamps for line movement tracking
    val openingTimestamp: Long? = null,
    val lastUpdateTimestamp: Long? = null
) {
    companion object {
        const val SHARP_ALERT_PUBLIC_THRESHOLD = 0.75  // 75% public betting triggers alert check
        const val SHARP_MONEY_DIVERGENCE_THRESHOLD = 0.15  // 15% gap between bet% and money%
    }

    /**
     * Calculate line movement direction.
     * Positive = line moved toward Home, Negative = line moved toward Away
     */
    val homeLineMovement: Double
        get() = openingHomeOdds - currentHomeOdds

    val awayLineMovement: Double
        get() = openingAwayOdds - currentAwayOdds

    /**
     * Detect Reverse Line Movement (RLM).
     * RLM occurs when line moves OPPOSITE to where the public is betting heavily.
     */
    fun detectReverseLineMovement(): ReverseLineMovementResult {
        val publicFavoriteSide = when {
            publicBetPercentHome >= SHARP_ALERT_PUBLIC_THRESHOLD -> "HOME"
            publicBetPercentAway >= SHARP_ALERT_PUBLIC_THRESHOLD -> "AWAY"
            else -> null
        }

        if (publicFavoriteSide == null) {
            return ReverseLineMovementResult(
                detected = false,
                publicSide = null,
                lineMovedToward = null,
                isSharpAlert = false,
                description = "No heavy public action (both sides < 75%)"
            )
        }

        // Determine which way the line moved
        // If Home odds decreased, line moved TOWARD Home (Home became more favored)
        // If Home odds increased, line moved TOWARD Away (Away became more favored)
        val lineMovedToward = when {
            homeLineMovement > 0.05 -> "HOME"  // Odds dropped = more favored
            awayLineMovement > 0.05 -> "AWAY"
            else -> "NEUTRAL"
        }

        val isRLM = publicFavoriteSide != lineMovedToward && lineMovedToward != "NEUTRAL"

        return ReverseLineMovementResult(
            detected = isRLM,
            publicSide = publicFavoriteSide,
            lineMovedToward = lineMovedToward,
            isSharpAlert = isRLM,
            description = if (isRLM) {
                "⚠️ SHARP ALERT: Public is ${"%.0f".format(
                    if (publicFavoriteSide == "HOME") publicBetPercentHome * 100
                    else publicBetPercentAway * 100
                )}% on $publicFavoriteSide but line moved toward $lineMovedToward"
            } else {
                "Line moving with public sentiment"
            }
        )
    }

    /**
     * Detect Sharp Money based on bet% vs money% divergence.
     * Sharp bettors place larger bets, so if money% >> bet%, sharp action detected.
     */
    fun detectSharpMoney(): SharpMoneyResult {
        val homeDivergence = moneyPercentHome - publicBetPercentHome
        val awayDivergence = moneyPercentAway - publicBetPercentAway

        val sharpSide = when {
            homeDivergence >= SHARP_MONEY_DIVERGENCE_THRESHOLD -> "HOME"
            awayDivergence >= SHARP_MONEY_DIVERGENCE_THRESHOLD -> "AWAY"
            else -> null
        }

        val divergenceAmount = maxOf(homeDivergence, awayDivergence)

        return SharpMoneyResult(
            detected = sharpSide != null,
            sharpSide = sharpSide,
            betPercentOnSharpSide = if (sharpSide == "HOME") publicBetPercentHome else publicBetPercentAway,
            moneyPercentOnSharpSide = if (sharpSide == "HOME") moneyPercentHome else moneyPercentAway,
            divergence = divergenceAmount,
            description = if (sharpSide != null) {
                "💰 SHARP MONEY on $sharpSide: ${"%.0f".format(
                    if (sharpSide == "HOME") publicBetPercentHome * 100 else publicBetPercentAway * 100
                )}% of bets but ${"%.0f".format(
                    if (sharpSide == "HOME") moneyPercentHome * 100 else moneyPercentAway * 100
                )}% of money"
            } else {
                "Bet % and Money % aligned (no sharp divergence)"
            }
        )
    }

    /**
     * Get comprehensive sharp analysis summary.
     */
    fun getSharpAnalysisSummary(): String = buildString {
        val rlm = detectReverseLineMovement()
        val sharp = detectSharpMoney()

        appendLine("LINE MOVEMENT ANALYSIS:")
        appendLine("  Opening: Home ${openingHomeOdds} | Away ${openingAwayOdds}")
        appendLine("  Current: Home ${currentHomeOdds} | Away ${currentAwayOdds}")
        appendLine("  Movement: Home ${if (homeLineMovement > 0) "↓" else "↑"} | Away ${if (awayLineMovement > 0) "↓" else "↑"}")
        appendLine()
        appendLine("PUBLIC vs SHARP:")
        appendLine("  Bet %:   Home ${"%.0f".format(publicBetPercentHome * 100)}% | Away ${"%.0f".format(publicBetPercentAway * 100)}%")
        appendLine("  Money %: Home ${"%.0f".format(moneyPercentHome * 100)}% | Away ${"%.0f".format(moneyPercentAway * 100)}%")
        appendLine()
        if (rlm.isSharpAlert) appendLine(rlm.description)
        if (sharp.detected) appendLine(sharp.description)
    }
}

/**
 * Result of Reverse Line Movement detection.
 */
data class ReverseLineMovementResult(
    val detected: Boolean,
    val publicSide: String?,       // Where public is betting ("HOME", "AWAY")
    val lineMovedToward: String?,  // Where line moved ("HOME", "AWAY", "NEUTRAL")
    val isSharpAlert: Boolean,
    val description: String
)

/**
 * Result of Sharp Money detection.
 */
data class SharpMoneyResult(
    val detected: Boolean,
    val sharpSide: String?,           // Side with sharp action ("HOME", "AWAY")
    val betPercentOnSharpSide: Double?,
    val moneyPercentOnSharpSide: Double?,
    val divergence: Double,           // Gap between money% and bet%
    val description: String
)

/**
 * Recent statistics and form data for analysis.
 */
data class RecentStats(
    val homeTeamForm: String? = null,          // e.g., "WWLDW"
    val awayTeamForm: String? = null,          // e.g., "LWWDL"
    val homeGoalsScored: Int? = null,
    val homeGoalsConceded: Int? = null,
    val awayGoalsScored: Int? = null,
    val awayGoalsConceded: Int? = null,
    val headToHead: String? = null,            // e.g., "Home: 3W, Away: 2W, Draw: 1"
    val injuries: List<String> = emptyList(),
    val additionalNotes: String? = null,
    // Trend Analysis Data
    val homeTrend: TeamTrendData? = null,
    val awayTrend: TeamTrendData? = null,
    // Line Movement Intelligence
    val lineMovement: LineMovementData? = null
) {
    fun toAnalysisString(): String = buildString {
        homeTeamForm?.let { appendLine("Home Team Recent Form: $it") }
        awayTeamForm?.let { appendLine("Away Team Recent Form: $it") }
        homeGoalsScored?.let { appendLine("Home Goals Scored (last 5): $it") }
        homeGoalsConceded?.let { appendLine("Home Goals Conceded (last 5): $it") }
        awayGoalsScored?.let { appendLine("Away Goals Scored (last 5): $it") }
        awayGoalsConceded?.let { appendLine("Away Goals Conceded (last 5): $it") }
        headToHead?.let { appendLine("Head-to-Head Record: $it") }
        if (injuries.isNotEmpty()) {
            appendLine("Injuries/Suspensions: ${injuries.joinToString(", ")}")
        }
        additionalNotes?.let { appendLine("Additional Notes: $it") }

        // Trend Analysis Section
        homeTrend?.let { trend ->
            appendLine()
            appendLine("HOME TEAM TREND ANALYSIS:")
            trend.l3Record?.let { appendLine("  L3 Record: ${it.wins}W-${it.losses}L${if (it.draws > 0) "-${it.draws}D" else ""} (${String.format("%.1f", it.winPercentage * 100)}%)") }
            trend.l10Record?.let { appendLine("  L10 Record: ${it.wins}W-${it.losses}L${if (it.draws > 0) "-${it.draws}D" else ""} (${String.format("%.1f", it.winPercentage * 100)}%)") }
            trend.seasonRecord?.let { appendLine("  Season Record: ${it.wins}W-${it.losses}L${if (it.draws > 0) "-${it.draws}D" else ""} (${String.format("%.1f", it.winPercentage * 100)}%)") }
            trend.calculateMomentumScore()?.let { appendLine("  Momentum Score (L3-L10): ${String.format("%+.1f", it * 100)}%") }
            trend.calculateSeasonDivergence()?.let { appendLine("  Season Divergence: ${String.format("%+.1f", it * 100)}%") }
            if (trend.hasFatigue) {
                appendLine("  ⚠️ FATIGUE ALERT: ${if (trend.isBackToBack) "Back-to-Back" else "3rd game in 4 nights"}")
            }
        }

        awayTrend?.let { trend ->
            appendLine()
            appendLine("AWAY TEAM TREND ANALYSIS:")
            trend.l3Record?.let { appendLine("  L3 Record: ${it.wins}W-${it.losses}L${if (it.draws > 0) "-${it.draws}D" else ""} (${String.format("%.1f", it.winPercentage * 100)}%)") }
            trend.l10Record?.let { appendLine("  L10 Record: ${it.wins}W-${it.losses}L${if (it.draws > 0) "-${it.draws}D" else ""} (${String.format("%.1f", it.winPercentage * 100)}%)") }
            trend.seasonRecord?.let { appendLine("  Season Record: ${it.wins}W-${it.losses}L${if (it.draws > 0) "-${it.draws}D" else ""} (${String.format("%.1f", it.winPercentage * 100)}%)") }
            trend.calculateMomentumScore()?.let { appendLine("  Momentum Score (L3-L10): ${String.format("%+.1f", it * 100)}%") }
            trend.calculateSeasonDivergence()?.let { appendLine("  Season Divergence: ${String.format("%+.1f", it * 100)}%") }
            if (trend.hasFatigue) {
                appendLine("  ⚠️ FATIGUE ALERT: ${if (trend.isBackToBack) "Back-to-Back" else "3rd game in 4 nights"}")
            }
        }

        // Line Movement Intelligence
        lineMovement?.let { lm ->
            appendLine()
            append(lm.getSharpAnalysisSummary())
        }
    }
}

/**
 * AI Analysis response - the output from Claude.
 */
data class AIAnalysisResponse(
    @SerializedName("recommendation")
    val recommendation: String,         // "HOME", "AWAY", "DRAW", or "NO_BET"

    @SerializedName("confidence_score")
    val confidenceScore: Double,        // 0.0 to 1.0

    @SerializedName("is_value_bet")
    val isValueBet: Boolean,

    @SerializedName("rationale")
    val rationale: String,

    @SerializedName("projected_probability")
    val projectedProbability: Double? = null,

    @SerializedName("edge_percentage")
    val edgePercentage: Double? = null,

    @SerializedName("suggested_stake")
    val suggestedStake: Int? = null,    // 1-5 units based on Kelly Criterion

    @SerializedName("kelly_stake")
    val kellyStake: KellyStakeResult? = null,  // Detailed Kelly Criterion calculation

    @SerializedName("ensemble_analysis")
    val ensembleAnalysis: EnsembleAnalysis? = null,  // Multi-agent consensus

    @SerializedName("momentum_divergence")
    val momentumDivergence: MomentumDivergence? = null,

    @SerializedName("fatigue_adjusted")
    val fatigueAdjusted: Boolean? = null,  // True if fatigue penalty was applied

    // Line Movement Intelligence
    @SerializedName("sharp_alert")
    val sharpAlert: SharpAlertAnalysis? = null,

    @SerializedName("line_movement_signal")
    val lineMovementSignal: String? = null  // "SHARP_HOME", "SHARP_AWAY", "PUBLIC_HOME", "PUBLIC_AWAY", "NEUTRAL"
)

/**
 * Multi-Agent Ensemble Analysis.
 *
 * Three internal "experts" analyze the match from different perspectives:
 * 1. Statistical Modeler - Pure numbers, historical data, probability models
 * 2. Pro Scout - Context, injuries, team dynamics, matchup advantages
 * 3. Market Sharp - Line movement, market signals, where smart money is going
 *
 * A value bet is only confirmed if at least 2 of 3 perspectives agree
 * there is a significant edge (>5%).
 */
data class EnsembleAnalysis(
    @SerializedName("statistical_modeler")
    val statisticalModeler: AgentPerspective,

    @SerializedName("pro_scout")
    val proScout: AgentPerspective,

    @SerializedName("market_sharp")
    val marketSharp: AgentPerspective,

    @SerializedName("consensus_count")
    val consensusCount: Int,            // How many agents agree (0-3)

    @SerializedName("consensus_reached")
    val consensusReached: Boolean,      // True if ≥2 agents agree on value

    @SerializedName("dissenting_opinion")
    val dissentingOpinion: String?      // Summary of any disagreement
) {
    /**
     * Get list of agents that found value.
     */
    val agentsWithValue: List<String>
        get() = listOfNotNull(
            if (statisticalModeler.findsValue) "Statistical Modeler" else null,
            if (proScout.findsValue) "Pro Scout" else null,
            if (marketSharp.findsValue) "Market Sharp" else null
        )

    /**
     * Get the average edge across all agents.
     */
    val averageEdge: Double
        get() = listOf(
            statisticalModeler.estimatedEdge,
            proScout.estimatedEdge,
            marketSharp.estimatedEdge
        ).average()

    /**
     * Get the average probability across all agents.
     */
    val averageProbability: Double
        get() = listOf(
            statisticalModeler.projectedProbability,
            proScout.projectedProbability,
            marketSharp.projectedProbability
        ).average()
}

/**
 * Individual agent/expert perspective in the ensemble.
 */
data class AgentPerspective(
    @SerializedName("agent_name")
    val agentName: String,              // "statistical_modeler", "pro_scout", "market_sharp"

    @SerializedName("recommendation")
    val recommendation: String,         // "HOME", "AWAY", "DRAW", "NO_BET"

    @SerializedName("projected_probability")
    val projectedProbability: Double,   // This agent's probability estimate

    @SerializedName("estimated_edge")
    val estimatedEdge: Double,          // This agent's calculated edge %

    @SerializedName("finds_value")
    val findsValue: Boolean,            // True if edge > 5%

    @SerializedName("reasoning")
    val reasoning: String               // Brief explanation of this agent's analysis
)

/**
 * Kelly Criterion stake calculation result.
 *
 * Formula (Half-Kelly): f* = 0.5 × ((b × p - q) / b)
 * where:
 *   b = decimal odds - 1 (profit on a $1 bet)
 *   p = AI's estimated win probability
 *   q = 1 - p (probability of losing)
 */
data class KellyStakeResult(
    @SerializedName("edge")
    val edge: Double,                   // AI Probability - Implied Market Probability

    @SerializedName("kelly_fraction")
    val kellyFraction: Double,          // Raw Kelly fraction (before half-kelly)

    @SerializedName("half_kelly_fraction")
    val halfKellyFraction: Double,      // Half-Kelly (0.5 × kelly_fraction) for safer betting

    @SerializedName("recommended_stake_percent")
    val recommendedStakePercent: Double, // Percentage of bankroll to stake

    @SerializedName("stake_units")
    val stakeUnits: Int                 // Converted to 0-5 unit scale
)

/**
 * Kelly Criterion Calculator for optimal bet sizing.
 *
 * Uses the Half-Kelly approach (0.5 multiplier) for a more conservative
 * strategy that reduces variance while still capturing most of the edge.
 */
object KellyCriterionCalculator {

    private const val HALF_KELLY_MULTIPLIER = 0.5
    private const val MAX_STAKE_PERCENT = 0.10  // Cap at 10% of bankroll
    private const val MIN_EDGE_THRESHOLD = 0.0   // Only bet with positive edge

    /**
     * Calculate the optimal stake using the Kelly Criterion.
     *
     * @param aiProbability AI's estimated win probability (0.0 to 1.0)
     * @param impliedProbability Market implied probability (0.0 to 1.0)
     * @param decimalOdds The decimal odds offered by the bookmaker
     * @return KellyStakeResult with detailed calculation breakdown
     */
    fun calculate(
        aiProbability: Double,
        impliedProbability: Double,
        decimalOdds: Double
    ): KellyStakeResult {
        // Calculate Edge: AI Probability - Implied Market Probability
        val edge = aiProbability - impliedProbability

        // b = decimal odds - 1 (the profit on a $1 bet)
        val b = decimalOdds - 1.0

        // p = probability of winning (AI's estimate)
        val p = aiProbability

        // q = probability of losing = 1 - p
        val q = 1.0 - p

        // Kelly Formula: f* = (bp - q) / b
        // This gives the fraction of bankroll to bet
        val kellyFraction = if (b > 0) {
            (b * p - q) / b
        } else {
            0.0
        }

        // Half-Kelly: Multiply by 0.5 for safer betting
        // This reduces variance while still capturing ~75% of the expected growth
        val halfKellyFraction = HALF_KELLY_MULTIPLIER * kellyFraction

        // Clamp to valid range [0, MAX_STAKE_PERCENT]
        val recommendedStakePercent = halfKellyFraction
            .coerceIn(0.0, MAX_STAKE_PERCENT)

        // Convert percentage to unit scale (0-5 units)
        // 0-2% = 1 unit, 2-4% = 2 units, 4-6% = 3 units, 6-8% = 4 units, 8-10% = 5 units
        val stakeUnits = when {
            edge <= MIN_EDGE_THRESHOLD -> 0  // No positive edge = no bet
            recommendedStakePercent <= 0 -> 0
            recommendedStakePercent <= 0.02 -> 1
            recommendedStakePercent <= 0.04 -> 2
            recommendedStakePercent <= 0.06 -> 3
            recommendedStakePercent <= 0.08 -> 4
            else -> 5
        }

        return KellyStakeResult(
            edge = edge,
            kellyFraction = kellyFraction,
            halfKellyFraction = halfKellyFraction,
            recommendedStakePercent = recommendedStakePercent * 100, // As percentage
            stakeUnits = stakeUnits
        )
    }

    /**
     * Quick check if a bet has positive expected value.
     */
    fun hasPositiveEdge(aiProbability: Double, impliedProbability: Double): Boolean {
        return aiProbability > impliedProbability
    }

    /**
     * Calculate just the edge percentage.
     */
    fun calculateEdge(aiProbability: Double, impliedProbability: Double): Double {
        return (aiProbability - impliedProbability) * 100
    }
}

/**
 * Momentum divergence analysis comparing recent form to longer-term performance.
 */
data class MomentumDivergence(
    @SerializedName("home_momentum")
    val homeMomentum: Double?,          // L3 vs L10 difference (positive = hot streak)

    @SerializedName("away_momentum")
    val awayMomentum: Double?,          // L3 vs L10 difference

    @SerializedName("home_season_divergence")
    val homeSeasonDivergence: Double?,  // L3 vs Season avg (positive = above average)

    @SerializedName("away_season_divergence")
    val awaySeasonDivergence: Double?,

    @SerializedName("momentum_advantage")
    val momentumAdvantage: String?,     // "HOME", "AWAY", or "NEUTRAL"

    @SerializedName("confidence_adjustment")
    val confidenceAdjustment: Double?   // How much confidence was adjusted based on momentum
)

/**
 * Sharp Alert Analysis from Line Movement Intelligence.
 *
 * Detects when professional/sharp money is moving against the public consensus.
 * Key indicators:
 * - Reverse Line Movement (RLM): Line moves opposite to heavy public betting
 * - Sharp Money Divergence: Money % >> Bet % indicates sharp action
 *
 * PRIORITIZATION RULE: When Money % is significantly higher than Bet % on a side,
 * prioritize that side as it indicates sharp/professional action.
 */
data class SharpAlertAnalysis(
    @SerializedName("alert_triggered")
    val alertTriggered: Boolean,                    // True if any sharp signal detected

    @SerializedName("alert_type")
    val alertType: String?,                         // "RLM", "SHARP_MONEY", "BOTH", or null

    @SerializedName("sharp_side")
    val sharpSide: String?,                         // "HOME", "AWAY", or null

    @SerializedName("public_side")
    val publicSide: String?,                        // Where >75% of public bets are

    @SerializedName("public_percentage")
    val publicPercentage: Double?,                  // % of bets on public side

    @SerializedName("money_percentage")
    val moneyPercentage: Double?,                   // % of money on sharp side

    @SerializedName("divergence")
    val divergence: Double?,                        // Gap between money % and bet %

    @SerializedName("line_movement_direction")
    val lineMovementDirection: String?,             // "TOWARD_HOME", "TOWARD_AWAY", "NEUTRAL"

    @SerializedName("confidence_boost")
    val confidenceBoost: Double?,                   // How much to boost confidence on sharp side

    @SerializedName("alert_description")
    val alertDescription: String                    // Human-readable description
) {
    companion object {
        /**
         * Create a SharpAlertAnalysis from LineMovementData.
         */
        fun fromLineMovement(lineMovement: LineMovementData?): SharpAlertAnalysis {
            if (lineMovement == null) {
                return SharpAlertAnalysis(
                    alertTriggered = false,
                    alertType = null,
                    sharpSide = null,
                    publicSide = null,
                    publicPercentage = null,
                    moneyPercentage = null,
                    divergence = null,
                    lineMovementDirection = null,
                    confidenceBoost = null,
                    alertDescription = "No line movement data available"
                )
            }

            val rlmResult = lineMovement.detectReverseLineMovement()
            val sharpResult = lineMovement.detectSharpMoney()

            val alertTriggered = rlmResult.isSharpAlert || sharpResult.detected
            val alertType = when {
                rlmResult.isSharpAlert && sharpResult.detected -> "BOTH"
                rlmResult.isSharpAlert -> "RLM"
                sharpResult.detected -> "SHARP_MONEY"
                else -> null
            }

            // Sharp side is determined by either RLM or sharp money detection
            val sharpSide = when {
                rlmResult.isSharpAlert -> rlmResult.lineMovedToward
                sharpResult.detected -> sharpResult.sharpSide
                else -> null
            }

            // Line movement direction
            val lineDirection = when {
                lineMovement.homeLineMovement > 0.05 -> "TOWARD_HOME"
                lineMovement.awayLineMovement > 0.05 -> "TOWARD_AWAY"
                else -> "NEUTRAL"
            }

            // Calculate confidence boost based on signal strength
            val confidenceBoost = when {
                alertType == "BOTH" -> 0.08  // Strong signal: both RLM and sharp money
                alertType == "RLM" -> 0.05   // RLM is a strong signal
                alertType == "SHARP_MONEY" && (sharpResult.divergence ?: 0.0) > 0.20 -> 0.06
                alertType == "SHARP_MONEY" -> 0.04
                else -> 0.0
            }

            // Build description
            val description = buildString {
                if (rlmResult.isSharpAlert) {
                    append(rlmResult.description)
                }
                if (rlmResult.isSharpAlert && sharpResult.detected) {
                    append(" | ")
                }
                if (sharpResult.detected) {
                    append(sharpResult.description)
                }
                if (!alertTriggered) {
                    append("No sharp signals detected")
                }
            }

            return SharpAlertAnalysis(
                alertTriggered = alertTriggered,
                alertType = alertType,
                sharpSide = sharpSide,
                publicSide = rlmResult.publicSide,
                publicPercentage = when (rlmResult.publicSide) {
                    "HOME" -> lineMovement.publicBetPercentHome
                    "AWAY" -> lineMovement.publicBetPercentAway
                    else -> null
                },
                moneyPercentage = when (sharpSide) {
                    "HOME" -> lineMovement.moneyPercentHome
                    "AWAY" -> lineMovement.moneyPercentAway
                    else -> null
                },
                divergence = if (sharpResult.detected) sharpResult.divergence else null,
                lineMovementDirection = lineDirection,
                confidenceBoost = if (alertTriggered) confidenceBoost else null,
                alertDescription = description
            )
        }
    }

    /**
     * Get recommendation adjustment based on sharp signals.
     * Returns the side that sharp money favors, or null if no strong signal.
     */
    fun getSharpRecommendation(): String? {
        return if (alertTriggered && sharpSide != null) sharpSide else null
    }

    /**
     * Check if this alert should override public sentiment.
     */
    fun shouldFadePublic(): Boolean {
        return alertTriggered && alertType in listOf("RLM", "BOTH")
    }
}

/**
 * Complete analysis result combining input and AI response.
 */
data class BetAnalysisResult(
    val matchData: MatchData,
    val impliedProbabilities: ImpliedProbabilities,
    val aiAnalysis: AIAnalysisResponse,
    val analysisTimestamp: Long = System.currentTimeMillis()
) {
    val isRecommended: Boolean
        get() = aiAnalysis.isValueBet && aiAnalysis.confidenceScore >= 0.6

    /**
     * Check if the recommended team has positive momentum.
     */
    val hasPositiveMomentum: Boolean
        get() {
            val momentum = aiAnalysis.momentumDivergence ?: return false
            return when (aiAnalysis.recommendation) {
                "HOME" -> (momentum.homeMomentum ?: 0.0) > 0.05
                "AWAY" -> (momentum.awayMomentum ?: 0.0) > 0.05
                else -> false
            }
        }

    /**
     * Get a summary of the momentum situation.
     */
    val momentumSummary: String
        get() {
            val momentum = aiAnalysis.momentumDivergence ?: return "No momentum data"
            val homeStatus = when {
                (momentum.homeMomentum ?: 0.0) > 0.05 -> "🔥 Hot"
                (momentum.homeMomentum ?: 0.0) < -0.05 -> "❄️ Cold"
                else -> "➡️ Neutral"
            }
            val awayStatus = when {
                (momentum.awayMomentum ?: 0.0) > 0.05 -> "🔥 Hot"
                (momentum.awayMomentum ?: 0.0) < -0.05 -> "❄️ Cold"
                else -> "➡️ Neutral"
            }
            return "Home: $homeStatus | Away: $awayStatus"
        }

    /**
     * Check if ensemble consensus was reached (≥2 agents agree).
     */
    val hasEnsembleConsensus: Boolean
        get() = aiAnalysis.ensembleAnalysis?.consensusReached == true

    /**
     * Get the number of agents that found value.
     */
    val agentConsensusCount: Int
        get() = aiAnalysis.ensembleAnalysis?.consensusCount ?: 0

    /**
     * Get a summary of the ensemble analysis.
     */
    val ensembleSummary: String
        get() {
            val ensemble = aiAnalysis.ensembleAnalysis ?: return "No ensemble data"
            val stats = if (ensemble.statisticalModeler.findsValue) "✓" else "✗"
            val scout = if (ensemble.proScout.findsValue) "✓" else "✗"
            val market = if (ensemble.marketSharp.findsValue) "✓" else "✗"
            return "📊Stats:$stats | 🔍Scout:$scout | 💹Market:$market (${ensemble.consensusCount}/3)"
        }

    /**
     * Get detailed breakdown of each agent's opinion.
     */
    val agentBreakdown: List<Pair<String, String>>
        get() {
            val ensemble = aiAnalysis.ensembleAnalysis ?: return emptyList()
            return listOf(
                "📊 Statistical Modeler" to "${ensemble.statisticalModeler.recommendation} (${String.format("%.1f", ensemble.statisticalModeler.estimatedEdge)}% edge) - ${ensemble.statisticalModeler.reasoning}",
                "🔍 Pro Scout" to "${ensemble.proScout.recommendation} (${String.format("%.1f", ensemble.proScout.estimatedEdge)}% edge) - ${ensemble.proScout.reasoning}",
                "💹 Market Sharp" to "${ensemble.marketSharp.recommendation} (${String.format("%.1f", ensemble.marketSharp.estimatedEdge)}% edge) - ${ensemble.marketSharp.reasoning}"
            )
        }

    // ═══════════════════════════════════════════════════════════════════════════════
    // LINE MOVEMENT INTELLIGENCE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Check if a sharp alert was triggered for this match.
     */
    val hasSharpAlert: Boolean
        get() = aiAnalysis.sharpAlert?.alertTriggered == true

    /**
     * Get the sharp alert type if present.
     */
    val sharpAlertType: String?
        get() = aiAnalysis.sharpAlert?.alertType

    /**
     * Get the side favored by sharp money.
     */
    val sharpMoneySide: String?
        get() = aiAnalysis.sharpAlert?.sharpSide

    /**
     * Get a concise summary of line movement signals.
     */
    val lineMovementSummary: String
        get() {
            val signal = aiAnalysis.lineMovementSignal ?: return "No line data"
            val sharpAlert = aiAnalysis.sharpAlert

            return when {
                signal.startsWith("SHARP_") -> {
                    val side = signal.removePrefix("SHARP_")
                    val alertDesc = sharpAlert?.alertDescription ?: ""
                    "⚠️ SHARP on $side: $alertDesc"
                }
                signal.startsWith("PUBLIC_") -> {
                    val side = signal.removePrefix("PUBLIC_")
                    val pct = sharpAlert?.publicPercentage?.let { "${(it * 100).toInt()}%" } ?: ""
                    "📢 Public $pct on $side"
                }
                else -> "➡️ Line stable"
            }
        }

    /**
     * Check if recommendation aligns with sharp money.
     */
    val recommendationAlignsWithSharps: Boolean
        get() {
            val sharpSide = aiAnalysis.sharpAlert?.sharpSide ?: return false
            return aiAnalysis.recommendation == sharpSide
        }

    /**
     * Get confidence boost from sharp signals.
     */
    val sharpConfidenceBoost: Double
        get() = aiAnalysis.sharpAlert?.confidenceBoost ?: 0.0

    /**
     * Get full sharp analysis summary for display.
     */
    val sharpAnalysisSummary: String
        get() {
            val sharpAlert = aiAnalysis.sharpAlert
            if (sharpAlert == null || !sharpAlert.alertTriggered) {
                return "No sharp signals detected"
            }

            return buildString {
                appendLine("🎯 SHARP ALERT: ${sharpAlert.alertType}")
                appendLine(sharpAlert.alertDescription)

                sharpAlert.sharpSide?.let { side ->
                    appendLine()
                    appendLine("Sharp Side: $side")
                    sharpAlert.moneyPercentage?.let {
                        append("Money: ${String.format("%.0f", it * 100)}%")
                    }
                    sharpAlert.divergence?.let {
                        append(" (${String.format("+%.0f", it * 100)}% vs bets)")
                    }
                }

                sharpAlert.confidenceBoost?.let {
                    appendLine()
                    appendLine("Confidence Boost: +${String.format("%.0f", it * 100)}%")
                }
            }
        }
}

/**
 * Sealed class for analysis results with error handling.
 */
sealed class AnalysisResult {
    data class Success(val result: BetAnalysisResult) : AnalysisResult()
    data class Error(val message: String, val exception: Exception? = null) : AnalysisResult()
    data object Loading : AnalysisResult()
}

// ═══════════════════════════════════════════════════════════════════════════════
// BULK ANALYSIS MODELS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Input data for bulk slate analysis.
 * Contains match data paired with its statistics.
 */
data class SlateMatchInput(
    val matchData: MatchData,
    val recentStats: RecentStats
) {
    /**
     * Generate a unique match key for mapping responses.
     */
    val matchKey: String
        get() = matchData.eventId
}

/**
 * Single match analysis within a bulk response.
 * Contains the event_id to map back to the original match.
 */
data class BulkMatchAnalysis(
    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("recommendation")
    val recommendation: String,

    @SerializedName("confidence_score")
    val confidenceScore: Double,

    @SerializedName("is_value_bet")
    val isValueBet: Boolean,

    @SerializedName("rationale")
    val rationale: String,

    @SerializedName("projected_probability")
    val projectedProbability: Double? = null,

    @SerializedName("edge_percentage")
    val edgePercentage: Double? = null,

    @SerializedName("suggested_stake")
    val suggestedStake: Int? = null,

    @SerializedName("kelly_stake")
    val kellyStake: KellyStakeResult? = null,

    @SerializedName("ensemble_analysis")
    val ensembleAnalysis: EnsembleAnalysis? = null,

    @SerializedName("momentum_divergence")
    val momentumDivergence: MomentumDivergence? = null,

    @SerializedName("fatigue_adjusted")
    val fatigueAdjusted: Boolean? = null,

    // Line Movement Intelligence
    @SerializedName("sharp_alert")
    val sharpAlert: SharpAlertAnalysis? = null,

    @SerializedName("line_movement_signal")
    val lineMovementSignal: String? = null
) {
    /**
     * Convert to AIAnalysisResponse for compatibility with existing UI.
     */
    fun toAIAnalysisResponse(): AIAnalysisResponse = AIAnalysisResponse(
        recommendation = recommendation,
        confidenceScore = confidenceScore,
        isValueBet = isValueBet,
        rationale = rationale,
        projectedProbability = projectedProbability,
        edgePercentage = edgePercentage,
        suggestedStake = suggestedStake,
        kellyStake = kellyStake,
        ensembleAnalysis = ensembleAnalysis,
        momentumDivergence = momentumDivergence,
        fatigueAdjusted = fatigueAdjusted,
        sharpAlert = sharpAlert,
        lineMovementSignal = lineMovementSignal
    )
}

/**
 * Result of bulk slate analysis.
 */
sealed class BulkAnalysisResult {
    /**
     * Successful bulk analysis with all match results.
     */
    data class Success(
        val results: List<BetAnalysisResult>,
        val totalMatches: Int,
        val valueBetsFound: Int,
        val analysisTimestamp: Long = System.currentTimeMillis()
    ) : BulkAnalysisResult() {
        val successRate: Double
            get() = if (totalMatches > 0) results.size.toDouble() / totalMatches else 0.0
    }

    /**
     * Partial success - some matches analyzed, some failed.
     */
    data class PartialSuccess(
        val results: List<BetAnalysisResult>,
        val failures: List<Pair<String, String>>,  // eventId to error message
        val analysisTimestamp: Long = System.currentTimeMillis()
    ) : BulkAnalysisResult()

    /**
     * Complete failure of bulk analysis.
     */
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : BulkAnalysisResult()

    /**
     * Loading state for bulk analysis.
     */
    data object Loading : BulkAnalysisResult()
}

/**
 * Summary statistics for a slate analysis.
 */
data class SlateAnalysisSummary(
    val totalMatches: Int,
    val valueBetsFound: Int,
    val averageEdge: Double,
    val totalSuggestedUnits: Int,
    val topPicks: List<BetAnalysisResult>  // Top 3 by edge
) {
    companion object {
        fun fromResults(results: List<BetAnalysisResult>): SlateAnalysisSummary {
            val valueBets = results.filter { it.aiAnalysis.isValueBet }
            val averageEdge = valueBets.mapNotNull { it.aiAnalysis.edgePercentage }.average()
                .takeIf { !it.isNaN() } ?: 0.0
            val totalUnits = valueBets.mapNotNull { it.aiAnalysis.suggestedStake }.sum()
            val topPicks = results
                .filter { it.aiAnalysis.isValueBet }
                .sortedByDescending { it.aiAnalysis.edgePercentage ?: 0.0 }
                .take(3)

            return SlateAnalysisSummary(
                totalMatches = results.size,
                valueBetsFound = valueBets.size,
                averageEdge = averageEdge,
                totalSuggestedUnits = totalUnits,
                topPicks = topPicks
            )
        }
    }
}
