package com.mmg.manahub.feature.decks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.mmg.manahub.core.di.ApplicationScope
import com.mmg.manahub.core.domain.model.*
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.DeckCardValidator
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.decks.engine.DeckImportExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GroupingMode { TYPE, COLOR, COST, TAG }

data class LandDelta(
    val landName: String,
    val manaSymbol: String,
    val delta: Int
)

data class DeckMagicDetailUiState(
    val deck: Deck? = null,
    val cards: List<DeckSlotEntry> = emptyList(),
    val isLoading: Boolean = true,
    val groupingMode: GroupingMode = GroupingMode.TYPE,
    val landDeltas: List<LandDelta> = emptyList(),
    val showLandSuggestions: Boolean = true,
    val totalCards: Int = 0,
    val manaCurve: Map<Int, Int> = emptyMap(),
    val collectionIds: Set<String> = emptySet(),
    val error: String? = null,

    val mainboardExpanded: Boolean = true,
    val sideboardExpanded: Boolean = false,

    // Search / Add cards state
    val addCardsQuery: String = "",
    val addCardsResults: List<AddCardRow> = emptyList(),
    val isSearchingCards: Boolean = false,
    val scryfallResults: List<AddCardRow> = emptyList(),
    val isSearchingScryfall: Boolean = false,

    // Format validation
    val overLimitCards: Set<String> = emptySet(),
    val acknowledgedOverLimitCards: Set<String> = emptySet(),
    val invalidColorIdentityCards: Set<String> = emptySet(),

    // Commander specific
    val commanderCard: DeckSlotEntry? = null,
    val isCommanderInvalid: Boolean = false,
    val isSearchingCommander: Boolean = false,
    val commanderSearchResults: List<Card> = emptyList(),

    val detailTags: List<CardTag> = emptyList(),

    val isImporting: Boolean = false,
    val importError: String? = null,
    val syncState: SyncState = SyncState.IDLE,
    val syncError: String? = null,

    // Draft / unsaved-changes state
    val hasUnsavedChanges: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DeckMagicDetailViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val authRepository: AuthRepository,
    private val suggestTagsUseCase: SuggestTagsUseCase,
    private val userPreferencesRepo: UserPreferencesRepository,
    private val syncManager: SyncManager,
    private val workManager: WorkManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val deckId: String = checkNotNull(savedStateHandle["deckId"])

    private val _uiState = MutableStateFlow(DeckMagicDetailUiState())
    val uiState: StateFlow<DeckMagicDetailUiState> = _uiState.asStateFlow()

    // ── Persisted snapshot (last state written to Room) ───────────────────────
    private var persistedCardsMap: Map<Pair<String, Boolean>, Int> = emptyMap()
    private var persistedDeck: Deck? = null

    // ── Draft state (what the user sees, not yet persisted) ───────────────────
    private var draftCardsMap: Map<Pair<String, Boolean>, Int> = emptyMap()
    private var draftDeck: Deck? = null

    // ── Card data cache (scryfallId → Card) ───────────────────────────────────
    private var cardCache: Map<String, Card> = emptyMap()

    private var collectionCards: List<Card> = emptyList()

    val deckFormat: DeckFormat?
        get() = draftDeck?.format?.let { fmt ->
            DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) }
        }

    init {
        observeDeck()
        observeCollection()
        observeSyncState()
        observeDraftChanges()
    }

    private fun observeDraftChanges() {
        uiState
            .map { it.hasUnsavedChanges }
            .distinctUntilChanged()
            .filter { it }
            .debounce(2000)
            .onEach { persistToLocal() }
            .launchIn(viewModelScope)
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.update { it.copy(syncState = state) }
            }
        }
    }

    private fun observeDeck() {
        deckRepository.observeDeckWithCards(deckId)
            .onEach { deckWithCards ->
                if (deckWithCards == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@onEach
                }

                val mainEntries = deckWithCards.mainboard.map { slot ->
                    val card = (cardRepository.getCardById(slot.scryfallId) as? DataResult.Success)?.data
                    DeckSlotEntry(slot.scryfallId, slot.quantity, false, card)
                }
                val sideEntries = deckWithCards.sideboard.map { slot ->
                    val card = (cardRepository.getCardById(slot.scryfallId) as? DataResult.Success)?.data
                    DeckSlotEntry(slot.scryfallId, slot.quantity, true, card)
                }

                // Update card cache with any newly loaded Card objects
                val newCards = (mainEntries + sideEntries).mapNotNull { it.card }
                cardCache = cardCache + newCards.associateBy { it.scryfallId }

                // Always update persisted snapshot
                val newPersistedMap = (mainEntries + sideEntries)
                    .associate { (it.scryfallId to it.isSideboard) to it.quantity }
                persistedCardsMap = newPersistedMap
                persistedDeck = deckWithCards.deck

                // Only sync draft to persisted if no pending changes (or first load)
                if (!_uiState.value.hasUnsavedChanges) {
                    draftCardsMap = newPersistedMap
                    draftDeck = deckWithCards.deck
                }

                rebuildUiState()
            }
            .launchIn(viewModelScope)
    }

    private fun observeCollection() {
        userCardRepository.observeCollection()
            .onEach { collection ->
                collectionCards = collection.map { it.card }.distinctBy { it.scryfallId }.sortedBy { it.name }
                val ids = collectionCards.map { it.scryfallId }.toSet()
                _uiState.update { it.copy(collectionIds = ids) }
            }
            .launchIn(viewModelScope)
    }

    // ── Draft rebuild ─────────────────────────────────────────────────────────

    private fun rebuildUiState() {
        val deck = draftDeck ?: return
        val format = DeckFormat.entries.firstOrNull { it.name.equals(deck.format, ignoreCase = true) }
        val isCommanderFormat = format == DeckFormat.COMMANDER
        val commanderId = deck.commanderCardId

        val entries = draftCardsMap.map { (key, qty) ->
            val (scryfallId, isSideboard) = key
            DeckSlotEntry(scryfallId, qty, isSideboard, cardCache[scryfallId])
        }
        val mainEntries = entries.filter { !it.isSideboard }

        var commanderEntry: DeckSlotEntry? = null
        val otherEntries = if (isCommanderFormat && commanderId != null) {
            commanderEntry = entries.find { it.scryfallId == commanderId && !it.isSideboard }
            entries.filter { it.scryfallId != commanderId || it.isSideboard }
        } else {
            entries
        }

        val overLimit = mainEntries
            .groupBy { it.scryfallId }
            .filter { (_, slots) ->
                val card = slots.first().card
                card != null && !BasicLandCalculator.isBasicLand(card) && run {
                    val limit = format?.maxCopies ?: 4
                    slots.sumOf { it.quantity } > limit
                }
            }
            .keys

        val invalidIdentity = if (isCommanderFormat && commanderEntry?.card != null) {
            val identity = commanderEntry.card.colorIdentity.toSet()
            otherEntries.filter { entry ->
                entry.card != null && !identity.containsAll(entry.card.colorIdentity)
            }.map { it.scryfallId }.toSet()
        } else {
            emptySet()
        }

        val isCommanderInvalid = isCommanderFormat && commanderEntry?.card != null &&
                !commanderEntry.card.typeLine.contains("Legendary", ignoreCase = true)

        val hasChanges = draftCardsMap != persistedCardsMap || draftDeck != persistedDeck

        _uiState.update { s ->
            s.copy(
                deck = deck,
                cards = otherEntries,
                commanderCard = commanderEntry,
                isCommanderInvalid = isCommanderInvalid,
                isLoading = false,
                totalCards = mainEntries.sumOf { it.quantity },
                manaCurve = calculateManaCurve(entries),
                landDeltas = calculateLandDeltas(
                    entries = entries,
                    formatName = deck.format,
                    commanderIdentity = commanderEntry?.card?.colorIdentity?.toSet()
                ),
                overLimitCards = overLimit,
                invalidColorIdentityCards = invalidIdentity,
                hasUnsavedChanges = hasChanges,
                addCardsResults = s.addCardsResults.map { row ->
                    row.copy(quantityInDeck = draftCardsMap[row.card.scryfallId to false] ?: 0)
                },
                scryfallResults = s.scryfallResults.map { row ->
                    row.copy(quantityInDeck = draftCardsMap[row.card.scryfallId to false] ?: 0)
                },
            )
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    fun toggleMainboard() = _uiState.update { it.copy(mainboardExpanded = !it.mainboardExpanded) }
    fun toggleSideboard() = _uiState.update { it.copy(sideboardExpanded = !it.sideboardExpanded) }
    fun setGroupingMode(mode: GroupingMode) = _uiState.update { it.copy(groupingMode = mode) }
    fun toggleLandSuggestions() = _uiState.update { it.copy(showLandSuggestions = !it.showLandSuggestions) }

    private fun calculateManaCurve(cards: List<DeckSlotEntry>): Map<Int, Int> {
        val curve = mutableMapOf<Int, Int>()
        cards.filter { it.card != null && !it.isSideboard && !BasicLandCalculator.isLand(it.card!!) }.forEach { entry ->
            val cmc = entry.card!!.cmc.toInt().coerceIn(0, 7)
            curve[cmc] = (curve[cmc] ?: 0) + entry.quantity
        }
        return curve
    }

    private fun calculateLandDeltas(
        entries: List<DeckSlotEntry>,
        formatName: String,
        commanderIdentity: Set<String>? = null
    ): List<LandDelta> {
        val format = DeckFormat.entries.find { it.name.equals(formatName, ignoreCase = true) }
            ?: DeckFormat.STANDARD

        val deckCards = entries.filter { it.card != null && !it.isSideboard }.map {
            DeckCard(it.card!!, it.quantity, isOwned = true)
        }

        val nonBasicLands = deckCards.filter { !BasicLandCalculator.isBasicLand(it.card) && BasicLandCalculator.isLand(it.card) }
        val mainboardNonLands = deckCards.filter { !BasicLandCalculator.isLand(it.card) }

        val suggested = BasicLandCalculator.calculate(
            mainboard = mainboardNonLands,
            nonBasicLands = nonBasicLands,
            format = format,
            commanderIdentity = commanderIdentity
        )
        val suggestedMap = suggested.toMap()

        val currentBasics = entries.filter { it.card != null && !it.isSideboard && BasicLandCalculator.isBasicLand(it.card!!) }
        val currentCounts = mutableMapOf<String, Int>()
        currentBasics.forEach {
            currentCounts[it.card!!.name] = (currentCounts[it.card!!.name] ?: 0) + it.quantity
        }

        val deltas = mutableListOf<LandDelta>()
        BasicLandCalculator.LAND_FOR_COLOR.forEach { (symbol, landName) ->
            val suggestedCount = suggestedMap[symbol] ?: 0
            val currentCount = currentCounts[landName] ?: 0
            if (suggestedCount != currentCount) {
                deltas.add(LandDelta(landName, symbol, suggestedCount - currentCount))
            }
        }
        return deltas
    }

    // ── Draft mutations (no Room writes) ─────────────────────────────────────

    fun addCardToDeck(scryfallId: String, isSideboard: Boolean = false) {
        val currentQtyInBoard = draftCardsMap[scryfallId to isSideboard] ?: 0

        // Resolve the card from any available source and populate the cache.
        // Validation is intentionally deferred to WarningOverlay so the user can
        // add multiple copies freely and review the warnings at their own pace.
        val card = (_uiState.value.addCardsResults + _uiState.value.scryfallResults +
                _uiState.value.cards.mapNotNull { e -> e.card?.let { c -> AddCardRow(c, e.quantity, true) } })
            .find { it.card.scryfallId == scryfallId }?.card

        if (card != null) {
            cardCache = cardCache + (scryfallId to card)
        }

        draftCardsMap = draftCardsMap + ((scryfallId to isSideboard) to (currentQtyInBoard + 1))
        rebuildUiState()
    }

    fun removeCardFromDeck(scryfallId: String, isSideboard: Boolean = false) {
        val currentQty = draftCardsMap[scryfallId to isSideboard] ?: 0
        draftCardsMap = if (currentQty <= 1) {
            draftCardsMap - (scryfallId to isSideboard)
        } else {
            draftCardsMap + ((scryfallId to isSideboard) to (currentQty - 1))
        }
        rebuildUiState()
    }

    fun removeCard(scryfallId: String, isSideboard: Boolean = false) {
        draftCardsMap = draftCardsMap - (scryfallId to isSideboard)
        rebuildUiState()
    }

    fun moveQuantityToSideboard(scryfallId: String, quantity: Int) {
        val mainEntry = _uiState.value.cards.find { it.scryfallId == scryfallId && !it.isSideboard } ?: return
        val toMove = quantity.coerceIn(1, mainEntry.quantity)

        val newMain = mainEntry.quantity - toMove
        draftCardsMap = if (newMain <= 0) {
            draftCardsMap - (scryfallId to false)
        } else {
            draftCardsMap + ((scryfallId to false) to newMain)
        }

        val currentSide = draftCardsMap[scryfallId to true] ?: 0
        draftCardsMap = draftCardsMap + ((scryfallId to true) to (currentSide + toMove))
        rebuildUiState()
    }

    fun moveQuantityToMainboard(scryfallId: String, quantity: Int) {
        val sideEntry = _uiState.value.cards.find { it.scryfallId == scryfallId && it.isSideboard } ?: return
        val toMove = quantity.coerceIn(1, sideEntry.quantity)

        val newSide = sideEntry.quantity - toMove
        draftCardsMap = if (newSide <= 0) {
            draftCardsMap - (scryfallId to true)
        } else {
            draftCardsMap + ((scryfallId to true) to newSide)
        }

        val currentMain = draftCardsMap[scryfallId to false] ?: 0
        draftCardsMap = draftCardsMap + ((scryfallId to false) to (currentMain + toMove))
        rebuildUiState()
    }

    fun acknowledgeOverLimit(scryfallId: String) {
        _uiState.update { it.copy(acknowledgedOverLimitCards = it.acknowledgedOverLimitCards + scryfallId) }
    }

    fun unacknowledgeOverLimit(scryfallId: String) {
        _uiState.update { it.copy(acknowledgedOverLimitCards = it.acknowledgedOverLimitCards - scryfallId) }
    }

    fun addBasicLandByName(name: String) {
        viewModelScope.launch {
            val existingEntry = _uiState.value.cards.find { it.card?.name == name && !it.isSideboard }
            if (existingEntry != null) {
                val currentQty = draftCardsMap[existingEntry.scryfallId to false] ?: 0
                draftCardsMap = draftCardsMap + ((existingEntry.scryfallId to false) to (currentQty + 1))
                rebuildUiState()
            } else {
                val result = cardRepository.searchCardByName(name)
                if (result is DataResult.Success) {
                    val card = result.data
                    cardCache = cardCache + (card.scryfallId to card)
                    val currentQty = draftCardsMap[card.scryfallId to false] ?: 0
                    draftCardsMap = draftCardsMap + ((card.scryfallId to false) to (currentQty + 1))
                    rebuildUiState()
                }
            }
        }
    }

    fun removeBasicLandByName(name: String) {
        val existingEntry = _uiState.value.cards.find { it.card?.name == name && !it.isSideboard } ?: return
        val currentQty = draftCardsMap[existingEntry.scryfallId to false] ?: 0
        draftCardsMap = if (currentQty <= 1) {
            draftCardsMap - (existingEntry.scryfallId to false)
        } else {
            draftCardsMap + ((existingEntry.scryfallId to false) to (currentQty - 1))
        }
        rebuildUiState()
    }

    fun applyLandSuggestions() {
        val deltas = _uiState.value.landDeltas
        viewModelScope.launch {
            for (delta in deltas) {
                if (delta.delta > 0) {
                    val card = searchBasicLandCached(delta.landName) ?: continue
                    val currentQty = draftCardsMap[card.scryfallId to false] ?: 0
                    draftCardsMap = draftCardsMap + ((card.scryfallId to false) to (currentQty + delta.delta))
                } else if (delta.delta < 0) {
                    val existing = _uiState.value.cards.find { it.card?.name == delta.landName && !it.isSideboard }
                        ?: continue
                    val newQty = (draftCardsMap[existing.scryfallId to false] ?: 0) + delta.delta
                    draftCardsMap = if (newQty <= 0) {
                        draftCardsMap - (existing.scryfallId to false)
                    } else {
                        draftCardsMap + ((existing.scryfallId to false) to newQty)
                    }
                }
            }
            rebuildUiState()
        }
    }

    private suspend fun searchBasicLandCached(name: String): Card? {
        cardCache.values.find { it.name == name }?.let { return it }
        return (cardRepository.searchCardByName(name) as? DataResult.Success<Card>)?.data?.also { card ->
            cardCache = cardCache + (card.scryfallId to card)
        }
    }

    fun updateDeckName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        draftDeck = draftDeck?.copy(name = trimmed) ?: return
        rebuildUiState()
    }

    fun setCoverCard(scryfallId: String) {
        draftDeck = draftDeck?.copy(coverCardId = scryfallId) ?: return
        rebuildUiState()
    }

    fun setCommander(card: Card) {
        val deck = draftDeck ?: return
        val oldCommanderId = deck.commanderCardId

        cardCache = cardCache + (card.scryfallId to card)
        draftDeck = deck.copy(commanderCardId = card.scryfallId, coverCardId = card.scryfallId)

        // Ensure new commander is in mainboard with qty 1
        draftCardsMap = draftCardsMap + ((card.scryfallId to false) to 1)

        // Remove old commander from deck if different
        if (oldCommanderId != null && oldCommanderId != card.scryfallId) {
            draftCardsMap = draftCardsMap - (oldCommanderId to false)
        }

        // Remove from sideboard if present
        if (draftCardsMap.containsKey(card.scryfallId to true)) {
            draftCardsMap = draftCardsMap - (card.scryfallId to true)
        }

        rebuildUiState()
    }

    fun removeCommander() {
        val deck = draftDeck ?: return
        val commanderId = deck.commanderCardId ?: return
        draftDeck = deck.copy(commanderCardId = null)
        draftCardsMap = draftCardsMap - (commanderId to false)
        rebuildUiState()
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun onAddCardsQueryChange(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            showCollectionCards()
            return
        }
        val filtered = collectionCards.filter { it.name.contains(query, ignoreCase = true) }
        _uiState.update { s ->
            s.copy(
                addCardsResults = filtered.map { card ->
                    AddCardRow(
                        card = card,
                        quantityInDeck = draftCardsMap[card.scryfallId to false] ?: 0,
                        isOwned = true,
                    )
                },
            )
        }
    }

    fun showCollectionCards() {
        _uiState.update { s ->
            s.copy(
                addCardsResults = collectionCards.map { card ->
                    AddCardRow(
                        card = card,
                        quantityInDeck = draftCardsMap[card.scryfallId to false] ?: 0,
                        isOwned = true,
                    )
                },
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
                    s.copy(
                        isSearchingScryfall = false,
                        scryfallResults = cards.map { card ->
                            AddCardRow(
                                card = card,
                                quantityInDeck = draftCardsMap[card.scryfallId to false] ?: 0,
                                isOwned = card.scryfallId in ownedIds,
                            )
                        },
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearchingScryfall = false) }
            }
        }
    }

    fun clearAddCardsState() {
        _uiState.update { it.copy(addCardsQuery = "", addCardsResults = emptyList(), scryfallResults = emptyList()) }
    }

    fun searchCommander(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            showCollectionCards()
            _uiState.update { it.copy(scryfallResults = emptyList(), isSearchingScryfall = false) }
            return
        }

        val filteredLocal = collectionCards.filter { it.name.contains(query, ignoreCase = true) }
        _uiState.update {
            it.copy(
                addCardsResults = filteredLocal.map { card ->
                    AddCardRow(
                        card = card,
                        quantityInDeck = if (draftCardsMap[card.scryfallId to false] != null) 1 else 0,
                        isOwned = true,
                    )
                }
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingScryfall = true) }
            try {
                val cards = when (val results = cardRepository.searchCards(query)) {
                    is DataResult.Success -> results.data
                    is DataResult.Error -> emptyList()
                }
                val ownedIds = _uiState.value.collectionIds
                _uiState.update { s ->
                    s.copy(
                        isSearchingScryfall = false,
                        scryfallResults = cards.map { card ->
                            AddCardRow(
                                card = card,
                                quantityInDeck = if (draftCardsMap[card.scryfallId to false] != null) 1 else 0,
                                isOwned = card.scryfallId in ownedIds,
                            )
                        },
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearchingScryfall = false) }
            }
        }
    }

    fun clearCommanderSearch() {
        _uiState.update { it.copy(addCardsQuery = "", addCardsResults = emptyList(), scryfallResults = emptyList()) }
    }

    // ── Card details ──────────────────────────────────────────────────────────

    fun loadCardDetails(scryfallId: String) {
        viewModelScope.launch {
            val card = (cardRepository.getCardById(scryfallId) as? DataResult.Success)?.data ?: return@launch
            val tags = if (card.tags.isNotEmpty() || card.userTags.isNotEmpty()) {
                card.tags + card.userTags
            } else {
                suggestTagsUseCase(card).confirmed
            }
            _uiState.update { it.copy(detailTags = tags.distinctBy { t -> t.key }) }
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun exportDeckToText(): String? {
        val state = _uiState.value
        val deck = state.deck ?: return null

        val commanderCard = deck.commanderCardId?.let { cmdId ->
            state.cards.firstOrNull { it.scryfallId == cmdId }?.card ?: state.commanderCard?.card
        }
        val commanderScryfallId = commanderCard?.scryfallId

        val mainDeckCards = state.cards
            .filter { !it.isSideboard && it.scryfallId != commanderScryfallId }
            .mapNotNull { dc -> dc.card?.let { card -> DeckCard(card = card, quantity = dc.quantity) } }

        val sideboardCards = state.cards
            .filter { it.isSideboard }
            .mapNotNull { dc -> dc.card?.let { card -> DeckCard(card = card, quantity = dc.quantity) } }

        return DeckImportExportHelper.export(
            deckName = deck.name,
            mainboard = mainDeckCards,
            sideboard = sideboardCards,
            commander = commanderCard,
        )
    }

    // ── Navigation / Save / Discard ───────────────────────────────────────────

    /**
     * Called when the user taps the system back button or the back arrow.
     * Always returns true as changes are auto-saved.
     */
    fun onNavigatingBack(): Boolean {
        if (_uiState.value.hasUnsavedChanges) {
            applicationScope.launch {
                persistToLocal()
                triggerSync()
            }
        }
        return true
    }

    private suspend fun persistToLocal() {
        // 1. Persist deck metadata if changed
        val currentDeck = draftDeck
        if (currentDeck != null && currentDeck != persistedDeck) {
            runCatching {
                deckRepository.updateDeck(currentDeck.copy(updatedAt = System.currentTimeMillis()))
            }
        }

        // 2. Atomically replace all card slots
        val slots = draftCardsMap.map { (key, qty) ->
            Triple(key.first, qty, key.second)
        }
        runCatching { deckRepository.replaceAllCards(deckId, slots) }

        // Mark as saved only after Room confirms
        _uiState.update { it.copy(hasUnsavedChanges = false) }
    }

    private suspend fun triggerSync() {
        val userId = authRepository.getCurrentUser()?.id
        if (userId != null) {
            workManager.enqueueUniqueWork(
                CollectionSyncWorker.WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                CollectionSyncWorker.oneTimeWorkRequest()
            )
            val result = syncManager.sync(userId)
            _uiState.update { it.copy(syncError = result.error) }
        }
    }

    fun getManaCode(landName: String): String? = when (landName) {
        "Plains" -> "W"
        "Island" -> "U"
        "Swamp" -> "B"
        "Mountain" -> "R"
        "Forest" -> "G"
        else -> null
    }
}
