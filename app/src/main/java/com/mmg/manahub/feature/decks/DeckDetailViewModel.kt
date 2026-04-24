package com.mmg.manahub.feature.decks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.*
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.DeckCardValidator
import com.mmg.manahub.feature.decks.engine.DeckImportExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class DeckDetailViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        val BASIC_LAND_NAMES = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    }

    private val deckId: String = checkNotNull(savedStateHandle["deckId"])

    data class UiState(
        val deck: Deck? = null,
        val cards: List<DeckSlotEntry> = emptyList(),
        val isLoading: Boolean = true,
        val totalCards: Int = 0,
        val manaCurve: Map<Int, Int> = emptyMap(),
        val colorDistribution: Map<String, Int> = emptyMap(),
        // Add cards sheet state
        val addCardsQuery: String = "",
        val addCardsResults: List<AddCardRow> = emptyList(),
        val isSearchingCards: Boolean = false,
        // Scryfall tab results (separate from collection tab)
        val scryfallResults: List<AddCardRow> = emptyList(),
        val isSearchingScryfall: Boolean = false,
        // Collection ownership set (scryfallIds owned by the user)
        val collectionIds: Set<String> = emptySet(),
        // Import state
        val isImporting: Boolean = false,
        val importError: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var deckCardsMap: Map<String, Int> = emptyMap()
    private var collectionCards: List<Card> = emptyList()

    val deckFormat: DeckFormat?
        get() = _uiState.value.deck?.format?.let { fmt ->
            DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) }
        }

    init {
        viewModelScope.launch {
            deckRepository.observeDeckWithCards(deckId).collect { deckWithCards ->
                if (deckWithCards == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@collect
                }
                deckCardsMap = deckWithCards.mainboard.associate { it.scryfallId to it.quantity }
                _uiState.update {
                    it.copy(
                        deck = deckWithCards.deck,
                        isLoading = false,
                        totalCards = deckWithCards.totalCards,
                        // Update quantities in add cards results
                        addCardsResults = it.addCardsResults.map { row ->
                            row.copy(quantityInDeck = deckCardsMap[row.card.scryfallId] ?: 0)
                        },
                        scryfallResults = it.scryfallResults.map { row ->
                            row.copy(quantityInDeck = deckCardsMap[row.card.scryfallId] ?: 0)
                        },
                    )
                }
                val mainboard = deckWithCards.mainboard.map { slot ->
                    val card = when (val r = cardRepository.getCardById(slot.scryfallId)) {
                        is DataResult.Success -> r.data
                        else -> null
                    }
                    DeckSlotEntry(slot.scryfallId, slot.quantity, isSideboard = false, card = card)
                }
                _uiState.update {
                    it.copy(
                        cards = mainboard,
                        manaCurve = buildManaCurve(mainboard),
                        colorDistribution = buildColorDist(mainboard),
                    )
                }
            }
        }
        loadCollection()
    }

    private fun loadCollection() {
        viewModelScope.launch {
            try {
                val owned = userCardRepository.observeCollection().first()
                collectionCards = owned.map { it.card }.distinctBy { it.scryfallId }.sortedBy { it.name }
                val ids = collectionCards.map { it.scryfallId }.toSet()
                _uiState.update { it.copy(collectionIds = ids) }
            } catch (_: Exception) {
            }
        }
    }

    fun removeCard(cardId: String) {
        viewModelScope.launch {
            deckRepository.removeCardFromDeck(deckId, cardId, isSideboard = false)
        }
    }

    // ── Add cards sheet ──────────────────────────────────────────────────────

    fun onAddCardsQueryChange(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            showCollectionCards()
            return
        }
        // Only filter the local collection — never fall back to Scryfall here.
        val filtered = collectionCards.filter { it.name.contains(query, ignoreCase = true) }
        _uiState.update { s ->
            s.copy(
                addCardsResults = filtered.map { card ->
                    AddCardRow(
                        card = card,
                        quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
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
                        quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
                        isOwned = true,
                    )
                },
            )
        }
    }

    private fun searchScryfall(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingCards = true) }
            try {
                val result = cardRepository.searchCards(query)
                val cards = when (result) {
                    is DataResult.Success -> result.data
                    is DataResult.Error -> emptyList()
                }
                val ownedIds = collectionCards.map { it.scryfallId }.toSet()
                _uiState.update { s ->
                    s.copy(
                        isSearchingCards = false,
                        addCardsResults = cards.map { card ->
                            AddCardRow(
                                card = card,
                                quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
                                isOwned = card.scryfallId in ownedIds,
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingCards = false) }
            }
        }
    }

    fun addCardToDeck(scryfallId: String) {
        val format = deckFormat
        val currentQty = deckCardsMap[scryfallId] ?: 0
        val card = _uiState.value.addCardsResults.find { it.card.scryfallId == scryfallId }?.card

        if (format != null && card != null) {
            val result = DeckCardValidator.canAddCard(card, format, currentQty)
            if (result == DeckCardValidator.AddResult.BLOCKED) return
        }

        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, scryfallId, currentQty + 1, false)
        }
    }

    fun removeCardFromDeck(scryfallId: String) {
        viewModelScope.launch {
            val currentQty = deckCardsMap[scryfallId] ?: 0
            if (currentQty <= 1) {
                deckRepository.removeCardFromDeck(deckId, scryfallId, false)
            } else {
                deckRepository.addCardToDeck(deckId, scryfallId, currentQty - 1, false)
            }
        }
    }

    fun addBasicLandByName(name: String) {
        viewModelScope.launch {
            // Try to find the land already in the deck (any print)
            val existingEntry = _uiState.value.cards.find { it.card?.name == name }
            if (existingEntry != null) {
                val currentQty = deckCardsMap[existingEntry.scryfallId] ?: 0
                deckRepository.addCardToDeck(
                    deckId,
                    existingEntry.scryfallId,
                    currentQty + 1,
                    false
                )
            } else {
                // searchCardByName fetches from Scryfall AND caches in the local DB,
                // which satisfies the FK constraint on deck_cards.scryfall_id.
                val result = cardRepository.searchCardByName(name)
                if (result is DataResult.Success) {
                    deckRepository.addCardToDeck(deckId, result.data.scryfallId, 1, false)
                }
            }
        }
    }

    fun removeBasicLandByName(name: String) {
        viewModelScope.launch {
            val existingEntry = _uiState.value.cards.find { it.card?.name == name } ?: return@launch
            val currentQty = deckCardsMap[existingEntry.scryfallId] ?: 0
            if (currentQty <= 1) {
                deckRepository.removeCardFromDeck(deckId, existingEntry.scryfallId, false)
            } else {
                deckRepository.addCardToDeck(
                    deckId,
                    existingEntry.scryfallId,
                    currentQty - 1,
                    false
                )
            }
        }
    }

    fun clearAddCardsState() {
        _uiState.update { it.copy(addCardsQuery = "", addCardsResults = emptyList(), scryfallResults = emptyList()) }
    }

    fun searchScryfallDirect(query: String) {
        // Always sync the shared query field so the search bar stays up-to-date.
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(scryfallResults = emptyList(), isSearchingScryfall = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingScryfall = true) }
            try {
                val result = cardRepository.searchCards(query)
                val cards = when (result) {
                    is DataResult.Success -> result.data
                    is DataResult.Error -> emptyList()
                }
                val ownedIds = collectionCards.map { it.scryfallId }.toSet()
                _uiState.update { s ->
                    s.copy(
                        isSearchingScryfall = false,
                        scryfallResults = cards.map { card ->
                            AddCardRow(
                                card = card,
                                quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
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

    fun updateDeckName(name: String) {
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                runCatching { deckRepository.updateDeck(deck.copy(name = trimmed)) }
            }
        }
    }

    // ── Import / Export ──────────────────────────────────────────────────────

    /**
     * Parses [text] in Moxfield/Arena format and adds all resolved cards to this deck.
     */
    fun importCardsFromText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }
            try {
                val parsed = DeckImportExportHelper.parse(text)

                val allLines = parsed.mainboard + parsed.sideboard +
                    listOfNotNull(parsed.commander)

                var failCount = 0
                for (line in allLines) {
                    val isSide = line in parsed.sideboard
                    val result = cardRepository.searchCardByName(line.name)
                    if (result is DataResult.Success) {
                        val currentQty = deckCardsMap[result.data.scryfallId] ?: 0
                        deckRepository.addCardToDeck(
                            deckId      = deckId,
                            scryfallId  = result.data.scryfallId,
                            quantity    = currentQty + line.quantity,
                            isSideboard = isSide,
                        )
                    } else {
                        failCount++
                    }
                }

                val msg = if (failCount > 0) "$failCount card(s) could not be found" else null
                _uiState.update { it.copy(isImporting = false, importError = msg) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, importError = e.message) }
            }
        }
    }

    fun clearImportError() {
        _uiState.update { it.copy(importError = null) }
    }

    /**
     * Returns the deck as a Moxfield/Arena-compatible text string.
     * Returns null if the deck has not loaded yet.
     */
    fun exportDeckToText(): String? {
        val state = _uiState.value
        val deck  = state.deck ?: return null

        // Commander is identified by coverCardId, which is set to the commander's scryfallId on creation
        val commanderCard = deck.coverCardId?.let { coverId ->
            state.cards.firstOrNull { it.scryfallId == coverId }?.card
        }
        val commanderScryfallId = commanderCard?.scryfallId

        val mainDeckCards = state.cards
            .filter { !it.isSideboard && it.scryfallId != commanderScryfallId }
            .mapNotNull { dc -> dc.card?.let { card ->
                com.mmg.manahub.core.domain.model.DeckCard(card = card, quantity = dc.quantity)
            }}

        val sideboardCards = state.cards
            .filter { it.isSideboard }
            .mapNotNull { dc -> dc.card?.let { card ->
                com.mmg.manahub.core.domain.model.DeckCard(card = card, quantity = dc.quantity)
            }}

        return DeckImportExportHelper.export(
            deckName  = deck.name,
            mainboard = mainDeckCards,
            sideboard = sideboardCards,
            commander = commanderCard,
        )
    }

    /**
     * Called when the user navigates away from this screen (back button or system back).
     * Triggers a best-effort push to Supabase if the deck has unsynchronised local changes.
     * The sync_status field in Room is the durable retry mechanism if this coroutine is
     * cancelled before completing.
     */
    fun onNavigatingBack() {
        // Sync is handled by SyncManager / WorkManager — no inline sync needed here.
    }

    fun setCoverCard(scryfallId: String) {
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            runCatching { deckRepository.updateDeck(deck.copy(coverCardId = scryfallId)) }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildManaCurve(cards: List<DeckSlotEntry>): Map<Int, Int> =
        cards
            .mapNotNull { it.card }
            .groupBy { minOf(it.cmc.toInt(), 7) }
            .mapValues { it.value.size }

    private fun buildColorDist(cards: List<DeckSlotEntry>): Map<String, Int> =
        cards
            .mapNotNull { it.card }
            .flatMap { it.colors }
            .groupBy { it }
            .mapValues { it.value.size }

    fun getManaCode(landName: String): String? = when (landName) {
        "Plains" -> "W"
        "Island" -> "U"
        "Swamp" -> "B"
        "Mountain" -> "R"
        "Forest" -> "G"
        else -> null
    }
}
