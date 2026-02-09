package com.quickodds.app.data.repository

import android.util.Log
import com.quickodds.app.data.remote.api.OddsCloudFunctionService
import com.quickodds.app.data.remote.api.ScoreEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching real team form data from the scores API.
 * Uses a 30-minute in-memory cache (1 API call per sport per 30 min).
 */
@Singleton
class TeamFormRepository @Inject constructor(
    private val cloudFunctionService: OddsCloudFunctionService
) {
    companion object {
        private const val TAG = "TeamFormRepository"
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes
        private const val DAYS_LOOKBACK = 3
    }

    // Cache: sportKey -> (timestamp, list of completed score events)
    private val cache = mutableMapOf<String, Pair<Long, List<ScoreEvent>>>()

    /**
     * Get recent form string (e.g., "WLD") for a team in a given sport.
     * Returns null if no data available.
     */
    suspend fun getTeamForm(sportKey: String, teamName: String): TeamFormData? {
        return try {
            val scores = getScoresForSport(sportKey) ?: return null
            calculateTeamForm(scores, teamName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get form for $teamName: ${e.message}")
            null
        }
    }

    /**
     * Get form data for both teams in a match.
     */
    suspend fun getMatchFormData(
        sportKey: String,
        homeTeam: String,
        awayTeam: String
    ): Pair<TeamFormData?, TeamFormData?> {
        return try {
            val scores = getScoresForSport(sportKey)
            if (scores == null) return null to null

            val homeForm = calculateTeamForm(scores, homeTeam)
            val awayForm = calculateTeamForm(scores, awayTeam)
            homeForm to awayForm
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get match form data: ${e.message}")
            null to null
        }
    }

    /**
     * Fetch scores for a sport, using cache if fresh.
     */
    private suspend fun getScoresForSport(sportKey: String): List<ScoreEvent>? {
        // Check cache
        val cached = cache[sportKey]
        if (cached != null && System.currentTimeMillis() - cached.first < CACHE_DURATION_MS) {
            Log.d(TAG, "Using cached scores for $sportKey (${cached.second.size} events)")
            return cached.second
        }

        // Fetch from API
        return try {
            val response = cloudFunctionService.getScores(
                sportKey = sportKey,
                daysFrom = DAYS_LOOKBACK
            )

            if (response.isSuccessful) {
                val events = response.body()?.filter { it.completed && it.scores != null } ?: emptyList()
                cache[sportKey] = System.currentTimeMillis() to events
                Log.d(TAG, "Fetched ${events.size} completed events for $sportKey")
                events
            } else {
                Log.e(TAG, "Scores API error: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scores API call failed: ${e.message}")
            null
        }
    }

    /**
     * Calculate W/L/D form string and goals from completed score events.
     */
    private fun calculateTeamForm(scores: List<ScoreEvent>, teamName: String): TeamFormData? {
        // Find matches involving this team
        val teamMatches = scores.filter { event ->
            event.home_team.equals(teamName, ignoreCase = true) ||
            event.away_team.equals(teamName, ignoreCase = true)
        }.sortedByDescending { it.commence_time } // Most recent first

        if (teamMatches.isEmpty()) return null

        val formBuilder = StringBuilder()
        var goalsScored = 0
        var goalsConceded = 0

        for (match in teamMatches) {
            val matchScores = match.scores ?: continue
            val isHome = match.home_team.equals(teamName, ignoreCase = true)

            val teamScore = matchScores.find {
                it.name.equals(if (isHome) match.home_team else match.away_team, ignoreCase = true)
            }?.score?.toIntOrNull() ?: continue

            val opponentScore = matchScores.find {
                it.name.equals(if (isHome) match.away_team else match.home_team, ignoreCase = true)
            }?.score?.toIntOrNull() ?: continue

            goalsScored += teamScore
            goalsConceded += opponentScore

            formBuilder.append(
                when {
                    teamScore > opponentScore -> "W"
                    teamScore < opponentScore -> "L"
                    else -> "D"
                }
            )
        }

        val form = formBuilder.toString()
        if (form.isEmpty()) return null

        return TeamFormData(
            formString = form,
            matchesPlayed = form.length,
            goalsScored = goalsScored,
            goalsConceded = goalsConceded
        )
    }
}

/**
 * Computed team form data from recent matches.
 */
data class TeamFormData(
    val formString: String,     // e.g., "WWLDL"
    val matchesPlayed: Int,
    val goalsScored: Int,
    val goalsConceded: Int
)
