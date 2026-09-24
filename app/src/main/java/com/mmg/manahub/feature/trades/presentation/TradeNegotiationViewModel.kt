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
import com.mmg.manahub.core.data.local.entity.TradeCollectionSyncEntity
import com.mmg.manahub.core.model.TradeStatus
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    /** The trade is not COMPLETED yet: the collection will update once the other party completes it. */
    object CollectionApplyDeferred : NegotiationEvent()

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
    /** True until the first thread refresh finishes, so an empty cache never reads as "no proposals". */
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** True when the last refresh failed; with an empty [thread] the screen shows a retryable error. */
    val refreshFailed: Boolean = false,
    val isProcessing: Boolean = false,
    val errorDialog: NegotiationError? = null,
    val pendingMarkCompletedProposalId: String? = null,
    val pendingMarkCompletedSentItems: List<TradeItem> = emptyList(),
    val pendingMarkCompletedReceivedItems: List<TradeItem> = emptyList(),
    /** Proposal ID waiting for revoke confirmation. */
    val pendingRevokeProposalId: String? = null,
    /** True if the user already synced the collection for the pending-revoke proposal. */
    val pendingRevokeHasSynced: Boolean = false,
    /** False when the pending-revoke proposal's items are not loaded, so nothing can be reversed. */
    val pendingRevokeCanReverse: Boolean = true,
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
    /** Proposal IDs whose collection update was requested but waits for the trade to be COMPLETED. */
    val pendingApplyProposalIds: Set<String> = emptySet(),
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

    // Declared before init: the Main.immediate collectors launched there can run synchronously.
    // Guards against re-running a failed auto-apply on every emission of the same state.
    private val autoApplyAttempted = mutableSetOf<String>()
    // The first entry is covered by the session collector; later entries (back from the editor) refresh.
    private var hasEnteredScreen = false
    private var refreshJob: Job? = null

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
                        val previousUserId = _uiState.value.currentUserId
                        val isAccountSwitch = previousUserId.isNotBlank() && previousUserId != userId
                        val nickname = state.user.nickname?.takeIf { it.isNotBlank() }
                        _uiState.update { s ->
                            // Another account's dialogs and names must not survive a switch; the refresh re-scopes the thread.
                            val base = if (isAccountSwitch) NegotiationUiState(thread = s.thread, isLoading = true) else s
                            base.copy(
                                currentUserId = userId,
                                participantNames = if (nickname != null) base.participantNames + (userId to nickname) else base.participantNames,
                            )
                        }
                        if (isAccountSwitch) autoApplyAttempted.clear()
                        if (previousUserId.isBlank() || isAccountSwitch) startRefresh(userId, force = isAccountSwitch)
                    }
                }
        }
        viewModelScope.launch {
            friendRepository.observeFriends()
                .distinctUntilChanged()
                .catch { /* friends are supplementary for display only */ }
                .collect { friends ->
                    val names = friends.filter { it.nickname.isNotBlank() }.associate { it.userId to it.nickname }
                    _uiState.update { s -> s.copy(participantNames = s.participantNames + names) }
                }
        }
        viewModelScope.launch {
            getThread(rootProposalId)
                .distinctUntilChanged()
                .catch { e ->
                    recordNonFatal("trade_thread_observe_failed", e)
                    _uiState.update { s -> s.copy(isLoading = false, refreshFailed = true) }
                }
                .collect { thread ->
                    // A cached thread renders at once; an empty one keeps loading until the refresh ends.
                    _uiState.update { s -> s.copy(thread = thread, isLoading = s.isLoading && thread.isEmpty()) }
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
        // Applies a deferred collection update once the trade is seen COMPLETED; drops it when the
        // trade ended any other way.
        viewModelScope.launch {
            authRepository.sessionState
                .filterIsInstance<SessionState.Authenticated>()
                .map { it.user.id }
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    combine(getThread(rootProposalId), tradeCollectionSyncDao.observePendingApplyProposalIds(userId)) { thread, pending ->
                        Triple(userId, thread, pending.toSet())
                    }
                }
                .catch { e -> recordNonFatal("trade_pending_apply_observe_failed", e) }
                .collect { (userId, thread, pending) ->
                    _uiState.update { s -> s.copy(pendingApplyProposalIds = pending) }
                    resolvePendingApplies(userId, thread, pending)
                }
        }
    }

    private fun resolvePendingApplies(userId: String, thread: List<TradeProposal>, pending: Set<String>) {
        thread.filter { it.id in pending }.forEach { proposal ->
            when {
                proposal.status == TradeStatus.COMPLETED && proposal.itemsLoaded -> {
                    if (!autoApplyAttempted.add(proposal.id)) return@forEach
                    viewModelScope.launch(ioDispatcher) {
                        updateTradeCollection(proposal.id, userId, sentItemsOf(proposal, userId), receivedItemsOf(proposal, userId))
                            .onSuccess { _events.trySend(NegotiationEvent.CollectionSyncResult(success = true)) }
                            .onFailure { e ->
                                recordNonFatal("trade_pending_apply_failed", e)
                                _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
                            }
                    }
                }
                proposal.status.isTerminal && proposal.status != TradeStatus.COMPLETED ->
                    viewModelScope.launch(ioDispatcher) {
                        runCatching { tradeCollectionSyncDao.clearPendingApply(proposal.id, userId) }
                            .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e }
                    }
            }
        }
    }

    private fun sentItemsOf(proposal: TradeProposal, userId: String) =
        proposal.items.filter { it.fromUserId == userId && !it.isReviewCollectionPlaceholder }

    private fun receivedItemsOf(proposal: TradeProposal, userId: String) =
        proposal.items.filter { it.toUserId == userId && !it.isReviewCollectionPlaceholder }

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
            FirebaseCrashlytics.getInstance().log("trade_accept_started")
            acceptProposal(proposalId)
                .onSuccess {
                    analyticsHelper.logEvent("trade_accepted", emptyMap())
                    startRefresh(force = true)
                }
                .onFailure { e ->
                    val error = when (e) {
                        is TradeError.CardAlreadyLocked -> NegotiationError.CardAlreadyLocked(e.cardIds)
                        is TradeError.CannotAcceptReviewCollection -> NegotiationError.Generic(e.toUserFacingMessage())
                        else -> {
                            FirebaseCrashlytics.getInstance().apply {
                                log("trade_accept_failed")
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
                .onSuccess { startRefresh(force = true) }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_decline_failed")
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
                .onSuccess { startRefresh(force = true) }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_cancel_failed")
                        recordException(e)
                    }
                    _events.trySend(NegotiationEvent.ShowError(e.toUserFacingMessage()))
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onRevoke(proposalId: String) {
        if (_uiState.value.isProcessing) return
        val proposal = _uiState.value.thread.find { it.id == proposalId }
        val hasSynced = proposalId in _uiState.value.syncedCollectionProposalIds
        _uiState.update { it.copy(
            pendingRevokeProposalId = proposalId,
            pendingRevokeHasSynced = hasSynced,
            pendingRevokeCanReverse = proposal?.itemsLoaded == true,
        )}
    }

    fun onRevokeDismissed() {
        _uiState.update { it.copy(pendingRevokeProposalId = null, pendingRevokeHasSynced = false, pendingRevokeCanReverse = true) }
    }

    fun onRevokeConfirmed(reverseCollection: Boolean) {
        val proposalId = _uiState.value.pendingRevokeProposalId ?: return
        _uiState.update { it.copy(pendingRevokeProposalId = null, pendingRevokeHasSynced = false, pendingRevokeCanReverse = true) }
        doRevoke(proposalId, reverseCollection)
    }

    private fun doRevoke(proposalId: String, reverseCollection: Boolean) {
        val userId = _uiState.value.currentUserId
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        // Reversing from an unloaded item list would reverse nothing yet still drop the sync record.
        if (reverseCollection && !proposal.itemsLoaded) return
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
                                recordNonFatal("trade_revoke_collection_reverse_failed", e)
                                _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
                            }
                    }
                    runCatching { tradeCollectionSyncDao.clearPendingApply(proposalId, userId) }
                        .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e }
                    FirebaseCrashlytics.getInstance().log("trade_revoked")
                    startRefresh(force = true)
                }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_revoke_failed")
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
        if (!proposal.itemsLoaded) return
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
            FirebaseCrashlytics.getInstance().log("trade_mark_completed_started")
            markCompleted(proposalId)
                .onSuccess {
                    if (addToCollection) {
                        applyWhenCompleted(proposalId, userId, sentItems, receivedItems)
                    } else {
                        startRefresh(force = true)
                    }
                    analyticsHelper.logEvent("trade_completed", mapOf("added_to_collection" to addToCollection))
                    FirebaseCrashlytics.getInstance().log("trade_mark_completed_success")
                }
                .onFailure { e ->
                    val error = when (e) {
                        is TradeError.InventoryGone -> NegotiationError.InventoryGone
                        else -> {
                            FirebaseCrashlytics.getInstance().apply {
                                log("trade_mark_completed_failed")
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

    /**
     * Marking completed keeps the trade ACCEPTED until BOTH parties mark it, and it can still be
     * revoked meanwhile, so the collection changes only when the refreshed status is COMPLETED.
     * Otherwise the choice is persisted and applied when COMPLETED is observed.
     */
    private suspend fun applyWhenCompleted(
        proposalId: String,
        userId: String,
        sentItems: List<TradeItem>,
        receivedItems: List<TradeItem>,
    ) {
        refreshTradeThread(rootProposalId, userId)
            .onFailure { e -> recordNonFatal("trade_mark_completed_refresh_failed", e) }
        val refreshed = getThread(rootProposalId).first().find { it.id == proposalId }
        if (refreshed?.status == TradeStatus.COMPLETED) {
            val loaded = refreshed.takeIf { it.itemsLoaded }
            // §2.5 fix: fold the Result — a failed update must never leave the collection stale silently.
            updateTradeCollection(
                proposalId,
                userId,
                loaded?.let { sentItemsOf(it, userId) } ?: sentItems,
                loaded?.let { receivedItemsOf(it, userId) } ?: receivedItems,
            )
                .onSuccess { _events.trySend(NegotiationEvent.CollectionSyncResult(success = true)) }
                .onFailure { e ->
                    recordNonFatal("trade_mark_completed_collection_update_failed", e)
                    _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
                }
        } else {
            runCatching {
                tradeCollectionSyncDao.markPendingApply(
                    TradeCollectionSyncEntity(proposalId = proposalId, userId = userId, pendingApply = true)
                )
            }.onSuccess {
                _events.trySend(NegotiationEvent.CollectionApplyDeferred)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                recordNonFatal("trade_mark_completed_pending_apply_failed", e)
                _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
            }
        }
    }

    /**
     * Reverses the collection changes of a trade that was revoked after they were applied (the
     * other party revoked, or the changes predate the COMPLETED-only rule).
     */
    fun onUndoCollectionChanges(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId }
            ?.takeIf { it.status == TradeStatus.REVOKED && it.itemsLoaded } ?: return
        val userId = _uiState.value.currentUserId
        if (userId.isBlank() || proposalId !in _uiState.value.syncedCollectionProposalIds) return
        var acquired = false
        _uiState.update { state ->
            if (state.isSyncingCollection) state else { acquired = true; state.copy(isSyncingCollection = true) }
        }
        if (!acquired) return
        viewModelScope.launch(ioDispatcher) {
            updateTradeCollection(proposalId, userId, sentItemsOf(proposal, userId), receivedItemsOf(proposal, userId), reverse = true)
                .onSuccess {
                    _uiState.update { s -> s.copy(syncedCollectionProposalIds = s.syncedCollectionProposalIds - proposalId) }
                    _events.trySend(NegotiationEvent.CollectionSyncResult(success = true))
                }
                .onFailure { e ->
                    recordNonFatal("trade_undo_collection_changes_failed", e)
                    _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
                }
            _uiState.update { it.copy(isSyncingCollection = false) }
        }
    }

    fun onCounter(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId }?.takeIf { it.itemsLoaded } ?: return
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
        val proposal = _uiState.value.thread.find { it.id == proposalId }?.takeIf { it.itemsLoaded } ?: return
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
        val proposal = _uiState.value.thread.find { it.id == proposalId }?.takeIf { it.itemsLoaded } ?: return
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
                    analyticsHelper.logEvent("trade_collection_updated", emptyMap())
                    _uiState.update { s ->
                        s.copy(syncedCollectionProposalIds = s.syncedCollectionProposalIds + proposalId)
                    }
                    _events.trySend(NegotiationEvent.CollectionSyncResult(success = true))
                }
                .onFailure { e ->
                    // §5.1 fix: previously showed `e.message` verbatim (raw server text /
                    // sentinel key leaking to the UI). Now recorded for diagnosis and
                    // surfaced as a semantic event the screen resolves to a fixed string.
                    recordNonFatal("trade_collection_update_failed", e)
                    _events.trySend(NegotiationEvent.CollectionSyncResult(success = false))
                }
            _uiState.update { it.copy(isSyncingCollection = false) }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(errorDialog = null) }

    /** Called each time the screen enters composition; refreshes only on a return to it. */
    fun onScreenEntered() {
        if (hasEnteredScreen) refresh() else hasEnteredScreen = true
    }

    /** User-initiated refresh; a no-op while another refresh is in flight. */
    fun refresh() = startRefresh(force = false)

    /**
     * Single-flight thread refresh. `isRefreshing` is claimed atomically before launching, so two
     * triggers never start two network refreshes. [force] (after a mutation) supersedes an
     * in-flight refresh instead, since that one may have read the pre-mutation state.
     */
    private fun startRefresh(userId: String = _uiState.value.currentUserId, force: Boolean) {
        if (userId.isBlank()) return
        var acquired = false
        _uiState.update { s ->
            if (s.isRefreshing && !force) s else { acquired = true; s.copy(isRefreshing = true) }
        }
        if (!acquired) return
        val previous = refreshJob
        refreshJob = viewModelScope.launch(ioDispatcher) {
            previous?.cancelAndJoin()
            val result = refreshTradeThread(rootProposalId, userId)
            ensureActive()
            result.onFailure { e -> _events.trySend(NegotiationEvent.ShowError(e.toUserFacingMessage())) }
            _uiState.update { it.copy(isRefreshing = false, isLoading = false, refreshFailed = result.isFailure) }
        }
    }
}
