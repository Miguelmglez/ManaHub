package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.model.AddCardRow
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendCard
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.domain.model.OpenForTradeEntry
import com.mmg.manahub.feature.trades.domain.model.TradeError
import com.mmg.manahub.feature.trades.domain.model.TradeSide
import com.mmg.manahub.core.domain.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import com.mmg.manahub.feature.trades.domain.repository.ReviewFlags
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import com.mmg.manahub.feature.trades.domain.usecase.CounterProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CreateTradeProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.EditProposalUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val navigateToThread: Pair<String, String>? = null, // (proposalId, rootProposalId)
    val navigateBack: Boolean = false,

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
) {
    val totalProposerValueUsd: Double get() = proposerItems.sumOf { 
        val price = if (it.isFoil) (it.priceUsdFoil ?: it.priceUsd ?: 0.0) else (it.priceUsd ?: 0.0)
        price * it.quantity 
    }
    val totalReceiverValueUsd: Double get() = receiverItems.sumOf { 
        val price = if (it.isFoil) (it.priceUsdFoil ?: it.priceUsd ?: 0.0) else (it.priceUsd ?: 0.0)
        price * it.quantity 
    }
    val totalProposerValueEur: Double get() = proposerItems.sumOf { 
        val price = if (it.isFoil) (it.priceEurFoil ?: it.priceEur ?: 0.0) else (it.priceEur ?: 0.0)
        price * it.quantity 
    }
    val totalReceiverValueEur: Double get() = receiverItems.sumOf { 
        val price = if (it.isFoil) (it.priceEurFoil ?: it.priceEur ?: 0.0) else (it.priceEur ?: 0.0)
        price * it.quantity 
    }
}

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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProposalEditorUiState())
    val uiState: StateFlow<ProposalEditorUiState> = _uiState.asStateFlow()

    private var currentUserId: String = ""
    private var collectionCards: List<Card> = emptyList()
    private var wishlistEntries: List<WishlistEntry> = emptyList()
    private var offerEntries: List<OpenForTradeEntry> = emptyList()

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
            viewModelScope.launch(ioDispatcher) {
                val proposals = tradesRepository.observeProposalThread(rootProposalId).first()
                val proposal = proposals.find { it.id == proposalToPreFill } ?: return@launch

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
                        proposerItems = myItems,
                        receiverItems = theirItems,
                        includesReviewFromProposer = if (iAmProposer) proposal.includesReviewCollectionFromProposer else proposal.includesReviewCollectionFromReceiver,
                        includesReviewFromReceiver = if (iAmProposer) proposal.includesReviewCollectionFromReceiver else proposal.includesReviewCollectionFromProposer,
                        currentVersion = proposal.proposalVersion,
                    )
                }
            }
        }
    }

    private fun observeCollection() {
        viewModelScope.launch {
            userCardRepository.observeCollection().collect { collection ->
                collectionCards = collection.map { it.card }.distinctBy { it.scryfallId }.sortedBy { it.name }
                val ids = collectionCards.map { it.scryfallId }.toSet()
                _uiState.update { it.copy(collectionIds = ids) }
                updateSearchLists(_uiState.value.addCardsQuery)
            }
        }
    }

    private fun observeWishlist() {
        viewModelScope.launch {
            wishlistRepository.observeLocal().collect { wishlist ->
                wishlistEntries = wishlist.filter { it.card != null }.sortedBy { it.card?.name }
                updateSearchLists(_uiState.value.addCardsQuery)
            }
        }
    }

    private fun observeOffers() {
        viewModelScope.launch {
            openForTradeRepository.observeLocal().collect { offers ->
                offerEntries = offers.filter { it.card != null }.sortedBy { it.card?.name }
                updateSearchLists(_uiState.value.addCardsQuery)
            }
        }
    }

    private fun observeFriends() {
        viewModelScope.launch {
            friendRepository.observeFriends().collect { friends ->
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
     * Fetches the selected friend's wishlist and open-for-trade list via the unified
     * get_friend_collection RPC. This RPC is SECURITY DEFINER and enforces both
     * friendship checks and per-list privacy flags (wishlist_public / trade_list_public),
     * unlike direct table queries which only check friendship.
     *
     * The previous [Job] is cancelled before starting a new fetch so that switching
     * friends quickly never applies stale data from the previous selection.
     */
    private fun fetchFriendData(userId: String) {
        friendDataJob?.cancel()
        friendDataJob = viewModelScope.launch(ioDispatcher) {
            // Wishlist via unified RPC — single atomic copy update to avoid partial-state reads
            friendRepository.getFriendCollection(userId, "wishlist", "")
                .onSuccess { cards ->
                    friendData = friendData.copy(wishlist = cards.sortedBy { it.name })
                    updateSearchLists(_uiState.value.addCardsQuery)
                }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_friend_wishlist_load_failed: userId=$userId")
                        recordException(RuntimeException("[TradeProposal] Friend wishlist fetch failed", e))
                    }
                    friendData = friendData.copy(wishlist = emptyList())
                }

            // Open-for-trade via unified RPC
            friendRepository.getFriendCollection(userId, "trade", "")
                .onSuccess { cards ->
                    friendData = friendData.copy(offers = cards.sortedBy { it.name })
                    updateSearchLists(_uiState.value.addCardsQuery)
                }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_friend_offers_load_failed: userId=$userId")
                        recordException(RuntimeException("[TradeProposal] Friend offers fetch failed", e))
                    }
                    friendData = friendData.copy(offers = emptyList())
                }

            // Public collection via unified RPC — used in the "You get" side Offer tab
            friendRepository.getFriendCollection(userId, "collection", "")
                .onSuccess { cards ->
                    friendData = friendData.copy(
                        collection = cards.mapNotNull { it.toCard() }.sortedBy { it.name }
                    )
                    updateSearchLists(_uiState.value.addCardsQuery)
                }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_friend_collection_load_failed: userId=$userId")
                        recordException(RuntimeException("[TradeProposal] Friend collection fetch failed", e))
                    }
                    friendData = friendData.copy(collection = emptyList())
                }

            updateSearchLists(_uiState.value.addCardsQuery)
        }
    }

    fun onAddCardsQueryChange(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        updateSearchLists(query)
    }

    fun onOpenSearch(side: TradeSide) {
        _uiState.update { it.copy(searchingSide = side, isNavigatingToDetail = false) }
        updateSearchLists(_uiState.value.addCardsQuery)
    }

    fun setNavigatingToDetail(isNavigating: Boolean) {
        _uiState.update { it.copy(isNavigatingToDetail = isNavigating) }
    }

    private fun updateSearchLists(query: String) {
        val state = _uiState.value
        val isFriendSelected = state.selectedFriend != null
        val searchingSide = state.searchingSide

        // My collection is always the source for the addCardsResults (collection browser).
        val filteredCollection = if (query.isBlank()) collectionCards
            else collectionCards.filter { it.name.contains(query, ignoreCase = true) }

        val ownedIds = state.collectionIds

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
            _uiState.update { it.copy(errorMessage = "NO_RECEIVER") }
            return
        }
        if (!state.isCounterMode && state.receiverId == state.currentUserId) {
            _uiState.update { it.copy(errorMessage = "SELF_TRADE") }
            return
        }
        if (!validateInitialProposal(state)) {
            _uiState.update { it.copy(errorMessage = "INITIAL_ASYMMETRY") }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSaving = true) }
            val result = createProposal(
                receiverId = state.receiverId,
                items = buildItemRequestDtos(state),
                includesReviewFromProposer = state.includesReviewFromProposer,
                includesReviewFromReceiver = state.includesReviewFromReceiver,
                autoSend = false,
            )
            _uiState.update { s ->
                result.fold(
                    onSuccess = { proposalId ->
                        s.copy(isSaving = false, navigateToThread = Pair(proposalId, proposalId))
                    },
                    onFailure = { e ->
                        FirebaseCrashlytics.getInstance().apply {
                            log("trade_proposal_save_draft_failed")
                            setCustomKey("trade_proposer_item_count", s.proposerItems.size)
                            recordException(e)
                        }
                        s.copy(isSaving = false, errorMessage = e.toUserFacingMessage() ?: e.message)
                    }
                )
            }
        }
    }

    fun onSendProposal() {
        val state = _uiState.value
        val myId = state.currentUserId
        val receiverId = state.receiverId

        // Guard: a proposal requires both participants to be identified.
        if (myId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Not logged in") }
            return
        }
        if (receiverId.isBlank() && !state.isCounterMode && state.editingProposalId == null) {
            _uiState.update { it.copy(errorMessage = "NO_RECEIVER") }
            return
        }
        if (!state.isCounterMode && state.editingProposalId == null && receiverId == myId) {
            _uiState.update { it.copy(errorMessage = "SELF_TRADE") }
            return
        }

        if (!validateInitialProposal(state)) {
            _uiState.update { it.copy(errorMessage = "INITIAL_ASYMMETRY") }
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSaving = true) }

            val editingId = state.editingProposalId
            val parentId = state.parentProposalId
            val items = buildItemRequestDtos(state)
            val reviewFlags = ReviewFlags(
                state.includesReviewFromProposer,
                state.includesReviewFromReceiver,
            )

            FirebaseCrashlytics.getInstance().apply {
                log("trade_proposal_send_attempt: isCounter=${state.isCounterMode}, proposerItems=${state.proposerItems.size}, receiverItems=${state.receiverItems.size}")
                setCustomKey("trade_my_id", myId)
                setCustomKey("trade_receiver_id", receiverId)
            }

            // Each branch is handled separately so every Result stays properly typed
            // and no unchecked casts or vacuous .map { it } calls are needed.
            val handleError: (Throwable) -> Unit = { e ->
                val msg = when (e) {
                    is TradeError.ProposalVersionMismatch    -> "PROPOSAL_VERSION_MISMATCH"
                    is TradeError.InitialAsymmetryNotAllowed -> "INITIAL_ASYMMETRY"
                    else -> {
                        FirebaseCrashlytics.getInstance().apply {
                            log("trade_proposal_send_failed: isCounter=${state.isCounterMode}")
                            setCustomKey("trade_proposer_item_count", state.proposerItems.size)
                            setCustomKey("trade_receiver_item_count", state.receiverItems.size)
                            recordException(e)
                        }
                        e.toUserFacingMessage() ?: e.message ?: "Unknown error"
                    }
                }
                _uiState.update { it.copy(isSaving = false, errorMessage = msg) }
            }

            when {
                editingId != null -> editProposal(
                    proposalId = editingId,
                    expectedVersion = state.currentVersion,
                    newItems = items,
                    newReviewFlags = reviewFlags,
                ).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, navigateBack = true) } },
                    onFailure = handleError,
                )

                parentId != null -> counterProposal(
                    parentProposalId = parentId,
                    items = items,
                    reviewFlags = reviewFlags,
                ).fold(
                    onSuccess = { newId ->
                        _uiState.update { s ->
                            s.copy(
                                isSaving = false,
                                navigateToThread = if (newId.isNotBlank()) Pair(newId, s.rootProposalId) else null,
                                navigateBack = newId.isBlank(),
                            )
                        }
                    },
                    onFailure = handleError,
                )

                else -> createProposal(
                    receiverId = receiverId,
                    items = items,
                    includesReviewFromProposer = state.includesReviewFromProposer,
                    includesReviewFromReceiver = state.includesReviewFromReceiver,
                    autoSend = true,
                ).fold(
                    onSuccess = { newId ->
                        analyticsHelper.logEvent("trade_proposal_sent", mapOf(
                            "proposer_item_count" to state.proposerItems.size,
                            "receiver_item_count" to state.receiverItems.size,
                            "has_review_proposer" to state.includesReviewFromProposer,
                            "has_review_receiver" to state.includesReviewFromReceiver,
                        ))
                        _uiState.update { s ->
                            s.copy(
                                isSaving = false,
                                navigateToThread = if (newId.isNotBlank()) Pair(newId, newId) else null,
                                navigateBack = newId.isBlank(),
                            )
                        }
                    },
                    onFailure = handleError,
                )
            }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(errorMessage = null) }
    fun onSnackbarDismissed() = _uiState.update { it.copy(snackbarMessage = null) }
    fun onNavigationConsumed() = _uiState.update { it.copy(navigateToThread = null, navigateBack = false) }

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
