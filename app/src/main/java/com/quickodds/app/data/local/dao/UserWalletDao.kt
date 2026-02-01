package com.quickodds.app.data.local.dao

import androidx.room.*
import com.quickodds.app.data.local.entity.UserWallet
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UserWallet operations.
 * Uses Kotlin Coroutines for async operations and Flow for reactive streams.
 */
@Dao
interface UserWalletDao {

    // ============ OBSERVE WALLET ============

    /**
     * Observe the wallet as a Flow for reactive UI updates.
     */
    @Query("SELECT * FROM user_wallet WHERE id = 1")
    fun observeWallet(): Flow<UserWallet?>

    /**
     * Observe only the balance as a Flow.
     */
    @Query("SELECT balance FROM user_wallet WHERE id = 1")
    fun observeBalance(): Flow<Double?>

    // ============ ONE-TIME QUERIES ============

    /**
     * Get wallet snapshot (one-time, not reactive).
     */
    @Query("SELECT * FROM user_wallet WHERE id = 1")
    suspend fun getWallet(): UserWallet?

    /**
     * Get current balance (one-time).
     */
    @Query("SELECT balance FROM user_wallet WHERE id = 1")
    suspend fun getBalance(): Double?

    // ============ INSERT/UPDATE ============

    /**
     * Insert or replace wallet.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: UserWallet)

    /**
     * Update existing wallet.
     */
    @Update
    suspend fun updateWallet(wallet: UserWallet)

    // ============ BALANCE OPERATIONS ============

    /**
     * Add funds to wallet balance.
     * @param amount The amount to add (positive value)
     */
    @Query("""
        UPDATE user_wallet
        SET balance = balance + :amount,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun addFunds(amount: Double, timestamp: Long = System.currentTimeMillis())

    /**
     * Subtract funds from wallet balance.
     * @param amount The amount to subtract (positive value)
     */
    @Query("""
        UPDATE user_wallet
        SET balance = balance - :amount,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun subtractFunds(amount: Double, timestamp: Long = System.currentTimeMillis())

    /**
     * Set wallet balance to a specific value.
     */
    @Query("""
        UPDATE user_wallet
        SET balance = :newBalance,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun setBalance(newBalance: Double, timestamp: Long = System.currentTimeMillis())

    // ============ INITIALIZATION ============

    /**
     * Delete all wallet data (for reset).
     */
    @Query("DELETE FROM user_wallet")
    suspend fun deleteWallet()

    /**
     * Check if wallet exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM user_wallet WHERE id = 1)")
    suspend fun walletExists(): Boolean
}
