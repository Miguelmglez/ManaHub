package com.mmg.manahub.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.mmg.manahub.core.domain.model.AdvancedSearchQuery
import com.mmg.manahub.core.domain.model.ComparisonOperator
import com.mmg.manahub.core.domain.model.SearchCriterion
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RemoveCardUseCase
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the collection screen.
 *
 * Sync is reduced to a single [onSync] action that triggers both push and pull
 * via [SyncManager]. The periodic background sync is scheduled once via WorkManager
 * when the user is authenticated.
 *
 * All push/pull details, watermarks, and LWW conflict resolution are handled
 * internally by [SyncManager] — this ViewModel only reports the state to the UI.
 */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val getCollection: GetCollectionUseCase,
    private val removeCard: RemoveCardUseCase,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val workManager: WorkManager,
    private val migrateLocalTradeLists: MigrateLocalTradeListsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    // Raw unfiltered collection from Room (non-deleted entries)
    private val _allCards = MutableStateFlow<List<UserCardWithCard>>(emptyList())

    // ViewModel-scoped field (not a local var) so it survives config changes.
    // Reset only on genuine Unauthenticated transitions, never on Loading, so
    // that token-refresh cycles (Authenticated → Loading → Authenticated) don't
    // re-trigger assignUserIdAndSync on every screen rotation.
    private var previouslyAuthenticated = false

    init {
        observeCollection()
        refreshPrices()
        observeSyncState()
        observeSessionChanges()
    }

    // ── Collection observation ────────────────────────────────────────────────

    private fun observeCollection() {
        viewModelScope.launch {
            getCollection()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { cards ->
                    _allCards.value = cards
                    applyFilters()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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

    /** Forwards [SyncManager.syncState] into the UI state. */
    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.update { it.copy(syncState = state) }
            }
        }
    }

    private fun observeSessionChanges() {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                _uiState.update { it.copy(sessionState = state) }
                when (state) {
                    is SessionState.Authenticated -> {
                        CollectionSyncWorker.schedulePeriodicSync(workManager)
                        if (!previouslyAuthenticated) {
                            // First transition to authenticated in this session.
                            // Set the flag BEFORE launching so a rapid second Authenticated
                            // emit (profile enrichment) doesn't fire a second sync.
                            previouslyAuthenticated = true
                            viewModelScope.launch {
                                syncManager.assignUserIdAndSync(state.user.id)
                            }
                            triggerTradeListMigration(state.user.id)
                        }
                    }
                    is SessionState.Unauthenticated -> {
                        // Cancel background sync — stale/missing session would cause
                        // Supabase RPC failures if the worker ran now.
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_ONE_TIME)
                        previouslyAuthenticated = false
                    }
                    is SessionState.Loading -> { /* no-op — wait for final state */ }
                }
            }
        }
    }

    // ── Sync user action ──────────────────────────────────────────────────────

    /**
     * Triggers a one-shot full sync (push + pull) for the current user.
     * Runs inline so the UI reflects the result immediately via [SyncManager.syncState].
     */
    fun onSync() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            _uiState.update { it.copy(syncError = null) }
            val result = syncManager.sync(userId)
            _uiState.update { it.copy(syncError = result.error) }
        }
    }

    /** Dismisses the sync status snackbar/banner. */
    fun onSyncDismissed() {
        _uiState.update { it.copy(syncState = SyncState.IDLE, syncError = null) }
    }

    // ── User actions ──────────────────────────────────────────────────────────

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

    fun onDeleteCard(userCardId: String) {
        viewModelScope.launch {
            runCatching { removeCard(userCardId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onTabSelected(tab: CollectionTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun onSnackbarDismissed() = _uiState.update { it.copy(snackbarMessage = null) }

    /**
     * Migrates locally stored trade lists (wishlist + open-for-trade) to the
     * authenticated user's remote account. Shows a Snackbar if cards were migrated.
     *
     * The migration string format is handled by the caller (CollectionScreen) using
     * the count stored in [CollectionUiState.snackbarMessage].
     */
    private fun triggerTradeListMigration(userId: String) {
        viewModelScope.launch {
            migrateLocalTradeLists(userId)
                .onSuccess { count ->
                    if (count > 0) {
                        _uiState.update { it.copy(snackbarMessage = count.toString()) }
                    }
                }
        }
    }

    fun applyAdvancedFilters(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(activeQuery = if (query.isEmpty()) null else query) }
        applyFilters()
    }

    fun clearAdvancedFilters() {
        _uiState.update { it.copy(activeQuery = null) }
        applyFilters()
    }

    // ── Filtering & sorting ───────────────────────────────────────────────────

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
            SortOrder.NAME       -> grouped.sortedBy { it.card.name }
            SortOrder.PRICE_DESC -> grouped.sortedByDescending { it.card.priceUsd ?: 0.0 }
            SortOrder.PRICE_ASC  -> grouped.sortedBy { it.card.priceUsd ?: 0.0 }
            SortOrder.RARITY     -> grouped.sortedByDescending { rarityWeight(it.card.rarity) }
            SortOrder.DATE_ADDED -> grouped.sortedByDescending { it.latestAddedAt }
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

    private fun matchesFormat(
        card: com.mmg.manahub.core.domain.model.Card,
        format: String,
        legal: Boolean,
    ): Boolean {
        val isLegal = when (format.lowercase()) {
            "standard"  -> card.legalityStandard  == "legal"
            "pioneer"   -> card.legalityPioneer   == "legal"
            "modern"    -> card.legalityModern    == "legal"
            "commander" -> card.legalityCommander == "legal"
            else        -> false
        }
        return isLegal == legal
    }

    private fun compareRarity(
        cardRarity: String,
        targetRarity: String,
        op: ComparisonOperator,
    ): Boolean {
        val order = listOf("common", "uncommon", "rare", "mythic")
        val cardIdx = order.indexOf(cardRarity.lowercase())
        val targetIdx = order.indexOf(targetRarity.lowercase())
        if (cardIdx < 0 || targetIdx < 0) return false
        return when (op) {
            ComparisonOperator.EQUAL            -> cardIdx == targetIdx
            ComparisonOperator.LESS             -> cardIdx < targetIdx
            ComparisonOperator.LESS_OR_EQUAL    -> cardIdx <= targetIdx
            ComparisonOperator.GREATER          -> cardIdx > targetIdx
            ComparisonOperator.GREATER_OR_EQUAL -> cardIdx >= targetIdx
            ComparisonOperator.NOT_EQUAL        -> cardIdx != targetIdx
        }
    }

    private fun compareInt(cardVal: Int, target: Int, op: ComparisonOperator): Boolean = when (op) {
        ComparisonOperator.EQUAL            -> cardVal == target
        ComparisonOperator.LESS             -> cardVal < target
        ComparisonOperator.LESS_OR_EQUAL    -> cardVal <= target
        ComparisonOperator.GREATER          -> cardVal > target
        ComparisonOperator.GREATER_OR_EQUAL -> cardVal >= target
        ComparisonOperator.NOT_EQUAL        -> cardVal != target
    }

    private fun compareDouble(cardVal: Double, target: Double, op: ComparisonOperator): Boolean =
        when (op) {
            ComparisonOperator.EQUAL            -> cardVal == target
            ComparisonOperator.LESS             -> cardVal < target
            ComparisonOperator.LESS_OR_EQUAL    -> cardVal <= target
            ComparisonOperator.GREATER          -> cardVal > target
            ComparisonOperator.GREATER_OR_EQUAL -> cardVal >= target
            ComparisonOperator.NOT_EQUAL        -> cardVal != target
        }

    private fun rarityWeight(rarity: String) = when (rarity.lowercase()) {
        "mythic"   -> 4
        "rare"     -> 3
        "uncommon" -> 2
        else       -> 1
    }
}
