package com.mmg.manahub.feature.decks.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.GroupingMode
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import com.mmg.manahub.feature.decks.domain.engine.DeckMagicEngine
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.MagicDiscovery
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.toScoreWeights
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.BuildDeckFromSeedsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferredIdentity
import com.mmg.manahub.feature.decks.domain.usecase.SeedDeckResult
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.queryFragment
import com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.Companion.MAX_SEED_CARDS
import com.mmg.manahub.core.domain.repository.WishlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** True when the deck is Commander format and the commander card is not legendary (C5). */
    val isCommanderInvalid: Boolean = false,

    // ── Basic-land suggestions (C4) ───────────────────────────────────────────
    /** Per-color basic-land add/remove deltas suggested by [BasicLandCalculator]. */
    val landDeltas: List<LandDelta> = emptyList(),
    /** Whether the land-suggestion strip is shown in the Lands group (default on). */
    val showLandSuggestions: Boolean = true,

    // ── Per-card construction warnings (C5) ───────────────────────────────────
    /** Mainboard scryfallIds that exceed the format copy limit (non-basics only). */
    val overLimitCards: Set<String> = emptySet(),
    /** Slots the user has acknowledged as intentional deviations (over-limit / off-identity). */
    val acknowledgedOverLimitCards: Set<String> = emptySet(),
    /** Scryfallids outside the commander's color identity (Commander format only). */
    val invalidColorIdentityCards: Set<String> = emptySet(),

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

    // ── Seed-build flow (Phase 3) ─────────────────────────────────────────────
    /** Whether the "Build from seed" sheet is visible. */
    val showSeedSheet: Boolean = false,
    /** Currently picked seed cards (de-duped by scryfallId). */
    val seedCards: List<Card> = emptyList(),
    /** Seed-search text. */
    val seedQuery: String = "",
    /** Scryfall search results for the current seed query. */
    val seedSearchResults: List<Card> = emptyList(),
    /** True while a debounced seed search is in flight. */
    val isSearchingSeeds: Boolean = false,
    /** Inferred identity from the picked seeds (null when no seeds). */
    val inferredIdentity: InferredIdentity? = null,
    /** True while the seed deck is being generated + written. */
    val isGenerating: Boolean = false,

    // ── Inspirations (Discoveries, Phase 4) ───────────────────────────────────
    /** Collection-synergy discoveries (Inspirations surface, Phase 4). Empty until loaded. */
    val discoveries: List<MagicDiscovery> = emptyList(),
    /** Whether the Inspirations (Discoveries) bottom sheet is visible. */
    val showInspirations: Boolean = false,
    /** True while discoveries are being computed off the collection. */
    val isLoadingDiscoveries: Boolean = false,

    // ── Import (Group B) ──────────────────────────────────────────────────────
    /** True while a pasted deck list is being resolved + written into the live draft. */
    val isImporting: Boolean = false,
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
@OptIn(ExperimentalCoroutinesApi::class)
class DeckStudioViewModel(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val searchCardsUseCase: SearchCardsUseCase,
    private val suggestTagsUseCase: SuggestTagsUseCase,
    private val evaluateDeckUseCase: EvaluateDeckUseCase,
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase,
    private val suggestCutsUseCase: SuggestCutsUseCase,
    private val suggestAddsWithBudgetUseCase: SuggestAddsWithBudgetUseCase,
    private val buildDeckFromSeedsUseCase: BuildDeckFromSeedsUseCase,
    private val getDeckGameStatsUseCase: GetDeckGameStatsUseCase,
    private val importDeckUseCase: ImportDeckUseCase,
    private val deckMagicEngine: DeckMagicEngine,
    private val wishlistRepository: WishlistRepository,
    private val userPreferences: UserPreferencesDataStore,
    private val appContext: Context,
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

    /**
     * Per-deck game statistics for the [DeckStatsCard], kept independent of [uiState]
     * so a stats update never invalidates the editor state machine.
     *
     * Unlike [DeckMagicDetailViewModel], the live `deckId` here is resolved
     * ASYNCHRONOUSLY in [init] (it may be a freshly created draft), so this flow keys
     * off `uiState.deck?.id` — which becomes non-null only after [observeDeck] emits,
     * i.e. once `deckId` exists — rather than off a synchronous SavedStateHandle id.
     * Emits null until the deck loads and the first Room query fires.
     */
    val deckStatsFlow: StateFlow<GetDeckGameStatsUseCase.Result?> =
        _uiState
            .map { it.deck?.id }
            .distinctUntilChanged()
            .filterNotNull()
            .flatMapLatest { id -> getDeckGameStatsUseCase(id) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /** The app user's player name, used to compute win/loss in [DeckStatsCard]. */
    val playerNameFlow: StateFlow<String> = userPreferences.playerNameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    /** The live deck id. Resolved on init; either passed in or created as a draft. */
    private lateinit var deckId: String

    /**
     * True only when THIS ViewModel created a brand-new draft deck on entry (the
     * no-`deckId` path). It gates the discard-if-empty contract in [onExitRequested]:
     * an EXISTING deck passed in via `savedStateHandle` must NEVER be auto-deleted,
     * even if it is empty and happens to share the default name.
     */
    private var createdFreshDraft: Boolean = false

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
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (existingId != null) {
                deckId = existingId
                crashlytics.log("deck_studio_opened_existing")
                crashlytics.setCustomKey("deck_studio_deck_id", existingId)
            } else {
                // A failed draft creation must NOT leave `deckId` uninitialized — that
                // would strand the screen on an infinite spinner and later throw
                // UninitializedPropertyAccessException on the first mutation. Bail out.
                val createdId = runCatching {
                    deckRepository.createDeck(
                        name = defaultDeckName,
                        description = "Draft",
                        format = "casual",
                    )
                }.getOrElse { e ->
                    crashlytics.log("deck_studio_init_create_failed")
                    crashlytics.recordException(RuntimeException("[DeckStudio] deck_studio_init_create_failed", e))
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(DeckStudioEvent.ShowToast(appContext.getString(R.string.deck_studio_create_failed)))
                    return@launch
                }
                deckId = createdId
                // Mark this as a VM-created draft so onExitRequested may discard it if
                // abandoned empty. Set ONLY after a successful create (the early
                // return@launch above leaves it false), and never in the existingId branch.
                createdFreshDraft = true
                crashlytics.log("deck_studio_created")
                crashlytics.setCustomKey("deck_studio_deck_id", createdId)
            }
            observeDeck()
            observeCollection()
            // Inspirations (Phase 4): compute collection-synergy discoveries on a SEPARATE
            // launch so they never block the (more important) deck load above. Gated on a
            // successfully resolved `deckId` — a failed draft creation returns early above,
            // so discoveries must NOT run for a deck that never came into existence.
            if (::deckId.isInitialized) loadDiscoveries()
        }
    }

    /**
     * Computes collection-synergy discoveries off the user's collection for the
     * Inspirations surface (Phase 4). Mirrors `DeckMagicViewModel.loadDiscoveries()`:
     * feeds the RAW `observeCollection()` items (List<UserCardWithCard>) to the engine.
     * A failure logs + records and leaves the discovery list empty — never fatal.
     */
    private fun loadDiscoveries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDiscoveries = true) }
            runCatching {
                val collection = userCardRepository.observeCollection().first()
                deckMagicEngine.discoverSynergies(collection)
            }.onSuccess { discoveries ->
                _uiState.update { it.copy(discoveries = discoveries, isLoadingDiscoveries = false) }
            }.onFailure { t ->
                FirebaseCrashlytics.getInstance().apply {
                    log("deck_studio_discovery_seeding_failed")
                    recordException(RuntimeException("[DeckStudio] deck_studio_discovery_seeding_failed", t))
                }
                _uiState.update { it.copy(isLoadingDiscoveries = false) }
            }
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

        // C5: mainboard non-basics whose total copies exceed the format limit.
        val overLimit = mainEntries
            .groupBy { it.scryfallId }
            .filter { (_, slots) ->
                val card = slots.first().card
                card != null && !BasicLandCalculator.isBasicLand(card) &&
                    slots.sumOf { it.quantity } > (format?.maxCopies ?: 4)
            }
            .keys

        // C5: cards outside the commander's color identity (Commander format only).
        val commanderCard = commanderEntry?.card
        val commanderColorIdentity = commanderCard?.colorIdentity?.toSet()
        val invalidIdentity = if (isCommanderFormat && commanderColorIdentity != null) {
            otherEntries
                .filter { entry ->
                    val entryCard = entry.card
                    entryCard != null && !commanderColorIdentity.containsAll(entryCard.colorIdentity)
                }
                .map { it.scryfallId }
                .toSet()
        } else {
            emptySet()
        }

        // C5: a Commander format with a non-legendary commander card.
        val isCommanderInvalid = isCommanderFormat && commanderCard != null &&
            !commanderCard.typeLine.contains("Legendary", ignoreCase = true)

        _uiState.update { s ->
            s.copy(
                deck = deck,
                cards = otherEntries,
                commanderCard = commanderEntry,
                isCommanderInvalid = isCommanderInvalid,
                isLoading = false,
                totalCards = mainEntries.sumOf { it.quantity },
                manaCurve = calculateManaCurve(allEntries),
                landDeltas = calculateLandDeltas(
                    entries = allEntries,
                    formatName = deck.format,
                    commanderIdentity = commanderColorIdentity,
                ),
                overLimitCards = overLimit,
                invalidColorIdentityCards = invalidIdentity,
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

    // ── Basic-land suggestions (C4) ─────────────────────────────────────────────

    /**
     * Computes the per-color basic-land deltas between the [BasicLandCalculator] recommendation
     * and the deck's current basic-land counts. A positive delta = add that many of the land; a
     * negative delta = remove that many. Ported from [DeckMagicDetailViewModel.calculateLandDeltas]
     * (identical math; this VM only reads from resolved [DeckSlotEntry]s instead of an in-memory map).
     */
    private fun calculateLandDeltas(
        entries: List<DeckSlotEntry>,
        formatName: String,
        commanderIdentity: Set<String>? = null,
    ): List<LandDelta> {
        val format = DeckFormat.entries.firstOrNull { it.name.equals(formatName, ignoreCase = true) }
            ?: DeckFormat.CASUAL

        val deckCards = entries.filter { it.card != null && !it.isSideboard }
            .map { DeckCard(it.card!!, it.quantity, isOwned = true) }

        val nonBasicLands = deckCards.filter { !BasicLandCalculator.isBasicLand(it.card) && BasicLandCalculator.isLand(it.card) }
        val mainboardNonLands = deckCards.filter { !BasicLandCalculator.isLand(it.card) }

        val suggestedMap = BasicLandCalculator.calculate(
            mainboard = mainboardNonLands,
            nonBasicLands = nonBasicLands,
            format = format,
            commanderIdentity = commanderIdentity,
        ).toMap()

        val currentCounts = mutableMapOf<String, Int>()
        entries.filter { it.card != null && !it.isSideboard && BasicLandCalculator.isBasicLand(it.card!!) }
            .forEach { currentCounts[it.card!!.name] = (currentCounts[it.card!!.name] ?: 0) + it.quantity }

        val deltas = mutableListOf<LandDelta>()
        BasicLandCalculator.LAND_FOR_COLOR.forEach { (symbol, landName) ->
            val suggestedCount = suggestedMap[symbol] ?: 0
            val currentCount = currentCounts[landName] ?: 0
            if (suggestedCount != currentCount) {
                deltas.add(LandDelta(landName, symbol, suggestedCount - currentCount))
            }
        }
        return deltas
    }

    /**
     * Applies every current [DeckStudioUiState.landDeltas] entry to the live deck by writing
     * the resulting ABSOLUTE quantity through the repository (this VM is write-through; there is
     * no in-memory draft to mutate). Positive deltas add the land (searching Scryfall for its
     * printing when no mainboard slot exists yet); negative deltas reduce / remove the slot.
     */
    fun applyLandSuggestions() {
        invalidateSuggestions()
        viewModelScope.launch {
            runCatching {
                for (delta in _uiState.value.landDeltas) {
                    val existing = _uiState.value.cards.find { it.card?.name == delta.landName && !it.isSideboard }
                    when {
                        delta.delta > 0 -> {
                            val scryfallId = existing?.scryfallId
                                ?: (cardRepository.searchCardByName(delta.landName) as? DataResult.Success)?.data
                                    ?.also { card -> cardCache = cardCache + (card.scryfallId to card) }
                                    ?.scryfallId
                                ?: continue
                            val currentQty = currentQuantity(scryfallId, false)
                            deckRepository.addCardToDeck(deckId, scryfallId, currentQty + delta.delta, false)
                        }
                        delta.delta < 0 && existing != null -> {
                            val newQty = currentQuantity(existing.scryfallId, false) + delta.delta
                            if (newQty <= 0) deckRepository.removeCardFromDeck(deckId, existing.scryfallId, false)
                            else deckRepository.addCardToDeck(deckId, existing.scryfallId, newQty, false)
                        }
                    }
                }
            }.onFailure { logFailure("deck_studio_apply_land_suggestions_failed", it) }
        }
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

    /** Toggles the basic-land suggestion strip in the Lands group (C4). */
    fun toggleLandSuggestions() = _uiState.update { it.copy(showLandSuggestions = !it.showLandSuggestions) }

    /** Marks a slot's over-limit / off-identity deviation as acknowledged (C5). */
    fun acknowledgeOverLimit(scryfallId: String) =
        _uiState.update { it.copy(acknowledgedOverLimitCards = it.acknowledgedOverLimitCards + scryfallId) }

    /** Clears a slot's deviation acknowledgement (C5). */
    fun unacknowledgeOverLimit(scryfallId: String) =
        _uiState.update { it.copy(acknowledgedOverLimitCards = it.acknowledgedOverLimitCards - scryfallId) }

    // ── Manual mutations (write straight through the repository) ───────────────

    /** Adds one copy of [scryfallId] to the deck (resolving + caching its Card). */
    fun addCardToDeck(scryfallId: String, isSideboard: Boolean = false) {
        invalidateSuggestions()
        viewModelScope.launch {
            // resolveCard hits getCardById, which can throw. Keep it INSIDE the
            // protected block so a lookup failure logs + falls back to an unresolved
            // add rather than escaping and cancelling this coroutine (which silently
            // dropped the add). A null card here is non-fatal: the slot is still
            // written and the Card resolves on the next observe rebuild.
            runCatching {
                val card = (_uiState.value.addCardsResults + _uiState.value.scryfallResults)
                    .find { it.card.scryfallId == scryfallId }?.card
                    ?: _uiState.value.cards.find { it.scryfallId == scryfallId }?.card
                    ?: resolveCard(scryfallId)
                if (card != null) cardCache = cardCache + (scryfallId to card)

                val currentQty = currentQuantity(scryfallId, isSideboard)
                deckRepository.addCardToDeck(deckId, scryfallId, currentQty + 1, isSideboard)
            }.onFailure {
                logFailure("deck_studio_add_failed", it)
                _events.send(DeckStudioEvent.ShowToast(appContext.getString(R.string.deck_studio_add_failed)))
            }
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
            // H4: a single atomic repo write — the old two-write sequence let Room
            // re-emit an intermediate state (copies briefly in neither board), which the
            // editor rendered as a flicker. The repo no-ops when there are no mainboard
            // copies, so the prior mainQty<=0 guard is now redundant.
            runCatching {
                deckRepository.moveCardQuantity(deckId, scryfallId, fromSideboard = false, quantity = quantity)
            }.onFailure { logFailure("deck_studio_move_to_side_failed", it) }
        }
    }

    fun moveQuantityToMainboard(scryfallId: String, quantity: Int = 1) {
        invalidateSuggestions()
        viewModelScope.launch {
            // H4: atomic single-transaction move (see moveQuantityToSideboard).
            runCatching {
                deckRepository.moveCardQuantity(deckId, scryfallId, fromSideboard = true, quantity = quantity)
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
            // searchCardByName can throw — keep it inside the protected block so a
            // lookup failure logs instead of cancelling the coroutine.
            runCatching {
                // M3: when a mainboard slot for this land name already exists, reuse its
                // scryfallId directly (avoiding an unnecessary network search and a possible
                // printing mismatch); only search Scryfall when no slot exists yet. A slot
                // whose Card is still unresolved still carries its scryfallId, so we increment
                // by id even when `existing.card` is null.
                val existing = _uiState.value.cards.find { it.card?.name == name && !it.isSideboard }
                val scryfallId = existing?.scryfallId
                    ?: (cardRepository.searchCardByName(name) as? DataResult.Success)?.data
                        ?.also { card -> cardCache = cardCache + (card.scryfallId to card) }
                        ?.scryfallId
                if (scryfallId != null) {
                    val currentQty = currentQuantity(scryfallId, false)
                    deckRepository.addCardToDeck(deckId, scryfallId, currentQty + 1, false)
                }
            }.onFailure { logFailure("deck_studio_add_land_failed", it) }
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

    /**
     * Changes the live deck's format (Group B / B1). Writes through the repository
     * (mirroring [updateDeckName] / [setCoverCard]) then invalidates the loaded
     * Suggestions analysis so a stale per-format evaluation isn't reused.
     *
     * A format change deliberately does NOT touch the discard-if-empty gate
     * ([onExitRequested]): a freshly created draft is "casual", and a user who only
     * picks a format but adds no cards must STILL discard on exit.
     */
    fun changeFormat(format: DeckFormat) {
        // No-op when the format is unchanged (avoids a wasted write + analysis invalidation).
        if (deckFormat == format) return
        viewModelScope.launch {
            val deck = _uiState.value.deck ?: return@launch
            runCatching {
                deckRepository.updateDeck(deck.copy(format = format.name, updatedAt = System.currentTimeMillis()))
            }.onFailure { logFailure("deck_studio_change_format_failed", it) }
        }
        // Invalidate AFTER scheduling the write so the next Suggestions open re-analyses
        // against the new format (mirrors every other manual mutation).
        invalidateSuggestions()
    }

    // ── Import (Group B / B2) ───────────────────────────────────────────────────

    /**
     * Imports a pasted Moxfield / Arena deck list INTO the live draft deck via
     * [ImportDeckUseCase]. The observe flow rebuilds the card list automatically once
     * the writes land; an [isImporting] guard blocks exit (see [onExitRequested]) so a
     * half-imported deck can't be discarded or kept mid-write.
     */
    fun importDeck(text: String) {
        if (text.isBlank()) return
        if (!::deckId.isInitialized) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            FirebaseCrashlytics.getInstance().log("deck_studio_import_started")
            importDeckUseCase(deckId, text)
                .onSuccess {
                    // Invalidate so the next Suggestions open re-analyses the imported cards.
                    invalidateSuggestions()
                }
                .onFailure { t ->
                    logFailure("deck_studio_import_failed", t)
                    _events.send(DeckStudioEvent.ShowToast(appContext.getString(R.string.deck_studio_import_failed)))
                }
            _uiState.update { it.copy(isImporting = false) }
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
                is DataResult.Error -> {
                    // No Throwable is carried by DataResult.Error — log only (no recordException).
                    FirebaseCrashlytics.getInstance().apply {
                        log("deck_studio_search_scryfall_error")
                        deckFormat?.let { setCustomKey("deck_studio_format", it.name) }
                    }
                    emptyList()
                }
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
     * Discard applies ONLY to a fresh draft this ViewModel created on entry
     * ([createdFreshDraft]); an EXISTING deck opened via `savedStateHandle` is never
     * auto-deleted, even if it is empty and shares the default name. When discardable
     * and still empty with the untouched default name, the draft is deleted so an
     * abandoned session leaves no orphan; otherwise it is kept.
     * [onNavigateBack] is invoked ONLY after the (optional) delete completes — we
     * never navigate-then-delete, and the delete runs in [viewModelScope] so the
     * main thread is never blocked.
     */
    fun onExitRequested(onNavigateBack: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            // Block exit while an import is in flight: discarding now could delete a deck the
            // import is still writing into, and keeping now could strand a half-imported deck.
            if (state.isImporting) {
                _events.send(DeckStudioEvent.ShowToast(appContext.getString(R.string.deck_studio_import_in_progress)))
                return@launch
            }
            val deck = state.deck
            val shouldDiscard = createdFreshDraft &&
                state.isEmptyDeck &&
                deck != null &&
                deck.name == defaultDeckName
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (shouldDiscard && ::deckId.isInitialized) {
                crashlytics.log("deck_studio_draft_discarded")
                runCatching { deckRepository.deleteDeck(deckId) }
                    .onFailure { logFailure("deck_studio_discard_failed", it) }
            } else {
                crashlytics.log("deck_studio_draft_kept")
            }
            // H5: navigate via the direct callback ONLY. Previously we ALSO emitted
            // DeckStudioEvent.NavigateBack, but the screen ignores that event (navigation
            // is driven by this callback), so emitting it was a latent double-pop risk.
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

    /**
     * The in-flight ADD recompute job (H3). A budget edit, an incremental recompute, and the
     * initial analysis can all race to recompute the external pool; each writes
     * `analysisCache.externalPool`. Tracking + cancel-before-launch (mirroring [analysisJob])
     * guarantees only the latest recompute survives, so a stale slower fetch can't clobber the
     * cache or emit a stale ADD list.
     */
    private var recomputeAddsJob: Job? = null

    /** The debounced seed-search job (Phase 3); cancelled on each new keystroke / sheet close. */
    private var seedSearchJob: Job? = null

    /** The in-flight seed-generation job (Phase 3); cancelled when the seed sheet is closed (M6). */
    private var generateJob: Job? = null

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
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("deck_studio_suggestions_analysis_started")
            _uiState.update { it.copy(isSuggestionsLoading = true, suggestionsLoaded = true) }

            val deckWithCards = deckRepository.observeDeckWithCards(deckId).first()
            if (deckWithCards == null) {
                crashlytics.log("deck_studio_suggestions_analysis_aborted")
                _uiState.update { it.copy(isSuggestionsLoading = false) }
                return@launch
            }

            val collection = userCardRepository.observeCollection().first()
            val format = DeckFormat.entries
                .firstOrNull { it.name.equals(deckWithCards.deck.format, ignoreCase = true) }
                ?: DeckFormat.CASUAL

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

            if (unresolvedCount > 0) {
                crashlytics.setCustomKey("deck_studio_card_count", mainboardEntries.sumOf { it.quantity } + unresolvedCount)
            }
            crashlytics.log("deck_studio_suggestions_analysis_succeeded")

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
        // Cancel any in-flight recompute so two racing fetches can't both write
        // context.externalPool / emit a stale ADD list (H3).
        recomputeAddsJob?.cancel()
        recomputeAddsJob = viewModelScope.launch {
            _uiState.update { it.copy(isAddsLoading = true) }
            val mainboardIds = context.workingMainboard.map { it.card.scryfallId }.toSet()
            val mainboardCopiesByName = context.workingMainboard
                .groupBy { it.card.name }
                .mapValues { (_, entries) -> entries.sumOf { it.quantity } }
            val weights = userPreferences.observeScoreWeightOverrides().first().toScoreWeights()
            val result = runCatching {
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
            }
            val selection = result.getOrNull()

            if (selection == null) {
                val error = result.exceptionOrNull()
                FirebaseCrashlytics.getInstance().apply {
                    log("deck_studio_external_pool_failed")
                    setCustomKey("deck_studio_format", context.format.name)
                    setCustomKey("deck_studio_card_count", context.workingMainboard.sumOf { it.quantity })
                    if (error != null) {
                        recordException(RuntimeException("[DeckStudio] deck_studio_external_pool_failed", error))
                    }
                }
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
        // M1: `toDoubleOrNull()` accepts "Infinity"/"NaN" and zero/negative values, all of
        // which BudgetConstraints rejects in its init block (a thrown IAE down below). Treat a
        // non-finite or non-positive amount as a parse error here so we keep the last valid
        // budget WITHOUT ever invoking the throwing constructor.
        val perCard = state.rawPerCardText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
        val total = state.rawTotalText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
        // A non-blank-but-unparseable field (e.g. "1.2.3") is an error without ever
        // calling the throwing constructor.
        val perCardBlank = state.rawPerCardText.isBlank()
        val totalBlank = state.rawTotalText.isBlank()
        if ((!perCardBlank && perCard == null) || (!totalBlank && total == null)) {
            logBudgetParseError("non_numeric")
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
            logBudgetParseError("non_positive_or_constructor_rejected")
            _uiState.update { it.copy(budgetError = true) }
        }
    }

    private fun logBudgetParseError(type: String) {
        FirebaseCrashlytics.getInstance().apply {
            log("deck_studio_budget_parse_error")
            setCustomKey("deck_studio_budget_error_type", type)
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
            // C1: cut ONE copy, not the whole slot. A 3-of must become a 2-of (mirrors the
            // Build-tab decrement). Previously this removed the entire slot, silently dropping
            // every copy — a data-loss bug for multi-copy 60-card decks.
            val currentQty = context?.workingMainboard
                ?.firstOrNull { it.card.scryfallId == scryfallId }?.quantity
                ?: currentQuantity(scryfallId, false)
            runCatching {
                if (currentQty <= 1) deckRepository.removeCardFromDeck(deckId, scryfallId, false)
                else deckRepository.addCardToDeck(deckId, scryfallId, currentQty - 1, false)
            }.onFailure { logFailure("deck_studio_suggestion_cut_failed", it); return@launch }
            _events.send(DeckStudioEvent.CardCut(cardName))

            if (context == null) {
                loadAnalysis()
                return@launch
            }
            // Decrement the in-memory working entry in parallel; drop the entry only at qty 0.
            context.workingMainboard = context.workingMainboard.mapNotNull { entry ->
                if (entry.card.scryfallId != scryfallId) entry
                else if (entry.quantity <= 1) null
                else entry.copy(quantity = entry.quantity - 1)
            }
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
            recomputeAddsJob?.cancel()
            analysisCache = null
            // M9: clear any stale budget parse error too, so the next time the user opens
            // Suggestions the inline error doesn't linger from a previous editing session.
            _uiState.update { it.copy(suggestionsLoaded = false, budgetError = false) }
        }
    }

    // ── Seed-build flow (Phase 3) ─────────────────────────────────────────────

    fun openSeedSheet() {
        _uiState.update { it.copy(showSeedSheet = true) }
    }

    fun closeSeedSheet() {
        seedSearchJob?.cancel()
        // M6: cancel any in-flight generation so dismissing the sheet doesn't leave a build
        // running (which would later write cards into a deck the user backed out of) and
        // reset the spinner.
        generateJob?.cancel()
        _uiState.update {
            it.copy(
                showSeedSheet = false,
                seedQuery = "",
                seedSearchResults = emptyList(),
                isSearchingSeeds = false,
                isGenerating = false,
            )
        }
    }

    /** Debounced Scryfall seed search (mirrors DeckMagicViewModel.onSeedQueryChange). */
    fun onSeedQueryChange(query: String) {
        _uiState.update { it.copy(seedQuery = query) }
        seedSearchJob?.cancel()
        if (query.trim().length < SEED_QUERY_MIN_LENGTH) {
            _uiState.update { it.copy(seedSearchResults = emptyList(), isSearchingSeeds = false) }
            return
        }
        seedSearchJob = viewModelScope.launch {
            delay(SEED_SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingSeeds = true) }
            val results = when (val res = searchCardsUseCase(query.trim())) {
                is DataResult.Success -> res.data
                is DataResult.Error -> emptyList()
            }
            _uiState.update { it.copy(seedSearchResults = results, isSearchingSeeds = false) }
        }
    }

    /** Adds a seed (de-duped by scryfallId) and re-infers identity. */
    fun addSeed(card: Card) {
        // M7: compute the inferred identity OUTSIDE the update lambda. _uiState.update may
        // re-run its block under contention, and inferDeckIdentityUseCase is non-trivial work
        // that must not execute more than once per state change.
        val current = _uiState.value.seedCards
        if (current.any { it.scryfallId == card.scryfallId }) return
        val seeds = current + card
        val identity = inferDeckIdentityUseCase(seeds)
        _uiState.update { it.copy(seedCards = seeds, inferredIdentity = identity) }
    }

    /** Removes a seed and re-infers identity (null when empty). */
    fun removeSeed(card: Card) {
        // M7: identity inference computed outside the update lambda (see addSeed).
        val seeds = _uiState.value.seedCards.filterNot { it.scryfallId == card.scryfallId }
        val identity = if (seeds.isEmpty()) null else inferDeckIdentityUseCase(seeds)
        _uiState.update { it.copy(seedCards = seeds, inferredIdentity = identity) }
    }

    /**
     * Generates a deck from the picked seeds and writes it INTO the live draft deck.
     *
     * Double-tap guard: the snapshot is captured atomically INSIDE [_uiState.update] so two
     * rapid taps can't both pass the `isGenerating` check (mirrors DeckMagicViewModel).
     *
     * On success the seeds are cleared, the sheet is closed and the Build tab is selected; the
     * caller's [onComplete] fires the SUCCESS toast with the number of cards written. On failure
     * the sheet stays open so the user can retry.
     *
     * @param onComplete invoked with the number of mainboard cards actually written.
     */
    fun generateFromSeeds(onComplete: (Int) -> Unit) {
        var captured: DeckStudioUiState? = null
        _uiState.update { s ->
            if (s.seedCards.isEmpty() || s.isGenerating) return@update s
            captured = s
            s.copy(isGenerating = true)
        }
        val snapshot = captured ?: return

        // L5: a failed draft creation returns early in init without initializing `deckId`.
        // Guard the lateinit access here so a generate tap on a half-created studio surfaces a
        // toast instead of throwing UninitializedPropertyAccessException.
        if (!::deckId.isInitialized) {
            _uiState.update { it.copy(isGenerating = false) }
            viewModelScope.launch {
                _events.send(DeckStudioEvent.ShowToast(appContext.getString(R.string.deck_studio_seed_build_failed)))
            }
            return
        }

        generateJob = viewModelScope.launch {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("deck_studio_seed_build_started")

            val writtenCount = runCatching {
                val format = deckFormat ?: DeckFormat.CASUAL
                val identity = snapshot.inferredIdentity ?: inferDeckIdentityUseCase(snapshot.seedCards)
                // REUSE the Phase-2 free-text budget state (never a separate budget here).
                val constraints = snapshot.budgetConstraints
                val collection = userCardRepository.observeCollection().first().map { it.card }
                val weights = userPreferences.observeScoreWeightOverrides().first().toScoreWeights()
                val seedResult: SeedDeckResult = buildDeckFromSeedsUseCase(
                    seeds = snapshot.seedCards,
                    identity = identity,
                    format = format,
                    constraints = constraints,
                    collection = collection,
                    weights = weights,
                )

                // U8: write straight through the repository, one card per copy. A coroutine
                // cancellation mid-loop leaves a PARTIAL write — this is ACCEPTABLE (the live
                // deck is the source of truth and the user reviews the result on the Build tab);
                // we intentionally do NOT add a batch repo method.
                var written = 0
                seedResult.mainboard.forEach { magicCard ->
                    val card = magicCard.card
                    // Cache the generated card so the observe rebuild resolves it without a re-fetch.
                    cardCache = cardCache + (card.scryfallId to card)
                    // H1: write the engine-recommended copy count, not a hard-coded 1 (the seed
                    // engine may recommend multiples of a card in 60-card formats).
                    val copies = magicCard.quantity.coerceAtLeast(1)
                    deckRepository.addCardToDeck(deckId, card.scryfallId, copies, false)
                    written += copies
                }
                // seedResult.reservedLandSlots land slots are INTENTIONALLY left for the user to
                // fill via the existing BasicLandsSheet: BuildDeckFromSeedsUseCase deliberately does
                // NOT materialize lands (no color-distribution logic lives in the studio), so
                // auto-adding basics here would invent a mana base. Per the use-case contract,
                // lands are out of scope and `written` is the spell count only.
                written
            }.getOrElse { t ->
                logFailure("deck_studio_seed_build_failed", t)
                _uiState.update { it.copy(isGenerating = false) }
                // Sheet stays open so the user can retry.
                _events.send(DeckStudioEvent.ShowToast(appContext.getString(R.string.deck_studio_seed_build_failed)))
                return@launch
            }

            // Refresh suggestions lazily: the next Suggestions open re-runs loadAnalysis()
            // against the new cards (consistent with every other manual mutation). Done
            // unconditionally — even a zero-card build invalidates the prior analysis.
            invalidateSuggestions()

            // M5: a successful build that wrote zero cards means the engine found nothing
            // within the active budget (distinct from an engine error, handled in getOrElse
            // above). Surface a budget-specific toast and keep the sheet open so the user can
            // raise the budget / add seeds, instead of silently closing on an empty result.
            if (writtenCount == 0) {
                crashlytics.log("deck_studio_seed_build_no_results")
                _uiState.update { it.copy(isGenerating = false) }
                _events.send(DeckStudioEvent.ShowToast(appContext.getString(R.string.deck_studio_seed_build_no_results)))
                return@launch
            }

            crashlytics.log("deck_studio_seed_build_succeeded")
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    showSeedSheet = false,
                    seedCards = emptyList(),
                    seedQuery = "",
                    seedSearchResults = emptyList(),
                    inferredIdentity = null,
                    selectedTab = DeckStudioTab.BUILD,
                )
            }
            onComplete(writtenCount)
        }
    }

    // ── Inspirations (Discoveries, Phase 4) ───────────────────────────────────

    /** Opens the Inspirations (Discoveries) bottom sheet. */
    fun openInspirations() {
        FirebaseCrashlytics.getInstance().log("deck_studio_inspirations_opened")
        _uiState.update { it.copy(showInspirations = true) }
    }

    /** Closes the Inspirations (Discoveries) bottom sheet. */
    fun closeInspirations() {
        _uiState.update { it.copy(showInspirations = false) }
    }

    /**
     * Seeds the studio from a collection discovery: pre-populates [DeckStudioUiState.seedCards]
     * from the discovery's cards (de-duped and capped at [MAX_SEED_CARDS]), sets the inferred
     * identity from those seeds, closes the Inspirations sheet, and OPENS the seed sheet.
     *
     * The user still taps Generate — we deliberately do NOT auto-generate (R3/U9). We re-infer
     * identity from the seed cards (consistent with [addSeed]/[removeSeed]) rather than using
     * the discovery's `primaryTag` directly, so the seed sheet shows the same identity shape.
     */
    fun startFromDiscovery(discovery: MagicDiscovery) {
        // H2: MERGE the discovery's cards into any seeds the user already picked (de-duped by
        // scryfallId) rather than overwriting them — opening Inspirations after manually adding
        // seeds must not silently discard the manual picks. Capped at MAX_SEED_CARDS.
        val discoverySeeds = discovery.cards.map { it.card }
        val seeds = (_uiState.value.seedCards + discoverySeeds)
            .distinctBy { it.scryfallId }
            .take(MAX_SEED_CARDS)
        // M7: identity inference computed outside the update lambda (see addSeed).
        val identity = if (seeds.isEmpty()) null else inferDeckIdentityUseCase(seeds)
        _uiState.update {
            it.copy(
                showInspirations = false,
                showSeedSheet = true,
                seedCards = seeds,
                inferredIdentity = identity,
                seedQuery = "",
                seedSearchResults = emptyList(),
            )
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
            // Non-PII context to triage the failure (format, deck size, active tab).
            deckFormat?.let { setCustomKey("deck_studio_format", it.name) }
            setCustomKey("deck_studio_card_count", _uiState.value.totalCards)
            setCustomKey("deck_studio_active_tab", _uiState.value.selectedTab.name)
            recordException(RuntimeException("[DeckStudio] $tag", t))
        }
    }

    private companion object {
        /** Identity tag categories used to rank inference seed cards (mirrors the scorer's set). */
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)

        /** Cap on auto-selected identity seed cards (plus the commander) so one card can't skew the seed. */
        const val MAX_SEED_CARDS = 8

        /** Minimum seed-query length before a Scryfall search fires (mirrors DeckMagicViewModel). */
        const val SEED_QUERY_MIN_LENGTH = 2

        /** Debounce before a seed search runs, in ms (mirrors DeckMagicViewModel). */
        const val SEED_SEARCH_DEBOUNCE_MS = 400L
    }
}
