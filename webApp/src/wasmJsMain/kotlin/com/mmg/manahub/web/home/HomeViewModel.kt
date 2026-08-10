package com.mmg.manahub.web.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.UserCardWithCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How many rows each "recent" strip shows -- deliberately small, this is a dashboard, not a list screen. */
private const val RECENT_LIMIT = 5

/**
 * UI state for [HomeScreen] (web roadmap W4d) -- covers the three states relevant to this
 * read-only dashboard (CLAUDE.md requirement): loading (before the first combined emission),
 * empty (both strips empty -- a genuinely new account with no decks/cards yet, handled per-section
 * rather than as one all-or-nothing empty screen, since the quick-links header is always useful),
 * and content.
 */
data class HomeUiState(
    val recentDecks: List<DeckSummary> = emptyList(),
    val recentCards: List<UserCardWithCard> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Backs [HomeScreen] -- the seventh REAL web MVP screen (web roadmap W4d), and the last of the
 * master plan's originally-scoped MVP screen list. Deliberately NOT a port of Android's 17-widget
 * customizable Home board (`feature/home/`, CLAUDE.md's "Home widget board" section) -- that is a
 * multi-phase gamification/trades/community system entirely out of scope here. Per this slice's
 * no-stub mandate, both strips below are 100% real, wired data (zero placeholder content): each is
 * a straightforward reuse of an ALREADY-real repository read method
 * ([DeckRepository.observeAllDeckSummaries] since W3c, [UserCardRepository.observeCollection]
 * since W3d), just sorted client-side by recency and capped to [RECENT_LIMIT] -- no new repository
 * method, no new backend work.
 */
class HomeViewModel(
    private val deckRepository: DeckRepository,
    private val userCardRepository: UserCardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                deckRepository.observeAllDeckSummaries(),
                userCardRepository.observeCollection(),
            ) { decks, cards ->
                HomeUiState(
                    recentDecks = decks.sortedByDescending { it.createdAt }.take(RECENT_LIMIT),
                    recentCards = cards.sortedByDescending { it.userCard.createdAt }.take(RECENT_LIMIT),
                    isLoading = false,
                )
            }.collect { state -> _uiState.update { state } }
        }
    }
}
