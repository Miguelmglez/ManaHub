package com.mmg.manahub.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStats:            GetCollectionStatsUseCase,
    private val refreshPricesUseCase: RefreshCollectionPricesUseCase,
    private val userPreferencesDataStore: UserPreferencesRepository,
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

        viewModelScope.launch {
            userPreferencesDataStore.lastPriceRefreshFlow.collect { lastRefresh ->
                _uiState.update { it.copy(lastRefreshedAt = lastRefresh) }
            }
        }
    }

    fun onCurrencyToggle() {
        viewModelScope.launch {
            val next = if (_uiState.value.currency == com.mmg.manahub.core.domain.model.PreferredCurrency.USD)
                com.mmg.manahub.core.domain.model.PreferredCurrency.EUR
            else 
                com.mmg.manahub.core.domain.model.PreferredCurrency.USD
            userPreferencesDataStore.setPreferredCurrency(next)
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

}
