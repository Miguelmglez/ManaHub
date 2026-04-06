package com.mmg.magicfolder.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.AdvancedSearchQuery
import com.mmg.magicfolder.core.domain.model.ComparisonOperator
import com.mmg.magicfolder.core.domain.model.SearchCriterion
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

    fun toggleColorFilter(filter: ColorFilter) {
        val current = _uiState.value.activeFilters.toMutableSet()
        when (filter) {
            ColorFilter.ALL -> current.clear()
            ColorFilter.COLORLESS -> {
                if (current.contains(ColorFilter.COLORLESS)) current.remove(ColorFilter.COLORLESS)
                else { current.clear(); current.add(ColorFilter.COLORLESS) }
            }
            else -> {
                // WUBRG — multi-selectable, exclusive with COLORLESS and MULTICOLOR
                if (current.contains(filter)) current.remove(filter)
                else {
                    current.remove(ColorFilter.COLORLESS)
                    current.add(filter)
                }
            }
        }
        _uiState.update { it.copy(activeFilters = current) }
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

    fun applyAdvancedFilters(query: AdvancedSearchQuery) {
        val filtered = if (query.isEmpty()) {
            _allCards.value
        } else {
            _allCards.value.filter { card ->
                query.criteria.all { criterion -> matchesCriterion(card, criterion) }
            }
        }
        _uiState.update { it.copy(cards = filtered.groupByCard()) }
    }

    private fun matchesCriterion(card: UserCardWithCard, criterion: SearchCriterion): Boolean {
        return when (criterion) {
            is SearchCriterion.Name ->
                if (criterion.exact)
                    card.card.name.equals(criterion.value, ignoreCase = true)
                else
                    card.card.name.contains(criterion.value, ignoreCase = true)
            is SearchCriterion.OracleText ->
                card.card.oracleText?.contains(criterion.value, ignoreCase = true) == true
            is SearchCriterion.CardType ->
                criterion.value.split(" ").filter { it.isNotBlank() }.all { word ->
                    card.card.typeLine.contains(word, ignoreCase = true)
                }
            is SearchCriterion.Colors ->
                if (criterion.exactly)
                    card.card.colors.map { it.uppercase() }.toSet() ==
                        criterion.colors.map { it.uppercase() }.toSet()
                else
                    criterion.colors.all { c ->
                        card.card.colors.any { it.equals(c, ignoreCase = true) }
                    }
            is SearchCriterion.ColorIdentity ->
                if (criterion.exactly)
                    card.card.colorIdentity.map { it.uppercase() }.toSet() ==
                        criterion.colors.map { it.uppercase() }.toSet()
                else
                    criterion.colors.all { c ->
                        card.card.colorIdentity.any { it.equals(c, ignoreCase = true) }
                    }
            is SearchCriterion.Rarity ->
                compareRarity(card.card.rarity, criterion.rarity, criterion.operator)
            is SearchCriterion.ManaCost ->
                compareInt(card.card.cmc.toInt(), criterion.value, criterion.operator)
            is SearchCriterion.Price -> {
                val price = if (criterion.currency == "eur") card.card.priceEur else card.card.priceUsd
                price != null && compareDouble(price, criterion.value, criterion.operator)
            }
            else -> true
        }
    }

    private fun compareRarity(cardRarity: String, targetRarity: String, op: ComparisonOperator): Boolean {
        val order = listOf("common", "uncommon", "rare", "mythic")
        val cardIdx = order.indexOf(cardRarity.lowercase())
        val targetIdx = order.indexOf(targetRarity.lowercase())
        if (cardIdx < 0 || targetIdx < 0) return false
        return when (op) {
            ComparisonOperator.EQUAL             -> cardIdx == targetIdx
            ComparisonOperator.LESS              -> cardIdx < targetIdx
            ComparisonOperator.LESS_OR_EQUAL     -> cardIdx <= targetIdx
            ComparisonOperator.GREATER           -> cardIdx > targetIdx
            ComparisonOperator.GREATER_OR_EQUAL  -> cardIdx >= targetIdx
            ComparisonOperator.NOT_EQUAL         -> cardIdx != targetIdx
        }
    }

    private fun compareInt(cardVal: Int, target: Int, op: ComparisonOperator): Boolean = when (op) {
        ComparisonOperator.EQUAL             -> cardVal == target
        ComparisonOperator.LESS              -> cardVal < target
        ComparisonOperator.LESS_OR_EQUAL     -> cardVal <= target
        ComparisonOperator.GREATER           -> cardVal > target
        ComparisonOperator.GREATER_OR_EQUAL  -> cardVal >= target
        ComparisonOperator.NOT_EQUAL         -> cardVal != target
    }

    private fun compareDouble(cardVal: Double, target: Double, op: ComparisonOperator): Boolean = when (op) {
        ComparisonOperator.EQUAL             -> cardVal == target
        ComparisonOperator.LESS              -> cardVal < target
        ComparisonOperator.LESS_OR_EQUAL     -> cardVal <= target
        ComparisonOperator.GREATER           -> cardVal > target
        ComparisonOperator.GREATER_OR_EQUAL  -> cardVal >= target
        ComparisonOperator.NOT_EQUAL         -> cardVal != target
    }

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
        val filters = state.activeFilters
        if (filters.isNotEmpty()) {
            result = result.filter { item ->
                val colors = item.card.colorIdentity
                when {
                    filters.contains(ColorFilter.COLORLESS)  -> colors.isEmpty()
                    else -> {
                        // AND logic: card must contain ALL selected WUBRG colors
                        val selectedColors = filters.map { it.name }.toSet()
                        selectedColors.all { colors.contains(it) }
                    }
                }
            }
        }

        // Group copies of the same card into one entry
        val grouped = result.groupByCard()

        // Sort
        val sorted = when (state.sortOrder) {
            SortOrder.NAME        -> grouped.sortedBy { it.card.name }
            SortOrder.PRICE_DESC  -> grouped.sortedByDescending { it.card.priceUsd ?: 0.0 }
            SortOrder.PRICE_ASC   -> grouped.sortedBy { it.card.priceUsd ?: 0.0 }
            SortOrder.RARITY      -> grouped.sortedByDescending { rarityWeight(it.card.rarity) }
            SortOrder.DATE_ADDED  -> grouped.sortedByDescending { it.latestAddedAt }
        }

        _uiState.update { it.copy(cards = sorted) }
    }

    private fun rarityWeight(rarity: String) = when (rarity.lowercase()) {
        "mythic"   -> 4
        "rare"     -> 3
        "uncommon" -> 2
        else       -> 1
    }
}
