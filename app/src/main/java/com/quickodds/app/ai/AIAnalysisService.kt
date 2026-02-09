package com.quickodds.app.ai

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.quickodds.app.ai.api.AnthropicApi
import com.quickodds.app.ai.api.Message
import com.quickodds.app.ai.api.MessageRequest
import com.quickodds.app.ai.model.*
import com.quickodds.app.ai.model.KellyCriterionCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Analysis Service using Claude via Firebase Function.
 *
 * This service acts as a Quantitative Sports Analyst, analyzing match data
 * and statistics to identify value betting opportunities.
 *
 * The API key is stored securely in Firebase and never exposed to the client.
 */
class AIAnalysisService(
    private val api: AnthropicApi
) {
    private val gson = Gson()

    // ═══════════════════════════════════════════════════════════════════════════════
    // MODEL SWITCHING - Cost Optimization
    // ═══════════════════════════════════════════════════════════════════════════════
    //
    // SONNET (Premium): Advanced Quantitative Analysis, Multi-Agent Ensemble
    // HAIKU (Budget):   Result Settlement, Basic Data Formatting
    //
    // Cost savings: ~90% reduction for routine background tasks
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Task types that determine which model to use.
     */
    enum class AITaskType {
        /** Advanced multi-agent betting analysis - uses SONNET */
        ADVANCED_ANALYSIS,

        /** Bulk slate analysis - uses SONNET */
        BULK_ANALYSIS,

        /** Match result settlement determination - uses HAIKU */
        RESULT_SETTLEMENT,

        /** Basic data formatting/extraction - uses HAIKU */
        DATA_FORMATTING,

        /** Simple queries and lookups - uses HAIKU */
        SIMPLE_QUERY
    }

    /**
     * Get the appropriate model for the task type.
     */
    private fun getModelForTask(taskType: AITaskType): String {
        return when (taskType) {
            AITaskType.ADVANCED_ANALYSIS -> AnthropicApi.MODEL_SONNET
            AITaskType.BULK_ANALYSIS -> AnthropicApi.MODEL_SONNET
            AITaskType.RESULT_SETTLEMENT -> AnthropicApi.MODEL_HAIKU
            AITaskType.DATA_FORMATTING -> AnthropicApi.MODEL_HAIKU
            AITaskType.SIMPLE_QUERY -> AnthropicApi.MODEL_HAIKU
        }
    }

    /**
     * Get max tokens for task type.
     */
    private fun getMaxTokensForTask(taskType: AITaskType): Int {
        return when (taskType) {
            AITaskType.ADVANCED_ANALYSIS -> 1500
            AITaskType.BULK_ANALYSIS -> 4000
            AITaskType.RESULT_SETTLEMENT -> 300
            AITaskType.DATA_FORMATTING -> 500
            AITaskType.SIMPLE_QUERY -> 200
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // HAIKU SYSTEM PROMPTS (Low-cost routine tasks)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * System prompt for result settlement (HAIKU).
     * Simple, focused task - determine bet outcome from scores.
     */
    private val settlementSystemPrompt = """
        You are a sports result settlement assistant. Your ONLY job is to determine bet outcomes from match scores.

        INPUT: You will receive match details and final scores.
        OUTPUT: JSON only with the settlement determination.

        Rules:
        1. Compare final scores to determine winner (HOME/AWAY/DRAW)
        2. Match winner against user's bet selection
        3. Return settlement status

        OUTPUT FORMAT (JSON ONLY):
        {
            "match_completed": <boolean>,
            "home_score": <int>,
            "away_score": <int>,
            "actual_winner": "HOME" | "AWAY" | "DRAW",
            "user_selection": "<from input>",
            "bet_result": "WON" | "LOST" | "PUSH" | "PENDING",
            "settlement_reason": "<brief explanation>"
        }

        CRITICAL: Output ONLY valid JSON. No explanations outside JSON.
    """.trimIndent()

    /**
     * System prompt for basic data formatting (HAIKU).
     * Extract and format data from API responses.
     */
    private val dataFormattingSystemPrompt = """
        You are a data formatting assistant. Extract and format sports data into clean JSON.

        Tasks:
        - Parse match schedules
        - Extract team names and odds
        - Format timestamps
        - Normalize data structures

        OUTPUT: Always return valid JSON matching the requested format.
        Be concise and accurate. No explanations needed.
    """.trimIndent()

    /**
     * System prompt for simple queries (HAIKU).
     * Quick lookups and basic calculations.
     */
    private val simpleQuerySystemPrompt = """
        You are a quick sports data assistant. Answer queries briefly and accurately.

        - For calculations: show result only
        - For lookups: return the value
        - For yes/no questions: answer directly

        Be concise. One-line answers when possible.
    """.trimIndent()

    // ═══════════════════════════════════════════════════════════════════════════════
    // SONNET SYSTEM PROMPTS (Premium advanced analysis)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * System prompt for SINGLE match analysis (SONNET).
     * Uses Multi-Agent Ensemble approach with Line Movement Intelligence.
     */
    private val singleMatchSystemPrompt = """
        You are a professional Quantitative Sports Betting Analyst implementing a MULTI-AGENT ENSEMBLE approach.

        ═══════════════════════════════════════════════════════════════════════════════
        MULTI-AGENT ENSEMBLE ANALYSIS (MANDATORY)
        ═══════════════════════════════════════════════════════════════════════════════

        You MUST analyze each match from THREE distinct expert perspectives, then synthesize:

        **AGENT 1: STATISTICAL MODELER** 📊
        - Focus: Pure numbers, historical data, probability models
        - Analyzes: Win rates, scoring averages, head-to-head records, home/away splits
        - Method: Calculate fair odds using historical performance data
        - Edge threshold: >5% edge required to find value

        **AGENT 2: PRO SCOUT** 🔍
        - Focus: Context, injuries, team dynamics, matchup advantages
        - Analyzes: Key player availability, tactical matchups, motivation factors
        - Method: Qualitative assessment of intangibles that stats miss
        - Edge threshold: >5% edge required to find value

        **AGENT 3: MARKET SHARP** 💹
        - Focus: Line movement, market signals, where smart money flows, cross-bookmaker odds discrepancies
        - Analyzes: Opening vs current odds, steam moves, reverse line movement, odds spread across books
        - Method: Detect if market is overreacting or underreacting. Wide odds spread = disagreement = value signal
        - When multiple bookmakers provided: compare best odds vs consensus to find where books disagree
        - Edge threshold: >5% edge required to find value

        **CONSENSUS RULE (CRITICAL):**
        is_value_bet = TRUE only if AT LEAST 2 of 3 agents agree there is >5% edge
        If only 1 or 0 agents find value → is_value_bet = FALSE

        ═══════════════════════════════════════════════════════════════════════════════
        LINE MOVEMENT INTELLIGENCE (CRITICAL)
        ═══════════════════════════════════════════════════════════════════════════════

        When LINE MOVEMENT DATA is provided, apply these rules:

        **SHARP ALERT - Reverse Line Movement (RLM):**
        If PUBLIC BET % is >75% on one side BUT the betting LINE is MOVING OPPOSITE:
        → This is a SHARP ALERT! Professional money is fading the public.
        → PRIORITIZE the side where the line is moving (not the public side).
        → Set line_movement_signal = "SHARP_HOME" or "SHARP_AWAY"

        **SHARP MONEY DETECTION:**
        Compare BET % vs MONEY % for each side:
        - If MONEY % is significantly HIGHER than BET % (>15% gap):
          → Sharp bettors are placing LARGER bets on that side
          → PRIORITIZE the side with higher MONEY % than BET %
        - Example: 30% of bets but 55% of money on Away = Sharp on Away

        **PRIORITIZATION RULE (MANDATORY):**
        When Money % >> Bet % on a side, that side has SHARP ACTION.
        The Market Sharp agent MUST prioritize the side with higher Money %.
        Boost confidence by 5-8% when sharp signals align with your pick.

        **Sharp Alert Output:**
        {
            "sharp_alert": {
                "alert_triggered": <boolean>,
                "alert_type": "RLM" | "SHARP_MONEY" | "BOTH" | null,
                "sharp_side": "HOME" | "AWAY" | null,
                "public_side": "HOME" | "AWAY" | null,
                "public_percentage": <float>,
                "money_percentage": <float>,
                "divergence": <float, money% - bet%>,
                "line_movement_direction": "TOWARD_HOME" | "TOWARD_AWAY" | "NEUTRAL",
                "confidence_boost": <float, 0.05-0.08 if alert triggered>,
                "alert_description": "<human-readable description>"
            },
            "line_movement_signal": "SHARP_HOME" | "SHARP_AWAY" | "PUBLIC_HOME" | "PUBLIC_AWAY" | "NEUTRAL"
        }

        ═══════════════════════════════════════════════════════════════════════════════
        METHODOLOGY
        ═══════════════════════════════════════════════════════════════════════════════

        1. Calculate Implied Probability from decimal odds: P = 1 / Decimal_Odds
        2. Each agent estimates their own TRUE probability independently
        3. Final projected_probability = average of the 3 agent probabilities
        4. A "Value Bet" requires ≥2 agents agreeing on >5% edge

        **MOMENTUM & TREND ANALYSIS:**
        - Rolling Momentum Score: L3_WinPct - L10_WinPct
        - Fatigue Penalty: -3% if Back-to-Back or 3rd game in 4 nights

        **KELLY CRITERION (Half-Kelly):**
        f* = 0.5 × ((b × p - q) / b)
        where b = odds-1, p = projected_probability, q = 1-p

        Stake Units: 0 (no bet) → 5 (max confidence)

        ═══════════════════════════════════════════════════════════════════════════════
        OUTPUT FORMAT (JSON ONLY)
        ═══════════════════════════════════════════════════════════════════════════════

        {
            "recommendation": "HOME" | "AWAY" | "DRAW" | "NO_BET",
            "confidence_score": <float 0.3-0.85>,
            "is_value_bet": <boolean, TRUE only if ≥2 agents agree on >5% edge>,
            "rationale": "<synthesize all 3 agent opinions, explain consensus, mention sharp signals if present>",
            "projected_probability": <float, average of 3 agents>,
            "edge_percentage": <float>,
            "suggested_stake": <integer 0-5>,
            "kelly_stake": {
                "edge": <float>,
                "kelly_fraction": <float>,
                "half_kelly_fraction": <float>,
                "recommended_stake_percent": <float>,
                "stake_units": <integer 0-5>
            },
            "ensemble_analysis": {
                "statistical_modeler": {
                    "agent_name": "statistical_modeler",
                    "recommendation": "HOME" | "AWAY" | "DRAW" | "NO_BET",
                    "projected_probability": <float>,
                    "estimated_edge": <float, as percentage>,
                    "finds_value": <boolean, true if edge > 5%>,
                    "reasoning": "<brief stats-based reasoning>"
                },
                "pro_scout": {
                    "agent_name": "pro_scout",
                    "recommendation": "HOME" | "AWAY" | "DRAW" | "NO_BET",
                    "projected_probability": <float>,
                    "estimated_edge": <float>,
                    "finds_value": <boolean>,
                    "reasoning": "<brief context/injury-based reasoning>"
                },
                "market_sharp": {
                    "agent_name": "market_sharp",
                    "recommendation": "HOME" | "AWAY" | "DRAW" | "NO_BET",
                    "projected_probability": <float>,
                    "estimated_edge": <float>,
                    "finds_value": <boolean>,
                    "reasoning": "<MUST reference line movement/sharp signals if provided>"
                },
                "consensus_count": <integer 0-3, how many find value>,
                "consensus_reached": <boolean, true if ≥2 agree>,
                "dissenting_opinion": "<explain any disagreement, or null>"
            },
            "momentum_divergence": {
                "home_momentum": <float or null>,
                "away_momentum": <float or null>,
                "home_season_divergence": <float or null>,
                "away_season_divergence": <float or null>,
                "momentum_advantage": "HOME" | "AWAY" | "NEUTRAL",
                "confidence_adjustment": <float>
            },
            "fatigue_adjusted": <boolean>,
            "sharp_alert": {
                "alert_triggered": <boolean>,
                "alert_type": "RLM" | "SHARP_MONEY" | "BOTH" | null,
                "sharp_side": "HOME" | "AWAY" | null,
                "public_side": "HOME" | "AWAY" | null,
                "public_percentage": <float or null>,
                "money_percentage": <float or null>,
                "divergence": <float or null>,
                "line_movement_direction": "TOWARD_HOME" | "TOWARD_AWAY" | "NEUTRAL",
                "confidence_boost": <float or null>,
                "alert_description": "<string>"
            },
            "line_movement_signal": "SHARP_HOME" | "SHARP_AWAY" | "PUBLIC_HOME" | "PUBLIC_AWAY" | "NEUTRAL"
        }

        ═══════════════════════════════════════════════════════════════════════════════
        CRITICAL RULES
        ═══════════════════════════════════════════════════════════════════════════════

        1. ALWAYS output valid JSON only - no markdown, no explanations
        2. ALWAYS analyze from all 3 agent perspectives before concluding
        3. is_value_bet = TRUE **ONLY IF** consensus_count ≥ 2 AND consensus_reached = TRUE
        4. Each agent must provide independent probability estimate and reasoning
        5. The rationale must synthesize and compare all 3 perspectives
        6. If agents disagree, explain the dissenting_opinion
        7. suggested_stake MUST match kelly_stake.stake_units
        8. Apply 3% fatigue penalty when indicated
        9. When sharp signals detected: Market Sharp agent MUST factor this into recommendation
        10. PRIORITIZE sides with Money % >> Bet % (sharp money indicator)
        11. ANTI-HALLUCINATION: Do NOT invent team form, W/L records, or statistics that are not provided in the data. If form says "Not available", state that data is limited and rely on odds/market signals
        12. MISSING FORM DATA: When recent form/statistics are unavailable, cap confidence_score at 0.65 and require edge_percentage > 7% for is_value_bet = TRUE
        13. Use LEAGUE BASELINE stats as base rates when team-specific data is missing
        14. Use BEST AVAILABLE ODDS (not average) for Kelly Criterion calculations
        15. When ODDS SPREAD is wide (>0.3), flag this as bookmaker disagreement in Market Sharp reasoning
    """.trimIndent()

    /**
     * System prompt for BULK slate analysis (5-10 matches).
     * Optimized to send one system prompt and analyze all matches in a single round-trip.
     * This reduces token costs significantly compared to individual calls.
     */
    private val bulkSlateSystemPrompt = """
        You are a professional Quantitative Sports Betting Analyst implementing a MULTI-AGENT ENSEMBLE approach.

        ═══════════════════════════════════════════════════════════════════════════════
        BULK SLATE ANALYSIS MODE
        ═══════════════════════════════════════════════════════════════════════════════

        You will receive a list of 5-10 matches. Analyze EACH match using the Multi-Agent Ensemble method
        and return a JSON ARRAY of recommendations.

        **MULTI-AGENT ENSEMBLE (Applied to each match):**

        AGENT 1: STATISTICAL MODELER 📊 - Pure numbers, probability models, historical data
        AGENT 2: PRO SCOUT 🔍 - Context, injuries, matchups, intangibles
        AGENT 3: MARKET SHARP 💹 - Line movement, market signals, smart money, SHARP ALERTS

        **CONSENSUS RULE:** is_value_bet = TRUE only if AT LEAST 2 of 3 agents agree there is >5% edge

        ═══════════════════════════════════════════════════════════════════════════════
        LINE MOVEMENT INTELLIGENCE (APPLY TO EACH MATCH)
        ═══════════════════════════════════════════════════════════════════════════════

        When LINE MOVEMENT DATA is provided for a match:

        **SHARP ALERT - Reverse Line Movement (RLM):**
        - If PUBLIC BET % >75% on one side BUT line moves OPPOSITE → SHARP ALERT!
        - Professional money is fading the public
        - PRIORITIZE the side where the line is moving

        **SHARP MONEY DETECTION (CRITICAL RULE):**
        - Compare BET % vs MONEY % for each side
        - If MONEY % is significantly HIGHER than BET % (>15% gap):
          → Sharp bettors placing LARGER bets on that side
          → PRIORITIZE the side with higher MONEY % than BET %
        - Example: 30% of bets but 55% of money on Away = Sharp on Away

        **PRIORITIZATION RULE (MANDATORY):**
        When Money % >> Bet % on a side, the Market Sharp agent MUST:
        1. Identify this as sharp action
        2. Factor it into their recommendation
        3. Boost confidence by 5-8% when sharp signals align

        **METHODOLOGY:**
        1. Calculate Implied Probability: P = 1 / Decimal_Odds
        2. Each agent estimates TRUE probability independently
        3. projected_probability = average of 3 agent probabilities
        4. Edge = projected_probability - implied_probability
        5. Half-Kelly: f* = 0.5 × ((b × p - q) / b)

        **MOMENTUM & FATIGUE:**
        - Rolling Momentum Score: L3_WinPct - L10_WinPct
        - Fatigue Penalty: -3% if Back-to-Back or 3rd game in 4 nights

        ═══════════════════════════════════════════════════════════════════════════════
        OUTPUT FORMAT - JSON ARRAY (MANDATORY)
        ═══════════════════════════════════════════════════════════════════════════════

        Return ONLY a valid JSON array. Each element must include the event_id to identify the match:

        [
            {
                "event_id": "<must match input event_id>",
                "recommendation": "HOME" | "AWAY" | "DRAW" | "NO_BET",
                "confidence_score": <float 0.3-0.85>,
                "is_value_bet": <boolean>,
                "rationale": "<concise synthesis, mention sharp signals if detected>",
                "projected_probability": <float>,
                "edge_percentage": <float>,
                "suggested_stake": <integer 0-5>,
                "kelly_stake": {
                    "edge": <float>,
                    "kelly_fraction": <float>,
                    "half_kelly_fraction": <float>,
                    "recommended_stake_percent": <float>,
                    "stake_units": <integer 0-5>
                },
                "ensemble_analysis": {
                    "statistical_modeler": { "agent_name": "statistical_modeler", "recommendation": "...", "projected_probability": <float>, "estimated_edge": <float>, "finds_value": <bool>, "reasoning": "..." },
                    "pro_scout": { "agent_name": "pro_scout", "recommendation": "...", "projected_probability": <float>, "estimated_edge": <float>, "finds_value": <bool>, "reasoning": "..." },
                    "market_sharp": { "agent_name": "market_sharp", "recommendation": "...", "projected_probability": <float>, "estimated_edge": <float>, "finds_value": <bool>, "reasoning": "<MUST reference sharp signals if present>" },
                    "consensus_count": <integer 0-3>,
                    "consensus_reached": <boolean>,
                    "dissenting_opinion": "<or null>"
                },
                "momentum_divergence": {
                    "home_momentum": <float or null>,
                    "away_momentum": <float or null>,
                    "momentum_advantage": "HOME" | "AWAY" | "NEUTRAL",
                    "confidence_adjustment": <float>
                },
                "fatigue_adjusted": <boolean>,
                "sharp_alert": {
                    "alert_triggered": <boolean>,
                    "alert_type": "RLM" | "SHARP_MONEY" | "BOTH" | null,
                    "sharp_side": "HOME" | "AWAY" | null,
                    "public_percentage": <float or null>,
                    "money_percentage": <float or null>,
                    "divergence": <float or null>,
                    "alert_description": "<string>"
                },
                "line_movement_signal": "SHARP_HOME" | "SHARP_AWAY" | "PUBLIC_HOME" | "PUBLIC_AWAY" | "NEUTRAL"
            },
            ... (one object per match)
        ]

        ═══════════════════════════════════════════════════════════════════════════════
        CRITICAL RULES
        ═══════════════════════════════════════════════════════════════════════════════

        1. OUTPUT MUST BE A VALID JSON ARRAY - no markdown, no explanations, no text outside the array
        2. EVERY match in input MUST have a corresponding object in output
        3. event_id in output MUST exactly match the event_id from input
        4. Apply 3% fatigue penalty when indicated
        5. is_value_bet = TRUE ONLY IF consensus_count >= 2
        6. Keep rationale concise (1-2 sentences per match)
        7. When sharp signals detected: Market Sharp agent MUST reference them
        8. PRIORITIZE sides where Money % >> Bet % (sharp money indicator)
        9. ANTI-HALLUCINATION: Do NOT invent team form or statistics not in the data. If form is unavailable, rely on odds/market signals
        10. MISSING FORM DATA: Cap confidence_score at 0.65 and require edge > 7% when form is unavailable
        11. Use LEAGUE BASELINE stats as base rates when team-specific data is missing
        12. Use BEST AVAILABLE ODDS for Kelly calculations. Flag wide odds spreads (>0.3) as value signals
    """.trimIndent()

    /**
     * Analyze a match and return betting recommendation.
     *
     * @param matchData The match information with odds
     * @param recentStats Recent statistics and form data
     * @return AnalysisResult containing the AI analysis or error
     */
    suspend fun analyzeMatch(
        matchData: MatchData,
        recentStats: RecentStats
    ): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            val impliedProbs = matchData.getImpliedProbabilities()
            val userPrompt = buildUserPrompt(matchData, impliedProbs, recentStats)

            val request = MessageRequest(
                model = AnthropicApi.MODEL_SONNET,
                maxTokens = 1500, // Increased for Multi-Agent Ensemble analysis
                system = singleMatchSystemPrompt,
                messages = listOf(
                    Message(role = "user", content = userPrompt)
                )
            )

            val response = api.createMessage(request = request)

            if (response.isSuccessful) {
                val messageResponse = response.body()
                    ?: return@withContext AnalysisResult.Error("Empty response from API")

                val textContent = messageResponse.getTextContent()
                var aiResponse = parseAIResponse(textContent)

                // Verify and potentially correct Kelly calculation
                aiResponse = verifyKellyCalculation(aiResponse, matchData, impliedProbs)

                AnalysisResult.Success(
                    BetAnalysisResult(
                        matchData = matchData,
                        impliedProbabilities = impliedProbs,
                        aiAnalysis = aiResponse
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                AnalysisResult.Error("API Error ${response.code()}: $errorBody")
            }
        } catch (e: JsonSyntaxException) {
            AnalysisResult.Error("Failed to parse AI response: ${e.message}", e)
        } catch (e: MalformedResponseException) {
            AnalysisResult.Error("Malformed AI response: ${e.message}", e)
        } catch (e: Exception) {
            AnalysisResult.Error("Analysis failed: ${e.message}", e)
        }
    }

    /**
     * Verify and correct the AI's Kelly calculation if needed.
     * This ensures mathematical accuracy of the stake recommendation.
     */
    private fun verifyKellyCalculation(
        aiResponse: AIAnalysisResponse,
        matchData: MatchData,
        impliedProbs: ImpliedProbabilities
    ): AIAnalysisResponse {
        val projectedProb = aiResponse.projectedProbability ?: return aiResponse

        // Determine which odds to use based on recommendation
        val (decimalOdds, impliedProb) = when (aiResponse.recommendation) {
            "HOME" -> matchData.homeOdds to impliedProbs.home
            "AWAY" -> matchData.awayOdds to impliedProbs.away
            "DRAW" -> (matchData.drawOdds ?: return aiResponse) to (impliedProbs.draw ?: return aiResponse)
            else -> return aiResponse // NO_BET - no calculation needed
        }

        // Calculate correct Kelly stake using our calculator
        val correctKelly = KellyCriterionCalculator.calculate(
            aiProbability = projectedProb,
            impliedProbability = impliedProb,
            decimalOdds = decimalOdds
        )

        // Return updated response with verified Kelly calculation
        return aiResponse.copy(
            kellyStake = correctKelly,
            suggestedStake = correctKelly.stakeUnits,
            edgePercentage = correctKelly.edge * 100
        )
    }

    /**
     * Build the user prompt with match data and statistics.
     */
    private fun buildUserPrompt(
        matchData: MatchData,
        impliedProbs: ImpliedProbabilities,
        recentStats: RecentStats
    ): String = buildString {
        appendLine("MATCH ANALYSIS REQUEST")
        appendLine("=" .repeat(50))
        appendLine()
        appendLine("EVENT: ${matchData.matchName}")
        appendLine("LEAGUE: ${matchData.league}")
        appendLine("KICKOFF: ${matchData.commenceTime}")
        appendLine()

        // Multi-bookmaker odds section
        appendLine("BEST AVAILABLE ODDS (Decimal) - across ${matchData.bookmakerCount} bookmakers:")
        appendLine("  - ${matchData.homeTeam} (Home): ${matchData.homeOdds}${matchData.bestHomeBookmaker?.let { " @ $it" } ?: ""}")
        matchData.drawOdds?.let { appendLine("  - Draw: $it${matchData.bestDrawBookmaker?.let { b -> " @ $b" } ?: ""}") }
        appendLine("  - ${matchData.awayTeam} (Away): ${matchData.awayOdds}${matchData.bestAwayBookmaker?.let { " @ $it" } ?: ""}")
        appendLine()

        // Market consensus from average odds
        if (matchData.avgHomeOdds != null && matchData.avgAwayOdds != null) {
            appendLine("MARKET CONSENSUS (Average odds across ${matchData.bookmakerCount} books):")
            appendLine("  - Home: ${String.format("%.2f", matchData.avgHomeOdds)}")
            matchData.avgDrawOdds?.let { appendLine("  - Draw: ${String.format("%.2f", it)}") }
            appendLine("  - Away: ${String.format("%.2f", matchData.avgAwayOdds)}")

            val consensus = matchData.getConsensusImpliedProbabilities()
            if (consensus != null) {
                appendLine("  Consensus Implied Prob: Home ${String.format("%.1f", consensus.home * 100)}%${consensus.draw?.let { " | Draw ${String.format("%.1f", it * 100)}%" } ?: ""} | Away ${String.format("%.1f", consensus.away * 100)}%")
            }
            appendLine()
        }

        // Odds spread (bookmaker disagreement)
        val spread = matchData.getOddsSpread()
        if (spread != null && matchData.bookmakerCount > 1) {
            appendLine("ODDS SPREAD (bookmaker disagreement):")
            appendLine("  - Home range: ${matchData.minHomeOdds} - ${matchData.maxHomeOdds} (spread: ${String.format("%.2f", spread.first)})")
            appendLine("  - Away range: ${matchData.minAwayOdds} - ${matchData.maxAwayOdds} (spread: ${String.format("%.2f", spread.third)})")
            if (spread.first > 0.3 || spread.third > 0.3) {
                appendLine("  ⚠️ WIDE SPREAD detected - bookmakers disagree, potential value opportunity")
            }
            appendLine()
        }

        appendLine("IMPLIED PROBABILITIES (from best odds):")
        appendLine("  - Home Win: ${String.format("%.1f", impliedProbs.home * 100)}%")
        impliedProbs.draw?.let { appendLine("  - Draw: ${String.format("%.1f", it * 100)}%") }
        appendLine("  - Away Win: ${String.format("%.1f", impliedProbs.away * 100)}%")
        appendLine("  - Bookmaker Margin: ${String.format("%.1f", impliedProbs.bookmakerMargin)}%")
        appendLine()

        // League baseline statistics
        val baseline = getLeagueBaseline(matchData.league)
        if (baseline != null) {
            appendLine("LEAGUE BASELINE STATISTICS:")
            appendLine("  $baseline")
            appendLine()
        }

        appendLine("RECENT STATISTICS:")
        val statsString = recentStats.toAnalysisString()
        if (statsString.isBlank()) {
            appendLine("  Not available - rely on odds data, market consensus, and league knowledge")
        } else {
            appendLine(statsString)
        }

        // Add fatigue alerts prominently
        val homeFatigue = recentStats.homeTrend?.hasFatigue == true
        val awayFatigue = recentStats.awayTrend?.hasFatigue == true
        if (homeFatigue || awayFatigue) {
            appendLine()
            appendLine("⚠️ FATIGUE ALERTS (APPLY 3% PENALTY):")
            if (homeFatigue) {
                val reason = if (recentStats.homeTrend?.isBackToBack == true) "Back-to-Back" else "3rd game in 4 nights"
                appendLine("  - ${matchData.homeTeam}: $reason → SUBTRACT 3% from win probability")
            }
            if (awayFatigue) {
                val reason = if (recentStats.awayTrend?.isBackToBack == true) "Back-to-Back" else "3rd game in 4 nights"
                appendLine("  - ${matchData.awayTeam}: $reason → SUBTRACT 3% from win probability")
            }
        }

        // Add momentum summary
        val homeMomentum = recentStats.homeTrend?.calculateMomentumScore()
        val awayMomentum = recentStats.awayTrend?.calculateMomentumScore()
        if (homeMomentum != null || awayMomentum != null) {
            appendLine()
            appendLine("MOMENTUM SUMMARY:")
            homeMomentum?.let {
                val trend = if (it > 0.05) "🔥 HOT" else if (it < -0.05) "❄️ COLD" else "➡️ NEUTRAL"
                appendLine("  - ${matchData.homeTeam}: $trend (${String.format("%+.1f", it * 100)}%)")
            }
            awayMomentum?.let {
                val trend = if (it > 0.05) "🔥 HOT" else if (it < -0.05) "❄️ COLD" else "➡️ NEUTRAL"
                appendLine("  - ${matchData.awayTeam}: $trend (${String.format("%+.1f", it * 100)}%)")
            }
        }

        appendLine()
        appendLine("Analyze using all bookmaker odds data, market consensus, and odds spread signals. Provide your JSON recommendation.")
    }

    /**
     * Calculate pre-computed momentum analysis for use in results.
     */
    fun calculateMomentumAnalysis(recentStats: RecentStats): MomentumAnalysisResult {
        val homeTrend = recentStats.homeTrend
        val awayTrend = recentStats.awayTrend

        val homeMomentum = homeTrend?.calculateMomentumScore()
        val awayMomentum = awayTrend?.calculateMomentumScore()
        val homeSeasonDiv = homeTrend?.calculateSeasonDivergence()
        val awaySeasonDiv = awayTrend?.calculateSeasonDivergence()

        // Determine momentum advantage
        val momentumAdvantage = when {
            homeMomentum == null && awayMomentum == null -> "NEUTRAL"
            homeMomentum != null && awayMomentum != null -> {
                when {
                    homeMomentum - awayMomentum > 0.05 -> "HOME"
                    awayMomentum - homeMomentum > 0.05 -> "AWAY"
                    else -> "NEUTRAL"
                }
            }
            homeMomentum != null && homeMomentum > 0.05 -> "HOME"
            awayMomentum != null && awayMomentum > 0.05 -> "AWAY"
            else -> "NEUTRAL"
        }

        // Calculate fatigue impact
        val homeFatigue = homeTrend?.hasFatigue == true
        val awayFatigue = awayTrend?.hasFatigue == true
        val fatiguePenalty = 0.03 // 3% penalty

        return MomentumAnalysisResult(
            homeMomentum = homeMomentum,
            awayMomentum = awayMomentum,
            homeSeasonDivergence = homeSeasonDiv,
            awaySeasonDivergence = awaySeasonDiv,
            momentumAdvantage = momentumAdvantage,
            homeFatiguePenalty = if (homeFatigue) fatiguePenalty else 0.0,
            awayFatiguePenalty = if (awayFatigue) fatiguePenalty else 0.0
        )
    }

    /**
     * Data class for pre-computed momentum analysis.
     */
    data class MomentumAnalysisResult(
        val homeMomentum: Double?,
        val awayMomentum: Double?,
        val homeSeasonDivergence: Double?,
        val awaySeasonDivergence: Double?,
        val momentumAdvantage: String,
        val homeFatiguePenalty: Double,
        val awayFatiguePenalty: Double
    ) {
        val hasFatigueAdjustment: Boolean
            get() = homeFatiguePenalty > 0 || awayFatiguePenalty > 0
    }

    /**
     * Get league baseline statistics for use as base rates when form data is unavailable.
     */
    private fun getLeagueBaseline(league: String): String? {
        val normalized = league.lowercase()
        return when {
            "premier league" in normalized || "epl" in normalized ->
                "EPL avg: Home win 46%, Draw 25%, Away win 29%, ~2.7 goals/game"
            "la liga" in normalized ->
                "La Liga avg: Home win 47%, Draw 24%, Away win 29%, ~2.5 goals/game"
            "serie a" in normalized ->
                "Serie A avg: Home win 45%, Draw 26%, Away win 29%, ~2.6 goals/game"
            "bundesliga" in normalized ->
                "Bundesliga avg: Home win 45%, Draw 23%, Away win 32%, ~3.1 goals/game"
            "ligue 1" in normalized ->
                "Ligue 1 avg: Home win 46%, Draw 25%, Away win 29%, ~2.6 goals/game"
            "mls" in normalized ->
                "MLS avg: Home win 49%, Draw 23%, Away win 28%, ~2.9 goals/game"
            "nba" in normalized ->
                "NBA avg: Home win 58%, ~224 points/game. No draws."
            "nfl" in normalized ->
                "NFL avg: Home win 57%, ~44 combined points/game. No draws."
            else -> null
        }
    }

    /**
     * Parse the AI response JSON with error handling.
     */
    private fun parseAIResponse(content: String): AIAnalysisResponse {
        // Clean the response - remove any markdown code blocks if present
        val cleanedContent = content
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (cleanedContent.isEmpty()) {
            throw MalformedResponseException("Empty response content")
        }

        // Attempt to find JSON object in the response
        val jsonStart = cleanedContent.indexOf('{')
        val jsonEnd = cleanedContent.lastIndexOf('}')

        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
            throw MalformedResponseException("No valid JSON object found in response: $cleanedContent")
        }

        val jsonString = cleanedContent.substring(jsonStart, jsonEnd + 1)

        return try {
            val response = gson.fromJson(jsonString, AIAnalysisResponse::class.java)
            validateResponse(response)
            response
        } catch (e: JsonSyntaxException) {
            throw MalformedResponseException("Invalid JSON syntax: ${e.message}")
        }
    }

    /**
     * Validate the parsed response has all required fields.
     */
    private fun validateResponse(response: AIAnalysisResponse) {
        require(response.recommendation in listOf("HOME", "AWAY", "DRAW", "NO_BET")) {
            "Invalid recommendation: ${response.recommendation}"
        }
        require(response.confidenceScore in 0.0..1.0) {
            "Confidence score must be between 0 and 1: ${response.confidenceScore}"
        }
        require(response.rationale.isNotBlank()) {
            "Rationale cannot be empty"
        }
    }

    /**
     * Batch analyze multiple matches (legacy - calls individual analysis).
     * @deprecated Use analyzeUpcomingSlate for better efficiency.
     */
    @Deprecated("Use analyzeUpcomingSlate for bulk analysis", ReplaceWith("analyzeUpcomingSlate(matches)"))
    suspend fun analyzeMatches(
        matches: List<Pair<MatchData, RecentStats>>
    ): List<AnalysisResult> = withContext(Dispatchers.IO) {
        matches.map { (matchData, stats) ->
            analyzeMatch(matchData, stats)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BULK SLATE ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Analyze an upcoming slate of 5-10 matches in a single API call.
     *
     * Benefits over individual analyzeMatch calls:
     * - Reduces system prompt token cost (paid once instead of N times)
     * - Single round-trip latency instead of N sequential calls
     * - More efficient for analyzing full daily slates
     *
     * @param matches List of match data with statistics (recommended: 5-10 matches)
     * @return BulkAnalysisResult containing all match analyses
     */
    suspend fun analyzeUpcomingSlate(
        matches: List<SlateMatchInput>
    ): BulkAnalysisResult = withContext(Dispatchers.IO) {
        if (matches.isEmpty()) {
            return@withContext BulkAnalysisResult.Error("No matches provided")
        }

        if (matches.size > 15) {
            return@withContext BulkAnalysisResult.Error("Too many matches (max 15). Split into smaller batches.")
        }

        try {
            // Build the bulk user prompt
            val userPrompt = buildBulkUserPrompt(matches)

            // Calculate tokens needed: ~800 tokens per match for response
            val estimatedMaxTokens = minOf(8192, 800 * matches.size)

            val request = MessageRequest(
                model = AnthropicApi.MODEL_SONNET,
                maxTokens = estimatedMaxTokens,
                system = bulkSlateSystemPrompt,
                messages = listOf(
                    Message(role = "user", content = userPrompt)
                )
            )

            val response = api.createMessage(request = request)

            if (response.isSuccessful) {
                val messageResponse = response.body()
                    ?: return@withContext BulkAnalysisResult.Error("Empty response from API")

                val textContent = messageResponse.getTextContent()
                parseBulkResponse(textContent, matches)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                BulkAnalysisResult.Error("API Error ${response.code()}: $errorBody")
            }
        } catch (e: JsonSyntaxException) {
            BulkAnalysisResult.Error("Failed to parse bulk response: ${e.message}", e)
        } catch (e: MalformedResponseException) {
            BulkAnalysisResult.Error("Malformed bulk response: ${e.message}", e)
        } catch (e: Exception) {
            BulkAnalysisResult.Error("Bulk analysis failed: ${e.message}", e)
        }
    }

    /**
     * Build the user prompt for bulk slate analysis.
     */
    private fun buildBulkUserPrompt(matches: List<SlateMatchInput>): String = buildString {
        appendLine("═══════════════════════════════════════════════════════════════════════════════")
        appendLine("SLATE ANALYSIS REQUEST - ${matches.size} MATCHES")
        appendLine("═══════════════════════════════════════════════════════════════════════════════")
        appendLine()

        matches.forEachIndexed { index, input ->
            val matchData = input.matchData
            val recentStats = input.recentStats
            val impliedProbs = matchData.getImpliedProbabilities()

            appendLine("───────────────────────────────────────────────────────────────────────────────")
            appendLine("MATCH ${index + 1}: ${matchData.matchName}")
            appendLine("───────────────────────────────────────────────────────────────────────────────")
            appendLine("EVENT_ID: ${matchData.eventId}")
            appendLine("LEAGUE: ${matchData.league}")
            appendLine("KICKOFF: ${matchData.commenceTime}")
            appendLine()
            appendLine("BEST ODDS (${matchData.bookmakerCount} books):")
            appendLine("  ${matchData.homeTeam} (Home): ${matchData.homeOdds}${matchData.bestHomeBookmaker?.let { " @ $it" } ?: ""}")
            matchData.drawOdds?.let { appendLine("  Draw: $it${matchData.bestDrawBookmaker?.let { b -> " @ $b" } ?: ""}") }
            appendLine("  ${matchData.awayTeam} (Away): ${matchData.awayOdds}${matchData.bestAwayBookmaker?.let { " @ $it" } ?: ""}")
            if (matchData.avgHomeOdds != null) {
                appendLine("  Avg: Home ${String.format("%.2f", matchData.avgHomeOdds)}${matchData.avgDrawOdds?.let { " | Draw ${String.format("%.2f", it)}" } ?: ""} | Away ${String.format("%.2f", matchData.avgAwayOdds)}")
            }
            val spread = matchData.getOddsSpread()
            if (spread != null && (spread.first > 0.3 || spread.third > 0.3)) {
                appendLine("  ⚠️ WIDE SPREAD: Home ${String.format("%.2f", spread.first)} | Away ${String.format("%.2f", spread.third)}")
            }
            appendLine()
            appendLine("IMPLIED PROB:")
            appendLine("  Home: ${String.format("%.1f", impliedProbs.home * 100)}%")
            impliedProbs.draw?.let { appendLine("  Draw: ${String.format("%.1f", it * 100)}%") }
            appendLine("  Away: ${String.format("%.1f", impliedProbs.away * 100)}%")
            appendLine("  Margin: ${String.format("%.1f", impliedProbs.bookmakerMargin)}%")
            appendLine()

            // League baseline
            getLeagueBaseline(matchData.league)?.let { appendLine("LEAGUE: $it") }

            // Recent stats summary (condensed for bulk)
            recentStats.homeTeamForm?.let { appendLine("Home Form: $it") }
            recentStats.awayTeamForm?.let { appendLine("Away Form: $it") }
            if (recentStats.homeTeamForm == null && recentStats.awayTeamForm == null) {
                appendLine("Form: Not available - use odds data and league baselines")
            }
            recentStats.headToHead?.let { appendLine("H2H: $it") }

            // Momentum summary
            val homeMomentum = recentStats.homeTrend?.calculateMomentumScore()
            val awayMomentum = recentStats.awayTrend?.calculateMomentumScore()
            if (homeMomentum != null || awayMomentum != null) {
                appendLine("MOMENTUM:")
                homeMomentum?.let { appendLine("  ${matchData.homeTeam}: ${String.format("%+.1f", it * 100)}%") }
                awayMomentum?.let { appendLine("  ${matchData.awayTeam}: ${String.format("%+.1f", it * 100)}%") }
            }

            // Fatigue alerts
            val homeFatigue = recentStats.homeTrend?.hasFatigue == true
            val awayFatigue = recentStats.awayTrend?.hasFatigue == true
            if (homeFatigue || awayFatigue) {
                appendLine("⚠️ FATIGUE:")
                if (homeFatigue) appendLine("  ${matchData.homeTeam}: ${if (recentStats.homeTrend?.isBackToBack == true) "B2B" else "3in4"} → -3%")
                if (awayFatigue) appendLine("  ${matchData.awayTeam}: ${if (recentStats.awayTrend?.isBackToBack == true) "B2B" else "3in4"} → -3%")
            }

            appendLine()
        }

        appendLine("═══════════════════════════════════════════════════════════════════════════════")
        appendLine("Analyze all ${matches.size} matches and return a JSON array of recommendations.")
        appendLine("═══════════════════════════════════════════════════════════════════════════════")
    }

    /**
     * Parse the bulk analysis response and map back to original matches.
     */
    private fun parseBulkResponse(
        content: String,
        originalMatches: List<SlateMatchInput>
    ): BulkAnalysisResult {
        // Clean the response
        val cleanedContent = content
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (cleanedContent.isEmpty()) {
            return BulkAnalysisResult.Error("Empty response content")
        }

        // Find JSON array in response
        val jsonStart = cleanedContent.indexOf('[')
        val jsonEnd = cleanedContent.lastIndexOf(']')

        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
            return BulkAnalysisResult.Error("No valid JSON array found in response")
        }

        val jsonString = cleanedContent.substring(jsonStart, jsonEnd + 1)

        return try {
            val typeToken = object : com.google.gson.reflect.TypeToken<List<BulkMatchAnalysis>>() {}.type
            val bulkAnalyses: List<BulkMatchAnalysis> = gson.fromJson(jsonString, typeToken)

            // Create a map of original matches by eventId for quick lookup
            val matchMap = originalMatches.associateBy { it.matchData.eventId }

            val results = mutableListOf<BetAnalysisResult>()
            val failures = mutableListOf<Pair<String, String>>()

            for (analysis in bulkAnalyses) {
                val originalInput = matchMap[analysis.eventId]
                if (originalInput == null) {
                    failures.add(analysis.eventId to "Event ID not found in original matches")
                    continue
                }

                try {
                    // Convert to standard AIAnalysisResponse
                    var aiResponse = analysis.toAIAnalysisResponse()

                    // Verify Kelly calculation
                    aiResponse = verifyKellyCalculation(
                        aiResponse,
                        originalInput.matchData,
                        originalInput.matchData.getImpliedProbabilities()
                    )

                    results.add(
                        BetAnalysisResult(
                            matchData = originalInput.matchData,
                            impliedProbabilities = originalInput.matchData.getImpliedProbabilities(),
                            aiAnalysis = aiResponse
                        )
                    )
                } catch (e: Exception) {
                    failures.add(analysis.eventId to "Failed to process: ${e.message}")
                }
            }

            // Check for matches that weren't in the response
            for (input in originalMatches) {
                if (results.none { it.matchData.eventId == input.matchData.eventId } &&
                    failures.none { it.first == input.matchData.eventId }) {
                    failures.add(input.matchData.eventId to "No analysis returned for this match")
                }
            }

            when {
                failures.isEmpty() -> {
                    val valueBetsCount = results.count { it.aiAnalysis.isValueBet }
                    BulkAnalysisResult.Success(
                        results = results,
                        totalMatches = originalMatches.size,
                        valueBetsFound = valueBetsCount
                    )
                }
                results.isNotEmpty() -> {
                    BulkAnalysisResult.PartialSuccess(
                        results = results,
                        failures = failures
                    )
                }
                else -> {
                    BulkAnalysisResult.Error("All matches failed to analyze")
                }
            }
        } catch (e: JsonSyntaxException) {
            BulkAnalysisResult.Error("Invalid JSON array syntax: ${e.message}", e)
        }
    }

    /**
     * Convenience method to analyze a slate from MatchData/RecentStats pairs.
     * Use this when you have legacy Pair-based data structure.
     */
    suspend fun analyzeUpcomingSlatePairs(
        matches: List<Pair<MatchData, RecentStats>>
    ): BulkAnalysisResult {
        val slateInputs = matches.map { (matchData, stats) ->
            SlateMatchInput(matchData, stats)
        }
        return analyzeUpcomingSlate(slateInputs)
    }

    /**
     * Get a summary of the slate analysis results.
     */
    fun getSlateAnalysisSummary(results: List<BetAnalysisResult>): SlateAnalysisSummary {
        return SlateAnalysisSummary.fromResults(results)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // HAIKU-POWERED FUNCTIONS (Low-cost routine tasks)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Determine bet settlement from match scores using HAIKU (low cost).
     *
     * Use this for background settlement tasks instead of manual logic.
     * Cost: ~90% cheaper than using Sonnet.
     *
     * @param homeTeam Name of home team
     * @param awayTeam Name of away team
     * @param homeScore Final score for home team
     * @param awayScore Final score for away team
     * @param userSelection User's bet selection ("HOME", "AWAY", "DRAW")
     * @return SettlementResult with determination
     */
    suspend fun settleMatchResult(
        homeTeam: String,
        awayTeam: String,
        homeScore: Int,
        awayScore: Int,
        userSelection: String
    ): SettlementResult = withContext(Dispatchers.IO) {
        try {
            val userPrompt = buildString {
                appendLine("MATCH SETTLEMENT REQUEST")
                appendLine("========================")
                appendLine("Home Team: $homeTeam")
                appendLine("Away Team: $awayTeam")
                appendLine("Final Score: $homeScore - $awayScore")
                appendLine("User's Bet Selection: $userSelection")
                appendLine()
                appendLine("Determine the bet outcome.")
            }

            val request = MessageRequest(
                model = getModelForTask(AITaskType.RESULT_SETTLEMENT),
                maxTokens = getMaxTokensForTask(AITaskType.RESULT_SETTLEMENT),
                system = settlementSystemPrompt,
                messages = listOf(Message(role = "user", content = userPrompt))
            )

            val response = api.createMessage(request = request)

            if (response.isSuccessful) {
                val content = response.body()?.getTextContent() ?: ""
                parseSettlementResponse(content)
            } else {
                SettlementResult.Error("API Error: ${response.code()}")
            }
        } catch (e: Exception) {
            SettlementResult.Error("Settlement failed: ${e.message}")
        }
    }

    /**
     * Parse settlement response from Haiku.
     */
    private fun parseSettlementResponse(content: String): SettlementResult {
        return try {
            val cleanedContent = content
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonStart = cleanedContent.indexOf('{')
            val jsonEnd = cleanedContent.lastIndexOf('}')

            if (jsonStart == -1 || jsonEnd == -1) {
                return SettlementResult.Error("Invalid response format")
            }

            val jsonString = cleanedContent.substring(jsonStart, jsonEnd + 1)
            val parsed = gson.fromJson(jsonString, SettlementResponse::class.java)

            SettlementResult.Success(
                matchCompleted = parsed.matchCompleted,
                homeScore = parsed.homeScore,
                awayScore = parsed.awayScore,
                actualWinner = parsed.actualWinner,
                userSelection = parsed.userSelection,
                betResult = parsed.betResult,
                reason = parsed.settlementReason
            )
        } catch (e: Exception) {
            SettlementResult.Error("Failed to parse settlement: ${e.message}")
        }
    }

    /**
     * Format raw API data using HAIKU (low cost).
     *
     * Use this for transforming/cleaning API responses.
     *
     * @param rawData The raw data to format
     * @param targetFormat Description of desired output format
     * @return Formatted data string
     */
    suspend fun formatData(
        rawData: String,
        targetFormat: String
    ): DataFormatResult = withContext(Dispatchers.IO) {
        try {
            val userPrompt = buildString {
                appendLine("FORMAT REQUEST")
                appendLine("==============")
                appendLine("Target Format: $targetFormat")
                appendLine()
                appendLine("Raw Data:")
                appendLine(rawData)
            }

            val request = MessageRequest(
                model = getModelForTask(AITaskType.DATA_FORMATTING),
                maxTokens = getMaxTokensForTask(AITaskType.DATA_FORMATTING),
                system = dataFormattingSystemPrompt,
                messages = listOf(Message(role = "user", content = userPrompt))
            )

            val response = api.createMessage(request = request)

            if (response.isSuccessful) {
                val content = response.body()?.getTextContent() ?: ""
                DataFormatResult.Success(content.trim())
            } else {
                DataFormatResult.Error("API Error: ${response.code()}")
            }
        } catch (e: Exception) {
            DataFormatResult.Error("Formatting failed: ${e.message}")
        }
    }

    /**
     * Quick query using HAIKU (low cost).
     *
     * Use for simple lookups or calculations.
     *
     * @param query The question to answer
     * @return Quick answer string
     */
    suspend fun quickQuery(query: String): String = withContext(Dispatchers.IO) {
        try {
            val request = MessageRequest(
                model = getModelForTask(AITaskType.SIMPLE_QUERY),
                maxTokens = getMaxTokensForTask(AITaskType.SIMPLE_QUERY),
                system = simpleQuerySystemPrompt,
                messages = listOf(Message(role = "user", content = query))
            )

            val response = api.createMessage(request = request)

            if (response.isSuccessful) {
                response.body()?.getTextContent()?.trim() ?: "No response"
            } else {
                "Error: ${response.code()}"
            }
        } catch (e: Exception) {
            "Query failed: ${e.message}"
        }
    }

    /**
     * Get the model being used for a specific task type.
     * Useful for logging and debugging.
     */
    fun getModelName(taskType: AITaskType): String {
        return when (getModelForTask(taskType)) {
            AnthropicApi.MODEL_SONNET -> "Claude Sonnet (Premium)"
            AnthropicApi.MODEL_HAIKU -> "Claude Haiku (Budget)"
            else -> "Unknown"
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HAIKU RESPONSE MODELS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Settlement response from Haiku.
 */
data class SettlementResponse(
    @com.google.gson.annotations.SerializedName("match_completed")
    val matchCompleted: Boolean,
    @com.google.gson.annotations.SerializedName("home_score")
    val homeScore: Int,
    @com.google.gson.annotations.SerializedName("away_score")
    val awayScore: Int,
    @com.google.gson.annotations.SerializedName("actual_winner")
    val actualWinner: String,
    @com.google.gson.annotations.SerializedName("user_selection")
    val userSelection: String,
    @com.google.gson.annotations.SerializedName("bet_result")
    val betResult: String,
    @com.google.gson.annotations.SerializedName("settlement_reason")
    val settlementReason: String
)

/**
 * Settlement result sealed class.
 */
sealed class SettlementResult {
    data class Success(
        val matchCompleted: Boolean,
        val homeScore: Int,
        val awayScore: Int,
        val actualWinner: String,
        val userSelection: String,
        val betResult: String,  // "WON", "LOST", "PUSH", "PENDING"
        val reason: String
    ) : SettlementResult()

    data class Error(val message: String) : SettlementResult()
}

/**
 * Data format result sealed class.
 */
sealed class DataFormatResult {
    data class Success(val formattedData: String) : DataFormatResult()
    data class Error(val message: String) : DataFormatResult()
}

/**
 * Exception for malformed AI responses.
 */
class MalformedResponseException(message: String) : Exception(message)
