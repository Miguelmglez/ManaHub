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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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
)

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
    /** A draft was saved, or a proposal was sent/countered — navigate to its thread. */
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
    val collectionIds: Set<String> = emptySet(),

    val friends: List<Friend> = emptyList(),
    val selectedFriend: Friend? = null,
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
     * (see [onAddCardsQueryChange]); only the expensive [updateSearchLists] rebuild — which
     * filters + maps 4+ lists, potentially thousands of collection cards — is throttled
     * through this flow's [kotlinx.coroutines.flow.debounce] collector in `init`.
     */
    private val searchQueryFlow = MutableStateFlow("")

    private var currentUserId: String = ""

    // §6.3 fix: the debounced search-list rebuild (see `searchQueryFlow` below) now runs
    // `updateSearchLists` on `defaultDispatcher`, off the Main thread these are written from
    // (the `observe*` collectors). `@Volatile` guarantees the background reader sees the latest
    // write without needing its own synchronization — the same reasoning already applied to
    // `friendData` below, which is written on `ioDispatcher` and read from `updateSearchLists`.
    @Volatile private var collectionCards: List<Card> = emptyList()
    @Volatile private var wishlistEntries: List<WishlistEntry> = emptyList()
    @Volatile private var offerEntries: List<OpenForTradeEntry> = emptyList()

    private companion object {
        /** Matches the debounce window already used elsewhere in the app (News, Friend search). */
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    // Captured at init so a failed prefill (§2.9) can be retried from the screen.
    private var prefillProposalId: String? = null
    private var prefillRootProposalId: String = ""
    private var prefillReceiverId: String = ""

    // Friend data fetched via get_friend_collection RPC (unified endpoint).
    // Written on ioDispatcher and read on Main; @Volatile + single-copy assignment
    // ensures no partial-update races between the two RPC calls.
    private data class FriendData(
        val collection: List<Card> = emptyList(),
        val wishlist: List<FriendCard> = emptyList(),
        val offers: List<FriendCard> = emptyList(),
    )
    @Volatile private var friendData: FriendData = FriendData()
    private var friendDataJob: Job? = null

    init {
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

        // §6.3 fix: debounce the per-keystroke search-list rebuild and run it off Main.
        // `collectLatest` also cancels any rebuild still in flight for a now-superseded query.
        viewModelScope.launch {
            searchQueryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { query ->
                    withContext(defaultDispatcher) { updateSearchLists(query) }
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

        var proposal = tradesRepository.observeProposalThread(rootProposalId).first()
            .find { it.id == proposalToPreFill }

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
                .find { it.id == proposalToPreFill }
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
        val imageMap = buildMap<String, Card?> {
            allCardIds.forEach { id ->
                val r = cardRepository.getCardById(id)
                if (r is DataResult.Success) put(id, r.data)
            }
        }

        val myItems = proposal.items
            .filter { it.fromUserId != receiverId && !it.isReviewCollectionPlaceholder }
            .map { item ->
                val card = imageMap[item.cardId]
                TradeItemDraft(
                    cardId = item.cardId,
                    cardName = item.cardName,
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
                    cardName = item.cardName,
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
                .catch { e -> recordSafeNonFatal("trade_proposal_observe_collection_failed", e) }
                .collect { collection ->
                    collectionCards = collection.map { it.card }.distinctBy { it.scryfallId }.sortedBy { it.name }
                    val ids = collectionCards.map { it.scryfallId }.toSet()
                    _uiState.update { it.copy(collectionIds = ids) }
                    updateSearchLists(_uiState.value.addCardsQuery)
                }
        }
    }

    private fun observeWishlist() {
        viewModelScope.launch {
            wishlistRepository.observeLocal()
                .catch { e -> recordSafeNonFatal("trade_proposal_observe_wishlist_failed", e) }
                .collect { wishlist ->
                    wishlistEntries = wishlist.filter { it.card != null }.sortedBy { it.card?.name }
                    updateSearchLists(_uiState.value.addCardsQuery)
                }
        }
    }

    private fun observeOffers() {
        viewModelScope.launch {
            openForTradeRepository.observeLocal()
                .catch { e -> recordSafeNonFatal("trade_proposal_observe_offers_failed", e) }
                .collect { offers ->
                    offerEntries = offers.filter { it.card != null }.sortedBy { it.card?.name }
                    updateSearchLists(_uiState.value.addCardsQuery)
                }
        }
    }

    private fun observeFriends() {
        viewModelScope.launch {
            friendRepository.observeFriends()
                .catch { e -> recordSafeNonFatal("trade_proposal_observe_friends_failed", e) }
                .collect { friends ->
                    _uiState.update { it.copy(friends = friends) }
                    // Auto-select receiver if they are a friend
                    val receiverId = _uiState.value.receiverId
                    val receiverFriend = friends.find { it.userId == receiverId }
                    if (receiverFriend != null && _uiState.value.selectedFriend == null) {
                        onFriendSelected(receiverFriend)
                    }
                }
        }
    }

    fun onFriendSelected(friend: Friend?) {
        _uiState.update { it.copy(selectedFriend = friend, receiverId = friend?.userId ?: "") }
        if (friend != null) {
            fetchFriendData(friend.userId)
        } else {
            friendDataJob?.cancel()
            friendData = FriendData()
            updateSearchLists(_uiState.value.addCardsQuery)
        }
    }

    /**
     * Fetches the selected friend's wishlist, open-for-trade list, and public collection via
     * the unified get_friend_collection RPC. This RPC is SECURITY DEFINER and enforces both
     * friendship checks and per-list privacy flags (wishlist_public / trade_list_public),
     * unlike direct table queries which only check friendship.
     *
     * The previous [Job] is cancelled before starting a new fetch, and [friendData] is reset
     * immediately (audit §6.2) so switching friends quickly never briefly shows the *previous*
     * friend's lists while the new fetch is in flight. The three RPCs are launched concurrently
     * with [async]/[coroutineScope] (previously sequential — the selector could take up to 3x
     * as long as the slowest single call).
     */
    private fun fetchFriendData(userId: String) {
        friendDataJob?.cancel()
        friendData = FriendData()
        updateSearchLists(_uiState.value.addCardsQuery)

        friendDataJob = viewModelScope.launch(ioDispatcher) {
            coroutineScope {
                val wishlistDeferred = async { friendRepository.getFriendCollection(userId, "wishlist", "") }
                val offersDeferred = async { friendRepository.getFriendCollection(userId, "trade", "") }
                val collectionDeferred = async { friendRepository.getFriendCollection(userId, "collection", "") }

                wishlistDeferred.await()
                    .onSuccess { cards -> friendData = friendData.copy(wishlist = cards.sortedBy { it.name }) }
                    .onFailure { e ->
                        recordSafeNonFatal("trade_friend_wishlist_load_failed", e)
                        friendData = friendData.copy(wishlist = emptyList())
                    }

                offersDeferred.await()
                    .onSuccess { cards -> friendData = friendData.copy(offers = cards.sortedBy { it.name }) }
                    .onFailure { e ->
                        recordSafeNonFatal("trade_friend_offers_load_failed", e)
                        friendData = friendData.copy(offers = emptyList())
                    }

                collectionDeferred.await()
                    .onSuccess { cards ->
                        friendData = friendData.copy(
                            collection = cards.mapNotNull { it.toCard() }.sortedBy { it.name }
                        )
                    }
                    .onFailure { e ->
                        recordSafeNonFatal("trade_friend_collection_load_failed", e)
                        friendData = friendData.copy(collection = emptyList())
                    }
            }
            updateSearchLists(_uiState.value.addCardsQuery)
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
        updateSearchLists(_uiState.value.addCardsQuery)
    }

    fun setNavigatingToDetail(isNavigating: Boolean) {
        _uiState.update { it.copy(isNavigatingToDetail = isNavigating) }
    }

    /**
     * Rebuilds every add-cards search list ([ProposalEditorUiState.offerResults] /
     * [ProposalEditorUiState.addCardsResults] / [ProposalEditorUiState.wishlistResults] /
     * match suggestions) for the given [query].
     *
     * Filters + maps 4+ lists (the user's collection can be thousands of cards), so callers
     * driven by user typing MUST go through the debounced [searchQueryFlow] (see
     * [onAddCardsQueryChange]) rather than calling this directly — this function itself stays
     * synchronous so programmatic callers (collection/wishlist/offer refresh, friend switch,
     * item add/remove) keep updating the search lists immediately with no added latency
     * (audit §6.3).
     */
    private fun updateSearchLists(query: String) {
        // My collection is always the source for the addCardsResults (collection browser).
        // Pure function of `query` + the plain backing fields below (none of which live in
        // `_uiState`), so it's safe to compute once outside the atomic update.
        val filteredCollection = if (query.isBlank()) collectionCards
            else collectionCards.filter { it.name.contains(query, ignoreCase = true) }

        // ── Correct side-aware mapping ─────────────────────────────────────────
        // PROPOSER (A → B): A offers cards to B.
        //   offerResults   → MY offers (offerEntries) — what I have available to give
        //   wishlistResults → FRIEND's wishlist (friendWishlistCards) — what B wants
        //   addCardsResults → MY collection (collectionCards) — fallback search
        //
        // RECEIVER (A ← B): A requests cards from B.
        //   offerResults   → FRIEND's offers (friendOfferCards) — what B has available
        //   wishlistResults → MY wishlist (wishlistEntries) — what I want
        //   addCardsResults → MY collection (collectionCards) — fallback search

        _uiState.update { s ->
            // §6.3 fix: `isFriendSelected` / `searchingSide` / `ownedIds` are derived from
            // `_uiState` and MUST be read from this lambda's `s` snapshot, never captured in a
            // `val` before `_uiState.update {}` — `update {}` retries its lambda against the
            // latest value on contention, so a `val` captured beforehand can go stale mid-flight
            // (e.g. a concurrent `onOpenSearch`/`onFriendSelected` changes `searchingSide`
            // between the outer read and this lambda committing) and the results computed for
            // the OLD side would be applied on top of the NEW state. This is the project's
            // banned stale-snapshot pattern (CLAUDE.md's Deck Doctor `generateFromSeeds`
            // atomic-capture precedent).
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

            when (searchingSide) {
                TradeSide.PROPOSER -> {
                    // A is offering cards: show MY offers + FRIEND's wishlist
                    val friendWishlist = friendData.wishlist
                    val filteredOffer = if (query.isBlank()) offerEntries
                        else offerEntries.filter { it.card?.name?.contains(query, ignoreCase = true) == true }
                    val filteredFriendWishlist = if (query.isBlank()) friendWishlist
                        else friendWishlist.filter { it.name.contains(query, ignoreCase = true) }

                    s.copy(
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
                    val friendOffers = friendData.offers
                    val friendCollection = friendData.collection
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

                    s.copy(
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
                    // No sheet open: only update the match suggestions (data may have refreshed)
                    s.copy(
                        proposerMatches = computeProposerMatches(s, ownedIds),
                        receiverMatches = computeReceiverMatches(s, ownedIds),
                    )
                }
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
        val currentFriendWishlist = friendData.wishlist
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
        val currentFriendOffers = friendData.offers
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

    fun searchScryfallDirect(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(scryfallResults = emptyList(), isSearchingScryfall = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingScryfall = true) }
            try {
                val cards = when (val result = cardRepository.searchCards(query)) {
                    is DataResult.Success -> result.data
                    is DataResult.Error -> emptyList()
                }
                val ownedIds = _uiState.value.collectionIds
                _uiState.update { s ->
                    val sideItems = when (s.searchingSide) {
                        TradeSide.PROPOSER -> s.proposerItems
                        TradeSide.RECEIVER -> s.receiverItems
                        null -> s.proposerItems + s.receiverItems
                    }
                    val allItems = sideItems + s.pendingAddedItems
                    s.copy(
                    isSearchingScryfall = false,
                    scryfallResults = cards.map { card ->
                        AddCardRow(
                            card = card,
                            quantityInDeck = allItems.filter { it.cardId == card.scryfallId && it.userCardIdRef == null }.sumOf { it.quantity },
                            isOwned = card.scryfallId in ownedIds,
                            availableQuantity = 0,
                        )
                    },
                )
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().apply {
                    log("trade_scryfall_search_failed: query_length=${query.length}")
                    setCustomKey("scryfall_query_length", query.length)
                    setCustomKey("scryfall_error_type", e::class.simpleName ?: "Unknown")
                    recordException(RuntimeException("[TradeProposal] Scryfall search failed", e))
                }
                _uiState.update { it.copy(isSearchingScryfall = false) }
            }
        }
    }

    fun clearAddCardsState() {
        _uiState.update { it.copy(
            addCardsQuery = "",
            addCardsResults = emptyList(),
            offerResults = emptyList(),
            wishlistResults = emptyList(),
            scryfallResults = emptyList(),
            pendingAddedItems = emptyList(),
            searchingSide = null,
            isNavigatingToDetail = false,
        ) }
    }

    fun getCardById(scryfallId: String): Card? {
        return (uiState.value.addCardsResults + uiState.value.scryfallResults + uiState.value.wishlistResults + uiState.value.offerResults)
            .find { it.card.scryfallId == scryfallId }?.card
    }

    fun addProposerItem(item: TradeItemDraft) {
        _uiState.update { s ->
            val existing = s.pendingAddedItems.find { it.cardId == item.cardId && it.isFoil == item.isFoil && it.condition == item.condition && it.language == item.language && it.userCardIdRef == item.userCardIdRef }
            if (existing != null) {
                s.copy(pendingAddedItems = s.pendingAddedItems.map { if (it.id == existing.id) it.copy(quantity = it.quantity + 1) else it })
            } else {
                s.copy(pendingAddedItems = s.pendingAddedItems + item)
            }
        }
        updateSearchLists(_uiState.value.addCardsQuery)
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
        updateSearchLists(_uiState.value.addCardsQuery)
    }

    fun addReceiverItem(item: TradeItemDraft) {
        _uiState.update { s ->
            val existing = s.pendingAddedItems.find { it.cardId == item.cardId && it.isFoil == item.isFoil && it.condition == item.condition && it.language == item.language && it.userCardIdRef == item.userCardIdRef }
            if (existing != null) {
                s.copy(pendingAddedItems = s.pendingAddedItems.map { if (it.id == existing.id) it.copy(quantity = it.quantity + 1) else it })
            } else {
                s.copy(pendingAddedItems = s.pendingAddedItems + item)
            }
        }
        updateSearchLists(_uiState.value.addCardsQuery)
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
        updateSearchLists(_uiState.value.addCardsQuery)
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
                        if (it.id == existing.id) it.copy(quantity = it.quantity + 1) else it
                    }
                )
            } else {
                s.copy(proposerItems = s.proposerItems + item)
            }
        }
        updateSearchLists(_uiState.value.addCardsQuery)
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
                        if (it.id == existing.id) it.copy(quantity = it.quantity + 1) else it
                    }
                )
            } else {
                s.copy(receiverItems = s.receiverItems + item)
            }
        }
        updateSearchLists(_uiState.value.addCardsQuery)
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
                result[idx] = duplicate.copy(quantity = duplicate.quantity + addition.quantity)
            } else {
                result.add(addition)
            }
        }
        return result
    }

    fun onCancelPendingItems() {
        _uiState.update { it.copy(pendingAddedItems = emptyList()) }
    }

    fun toggleReviewCollectionProposer() {
        _uiState.update { it.copy(includesReviewFromProposer = !it.includesReviewFromProposer) }
    }

    fun toggleReviewCollectionReceiver() {
        _uiState.update { it.copy(includesReviewFromReceiver = !it.includesReviewFromReceiver) }
    }

    fun updateProposerItem(updated: TradeItemDraft) {
        _uiState.update {
            it.copy(proposerItems = it.proposerItems.map { i -> if (i.id == updated.id) updated else i })
        }
    }

    fun updateReceiverItem(updated: TradeItemDraft) {
        _uiState.update {
            it.copy(receiverItems = it.receiverItems.map { i -> if (i.id == updated.id) updated else i })
        }
    }

    private fun validateInitialProposal(state: ProposalEditorUiState): Boolean {
        val proposerCovered = state.proposerItems.isNotEmpty() || state.includesReviewFromProposer
        val receiverCovered = state.receiverItems.isNotEmpty() || state.includesReviewFromReceiver
        return proposerCovered && receiverCovered
    }

    fun onSaveDraft() {
        val state = _uiState.value
        // A missing receiver ID would send an empty string to the RPC, which
        // either fails with a cryptic server error or creates a malformed proposal.
        if (!state.isCounterMode && state.receiverId.isBlank()) {
            _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_error_no_receiver))
            return
        }
        if (!state.isCounterMode && state.receiverId == state.currentUserId) {
            _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_error_self_trade))
            return
        }
        if (!validateInitialProposal(state)) {
            _events.trySend(ProposalEvent.ShowValidationError(R.string.trades_error_initial_asymmetry))
            return
        }

        // Atomic capture-and-flip: bail if a save/send is already in flight so a fast
        // double-tap can never create two proposals (audit §2.7). The button's
        // `enabled = !isSaving` alone is not sufficient — it updates asynchronously and
        // leaves a window between two taps. Mirrors the DeckStudioViewModel.generateFromSeeds
        // precedent (CLAUDE.md).
        var captured: ProposalEditorUiState? = null
        _uiState.update { s ->
            if (s.isSaving) return@update s
            captured = s
            s.copy(isSaving = true)
        }
        val snapshot = captured ?: return

        viewModelScope.launch(ioDispatcher) {
            val result = createProposal(
                receiverId = snapshot.receiverId,
                items = buildItemRequestDtos(snapshot),
                includesReviewFromProposer = snapshot.includesReviewFromProposer,
                includesReviewFromReceiver = snapshot.includesReviewFromReceiver,
                autoSend = false,
            )
            result.fold(
                onSuccess = { proposalId ->
                    _uiState.update { it.copy(isSaving = false) }
                    _events.trySend(ProposalEvent.NavigateToThread(proposalId, proposalId))
                },
                onFailure = { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_proposal_save_draft_failed")
                        setCustomKey("trade_proposer_item_count", snapshot.proposerItems.size)
                        recordException(e)
                    }
                    _uiState.update { it.copy(isSaving = false) }
                    // Only a typed TradeError's pre-resolved friendly text reaches the user;
                    // any other exception's raw message is never shown (audit §5.1) — the
                    // screen falls back to a generic string when this is null.
                    _events.trySend(ProposalEvent.ShowRemoteError(if (e is TradeError) e.toUserFacingMessage() else null))
                }
            )
        }
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

        // Atomic capture-and-flip — see onSaveDraft() for rationale (audit §2.7).
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
        id = scryfallId,
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
