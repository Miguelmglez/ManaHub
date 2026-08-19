package com.mmg.manahub.feature.decks.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCard
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.GroupingMode
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.LandTargetResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorEvent
import com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator
import com.mmg.manahub.feature.decks.domain.orchestrator.DoctorAnalysisStage
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator
import com.mmg.manahub.feature.decks.domain.usecase.CommunityAddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.FindSimilarDecksUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckCardsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportOutcome
import com.mmg.manahub.feature.decks.domain.usecase.ImportSource
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SimilarDeckResult
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCommunityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2
import com.mmg.manahub.feature.decks.domain.template.DiscoverSynergiesV2UseCase
import com.mmg.manahub.feature.decks.domain.template.DiscoverySearchFilter
import com.mmg.manahub.feature.decks.domain.model.ComboResult
import com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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

/** Tabs of the v2 synergy browser (Deck Engine Unification plan D7, Phase 4). */
enum class InspirationsTab { STRATEGIES, COMBOS }

/**
 * A single basic-land count adjustment recommended by [BasicLandCalculator] against a deck's
 * current basic-land counts. Positive [delta] = add that many of [landName]; negative = remove.
 *
 * Originally declared in the retired `DeckMagicDetailViewModel` (deleted in the Deck Wizard &
 * Engine Rework plan, WS7.1) — moved here since [MagicLandSuggestionItem] and this ViewModel are
 * its only consumers now.
 */
data class LandDelta(
    val landName: String,
    val manaSymbol: String,
    val delta: Int,
)

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
    /** English-preferred display card for the open [CardDetailSheet] (entry-only redirect, same
     * rationale as [com.mmg.manahub.feature.carddetail.presentation.CardDetailViewModel]'s
     * loadCard() fix — the deck-list thumbnail already shows the English sibling image, so the
     * sheet must open on it too, or the image visibly flashes into the saved non-English
     * printing). Null until [loadCardDetails] resolves it; the sheet falls back to the tapped
     * card's own data while this is null / loading. */
    val detailDisplayCard: Card? = null,
    /** True while [loadCardDetails] is resolving [detailTags] + [detailDisplayCard] — the sheet
     * shows a loading placeholder instead of painting the wrong-language image for a frame. */
    val isLoadingCardDetail: Boolean = false,

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
    /** Deck Wizard & Engine Rework plan, Workstream 8.4 -- non-null while the FULL analysis pass
     * is progressing (mirrors [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorState.stage]).
     * Drives the Suggestions tab's staged progress screen (same visual language as the wizard's
     * generating step). */
    val doctorStage: DoctorAnalysisStage? = null,
    /** Stages already finished this analysis pass, oldest first -- the completed-stages checklist
     * shown under [doctorStage]'s current label. */
    val doctorCompletedStages: List<DoctorAnalysisStage> = emptyList(),

    // ── Scryfall backstop -- 3rd adds source (Deck Wizard & Engine Rework plan WS8.2) ──────────
    /** The Suggestions tab's own "include outside collection" toggle -- see
     * [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorState.includeOutsideCollection]'s
     * KDoc (a SEPARATE choice from the wizard's own per-build toggle). */
    val includeOutsideCollection: Boolean = false,
    /** True when the toggle above is on but the last Scryfall backstop fetch failed -- [adds]
     * still shows Motor A's (and Motor B's) results, this is a per-source degrade notice only. */
    val outsideCollectionUnavailable: Boolean = false,

    // ── Motor B — community suggestions (Deck Doctor Community/Archetype plan, Phase 4) ────────
    /** Whether Motor B / the Community Hub Discover surface is enabled (`communityEngineEnabledFlow`). */
    val communityEngineEnabled: Boolean = false,
    /** "Popular in similar decks" — see [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorState.communityAdds]. */
    val communityAdds: List<CommunityAddSuggestion> = emptyList(),
    /** "Decks like yours" carousel. */
    val similarDecks: List<SimilarDeckResult> = emptyList(),
    val isCommunityLoading: Boolean = false,
    val communityUnavailable: Boolean = false,

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

    // ── Inspirations (Discoveries, Phase 4) ───────────────────────────────────
    /** Discoveries (identity-only clustering: STRATEGY/ARCHETYPE tags + derived `tribe:<x>` keys)
     * populated by [discoverSynergiesV2UseCase]. The legacy ANY-tag-category `discoveries` field
     * (backed by `DeckMagicEngine.discoverSynergies`) was RETIRED in the Deck Wizard & Engine
     * Rework plan, WS7.2 (2026-07-28) — this is the only discoveries list now. */
    val discoveriesV2: List<DeckDiscoveryV2> = emptyList(),
    /** Whether the Inspirations (Discoveries) bottom sheet is visible. */
    val showInspirations: Boolean = false,
    /** True while discoveries are being computed off the collection. */
    val isLoadingDiscoveries: Boolean = false,
    /** Which tab of the v2 synergy browser is active (Deck Engine Unification plan D7, Phase 4). */
    val inspirationsTab: InspirationsTab = InspirationsTab.STRATEGIES,
    /** Free-text label search (4.2) — filters [discoveriesV2] client-side, no recomputation. */
    val discoverySearchQuery: String = "",
    /** Search-by-card picks (4.2) — a cluster survives only if it contains at least one of these
     * (by name). Populated from [DiscoverySearchFilter.pickableCardNames]. */
    val discoverySelectedCardNames: Set<String> = emptySet(),
    /** [discoveriesV2] narrowed by [discoverySearchQuery]/[discoverySelectedCardNames] — the list
     * the Strategies tab actually renders. Recomputed by [recomputeFilteredDiscoveries] whenever
     * any of the three inputs change (kept in state, not computed in the Composable, so the pure
     * [DiscoverySearchFilter] stays the single source of truth and is unit-testable via the VM). */
    val filteredDiscoveriesV2: List<DeckDiscoveryV2> = emptyList(),
    /** [discoveriesV2]'s members narrowed by the SAME [discoverySearchQuery]/
     * [discoverySelectedCardNames] filter (via [DiscoverySearchFilter.matchingCards]) -- the flat
     * "matching cards" preview shown directly under the Strategies tab search bar, kept in sync
     * with [filteredDiscoveriesV2] by [recomputeFilteredDiscoveries] so the query filters both the
     * cluster list AND this card-level preview. Empty when no search is active. */
    val discoveryMatchingCards: List<Card> = emptyList(),
    /** Commander Spellbook combo results (Deck Engine Unification plan D7, Phase 4.3). Null until
     * [loadCombos] has run at least once (lazy: only fetched on first Combos-tab selection, never
     * on sheet open, so opening Inspirations never fires a network call by itself). */
    val comboResult: ComboResult? = null,
    /** Every combo card name (across [comboResult]'s complete + almost-there variants, including
     * each [com.mmg.manahub.feature.decks.domain.model.AlmostCombo.missingCardName]) resolved to a
     * full [Card] via [CardRepository.getCardByExactName] -- populated once alongside
     * [comboResult] so the Combos tab can render real card-art tiles instead of name-only chips.
     * A name absent from this map (resolution failed/timed out) falls back to a text chip. */
    val comboCardsByName: Map<String, Card> = emptyMap(),
    /** True while [loadCombos] is in flight. */
    val isLoadingCombos: Boolean = false,
    /** True once [loadCombos] has completed at least once (success OR degraded-empty) — guards
     * against re-fetching on every tab re-selection within the same sheet session. */
    val combosLoaded: Boolean = false,

    // ── Import (Group B) ──────────────────────────────────────────────────────
    /** True while a pasted deck list is being resolved + written into the live draft. */
    val isImporting: Boolean = false,
) {
    val isEmptyDeck: Boolean get() = cards.isEmpty() && commanderCard == null
}

/**
 * Drives the unified Deck Studio editor against a single live draft deck.
 *
 * Unlike the retired `DeckMagicDetailViewModel` (an in-memory draft that flushed to Room on
 * exit — deleted in the Deck Wizard & Engine Rework plan, WS7.1), every manual operation writes
 * straight through [DeckRepository] so the live `deckId` is always the source of truth.
 * [observeDeckWithCards] re-emits and rebuilds the UI after each write.
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
    private val suggestAddsFromCollectionUseCase: SuggestAddsFromCollectionUseCase,
    private val getDeckGameStatsUseCase: GetDeckGameStatsUseCase,
    private val importDeckUseCase: ImportDeckUseCase,
    private val wishlistRepository: WishlistRepository,
    private val userPreferences: UserPreferencesDataStore,
    private val crashReporter: CrashReporter,
    private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    // ── Motor B (Phase 4) — appended last, nullable-defaulted so no existing test call site
    // (all named-arg) needs to change; a `null` value means this ViewModel behaves exactly as
    // before Phase 4 (no community state is ever populated).
    private val suggestAddsFromCommunityUseCase: SuggestAddsFromCommunityUseCase? = null,
    private val findSimilarDecksUseCase: FindSimilarDecksUseCase? = null,
    private val communityAggregateRepository: CommunityAggregateRepository? = null,
    // Deck Doctor Community/Archetype plan, Phase 6 (D17 deckstats.net import-by-URL). Reuses the
    // SAME paste-a-deck-list text field the Studio already has — a pasted deckstats.net URL is
    // detected and routed through the unified pipeline instead of the plain-text parser.
    private val importDeckCardsUseCase: ImportDeckCardsUseCase? = null,
    // Deck Builder v2, Phase 5 (docs/plans/deck-builder-v2-plan.md §3.5) -- appended last,
    // nullable-defaulted so no existing test call site needs to change; null behaves exactly as
    // before Phase 5 (discoveriesV2 never populates, loadDiscoveries falls back to the legacy path).
    private val discoverSynergiesV2UseCase: DiscoverSynergiesV2UseCase? = null,
    // Deck Engine Unification plan D7 (Phase 4.3) -- appended last, nullable-defaulted so no
    // existing test call site needs to change; null means the Combos tab always degrades to an
    // empty result (never a crash -- mirrors every other optional community-data dependency here).
    private val findCombosUseCase: FindCombosUseCase? = null,
    // Deck Wizard & Engine Rework plan, Workstream 6 ("One land engine") -- appended last,
    // nullable/defaulted so no existing test call site needs to change. `deckScorer == null` (every
    // test call site that doesn't pass it) means calculateLandDeltas falls back to EXACTLY the
    // pre-WS6 behavior (format.targetLandCount-based BasicLandCalculator.calculate) -- never a
    // crash, never a silently wrong number. See calculateLandDeltas'/resolveStudioLandTarget's KDoc.
    private val deckScorer: DeckScorer? = null,
    private val manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
    // Deck Wizard & Engine Rework plan, Workstream 8.2 -- appended last, nullable-defaulted so no
    // existing test call site needs to change; `null` means the Suggestions tab's "include outside
    // collection" toggle is inert (adds stays Motor-A-only regardless of the toggle's UI state).
    // Shares the SAME CandidatePoolGenerator singleton the wizard's own build-time backstop uses.
    private val candidatePoolGenerator: CandidatePoolGenerator? = null,
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
     * Owns the Suggestions-surface (Deck Doctor) incremental-analysis machinery — see
     * [DeckDoctorOrchestrator] (Phase 0.4 extraction,
     * `docs/claude-code-prompt-deck-doctor-community.md`). [uiState.health]/`cuts`/`adds`/
     * `isSuggestionsLoading`/`isAddsLoading`/`suggestionsLoaded` are kept in sync with
     * [DeckDoctorOrchestrator.state] via the collector in [init]; this ViewModel otherwise only
     * delegates ([onSelectTab], [onAddSuggestion], [onCutSuggestion], [invalidateSuggestions],
     * budget changes in [reparseBudget]).
     */
    private val deckDoctorOrchestrator = DeckDoctorOrchestrator(
        scope = viewModelScope,
        deckRepository = deckRepository,
        userCardRepository = userCardRepository,
        wishlistRepository = wishlistRepository,
        evaluateDeckUseCase = evaluateDeckUseCase,
        suggestCutsUseCase = suggestCutsUseCase,
        suggestAddsFromCollectionUseCase = suggestAddsFromCollectionUseCase,
        inferDeckIdentityUseCase = inferDeckIdentityUseCase,
        crashReporter = crashReporter,
        resolveCard = ::resolveCard,
        weightsProvider = { userPreferences.observeScoreWeightOverrides().first() },
        communityAggregateRepository = communityAggregateRepository,
        suggestAddsFromCommunityUseCase = suggestAddsFromCommunityUseCase,
        findSimilarDecksUseCase = findSimilarDecksUseCase,
        isCommunityEngineEnabled = { userPreferences.communityEngineEnabledFlow.first() },
        candidatePoolGenerator = candidatePoolGenerator,
        isSuggestionsEngineEnabled = { FeatureFlags.Decks.DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED },
    )

    /**
     * Per-deck game statistics for the [DeckStatsCard], kept independent of [uiState]
     * so a stats update never invalidates the editor state machine.
     *
     * Unlike the retired `DeckMagicDetailViewModel`, the live `deckId` here is resolved
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
            .flatMapLatest { id ->
                userPreferences.playerNameFlow.flatMapLatest { name -> getDeckGameStatsUseCase(id, name) }
            }
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
        // Mirror the Deck Doctor orchestrator's state into this VM's own uiState so the
        // Screen's single collectAsStateWithLifecycle() contract is unchanged (Phase 0.4).
        viewModelScope.launch {
            deckDoctorOrchestrator.state.collect { doctorState ->
                _uiState.update { s ->
                    s.copy(
                        health = doctorState.health,
                        cuts = doctorState.cuts,
                        adds = doctorState.adds,
                        addsTotalCostEur = doctorState.addsTotalCostEur,
                        addsCardsToBuy = doctorState.addsCardsToBuy,
                        isSuggestionsLoading = doctorState.isSuggestionsLoading,
                        isAddsLoading = doctorState.isAddsLoading,
                        suggestionsLoaded = doctorState.isLoaded,
                        doctorStage = doctorState.stage,
                        doctorCompletedStages = doctorState.completedStages,
                        includeOutsideCollection = doctorState.includeOutsideCollection,
                        outsideCollectionUnavailable = doctorState.outsideCollectionUnavailable,
                        communityAdds = doctorState.communityAdds,
                        similarDecks = doctorState.similarDecks,
                        isCommunityLoading = doctorState.isCommunityLoading,
                        communityUnavailable = doctorState.communityUnavailable,
                    )
                }
            }
        }
        // Motor B (Phase 4) / Community Hub (Phase 5) master flag — mirrored into uiState so the
        // seed sheet's "Use community data" toggle default and the Suggestions tab's community
        // section can both read it synchronously off one source.
        viewModelScope.launch {
            userPreferences.communityEngineEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(communityEngineEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            deckDoctorOrchestrator.events.collect { event ->
                when (event) {
                    DeckDoctorEvent.ExternalPoolFailed -> _events.send(DeckStudioEvent.ExternalPoolFailed)
                }
            }
        }

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
     * Computes collection-synergy discoveries off the user's collection for the Inspirations
     * surface, via [discoverSynergiesV2UseCase] (identity-only clustering — STRATEGY/ARCHETYPE
     * tags + derived `tribe:<x>` keys). A failure logs + records and leaves the discovery list
     * empty — never fatal.
     *
     * The legacy `DeckMagicEngine.discoverSynergies` path (ANY-tag-category clustering, mixing
     * STRATEGY/TYPE/KEYWORD indiscriminately — the exact "presentation mixes the axes" bug the v2
     * clustering was built to fix) was RETIRED in the Deck Wizard & Engine Rework plan, WS7.2
     * (2026-07-28), together with `DeckFeatureFlags.DISCOVERIES_V2_ENABLED` (v2 is now the only
     * path). [discoverSynergiesV2UseCase] stays nullable/defaulted for test-constructor
     * convenience only — a `null` value degrades to an empty [DeckStudioUiState.discoveriesV2]
     * (never a crash), mirroring every other optional community-data dependency here.
     */
    private fun loadDiscoveries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDiscoveries = true) }
            val v2UseCase = discoverSynergiesV2UseCase
            if (v2UseCase == null) {
                _uiState.update { it.copy(isLoadingDiscoveries = false) }
                return@launch
            }
            runCatching {
                val collection = userCardRepository.observeCollection().first()
                v2UseCase(collection)
            }.onSuccess { discoveries ->
                // 4.2: filteredDiscoveriesV2 starts equal to the full list (no search active
                // yet) -- recomputeFilteredDiscoveries() re-derives it from state whenever the
                // user actually searches, so this is just the correct initial value.
                _uiState.update { it.copy(discoveriesV2 = discoveries, filteredDiscoveriesV2 = discoveries, isLoadingDiscoveries = false) }
            }.onFailure { t ->
                FirebaseCrashlytics.getInstance().apply {
                    log("deck_studio_discovery_v2_seeding_failed")
                    recordException(RuntimeException("[DeckStudio] deck_studio_discovery_v2_seeding_failed", t))
                }
                _uiState.update { it.copy(isLoadingDiscoveries = false) }
            }
        }
    }

    // ── Observation ─────────────────────────────────────────────────────────────

    private fun observeDeck() {
        deckRepository.observeDeckWithCards(deckId)
            .distinctUntilChanged()
            .onEach { deckWithCards ->
                if (deckWithCards == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@onEach
                }

                // WS4a finding 4 (Backend & Performance Optimization plan, 2026-07-28): batch-
                // resolve every mainboard/sideboard id BEFORE building entries. resolveCard() below
                // used to be called per-slot inside a sequential map -- up to N Room/network
                // round-trips for an N-card deck, re-fired on every content-changing
                // observeDeckWithCards emission. warmCacheForIds + getCardsByIds mirrors the
                // FriendRepositoryImpl N+1 fix (WS1+WS3): only ids missing from the in-memory
                // cardCache are resolved, and only via Room (no per-id network fallback here) --
                // resolveCard() still covers any id Room can't resolve as a last-resort fallback.
                val unresolvedIds = (deckWithCards.mainboard.map { it.scryfallId } + deckWithCards.sideboard.map { it.scryfallId })
                    .distinct()
                    .filterNot { cardCache.containsKey(it) }
                if (unresolvedIds.isNotEmpty()) {
                    cardRepository.warmCacheForIds(unresolvedIds)
                    val resolved = cardRepository.getCardsByIds(unresolvedIds).associateBy { it.scryfallId }
                    cardCache = cardCache + resolved
                }

                val mainEntries = deckWithCards.mainboard.map { slot ->
                    // RUN 7b fix (BUG 1): thread the slot's provenance through -- DeckSlotEntry used
                    // to drop it entirely, which is what let the quantity-adjustment call sites below
                    // silently rewrite a WIZARD/SUGGESTION slot's source back to USER.
                    DeckSlotEntry(slot.scryfallId, slot.quantity, false, resolveCard(slot.scryfallId), slot.source)
                }
                val sideEntries = deckWithCards.sideboard.map { slot ->
                    DeckSlotEntry(slot.scryfallId, slot.quantity, true, resolveCard(slot.scryfallId), slot.source)
                }
                val allEntries = mainEntries + sideEntries
                cardCache = cardCache + allEntries.mapNotNull { it.card }.associateBy { it.scryfallId }

                rebuildUiState(deckWithCards.deck, allEntries)
            }
            .launchIn(viewModelScope)
    }

    private fun observeCollection() {
        userCardRepository.observeCollection()
            .distinctUntilChanged()
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
                    deck = deck,
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
     * negative delta = remove that many. Originally ported from the retired
     * `DeckMagicDetailViewModel.calculateLandDeltas` (identical math; this VM only reads from
     * resolved [DeckSlotEntry]s instead of an in-memory map).
     *
     * WS6 (One land engine, `docs/plans/deck-wizard-rework-plan.md`): the TOTAL land target now
     * comes from [LandTargetResolver] (via [resolveStudioLandTarget]) -- the SAME resolver
     * [com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase] uses at build
     * time -- instead of [BasicLandCalculator]'s convenience overload that hardcodes
     * [DeckFormat.targetLandCount] (archetype-blind, `dynamicLandIdeal`-blind). [deckScorer] is
     * `null` at every existing test call site (a nullable-defaulted, appended-last constructor
     * param): that falls back to EXACTLY the pre-WS6 behavior, never a crash. A failure resolving
     * the WS6 land target (defensive; should not happen in practice) also falls back to the legacy
     * path rather than propagating.
     */
    private fun calculateLandDeltas(
        entries: List<DeckSlotEntry>,
        deck: Deck,
        commanderIdentity: Set<String>? = null,
    ): List<LandDelta> {
        val format = DeckFormat.entries.firstOrNull { it.name.equals(deck.format, ignoreCase = true) }
            ?: DeckFormat.CASUAL

        val deckCards = entries.filter { it.card != null && !it.isSideboard }
            .map { DeckCard(it.card!!, it.quantity, isOwned = true) }

        val nonBasicLands = deckCards.filter { !BasicLandCalculator.isBasicLand(it.card) && BasicLandCalculator.isLand(it.card) }
        val mainboardNonLands = deckCards.filter { !BasicLandCalculator.isLand(it.card) }

        val scorer = deckScorer
        val distribution = if (scorer != null) {
            runCatching {
                val landTarget = resolveStudioLandTarget(scorer, format, deck, mainboardNonLands, commanderIdentity)
                BasicLandCalculator.calculate(
                    mainboard = mainboardNonLands,
                    nonBasicLands = nonBasicLands,
                    totalLandTarget = landTarget,
                    commanderIdentity = commanderIdentity,
                )
            }.getOrElse { t ->
                crashReporter.log("deck_studio_land_target_resolve_failed")
                crashReporter.recordException(RuntimeException("[DeckStudioViewModel] deck_studio_land_target_resolve_failed", t))
                BasicLandCalculator.calculate(
                    mainboard = mainboardNonLands,
                    nonBasicLands = nonBasicLands,
                    format = format,
                    commanderIdentity = commanderIdentity,
                )
            }
        } else {
            BasicLandCalculator.calculate(
                mainboard = mainboardNonLands,
                nonBasicLands = nonBasicLands,
                format = format,
                commanderIdentity = commanderIdentity,
            )
        }
        val suggestedMap = distribution.toMap()

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
     * WS6 (One land engine): resolves the land target the SAME way
     * [com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase] does at build
     * time -- [LandTargetResolver.resolve] over a resolved [ArchetypeSkeletonResolver] skeleton (or
     * `null` for the GENERIC-with-no-themes case) plus a [DeckScorer.profile] snapshot of the
     * mainboard.
     *
     * The archetype/themes come from [Deck.archetypeOverride]/[Deck.themesOverride] -- the RAW
     * persisted pin the wizard itself wrote at build time via `template.archetypeInfo`, mapped
     * defensively via `entries.firstOrNull` (same pattern as
     * [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator.pinSeedTags]) --
     * deliberately NOT a re-inferred/Doctor-evaluated identity (`DeckHealth.archetypeResolution`),
     * since that requires [DeckDoctorOrchestrator]'s async analysis state, which may not be loaded
     * yet when [rebuildUiState] fires, AND because the raw override is exactly what the wizard used
     * for its OWN final land-target recompute -- the correct basis for byte-identical agreement.
     *
     * Color count is derived via [deriveStudioColorIdentity] (mirrors
     * [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase.deriveColorIdentity]) --
     * a placeholder/simplification WS9 (color-identity/count awareness) will revisit.
     */
    private fun resolveStudioLandTarget(
        scorer: DeckScorer,
        format: DeckFormat,
        deck: Deck,
        mainboardNonLands: List<DeckCard>,
        commanderIdentitySymbols: Set<String>?,
    ): Int {
        val archetype = deck.archetypeOverride?.let { name -> ArchetypeId.entries.firstOrNull { it.name == name } }
            ?: ArchetypeId.GENERIC
        val themes = deck.themesOverride.mapNotNull { name -> ThemeId.entries.firstOrNull { it.name == name } }
        val colorIdentity = deriveStudioColorIdentity(mainboardNonLands, commanderIdentitySymbols)
        val archetypeFormat = ArchetypeFormat.of(format)
        val skeleton = if (archetypeFormat == null || (archetype == ArchetypeId.GENERIC && themes.isEmpty())) {
            null
        } else {
            ArchetypeSkeletonResolver.resolveWithColor(
                format = archetypeFormat,
                archetype = archetype,
                themes = themes,
                identity = colorIdentity,
            )
        }
        val mainboardEntries = mainboardNonLands.map { deckCard ->
            DeckEntry(card = deckCard.card, quantity = deckCard.quantity, isOwned = true, isSideboard = false)
        }
        val profile = scorer.profile(mainboard = mainboardEntries, format = format, colorIdentity = colorIdentity, seedTags = emptyList())
        return LandTargetResolver.resolve(
            format = format,
            archetypeSkeleton = skeleton,
            profile = profile,
            manaBaseAnalyzer = manaBaseAnalyzer,
        )
    }

    /**
     * Mirrors [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase.deriveColorIdentity]
     * (private there, so re-derived here rather than exposed): union of [mainboardNonLands]' own
     * [Card.colorIdentity] symbols plus the commander's, mapped to [ManaColor] -- only WUBRG maps,
     * "C"/unknown symbols are dropped (an empty result correctly means "no color restriction").
     */
    private fun deriveStudioColorIdentity(mainboardNonLands: List<DeckCard>, commanderIdentitySymbols: Set<String>?): Set<ManaColor> {
        val symbols = buildSet {
            mainboardNonLands.forEach { addAll(it.card.colorIdentity) }
            commanderIdentitySymbols?.let { addAll(it) }
        }
        return symbols.mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } }.toSet()
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
                            // RUN 7b fix (BUG 1): preserve a WIZARD-placed basic land's provenance
                            // across a land-count bump. `existing` is null for a brand-new land slot
                            // (nothing to preserve), so that path correctly defaults to USER.
                            deckRepository.addCardToDeck(
                                deckId, scryfallId, currentQty + delta.delta, false,
                                source = existing?.source ?: DeckCardSource.USER,
                            )
                        }
                        delta.delta < 0 && existing != null -> {
                            val newQty = currentQuantity(existing.scryfallId, false) + delta.delta
                            if (newQty <= 0) deckRepository.removeCardFromDeck(deckId, existing.scryfallId, false)
                            else deckRepository.addCardToDeck(
                                deckId, existing.scryfallId, newQty, false,
                                source = existing.source,
                            )
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
        // and avoids a Scryfall call for a deck the user may never analyse). Reads the
        // orchestrator's TRUE current state directly (not the merged _uiState, which is
        // only eventually-consistent via the init collector) so this gate is race-free.
        if (tab == DeckStudioTab.SUGGESTIONS && !deckDoctorOrchestrator.state.value.isLoaded && ::deckId.isInitialized) {
            deckDoctorOrchestrator.loadAnalysis(deckId, _uiState.value.budgetConstraints)
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
                // RUN 7b fix (BUG 1): preserve the existing slot's provenance across a quantity
                // bump -- omitting `source` would fall back to the repository's USER default,
                // silently stripping WIZARD/SUGGESTION provenance and defeating D4's hard no-cut
                // guarantee. A genuinely NEW slot (no existing match) has nothing to preserve and
                // correctly falls back to USER.
                deckRepository.addCardToDeck(
                    deckId, scryfallId, currentQty + 1, isSideboard,
                    source = existingSource(scryfallId, isSideboard) ?: DeckCardSource.USER,
                )
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
                // RUN 7b fix (BUG 1): same provenance-preservation as addCardToDeck above -- this
                // decrement-but-stays-above-0 branch is still an upsert of an EXISTING slot.
                runCatching {
                    deckRepository.addCardToDeck(
                        deckId, scryfallId, currentQty - 1, isSideboard,
                        source = existingSource(scryfallId, isSideboard) ?: DeckCardSource.USER,
                    )
                }.onFailure { logFailure("deck_studio_decrement_failed", it) }
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

    /**
     * Looks up the [DeckCardSource] of an EXISTING slot, mirroring [currentQuantity]'s
     * commander-then-cards lookup order. RUN 7b fix (BUG 1): every quantity-adjustment call site
     * that upserts an existing slot must pass this back into [DeckRepository.addCardToDeck]'s
     * `source` param instead of omitting it (which silently defaults to USER and strips a
     * WIZARD/SUGGESTION card's provenance). Returns null only when there is truly no existing
     * slot to preserve (a genuinely new add), in which case callers fall back to USER themselves.
     */
    private fun existingSource(scryfallId: String, isSideboard: Boolean): DeckCardSource? {
        val s = _uiState.value
        val commander = s.commanderCard
        if (commander != null && commander.scryfallId == scryfallId && !isSideboard) return commander.source
        return s.cards.find { it.scryfallId == scryfallId && it.isSideboard == isSideboard }?.source
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

            // Phase 6 (D17): a pasted deckstats.net URL reuses this SAME text field but routes
            // through the unified pipeline instead of the plain-text line parser. Falls back to
            // the original text-list import for everything else — zero behavior change for the
            // Moxfield/Arena paste flow that already worked here.
            val trimmed = text.trim()
            val cardsUseCase = importDeckCardsUseCase
            val success = if (cardsUseCase != null && DECKSTATS_URL_PATTERN.containsMatchIn(trimmed)) {
                when (val outcome = cardsUseCase(source = ImportSource.DeckstatsUrl(trimmed), targetDeckId = deckId)) {
                    is ImportOutcome.Success -> true
                    is ImportOutcome.Error -> {
                        logFailure("deck_studio_import_deckstats_failed", IllegalStateException(outcome.message))
                        false
                    }
                }
            } else {
                importDeckUseCase(deckId, text)
                    .onFailure { t -> logFailure("deck_studio_import_failed", t) }
                    .isSuccess
            }

            if (success) {
                // Invalidate so the next Suggestions open re-analyses the imported cards.
                invalidateSuggestions()
            } else {
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
                is DataResult.Success -> result.data.cards
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
            _uiState.update { it.copy(isLoadingCardDetail = true, detailDisplayCard = null) }
            val card = (cardRepository.getCardById(scryfallId) as? DataResult.Success)?.data ?: run {
                _uiState.update { it.copy(isLoadingCardDetail = false) }
                return@launch
            }
            val tags = if (card.tags.isNotEmpty() || card.userTags.isNotEmpty()) {
                card.tags + card.userTags
            } else {
                suggestTagsUseCase(card).confirmed
            }
            // Entry-only English-first redirect for the sheet's IMAGE/name/type-line/oracle-text —
            // same rationale as CardDetailViewModel.loadCard(): the deck-list thumbnail this sheet
            // opened from already shows the English sibling image (Collection/Deck Studio image
            // fallback), so painting the saved non-English printing here would flash a mismatch.
            // The tapped [DeckSlotEntry]/scryfallId itself is untouched — only the DISPLAY card
            // resolved here swaps; +/-/delete/commander actions keep operating on the original.
            val displayCard = if (card.lang != "en") {
                val languageResult = cardRepository.getLanguagePrints(card.setCode, card.collectorNumber)
                val englishId = (languageResult as? DataResult.Success)?.data
                    ?.firstOrNull { it.lang == "en" }
                    ?.scryfallId
                if (englishId != null && englishId != card.scryfallId) {
                    (cardRepository.getCardById(englishId) as? DataResult.Success)?.data ?: card
                } else {
                    card
                }
            } else {
                card
            }
            _uiState.update {
                it.copy(
                    detailTags = tags.distinctBy { t -> t.key },
                    detailDisplayCard = displayCard,
                    isLoadingCardDetail = false,
                )
            }
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
    //  The AnalysisCache / GapSignature / loadAnalysis / recomputeIncremental /
    //  recomputeAdds incremental pattern lives in [DeckDoctorOrchestrator] (Phase
    //  0.4 extraction — this VM only delegates and merges its state; see the
    //  collectors in [init]).
    // ─────────────────────────────────────────────────────────────────────────

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
            deckDoctorOrchestrator.recomputeAdds(constraints)
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
     * INCREMENTALLY via [DeckDoctorOrchestrator.onAddCard]. Falls back to a full
     * [DeckDoctorOrchestrator.loadAnalysis] only when the cache is missing or the card cannot
     * be resolved from any cached source (see [DeckDoctorOrchestrator.onAddCard]'s return contract).
     */
    fun onAddSuggestion(scryfallId: String, cardName: String) {
        viewModelScope.launch {
            val currentQty = currentQuantity(scryfallId, false)
            // Deck Engine Unification (D4/RUN 1 follow-up): a Suggestions-tab accept persists
            // DeckCardSource.SUGGESTION (never the default USER) so a future strategyLocked cut-gate
            // change can distinguish "the Doctor suggested this" from "the user typed it in manually"
            // if that distinction is ever needed -- today both stay equally cuttable, this is
            // provenance-correctness only.
            runCatching { deckRepository.addCardToDeck(deckId, scryfallId, currentQty + 1, false, DeckCardSource.SUGGESTION) }
                .onFailure { logFailure("deck_studio_suggestion_add_failed", it); return@launch }
            _events.send(DeckStudioEvent.CardAdded(cardName))

            if (!deckDoctorOrchestrator.onAddCard(scryfallId, _uiState.value.budgetConstraints)) {
                deckDoctorOrchestrator.loadAnalysis(deckId, _uiState.value.budgetConstraints)
            }
        }
    }

    /**
     * Removes a cut-candidate card from the live deck's mainboard, then recomputes
     * INCREMENTALLY via [DeckDoctorOrchestrator.onCutCard]. Falls back to
     * [DeckDoctorOrchestrator.loadAnalysis] when the cache is missing.
     */
    fun onCutSuggestion(scryfallId: String, cardName: String) {
        viewModelScope.launch {
            // C1: cut ONE copy, not the whole slot. A 3-of must become a 2-of (mirrors the
            // Build-tab decrement). Previously this removed the entire slot, silently dropping
            // every copy — a data-loss bug for multi-copy 60-card decks.
            val currentQty = deckDoctorOrchestrator.cachedMainboardQuantity(scryfallId)
                ?: currentQuantity(scryfallId, false)
            runCatching {
                if (currentQty <= 1) deckRepository.removeCardFromDeck(deckId, scryfallId, false)
                else deckRepository.addCardToDeck(deckId, scryfallId, currentQty - 1, false)
            }.onFailure { logFailure("deck_studio_suggestion_cut_failed", it); return@launch }
            _events.send(DeckStudioEvent.CardCut(cardName))

            if (!deckDoctorOrchestrator.onCutCard(scryfallId, _uiState.value.budgetConstraints)) {
                deckDoctorOrchestrator.loadAnalysis(deckId, _uiState.value.budgetConstraints)
            }
        }
    }

    /**
     * Pins the deck's archetype/theme plan (Deck Doctor Community/Archetype plan, Phase 1.7
     * Studio header chip / bottom sheet). Delegates straight to
     * [DeckDoctorOrchestrator.setArchetypeOverride], which writes through the repository and
     * re-runs a full analysis. A no-op when `deckId` never resolved (defensive — the chip is
     * only reachable once the deck has loaded).
     *
     * @param tribe Deck Analysis Engine v2 Phase 3 — forwarded to
     *        [DeckDoctorOrchestrator.setArchetypeOverride]'s own `tribe` param (the curated
     *        strategy picker's tribe sub-pick); `null` for every non-tribal strategy.
     */
    fun onSetArchetypeOverride(archetypeId: ArchetypeId?, themes: List<ThemeId>, tribe: String? = null) {
        if (!::deckId.isInitialized) return
        deckDoctorOrchestrator.setArchetypeOverride(deckId, _uiState.value.budgetConstraints, archetypeId, themes, tribe)
    }

    /**
     * Deck Analysis Engine v2 Phase 3 — convenience wrapper the [CuratedStrategyPickerSheet]
     * ([com.mmg.manahub.feature.decks.presentation.components.CuratedStrategyPickerSheet]) calls
     * directly with the [CuratedStrategy] the player tapped, instead of unpacking its
     * archetype/themes at the call site.
     */
    fun onApplyCuratedStrategy(strategy: CuratedStrategy, tribe: String?) {
        crashReporter.setCustomKey("deck_analysis_strategy_pick_id", strategy.id)
        crashReporter.setCustomKey("deck_analysis_strategy_pick_tribe", tribe ?: "none")
        crashReporter.log("deck_analysis_strategy_pick")
        onSetArchetypeOverride(strategy.archetype, strategy.themes, tribe)
    }

    /** "Auto-detect" — clears the pin and re-infers the archetype/themes from the live deck. */
    fun onClearArchetypeOverride() {
        if (!::deckId.isInitialized) return
        crashReporter.setCustomKey("deck_analysis_strategy_pick_id", "auto_detect")
        crashReporter.setCustomKey("deck_analysis_strategy_pick_tribe", "none")
        crashReporter.log("deck_analysis_strategy_pick")
        deckDoctorOrchestrator.clearArchetypeOverride(deckId, _uiState.value.budgetConstraints)
    }

    /**
     * Deck Wizard & Engine Rework plan WS8.2: the Suggestions tab's own "include outside
     * collection" toggle (a SEPARATE choice from the wizard's per-build one). Delegates straight
     * to [DeckDoctorOrchestrator.setIncludeOutsideCollection], which re-ranks `adds` incrementally
     * (no full [DeckDoctorOrchestrator.loadAnalysis] reload needed — only the ADD candidate
     * sources change, never the deck's health/cuts).
     */
    fun onToggleIncludeOutsideCollection(enabled: Boolean) {
        deckDoctorOrchestrator.setIncludeOutsideCollection(enabled, _uiState.value.budgetConstraints)
    }

    /**
     * Deck Engine Unification plan (D4): the deck's own explicit "Unlock strategy" action (Studio
     * shows a confirmation dialog before calling this — see [DeckStudioScreen]). Delegates straight
     * to [DeckDoctorOrchestrator.unlockStrategy], which flips `Deck.strategyLocked` off and re-runs a
     * full analysis so [DeckDoctorState.cuts] immediately reflects the unlocked candidate pool.
     */
    fun onUnlockStrategy() {
        if (!::deckId.isInitialized) return
        crashReporter.log("deck_studio_unlock_strategy_confirmed")
        deckDoctorOrchestrator.unlockStrategy(deckId, _uiState.value.budgetConstraints)
    }

    /**
     * Invalidates the loaded analysis after a MANUAL (Build-tab) deck mutation so the
     * next time the user opens Suggestions a fresh [DeckDoctorOrchestrator.loadAnalysis]
     * re-syncs with the live deck. We deliberately do NOT recompute here (the work is wasted
     * while the user is still editing on the Build tab) and we NEVER trigger analysis from
     * the deck-observe transformer (that would create a write→observe→recompute feedback loop).
     */
    private fun invalidateSuggestions() {
        if (deckDoctorOrchestrator.state.value.isLoaded) {
            deckDoctorOrchestrator.invalidate()
            // M9: clear any stale budget parse error too, so the next time the user opens
            // Suggestions the inline error doesn't linger from a previous editing session.
            _uiState.update { it.copy(budgetError = false) }
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

    // ── Synergy browser: tabs + search (Deck Engine Unification plan D7, 4.1-4.2) ──────────────

    /** Switches the v2 synergy browser's active tab. Lazily kicks off [loadCombos] the FIRST
     * time [InspirationsTab.COMBOS] is selected — opening Inspirations never fires a network call
     * by itself; only actually looking at the Combos tab does. */
    fun onSelectInspirationsTab(tab: InspirationsTab) {
        _uiState.update { it.copy(inspirationsTab = tab) }
        if (tab == InspirationsTab.COMBOS && !_uiState.value.combosLoaded && !_uiState.value.isLoadingCombos) {
            loadCombos()
        }
    }

    /** Updates the free-text label search (4.2) and re-derives [DeckStudioUiState.filteredDiscoveriesV2]. */
    fun onDiscoverySearchQueryChange(query: String) {
        _uiState.update { it.copy(discoverySearchQuery = query) }
        recomputeFilteredDiscoveries()
    }

    /** Toggles one card in/out of the search-by-card pick set (4.2). */
    fun onToggleDiscoverySearchCard(cardName: String) {
        _uiState.update {
            val selected = it.discoverySelectedCardNames
            it.copy(discoverySelectedCardNames = if (cardName in selected) selected - cardName else selected + cardName)
        }
        recomputeFilteredDiscoveries()
    }

    /** Clears both search inputs (4.2) back to the unfiltered [DeckStudioUiState.discoveriesV2] list. */
    fun onClearDiscoverySearch() {
        _uiState.update { it.copy(discoverySearchQuery = "", discoverySelectedCardNames = emptySet()) }
        recomputeFilteredDiscoveries()
    }

    /** Pure re-derivation via [DiscoverySearchFilter] -- the single source of truth for what the
     * Strategies tab renders, kept in state (not computed in the Composable) so it stays unit
     * testable from the VM and Compose stays a dumb `uiState.filteredDiscoveriesV2` reader. */
    private fun recomputeFilteredDiscoveries() {
        _uiState.update { state ->
            state.copy(
                filteredDiscoveriesV2 = DiscoverySearchFilter.apply(
                    discoveries = state.discoveriesV2,
                    query = state.discoverySearchQuery,
                    selectedCardNames = state.discoverySelectedCardNames,
                ),
                discoveryMatchingCards = DiscoverySearchFilter.matchingCards(
                    discoveries = state.discoveriesV2,
                    query = state.discoverySearchQuery,
                    selectedCardNames = state.discoverySelectedCardNames,
                ),
            )
        }
    }

    // ── Combos tab (Deck Engine Unification plan D7, 4.3) ──────────────────────────────────────

    /**
     * Finds Commander Spellbook combos over the user's OWNED collection (the SAME
     * `observeCollection()` snapshot [loadDiscoveries] uses -- this tab answers "what combos
     * could I already build with what I own," not "what combos exist in this deck"). Never
     * throws: [findCombosUseCase] is null-safe (degrades to [ComboResult.EMPTY]) and every
     * failure inside it already degrades per [com.mmg.manahub.core.data.repository
     * .CommanderSpellbookRepositoryImpl]'s own cache-then-empty contract -- this function's
     * `runCatching` is defense-in-depth only (e.g. a Room read failure before the network call).
     */
    fun loadCombos() {
        val useCase = findCombosUseCase
        if (useCase == null) {
            _uiState.update { it.copy(comboResult = ComboResult.EMPTY, combosLoaded = true, isLoadingCombos = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCombos = true) }
            runCatching {
                val collection = userCardRepository.observeCollection().first()
                val cardNames = collection.map { it.card.name }.distinct()
                useCase(cardNames = cardNames)
            }.onSuccess { result ->
                val combos = (result as? DataResult.Success)?.data ?: ComboResult.EMPTY
                val cardsByName = resolveComboCards(combos)
                _uiState.update {
                    it.copy(
                        comboResult = combos,
                        comboCardsByName = cardsByName,
                        isLoadingCombos = false,
                        combosLoaded = true,
                    )
                }
            }.onFailure { t ->
                crashReporter.log("deck_studio_combos_load_failed")
                crashReporter.recordException(RuntimeException("[DeckStudio] deck_studio_combos_load_failed", t))
                _uiState.update { it.copy(comboResult = ComboResult.EMPTY, isLoadingCombos = false, combosLoaded = true) }
            }
        }
    }

    /**
     * Resolves every distinct card name referenced by [combos] (complete + almost-there,
     * including each [com.mmg.manahub.feature.decks.domain.model.AlmostCombo.missingCardName]) to
     * a full [Card] in parallel, mirroring the established
     * [com.mmg.manahub.feature.communitydecks.presentation.CommunityDecksSearchViewModel
     * .resolveTrendingCards] pattern -- concurrent [CardRepository.getCardByExactName] calls
     * (already rate-limited/cached by the Scryfall request queue underneath), unresolved names
     * dropped rather than surfaced as an error so the Combos tab degrades to its text-chip
     * fallback per-card instead of failing the whole tab. Capped at [MAX_COMBO_CARDS_TO_RESOLVE]
     * distinct names to bound the burst of concurrent network calls for a large combo result.
     */
    private suspend fun resolveComboCards(combos: ComboResult): Map<String, Card> = coroutineScope {
        val names = (
            combos.complete.flatMap { it.cardNames } +
                combos.almostThere.flatMap { it.ownedCardNames + it.missingCardName }
            ).distinct().take(MAX_COMBO_CARDS_TO_RESOLVE)

        names
            .map { name -> name to async { runCatching { cardRepository.getCardByExactName(name) }.getOrNull()?.getOrNull() } }
            .mapNotNull { (name, deferred) -> deferred.await()?.let { name to it } }
            .toMap()
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
        /** Cap on distinct combo card names resolved to full [Card]s per [loadCombos] call, so a
         * large combo result can't burst an unbounded number of concurrent Scryfall lookups. */
        const val MAX_COMBO_CARDS_TO_RESOLVE = 40

        /** Detects a pasted deckstats.net deck URL in the Studio's plain-text import field
         * (Phase 6, D17) — a cheap containment check, not a full URL parse (that happens inside
         * [ImportDeckCardsUseCase]/`DeckstatsFetcherImpl`). */
        val DECKSTATS_URL_PATTERN = Regex("""deckstats\.net/decks/\d+/\d+""")
    }
}
