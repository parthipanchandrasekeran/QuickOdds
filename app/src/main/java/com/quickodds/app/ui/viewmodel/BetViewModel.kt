package com.quickodds.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.quickodds.app.data.local.AppDatabase
import com.quickodds.app.data.local.dao.UserWalletDao
import com.quickodds.app.data.local.dao.VirtualBetDao
import com.quickodds.app.data.local.entity.BetStatus
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.data.local.entity.VirtualBet
import com.quickodds.app.worker.SmartSettlementWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Result of a bet placement attempt.
 */
sealed class BetResult {
    data class Success(val bet: VirtualBet) : BetResult()
    data class Error(val message: String) : BetResult()
}

/**
 * Result of bet settlement.
 */
sealed class SettlementResult {
    data class Won(val betId: Long, val winnings: Double) : SettlementResult()
    data class Lost(val betId: Long) : SettlementResult()
    data class Error(val message: String) : SettlementResult()
}

/**
 * UI State for bet-related screens.
 */
data class BetUiState(
    val wallet: UserWallet? = null,
    val pendingBets: List<VirtualBet> = emptyList(),
    val allBets: List<VirtualBet> = emptyList(),
    val isLoading: Boolean = false,
    val lastBetResult: BetResult? = null,
    val lastSettlementResult: SettlementResult? = null
)

/**
 * ViewModel for handling virtual betting transactions.
 *
 * Manages the complete lifecycle of virtual bets including:
 * - Placing new bets with balance validation
 * - Settling bets (WON/LOST) with wallet updates
 * - Real-time UI updates via Kotlin Flow
 *
 * @param walletDao DAO for wallet operations
 * @param betDao DAO for bet operations
 */
@HiltViewModel
class BetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val walletDao: UserWalletDao,
    private val betDao: VirtualBetDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BetUiState())
    val uiState: StateFlow<BetUiState> = _uiState.asStateFlow()

    // Expose wallet as a separate flow for components that only need balance
    val walletFlow: Flow<UserWallet?> = walletDao.observeWallet()

    // Expose pending bets count for badges
    val pendingBetsCount: Flow<Int> = betDao.observePendingBetsCount()

    init {
        observeWallet()
        observeBets()
    }

    /**
     * Observe wallet changes and update UI state.
     * Uses Kotlin Flow for instant UI updates when balance changes.
     */
    private fun observeWallet() {
        viewModelScope.launch {
            walletDao.observeWallet().collect { wallet ->
                _uiState.update { it.copy(wallet = wallet) }
            }
        }
    }

    /**
     * Observe all bets and pending bets.
     * UI updates instantly when bets are added or status changes.
     */
    private fun observeBets() {
        viewModelScope.launch {
            betDao.observeAllBets().collect { bets ->
                _uiState.update { it.copy(allBets = bets) }
            }
        }

        viewModelScope.launch {
            betDao.observePendingBets().collect { pending ->
                _uiState.update { it.copy(pendingBets = pending) }
            }
        }
    }

    /**
     * Place a virtual bet.
     *
     * This function:
     * 1. Validates sufficient wallet balance
     * 2. Subtracts stake from wallet
     * 3. Creates bet record with PENDING status
     * 4. Returns result via Flow for instant UI update
     *
     * @param eventId The Odds API event ID for settlement lookup
     * @param sportKey Sport key for API queries (e.g., "soccer_epl")
     * @param matchName Display name (e.g., "Team A vs Team B")
     * @param homeTeam Home team name for score comparison
     * @param awayTeam Away team name for score comparison
     * @param selection The team/outcome selected (e.g., "Team A", "Draw")
     * @param odds Decimal odds for the selection
     * @param amount Stake amount to bet
     * @param commenceTime Match start time (epoch millis) for scheduling settlement
     * @return BetResult indicating success or error
     */
    fun placeVirtualBet(
        eventId: String,
        sportKey: String,
        matchName: String,
        homeTeam: String,
        awayTeam: String,
        selection: String,
        odds: Double,
        amount: Double,
        commenceTime: Long
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, lastBetResult = null) }

            try {
                val wallet = _uiState.value.wallet

                // Validate wallet exists
                if (wallet == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastBetResult = BetResult.Error("Wallet not initialized")
                        )
                    }
                    return@launch
                }

                // Validate sufficient funds
                if (amount > wallet.balance) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastBetResult = BetResult.Error(
                                "Insufficient funds. Balance: $${String.format("%.2f", wallet.balance)}, Required: $${String.format("%.2f", amount)}"
                            )
                        )
                    }
                    return@launch
                }

                // Validate bet amount is positive
                if (amount <= 0) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastBetResult = BetResult.Error("Bet amount must be greater than zero")
                        )
                    }
                    return@launch
                }

                // Validate odds are valid
                if (odds <= 1.0) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastBetResult = BetResult.Error("Invalid odds")
                        )
                    }
                    return@launch
                }

                // Create bet record
                val bet = VirtualBet(
                    eventId = eventId,
                    sportKey = sportKey,
                    matchName = matchName,
                    homeTeam = homeTeam,
                    awayTeam = awayTeam,
                    selectedTeam = selection,
                    odds = odds,
                    stakeAmount = amount,
                    status = BetStatus.PENDING,
                    commenceTime = commenceTime,
                    timestamp = System.currentTimeMillis()
                )

                // ATOMIC: Insert bet and subtract stake in a single transaction
                val betId = database.withTransaction {
                    val id = betDao.insertBet(bet)
                    walletDao.subtractFunds(amount)
                    id
                }

                // Schedule settlement worker
                SmartSettlementWorker.scheduleSettlement(
                    context = context,
                    betId = betId,
                    eventId = eventId,
                    sportKey = sportKey,
                    commenceTime = commenceTime
                )

                // Update UI with success
                val savedBet = bet.copy(id = betId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastBetResult = BetResult.Success(savedBet)
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastBetResult = BetResult.Error("Failed to place bet: ${e.message}")
                    )
                }
            }
        }
    }

    /**
     * Settle a bet based on match result.
     *
     * This function:
     * 1. Updates bet status to WON or LOST
     * 2. If WON, credits wallet with winnings (stake × odds)
     * 3. Emits result via Flow for instant UI update
     *
     * @param betId The ID of the bet to settle
     * @param winningSelection The winning outcome (team name or "Draw")
     */
    fun settleBet(betId: Long, winningSelection: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, lastSettlementResult = null) }

            try {
                // Find the bet
                val bet = _uiState.value.allBets.find { it.id == betId }

                if (bet == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastSettlementResult = SettlementResult.Error("Bet not found")
                        )
                    }
                    return@launch
                }

                // Check if already settled
                if (bet.status != BetStatus.PENDING) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastSettlementResult = SettlementResult.Error("Bet already settled")
                        )
                    }
                    return@launch
                }

                // Determine if bet won
                val isWinner = bet.selectedTeam.equals(winningSelection, ignoreCase = true)

                if (isWinner) {
                    // Calculate winnings: stake × odds
                    val winnings = bet.stakeAmount * bet.odds

                    // Update bet status to WON
                    betDao.updateBetStatus(betId, BetStatus.WON)

                    // Credit winnings to wallet
                    walletDao.addFunds(winnings)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastSettlementResult = SettlementResult.Won(betId, winnings)
                        )
                    }
                } else {
                    // Update bet status to LOST
                    betDao.updateBetStatus(betId, BetStatus.LOST)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastSettlementResult = SettlementResult.Lost(betId)
                        )
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastSettlementResult = SettlementResult.Error("Settlement failed: ${e.message}")
                    )
                }
            }
        }
    }

    /**
     * Settle multiple bets at once (batch settlement).
     * Useful for settling all bets for a completed match.
     *
     * @param matchName The match name to identify related bets
     * @param winningSelection The winning outcome
     */
    fun settleMatchBets(matchName: String, winningSelection: String) {
        viewModelScope.launch {
            val matchBets = _uiState.value.pendingBets.filter {
                it.matchName == matchName
            }

            matchBets.forEach { bet ->
                settleBet(bet.id, winningSelection)
            }
        }
    }

    /**
     * Mock settlement for demo/testing purposes.
     * Randomly determines if bet won or lost.
     *
     * @param betId The bet to settle
     */
    fun mockSettleBet(betId: Long) {
        viewModelScope.launch {
            val bet = _uiState.value.allBets.find { it.id == betId }
            if (bet != null) {
                // 50% chance of winning for demo
                val isWinner = kotlin.random.Random.nextBoolean()
                val winningSelection = if (isWinner) bet.selectedTeam else "OTHER_TEAM"
                settleBet(betId, winningSelection)
            }
        }
    }

    /**
     * Settle all pending bets with random outcomes (for demo/testing).
     */
    fun mockSettleAllPending() {
        viewModelScope.launch {
            _uiState.value.pendingBets.forEach { bet ->
                mockSettleBet(bet.id)
                // Small delay between settlements for visual feedback
                kotlinx.coroutines.delay(500)
            }
        }
    }

    /**
     * Get betting statistics.
     */
    fun getBetStats(): BetStats {
        val bets = _uiState.value.allBets
        val wonBets = bets.filter { it.status == BetStatus.WON }
        val lostBets = bets.filter { it.status == BetStatus.LOST }

        val totalStaked = bets.sumOf { it.stakeAmount }
        val totalWinnings = wonBets.sumOf { it.stakeAmount * it.odds }
        val totalLost = lostBets.sumOf { it.stakeAmount }

        return BetStats(
            totalBets = bets.size,
            pendingBets = bets.count { it.status == BetStatus.PENDING },
            wonBets = wonBets.size,
            lostBets = lostBets.size,
            totalStaked = totalStaked,
            totalWinnings = totalWinnings,
            netProfit = totalWinnings - totalStaked,
            winRate = if (wonBets.size + lostBets.size > 0) {
                wonBets.size.toDouble() / (wonBets.size + lostBets.size)
            } else 0.0
        )
    }

    /**
     * Clear the last bet result (for dismissing notifications/dialogs).
     */
    fun clearLastBetResult() {
        _uiState.update { it.copy(lastBetResult = null) }
    }

    /**
     * Clear the last settlement result.
     */
    fun clearLastSettlementResult() {
        _uiState.update { it.copy(lastSettlementResult = null) }
    }
}

/**
 * Statistics about user's betting history.
 */
data class BetStats(
    val totalBets: Int,
    val pendingBets: Int,
    val wonBets: Int,
    val lostBets: Int,
    val totalStaked: Double,
    val totalWinnings: Double,
    val netProfit: Double,
    val winRate: Double
)
