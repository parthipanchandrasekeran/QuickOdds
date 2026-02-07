package com.quickodds.app.ui.screens.market

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.ai.AIAnalysisService
import com.quickodds.app.ai.model.*
import androidx.room.withTransaction
import com.quickodds.app.data.local.AppDatabase
import com.quickodds.app.data.local.dao.CachedAnalysisDao
import com.quickodds.app.data.local.dao.CachedOddsEventDao
import com.quickodds.app.data.local.dao.UserWalletDao
import com.quickodds.app.data.local.dao.VirtualBetDao
import com.quickodds.app.data.local.entity.BetStatus
import com.quickodds.app.data.local.entity.CachedAnalysisEntity
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.data.local.entity.VirtualBet
import com.quickodds.app.data.remote.api.OddsCloudFunctionService
import com.quickodds.app.data.remote.MockOddsDataSource
import com.quickodds.app.data.remote.dto.OddsEvent
import com.quickodds.app.worker.SmartSettlementWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Data for bet confirmation bottom sheet.
 */
data class BetConfirmationData(
    val eventId: String,
    val sportKey: String,
    val matchName: String,
    val homeTeam: String,
    val awayTeam: String,
    val selection: String,
    val odds: Double,
    val stake: Double,
    val potentialReturn: Double,
    val commenceTime: Long,
    val recommendedStake: Int? = null
)

/**
 * UI State for Market Detail Screen.
 */
data class MarketDetailUiState(
    val isLoading: Boolean = true,
    val match: OddsEvent? = null,
    val wallet: UserWallet? = null,
    val analysisResult: AIAnalysisResponse? = null,
    val isAnalyzing: Boolean = false,
    val betPlaced: Boolean = false,
    val error: String? = null,
    val isLiveData: Boolean = false,
    val showBetConfirmation: Boolean = false,
    val betConfirmationData: BetConfirmationData? = null,
    val isPlacingBet: Boolean = false
)

/**
 * ViewModel for Market Detail Screen.
 *
 * Handles match details, AI analysis, and bet placement.
 * Uses cached data when available to save API credits.
 *
 * @param context Application context for scheduling workers
 * @param walletDao DAO for wallet operations
 * @param betDao DAO for bet operations
 * @param cloudFunctionService Cloud Function service for odds data (secure proxy)
 * @param analysisService AI analysis service via Firebase Function
 * @param cachedOddsEventDao DAO for cached odds events
 */
@HiltViewModel
class MarketDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val walletDao: UserWalletDao,
    private val betDao: VirtualBetDao,
    private val cloudFunctionService: OddsCloudFunctionService,
    private val analysisService: AIAnalysisService,
    private val cachedOddsEventDao: CachedOddsEventDao,
    private val cachedAnalysisDao: CachedAnalysisDao
) : ViewModel() {

    companion object {
        private const val TAG = "MarketDetailViewModel"
    }

    private val _uiState = MutableStateFlow(MarketDetailUiState())
    val uiState: StateFlow<MarketDetailUiState> = _uiState.asStateFlow()

    init {
        initializeAndObserveWallet()
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
     * Load match details by ID.
     * Uses cache-first strategy: checks cache before making API calls to save credits.
     */
    fun loadMatch(matchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                var match: OddsEvent? = null
                var isLiveData = false

                // Step 1: Try to find match in cache first (saves API credits)
                val cachedEvent = cachedOddsEventDao.getEventById(matchId)
                if (cachedEvent != null && !cachedEvent.isStale()) {
                    match = cachedEvent.toOddsEvent()
                    if (match != null) {
                        Log.d(TAG, "Found match $matchId in cache (saving API credits)")
                        isLiveData = true
                    }
                }

                val sportsToSearch = listOf(
                    "soccer_epl",
                    "soccer_usa_mls",
                    "americanfootball_nfl",
                    "basketball_nba"
                )

                // Step 2: If not in cache or stale, try API
                if (match == null) {
                    Log.d(TAG, "Match $matchId not in cache, checking API")

                    for (sport in sportsToSearch) {
                        if (match != null) break

                        // Check if we have fresh cache for this sport
                        val shouldFetch = cachedOddsEventDao.shouldFetchFromApi(sport)

                        if (!shouldFetch) {
                            // Check cached events for this sport
                            val cachedEvents = cachedOddsEventDao.getEventsBySport(sport)
                            val cachedMatch = cachedEvents.find { it.id == matchId }?.toOddsEvent()
                            if (cachedMatch != null) {
                                match = cachedMatch
                                isLiveData = true
                                Log.d(TAG, "Found match in sport cache: $sport")
                                break
                            }
                        } else {
                            // Fetch from API
                            try {
                                val response = cloudFunctionService.getUpcomingOdds(
                                    sportKey = sport,
                                    regions = "us,uk,eu",
                                    markets = "h2h",  // Only h2h to save credits
                                    oddsFormat = "decimal"
                                )

                                if (response.isSuccessful) {
                                    match = response.body()?.find { it.id == matchId }
                                    if (match != null) {
                                        isLiveData = true
                                        Log.d(TAG, "Found match via API: $sport")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "API error for $sport: ${e.message}")
                            }
                        }
                    }
                }

                // Step 3: Fall back to mock data if not found
                if (match == null) {
                    val allMockMatches = mutableListOf<OddsEvent>()
                    for (sport in sportsToSearch) {
                        allMockMatches.addAll(MockOddsDataSource.getMatches(sport))
                    }
                    match = allMockMatches.find { it.id == matchId }
                    isLiveData = false
                    Log.d(TAG, "Using mock data for match $matchId")
                }

                // Also check for cached analysis
                var cachedAnalysis: AIAnalysisResponse? = null
                if (match != null) {
                    val cached = cachedAnalysisDao.getAnalysis(matchId)
                    if (cached != null && !cached.isStale()) {
                        cachedAnalysis = cached.toAnalysis()
                        if (cachedAnalysis != null) {
                            Log.d(TAG, "Loaded cached analysis for $matchId")
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        match = match,
                        isLiveData = isLiveData,
                        analysisResult = cachedAnalysis,
                        error = if (match == null) "Match not found" else null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading match", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load match: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Trigger AI analysis for the match.
     * Uses cache-first strategy to avoid duplicate API calls.
     */
    fun analyzeMatch() {
        val match = _uiState.value.match ?: return
        val matchId = match.id

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }

            try {
                // Step 1: Check cache first
                val cachedAnalysis = cachedAnalysisDao.getAnalysis(matchId)
                if (cachedAnalysis != null && !cachedAnalysis.isStale()) {
                    val analysis = cachedAnalysis.toAnalysis()
                    if (analysis != null) {
                        Log.d(TAG, "Using cached analysis for $matchId (saving API credits)")
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
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
                    homeTeamForm = "WWLDW",
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

                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                analysisResult = analysis
                            )
                        }
                    }
                    is AnalysisResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                error = result.message
                            )
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        error = e.message ?: "Analysis failed"
                    )
                }
            }
        }
    }

    /**
     * Show bet confirmation bottom sheet.
     */
    fun showBetConfirmation(selection: String, stake: Double) {
        Log.d(TAG, "showBetConfirmation called: selection=$selection, stake=$stake")

        val match = _uiState.value.match
        if (match == null) {
            Log.e(TAG, "Match is null")
            _uiState.update { it.copy(error = "Match not loaded") }
            return
        }
        Log.d(TAG, "Match loaded: ${match.homeTeam} vs ${match.awayTeam}")

        val wallet = _uiState.value.wallet
        if (wallet == null) {
            Log.e(TAG, "Wallet is null")
            _uiState.update { it.copy(error = "Wallet not initialized. Please restart the app.") }
            return
        }
        Log.d(TAG, "Wallet balance: ${wallet.balance}")

        if (stake > wallet.balance) {
            Log.e(TAG, "Insufficient balance: stake=$stake, balance=${wallet.balance}")
            _uiState.update { it.copy(error = "Insufficient balance") }
            return
        }

        if (stake <= 0) {
            Log.e(TAG, "Invalid stake: $stake")
            _uiState.update { it.copy(error = "Stake must be greater than zero") }
            return
        }

        val bookmaker = match.bookmakers.firstOrNull()
        val h2h = bookmaker?.markets?.find { it.key == "h2h" }

        // Log all available outcomes for debugging
        Log.d(TAG, "Bookmaker: ${bookmaker?.title}, markets: ${bookmaker?.markets?.map { it.key }}")
        Log.d(TAG, "H2H outcomes: ${h2h?.outcomes?.map { "${it.name}=${it.price}" }}")

        // Handle "Draw" case-insensitively
        val odds = h2h?.outcomes?.find {
            it.name.equals(selection, ignoreCase = true) ||
            (selection.equals("Draw", ignoreCase = true) && it.name.equals("Draw", ignoreCase = true))
        }?.price ?: 0.0

        Log.d(TAG, "Looking for selection='$selection', found odds=$odds")

        if (odds <= 0) {
            Log.e(TAG, "Invalid odds for selection '$selection': $odds")
            _uiState.update { it.copy(error = "Invalid odds for $selection") }
            return
        }

        val potentialReturn = stake * odds
        val recommendedStake = _uiState.value.analysisResult?.suggestedStake

        // Parse commence time
        val commenceTime = try {
            java.time.Instant.parse(match.commenceTime).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        val data = BetConfirmationData(
            eventId = match.id,
            sportKey = match.sportKey,
            matchName = "${match.homeTeam} vs ${match.awayTeam}",
            homeTeam = match.homeTeam,
            awayTeam = match.awayTeam,
            selection = selection,
            odds = odds,
            stake = stake,
            potentialReturn = potentialReturn,
            commenceTime = commenceTime,
            recommendedStake = recommendedStake
        )

        _uiState.update {
            it.copy(
                showBetConfirmation = true,
                betConfirmationData = data
            )
        }
    }

    /**
     * Dismiss bet confirmation bottom sheet.
     */
    fun dismissBetConfirmation() {
        _uiState.update {
            it.copy(
                showBetConfirmation = false,
                betConfirmationData = null
            )
        }
    }

    /**
     * Confirm and place the virtual bet.
     * Accepts optional data parameter to support local state approach.
     */
    fun confirmBet(data: BetConfirmationData? = null) {
        val confirmationData = data ?: _uiState.value.betConfirmationData ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isPlacingBet = true) }

            try {
                // Create bet with PENDING status and all settlement info
                val bet = VirtualBet(
                    eventId = confirmationData.eventId,
                    sportKey = confirmationData.sportKey,
                    matchName = confirmationData.matchName,
                    homeTeam = confirmationData.homeTeam,
                    awayTeam = confirmationData.awayTeam,
                    selectedTeam = confirmationData.selection,
                    odds = confirmationData.odds,
                    stakeAmount = confirmationData.stake,
                    status = BetStatus.PENDING,
                    commenceTime = confirmationData.commenceTime
                )

                // ATOMIC: Insert bet and subtract stake in a single transaction
                val betId = database.withTransaction {
                    val id = betDao.insertBet(bet)
                    walletDao.subtractFunds(confirmationData.stake)
                    id
                }

                // Schedule settlement worker to check match result
                SmartSettlementWorker.scheduleSettlement(
                    context = context,
                    betId = betId,
                    eventId = confirmationData.eventId,
                    sportKey = confirmationData.sportKey,
                    commenceTime = confirmationData.commenceTime
                )

                _uiState.update {
                    it.copy(
                        isPlacingBet = false,
                        showBetConfirmation = false,
                        betConfirmationData = null,
                        betPlaced = true
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPlacingBet = false,
                        error = "Failed to place bet: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Legacy method - now shows confirmation instead.
     */
    fun placeBet(selection: String, stake: Double) {
        Log.d(TAG, "placeBet called: selection=$selection, stake=$stake")
        showBetConfirmation(selection, stake)
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetBetPlaced() {
        _uiState.update { it.copy(betPlaced = false) }
    }
}
