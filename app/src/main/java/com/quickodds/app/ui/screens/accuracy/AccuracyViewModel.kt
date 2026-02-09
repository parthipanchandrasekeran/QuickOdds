package com.quickodds.app.ui.screens.accuracy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.data.local.dao.PredictionRecordDao
import com.quickodds.app.data.local.dao.SportAccuracy
import com.quickodds.app.data.local.dao.TierAccuracy
import com.quickodds.app.data.local.entity.PredictionRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentAccuracy(
    val agentName: String,
    val total: Int,
    val correct: Int
) {
    val accuracy: Double get() = if (total > 0) correct.toDouble() / total else 0.0
}

data class AccuracyUiState(
    val totalPredictions: Int = 0,
    val settledPredictions: Int = 0,
    val correctPredictions: Int = 0,
    val overallAccuracy: Double = 0.0,
    val sportAccuracies: List<SportAccuracy> = emptyList(),
    val confidenceTierAccuracies: List<TierAccuracy> = emptyList(),
    val agentAccuracies: List<AgentAccuracy> = emptyList(),
    val recentPredictions: List<PredictionRecord> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AccuracyViewModel @Inject constructor(
    private val predictionRecordDao: PredictionRecordDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccuracyUiState())
    val uiState: StateFlow<AccuracyUiState> = _uiState.asStateFlow()

    init {
        observeAccuracyData()
        loadAgentAccuracy()
    }

    private fun observeAccuracyData() {
        // Combine all reactive flows
        viewModelScope.launch {
            combine(
                predictionRecordDao.observeTotalCount(),
                predictionRecordDao.observeSettledCount(),
                predictionRecordDao.observeCorrectCount(),
                predictionRecordDao.observeAccuracyBySport(),
                predictionRecordDao.observeAccuracyByConfidenceTier()
            ) { total, settled, correct, sports, tiers ->
                _uiState.value.copy(
                    totalPredictions = total,
                    settledPredictions = settled,
                    correctPredictions = correct,
                    overallAccuracy = if (settled > 0) correct.toDouble() / settled else 0.0,
                    sportAccuracies = sports,
                    confidenceTierAccuracies = tiers,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Recent predictions
        viewModelScope.launch {
            predictionRecordDao.observeRecentSettled(20).collect { recent ->
                _uiState.update { it.copy(recentPredictions = recent) }
            }
        }
    }

    private fun loadAgentAccuracy() {
        viewModelScope.launch {
            try {
                val statTotal = predictionRecordDao.getStatModelerTotal()
                val statCorrect = predictionRecordDao.getStatModelerCorrect()
                val scoutTotal = predictionRecordDao.getProScoutTotal()
                val scoutCorrect = predictionRecordDao.getProScoutCorrect()
                val sharpTotal = predictionRecordDao.getMarketSharpTotal()
                val sharpCorrect = predictionRecordDao.getMarketSharpCorrect()

                val agents = listOf(
                    AgentAccuracy("Stat Modeler", statTotal, statCorrect),
                    AgentAccuracy("Pro Scout", scoutTotal, scoutCorrect),
                    AgentAccuracy("Market Sharp", sharpTotal, sharpCorrect)
                )

                _uiState.update { it.copy(agentAccuracies = agents) }
            } catch (_: Exception) {
                // Non-fatal — agent accuracy is supplementary
            }
        }
    }
}
