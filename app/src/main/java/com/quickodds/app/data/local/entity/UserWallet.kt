package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing the user's virtual wallet.
 * Stores virtual currency balance for the betting simulation.
 */
@Entity(tableName = "user_wallet")
data class UserWallet(
    @PrimaryKey
    val id: Int = 1,                        // Singleton wallet (always id = 1)

    val balance: Double = 10000.0,          // Current virtual balance

    val currency: String = "USD",           // Currency code (USD, EUR, GBP, etc.)

    val createdAt: Long = System.currentTimeMillis(),

    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun createDefault(
            initialBalance: Double = 10000.0,
            currency: String = "USD"
        ) = UserWallet(
            balance = initialBalance,
            currency = currency
        )
    }
}
