package com.mmg.manahub.feature.decks.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.AddCardRow
import com.mmg.manahub.core.domain.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.DeckSlotEntry
import com.mmg.manahub.core.domain.model.GroupingMode
import com.mmg.manahub.core.domain.model.TagCategory
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.toScoreWeights
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.queryFragment
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One-shot side effects emitted by [DeckStudioViewModel].
 *
 * Delivered through a buffered [Channel] (collected via `receiveAsFlow()`) rather
 * than a nullable [MutableStateFlow]: a StateFlow equality-collapses repeated
 * events and drops them while the lifecycle is paused.
 */
sealed interface DeckStudioEvent {
    /** Navigate up. Emitted after any discard-if-empty cleanup completes. */
    data object NavigateBack : DeckStudioEvent

    /** Show a transient toast. */
    data class ShowToast(val message: String) : DeckStudioEvent

    /** A card was added via the Suggestions surface (carries the card name for the toast). */
    data class CardAdded(val cardName: String) : DeckStudioEvent

    /** A card was cut via the Suggestions surface (carries the card name for the toast). */
    data class CardCut(val cardName: String) : DeckStudioEvent

    /**
     * The external (Scryfall) candidate fetch failed; suggestions fell back to collection + wishlist.
     * The UI surfaces this as a non-fatal warning toast.
     */
    data object ExternalPoolFailed : DeckStudioEvent
}

/**
 * Which tab of the Deck Studio is active.
 *
 * BUILD is the manual editor (Phase 1). SUGGESTIONS is a Phase-2 stub.
 */
enum class DeckStudioTab { BUILD, SUGGESTIONS }

/**
 * UI state for the unified Deck Studio editor surface (Phase 1).
 *
 * The Suggestions surface fields are intentionally absent here — they land with
 * the Deck Doctor wiring in Phase 2.
 */
data class DeckStudioUiState(
    val deck: Deck? = null,
    val cards: List<DeckSlotEntry> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTab: DeckStudioTab = DeckStudioTab.BUILD,
    val groupingMode: GroupingMode = GroupingMode.TYPE,
    val totalCards: Int = 0,
    val manaCurve: Map<Int, Int> = emptyMap(),
    val collectionIds: Set<String> = emptySet(),

    val mainboardExpanded: Boolean = true,
    val sideboardExpanded: Boolean = false,

    // ── Search / Add cards state ──────────────────────────────────────────────
    val addCardsQuery: String = "",
    val addCardsResults: List<AddCardRow> = emptyList(),
    val isSearchingCards: Boolean = false,
    val scryfallResults: List<AddCardRow> = emptyList(),
    val isSearchingScryfall: Boolean = false,

    // ── Commander (Commander format only) ─────────────────────────────────────
    val commanderCard: DeckSlotEntry? = null,

    // ── Card detail sheet ─────────────────────────────────────────────────────
    val detailTags: List<CardTag> = emptyList(),

    // ── Suggestions surface (Deck Doctor inline, Phase 2) ─────────────────────
    /** Read-only Health evaluation from the scoring engine. Null until first computed. */
    val health: DeckHealth? = null,
    /** Cut candidates (worst fit first), excluding lands / commander / combo cores. */
    val cuts: List<CardFit> = emptyList(),
    /** Add suggestions (collection + wishlist + external), budget-filtered, best fit first. */
    val adds: List<AddSuggestion> = emptyList(),
    /** Total € the currently shown adds would cost to buy (owned/free cards excluded). */
    val addsTotalCostEur: Double = 0.0,
    /** How many of the shown adds have a non-zero price (i.e. need buying). */
    val addsCardsToBuy: Int = 0,
    /** True while the full analysis (Health + Cut + Add) is being computed. */
    val isSuggestionsLoading: Boolean = false,
    /** True while only the external (Scryfall) ADD pool is being fetched/recomputed. */
    val isAddsLoading: Boolean = false,
    /** True once the Suggestions surface has been opened at least once (lazy first analysis). */
    val suggestionsLoaded: Boolean = false,

    // ── Free-text budget state (U7) ───────────────────────────────────────────
    /** Raw per-card € text exactly as typed (may be blank or invalid mid-typing). */
    val rawPerCardText: String = "",
    /** Raw total € text exactly as typed (may be blank or invalid mid-typing). */
    val rawTotalText: String = "",
    /** Whether owned cards are treated as 0 € (mirrors [BudgetConstraints.ownedCardsAreFree]). */
    val ownedCardsAreFree: Boolean = true,
    /** The LAST VALID parsed budget (default unconstrained); never an invalid object. */
    val budgetConstraints: BudgetConstraints = BudgetConstraints(),
    /** True when the current raw text failed to parse — the inline error is shown and the last valid budget is kept. */
    val budgetError: Boolean = false,
) {
    val isEmptyDeck: Boolean get() = cards.isEmpty() && commanderCard == null
}

/**
 * Drives the unified Deck Studio editor against a single live draft deck.
 *
 * Unlike [DeckMagicDetailViewModel] (an in-memory draft that flushes to Room on
 * exit), every manual operation writes straight through [DeckRepository] so the
 * live `deckId` is always the source of truth. [observeDeckWithCards] re-emits and
 * rebuilds the UI after each write.
 *
 * Phase 1 scope: manual editing (add/remove/+/-/move/basic-lands/commander/
 * metadata/export) + the discard-if-empty exit contract. The Suggestions surface
 * (Deck Doctor) is a Phase-2 stub — see [onSelectTab].
 *
 * Process-death note (U4): an empty default-named draft orphaned by a process
 * kill before [onExitRequested] runs is OUT OF SCOPE. Such orphans are cleaned
 * manually from the Decks list; there is no `isDraft` column or schema change.
 */
@HiltViewModel
class DeckStudioViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val searchCardsUseCase: SearchCardsUseCase,
    private val suggestTagsUseCase: SuggestTagsUseCase,
    private val evaluateDeckUseCase: EvaluateDeckUseCase,
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase,
    private val suggestCutsUseCase: SuggestCutsUseCase,
    private val suggestAddsWithBudgetUseCase: SuggestAddsWithBudgetUseCase,
    private val wishlistRepository: WishlistRepository,
    private val userPreferences: UserPreferencesDataStore,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * The default deck name used for a freshly created draft. Held here so the
     * discard-if-empty predicate ([onExitRequested]) can compare against the exact
     * resolved string the deck was created with.
     */
    private val defaultDeckName: String = appContext.getString(R.string.deck_studio_default_name)

    private val _uiState = MutableStateFlow(DeckStudioUiState())
    val uiState: StateFlow<DeckStudioUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeckStudioEvent>(Channel.BUFFERED)
    val events: Flow<DeckStudioEvent> = _events.receiveAsFlow()

    /** The live deck id. Resolved on init; either passed in or created as a draft. */
    private lateinit var deckId: String

    /** Card data cache (scryfallId → Card) to avoid re-fetching on each rebuild. */
    private var cardCache: Map<String, Card> = emptyMap()

    /** The user's collection, used to populate the "owned" search tab. */
    private var collectionCards: List<Card> = emptyList()

    /** The resolved [DeckFormat] of the live deck (never via [DeckFormat.valueOf]). */
    private val deckFormat: DeckFormat?
        get() = _uiState.value.deck?.format?.let { fmt ->
            DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) }
        }

    init {
        // Nav passes "" (not null) for an absent optional StringType arg → treat
        // blank as "create a fresh draft".
        val existingId = savedStateHandle.get<String?>("deckId")?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            deckId = existingId ?: deckRepository.createDeck(
                name = defaultDeckName,
                description = "Draft",
                format = "casual",
            )
            observeDeck()
            observeCollection()
        }
    }

    // ── Observation ─────────────────────────────────────────────────────────────

    private fun observeDeck() {
        deckRepository.observeDeckWithCards(deckId)
            .onEach { deckWithCards ->
                if (deckWithCards == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@onEach
                }

                val mainEntries = deckWithCards.mainboard.map { slot ->
                    DeckSlotEntry(slot.scryfallId, slot.quantity, false, resolveCard(slot.scryfallId))
                }
                val sideEntries = deckWithCards.sideboard.map { slot ->
                    DeckSlotEntry(slot.scryfallId, slot.quantity, true, resolveCard(slot.scryfallId))
                }
                val allEntries = mainEntries + sideEntries
                cardCache = cardCache + allEntries.mapNotNull { it.card }.associateBy { it.scryfallId }

                rebuildUiState(deckWithCards.deck, allEntries)
            }
            .launchIn(viewModelScope)
    }

    private fun observeCollection() {
        userCardRepository.observeCollection()
            .onEach { collection ->
                collectionCards = collection.map { it.card }.distinctBy { it.scryfallId }.sortedBy { it.name }
                _uiState.update { it.copy(collectionIds = collectionCards.map { c -> c.scryfallId }.toSet()) }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun resolveCard(scryfallId: String): Card? {
        cardCache[scryfallId]?.let { return it }
        return (cardRepository.getCardById(scryfallId) as? DataResult.Success)?.data
    }

    private fun rebuildUiState(deck: Deck, allEntries: List<DeckSlotEntry>) {
        val format = DeckFormat.entries.firstOrNull { it.name.equals(deck.format, ignoreCase = true) }
        val isCommanderFormat = format == DeckFormat.COMMANDER
        val commanderId = deck.commanderCardId

        val mainEntries = allEntries.filter { !it.isSideboard }

        var commanderEntry: DeckSlotEntry? = null
        val otherEntries = if (isCommanderFormat && commanderId != null) {
            commanderEntry = allEntries.find { it.scryfallId == commanderId && !it.isSideboard }
            allEntries.filter { it.scryfallId != commanderId || it.isSideboard }
        } else {
            allEntries
        }

        _uiState.update { s ->
            s.copy(
                deck = deck,
                cards = otherEntries,
                commanderCard = commanderEntry,
                isLoading = false,
                totalCards = mainEntries.sumOf { it.quantity },
                manaCurve = calculateManaCurve(allEntries),
                addCardsResults = s.addCardsResults.map { row ->
                    row.copy(quantityInDeck = quantityInMainboard(allEntries, row.card.scryfallId))
                },
                scryfallResults = s.scryfallResults.map { row ->
                    row.copy(quantityInDeck = quantityInMainboard(allEntries, row.card.scryfallId))
                },
            )
        }
    }

    private fun quantityInMainboard(entries: List<DeckSlotEntry>, scryfallId: String): Int =
        entries.find { it.scryfallId == scryfallId && !it.isSideboard }?.quantity ?: 0

    private fun calculateManaCurve(cards: List<DeckSlotEntry>): Map<Int, Int> {
        val curve = mutableMapOf<Int, Int>()
        cards.filter { it.card != null && !it.isSideboard && !BasicLandCalculator.isLand(it.card!!) }.forEach { entry ->
            val cmc = entry.card!!.cmc.toInt().coerceIn(0, 7)
            curve[cmc] = (curve[cmc] ?: 0) + entry.quantity
        }
        return curve
    }

    // ── Tab / UI toggles ──────────────────────────────────────────────────────

    fun onSelectTab(tab: DeckStudioTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        // Lazily run the first Deck Doctor analysis the first time the user opens
        // Suggestions — never on init (keeps Phase-1 "straight into the editor" fast
        // and avoids a Scryfall call for a deck the user may never analyse).
        if (tab == DeckStudioTab.SUGGESTIONS && !_uiState.value.suggestionsLoaded && ::deckId.isInitialized) {
            loadAnalysis()
        }
    }
    fun toggleMainboard() = _uiState.update { it.copy(mainboardExpanded = !it.mainboardExpanded) }
    fun toggleSideboard() = _uiState.update { it.copy(sideboardExpanded = !it.sideboardExpanded) }
    fun setGroupingMode(mode: GroupingMode) = _uiState.update { it.copy(groupingMode = mode) }

    // ── Manual mutations (write straight through the repository) ───────────────

    /** Adds one copy of [scryfallId] to the deck (resolving + caching its Card). */
    fun addCardToDeck(scryfallId: String, isSideboard: Boolean = false) {
        invalidateSuggestions()
        viewModelScope.launch {
            val card = (_uiState.value.addCardsResults + _uiState.value.scryfallResults)
                .find { it.card.scryfallId == scryfallId }?.card
                ?: _uiState.value.cards.find { it.scryfallId == scryfallId }?.card
                ?: resolveCard(scryfallId)
            if (card != null) cardCache = cardCache + (scryfallId to card)

            val currentQty = currentQuantity(scryfallId, isSideboard)
            runCatching {
                deckRepository.addCardToDeck(deckId, scryfallId, currentQty + 1, isSideboard)
            }.onFailure { logFailure("deck_studio_add_failed", it) }
        }
    }

    /** Removes one copy of [scryfallId]; deletes the slot when it hits zero. */
    fun removeCardFromDeck(scryfallId: String, isSideboard: Boolean = false) {
        invalidateSuggestions()
        viewModelScope.launch {
            val currentQty = currentQuantity(scryfallId, isSideboard)
            if (currentQty <= 1) {
                runCatching { deckRepository.removeCardFromDeck(deckId, scryfallId, isSideboard) }
                    .onFailure { logFailure("deck_studio_remove_failed", it) }
            } else {
                runCatching { deckRepository.addCardToDeck(deckId, scryfallId, currentQty - 1, isSideboard) }
                    .onFailure { logFailure("deck_studio_decrement_failed", it) }
            }
        }
    }

    /** Removes a card slot entirely (the "delete" action in the detail sheet). */
    fun removeCard(scryfallId: String, isSideboard: Boolean = false) {
        invalidateSuggestions()
        viewModelScope.launch {
            runCatching { deckRepository.removeCardFromDeck(deckId, scryfallId, isSideboard) }
                .onFailure { logFailure("deck_studio_delete_failed", it) }
        }
    }

    fun moveQuantityToSideboard(scryfallId: String, quantity: Int = 1) {
        invalidateSuggestions()
        viewModelScope.launch {
            val mainQty = currentQuantity(scryfallId, false)
            if (mainQty <= 0) return@launch
            val toMove = quantity.coerceIn(1, mainQty)
            val newMain = mainQty - toMove
            runCatching {
                if (newMain <= 0) deckRepository.removeCardFromDeck(deckId, scryfallId, false)
                else deckRepository.addCardToDeck(deckId, scryfallId, newMain, false)
                val sideQty = currentQuantity(scryfallId, true)
                deckRepository.addCardToDeck(deckId, scryfallId, sideQty + toMove, true)
            }.onFailure { logFailure("deck_studio_move_to_side_failed", it) }
        }
    }

    fun moveQuantityToMainboard(scryfallId: String, quantity: Int = 1) {
        invalidateSuggestions()
        viewModelScope.launch {
            val sideQty = currentQuantity(scryfallId, true)
            if (sideQty <= 0) return@launch
            val toMove = quantity.coerceIn(1, sideQty)
            val newSide = sideQty - toMove
            runCatching {
                if (newSide <= 0) deckRepository.removeCardFromDeck(deckId, scryfallId, true)
                else deckRepository.addCardToDeck(deckId, scryfallId, newSide, true)
                val mainQty = currentQuantity(scryfallId, false)
                deckRepository.addCardToDeck(deckId, scryfallId, mainQty + toMove, false)
            }.onFailure { logFailure("deck_studio_move_to_main_failed", it) }
        }
    }

    private fun currentQuantity(scryfallId: String, isSideboard: Boolean): Int {
        val s = _uiState.value
        val commander = s.commanderCard
        if (commander != null && commander.scryfallId == scryfallId && !isSideboard) return commander.quantity
        return s.cards.find { it.scryfallId == scryfallId && it.isSideboard == isSideboard }?.quantity ?: 0
    }

    // ── Basic lands ─────────────────────────────────────────────────────────────

    /** Current mainboard count for each of the five basic lands (by name). */
    fun basicLandCounts(): Map<String, Int> = BASIC_LAND_NAMES.associateWith { landName ->
        _uiState.value.cards.filter { it.card?.name == landName && !it.isSideboard }.sumOf { it.quantity }
    }

    fun addBasicLandByName(name: String) {
        invalidateSuggestions()
        viewModelScope.launch {
            val existing = _uiState.value.cards.find { it.card?.name == name && !it.isSideboard }
            val card = existing?.card ?: (cardRepository.searchCardByName(name) as? DataResult.Success)?.data
            if (card != null) {
                cardCache = cardCache + (card.scryfallId to card)
                val currentQty = currentQuantity(card.scryfallId, false)
                runCatching { deckRepository.addCardToDeck(deckId, card.scryfallId, currentQty + 1, false) }
                    .onFailure { logFailure("deck_studio_add_land_failed", it) }
            }
        }
    }

    fun removeBasicLandByName(name: String) {
        val existing = _uiState.value.cards.find { it.card?.name == name && !it.isSideboard } ?: return
        removeCardFromDeck(existing.scryfallId, false)
    }

    fun getManaCode(landName: String): String? = when (landName) {
        "Plains" -> "W"
        "Island" -> "U"
        "Swamp" -> "B"
        "Mountain" -> "R"
        "Forest" -> "G"
        else -> null
    }

    // ── Commander ─────────────────────────────────────────────────────────────

    fun setCommander(card: Card) {
        invalidateSuggestions()
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            val oldCommanderId = deck.commanderCardId
            cardCache = cardCache + (card.scryfallId to card)

            runCatching {
                deckRepository.updateDeck(
                    deck.copy(
                        commanderCardId = card.scryfallId,
                        coverCardId = card.scryfallId,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                // Ensure the commander is present in the mainboard with qty 1.
                if (currentQuantity(card.scryfallId, false) <= 0) {
                    deckRepository.addCardToDeck(deckId, card.scryfallId, 1, false)
                }
                // Drop a previous commander, and any sideboard copy of the new one.
                if (oldCommanderId != null && oldCommanderId != card.scryfallId) {
                    deckRepository.removeCardFromDeck(deckId, oldCommanderId, false)
                }
                if (currentQuantity(card.scryfallId, true) > 0) {
                    deckRepository.removeCardFromDeck(deckId, card.scryfallId, true)
                }
            }.onFailure { logFailure("deck_studio_set_commander_failed", it) }
        }
    }

    fun removeCommander() {
        invalidateSuggestions()
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            val commanderId = deck.commanderCardId ?: return@launch
            runCatching {
                deckRepository.updateDeck(deck.copy(commanderCardId = null, updatedAt = System.currentTimeMillis()))
                deckRepository.removeCardFromDeck(deckId, commanderId, false)
            }.onFailure { logFailure("deck_studio_remove_commander_failed", it) }
        }
    }

    // ── Metadata ────────────────────────────────────────────────────────────────

    fun updateDeckName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            runCatching { deckRepository.updateDeck(deck.copy(name = trimmed, updatedAt = System.currentTimeMillis())) }
                .onFailure { logFailure("deck_studio_rename_failed", it) }
        }
    }

    fun setCoverCard(scryfallId: String) {
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            runCatching { deckRepository.updateDeck(deck.copy(coverCardId = scryfallId, updatedAt = System.currentTimeMillis())) }
                .onFailure { logFailure("deck_studio_set_cover_failed", it) }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun showCollectionCards() {
        _uiState.update { s ->
            s.copy(
                addCardsResults = collectionCards.map { card ->
                    AddCardRow(card, quantityInMainboard(s.cards + listOfNotNull(s.commanderCard), card.scryfallId), isOwned = true)
                },
            )
        }
    }

    fun onAddCardsQueryChange(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            showCollectionCards()
            return
        }
        val filtered = collectionCards.filter { it.name.contains(query, ignoreCase = true) }
        _uiState.update { s ->
            s.copy(
                addCardsResults = filtered.map { card ->
                    AddCardRow(card, quantityInMainboard(s.cards + listOfNotNull(s.commanderCard), card.scryfallId), isOwned = true)
                },
            )
        }
    }

    fun searchScryfallDirect(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(scryfallResults = emptyList(), isSearchingScryfall = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingScryfall = true) }
            val cards = when (val result = searchCardsUseCase(query)) {
                is DataResult.Success -> result.data
                is DataResult.Error -> emptyList()
            }
            val ownedIds = _uiState.value.collectionIds
            _uiState.update { s ->
                s.copy(
                    isSearchingScryfall = false,
                    scryfallResults = cards.map { card ->
                        AddCardRow(
                            card = card,
                            quantityInDeck = quantityInMainboard(s.cards + listOfNotNull(s.commanderCard), card.scryfallId),
                            isOwned = card.scryfallId in ownedIds,
                        )
                    },
                )
            }
        }
    }

    /** Commander-mode search: shares Scryfall results but is invoked separately. */
    fun searchCommander(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            showCollectionCards()
            _uiState.update { it.copy(scryfallResults = emptyList(), isSearchingScryfall = false) }
            return
        }
        onAddCardsQueryChange(query)
        searchScryfallDirect(query)
    }

    fun clearAddCardsState() {
        _uiState.update { it.copy(addCardsQuery = "", addCardsResults = emptyList(), scryfallResults = emptyList()) }
    }

    // ── Card details ────────────────────────────────────────────────────────────

    fun loadCardDetails(scryfallId: String) {
        viewModelScope.launch {
            val card = (cardRepository.getCardById(scryfallId) as? DataResult.Success)?.data ?: return@launch
            val tags = if (card.tags.isNotEmpty() || card.userTags.isNotEmpty()) {
                card.tags + card.userTags
            } else {
                suggestTagsUseCase(card).confirmed
            }
            _uiState.update { it.copy(detailTags = tags.distinctBy { t -> t.key }) }
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun exportDeckToText(): String? {
        val state = _uiState.value
        val deck = state.deck ?: return null

        val commanderCard = state.commanderCard?.card
            ?: deck.commanderCardId?.let { id -> state.cards.firstOrNull { it.scryfallId == id }?.card }
        val commanderScryfallId = commanderCard?.scryfallId

        val mainDeckCards = state.cards
            .filter { !it.isSideboard && it.scryfallId != commanderScryfallId }
            .mapNotNull { dc -> dc.card?.let { card -> DeckCard(card = card, quantity = dc.quantity) } }

        val sideboardCards = state.cards
            .filter { it.isSideboard }
            .mapNotNull { dc -> dc.card?.let { card -> DeckCard(card = card, quantity = dc.quantity) } }

        return DeckImportExportHelper.export(
            deckName = deck.name,
            mainboard = mainDeckCards,
            sideboard = sideboardCards,
            commander = commanderCard,
        )
    }

    // ── Exit (discard-if-empty contract, U1/U2) ─────────────────────────────────

    /**
     * Handles a back request from the screen (back arrow OR system back).
     *
     * If the deck is still empty AND still carries the untouched default name, it
     * is deleted so an abandoned session leaves no orphan; otherwise it is kept.
     * [onNavigateBack] is invoked ONLY after the (optional) delete completes — we
     * never navigate-then-delete, and the delete runs in [viewModelScope] so the
     * main thread is never blocked.
     */
    fun onExitRequested(onNavigateBack: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val deck = state.deck
            val shouldDiscard = state.isEmptyDeck &&
                deck != null &&
                deck.name == defaultDeckName
            if (shouldDiscard && ::deckId.isInitialized) {
                runCatching { deckRepository.deleteDeck(deckId) }
                    .onFailure { logFailure("deck_studio_discard_failed", it) }
            }
            _events.send(DeckStudioEvent.NavigateBack)
            onNavigateBack()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Suggestions surface — Deck Doctor inline (Phase 2)
    //
    //  This DUPLICATES the AnalysisCache / GapSignature / loadAnalysis /
    //  recomputeIncremental / recomputeAdds incremental pattern from
    //  DeckImprovementViewModel (U6 — intentionally NOT a shared class; the two VMs
    //  have different state shapes). The external Scryfall pool is re-fetched ONLY
    //  when the queryable gap set changes; otherwise the cached pool is reused via
    //  `externalCardsOverride` (zero Scryfall calls).
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Everything an in-memory incremental re-analysis needs after the first full
     * [loadAnalysis]. A suggestion add/cut mutates [workingMainboard] and recomputes
     * profile/evaluation/cuts locally; the expensive external (Scryfall) pool is only
     * re-fetched when [gapSignature] changes. Null until the first full analysis runs.
     */
    private var analysisCache: AnalysisCache? = null

    private var analysisJob: Job? = null

    private class AnalysisCache(
        var workingMainboard: List<DeckEntry>,
        val format: DeckFormat,
        val commanderId: String?,
        val commanderIdentity: Set<String>,
        val seedTags: List<CardTag>,
        val collection: List<Card>,
        val wishlistIds: Set<String>,
        val resolvedById: MutableMap<String, Card>,
        val unresolvedCount: Int,
        var gapSignature: GapSignature,
        var externalPool: List<Card>,
    )

    /** Drives the external Scryfall candidate query; an unchanged signature reuses the cached pool. */
    private data class GapSignature(
        val gapRoles: Set<DeckRole>,
        val colorIdentity: Set<ManaColor>,
        val format: DeckFormat,
    )

    /**
     * Full analysis: snapshot the live deck, resolve the mainboard, infer the seed
     * tags (commander + top identity-tag cards), evaluate Health + cuts, then run the
     * ADD pipeline. Primes [analysisCache] for subsequent incremental recompute and
     * sets `suggestionsLoaded`.
     */
    private fun loadAnalysis() {
        analysisCache = null
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(isSuggestionsLoading = true, suggestionsLoaded = true) }

            val deckWithCards = deckRepository.observeDeckWithCards(deckId).first()
            if (deckWithCards == null) {
                _uiState.update { it.copy(isSuggestionsLoading = false) }
                return@launch
            }

            val collection = userCardRepository.observeCollection().first()
            val format = DeckFormat.entries
                .firstOrNull { it.name.equals(deckWithCards.deck.format, ignoreCase = true) }
                ?: DeckFormat.STANDARD

            var unresolvedCount = 0
            val mainboardEntries = deckWithCards.mainboard.mapNotNull { slot ->
                val card = resolveCard(slot.scryfallId)
                if (card == null) {
                    unresolvedCount += slot.quantity
                    null
                } else {
                    DeckEntry(card = card, quantity = slot.quantity, isOwned = false, isSideboard = false)
                }
            }

            val commanderId = deckWithCards.deck.commanderCardId
            val commanderCard = commanderId?.let { resolveCard(it) }
            val commanderIdentity = commanderCard?.colorIdentity?.toSet().orEmpty()

            val seedCards = inferenceSeeds(commanderCard, mainboardEntries)
            val seedTags = inferDeckIdentityUseCase(seedCards).seedTags
            val weights = userPreferences.observeScoreWeightOverrides().first().toScoreWeights()

            val health = evaluateDeckUseCase(
                mainboard = mainboardEntries,
                format = format,
                commanderIdentity = commanderIdentity,
                seedTags = seedTags,
                weights = weights,
            )
            val cuts = suggestCutsUseCase(
                mainboard = mainboardEntries,
                profile = health.profile,
                protectedIds = setOfNotNull(commanderId),
                weights = weights,
            )

            val collectionCards = collection.map { it.card }
            val wishlistIds = wishlistRepository.observeLocal().first().map { it.cardId }.toSet()

            val resolvedById = HashMap<String, Card>()
            mainboardEntries.forEach { resolvedById[it.card.scryfallId] = it.card }
            collectionCards.forEach { resolvedById.putIfAbsent(it.scryfallId, it) }
            commanderCard?.let { resolvedById.putIfAbsent(it.scryfallId, it) }

            analysisCache = AnalysisCache(
                workingMainboard = mainboardEntries,
                format = format,
                commanderId = commanderId,
                commanderIdentity = commanderIdentity,
                seedTags = seedTags,
                collection = collectionCards,
                wishlistIds = wishlistIds,
                resolvedById = resolvedById,
                unresolvedCount = unresolvedCount,
                gapSignature = gapSignatureOf(health),
                externalPool = emptyList(),
            )

            _uiState.update {
                it.copy(
                    health = withUnresolvedWarning(health, unresolvedCount),
                    cuts = cuts,
                    isSuggestionsLoading = false,
                )
            }
            recomputeAdds(externalOverride = null)
        }
    }

    /**
     * Recomputes the ADD suggestions from [analysisCache] using the current parsed
     * [DeckStudioUiState.budgetConstraints].
     *
     * @param externalOverride when non-null, the cached external pool is reused and NO
     *        Scryfall call is made; when null, a fresh external pool is fetched and re-cached.
     */
    private fun recomputeAdds(
        constraints: BudgetConstraints = _uiState.value.budgetConstraints,
        externalOverride: List<Card>?,
    ) {
        val context = analysisCache ?: return
        val profile = _uiState.value.health?.profile ?: return
        val evaluation = _uiState.value.health?.evaluation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAddsLoading = true) }
            val mainboardIds = context.workingMainboard.map { it.card.scryfallId }.toSet()
            val mainboardCopiesByName = context.workingMainboard
                .groupBy { it.card.name }
                .mapValues { (_, entries) -> entries.sumOf { it.quantity } }
            val weights = userPreferences.observeScoreWeightOverrides().first().toScoreWeights()
            val selection = runCatching {
                suggestAddsWithBudgetUseCase(
                    collection = context.collection,
                    wishlistIds = context.wishlistIds,
                    mainboardIds = mainboardIds,
                    profile = profile,
                    evaluation = evaluation,
                    constraints = constraints,
                    weights = weights,
                    externalCardsOverride = externalOverride,
                    mainboardCopiesByName = mainboardCopiesByName,
                )
            }.getOrNull()

            if (selection == null) {
                _uiState.update { it.copy(isAddsLoading = false) }
                _events.send(DeckStudioEvent.ExternalPoolFailed)
                return@launch
            }

            context.externalPool = selection.externalPool
            selection.selected.forEach { context.resolvedById.putIfAbsent(it.fit.card.scryfallId, it.fit.card) }

            _uiState.update {
                it.copy(
                    adds = selection.selected,
                    addsTotalCostEur = selection.totalCostEur,
                    addsCardsToBuy = selection.cardsToBuy,
                    isAddsLoading = false,
                )
            }
        }
    }

    /**
     * Re-evaluates the deck IN MEMORY from [AnalysisCache.workingMainboard] after a
     * single-card suggestion add/cut: rebuild profile/evaluation/cuts (pure), then
     * recompute ADD suggestions. The external pool is re-fetched ONLY when the queryable
     * gap set changed; otherwise the cached pool is reused (ZERO Scryfall calls).
     */
    private fun recomputeIncremental() {
        val context = analysisCache ?: return
        viewModelScope.launch {
            val mainboard = context.workingMainboard
            val weights = userPreferences.observeScoreWeightOverrides().first().toScoreWeights()
            val health = evaluateDeckUseCase(
                mainboard = mainboard,
                format = context.format,
                commanderIdentity = context.commanderIdentity,
                seedTags = context.seedTags,
                weights = weights,
            )
            val cuts = suggestCutsUseCase(
                mainboard = mainboard,
                profile = health.profile,
                protectedIds = setOfNotNull(context.commanderId),
                weights = weights,
            )
            _uiState.update {
                it.copy(
                    health = withUnresolvedWarning(health, context.unresolvedCount),
                    cuts = cuts,
                )
            }

            val newSignature = gapSignatureOf(health)
            val gapsUnchanged = newSignature == context.gapSignature
            context.gapSignature = newSignature
            recomputeAdds(externalOverride = if (gapsUnchanged) context.externalPool else null)
        }
    }

    // ── Budget free-text (U7) ─────────────────────────────────────────────────

    /**
     * Updates the raw per-card € text and re-parses the budget. The parse guard keeps
     * the LAST VALID [BudgetConstraints] and flips [DeckStudioUiState.budgetError] on an
     * invalid amount — it NEVER constructs an invalid constraints object. Blank ⇒ null cap.
     */
    fun onPerCardBudgetChange(text: String) {
        _uiState.update { it.copy(rawPerCardText = text) }
        reparseBudget()
    }

    fun onTotalBudgetChange(text: String) {
        _uiState.update { it.copy(rawTotalText = text) }
        reparseBudget()
    }

    fun onOwnedCardsFreeChange(free: Boolean) {
        _uiState.update { it.copy(ownedCardsAreFree = free) }
        reparseBudget()
    }

    /** Clears both budget fields back to "no constraint". */
    fun onClearBudget() {
        _uiState.update { it.copy(rawPerCardText = "", rawTotalText = "") }
        reparseBudget()
    }

    /**
     * Parses the current raw text into a [BudgetConstraints]. A blank field maps to a
     * null cap. [BudgetConstraints.init] THROWS on ≤0/non-finite values; on that
     * [IllegalArgumentException] we keep the previous valid budget and set
     * [DeckStudioUiState.budgetError] = true. On success we clear the error and
     * recompute the ADD suggestions (external pool re-fetched: a budget change alters
     * the external USD pre-filter).
     */
    private fun reparseBudget() {
        val state = _uiState.value
        val perCard = state.rawPerCardText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val total = state.rawTotalText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        // A non-blank-but-unparseable field (e.g. "1.2.3") is an error without ever
        // calling the throwing constructor.
        val perCardBlank = state.rawPerCardText.isBlank()
        val totalBlank = state.rawTotalText.isBlank()
        if ((!perCardBlank && perCard == null) || (!totalBlank && total == null)) {
            _uiState.update { it.copy(budgetError = true) }
            return
        }
        try {
            val constraints = BudgetConstraints(
                maxPerCardEur = perCard,
                maxTotalEur = total,
                ownedCardsAreFree = state.ownedCardsAreFree,
            )
            _uiState.update { it.copy(budgetConstraints = constraints, budgetError = false) }
            recomputeAdds(constraints = constraints, externalOverride = null)
        } catch (e: IllegalArgumentException) {
            // Keep the last valid budgetConstraints; just flag the error.
            _uiState.update { it.copy(budgetError = true) }
        }
    }

    // ── Suggestion add / cut (write-through + incremental recompute) ───────────

    /**
     * Adds one copy of a suggested card to the live deck's mainboard, then recomputes
     * INCREMENTALLY. Falls back to a full [loadAnalysis] only when the cache is missing
     * or the card cannot be resolved from any cached source.
     */
    fun onAddSuggestion(scryfallId: String, cardName: String) {
        viewModelScope.launch {
            val context = analysisCache
            val currentQty = currentQuantity(scryfallId, false)
            runCatching { deckRepository.addCardToDeck(deckId, scryfallId, currentQty + 1, false) }
                .onFailure { logFailure("deck_studio_suggestion_add_failed", it); return@launch }
            _events.send(DeckStudioEvent.CardAdded(cardName))

            if (context == null) {
                loadAnalysis()
                return@launch
            }
            val added = findCachedCard(context, scryfallId)
            if (added == null) {
                loadAnalysis()
                return@launch
            }
            val existing = context.workingMainboard.firstOrNull { it.card.scryfallId == scryfallId }
            context.workingMainboard = if (existing != null) {
                context.workingMainboard.map {
                    if (it.card.scryfallId == scryfallId) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                context.workingMainboard + DeckEntry(card = added, quantity = 1, isOwned = false, isSideboard = false)
            }
            recomputeIncremental()
        }
    }

    /**
     * Removes a cut-candidate card from the live deck's mainboard, then recomputes
     * INCREMENTALLY. Falls back to [loadAnalysis] when the cache is missing.
     */
    fun onCutSuggestion(scryfallId: String, cardName: String) {
        viewModelScope.launch {
            val context = analysisCache
            runCatching { deckRepository.removeCardFromDeck(deckId, scryfallId, false) }
                .onFailure { logFailure("deck_studio_suggestion_cut_failed", it); return@launch }
            _events.send(DeckStudioEvent.CardCut(cardName))

            if (context == null) {
                loadAnalysis()
                return@launch
            }
            context.workingMainboard = context.workingMainboard.filterNot { it.card.scryfallId == scryfallId }
            recomputeIncremental()
        }
    }

    /** Looks up a resolved [Card] in the cached add list / resolved-by-id map (no repository call). */
    private fun findCachedCard(context: AnalysisCache, scryfallId: String): Card? =
        _uiState.value.adds.firstOrNull { it.fit.card.scryfallId == scryfallId }?.fit?.card
            ?: context.resolvedById[scryfallId]

    /**
     * Invalidates the loaded analysis after a MANUAL (Build-tab) deck mutation so the
     * next time the user opens Suggestions a fresh [loadAnalysis] re-syncs with the live
     * deck. We deliberately do NOT recompute here (the work is wasted while the user is
     * still editing on the Build tab) and we NEVER trigger analysis from the deck-observe
     * transformer (that would create a write→observe→recompute feedback loop).
     */
    private fun invalidateSuggestions() {
        if (_uiState.value.suggestionsLoaded) {
            analysisJob?.cancel()
            analysisCache = null
            _uiState.update { it.copy(suggestionsLoaded = false) }
        }
    }

    /**
     * Picks the inference seed cards: the commander (when present) plus the deck's
     * highest-weight identity cards (most STRATEGY / ARCHETYPE / TRIBAL tags), capped so
     * one off-theme card can't skew the seed.
     */
    private fun inferenceSeeds(commander: Card?, mainboard: List<DeckEntry>): List<Card> {
        val ranked = mainboard
            .map { it.card }
            .filter { it.scryfallId != commander?.scryfallId }
            .map { card -> card to identityTagCount(card) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_SEED_CARDS)
            .map { it.first }
        return (listOfNotNull(commander) + ranked).distinctBy { it.scryfallId }
    }

    private fun identityTagCount(card: Card): Int =
        (card.tags + card.userTags).count { it.category in IDENTITY_CATEGORIES }

    /** The queryable gap set + identity/format that drives the external Scryfall pool. */
    private fun gapSignatureOf(health: DeckHealth): GapSignature = GapSignature(
        gapRoles = health.evaluation.roleCoverage
            .filter { it.gap > 0 && it.role.queryFragment() != null }
            .map { it.role }
            .toSet(),
        colorIdentity = health.profile.colorIdentity,
        format = health.profile.format,
    )

    /** Appends a [DeckWarning.UnresolvedCards] when one or more mainboard slots failed to resolve. */
    private fun withUnresolvedWarning(health: DeckHealth, unresolvedCount: Int): DeckHealth {
        if (unresolvedCount <= 0) return health
        val withWarning = health.evaluation.copy(
            warnings = health.evaluation.warnings + DeckWarning.UnresolvedCards(unresolvedCount)
        )
        return health.copy(evaluation = withWarning)
    }

    private fun logFailure(tag: String, t: Throwable) {
        FirebaseCrashlytics.getInstance().apply {
            log("$tag: deckId=${if (::deckId.isInitialized) deckId else "uninitialized"}")
            recordException(RuntimeException("[DeckStudio] $tag", t))
        }
    }

    private companion object {
        /** Identity tag categories used to rank inference seed cards (mirrors the scorer's set). */
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)

        /** Cap on auto-selected identity seed cards (plus the commander) so one card can't skew the seed. */
        const val MAX_SEED_CARDS = 8
    }
}
