package com.quickodds.app.ui.screens.market

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.ai.AIAnalysisService
import com.quickodds.app.ai.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.quickodds.app.data.local.dao.CachedAnalysisDao
import com.quickodds.app.data.local.dao.CachedOddsEventDao
import com.quickodds.app.data.local.dao.UserWalletDao
import com.quickodds.app.data.local.entity.CachedAnalysisEntity
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.data.local.entity.CachedOddsEventEntity
import com.quickodds.app.data.remote.api.OddsCloudFunctionService
import com.quickodds.app.data.remote.dto.OddsEvent
import com.quickodds.app.data.remote.MockOddsDataSource
import com.quickodds.app.data.repository.TeamFormRepository
import com.quickodds.app.ui.components.MatchCardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UI State for the Market Screen.
 */
data class MarketUiState(
    val isLoading: Boolean = true,
    val wallet: UserWallet? = null,
    val matches: List<OddsEvent> = emptyList(),
    val matchStates: Map<String, MatchCardState> = emptyMap(),
    val selectedSport: String = "basketball_nba",
    val availableSports: List<SportOption> = defaultSports,
    val error: String? = null,
    val isUsingLiveData: Boolean = false,
    val isAnalyzingAll: Boolean = false,
    val isScanningAll: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val lastScanCompleted: Long? = null,
    val valueBetsFound: Int = 0
) {
    companion object {
        val defaultSports = listOf(
            SportOption("basketball_nba", "NBA"),
            SportOption("soccer_epl", "Premier League"),
            SportOption("soccer_usa_mls", "MLS"),
            SportOption("americanfootball_nfl", "NFL"),
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
    private val cachedAnalysisDao: CachedAnalysisDao,
    private val teamFormRepository: TeamFormRepository
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
            // Wallet is initialized in QuickOddsApp - just observe it
            walletDao.observeWallet().collect { wallet ->
                _uiState.update { it.copy(wallet = wallet) }
            }
        }
    }

    /**
     * Load matches for the selected sport.
     * Shows cached/mock data instantly, then refreshes from API in the background.
     */
    fun loadMatches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val sportKey = _uiState.value.selectedSport

                // Step 1: Show cached or mock data immediately
                val immediateData = withContext(Dispatchers.IO) {
                    val cachedEvents = cachedOddsEventDao.getEventsBySport(sportKey)
                    val cached = cachedEvents.mapNotNull { it.toOddsEvent() }
                    if (cached.isNotEmpty()) cached else null
                }

                if (immediateData != null) {
                    val sorted = immediateData.sortedBy { it.commenceTime }
                    val matchStates = preserveMatchStates(sorted)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            matches = sorted,
                            matchStates = matchStates,
                            isUsingLiveData = true,
                            error = null
                        )
                    }
                    Log.d(TAG, "Showing ${sorted.size} cached events for $sportKey instantly")
                } else {
                    // No cache — show mock data instantly
                    val mock = withContext(Dispatchers.IO) {
                        MockOddsDataSource.getMatches(sportKey).sortedBy { it.commenceTime }
                    }
                    val matchStates = preserveMatchStates(mock)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            matches = mock,
                            matchStates = matchStates,
                            isUsingLiveData = false,
                            error = "Using demo data"
                        )
                    }
                    Log.d(TAG, "Showing mock data for $sportKey instantly")
                }

                // Step 2: Check if API refresh is needed, do it in background
                val shouldFetch = withContext(Dispatchers.IO) {
                    cachedOddsEventDao.shouldFetchFromApi(sportKey)
                }

                if (shouldFetch) {
                    refreshFromApi(sportKey)
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
     * Refresh data from API in the background without blocking the UI.
     */
    private suspend fun refreshFromApi(sportKey: String) {
        try {
            Log.d(TAG, "Background refresh from API for $sportKey")
            val response = withContext(Dispatchers.IO) {
                cloudFunctionService.getUpcomingOdds(
                    sportKey = sportKey,
                    regions = "us,uk,eu",
                    markets = "h2h",
                    oddsFormat = "decimal"
                )
            }

            if (response.isSuccessful) {
                val body = response.body().orEmpty()
                if (body.isNotEmpty()) {
                    val freshMatches = body.sortedBy { it.commenceTime }

                    // Cache the fresh data
                    withContext(Dispatchers.IO) {
                        val cachedEntities = freshMatches.map { CachedOddsEventEntity.fromOddsEvent(it) }
                        cachedOddsEventDao.refreshEvents(sportKey, cachedEntities)
                    }
                    Log.d(TAG, "Fetched and cached ${freshMatches.size} events from API for $sportKey")

                    // Update UI with fresh data, preserving any existing analysis states
                    val matchStates = preserveMatchStates(freshMatches)
                    _uiState.update {
                        it.copy(
                            matches = freshMatches,
                            matchStates = matchStates,
                            isUsingLiveData = true,
                            error = null
                        )
                    }
                } else {
                    // API returned empty — off-season or no upcoming matches
                    Log.d(TAG, "API returned empty for $sportKey (off-season or no upcoming matches)")
                    _uiState.update {
                        it.copy(error = "No upcoming matches \u2014 this league may be off-season")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background API refresh failed: ${e.message}")
            // Don't update UI error — we already have data showing
        }
    }

    /**
     * Preserve existing match analysis states when updating the match list.
     */
    private fun preserveMatchStates(matches: List<OddsEvent>): Map<String, MatchCardState> {
        val existing = _uiState.value.matchStates
        return matches.associate { it.id to (existing[it.id] ?: MatchCardState()) }
    }

    /**
     * Change the selected sport and reload matches.
     */
    fun selectSport(sportKey: String) {
        _uiState.update { it.copy(selectedSport = sportKey) }
        loadMatches()
    }

    /**
     * Trigger AI analysis for a specific match (fire-and-forget from UI).
     */
    fun analyzeMatch(matchId: String) {
        viewModelScope.launch {
            performAnalysis(matchId)
        }
    }

    /**
     * Core analysis logic as a suspend function.
     * Uses cache-first strategy to avoid duplicate API calls.
     */
    private suspend fun performAnalysis(matchId: String) {
        val match = _uiState.value.matches.find { it.id == matchId } ?: return

        // Update state to show loading
        updateMatchState(matchId) { it.copy(isAnalyzing = true, error = null) }

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
                    return
                }
            }

            // Step 2: No valid cache, call API
            Log.d(TAG, "No cached analysis for $matchId, calling API")
            val matchData = createMatchData(match)

            // Fetch real form data from scores API (graceful fallback to empty)
            val (homeForm, awayForm) = teamFormRepository.getMatchFormData(
                sportKey = match.sportKey,
                homeTeam = match.homeTeam,
                awayTeam = match.awayTeam
            )
            val stats = RecentStats(
                homeTeamForm = homeForm?.formString,
                awayTeamForm = awayForm?.formString,
                homeGoalsScored = homeForm?.goalsScored,
                homeGoalsConceded = homeForm?.goalsConceded,
                awayGoalsScored = awayForm?.goalsScored,
                awayGoalsConceded = awayForm?.goalsConceded
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

    private fun updateMatchState(matchId: String, update: (MatchCardState) -> MatchCardState) {
        _uiState.update { state ->
            val currentState = state.matchStates[matchId] ?: MatchCardState()
            state.copy(
                matchStates = state.matchStates + (matchId to update(currentState))
            )
        }
    }

    private fun createMatchData(event: OddsEvent): MatchData {
        // Aggregate odds across ALL bookmakers
        val homeOddsList = mutableListOf<Pair<String, Double>>() // bookmaker to odds
        val drawOddsList = mutableListOf<Pair<String, Double>>()
        val awayOddsList = mutableListOf<Pair<String, Double>>()

        for (bookmaker in event.bookmakers) {
            val h2h = bookmaker.markets.find { it.key == "h2h" } ?: continue
            for (outcome in h2h.outcomes) {
                when {
                    outcome.name == event.homeTeam -> homeOddsList.add(bookmaker.title to outcome.price)
                    outcome.name == event.awayTeam -> awayOddsList.add(bookmaker.title to outcome.price)
                    outcome.name.lowercase() == "draw" -> drawOddsList.add(bookmaker.title to outcome.price)
                }
            }
        }

        // Best odds = highest price (best value for bettor)
        val bestHome = homeOddsList.maxByOrNull { it.second }
        val bestDraw = drawOddsList.maxByOrNull { it.second }
        val bestAway = awayOddsList.maxByOrNull { it.second }

        return MatchData(
            eventId = event.id,
            homeTeam = event.homeTeam,
            awayTeam = event.awayTeam,
            homeOdds = bestHome?.second ?: 2.0,
            drawOdds = bestDraw?.second,
            awayOdds = bestAway?.second ?: 2.0,
            league = event.sportTitle,
            commenceTime = event.commenceTime,
            bestHomeBookmaker = bestHome?.first,
            bestDrawBookmaker = bestDraw?.first,
            bestAwayBookmaker = bestAway?.first,
            avgHomeOdds = homeOddsList.map { it.second }.average().takeIf { homeOddsList.isNotEmpty() },
            avgDrawOdds = drawOddsList.map { it.second }.average().takeIf { drawOddsList.isNotEmpty() },
            avgAwayOdds = awayOddsList.map { it.second }.average().takeIf { awayOddsList.isNotEmpty() },
            minHomeOdds = homeOddsList.minOfOrNull { it.second },
            maxHomeOdds = homeOddsList.maxOfOrNull { it.second },
            minAwayOdds = awayOddsList.minOfOrNull { it.second },
            maxAwayOdds = awayOddsList.maxOfOrNull { it.second },
            bookmakerCount = event.bookmakers.size
        )
    }

    /**
     * Analyze all matches that are scheduled for today.
     * Skips matches that are already analyzed or currently being analyzed.
     */
    fun analyzeAllToday() {
        val state = _uiState.value
        val todayMatches = state.matches.filter { match ->
            isTodayMatch(match.commenceTime) &&
                state.matchStates[match.id]?.isAnalyzed != true &&
                state.matchStates[match.id]?.isAnalyzing != true
        }

        if (todayMatches.isEmpty()) return

        _uiState.update { it.copy(isAnalyzingAll = true) }

        viewModelScope.launch {
            // Analyze sequentially to avoid API rate limits
            for (match in todayMatches) {
                performAnalysis(match.id)
            }
            _uiState.update { it.copy(isAnalyzingAll = false) }
        }
    }

    private fun isTodayMatch(commenceTime: String): Boolean {
        return try {
            val matchInstant = java.time.Instant.parse(commenceTime)
            val matchDate = matchInstant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val today = java.time.LocalDate.now()
            matchDate == today
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Scan all currently loaded matches using bulk slate analysis (single API call).
     */
    fun scanAllMatches() {
        val matches = _uiState.value.matches
        if (matches.isEmpty()) return

        _uiState.update { it.copy(isScanningAll = true, scanProgress = 0, scanTotal = matches.size) }

        viewModelScope.launch {
            try {
                // Build SlateMatchInput list with real form data
                val slateInputs = matches.map { match ->
                    val matchData = createMatchData(match)
                    val (homeForm, awayForm) = teamFormRepository.getMatchFormData(
                        sportKey = match.sportKey,
                        homeTeam = match.homeTeam,
                        awayTeam = match.awayTeam
                    )
                    val stats = RecentStats(
                        homeTeamForm = homeForm?.formString,
                        awayTeamForm = awayForm?.formString,
                        homeGoalsScored = homeForm?.goalsScored,
                        homeGoalsConceded = homeForm?.goalsConceded,
                        awayGoalsScored = awayForm?.goalsScored,
                        awayGoalsConceded = awayForm?.goalsConceded
                    )
                    SlateMatchInput(matchData, stats)
                }

                _uiState.update { it.copy(scanProgress = 1) }

                when (val result = analysisService.analyzeUpcomingSlate(slateInputs)) {
                    is BulkAnalysisResult.Success -> {
                        var valueBets = 0
                        for (betResult in result.results) {
                            val eventId = betResult.matchData.eventId
                            val analysis = betResult.aiAnalysis
                            if (analysis.isValueBet) valueBets++

                            // Cache each result
                            cachedAnalysisDao.insertAnalysis(
                                CachedAnalysisEntity.fromAnalysis(eventId, analysis)
                            )

                            // Update match card state
                            updateMatchState(eventId) {
                                it.copy(isAnalyzed = true, analysisResult = analysis)
                            }
                        }
                        _uiState.update {
                            it.copy(
                                isScanningAll = false,
                                scanProgress = matches.size,
                                lastScanCompleted = System.currentTimeMillis(),
                                valueBetsFound = valueBets
                            )
                        }
                        Log.d(TAG, "Bulk scan complete: ${result.results.size} analyzed, $valueBets value bets found")
                    }
                    is BulkAnalysisResult.PartialSuccess -> {
                        var valueBets = 0
                        for (betResult in result.results) {
                            val eventId = betResult.matchData.eventId
                            val analysis = betResult.aiAnalysis
                            if (analysis.isValueBet) valueBets++

                            cachedAnalysisDao.insertAnalysis(
                                CachedAnalysisEntity.fromAnalysis(eventId, analysis)
                            )
                            updateMatchState(eventId) {
                                it.copy(isAnalyzed = true, analysisResult = analysis)
                            }
                        }
                        _uiState.update {
                            it.copy(
                                isScanningAll = false,
                                scanProgress = matches.size,
                                lastScanCompleted = System.currentTimeMillis(),
                                valueBetsFound = valueBets,
                                error = "${result.failures.size} matches failed to analyze"
                            )
                        }
                        Log.d(TAG, "Bulk scan partial: ${result.results.size} ok, ${result.failures.size} failed")
                    }
                    is BulkAnalysisResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isScanningAll = false,
                                error = "Scan failed: ${result.message}"
                            )
                        }
                        Log.e(TAG, "Bulk scan error: ${result.message}")
                    }
                    is BulkAnalysisResult.Loading -> { /* no-op */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "scanAllMatches error", e)
                _uiState.update {
                    it.copy(
                        isScanningAll = false,
                        error = "Scan failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun formatLastUpdated(timestamp: Long?): String {
        if (timestamp == null) return "Never"
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
