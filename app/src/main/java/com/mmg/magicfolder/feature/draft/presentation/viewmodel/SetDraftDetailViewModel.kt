package com.mmg.magicfolder.feature.draft.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.feature.draft.domain.model.DraftVideo
import com.mmg.magicfolder.feature.draft.domain.model.SetDraftGuide
import com.mmg.magicfolder.feature.draft.domain.model.SetTierList
import com.mmg.magicfolder.feature.draft.domain.usecase.GetSetCardsUseCase
import com.mmg.magicfolder.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.magicfolder.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.magicfolder.feature.draft.domain.usecase.GetSetVideosUseCase
import com.mmg.magicfolder.feature.draft.domain.usecase.LookupCardIdUseCase
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
    // Cards
    val cards: List<Card> = emptyList(),
    val isCardsLoading: Boolean = false,
    val cardsError: String? = null,
    val cardsPage: Int = 1,
    val hasMoreCards: Boolean = false,
    val cardColorFilter: Set<String> = emptySet(),
    val cardRarityFilter: Set<String> = emptySet(),
    val cardSortBy: CardSortOption = CardSortOption.COLLECTOR,
    // Videos
    val videos: List<DraftVideo> = emptyList(),
    val isVideosLoading: Boolean = false,
    val videosError: String? = null,
    // Card navigation (resolved scryfallId ready to navigate)
    val pendingCardNavigation: String? = null,
)

enum class CardSortOption { PRICE, NAME, COLLECTOR, RARITY }

@HiltViewModel
class SetDraftDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSetGuideUseCase: GetSetGuideUseCase,
    private val getSetTierListUseCase: GetSetTierListUseCase,
    private val getSetCardsUseCase: GetSetCardsUseCase,
    private val getSetVideosUseCase: GetSetVideosUseCase,
    private val lookupCardIdUseCase: LookupCardIdUseCase,
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
    private var cardsLoaded = false
    private var videosLoaded = false

    init {
        loadGuide()
    }

    fun onTabSelected(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            0 -> if (!guideLoaded) loadGuide()
            1 -> if (!tierListLoaded) loadTierList()
            2 -> if (!cardsLoaded) loadCards()
            3 -> if (!videosLoaded) loadVideos()
        }
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

    fun loadCards(nextPage: Boolean = false) {
        viewModelScope.launch {
            val page = if (nextPage) _uiState.value.cardsPage + 1 else 1
            _uiState.update { it.copy(isCardsLoading = true, cardsError = null) }
            when (val result = getSetCardsUseCase(setCode, page)) {
                is DataResult.Success -> {
                    cardsLoaded = true
                    _uiState.update { state ->
                        val newCards = if (nextPage) state.cards + result.data else result.data
                        state.copy(
                            cards = newCards,
                            isCardsLoading = false,
                            cardsPage = page,
                            hasMoreCards = result.data.size >= 175,
                        )
                    }
                }
                is DataResult.Error -> {
                    cardsLoaded = true
                    _uiState.update { it.copy(isCardsLoading = false, cardsError = result.message) }
                }
            }
        }
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isVideosLoading = true, videosError = null) }
            when (val result = getSetVideosUseCase(setCode, setName)) {
                is DataResult.Success -> {
                    videosLoaded = true
                    _uiState.update { it.copy(videos = result.data, isVideosLoading = false) }
                }
                is DataResult.Error -> {
                    videosLoaded = true
                    _uiState.update { it.copy(isVideosLoading = false, videosError = result.message) }
                }
            }
        }
    }

    fun toggleCardColorFilter(color: String) {
        _uiState.update { state ->
            val newFilter = state.cardColorFilter.toMutableSet()
            if (color in newFilter) newFilter.remove(color) else newFilter.add(color)
            state.copy(cardColorFilter = newFilter)
        }
    }

    fun toggleCardRarityFilter(rarity: String) {
        _uiState.update { state ->
            val newFilter = state.cardRarityFilter.toMutableSet()
            if (rarity in newFilter) newFilter.remove(rarity) else newFilter.add(rarity)
            state.copy(cardRarityFilter = newFilter)
        }
    }

    fun setCardSortBy(sort: CardSortOption) {
        _uiState.update { it.copy(cardSortBy = sort) }
    }

    fun resolveAndNavigate(cardName: String) {
        viewModelScope.launch {
            when (val result = lookupCardIdUseCase(cardName, setCode)) {
                is DataResult.Success -> _uiState.update { it.copy(pendingCardNavigation = result.data) }
                is DataResult.Error -> { /* silently ignore — user can tap again */ }
            }
        }
    }

    fun onCardNavigationHandled() {
        _uiState.update { it.copy(pendingCardNavigation = null) }
    }

    fun toggleTierListColorFilter(color: String) {
        _uiState.update { state ->
            val newFilter = state.tierListColorFilter.toMutableSet()
            if (color in newFilter) newFilter.remove(color) else newFilter.add(color)
            state.copy(tierListColorFilter = newFilter)
        }
    }
}
