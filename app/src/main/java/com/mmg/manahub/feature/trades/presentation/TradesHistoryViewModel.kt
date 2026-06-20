package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.model.TradeStatus
import com.mmg.manahub.feature.trades.domain.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.usecase.GetActiveTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeHistoryUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter { ALL, ACTIVE, COMPLETED, DECLINED }

data class TradesHistoryUiState(
    val proposals: List<TradeProposal> = emptyList(),
    val currentUserId: String = "",
    val friends: List<Friend> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val navigateToThread: Pair<String, String>? = null,
    val lastRefreshedAt: Long = 0L,
    /** True only when the user has an active authenticated session. */
    val isLoggedIn: Boolean = false,
) {
    val filtered: List<TradeProposal> get() = when (filter) {
        HistoryFilter.ALL      -> proposals
        HistoryFilter.ACTIVE   -> proposals.filter { it.status.isActive }
        HistoryFilter.COMPLETED -> proposals.filter { it.status == TradeStatus.COMPLETED }
        // Declined covers all rejection/cancellation terminal states
        HistoryFilter.DECLINED -> proposals.filter {
            it.status in setOf(
                TradeStatus.DECLINED, TradeStatus.CANCELLED, TradeStatus.REVOKED, TradeStatus.COUNTERED,
            )
        }
    }
}

private const val CACHE_TTL_MS = 5 * 60 * 1_000L

@HiltViewModel
class TradesHistoryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val getActive: GetActiveTradesUseCase,
    private val getHistory: GetTradeHistoryUseCase,
    private val refreshTrades: RefreshTradesUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradesHistoryUiState())
    val uiState: StateFlow<TradesHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                val isAuthenticated = state is SessionState.Authenticated
                _uiState.update { it.copy(isLoggedIn = isAuthenticated) }
                if (isAuthenticated) {
                    val userId = (state as SessionState.Authenticated).user.id
                    val firstAuth = _uiState.value.currentUserId.isBlank()
                    _uiState.update { it.copy(currentUserId = userId) }
                    if (firstAuth) refresh()
                }
            }
        }
        // Combine active + historical proposals into one list sorted by most recent first.
        viewModelScope.launch {
            combine(getActive(), getHistory()) { active, history ->
                (active + history).sortedByDescending { it.updatedAt }
            }
                .catch { _uiState.update { s -> s.copy(isLoading = false) } }
                .collect { allProposals ->
                    _uiState.update { s -> s.copy(proposals = allProposals, isLoading = false) }
                }
        }
        viewModelScope.launch {
            friendRepository.observeFriends()
                .catch { /* friends are supplementary display data; ignore errors */ }
                .collect { friends -> _uiState.update { it.copy(friends = friends) } }
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
        val userId = _uiState.value.currentUserId
        if (userId.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isRefreshing = true) }
            refreshTrades(userId)
                .onSuccess { _uiState.update { s -> s.copy(lastRefreshedAt = System.currentTimeMillis()) } }
                .onFailure { e -> _uiState.update { s -> s.copy(snackbarMessage = e.toUserFacingMessage()) } }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun refreshIfStale() {
        val state = _uiState.value
        if (state.isRefreshing) return
        val age = System.currentTimeMillis() - state.lastRefreshedAt
        if (age > CACHE_TTL_MS) refresh()
    }

    fun onSnackbarDismissed() = _uiState.update { it.copy(snackbarMessage = null) }
    fun onNavigationConsumed() = _uiState.update { it.copy(navigateToThread = null) }
}
