package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import com.mmg.manahub.core.util.recordNonFatal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryFilter { ALL, ACTIVE, COMPLETED, DECLINED }

data class TradesHistoryUiState(
    /** The latest proposal of each thread (a counter-offer chain shows once), most recent first. */
    val proposals: List<TradeProposal> = emptyList(),
    val currentUserId: String = "",
    val friends: List<Friend> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    /** True until the first refresh finishes, so an empty cache never reads as "no trades". */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** True when the last refresh failed; with nothing cached the screen offers a retry. */
    val refreshFailed: Boolean = false,
    val lastRefreshedAt: Long = 0L,
    /** True only when the user has an active authenticated session. */
    val isLoggedIn: Boolean = false,
) {
    val filtered: List<TradeProposal> get() = when (filter) {
        HistoryFilter.ALL      -> proposals
        HistoryFilter.ACTIVE   -> proposals.filter { it.status.isActive }
        HistoryFilter.COMPLETED -> proposals.filter { it.status == TradeStatus.COMPLETED }
        // COUNTERED is a superseded step of a live thread, not a declined trade.
        HistoryFilter.DECLINED -> proposals.filter {
            it.status in setOf(TradeStatus.DECLINED, TradeStatus.CANCELLED, TradeStatus.REVOKED)
        }
    }
}

/**
 * One entry per thread: the newest proposal of each `rootProposalId` (the head of a counter-offer
 * chain), preferring a non-COUNTERED head, ordered by most recent activity first.
 */
internal fun latestPerThread(proposals: List<TradeProposal>): List<TradeProposal> =
    proposals
        .groupBy { it.rootProposalId.ifBlank { it.id } }
        .values
        .map { thread ->
            val order = compareBy<TradeProposal>({ it.createdAt }, { it.proposalVersion }, { it.updatedAt })
            thread.filter { it.status != TradeStatus.COUNTERED }.maxWithOrNull(order) ?: thread.maxWith(order)
        }
        .sortedByDescending { it.updatedAt }

/**
 * One-shot events for [TradesHistoryViewModel] (snackbar + navigation). Delivered via a buffered
 * [Channel] rather than nullable [MutableStateFlow] fields, per CLAUDE.md's Playtest-section
 * standard — see [TradesEvent] for the rationale. Collected via
 * `LaunchedEffect(Unit) { viewModel.events.collect { } }` in [TradesHistoryScreen].
 */
sealed class TradesHistoryEvent {
    /** A pre-resolved, user-facing message. A null message is intentionally dropped by the screen. */
    data class ShowMessage(val message: String?) : TradesHistoryEvent()
    data class NavigateToThread(val proposalId: String, val rootProposalId: String) : TradesHistoryEvent()
}

private const val CACHE_TTL_MS = 5 * 60 * 1_000L

class TradesHistoryViewModel(
    private val authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val tradesRepository: TradesRepository,
    private val refreshTrades: RefreshTradesUseCase,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradesHistoryUiState())
    val uiState: StateFlow<TradesHistoryUiState> = _uiState.asStateFlow()

    private val _events = Channel<TradesHistoryEvent>(Channel.BUFFERED)
    val events: Flow<TradesHistoryEvent> = _events.receiveAsFlow()

    // Declared before init: the Main.immediate session collector can start a refresh synchronously.
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                val isAuthenticated = state is SessionState.Authenticated
                // Signed out there is nothing to load; the screen shows its login prompt instead.
                _uiState.update { it.copy(isLoggedIn = isAuthenticated, isLoading = isAuthenticated && it.isLoading) }
                if (isAuthenticated) {
                    val userId = (state as SessionState.Authenticated).user.id
                    val previousUserId = _uiState.value.currentUserId
                    // A userId CHANGE (not just blank->set) must be treated as a fresh session:
                    // sign-out followed by sign-in as a DIFFERENT account must never reuse the
                    // previous account's cached proposals. The shared TradesRepository cache is a
                    // process-lifetime singleton with no per-user partitioning, so it is cleared
                    // explicitly here before the new account's first refresh (trades audit §2.10,
                    // 2026-07-10).
                    val isAccountSwitch = previousUserId.isNotBlank() && previousUserId != userId
                    if (isAccountSwitch) tradesRepository.clearCache()
                    val firstAuth = previousUserId.isBlank() || isAccountSwitch
                    _uiState.update {
                        if (isAccountSwitch) it.copy(currentUserId = userId, isLoading = true, lastRefreshedAt = 0L, refreshFailed = false)
                        else it.copy(currentUserId = userId)
                    }
                    if (firstAuth) startRefresh(userId, force = isAccountSwitch)
                } else {
                    // Sign-out: drop the shared cache so a guest (or the next account) browsing
                    // this screen never briefly sees the previous user's trade history.
                    tradesRepository.clearCache()
                    _uiState.update { it.copy(currentUserId = "") }
                }
            }
        }
        // Observe all proposals sorted by most recent first. Using observeAllProposals()
        // instead of combining getActive() and getHistory() prevents a race condition
        // where a proposal transitioning between active/terminal states could briefly
        // appear in both lists and crash the LazyColumn on a duplicate key.
        viewModelScope.launch {
            tradesRepository.observeAllProposals()
                .map(::latestPerThread)
                .flowOn(defaultDispatcher)
                .catch { e ->
                    recordNonFatal("trade_history_observe_failed", e)
                    _uiState.update { s -> s.copy(isLoading = false, refreshFailed = true) }
                }
                .collect { threads ->
                    // A cached list renders at once; an empty one keeps loading until the refresh ends.
                    _uiState.update { s -> s.copy(proposals = threads, isLoading = s.isLoading && threads.isEmpty()) }
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
        _events.trySend(TradesHistoryEvent.NavigateToThread(proposal.id, proposal.rootProposalId))
    }

    /** User-initiated refresh; a no-op while another refresh is in flight. */
    fun refresh() = startRefresh(_uiState.value.currentUserId, force = false)

    fun refreshIfStale() {
        val state = _uiState.value
        if (state.isRefreshing) return
        val age = System.currentTimeMillis() - state.lastRefreshedAt
        if (age > CACHE_TTL_MS) refresh()
    }

    /**
     * Single-flight refresh: `isRefreshing` is claimed atomically before launching, so the session
     * trigger and the screen's stale check can never both hit the network. [force] (account switch)
     * supersedes an in-flight refresh of the previous account.
     */
    private fun startRefresh(userId: String, force: Boolean) {
        if (userId.isBlank()) return
        var acquired = false
        _uiState.update { s ->
            if (s.isRefreshing && !force) s else { acquired = true; s.copy(isRefreshing = true) }
        }
        if (!acquired) return
        val previous = refreshJob
        refreshJob = viewModelScope.launch(ioDispatcher) {
            previous?.cancelAndJoin()
            val result = refreshTrades(userId)
            ensureActive()
            result.onFailure { e -> _events.trySend(TradesHistoryEvent.ShowMessage(e.toUserFacingMessage())) }
            _uiState.update { s ->
                s.copy(
                    isRefreshing = false,
                    isLoading = false,
                    refreshFailed = result.isFailure,
                    lastRefreshedAt = if (result.isSuccess) System.currentTimeMillis() else s.lastRefreshedAt,
                )
            }
        }
    }
}
