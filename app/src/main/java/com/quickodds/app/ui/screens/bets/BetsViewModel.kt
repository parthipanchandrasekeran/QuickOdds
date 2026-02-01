package com.quickodds.app.ui.screens.bets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.data.repository.BettingRepository
import com.quickodds.app.domain.model.Bet
import com.quickodds.app.domain.model.BetStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BetsUiState(
    val isLoading: Boolean = true,
    val allBets: List<Bet> = emptyList(),
    val error: String? = null
)

class BetsViewModel(
    private val bettingRepository: BettingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BetsUiState())
    val uiState: StateFlow<BetsUiState> = _uiState.asStateFlow()

    init {
        observeBets()
    }

    private fun observeBets() {
        viewModelScope.launch {
            bettingRepository.getAllBets().collect { bets ->
                _uiState.update {
                    it.copy(isLoading = false, allBets = bets)
                }
            }
        }
    }

    // For demo purposes - simulate settling bets
    fun settleBet(betId: String, won: Boolean) {
        viewModelScope.launch {
            bettingRepository.settleBet(betId, won)
        }
    }
}
