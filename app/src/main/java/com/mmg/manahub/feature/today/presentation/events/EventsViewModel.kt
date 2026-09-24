package com.mmg.manahub.feature.today.presentation.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.UpcomingRelease
import com.mmg.manahub.feature.news.domain.usecase.GetProTourContentUseCase
import com.mmg.manahub.feature.news.domain.usecase.GetUpcomingReleasesUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventsUiState(
    val releasesLoading: Boolean = true,
    val releasesFailed: Boolean = false,
    val releases: List<UpcomingRelease> = emptyList(),
    val latestLimitedSet: MagicSet? = null,
    val postalCode: String = "",
    /** Null until the first emission; empty means no followed source covers a Pro Tour right now. */
    val proTourItems: List<NewsItem>? = null,
    val selectedFormat: MetagameFormat = MetagameFormat.STANDARD,
)

/** MTG Today › Events: releases from the cached Scryfall set list, event locator, Pro Tour coverage, editorial links. */
class EventsViewModel(
    private val getUpcomingReleases: GetUpcomingReleasesUseCase,
    getProTourContent: GetProTourContentUseCase,
    manageSources: ManageSourcesUseCase,
    private val userPrefsDataStore: UserPreferencesDataStore,
    private val crashReporter: CrashReporter,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EventsUiState(selectedFormat = MetagameFormat.fromId(savedStateHandle[KEY_FORMAT])),
    )
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private var releasesJob: Job? = null

    init {
        loadReleases()
        viewModelScope.launch {
            userPrefsDataStore.eventsPostalCodeFlow.collect { saved ->
                // Only seed the field; later edits are the source of truth while the screen is open.
                _uiState.update { if (it.postalCode.isEmpty()) it.copy(postalCode = saved) else it }
            }
        }
        viewModelScope.launch {
            combine(getProTourContent(), manageSources.observeSources()) { items, sources ->
                val followedIds = sources.filter { it.isEnabled }.map { it.id }.toSet()
                items.filter { it.sourceId in followedIds }.distinctBy { it.id }.take(PRO_TOUR_LIMIT)
            }
                .catch {
                    crashReporter.log("events_pro_tour_feed_failed")
                    emit(emptyList())
                }
                .collect { items -> _uiState.update { it.copy(proTourItems = items) } }
        }
    }

    fun loadReleases() {
        if (releasesJob?.isActive == true) return
        _uiState.update { it.copy(releasesLoading = true, releasesFailed = false) }
        releasesJob = viewModelScope.launch {
            getUpcomingReleases()
                .onSuccess { calendar ->
                    _uiState.update {
                        it.copy(
                            releasesLoading = false,
                            releases = calendar.releases,
                            latestLimitedSet = calendar.latestLimitedSet,
                        )
                    }
                }
                .onFailure {
                    crashReporter.log("events_releases_load_failed")
                    _uiState.update { it.copy(releasesLoading = false, releasesFailed = true) }
                }
        }
    }

    fun onFormatSelected(format: MetagameFormat) {
        savedStateHandle[KEY_FORMAT] = format.id
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun onPostalCodeChanged(postalCode: String) {
        val trimmed = postalCode.take(MAX_POSTAL_CODE_LENGTH)
        _uiState.update { it.copy(postalCode = trimmed) }
        viewModelScope.launch { userPrefsDataStore.setEventsPostalCode(trimmed) }
    }

    fun onReleaseOpened() = crashReporter.log("events_release_opened")

    fun onLocatorOpened() = crashReporter.log("events_locator_opened")

    fun onLinkOpened(linkId: String) = crashReporter.log("events_link_opened:$linkId")

    private companion object {
        const val KEY_FORMAT = "events_format"
        const val PRO_TOUR_LIMIT = 5
        const val MAX_POSTAL_CODE_LENGTH = 16
    }
}
