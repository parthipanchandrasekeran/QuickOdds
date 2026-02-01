package com.quickodds.app.data.local.dao

import androidx.room.*
import com.quickodds.app.data.local.entity.BetStatus
import com.quickodds.app.data.local.entity.VirtualBet
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for VirtualBet operations.
 * Uses Kotlin Coroutines for async operations and Flow for reactive streams.
 */
@Dao
interface VirtualBetDao {

    // ============ INSERT OPERATIONS ============

    /**
     * Insert a new bet. Returns the generated ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBet(bet: VirtualBet): Long

    /**
     * Insert multiple bets.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBets(bets: List<VirtualBet>): List<Long>

    // ============ UPDATE OPERATIONS ============

    /**
     * Update an existing bet.
     */
    @Update
    suspend fun updateBet(bet: VirtualBet)

    /**
     * Update the status of a specific bet.
     * @param betId The ID of the bet to update
     * @param status The new status (PENDING, WON, or LOST)
     */
    @Query("UPDATE virtual_bets SET status = :status WHERE id = :betId")
    suspend fun updateBetStatus(betId: Long, status: BetStatus)

    /**
     * Mark a bet as WON.
     */
    @Query("UPDATE virtual_bets SET status = 'WON' WHERE id = :betId")
    suspend fun markBetAsWon(betId: Long)

    /**
     * Mark a bet as LOST.
     */
    @Query("UPDATE virtual_bets SET status = 'LOST' WHERE id = :betId")
    suspend fun markBetAsLost(betId: Long)

    // ============ DELETE OPERATIONS ============

    /**
     * Delete a specific bet.
     */
    @Delete
    suspend fun deleteBet(bet: VirtualBet)

    /**
     * Delete bet by ID.
     */
    @Query("DELETE FROM virtual_bets WHERE id = :betId")
    suspend fun deleteBetById(betId: Long)

    /**
     * Delete all bets.
     */
    @Query("DELETE FROM virtual_bets")
    suspend fun deleteAllBets()

    /**
     * Delete all settled bets (WON or LOST).
     */
    @Query("DELETE FROM virtual_bets WHERE status != 'PENDING'")
    suspend fun deleteSettledBets()

    // ============ QUERY - REACTIVE (FLOW) ============

    /**
     * Observe all bets, ordered by most recent first.
     */
    @Query("SELECT * FROM virtual_bets ORDER BY timestamp DESC")
    fun observeAllBets(): Flow<List<VirtualBet>>

    /**
     * Observe bets filtered by status.
     */
    @Query("SELECT * FROM virtual_bets WHERE status = :status ORDER BY timestamp DESC")
    fun observeBetsByStatus(status: BetStatus): Flow<List<VirtualBet>>

    /**
     * Observe all pending bets.
     */
    @Query("SELECT * FROM virtual_bets WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun observePendingBets(): Flow<List<VirtualBet>>

    /**
     * Observe all settled bets (WON or LOST).
     */
    @Query("SELECT * FROM virtual_bets WHERE status IN ('WON', 'LOST') ORDER BY timestamp DESC")
    fun observeSettledBets(): Flow<List<VirtualBet>>

    /**
     * Observe a specific bet by ID.
     */
    @Query("SELECT * FROM virtual_bets WHERE id = :betId")
    fun observeBetById(betId: Long): Flow<VirtualBet?>

    // ============ QUERY - ONE-TIME ============

    /**
     * Get all bets (one-time snapshot).
     */
    @Query("SELECT * FROM virtual_bets ORDER BY timestamp DESC")
    suspend fun getAllBets(): List<VirtualBet>

    /**
     * Get a specific bet by ID.
     */
    @Query("SELECT * FROM virtual_bets WHERE id = :betId")
    suspend fun getBetById(betId: Long): VirtualBet?

    /**
     * Get bets by status.
     */
    @Query("SELECT * FROM virtual_bets WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getBetsByStatus(status: BetStatus): List<VirtualBet>

    /**
     * Get recent bets with limit.
     */
    @Query("SELECT * FROM virtual_bets ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBets(limit: Int): List<VirtualBet>

    // ============ AGGREGATE QUERIES ============

    /**
     * Get count of all bets.
     */
    @Query("SELECT COUNT(*) FROM virtual_bets")
    fun observeTotalBetsCount(): Flow<Int>

    /**
     * Get count of pending bets.
     */
    @Query("SELECT COUNT(*) FROM virtual_bets WHERE status = 'PENDING'")
    fun observePendingBetsCount(): Flow<Int>

    /**
     * Get total staked amount on pending bets.
     */
    @Query("SELECT COALESCE(SUM(stakeAmount), 0.0) FROM virtual_bets WHERE status = 'PENDING'")
    fun observeTotalPendingStake(): Flow<Double>

    /**
     * Get total winnings (sum of payouts from won bets).
     */
    @Query("SELECT COALESCE(SUM(stakeAmount * odds), 0.0) FROM virtual_bets WHERE status = 'WON'")
    fun observeTotalWinnings(): Flow<Double>

    /**
     * Get total losses (sum of stakes from lost bets).
     */
    @Query("SELECT COALESCE(SUM(stakeAmount), 0.0) FROM virtual_bets WHERE status = 'LOST'")
    fun observeTotalLosses(): Flow<Double>

    /**
     * Get win/loss statistics.
     */
    @Query("""
        SELECT
            COUNT(CASE WHEN status = 'WON' THEN 1 END) as wonCount,
            COUNT(CASE WHEN status = 'LOST' THEN 1 END) as lostCount,
            COUNT(CASE WHEN status = 'PENDING' THEN 1 END) as pendingCount
        FROM virtual_bets
    """)
    suspend fun getBetStatistics(): BetStatistics
}

/**
 * Data class for bet statistics query result.
 */
data class BetStatistics(
    val wonCount: Int,
    val lostCount: Int,
    val pendingCount: Int
) {
    val totalSettled: Int get() = wonCount + lostCount
    val winRate: Double get() = if (totalSettled > 0) wonCount.toDouble() / totalSettled else 0.0
}
