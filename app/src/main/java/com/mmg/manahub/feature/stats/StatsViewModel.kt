package com.mmg.manahub.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStats:            GetCollectionStatsUseCase,
    private val refreshPricesUseCase: RefreshCollectionPricesUseCase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesDataStore.preferredCurrencyFlow
                .flatMapLatest { currency ->
                    _uiState.update { it.copy(currency = currency) }
                    getStats(currency)
                }
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { stats -> _uiState.update { it.copy(stats = stats, isLoading = false) } }
        }

        // Load persisted preferences into state
        viewModelScope.launch {
            combine(
                userPreferencesDataStore.lastPriceRefreshFlow,
                userPreferencesDataStore.autoRefreshPricesFlow,
            ) { lastRefresh, autoRefresh -> lastRefresh to autoRefresh }
                .collect { (lastRefresh, autoRefresh) ->
                    _uiState.update { it.copy(
                        lastRefreshedAt   = lastRefresh,
                        autoRefreshPrices = autoRefresh,
                    )}
                }
        }

        // Auto-refresh if enabled and last refresh was > 24 h ago
        viewModelScope.launch {
            val (autoRefresh, lastRefresh) = combine(
                userPreferencesDataStore.autoRefreshPricesFlow,
                userPreferencesDataStore.lastPriceRefreshFlow,
            ) { a, b -> a to b }.first()

            if (autoRefresh) {
                val elapsed = System.currentTimeMillis() - (lastRefresh ?: 0L)
                if (elapsed > 24 * 60 * 60 * 1000L) refreshPrices()
            }
        }
    }

    fun onCurrencyToggle() {
        viewModelScope.launch {
            val next = if (_uiState.value.currency == com.mmg.manahub.core.domain.model.PreferredCurrency.USD)
                com.mmg.manahub.core.domain.model.PreferredCurrency.EUR
            else 
                com.mmg.manahub.core.domain.model.PreferredCurrency.USD
            userPreferencesDataStore.savePreferredCurrency(next)
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun refreshPrices() {
        if (_uiState.value.isRefreshingPrices) return
        viewModelScope.launch {
            refreshPricesUseCase.invoke().collect { result ->
                when (result) {
                    is RefreshCollectionPricesUseCase.Result.Progress -> {
                        _uiState.update { it.copy(
                            isRefreshingPrices = true,
                            refreshProgress    = result.current to result.total,
                        )}
                    }
                    is RefreshCollectionPricesUseCase.Result.Success -> {
                        val now = System.currentTimeMillis()
                        userPreferencesDataStore.saveLastPriceRefresh(now)
                        val message = buildString {
                            append("Updated ${result.updatedCount} prices")
                            if (result.notFoundCount > 0)
                                append(" (${result.notFoundCount} not found)")
                        }
                        _uiState.update { it.copy(
                            isRefreshingPrices = false,
                            refreshProgress    = null,
                            lastRefreshedAt    = now,
                            refreshResult      = message,
                        )}
                    }
                    is RefreshCollectionPricesUseCase.Result.Error -> {
                        _uiState.update { it.copy(
                            isRefreshingPrices = false,
                            refreshProgress    = null,
                            refreshError       = result.message,
                        )}
                    }
                }
            }
        }
    }

    fun clearRefreshMessage() {
        _uiState.update { it.copy(refreshResult = null, refreshError = null) }
    }

    fun onAutoRefreshChanged(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.saveAutoRefreshPrices(enabled)
            _uiState.update { it.copy(autoRefreshPrices = enabled) }
        }
    }
}
