package com.mmg.magicfolder.feature.decks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.core.domain.repository.CardRepository
import com.mmg.magicfolder.core.domain.repository.DeckRepository
import com.mmg.magicfolder.core.domain.repository.UserCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckAddCardsViewModel @Inject constructor(
    private val deckRepository:     DeckRepository,
    private val userCardRepository: UserCardRepository,
    private val cardRepository:     CardRepository,
    savedStateHandle:               SavedStateHandle,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])

    data class CardRow(
        val card:           Card,
        val quantityInDeck: Int,
        val isOwned:        Boolean,
    )

    data class UiState(
        val deckName:   String       = "",
        val query:      String       = "",
        val cards:      List<CardRow> = emptyList(),
        val isLoading:  Boolean      = true,
        val isSearching: Boolean     = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Kept in sync with observeDeckWithCards
    private var deckCardsMap: Map<String, Int> = emptyMap()
    private var collectionCards: List<Card> = emptyList()

    init {
        viewModelScope.launch {
            deckRepository.observeDeckWithCards(deckId).collect { deckWithCards ->
                val deck = deckWithCards ?: return@collect
                deckCardsMap = deck.mainboard.associate { it.scryfallId to it.quantity }
                _uiState.update { s ->
                    s.copy(
                        deckName = deck.deck.name,
                        cards    = s.cards.map { row ->
                            row.copy(quantityInDeck = deckCardsMap[row.card.scryfallId] ?: 0)
                        },
                    )
                }
            }
        }
        loadCollection()
    }

    private fun loadCollection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val owned = userCardRepository.observeCollection().first()
                collectionCards = owned.map { it.card }.sortedBy { it.name }
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        cards     = collectionCards.map { card ->
                            CardRow(
                                card           = card,
                                quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
                                isOwned        = true,
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.isBlank()) {
            showCollection()
            return
        }
        val filtered = collectionCards.filter { it.name.contains(query, ignoreCase = true) }
        if (filtered.isNotEmpty()) {
            _uiState.update { s ->
                s.copy(
                    cards = filtered.map { card ->
                        CardRow(
                            card           = card,
                            quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
                            isOwned        = true,
                        )
                    },
                )
            }
        } else {
            searchScryfall(query)
        }
    }

    private fun showCollection() {
        _uiState.update { s ->
            s.copy(
                cards = collectionCards.map { card ->
                    CardRow(
                        card           = card,
                        quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
                        isOwned        = true,
                    )
                },
            )
        }
    }

    private fun searchScryfall(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val result = cardRepository.searchCards(query)
                val cards = when (result) {
                    is DataResult.Success -> result.data
                    is DataResult.Error   -> emptyList()
                }
                val ownedIds = collectionCards.map { it.scryfallId }.toSet()
                _uiState.update { s ->
                    s.copy(
                        isSearching = false,
                        cards       = cards.map { card ->
                            CardRow(
                                card           = card,
                                quantityInDeck = deckCardsMap[card.scryfallId] ?: 0,
                                isOwned        = card.scryfallId in ownedIds,
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun addCard(scryfallId: String) {
        viewModelScope.launch {
            val currentQty = deckCardsMap[scryfallId] ?: 0
            deckRepository.addCardToDeck(deckId, scryfallId, currentQty + 1, false)
        }
    }

    fun removeCard(scryfallId: String) {
        viewModelScope.launch {
            val currentQty = deckCardsMap[scryfallId] ?: 0
            if (currentQty <= 1) {
                deckRepository.removeCardFromDeck(deckId, scryfallId, false)
            } else {
                deckRepository.addCardToDeck(deckId, scryfallId, currentQty - 1, false)
            }
        }
    }
}
