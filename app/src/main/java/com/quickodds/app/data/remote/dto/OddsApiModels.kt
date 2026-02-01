package com.quickodds.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data models for The-Odds-API.
 * Matches the JSON structure of the Moneyline (h2h) market response.
 *
 * API Documentation: https://the-odds-api.com/liveapi/guides/v4/
 */

/**
 * Represents a single sporting event with odds from multiple bookmakers.
 * This is the root object returned in the API response array.
 */
data class OddsEvent(
    @SerializedName("id")
    val id: String,

    @SerializedName("sport_key")
    val sportKey: String,               // e.g., "soccer_usa_mls", "americanfootball_nfl"

    @SerializedName("sport_title")
    val sportTitle: String,             // e.g., "MLS", "NFL"

    @SerializedName("commence_time")
    val commenceTime: String,           // ISO 8601 format: "2024-01-15T19:00:00Z"

    @SerializedName("home_team")
    val homeTeam: String,               // Home team name

    @SerializedName("away_team")
    val awayTeam: String,               // Away team name

    @SerializedName("bookmakers")
    val bookmakers: List<BookmakerOdds>
) {
    /**
     * Match name in "Home vs Away" format.
     */
    val matchName: String
        get() = "$homeTeam vs $awayTeam"

    /**
     * Get the best available odds for a specific outcome.
     */
    fun getBestOddsFor(team: String): BookmakerPrice? {
        return bookmakers
            .flatMap { bookmaker ->
                bookmaker.markets
                    .filter { it.key == "h2h" }
                    .flatMap { market ->
                        market.outcomes
                            .filter { it.name == team }
                            .map { outcome ->
                                BookmakerPrice(
                                    bookmakerName = bookmaker.title,
                                    price = outcome.price
                                )
                            }
                    }
            }
            .maxByOrNull { it.price }
    }
}

/**
 * Represents a bookmaker with their offered odds.
 */
data class BookmakerOdds(
    @SerializedName("key")
    val key: String,                    // e.g., "draftkings", "fanduel", "betmgm"

    @SerializedName("title")
    val title: String,                  // Display name: "DraftKings", "FanDuel"

    @SerializedName("last_update")
    val lastUpdate: String,             // ISO 8601 timestamp

    @SerializedName("markets")
    val markets: List<OddsMarket>
) {
    /**
     * Get h2h (moneyline) odds from this bookmaker.
     */
    fun getMoneylineOdds(): MoneylineOdds? {
        val h2hMarket = markets.find { it.key == "h2h" } ?: return null

        val outcomes = h2hMarket.outcomes
        if (outcomes.size < 2) return null

        // For 2-way markets (no draw): [home, away]
        // For 3-way markets (with draw): [home, away, draw] or [home, draw, away]
        return MoneylineOdds(
            homeTeamOdds = outcomes.getOrNull(0)?.price ?: 0.0,
            awayTeamOdds = outcomes.getOrNull(1)?.price ?: 0.0,
            drawOdds = outcomes.find { it.name.lowercase() == "draw" }?.price
        )
    }
}

/**
 * Represents a betting market (e.g., h2h for moneyline, spreads, totals).
 */
data class OddsMarket(
    @SerializedName("key")
    val key: String,                    // "h2h", "spreads", "totals"

    @SerializedName("last_update")
    val lastUpdate: String,

    @SerializedName("outcomes")
    val outcomes: List<OddsOutcome>
)

/**
 * Represents a single betting outcome with decimal odds (price).
 */
data class OddsOutcome(
    @SerializedName("name")
    val name: String,                   // Team name, "Draw", "Over", "Under"

    @SerializedName("price")
    val price: Double,                  // Decimal odds (e.g., 2.50, 1.85)

    @SerializedName("point")
    val point: Double? = null           // For spreads/totals (e.g., -3.5, 45.5)
) {
    /**
     * Implied probability from decimal odds.
     */
    val impliedProbability: Double
        get() = if (price > 0) 1.0 / price else 0.0

    /**
     * Convert to American odds format.
     */
    val americanOdds: Int
        get() = when {
            price >= 2.0 -> ((price - 1) * 100).toInt()
            price > 1.0 -> (-100 / (price - 1)).toInt()
            else -> 0
        }

    /**
     * Formatted American odds string (e.g., "+150", "-110").
     */
    val americanOddsFormatted: String
        get() = if (americanOdds >= 0) "+$americanOdds" else "$americanOdds"
}

// ============ HELPER DATA CLASSES ============

/**
 * Simplified moneyline odds for a match.
 */
data class MoneylineOdds(
    val homeTeamOdds: Double,
    val awayTeamOdds: Double,
    val drawOdds: Double? = null        // Null for sports without draws (NFL, NBA)
)

/**
 * Bookmaker with their price for comparison.
 */
data class BookmakerPrice(
    val bookmakerName: String,
    val price: Double
)

// ============ API RESPONSE WRAPPER ============

/**
 * Wrapper for paginated or meta-data enriched responses (if needed).
 */
data class OddsApiResponse<T>(
    @SerializedName("data")
    val data: T,

    @SerializedName("remaining_requests")
    val remainingRequests: Int? = null,

    @SerializedName("used_requests")
    val usedRequests: Int? = null
)
