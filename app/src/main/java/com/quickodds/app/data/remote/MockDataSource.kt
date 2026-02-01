package com.quickodds.app.data.remote

import com.quickodds.app.domain.model.*
import kotlinx.coroutines.delay

/**
 * Mock data source for development and testing.
 * Replace with actual API calls in production.
 */
object MockDataSource {

    suspend fun getSports(): List<Sport> {
        delay(500) // Simulate network delay
        return listOf(
            Sport("football", "Football", "soccer", 24),
            Sport("basketball", "Basketball", "basketball", 18),
            Sport("tennis", "Tennis", "tennis", 12),
            Sport("baseball", "Baseball", "baseball", 8),
            Sport("hockey", "Hockey", "hockey", 6)
        )
    }

    suspend fun getMarkets(sportId: String): List<Market> {
        delay(300)
        val currentTime = System.currentTimeMillis()

        return when (sportId) {
            "football" -> listOf(
                Market(
                    id = "mkt_001",
                    sportId = sportId,
                    homeTeam = "Manchester United",
                    awayTeam = "Liverpool",
                    league = "Premier League",
                    startTime = currentTime + 3600000,
                    status = MatchStatus.UPCOMING,
                    odds = Odds(2.45, 3.40, 2.90),
                    stats = MatchStats(
                        homeForm = "WWLDW",
                        awayForm = "WDWWL",
                        homeGoalsScored = 12,
                        homeGoalsConceded = 6,
                        awayGoalsScored = 14,
                        awayGoalsConceded = 8,
                        headToHead = "H2H: Man Utd 2W, Liverpool 3W, 1D"
                    )
                ),
                Market(
                    id = "mkt_002",
                    sportId = sportId,
                    homeTeam = "Arsenal",
                    awayTeam = "Chelsea",
                    league = "Premier League",
                    startTime = currentTime + 7200000,
                    status = MatchStatus.UPCOMING,
                    odds = Odds(1.95, 3.60, 3.80),
                    stats = MatchStats(
                        homeForm = "WWWDW",
                        awayForm = "LDWLW",
                        homeGoalsScored = 15,
                        homeGoalsConceded = 4,
                        awayGoalsScored = 9,
                        awayGoalsConceded = 10,
                        headToHead = "H2H: Arsenal 3W, Chelsea 1W, 2D"
                    )
                ),
                Market(
                    id = "mkt_003",
                    sportId = sportId,
                    homeTeam = "Real Madrid",
                    awayTeam = "Barcelona",
                    league = "La Liga",
                    startTime = currentTime + 86400000,
                    status = MatchStatus.UPCOMING,
                    odds = Odds(2.10, 3.50, 3.40),
                    stats = MatchStats(
                        homeForm = "WDWWW",
                        awayForm = "WWLDW",
                        homeGoalsScored = 18,
                        homeGoalsConceded = 7,
                        awayGoalsScored = 16,
                        awayGoalsConceded = 9,
                        headToHead = "H2H: Real 4W, Barca 3W, 3D"
                    )
                ),
                Market(
                    id = "mkt_004",
                    sportId = sportId,
                    homeTeam = "Bayern Munich",
                    awayTeam = "Dortmund",
                    league = "Bundesliga",
                    startTime = currentTime + 172800000,
                    status = MatchStatus.UPCOMING,
                    odds = Odds(1.65, 4.00, 4.80),
                    stats = MatchStats(
                        homeForm = "WWWWW",
                        awayForm = "WLDWW",
                        homeGoalsScored = 22,
                        homeGoalsConceded = 5,
                        awayGoalsScored = 13,
                        awayGoalsConceded = 11,
                        headToHead = "H2H: Bayern 5W, Dortmund 2W, 3D",
                        injuries = listOf("Dortmund: Key striker injured")
                    )
                )
            )
            "basketball" -> listOf(
                Market(
                    id = "mkt_b01",
                    sportId = sportId,
                    homeTeam = "LA Lakers",
                    awayTeam = "Golden State",
                    league = "NBA",
                    startTime = currentTime + 5400000,
                    status = MatchStatus.UPCOMING,
                    odds = Odds(1.85, 0.0, 1.95), // No draw in basketball
                    stats = MatchStats(
                        homeForm = "WLWWL",
                        awayForm = "WWWLW",
                        homeGoalsScored = 112,
                        homeGoalsConceded = 108,
                        awayGoalsScored = 118,
                        awayGoalsConceded = 110,
                        headToHead = "H2H: Lakers 2W, Warriors 3W"
                    )
                )
            )
            else -> emptyList()
        }
    }

    suspend fun getMarketById(marketId: String): Market? {
        delay(200)
        val allMarkets = getSports().flatMap { getMarkets(it.id) }
        return allMarkets.find { it.id == marketId }
    }

    fun getInitialWallet(): Wallet = Wallet(
        balance = 10000.0,
        totalDeposited = 10000.0,
        totalWon = 0.0,
        totalLost = 0.0,
        pendingBets = 0.0
    )
}
