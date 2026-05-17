package com.mmg.manahub.feature.draft.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.model.DraftVideo
import com.mmg.manahub.feature.draft.domain.model.SetDraftGuide
import com.mmg.manahub.feature.draft.domain.model.SetTierList
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetVideosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    // Videos (loaded in background, shown in Guide tab)
    val videos: List<DraftVideo> = emptyList(),
    val isVideosLoading: Boolean = false,
)

@HiltViewModel
class SetDraftDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSetGuideUseCase: GetSetGuideUseCase,
    private val getSetTierListUseCase: GetSetTierListUseCase,
    private val getSetVideosUseCase: GetSetVideosUseCase,
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

    private var guideLoaded = false
    private var tierListLoaded = false
    private var videosLoaded = false

    init {
        loadGuide()
        loadVideos()
    }

    fun onTabSelected(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == 1 && !tierListLoaded) loadTierList()
    }

    fun loadGuide() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGuideLoading = true, guideError = null) }
            when (val result = getSetGuideUseCase(setCode)) {
                is DataResult.Success -> {
                    guideLoaded = true
                    _uiState.update { it.copy(guide = result.data, isGuideLoading = false) }
                }
                is DataResult.Error -> {
                    guideLoaded = true
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
                    tierListLoaded = true
                    _uiState.update { it.copy(tierList = result.data, isTierListLoading = false) }
                }
                is DataResult.Error -> {
                    tierListLoaded = true
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
                    videosLoaded = true
                    _uiState.update { it.copy(videos = result.data, isVideosLoading = false) }
                }
                is DataResult.Error -> {
                    videosLoaded = true
                    _uiState.update { it.copy(isVideosLoading = false) }
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
}
