package com.quickodds.app.ui.screens.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.ai.AIAnalysisService
import com.quickodds.app.ai.model.*
import com.quickodds.app.data.repository.MarketRepository
import com.quickodds.app.domain.model.Market
import com.quickodds.app.domain.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Analysis state for a single market.
 */
data class MarketAnalysisState(
    val marketId: String,
    val isAnalyzing: Boolean = false,
    val result: BetAnalysisResult? = null,
    val error: String? = null,
    val analyzedAt: Long? = null
)

data class MarketsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sports: List<Sport> = emptyList(),
    val selectedSport: Sport? = null,
    val markets: List<Market> = emptyList(),
    val error: String? = null,
    val isCacheStale: Boolean = false,
    // Last updated timestamp
    val lastUpdated: Long? = null,
    // Bulk analysis state
    val isScanningAll: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val bulkAnalysisResults: Map<String, MarketAnalysisState> = emptyMap(),
    val lastScanCompleted: Long? = null,
    val valueBetsFound: Int = 0
)

/**
 * ViewModel for Markets screen with cache-first strategy.
 *
 * Caching behavior:
 * - Shows cached data immediately when switching screens
 * - No new API call if cache is fresh (<30 minutes)
 * - HTTP cache: 15-minute max-age prevents redundant network calls
 * - Pull-to-refresh forces a network fetch
 *
 * AI Analysis:
 * - Manual trigger only - no automatic analysis
 * - "Analyze" button for individual matches
 * - "Scan All" button for bulk slate analysis
 * - Last Updated timestamp for data freshness
 */
@HiltViewModel
class MarketsViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val aiAnalysisService: AIAnalysisService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketsUiState())
    val uiState: StateFlow<MarketsUiState> = _uiState.asStateFlow()

    init {
        loadSports()
    }

    private fun loadSports() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            marketRepository.getSports().onSuccess { sports ->
                _uiState.update {
                    it.copy(
                        sports = sports,
                        selectedSport = sports.firstOrNull(),
                        isLoading = false
                    )
                }
                sports.firstOrNull()?.let { loadMarkets(it.id) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message)
                }
            }
        }
    }

    fun selectSport(sport: Sport) {
        _uiState.update { it.copy(selectedSport = sport) }
        loadMarkets(sport.id)
    }

    /**
     * Load markets with cache-first strategy.
     * Returns cached data immediately if available.
     * Fetches from network only if cache is stale (>30 minutes).
     */
    private fun loadMarkets(sportId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Check if cache is stale
            val isCacheStale = marketRepository.isCacheStale(sportId)

            marketRepository.getMarkets(sportId).onSuccess { markets ->
                _uiState.update {
                    it.copy(
                        markets = markets,
                        isLoading = false,
                        isCacheStale = isCacheStale,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message)
                }
            }
        }
    }

    /**
     * Force refresh markets from network (bypasses cache).
     * Use for pull-to-refresh functionality.
     */
    fun refreshMarkets() {
        val sportId = _uiState.value.selectedSport?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            marketRepository.refreshMarkets(sportId).onSuccess { markets ->
                _uiState.update {
                    it.copy(
                        markets = markets,
                        isRefreshing = false,
                        isCacheStale = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isRefreshing = false, error = error.message)
                }
            }
        }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // AI ANALYSIS - MANUAL TRIGGER ONLY
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Analyze a single match (Manual trigger).
     * User must click "Analyze" button to trigger this.
     */
    fun analyzeMatch(market: Market) {
        viewModelScope.launch {
            // Update state to show analyzing
            _uiState.update { state ->
                val updatedResults = state.bulkAnalysisResults.toMutableMap()
                updatedResults[market.id] = MarketAnalysisState(
                    marketId = market.id,
                    isAnalyzing = true
                )
                state.copy(bulkAnalysisResults = updatedResults)
            }

            // Create match data for analysis
            val matchData = market.toMatchData()
            val recentStats = RecentStats() // TODO: Fetch actual stats

            val result = aiAnalysisService.analyzeMatch(matchData, recentStats)

            // Update state with result
            _uiState.update { state ->
                val updatedResults = state.bulkAnalysisResults.toMutableMap()
                when (result) {
                    is AnalysisResult.Success -> {
                        updatedResults[market.id] = MarketAnalysisState(
                            marketId = market.id,
                            isAnalyzing = false,
                            result = result.result,
                            analyzedAt = System.currentTimeMillis()
                        )
                    }
                    is AnalysisResult.Error -> {
                        updatedResults[market.id] = MarketAnalysisState(
                            marketId = market.id,
                            isAnalyzing = false,
                            error = result.message
                        )
                    }
                    else -> {}
                }
                state.copy(bulkAnalysisResults = updatedResults)
            }
        }
    }

    /**
     * Scan all matches in the current slate (Bulk Analysis).
     * User must click "Scan All" button to trigger this.
     * Uses single API call for efficiency.
     */
    fun scanAllMatches() {
        val markets = _uiState.value.markets
        if (markets.isEmpty()) return

        viewModelScope.launch {
            // Update state to show scanning
            _uiState.update {
                it.copy(
                    isScanningAll = true,
                    scanProgress = 0,
                    scanTotal = markets.size,
                    error = null
                )
            }

            // Convert markets to SlateMatchInput
            val slateInputs = markets.map { market ->
                SlateMatchInput(
                    matchData = market.toMatchData(),
                    recentStats = RecentStats() // TODO: Fetch actual stats
                )
            }

            // Perform bulk analysis
            val result = aiAnalysisService.analyzeUpcomingSlate(slateInputs)

            // Update state with results
            _uiState.update { state ->
                when (result) {
                    is BulkAnalysisResult.Success -> {
                        val updatedResults = state.bulkAnalysisResults.toMutableMap()
                        result.results.forEach { analysisResult ->
                            updatedResults[analysisResult.matchData.eventId] = MarketAnalysisState(
                                marketId = analysisResult.matchData.eventId,
                                isAnalyzing = false,
                                result = analysisResult,
                                analyzedAt = System.currentTimeMillis()
                            )
                        }
                        state.copy(
                            isScanningAll = false,
                            scanProgress = result.totalMatches,
                            bulkAnalysisResults = updatedResults,
                            lastScanCompleted = System.currentTimeMillis(),
                            valueBetsFound = result.valueBetsFound
                        )
                    }
                    is BulkAnalysisResult.PartialSuccess -> {
                        val updatedResults = state.bulkAnalysisResults.toMutableMap()
                        result.results.forEach { analysisResult ->
                            updatedResults[analysisResult.matchData.eventId] = MarketAnalysisState(
                                marketId = analysisResult.matchData.eventId,
                                isAnalyzing = false,
                                result = analysisResult,
                                analyzedAt = System.currentTimeMillis()
                            )
                        }
                        result.failures.forEach { (eventId, errorMsg) ->
                            updatedResults[eventId] = MarketAnalysisState(
                                marketId = eventId,
                                isAnalyzing = false,
                                error = errorMsg
                            )
                        }
                        val valueBets = result.results.count { it.aiAnalysis.isValueBet }
                        state.copy(
                            isScanningAll = false,
                            bulkAnalysisResults = updatedResults,
                            lastScanCompleted = System.currentTimeMillis(),
                            valueBetsFound = valueBets,
                            error = "Some matches failed to analyze"
                        )
                    }
                    is BulkAnalysisResult.Error -> {
                        state.copy(
                            isScanningAll = false,
                            error = result.message
                        )
                    }
                    else -> state.copy(isScanningAll = false)
                }
            }
        }
    }

    /**
     * Get analysis result for a specific market.
     */
    fun getAnalysisState(marketId: String): MarketAnalysisState? {
        return _uiState.value.bulkAnalysisResults[marketId]
    }

    /**
     * Clear all analysis results.
     */
    fun clearAnalysisResults() {
        _uiState.update {
            it.copy(
                bulkAnalysisResults = emptyMap(),
                lastScanCompleted = null,
                valueBetsFound = 0
            )
        }
    }

    /**
     * Format last updated time for display.
     */
    fun formatLastUpdated(timestamp: Long?): String {
        if (timestamp == null) return "Never"
        val now = System.currentTimeMillis()
        val diffMinutes = (now - timestamp) / (1000 * 60)
        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "$diffMinutes min ago"
            else -> {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                "at ${formatter.format(Date(timestamp))}"
            }
        }
    }
}

/**
 * Extension to convert Market domain model to MatchData for AI analysis.
 */
private fun Market.toMatchData(): MatchData {
    return MatchData(
        eventId = id,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        homeOdds = odds.home,
        drawOdds = if (odds.draw > 0) odds.draw else null,
        awayOdds = odds.away,
        league = league,
        commenceTime = java.time.Instant.ofEpochMilli(startTime).toString()
    )
}
