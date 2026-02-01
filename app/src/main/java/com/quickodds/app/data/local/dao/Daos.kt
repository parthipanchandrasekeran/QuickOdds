package com.quickodds.app.data.local.dao

import androidx.room.*
import com.quickodds.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BetDao {
    @Query("SELECT * FROM bets ORDER BY placedAt DESC")
    fun getAllBets(): Flow<List<BetEntity>>

    @Query("SELECT * FROM bets WHERE status = 'PENDING' ORDER BY placedAt DESC")
    fun getPendingBets(): Flow<List<BetEntity>>

    @Query("SELECT * FROM bets WHERE id = :id")
    suspend fun getBetById(id: String): BetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBet(bet: BetEntity)

    @Update
    suspend fun updateBet(bet: BetEntity)

    @Delete
    suspend fun deleteBet(bet: BetEntity)

    @Query("SELECT COUNT(*) FROM bets WHERE status = 'PENDING'")
    fun getPendingBetsCount(): Flow<Int>
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet WHERE id = 1")
    fun getWallet(): Flow<WalletEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWallet(wallet: WalletEntity)

    @Query("UPDATE wallet SET balance = balance + :amount WHERE id = 1")
    suspend fun updateBalance(amount: Double)
}

@Dao
interface FavoriteMarketDao {
    @Query("SELECT * FROM favorite_markets ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteMarketEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_markets WHERE marketId = :marketId)")
    suspend fun isFavorite(marketId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteMarketEntity)

    @Query("DELETE FROM favorite_markets WHERE marketId = :marketId")
    suspend fun removeFavorite(marketId: String)
}
