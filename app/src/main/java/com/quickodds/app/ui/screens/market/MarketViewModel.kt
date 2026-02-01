package com.quickodds.app.ui.screens.market

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.ai.AIAnalysisService
import com.quickodds.app.ai.model.*
import com.quickodds.app.data.local.dao.CachedAnalysisDao
import com.quickodds.app.data.local.dao.CachedOddsEventDao
import com.quickodds.app.data.local.dao.UserWalletDao
import com.quickodds.app.data.local.entity.CachedAnalysisEntity
import com.quickodds.app.data.local.entity.CachedOddsEventEntity
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.data.remote.api.OddsCloudFunctionService
import com.quickodds.app.data.remote.dto.OddsEvent
import com.quickodds.app.data.remote.MockOddsDataSource
import com.quickodds.app.ui.components.MatchCardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the Market Screen.
 */
data class MarketUiState(
    val isLoading: Boolean = true,
    val wallet: UserWallet? = null,
    val matches: List<OddsEvent> = emptyList(),
    val matchStates: Map<String, MatchCardState> = emptyMap(),
    val selectedSport: String = "soccer_epl",
    val availableSports: List<SportOption> = defaultSports,
    val error: String? = null,
    val isUsingLiveData: Boolean = false
) {
    companion object {
        val defaultSports = listOf(
            SportOption("soccer_epl", "Premier League"),
            SportOption("soccer_usa_mls", "MLS"),
            SportOption("americanfootball_nfl", "NFL"),
            SportOption("basketball_nba", "NBA"),
            SportOption("soccer_spain_la_liga", "La Liga"),
            SportOption("soccer_germany_bundesliga", "Bundesliga"),
            SportOption("soccer_italy_serie_a", "Serie A"),
            SportOption("soccer_france_ligue_one", "Ligue 1")
        )
    }
}

data class SportOption(
    val key: String,
    val displayName: String
)

/**
 * ViewModel for the Market Screen.
 * Handles fetching matches via Firebase Cloud Function and triggering AI analysis.
 * Implements 15-minute cache-first strategy to save API credits.
 *
 * @param walletDao DAO for accessing wallet data
 * @param cloudFunctionService Retrofit service for Firebase Cloud Function (secure proxy)
 * @param analysisService AI analysis service via Firebase Function
 * @param cachedOddsEventDao DAO for caching odds events
 */
@HiltViewModel
class MarketViewModel @Inject constructor(
    private val walletDao: UserWalletDao,
    private val cloudFunctionService: OddsCloudFunctionService,
    private val analysisService: AIAnalysisService,
    private val cachedOddsEventDao: CachedOddsEventDao,
    private val cachedAnalysisDao: CachedAnalysisDao
) : ViewModel() {

    companion object {
        private const val TAG = "MarketViewModel"
    }

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    init {
        initializeAndObserveWallet()
        loadMatches()
    }

    private fun initializeAndObserveWallet() {
        viewModelScope.launch {
            // Ensure wallet exists before observing
            val existingWallet = walletDao.getWallet()
            if (existingWallet == null) {
                Log.d(TAG, "Wallet not found, creating default wallet")
                walletDao.insertWallet(
                    UserWallet(
                        balance = 10000.0,
                        currency = "USD"
                    )
                )
            }

            // Now observe the wallet
            walletDao.observeWallet().collect { wallet ->
                _uiState.update { it.copy(wallet = wallet) }
            }
        }
    }

    /**
     * Load matches for the selected sport.
     * Implements 15-minute cache-first strategy to save API credits.
     * 1. Check if cache is fresh (< 15 min old)
     * 2. If fresh, return cached data
     * 3. If stale or empty, fetch from API
     * 4. Falls back to mock data if API fails
     */
    fun loadMatches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val sportKey = _uiState.value.selectedSport
                var matches: List<OddsEvent> = emptyList()
                var isLiveData = false
                var fromCache = false

                // Step 1: Check if cache is fresh (15-minute stale time)
                val shouldFetch = cachedOddsEventDao.shouldFetchFromApi(sportKey)
                Log.d(TAG, "Sport: $sportKey, shouldFetchFromApi: $shouldFetch")

                if (!shouldFetch) {
                    // Cache is fresh - return cached data
                    val cachedEvents = cachedOddsEventDao.getEventsBySport(sportKey)
                    matches = cachedEvents.mapNotNull { it.toOddsEvent() }

                    if (matches.isNotEmpty()) {
                        Log.d(TAG, "Returning ${matches.size} cached events for $sportKey (saving API credits)")
                        isLiveData = true  // Cached live data is still live data
                        fromCache = true
                    }
                }

                // Step 2: If cache is stale or empty, fetch from API
                if (matches.isEmpty()) {
                    Log.d(TAG, "Cache empty or stale, fetching from API for $sportKey")
                    try {
                        val response = cloudFunctionService.getUpcomingOdds(
                            sportKey = sportKey,
                            regions = "us,uk,eu",
                            markets = "h2h",  // Only request h2h market to save credits
                            oddsFormat = "decimal"
                        )

                        if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                            matches = response.body()!!
                            isLiveData = true

                            // Cache the fresh data
                            val cachedEntities = matches.map { CachedOddsEventEntity.fromOddsEvent(it) }
                            cachedOddsEventDao.refreshEvents(sportKey, cachedEntities)
                            Log.d(TAG, "Fetched and cached ${matches.size} events from API for $sportKey")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "API call failed: ${e.message}")

                        // Try to serve stale cache if available
                        val cachedEvents = cachedOddsEventDao.getEventsBySport(sportKey)
                        if (cachedEvents.isNotEmpty()) {
                            matches = cachedEvents.mapNotNull { it.toOddsEvent() }
                            isLiveData = true
                            fromCache = true
                            Log.d(TAG, "Serving stale cache (${matches.size} events) due to API error")
                        }
                    }
                }

                // Step 3: Fall back to mock data if no live data available
                if (matches.isEmpty()) {
                    matches = MockOddsDataSource.getMatches(sportKey)
                    isLiveData = false
                    Log.d(TAG, "Using mock data for $sportKey")
                }

                // Initialize match states
                val matchStates = matches.associate { it.id to MatchCardState() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        matches = matches,
                        matchStates = matchStates,
                        isUsingLiveData = isLiveData,
                        error = when {
                            !isLiveData -> "Using demo data"
                            fromCache -> "Using cached data (< 15 min old)"
                            else -> null
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading matches", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load matches: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Change the selected sport and reload matches.
     */
    fun selectSport(sportKey: String) {
        _uiState.update { it.copy(selectedSport = sportKey) }
        loadMatches()
    }

    /**
     * Trigger AI analysis for a specific match.
     * Uses cache-first strategy to avoid duplicate API calls.
     */
    fun analyzeMatch(matchId: String) {
        val match = _uiState.value.matches.find { it.id == matchId } ?: return

        // Update state to show loading
        updateMatchState(matchId) { it.copy(isAnalyzing = true, error = null) }

        viewModelScope.launch {
            try {
                // Step 1: Check cache first
                val cachedAnalysis = cachedAnalysisDao.getAnalysis(matchId)
                if (cachedAnalysis != null && !cachedAnalysis.isStale()) {
                    val analysis = cachedAnalysis.toAnalysis()
                    if (analysis != null) {
                        Log.d(TAG, "Using cached analysis for $matchId (saving API credits)")
                        updateMatchState(matchId) {
                            it.copy(
                                isAnalyzing = false,
                                isAnalyzed = true,
                                analysisResult = analysis
                            )
                        }
                        return@launch
                    }
                }

                // Step 2: No valid cache, call API
                Log.d(TAG, "No cached analysis for $matchId, calling API")
                val matchData = createMatchData(match)
                val stats = RecentStats(
                    homeTeamForm = "WWLDW",  // Would come from actual stats API
                    awayTeamForm = "LWWDL"
                )

                when (val result = analysisService.analyzeMatch(matchData, stats)) {
                    is AnalysisResult.Success -> {
                        val analysis = result.result.aiAnalysis

                        // Save to cache
                        cachedAnalysisDao.insertAnalysis(
                            CachedAnalysisEntity.fromAnalysis(matchId, analysis)
                        )
                        Log.d(TAG, "Cached analysis for $matchId")

                        updateMatchState(matchId) {
                            it.copy(
                                isAnalyzing = false,
                                isAnalyzed = true,
                                analysisResult = analysis
                            )
                        }
                    }
                    is AnalysisResult.Error -> {
                        updateMatchState(matchId) {
                            it.copy(
                                isAnalyzing = false,
                                error = result.message
                            )
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                updateMatchState(matchId) {
                    it.copy(
                        isAnalyzing = false,
                        error = e.message ?: "Analysis failed"
                    )
                }
            }
        }
    }

    private fun updateMatchState(matchId: String, update: (MatchCardState) -> MatchCardState) {
        _uiState.update { state ->
            val currentState = state.matchStates[matchId] ?: MatchCardState()
            state.copy(
                matchStates = state.matchStates + (matchId to update(currentState))
            )
        }
    }

    private fun createMatchData(event: OddsEvent): MatchData {
        val bookmaker = event.bookmakers.firstOrNull()
        val h2hMarket = bookmaker?.markets?.find { it.key == "h2h" }
        val outcomes = h2hMarket?.outcomes ?: emptyList()

        return MatchData(
            eventId = event.id,
            homeTeam = event.homeTeam,
            awayTeam = event.awayTeam,
            homeOdds = outcomes.find { it.name == event.homeTeam }?.price ?: 2.0,
            drawOdds = outcomes.find { it.name.lowercase() == "draw" }?.price,
            awayOdds = outcomes.find { it.name == event.awayTeam }?.price ?: 2.0,
            league = event.sportTitle,
            commenceTime = event.commenceTime
        )
    }

    /**
     * Generate demo analysis result for testing without API.
     */
    private fun generateDemoAnalysis(match: OddsEvent): AIAnalysisResponse {
        val isValue = kotlin.random.Random.nextFloat() > 0.5
        val recommendation = listOf("HOME", "AWAY", "DRAW").random()

        return AIAnalysisResponse(
            recommendation = if (isValue) recommendation else "NO_BET",
            confidenceScore = if (isValue) 0.6 + kotlin.random.Random.nextFloat() * 0.3 else 0.4,
            isValueBet = isValue,
            rationale = if (isValue) {
                "Statistical analysis indicates ${if (recommendation == "HOME") match.homeTeam else match.awayTeam} has a ${(5 + kotlin.random.Random.nextInt(10))}% edge"
            } else {
                "No significant edge found - odds are fairly priced"
            },
            projectedProbability = 0.45 + kotlin.random.Random.nextFloat() * 0.2,
            edgePercentage = if (isValue) 5.0 + kotlin.random.Random.nextDouble() * 10 else -2.0,
            suggestedStake = if (isValue) kotlin.random.Random.nextInt(1, 4) else 0
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
