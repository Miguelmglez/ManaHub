package com.mmg.manahub.feature.today.presentation.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.feature.news.domain.feed.filterFeed
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.ObserveSavedItemsUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ToggleSavedItemUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot Feed effects, delivered through a [Channel] so two identical toasts in a row are never collapsed. */
sealed interface FeedEvent {
    data class PartialRefreshFailure(val failedCount: Int) : FeedEvent
    data class SavedChanged(val saved: Boolean) : FeedEvent
    data class Unfollowed(val sourceName: String) : FeedEvent
    data object ActionFailed : FeedEvent
}

data class FeedUiState(
    val savedOnly: Boolean = false,
    val filterSources: List<ContentSource> = emptyList(),
    val items: List<NewsItem> = emptyList(),
    val followedSources: List<ContentSource> = emptyList(),
    val selectedSource: ContentSource? = null,
    val selectedSourceIds: Set<String> = emptySet(),
    val contentFilter: FeedContentFilter = FeedContentFilter.ALL,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val savedIds: Set<String> = emptySet(),
    val sourceLanguages: Map<String, String> = emptyMap(),
    val showLanguageBadge: Boolean = false,
    val sourcesLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val showTotalFailure: Boolean = false,
    val isFiltered: Boolean = false,
)

/** MTG Today › Feed: what you follow is what you see; type, search and the selected source only narrow it. */
@OptIn(FlowPreview::class)
class FeedViewModel(
    getNewsFeed: GetNewsFeedUseCase,
    private val refreshNewsFeed: RefreshNewsFeedUseCase,
    private val manageSources: ManageSourcesUseCase,
    observeSavedItems: ObserveSavedItemsUseCase,
    private val toggleSavedItem: ToggleSavedItemUseCase,
    private val crashReporter: CrashReporter,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val savedOnly = savedStateHandle.getStateFlow("feed_saved_only", false)
    private val savedItems = observeSavedItems().map { rows -> rows.map { it.item } }.catch {
        crashReporter.recordException(RuntimeException("Today saved items observation failed"))
        emit(emptyList())
    }
    private val contentFilter = savedStateHandle.getStateFlow(KEY_CONTENT_FILTER, FeedContentFilter.ALL.name)
    private val selectedSourceId = savedStateHandle.getStateFlow<String?>(KEY_SELECTED_SOURCE, null)
    private val searchActive = savedStateHandle.getStateFlow(KEY_SEARCH_ACTIVE, false)
    private val searchQuery = savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")

    // Clearing the query applies at once; typing waits for a pause.
    private val debouncedQuery = searchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }

    private val isRefreshing = MutableStateFlow(false)
    private val isInitialLoad = MutableStateFlow(true)
    private val totalFailure = MutableStateFlow(false)
    private var refreshJob: Job? = null

    private val _events = Channel<FeedEvent>(Channel.BUFFERED)
    val events: Flow<FeedEvent> = _events.receiveAsFlow()

    private val sources: StateFlow<List<ContentSource>?> = manageSources.observeSources()
        .onEach { list -> crashReporter.setCustomKey(KEY_FOLLOWED_COUNT, list.count { it.isEnabled }.toString()) }
        .catch {
            crashReporter.log("today_feed_sources_load_failed")
            crashReporter.recordException(RuntimeException("Today sources observation failed"))
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val savedIds: StateFlow<Set<String>> = observeSavedItems.savedIds()
        .catch {
            crashReporter.log("today_saved_ids_load_failed")
            crashReporter.recordException(RuntimeException("Today saved IDs observation failed"))
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    private data class FilterInputs(
        val contentFilter: FeedContentFilter,
        val selectedSourceIds: Set<String>,
        val searchActive: Boolean,
        val rawQuery: String,
        val query: String,
    )

    private val filterInputs: Flow<FilterInputs> =
        combine(contentFilter, selectedSourceId, searchActive, searchQuery, debouncedQuery) { filter, selected, active, raw, query ->
            FilterInputs(
                contentFilter = FeedContentFilter.entries.firstOrNull { it.name == filter } ?: FeedContentFilter.ALL,
                selectedSourceIds = selected?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
                searchActive = active,
                rawQuery = raw,
                query = query,
            )
        }

    private data class Status(val refreshing: Boolean, val initialLoad: Boolean, val totalFailure: Boolean)

    private val status: Flow<Status> =
        combine(isRefreshing, isInitialLoad, totalFailure) { refreshing, initialLoad, failure -> Status(refreshing, initialLoad, failure) }

    val uiState: StateFlow<FeedUiState> = combine(
        combine(getNewsFeed(), savedItems, savedOnly) { live, saved, onlySaved -> onlySaved to if (onlySaved) saved else live }.catch {
            crashReporter.log("today_feed_load_failed")
            crashReporter.recordException(RuntimeException("Today feed observation failed"))
            emit(false to emptyList())
        },
        sources,
        savedIds,
        filterInputs,
        status,
    ) { feed, sources, savedIds, filters, status ->
        val (onlySaved, items) = feed
        crashReporter.setCustomKey("today_feed_mode", if (onlySaved) "saved" else "live")
        crashReporter.setCustomKey("today_feed_content_filter", filters.contentFilter.name)
        val followed = sources.orEmpty().filter { it.isEnabled }
        val filterSources = if (onlySaved) items.distinctBy { it.sourceId }.map { item ->
            sources.orEmpty().firstOrNull { it.id == item.sourceId } ?: ContentSource(
                id = item.sourceId, name = item.sourceName, feedUrl = "", type = if (item is NewsItem.Video)
                    com.mmg.manahub.core.model.news.SourceType.VIDEO else com.mmg.manahub.core.model.news.SourceType.ARTICLE,
            )
        } else followed
        val selectedIds = filterSources.map { it.id }.toSet().intersect(filters.selectedSourceIds)
        val selected = if (selectedIds.size == 1) filterSources.firstOrNull { it.id == selectedIds.first() } else null
        val visible = filterFeed(
            items = items,
            followedIds = if (onlySaved) items.map { it.sourceId }.toSet() else followed.map { it.id }.toSet(),
            contentFilter = filters.contentFilter,
            selectedSourceIds = selectedIds,
            query = filters.query,
            sourceNames = followed.associate { it.id to it.name },
        )
        FeedUiState(
            savedOnly = onlySaved,
            filterSources = filterSources,
            items = visible,
            followedSources = followed,
            selectedSource = selected,
            selectedSourceIds = selectedIds,
            contentFilter = filters.contentFilter,
            searchActive = filters.searchActive,
            searchQuery = filters.rawQuery,
            savedIds = savedIds,
            sourceLanguages = followed.associate { it.id to it.language },
            showLanguageBadge = followed.map { it.language }.distinct().size > 1,
            sourcesLoaded = sources != null,
            isLoading = !onlySaved && status.initialLoad && visible.isEmpty(),
            isRefreshing = status.refreshing && !status.initialLoad,
            showTotalFailure = !onlySaved && status.totalFailure && visible.isEmpty(),
            isFiltered = filters.contentFilter != FeedContentFilter.ALL || selectedIds.isNotEmpty() || filters.query.isNotBlank(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedUiState())

    init {
        refresh()
    }

    /** [force] (pull-to-refresh) bypasses the per-source 1h TTL; conditional GET still applies. */
    fun refresh(force: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            isRefreshing.value = true
            totalFailure.value = false
            refreshNewsFeed(force)
                .onSuccess { result ->
                    if (result.failed > 0) _events.send(FeedEvent.PartialRefreshFailure(result.failed))
                    // Toast-only for a partial failure; a total one with nothing cached gets a persistent banner.
                    totalFailure.value = result.attempted > 0 && result.failed == result.attempted
                }
                .onFailure { e ->
                    crashReporter.log("news_refresh_failed: ${e::class.simpleName}")
                    crashReporter.recordException(RuntimeException("[FeedViewModel] Feed refresh failed"))
                    totalFailure.value = true
                }
            isRefreshing.value = false
            isInitialLoad.value = false
        }
    }

    fun onSavedOnlyChanged(saved: Boolean) {
        savedStateHandle["feed_saved_only"] = saved
        crashReporter.log("today_feed_mode_changed")
    }

    fun onContentFilterSelected(filter: FeedContentFilter) {
        savedStateHandle[KEY_CONTENT_FILTER] = filter.name
        crashReporter.log("today_feed_filter_changed")
    }

    fun onSearchActiveChanged(active: Boolean) {
        savedStateHandle[KEY_SEARCH_ACTIVE] = active
        if (!active) savedStateHandle[KEY_SEARCH_QUERY] = ""
    }

    fun onSearchQueryChanged(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    /** Filters the feed by source IDs; null clears specific source selections. */
    fun selectSource(sourceId: String?) {
        if (sourceId == null) {
            savedStateHandle[KEY_SELECTED_SOURCE] = null
            crashReporter.setCustomKey("today_feed_source_filter_active", "false")
        } else {
            val currentRaw = savedStateHandle.get<String?>(KEY_SELECTED_SOURCE)
            val currentSet = currentRaw?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
            val newSet = if (sourceId in currentSet) {
                currentSet - sourceId
            } else {
                currentSet + sourceId
            }
            val newRaw = if (newSet.isEmpty()) null else newSet.joinToString(",")
            savedStateHandle[KEY_SELECTED_SOURCE] = newRaw
            crashReporter.setCustomKey("today_feed_source_filter_active", (newRaw != null).toString())
        }
    }

    /** Rail chip tap: toggles the source selection. */
    fun onSourceChipClicked(sourceId: String) {
        selectSource(sourceId)
    }

    fun toggleSaved(item: NewsItem) {
        viewModelScope.launch {
            try {
                val saved = toggleSavedItem(item, isCurrentlySaved = item.id in savedIds.value)
                crashReporter.log(if (saved) "news_item_saved" else "news_item_unsaved")
                _events.send(FeedEvent.SavedChanged(saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(RuntimeException("[FeedViewModel] Save toggle failed"))
                _events.send(FeedEvent.ActionFailed)
            }
        }
    }

    fun unfollow(source: ContentSource) {
        viewModelScope.launch {
            try {
                manageSources.setFollowed(source.id, followed = false)
                val currentSet = selectedSourceId.value?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
                if (source.id in currentSet) {
                    selectSource(source.id)
                }
                crashReporter.log("news_source_unfollowed")
                _events.send(FeedEvent.Unfollowed(source.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(RuntimeException("[FeedViewModel] Unfollow failed"))
                _events.send(FeedEvent.ActionFailed)
            }
        }
    }

    fun onSourcesOpened() = crashReporter.log("today_sources_sheet_opened")

    fun onFiltersOpened() = crashReporter.log("today_feed_filters_opened")

    fun onSourceSiteOpened() {
        crashReporter.log("news_source_site_opened")
    }

    fun onTabSelected(tabRouteId: String) {
        crashReporter.log("today_tab_selected")
        crashReporter.setCustomKey(KEY_SELECTED_TAB, tabRouteId)
    }

    private companion object {
        const val KEY_CONTENT_FILTER = "feed_content_filter"
        const val KEY_SELECTED_SOURCE = "feed_selected_source"
        const val KEY_SEARCH_ACTIVE = "feed_search_active"
        const val KEY_SEARCH_QUERY = "feed_search_query"
        const val KEY_FOLLOWED_COUNT = "news_followed_source_count"
        const val KEY_SELECTED_TAB = "today_selected_tab"
        const val SEARCH_DEBOUNCE_MS = 300L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
