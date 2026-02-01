package com.quickodds.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.quickodds.app.domain.model.*

data class SportDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: String,
    @SerializedName("markets_count") val marketsCount: Int
) {
    fun toDomain(): Sport = Sport(
        id = id,
        name = name,
        icon = icon,
        marketsCount = marketsCount
    )
}

data class MarketDto(
    @SerializedName("id") val id: String,
    @SerializedName("sport_id") val sportId: String,
    @SerializedName("home_team") val homeTeam: String,
    @SerializedName("away_team") val awayTeam: String,
    @SerializedName("league") val league: String,
    @SerializedName("start_time") val startTime: Long,
    @SerializedName("status") val status: String,
    @SerializedName("odds") val odds: OddsDto,
    @SerializedName("stats") val stats: MatchStatsDto? = null
) {
    fun toDomain(): Market = Market(
        id = id,
        sportId = sportId,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        league = league,
        startTime = startTime,
        status = MatchStatus.valueOf(status.uppercase()),
        odds = odds.toDomain(),
        stats = stats?.toDomain()
    )
}

data class OddsDto(
    @SerializedName("home") val home: Double,
    @SerializedName("draw") val draw: Double,
    @SerializedName("away") val away: Double,
    @SerializedName("last_updated") val lastUpdated: Long? = null
) {
    fun toDomain(): Odds = Odds(
        home = home,
        draw = draw,
        away = away,
        lastUpdated = lastUpdated ?: System.currentTimeMillis()
    )
}

data class MatchStatsDto(
    @SerializedName("home_form") val homeForm: String,
    @SerializedName("away_form") val awayForm: String,
    @SerializedName("home_goals_scored") val homeGoalsScored: Int,
    @SerializedName("home_goals_conceded") val homeGoalsConceded: Int,
    @SerializedName("away_goals_scored") val awayGoalsScored: Int,
    @SerializedName("away_goals_conceded") val awayGoalsConceded: Int,
    @SerializedName("head_to_head") val headToHead: String,
    @SerializedName("injuries") val injuries: List<String>? = null
) {
    fun toDomain(): MatchStats = MatchStats(
        homeForm = homeForm,
        awayForm = awayForm,
        homeGoalsScored = homeGoalsScored,
        homeGoalsConceded = homeGoalsConceded,
        awayGoalsScored = awayGoalsScored,
        awayGoalsConceded = awayGoalsConceded,
        headToHead = headToHead,
        injuries = injuries ?: emptyList()
    )
}
