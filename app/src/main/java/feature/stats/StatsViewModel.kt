package feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.domain.usecase.stats.GetCollectionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStats: GetCollectionStatsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getStats()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { stats -> _uiState.update { it.copy(stats = stats, isLoading = false) } }
        }
    }

    fun onCurrencyToggle() {
        _uiState.update {
            it.copy(currency = if (it.currency == Currency.USD) Currency.EUR else Currency.USD)
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}
