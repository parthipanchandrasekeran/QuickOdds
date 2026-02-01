package com.quickodds.app.domain.model

import java.util.UUID

// ============ MARKET MODELS ============

data class Sport(
    val id: String,
    val name: String,
    val icon: String,
    val marketsCount: Int = 0
)

data class Market(
    val id: String,
    val sportId: String,
    val homeTeam: String,
    val awayTeam: String,
    val league: String,
    val startTime: Long,
    val status: MatchStatus,
    val odds: Odds,
    val stats: MatchStats? = null
)

data class Odds(
    val home: Double,
    val draw: Double,
    val away: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class MatchStats(
    val homeForm: String,      // e.g., "WWLDW"
    val awayForm: String,      // e.g., "LWWDL"
    val homeGoalsScored: Int,
    val homeGoalsConceded: Int,
    val awayGoalsScored: Int,
    val awayGoalsConceded: Int,
    val headToHead: String,    // e.g., "Home: 3W, Away: 1W, Draw: 1"
    val injuries: List<String> = emptyList()
)

enum class MatchStatus {
    UPCOMING,
    LIVE,
    FINISHED,
    POSTPONED
}

// ============ BETTING MODELS ============

data class Bet(
    val id: String = UUID.randomUUID().toString(),
    val marketId: String,
    val homeTeam: String,
    val awayTeam: String,
    val selection: BetSelection,
    val odds: Double,
    val stake: Double,
    val potentialWin: Double,
    val status: BetStatus,
    val placedAt: Long = System.currentTimeMillis(),
    val settledAt: Long? = null
)

enum class BetSelection {
    HOME,
    DRAW,
    AWAY
}

enum class BetStatus {
    PENDING,
    WON,
    LOST,
    VOID
}

// ============ WALLET MODELS ============

data class Wallet(
    val balance: Double,
    val totalDeposited: Double,
    val totalWon: Double,
    val totalLost: Double,
    val pendingBets: Double
)

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val type: TransactionType,
    val amount: Double,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val betId: String? = null
)

enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    BET_PLACED,
    BET_WON,
    BET_LOST,
    BET_VOID
}

// ============ ANALYSIS MODELS ============

data class BetAnalysis(
    val marketId: String,
    val recommendation: String,
    val confidenceScore: Double,
    val rationale: String,
    val suggestedStake: Int,
    val isValueBet: Boolean,
    val impliedProbabilities: Probabilities,
    val projectedProbabilities: Probabilities,
    val edges: Probabilities
)

data class Probabilities(
    val home: Double,
    val draw: Double,
    val away: Double
)
