package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.communitydecks.domain.usecase.SearchCommunityDecksUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Card-name search debounce window shared by the Commander and Card advanced-search pickers. */
private const val CARD_PICKER_DEBOUNCE_MS = 400L

/** Minimum query length before a card-picker search fires. */
private const val CARD_PICKER_MIN_LENGTH = 2

/** Page size for every Discover section row (kept small — Archidekt caps a page at 60 anyway). */
private const val DISCOVER_SECTION_SIZE = 10

/** Page size for a Search-tab page. */
private const val SEARCH_PAGE_SIZE = 20

/**
 * ViewModel for the Community Decks Hub (Discover + Search).
 *
 * Drives a paged Archidekt search ([SearchCommunityDecksUseCase]) by deck name + advanced filters
 * (Discover/Search overhaul 2026-07-15), and a multi-section Discover feed. Both the landing
 * ([com.mmg.manahub.app.navigation.Screen.CommunityDecks]) and the "decks containing a card"
 * deep-link ([com.mmg.manahub.app.navigation.Screen.CommunityDecksByCard]) routes share this
 * ViewModel; the latter resolves [initialCardName] into the CARD advanced filter and auto-runs.
 *
 * One-shot navigation / error effects are delivered through a buffered [Channel].
 */
class CommunityDecksSearchViewModel(
    savedStateHandle: SavedStateHandle,
    private val searchCommunityDecks: SearchCommunityDecksUseCase,
    private val userPreferences: UserPreferencesDataStore,
    private val communityAggregateRepository: CommunityAggregateRepository,
    private val cardRepository: CardRepository,
    private val searchCards: SearchCardsUseCase,
) : ViewModel() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    /** Pre-filled card name from the CommunityDecksByCard route (Uri-decoded by the nav arg). */
    private val initialCardName: String? = savedStateHandle.get<String>("cardName")

    /** The ByCard deep-link ALWAYS lands on Search (route contract unchanged, plan D8/Phase 5). */
    private val openedViaByCardDeepLink: Boolean = !initialCardName.isNullOrBlank()

    private val _uiState = MutableStateFlow(
        CommunityDecksSearchUiState(
            hubTab = if (openedViaByCardDeepLink) CommunityHubTab.SEARCH else CommunityHubTab.DISCOVER,
        ),
    )
    val uiState: StateFlow<CommunityDecksSearchUiState> = _uiState.asStateFlow()

    private val _events = Channel<CommunityDecksSearchEvent>(Channel.BUFFERED)
    val events: Flow<CommunityDecksSearchEvent> = _events.receiveAsFlow()

    private var currentPage = 1
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var discoverJob: Job? = null
    private var discoverLoaded = false

    /** Debounced input flows for the two advanced-search card pickers (Commander / Card). */
    private val commanderQueryFlow = MutableStateFlow("")
    private val cardQueryFlow = MutableStateFlow("")

    init {
        crashlytics.log("screen_viewed: community_decks_search")
        crashlytics.setCustomKey("community_search_prefilled", openedViaByCardDeepLink)

        if (openedViaByCardDeepLink) {
            resolveDeepLinkCardAndSearch(initialCardName!!)
        }

        // Phase 5 Community Hub: mirror the D4 flag into state; a lazy Discover load fires only
        // once (guarded by `discoverLoaded`) the FIRST time it becomes true AND the landing tab is
        // actually Discover (the ByCard deep-link never needs it unless the user manually switches
        // tabs — [onSelectHubTab] covers that case).
        viewModelScope.launch {
            userPreferences.communityEngineEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(discoverEnabled = enabled) }
                if (enabled && !openedViaByCardDeepLink && !discoverLoaded) loadDiscover()
            }
        }

        viewModelScope.launch {
            // `distinctUntilChanged()` runs BEFORE `filter` (bug fix) so a blank reset — e.g.
            // `onClearAdvancedFilters`'s `commanderQueryFlow.value = ""` — still advances
            // distinctUntilChanged's "last seen" baseline even though the blank value itself never
            // reaches `runCardPickerSearch`. With the filter first (the original order), a blank
            // emission was dropped BEFORE distinctUntilChanged ever saw it, so the baseline stayed
            // on the last real query — re-entering that EXACT same query right after a clear was
            // silently suppressed as a "duplicate", leaving the picker's results stuck empty.
            commanderQueryFlow
                .debounce(CARD_PICKER_DEBOUNCE_MS)
                .distinctUntilChanged()
                .filter { it.length >= CARD_PICKER_MIN_LENGTH }
                .collectLatest { query -> runCardPickerSearch(query, isCommander = true) }
        }
        viewModelScope.launch {
            cardQueryFlow
                .debounce(CARD_PICKER_DEBOUNCE_MS)
                .distinctUntilChanged()
                .filter { it.length >= CARD_PICKER_MIN_LENGTH }
                .collectLatest { query -> runCardPickerSearch(query, isCommander = false) }
        }
    }

    /**
     * The ByCard deep-link now resolves [name] into the Search tab's CARD advanced filter (rather
     * than the plain-text query bar) and auto-runs. If Scryfall resolution fails (unknown/renamed
     * card), falls back to the old behaviour of searching the deck-NAME bar with the raw text so
     * the deep-link never dead-ends.
     */
    private fun resolveDeepLinkCardAndSearch(name: String) {
        viewModelScope.launch {
            val resolved = runCatching { cardRepository.getCardByExactName(name) }
                .getOrNull()
                ?.getOrNull()

            _uiState.update {
                if (resolved != null) {
                    // Deep-link REPLACES the CARD filter (never appends) — this is the entry
                    // point's own single-card intent, not an addition to a pre-existing selection.
                    it.copy(advancedFilters = it.advancedFilters.copy(cards = listOf(resolved)))
                } else {
                    it.copy(query = name)
                }
            }
            search()
        }
    }

    /** Switches the active [CommunityHubTab], lazily loading Discover data the first time it's shown. */
    fun onSelectHubTab(tab: CommunityHubTab) {
        _uiState.update { it.copy(hubTab = tab) }
        if (tab == CommunityHubTab.DISCOVER && !discoverLoaded && _uiState.value.discoverEnabled) loadDiscover()
    }

    /**
     * Fetches every Discover section IN PARALLEL: trending commanders/cards (resolved to full
     * [Card]s for image tiles), popular/recent/recently-updated/primer decks, and a weekly-rotating
     * featured non-Commander format. Runs at most once per ViewModel instance ([discoverLoaded])
     * on the FIRST call, but [onSelectDiscoveryFormat] re-invokes it on every format switch — the
     * previous in-flight [discoverJob] is cancelled first (bug fix) so a slower response for a
     * format the user already switched away from can never land after, and overwrite, the newer
     * format's results (the same stale-response race [search] already guarded against via
     * [searchJob]). Each section fails INDEPENDENTLY (`runCatching`) and simply renders
     * empty/hidden — [CommunityDecksSearchUiState.discoverUnavailable] is only set when literally
     * EVERY section came back empty, never for a single degraded source.
     */
    private fun loadDiscover() {
        discoverLoaded = true
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            _uiState.update { it.copy(isDiscoverLoading = true) }

            val selectedFormat = uiState.value.selectedDiscoveryFormat

            val trendingDeferred = async { runCatching { communityAggregateRepository.getTrending() }.getOrNull() }
            val popularDeferred = async { fetchDecks(orderBy = CommunityDeckSort.POPULAR.apiValue, deckFormat = selectedFormat) }
            val recentDeferred = async { fetchDecks(orderBy = CommunityDeckSort.RECENT.apiValue, deckFormat = selectedFormat) }
            val updatedDeferred = async { fetchDecks(orderBy = CommunityDeckSort.UPDATED.apiValue, deckFormat = selectedFormat) }
            val primersDeferred = async {
                fetchDecks(orderBy = CommunityDeckSort.POPULAR.apiValue, primersOnly = true, deckFormat = selectedFormat)
            }

            val trendingSnapshot = (trendingDeferred.await() as? DataResult.Success)?.data
            val commanderCards = resolveTrendingCards(trendingSnapshot?.topCommanders.orEmpty().map { it.name })
            val cardCards = resolveTrendingCards(trendingSnapshot?.topCards.orEmpty().map { it.name })

            val popularDecks = popularDeferred.await()
            val recentDecks = recentDeferred.await()
            val updatedDecks = updatedDeferred.await()
            val primerDecks = primersDeferred.await()

            val allEmpty = commanderCards.isEmpty() && cardCards.isEmpty() && popularDecks.isEmpty() &&
                recentDecks.isEmpty() && updatedDecks.isEmpty() && primerDecks.isEmpty()
            if (allEmpty) crashlytics.log("community_discover_all_failed")

            _uiState.update {
                it.copy(
                    isDiscoverLoading = false,
                    discoverUnavailable = allEmpty,
                    trendingCommanderCards = commanderCards,
                    trendingCardCards = cardCards,
                    popularDecks = popularDecks,
                    recentDecks = recentDecks,
                    updatedDecks = updatedDecks,
                    primerDecks = primerDecks,
                    selectedDiscoveryFormat = selectedFormat,
                )
            }
        }
    }

    /** One Discover section's decks; any failure (exception or [DataResult.Error]) degrades to empty. */
    private suspend fun fetchDecks(
        orderBy: String,
        deckFormat: CommunityDeckFormatFilter = CommunityDeckFormatFilter.COMMANDER,
        primersOnly: Boolean = false,
    ): List<CommunityDeckSummary> = runCatching {
        val filters = CommunityAdvancedFilters(
            formats = deckFormat,
            primersOnly = primersOnly,
        ).toSearchFilters(deckName = null, orderBy = orderBy, page = 1, pageSize = DISCOVER_SECTION_SIZE)
        (searchCommunityDecks(filters) as? DataResult.Success)?.data?.decks.orEmpty()
    }.getOrElse { emptyList() }

    /** Resolves trending card/commander names to full [Card]s in parallel; unresolved names are dropped. */
    private suspend fun resolveTrendingCards(names: List<String>): List<Card> = coroutineScope {
        names.take(DISCOVER_SECTION_SIZE)
            .map { name -> async { runCatching { cardRepository.getCardByExactName(name) }.getOrNull()?.getOrNull() } }
            .awaitAll()
            .filterNotNull()
    }



    /** A trending commander tile tapped in Discover — switches to Search with the COMMANDER filter set. */
    fun onTrendingCommanderClick(card: Card) {
        crashlytics.log("community_discover_term_click")
        _uiState.update {
            it.copy(hubTab = CommunityHubTab.SEARCH, advancedFilters = it.advancedFilters.copy(commander = card))
        }
        search()
    }

    /**
     * A trending card tile tapped in Discover — switches to Search with the CARD filter set to
     * ONLY this card (replaces, never appends — mirrors the ByCard deep-link's replace semantics;
     * a Discover tap is a fresh single-card intent, not an addition to a stale selection).
     */
    fun onTrendingCardClick(card: Card) {
        crashlytics.log("community_discover_term_click")
        _uiState.update {
            it.copy(hubTab = CommunityHubTab.SEARCH, advancedFilters = it.advancedFilters.copy(cards = listOf(card)))
        }
        search()
    }

    fun onQueryChange(query: String) {
        // Only ever updates the raw text — never touches `results`/`hasSearched`, so editing the
        // field can never blank an already-rendered result set (Discover/Search overhaul fix).
        _uiState.update { it.copy(query = query) }
    }

    /** Re-runs the search if one was already issued (so the new sort applies live). */
    fun onSortUpdated(sort: CommunityDeckSort) {
        _uiState.update { it.copy(selectedSort = sort) }
        if (_uiState.value.hasSearched) search()
    }

    fun onSearchDeckFilterUpdated(format: CommunityDeckFormatFilter) {
        _uiState.update { it.copy(advancedFilters = it.advancedFilters.copy(formats = format)) }
        if (_uiState.value.hasSearched) search()
    }

    fun onSelectDiscoveryFormat(format: CommunityDeckFormatFilter){
        if (_uiState.value.selectedDiscoveryFormat == format){
            return
        } else {
            _uiState.update { it.copy(selectedDiscoveryFormat = format) }
            loadDiscover()
        }

    }
    // ── Advanced search filters (Phase 2) ───────────────────────────────────────────
    // NOTE: the advanced-search sheet's format selection is driven by `onSearchDeckFilterUpdated`
    // (below, wired to the ManaHubBottomSheetSelector in the Search body) — a separate
    // `onFormatFilterSelected` used to exist here wired to a `CommunityAdvancedSearchSheet` format
    // callback that the sheet itself never actually invoked (dead code, removed in the same pass
    // as this note).

    fun onColorToggled(color: String) {
        _uiState.update {
            val current = it.advancedFilters.colors
            val next = if (current.contains(color)) current - color else current + color
            it.copy(advancedFilters = it.advancedFilters.copy(colors = next))
        }
    }

    fun onBracketSelected(bracket: Int?) {
        _uiState.update { it.copy(advancedFilters = it.advancedFilters.copy(edhBracket = bracket)) }
    }

    fun onUsernameChanged(username: String) {
        _uiState.update { it.copy(advancedFilters = it.advancedFilters.copy(ownerUsername = username)) }
    }

    fun onDeckSizeChanged(size: String) {
        // Free-text only; parsed to Int? at request-build time (never build a partial filter here).
        _uiState.update { it.copy(advancedFilters = it.advancedFilters.copy(deckSize = size)) }
    }

    fun onPrimersOnlyToggled(primersOnly: Boolean) {
        _uiState.update { it.copy(advancedFilters = it.advancedFilters.copy(primersOnly = primersOnly)) }
    }

    fun onCommanderQueryChange(query: String) {
        _uiState.update { it.copy(commanderQuery = query) }
        commanderQueryFlow.value = query
        if (query.isBlank()) _uiState.update { it.copy(commanderResults = emptyList()) }
    }

    fun onCardQueryChange(query: String) {
        _uiState.update { it.copy(cardQuery = query) }
        cardQueryFlow.value = query
        if (query.isBlank()) _uiState.update { it.copy(cardResults = emptyList()) }
    }

    private suspend fun runCardPickerSearch(query: String, isCommander: Boolean) {
        _uiState.update {
            if (isCommander) it.copy(isCommanderSearching = true) else it.copy(isCardSearching = true)
        }
        val results = (searchCards(query, page = 1) as? DataResult.Success)?.data?.cards.orEmpty()
        _uiState.update {
            if (isCommander) it.copy(commanderResults = results, isCommanderSearching = false)
            else it.copy(cardResults = results, isCardSearching = false)
        }
    }

    fun onCommanderSelected(card: Card) {
        _uiState.update {
            it.copy(
                advancedFilters = it.advancedFilters.copy(commander = card),
                commanderQuery = "",
                commanderResults = emptyList(),
            )
        }
    }

    fun onCommanderCleared() {
        _uiState.update { it.copy(advancedFilters = it.advancedFilters.copy(commander = null)) }
    }

    /**
     * Appends [card] to the CARD advanced filter (Archidekt multi-card search expansion,
     * 2026-07-24), capped at [MAX_COMMUNITY_CARD_FILTERS] and deduped by name. Selecting a card
     * already in the list, or attempting to add past the cap, is a silent no-op — the sheet hides
     * the picker once the cap is reached (see `CommunityAdvancedSearchSheet`), so this is
     * defense-in-depth, not the primary UX gate.
     */
    fun onCardFilterSelected(card: Card) {
        _uiState.update {
            val current = it.advancedFilters.cards
            val alreadySelected = current.any { existing -> existing.name == card.name }
            val next = if (alreadySelected || current.size >= MAX_COMMUNITY_CARD_FILTERS) current else current + card
            it.copy(
                advancedFilters = it.advancedFilters.copy(cards = next),
                cardQuery = "",
                cardResults = emptyList(),
            )
        }
    }

    /** Removes one [card] from the CARD advanced filter (replaces the old single-card clear). */
    fun onCardFilterRemoved(card: Card) {
        _uiState.update {
            it.copy(advancedFilters = it.advancedFilters.copy(cards = it.advancedFilters.cards - card))
        }
    }

    fun onClearAdvancedFilters() {
        _uiState.update {
            it.copy(
                advancedFilters = CommunityAdvancedFilters(),
                commanderQuery = "",
                commanderResults = emptyList(),
                cardQuery = "",
                cardResults = emptyList(),
            )
        }
        // Bug fix: also reset the backing debounce flows, not just the visible uiState text. A
        // MutableStateFlow conflates equal values and runCardPickerSearch's pipeline applies
        // distinctUntilChanged — leaving these at their pre-clear value meant re-entering the EXACT
        // same query text right after clearing (e.g. a paste) silently produced no new emission, so
        // the picker never re-searched even though the visible query field was blank a moment ago.
        commanderQueryFlow.value = ""
        cardQueryFlow.value = ""
    }

    /** Applies the advanced filters (called from the sheet's Search button) and runs a fresh search. */
    fun onApplyAdvancedFilters() {
        search()
    }

    // ── Search ───────────────────────────────────────────────────────────────────

    /**
     * Runs a fresh search (page 1) combining the deck-name query bar with the applied advanced
     * filters. No-ops only when BOTH the query is blank AND no advanced filter is active — a blank
     * query with active filters (e.g. "commander = Krenko, Mob Boss") still searches.
     *
     * Also cancels any in-flight [loadMoreJob] (bug fix): without this, a [loadMore] fetch for the
     * PREVIOUS query/filters could complete after this fresh search's own result lands and append
     * its now-stale page onto the new (unrelated) result set via `it.results + result.data.decks`.
     */
    fun search() {
        val state = _uiState.value
        val deckName = state.query.trim()
        val filters = state.advancedFilters
        if (deckName.isBlank() && filters.activeCount == 0) return

        searchJob?.cancel()
        loadMoreJob?.cancel()
        currentPage = 1

        searchJob = viewModelScope.launch {
            // `isLoadingMore = false` guards against a stuck "loading more" spinner: the
            // `loadMoreJob` just cancelled above may have already flipped it to `true` and, being
            // cancelled, will never reach its own completion update to flip it back.
            _uiState.update { it.copy(isLoading = true, error = null, hasSearched = true, isLoadingMore = false) }

            crashlytics.setCustomKey("community_search_format", filters.formats.apiId)
            crashlytics.setCustomKey("community_search_sort", state.selectedSort.name)
            crashlytics.setCustomKey("community_search_query_len", deckName.length)
            // NOTE (Archidekt multi-card search expansion, 2026-07-24): the semantics of
            // `activeCount` shifted here — each selected card now counts individually (see
            // CommunityAdvancedFilters.activeCount KDoc), so this key's value can jump by more
            // than 1 per user action. `community_search_card_filter_count` below is the
            // card-selection-only signal for anyone reading the dashboard who needs to
            // disambiguate the two.
            crashlytics.setCustomKey("community_search_active_filters", filters.activeCount)
            crashlytics.setCustomKey("community_search_card_filter_count", filters.cards.size)

            val dataFilters = filters.toSearchFilters(
                deckName = deckName,
                orderBy = state.selectedSort.apiValue,
                page = 1,
                pageSize = SEARCH_PAGE_SIZE,
            )

            when (val result = searchCommunityDecks(dataFilters)) {
                is DataResult.Success -> {
                    crashlytics.setCustomKey("community_search_result_count", result.data.totalCount)
                    crashlytics.log("community_search_success")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = result.data.decks,
                            totalCount = result.data.totalCount,
                            hasMore = result.data.hasMore,
                            error = null,
                        )
                    }
                }
                is DataResult.Error -> {
                    crashlytics.log("community_search_error: ${result.message.take(80)}")
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    /**
     * Appends the next page of results. No-ops when already loading more or no more pages.
     *
     * Tracks its own [loadMoreJob] (bug fix) so a fresh [search] can cancel an in-flight
     * `loadMore` fetch for the query/filters that were active before the change — otherwise the
     * old page could land after the new search's results and get appended onto them.
     */
    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        val nextPage = currentPage + 1

        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val state = _uiState.value
            val dataFilters = state.advancedFilters.toSearchFilters(
                deckName = state.query.trim(),
                orderBy = state.selectedSort.apiValue,
                page = nextPage,
                pageSize = SEARCH_PAGE_SIZE,
            )

            when (val result = searchCommunityDecks(dataFilters)) {
                is DataResult.Success -> {
                    currentPage = nextPage
                    crashlytics.log("community_search_load_more")
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            results = it.results + result.data.decks,
                            hasMore = result.data.hasMore,
                        )
                    }
                }
                is DataResult.Error -> {
                    crashlytics.log(
                        "community_search_load_more_error: page=$nextPage, " +
                            "accumulated=${_uiState.value.results.size}",
                    )
                    crashlytics.setCustomKey("community_search_failed_page", nextPage)
                    _events.send(CommunityDecksSearchEvent.ShowError(result.message))
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun onDeckClick(archidektId: Int) {
        viewModelScope.launch {
            _events.send(CommunityDecksSearchEvent.NavigateToDeck(archidektId))
        }
    }
}

/** One-shot effects for the search screen. */
sealed interface CommunityDecksSearchEvent {
    data class NavigateToDeck(val archidektId: Int) : CommunityDecksSearchEvent
    data class ShowError(val message: String) : CommunityDecksSearchEvent
}
