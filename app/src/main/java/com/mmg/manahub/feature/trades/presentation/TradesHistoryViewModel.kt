package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.model.TradeStatus
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeHistoryUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter { ALL, COMPLETED, CANCELLED, DECLINED }

data class TradesHistoryUiState(
    val proposals: List<TradeProposal> = emptyList(),
    val currentUserId: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val navigateToThread: Pair<String, String>? = null,
) {
    val filtered: List<TradeProposal> get() = when (filter) {
        HistoryFilter.ALL -> proposals
        HistoryFilter.COMPLETED -> proposals.filter { it.status == TradeStatus.COMPLETED }
        HistoryFilter.CANCELLED -> proposals.filter { it.status == TradeStatus.CANCELLED }
        HistoryFilter.DECLINED -> proposals.filter { it.status == TradeStatus.DECLINED }
    }
}

@HiltViewModel
class TradesHistoryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val getHistory: GetTradeHistoryUseCase,
    private val refreshTrades: RefreshTradesUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradesHistoryUiState())
    val uiState: StateFlow<TradesHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.Authenticated) {
                    _uiState.update { it.copy(currentUserId = state.user.id) }
                }
            }
        }
        viewModelScope.launch {
            getHistory()
                .catch { _uiState.update { s -> s.copy(isLoading = false) } }
                .collect { list ->
                    _uiState.update { s -> s.copy(proposals = list, isLoading = false) }
                }
        }
    }

    fun onFilterSelected(filter: HistoryFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun onProposalClick(proposal: TradeProposal) {
        _uiState.update {
            it.copy(navigateToThread = Pair(proposal.id, proposal.rootProposalId))
        }
    }

    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isRefreshing = true) }
            refreshTrades(_uiState.value.currentUserId)
                .onFailure { e -> _uiState.update { s -> s.copy(snackbarMessage = e.toUserFacingMessage()) } }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun onSnackbarDismissed() = _uiState.update { it.copy(snackbarMessage = null) }
    fun onNavigationConsumed() = _uiState.update { it.copy(navigateToThread = null) }
}
