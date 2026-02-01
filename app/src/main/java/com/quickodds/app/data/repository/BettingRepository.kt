package com.quickodds.app.data.repository

import com.quickodds.app.data.local.dao.BetDao
import com.quickodds.app.data.local.dao.TransactionDao
import com.quickodds.app.data.local.dao.WalletDao
import com.quickodds.app.data.local.entity.BetEntity
import com.quickodds.app.data.local.entity.TransactionEntity
import com.quickodds.app.data.local.entity.WalletEntity
import com.quickodds.app.data.remote.MockDataSource
import com.quickodds.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class BettingRepository(
    private val betDao: BetDao,
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao
) {

    // ============ WALLET OPERATIONS ============

    fun getWallet(): Flow<Wallet> {
        return walletDao.getWallet().map { entity ->
            entity?.toDomain() ?: MockDataSource.getInitialWallet()
        }
    }

    suspend fun initializeWallet() {
        val existing = walletDao.getWallet().firstOrNull()
        if (existing == null) {
            val initial = MockDataSource.getInitialWallet()
            walletDao.insertOrUpdateWallet(WalletEntity.fromDomain(initial))

            // Add initial deposit transaction
            val transaction = Transaction(
                type = TransactionType.DEPOSIT,
                amount = initial.balance,
                description = "Initial virtual balance"
            )
            transactionDao.insertTransaction(TransactionEntity.fromDomain(transaction))
        }
    }

    suspend fun deposit(amount: Double) {
        val wallet = walletDao.getWallet().firstOrNull() ?: return
        val updated = wallet.copy(
            balance = wallet.balance + amount,
            totalDeposited = wallet.totalDeposited + amount
        )
        walletDao.insertOrUpdateWallet(updated)

        val transaction = Transaction(
            type = TransactionType.DEPOSIT,
            amount = amount,
            description = "Virtual deposit"
        )
        transactionDao.insertTransaction(TransactionEntity.fromDomain(transaction))
    }

    // ============ BET OPERATIONS ============

    fun getAllBets(): Flow<List<Bet>> {
        return betDao.getAllBets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getPendingBets(): Flow<List<Bet>> {
        return betDao.getPendingBets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getPendingBetsCount(): Flow<Int> {
        return betDao.getPendingBetsCount()
    }

    suspend fun placeBet(
        market: Market,
        selection: BetSelection,
        stake: Double
    ): Result<Bet> {
        val wallet = walletDao.getWallet().firstOrNull()?.toDomain()
            ?: return Result.failure(Exception("Wallet not initialized"))

        if (stake > wallet.balance) {
            return Result.failure(Exception("Insufficient balance"))
        }

        if (stake <= 0) {
            return Result.failure(Exception("Invalid stake amount"))
        }

        val odds = when (selection) {
            BetSelection.HOME -> market.odds.home
            BetSelection.DRAW -> market.odds.draw
            BetSelection.AWAY -> market.odds.away
        }

        val bet = Bet(
            marketId = market.id,
            homeTeam = market.homeTeam,
            awayTeam = market.awayTeam,
            selection = selection,
            odds = odds,
            stake = stake,
            potentialWin = stake * odds,
            status = BetStatus.PENDING
        )

        // Save bet
        betDao.insertBet(BetEntity.fromDomain(bet))

        // Update wallet
        val updatedWallet = WalletEntity(
            balance = wallet.balance - stake,
            totalDeposited = wallet.totalDeposited,
            totalWon = wallet.totalWon,
            totalLost = wallet.totalLost,
            pendingBets = wallet.pendingBets + stake
        )
        walletDao.insertOrUpdateWallet(updatedWallet)

        // Add transaction
        val selectionText = when (selection) {
            BetSelection.HOME -> market.homeTeam
            BetSelection.DRAW -> "Draw"
            BetSelection.AWAY -> market.awayTeam
        }
        val transaction = Transaction(
            type = TransactionType.BET_PLACED,
            amount = -stake,
            description = "Bet on $selectionText @ $odds",
            betId = bet.id
        )
        transactionDao.insertTransaction(TransactionEntity.fromDomain(transaction))

        return Result.success(bet)
    }

    suspend fun settleBet(betId: String, won: Boolean) {
        val bet = betDao.getBetById(betId)?.toDomain() ?: return
        val wallet = walletDao.getWallet().firstOrNull()?.toDomain() ?: return

        val updatedBet = bet.copy(
            status = if (won) BetStatus.WON else BetStatus.LOST,
            settledAt = System.currentTimeMillis()
        )
        betDao.updateBet(BetEntity.fromDomain(updatedBet))

        val winAmount = if (won) bet.potentialWin else 0.0
        val updatedWallet = WalletEntity(
            balance = wallet.balance + winAmount,
            totalDeposited = wallet.totalDeposited,
            totalWon = wallet.totalWon + (if (won) winAmount else 0.0),
            totalLost = wallet.totalLost + (if (!won) bet.stake else 0.0),
            pendingBets = wallet.pendingBets - bet.stake
        )
        walletDao.insertOrUpdateWallet(updatedWallet)

        val transaction = Transaction(
            type = if (won) TransactionType.BET_WON else TransactionType.BET_LOST,
            amount = if (won) winAmount else 0.0,
            description = if (won) "Won bet: ${bet.homeTeam} vs ${bet.awayTeam}"
                         else "Lost bet: ${bet.homeTeam} vs ${bet.awayTeam}",
            betId = betId
        )
        transactionDao.insertTransaction(TransactionEntity.fromDomain(transaction))
    }

    // ============ TRANSACTION OPERATIONS ============

    fun getTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>> {
        return transactionDao.getRecentTransactions(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
