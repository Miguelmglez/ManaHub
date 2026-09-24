package com.mmg.manahub.feature.addcard.presentation

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityDecksRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.search.AdvancedSearchCardMatcher
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.queue.AddAllToCollectionResult
import com.mmg.manahub.core.domain.usecase.queue.CardQueueActions
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.model.CardAddOrigin
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.GetSpotlightFeedUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.CollectionViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel of the AddCard screen: Scryfall search plus the "Select multiple" mode, where tapping a
 * result toggles its presence in the app-wide [CardQueueRepository] shared with the Scanner.
 *
 * Queue commits run in `appScope` (on the main dispatcher, like every other queue mutation) so an
 * "add all" started here finishes even if the user leaves the screen mid-commit.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AddCardViewModel(
    private val searchCards:        SearchCardsUseCase,
    private val userPreferences:    UserPreferencesRepository,
    private val buildScryfallQuery: BuildScryfallQueryUseCase,
    private val getSpotlightFeed:   GetSpotlightFeedUseCase,
    private val queueRepository:    CardQueueRepository,
    private val queueActions:       CardQueueActions,
    private val userCardRepository: UserCardRepository,
    private val cardRepository:     CardRepository,
    private val deckRepository:     DeckRepository,
    private val communityDecksRepository: CommunityDecksRepository,
    appScope:                       CoroutineScope,
    launchArgs:                     AddCardLaunchArgs = AddCardLaunchArgs(),
    private val restorableState:    AddCardRestorableState = InMemoryAddCardRestorableState(),
    private val nowMillis:          () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddCardUiState(
            queue = queueRepository.queue.value,
            selectedScryfallIds = queueRepository.queue.value.selectedIds(),
            isCommittingQueue = queueActions.isCommitting.value,
            isAddingAllToWishlist = queueActions.isAddingAllToWishlist.value,
            inFlightQueueIds = queueActions.inFlightIds.value,
        )
    )
    val uiState: StateFlow<AddCardUiState> = _uiState.asStateFlow()

    // The repository is not synchronized: keeping commit-side mutations on the main thread avoids
    // racing the UI's toggles while still outliving this ViewModel.
    private val commitScope = CoroutineScope(appScope.coroutineContext + Dispatchers.Main.immediate)

    private var printsLoadJob: Job? = null
    private var variantLoadJob: Job? = null
    private var deckLoadJob: Job? = null
    private val reportedDeckLoadFailures = mutableSetOf<AddCardDeckSource>()

    private val textQueryFlow  = MutableStateFlow("")
    private val activeQueryFlow = MutableStateFlow<AdvancedSearchQuery?>(null)

    private val forceSearchTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var lastEffectiveQuery: String? = null
    private var currentSpotlightSetIndex: Int = 0
    private var hasMoreSpotlightSets: Boolean = true

    val gridState = LazyGridState()

    init {
        observeSharedQueue()
        observeOwnedCardIdentityKeys()
        // The nav args outlive "Clear deck cards"; a restored screen must not reload a cleared deck.
        val args = if (restorableState.isDeckSourceCleared) launchArgs.withoutDeckSource() else launchArgs
        if (args.multi || args.deckSource != null) enableMultiSelectMode()
        args.entryPoint?.let(::logEntryPointOnce)
        args.deckSource?.let(::loadDeckSource)
        viewModelScope.launch {
            val dataFlow = combine(
                textQueryFlow.debounce(400L),
                activeQueryFlow,
                userPreferences.preferencesFlow,
            ) { text, active, prefs ->
                Triple(text, active, prefs)
            }.distinctUntilChanged()

            combine(
                dataFlow,
                forceSearchTrigger.onStart { emit(Unit) }
            ) { data, _ -> data }
                .collectLatest { (text, active, prefs) ->
                    _uiState.update {
                        it.copy(
                            preferredCurrency = prefs.preferredCurrency,
                            searchLanguage = prefs.cardLanguage.toScryfallCode(),
                        )
                    }
                    // A preloaded deck list is filtered locally; Scryfall is never queried meanwhile.
                    if (_uiState.value.isDeckMode) return@collectLatest

                    val advancedString = active?.let { buildScryfallQuery(it) } ?: ""
                    val combinedQuery = when {
                        text.isNotBlank() && advancedString.isNotBlank() -> "$text $advancedString"
                        text.isNotBlank()                                -> text
                        advancedString.isNotBlank()                      -> advancedString
                        else                                             -> ""
                    }

                    if (combinedQuery.isBlank() || (text.length < 2 && advancedString.isBlank())) {
                        _uiState.update { 
                            it.copy(
                                results = emptyList(), 
                                isSearching = false,
                                isLoadingMore = false,
                                hasMore = false,
                                currentPage = 1,
                                error = null
                            ) 
                        }
                        lastEffectiveQuery = null
                        return@collectLatest
                    }

                    _uiState.update { 
                        it.copy(
                            isSearching = true, 
                            error = null,
                            isLoadingMore = false,
                            hasMore = false,
                            currentPage = 1
                            // We don't clear results here to keep stale results visible (F-08)
                        ) 
                    }
                    val lang = _uiState.value.searchLanguage
                    val effectiveQuery = if (lang != "en" && !hasLangToken(combinedQuery)) {
                        "$combinedQuery lang:$lang"
                    } else {
                        combinedQuery
                    }
                    lastEffectiveQuery = effectiveQuery

                    when (val result = searchCards(effectiveQuery, page = 1)) {
                        is DataResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    results = result.data.cards,
                                    isSearching = false,
                                    error = null,
                                    hasMore = result.data.hasMore,
                                    totalCards = result.data.totalCards,
                                    currentPage = 1
                                )
                            }
                        }
                        is DataResult.Error -> _uiState.update {
                            it.copy(error = result.message, isSearching = false)
                        }
                    }
                }
                
            // Launch spotlight feed fetch
            loadSpotlightFeed()
        }
    }

    private fun hasLangToken(query: String): Boolean {
        return query.split("\\s+".toRegex()).any { it.startsWith("lang:") }
    }

    fun forceSearch() {
        forceSearchTrigger.tryEmit(Unit)
    }

    fun loadNextPage() {
        val query = lastEffectiveQuery ?: return
        val currentState = _uiState.value
        if (currentState.isDeckMode) return
        
        if (!currentState.hasMore || currentState.isLoadingMore || currentState.isSearching) return
        
        val nextPage = currentState.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        
        viewModelScope.launch {
            when (val result = searchCards(query, nextPage)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(
                        results = it.results + result.data.cards,
                        isLoadingMore = false,
                        hasMore = result.data.hasMore,
                        currentPage = nextPage
                    )
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(isLoadingMore = false, error = result.message)
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        textQueryFlow.value = query
        applyDeckFilter()
    }

    fun onAdvancedQuerySearch(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(activeQuery = query) }
        activeQueryFlow.value = query
        
        val nameCriterion = query.criteria.filterIsInstance<com.mmg.manahub.core.model.SearchCriterion.Name>().firstOrNull()
        if (nameCriterion != null && nameCriterion.value.isNotBlank()) {
            onQueryChange(nameCriterion.value)
        }
        applyDeckFilter()
    }

    fun onClearFilters() {
        _uiState.update { it.copy(activeQuery = null) }
        activeQueryFlow.value = null
        applyDeckFilter()
    }

    fun onClearAll() {
        _uiState.update {
            it.copy(
                query = "",
                activeQuery = null,
                results = emptyList(),
                isSearching = false,
                isLoadingMore = false,
                hasMore = false,
                currentPage = 1,
                error = null
            )
        }
        textQueryFlow.value = ""
        activeQueryFlow.value = null
        lastEffectiveQuery = null
        applyDeckFilter()
    }

    fun onLanguageChange(code: String) {
        viewModelScope.launch {
            val lang = when (code) {
                "es"  -> com.mmg.manahub.core.model.CardLanguage.SPANISH
                "de"  -> com.mmg.manahub.core.model.CardLanguage.GERMAN
                "fr"  -> com.mmg.manahub.core.model.CardLanguage.FRENCH
                "it"  -> com.mmg.manahub.core.model.CardLanguage.ITALIAN
                "pt"  -> com.mmg.manahub.core.model.CardLanguage.PORTUGUESE
                "ja"  -> com.mmg.manahub.core.model.CardLanguage.JAPANESE
                "ko"  -> com.mmg.manahub.core.model.CardLanguage.KOREAN
                "ru"  -> com.mmg.manahub.core.model.CardLanguage.RUSSIAN
                "zhs" -> com.mmg.manahub.core.model.CardLanguage.CHINESE_SIMPLIFIED
                "zht" -> com.mmg.manahub.core.model.CardLanguage.CHINESE_TRADITIONAL
                else  -> com.mmg.manahub.core.model.CardLanguage.ENGLISH
            }
            userPreferences.setCardLanguage(lang)
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun loadSpotlightFeed() {
        val currentState = _uiState.value
        if (currentState.isSpotlightLoading || !hasMoreSpotlightSets) return
        if (currentState.query.length >= 2 || currentState.activeQuery != null) return // Only load if idle
        
        _uiState.update { it.copy(isSpotlightLoading = true) }
        viewModelScope.launch {
            when (val result = getSpotlightFeed(currentSpotlightSetIndex)) {
                is DataResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            spotlightCards = it.spotlightCards + result.data.cards,
                            spotlightSet = result.data.sourceSet,
                            isSpotlightLoading = false
                        )
                    }
                    currentSpotlightSetIndex = result.data.nextSetIndex
                }
                is DataResult.Error -> {
                    _uiState.update { it.copy(isSpotlightLoading = false) }
                    if (result.message == "No more sets available") {
                        hasMoreSpotlightSets = false
                    }
                }
            }
        }
    }

    fun onViewModeToggle(){
        _uiState.update { it.copy(viewMode = if (it.viewMode == CollectionViewMode.GRID) CollectionViewMode.LIST else CollectionViewMode.GRID) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-select mode + shared queue
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeSharedQueue() {
        viewModelScope.launch {
            queueRepository.queue.collect { cards -> _uiState.update { it.withQueue(cards) } }
        }
        viewModelScope.launch {
            queueActions.isCommitting.collect { committing ->
                _uiState.update { it.copy(isCommittingQueue = committing) }
            }
        }
        viewModelScope.launch {
            queueActions.isAddingAllToWishlist.collect { adding ->
                _uiState.update { it.copy(isAddingAllToWishlist = adding) }
            }
        }
        viewModelScope.launch {
            queueActions.inFlightIds.collect { ids -> _uiState.update { it.copy(inFlightQueueIds = ids) } }
        }
    }

    // An empty queue sheet has nothing to act on (its "Proceed" CTA is already gone).
    private fun AddCardUiState.withQueue(cards: List<QueuedCard>) = copy(
        queue = cards,
        selectedScryfallIds = cards.selectedIds(),
        showQueueSheet = showQueueSheet && cards.isNotEmpty(),
    )

    // The repository mutates synchronously; mirroring immediately keeps reads right after a
    // mutation consistent instead of waiting for the collector to be dispatched.
    private fun syncQueueSnapshot() {
        val cards = queueRepository.queue.value
        _uiState.update {
            it.withQueue(cards).copy(
                isCommittingQueue = queueActions.isCommitting.value,
                isAddingAllToWishlist = queueActions.isAddingAllToWishlist.value,
                inFlightQueueIds = queueActions.inFlightIds.value,
            )
        }
    }

    // Only collected while multi-select is on: the owned badge is only visible in the queue sheet.
    private fun observeOwnedCardIdentityKeys() {
        viewModelScope.launch {
            _uiState.map { it.isMultiSelectMode }
                .distinctUntilChanged()
                .flatMapLatest { active ->
                    if (active) {
                        userCardRepository.observeCollection().map { rows -> rows.ownedIdentityKeys() }
                    } else {
                        flowOf(emptySet())
                    }
                }
                .collect { keys -> _uiState.update { it.copy(ownedCardIdentityKeys = keys) } }
        }
    }

    /** Turns "Select multiple" on; idempotent (nav args and deck sources call it unconditionally). */
    fun enableMultiSelectMode() {
        _uiState.update { if (it.isMultiSelectMode) it else it.copy(isMultiSelectMode = true) }
    }

    /** Turns "Select multiple" on or off. Turning it off closes the queue sheet; the queue is kept. */
    fun onToggleMultiSelectMode() {
        _uiState.update {
            val enabled = !it.isMultiSelectMode
            it.copy(isMultiSelectMode = enabled, showQueueSheet = it.showQueueSheet && enabled)
        }
        val enabled = _uiState.value.isMultiSelectMode
        AddCardTelemetry.multiSelectToggled(enabled)
        if (enabled) logEntryPointOnce(MultiSelectEntryPoint.TOOLBAR)
    }

    private fun logEntryPointOnce(entryPoint: MultiSelectEntryPoint) {
        if (restorableState.isEntryPointLogged) return
        restorableState.isEntryPointLogged = true
        AddCardTelemetry.multiSelectOpenedFrom(entryPoint)
    }

    /**
     * Toggles [card]'s selection: an unselected card is queued once (non-foil, NM, its own language
     * and set); a selected card removes EVERY queue entry with its scryfallId, scanned ones included.
     * A card with an entry being written cannot be deselected (the write would land anyway).
     */
    fun onToggleCardSelection(card: Card) {
        if (!_uiState.value.isMultiSelectMode) return
        val entries = queueRepository.queue.value.filter { it.card.scryfallId == card.scryfallId }
        val inFlight = queueActions.inFlightIds.value
        if (entries.any { it.id in inFlight }) {
            showQueueToast(AddCardQueueToast.SelectionLockedWhileAdding(card.name))
            return
        }
        if (entries.isNotEmpty()) {
            queueRepository.removeByScryfallId(card.scryfallId)
        } else {
            queueRepository.add(newQueueEntry(card))
        }
        syncQueueSnapshot()
    }

    private fun newQueueEntry(card: Card) = QueuedCard(
        card = card,
        quantity = 1,
        isFoil = false,
        language = card.lang,
        condition = DEFAULT_CONDITION,
        setCode = card.setCode,
        timestamp = nowMillis(),
        origin = CardAddOrigin.MANUAL,
    )

    /** Opens the queue sheet; no-op when the queue is empty. */
    fun onOpenQueueSheet() {
        val count = queueRepository.queue.value.size
        if (count == 0) return
        _uiState.update { it.copy(showQueueSheet = true) }
        AddCardTelemetry.queueOpened(count)
    }

    /** Closes the queue sheet. */
    fun onCloseQueueSheet() {
        _uiState.update { it.copy(showQueueSheet = false) }
    }

    fun onRemoveQueuedCard(entry: QueuedCard) {
        queueRepository.remove(entry.id)
        syncQueueSnapshot()
    }

    fun onClearQueue() {
        queueRepository.clear()
        syncQueueSnapshot()
        _uiState.update { it.copy(showQueueSheet = false) }
    }

    fun onIncrementQueuedCardQuantity(entry: QueuedCard) {
        queueRepository.incrementQuantity(entry.id)
        syncQueueSnapshot()
    }

    /** Decrements the quantity; an entry at quantity 1 is removed. */
    fun onDecrementQueuedCardQuantity(entry: QueuedCard) {
        queueRepository.decrementQuantity(entry.id)
        syncQueueSnapshot()
    }

    /** Inserts a copy of [entry] right after it in the queue. */
    fun onDuplicateQueuedCard(entry: QueuedCard) {
        queueRepository.duplicate(entry)
        syncQueueSnapshot()
    }

    /** Per-entry adds also remove the entry on success while this is on; bulk adds are unaffected. */
    fun onToggleAutoDeleteOnAdd() {
        _uiState.update { it.copy(isAutoDeleteOnAddEnabled = !it.isAutoDeleteOnAddEnabled) }
    }

    /** Commits one entry; a second tap while it (or an "add all" including it) is in flight is a no-op. */
    fun onAddEntryToCollection(entry: QueuedCard) {
        val removeOnSuccess = _uiState.value.isAutoDeleteOnAddEnabled
        val launched = queueActions.addEntryToCollection(commitScope, entry, removeOnSuccess) { succeeded ->
            syncQueueSnapshot()
            showQueueToast(
                if (succeeded) AddCardQueueToast.AddedToCollection(entry.card.name)
                else AddCardQueueToast.AddFailed(entry.card.name)
            )
        }
        if (launched) syncQueueSnapshot()
    }

    fun onAddEntryToWishlist(entry: QueuedCard) {
        val removeOnSuccess = _uiState.value.isAutoDeleteOnAddEnabled
        val launched = queueActions.addEntryToWishlist(commitScope, entry, removeOnSuccess) { result ->
            syncQueueSnapshot()
            showQueueToast(
                if (result.isSuccess) AddCardQueueToast.AddedToWishlist(entry.card.name)
                else AddCardQueueToast.AddFailed(entry.card.name)
            )
        }
        if (launched) syncQueueSnapshot()
    }

    /** Adds every queued entry (with its quantity) to the wishlist, keeping them queued. */
    fun onAddAllToWishlist() {
        var failed = 0
        val launched = queueActions.addAllToWishlist(
            scope = commitScope,
            onEntryAdded = { _, result -> if (result.isFailure) failed++ },
        ) { total ->
            showQueueToast(
                if (failed == 0) AddCardQueueToast.AddedAllToWishlist(total)
                else AddCardQueueToast.AddAllPartialFailure(failed = failed, total = total)
            )
        }
        if (launched) syncQueueSnapshot()
    }

    /**
     * Commits the whole queue to the collection in one batch. Full success empties the queue and
     * closes the sheet; a partial failure keeps only the failed entries queued.
     */
    fun onAddAllToCollection() {
        val launched = queueActions.addAllToCollection(commitScope) { result ->
            syncQueueSnapshot()
            when (result) {
                is AddAllToCollectionResult.Success -> {
                    _uiState.update { it.copy(showQueueSheet = false) }
                    showQueueToast(AddCardQueueToast.AddedAllToCollection(result.committedEntries))
                }
                is AddAllToCollectionResult.PartialFailure -> showQueueToast(
                    AddCardQueueToast.AddAllPartialFailure(result.failedEntries, result.totalEntries)
                )
            }
        }
        if (launched) syncQueueSnapshot()
    }

    private fun showQueueToast(toast: AddCardQueueToast) {
        _uiState.update { it.copy(queueToast = toast) }
    }

    /** Clears the one-shot queue toast once the UI has shown it. */
    fun onQueueToastShown() {
        _uiState.update { it.copy(queueToast = null) }
    }

    /** Opens the attribute editor for [entry] and loads its available printings. */
    fun onEditQueuedCard(entry: QueuedCard) {
        printsLoadJob?.cancel()
        _uiState.update {
            it.copy(editingQueuedCard = entry, availablePrints = emptyList(), isLoadingPrints = true)
        }
        printsLoadJob = viewModelScope.launch {
            val result = cardRepository.getCardPrints(entry.card.name)
            _uiState.update { state ->
                if (state.editingQueuedCard?.id != entry.id) return@update state
                if (result is DataResult.Success) state.copy(availablePrints = result.data, isLoadingPrints = false)
                else state.copy(isLoadingPrints = false)
            }
        }
    }

    /** Saves the edited attributes onto the entry being edited and closes the editor. */
    fun onUpdateQueuedCard(updated: QueuedCard) {
        val original = _uiState.value.editingQueuedCard ?: return
        queueRepository.update(updated.copy(id = original.id))
        syncQueueSnapshot()
        onCloseEditSheet()
    }

    fun onCloseEditSheet() {
        printsLoadJob?.cancel()
        _uiState.update { it.copy(editingQueuedCard = null, availablePrints = emptyList(), isLoadingPrints = false) }
    }

    fun onOpenVariantSelector(entry: QueuedCard) {
        variantLoadJob?.cancel()
        _uiState.update {
            it.copy(variantSelectorEntry = entry, cardVariants = emptyList(), isLoadingVariants = true)
        }
        variantLoadJob = viewModelScope.launch {
            val result = cardRepository.getCardArtVariants(entry.card.name)
            _uiState.update { state ->
                if (state.variantSelectorEntry?.id != entry.id) return@update state
                if (result is DataResult.Success) state.copy(cardVariants = result.data, isLoadingVariants = false)
                else state.copy(isLoadingVariants = false)
            }
        }
    }

    fun onCloseVariantSelector() {
        variantLoadJob?.cancel()
        _uiState.update {
            it.copy(variantSelectorEntry = null, cardVariants = emptyList(), isLoadingVariants = false)
        }
    }

    /** Swaps the printing of the entry the variant selector was opened for. */
    fun onSelectVariant(variant: Card) {
        val original = _uiState.value.variantSelectorEntry ?: return
        queueRepository.queue.value.firstOrNull { it.id == original.id }?.let { current ->
            queueRepository.update(current.copy(card = variant, setCode = variant.setCode))
        }
        syncQueueSnapshot()
        onCloseVariantSelector()
        _uiState.update { state ->
            val editing = state.editingQueuedCard
            if (editing?.id == original.id) {
                state.copy(editingQueuedCard = editing.copy(card = variant, setCode = variant.setCode))
            } else state
        }
    }

    fun onExpandVariantImage(imageUrl: String) {
        if (imageUrl.isBlank()) return
        _uiState.update { it.copy(expandedVariantImageUrl = imageUrl) }
    }

    fun onCloseExpandedImage() {
        _uiState.update { it.copy(expandedVariantImageUrl = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Deck source mode
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadDeckSource(source: AddCardDeckSource) {
        deckLoadJob?.cancel()
        _uiState.update {
            it.copy(
                deckSource = source,
                deckCards = emptyList(),
                results = emptyList(),
                totalCards = 0,
                hasMore = false,
                isSearching = false,
                isLoadingMore = false,
                error = null,
                isDeckLoading = true,
                deckLoadFailed = false,
            )
        }
        deckLoadJob = viewModelScope.launch {
            val outcome = try {
                when (source) {
                    is AddCardDeckSource.Local -> loadLocalDeck(source.deckId)
                    is AddCardDeckSource.Community -> loadCommunityDeck(source.archidektId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeckLoadOutcome.Failed(DeckSourceLoadFailure.EXCEPTION, e)
            }
            if (_uiState.value.deckSource != source) return@launch
            _uiState.update {
                when (outcome) {
                    is DeckLoadOutcome.Failed -> it.copy(isDeckLoading = false, deckLoadFailed = true)
                    is DeckLoadOutcome.Loaded -> it.copy(
                        isDeckLoading = false,
                        deckLoadFailed = false,
                        deckName = outcome.name,
                        deckCards = outcome.cards,
                    )
                }
            }
            if (outcome is DeckLoadOutcome.Failed) {
                // One non-fatal per source per screen; retries of an offline deck only leave breadcrumbs.
                val firstFailure = reportedDeckLoadFailures.add(source)
                AddCardTelemetry.deckSourceLoadFailed(source, outcome.failure, outcome.cause, recordNonFatal = firstFailure)
            }
            applyDeckFilter()
        }
    }

    private suspend fun loadLocalDeck(deckId: String): DeckLoadOutcome {
        val deck = deckRepository.observeDeckWithCards(deckId).first()
            ?: return DeckLoadOutcome.Failed(DeckSourceLoadFailure.NOT_FOUND)
        val ids = buildList {
            deck.deck.commanderCardId?.takeIf { it.isNotBlank() }?.let(::add)
            deck.mainboard.forEach { add(it.scryfallId) }
            deck.sideboard.forEach { add(it.scryfallId) }
        }.distinct()
        val cards = resolveCards(ids) ?: return DeckLoadOutcome.Failed(DeckSourceLoadFailure.UNRESOLVED)
        return DeckLoadOutcome.Loaded(deck.deck.name, cards)
    }

    private suspend fun loadCommunityDeck(archidektId: Int): DeckLoadOutcome {
        val result = communityDecksRepository.getDeckById(archidektId)
        if (result !is DataResult.Success) return DeckLoadOutcome.Failed(DeckSourceLoadFailure.NOT_FOUND)
        val ids = result.data.cards.map { it.scryfallId }.filter { it.isNotBlank() }.distinct()
        val cards = resolveCards(ids) ?: return DeckLoadOutcome.Failed(DeckSourceLoadFailure.UNRESOLVED)
        return DeckLoadOutcome.Loaded(result.data.name, cards)
    }

    // Batch path only (never a per-card name search); keeps deck order. warmCacheForIds hydrates
    // sync placeholders; any still unresolved stay out (they carry no real card data). Null when
    // ids exist but none resolved, which in practice means the batch fetch failed.
    private suspend fun resolveCards(ids: List<String>): List<Card>? {
        if (ids.isEmpty()) return emptyList()
        cardRepository.warmCacheForIds(ids)
        val byId = cardRepository.getCardsByIds(ids)
            .filterNot { it.staleReason == PENDING_HYDRATION }
            .associateBy { it.scryfallId }
        return ids.mapNotNull(byId::get).ifEmpty { null }
    }

    private fun applyDeckFilter() {
        _uiState.update { state ->
            if (!state.isDeckMode) return@update state
            val text = state.query.trim()
            val active = state.activeQuery
            val filtered = state.deckCards.filter { card ->
                val nameMatches = text.isEmpty() ||
                    card.name.contains(text, ignoreCase = true) ||
                    card.printedName?.contains(text, ignoreCase = true) == true
                nameMatches && (active == null || AdvancedSearchCardMatcher.matches(card, active, lenient = true))
            }
            state.copy(
                results = filtered,
                totalCards = filtered.size,
                hasMore = false,
                currentPage = 1,
                isSearching = false,
                isLoadingMore = false,
                error = null,
            )
        }
    }

    /** Retries loading the current deck source after a failure. */
    fun onRetryDeckLoad() {
        _uiState.value.deckSource?.let(::loadDeckSource)
    }

    /**
     * Drops the preloaded deck list and its source and returns to the normal Scryfall search with
     * the current query/filters. Multi-select mode and the queue are kept.
     */
    fun onClearDeckCards() {
        if (!_uiState.value.isDeckMode) return
        deckLoadJob?.cancel()
        _uiState.update {
            it.copy(
                deckSource = null,
                deckName = null,
                deckCards = emptyList(),
                isDeckLoading = false,
                deckLoadFailed = false,
                results = emptyList(),
                totalCards = 0,
                hasMore = false,
                currentPage = 1,
            )
        }
        restorableState.isDeckSourceCleared = true
        lastEffectiveQuery = null
        forceSearch()
    }

    /** Queues every visible deck card that is not selected yet (qty 1, same defaults as a tap). */
    fun onSelectAllDeckCards() {
        val state = _uiState.value
        if (!state.isDeckMode) return
        AddCardTelemetry.selectAll(queueUnselected(state.results))
    }

    /**
     * Removes every visible deck card from the queue in one persist; cards hidden by the current
     * filter stay queued, and cards with an entry being written are skipped (the write lands anyway).
     */
    fun onUnselectAllDeckCards() {
        val state = _uiState.value
        if (!state.isDeckMode) return
        val visibleIds = state.results.mapTo(HashSet()) { it.scryfallId }
        val inFlight = queueActions.inFlightIds.value
        val lockedIds = queueRepository.queue.value
            .filter { it.id in inFlight }
            .mapTo(HashSet()) { it.card.scryfallId }
        val toRemove = queueRepository.queue.value.selectedIds()
            .filter { it in visibleIds && it !in lockedIds }
        queueRepository.removeByScryfallIds(toRemove)
        syncQueueSnapshot()
        AddCardTelemetry.unselectAll(toRemove.size)
    }

    /** Queues every visible deck card not owned under its oracleId or its exact name. */
    fun onSelectMissingDeckCards() {
        val candidates = _uiState.value.takeIf { it.isDeckMode }?.results ?: return
        viewModelScope.launch {
            // Read once instead of the multi-mode collector, which may not have emitted yet.
            val owned = userCardRepository.observeCollection().first().ownedIdentityKeys()
            AddCardTelemetry.selectMissing(queueUnselected(candidates.filterNot { it.isOwnedIn(owned) }))
        }
    }

    /** Returns how many cards were newly queued. */
    private fun queueUnselected(cards: List<Card>): Int {
        enableMultiSelectMode()
        val queued = queueRepository.queue.value.selectedIds()
        val toAdd = cards.filter { it.scryfallId !in queued }.distinctBy { it.scryfallId }
        queueRepository.addAll(toAdd.map(::newQueueEntry))
        syncQueueSnapshot()
        showQueueToast(AddCardQueueToast.DeckCardsSelected(toAdd.size))
        return toAdd.size
    }

    private sealed interface DeckLoadOutcome {
        data class Loaded(val name: String, val cards: List<Card>) : DeckLoadOutcome
        data class Failed(val failure: DeckSourceLoadFailure, val cause: Throwable? = null) : DeckLoadOutcome
    }

    private companion object {
        const val DEFAULT_CONDITION = "NM"
        const val PENDING_HYDRATION = "pending_hydration"

        fun List<QueuedCard>.selectedIds(): Set<String> = mapTo(HashSet(size)) { it.card.scryfallId }

        // Both keys: an owned row cached before the oracle_id backfill only has its name.
        fun List<UserCardWithCard>.ownedIdentityKeys(): Set<String> = HashSet<String>(size * 2).also { keys ->
            forEach { row ->
                row.card.oracleId.takeIf { it.isNotBlank() }?.let(keys::add)
                keys.add(row.card.name)
            }
        }

        fun Card.isOwnedIn(ownedKeys: Set<String>): Boolean =
            (oracleId.isNotBlank() && oracleId in ownedKeys) || name in ownedKeys
    }
}
