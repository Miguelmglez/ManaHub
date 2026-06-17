package com.mmg.manahub.feature.decks.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
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
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun onSelectTab(tab: DeckStudioTab) = _uiState.update { it.copy(selectedTab = tab) }
    fun toggleMainboard() = _uiState.update { it.copy(mainboardExpanded = !it.mainboardExpanded) }
    fun toggleSideboard() = _uiState.update { it.copy(sideboardExpanded = !it.sideboardExpanded) }
    fun setGroupingMode(mode: GroupingMode) = _uiState.update { it.copy(groupingMode = mode) }

    // ── Manual mutations (write straight through the repository) ───────────────

    /** Adds one copy of [scryfallId] to the deck (resolving + caching its Card). */
    fun addCardToDeck(scryfallId: String, isSideboard: Boolean = false) {
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
        viewModelScope.launch {
            runCatching { deckRepository.removeCardFromDeck(deckId, scryfallId, isSideboard) }
                .onFailure { logFailure("deck_studio_delete_failed", it) }
        }
    }

    fun moveQuantityToSideboard(scryfallId: String, quantity: Int = 1) {
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
    //  Phase-2 Suggestions stub
    //
    //  The Suggestions tab (Deck Doctor: Health / Cut list / Add list, budget-
    //  aware, incremental recompute) lands in Phase 2 (P2-T2/P2-T3). For Phase 1
    //  the tab renders a "coming soon" placeholder and this VM holds no Deck
    //  Doctor use-cases. When Phase 2 wires in:
    //   - inject EvaluateDeckUseCase / SuggestCutsUseCase /
    //     SuggestAddsWithBudgetUseCase / InferDeckIdentityUseCase,
    //   - duplicate the AnalysisCache / GapSignature incremental pattern from
    //     DeckImprovementViewModel (U6 — do NOT extract a shared class),
    //   - add budget free-text state (rawPerCardText/rawTotalText/budgetError) per
    //     U7 and the parse-on-change guard.
    // ─────────────────────────────────────────────────────────────────────────

    private fun logFailure(tag: String, t: Throwable) {
        FirebaseCrashlytics.getInstance().apply {
            log("$tag: deckId=${if (::deckId.isInitialized) deckId else "uninitialized"}")
            recordException(RuntimeException("[DeckStudio] $tag", t))
        }
    }
}
