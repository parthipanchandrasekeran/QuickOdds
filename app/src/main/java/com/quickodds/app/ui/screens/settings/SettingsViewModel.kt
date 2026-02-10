package com.quickodds.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickodds.app.billing.BillingRepository
import com.quickodds.app.data.preferences.ThemeMode
import com.quickodds.app.data.preferences.ThemePreferences
import com.quickodds.app.data.repository.UsageLimitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isSubscribed: Boolean = false,
    val remainingScans: Int = 0,
    val remainingAnalyzes: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val usageLimitRepository: UsageLimitRepository,
    val billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            themePreferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            billingRepository.isSubscribed.collect { subscribed ->
                _uiState.update { it.copy(isSubscribed = subscribed) }
            }
        }
        refreshUsage()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    remainingScans = usageLimitRepository.getRemainingScans(),
                    remainingAnalyzes = usageLimitRepository.getRemainingAnalyzes()
                )
            }
        }
    }
}
