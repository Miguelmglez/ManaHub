package com.mmg.manahub.feature.draft.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.model.DraftVideo
import com.mmg.manahub.feature.draft.domain.model.SetDraftGuide
import com.mmg.manahub.feature.draft.domain.model.SetTierList
import com.mmg.manahub.feature.draft.domain.usecase.GetCardByNameUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetCardsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetVideosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    // Main tabs: 0 = Guide, 1 = Cards
    val selectedTab: Int = 0,
    // Cards sub-tabs: 0 = All Cards, 1 = Tier List
    val selectedCardsSubTab: Int = 0,
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
    val cardSortBy: CardSortOption = CardSortOption.RARITY,
    // Videos (loaded in background, shown in Guide tab)
    val videos: List<DraftVideo> = emptyList(),
    val isVideosLoading: Boolean = false,
    // Card detail bottom sheet
    val cardDetail: Card? = null,
    val isCardDetailLoading: Boolean = false,
)

enum class CardSortOption { PRICE, NAME, RARITY }

@HiltViewModel
class SetDraftDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSetGuideUseCase: GetSetGuideUseCase,
    private val getSetTierListUseCase: GetSetTierListUseCase,
    private val getSetCardsUseCase: GetSetCardsUseCase,
    private val getSetVideosUseCase: GetSetVideosUseCase,
    private val getCardByNameUseCase: GetCardByNameUseCase,
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
        loadVideos()
        loadCards() // eager load for art-crop URL cache
    }

    fun onTabSelected(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onCardsSubTabSelected(tab: Int) {
        _uiState.update { it.copy(selectedCardsSubTab = tab) }
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

    /**
     * Loads ALL pages of set cards sequentially (150 ms between pages to respect
     * Scryfall's 10 req/s limit).  Updates [SetDraftDetailUiState.cards] progressively
     * so the art-crop cache is populated as quickly as possible.
     */
    fun loadCards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCardsLoading = true, cardsError = null) }
            val accumulated = mutableListOf<Card>()
            var page = 1
            var keepGoing = true
            while (keepGoing) {
                when (val result = getSetCardsUseCase(setCode, page)) {
                    is DataResult.Success -> {
                        accumulated.addAll(result.data)
                        // Publish intermediate results so art-crop URLs appear progressively
                        _uiState.update { it.copy(cards = accumulated.toList()) }
                        if (result.data.size >= 175) {
                            page++
                            delay(150L) // stay well under Scryfall's 10 req/s limit
                        } else {
                            keepGoing = false
                        }
                    }
                    is DataResult.Error -> {
                        keepGoing = false
                        if (accumulated.isEmpty()) {
                            _uiState.update { it.copy(cardsError = result.message) }
                        }
                        // If we already have some cards, don't surface the error
                    }
                }
            }
            cardsLoaded = true
            _uiState.update { it.copy(isCardsLoading = false, hasMoreCards = false, cardsPage = page) }
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

    fun loadCardDetail(cardName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCardDetailLoading = true, cardDetail = null) }
            when (val result = getCardByNameUseCase(cardName, setCode)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(cardDetail = result.data, isCardDetailLoading = false)
                }
                is DataResult.Error -> _uiState.update { it.copy(isCardDetailLoading = false) }
            }
        }
    }

    fun showCardDetail(card: Card) {
        _uiState.update { it.copy(cardDetail = card, isCardDetailLoading = false) }
    }

    fun dismissCardDetail() {
        _uiState.update { it.copy(cardDetail = null, isCardDetailLoading = false) }
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

    fun toggleTierListColorFilter(color: String) {
        _uiState.update { state ->
            val newFilter = state.tierListColorFilter.toMutableSet()
            if (color in newFilter) newFilter.remove(color) else newFilter.add(color)
            state.copy(tierListColorFilter = newFilter)
        }
    }
}
