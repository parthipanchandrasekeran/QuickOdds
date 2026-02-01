package com.quickodds.app.ai

import com.quickodds.app.ai.model.*
import com.quickodds.app.data.remote.dto.OddsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository for AI-powered betting analysis.
 * Bridges the API data with the AI analysis service.
 */
class AIAnalysisRepository(
    private val analysisService: AIAnalysisService
) {

    /**
     * Analyze a match from OddsEvent data.
     */
    suspend fun analyzeFromOddsEvent(
        event: OddsEvent,
        stats: RecentStats
    ): AnalysisResult {
        // Get best odds from first bookmaker (or implement best odds selection)
        val bookmaker = event.bookmakers.firstOrNull()
            ?: return AnalysisResult.Error("No bookmaker odds available")

        val h2hMarket = bookmaker.markets.find { it.key == "h2h" }
            ?: return AnalysisResult.Error("No h2h market available")

        val outcomes = h2hMarket.outcomes
        if (outcomes.size < 2) {
            return AnalysisResult.Error("Insufficient outcomes in market")
        }

        // Map outcomes to odds (handle both 2-way and 3-way markets)
        val homeOdds = outcomes.find { it.name == event.homeTeam }?.price
            ?: outcomes.getOrNull(0)?.price
            ?: return AnalysisResult.Error("Cannot determine home odds")

        val awayOdds = outcomes.find { it.name == event.awayTeam }?.price
            ?: outcomes.getOrNull(1)?.price
            ?: return AnalysisResult.Error("Cannot determine away odds")

        val drawOdds = outcomes.find { it.name.lowercase() == "draw" }?.price

        val matchData = MatchData(
            eventId = event.id,
            homeTeam = event.homeTeam,
            awayTeam = event.awayTeam,
            homeOdds = homeOdds,
            drawOdds = drawOdds,
            awayOdds = awayOdds,
            league = event.sportTitle,
            commenceTime = event.commenceTime
        )

        return analysisService.analyzeMatch(matchData, stats)
    }

    /**
     * Analyze match with manual odds input.
     */
    suspend fun analyze(
        homeTeam: String,
        awayTeam: String,
        homeOdds: Double,
        drawOdds: Double?,
        awayOdds: Double,
        league: String,
        stats: RecentStats
    ): AnalysisResult {
        val matchData = MatchData(
            eventId = "${homeTeam}_${awayTeam}_${System.currentTimeMillis()}",
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeOdds = homeOdds,
            drawOdds = drawOdds,
            awayOdds = awayOdds,
            league = league,
            commenceTime = java.time.Instant.now().toString()
        )

        return analysisService.analyzeMatch(matchData, stats)
    }

    /**
     * Analyze multiple matches and emit results as a Flow.
     */
    fun analyzeMatchesFlow(
        matches: List<Pair<MatchData, RecentStats>>
    ): Flow<Pair<MatchData, AnalysisResult>> = flow {
        for ((matchData, stats) in matches) {
            val result = analysisService.analyzeMatch(matchData, stats)
            emit(matchData to result)
        }
    }

    /**
     * Quick analysis with minimal stats (for rapid scanning).
     */
    suspend fun quickAnalyze(
        matchData: MatchData,
        homeForm: String,
        awayForm: String
    ): AnalysisResult {
        val stats = RecentStats(
            homeTeamForm = homeForm,
            awayTeamForm = awayForm
        )
        return analysisService.analyzeMatch(matchData, stats)
    }
}
