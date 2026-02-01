package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.quickodds.app.domain.model.Bet
import com.quickodds.app.domain.model.BetSelection
import com.quickodds.app.domain.model.Transaction
import com.quickodds.app.domain.model.TransactionType
import com.quickodds.app.domain.model.Wallet
import com.quickodds.app.domain.model.BetStatus as DomainBetStatus

@Entity(tableName = "bets")
data class BetEntity(
    @PrimaryKey val id: String,
    val marketId: String,
    val homeTeam: String,
    val awayTeam: String,
    val selection: String,
    val odds: Double,
    val stake: Double,
    val potentialWin: Double,
    val status: String,
    val placedAt: Long,
    val settledAt: Long?
) {
    fun toDomain(): Bet = Bet(
        id = id,
        marketId = marketId,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        selection = BetSelection.valueOf(selection),
        odds = odds,
        stake = stake,
        potentialWin = potentialWin,
        status = DomainBetStatus.valueOf(status),
        placedAt = placedAt,
        settledAt = settledAt
    )

    companion object {
        fun fromDomain(bet: Bet): BetEntity = BetEntity(
            id = bet.id,
            marketId = bet.marketId,
            homeTeam = bet.homeTeam,
            awayTeam = bet.awayTeam,
            selection = bet.selection.name,
            odds = bet.odds,
            stake = bet.stake,
            potentialWin = bet.potentialWin,
            status = bet.status.name,
            placedAt = bet.placedAt,
            settledAt = bet.settledAt
        )
    }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amount: Double,
    val description: String,
    val timestamp: Long,
    val betId: String?
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        type = TransactionType.valueOf(type),
        amount = amount,
        description = description,
        timestamp = timestamp,
        betId = betId
    )

    companion object {
        fun fromDomain(transaction: Transaction): TransactionEntity = TransactionEntity(
            id = transaction.id,
            type = transaction.type.name,
            amount = transaction.amount,
            description = transaction.description,
            timestamp = transaction.timestamp,
            betId = transaction.betId
        )
    }
}

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 1,
    val balance: Double,
    val totalDeposited: Double,
    val totalWon: Double,
    val totalLost: Double,
    val pendingBets: Double
) {
    fun toDomain(): Wallet = Wallet(
        balance = balance,
        totalDeposited = totalDeposited,
        totalWon = totalWon,
        totalLost = totalLost,
        pendingBets = pendingBets
    )

    companion object {
        fun fromDomain(wallet: Wallet): WalletEntity = WalletEntity(
            balance = wallet.balance,
            totalDeposited = wallet.totalDeposited,
            totalWon = wallet.totalWon,
            totalLost = wallet.totalLost,
            pendingBets = wallet.pendingBets
        )
    }
}

@Entity(tableName = "favorite_markets")
data class FavoriteMarketEntity(
    @PrimaryKey val marketId: String,
    val addedAt: Long = System.currentTimeMillis()
)
