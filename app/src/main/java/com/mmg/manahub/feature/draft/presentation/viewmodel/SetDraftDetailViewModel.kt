package com.mmg.manahub.feature.draft.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftVideo
import com.mmg.manahub.core.model.SetDraftGuide
import com.mmg.manahub.core.model.SetTierList
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetVideosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class SetDraftDetailUiState(
    val setCode: String = "",
    val setName: String = "",
    val setIconUri: String = "",
    val setReleasedAt: String = "",
    // Main tabs: 0 = Guide, 1 = Tier List
    val selectedTab: Int = 0,
    // Guide
    val guide: SetDraftGuide? = null,
    val isGuideLoading: Boolean = false,
    val guideError: String? = null,
    // Tier List
    val tierList: SetTierList? = null,
    val isTierListLoading: Boolean = false,
    val tierListError: String? = null,
    val tierListColorFilter: Set<String> = emptySet(),
    val tierListSearchQuery: String = "",
    // Videos (loaded in background, shown in Guide tab)
    val videos: List<DraftVideo> = emptyList(),
    val isVideosLoading: Boolean = false,
    /** Non-null when this set has a published booster.json and can be draft-simulated. */
    val boosterVersion: String? = null,
)

/**
 * Drives the Set Draft Detail screen (Guide + Tier List + videos).
 *
 * KMP migration — Phase 1: resolved by Koin (`koinViewModel()`), not Hilt. The [SavedStateHandle] is
 * Koin-injected and carries the route nav args (`setCode`/`setName`/`setIconUri`/`setReleasedAt`).
 */
class SetDraftDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val getSetGuideUseCase: GetSetGuideUseCase,
    private val getSetTierListUseCase: GetSetTierListUseCase,
    private val getSetVideosUseCase: GetSetVideosUseCase,
    private val getDraftableSetsUseCase: GetDraftableSetsUseCase,
) : ViewModel() {

    val setCode: String = savedStateHandle.get<String>("setCode") ?: ""
    private val setName: String = savedStateHandle.get<String>("setName") ?: ""
    private val setIconUri: String = savedStateHandle.get<String>("setIconUri") ?: ""
    private val setReleasedAt: String = savedStateHandle.get<String>("setReleasedAt") ?: ""

    private val _uiState = MutableStateFlow(
        SetDraftDetailUiState(
            setCode = setCode,
            setName = setName,
            setIconUri = setIconUri,
            setReleasedAt = setReleasedAt,
        )
    )
    val uiState: StateFlow<SetDraftDetailUiState> = _uiState.asStateFlow()

    private val guideLoaded = AtomicBoolean(false)
    private val tierListLoaded = AtomicBoolean(false)
    private val videosLoaded = AtomicBoolean(false)

    init {
        loadGuide()
        loadVideos()
        loadDraftability()
    }

    /**
     * Resolves whether this set is draft-simulable by looking up its booster version in the
     * draftable-sets index. Failures are silent — the Simulate Draft button simply stays hidden.
     */
    private fun loadDraftability() {
        viewModelScope.launch {
            when (val result = getDraftableSetsUseCase()) {
                is DataResult.Success -> {
                    val version = result.data
                        .firstOrNull { it.code.equals(setCode, ignoreCase = true) }
                        ?.boosterVersion
                    _uiState.update { it.copy(boosterVersion = version) }
                }
                is DataResult.Error -> Unit
            }
        }
    }

    fun onTabSelected(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == 1 && !tierListLoaded.get()) loadTierList()
    }

    fun loadGuide() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGuideLoading = true, guideError = null) }
            when (val result = getSetGuideUseCase(setCode)) {
                is DataResult.Success -> {
                    guideLoaded.set(true)
                    _uiState.update { it.copy(guide = result.data, isGuideLoading = false) }
                }
                is DataResult.Error -> {
                    // Do NOT set guideLoaded = true on error so the UI can offer a retry
                    _uiState.update { it.copy(isGuideLoading = false, guideError = result.message) }
                }
            }
        }
    }

    fun loadTierList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTierListLoading = true, tierListError = null) }
            when (val result = getSetTierListUseCase(setCode)) {
                is DataResult.Success -> {
                    tierListLoaded.set(true)
                    _uiState.update { it.copy(tierList = result.data, isTierListLoading = false) }
                }
                is DataResult.Error -> {
                    // Do NOT set tierListLoaded = true on error so the UI can offer a retry
                    _uiState.update { it.copy(isTierListLoading = false, tierListError = result.message) }
                }
            }
        }
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isVideosLoading = true) }
            when (val result = getSetVideosUseCase(setCode, setName)) {
                is DataResult.Success -> {
                    videosLoaded.set(true)
                    _uiState.update { it.copy(videos = result.data, isVideosLoading = false) }
                }
                is DataResult.Error -> {
                    videosLoaded.set(true)
                    _uiState.update { it.copy(isVideosLoading = false) }
                    FirebaseCrashlytics.getInstance().apply {
                        log("draft_videos_load_failed: set=$setCode error=${result.message}")
                        setCustomKey("draft_set_code", setCode)
                    }
                }
            }
        }
    }

    fun toggleTierListColorFilter(color: String) {
        _uiState.update { state ->
            val newFilter = state.tierListColorFilter.toMutableSet()
            if (color == "All") {
                newFilter.clear()
            } else {
                if (color in newFilter) newFilter.remove(color) else newFilter.add(color)
            }
            state.copy(tierListColorFilter = newFilter)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(tierListSearchQuery = query) }
    }
}
