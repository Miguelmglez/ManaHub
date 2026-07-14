package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.model.TradeError
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.usecase.AcceptProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CancelProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.DeclineProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MarkCompletedUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RevokeAcceptanceUseCase
import com.mmg.manahub.feature.trades.domain.usecase.UpdateTradeCollectionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class NegotiationError {
    data class CardAlreadyLocked(val cardIds: List<String>) : NegotiationError()
    object ProposalVersionMismatch : NegotiationError()
    object InventoryGone : NegotiationError()
    data class Generic(val message: String?) : NegotiationError()
}

data class EditorNavArgs(
    val receiverId: String,
    val proposalId: String,
    val rootProposalId: String,
    val isCounter: Boolean,
)

/**
 * One-shot events emitted by [TradeNegotiationViewModel].
 *
 * Delivered through a buffered [Channel] rather than a nullable `StateFlow` field
 * (project standard — see CLAUDE.md Playtest section): a `StateFlow` equality-collapses
 * two consecutive identical events (e.g. two "collection update failed" toasts back to
 * back) and can drop events emitted while the screen's lifecycle is paused.
 *
 * Events carry semantic outcomes only, never literal display text or sentinel keys —
 * [TradeNegotiationDetailScreen] resolves every [android.content.res.Resources] string in
 * ONE place, killing the previous "screen must know the VM's magic strings" coupling
 * (e.g. the old `snackbarMessage = "collection_updated"` sentinel).
 */
sealed class NegotiationEvent {
    /** Navigate to the counter/edit proposal editor. */
    data class NavigateToEditor(val args: EditorNavArgs) : NegotiationEvent()

    /**
     * Outcome of a collection sync tied to this proposal — fired by the explicit
     * "Update Collection" button AND by the automatic collection update/reverse that
     * can run as part of marking a trade completed or revoking it.
     */
    data class CollectionSyncResult(val success: Boolean) : NegotiationEvent()

    /**
     * A failure message already resolved to user-facing text via [toUserFacingMessage].
     * Null falls back to a generic error string resolved by the screen.
     */
    data class ShowError(val message: String?) : NegotiationEvent()
}

data class NegotiationUiState(
    val thread: List<TradeProposal> = emptyList(),
    val currentUserId: String = "",
    /** userId → display name; populated from auth session (current user) and friends list. */
    val participantNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isProcessing: Boolean = false,
    val errorDialog: NegotiationError? = null,
    val pendingMarkCompletedProposalId: String? = null,
    val pendingMarkCompletedSentItems: List<TradeItem> = emptyList(),
    val pendingMarkCompletedReceivedItems: List<TradeItem> = emptyList(),
    /** Proposal ID waiting for revoke confirmation. */
    val pendingRevokeProposalId: String? = null,
    /** True if the user already synced the collection for the pending-revoke proposal. */
    val pendingRevokeHasSynced: Boolean = false,
    val pendingCancelProposalId: String? = null,
    /**
     * Set when the user taps Accept on a proposal where the other party has only included
     * "Review my collection" (no concrete card items). Stores the proposal ID until the user
     * confirms or dismisses the gift-warning dialog.
     */
    val pendingGiftAcceptProposalId: String? = null,
    /**
     * Set of proposal IDs for which the current user has already run
     * "Update Collection". Populated reactively from Room via
     * [TradeCollectionSyncDao.observeSyncedProposalIds].
     */
    val syncedCollectionProposalIds: Set<String> = emptySet(),
    /** True while [UpdateTradeCollectionUseCase] is executing, to disable the button. */
    val isSyncingCollection: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TradeNegotiationViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val getThread: GetTradeThreadUseCase,
    private val refreshTradeThread: RefreshTradeThreadUseCase,
    private val acceptProposal: AcceptProposalUseCase,
    private val declineProposal: DeclineProposalUseCase,
    private val cancelProposal: CancelProposalUseCase,
    private val revokeAcceptance: RevokeAcceptanceUseCase,
    private val markCompleted: MarkCompletedUseCase,
    private val updateTradeCollection: UpdateTradeCollectionUseCase,
    private val tradeCollectionSyncDao: TradeCollectionSyncDao,
    private val analyticsHelper: AnalyticsHelper,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val rootProposalId: String = savedStateHandle["rootProposalId"] ?: ""

    private val _uiState = MutableStateFlow(NegotiationUiState(isLoading = true))
    val uiState: StateFlow<NegotiationUiState> = _uiState.asStateFlow()

    private val _events = Channel<NegotiationEvent>(Channel.BUFFERED)
    val events: Flow<NegotiationEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Note (§6.4): no `.catch` here — `sessionState` is a `StateFlow`, which by
            // contract never throws from its own emission machinery. A `.catch` operator
            // chained directly onto a StateFlow/SharedFlow is dead code (the compiler
            // flags it: "SharedFlow.catch has no effect"). Genuinely-throwing sources in
            // this ViewModel (Room/repo flows below) each carry their own `.catch`.
            authRepository.sessionState
                .collect { state ->
                    if (state is SessionState.Authenticated) {
                        val userId = state.user.id
                        val firstAuth = _uiState.value.currentUserId.isBlank()
                        val nickname = state.user.nickname ?: ""
                        _uiState.update { s ->
                            s.copy(
                                currentUserId = userId,
                                participantNames = s.participantNames + (userId to nickname),
                            )
                        }
                        if (firstAuth) refresh()
                    }
                }
        }
        viewModelScope.launch {
            friendRepository.observeFriends()
                .catch { /* friends are supplementary for display only */ }
                .collect { friends ->
                    val names = friends.associate { it.userId to it.nickname }
                    _uiState.update { s -> s.copy(participantNames = s.participantNames + names) }
                }
        }
        viewModelScope.launch {
            getThread(rootProposalId)
                .catch { _uiState.update { s -> s.copy(isLoading = false) } }
                .collect { thread ->
                    _uiState.update { s -> s.copy(thread = thread, isLoading = false) }
                }
        }
        // Observe which proposals have already been collection-synced by this user.
        //
        // §2.4 fix: previously this nested a second `.collect { }` INSIDE the outer
        // `sessionState.collect { }`, so the outer collector never advanced past its
        // first `Authenticated` emission — a sign-out/sign-in as a different account
        // never re-targeted the DAO query. `flatMapLatest` cancels the previous DAO
        // flow and re-subscribes for the new user id on every session change instead.
        viewModelScope.launch {
            authRepository.sessionState
                .filterIsInstance<SessionState.Authenticated>()
                .map { it.user.id }
                .distinctUntilChanged()
                .flatMapLatest { userId -> tradeCollectionSyncDao.observeSyncedProposalIds(userId) }
                .catch { /* non-critical — sync state is best-effort */ }
                .collect { ids ->
                    _uiState.update { s -> s.copy(syncedCollectionProposalIds = ids.toSet()) }
                }
        }
    }

    fun onAccept(proposalId: String) {
        if (_uiState.value.isProcessing) return
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val isProposer = proposal.proposerId == currentUserId
        // Determine whether the other party's side is a gift:
        // they have the review-collection flag set but zero concrete card items.
        val otherPartyId = if (isProposer) proposal.receiverId else proposal.proposerId
        val otherPartyReviewFlag = if (isProposer) {
            proposal.includesReviewCollectionFromReceiver
        } else {
            proposal.includesReviewCollectionFromProposer
        }
        val otherPartyActualItems = proposal.items.filter {
            it.fromUserId == otherPartyId && !it.isReviewCollectionPlaceholder
        }
        val isGiftTrade = otherPartyReviewFlag && otherPartyActualItems.isEmpty()
        if (isGiftTrade) {
            _uiState.update { it.copy(pendingGiftAcceptProposalId = proposalId) }
            return
        }
        doAccept(proposalId)
    }

    /** Called when the user confirms accepting a gift trade via the warning dialog. */
    fun onGiftAcceptConfirmed() {
        val proposalId = _uiState.value.pendingGiftAcceptProposalId ?: return
        _uiState.update { it.copy(pendingGiftAcceptProposalId = null) }
        doAccept(proposalId)
    }

    /** Called when the user dismisses the gift-accept warning dialog. */
    fun onGiftAcceptDismissed() {
        _uiState.update { it.copy(pendingGiftAcceptProposalId = null) }
    }

    /**
     * §2.7 fix: acquires the [NegotiationUiState.isProcessing] lock synchronously via a
     * single [MutableStateFlow.update] call BEFORE launching the coroutine. The previous
     * code checked `_uiState.value.isProcessing` on the caller's thread and only flipped
     * the flag to `true` from inside the (asynchronously dispatched) coroutine — two fast
     * taps could both read `false` and both proceed. `update` is atomic under concurrent
     * callers, so only one caller ever wins the compare-and-set.
     */
    private fun doAccept(proposalId: String) {
        var acquired = false
        _uiState.update { state ->
            if (state.isProcessing) state else { acquired = true; state.copy(isProcessing = true) }
        }
        if (!acquired) return
        viewModelScope.launch(ioDispatcher) {
            FirebaseCrashlytics.getInstance().log("trade_accept_started: proposal=$proposalId")
            acceptProposal(proposalId)
                .onSuccess {
                    analyticsHelper.logEvent("trade_accepted", mapOf("root_proposal_id" to rootProposalId))
                    refresh()
                }
                .onFailure { e ->
                    val error = when (e) {
                        is TradeError.CardAlreadyLocked -> NegotiationError.CardAlreadyLocked(e.cardIds)
                        is TradeError.CannotAcceptReviewCollection -> NegotiationError.Generic(e.toUserFacingMessage())
                        else -> {
                            FirebaseCrashlytics.getInstance().apply {
                                log("trade_accept_failed: proposal=$proposalId")
                                setCustomKey("trade_root_proposal_id", rootProposalId)
                                recordException(e)
                            }
                            NegotiationError.Generic(e.toUserFacingMessage())
                        }
                    }
                    _uiState.update { it.copy(errorDialog = error) }
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onDecline(proposalId: String) {
        var acquired = false
        _uiState.update { state ->
            if (state.isProcessing) state else { acquired = true; state.copy(isProcessing = true) }
        }
        if (!acquired) return
        viewModelScope.launch(ioDispatcher) {
            declineProposal(proposalId)
                .onSuccess { refresh() }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_decline_failed: proposal=$proposalId")
                        setCustomKey("trade_root_proposal_id", rootProposalId)
                        recordException(e)
                    }
                    _events.trySend(NegotiationEvent.ShowError(e.toUserFacingMessage()))
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onCancelRequested(proposalId: String) {
        _uiState.update { it.copy(pendingCancelProposalId = proposalId) }
    }

    fun onCancelConfirmed() {
        val proposalId = _uiState.value.pendingCancelProposalId ?: return
        _uiState.update { it.copy(pendingCancelProposalId = null) }
        onCancel(proposalId)
    }

    fun onCancelDismissed() {
        _uiState.update { it.copy(pendingCancelProposalId = null) }
    }

    private fun onCancel(proposalId: String) {
        var acquired = false
        _uiState.update { state ->
            if (state.isProcessing) state else { acquired = true; state.copy(isProcessing = true) }
        }
        if (!acquired) return
        viewModelScope.launch(ioDispatcher) {
            cancelProposal(proposalId)
                .onSuccess { refresh() }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_cancel_failed: proposal=$proposalId")
                        setCustomKey("trade_root_proposal_id", rootProposalId)
                        recordException(e)
                    }
                    _events.trySend(NegotiationEvent.ShowError(e.toUserFacingMessage()))
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onRevoke(proposalId: String) {
        if (_uiState.value.isProcessing) return
        val hasSynced = proposalId in _uiState.value.syncedCollectionProposalIds
        _uiState.update { it.copy(
            pendingRevokeProposalId = proposalId,
            pendingRevokeHasSynced = hasSynced,
        )}
    }

    fun onRevokeDismissed() {
        _uiState.update { it.copy(pendingRevokeProposalId = null, pendingRevokeHasSynced = false) }
    }

    fun onRevokeConfirmed(reverseCollection: Boolean) {
        val proposalId = _uiState.value.pendingRevokeProposalId ?: return
        _uiState.update { it.copy(pendingRevokeProposalId = null, pendingRevokeHasSynced = false) }
        doRevoke(proposalId, reverseCollection)
    }

    private fun doRevoke(proposalId: String, reverseCollection: Boolean) {
        val userId = _uiState.value.currentUserId
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        var acquired = false
        _uiState.update { state ->
            if (state.isProcessing) state else { acquired = true; state.copy(isProcessing = true) }
        }
        if (!acquired) return
        viewModelScope.launch(ioDispatcher) {
            revokeAcceptance(proposalId)
                .onSuccess {
                    if (reverseCollection) {
                        val sentItems = proposal.items.filter {
                            it.fromUserId == userId && !it.isReviewCollectionPlaceholder
                        }
                        val receivedItems = proposal.items.filter {
                            it.toUserId == userId && !it.isReviewCollectionPlaceholder
                        }
                        // §2.5 fix: the Result from this automatic collection reversal used
                        // to be discarded — a failure here left the collection out of sync
                        // with the revoked trade, silently, with no Crashlytics signal.
                        updateTradeCollection(proposalId, userId, sentItems, receivedItems, reverse = true)
                            .onSuccess { _events.trySend(NegotiationEvent.CollectionSyncResult(success = true)) }
                            .onFailure { e ->
                                recordNonFatal("trade_revoke_collection_reverse_failed: proposal=$proposalId", e)
                                _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
                            }
                    }
                    FirebaseCrashlytics.getInstance().log("trade_revoked: proposal=$proposalId")
                    refresh()
                }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_revoke_failed: proposal=$proposalId")
                        setCustomKey("trade_root_proposal_id", rootProposalId)
                        recordException(e)
                    }
                    _events.trySend(NegotiationEvent.ShowError(e.toUserFacingMessage()))
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onMarkCompleted(proposalId: String) {
        if (_uiState.value.isProcessing) return
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val sentItems = proposal.items.filter { it.fromUserId == currentUserId && !it.isReviewCollectionPlaceholder }
        val receivedItems = proposal.items.filter { it.toUserId == currentUserId && !it.isReviewCollectionPlaceholder }
        _uiState.update { it.copy(
            pendingMarkCompletedProposalId = proposalId,
            pendingMarkCompletedSentItems = sentItems,
            pendingMarkCompletedReceivedItems = receivedItems,
        )}
    }

    fun onConfirmMarkCompleted(addToCollection: Boolean) {
        val proposalId = _uiState.value.pendingMarkCompletedProposalId ?: return
        val sentItems = _uiState.value.pendingMarkCompletedSentItems
        val receivedItems = _uiState.value.pendingMarkCompletedReceivedItems
        _uiState.update { it.copy(
            pendingMarkCompletedProposalId = null,
            pendingMarkCompletedSentItems = emptyList(),
            pendingMarkCompletedReceivedItems = emptyList(),
        )}
        doMarkCompleted(proposalId, addToCollection, sentItems, receivedItems)
    }

    fun onDismissMarkCompletedDialog() {
        _uiState.update { it.copy(
            pendingMarkCompletedProposalId = null,
            pendingMarkCompletedSentItems = emptyList(),
            pendingMarkCompletedReceivedItems = emptyList(),
        )}
    }

    private fun doMarkCompleted(
        proposalId: String,
        addToCollection: Boolean,
        sentItems: List<TradeItem>,
        receivedItems: List<TradeItem>,
    ) {
        val userId = _uiState.value.currentUserId
        var acquired = false
        _uiState.update { state ->
            if (state.isProcessing) state else { acquired = true; state.copy(isProcessing = true) }
        }
        if (!acquired) return
        viewModelScope.launch(ioDispatcher) {
            FirebaseCrashlytics.getInstance().log("trade_mark_completed_started: proposal=$proposalId")
            markCompleted(proposalId)
                .onSuccess {
                    if (addToCollection) {
                        // §2.5 fix: fold the Result instead of discarding it — a failed
                        // collection update after a successful "mark completed" used to
                        // leave the user's collection stale with zero feedback or signal.
                        updateTradeCollection(proposalId, userId, sentItems, receivedItems)
                            .onSuccess { _events.trySend(NegotiationEvent.CollectionSyncResult(success = true)) }
                            .onFailure { e ->
                                recordNonFatal("trade_mark_completed_collection_update_failed: proposal=$proposalId", e)
                                _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
                            }
                    }
                    analyticsHelper.logEvent("trade_completed", mapOf(
                        "root_proposal_id" to rootProposalId,
                        "added_to_collection" to addToCollection,
                    ))
                    FirebaseCrashlytics.getInstance().log("trade_mark_completed_success: proposal=$proposalId")
                    refresh()
                }
                .onFailure { e ->
                    val error = when (e) {
                        is TradeError.InventoryGone -> NegotiationError.InventoryGone
                        else -> {
                            FirebaseCrashlytics.getInstance().apply {
                                log("trade_mark_completed_failed: proposal=$proposalId")
                                setCustomKey("trade_root_proposal_id", rootProposalId)
                                recordException(e)
                            }
                            NegotiationError.Generic(e.toUserFacingMessage())
                        }
                    }
                    _uiState.update { it.copy(errorDialog = error) }
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onCounter(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val otherUserId = if (proposal.proposerId == currentUserId) proposal.receiverId else proposal.proposerId
        _events.trySend(NegotiationEvent.NavigateToEditor(EditorNavArgs(
            receiverId = otherUserId,
            proposalId = proposalId,
            rootProposalId = rootProposalId,
            isCounter = true,
        )))
    }

    fun onEdit(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val otherUserId = if (proposal.proposerId == currentUserId) proposal.receiverId else proposal.proposerId
        _events.trySend(NegotiationEvent.NavigateToEditor(EditorNavArgs(
            receiverId = otherUserId,
            proposalId = proposalId,
            rootProposalId = rootProposalId,
            isCounter = false,
        )))
    }

    /**
     * Triggered when the user taps "Update Collection" on a completed proposal.
     *
     * Deducts sent cards from the local collection and adds received cards, then
     * writes a [TradeCollectionSyncEntity] so the button is replaced with a
     * static "Collection updated" label on subsequent renders.
     */
    fun onUpdateCollection(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val userId = _uiState.value.currentUserId
        if (userId.isBlank()) return
        var acquired = false
        _uiState.update { state ->
            if (state.isSyncingCollection) state else { acquired = true; state.copy(isSyncingCollection = true) }
        }
        if (!acquired) return
        viewModelScope.launch(ioDispatcher) {
            val sentItems = proposal.items.filter {
                it.fromUserId == userId && !it.isReviewCollectionPlaceholder
            }
            val receivedItems = proposal.items.filter {
                it.toUserId == userId && !it.isReviewCollectionPlaceholder
            }
            updateTradeCollection(proposalId, userId, sentItems, receivedItems)
                .onSuccess {
                    analyticsHelper.logEvent(
                        "trade_collection_updated",
                        mapOf("proposal_id" to proposalId),
                    )
                    _uiState.update { s ->
                        s.copy(syncedCollectionProposalIds = s.syncedCollectionProposalIds + proposalId)
                    }
                    _events.trySend(NegotiationEvent.CollectionSyncResult(success = true))
                }
                .onFailure { e ->
                    // §5.1 fix: previously showed `e.message` verbatim (raw server text /
                    // sentinel key leaking to the UI). Now recorded for diagnosis and
                    // surfaced as a semantic event the screen resolves to a fixed string.
                    recordNonFatal("trade_collection_update_failed: proposal=$proposalId", e)
                    _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
                }
            _uiState.update { it.copy(isSyncingCollection = false) }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(errorDialog = null) }

    fun refresh() {
        val userId = _uiState.value.currentUserId
        if (userId.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isRefreshing = true) }
            refreshTradeThread(rootProposalId, userId)
                .onFailure { e -> _events.trySend(NegotiationEvent.ShowError(e.toUserFacingMessage())) }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
