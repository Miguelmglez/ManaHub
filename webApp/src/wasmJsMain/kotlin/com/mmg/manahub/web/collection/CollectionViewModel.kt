package com.mmg.manahub.web.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.model.CollectionCardGroup
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionSection
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.groupByCard
import com.mmg.manahub.core.model.groupCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [CollectionScreen] (web roadmap W3d, [viewMode] added by the Settings expansion
 * slice, [groups]/[sections]/[groupingMode] added by the grouping-wiring slice) — covers all four
 * states CLAUDE.md requires every screen to handle: loading (initial collection before the first
 * emission), error (reserved for a future mutation surface on this screen — none exist yet,
 * read-only for this slice), empty ([groups] empty after loading), and content ([groups] populated).
 *
 * [groups] is the (identity, set)-collapsed collection ([groupByCard], shared/core-model) —
 * multiple raw entries (different languages/conditions of the same printing) collapse into one
 * [CollectionCardGroup], matching Android's Collection screen and the shape
 * [com.mmg.manahub.core.ui.components.CardListItem]'s dedicated overload already expects.
 * [sections] is [groups] bucketed per [groupingMode] via [groupCollection] — ALWAYS at least one
 * section when [groups] is non-empty (even [CollectionGroupingMode.NONE] returns one section with
 * a blank `labelToken`, per that function's own contract); [CollectionScreen] renders a section
 * header only when [groupingMode] != [CollectionGroupingMode.NONE].
 */
data class CollectionUiState(
    val groups: List<CollectionCardGroup> = emptyList(),
    val sections: List<CollectionSection> = emptyList(),
    val isLoading: Boolean = true,
    val viewMode: CollectionViewMode = CollectionViewMode.GRID,
    val groupingMode: CollectionGroupingMode = CollectionGroupingMode.NONE,
)

/**
 * Backs [CollectionScreen] — the fourth REAL web MVP screen (web roadmap W3d), proving
 * [UserCardRepository] (`WebUserCardRepository`) works end-to-end against real Supabase data: a
 * card added via [com.mmg.manahub.web.search.CardSearchViewModel.addToCollection] must appear here
 * AND survive a full page reload (the `WebUserCardRepository` cache is session-scoped, so a reload
 * re-hydrates from Supabase, not from anything cached locally).
 *
 * Read-only over collection CONTENT for this slice (no quantity edit / remove UI yet) —
 * deliberately minimal, mirroring [com.mmg.manahub.web.decks.DeckListScreen]'s scope discipline.
 * [setViewMode]/[setGroupingMode] are the two write paths this ViewModel owns: both persist through
 * [UserPreferencesRepository] on the same Koin singleton
 * [com.mmg.manahub.web.settings.SettingsViewModel] writes through, so this screen's own inline
 * controls and the Settings screen's pickers always agree.
 *
 * **Grouping-wiring slice**: [UserCardRepository.observeCollection] is combined with
 * [UserPreferencesRepository.collectionGroupingModeFlow] (mirrors how [viewMode] is already read
 * from [UserPreferencesRepository.collectionViewModeFlow] in a second `init` coroutine) — every
 * collection change AND every grouping-mode change recomputes [CollectionUiState.groups]/[sections]
 * via the pure, commonMain [groupByCard]/[groupCollection] pair (`shared/core-model`). No sort
 * order is applied before grouping (this screen has no sort feature yet, unlike Android's
 * `CollectionViewModel`) — [groupCollection]'s "already sorted by the caller" contract only affects
 * relative order WITHIN a section, not correctness.
 */
class CollectionViewModel(
    private val userCardRepository: UserCardRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userCardRepository.observeCollection(),
                userPreferencesRepository.collectionGroupingModeFlow,
            ) { cards, mode -> cards to mode }
                .collect { (cards, mode) ->
                    val groups = cards.groupByCard()
                    val sections = groupCollection(groups, mode)
                    _uiState.update {
                        it.copy(
                            groups = groups,
                            sections = sections,
                            groupingMode = mode,
                            isLoading = false,
                        )
                    }
                }
        }
        viewModelScope.launch {
            userPreferencesRepository.collectionViewModeFlow.collect { mode ->
                _uiState.update { it.copy(viewMode = mode) }
            }
        }
    }

    fun setViewMode(mode: CollectionViewMode) {
        viewModelScope.launch { userPreferencesRepository.saveCollectionViewMode(mode) }
    }

    fun setGroupingMode(mode: CollectionGroupingMode) {
        viewModelScope.launch { userPreferencesRepository.saveCollectionGroupingMode(mode) }
    }
}
