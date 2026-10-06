package com.mmg.manahub.feature.today.presentation.saved

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SavedNewsItem
import com.mmg.manahub.feature.news.domain.feed.matches
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.ObserveSavedItemsUseCase
import com.mmg.manahub.feature.news.domain.usecase.ToggleSavedItemUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SavedEvent {
    data object Removed : SavedEvent
    data object ActionFailed : SavedEvent
}

data class SavedUiState(
    val items: List<NewsItem> = emptyList(),
    val contentFilter: FeedContentFilter = FeedContentFilter.ALL,
    /** Source id → site URL for "Open source"; missing when the source was deleted. */
    val sourceSiteUrls: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val hasAnySaved: Boolean = false,
)

/** MTG Today › Saved: device-local snapshots, newest save first. */
class SavedViewModel(
    observeSavedItems: ObserveSavedItemsUseCase,
    manageSources: ManageSourcesUseCase,
    private val toggleSavedItem: ToggleSavedItemUseCase,
    private val crashReporter: CrashReporter,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val contentFilter = savedStateHandle.getStateFlow(KEY_CONTENT_FILTER, FeedContentFilter.ALL.name)

    private val _events = Channel<SavedEvent>(Channel.BUFFERED)
    val events: Flow<SavedEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SavedUiState> = combine(
        observeSavedItems().catch {
            crashReporter.log("today_saved_load_failed")
            emit(emptyList<SavedNewsItem>())
        },
        manageSources.observeSources()
            .map { sources -> sources.mapNotNull { source -> source.siteUrl?.let { source.id to it } }.toMap() }
            .catch { emit(emptyMap()) },
        contentFilter,
    ) { saved, siteUrls, filterName ->
        val filter = FeedContentFilter.entries.firstOrNull { it.name == filterName } ?: FeedContentFilter.ALL
        SavedUiState(
            items = saved.map { it.item }.filter { it.matches(filter) },
            contentFilter = filter,
            sourceSiteUrls = siteUrls,
            isLoading = false,
            hasAnySaved = saved.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SavedUiState())

    fun onContentFilterSelected(filter: FeedContentFilter) {
        savedStateHandle[KEY_CONTENT_FILTER] = filter.name
    }

    fun remove(item: NewsItem) {
        viewModelScope.launch {
            try {
                toggleSavedItem(item, isCurrentlySaved = true)
                crashReporter.log("news_item_unsaved")
                _events.send(SavedEvent.Removed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(RuntimeException("[SavedViewModel] Remove failed", e))
                _events.send(SavedEvent.ActionFailed)
            }
        }
    }

    fun onSourceSiteOpened() {
        crashReporter.log("news_source_site_opened")
    }

    private companion object {
        const val KEY_CONTENT_FILTER = "saved_content_filter"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
