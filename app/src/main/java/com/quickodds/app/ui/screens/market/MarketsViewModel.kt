package com.quickodds.app.ui.screens.market

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.ai.AIAnalysisService
import com.quickodds.app.ai.model.*
import com.quickodds.app.data.local.dao.CachedAnalysisDao
import com.quickodds.app.data.local.entity.CachedAnalysisEntity
import com.quickodds.app.billing.BillingRepository
import com.quickodds.app.data.repository.MarketRepository
import com.quickodds.app.data.repository.UsageLimitRepository
import com.quickodds.app.domain.model.Market
import com.quickodds.app.domain.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
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
    val valueBetsFound: Int = 0,
    val showPaywall: Boolean = false,
    val remainingScans: Int = 1,
    val remainingAnalyzes: Int = 3
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
 * - "Scan All" button for progressive analysis (results appear one by one)
 * - Results cached in Room and restored on screen revisit
 */
@HiltViewModel
class MarketsViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val aiAnalysisService: AIAnalysisService,
    private val cachedAnalysisDao: CachedAnalysisDao,
    private val usageLimitRepository: UsageLimitRepository,
    val billingRepository: BillingRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MarketsViewModel"
    }

    private val _uiState = MutableStateFlow(MarketsUiState())
    val uiState: StateFlow<MarketsUiState> = _uiState.asStateFlow()

    init {
        loadSports()
        refreshUsageCounts()
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
     * After loading, restores any cached analysis results so they survive tab switches.
     */
    private fun loadMarkets(sportId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val isCacheStale = marketRepository.isCacheStale(sportId)

            marketRepository.getMarkets(sportId).onSuccess { markets ->
                val todayOnly = markets.filter { isTodayMatch(it.startTime) }
                _uiState.update {
                    it.copy(
                        markets = todayOnly,
                        isLoading = false,
                        isCacheStale = isCacheStale,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                // Restore cached analysis results for these markets
                restoreCachedAnalysis(todayOnly)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message)
                }
            }
        }
    }

    /**
     * Restore cached analysis results from Room DB.
     * This ensures results survive tab switches without re-calling the AI API.
     */
    private suspend fun restoreCachedAnalysis(markets: List<Market>) {
        if (markets.isEmpty()) return
        try {
            val ids = markets.map { it.id }
            val cached = cachedAnalysisDao.getAnalysesByIds(ids)
            if (cached.isEmpty()) return

            val restoredResults = mutableMapOf<String, MarketAnalysisState>()
            var valueBets = 0

            for (entity in cached) {
                if (entity.isStale()) continue
                val aiResponse = entity.toAnalysis() ?: continue
                val market = markets.find { it.id == entity.eventId } ?: continue

                val betResult = BetAnalysisResult(
                    matchData = market.toMatchData(),
                    impliedProbabilities = calculateImpliedProbabilities(market),
                    aiAnalysis = aiResponse,
                    analysisTimestamp = entity.cachedAt
                )
                restoredResults[entity.eventId] = MarketAnalysisState(
                    marketId = entity.eventId,
                    result = betResult,
                    analyzedAt = entity.cachedAt
                )
                if (aiResponse.isValueBet) valueBets++
            }

            if (restoredResults.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        bulkAnalysisResults = state.bulkAnalysisResults + restoredResults,
                        valueBetsFound = valueBets,
                        lastScanCompleted = restoredResults.values.maxOfOrNull { it.analyzedAt ?: 0L }
                    )
                }
                Log.d(TAG, "Restored ${restoredResults.size} cached analyses ($valueBets value bets)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore cached analysis", e)
        }
    }

    /**
     * Force refresh markets from network (bypasses cache).
     */
    fun refreshMarkets() {
        val sportId = _uiState.value.selectedSport?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            marketRepository.refreshMarkets(sportId).onSuccess { markets ->
                val todayOnly = markets.filter { isTodayMatch(it.startTime) }
                _uiState.update {
                    it.copy(
                        markets = todayOnly,
                        isRefreshing = false,
                        isCacheStale = false
                    )
                }
                restoreCachedAnalysis(todayOnly)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isRefreshing = false, error = error.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // AI ANALYSIS - PROGRESSIVE (results appear one by one)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Analyze a single match. Checks usage limits first — shows paywall if exceeded.
     * Caches result in Room DB.
     */
    fun analyzeMatch(market: Market) {
        viewModelScope.launch {
            if (!usageLimitRepository.canAnalyze()) {
                _uiState.update { it.copy(showPaywall = true) }
                return@launch
            }
            usageLimitRepository.recordAnalyze()
            refreshUsageCounts()

            _uiState.update { state ->
                val updated = state.bulkAnalysisResults.toMutableMap()
                updated[market.id] = MarketAnalysisState(marketId = market.id, isAnalyzing = true)
                state.copy(bulkAnalysisResults = updated)
            }

            val matchData = market.toMatchData()
            val recentStats = RecentStats()

            when (val result = aiAnalysisService.analyzeMatch(matchData, recentStats)) {
                is AnalysisResult.Success -> {
                    cachedAnalysisDao.insertAnalysis(
                        CachedAnalysisEntity.fromAnalysis(market.id, result.result.aiAnalysis)
                    )
                    _uiState.update { state ->
                        val updated = state.bulkAnalysisResults.toMutableMap()
                        updated[market.id] = MarketAnalysisState(
                            marketId = market.id,
                            result = result.result,
                            analyzedAt = System.currentTimeMillis()
                        )
                        state.copy(bulkAnalysisResults = updated)
                    }
                }
                is AnalysisResult.Error -> {
                    _uiState.update { state ->
                        val updated = state.bulkAnalysisResults.toMutableMap()
                        updated[market.id] = MarketAnalysisState(
                            marketId = market.id,
                            error = result.message
                        )
                        state.copy(bulkAnalysisResults = updated)
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Scan all matches progressively — analyzes up to 3 at a time and updates
     * the UI as each result comes back so the user sees results one by one.
     * Each result is cached in Room DB.
     */
    fun scanAllMatches() {
        viewModelScope.launch {
            if (!usageLimitRepository.canScanAll()) {
                _uiState.update { it.copy(showPaywall = true) }
                return@launch
            }
            usageLimitRepository.recordScanAll()
            refreshUsageCounts()
            doScanAll()
        }
    }

    private fun doScanAll() {
        val markets = _uiState.value.markets
        val existing = _uiState.value.bulkAnalysisResults
        val toAnalyze = markets.filter { market ->
            existing[market.id]?.result == null && existing[market.id]?.isAnalyzing != true
        }
        if (toAnalyze.isEmpty()) return

        _uiState.update {
            it.copy(isScanningAll = true, scanProgress = 0, scanTotal = toAnalyze.size, error = null)
        }

        viewModelScope.launch {
            val semaphore = Semaphore(3)
            val completed = AtomicInteger(0)
            val valueBets = AtomicInteger(_uiState.value.valueBetsFound)

            val jobs = toAnalyze.map { market ->
                launch {
                    semaphore.acquire()
                    try {
                        // Mark as analyzing
                        _uiState.update { state ->
                            val updated = state.bulkAnalysisResults.toMutableMap()
                            updated[market.id] = MarketAnalysisState(marketId = market.id, isAnalyzing = true)
                            state.copy(bulkAnalysisResults = updated)
                        }

                        val matchData = market.toMatchData()
                        val recentStats = RecentStats()

                        when (val result = aiAnalysisService.analyzeMatch(matchData, recentStats)) {
                            is AnalysisResult.Success -> {
                                cachedAnalysisDao.insertAnalysis(
                                    CachedAnalysisEntity.fromAnalysis(market.id, result.result.aiAnalysis)
                                )
                                if (result.result.aiAnalysis.isValueBet) valueBets.incrementAndGet()
                                _uiState.update { state ->
                                    val updated = state.bulkAnalysisResults.toMutableMap()
                                    updated[market.id] = MarketAnalysisState(
                                        marketId = market.id,
                                        result = result.result,
                                        analyzedAt = System.currentTimeMillis()
                                    )
                                    state.copy(bulkAnalysisResults = updated)
                                }
                            }
                            is AnalysisResult.Error -> {
                                _uiState.update { state ->
                                    val updated = state.bulkAnalysisResults.toMutableMap()
                                    updated[market.id] = MarketAnalysisState(
                                        marketId = market.id,
                                        error = result.message
                                    )
                                    state.copy(bulkAnalysisResults = updated)
                                }
                            }
                            else -> {}
                        }
                    } finally {
                        semaphore.release()
                        val progress = completed.incrementAndGet()
                        _uiState.update {
                            it.copy(scanProgress = progress, valueBetsFound = valueBets.get())
                        }
                    }
                }
            }

            jobs.forEach { it.join() }

            _uiState.update {
                it.copy(
                    isScanningAll = false,
                    lastScanCompleted = System.currentTimeMillis()
                )
            }
            Log.d(TAG, "Progressive scan complete: ${toAnalyze.size} analyzed, ${valueBets.get()} value bets")
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

    private fun isTodayMatch(startTimeMillis: Long): Boolean {
        return try {
            val matchDate = java.time.Instant.ofEpochMilli(startTimeMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            matchDate == java.time.LocalDate.now()
        } catch (e: Exception) {
            false
        }
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(showPaywall = false) }
    }

    private fun refreshUsageCounts() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    remainingScans = usageLimitRepository.getRemainingScans(),
                    remainingAnalyzes = usageLimitRepository.getRemainingAnalyzes()
                )
            }
        }
    }

    private fun calculateImpliedProbabilities(market: Market): ImpliedProbabilities {
        val home = 1.0 / market.odds.home
        val draw = if (market.odds.draw > 0) 1.0 / market.odds.draw else null
        val away = 1.0 / market.odds.away
        val total = home + (draw ?: 0.0) + away
        return ImpliedProbabilities(
            home = home / total,
            draw = draw?.let { it / total },
            away = away / total,
            bookmakerMargin = total - 1.0
        )
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
