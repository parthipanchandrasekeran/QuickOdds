package com.quickodds.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.data.repository.BettingRepository
import com.quickodds.app.data.repository.MarketRepository
import com.quickodds.app.domain.BetAnalyzer
import com.quickodds.app.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val wallet: Wallet? = null,
    val featuredMarkets: List<Market> = emptyList(),
    val valueBets: List<Pair<Market, BetAnalysis>> = emptyList(),
    val pendingBetsCount: Int = 0,
    val error: String? = null
)

class DashboardViewModel(
    private val marketRepository: MarketRepository,
    private val bettingRepository: BettingRepository
) : ViewModel() {

    private val analyzer = BetAnalyzer()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
        observeWallet()
        observePendingBets()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load featured markets
            val marketsResult = marketRepository.getMarkets("football")
            marketsResult.onSuccess { markets ->
                val featured = markets.take(4)
                val valueBets = markets
                    .map { market -> market to analyzer.analyze(market) }
                    .filter { (_, analysis) -> analysis.isValueBet }
                    .sortedByDescending { (_, analysis) -> analysis.confidenceScore }
                    .take(3)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        featuredMarkets = featured,
                        valueBets = valueBets
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message)
                }
            }
        }
    }

    private fun observeWallet() {
        viewModelScope.launch {
            bettingRepository.getWallet().collect { wallet ->
                _uiState.update { it.copy(wallet = wallet) }
            }
        }
    }

    private fun observePendingBets() {
        viewModelScope.launch {
            bettingRepository.getPendingBetsCount().collect { count ->
                _uiState.update { it.copy(pendingBetsCount = count) }
            }
        }
    }

    fun refresh() {
        loadDashboard()
    }
}
