package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a virtual bet placed by the user.
 */
@Entity(tableName = "virtual_bets")
data class VirtualBet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val eventId: String,                // The Odds API event ID for settlement lookup

    val sportKey: String,               // Sport key for API queries (e.g., "soccer_epl")

    val matchName: String,              // e.g., "Manchester United vs Liverpool"

    val homeTeam: String,               // Home team name for score comparison

    val awayTeam: String,               // Away team name for score comparison

    val selectedTeam: String,           // The team/outcome the user bet on

    val odds: Double,                   // Decimal odds at time of placement

    val stakeAmount: Double,            // Amount wagered

    val status: BetStatus = BetStatus.PENDING,

    val commenceTime: Long,             // Match start time (epoch millis) for scheduling

    val timestamp: Long = System.currentTimeMillis()  // When bet was placed
) {
    /**
     * Calculate potential payout if bet wins.
     */
    val potentialPayout: Double
        get() = stakeAmount * odds

    /**
     * Calculate potential profit (payout minus stake).
     */
    val potentialProfit: Double
        get() = potentialPayout - stakeAmount
}

/**
 * Enum representing the status of a virtual bet.
 */
enum class BetStatus {
    PENDING,    // Bet is active, awaiting result
    WON,        // Bet was successful
    LOST        // Bet was unsuccessful
}
