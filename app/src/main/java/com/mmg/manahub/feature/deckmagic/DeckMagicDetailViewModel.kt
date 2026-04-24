package com.mmg.manahub.feature.deckmagic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.mmg.manahub.core.domain.model.*
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.DeckCardValidator
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.decks.engine.DeckImportExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val syncState: SyncState = SyncState.IDLE,
    val syncError: String? = null
)

@HiltViewModel
class DeckMagicDetailViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val workManager: WorkManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

   // val deckId: Long = checkNotNull(savedStateHandle["deckId"])

    val deckId: String = checkNotNull(savedStateHandle["deckId"])
    private val _uiState = MutableStateFlow(DeckMagicDetailUiState())
    val uiState: StateFlow<DeckMagicDetailUiState> = _uiState.asStateFlow()

    private var deckCardsMap: Map<Pair<String, Boolean>, Int> = emptyMap()
    private var collectionCards: List<Card> = emptyList()

    val deckFormat: DeckFormat?
        get() = _uiState.value.deck?.format?.let { fmt ->
            DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) }
        }

    init {
        observeDeck()
        observeCollection()
        observeSyncState()
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
                val entries = mainEntries + sideEntries

                deckCardsMap = entries.associate { (it.scryfallId to it.isSideboard) to it.quantity }

                // Explicit validation based on requirements
                val format = deckFormat
                val overLimit = entries
                    .groupBy { it.scryfallId }
                    .filter { (_, slots) ->
                        val card = slots.first().card
                        card != null && !BasicLandCalculator.isBasicLand(card) && run {
                            val limit = when (format) {
                                DeckFormat.COMMANDER -> 1
                                DeckFormat.STANDARD -> 4
                                DeckFormat.DRAFT -> Int.MAX_VALUE
                                else -> 4 // Fallback
                            }
                            slots.sumOf { it.quantity } > limit
                        }
                    }
                    .keys

                _uiState.update { s ->
                    s.copy(
                        deck = deckWithCards.deck,
                        cards = entries,
                        isLoading = false,
                        totalCards = deckWithCards.totalCards,
                        manaCurve = calculateManaCurve(entries),
                        landDeltas = calculateLandDeltas(entries, deckWithCards.deck.format),
                        overLimitCards = overLimit,
                        addCardsResults = s.addCardsResults.map { row ->
                            row.copy(quantityInDeck = deckCardsMap[row.card.scryfallId to false] ?: 0)
                        },
                        scryfallResults = s.scryfallResults.map { row ->
                            row.copy(quantityInDeck = deckCardsMap[row.card.scryfallId to false] ?: 0)
                        }
                    )
                }
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

    private fun calculateLandDeltas(entries: List<DeckSlotEntry>, formatName: String): List<LandDelta> {
        val format = DeckFormat.entries.find { it.name.equals(formatName, ignoreCase = true) }
            ?: DeckFormat.STANDARD
        
        val deckCards = entries.filter { it.card != null && !it.isSideboard }.map { 
            DeckCard(it.card!!, it.quantity, isOwned = true) 
        }
        
        val nonBasicLands = deckCards.filter { !BasicLandCalculator.isBasicLand(it.card) && BasicLandCalculator.isLand(it.card) }
        val mainboardNonLands = deckCards.filter { !BasicLandCalculator.isLand(it.card) }
        
        val suggested = BasicLandCalculator.calculate(mainboardNonLands, nonBasicLands, format)
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

    fun applyLandSuggestions() {
        val deltas = _uiState.value.landDeltas
        viewModelScope.launch {
            deltas.forEach { delta ->
                    if (delta.delta > 0) {
                        val card = searchBasicLand(delta.landName)
                        if (card != null) {
                            deckRepository.addCardToDeck(deckId, card.scryfallId, (deckCardsMap[card.scryfallId to false] ?: 0) + delta.delta, false)
                        }
                    } else if (delta.delta < 0) {
                        val existing = _uiState.value.cards.find { it.card?.name == delta.landName && !it.isSideboard }
                        if (existing != null) {
                            val newQty = (deckCardsMap[existing.scryfallId to false] ?: 0) + delta.delta
                            if (newQty <= 0) {
                                deckRepository.removeCardFromDeck(deckId, existing.scryfallId, false)
                            } else {
                                deckRepository.addCardToDeck(deckId, existing.scryfallId, newQty, false)
                            }
                        }
                    }
            }
        }
    }

    private suspend fun searchBasicLand(name: String): Card? {
        return (cardRepository.searchCardByName(name) as? DataResult.Success<Card>)?.data
    }

    // ── Deck Management ───────────────────────────────────────────────────────

    fun addCardToDeck(scryfallId: String, isSideboard: Boolean = false) {
        val format = deckFormat
        val currentQtyInBoard = deckCardsMap[scryfallId to isSideboard] ?: 0
        val totalQty = (deckCardsMap[scryfallId to true] ?: 0) + (deckCardsMap[scryfallId to false] ?: 0)
        
        val card = (_uiState.value.addCardsResults + _uiState.value.scryfallResults + _uiState.value.cards.map { AddCardRow(it.card!!, it.quantity, true) })
            .find { it.card.scryfallId == scryfallId }?.card

        if (format != null && card != null) {
            val result = DeckCardValidator.canAddCard(card, format, totalQty)
            if (result == DeckCardValidator.AddResult.BLOCKED) return
        }

        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, scryfallId, currentQtyInBoard + 1, isSideboard)
        }
    }

    fun removeCardFromDeck(scryfallId: String, isSideboard: Boolean = false) {
        viewModelScope.launch {
            val currentQty = deckCardsMap[scryfallId to isSideboard] ?: 0
            if (currentQty <= 1) {
                deckRepository.removeCardFromDeck(deckId, scryfallId, isSideboard)
            } else {
                deckRepository.addCardToDeck(deckId, scryfallId, currentQty - 1, isSideboard)
            }
        }
    }

    fun removeCard(scryfallId: String, isSideboard: Boolean = false) {
        viewModelScope.launch {
            deckRepository.removeCardFromDeck(deckId, scryfallId, isSideboard)
        }
    }

    fun moveQuantityToSideboard(scryfallId: String, quantity: Int) {
        viewModelScope.launch {
            val entry = _uiState.value.cards.find { it.scryfallId == scryfallId && !it.isSideboard } ?: return@launch
            val toMove = quantity.coerceIn(1, entry.quantity)
            
            // Update mainboard (decrement or remove)
            if (entry.quantity == toMove) {
                deckRepository.removeCardFromDeck(deckId, scryfallId, false)
            } else {
                deckRepository.addCardToDeck(deckId, scryfallId, entry.quantity - toMove, false)
            }
            
            // Update sideboard (increment or add)
            val sideEntry = _uiState.value.cards.find { it.scryfallId == scryfallId && it.isSideboard }
            val currentSideQty = sideEntry?.quantity ?: 0
            deckRepository.addCardToDeck(deckId, scryfallId, currentSideQty + toMove, true)
        }
    }

    fun moveQuantityToMainboard(scryfallId: String, quantity: Int) {
        viewModelScope.launch {
            val entry = _uiState.value.cards.find { it.scryfallId == scryfallId && it.isSideboard } ?: return@launch
            val toMove = quantity.coerceIn(1, entry.quantity)
            
            // Update sideboard
            if (entry.quantity == toMove) {
                deckRepository.removeCardFromDeck(deckId, scryfallId, true)
            } else {
                deckRepository.addCardToDeck(deckId, scryfallId, entry.quantity - toMove, true)
            }
            
            // Update mainboard
            val mainEntry = _uiState.value.cards.find { it.scryfallId == scryfallId && !it.isSideboard }
            val currentMainQty = mainEntry?.quantity ?: 0
            deckRepository.addCardToDeck(deckId, scryfallId, currentMainQty + toMove, false)
        }
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
                val currentQty = deckCardsMap[existingEntry.scryfallId to false] ?: 0
                deckRepository.addCardToDeck(deckId, existingEntry.scryfallId, currentQty + 1, false)
            } else {
                val result = cardRepository.searchCardByName(name)
                if (result is DataResult.Success) {
                    deckRepository.addCardToDeck(deckId, result.data.scryfallId, 1, false)
                }
            }
        }
    }

    fun removeBasicLandByName(name: String) {
        viewModelScope.launch {
            val existingEntry = _uiState.value.cards.find { it.card?.name == name && !it.isSideboard } ?: return@launch
            val currentQty = deckCardsMap[existingEntry.scryfallId to false] ?: 0
            if (currentQty <= 1) {
                deckRepository.removeCardFromDeck(deckId, existingEntry.scryfallId, false)
            } else {
                deckRepository.addCardToDeck(deckId, existingEntry.scryfallId, currentQty - 1, false)
            }
        }
    }

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
                        quantityInDeck = deckCardsMap[card.scryfallId to false] ?: 0,
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
                        quantityInDeck = deckCardsMap[card.scryfallId to false] ?: 0,
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
                                quantityInDeck = deckCardsMap[card.scryfallId to false] ?: 0,
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

    fun updateDeckName(name: String) {
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                runCatching { deckRepository.updateDeck(deck.copy(name = trimmed)) }
            }
        }
    }

    fun setCoverCard(scryfallId: String) {
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            runCatching { deckRepository.updateDeck(deck.copy(coverCardId = scryfallId)) }
        }
    }

    fun exportDeckToText(): String? {
        val state = _uiState.value
        val deck  = state.deck ?: return null

        val commanderCard = deck.coverCardId?.let { coverId ->
            state.cards.firstOrNull { it.scryfallId == coverId }?.card
        }
        val commanderScryfallId = commanderCard?.scryfallId

        val mainDeckCards = state.cards
            .filter { !it.isSideboard && it.scryfallId != commanderScryfallId }
            .mapNotNull { dc -> dc.card?.let { card ->
                DeckCard(card = card, quantity = dc.quantity)
            }}

        val sideboardCards = state.cards
            .filter { it.isSideboard }
            .mapNotNull { dc -> dc.card?.let { card ->
                DeckCard(card = card, quantity = dc.quantity)
            }}

        return DeckImportExportHelper.export(
            deckName  = deck.name,
            mainboard = mainDeckCards,
            sideboard = sideboardCards,
            commander = commanderCard,
        )
    }

    fun onNavigatingBack() {
        // Sync is handled by SyncManager / WorkManager — no inline sync needed here.
    }

    fun saveDeck(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, syncError = null) }

            val userId = authRepository.getCurrentUser()?.id
            if (userId != null) {
                // Enqueue background sync as fallback
                workManager.enqueueUniqueWork(
                    CollectionSyncWorker.WORK_NAME_ONE_TIME,
                    ExistingWorkPolicy.REPLACE,
                    CollectionSyncWorker.oneTimeWorkRequest()
                )
                // Run inline sync for immediate feedback
                val result = syncManager.sync(userId)
                _uiState.update { it.copy(syncError = result.error) }
            }

            _uiState.update { it.copy(isSaving = false) }
            onComplete()
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
