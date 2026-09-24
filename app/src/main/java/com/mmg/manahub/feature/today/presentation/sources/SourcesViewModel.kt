package com.mmg.manahub.feature.today.presentation.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SourcesEvent {
    data class Followed(val sourceName: String) : SourcesEvent
    data class Unfollowed(val sourceName: String) : SourcesEvent
    data class Deleted(val sourceName: String) : SourcesEvent
    data object ActionFailed : SourcesEvent
}

/** A Discover section: sources in one language that the user does not follow. */
data class DiscoverGroup(val language: String, val sources: List<ContentSource>)

data class SourcesUiState(
    val following: List<ContentSource> = emptyList(),
    val discover: List<DiscoverGroup> = emptyList(),
    val isLoading: Boolean = true,
)

/** MTG Today › Sources: following = `is_enabled`; defaults can be unfollowed, custom sources deleted. */
class SourcesViewModel(
    private val manageSources: ManageSourcesUseCase,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _events = Channel<SourcesEvent>(Channel.BUFFERED)
    val events: Flow<SourcesEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SourcesUiState> = manageSources.observeSources()
        .map { sources -> sources.toUiState() }
        .catch {
            crashReporter.log("today_sources_load_failed")
            emit(SourcesUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SourcesUiState())

    fun follow(source: ContentSource) = launchAction("follow") {
        manageSources.setFollowed(source.id, followed = true)
        crashReporter.log("news_source_followed")
        _events.send(SourcesEvent.Followed(source.name))
        // Fetch right away so its posts are in the feed when the user switches back; failures retry on the next refresh.
        manageSources.refreshSource(source.id)
    }

    fun unfollow(source: ContentSource) = launchAction("unfollow") {
        manageSources.setFollowed(source.id, followed = false)
        crashReporter.log("news_source_unfollowed")
        _events.send(SourcesEvent.Unfollowed(source.name))
    }

    fun delete(source: ContentSource) = launchAction("delete") {
        if (source.isDefault) return@launchAction
        manageSources.deleteSource(source.id)
        crashReporter.log("news_source_deleted")
        _events.send(SourcesEvent.Deleted(source.name))
    }

    fun onSourceSiteOpened() {
        crashReporter.log("news_source_site_opened")
    }

    private fun launchAction(name: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(RuntimeException("[SourcesViewModel] $name failed", e))
                _events.send(SourcesEvent.ActionFailed)
            }
        }
    }

    private fun List<ContentSource>.toUiState(): SourcesUiState {
        val (followed, notFollowed) = partition { it.isEnabled }
        val languageOrder = ContentSource.SUPPORTED_LANGUAGES
        return SourcesUiState(
            following = followed.sortedBy { it.name.lowercase() },
            // Unfollowed custom sources stay listed here so they can be followed again.
            discover = notFollowed
                .groupBy { it.language }
                .toList()
                .sortedBy { (language, _) -> languageOrder.indexOf(language).let { if (it == -1) Int.MAX_VALUE else it } }
                .map { (language, sources) -> DiscoverGroup(language, sources.sortedBy { it.name.lowercase() }) },
            isLoading = false,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
