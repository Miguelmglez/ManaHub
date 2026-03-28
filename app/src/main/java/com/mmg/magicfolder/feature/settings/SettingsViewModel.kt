package com.mmg.magicfolder.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.PreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesDataStore.autoRefreshPricesFlow.collect { enabled ->
                _uiState.update { it.copy(autoRefreshPrices = enabled) }
            }
        }
    }

    fun onAutoRefreshChanged(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.saveAutoRefreshPrices(enabled)
            _uiState.update { it.copy(autoRefreshPrices = enabled) }
        }
    }
}
