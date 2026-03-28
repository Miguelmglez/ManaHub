package com.mmg.magicfolder.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.PreferencesDataStore
import com.mmg.magicfolder.core.domain.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.magicfolder.core.domain.usecase.stats.GetCollectionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStats:            GetCollectionStatsUseCase,
    private val refreshPricesUseCase: RefreshCollectionPricesUseCase,
    private val preferencesDataStore: PreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getStats()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { stats -> _uiState.update { it.copy(stats = stats, isLoading = false) } }
        }

        // Load persisted preferences into state
        viewModelScope.launch {
            combine(
                preferencesDataStore.lastPriceRefreshFlow,
                preferencesDataStore.autoRefreshPricesFlow,
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
                preferencesDataStore.autoRefreshPricesFlow,
                preferencesDataStore.lastPriceRefreshFlow,
            ) { a, b -> a to b }.first()

            if (autoRefresh) {
                val elapsed = System.currentTimeMillis() - (lastRefresh ?: 0L)
                if (elapsed > 24 * 60 * 60 * 1000L) refreshPrices()
            }
        }
    }

    fun onCurrencyToggle() {
        _uiState.update {
            it.copy(currency = if (it.currency == Currency.USD) Currency.EUR else Currency.USD)
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
                        preferencesDataStore.saveLastPriceRefresh(now)
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
            preferencesDataStore.saveAutoRefreshPrices(enabled)
            _uiState.update { it.copy(autoRefreshPrices = enabled) }
        }
    }
}
