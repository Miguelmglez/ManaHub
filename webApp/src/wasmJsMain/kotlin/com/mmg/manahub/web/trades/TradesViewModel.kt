package com.mmg.manahub.web.trades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.remote.FriendshipClient
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.usecase.GetActiveTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeHistoryUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import com.mmg.manahub.web.common.toUserFacingMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Top-level tabs inside the web Trades feature (Android splits Active/History and the Wishlist/Open-for-Trade hub across two screens; this web MVP folds all four into one). */
enum class TradesTab { ACTIVE, HISTORY, WISHLIST, OPEN_FOR_TRADE }

data class TradesUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val currentUserId: String = "",
    val selectedTab: TradesTab = TradesTab.ACTIVE,
    val activeProposals: List<TradeProposal> = emptyList(),
    val historyProposals: List<TradeProposal> = emptyList(),
    val wishlist: List<WishlistEntry> = emptyList(),
    val openForTrade: List<OpenForTradeEntry> = emptyList(),
    /** userId -> display name, resolved for every proposer/receiver seen so far. */
    val participantNames: Map<String, String> = emptyMap(),
    val error: String? = null,
)

/**
 * Backs [TradesScreen] -- the web Trades hub: Active / History proposal lists plus minimal
 * Wishlist / Open-for-Trade read-only tabs (web scope expansion, Friends + Trades wave, approved
 * 2026-08-04). Proposal negotiation (accept/decline/counter/cancel/revoke) lives one level deeper,
 * in [TradeThreadViewModel] -- this VM only lists and routes into a thread.
 *
 * Follows the same `supabaseClient.auth.sessionStatus`-driven pattern as every other web VM/
 * repository: [refreshTrades] fires once per genuine sign-in (guarded by [refreshedForUserId], not
 * on every `Authenticated` re-emission such as a token refresh) -- mirrors Android's
 * `TradesViewModel.observeSession()` `distinctUntilChangedBy { user.id }` gate. The proposal/
 * wishlist/open-for-trade LISTS themselves are already-reactive `Flow`s from their repositories
 * (in-memory caches, no Room) -- this VM just collects and combines them.
 */
class TradesViewModel(
    private val supabaseClient: SupabaseClient,
    private val getActiveTrades: GetActiveTradesUseCase,
    private val getTradeHistory: GetTradeHistoryUseCase,
    private val refreshTrades: RefreshTradesUseCase,
    private val tradesRepository: TradesRepository,
    private val wishlistRepository: WishlistRepository,
    private val openForTradeRepository: OpenForTradeRepository,
    private val friendshipClient: FriendshipClient,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradesUiState())
    val uiState: StateFlow<TradesUiState> = _uiState.asStateFlow()

    private var refreshedForUserId: String? = null

    init {
        observeSession()
        observeLists()
    }

    private fun observeSession() {
        viewModelScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val userId = status.session.user?.id
                        _uiState.update { it.copy(isSignedIn = true, currentUserId = userId ?: it.currentUserId) }
                        if (userId != null && refreshedForUserId != userId) {
                            refreshedForUserId = userId
                            doRefresh(userId)
                        }
                    }
                    else -> _uiState.update { it.copy(isSignedIn = false, isLoading = false) }
                }
            }
        }
    }

    private fun observeLists() {
        combine(
            getActiveTrades(),
            getTradeHistory(),
            wishlistRepository.observeLocal(),
            openForTradeRepository.observeLocal(),
        ) { active, history, wishlist, oft -> TradeLists(active, history, wishlist, oft) }
            .distinctUntilChanged()
            .onEach { lists ->
                val names = resolveParticipantNames(lists.active + lists.history)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeProposals = lists.active,
                        historyProposals = lists.history,
                        wishlist = lists.wishlist,
                        openForTrade = lists.openForTrade,
                        participantNames = it.participantNames + names,
                    )
                }
            }
            .catch { _uiState.update { it.copy(isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private suspend fun doRefresh(userId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        refreshTrades(userId)
            .onSuccess { hydrateActiveItemCounts() }
            .onFailure { e -> _uiState.update { it.copy(error = e.toUserFacingMessage("load trades", crashReporter)) } }
        _uiState.update { it.copy(isLoading = false) }
    }

    /**
     * [TradesRepository.refreshProposals] only ever fetches proposal METADATA, never items (same
     * documented split Home's `hydrateTradeItemCounts` fans out over) -- without this, every list
     * row would show "0 items" until a thread is opened. Fans [TradesRepository.refreshItemsForThread]
     * out over every DISTINCT active root proposal id, capped at [MAX_ITEM_HYDRATION_THREADS] (this
     * is the dedicated Trades screen, not a compact Home widget, so the cap is generous rather than
     * Home's <=5 -- still bounded so an account with dozens of simultaneous negotiations can't fan
     * out unbounded concurrent requests).
     */
    private suspend fun hydrateActiveItemCounts() {
        val rootIds = _uiState.value.activeProposals.map { it.rootProposalId }.distinct().take(MAX_ITEM_HYDRATION_THREADS)
        coroutineScope {
            rootIds.map { rootId -> async { tradesRepository.refreshItemsForThread(rootId) } }.forEach { it.await() }
        }
    }

    fun refresh() {
        val userId = _uiState.value.currentUserId
        if (userId.isBlank()) return
        viewModelScope.launch { doRefresh(userId) }
    }

    fun onTabSelected(tab: TradesTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Resolves display names for any userId in [proposals] not already in
     * [TradesUiState.participantNames], via [FriendshipClient.getProfilesByIds] (the SAME endpoint
     * Android's `FriendRemoteDataSource` uses). Best-effort: a lookup failure just leaves those ids
     * unresolved (the screen falls back to a truncated raw id) rather than blocking the list.
     */
    private suspend fun resolveParticipantNames(proposals: List<TradeProposal>): Map<String, String> {
        val known = _uiState.value.participantNames.keys
        val currentUserId = _uiState.value.currentUserId
        val missing = proposals.flatMap { listOf(it.proposerId, it.receiverId) }
            .filter { it.isNotBlank() && it != currentUserId }
            .distinct()
            .filterNot { known.contains(it) }
        if (missing.isEmpty()) return emptyMap()
        return try {
            friendshipClient.getProfilesByIds(idFilter = "in.(${missing.joinToString(",")})")
                .associate { it.id to (it.nickname?.takeIf { n -> n.isNotBlank() } ?: it.gameTag ?: "Unknown") }
        } catch (e: Throwable) {
            crashReporter.recordException(e)
            emptyMap()
        }
    }

    private data class TradeLists(
        val active: List<TradeProposal>,
        val history: List<TradeProposal>,
        val wishlist: List<WishlistEntry>,
        val openForTrade: List<OpenForTradeEntry>,
    )

    private companion object {
        const val MAX_ITEM_HYDRATION_THREADS = 20
    }
}
