package com.mmg.magicfolder.feature.decks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.core.domain.model.Deck
import com.mmg.magicfolder.core.domain.model.DeckFormat
import com.mmg.magicfolder.core.domain.repository.CardRepository
import com.mmg.magicfolder.core.domain.repository.DeckRepository
import com.mmg.magicfolder.core.domain.repository.UserCardRepository
import com.mmg.magicfolder.core.domain.usecase.decks.BasicLandCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckCard(
    val scryfallId: String,
    val quantity: Int,
    val isSideboard: Boolean,
    val card: Card?,
)

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

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])

    data class AddCardRow(
        val card: Card,
        val quantityInDeck: Int,
        val isOwned: Boolean,
    )

    data class UiState(
        val deck: Deck? = null,
        val cards: List<DeckCard> = emptyList(),
        val isLoading: Boolean = true,
        val totalCards: Int = 0,
        val manaCurve: Map<Int, Int> = emptyMap(),
        val colorDistribution: Map<String, Int> = emptyMap(),
        // Add cards sheet state
        val addCardsQuery: String = "",
        val addCardsResults: List<AddCardRow> = emptyList(),
        val isSearchingCards: Boolean = false,
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
                    )
                }
                val mainboard = deckWithCards.mainboard.map { slot ->
                    val card = when (val r = cardRepository.getCardById(slot.scryfallId)) {
                        is DataResult.Success -> r.data
                        else -> null
                    }
                    DeckCard(slot.scryfallId, slot.quantity, isSideboard = false, card = card)
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
                collectionCards = owned.map { it.card }.sortedBy { it.name }
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
        val filtered = collectionCards.filter { it.name.contains(query, ignoreCase = true) }
        if (filtered.isNotEmpty()) {
            val ownedIds = collectionCards.map { it.scryfallId }.toSet()
            _uiState.update { s ->
                s.copy(
                    addCardsResults = filtered.map { card ->
                        AddCardRow(
                            card = card,
                            quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
                            isOwned = card.scryfallId in ownedIds,
                        )
                    },
                )
            }
        } else {
            searchScryfall(query)
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
            val isBasic = BasicLandCalculator.isBasicLand(card)
            if (!isBasic) {
                // Commander: hard limit at 1
                if (format.uniqueCards && currentQty >= 1) return
                // Draft (maxCopies >= 99): unlimited, no check needed
            }
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
        _uiState.update { it.copy(addCardsQuery = "", addCardsResults = emptyList()) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildManaCurve(cards: List<DeckCard>): Map<Int, Int> =
        cards
            .mapNotNull { it.card }
            .groupBy { minOf(it.cmc.toInt(), 7) }
            .mapValues { it.value.size }

    private fun buildColorDist(cards: List<DeckCard>): Map<String, Int> =
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
