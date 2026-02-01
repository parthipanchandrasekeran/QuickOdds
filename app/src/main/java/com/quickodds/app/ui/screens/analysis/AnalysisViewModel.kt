package com.quickodds.app.ui.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.ai.AIAnalysisRepository
import com.quickodds.app.ai.AIAnalysisService
import com.quickodds.app.ai.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for the analysis screen.
 */
data class AnalysisUiState(
    val isLoading: Boolean = false,
    val matchData: MatchData? = null,
    val analysisResult: BetAnalysisResult? = null,
    val error: String? = null,

    // Input fields
    val homeTeam: String = "",
    val awayTeam: String = "",
    val homeOdds: String = "",
    val drawOdds: String = "",
    val awayOdds: String = "",
    val league: String = "",
    val homeForm: String = "",
    val awayForm: String = "",
    val headToHead: String = "",
    val injuries: String = ""
)

/**
 * ViewModel for AI betting analysis.
 */
class AnalysisViewModel : ViewModel() {

    private val analysisService = AIAnalysisService()
    private val repository = AIAnalysisRepository(analysisService)

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    // ============ INPUT HANDLERS ============

    fun updateHomeTeam(value: String) {
        _uiState.update { it.copy(homeTeam = value) }
    }

    fun updateAwayTeam(value: String) {
        _uiState.update { it.copy(awayTeam = value) }
    }

    fun updateHomeOdds(value: String) {
        if (value.isEmpty() || value.toDoubleOrNull() != null) {
            _uiState.update { it.copy(homeOdds = value) }
        }
    }

    fun updateDrawOdds(value: String) {
        if (value.isEmpty() || value.toDoubleOrNull() != null) {
            _uiState.update { it.copy(drawOdds = value) }
        }
    }

    fun updateAwayOdds(value: String) {
        if (value.isEmpty() || value.toDoubleOrNull() != null) {
            _uiState.update { it.copy(awayOdds = value) }
        }
    }

    fun updateLeague(value: String) {
        _uiState.update { it.copy(league = value) }
    }

    fun updateHomeForm(value: String) {
        _uiState.update { it.copy(homeForm = value.uppercase()) }
    }

    fun updateAwayForm(value: String) {
        _uiState.update { it.copy(awayForm = value.uppercase()) }
    }

    fun updateHeadToHead(value: String) {
        _uiState.update { it.copy(headToHead = value) }
    }

    fun updateInjuries(value: String) {
        _uiState.update { it.copy(injuries = value) }
    }

    // ============ ANALYSIS ============

    /**
     * Run AI analysis on the current input.
     */
    fun analyzeMatch() {
        val state = _uiState.value

        // Validate inputs
        if (state.homeTeam.isBlank() || state.awayTeam.isBlank()) {
            _uiState.update { it.copy(error = "Please enter both team names") }
            return
        }

        val homeOdds = state.homeOdds.toDoubleOrNull()
        val awayOdds = state.awayOdds.toDoubleOrNull()

        if (homeOdds == null || awayOdds == null || homeOdds <= 1 || awayOdds <= 1) {
            _uiState.update { it.copy(error = "Please enter valid odds (> 1.0)") }
            return
        }

        val drawOdds = state.drawOdds.toDoubleOrNull()?.takeIf { it > 1 }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val matchData = MatchData(
                eventId = "${state.homeTeam}_${state.awayTeam}_${System.currentTimeMillis()}",
                homeTeam = state.homeTeam,
                awayTeam = state.awayTeam,
                homeOdds = homeOdds,
                drawOdds = drawOdds,
                awayOdds = awayOdds,
                league = state.league.ifBlank { "Unknown League" },
                commenceTime = java.time.Instant.now().toString()
            )

            val stats = RecentStats(
                homeTeamForm = state.homeForm.takeIf { it.isNotBlank() },
                awayTeamForm = state.awayForm.takeIf { it.isNotBlank() },
                headToHead = state.headToHead.takeIf { it.isNotBlank() },
                injuries = state.injuries.split(",").map { it.trim() }.filter { it.isNotBlank() }
            )

            when (val result = analysisService.analyzeMatch(matchData, stats)) {
                is AnalysisResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            matchData = matchData,
                            analysisResult = result.result,
                            error = null
                        )
                    }
                }
                is AnalysisResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
                is AnalysisResult.Loading -> {
                    // Already handled by isLoading state
                }
            }
        }
    }

    /**
     * Clear the current analysis.
     */
    fun clearAnalysis() {
        _uiState.update {
            it.copy(
                analysisResult = null,
                matchData = null,
                error = null
            )
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Reset all fields.
     */
    fun resetForm() {
        _uiState.value = AnalysisUiState()
    }

    /**
     * Load sample data for testing.
     */
    fun loadSampleData() {
        _uiState.update {
            it.copy(
                homeTeam = "Manchester United",
                awayTeam = "Liverpool",
                homeOdds = "2.45",
                drawOdds = "3.40",
                awayOdds = "2.90",
                league = "Premier League",
                homeForm = "WWLDW",
                awayForm = "WDWWL",
                headToHead = "Man Utd 2W, Liverpool 3W, 1D in last 6",
                injuries = "Marcus Rashford (doubtful)"
            )
        }
    }
}
