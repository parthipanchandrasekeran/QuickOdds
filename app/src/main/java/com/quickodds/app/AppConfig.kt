package com.quickodds.app

/**
 * Centralized configuration constants for the Quick Odds app.
 * Prevents magic numbers scattered across the codebase.
 */
object AppConfig {

    /** Initial virtual wallet balance for new users. */
    const val INITIAL_WALLET_BALANCE = 200.0

    /** Default wallet currency code. */
    const val DEFAULT_CURRENCY = "USD"

    /** Cache staleness threshold in minutes. */
    const val CACHE_STALE_MINUTES = 15

    /** HTTP cache size in bytes (10 MB). */
    const val HTTP_CACHE_SIZE_BYTES = 10L * 1024 * 1024

    /** Network timeout in seconds. */
    const val NETWORK_TIMEOUT_SECONDS = 30L

    /** Hours after match start to first check for settlement. */
    const val SETTLEMENT_DELAY_HOURS = 2L

    /** Minutes between settlement retry attempts. */
    const val SETTLEMENT_RETRY_MINUTES = 30L

    /** Maximum number of times to reschedule settlement before giving up. */
    const val MAX_SETTLEMENT_RESCHEDULES = 48  // 48 * 30min = 24 hours of retrying

    /** Maximum retry attempts for settlement worker exceptions. */
    const val MAX_SETTLEMENT_RETRIES = 5

    /** Stale cache serving window when offline (hours). */
    const val OFFLINE_CACHE_HOURS = 24
}
