package com.quickodds.app.ui.screens.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.data.repository.BettingRepository
import com.quickodds.app.domain.model.Transaction
import com.quickodds.app.domain.model.Wallet
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WalletUiState(
    val isLoading: Boolean = true,
    val wallet: Wallet? = null,
    val transactions: List<Transaction> = emptyList(),
    val depositAmount: String = "",
    val showDepositDialog: Boolean = false,
    val error: String? = null
)

class WalletViewModel(
    private val bettingRepository: BettingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        observeWallet()
        observeTransactions()
    }

    private fun observeWallet() {
        viewModelScope.launch {
            bettingRepository.getWallet().collect { wallet ->
                _uiState.update {
                    it.copy(isLoading = false, wallet = wallet)
                }
            }
        }
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            bettingRepository.getTransactions().collect { transactions ->
                _uiState.update { it.copy(transactions = transactions) }
            }
        }
    }

    fun showDepositDialog() {
        _uiState.update { it.copy(showDepositDialog = true) }
    }

    fun hideDepositDialog() {
        _uiState.update { it.copy(showDepositDialog = false, depositAmount = "") }
    }

    fun updateDepositAmount(amount: String) {
        if (amount.isEmpty() || amount.toDoubleOrNull() != null) {
            _uiState.update { it.copy(depositAmount = amount) }
        }
    }

    fun deposit() {
        val amount = _uiState.value.depositAmount.toDoubleOrNull() ?: return

        viewModelScope.launch {
            bettingRepository.deposit(amount)
            hideDepositDialog()
        }
    }
}
