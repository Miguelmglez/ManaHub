package com.mmg.manahub.feature.trades.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.TradeError
import com.mmg.manahub.core.model.TradeSide
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.model.toUserFacingMessage
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.model.ReviewFlags
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.feature.trades.domain.usecase.CounterProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CreateTradeProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.EditProposalUseCase
import com.mmg.manahub.core.domain.search.FriendCardSearchMapper
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardSearchParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class TradeItemDraft(
    val id: String = UUID.randomUUID().toString(),
    val cardId: String,
    val cardName: String = "",
    val imageUrl: String? = null,
    val typeLine: String? = null,
    val setCode: String? = null,
    val setName: String? = null,
    val rarity: String? = null,
    val priceUsd: Double? = null,
    val priceUsdFoil: Double? = null,
    val priceEur: Double? = null,
    val priceEurFoil: Double? = null,
    val quantity: Int = 1,
    val isFoil: Boolean = false,
    val condition: String = "NM",
    val language: String = "en",
    val userCardIdRef: String? = null,
    val isReviewCollectionPlaceholder: Boolean = false,
    /** True if this card was found in the user's registered collection or offer list when added. */
    val isInCollection: Boolean = true,
    /** Copies available on the offer this item was picked from; null when unbounded. */
    val maxQuantity: Int? = null,
    /** True when the item came from the selected friend's own lists (their wishlist). */
    val fromFriendList: Boolean = false,
) {
    /** True when the item is tied to a concrete collection row, so its variant must not change. */
    val isVariantLocked: Boolean get() = !userCardIdRef.isNullOrBlank()

    /** Returns a copy whose quantity is [newQuantity] clamped to 1..[maxQuantity]. */
    fun withQuantity(newQuantity: Int): TradeItemDraft =
        copy(quantity = newQuantity.coerceIn(1, maxQuantity?.coerceAtLeast(1) ?: Int.MAX_VALUE))
}

/**
 * Builds the draft for a picked [AddCardRow]. The offered copy's variant wins over the wished one:
 * the offer is the physical card that changes hands, the wish only says what the other side wants.
 */
internal fun AddCardRow.toTradeItemDraft(isInCollection: Boolean, fromFriendList: Boolean = false): TradeItemDraft =
    TradeItemDraft(
        cardId = card.scryfallId,
        cardName = card.name,
        imageUrl = card.imageArtCrop ?: card.imageNormal,
        typeLine = card.typeLine,
        setCode = card.setCode,
        setName = card.setName,
        rarity = card.rarity,
        priceUsd = card.priceUsd,
        priceUsdFoil = card.priceUsdFoil,
        priceEur = card.priceEur,
        priceEurFoil = card.priceEurFoil,
        quantity = 1,
        isFoil = offerEntry?.isFoil ?: wishlistEntry?.isFoil ?: false,
        condition = offerEntry?.condition ?: wishlistEntry?.condition ?: "NM",
        language = offerEntry?.language ?: wishlistEntry?.language ?: "en",
        userCardIdRef = offerEntry?.userCardId?.takeIf { it.isNotBlank() },
        isInCollection = isInCollection,
        maxQuantity = offerEntry?.quantity?.takeIf { it > 0 },
        fromFriendList = fromFriendList,
    )

/** True when [draft] is the item [toTradeItemDraft] builds for this row (same card, variant and ref). */
internal fun AddCardRow.matchesDraft(draft: TradeItemDraft): Boolean {
    val expected = toTradeItemDraft(isInCollection = true)
    return draft.cardId == expected.cardId &&
        draft.isFoil == expected.isFoil &&
        draft.condition == expected.condition &&
        draft.language == expected.language &&
        draft.userCardIdRef == expected.userCardIdRef
}

/** A friend change waiting for confirmation because it would drop friend-specific items. */
data class PendingFriendSwitch(val friend: Friend?)

/**
 * One-shot events emitted by [TradeProposalViewModel].
 *
 * Delivered through a buffered [Channel] rather than nullable `StateFlow` fields (project
 * standard — CLAUDE.md Playtest section; mirrors [TradeNegotiationViewModel]'s `NegotiationEvent`):
 * a `StateFlow` equality-collapses two consecutive identical events (e.g. two "select a friend
 * first" errors from a fast double-tap) and can drop events emitted while the screen's lifecycle
 * is paused. [CreateTradeProposalScreen] resolves every string resource in ONE place — the
 * ViewModel never carries a raw literal sentinel key like the old `errorMessage = "NO_RECEIVER"`.
 */
sealed class ProposalEvent {
    /** A proposal was sent/countered — navigate to its thread. */
    data class NavigateToThread(val proposalId: String, val rootProposalId: String) : ProposalEvent()

    /** An edit/counter was submitted successfully — pop back to the previous screen. */
    object NavigateBack : ProposalEvent()

    /** A local (VM-side) validation failure. Always has a static, resource-backed message. */
    data class ShowValidationError(@StringRes val messageRes: Int) : ProposalEvent()

    /**
     * A failure surfaced by the repository/use-case layer. [message] is already resolved to
     * friendly text via [toUserFacingMessage] for a typed [TradeError]; it is null for any
     * other (untyped/unexpected) exception so the screen falls back to a generic string —
     * the raw [Throwable.message] is never shown to the user (audit §5.1).
     */
    data class ShowRemoteError(val message: String?) : ProposalEvent()
}

data class ProposalEditorUiState(
    val receiverId: String = "",
    val proposerItems: List<TradeItemDraft> = emptyList(),
    val receiverItems: List<TradeItemDraft> = emptyList(),
    val includesReviewFromProposer: Boolean = false,
    val includesReviewFromReceiver: Boolean = false,
    val isCounterMode: Boolean = false,
    val parentProposalId: String? = null,
    val editingProposalId: String? = null,
    val rootProposalId: String = "",
    val currentVersion: Int = 1,
    val isSaving: Boolean = false,
    /** True while the counter/edit prefill (§2.9) is loading the source proposal. */
    val isPrefillLoading: Boolean = false,
    /** True when the prefill could not resolve the source proposal even after a network
     *  refresh — the editor form is hidden entirely so a save can never wipe the real
     *  proposal's items from a blank draft (audit §2.9). */
    val prefillFailed: Boolean = false,

    // Search / Add cards state
    val addCardsQuery: String = "",
    val offerResults: List<AddCardRow> = emptyList(), // Specific offer list
    val addCardsResults: List<AddCardRow> = emptyList(), // Full Collection
    val wishlistResults: List<AddCardRow> = emptyList(),
    val isSearchingCards: Boolean = false,
    val isSearchingWishlist: Boolean = false,
    val scryfallResults: List<AddCardRow> = emptyList(),
    val isSearchingScryfall: Boolean = false,
    /** Raw error of the last failed Scryfall search (it may carry a rate-limit sentinel); null otherwise. */
    val scryfallError: String? = null,
    /** More pages of the selected friend's collection exist for the current query. */
    val friendCollectionHasMore: Boolean = false,
    val isLoadingMoreFriendCollection: Boolean = false,
    val friendCollectionLoadFailed: Boolean = false,
    val collectionIds: Set<String> = emptySet(),

    val friends: List<Friend> = emptyList(),
    val selectedFriend: Friend? = null,
    /** Set while a friend change that would drop the current friend's items awaits confirmation. */
    val pendingFriendSwitch: PendingFriendSwitch? = null,
    val sessionState: SessionState = SessionState.Loading,
    val currentUserId: String = "",
    val currentUserNickname: String = "",
    val currentUserAvatarUrl: String? = null,
    val searchingSide: TradeSide? = null,
    val isNavigatingToDetail: Boolean = false,
    val pendingAddedItems: List<TradeItemDraft> = emptyList(),

    /** Cards I should offer: my offerEntries whose scryfallId is in the friend's wishlist. */
    val proposerMatches: List<AddCardRow> = emptyList(),
    /** Cards I should request: friend's offerCards whose scryfallId is in my wishlist. */
    val receiverMatches: List<AddCardRow> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TradeProposalViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val tradesRepository: TradesRepository,
    private val createProposal: CreateTradeProposalUseCase,
    private val editProposal: EditProposalUseCase,
    private val counterProposal: CounterProposalUseCase,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val wishlistRepository: WishlistRepository,
    private val openForTradeRepository: OpenForTradeRepository,
    private val friendRepository: FriendRepository,
    private val analyticsHelper: AnalyticsHelper,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProposalEditorUiState())
    val uiState: StateFlow<ProposalEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProposalEvent>(Channel.BUFFERED)
    val events: Flow<ProposalEvent> = _events.receiveAsFlow()

    /**
     * Raw search-box text, decoupled from [ProposalEditorUiState.addCardsQuery] (audit §6.3).
     * The text field itself still echoes every keystroke synchronously via `addCardsQuery`
     * (see [onAddCardsQueryChange]); only the expensive list rebuild ([computeSearchLists]) is
     * throttled through this flow's [kotlinx.coroutines.flow.debounce] collector in `init`.
     */
    private val searchQueryFlow = MutableStateFlow("")

    private var currentUserId: String = ""

    // Written on Main by the observe* collectors, read by the rebuild on defaultDispatcher.
    @Volatile private var collectionCards: List<Card> = emptyList()
    @Volatile private var wishlistEntries: List<WishlistEntry> = emptyList()
    @Volatile private var offerEntries: List<OpenForTradeEntry> = emptyList()

    private companion object {
        /** Matches the debounce window already used elsewhere in the app (News, Friend search). */
        const val SEARCH_DEBOUNCE_MS = 300L

        /** `search_friend_cards` serves at most 100 rows per page. */
        const val FRIEND_PAGE_SIZE = 100

        /** Match suggestions read at most this many pages of a friend's wishlist / trade list. */
        const val FRIEND_LIST_MAX_PAGES = 10

        const val PENDING_HYDRATION = "pending_hydration"
    }

    // Captured at init so a failed prefill (§2.9) can be retried from the screen.
    private var prefillProposalId: String? = null
    private var prefillRootProposalId: String = ""
    private var prefillReceiverId: String = ""

    // Friend lists from search_friend_cards, one owner job each so concurrent writers never overwrite
    // each other's list. Written on ioDispatcher, read by the rebuild on defaultDispatcher.
    @Volatile private var friendWishlist: List<FriendCard> = emptyList()
    @Volatile private var friendOffers: List<FriendCard> = emptyList()
    // Only the pages loaded for the current query, never the whole collection.
    @Volatile private var friendCollection: List<Card> = emptyList()
    private var friendDataJob: Job? = null
    private var friendCollectionJob: Job? = null
    @Volatile private var friendCollectionCursor: FriendCardCursor? = null
    private var friendCollectionKey: Pair<String, String?>? = null

    private var scryfallJob: Job? = null

    // The nav-arg receiver is auto-selected once; after that only the user picks (including "None").
    private var receiverSelectionSettled = false

    // Card ids already sent to the cache warm, so a still-missing card is not re-requested per emission.
    private val warmRequestedIds = mutableSetOf<String>()

    // One conflated rebuild request: a burst of changes produces one off-Main rebuild of the latest state.
    private val rebuildRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun requestRebuild() {
        rebuildRequests.tryEmit(Unit)
    }

    init {
        // Launched first so it is subscribed before any collector below requests a rebuild.
        // Every rebuild runs off Main on the latest state; collectLatest drops a superseded one.
        viewModelScope.launch {
            rebuildRequests.collectLatest {
                val snapshot = _uiState.value
                val lists = withContext(defaultDispatcher) { computeSearchLists(snapshot) }
                // Inputs changed meanwhile: whoever changed them already queued a newer rebuild.
                _uiState.update { s -> if (s.hasSameSearchInputs(snapshot)) lists.applyTo(s) else s }
            }
        }
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                var userId = ""
                var nickname = ""
                var avatarUrl: String? = null
                if (state is SessionState.Authenticated) {
                    userId = state.user.id
                    nickname = state.user.nickname ?: ""
                    avatarUrl = state.user.avatarUrl
                    currentUserId = userId
                }
                _uiState.update { it.copy(
                    sessionState = state,
                    currentUserId = userId,
                    currentUserNickname = nickname,
                    currentUserAvatarUrl = avatarUrl
                ) }
            }
        }
        observeCollection()
        observeWishlist()
        observeOffers()
        observeFriends()

        // Typing only rebuilds after the debounce; the friend collection query is re-run server side.
        viewModelScope.launch {
            searchQueryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest {
                    requestRebuild()
                    if (_uiState.value.searchingSide == TradeSide.RECEIVER) loadFriendCollection(append = false)
                }
        }

        val receiverId = savedStateHandle.get<String>("receiverId") ?: ""
        val parentProposalId = savedStateHandle.get<String>("parentProposalId")
        val editingProposalId = savedStateHandle.get<String>("editingProposalId")
        val rootProposalId = savedStateHandle.get<String>("rootProposalId") ?: receiverId
        _uiState.update {
            it.copy(
                receiverId = receiverId,
                parentProposalId = parentProposalId,
                editingProposalId = editingProposalId,
                rootProposalId = rootProposalId,
                isCounterMode = parentProposalId != null,
            )
        }

        val proposalToPreFill = editingProposalId ?: parentProposalId
        if (proposalToPreFill != null && rootProposalId.isNotBlank()) {
            prefillProposalId = proposalToPreFill
            prefillRootProposalId = rootProposalId
            prefillReceiverId = receiverId
            viewModelScope.launch(ioDispatcher) {
                loadPrefillProposal(proposalToPreFill, rootProposalId, receiverId)
            }
        }
    }

    /**
     * Loads the source proposal for the counter/edit editor from [TradesRepository]'s
     * proposals cache.
     *
     * The cache is an in-memory singleton with no persistence (audit §2.9): after process
     * death it starts empty, so `observeProposalThread(...).first()` finds nothing and, before
     * this fix, the editor opened silently blank — saving from that state could wipe the real
     * proposal's items via [editProposal]. On a cache miss this now triggers an explicit
     * [TradesRepository.refreshProposalThread] and retries once; if that also fails, the editor
     * form is kept hidden ([ProposalEditorUiState.prefillFailed]) with a retry affordance
     * instead of ever exposing a blank, savable draft.
     */
    private suspend fun loadPrefillProposal(proposalToPreFill: String, rootProposalId: String, receiverId: String) {
        _uiState.update { it.copy(isPrefillLoading = true, prefillFailed = false) }

        // A cached proposal whose items never loaded is a miss: saving it would wipe the real items.
        var proposal = tradesRepository.observeProposalThread(rootProposalId).first()
            .find { it.id == proposalToPreFill && it.itemsLoaded }

        if (proposal == null) {
            val userId = (authRepository.sessionState.value as? SessionState.Authenticated)?.user?.id
            if (userId.isNullOrBlank()) {
                recordNonFatal("trade_proposal_prefill_no_session")
                _uiState.update { it.copy(isPrefillLoading = false, prefillFailed = true) }
                return
            }

            val refreshResult = tradesRepository.refreshProposalThread(rootProposalId, userId)
            refreshResult.exceptionOrNull()?.let { recordSafeNonFatal("trade_proposal_prefill_refresh_failed", it) }
            if (refreshResult.isFailure) {
                _uiState.update { it.copy(isPrefillLoading = false, prefillFailed = true) }
                return
            }

            proposal = tradesRepository.observeProposalThread(rootProposalId).first()
                .find { it.id == proposalToPreFill && it.itemsLoaded }
            if (proposal == null) {
                recordNonFatal("trade_proposal_prefill_not_found_after_refresh")
                _uiState.update { it.copy(isPrefillLoading = false, prefillFailed = true) }
                return
            }
        }

        val allCardIds = proposal.items
            .filter { !it.isReviewCollectionPlaceholder }
            .map { it.cardId }
            .distinct()
        // WS4a finding 5 (Backend & Performance Optimization plan, 2026-07-28): this used to call
        // cardRepository.getCardById(id) sequentially per card -- an N+1 (network fallback on every
        // miss), the same shape already fixed in FriendRepositoryImpl.getFriendCollection. One
        // batched warm + one batched Room-only read instead.
        if (allCardIds.isNotEmpty()) {
            cardRepository.warmCacheForIds(allCardIds)
        }
        val imageMap: Map<String, Card> = cardRepository.getCardsByIds(allCardIds).associateBy { it.scryfallId }

        val myItems = proposal.items
            .filter { it.fromUserId != receiverId && !it.isReviewCollectionPlaceholder }
            .map { item ->
                val card = imageMap[item.cardId]
                TradeItemDraft(
                    cardId = item.cardId,
                    cardName = card?.name?.takeIf { it.isNotBlank() } ?: item.cardName,
                    imageUrl = card?.imageArtCrop ?: card?.imageNormal,
                    typeLine = card?.typeLine,
                    setCode = card?.setCode,
                    setName = card?.setName,
                    rarity = card?.rarity,
                    quantity = item.quantity ?: 1,
                    isFoil = item.isFoil ?: false,
                    condition = item.condition ?: "NM",
                    language = item.language ?: "en",
                    userCardIdRef = item.userCardIdRef,
                    isInCollection = true,
                )
            }
        val theirItems = proposal.items
            .filter { it.fromUserId == receiverId && !it.isReviewCollectionPlaceholder }
            .map { item ->
                val card = imageMap[item.cardId]
                TradeItemDraft(
                    cardId = item.cardId,
                    cardName = card?.name?.takeIf { it.isNotBlank() } ?: item.cardName,
                    imageUrl = card?.imageArtCrop ?: card?.imageNormal,
                    typeLine = card?.typeLine,
                    setCode = card?.setCode,
                    setName = card?.setName,
                    rarity = card?.rarity,
                    quantity = item.quantity ?: 1,
                    isFoil = item.isFoil ?: false,
                    condition = item.condition ?: "NM",
                    language = item.language ?: "en",
                    userCardIdRef = item.userCardIdRef,
                    isInCollection = true,
                )
            }

        val iAmProposer = receiverId == proposal.receiverId
        _uiState.update { s ->
            s.copy(
                isPrefillLoading = false,
                prefillFailed = false,
                proposerItems = myItems,
                receiverItems = theirItems,
                includesReviewFromProposer = if (iAmProposer) proposal.includesReviewCollectionFromProposer else proposal.includesReviewCollectionFromReceiver,
                includesReviewFromReceiver = if (iAmProposer) proposal.includesReviewCollectionFromReceiver else proposal.includesReviewCollectionFromProposer,
                currentVersion = proposal.proposalVersion,
            )
        }
        requestRebuild()
    }

    /** Retries the counter/edit prefill after a failure (§2.9); called from the screen's retry action. */
    fun retryPrefill() {
        val proposalId = prefillProposalId ?: return
        viewModelScope.launch(ioDispatcher) {
            loadPrefillProposal(proposalId, prefillRootProposalId, prefillReceiverId)
        }
    }

    private fun observeCollection() {
        viewModelScope.launch {
            userCardRepository.observeCollection()
                .distinctUntilChanged()
                .catch { e -> recordSafeNonFatal("trade_proposal_observe_collection_failed", e) }
                .collect { collection ->
                    // Unhydrated placeholders have no name to show or search by.
                    collectionCards = collection.map { it.card }
                        .filter { it.name.isNotBlank() && it.staleReason != PENDING_HYDRATION }
                        .distinctBy { it.scryfallId }
                        .sortedBy { it.name }
                    val ids = collection.mapTo(HashSet()) { it.card.scryfallId }
                    _uiState.update { it.copy(collectionIds = ids) }
                    requestRebuild()
                }
        }
    }

    private fun observeWishlist() {
        viewModelScope.launch {
            wishlistRepository.observeLocal()
                .distinctUntilChanged()
                .catch { e -> recordSafeNonFatal("trade_proposal_observe_wishlist_failed", e) }
                .collect { wishlist ->
                    wishlistEntries = wishlist.filter { it.card != null }.sortedBy { it.card?.name }
                    warmMissingCards(wishlist.filter { it.card == null }.map { it.cardId })
                    requestRebuild()
                }
        }
    }

    private fun observeOffers() {
        viewModelScope.launch {
            openForTradeRepository.observeLocal()
                .distinctUntilChanged()
                .catch { e -> recordSafeNonFatal("trade_proposal_observe_offers_failed", e) }
                .collect { offers ->
                    offerEntries = offers.filter { it.card != null }.sortedBy { it.card?.name }
                    warmMissingCards(offers.filter { it.card == null }.map { it.scryfallId })
                    requestRebuild()
                }
        }
    }

    /** Caches card metadata for list entries whose card is not cached yet; Room then re-emits them. */
    private fun warmMissingCards(ids: List<String>) {
        val fresh = ids.filter { it.isNotBlank() && warmRequestedIds.add(it) }.distinct()
        if (fresh.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            try {
                cardRepository.warmCacheForIds(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordSafeNonFatal("trade_proposal_warm_missing_cards_failed", e)
            }
        }
    }

    private fun observeFriends() {
        viewModelScope.launch {
            friendRepository.observeFriends()
                .distinctUntilChanged()
                .catch { e -> recordSafeNonFatal("trade_proposal_observe_friends_failed", e) }
                .collect { friends ->
                    _uiState.update { it.copy(friends = friends) }
                    if (receiverSelectionSettled) return@collect
                    val receiverId = _uiState.value.receiverId
                    friends.find { it.userId == receiverId }?.let { onFriendSelected(it) }
                }
        }
    }

    /**
     * Selects [friend] as the counterparty. Switching away from an already-selected friend while the
     * draft holds that friend's items asks for confirmation first ([ProposalEditorUiState.pendingFriendSwitch]).
     */
    fun onFriendSelected(friend: Friend?) {
        receiverSelectionSettled = true
        val state = _uiState.value
        val previous = state.selectedFriend
        val isSwitch = previous != null && previous.userId != friend?.userId
        val holdsFriendItems = state.receiverItems.isNotEmpty() || state.proposerItems.any { it.fromFriendList }
        if (isSwitch && holdsFriendItems) {
            _uiState.update { it.copy(pendingFriendSwitch = PendingFriendSwitch(friend)) }
            return
        }
        applyFriendSelection(friend, clearFriendItems = isSwitch)
    }

    /** Confirms the pending friend switch, dropping the previous friend's items. */
    fun onConfirmFriendSwitch() {
        val pending = _uiState.value.pendingFriendSwitch ?: return
        _uiState.update { it.copy(pendingFriendSwitch = null) }
        applyFriendSelection(pending.friend, clearFriendItems = true)
    }

    /** Keeps the current friend and items. */
    fun onDismissFriendSwitch() {
        _uiState.update { it.copy(pendingFriendSwitch = null) }
    }

    private fun applyFriendSelection(friend: Friend?, clearFriendItems: Boolean) {
        _uiState.update { s ->
            val base = s.copy(selectedFriend = friend, receiverId = friend?.userId ?: "")
            if (!clearFriendItems) base else base.copy(
                receiverItems = emptyList(),
                proposerItems = s.proposerItems.filterNot { it.fromFriendList },
                pendingAddedItems = emptyList(),
                includesReviewFromReceiver = false,
            )
        }
        if (friend != null) {
            fetchFriendData(friend.userId)
        } else {
            friendDataJob?.cancel()
            resetFriendCollection()
            friendWishlist = emptyList()
            friendOffers = emptyList()
            requestRebuild()
        }
    }

    /**
     * Loads the selected friend's wishlist and trade list for match suggestions, draining at most
     * [FRIEND_LIST_MAX_PAGES] keyset pages of `search_friend_cards` each (the RPC enforces friendship
     * and per-list privacy). The friend's collection is never loaded eagerly: the "You get" sheet
     * pages through it on demand ([loadFriendCollection]).
     *
     * The previous job is cancelled and the friend lists reset first, so a quick friend switch never
     * briefly shows the previous friend's lists.
     */
    private fun fetchFriendData(userId: String) {
        friendDataJob?.cancel()
        resetFriendCollection()
        friendWishlist = emptyList()
        friendOffers = emptyList()
        requestRebuild()
        if (_uiState.value.searchingSide == TradeSide.RECEIVER) loadFriendCollection(append = false)

        friendDataJob = viewModelScope.launch(ioDispatcher) {
            coroutineScope {
                val wishlistDeferred = async { drainFriendList(userId, "wishlist") }
                val offersDeferred = async { drainFriendList(userId, "trade") }
                friendWishlist = wishlistDeferred.await().sortedBy { it.name }
                friendOffers = offersDeferred.await().sortedBy { it.name }
            }
            requestRebuild()
        }
    }

    /**
     * Pages through one of the friend's lists. A failed page keeps the rows already read: these
     * rows only drive suggestions, so a partial list is better than none.
     */
    private suspend fun drainFriendList(friendUserId: String, list: String): List<FriendCard> {
        val rows = LinkedHashMap<String, FriendCard>()
        var cursor: FriendCardCursor? = null
        repeat(FRIEND_LIST_MAX_PAGES) {
            val page = friendRepository.searchFriendCards(friendUserId, list, FriendCardSearchParams(), cursor, FRIEND_PAGE_SIZE)
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    recordSafeNonFatal("trade_friend_${list}_load_failed", e)
                    return rows.values.toList()
                }
            page.cards.forEach { rows[it.rowId ?: "${it.scryfallId}_${it.isFoil}_${it.condition}_${it.language}"] = it }
            cursor = page.nextCursor
            if (!page.hasMore || cursor == null) return rows.values.toList()
        }
        FirebaseCrashlytics.getInstance().log("trade_friend_list_page_cap_reached")
        return rows.values.toList()
    }

    private fun resetFriendCollection() {
        friendCollectionJob?.cancel()
        friendCollectionCursor = null
        friendCollectionKey = null
        friendCollection = emptyList()
        _uiState.update {
            it.copy(friendCollectionHasMore = false, isLoadingMoreFriendCollection = false, friendCollectionLoadFailed = false)
        }
    }

    /**
     * Loads the first ([append] = false) or the next keyset page of the selected friend's
     * collection matching the current query. A first-page load for an unchanged friend + query
     * is skipped, so re-opening the sheet or re-debouncing the same text costs no request.
     */
    private fun loadFriendCollection(append: Boolean) {
        val friendId = _uiState.value.selectedFriend?.userId ?: return
        val name = _uiState.value.addCardsQuery.trim().takeIf { it.length >= FriendCardSearchMapper.MIN_NAME_LENGTH }
            ?.take(FriendCardSearchMapper.MAX_TEXT_LENGTH)
        val key = friendId to name
        if (append) {
            val state = _uiState.value
            if (key != friendCollectionKey || !state.friendCollectionHasMore || state.isLoadingMoreFriendCollection) return
            if (friendCollectionJob?.isActive == true) return
        } else {
            if (key == friendCollectionKey && !_uiState.value.friendCollectionLoadFailed) return
            resetFriendCollection()
            friendCollectionKey = key
        }
        val cursor = if (append) friendCollectionCursor else null
        _uiState.update {
            if (append) it.copy(isLoadingMoreFriendCollection = true, friendCollectionLoadFailed = false)
            else it.copy(isSearchingCards = true, friendCollectionLoadFailed = false)
        }
        friendCollectionJob = viewModelScope.launch(ioDispatcher) {
            val result = friendRepository.searchFriendCards(friendId, "collection", FriendCardSearchParams(name = name), cursor, FRIEND_PAGE_SIZE)
            ensureActive()
            result.fold(
                onSuccess = { page ->
                    friendCollectionCursor = page.nextCursor
                    val pageCards = page.cards.mapNotNull { it.toCard() }
                    val merged = if (append) friendCollection + pageCards else pageCards
                    friendCollection = merged.distinctBy { it.scryfallId }
                    _uiState.update {
                        it.copy(isSearchingCards = false, isLoadingMoreFriendCollection = false, friendCollectionHasMore = page.hasMore)
                    }
                    requestRebuild()
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    recordSafeNonFatal("trade_friend_collection_page_failed", e)
                    _uiState.update {
                        it.copy(isSearchingCards = false, isLoadingMoreFriendCollection = false, friendCollectionLoadFailed = true)
                    }
                },
            )
        }
    }

    /** Loads the next page of the friend's collection when the sheet scrolls to its end. */
    fun onLoadMoreFriendCollection() = loadFriendCollection(append = true)

    /** Retries the friend collection page that failed. */
    fun onRetryFriendCollection() {
        val appendRetry = friendCollectionCursor != null
        _uiState.update { it.copy(friendCollectionLoadFailed = false) }
        if (appendRetry) loadFriendCollection(append = true) else {
            friendCollectionKey = null
            loadFriendCollection(append = false)
        }
    }

    fun onAddCardsQueryChange(query: String) {
        // The text field echoes every keystroke immediately via `addCardsQuery`; the actual
        // (expensive) list rebuild is debounced through `searchQueryFlow` (audit §6.3, see the
        // collector wired in `init`).
        _uiState.update { it.copy(addCardsQuery = query) }
        searchQueryFlow.value = query
    }

    fun onOpenSearch(side: TradeSide) {
        _uiState.update { it.copy(searchingSide = side, isNavigatingToDetail = false) }
        requestRebuild()
        if (side == TradeSide.RECEIVER) loadFriendCollection(append = false)
    }

    fun setNavigatingToDetail(isNavigating: Boolean) {
        _uiState.update { it.copy(isNavigatingToDetail = isNavigating) }
    }

    /** The add-cards lists and match suggestions computed for one state snapshot. */
    private data class SearchLists(
        val offerResults: List<AddCardRow>?,
        val addCardsResults: List<AddCardRow>?,
        val wishlistResults: List<AddCardRow>?,
        val proposerMatches: List<AddCardRow>,
        val receiverMatches: List<AddCardRow>,
    ) {
        /** Null lists are the ones this snapshot does not own (no sheet open) and stay as they are. */
        fun applyTo(state: ProposalEditorUiState) = state.copy(
            offerResults = offerResults ?: state.offerResults,
            addCardsResults = addCardsResults ?: state.addCardsResults,
            wishlistResults = wishlistResults ?: state.wishlistResults,
            proposerMatches = proposerMatches,
            receiverMatches = receiverMatches,
        )
    }

    private fun ProposalEditorUiState.hasSameSearchInputs(other: ProposalEditorUiState): Boolean =
        addCardsQuery == other.addCardsQuery &&
            searchingSide == other.searchingSide &&
            selectedFriend?.userId == other.selectedFriend?.userId &&
            collectionIds == other.collectionIds &&
            proposerItems == other.proposerItems &&
            receiverItems == other.receiverItems &&
            pendingAddedItems == other.pendingAddedItems

    /**
     * Builds every add-cards list ([ProposalEditorUiState.offerResults] /
     * [ProposalEditorUiState.addCardsResults] / [ProposalEditorUiState.wishlistResults]) and the
     * match suggestions for [s]. Pure over [s] and the backing fields, so it runs off Main; the
     * collection alone can be thousands of cards. Callers go through [requestRebuild].
     */
    private fun computeSearchLists(s: ProposalEditorUiState): SearchLists {
        val query = s.addCardsQuery
        val filteredCollection = if (query.isBlank()) collectionCards
            else collectionCards.filter { it.name.contains(query, ignoreCase = true) }

        // PROPOSER (A → B): offerResults = MY offers, wishlistResults = FRIEND's wishlist.
        // RECEIVER (A ← B): offerResults = FRIEND's offers + collection page, wishlistResults = MY wishlist.
        // addCardsResults is always MY collection (fallback search).
        val isFriendSelected = s.selectedFriend != null
        val searchingSide = s.searchingSide
        val ownedIds = s.collectionIds

        // Only count items from the active side so the "selected" indicator and
        // over-limit warning reflect what's been added to THIS side of the trade.
        val sideItems = when (searchingSide) {
            TradeSide.PROPOSER -> s.proposerItems
            TradeSide.RECEIVER -> s.receiverItems
            null -> s.proposerItems + s.receiverItems
        }
        val allItems = sideItems + s.pendingAddedItems

        return when (searchingSide) {
            TradeSide.PROPOSER -> {
                // A is offering cards: show MY offers + FRIEND's wishlist
                val filteredOffer = if (query.isBlank()) offerEntries
                    else offerEntries.filter { it.card?.name?.contains(query, ignoreCase = true) == true }
                val filteredFriendWishlist = if (query.isBlank()) friendWishlist
                    else friendWishlist.filter { it.name.contains(query, ignoreCase = true) }

                SearchLists(
                    offerResults = filteredOffer.mapNotNull { entry ->
                        val card = entry.card ?: return@mapNotNull null
                        AddCardRow(
                            card = card,
                            quantityInDeck = allItems.filter { it.cardId == entry.scryfallId && it.isFoil == entry.isFoil && it.condition == entry.condition && it.language == entry.language }.sumOf { it.quantity },
                            isOwned = entry.scryfallId in ownedIds,
                            availableQuantity = entry.quantity,
                            offerEntry = entry,
                        )
                    },
                    addCardsResults = filteredCollection.map { card ->
                        AddCardRow(
                            card = card,
                            quantityInDeck = allItems.filter { it.cardId == card.scryfallId && it.userCardIdRef == null }.sumOf { it.quantity },
                            isOwned = card.scryfallId in ownedIds,
                            availableQuantity = 0,
                        )
                    },
                    wishlistResults = if (isFriendSelected) {
                        filteredFriendWishlist.mapNotNull { fc ->
                            val card = fc.toCard() ?: return@mapNotNull null
                            AddCardRow(
                                card = card,
                                quantityInDeck = allItems.filter { it.cardId == fc.scryfallId && it.isFoil == fc.isFoil && it.condition == (fc.condition ?: "NM") && it.language == (fc.language ?: "en") }.sumOf { it.quantity },
                                isOwned = fc.scryfallId in ownedIds,
                                availableQuantity = fc.quantity,
                                wishlistEntry = fc.toSyntheticWishlistEntry(),
                            )
                        }
                    } else emptyList(),
                    proposerMatches = computeProposerMatches(s, ownedIds),
                    receiverMatches = computeReceiverMatches(s, ownedIds),
                )
            }

            TradeSide.RECEIVER -> {
                // A is requesting cards: show FRIEND's offers + FRIEND's collection + MY wishlist
                val filteredWishlist = if (query.isBlank()) wishlistEntries
                    else wishlistEntries.filter { it.card?.name?.contains(query, ignoreCase = true) == true }
                val filteredFriendOffers = if (query.isBlank()) friendOffers
                    else friendOffers.filter { it.name.contains(query, ignoreCase = true) }
                // Friend's public collection — shown in the "Offer" tab (offerResults) alongside offers
                val filteredFriendCollection = if (query.isBlank()) friendCollection
                    else friendCollection.filter { it.name.contains(query, ignoreCase = true) }
                // Merge friend offers + friend collection into offerResults, de-duplicating by scryfallId
                // (offer entries take precedence as they carry quantity/variant metadata)
                val offerIds = filteredFriendOffers.map { it.scryfallId }.toSet()
                val collectionRows = filteredFriendCollection
                    .filter { it.scryfallId !in offerIds }
                    .map { card ->
                        AddCardRow(
                            card = card,
                            quantityInDeck = allItems.filter { it.cardId == card.scryfallId && it.userCardIdRef == null }.sumOf { it.quantity },
                            isOwned = card.scryfallId in ownedIds,
                            availableQuantity = 0,
                        )
                    }

                SearchLists(
                    offerResults = filteredFriendOffers.mapNotNull { fc ->
                        val card = fc.toCard() ?: return@mapNotNull null
                        AddCardRow(
                            card = card,
                            quantityInDeck = allItems.filter { it.cardId == fc.scryfallId && it.isFoil == fc.isFoil && it.condition == (fc.condition ?: "NM") && it.language == (fc.language ?: "en") }.sumOf { it.quantity },
                            isOwned = fc.scryfallId in ownedIds,
                            availableQuantity = fc.quantity,
                            offerEntry = fc.toSyntheticOfferEntry(),
                        )
                    } + collectionRows,
                    addCardsResults = filteredCollection.map { card ->
                        AddCardRow(
                            card = card,
                            quantityInDeck = allItems.filter { it.cardId == card.scryfallId && it.userCardIdRef == null }.sumOf { it.quantity },
                            isOwned = card.scryfallId in ownedIds,
                            availableQuantity = 0,
                        )
                    },
                    wishlistResults = filteredWishlist.mapNotNull { entry ->
                        val card = entry.card ?: return@mapNotNull null
                        AddCardRow(
                            card = card,
                            quantityInDeck = allItems.filter { it.cardId == entry.cardId && it.isFoil == entry.isFoil && it.condition == entry.condition && it.language == entry.language }.sumOf { it.quantity },
                            isOwned = entry.cardId in ownedIds,
                            availableQuantity = entry.quantity,
                            wishlistEntry = entry,
                        )
                    },
                    proposerMatches = computeProposerMatches(s, ownedIds),
                    receiverMatches = computeReceiverMatches(s, ownedIds),
                )
            }

            null -> {
                // No sheet open: only the match suggestions (data may have refreshed)
                SearchLists(
                    offerResults = null,
                    addCardsResults = null,
                    wishlistResults = null,
                    proposerMatches = computeProposerMatches(s, ownedIds),
                    receiverMatches = computeReceiverMatches(s, ownedIds),
                )
            }
        }
    }

    /**
     * Computes the proposer match suggestions: my [offerEntries] whose [scryfallId] appears in
     * the friend's wishlist. Emits one [AddCardRow] per offer entry (all variants), assigning
     * [AddCardRow.wishlistEntry] to the best-matching friend wish so that [AddCardRow.isExactMatch]
     * can distinguish exact-attribute matches from partial ones.
     */
    private fun computeProposerMatches(
        state: ProposalEditorUiState,
        ownedIds: Set<String>,
    ): List<AddCardRow> {
        val currentFriendWishlist = friendWishlist
        if (state.selectedFriend == null || currentFriendWishlist.isEmpty()) return emptyList()
        val friendWishlistIds = currentFriendWishlist.map { it.scryfallId }.toSet()
        val allItems = state.proposerItems + state.receiverItems + state.pendingAddedItems
        return offerEntries
            .filter { it.scryfallId in friendWishlistIds }
            .mapNotNull { entry ->
                val card = entry.card ?: return@mapNotNull null
                val friendWishes = currentFriendWishlist.filter { it.scryfallId == entry.scryfallId }
                val bestWish = friendWishes.firstOrNull { fw ->
                    fw.isFoil == entry.isFoil &&
                    (fw.condition == null || fw.condition == entry.condition) &&
                    (fw.language == null || fw.language == entry.language)
                } ?: friendWishes.firstOrNull()
                AddCardRow(
                    card = card,
                    quantityInDeck = allItems.filter { it.cardId == entry.scryfallId }.sumOf { it.quantity },
                    isOwned = entry.scryfallId in ownedIds,
                    availableQuantity = entry.quantity,
                    offerEntry = entry,
                    wishlistEntry = bestWish?.toSyntheticWishlistEntry(),
                )
            }
    }

    /**
     * Computes the receiver match suggestions: friend's [friendOfferCards] whose [scryfallId]
     * appears in my wishlist. Emits one [AddCardRow] per FriendCard entry (all variants),
     * attaching the best-matching [WishlistEntry] so [AddCardRow.isExactMatch] can be computed.
     */
    private fun computeReceiverMatches(
        state: ProposalEditorUiState,
        ownedIds: Set<String>,
    ): List<AddCardRow> {
        val currentFriendOffers = friendOffers
        if (state.selectedFriend == null || currentFriendOffers.isEmpty()) return emptyList()
        val myWishlistIds = wishlistEntries.map { it.cardId }.toSet()
        val allItems = state.proposerItems + state.receiverItems + state.pendingAddedItems
        return currentFriendOffers
            .filter { it.scryfallId in myWishlistIds }
            .mapNotNull { fc ->
                val card = fc.toCard() ?: return@mapNotNull null
                val myWishes = wishlistEntries.filter { it.cardId == fc.scryfallId }
                val bestWish = myWishes.firstOrNull { w ->
                    w.isFoil == fc.isFoil &&
                    (w.condition == null || w.condition == fc.condition) &&
                    (w.language == null || w.language == fc.language)
                } ?: myWishes.firstOrNull()
                AddCardRow(
                    card = card,
                    quantityInDeck = allItems.filter { it.cardId == fc.scryfallId }.sumOf { it.quantity },
                    isOwned = fc.scryfallId in ownedIds,
                    availableQuantity = fc.quantity,
                    offerEntry = fc.toSyntheticOfferEntry(),
                    wishlistEntry = bestWish,
                )
            }
    }

    /**
     * Searches Scryfall for the "All cards" tab. Each call cancels the previous search and waits
     * out the typing debounce first, so only the last query reaches the rate-limited queue.
     */
    fun searchScryfallDirect(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        searchQueryFlow.value = query
        scryfallJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(scryfallResults = emptyList(), isSearchingScryfall = false, scryfallError = null) }
            return
        }
        _uiState.update { it.copy(isSearchingScryfall = true, scryfallError = null) }
        scryfallJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val outcome = try {
                cardRepository.searchCards(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().apply {
                    log("trade_scryfall_search_failed")
                    setCustomKey("scryfall_query_length", query.length)
                    setCustomKey("scryfall_error_type", e::class.simpleName ?: "Unknown")
                    recordException(RuntimeException("[TradeProposal] Scryfall search failed", e))
                }
                DataResult.Error(e::class.simpleName ?: "error")
            }
            when (outcome) {
                is DataResult.Success -> {
                    val cards = outcome.data
                    _uiState.update { s ->
                        val sideItems = when (s.searchingSide) {
                            TradeSide.PROPOSER -> s.proposerItems
                            TradeSide.RECEIVER -> s.receiverItems
                            null -> s.proposerItems + s.receiverItems
                        }
                        val allItems = sideItems + s.pendingAddedItems
                        s.copy(
                            isSearchingScryfall = false,
                            scryfallError = null,
                            scryfallResults = cards.map { card ->
                                AddCardRow(
                                    card = card,
                                    quantityInDeck = allItems.filter { it.cardId == card.scryfallId && it.userCardIdRef == null }.sumOf { it.quantity },
                                    isOwned = card.scryfallId in s.collectionIds,
                                    availableQuantity = 0,
                                )
                            },
                        )
                    }
                }
                // Scryfall answers "no match" with a 404: an empty result, not an error.
                is DataResult.Error -> if (outcome.message == "SCRYFALL_404") {
                    _uiState.update { it.copy(isSearchingScryfall = false, scryfallResults = emptyList(), scryfallError = null) }
                } else {
                    FirebaseCrashlytics.getInstance().log("trade_scryfall_search_error_result")
                    _uiState.update { it.copy(isSearchingScryfall = false, scryfallResults = emptyList(), scryfallError = outcome.message) }
                }
            }
        }
    }

    /** Re-runs the Scryfall search that failed. */
    fun retryScryfallSearch() = searchScryfallDirect(_uiState.value.addCardsQuery)

    fun clearAddCardsState() {
        scryfallJob?.cancel()
        resetFriendCollection()
        _uiState.update { it.copy(
            scryfallError = null,
            isSearchingScryfall = false,
            isSearchingCards = false,
            addCardsQuery = "",
            addCardsResults = emptyList(),
            offerResults = emptyList(),
            wishlistResults = emptyList(),
            scryfallResults = emptyList(),
            pendingAddedItems = emptyList(),
            searchingSide = null,
            isNavigatingToDetail = false,
        ) }
        requestRebuild()
    }

    fun getCardById(scryfallId: String): Card? {
        return (uiState.value.addCardsResults + uiState.value.scryfallResults + uiState.value.wishlistResults + uiState.value.offerResults)
            .find { it.card.scryfallId == scryfallId }?.card
    }

    fun addProposerItem(item: TradeItemDraft) {
        _uiState.update { s ->
            val existing = s.pendingAddedItems.find { it.cardId == item.cardId && it.isFoil == item.isFoil && it.condition == item.condition && it.language == item.language && it.userCardIdRef == item.userCardIdRef }
            if (existing != null) {
                s.copy(pendingAddedItems = s.pendingAddedItems.map { if (it.id == existing.id) it.withQuantity(it.quantity + 1) else it })
            } else {
                s.copy(pendingAddedItems = s.pendingAddedItems + item)
            }
        }
        requestRebuild()
    }

    fun removeProposerItem(id: String) {
        _uiState.update { s ->
            val existing = s.pendingAddedItems.find { it.id == id }
            if (existing != null && existing.quantity > 1) {
                s.copy(pendingAddedItems = s.pendingAddedItems.map { if (it.id == id) it.copy(quantity = it.quantity - 1) else it })
            } else if (existing != null) {
                s.copy(pendingAddedItems = s.pendingAddedItems.filter { it.id != id })
            } else {
                // If not in pending, check in main list (for direct removal from proposal)
                val inMain = s.proposerItems.find { it.id == id }
                if (inMain != null && inMain.quantity > 1) {
                    s.copy(proposerItems = s.proposerItems.map { if (it.id == id) it.copy(quantity = it.quantity - 1) else it })
                } else {
                    s.copy(proposerItems = s.proposerItems.filter { it.id != id })
                }
            }
        }
        requestRebuild()
    }

    fun addReceiverItem(item: TradeItemDraft) {
        _uiState.update { s ->
            val existing = s.pendingAddedItems.find { it.cardId == item.cardId && it.isFoil == item.isFoil && it.condition == item.condition && it.language == item.language && it.userCardIdRef == item.userCardIdRef }
            if (existing != null) {
                s.copy(pendingAddedItems = s.pendingAddedItems.map { if (it.id == existing.id) it.withQuantity(it.quantity + 1) else it })
            } else {
                s.copy(pendingAddedItems = s.pendingAddedItems + item)
            }
        }
        requestRebuild()
    }

    fun removeReceiverItem(id: String) {
        _uiState.update { s ->
            val existing = s.pendingAddedItems.find { it.id == id }
            if (existing != null && existing.quantity > 1) {
                s.copy(pendingAddedItems = s.pendingAddedItems.map { if (it.id == id) it.copy(quantity = it.quantity - 1) else it })
            } else if (existing != null) {
                s.copy(pendingAddedItems = s.pendingAddedItems.filter { it.id != id })
            } else {
                val inMain = s.receiverItems.find { it.id == id }
                if (inMain != null && inMain.quantity > 1) {
                    s.copy(receiverItems = s.receiverItems.map { if (it.id == id) it.copy(quantity = it.quantity - 1) else it })
                } else {
                    s.copy(receiverItems = s.receiverItems.filter { it.id != id })
                }
            }
        }
        requestRebuild()
    }

    /**
     * Adds a suggestion card directly to [proposerItems] (the "They get" side).
     * Merges by incrementing quantity if the exact same variant already exists,
     * otherwise appends. No pending-items flow needed — suggestions bypass the sheet.
     */
    fun addSuggestionToProposer(item: TradeItemDraft) {
        _uiState.update { s ->
            val existing = s.proposerItems.find {
                it.cardId == item.cardId &&
                it.isFoil == item.isFoil &&
                it.condition == item.condition &&
                it.language == item.language &&
                it.userCardIdRef == item.userCardIdRef
            }
            if (existing != null) {
                s.copy(
                    proposerItems = s.proposerItems.map {
                        if (it.id == existing.id) it.withQuantity(it.quantity + 1) else it
                    }
                )
            } else {
                s.copy(proposerItems = s.proposerItems + item)
            }
        }
        requestRebuild()
    }

    /**
     * Adds a suggestion card directly to [receiverItems] (the "You get" side).
     * Merges by incrementing quantity if the exact same variant already exists,
     * otherwise appends.
     */
    fun addSuggestionToReceiver(item: TradeItemDraft) {
        _uiState.update { s ->
            val existing = s.receiverItems.find {
                it.cardId == item.cardId &&
                it.isFoil == item.isFoil &&
                it.condition == item.condition &&
                it.language == item.language &&
                it.userCardIdRef == item.userCardIdRef
            }
            if (existing != null) {
                s.copy(
                    receiverItems = s.receiverItems.map {
                        if (it.id == existing.id) it.withQuantity(it.quantity + 1) else it
                    }
                )
            } else {
                s.copy(receiverItems = s.receiverItems + item)
            }
        }
        requestRebuild()
    }

    fun onConfirmPendingItems() {
        val side = _uiState.value.searchingSide ?: return
        confirmPendingItemsForSide(side)
    }

    fun confirmPendingItemsForSide(side: TradeSide) {
        _uiState.update { state ->
            val pending = state.pendingAddedItems
            when (side) {
                TradeSide.PROPOSER -> state.copy(
                    proposerItems = mergeDraftItems(state.proposerItems, pending),
                    pendingAddedItems = emptyList()
                )
                TradeSide.RECEIVER -> state.copy(
                    receiverItems = mergeDraftItems(state.receiverItems, pending),
                    pendingAddedItems = emptyList()
                )
            }
        }
        requestRebuild()
    }

    private fun mergeDraftItems(
        existing: List<TradeItemDraft>,
        additions: List<TradeItemDraft>,
    ): List<TradeItemDraft> {
        val result = existing.toMutableList()
        additions.forEach { addition ->
            val duplicate = result.find {
                it.cardId == addition.cardId &&
                it.isFoil == addition.isFoil &&
                it.condition == addition.condition &&
                it.language == addition.language &&
                it.userCardIdRef == addition.userCardIdRef
            }
            if (duplicate != null) {
                val idx = result.indexOf(duplicate)
                result[idx] = duplicate.withQuantity(duplicate.quantity + addition.quantity)
            } else {
                result.add(addition)
            }
        }
        return result
    }

    fun onCancelPendingItems() {
        _uiState.update { it.copy(pendingAddedItems = emptyList()) }
        requestRebuild()
    }

    fun toggleReviewCollectionProposer() {
        _uiState.update { it.copy(includesReviewFromProposer = !it.includesReviewFromProposer) }
    }

    fun toggleReviewCollectionReceiver() {
        _uiState.update { it.copy(includesReviewFromReceiver = !it.includesReviewFromReceiver) }
    }

    fun updateProposerItem(updated: TradeItemDraft) {
        _uiState.update {
            it.copy(proposerItems = it.proposerItems.map { i -> if (i.id == updated.id) reconcileEdit(i, updated) else i })
        }
        requestRebuild()
    }

    fun updateReceiverItem(updated: TradeItemDraft) {
        _uiState.update {
            it.copy(receiverItems = it.receiverItems.map { i -> if (i.id == updated.id) reconcileEdit(i, updated) else i })
        }
        requestRebuild()
    }

    /**
     * A ref names one concrete collection row, so an edit that changes the variant can no longer
     * point at it: the ref and its availability cap are dropped. The quantity always stays in range.
     */
    private fun reconcileEdit(original: TradeItemDraft, edited: TradeItemDraft): TradeItemDraft {
        val variantChanged = edited.isFoil != original.isFoil ||
            edited.condition != original.condition ||
            edited.language != original.language
        val detached = if (variantChanged && original.isVariantLocked) {
            edited.copy(userCardIdRef = null, maxQuantity = null)
        } else {
            edited
        }
        return detached.withQuantity(edited.quantity)
    }

    private fun validateInitialProposal(state: ProposalEditorUiState): Boolean {
        val proposerCovered = state.proposerItems.isNotEmpty() || state.includesReviewFromProposer
        val receiverCovered = state.receiverItems.isNotEmpty() || state.includesReviewFromReceiver
        return proposerCovered && receiverCovered
    }

    fun onSendProposal() {
        val state = _uiState.value
        val myId = state.currentUserId
        val receiverId = state.receiverId

        // Guard: a proposal requires both participants to be identified.
        if (myId.isBlank()) {
            _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_error_not_logged_in))
            return
        }
        if (receiverId.isBlank() && !state.isCounterMode && state.editingProposalId == null) {
            _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_error_no_receiver))
            return
        }
        if (!state.isCounterMode && state.editingProposalId == null && receiverId == myId) {
            _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_error_self_trade))
            return
        }
        if (!validateInitialProposal(state)) {
            _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_error_initial_asymmetry))
            return
        }

        // Atomic capture-and-flip: a fast double-tap must never create two proposals (audit §2.7);
        // `enabled = !isSaving` alone updates asynchronously and leaves a window between taps.
        var captured: ProposalEditorUiState? = null
        _uiState.update { s ->
            if (s.isSaving) return@update s
            captured = s
            s.copy(isSaving = true)
        }
        val snapshot = captured ?: return

        viewModelScope.launch(ioDispatcher) {
            val editingId = snapshot.editingProposalId
            val parentId = snapshot.parentProposalId
            val items = buildItemRequestDtos(snapshot)
            val reviewFlags = ReviewFlags(
                snapshot.includesReviewFromProposer,
                snapshot.includesReviewFromReceiver,
            )

            FirebaseCrashlytics.getInstance().log(
                "trade_proposal_send_attempt: isCounter=${snapshot.isCounterMode}, proposerItems=${snapshot.proposerItems.size}, receiverItems=${snapshot.receiverItems.size}"
            )

            // Each branch is handled separately so every Result stays properly typed
            // and no unchecked casts or vacuous .map { it } calls are needed.
            val handleError: (Throwable) -> Unit = { e ->
                when (e) {
                    is TradeError.ProposalVersionMismatch ->
                        _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_version_mismatch))
                    is TradeError.InitialAsymmetryNotAllowed ->
                        _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_error_initial_asymmetry))
                    else -> {
                        FirebaseCrashlytics.getInstance().apply {
                            log("trade_proposal_send_failed: isCounter=${snapshot.isCounterMode}")
                            setCustomKey("trade_proposer_item_count", snapshot.proposerItems.size)
                            setCustomKey("trade_receiver_item_count", snapshot.receiverItems.size)
                            recordException(e)
                        }
                        // Raw e.message is never surfaced — only a typed TradeError's
                        // pre-resolved friendly text is (audit §5.1).
                        _events.trySend(ProposalEvent.ShowRemoteError(if (e is TradeError) e.toUserFacingMessage() else null))
                    }
                }
                _uiState.update { it.copy(isSaving = false) }
            }

            when {
                editingId != null -> editProposal(
                    proposalId = editingId,
                    expectedVersion = snapshot.currentVersion,
                    newItems = items,
                    newReviewFlags = reviewFlags,
                ).fold(
                    onSuccess = {
                        _uiState.update { it.copy(isSaving = false) }
                        _events.trySend(ProposalEvent.NavigateBack)
                    },
                    onFailure = handleError,
                )

                parentId != null -> counterProposal(
                    parentProposalId = parentId,
                    items = items,
                    reviewFlags = reviewFlags,
                ).fold(
                    onSuccess = { newId ->
                        _uiState.update { it.copy(isSaving = false) }
                        if (newId.isNotBlank()) {
                            _events.trySend(ProposalEvent.NavigateToThread(newId, snapshot.rootProposalId))
                        } else {
                            _events.trySend(ProposalEvent.NavigateBack)
                        }
                    },
                    onFailure = handleError,
                )

                else -> createProposal(
                    receiverId = receiverId,
                    items = items,
                    includesReviewFromProposer = snapshot.includesReviewFromProposer,
                    includesReviewFromReceiver = snapshot.includesReviewFromReceiver,
                    autoSend = true,
                ).fold(
                    onSuccess = { newId ->
                        analyticsHelper.logEvent("trade_proposal_sent", mapOf(
                            "proposer_item_count" to snapshot.proposerItems.size,
                            "receiver_item_count" to snapshot.receiverItems.size,
                            "has_review_proposer" to snapshot.includesReviewFromProposer,
                            "has_review_receiver" to snapshot.includesReviewFromReceiver,
                        ))
                        _uiState.update { it.copy(isSaving = false) }
                        if (newId.isNotBlank()) {
                            _events.trySend(ProposalEvent.NavigateToThread(newId, newId))
                        } else {
                            _events.trySend(ProposalEvent.NavigateBack)
                        }
                    },
                    onFailure = handleError,
                )
            }
        }
    }

    private fun buildItemRequestDtos(state: ProposalEditorUiState): List<TradeItemRequestDto> {
        val myId = state.currentUserId
        val proposerDtos = state.proposerItems.map { it.toRequestDto(fromUserId = myId, toUserId = state.receiverId) }
        val receiverDtos = state.receiverItems.map { it.toRequestDto(fromUserId = state.receiverId, toUserId = myId) }
        return proposerDtos + receiverDtos
    }

    // ── FriendCard conversion helpers ─────────────────────────────────────────

    /** Builds a minimal [Card] from an enriched [FriendCard]. Returns null if the
     *  card has no name (i.e. Room/Scryfall enrichment failed for this row). */
    private fun FriendCard.toCard(): Card? {
        if (name.isBlank()) return null
        return Card(
            scryfallId = scryfallId,
            name = name,
            printedName = null,
            manaCost = null,
            cmc = 0.0,
            colors = emptyList(),
            colorIdentity = emptyList(),
            typeLine = typeLine,
            printedTypeLine = null,
            oracleText = null,
            printedText = null,
            keywords = emptyList(),
            power = null,
            toughness = null,
            loyalty = null,
            setCode = setCode ?: "",
            setName = setName ?: "",
            collectorNumber = "",
            rarity = rarity ?: "",
            releasedAt = "",
            frameEffects = emptyList(),
            promoTypes = emptyList(),
            lang = "",
            imageNormal = imageNormal,
            imageArtCrop = imageArtCrop,
            imageBackNormal = null,
            priceUsd = priceUsd,
            priceUsdFoil = priceUsdFoil,
            priceEur = priceEur,
            priceEurFoil = priceEurFoil,
            legalityStandard = "",
            legalityPioneer = "",
            legalityModern = "",
            legalityCommander = "",
            flavorText = null,
            artist = null,
            scryfallUri = "",
            isStale = isStale,
        )
    }

    /** Synthetic [WishlistEntry] carrying the friend's metadata into [TradeItemDraft]. */
    private fun FriendCard.toSyntheticWishlistEntry() = WishlistEntry(
        // One scryfallId can be wished in several variants; the id feeds LazyColumn keys.
        id = rowId ?: "${scryfallId}_${isFoil}_${condition}_${language}",
        userId = "",
        cardId = scryfallId,
        quantity = quantity,
        matchAnyVariant = false,
        isFoil = isFoil,
        condition = condition,
        language = language,
        createdAt = 0L,
    )

    /** Synthetic [OpenForTradeEntry] carrying the friend's metadata into [TradeItemDraft].
     *  userCardId is empty because the unified RPC does not expose individual copy IDs.
     *  The id is a composite key so that multiple variants of the same scryfallId produce
     *  distinct [AddCardRow.uniqueKey] values and don't collide in the suggestions list. */
    private fun FriendCard.toSyntheticOfferEntry() = OpenForTradeEntry(
        id = "${scryfallId}_${isFoil}_${condition}_${language}",
        userId = "",
        userCardId = "",
        scryfallId = scryfallId,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition ?: "NM",
        language = language ?: "en",
        createdAt = 0L,
    )

    private fun TradeItemDraft.toRequestDto(fromUserId: String, toUserId: String) = TradeItemRequestDto(
        fromUserId = fromUserId,
        toUserId = toUserId,
        userCardIdRef = userCardIdRef?.takeIf { it.isNotBlank() },
        quantity = if (isReviewCollectionPlaceholder) null else quantity,
        isFoil = if (isReviewCollectionPlaceholder) null else isFoil,
        condition = if (isReviewCollectionPlaceholder) null else condition,
        language = if (isReviewCollectionPlaceholder) null else language,
        cardId = cardId,
        isReviewCollectionPlaceholder = isReviewCollectionPlaceholder,
    )
}
