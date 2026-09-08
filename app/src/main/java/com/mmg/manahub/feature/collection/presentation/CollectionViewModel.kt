package com.mmg.manahub.feature.collection.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.domain.search.AdvancedSearchCardMatcher
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CollectionCardGroup
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.model.groupByCard
import com.mmg.manahub.core.model.groupCollection
import com.mmg.manahub.core.sync.CollectionMergeConflict
import com.mmg.manahub.core.sync.CollectionMergeConflictResolver
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.core.sync.MergeConflictResolution
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
class CollectionViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val getCollection: GetCollectionUseCase,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val workManager: WorkManager,
    private val migrateLocalTradeLists: MigrateLocalTradeListsUseCase,
    private val getLocalWishlist: GetLocalWishlistUseCase,
    private val wishlistRepository: WishlistRepository,
    private val openForTradeRepository: OpenForTradeRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val analyticsHelper: AnalyticsHelper,
    private val collectionMergeConflictResolver: CollectionMergeConflictResolver,
) : ViewModel() {

    val gridState = LazyGridState()
    val listState = LazyListState()

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    // Raw unfiltered collection from Room (non-deleted entries)
    private val _allCards = MutableStateFlow<List<UserCardWithCard>>(emptyList())

    // Broken-image fix (2026-07-17). Cached ENGLISH sibling (same setCode + collectorNumber) for
    // every distinct non-English printing currently in the raw collection, keyed by
    // (setCode, collectorNumber). Refreshed ONCE per raw collection emission (see
    // refreshEnglishSiblingCache), never per filter/sort pass — applyFilters() only does a pure
    // in-memory lookup against this map so re-filtering/re-sorting never triggers a Room query.
    private var englishSiblingCache: Map<Pair<String, String>, Card> = emptyMap()

    // The wishlist and open-for-trade tables are the ONLY sources of truth for those two lists:
    // they are separate tables, not flags on a collection row (`UserCard.isForTrade` is read-only
    // dead weight — nothing in the app ever writes it true). Held as full entries, not just ids,
    // because CollectionSource.WISHLIST / FOR_TRADE render them AS the list, not as a filter over
    // the owned collection (a wishlisted card is by definition usually not owned).
    private val _wishlistEntries = MutableStateFlow<List<WishlistEntry>>(emptyList())
    private val _openForTradeEntries = MutableStateFlow<List<OpenForTradeEntry>>(emptyList())

    // ViewModel-scoped field (not a local var) so it survives config changes.
    // Reset only on genuine Unauthenticated transitions, never on Loading, so
    // that token-refresh cycles (Authenticated → Loading → Authenticated) don't
    // re-trigger assignUserIdAndSync on every screen rotation.
    private var previouslyAuthenticated = false

    // Local unsynced counts for wishlist and open-for-trade, updated reactively.
    // Used alongside SyncManager.countPendingChanges so the sync banner reflects
    // ALL pending changes, not just the main collection.
    private var wishlistUnsyncedCount = 0
    private var openForTradeUnsyncedCount = 0

    // Backend & Performance Optimization plan, WS5c item 4 (2026-07-28): the search box used to
    // call applyFilters() synchronously on every keystroke — a filter+group+sort pass over the
    // whole collection running on Compose's Main thread per character typed. Raw keystrokes are
    // pushed here instead; the TextField's visible value still updates immediately via
    // `_uiState.searchQuery` in [onSearchQueryChange] (no input lag), but the expensive re-filter
    // is debounced (mirrors TradeProposalViewModel's `searchQueryFlow` §6.3 fix — same anti-pattern,
    // same established remedy in this codebase).
    private val searchQueryFlow = MutableStateFlow("")

    init {
        // Initialize tab from SavedStateHandle ("tab" nav arg)
        val tabArg = savedStateHandle.get<String>("tab")?.lowercase()
        val initialTab = when (tabArg) {
            "decks" -> CollectionTab.DECKS
            "trades" -> CollectionTab.TRADES
            else -> CollectionTab.CARDS
        }
        _uiState.update { it.copy(selectedTab = initialTab) }

        observeCollection()
        observeWishlist()
        observeOpenForTrade()
        observeTradeListUnsyncedCounts()
        // Backend & Performance Optimization plan, WS1+WS3 Part B item 7a (2026-07-28): the
        // per-screen-entry price refresh that used to run here was removed — it duplicated
        // `PriceRefreshWorker`'s daily, watermark-guarded, stale-only refresh with NO guard of its
        // own (every Collection open re-fetched the whole collection's prices unconditionally,
        // stacking on top of the login-window Scryfall burst). `PriceRefreshWorker` is now the
        // SOLE price-refresh path.
        observeSyncState()
        observeSessionChanges()
        observeUserPreferences()

        viewModelScope.launch {
            searchQueryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { applyFilters() }
        }
    }

    private companion object {
        /** Matches TradeProposalViewModel's SEARCH_DEBOUNCE_MS — one shared convention. */
        const val SEARCH_DEBOUNCE_MS = 300L

        // Wishlist entries carry these as nullable (a wishlist can want "any printing").
        const val DEFAULT_CONDITION = "NM"
        const val DEFAULT_LANGUAGE = "en"
    }

    private fun observeUserPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.collectionViewModeFlow.collect { mode ->
                _uiState.update { it.copy(viewMode = mode) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.collectionGroupingModeFlow.collect { mode ->
                _uiState.update { it.copy(groupingMode = mode) }
                applyFilters()
            }
        }
    }

    private fun observeWishlist() {
        viewModelScope.launch {
            getLocalWishlist().distinctUntilChanged().collect { entries ->
                _wishlistEntries.value = entries
                applyFilters()
            }
        }
    }

    private fun observeOpenForTrade() {
        viewModelScope.launch {
            openForTradeRepository.observeLocal().distinctUntilChanged().collect { entries ->
                _openForTradeEntries.value = entries
                applyFilters()
            }
        }
    }

    private fun observeTradeListUnsyncedCounts() {
        viewModelScope.launch {
            wishlistRepository.observeUnsyncedCount().distinctUntilChanged().collect { count ->
                wishlistUnsyncedCount = count
                recomputeUnsyncedBanner()
            }
        }
        viewModelScope.launch {
            openForTradeRepository.observeUnsyncedCount().distinctUntilChanged().collect { count ->
                openForTradeUnsyncedCount = count
                recomputeUnsyncedBanner()
            }
        }
    }

    private suspend fun recomputeUnsyncedBanner() {
        val userId = authRepository.getCurrentUser()?.id
        val hasPending = if (userId != null && syncManager.syncState.value != SyncState.SYNCING) {
            syncManager.countPendingChanges(userId) > 0
                    || wishlistUnsyncedCount > 0
                    || openForTradeUnsyncedCount > 0
        } else {
            wishlistUnsyncedCount > 0 || openForTradeUnsyncedCount > 0
        }
        _uiState.update { it.copy(hasUnsyncedChanges = hasPending) }
    }

    // ── Collection observation ────────────────────────────────────────────────

    private fun observeCollection() {
        viewModelScope.launch {
            getCollection()
                .distinctUntilChanged()
                .catch { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("collection_observe_failed")
                        setCustomKey("collection_size", _allCards.value.size)
                        recordException(e)
                    }
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { cards ->
                    _allCards.value = cards
                    refreshEnglishSiblingCache(cards)
                    applyFilters()
                    // Re-check pending changes on every collection emit, but skip the check
                    // while a sync is actively running to avoid a false-positive during the
                    // PULL phase (Room emits new rows before the watermark is saved).
                    val userId = authRepository.getCurrentUser()?.id
                    // Use SyncManager's StateFlow directly — it is the authoritative source
                    // and is set to SYNCING before any Room emissions from the PULL phase,
                    // closing the TOCTOU window between SyncManager emitting SYNCING and
                    // _uiState being updated by the separate observeSyncState coroutine.
                    val hasPending =
                        if (userId != null && syncManager.syncState.value != SyncState.SYNCING) {
                            syncManager.countPendingChanges(userId) > 0
                                    || wishlistUnsyncedCount > 0
                                    || openForTradeUnsyncedCount > 0
                        } else {
                            _uiState.value.hasUnsyncedChanges
                        }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasStaleCards = cards.any { c -> c.card.isStale },
                            hasUnsyncedChanges = hasPending,
                        )
                    }
                }
        }
    }

    /**
     * Broken-image fix (2026-07-17). Batch-reads (Room only, no network) the English sibling
     * printing for every distinct non-English (setCode, collectorNumber) pair present in [cards],
     * and replaces [englishSiblingCache] wholesale. Called once per raw collection emission — the
     * O(1)-per-load Room read that keeps [applyFilters]'s per-pass lookup pure in-memory.
     * Best-effort: a lookup failure just leaves the cache empty for this load (groups fall back to
     * their own representative's image, never a crash or blocked render).
     */
    private suspend fun refreshEnglishSiblingCache(cards: List<UserCardWithCard>) {
        val pairs = cards.asSequence()
            .filter { it.card.lang != "en" }
            .map { it.card.setCode to it.card.collectorNumber }
            .toSet()
        englishSiblingCache = if (pairs.isEmpty()) {
            emptyMap()
        } else {
            runCatching { cardRepository.getCachedEnglishSiblings(pairs) }.getOrDefault(emptyMap())
        }
    }

    /**
     * Broken-image fix (2026-07-17). For every non-English group, overrides ONLY the image fields
     * with its cached English sibling's (many non-English Scryfall printings have no native image;
     * the English printing of the same set + collector number always shares the same illustration
     * and is guaranteed to have one). Every other field (name, price, set, rarity...) keeps coming
     * from the actual representative printing. A group with no cached sibling yet (e.g. added
     * before this fix, or the save-time warm-cache fetch hasn't landed) keeps its own image — no
     * regression, no network call from this list-rendering path. Pure in-memory, O(groups).
     */
    private fun overrideForeignImages(groups: List<CollectionCardGroup>): List<CollectionCardGroup> =
        groups.map { group ->
            val card = group.card
            if (card.lang == "en") return@map group
            val englishSibling =
                englishSiblingCache[card.setCode to card.collectorNumber] ?: return@map group
            group.copy(
                card = card.copy(
                    imageNormal = englishSibling.imageNormal,
                    imageArtCrop = englishSibling.imageArtCrop,
                )
            )
        }

    /** Forwards [SyncManager.syncState] into the UI state and clears the banner on success. */
    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.update {
                    it.copy(
                        syncState = state,
                        // After a successful collection sync, hide the banner only if
                        // wishlist and open-for-trade are also fully synced.
                        hasUnsyncedChanges = if (state == SyncState.SUCCESS)
                            wishlistUnsyncedCount > 0 || openForTradeUnsyncedCount > 0
                        else it.hasUnsyncedChanges,
                    )
                }
                // Write-path hardening audit (Phase 7, 2026-09-06): a SUCCESS transition is the
                // natural moment a first-login assignUserIdAndSync (now WorkManager-driven, Phase
                // 5) could have just left a pending conflict behind — re-check here rather than
                // polling.
                if (state == SyncState.SUCCESS) {
                    checkForMergeConflicts()
                }
            }
        }
    }

    /**
     * Refreshes [CollectionUiState.pendingMergeConflicts] for the current user. Cheap no-op for
     * the overwhelming majority of users (zero guest/account collisions) — see
     * [CollectionMergeConflictResolver]'s KDoc for why this is a one-shot check rather than a
     * live Flow.
     */
    private fun checkForMergeConflicts() {
        val userId = (uiState.value.sessionState as? SessionState.Authenticated)?.user?.id ?: return
        viewModelScope.launch {
            val conflicts = collectionMergeConflictResolver.getPendingConflicts(userId)
            if (conflicts.isEmpty()) {
                _uiState.update { it.copy(pendingMergeConflicts = emptyList()) }
                return@launch
            }
            val cardsById = cardRepository
                .getCardsByIds(conflicts.map { it.guestRow.scryfallId }.distinct())
                .associateBy { it.scryfallId }
            _uiState.update {
                it.copy(
                    pendingMergeConflicts = conflicts.map { conflict ->
                        val card = cardsById[conflict.guestRow.scryfallId]
                        MergeConflictUiItem(
                            conflict = conflict,
                            cardName = card?.name ?: conflict.guestRow.scryfallId,
                            imageUrl = card?.imageArtCrop,
                        )
                    },
                )
            }
        }
    }

    /**
     * Resolves one pending [CollectionMergeConflict] per the user's explicit [resolution] choice
     * and refreshes the conflict list. Dismissing the sheet without calling this loses nothing —
     * the conflict simply remains pending for next time.
     */
    fun onResolveMergeConflict(conflict: CollectionMergeConflict, resolution: MergeConflictResolution) {
        viewModelScope.launch {
            collectionMergeConflictResolver.resolve(conflict, resolution)
            _uiState.update { state ->
                state.copy(
                    pendingMergeConflicts = state.pendingMergeConflicts.filterNot { it.conflict == conflict },
                )
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
                            // Set the flag BEFORE using it so a rapid second Authenticated
                            // emit (profile enrichment) doesn't fire a second migration.
                            previouslyAuthenticated = true
                            // Collection sync data-loss fix, Phase 5 (2026-09-06): the
                            // offline-to-online first-login full pull is no longer launched here
                            // on viewModelScope (navigating away from this screen used to cancel
                            // it mid-flight). It is now dispatched app-wide, as durable
                            // WorkManager unique work, from the app-scoped session observer in
                            // ManaHubApp.kt — see CollectionSyncWorker.enqueueFirstLoginSync.
                            // This ViewModel only observes syncManager.syncState (below).
                            triggerTradeListMigration(state.user.id)
                        }
                    }

                    is SessionState.Unauthenticated -> {
                        // Cancel background sync — stale/missing session would cause
                        // Supabase RPC failures if the worker ran now.
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_ONE_TIME)
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_FIRST_LOGIN)
                        previouslyAuthenticated = false
                        // Clear any sync error so a stale ERROR from a previous background
                        // sync is not shown as fresh on the next app open or login.
                        syncManager.resetSyncState()
                        // Also clear hasUnsyncedChanges — a logged-out user must never see
                        // the sync banner, and stale true would persist into the next login.
                        _uiState.update {
                            it.copy(
                                syncState = SyncState.IDLE,
                                syncError = null,
                                hasUnsyncedChanges = false
                            )
                        }
                    }

                    is SessionState.Loading -> { /* no-op — wait for final state */
                    }
                }
            }
        }
    }

    // ── Sync user action ──────────────────────────────────────────────────────

    /**
     * Triggers a one-shot full sync (push + pull) for the current user.
     *
     * Collection sync data-loss fix, Phase 5 (2026-09-06): dispatched as durable WorkManager
     * unique work ([CollectionSyncWorker.enqueueOneTimeSync]) instead of an inline
     * `syncManager.sync(userId)` call on `viewModelScope` — the previous inline call was
     * cancelled if the user navigated away from this screen mid-sync. The UI already observes
     * [SyncManager.syncState] (SYNCING → SUCCESS/ERROR) via [observeSyncState]; this function only
     * kicks the work off and no longer awaits a [SyncManager.SyncResult] directly, so `syncError`
     * text now comes only from the trade-list migration below (the collection-sync outcome itself
     * is still fully visible via `uiState.syncState`).
     */
    fun onSync() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            _uiState.update { it.copy(syncError = null) }

            // Drives syncState SYNCING → SUCCESS/ERROR, observed via observeSyncState().
            CollectionSyncWorker.enqueueOneTimeSync(workManager)

            // Migrate any wishlist/open-for-trade entries added while offline. Independent of the
            // collection sync above (different tables/RPCs, no ordering requirement between them).
            // Capture the migration error so it can be surfaced to the UI — previously the Result
            // was discarded, silently leaving the banner stuck when Supabase rejected the batch
            // insert (e.g. network timeout or RLS violation).
            val migrationError: String? =
                if (wishlistUnsyncedCount > 0 || openForTradeUnsyncedCount > 0) {
                    migrateLocalTradeLists(userId)
                        .onFailure { e ->
                            FirebaseCrashlytics.getInstance().apply {
                                log("trade_lists_migration_failed_on_sync")
                                recordException(e)
                            }
                        }
                        .exceptionOrNull()
                        ?.message
                } else {
                    null
                }

            _uiState.update { it.copy(syncError = migrationError) }

            // Re-evaluate the banner after migration has finished (success or failure).
            // observeSyncState() already set hasUnsyncedChanges = (wishlistCount > 0)
            // when SUCCESS fired, but at that point migration had not run yet.
            // This call reconciles the flag with the actual Room counters post-migration.
            recomputeUnsyncedBanner()
        }
    }

    /** Dismisses the sync status snackbar/banner. */
    fun onSyncDismissed() {
        _uiState.update { it.copy(syncState = SyncState.IDLE, syncError = null) }
    }

    // ── User actions ──────────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    fun onSortChange(sort: SortOrder) {
        _uiState.update { it.copy(sortOrder = sort) }
        analyticsHelper.logEvent("collection_sort_changed", mapOf("sort_order" to sort.name))
        applyFilters()
        viewModelScope.launch {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        }
    }

    fun onSortDirectionChange(dir: SortDirection) {
        _uiState.update { it.copy(sortDirection = dir) }
        analyticsHelper.logEvent("collection_sort_direction_changed", mapOf("sort_direction" to dir.name))
        applyFilters()
        viewModelScope.launch {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        }
    }

    /**
     * Changes the Cards tab "Group by" selection. Updates local state immediately (so the
     * selector and sectioned list reflect the choice without waiting on the DataStore
     * round-trip — mirrors [onSortChange], unlike [onViewModeToggle]'s launch-only shape, since
     * grouping needs [CollectionUiState.sections] recomputed right away), persists the choice,
     * then re-runs [applyFilters] to rebuild [CollectionUiState.sections].
     */
    fun onGroupingChange(mode: CollectionGroupingMode) {
        _uiState.update { it.copy(groupingMode = mode) }
        analyticsHelper.logEvent("collection_grouping_changed", mapOf("grouping_mode" to mode.name))
        viewModelScope.launch { userPreferencesRepository.saveCollectionGroupingMode(mode) }
        applyFilters()
        viewModelScope.launch {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        }
    }

    fun onViewModeToggle() {
        viewModelScope.launch {
            val newMode = if (_uiState.value.viewMode == CollectionViewMode.GRID) {
                CollectionViewMode.LIST
            } else {
                CollectionViewMode.GRID
            }
            analyticsHelper.logEvent(
                "collection_view_mode_toggled",
                mapOf("new_mode" to newMode.name)
            )
            userPreferencesRepository.saveCollectionViewMode(newMode)
        }
    }

    fun onTabSelected(tab: CollectionTab) {
        if (_uiState.value.selectedTab == tab) return
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
                        analyticsHelper.logEvent(
                            "trade_lists_migrated",
                            mapOf("migrated_count" to count)
                        )
                        _uiState.update { it.copy(snackbarMessage = count.toString()) }
                    }
                }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_list_migration_failed")
                        recordException(e)
                    }
                }
        }
    }

    fun applyAdvancedFilters(query: AdvancedSearchQuery) {
        val source = query.criteria.filterIsInstance<SearchCriterion.CollectionStatus>()
            .firstOrNull()?.source ?: CollectionSource.COLLECTION
        val sourceChanged = source != _uiState.value.collectionSource
        _uiState.update {
            it.copy(
                activeQuery = if (query.isEmpty()) null else query,
                collectionSource = source,
            )
        }
        if (sourceChanged) setCollectionSourceTelemetry(source)
        if (!query.isEmpty()) {
            analyticsHelper.logEvent(
                "collection_advanced_filter_applied", mapOf(
                    "criteria_count" to query.criteria.size,
                )
            )
            FirebaseCrashlytics.getInstance()
                .setCustomKey("collection_active_filters_count", query.criteria.size)
        }
        applyFilters()
    }

    fun clearAdvancedFilters() {
        setCollectionSource(CollectionSource.COLLECTION)
        _uiState.update { it.copy(activeQuery = null) }
        applyFilters()
    }

    /** Switches which of the user's three lists the Cards tab is showing. */
    fun setCollectionSource(source: CollectionSource) {
        if (_uiState.value.collectionSource == source) return
        _uiState.update {
            it.copy(
                collectionSource = source,
                // Keep activeQuery's CollectionStatus criterion in lockstep with collectionSource
                // so the two can never disagree — applyAdvancedFilters derives one FROM the other.
                activeQuery = it.activeQuery.withCollectionSource(source),
            )
        }
        setCollectionSourceTelemetry(source)
        applyFilters()
    }

    private fun AdvancedSearchQuery?.withCollectionSource(source: CollectionSource): AdvancedSearchQuery? {
        val withoutStatus = (this?.criteria ?: emptyList())
            .filterNot { it is SearchCriterion.CollectionStatus }
        val criteria = if (source == CollectionSource.COLLECTION) withoutStatus
            else withoutStatus + SearchCriterion.CollectionStatus(source)
        return if (criteria.isEmpty()) null else (this ?: AdvancedSearchQuery()).copy(criteria = criteria)
    }

    private fun setCollectionSourceTelemetry(source: CollectionSource) {
        analyticsHelper.logEvent("collection_source_changed", mapOf("source" to source.name))
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("collection_source", source.name)
        // Recomputed once per source switch, not per applyFilters() pass, so a stale count from a
        // previous source never lingers after switching back to COLLECTION.
        crashlytics.setCustomKey("collection_source_uncached_rows", uncachedRowCountFor(source))
    }

    private fun uncachedRowCountFor(source: CollectionSource): Int = when (source) {
        CollectionSource.COLLECTION -> 0
        CollectionSource.WISHLIST -> _wishlistEntries.value.count { it.card == null }
        CollectionSource.FOR_TRADE -> _openForTradeEntries.value.count { it.card == null }
    }

    /**
     * The tag universe offered by the tag picker — derived from the ACTIVE source's rows, not the
     * owned collection, so filtering the wishlist never offers a tag no wishlist card carries (nor
     * hides one only a wishlist card does).
     */
    fun getAllCollectionTags(): Set<com.mmg.manahub.core.model.CardTag> {
        return baseListFor(_uiState.value.collectionSource)
            .flatMap { it.card.tags + it.card.userTags }
            .distinctBy { it.key }
            .toSet()
    }

    // ── Filtering & sorting ───────────────────────────────────────────────────

    private fun applyFilters() {
        val state = _uiState.value
        var result = baseListFor(state.collectionSource)

        // Text search
        if (state.searchQuery.isNotBlank()) {
            result = result.filter {
                it.card.name.contains(state.searchQuery, ignoreCase = true)
            }
        }

        // Advanced criteria
        state.activeQuery?.let { query ->
            if (!query.isEmpty()) {
                // Computed once per pass, not per (card, criterion) pair — avoids an O(cards x
                // criteria x wishlistSize) linear scan on every applyFilters() call.
                val wishlistIds = _wishlistEntries.value.mapTo(HashSet()) { it.cardId }
                val forTradeIds = _openForTradeEntries.value.mapTo(HashSet()) { it.scryfallId }
                result = result.filter { card ->
                    query.criteria.all { criterion ->
                        matchesCriterion(card, criterion, wishlistIds, forTradeIds)
                    }
                }
            }
        }

        // Group copies of the same card into one entry, then override each non-English group's
        // image with its cached English sibling's (broken-image fix, 2026-07-17).
        val grouped = overrideForeignImages(result.groupByCard())

        // Sort
        val sorted = when (state.sortOrder) {
            SortOrder.NAME -> {
                if (state.sortDirection == SortDirection.ASC) grouped.sortedBy { it.card.name }
                else grouped.sortedByDescending { it.card.name }
            }
            SortOrder.PRICE -> {
                if (state.sortDirection == SortDirection.ASC) {
                    grouped.sortedWith(compareBy<CollectionCardGroup> {
                        it.card.priceUsd ?: 0.0
                    }.thenBy { it.card.name })
                } else {
                    grouped.sortedWith(compareByDescending<CollectionCardGroup> {
                        it.card.priceUsd ?: 0.0
                    }.thenBy { it.card.name })
                }
            }
            SortOrder.RARITY -> {
                if (state.sortDirection == SortDirection.ASC) {
                    grouped.sortedWith(compareBy<CollectionCardGroup> {
                        rarityWeight(it.card.rarity)
                    }.thenBy { it.card.name })
                } else {
                    grouped.sortedWith(compareByDescending<CollectionCardGroup> {
                        rarityWeight(it.card.rarity)
                    }.thenBy { it.card.name })
                }
            }
            SortOrder.DATE_ADDED -> {
                if (state.sortDirection == SortDirection.ASC) {
                    grouped.sortedWith(compareBy<CollectionCardGroup> { it.latestAddedAt }.thenBy { it.card.name })
                } else {
                    grouped.sortedWith(compareByDescending<CollectionCardGroup> { it.latestAddedAt }.thenBy { it.card.name })
                }
            }
        }

        val sections = if (state.groupingMode == CollectionGroupingMode.NONE) {
            emptyList()
        } else {
            groupCollection(sorted, state.groupingMode)
        }

        _uiState.update {
            it.copy(
                cards = sorted,
                sections = sections,
                // Recomputed on every pass, not only on a source switch: the wishlist/trade Room
                // flows emit again as hydration lands, and a stale count would keep the "still
                // loading" notice up after the last row became renderable.
                uncachedSourceRows = uncachedRowCountFor(state.collectionSource),
            )
        }
    }

    /**
     * The rows the Cards tab is filtering, chosen by [CollectionSource].
     *
     * [CollectionSource.WISHLIST] / [CollectionSource.FOR_TRADE] project their own table's entries
     * into synthetic [UserCardWithCard] rows so every downstream stage (text search, criteria,
     * grouping, sorting, sections) runs unchanged. Those rows are READ-ONLY by construction: their
     * `UserCard.id` is the wishlist/trade entry's id, not a `user_card_collection` row id, so they
     * must never reach a collection write. Nothing on this screen mutates a row today (the grid and
     * list expose only `onCardClick`, which navigates by `scryfallId`) — keep it that way, or gate
     * the new action on `collectionSource == COLLECTION`.
     *
     * An entry whose card is not cached locally cannot be rendered and is skipped; the count is
     * reported rather than silently swallowed.
     */
    private fun baseListFor(source: CollectionSource): List<UserCardWithCard> = when (source) {
        CollectionSource.COLLECTION -> _allCards.value
        CollectionSource.WISHLIST -> _wishlistEntries.value.projectToRows { entry ->
            entry.card?.let { card ->
                UserCardWithCard(
                    userCard = UserCard(
                        id = entry.id,
                        scryfallId = entry.cardId,
                        quantity = entry.quantity,
                        isFoil = entry.isFoil,
                        condition = entry.condition ?: DEFAULT_CONDITION,
                        language = entry.language ?: DEFAULT_LANGUAGE,
                        isForTrade = false,
                        createdAt = entry.createdAt,
                        updatedAt = entry.createdAt,
                    ),
                    card = card,
                )
            }
        }
        CollectionSource.FOR_TRADE -> _openForTradeEntries.value.projectToRows { entry ->
            entry.card?.let { card ->
                UserCardWithCard(
                    userCard = UserCard(
                        id = entry.id,
                        scryfallId = entry.scryfallId,
                        quantity = entry.quantity,
                        isFoil = entry.isFoil,
                        condition = entry.condition,
                        language = entry.language,
                        isForTrade = true,
                        createdAt = entry.createdAt,
                        updatedAt = entry.createdAt,
                    ),
                    card = card,
                )
            }
        }
    }

    // Uncached-row count is reported via setCollectionSourceTelemetry (on source switch), not here.
    private fun <T> List<T>.projectToRows(map: (T) -> UserCardWithCard?): List<UserCardWithCard> =
        mapNotNull(map)

    // Strict mode: an unmatchable facet (a CardFunction with no local CardTag mapping) legitimately
    // means "no local card qualifies" here, unlike Deck Studio's lenient Collection tab.
    private fun matchesCriterion(
        card: UserCardWithCard,
        criterion: SearchCriterion,
        wishlistIds: Set<String>,
        forTradeIds: Set<String>,
    ): Boolean =
        AdvancedSearchCardMatcher.matchesCriterion(
            card = card.card,
            criterion = criterion,
            isWishlisted = card.userCard.scryfallId in wishlistIds,
            // Never `userCard.isForTrade`: that column is never written true anywhere in the app.
            isForTrade = card.userCard.scryfallId in forTradeIds,
        )

    private fun rarityWeight(rarity: String) = when (rarity.lowercase()) {
        "mythic" -> 4
        "rare" -> 3
        "uncommon" -> 2
        else -> 1
    }
}
