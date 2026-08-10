package com.mmg.manahub.web.deckeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.web.common.toUserFacingMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A resolved mainboard/sideboard row for [DeckEditorScreen] -- pairs a [DeckRepository]-owned
 * [com.mmg.manahub.core.model.DeckSlot] (scryfallId + quantity) with its joined [Card] data, the
 * same "resolve a second cache via the sibling [CardRepository] singleton" pattern
 * `WebUserCardRepository` established (web roadmap W3d). [card] is `null` until the batch
 * resolution in [DeckEditorViewModel.ensureCardsResolved] completes -- the row still renders (by
 * scryfallId) rather than being hidden, since it's better UX to show "resolving..." than to make a
 * card silently disappear from its own deck.
 */
data class DeckEditorCardRow(
    val scryfallId: String,
    val quantity: Int,
    val card: Card?,
)

/**
 * UI state for [DeckEditorScreen] (web roadmap W4c) -- covers the states CLAUDE.md requires:
 * loading ([isLoading]), content ([deck] non-null), and the search sub-flow's own
 * loading/error/content ([isSearching]/[searchError]/[searchResults]). There is no distinct "empty"
 * top-level state -- an empty mainboard/sideboard is valid content, rendered via [EmptyState] at the
 * section level in the screen.
 *
 * **Known Web v1 gap (documented, matches the eventual-consistency pattern already established by
 * every other repo-backed web screen this session)**: [deck] stays `null` while
 * [DeckRepository.observeDeckWithCards] hasn't yet resolved a live row for [deckId] -- this covers
 * BOTH "still loading" (session/cache hydration in flight) and "genuinely doesn't exist" with the
 * same state, since [com.mmg.manahub.core.data.repository.WebDeckRepository] has no way to
 * distinguish the two synchronously. In practice this only matters for a stale/bad deep link -- the
 * primary navigation path (tapping a row in [com.mmg.manahub.web.decks.DeckListScreen]) always
 * passes an id that is already hydrated in the SAME session's `decksCache`.
 */
data class DeckEditorUiState(
    val isLoading: Boolean = true,
    val deck: Deck? = null,
    val mainboard: List<DeckEditorCardRow> = emptyList(),
    val sideboard: List<DeckEditorCardRow> = emptyList(),
    val nameDraft: String = "",
    val actionMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Card> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
)

/**
 * Curated format subset for the deck editor's format picker -- mirrors Android's
 * `STUDIO_FORMATS` (`app/.../decks/presentation/components/DeckEditorComponents.kt`), which is
 * itself deliberately restricted to the three formats the app's construction-validation rules
 * fully support today. [Deck.format] is a raw lowercase string (`DeckFormat.name.lowercase()`,
 * matching the convention [com.mmg.manahub.web.decks.DeckListViewModel.createDeck] already
 * established: `format = "casual"`), resolved back to a [DeckFormat] via `entries.firstOrNull`
 * (never `.valueOf()`, per the standing CLAUDE.md convention) rather than a bare string compare.
 */
val EDITOR_FORMATS: List<DeckFormat> = listOf(DeckFormat.COMMANDER, DeckFormat.CASUAL, DeckFormat.DRAFT)

/**
 * Backs [DeckEditorScreen] -- the SIXTH real `:webApp` MVP screen (web roadmap W4c), a
 * deliberately minimal deck editor scoped WAY down from Android's Deck Studio (see the screen's
 * own KDoc for the exact scope boundary). Proves [DeckRepository] (`WebDeckRepository`, real since
 * W3c) end-to-end for card add/remove/quantity mutation and metadata (rename/format) edits -- no
 * new repository work was needed for this slice.
 *
 * [deckId] is a Koin runtime constructor parameter (`parametersOf(deckId)`), the same pattern
 * [com.mmg.manahub.web.carddetail.CardDetailViewModel] established for [scryfallId] in W4b --
 * `:webApp` has no `SavedStateHandle` integration, and the CMP nav route object already carries
 * the id directly (see `WebNavGraph.kt`'s `DeckEditorRoute`).
 *
 * The add-card flow does NOT reuse [com.mmg.manahub.web.search.CardSearchViewModel]/
 * `CardSearchScreen` -- a compact, screen-local search (calling [CardRepository.searchCardsPaginated]
 * directly, the same real method the standalone Search tab uses) is less invasive than adding a
 * nav-arg-driven "target deck" mode to the already-verified, W3d/W4b-proven Search-tab flow, per the
 * task brief's own steer. Card rows in both the search-results list and the mainboard/sideboard
 * lists reuse the shared, already-`commonMain` `CardListItem` general-purpose overload
 * (`core-ui/components/CardListItem.kt`) rather than a fresh tile type.
 */
class DeckEditorViewModel(
    private val deckId: String,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckEditorUiState())
    val uiState: StateFlow<DeckEditorUiState> = _uiState.asStateFlow()

    /** Resolved [Card] data per scryfall id seen in this deck's slots. Session-scoped, never persisted. */
    private val cardsCache = MutableStateFlow<Map<String, Card>>(emptyMap())

    /** True once [DeckEditorUiState.nameDraft] has been seeded from the deck's real name once --
     * guards against a live [DeckRepository.observeDeckWithCards] re-emission (e.g. after a card
     * add) clobbering in-progress rename text the user hasn't committed yet. */
    private var nameDraftSeeded = false

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                deckRepository.observeDeckWithCards(deckId),
                cardsCache,
            ) { deckWithCards, cards -> deckWithCards to cards }
                .collect { (deckWithCards, cards) ->
                    if (deckWithCards == null) {
                        _uiState.update { it.copy(isLoading = false) }
                        return@collect
                    }
                    val allIds = (deckWithCards.mainboard + deckWithCards.sideboard).map { it.scryfallId }
                    ensureCardsResolved(allIds)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            deck = deckWithCards.deck,
                            nameDraft = if (nameDraftSeeded) state.nameDraft else deckWithCards.deck.name,
                            mainboard = deckWithCards.mainboard.map { slot ->
                                DeckEditorCardRow(slot.scryfallId, slot.quantity, cards[slot.scryfallId])
                            },
                            sideboard = deckWithCards.sideboard.map { slot ->
                                DeckEditorCardRow(slot.scryfallId, slot.quantity, cards[slot.scryfallId])
                            },
                        )
                    }
                    nameDraftSeeded = true
                }
        }
    }

    private fun ensureCardsResolved(scryfallIds: List<String>) {
        val missing = scryfallIds.distinct().filterNot { cardsCache.value.containsKey(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            cardRepository.warmCacheForIds(missing)
            val resolved = cardRepository.getCardsByIds(missing)
            if (resolved.isNotEmpty()) cardsCache.update { it + resolved.associateBy(Card::scryfallId) }
        }
    }

    // ── Metadata edits ───────────────────────────────────────────────────────

    fun onNameDraftChange(name: String) {
        _uiState.update { it.copy(nameDraft = name) }
    }

    /** Commits [DeckEditorUiState.nameDraft] if it differs from the deck's current name. No-op when blank/unchanged. */
    fun commitRename() {
        val deck = _uiState.value.deck ?: return
        val newName = _uiState.value.nameDraft.trim()
        if (newName.isEmpty() || newName == deck.name) return
        viewModelScope.launch {
            try {
                deckRepository.updateDeck(deck.copy(name = newName))
            } catch (e: Throwable) {
                _uiState.update { it.copy(actionMessage = e.toUserFacingMessage("rename the deck", crashReporter)) }
            }
        }
    }

    fun changeFormat(format: DeckFormat) {
        val deck = _uiState.value.deck ?: return
        val raw = format.name.lowercase()
        if (deck.format.equals(raw, ignoreCase = true)) return
        viewModelScope.launch {
            try {
                deckRepository.updateDeck(deck.copy(format = raw))
            } catch (e: Throwable) {
                _uiState.update { it.copy(actionMessage = e.toUserFacingMessage("change the format", crashReporter)) }
            }
        }
    }

    // ── Add-card search ──────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun searchCards() {
        val query = _uiState.value.searchQuery.trim()
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null, isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            when (val result = cardRepository.searchCardsPaginated(query, page = 1)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(isSearching = false, searchResults = result.data.cards, searchError = null)
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(isSearching = false, searchError = result.message)
                }
            }
        }
    }

    /** Adds one copy of [card] to the mainboard (existing stack quantity + 1, or a fresh 1-copy stack). */
    fun addCardToMainboard(card: Card) {
        viewModelScope.launch {
            try {
                val existingQuantity = _uiState.value.mainboard.firstOrNull { it.scryfallId == card.scryfallId }?.quantity ?: 0
                deckRepository.addCardToDeck(
                    deckId = deckId,
                    scryfallId = card.scryfallId,
                    quantity = existingQuantity + 1,
                    isSideboard = false,
                )
                _uiState.update { it.copy(actionMessage = "Added ${card.name} to the mainboard.") }
            } catch (e: Throwable) {
                _uiState.update { it.copy(actionMessage = e.toUserFacingMessage("add ${card.name}", crashReporter)) }
            }
        }
    }

    // ── Mainboard / sideboard quantity + removal ─────────────────────────────

    fun incrementQuantity(row: DeckEditorCardRow, isSideboard: Boolean) {
        viewModelScope.launch {
            try {
                deckRepository.addCardToDeck(
                    deckId = deckId,
                    scryfallId = row.scryfallId,
                    quantity = row.quantity + 1,
                    isSideboard = isSideboard,
                )
            } catch (e: Throwable) {
                _uiState.update { it.copy(actionMessage = e.toUserFacingMessage("update the quantity", crashReporter)) }
            }
        }
    }

    /** Decrements [row]'s quantity by one, removing the slot entirely once it would reach zero. */
    fun decrementQuantity(row: DeckEditorCardRow, isSideboard: Boolean) {
        viewModelScope.launch {
            try {
                if (row.quantity <= 1) {
                    deckRepository.removeCardFromDeck(deckId, row.scryfallId, isSideboard)
                } else {
                    deckRepository.addCardToDeck(
                        deckId = deckId,
                        scryfallId = row.scryfallId,
                        quantity = row.quantity - 1,
                        isSideboard = isSideboard,
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(actionMessage = e.toUserFacingMessage("update the quantity", crashReporter)) }
            }
        }
    }

    fun removeCard(row: DeckEditorCardRow, isSideboard: Boolean) {
        viewModelScope.launch {
            try {
                deckRepository.removeCardFromDeck(deckId, row.scryfallId, isSideboard)
            } catch (e: Throwable) {
                _uiState.update { it.copy(actionMessage = e.toUserFacingMessage("remove the card", crashReporter)) }
            }
        }
    }
}
