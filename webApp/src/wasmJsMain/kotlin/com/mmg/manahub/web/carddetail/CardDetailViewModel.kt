package com.mmg.manahub.web.carddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [CardDetailScreen] (web roadmap W4b) — covers the three states relevant to this
 * read-only slice (CLAUDE.md requirement): loading ([isLoading]), error ([error]), and content
 * ([card] non-null once resolved). There is no "empty" state for a single-card detail view — a
 * missing/unresolvable id surfaces via [error] instead.
 */
data class CardDetailUiState(
    val isLoading: Boolean = true,
    val card: Card? = null,
    val error: String? = null,
)

/**
 * Backs [CardDetailScreen] — the fifth REAL web MVP screen (web roadmap W4b), a DELIBERATELY
 * minimal, read-only card detail view. Resolves [scryfallId] via [CardRepository.getCardById]
 * (already implemented for real on `WebCardRepository`, web roadmap W3b) — no new repository work.
 *
 * Scope boundary (see CLAUDE.md's "Card versions & languages" section for the full Android scope
 * this deliberately does NOT port): no tag editing, no language/set/print switching, no art
 * variants, no rulings, no "add to collection from here" — this screen only renders what
 * [CardRepository.getCardById] already returns. Adding to the collection stays on
 * [com.mmg.manahub.web.search.CardSearchScreen]'s existing tap-to-add flow (web roadmap W3d).
 *
 * [scryfallId] is a Koin-injected constructor parameter (`parametersOf(scryfallId)` from the
 * screen, via `webAppKoinModule`'s `viewModel { params -> ... }` factory) rather than resolved
 * from a `SavedStateHandle` the way Android's `CardDetailViewModel` does — `:webApp` has no
 * `koin-androidx-compose`/`SavedStateHandle` integration (Android-only), and the CMP
 * `navigation-compose-multiplatform` route object already carries the id cleanly (see
 * `WebNavGraph.kt`'s `CardDetailRoute`).
 */
class CardDetailViewModel(
    private val scryfallId: String,
    private val cardRepository: CardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = cardRepository.getCardById(scryfallId)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(isLoading = false, card = result.data, error = null)
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }
}
