package com.mmg.manahub.web.carddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [CardDetailScreen]. Covers the three top-level states (CLAUDE.md requirement):
 * loading ([isLoading]), error ([error]), and content ([card] non-null once resolved). There is no
 * "empty" state for a single-card detail view -- a missing/unresolvable id surfaces via [error].
 *
 * Fields added by the W4b completion slice (2026-08-05):
 * - [strategyTags]: read-only precomputed tags resolved via [CardStrategyTagsRepository] (see
 *   [CardDetailViewModel]'s KDoc). Merged with [Card.tags]/[Card.userTags] at the RENDER site
 *   ([CardDetailScreen]), never written back into [card] itself -- this repository is a
 *   supplementary, best-effort enrichment, not part of the card's own identity.
 * - Prints/languages picker ([printsAndLanguages]/[printsLoading]/[showPrintsPicker]) and art
 *   variants picker ([artVariants]/[artVariantsLoading]/[showArtVariantsPicker]) back the two new
 *   picker dialogs -- both deliberately lazy (fetched only when the user opens the picker, not on
 *   initial card load) since most card views never open either.
 */
data class CardDetailUiState(
    val isLoading: Boolean = true,
    val card: Card? = null,
    val error: String? = null,
    val strategyTags: List<CardTag> = emptyList(),
    val printsAndLanguages: List<Card> = emptyList(),
    val printsLoading: Boolean = false,
    val showPrintsPicker: Boolean = false,
    val artVariants: List<Card> = emptyList(),
    val artVariantsLoading: Boolean = false,
    val showArtVariantsPicker: Boolean = false,
)

/**
 * Backs [CardDetailScreen]. Resolves [scryfallId] via [CardRepository.getCardById] (real since web
 * roadmap W3b) -- no new repository work needed for the base card render.
 *
 * ## W4b completion slice (2026-08-05) -- language/print switching, art variants, tag DISPLAY
 * The screen was built deliberately minimal/read-only in W4b (see the class's prior KDoc, now
 * superseded). This slice adds:
 * - **Prints & languages**: [onOpenPrintsPicker] merges [CardRepository.getCardPrints] (other set
 *   printings, English by default -- no `lang:` filter) with [CardRepository.getLanguagePrints]
 *   (every language of THIS exact set+collector-number printing), deduped by [Card.scryfallId] --
 *   ONE simple combined list rather than a two-level picker, since both queries are cheap and the
 *   distinction ("different set" vs. "different language") is visible per-row anyway (set name +
 *   collector number + language flag). Both are already real [CardRepository] methods (verified
 *   before this slice -- `WebCardRepository` implements 13 of 27 interface methods for real,
 *   these three among them; no new repository work).
 * - **Art variants**: [onOpenArtVariantsPicker] calls [CardRepository.getCardArtVariants] (every
 *   PAPER printing across sets, i.e. different illustrations) -- reuses the SAME
 *   [com.mmg.manahub.web.carddetail.CardVersionPickerDialog] the prints/languages picker uses (one
 *   generic "pick a Card, navigate to its detail" component for all three data sources, per the
 *   task brief -- no three bespoke pickers).
 * - **Tag DISPLAY (read-only)**: [strategyTags] resolves the Supabase `card_strategy_tags` table
 *   via [CardStrategyTagsRepository.getStrategyTags] (already fully wired `commonMain`
 *   infrastructure -- [cardStrategyTagsRepository] returns already-resolved [CardTag] objects, not
 *   raw string keys, so no [com.mmg.manahub.core.data.tagging.TagDictionary] miss-fallback
 *   handling is needed at this call site; that resolution already happened inside
 *   `CardStrategyTagsRepositoryImpl.toFound()`). A genuine miss
 *   ([CardStrategyTagsResult.NotFound]/[CardStrategyTagsResult.Error]) or a blank `oracleId`
 *   silently leaves [strategyTags] empty -- this is supplementary data, never blocks or errors the
 *   main card render. Deliberately does NOT run the on-device fallback rule engine
 *   ([com.mmg.manahub.core.domain.usecase.card.ResolveCardStrategyTagsUseCase]) on a miss, and does
 *   NOT reuse Android's [com.mmg.manahub.core.domain.usecase.card.RefreshCardStrategyTagsUseCase]
 *   (which persists via [CardRepository.unionCardTags] -- a Room-only stub on web that would
 *   silently no-op) -- both are out of scope for a read-only display slice.
 *
 * Tag EDITING (confirming/dismissing suggested tags, custom user tags, the `custom_` key-prefix
 * CRUD system) is explicitly OUT of scope for this slice -- a genuinely separate, deeper feature
 * (an override repository + rule-syntax editor). [CardRepository]'s `updateCardTags`/
 * `unionCardTags`/`updateUserTags`/`updateSuggestedTags`/`confirmSuggestedTag`/`dismissSuggestedTag`
 * remain `WebCardRepository`'s existing loud `UnsupportedOperationException` stubs.
 *
 * [scryfallId] is a Koin-injected constructor parameter (see [CardDetailScreen]'s KDoc for why,
 * unchanged from W4b).
 */
class CardDetailViewModel(
    private val scryfallId: String,
    private val cardRepository: CardRepository,
    private val cardStrategyTagsRepository: CardStrategyTagsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = cardRepository.getCardById(scryfallId)) {
                is DataResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, card = result.data, error = null) }
                    loadStrategyTags(result.data.oracleId)
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    /** Best-effort, non-blocking: a miss/failure just leaves [CardDetailUiState.strategyTags] empty. */
    private fun loadStrategyTags(oracleId: String) {
        if (oracleId.isBlank()) return
        viewModelScope.launch {
            val result = runCatching { cardStrategyTagsRepository.getStrategyTags(oracleId) }.getOrNull()
            val tags = (result as? CardStrategyTagsResult.Found)?.tags.orEmpty()
            if (tags.isNotEmpty()) {
                _uiState.update { it.copy(strategyTags = tags) }
            }
        }
    }

    /**
     * Opens the prints/languages picker, fetching (merging + deduping by [Card.scryfallId]) the
     * current card's other set printings ([CardRepository.getCardPrints]) and every language of
     * this exact printing ([CardRepository.getLanguagePrints]). A failure on either call still
     * shows whatever the other call returned -- never blocks the whole picker on one query.
     */
    fun onOpenPrintsPicker() {
        val card = _uiState.value.card ?: return
        _uiState.update { it.copy(showPrintsPicker = true, printsLoading = true) }
        viewModelScope.launch {
            val otherPrints = (cardRepository.getCardPrints(card.name) as? DataResult.Success)?.data.orEmpty()
            val otherLanguages =
                (cardRepository.getLanguagePrints(card.setCode, card.collectorNumber) as? DataResult.Success)
                    ?.data.orEmpty()
            val merged = (otherPrints + otherLanguages).distinctBy { it.scryfallId }
            _uiState.update { it.copy(printsAndLanguages = merged, printsLoading = false) }
        }
    }

    fun onDismissPrintsPicker() {
        _uiState.update { it.copy(showPrintsPicker = false) }
    }

    /** Opens the art variants picker: every PAPER printing across sets (different illustrations). */
    fun onOpenArtVariantsPicker() {
        val card = _uiState.value.card ?: return
        _uiState.update { it.copy(showArtVariantsPicker = true, artVariantsLoading = true) }
        viewModelScope.launch {
            val variants = (cardRepository.getCardArtVariants(card.name) as? DataResult.Success)?.data.orEmpty()
            _uiState.update { it.copy(artVariants = variants, artVariantsLoading = false) }
        }
    }

    fun onDismissArtVariantsPicker() {
        _uiState.update { it.copy(showArtVariantsPicker = false) }
    }
}
