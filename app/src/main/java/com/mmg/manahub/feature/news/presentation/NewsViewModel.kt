package com.mmg.manahub.feature.news.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.news.NewsFilterPrefs
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One-shot side effects for [NewsScreen]. Delivered through a buffered [Channel] (see [events]),
 * never a nullable `StateFlow` field — a `StateFlow` equality-collapses two consecutive identical
 * events (e.g. two "couldn't update 1 source" toasts back to back) and can drop emissions made
 * while the screen's lifecycle is paused. Mirrors the pattern established in
 * `feature/trades`' `ProposalEvent` (see that file's KDoc for the full rationale).
 */
sealed class NewsEvent {
    /** At least one source failed to refresh this cycle (partial or total failure). */
    data class ShowPartialRefreshFailure(val failedCount: Int) : NewsEvent()
}

/**
 * KMP migration — Phase 1: resolved by Koin (`koinViewModel()`), not Hilt. The plain constructor lets
 * `newsKoinModule` build it from the bridged news use cases + the shared `UserPreferencesDataStore`.
 */
@OptIn(FlowPreview::class)
class NewsViewModel(
    getNewsFeed: GetNewsFeedUseCase,
    private val refreshNewsFeed: RefreshNewsFeedUseCase,
    manageSources: ManageSourcesUseCase,
    private val userPrefsDataStore: UserPreferencesDataStore,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    private val _isInitialLoad = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    private val _events = Channel<NewsEvent>(Channel.BUFFERED)
    val events: Flow<NewsEvent> = _events.receiveAsFlow()

    // Advanced search filter state. A user edit (onFiltersApplied) sets this LOCAL override
    // immediately (optimistic UI, no DataStore round-trip latency) while also writing through to
    // DataStore so the selection is shared with the Home news widget and survives process death.
    // `null` means "no local override yet — defer to whatever DataStore's persisted flow reports".
    private val _filterOverride = MutableStateFlow<NewsFilterPrefs?>(null)

    // Combining the persisted flow with the local override (instead of a one-shot `.first()` seed
    // in `init`) means the FIRST emitted `uiState` already reflects the persisted selection — no
    // DEFAULT flash while the DataStore read resolves, and no race between that seed and this
    // combine's own first read (F7/F8 fix).
    private val effectiveFilters: Flow<NewsFilterPrefs> = combine(
        userPrefsDataStore.observeNewsFilters(),
        _filterOverride,
    ) { persisted, override -> override ?: persisted }

    private val debouncedQuery = _searchQuery.debounce(300)

    // Pair(raw, debounced) — raw drives the search field's displayed text, debounced drives
    // filtering. Both are real combine inputs now; the transform lambda never reads `.value`.
    private val queryState: Flow<Pair<String, String>> =
        combine(_searchQuery, debouncedQuery) { raw, debounced -> raw to debounced }

    private val statusState: Flow<Triple<Boolean, String?, Boolean>> =
        combine(_isRefreshing, _error, _isInitialLoad) { refreshing, error, initialLoad ->
            Triple(refreshing, error, initialLoad)
        }

    // Triple(sourceId → language, enabled source ids, ALL known source ids). The third element
    // feeds the F6 allowlist-pruning below (a disabled-but-not-deleted source id must stay valid).
    private val sourceDataState: Flow<Triple<Map<String, String>, Set<String>, Set<String>>> =
        manageSources.observeSources().map { srcs ->
            val languageMap = srcs.associate { it.id to it.language }
            val enabledIds = srcs.filter { it.isEnabled }.map { it.id }.toSet()
            val allIds = srcs.map { it.id }.toSet()
            Triple(languageMap, enabledIds, allIds)
        }

    val sources: StateFlow<List<ContentSource>> = manageSources.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<NewsUiState> = combine(
        getNewsFeed(),
        queryState,
        effectiveFilters,
        statusState,
        sourceDataState,
    ) { allItems, queryPair, filters, status, sourceData ->
        val (rawQuery, debouncedQueryValue) = queryPair
        val (refreshing, error, isInitialLoad) = status
        val (languageMap, enabledSourceIds, allSourceIds) = sourceData

        // F6: a `sourceIds` allowlist may still reference a source that was since deleted —
        // prune it here (on read) so a phantom id doesn't linger in the UI forever.
        val prunedSourceIds = filters.sourceIds?.intersect(allSourceIds)

        val items = allItems
            .filter { item -> item.sourceId in enabledSourceIds }
            .filter { item ->
                val sourceLanguage = languageMap[item.sourceId] ?: "en"
                sourceLanguage in filters.languages
            }
            .filter { item ->
                when (item) {
                    is NewsItem.Article -> SourceType.ARTICLE in filters.types
                    is NewsItem.Video   -> SourceType.VIDEO in filters.types
                }
            }
            .filter { item ->
                prunedSourceIds == null || item.sourceId in prunedSourceIds
            }
            .filter { item ->
                debouncedQueryValue.isBlank() || item.title.contains(debouncedQueryValue, ignoreCase = true)
                    || item.description.contains(debouncedQueryValue, ignoreCase = true)
            }

        NewsUiState(
            items             = items,
            isLoading         = isInitialLoad && items.isEmpty(),
            isRefreshing      = refreshing && !isInitialLoad,
            searchQuery       = rawQuery,
            filterTypes       = filters.types,
            filterLanguages   = filters.languages,
            filterSourceIds   = prunedSourceIds,
            error             = error,
            sourceLanguageMap = languageMap,
            showLanguageBadge = filters.languages.size > 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NewsUiState())

    init {
        refresh()
    }

    /**
     * @param force manual pull-to-refresh: re-contacts every enabled source regardless of its
     *  per-source staleness watermark. Conditional GET still applies either way — this only
     *  bypasses the 1h TTL gate, never the 304 short-circuit.
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            val result = refreshNewsFeed(force)
            result.onSuccess { refreshResult ->
                if (refreshResult.failed > 0) {
                    _events.send(NewsEvent.ShowPartialRefreshFailure(refreshResult.failed))
                }
                // Per-source failures are isolated (RefreshResult never carries an overall
                // Result.failure — see NewsRepositoryImpl), so a PARTIAL failure stays toast-only
                // (there IS content to show). But when EVERY attempted source failed and there is
                // nothing already cached to show, the one-shot toast alone is not enough — once it
                // auto-dismisses, the screen is indistinguishable from a genuinely-empty feed. Surface
                // a persistent, retryable banner for that specific case.
                val isTotalFailure = refreshResult.attempted > 0 && refreshResult.failed == refreshResult.attempted
                if (isTotalFailure && uiState.value.items.isEmpty()) {
                    _error.value = TOTAL_REFRESH_FAILURE_MESSAGE
                }
            }
            result.onFailure { e ->
                FirebaseCrashlytics.getInstance().apply {
                    log("news_refresh_failed: ${e::class.simpleName}")
                    recordException(RuntimeException("[NewsViewModel] Feed refresh failed", e))
                }
                _error.value = e.message
            }
            _isRefreshing.value = false
            _isInitialLoad.value = false
        }
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    fun onFiltersApplied(
        types: Set<SourceType>,
        languages: Set<String>,
        sourceIds: Set<String>?,
    ) {
        val effectiveTypes = types.ifEmpty { NewsFilterPrefs.DEFAULT.types }
        val effectiveLanguages = languages.ifEmpty { NewsFilterPrefs.DEFAULT.languages }
        _filterOverride.value = NewsFilterPrefs(
            languages = effectiveLanguages,
            types = effectiveTypes,
            sourceIds = sourceIds,
        )
        // Write through to DataStore so NewsScreen and the Home widget share one source of truth.
        viewModelScope.launch {
            userPrefsDataStore.setNewsFilters(
                languages = effectiveLanguages,
                types = effectiveTypes,
                sourceIds = sourceIds,
            )
        }
    }

    fun onErrorDismissed() { _error.value = null }

    private companion object {
        /**
         * Persistent inline-banner copy for a total refresh failure (every attempted source
         * failed AND nothing is cached to show) — distinct from [NewsEvent.ShowPartialRefreshFailure]'s
         * toast copy, which covers the "some sources failed but there IS content" case.
         */
        const val TOTAL_REFRESH_FAILURE_MESSAGE = "Couldn't load news. Check your connection and try again."
    }
}

data class NewsUiState(
    val items: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val filterTypes: Set<SourceType> = NewsFilterPrefs.DEFAULT.types,
    val filterLanguages: Set<String> = NewsFilterPrefs.DEFAULT.languages,
    val filterSourceIds: Set<String>? = NewsFilterPrefs.DEFAULT.sourceIds,
    val error: String? = null,
    val sourceLanguageMap: Map<String, String> = emptyMap(),
    val showLanguageBadge: Boolean = false,
)
