package com.mmg.magicfolder.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.UserCardWithCard
import com.mmg.magicfolder.core.domain.repository.CardRepository
import com.mmg.magicfolder.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.magicfolder.core.domain.usecase.collection.RemoveCardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val getCollection: GetCollectionUseCase,
    private val removeCard: RemoveCardUseCase,
    private val cardRepository: CardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    // Raw unfiltered collection from Room
    private val _allCards = MutableStateFlow<List<UserCardWithCard>>(emptyList())

    init {
        observeCollection()
        refreshPrices()
    }

    private fun observeCollection() {
        viewModelScope.launch {
            getCollection()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { cards ->
                    _allCards.value = cards
                    applyFilters()
                    _uiState.update {
                        it.copy(
                            isLoading    = false,
                            hasStaleCards = cards.any { c -> c.card.isStale },
                        )
                    }
                }
        }
    }

    private fun refreshPrices() {
        viewModelScope.launch {
            runCatching { cardRepository.refreshCollectionPrices() }
        }
    }

    // ── User actions ──────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onFilterChange(filter: ColorFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
        applyFilters()
    }

    fun onSortChange(sort: SortOrder) {
        _uiState.update { it.copy(sortOrder = sort) }
        applyFilters()
    }

    fun onViewModeToggle() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    fun onDeleteCard(userCardId: Long) {
        viewModelScope.launch {
            runCatching { removeCard(userCardId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    // ── Filtering & sorting (pure local, no suspend needed) ───────────────

    private fun applyFilters() {
        val state = _uiState.value
        var result = _allCards.value

        // Search
        if (state.searchQuery.isNotBlank()) {
            result = result.filter {
                it.card.name.contains(state.searchQuery, ignoreCase = true)
            }
        }

        // Color filter
        if (state.activeFilter != ColorFilter.ALL) {
            result = result.filter { item ->
                val colors = item.card.colorIdentity
                when (state.activeFilter) {
                    ColorFilter.COLORLESS  -> colors.isEmpty()
                    ColorFilter.MULTICOLOR -> colors.size > 1
                    else                   -> colors.size == 1 && colors.firstOrNull() == state.activeFilter.name
                }
            }
        }

        // Sort
        result = when (state.sortOrder) {
            SortOrder.NAME        -> result.sortedBy { it.card.name }
            SortOrder.PRICE_DESC  -> result.sortedByDescending { it.card.priceUsd ?: 0.0 }
            SortOrder.PRICE_ASC   -> result.sortedBy { it.card.priceUsd ?: 0.0 }
            SortOrder.RARITY      -> result.sortedByDescending { rarityWeight(it.card.rarity) }
            SortOrder.DATE_ADDED  -> result.sortedByDescending { it.userCard.addedAt }
        }

        _uiState.update { it.copy(cards = result) }
    }

    private fun rarityWeight(rarity: String) = when (rarity.lowercase()) {
        "mythic"   -> 4
        "rare"     -> 3
        "uncommon" -> 2
        else       -> 1
    }
}
