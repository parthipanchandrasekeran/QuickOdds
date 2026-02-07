package com.quickodds.app.data.remote

import com.quickodds.app.data.remote.dto.*
import kotlinx.coroutines.delay

/**
 * Mock data source for demo purposes.
 * Replace with actual API calls in production.
 */
object MockOddsDataSource {

    suspend fun getMatches(sportKey: String): List<OddsEvent> {
        delay(200) // Simulate network delay

        return when (sportKey) {
            "soccer_usa_mls" -> mlsMatches
            "americanfootball_nfl" -> nflMatches
            "basketball_nba" -> nbaMatches
            "soccer_epl" -> eplMatches
            else -> emptyList()
        }
    }

    private val mlsMatches = listOf(
        createMatch(
            id = "mls_001",
            sportKey = "soccer_usa_mls",
            sportTitle = "MLS",
            homeTeam = "LA Galaxy",
            awayTeam = "LAFC",
            homeOdds = 2.30,
            drawOdds = 3.40,
            awayOdds = 2.85,
            hoursFromNow = 3
        ),
        createMatch(
            id = "mls_002",
            sportKey = "soccer_usa_mls",
            sportTitle = "MLS",
            homeTeam = "Inter Miami",
            awayTeam = "Atlanta United",
            homeOdds = 1.75,
            drawOdds = 3.80,
            awayOdds = 4.20,
            hoursFromNow = 6
        ),
        createMatch(
            id = "mls_003",
            sportKey = "soccer_usa_mls",
            sportTitle = "MLS",
            homeTeam = "Seattle Sounders",
            awayTeam = "Portland Timbers",
            homeOdds = 2.10,
            drawOdds = 3.50,
            awayOdds = 3.20,
            hoursFromNow = 26
        )
    )

    private val nflMatches = listOf(
        createMatch(
            id = "nfl_001",
            sportKey = "americanfootball_nfl",
            sportTitle = "NFL",
            homeTeam = "Kansas City Chiefs",
            awayTeam = "Buffalo Bills",
            homeOdds = 1.65,
            drawOdds = null,
            awayOdds = 2.25,
            hoursFromNow = 48
        ),
        createMatch(
            id = "nfl_002",
            sportKey = "americanfootball_nfl",
            sportTitle = "NFL",
            homeTeam = "San Francisco 49ers",
            awayTeam = "Dallas Cowboys",
            homeOdds = 1.55,
            drawOdds = null,
            awayOdds = 2.45,
            hoursFromNow = 72
        )
    )

    private val nbaMatches = listOf(
        createMatch(
            id = "nba_001",
            sportKey = "basketball_nba",
            sportTitle = "NBA",
            homeTeam = "LA Lakers",
            awayTeam = "Golden State Warriors",
            homeOdds = 1.90,
            drawOdds = null,
            awayOdds = 1.90,
            hoursFromNow = 5
        ),
        createMatch(
            id = "nba_002",
            sportKey = "basketball_nba",
            sportTitle = "NBA",
            homeTeam = "Boston Celtics",
            awayTeam = "Milwaukee Bucks",
            homeOdds = 1.75,
            drawOdds = null,
            awayOdds = 2.10,
            hoursFromNow = 8
        )
    )

    private val eplMatches = listOf(
        createMatch(
            id = "epl_001",
            sportKey = "soccer_epl",
            sportTitle = "Premier League",
            homeTeam = "Manchester United",
            awayTeam = "Liverpool",
            homeOdds = 2.45,
            drawOdds = 3.40,
            awayOdds = 2.90,
            hoursFromNow = 3
        ),
        createMatch(
            id = "epl_002",
            sportKey = "soccer_epl",
            sportTitle = "Premier League",
            homeTeam = "Arsenal",
            awayTeam = "Chelsea",
            homeOdds = 1.95,
            drawOdds = 3.60,
            awayOdds = 3.80,
            hoursFromNow = 6
        ),
        createMatch(
            id = "epl_003",
            sportKey = "soccer_epl",
            sportTitle = "Premier League",
            homeTeam = "Manchester City",
            awayTeam = "Tottenham",
            homeOdds = 1.35,
            drawOdds = 5.00,
            awayOdds = 8.50,
            hoursFromNow = 28
        )
    )

    private fun createMatch(
        id: String,
        sportKey: String,
        sportTitle: String,
        homeTeam: String,
        awayTeam: String,
        homeOdds: Double,
        drawOdds: Double?,
        awayOdds: Double,
        hoursFromNow: Int
    ): OddsEvent {
        val commenceTime = java.time.Instant.now()
            .plusSeconds(hoursFromNow * 3600L)
            .toString()

        val outcomes = mutableListOf(
            OddsOutcome(name = homeTeam, price = homeOdds),
            OddsOutcome(name = awayTeam, price = awayOdds)
        )
        drawOdds?.let { outcomes.add(OddsOutcome(name = "Draw", price = it)) }

        return OddsEvent(
            id = id,
            sportKey = sportKey,
            sportTitle = sportTitle,
            commenceTime = commenceTime,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            bookmakers = listOf(
                BookmakerOdds(
                    key = "fanduel",
                    title = "FanDuel",
                    lastUpdate = java.time.Instant.now().toString(),
                    markets = listOf(
                        OddsMarket(
                            key = "h2h",
                            lastUpdate = java.time.Instant.now().toString(),
                            outcomes = outcomes
                        )
                    )
                )
            )
        )
    }
}
