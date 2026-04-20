package com.mmg.manahub.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.AdvancedSearchQuery
import com.mmg.manahub.core.domain.model.ComparisonOperator
import com.mmg.manahub.core.domain.model.SearchCriterion
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RemoveCardUseCase
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val getCollection: GetCollectionUseCase,
    private val removeCard: RemoveCardUseCase,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val authRepository: AuthRepository,
    private val prefsDataStore: UserPreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    // Raw unfiltered collection from Room
    private val _allCards = MutableStateFlow<List<UserCardWithCard>>(emptyList())

    init {
        observeCollection()
        refreshPrices()
        observePendingCount()
        autoSyncIfFirstRunOfDay()
        observeSessionChanges()
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

    private fun observePendingCount() {
        viewModelScope.launch {
            userCardRepository.observePendingCount()
                .collect { count -> _uiState.update { it.copy(pendingUploadCount = count) } }
        }
    }

    private fun observeSessionChanges() {
        viewModelScope.launch {
            var previousUserId: String? = null
            authRepository.sessionState.collect { state ->
                _uiState.update { it.copy(sessionState = state) }
                when (state) {
                    is SessionState.Authenticated -> previousUserId = state.user.id
                    is SessionState.Unauthenticated -> {
                        previousUserId?.let { prefsDataStore.clearPendingDeleteRemoteIds(it) }
                        previousUserId = null
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun autoSyncIfFirstRunOfDay() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val today = LocalDate.now(ZoneOffset.UTC).toString()
            val lastDate = prefsDataStore.getLastSyncDate(userId)
            if (lastDate != today) {
                pushInternal(userId)
            }
        }
    }

    // ── Sync user actions ─────────────────────────────────────────────────────

    fun onPushCollection() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            pushInternal(userId)
        }
    }

    fun onPullCollection() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            _uiState.update { it.copy(syncState = SyncState.SYNCING, syncError = null) }
            userCardRepository.pullChanges(userId)
                .onSuccess { _uiState.update { it.copy(syncState = SyncState.SUCCESS) } }
                .onFailure { e -> _uiState.update { it.copy(syncState = SyncState.ERROR, syncError = e.message) } }
        }
    }

    fun onSyncDismissed() {
        _uiState.update { it.copy(syncState = SyncState.IDLE, syncError = null) }
    }

    private suspend fun pushInternal(userId: String) {
        _uiState.update { it.copy(syncState = SyncState.SYNCING, syncError = null) }
        userCardRepository.pushPendingChanges(userId)
            .onSuccess { _uiState.update { it.copy(syncState = SyncState.SUCCESS) } }
            .onFailure { e -> _uiState.update { it.copy(syncState = SyncState.ERROR, syncError = e.message) } }
    }

    // ── User actions ──────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
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

    fun onTabSelected(tab: CollectionTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun applyAdvancedFilters(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(activeQuery = if (query.isEmpty()) null else query) }
        applyFilters()
    }

    fun clearAdvancedFilters() {
        _uiState.update { it.copy(activeQuery = null) }
        applyFilters()
    }

    // ── Filtering & sorting ───────────────────────────────────────────────

    private fun applyFilters() {
        val state = _uiState.value
        var result = _allCards.value

        // Text search
        if (state.searchQuery.isNotBlank()) {
            result = result.filter {
                it.card.name.contains(state.searchQuery, ignoreCase = true)
            }
        }

        // Advanced criteria
        state.activeQuery?.let { query ->
            if (!query.isEmpty()) {
                result = result.filter { card ->
                    query.criteria.all { criterion -> matchesCriterion(card, criterion) }
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
            is SearchCriterion.CardSet ->
                criterion.setCodes.contains(card.card.setCode.lowercase())
            is SearchCriterion.Power -> {
                val power = card.card.power?.toIntOrNull() ?: return false
                compareInt(power, criterion.value, criterion.operator)
            }
            is SearchCriterion.Toughness -> {
                val toughness = card.card.toughness?.toIntOrNull() ?: return false
                compareInt(toughness, criterion.value, criterion.operator)
            }
            is SearchCriterion.Format ->
                matchesFormat(card.card, criterion.format, criterion.legal)
            is SearchCriterion.Keyword ->
                card.card.keywords.any { it.equals(criterion.value, ignoreCase = true) } ||
                    card.card.oracleText?.contains(criterion.value, ignoreCase = true) == true
            // ── Collection-local ──────────────────────────────────────────────
            is SearchCriterion.IsInWishlist ->
                card.userCard.isInWishlist == criterion.value
            is SearchCriterion.IsForTrade ->
                card.userCard.isForTrade == criterion.value
            is SearchCriterion.HasTag ->
                criterion.keys.any { key ->
                    card.card.tags.any { it.key == key } ||
                        card.card.userTags.any { it.key == key }
                }
            else -> true
        }
    }

    private fun matchesFormat(card: com.mmg.manahub.core.domain.model.Card, format: String, legal: Boolean): Boolean {
        val isLegal = when (format.lowercase()) {
            "standard"  -> card.legalityStandard  == "legal"
            "pioneer"   -> card.legalityPioneer   == "legal"
            "modern"    -> card.legalityModern    == "legal"
            "commander" -> card.legalityCommander == "legal"
            else        -> false
        }
        return isLegal == legal
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

    private fun rarityWeight(rarity: String) = when (rarity.lowercase()) {
        "mythic"   -> 4
        "rare"     -> 3
        "uncommon" -> 2
        else       -> 1
    }
}
