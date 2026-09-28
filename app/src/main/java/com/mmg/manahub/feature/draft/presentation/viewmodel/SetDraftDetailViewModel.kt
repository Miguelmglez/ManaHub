package com.mmg.manahub.feature.draft.presentation.viewmodel

// COMMENTS_REVIEWED: 2026-09-22

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/** UI state of the Set Draft Detail screen. */
data class SetDraftDetailUiState(
    val setCode: String = "",
    val setName: String = "",
    val setIconUri: String = "",
    val setReleasedAt: String = "",
    // 0 = Guide, 1 = Tier List
    val selectedTab: Int = 0,
    val guide: SetDraftGuideUiModel? = null,
    val isGuideLoading: Boolean = false,
    val guideError: String? = null,
    /**
     * Ids of expanded Guide sections ([GuideSection.id]), sub-sections (e.g. `archetypes:2`) and the
     * tier-list filter panel ([TIER_FILTERS_ID]).
     */
    val expandedGuideIds: Set<String> = DEFAULT_EXPANDED_GUIDE_IDS,
    val tierListTiers: List<TierGroupUi>? = null,
    /** [tierListTiers] with the colour filter and search query applied. */
    val filteredTiers: List<TierGroupUi> = emptyList(),
    val isTierListLoading: Boolean = false,
    val tierListError: String? = null,
    val tierListColorFilter: Set<String> = emptySet(),
    val tierListSearchQuery: String = "",
    /** Non-null when this set has a published booster.json and can be draft-simulated. */
    val boosterVersion: String? = null,
)

/** Only Overview starts expanded. */
val DEFAULT_EXPANDED_GUIDE_IDS: Set<String> = setOf(GuideSection.OVERVIEW.id)

/** Expansion id of the Tier List filter panel; collapsed by default. */
const val TIER_FILTERS_ID = "tier-filters"

/**
 * Drives the Set Draft Detail screen (Guide + Tier List). Guide/tier content is turned into
 * render-ready UI models on [defaultDispatcher] so composition never parses text or maps cards.
 */
class SetDraftDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val getSetGuideUseCase: GetSetGuideUseCase,
    private val getSetTierListUseCase: GetSetTierListUseCase,
    private val getDraftableSetsUseCase: GetDraftableSetsUseCase,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
            expandedGuideIds = savedStateHandle.get<ArrayList<String>>(KEY_EXPANDED_GUIDE_IDS)?.toSet()
                ?: DEFAULT_EXPANDED_GUIDE_IDS,
        )
    )
    val uiState: StateFlow<SetDraftDetailUiState> = _uiState.asStateFlow()

    private var guideJob: Job? = null
    private var tierListJob: Job? = null
    private var tierFilterJob: Job? = null

    init {
        loadGuide()
        loadDraftability()
    }

    // Failures are silent: the Simulate Draft button simply stays hidden.
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

    /** Switches between the Guide (0) and Tier List (1) tabs, loading the tier list on demand. */
    fun onTabSelected(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == 1 && _uiState.value.tierListTiers == null) loadTierList()
    }

    /** Loads the guide and, once shown, prefetches the tier list so the tab switch is instant. */
    fun loadGuide() {
        if (guideJob?.isActive == true) return
        guideJob = viewModelScope.launch {
            val start = TimeSource.Monotonic.markNow()
            _uiState.update { it.copy(isGuideLoading = true, guideError = null) }
            when (val result = getSetGuideUseCase(setCode)) {
                is DataResult.Success -> {
                    val model = withContext(defaultDispatcher) {
                        SetDraftDetailUiModelBuilder.buildGuide(result.data, setCode)
                    }
                    _uiState.update { it.copy(guide = model, isGuideLoading = false) }
                    logGuideLoaded(start.elapsedNow().inWholeMilliseconds)
                    if (_uiState.value.tierListTiers == null) loadTierList()
                }
                is DataResult.Error -> {
                    _uiState.update { it.copy(isGuideLoading = false, guideError = result.message) }
                }
            }
        }
    }

    /** Loads the tier list; a no-op while a load is already running. */
    fun loadTierList() {
        if (tierListJob?.isActive == true) return
        tierListJob = viewModelScope.launch {
            _uiState.update { it.copy(isTierListLoading = true, tierListError = null) }
            when (val result = getSetTierListUseCase(setCode)) {
                is DataResult.Success -> {
                    val snapshot = _uiState.value
                    val (tiers, filtered) = withContext(defaultDispatcher) {
                        val built = SetDraftDetailUiModelBuilder.buildTierList(result.data, setCode)
                        built to SetDraftDetailUiModelBuilder.filterTiers(
                            tiers = built,
                            colorFilter = snapshot.tierListColorFilter,
                            query = snapshot.tierListSearchQuery,
                        )
                    }
                    _uiState.update {
                        it.copy(tierListTiers = tiers, filteredTiers = filtered, isTierListLoading = false)
                    }
                    // Re-apply in case the filter changed while the list was being built.
                    if (_uiState.value.tierListColorFilter != snapshot.tierListColorFilter ||
                        _uiState.value.tierListSearchQuery != snapshot.tierListSearchQuery
                    ) {
                        refilterTiers()
                    }
                }
                is DataResult.Error -> {
                    _uiState.update { it.copy(isTierListLoading = false, tierListError = result.message) }
                }
            }
        }
    }

    /** Expands or collapses a Guide section, sub-section or the tier filter panel; survives scrolling and process death. */
    fun toggleGuideExpansion(id: String) {
        _uiState.update { state ->
            val expanded = state.expandedGuideIds
            state.copy(expandedGuideIds = if (id in expanded) expanded - id else expanded + id)
        }
        savedStateHandle[KEY_EXPANDED_GUIDE_IDS] = ArrayList(_uiState.value.expandedGuideIds)
    }

    /** Toggles a colour in the tier-list filter; "All" clears it. */
    fun toggleTierListColorFilter(color: String) {
        _uiState.update { state ->
            val newFilter = when {
                color == "All" -> emptySet()
                color in state.tierListColorFilter -> state.tierListColorFilter - color
                else -> state.tierListColorFilter + color
            }
            state.copy(tierListColorFilter = newFilter)
        }
        refilterTiers()
    }

    /** Updates the tier-list name query. */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(tierListSearchQuery = query) }
        refilterTiers()
    }

    private fun refilterTiers() {
        val tiers = _uiState.value.tierListTiers ?: return
        tierFilterJob?.cancel()
        tierFilterJob = viewModelScope.launch {
            val snapshot = _uiState.value
            val filtered = withContext(defaultDispatcher) {
                SetDraftDetailUiModelBuilder.filterTiers(
                    tiers = tiers,
                    colorFilter = snapshot.tierListColorFilter,
                    query = snapshot.tierListSearchQuery,
                )
            }
            _uiState.update { it.copy(filteredTiers = filtered) }
        }
    }

    private fun logGuideLoaded(elapsedMs: Long) {
        val bucket = when {
            elapsedMs < 250 -> "lt250ms"
            elapsedMs < 1000 -> "lt1s"
            elapsedMs < 3000 -> "lt3s"
            else -> "gte3s"
        }
        runCatching { FirebaseCrashlytics.getInstance().log("draft_guide_load_success bucket=$bucket") }
    }

    private companion object {
        const val KEY_EXPANDED_GUIDE_IDS = "draft_guide_expanded_ids"
    }
}
