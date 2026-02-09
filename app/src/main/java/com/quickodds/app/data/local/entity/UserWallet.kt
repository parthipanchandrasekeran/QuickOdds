package com.quickodds.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.quickodds.app.AppConfig

/**
 * Room entity representing the user's virtual wallet.
 * Stores virtual currency balance for the betting simulation.
 */
@Entity(tableName = "user_wallet")
data class UserWallet(
    @PrimaryKey
    val id: Int = 1,                        // Singleton wallet (always id = 1)

    val balance: Double = AppConfig.INITIAL_WALLET_BALANCE,

    val currency: String = "USD",           // Currency code (USD, EUR, GBP, etc.)

    val createdAt: Long = System.currentTimeMillis(),

    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun createDefault(
            initialBalance: Double = AppConfig.INITIAL_WALLET_BALANCE,
            currency: String = "USD"
        ) = UserWallet(
            balance = initialBalance,
            currency = currency
        )
    }
}
