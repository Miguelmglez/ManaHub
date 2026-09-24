package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-21

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.search.StructuredCardSearch
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckCreationSource
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.PlacementScorer
import com.mmg.manahub.feature.decks.domain.engine.PostureId
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.SectionMembership
import com.mmg.manahub.feature.decks.domain.engine.SectionQueryContext
import com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.engine.WizardPreferenceStore
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.domain.engine.nearestFor
import com.mmg.manahub.feature.decks.domain.engine.toPin
import com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase
import com.mmg.manahub.feature.decks.domain.template.CollectionProfile
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.CollectionTribeSignal
import com.mmg.manahub.feature.decks.domain.template.WizardDraftBuild
import com.mmg.manahub.feature.decks.domain.template.ManualAdd
import com.mmg.manahub.feature.decks.domain.template.OwnedCard
import com.mmg.manahub.feature.decks.domain.template.WizardBuildResult
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.RecommendWizardStrategiesUseCase
import com.mmg.manahub.feature.decks.domain.usecase.StrategyRecommendation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The wizard's internal phases (Deck Wizard 60-card wave v6, plan §5 Phase 5.1, S20). ONE build
 * engine, ONE step language for every format now (S1): [ENTRY] is the flow chooser (Casual-family
 * and every 60-card format visit it; Commander skips straight to [COMMANDER_PICK], the mandatory
 * first seed). [SEED_PICK]/[COLOR_PICK]/[STRATEGY_PICK] are the three "Start from …" flows'
 * respective first step ([WizardEntryFlow.CARDS]/`.COLORS`/`.STRATEGY`). [STRATEGY] is shared by
 * every anchor (Commander AND every 60-card flow). [PLAN_SECTIONS] (renamed from `MANUAL_ADDS`)
 * is the engine-attributed browse/add step, also shared by every anchor now that the legacy
 * skeleton-guided Casual `MANUAL_ADDS` step is gone. `DIRECTION`/`IDENTITY`/`RESULT` are DELETED —
 * every format now opens Deck Studio directly after persist (S13), never a Result screen.
 */
enum class WizardPhase { ENTRY, COMMANDER_PICK, SEED_PICK, COLOR_PICK, STRATEGY_PICK, STRATEGY, PLAN_SECTIONS, REVIEW, GENERATING, CHOICE }

/**
 * Deck Engine Unification plan (§5 Phase 3.1) — the three ways a build can start. [CARDS] is the
 * ORIGINAL wizard flow (commander/seed picker, unchanged) and is the ONLY flow [DeckFormat.COMMANDER]
 * ever uses (the commander itself is the mandatory first seed — plan: "Commander = cards-flow variant
 * with a mandatory commander slot as seed #1"), so [WizardPhase.ENTRY] is skipped entirely for
 * Commander builds. [COLORS] and [STRATEGY] land on [WizardPhase.COLOR_PICK]/[WizardPhase.STRATEGY_PICK]
 * respectively (60-card wave v6 — the pre-v6 shared `DIRECTION` phase these used to dispatch on is
 * gone).
 */
enum class WizardEntryFlow { CARDS, COLORS, STRATEGY }

/** A rankable color-combination pick for the STRATEGY_PICK flow — the curated
 * [com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity.combosFor] weight blended with
 * how strong the user's OWN collection already is in those colors ([CollectionProfile.colorShares]),
 * so a taxonomy pick favors combos the user can actually build today over a purely abstract ranking. */
data class ColorComboSuggestion(val colors: Set<ManaColor>, val score: Float)

/** One picked seed card and how many copies of it (Deck Wizard 60-card wave v6, plan §5 Phase 5.1,
 * S3/S4) — replaces the pre-v6 `List<Card>` shape now that copies are a first-class engine concept.
 * Commander seeds are always quantity 1 (dedupe-by-name, never incremented — see
 * [DeckWizardViewModel.onAddSeed]'s Commander branch). */
data class WizardSeed(val card: Card, val quantity: Int)

/** One-shot side effects, delivered via a buffered [Channel] (never a nullable [MutableStateFlow]
 * — see the project-wide "one-shot events" convention documented on every other feature VM). */
sealed interface DeckWizardEvent {
    // R15 structural guard: [type] defaults to INFO (every pre-existing call site is an unchanged
    // "you're missing a required pick" nudge); the persist-time replace refusal is the one caller
    // that passes ERROR.
    data class ShowToast(val message: String, val type: MagicToastType = MagicToastType.INFO) : DeckWizardEvent

    /** The Result screen's "Open in Deck Studio" CTA — the caller navigates + pops the wizard. */
    data class OpenDeckStudio(val deckId: String) : DeckWizardEvent

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: a missing/unsupported format nav arg
     * (Draft, or a corrupted deep link) — the screen pops the wizard via its own `onBack` callback
     * instead of silently building a Casual deck the old fallback used to (S1: every format now
     * has a real build path except Draft, so a genuine fallback is never warranted). */
    data object Exit : DeckWizardEvent
}

data class DeckWizardUiState(
    // Deck Wizard v4 (R13): never observed by production UI -- init resolves the real starting
    // phase synchronously from the required format nav arg before the first collector ever reads
    // this StateFlow. This default only matters to tests that construct DeckWizardUiState() directly.
    val phase: WizardPhase = WizardPhase.ENTRY,

    // ── Format (Deck Wizard v4, R13: fixed for the whole session, arrives via nav arg only) ─────
    val selectedFormat: DeckFormat? = null,

    // ── Entry chooser (Deck Engine Unification plan §5 Phase 3.1) ────────────────
    /** Casual/60-card only — Commander always forces [WizardEntryFlow.CARDS] and skips
     * [WizardPhase.ENTRY] entirely (see that enum's KDoc), so this default is only ever OBSERVED
     * for a non-Commander format. */
    val entryFlow: WizardEntryFlow = WizardEntryFlow.CARDS,

    // ── Collection snapshot ───────────────────────────────────────────────────
    val isLoadingProfile: Boolean = true,
    val collectionProfile: CollectionProfile? = null,
    /** Deck Wizard & Engine Rework plan, Workstream 2.3 -- the full owned-card snapshot (unlike
     * [collectionProfile], which only surfaces DERIVED top-N signals). Populated once, alongside
     * [collectionProfile], from the SAME `init` collection load. */
    val ownedCards: List<Card> = emptyList(),
    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.2: total owned quantity by [Card.name] (summed
     * across every printing) -- SEED_PICK's own [com.mmg.manahub.feature.decks.presentation
     * .components.CardDetailSheet] "You own %1$d" caption needs a real copy count, which
     * [ownedCards] alone (one entry per distinct PRINTING, not exploded by [Card.name]) cannot give. */
    val ownedQuantityByName: Map<String, Int> = emptyMap(),

    // ── COMMANDER_PICK (Commander only) ──────────────────────────────────────
    val selectedCommander: Card? = null,
    val commanderQuery: String = "",
    val commanderSearchResults: List<Card> = emptyList(),
    val isSearchingCommander: Boolean = false,
    /** Deck Wizard Commander v4 plan, Run 2 W2 review (P1.2): true when [triggerCommanderPickSearch]'s
     * Scryfall call failed -- lets COMMANDER_PICK render [com.mmg.manahub.core.ui.components
     * .InlineErrorState] instead of silently falling through to the "no results" empty state. Cleared
     * at the start of every new search attempt. */
    val commanderSearchError: Boolean = false,
    /** Deck Wizard Commander v4 plan (W2.1, G2/R2): the structured query applied via COMMANDER_PICK's
     * search-bar Tune icon. `null` while the user has added no filter on top of the locked criteria. */
    val commanderStructuredQuery: AdvancedSearchQuery? = null,

    /** Deck Wizard & Engine Rework plan, Workstream 3.1 -- colors derived from the union of currently
     * picked [seeds]' own [Card.colorIdentity], recomputed from scratch on every seed add/remove
     * ([DeckWizardViewModel.recomputeSeedLockedColors]). The UI renders every color in this set as
     * pre-selected AND visually locked (cannot be deselected) inside [colorIdentity] -- removing the
     * seed that contributed a color immediately unlocks it. Always a subset of [colorIdentity] by
     * construction. */
    val lockedColors: Set<ManaColor> = emptySet(),
    /** The wizard's ONE "Direction/Strategy pick" slot -- shared by every anchor (Commander AND
     * every 60-card flow) since the STRATEGY step is now common ground (S1/S7). */
    val selectedArchetype: ArchetypeId? = null,
    val selectedTribeKey: String? = null,
    val selectedTribeLabel: String? = null,

    // ── Seeds (SEED_PICK / PLAN_SECTIONS "Start from cards", S3/S4) ───────────
    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: replaces the pre-v6 `List<Card>` — a copy
     * is a first-class engine concept now (S2/S4). Commander entries are always quantity 1. */
    val seeds: List<WizardSeed> = emptyList(),
    /** SEED_PICK's own name-filter search bar text. */
    val seedPickQuery: String = "",
    /** SEED_PICK's own [com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet] result, on
     * top of [DeckWizardSixtySteps.seedLockedCriteria]'s locked format-legality criterion. */
    val seedPickStructuredQuery: AdvancedSearchQuery? = null,
    val seedPickResults: List<Card> = emptyList(),
    val isSearchingSeedPick: Boolean = false,
    /** True when [DeckWizardViewModel]'s own SEED_PICK Scryfall search failed -- mirrors
     * [commanderSearchError]'s contract. */
    val seedPickSearchError: Boolean = false,
    /** SEED_PICK's sticky "N cards added" pill -> opens the seed queue sheet. */
    val showSeedQueue: Boolean = false,
    /** The seed currently open in [com.mmg.manahub.feature.decks.presentation.components
     * .CardDetailSheet] from SEED_PICK (tile tap or queue-sheet image tap) — `null` when the sheet
     * is closed. */
    val seedDetailCard: Card? = null,

    // ── STRATEGY (shared by every anchor, S7) ─────────────────────────────────
    /** Deck Wizard Commander v3 plan, Phase 4.1 -- the ranked recommendation list from
     * [RecommendWizardStrategiesUseCase] (rename of `commanderStrategyRecommendations`, 60-card
     * wave v6 plan §5 Phase 5.2 -- Commander AND every 60-card anchor share this ONE field now),
     * sorted best-first; the UI splits it via [RecommendWizardStrategiesUseCase.splitRecommended]
     * into "Recommended" and collapsed "Partial fit" (Custom is a UI-level sentinel, always offered
     * separately). */
    val strategyRecommendations: List<StrategyRecommendation> = emptyList(),
    val isLoadingCommanderStrategies: Boolean = false,
    /** The currently-selected catalog entry's id, or `null` for Custom (D6) -- drives the STRATEGY
     * step's single-select highlight. */
    val selectedCuratedStrategyId: String? = null,
    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: distinguishes "the user explicitly tapped
     * Custom" from "nothing picked yet" -- both read as `selectedCuratedStrategyId == null`, which
     * is ambiguous for COLOR_PICK/STRATEGY_PICK's own Next-enable gate (run B, plan §5 Phase 5.3). */
    val isCustomStrategyChosen: Boolean = false,
    /** The STRATEGY step's own tribe sub-picker (shown when the user taps a `requiresTribe` entry
     * with no tribe the recommender could derive on its own) -- the entry awaiting a tribe pick, or
     * `null` when the sub-picker is closed. */
    val pendingTribeStrategy: CuratedStrategy? = null,
    /** [pendingTribeStrategy]'s own candidate list -- the collection's dominant tribes within the
     * current identity. */
    val commanderTribePickerCandidates: List<CollectionTribeSignal> = emptyList(),
    /** The STRATEGY step's OWN theme pick(s) -- up to
     * [com.mmg.manahub.feature.decks.domain.engine.StrategyCatalog.MAX_THEMES]. */
    val selectedStrategyThemes: List<ThemeId> = emptyList(),
    /** Deck Wizard Commander v3 plan (Phase 0 F3 gap, closed in Phase 5): [CuratedStrategy.toPin]'s
     * posture, forwarded to [DeckAnalysisPipeline.analyze]'s `postureOverride` so [planAnalysis]
     * scores the SAME plan the engine will score at generation time. */
    val selectedPosture: PostureId? = null,
    /** STRATEGY_PICK's own free-text taxonomy filter (rename of pre-v6 `taxonomyQuery`, run B wires
     * its UI -- plan §5 Phase 5.3) and its inline color-combo ranking (rename of pre-v6
     * `colorComboSuggestions`, [DeckWizardViewModel.recomputeColorComboSuggestions] retargets it). */
    val strategyPickQuery: String = "",
    val strategyPickCombos: List<ColorComboSuggestion> = emptyList(),
    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.3: the catalog row currently PENDING in
     * STRATEGY_PICK -- the row whose color-combo picker ([showStrategyPickColorSheet]) is open or
     * was last committed (see [DeckWizardViewModel.onSelectStrategyPickEntry]'s own KDoc for why
     * this is a separate field from [selectedCuratedStrategyId]). */
    val expandedStrategyPickId: String? = null,
    /** True while STRATEGY_PICK's color-combo `ModalBottomSheet` for [expandedStrategyPickId] is
     * open (see [DeckWizardViewModel.onSelectStrategyPickEntry]'s own KDoc). */
    val showStrategyPickColorSheet: Boolean = false,

    // ── PLAN_SECTIONS (shared by every anchor since the Casual `MANUAL_ADDS` step was deleted) ───
    /** The ONLY analysis engine call this step ever makes -- attribution comes from here, never a
     * wizard-side classifier. `null` while no build has been analyzed yet (before the first
     * recompute lands) or the pipeline degraded. */
    val planAnalysis: DeckAnalysis? = null,
    /** True while a [planAnalysis] recompute is in flight -- set synchronously on step entry and on
     * every in-step seed edit (before its debounce), so the step never renders the error state while
     * an analysis is merely pending. */
    val isAnalyzingPlan: Boolean = false,
    /** [CardSection.id] -> count of owned, identity-legal, format-legal candidates (excluding the
     * commander/every seed) that the SAME classification signal used to BUILD that section's own id
     * would credit -- the "N in your collection" hint. */
    val ownedAvailabilityBySection: Map<String, Int> = emptyMap(),
    /** PLAN_SECTIONS' "Browse for &lt;Category&gt;" sheet -- the plain search-bar text. */
    val planSectionsQuery: String = "",
    /** The structured query currently APPLIED via the browse sheet's Advanced Search sheet (Tune
     * icon or a section's own "Browse for X" preset). */
    val planSectionsStructuredQuery: AdvancedSearchQuery? = null,
    /** Deck Wizard UX polish plan, Run 1 §1.2: carries the originating `CardSection.id` itself
     * (never real `CardTag` keys any more) through
     * [com.mmg.manahub.core.ui.components.CardSearchSheet]'s `onFilterCollectionByTags(Set<String>)
     * -> Unit` param shape — kept for parity with the sheet's own param naming; [planSectionsPredicate]
     * (derived from the SAME section id) is what [DeckWizardViewModel.publishPlanSectionsCollectionResults]
     * actually filters by. */
    val planSectionsTagFilter: Set<String> = emptySet(),
    /** Deck Wizard UX polish plan, Run 1 §1.2: the [com.mmg.manahub.feature.decks.domain.engine.SectionMembership.predicate]
     * derived from the PLAN_SECTIONS "Browse for X" section id — the SAME per-card membership test
     * [com.mmg.manahub.feature.decks.domain.engine.AnalysisEngine] itself attributed that section's
     * contributions from. `null` for the generic Advanced Search path, which keeps filtering via
     * [planSectionsStructuredQuery] instead. */
    val planSectionsPredicate: ((Card) -> Boolean)? = null,
    /** Collection tab results -- local, lenient [StructuredCardSearch.collectionMatches] over
     * [ownedCards]. */
    val planSectionsCollectionResults: List<Card> = emptyList(),
    /** All-cards tab results -- a real Scryfall search. */
    val planSectionsScryfallResults: List<Card> = emptyList(),
    val isSearchingPlanSectionsScryfall: Boolean = false,

    // ── Colors identity (COLOR_PICK writes it; SEED_PICK/PLAN_SECTIONS derive it from seeds) ─────
    val colorIdentity: Set<ManaColor> = emptySet(),

    // ── Review ─────────────────────────────────────────────────────────────────
    /** Commander-only (R8/E10, Deck Wizard Commander v4 W5.2) — gates ONLY Stage A (owned
     * non-basic lands) of the wizard's land fill; basics (Stage B) always run regardless, per R12. */
    val includeNonBasicLands: Boolean = false,

    // ── Generation ─────────────────────────────────────────────────────────────
    /** Deck Wizard Commander v3 plan, Phase 6 (6.3) — the wizard's OWN staged-progress track
     * ([com.mmg.manahub.feature.decks.domain.template.WizardBuildStage]). Every format uses this
     * ONE track now (the legacy Casual `BuildStage`/`completedStages` pair was deleted in the
     * 60-card wave's UI unification -- `generateCasualDeck` no longer surfaces per-stage progress,
     * only a generic "Validating…" spinner, until run C unifies generation itself). */
    val commanderBuildStage: com.mmg.manahub.feature.decks.domain.template.WizardBuildStage? = null,
    val commanderCompletedStages: List<com.mmg.manahub.feature.decks.domain.template.WizardBuildStage> = emptyList(),
    val buildError: String? = null,
    /** False only for a refused unconfirmed write: retrying would hit the same guard, so the error
     * renders with Back alone. */
    val isBuildErrorRetryable: Boolean = true,

    val createdDeckId: String? = null,

    // ── Choice (W7 Task B, Commander only) ────────────────────────────────────
    /** The engine's pre-land, pre-persist draft — held in memory only while [WizardPhase.CHOICE] is
     * showing (never persisted, never surviving process death by design). `null` outside that phase. */
    val commanderDraftBuild: WizardDraftBuild? = null,
    /** Per-role selections made so far on the Choice screen: [RoleKey] -> id -> selected COPY count
     * (Deck Wizard 60-card wave v6, plan §5 Phase 5.4, S6: a 60-card row can hold more than one
     * copy of the same id, unlike Commander's always-1 world). A role ABSENT from this map has not
     * been touched by the user -- the UI still displays its tentative defaults (see
     * [tentativeCopies]) as pre-selected. An id present with a count of 0 never happens by
     * construction -- [DeckWizardViewModel.onChangeChoiceQuantity] drops the key entirely instead. */
    val choiceSelections: Map<RoleKey, Map<String, Int>> = emptyMap(),
) {
    /** Casual + 3-or-more colors: a non-blocking hint, never a hard gate (D9). */
    val showColorDisciplineHint: Boolean
        get() = selectedFormat?.isSixtyCardConstructed == true && colorIdentity.count { it != ManaColor.C } > 2

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.1, §10: [colorIdentity] can carry the UI-only
     * [ManaColor.C] sentinel (the "Colorless" chip, run B) — this is the ONE boundary helper that
     * strips it before the identity ever reaches the engine (an empty result IS the engine's own
     * "colorless build" signal, never a UI-only marker leaking through). */
    val engineIdentity: Set<ManaColor> get() = colorIdentity - ManaColor.C

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.1, S4: total seed COPIES so far. */
    val seedCopies: Int get() = seeds.sumOf { it.quantity }

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.1, S4: the seed-copy ceiling -- the format's
     * own deck size minus the commander's own slot (99 for Commander, the full [DeckFormat
     * .targetDeckSize] for every 60-card format). Replaces the pre-v6 flat `MAX_SEED_CARDS = 8`. */
    val seedCap: Int
        get() = (selectedFormat?.targetDeckSize ?: 60) - (if (selectedFormat?.isCommanderFormat == true) 1 else 0)

    /** Deck Wizard UX polish plan, Run 2: the ONE row id STRATEGY_PICK highlights -- a PENDING row
     * (mid color-combo pick) wins over an already-committed one, since it is what the user is
     * currently looking at; falls back to the committed pick otherwise. Fixes the pre-Run-2 bug
     * where the composable OR'd both fields together and could highlight two different rows at
     * once (`expandedStrategyPickId` on a fresh row the user just tapped, `selectedCuratedStrategyId`
     * still pointing at whatever was committed before). */
    val strategyPickSelectedId: String? get() = expandedStrategyPickId ?: selectedCuratedStrategyId

    /** Deck Wizard UX polish plan, Run 2: the STRATEGY/COLOR_PICK/STRATEGY_PICK pin's display
     * string for Review's strategy card -- `null` for Custom/no pick (the caller falls back to its
     * own "Custom" copy). Appends the tribe label for a resolved Tribal pin so Review reads "Tribal
     * — Elves" instead of the bare catalog name. */
    val strategyDisplayLabel: String?
        get() {
            val displayName = selectedCuratedStrategyId?.let { CuratedStrategyCatalog.byId(it) }?.displayName
                ?: return null
            return selectedTribeLabel?.let { "$displayName — $it" } ?: displayName
        }
}

/**
 * QA fix (Deck Engine Unification plan RUN 3b, edge-case audit 2026-07-20): [WizardPhase.ENTRY] is
 * reachable via normal back-navigation at ANY point after the user has already populated state in a
 * flow -- so a user can pick seeds/colors/a strategy, back out, and pick a DIFFERENT entry flow
 * without that state ever being cleared. Every field here represents "one entry-flow attempt"
 * scratch state, NOT a session-level preference.
 */
private fun DeckWizardUiState.resetDirectionScratchState(): DeckWizardUiState = copy(
    seeds = emptyList(),
    seedPickQuery = "",
    seedPickStructuredQuery = null,
    seedPickResults = emptyList(),
    isSearchingSeedPick = false,
    seedPickSearchError = false,
    showSeedQueue = false,
    seedDetailCard = null,
    lockedColors = emptySet(),
    colorIdentity = emptySet(),
    selectedArchetype = null,
    selectedTribeKey = null,
    selectedTribeLabel = null,
    strategyRecommendations = emptyList(),
    isLoadingCommanderStrategies = false,
    selectedCuratedStrategyId = null,
    isCustomStrategyChosen = false,
    pendingTribeStrategy = null,
    commanderTribePickerCandidates = emptyList(),
    selectedStrategyThemes = emptyList(),
    selectedPosture = null,
    strategyPickQuery = "",
    strategyPickCombos = emptyList(),
    expandedStrategyPickId = null,
    showStrategyPickColorSheet = false,
    planAnalysis = null,
    isAnalyzingPlan = false,
    ownedAvailabilityBySection = emptyMap(),
    planSectionsQuery = "",
    planSectionsStructuredQuery = null,
    planSectionsTagFilter = emptySet(),
    planSectionsCollectionResults = emptyList(),
    planSectionsScryfallResults = emptyList(),
    isSearchingPlanSectionsScryfall = false,
)

/**
 * Deck Wizard UX polish plan, Run 2: the ONE clearing shape every STRATEGY/COLOR_PICK/STRATEGY_PICK
 * recompute entry point applies to the PREVIOUS pick before a fresh one loads (mirrors
 * [DeckWizardViewModel.selectCommanderStrategy]'s own `strategy = null` clearing, plus the
 * STRATEGY_PICK-only fields that function doesn't touch) -- so the OLD selection/combo state never
 * renders as if it were still valid for the NEW anchor (a fresh commander, seed set, color set, or
 * a step re-entered via [DeckWizardViewModel.onBackPressed]).
 */
private fun DeckWizardUiState.clearStalePick(): DeckWizardUiState = copy(
    selectedCuratedStrategyId = null,
    selectedArchetype = null,
    selectedStrategyThemes = emptyList(),
    selectedTribeKey = null,
    selectedTribeLabel = null,
    selectedPosture = null,
    isCustomStrategyChosen = false,
    pendingTribeStrategy = null,
    commanderTribePickerCandidates = emptyList(),
    expandedStrategyPickId = null,
    showStrategyPickColorSheet = false,
    strategyPickCombos = emptyList(),
)

/** The PLAN_SECTIONS analysis fields, reset on every entry/exit so a re-entry can never show the
 * previous analysis before its own spinner. */
private fun DeckWizardUiState.clearPlanAnalysis(isAnalyzing: Boolean): DeckWizardUiState = copy(
    planAnalysis = null,
    ownedAvailabilityBySection = emptyMap(),
    isAnalyzingPlan = isAnalyzing,
)

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.4); generalized to every format by the
 * Deck Wizard 60-card wave (v6, plan §5 Phase 5.4, S14) — drives the wizard spec through
 * [BuildWizardDeckUseCase]. A Commander build creates a FRESH deck lazily, on [onGenerate] — NOT
 * on init — so backing out of the wizard before generating never orphans a draft (no
 * discard-if-empty machinery needed here, unlike Deck Studio). A 60-card build NEVER creates a
 * deck: it always writes into the caller's own [launchedFromDeckId] (Deck Studio's own "Build from
 * seed"/"Rebuild with the Wizard", R13/S15 — both always launch with a real `deckId`), and hands
 * off to Deck Studio only once the build succeeds.
 */
class DeckWizardViewModel(
    private val deckRepository: DeckRepository,
    private val userCardRepository: UserCardRepository,
    private val collectionProfileUseCase: CollectionProfileUseCase,
    private val searchCardsUseCase: SearchCardsUseCase,
    // Deck Wizard Commander v3 plan, Phase 4.1 -- the STRATEGY step's edhrec-theme fetch for a
    // Commander anchor ONLY (the 60-card anchors never carry EDHREC data, see
    // recommendCommanderStrategies' own KDoc). Deck Wizard 60-card wave v6 (plan §5 Phase 5.4):
    // verified this remains its ONLY production consumer before keeping it.
    private val communityAggregateRepository: CommunityAggregateRepository,
    private val crashReporter: CrashReporter,
    private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    // Deck Wizard & Engine Rework plan, Workstream 2 — the STRATEGY step's source 1
    // (`card_strategy_tags` payload). Required (like every other repository above), appended last
    // for the same positional-arg-free-call-site reason as the pure use cases below.
    private val cardStrategyTagsRepository: CardStrategyTagsRepository,
    // Deck Wizard Commander v3 plan, Phase 4.1 -- replaces the retired DeriveCommanderStrategiesUseCase.
    // Deck Wizard 60-card wave v6 (plan §5 Phase 2.1): the SAME instance now also scores every
    // 60-card anchor; typed by its real concrete name since run C (plan §5 Phase 5.4, per memory
    // `feedback_koin_single_concrete_type_mismatch` -- a Koin consumer's param type must be the
    // exact concrete name a bare `single { }` registers under).
    private val recommendCommanderStrategiesUseCase: RecommendWizardStrategiesUseCase = RecommendWizardStrategiesUseCase(),
    // Deck Wizard Commander v3 plan, Phase 5 (D2) -- the SINGLE analysis entry point PLAN_SECTIONS
    // scores against; the SAME shared singleton DeckDoctorOrchestrator/the harness use (never a
    // second instance). Required (no default, like every other repository param above): it has its
    // own non-trivial dependency graph (EvaluateDeckUseCase/InferDeckIdentityUseCase), so there is
    // no cheap fake default the way the pure use cases above get one.
    private val deckAnalysisPipeline: DeckAnalysisPipeline,
    // Deck Wizard Commander v3 plan, Phase 6; generalized to every format by the 60-card wave (v6,
    // plan §5 Phase 5.4) -- onGenerate's ONE build path now, replacing the deleted
    // the deleted Motor A wizard build use case/generateCasualDeck entirely. Defaulted from the two deps already
    // required above so no pre-existing test construction site needs to change unless it wants to
    // inject a fake/spy.
    private val buildWizardDeckUseCase: BuildWizardDeckUseCase = BuildWizardDeckUseCase(deckAnalysisPipeline, crashReporter),
    // Deck Wizard v4, W0.2 (G10/E10) -- lets generateWizardDeck pre-warm real, cached basic-land
    // Card objects the same way DeckStudioViewModel.applyLandSuggestions already does, so
    // BuildWizardDeckUseCase.materializeBasics never silently drops a colour the user's real
    // collection happens to own zero copies of. Appended last, required (no default: CardRepository
    // has no cheap fake, matches every other repository param above).
    private val cardRepository: CardRepository,
    // Deck Wizard v4, W7 Task B (E4/E8) -- lets a build bias placement toward cards the user
    // previously chose on the Choice screen (buildWithGroups' own preferenceStore param), and lets
    // onToggleChoiceCard record a freshly-made choice for FUTURE builds. Appended last, required
    // (no default -- a KeyValueStore-backed instance costs nothing to construct in production, and
    // an inert fake belongs in each test's own construction site, matching every other required
    // repository param above).
    private val wizardPreferenceStore: WizardPreferenceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckWizardUiState())
    val uiState: StateFlow<DeckWizardUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeckWizardEvent>(Channel.BUFFERED)
    val events: Flow<DeckWizardEvent> = _events.receiveAsFlow()

    /** Snapshot of the collection, resolved once at init — every downstream step (profile,
     * commander candidates, the actual build) reuses this SAME snapshot (mirrors Motor A/B's
     * "already-snapshotted collection" ownership split; avoids re-querying Room per step). */
    private var collectionSnapshot: List<com.mmg.manahub.core.model.UserCardWithCard> = emptyList()

    /** Memory optimization: pre-mapped list of cards from [collectionSnapshot] to avoid repeated
     * `.map { it.card }` calls on large collections during ranking/profile steps. */
    private var cardSnapshot: List<Card> = emptyList()

    private var commanderSearchJob: Job? = null
    private var seedPickSearchJob: Job? = null
    private var generateJob: Job? = null
    private var commanderStrategyJob: Job? = null
    private var planAnalysisJob: Job? = null
    private var planSectionsSearchJob: Job? = null

    /** Gate 5 audit (edge-case P1): [resolveComboSeeds] (launched from `init`) was NOT held in a
     * Job, so [cancelDirectionSearchJobs] could never cancel it -- a combo hand-off still resolving
     * when the user switched to a different entry flow could inject a seed into that NEW flow after
     * the fact. Cancelled alongside every other direction-scoped search job now. */
    private var comboSeedResolveJob: Job? = null

    /** STRATEGY_PICK: the combo colors waiting on a tribe sub-pick before they become
     * [DeckWizardUiState.colorIdentity]; `null` whenever no tribe pick is pending on a combo. */
    private var pendingStrategyPickColors: Set<ManaColor>? = null

    /** Set only once [onGenerate] has actually created the deck row -- lets [onCancelGeneration]
     * clean up a partial build instead of orphaning an empty draft. */
    private var pendingDeckId: String? = null

    /** Deck Wizard Commander v3 plan (Phase 6, D12); every 60-card build ALSO relies on this since
     * the wave-v6 generalization (R13/S14): the deckId nav arg (Screen.DeckWizard.createRoute) when
     * the wizard was launched from an existing Deck Studio draft (Studio's "Build from seed"/
     * "Rebuild with the Wizard" CTAs). Consumed by [generateWizardDeck] (fills THIS deck instead of
     * creating a new one -- the ONLY path for a 60-card anchor). Read once, at init. */
    private var launchedFromDeckId: String? = null

    /** Deck Wizard v4 (R15) structural guard: the `replaceConfirmed` nav arg (Screen.DeckWizard
     * .createRoute) -- true only when the caller already showed a replace-confirmation dialog for
     * [launchedFromDeckId] (Deck Studio's "Rebuild with the Wizard" confirm path). Read once, at
     * init; re-checked against the deck's REAL card count at persist time in
     * [generateWizardDeck], not trusted as a launch-time snapshot, since the deck can gain cards
     * while the wizard is open. */
    private var replaceConfirmed: Boolean = false

    /** Edge-case fix (Phase 6 adversarial pass), kept as defense-in-depth after Phase 8 JOB 2 made
     * `buildWizardDeckUseCase.persist` a single Room `@Transaction`
     * ([com.mmg.manahub.core.data.local.dao.DeckDao.persistWizardBuild]): true while that write
     * is in flight. */
    private var isWritingCommanderDeck = false

    /** W7 Fix 2: the exact args of the last [finalizeWizardDraft] attempt, so
     * [onRetryGeneration] can re-run the SAME finalize/persist call after a failure instead of
     * sending the user back to REVIEW. */
    private var pendingFinalize: PendingFinalize? = null

    /** W8 (telemetry, `deck_wizard_choice_resolution_mode`) -- which roles the user MANUALLY toggled
     * a card for vs. AUTO-filled via "Choose the remaining N for me". */
    private val manuallyToggledChoiceRoles = mutableSetOf<RoleKey>()
    private val autoFilledChoiceRoles = mutableSetOf<RoleKey>()

    /** W8 (telemetry, `deck_wizard_generate_duration_ms_bucket`) -- wall-clock from
     * [generateWizardDeck]'s entry to [finalizeWizardDraft]'s successful persist. */
    private var commanderGenerationStartAtMs: Long? = null

    /** Seed names handed to a Commander wizard, applied only once a commander exists to validate
     * them against (identity + legality) -- see the init pre-fill gate. */
    private var pendingCommanderSeedNames: List<String> = emptyList()

    /** A Commander hand-off's strategy pin, applied once the first recommendation pass has landed
     * (it would otherwise be overwritten by the auto-selected top pick). */
    private var pendingCommanderStrategyPrefill: Pair<CuratedStrategy, String?>? = null

    private data class PendingFinalize(
        val state: DeckWizardUiState,
        val format: DeckFormat,
        // Deck Wizard 60-card wave (v6), plan §5 Phase 5.4: null for every 60-card anchor.
        val commander: Card?,
        val strategyPick: StrategyPick,
        val manualAdds: List<ManualAdd>,
        val draft: WizardDraftBuild,
        val resolutions: Map<RoleKey, List<String>>,
    )

    init {
        // Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: "format" is a REQUIRED nav arg
        // (Screen.DeckWizard.createRoute) -- the wizard never renders its own format step, ever.
        // A format the wizard has no build path for (Draft, or a corrupted deep link) exits
        // immediately (DeckWizardEvent.Exit) instead of the pre-v6 "silently fall back to Casual"
        // behavior (S1: every non-Draft format has a real build path now).
        val requestedFormat = savedStateHandle.get<String?>("format")?.takeIf { it.isNotEmpty() }
            ?.let { name -> DeckFormat.entries.firstOrNull { it.name == name } }
        launchedFromDeckId = savedStateHandle.get<String?>("deckId")?.takeIf { it.isNotEmpty() }
        replaceConfirmed = savedStateHandle.get<Boolean>("replaceConfirmed") ?: false
        val resolvedFormat = requestedFormat?.takeIf { it.isCommanderFormat || it.isSixtyCardConstructed }

        if (resolvedFormat == null) {
            crashReporter.log("deck_wizard_unsupported_format")
            crashReporter.recordException(
                IllegalStateException("[DeckWizardViewModel] unsupported/missing format nav arg: $requestedFormat")
            )
            viewModelScope.launch {
                _events.send(
                    DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_unsupported_format_toast), MagicToastType.ERROR)
                )
                _events.send(DeckWizardEvent.Exit)
            }
        } else {
            _uiState.update { it.copy(selectedFormat = resolvedFormat) }
            if (resolvedFormat.isCommanderFormat) {
                logStep("commander_pick")
                _uiState.update { it.copy(phase = WizardPhase.COMMANDER_PICK, entryFlow = WizardEntryFlow.CARDS) }
            } else {
                logStep("entry")
                _uiState.update { it.copy(phase = WizardPhase.ENTRY) }
            }

            // Discoveries v2 "Build this" hand-off (D11) — optional, all blank by default. Deck
            // Engine Unification (D2): nav args carry the unified taxonomy directly (raw
            // ArchetypeId/ThemeId enum names + a tribe key) instead of the old SeedStrategy-name/
            // free-text-theme pair.
            val archetypeArg = savedStateHandle.get<String?>("archetype")?.takeIf { it.isNotEmpty() }
            val themeArg = savedStateHandle.get<String?>("theme")?.takeIf { it.isNotEmpty() }
            val tribeArg = savedStateHandle.get<String?>("tribe")?.takeIf { it.isNotEmpty() }
            val colorsArg = savedStateHandle.get<String?>("colors")?.takeIf { it.isNotEmpty() }
            // Deck Engine Unification plan D7 (4.3): a Commander Spellbook combo's card names --
            // percent-decoded by Navigation before this reads it, `|`-joined (NOT `,` -- many real
            // MTG card names contain a literal comma, e.g. "Urza, Lord High Artificer"; see
            // Screen.DeckWizard.createRoute's KDoc). Forces the CARDS flow: a combo hand-off IS a
            // card-first pick by construction.
            val seedsArg = savedStateHandle.get<String?>("seeds")?.takeIf { it.isNotEmpty() }
                ?.split("|")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

            // Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: pre-fill routes to the matching NEW
            // first step (SEED_PICK / COLOR_PICK / STRATEGY_PICK) — the pre-v6 shared DIRECTION
            // phase these hand-offs used to dispatch on is gone.
            // Commander always starts at COMMANDER_PICK: the 60-card maps would reject every coloured seed.
            val archetype = archetypeArg?.let { name -> ArchetypeId.entries.firstOrNull { a -> a.name == name } }
            val theme = themeArg?.let { name -> ThemeId.entries.firstOrNull { t -> t.name == name } }
            val nearest = archetype?.let { a -> CuratedStrategyCatalog.nearestFor(a, listOfNotNull(theme), resolvedFormat) }
            when {
                resolvedFormat.isCommanderFormat -> {
                    pendingCommanderSeedNames = seedsArg
                    // The identity is the commander's, so a colors arg has nothing to apply to.
                    if (colorsArg != null) crashReporter.log("deck_wizard_commander_prefill_colors_ignored")
                    if (nearest != null) {
                        pendingCommanderStrategyPrefill = nearest to tribeArg
                    } else if (archetypeArg != null || themeArg != null || tribeArg != null) {
                        crashReporter.log("deck_wizard_commander_prefill_strategy_unresolved")
                    }
                }
                seedsArg.isNotEmpty() -> _uiState.update { it.copy(entryFlow = WizardEntryFlow.CARDS, phase = WizardPhase.SEED_PICK) }
                colorsArg != null -> _uiState.update {
                    it.copy(entryFlow = WizardEntryFlow.COLORS, phase = WizardPhase.COLOR_PICK, colorIdentity = parseColorString(colorsArg))
                }
                archetypeArg != null || themeArg != null || tribeArg != null -> {
                    _uiState.update { it.copy(entryFlow = WizardEntryFlow.STRATEGY, phase = WizardPhase.STRATEGY_PICK) }
                    if (nearest != null) {
                        selectCommanderStrategy(nearest, tribeArg)
                    } else if (tribeArg != null) {
                        _uiState.update {
                            it.copy(selectedTribeKey = tribeArg, selectedTribeLabel = tribeArg.removePrefix("tribe:").replaceFirstChar(Char::uppercase))
                        }
                    }
                }
            }

            viewModelScope.launch {
                runCatching {
                    val collection = userCardRepository.observeCollection().first()
                    collectionSnapshot = collection
                    cardSnapshot = collection.map { it.card }
                    collectionProfileUseCase(cardSnapshot)
                }.onSuccess { profile ->
                    val ownedQuantityByName = collectionSnapshot
                        .groupBy { it.card.name }
                        .mapValues { (_, entries) -> entries.sumOf { entry -> entry.userCard.quantity } }
                    _uiState.update {
                        it.copy(
                            collectionProfile = profile,
                            ownedCards = cardSnapshot,
                            ownedQuantityByName = ownedQuantityByName,
                            isLoadingProfile = false,
                        )
                    }
                    // Gate 5 audit (edge-case P1): held in its own Job (was a bare suspend call)
                    // so cancelDirectionSearchJobs can actually cancel it -- see comboSeedResolveJob's
                    // own KDoc.
                    if (seedsArg.isNotEmpty() && !resolvedFormat.isCommanderFormat) {
                        comboSeedResolveJob = viewModelScope.launch { resolveComboSeeds(seedsArg) }
                    }
                }.onFailure { t ->
                    if (t is CancellationException) throw t
                    logFailure("deck_wizard_profile_load_failed", t)
                    _uiState.update { it.copy(isLoadingProfile = false) }
                }
            }
        }
    }

    private fun parseColorString(raw: String): Set<ManaColor> =
        raw.mapNotNull { ch -> ManaColor.entries.firstOrNull { it.symbol == ch.toString() } }.toSet()

    /** QA fix (RUN 3b): cancels any in-flight commander/seed search or strategy-recommendation fetch
     * BEFORE a format/entry-flow switch wipes the state those coroutines write into -- without this,
     * a debounced search from the ABANDONED flow could land after the reset and silently repopulate
     * stale results for the NEW flow. */
    private fun cancelDirectionSearchJobs() {
        commanderSearchJob?.cancel()
        seedPickSearchJob?.cancel()
        commanderStrategyJob?.cancel()
        planAnalysisJob?.cancel()
        planSectionsSearchJob?.cancel()
        comboSeedResolveJob?.cancel()
    }

    // ── COMMANDER_PICK ────────────────────────────────────────────────────────

    /**
     * Deck Wizard 60-card wave (v6), plan §5 Phase 5.1/5.2: the anchor's mandatory first pick
     * (Commander) resolving its color identity + kicking off strategy recommendation. Every field
     * this used to also clear ([availableThemeTags]/[selectedThemeHint], the pre-v6 Identity-step
     * theme picker) was deleted with that step in this run.
     */
    fun onSelectCommander(card: Card) {
        commanderSearchJob?.cancel()
        val identity = card.colorIdentity.toManaColorSet()
        _uiState.update {
            it.copy(
                selectedCommander = card,
                colorIdentity = identity,
                commanderQuery = "",
                commanderSearchResults = emptyList(),
                commanderSearchError = false,
                commanderStructuredQuery = null,
            )
        }
        pruneSeedsOutsideIdentity(identity)
        recomputeStrategyRecommendations()
        val stashedSeeds = pendingCommanderSeedNames
        if (stashedSeeds.isNotEmpty()) {
            pendingCommanderSeedNames = emptyList()
            comboSeedResolveJob?.cancel()
            comboSeedResolveJob = viewModelScope.launch { resolveComboSeeds(stashedSeeds) }
        }
    }

    fun onClearCommander() {
        // An in-flight EDHREC fetch would otherwise land and pre-select a strategy for no commander;
        // a still-resolving seed hand-off would add seeds against an empty identity.
        commanderStrategyJob?.cancel()
        comboSeedResolveJob?.cancel()
        _uiState.update {
            it.copy(
                selectedCommander = null,
                colorIdentity = emptySet(),
                strategyRecommendations = emptyList(),
                isLoadingCommanderStrategies = false,
                selectedCuratedStrategyId = null,
                isCustomStrategyChosen = false,
                pendingTribeStrategy = null,
                commanderTribePickerCandidates = emptyList(),
            )
        }
        pruneSeedsOutsideIdentity(emptySet())
    }

    /**
     * Drops every seed whose colour identity is not contained in [identity] — called by every
     * identity-narrowing writer, since [onAddSeed] validates only at add time. Reports the removed
     * count once (toast + bucketed breadcrumb); silent when nothing was outside.
     */
    private fun pruneSeedsOutsideIdentity(identity: Set<ManaColor>) {
        val identitySymbols = identity.filter { it != ManaColor.C }.map { it.symbol }.toSet()
        var removedCount = 0
        _uiState.update { state ->
            val (kept, dropped) = state.seeds.partition { seed -> identitySymbols.containsAll(seed.card.colorIdentity) }
            removedCount = dropped.sumOf { it.quantity }
            if (dropped.isEmpty()) state else state.copy(seeds = kept, lockedColors = kept.flatMap { it.card.colorIdentity }.toManaColorSet())
        }
        if (removedCount > 0) reportSeedsRemovedOutsideIdentity(removedCount)
    }

    private fun reportSeedsRemovedOutsideIdentity(removedCount: Int) {
        crashReporter.log("deck_wizard_seeds_pruned_outside_identity_${countBucket(removedCount)}")
        viewModelScope.launch {
            _events.send(
                DeckWizardEvent.ShowToast(
                    appContext.resources.getQuantityString(R.plurals.deck_wizard_seeds_pruned_outside_identity, removedCount, removedCount),
                    MagicToastType.WARNING,
                )
            )
        }
    }

    /**
     * Deck Wizard Commander v4 plan (W2.1, G2/R2): COMMANDER_PICK's own name-filter search bar --
     * feeds the SAME unified idle/search pipeline as [applyCommanderStructuredSearch]
     * ([triggerCommanderPickSearch]), debounced like every other incremental search field in this
     * VM.
     */
    fun onCommanderNameFilterChange(query: String) {
        _uiState.update { it.copy(commanderQuery = query) }
        triggerCommanderPickSearch(debounce = true)
    }

    /**
     * Deck Wizard Commander v4 plan (W2.1, G2/R2): the Tune icon's [com.mmg.manahub.core.ui
     * .components.search.AdvancedSearchSheet] result for COMMANDER_PICK. The sheet force-merges the
     * step's locked criteria ([commanderLockedCriteria]) into [query] before this is ever called
     * (D14), so this function never needs to re-add them. An explicit Search tap fires immediately
     * (no debounce -- it is a deliberate user action, not incremental typing).
     */
    fun applyCommanderStructuredSearch(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(commanderStructuredQuery = query.takeIf { q -> !q.isEmpty() }) }
        triggerCommanderPickSearch(debounce = false)
    }

    /** Deck Wizard Commander v4 plan (W2.2, G2/R2): the always-visible "clear filters" row --
     * drops [DeckWizardUiState.commanderStructuredQuery] back to `null` (the sheet re-merges the
     * locked criteria on its next open) but leaves [DeckWizardUiState.commanderQuery] alone, mirroring
     * `CollectionScreen.kt`'s own `onClearFilters` (does not touch the search bar text). */
    fun onClearCommanderFilters() {
        _uiState.update { it.copy(commanderStructuredQuery = null) }
        triggerCommanderPickSearch(debounce = false)
    }

    /** Deck Wizard Commander v4 plan (W2.2, G2): the zero-results empty state's own "clear filters"
     * affordance -- clears BOTH the plain search text and the structured query, mirroring
     * `CollectionScreen.kt`'s documented "a zero-result state can be caused by either one alone"
     * rule. */
    fun onClearCommanderSearchAndFilters() {
        _uiState.update { it.copy(commanderQuery = "", commanderStructuredQuery = null) }
        triggerCommanderPickSearch(debounce = false)
    }

    /**
     * Deck Wizard Commander v4 plan (W2.1, G2/R2/E9): the ONE trigger behind COMMANDER_PICK's
     * search bar and its Tune-icon filters. [commanderPickIsIdle] decides idle (no query text AND
     * no user-added filter beyond [commanderLockedCriteria]) vs. search: idle clears
     * [DeckWizardUiState.commanderSearchResults] and lets the UI fall back to the local owned-grid
     * ([commanderPickLocalCandidates]); otherwise [buildCommanderSearchQuery] combines the locked
     * criteria, any user-added structured criteria, and the plain name text into ONE
     * [AdvancedSearchQuery], rendered to a Scryfall fragment via the SAME [StructuredCardSearch]
     * helper every other structured-search caller in this VM uses -- no second query-building path.
     */
    private fun triggerCommanderPickSearch(debounce: Boolean) {
        commanderSearchJob?.cancel()
        val state = _uiState.value
        val lockedCriteria = commanderLockedCriteria(state.selectedFormat)
        val effectiveQuery = buildCommanderSearchQuery(state, lockedCriteria)
        if (effectiveQuery == null) {
            _uiState.update { it.copy(commanderSearchResults = emptyList(), isSearchingCommander = false, commanderSearchError = false) }
            return
        }
        val fragment = StructuredCardSearch.scryfallFragment(effectiveQuery)
        if (fragment == null) {
            _uiState.update { it.copy(commanderSearchResults = emptyList(), isSearchingCommander = false, commanderSearchError = false) }
            return
        }
        commanderSearchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingCommander = true, commanderSearchError = false) }
            val results = when (val res = searchCardsUseCase(fragment)) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_commander_structured_search_failed")
                    _uiState.update { it.copy(commanderSearchError = true) }
                    emptyList()
                }
            }
            _uiState.update { it.copy(commanderSearchResults = results, isSearchingCommander = false) }
        }
    }

    /** COMMANDER_PICK's "Next" — requires a commander. [recommendCommanderStrategies] already ran
     * on [onSelectCommander] so this is normally a pure phase transition; the self-heal recompute
     * below only fires when [DeckWizardUiState.strategyRecommendations] came back empty (an
     * [onBackPressed] from STRATEGY clears it, Deck Wizard UX polish plan Run 2 -- re-entering with
     * the SAME, unchanged commander must not strand STRATEGY on a permanently empty list). */
    fun onNextFromCommanderPick() {
        val commander = _uiState.value.selectedCommander
        if (commander == null) {
            crashReporter.log("deck_wizard_step_commander_pick_blocked_no_commander")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_commander_required)))
            }
            return
        }
        logStep("strategy")
        _uiState.update { it.copy(phase = WizardPhase.STRATEGY) }
        if (_uiState.value.strategyRecommendations.isEmpty()) recommendCommanderStrategies(commander)
    }

    // ── SEED_PICK ("Start from cards", Deck Wizard 60-card wave v6, plan §5 Phase 5.2) ───────────

    /** SEED_PICK's own name-filter search bar text. */
    fun onSeedPickQueryChange(query: String) {
        _uiState.update { it.copy(seedPickQuery = query) }
        triggerSeedPickSearch(debounce = true)
    }

    /** The Tune icon's [com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet] result for
     * SEED_PICK, locked to [DeckWizardSixtySteps.seedLockedCriteria] (D14, mirrors
     * [applyCommanderStructuredSearch]). */
    fun applySeedPickStructuredSearch(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(seedPickStructuredQuery = query.takeIf { q -> !q.isEmpty() }) }
        triggerSeedPickSearch(debounce = false)
    }

    fun onClearSeedPickFilters() {
        _uiState.update { it.copy(seedPickStructuredQuery = null) }
        triggerSeedPickSearch(debounce = false)
    }

    fun onClearSeedPickSearchAndFilters() {
        _uiState.update { it.copy(seedPickQuery = "", seedPickStructuredQuery = null) }
        triggerSeedPickSearch(debounce = false)
    }

    /** Mirrors [triggerCommanderPickSearch]'s exact idle/search dispatch, for SEED_PICK's own
     * format-legality-locked query ([DeckWizardSixtySteps.seedLockedCriteria]). */
    private fun triggerSeedPickSearch(debounce: Boolean) {
        seedPickSearchJob?.cancel()
        val state = _uiState.value
        val format = state.selectedFormat ?: return
        val lockedCriteria = seedLockedCriteria(format)
        val effectiveQuery = buildSeedPickSearchQuery(state, lockedCriteria)
        if (effectiveQuery == null) {
            _uiState.update { it.copy(seedPickResults = emptyList(), isSearchingSeedPick = false, seedPickSearchError = false) }
            return
        }
        val fragment = StructuredCardSearch.scryfallFragment(effectiveQuery)
        if (fragment == null) {
            _uiState.update { it.copy(seedPickResults = emptyList(), isSearchingSeedPick = false, seedPickSearchError = false) }
            return
        }
        seedPickSearchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingSeedPick = true, seedPickSearchError = false) }
            val results = when (val res = searchCardsUseCase(fragment)) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_seed_search_failed")
                    // res.message is a raw Ktor exception message (CardRepositoryImpl's safeCall
                    // does no sanitization) -- for an HTTP error it commonly embeds the full
                    // request URL, which includes this search's own free-text query. Never forward
                    // it verbatim (CLAUDE.md: no raw free-text queries in telemetry) -- length only.
                    crashReporter.recordException(
                        RuntimeException("[DeckWizard] deck_wizard_seed_search_failed: message_length=${res.message.length}"),
                    )
                    _uiState.update { it.copy(seedPickSearchError = true) }
                    emptyList()
                }
            }
            _uiState.update { it.copy(seedPickResults = results, isSearchingSeedPick = false) }
        }
    }

    /** SEED_PICK's toggle for the seed queue sheet (the "N cards added" sticky pill). */
    fun onToggleSeedQueue() = _uiState.update { it.copy(showSeedQueue = !it.showSeedQueue) }

    /** Opens/closes [com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet] in
     * seed-selection mode from SEED_PICK (tile tap, or the seed-queue sheet's image tap). */
    fun onShowSeedDetail(card: Card?) = _uiState.update { it.copy(seedDetailCard = card) }

    /** SEED_PICK's "Next" -- requires at least one seed (S3/S4). */
    fun onNextFromSeedPick() {
        if (_uiState.value.seeds.isEmpty()) {
            crashReporter.log("deck_wizard_step_seed_pick_blocked_no_seeds")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_seeds_required)))
            }
            return
        }
        logStep("strategy")
        recomputeStrategyRecommendations()
        _uiState.update { it.copy(phase = WizardPhase.STRATEGY) }
    }

    // ── STRATEGY (shared by every anchor, S7) ─────────────────────────────────

    /**
     * Deck Wizard 60-card wave (v6), plan §5 Phase 5.3: the ONE entry point every "Next into
     * STRATEGY" / color-chip toggle / STRATEGY_PICK entry calls now, with the full 5-signal wiring.
     * Commander delegates unchanged onto [recommendCommanderStrategies] (byte-identical, rule 0.3).
     * Every 60-card anchor:
     * - CARDS flow: `Sixty(engineIdentity, seeds.map { it.card })`.
     * - COLORS flow: `Sixty(engineIdentity, seeds = emptyList())` -- empty until ≥1 color chip is
     *   picked (no anchor call at all otherwise, see the early-return below).
     * - STRATEGY flow: `Sixty(identity = emptySet(), seeds = emptyList())` -- an empty identity
     *   zeroes the color-affinity signal, so owned support alone ranks the catalog (plan's own
     *   "ranked by owned support only" instruction for STRATEGY_PICK).
     * `ownTags`/`ownTribes` come from up to [MAX_SEED_TAGS_FETCH] seeds' own `card_strategy_tags`
     * (first N by quantity desc, existing best-effort repository call, union of every found tag/
     * tribe) -- `edhrecThemeNames` stays empty for every 60-card anchor (no EDHREC data exists for
     * seeds/colors/strategy picks, only for a specific commander).
     *
     * Deck Wizard UX polish plan, Run 2: every branch clears the PREVIOUS pick/list synchronously
     * (via [DeckWizardUiState.clearStalePick]) before doing any async work, so a step entry (or a
     * re-entry via [onBackPressed]) never renders the old recommendation list/selection while the
     * new one is still loading.
     */
    private fun recomputeStrategyRecommendations(debounceMs: Long = 0L) {
        val state = _uiState.value
        val format = state.selectedFormat ?: return
        if (format.isCommanderFormat) {
            val commander = state.selectedCommander ?: return
            recommendCommanderStrategies(commander)
            return
        }
        commanderStrategyJob?.cancel()
        if (state.entryFlow == WizardEntryFlow.COLORS && state.colorIdentity.isEmpty()) {
            _uiState.update { it.copy(strategyRecommendations = emptyList(), isLoadingCommanderStrategies = false).clearStalePick() }
            return
        }
        _uiState.update { it.copy(strategyRecommendations = emptyList(), isLoadingCommanderStrategies = true).clearStalePick() }
        commanderStrategyJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val identity = if (state.entryFlow == WizardEntryFlow.STRATEGY) emptySet() else state.engineIdentity
            val seedsForAnchor = if (state.entryFlow == WizardEntryFlow.CARDS) state.seeds.map { it.card } else emptyList()
            val anchor = BuildAnchor.Sixty(identity = identity, seeds = seedsForAnchor)

            val topSeeds = state.seeds.sortedByDescending { it.quantity }.take(MAX_SEED_TAGS_FETCH).map { it.card }
            val tagResults = topSeeds.map { seed ->
                runCatching { cardStrategyTagsRepository.getStrategyTags(seed.oracleId) }
                    .onFailure { t ->
                        if (t is CancellationException) throw t
                        crashReporter.log("deck_wizard_seed_strategy_tags_fetch_failed")
                    }
                    .getOrNull() as? CardStrategyTagsResult.Found
            }
            val ownTags = tagResults.flatMap { it?.tags.orEmpty() }.distinct()
            val ownTribes = tagResults.flatMap { it?.tribes.orEmpty() }.distinct()

            val recommendations = recommendCommanderStrategiesUseCase(
                format = format,
                anchor = anchor,
                ownedCollection = cardSnapshot.map { OwnedCard(it, 1) },
                ownTags = ownTags,
                ownTribes = ownTribes,
            )
            // The use case is not suspending: a job cancelled by Back must not land its zombie list.
            ensureActive()
            _uiState.update { it.copy(strategyRecommendations = recommendations, isLoadingCommanderStrategies = false) }
            // STRATEGY_PICK commits a pick only through a color-combo tap (onSelectStrategyPickCombo):
            // preselecting #1 there would highlight a row and enable Next with an empty identity.
            if (state.entryFlow == WizardEntryFlow.STRATEGY) return@launch
            val topPick = recommendations.firstOrNull()
            if (topPick != null) selectCommanderStrategy(topPick.strategy, topPick.tribe) else selectCommanderStrategy(null, null)
        }
    }

    /**
     * Deck Wizard Commander v3 plan, Phase 4.1 — ranks [com.mmg.manahub.feature.decks.domain.engine
     * .CuratedStrategyCatalog.ALL] for [commander] via [RecommendWizardStrategiesUseCase]. Never
     * throws: every signal degrades to zero contribution when its input is empty/absent. Preselects
     * the #1 recommendation on arrival via [selectCommanderStrategy].
     */
    private fun recommendCommanderStrategies(commander: Card) {
        commanderStrategyJob?.cancel()
        _uiState.update { it.copy(strategyRecommendations = emptyList(), isLoadingCommanderStrategies = true).clearStalePick() }
        commanderStrategyJob = viewModelScope.launch {
            val format = _uiState.value.selectedFormat ?: DeckFormat.COMMANDER
            val source1 = runCatching { cardStrategyTagsRepository.getStrategyTags(commander.oracleId) }
                .onFailure { t ->
                    if (t is CancellationException) throw t
                    crashReporter.log("deck_wizard_commander_strategy_tags_fetch_failed")
                }
                .getOrNull() as? CardStrategyTagsResult.Found
            val edhrecThemeNames = runCatching {
                val result = communityAggregateRepository.getCommanderAggregate(commander.name)
                (result as? DataResult.Success)?.data?.themeTags?.map { it.name }.orEmpty()
            }.getOrElse { t ->
                if (t is CancellationException) throw t
                emptyList()
            }

            val recommendations = recommendCommanderStrategiesUseCase(
                format = format,
                commander = commander,
                identity = commander.colorIdentity.toManaColorSet(),
                ownedCollection = cardSnapshot.map { OwnedCard(it, 1) },
                ownTags = source1?.tags.orEmpty(),
                ownTribes = source1?.tribes.orEmpty(),
                edhrecThemeNames = edhrecThemeNames,
            )
            ensureActive()
            _uiState.update { it.copy(strategyRecommendations = recommendations, isLoadingCommanderStrategies = false) }
            val prefill = pendingCommanderStrategyPrefill
            pendingCommanderStrategyPrefill = null
            val topPick = recommendations.firstOrNull()
            when {
                prefill != null -> selectCommanderStrategy(prefill.first, prefill.second)
                topPick != null -> selectCommanderStrategy(topPick.strategy, topPick.tribe)
                else -> selectCommanderStrategy(null, null)
            }
        }
    }

    /** Writes [strategy]/[tribe]'s pin into the shared archetype/themes/tribe fields (D4:
     * `CuratedStrategy.toPin`, the SAME function Deck Studio's own strategy picker uses) plus the
     * STRATEGY step's own [DeckWizardUiState.selectedCuratedStrategyId] display pointer.
     * `strategy == null` is Custom (D6): every pin field clears. [isExplicitCustom] is true ONLY
     * for [onSelectCustomStrategy]'s own deliberate tap -- see [DeckWizardUiState
     * .isCustomStrategyChosen]'s own KDoc for why a `null` recommendation fallback must NOT set it. */
    private fun selectCommanderStrategy(strategy: CuratedStrategy?, tribe: String?, isExplicitCustom: Boolean = false) {
        val pin = strategy?.toPin(tribe)
        _uiState.update {
            it.copy(
                selectedCuratedStrategyId = strategy?.id,
                isCustomStrategyChosen = isExplicitCustom,
                selectedArchetype = pin?.archetype,
                selectedStrategyThemes = pin?.themes.orEmpty(),
                selectedTribeKey = pin?.tribe,
                selectedTribeLabel = pin?.tribe?.let { key -> key.removePrefix("tribe:").replaceFirstChar(Char::uppercase) },
                selectedPosture = pin?.posture,
            )
        }
    }

    /** Selects a "Recommended"/"Partial fit" row. A [CuratedStrategy.requiresTribe] entry the
     * recommender could already resolve a concrete tribe for (its own
     * [StrategyRecommendation.tribe]) applies immediately; one it could NOT resolve opens the tribe
     * sub-picker instead ([onRequestTribeForStrategy]) rather than applying with a `null` tribe. */
    fun onSelectCommanderStrategy(strategy: CuratedStrategy) {
        val recommendation = _uiState.value.strategyRecommendations.firstOrNull { it.strategy.id == strategy.id }
        val tribe = recommendation?.tribe
        if (strategy.requiresTribe && tribe == null) {
            onRequestTribeForStrategy(strategy)
        } else {
            selectCommanderStrategy(strategy, tribe)
        }
    }

    /** D6: Custom is always separately offered, regardless of what the recommender returned. */
    fun onSelectCustomStrategy() = selectCommanderStrategy(null, null, isExplicitCustom = true)

    /** Opens the tribe sub-picker for [strategy], seeded with the collection's dominant tribes
     * within the current identity — reuses [CollectionProfileUseCase.dominantTribes] over an
     * identity-filtered slice of [cardSnapshot] (no new dominant-tribe computation). */
    fun onRequestTribeForStrategy(strategy: CuratedStrategy, identity: Set<ManaColor> = _uiState.value.colorIdentity) {
        pendingStrategyPickColors = null
        val identitySymbols = identity.map { it.symbol }.toSet()
        viewModelScope.launch {
            val identityCards = cardSnapshot.filter { card -> identitySymbols.containsAll(card.colorIdentity) }
            val profile = collectionProfileUseCase(identityCards, limit = TRIBE_PICKER_CANDIDATE_LIMIT)
            _uiState.update {
                it.copy(pendingTribeStrategy = strategy, commanderTribePickerCandidates = profile.dominantTribes)
            }
        }
    }

    /** Cancelling the tribe sub-picker leaves nothing half-applied: a STRATEGY_PICK combo whose
     * identity was waiting on this tribe is dropped and its row collapses back to "nothing pending". */
    fun onCancelTribePickForStrategy() {
        crashReporter.log("deck_wizard_tribe_pick_cancelled")
        val hadPendingCombo = pendingStrategyPickColors != null
        pendingStrategyPickColors = null
        _uiState.update { state ->
            val base = state.copy(pendingTribeStrategy = null, commanderTribePickerCandidates = emptyList())
            if (hadPendingCombo && state.expandedStrategyPickId != state.selectedCuratedStrategyId) {
                base.copy(expandedStrategyPickId = null, strategyPickCombos = emptyList())
            } else {
                base
            }
        }
    }

    fun onPickTribeForStrategy(tribeKey: String) {
        val strategy = _uiState.value.pendingTribeStrategy ?: return
        val pendingColors = pendingStrategyPickColors
        pendingStrategyPickColors = null
        _uiState.update {
            it.copy(
                pendingTribeStrategy = null,
                commanderTribePickerCandidates = emptyList(),
                colorIdentity = pendingColors ?: it.colorIdentity,
            )
        }
        if (pendingColors != null) pruneSeedsOutsideIdentity(pendingColors)
        selectCommanderStrategy(strategy, tribeKey)
    }

    /** STRATEGY_PICK's own free-text taxonomy filter (run B wires its UI, plan §5 Phase 5.3). */
    fun onStrategyPickQueryChange(query: String) = _uiState.update { it.copy(strategyPickQuery = query) }

    /** [com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity.combosFor] (curated
     * ranking) blended with how strong the user's OWN collection already is in each candidate combo
     * ([CollectionProfile.colorShares]) -- a taxonomy pick favors color combos the user can actually
     * build today, not a purely abstract ranking. Retargeted (60-card wave v6) to
     * [DeckWizardUiState.strategyPickCombos]; wired to a real caller in run B (plan §5 Phase 5.3). */
    private fun recomputeColorComboSuggestions(archetype: ArchetypeId?, theme: ThemeId?) {
        val shareByColor = _uiState.value.collectionProfile?.colorShares?.associate { it.color to it.share }.orEmpty()
        val combos = com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity.combosFor(archetype, theme)
            .map { (colors, weight) ->
                val avgShare = if (colors.isEmpty()) 0f else colors.map { shareByColor[it] ?: 0f }.average().toFloat()
                ColorComboSuggestion(colors, weight * (0.5f + avgShare))
            }
            .sortedByDescending { it.score }
        _uiState.update { it.copy(strategyPickCombos = combos) }
    }

    /**
     * Deck Wizard 60-card wave (v6), plan §5 Phase 5.3: the ONE shared entry into PLAN_SECTIONS --
     * Commander ([onNextFromStrategy]), Cards ([onNextFromStrategy], same STRATEGY step), Colors
     * ([onNextFromColorPick]), Strategy ([onNextFromStrategyPick]) all funnel here.
     */
    private fun enterPlanSections() {
        logStep("plan_sections")
        _uiState.update { it.copy(phase = WizardPhase.PLAN_SECTIONS).clearPlanAnalysis(isAnalyzing = true) }
        recomputePlanAnalysis(debounce = false)
    }

    /**
     * STRATEGY's "Next" (2.4/5.1) — shared by Commander AND the Cards flow now that STRATEGY is one
     * step reused across both anchors (S1). ALWAYS enabled: an untouched pick already means GENERIC
     * ("Balanced"), a valid, complete plan for every anchor.
     */
    fun onNextFromStrategy() = enterPlanSections()

    // ── COLOR_PICK (run B, plan §5 Phase 5.3) ─────────────────────────────────────────────────────

    /** COLOR_PICK's own color toggle -- a plain WUBRG chip toggle, plus the exclusive Colorless (`C`)
     * chip: picking `C` clears every WUBRG color (`colorIdentity = {C}`); picking any WUBRG color
     * clears `C` (S9). `C` is a UI-only sentinel -- [DeckWizardUiState.engineIdentity] strips it
     * before the identity ever reaches the engine (an empty result IS the real "colorless build"
     * signal). Re-ranks [strategyRecommendations], debounced [COLOR_PICK_STRATEGY_DEBOUNCE_MS] so a
     * burst of chip taps doesn't fire one recompute per tap. */
    fun onToggleColorFlowColor(color: ManaColor) {
        _uiState.update { state ->
            val updated = if (color == ManaColor.C) {
                if (ManaColor.C in state.colorIdentity) emptySet() else setOf(ManaColor.C)
            } else {
                val withoutColorless = state.colorIdentity - ManaColor.C
                if (color in withoutColorless) withoutColorless - color else withoutColorless + color
            }
            state.copy(colorIdentity = updated)
        }
        pruneSeedsOutsideIdentity(_uiState.value.engineIdentity)
        recomputeStrategyRecommendations(debounceMs = COLOR_PICK_STRATEGY_DEBOUNCE_MS)
    }

    /** COLOR_PICK's "Next" -- requires a color AND a strategy pick (S1.2's own Next-enable rule);
     * [DeckWizardUiState.isCustomStrategyChosen] resolves the "Custom vs. nothing picked yet"
     * ambiguity a bare `selectedCuratedStrategyId == null` check cannot. */
    fun onNextFromColorPick() {
        val state = _uiState.value
        if (state.colorIdentity.isEmpty() || (state.selectedCuratedStrategyId == null && !state.isCustomStrategyChosen)) {
            crashReporter.log("deck_wizard_step_color_pick_blocked_no_strategy")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_strategy_required)))
            }
            return
        }
        enterPlanSections()
    }

    // ── STRATEGY_PICK (run B, plan §5 Phase 5.3) ──────────────────────────────────────────────────

    /**
     * STRATEGY_PICK's own catalog-row tap -- mirrors the deleted pre-v6 Flow C's "pick strategy,
     * then pick matching colors" two-step (this time against the curated catalog, not raw taxonomy):
     * tapping a row marks it as PENDING ([DeckWizardUiState.expandedStrategyPickId]), opens its
     * color-combo picker ([DeckWizardUiState.showStrategyPickColorSheet]) and ranks its combos via
     * the existing [recomputeColorComboSuggestions] -- it does NOT yet commit
     * [DeckWizardUiState.colorIdentity]/the strategy pin, that happens in
     * [onSelectStrategyPickCombo]. Re-tapping the pending row while its picker is open closes it
     * ([onDismissStrategyPickColorSheet]).
     *
     * Single selection: tapping a DIFFERENT row than the committed one drops that commit (pin AND
     * `colorIdentity`) first, so [DeckWizardUiState.strategyPickSelectedId] can only ever name one
     * row. [DeckWizardUiState.selectedCuratedStrategyId] is otherwise left untouched here (only set
     * once a combo is actually chosen) -- a colorless combo pick sets `colorIdentity = emptySet()`,
     * which would be indistinguishable from "nothing chosen yet" if this function set
     * `selectedCuratedStrategyId` on the FIRST tap instead of the combo tap.
     */
    fun onSelectStrategyPickEntry(strategy: CuratedStrategy) {
        val state = _uiState.value
        if (state.expandedStrategyPickId == strategy.id && state.showStrategyPickColorSheet) {
            onDismissStrategyPickColorSheet()
            return
        }
        val isOtherRowCommitted = state.selectedCuratedStrategyId != null && state.selectedCuratedStrategyId != strategy.id
        _uiState.update {
            val base = if (isOtherRowCommitted) it.clearStalePick().copy(colorIdentity = emptySet()) else it
            base.copy(expandedStrategyPickId = strategy.id, showStrategyPickColorSheet = true, strategyPickCombos = emptyList())
        }
        if (isOtherRowCommitted) pruneSeedsOutsideIdentity(emptySet())
        recomputeColorComboSuggestions(strategy.archetypes.firstOrNull(), strategy.themes.firstOrNull())
    }

    /** Closes STRATEGY_PICK's color-combo picker without a pick -- a row that never committed a
     * combo collapses back to "nothing pending"; a committed row keeps its highlight. */
    fun onDismissStrategyPickColorSheet() {
        val state = _uiState.value
        val committedForRow = state.expandedStrategyPickId != null && state.expandedStrategyPickId == state.selectedCuratedStrategyId
        if (!committedForRow && state.showStrategyPickColorSheet) crashReporter.log("deck_wizard_strategy_pick_color_sheet_dismissed")
        _uiState.update {
            if (committedForRow) {
                it.copy(showStrategyPickColorSheet = false)
            } else {
                it.copy(showStrategyPickColorSheet = false, expandedStrategyPickId = null, strategyPickCombos = emptyList())
            }
        }
    }

    /** STRATEGY_PICK's own combo pick -- commits [DeckWizardUiState.colorIdentity], closes the
     * picker AND finalizes the strategy pin (or opens the existing tribe sub-picker first, for a
     * `requiresTribe` entry, same as every other strategy-pick surface). */
    fun onSelectStrategyPickCombo(strategy: CuratedStrategy, combo: ColorComboSuggestion) {
        _uiState.update { it.copy(showStrategyPickColorSheet = false) }
        if (strategy.requiresTribe) {
            // The identity commits together with the tribe, so a cancelled tribe pick has nothing to undo.
            onRequestTribeForStrategy(strategy, identity = combo.colors)
            pendingStrategyPickColors = combo.colors
        } else {
            _uiState.update { it.copy(colorIdentity = combo.colors) }
            pruneSeedsOutsideIdentity(combo.colors)
            selectCommanderStrategy(strategy, null)
        }
    }

    /** STRATEGY_PICK's "Next" -- requires a fully committed strategy (see [onSelectStrategyPickEntry]'s
     * own KDoc for why [DeckWizardUiState.selectedCuratedStrategyId] alone is the right signal here). */
    fun onNextFromStrategyPick() {
        if (_uiState.value.selectedCuratedStrategyId == null) {
            crashReporter.log("deck_wizard_step_strategy_pick_blocked_no_strategy")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_strategy_required)))
            }
            return
        }
        enterPlanSections()
    }

    // ── PLAN_SECTIONS (shared by every anchor since MANUAL_ADDS/Casual was deleted) ───────────────

    /**
     * The ONLY analysis call this step makes -- off the main thread by construction, debounced only
     * for in-step seed edits ([debounce]; step entry runs immediately). [mainboard] mirrors EXACTLY
     * what the wizard's own build engine passes to `analyze` for a REAL build: the commander (if
     * any) as its own [DeckEntry] first, then every [DeckWizardUiState.seeds] entry at its real
     * quantity -- so [DeckWizardUiState.planAnalysis] is exactly the object the real build's own
     * verify pass will produce, never an approximation.
     *
     * A thrown exception degrades to `planAnalysis = null` -- the step's empty/error state, never a
     * VM crash.
     */
    private fun recomputePlanAnalysis(debounce: Boolean = true) {
        planAnalysisJob?.cancel()
        // Loading flips BEFORE any delay so the step never renders the error/empty state (analysis
        // null, not analyzing) during the debounce window.
        _uiState.update { it.copy(isAnalyzingPlan = true) }
        planAnalysisJob = viewModelScope.launch {
            if (debounce) delay(PLAN_ANALYSIS_DEBOUNCE_MS)
            val state = _uiState.value
            val commander = state.selectedCommander
            val format = state.selectedFormat ?: DeckFormat.COMMANDER
            val mainboard = buildList {
                commander?.let { add(DeckEntry(card = it, quantity = 1, isOwned = true, isSideboard = false)) }
                state.seeds.forEach { seed ->
                    add(
                        DeckEntry(
                            card = seed.card,
                            quantity = seed.quantity,
                            isOwned = cardSnapshot.any { owned -> owned.scryfallId == seed.card.scryfallId },
                            isSideboard = false,
                        )
                    )
                }
            }
            val analysis = runCatching {
                deckAnalysisPipeline.analyze(
                    mainboard = mainboard,
                    format = format,
                    commander = commander,
                    archetypeOverride = state.selectedArchetype?.name,
                    themesOverride = state.selectedStrategyThemes.map { it.name },
                    tribeOverride = state.selectedTribeKey,
                    postureOverride = state.selectedPosture?.name,
                    emitProgression = false,
                ).analysis
            }.onFailure { t ->
                // A cancelled (superseded) pass is not a failure and must not touch the new pass's loading flag.
                if (t is CancellationException) throw t
                logFailure("deck_wizard_plan_analysis_failed", t)
            }.getOrNull()
            val excludeIds = (listOfNotNull(commander?.scryfallId) + state.seeds.map { it.card.scryfallId }).toSet()
            val availability = analysis?.let {
                computeOwnedAvailabilityBySection(
                    sections = it.pillars.flatMap { pillar -> pillar.sections },
                    ownedCards = cardSnapshot,
                    excludeIds = excludeIds,
                    identity = commander?.colorIdentity?.toManaColorSet() ?: state.engineIdentity,
                    format = format,
                    enforceIdentity = format.isCommanderFormat || state.entryFlow != WizardEntryFlow.CARDS,
                    ownedQuantityByName = state.ownedQuantityByName,
                )
            }.orEmpty()
            ensureActive()
            _uiState.update { it.copy(planAnalysis = analysis, isAnalyzingPlan = false, ownedAvailabilityBySection = availability) }
        }
    }

    /** PLAN_SECTIONS' "Browse for &lt;Category&gt;" sheet -- plain search-bar text, updates the
     * Collection tab only. */
    fun onPlanSectionsQueryChange(query: String) {
        _uiState.update { it.copy(planSectionsQuery = query) }
        publishPlanSectionsCollectionResults()
    }

    /** The Tune icon's [com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet] result, OR a
     * section's own "Browse for X" preset -- filters BOTH result tabs from one [AdvancedSearchQuery]
     * via the shared [StructuredCardSearch] helper. */
    fun applyPlanSectionsStructuredSearch(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(planSectionsStructuredQuery = query.takeIf { q -> !q.isEmpty() }) }
        publishPlanSectionsCollectionResults()
        searchPlanSectionsScryfall(_uiState.value.planSectionsQuery)
    }

    /** The Analysis-tab-style category browse filter for the Collection tab. Deck Wizard UX polish
     * plan, Run 1 §1.2: [keys] carries the originating section id itself (a single-element set,
     * mirroring [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.searchCollectionByTags]) —
     * resolves the REAL [SectionMembership.predicate] from it via a [SectionQueryContext] built the
     * same way [DeckWizardCommanderSteps]'s own `queryContext` is. */
    fun searchPlanSectionsCollectionByTags(keys: Set<String>) {
        val state = _uiState.value
        val sectionId = keys.firstOrNull()
        val format = state.selectedFormat ?: DeckFormat.COMMANDER
        val dominantTribe = state.selectedTribeKey?.removePrefix(TribeDeriver.TRIBE_PREFIX)
        val context = SectionQueryContext(colorIdentity = state.colorIdentity, format = format, dominantTribe = dominantTribe)
        val predicate = sectionId?.let { SectionMembership.predicate(it, context) }
        _uiState.update { it.copy(planSectionsTagFilter = keys, planSectionsPredicate = predicate) }
        publishPlanSectionsCollectionResults()
    }

    private fun publishPlanSectionsCollectionResults() {
        val state = _uiState.value
        val sectionPredicate = state.planSectionsPredicate
        val structuralMatch: (Card) -> Boolean = if (sectionPredicate != null) {
            // The category predicate alone would list off-identity/illegal cards onAddSeed rejects.
            val format = state.selectedFormat ?: DeckFormat.COMMANDER
            val gate = SectionSearchQuery.localStructuralGate(
                SectionQueryContext(colorIdentity = state.colorIdentity, format = format, dominantTribe = null),
                enforceIdentity = format.isCommanderFormat || state.entryFlow != WizardEntryFlow.CARDS,
            )
            val combined: (Card) -> Boolean = { card -> sectionPredicate(card) && gate(card) }
            combined
        } else {
            val structuredQuery = state.planSectionsStructuredQuery
            { card -> StructuredCardSearch.matches(card, structuredQuery) }
        }
        val matches = state.ownedCards
            .filter { card ->
                structuralMatch(card) &&
                    (state.planSectionsQuery.isBlank() || card.name.contains(state.planSectionsQuery, ignoreCase = true))
            }
            // Gate 5 audit (edge-case P1): one PRINTING per name, mirroring seedPickLocalCandidates
            // (DeckWizardSixtySteps.kt) -- two different printings of the same card name must never
            // both appear here, or onAddSeed's own per-name copy cap (below) becomes bypassable by
            // adding "the same card" via its second printing.
            .distinctBy { it.name }
        _uiState.update { it.copy(planSectionsCollectionResults = matches) }
    }

    /** The All-cards tab's real Scryfall search -- [query] (typed free text) combined with
     * [DeckWizardUiState.planSectionsStructuredQuery]'s own Scryfall fragment. */
    fun searchPlanSectionsScryfall(query: String) {
        _uiState.update { it.copy(planSectionsQuery = query) }
        val fragment = _uiState.value.planSectionsStructuredQuery?.let { StructuredCardSearch.scryfallFragment(it) }
        val effectiveQuery = listOfNotNull(query.takeIf { it.isNotBlank() }, fragment).joinToString(" ")
        planSectionsSearchJob?.cancel()
        if (effectiveQuery.isBlank()) {
            _uiState.update { it.copy(planSectionsScryfallResults = emptyList(), isSearchingPlanSectionsScryfall = false) }
            return
        }
        planSectionsSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingPlanSectionsScryfall = true) }
            val results = when (val res = searchCardsUseCase(effectiveQuery)) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_plan_sections_search_failed")
                    emptyList()
                }
            }
            _uiState.update { it.copy(planSectionsScryfallResults = results, isSearchingPlanSectionsScryfall = false) }
        }
    }

    /** Resets every PLAN_SECTIONS browse-sheet field -- called on the sheet's `onDismiss`. */
    fun clearPlanSectionsSearchState() {
        planSectionsSearchJob?.cancel()
        _uiState.update {
            it.copy(
                planSectionsQuery = "",
                planSectionsStructuredQuery = null,
                planSectionsTagFilter = emptySet(),
                planSectionsPredicate = null,
                planSectionsCollectionResults = emptyList(),
                planSectionsScryfallResults = emptyList(),
                isSearchingPlanSectionsScryfall = false,
            )
        }
    }

    /** PLAN_SECTIONS is fully skippable -- no gate, ever. Shared by every anchor now that the
     * legacy Casual `MANUAL_ADDS` step (with its own separate "Next") is deleted. */
    fun onNextFromPlanSections() {
        logStep("review")
        _uiState.update { it.copy(phase = WizardPhase.REVIEW) }
    }

    // ── Review ─────────────────────────────────────────────────────────────────

    /** Commander-only (W5.2). Gates ONLY Stage A (owned non-basic lands) of the wizard's land fill;
     * basics (Stage B) always run regardless, per R12. */
    fun onToggleIncludeNonBasicLands() = _uiState.update { it.copy(includeNonBasicLands = !it.includeNonBasicLands) }

    // ── Seeds (SEED_PICK / PLAN_SECTIONS "Start from cards", S2/S3/S4) ────────────────────────────

    /**
     * Deck Wizard 60-card wave (v6), plan §5 Phase 5.2: adds one copy of [card] as a seed.
     * Commander: dedupe-by-name (basics exempt), identity + format-legality gate
     * ([isCommanderManualAddValid], D7/R5), quantity never above 1, total cap [DeckWizardUiState
     * .seedCap] (99). 60-card (every entry flow): legality via [isLegalForFormat] (Casual/Commander
     * Casual permissive), a color-identity check ONLY outside the CARDS flow (S19 -- CARDS flow
     * seeds DEFINE the identity instead), a per-card copy cap [CopyPolicy.maxSeedCopies] (toast
     * `deck_wizard_seed_copy_cap`), and the total copy cap [DeckWizardUiState.seedCap] (toast
     * `deck_wizard_seed_cap_reached`).
     */
    fun onAddSeed(card: Card) {
        val state = _uiState.value
        val format = state.selectedFormat ?: return

        if (format.isCommanderFormat) {
            if (!isCommanderManualAddValid(card, state)) {
                viewModelScope.launch {
                    _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_manual_add_rejected)))
                }
                return
            }
            val current = state.seeds
            val isDuplicateByName = !BasicLandCalculator.isBasicLand(card) &&
                current.any { it.card.name.equals(card.name, ignoreCase = false) }
            if (current.any { it.card.scryfallId == card.scryfallId } || isDuplicateByName || current.size >= state.seedCap) return
            _uiState.update { it.copy(seeds = current + WizardSeed(card, 1)) }
            recomputeSeedLockedColors()
            if (state.phase == WizardPhase.PLAN_SECTIONS) recomputePlanAnalysis()
            return
        }

        if (!isLegalForFormat(card, format)) {
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_manual_add_rejected)))
            }
            return
        }
        // S19: seeds are never color-rejected in the CARDS flow (they DEFINE the identity via
        // recomputeSeedLockedColors below) -- COLORS/STRATEGY flows already picked the identity, so
        // a seed outside it is a real rules violation there.
        if (state.entryFlow != WizardEntryFlow.CARDS) {
            val identitySymbols = state.engineIdentity.map { it.symbol }.toSet()
            if (!card.colorIdentity.all { it in identitySymbols }) {
                viewModelScope.launch {
                    _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_manual_add_rejected)))
                }
                return
            }
        }
        // Gate 5 audit (edge-case P1): a non-basic card is keyed by NAME here, not scryfallId --
        // two different printings of the same card must collapse into ONE seed entry (mirrors the
        // Commander branch's own isDuplicateByName rule above), or the per-name copy cap below is
        // trivially bypassable by adding a second printing of a card already at its cap. Basics are
        // exempt (same precedent as the Commander branch): CopyPolicy.maxSeedCopies is unlimited for
        // them anyway, so keying by scryfallId there is harmless and preserves any existing
        // multi-printing-basics behavior.
        val isBasic = BasicLandCalculator.isBasicLand(card)
        val existing = if (isBasic) {
            state.seeds.firstOrNull { it.card.scryfallId == card.scryfallId }
        } else {
            state.seeds.firstOrNull { it.card.name.equals(card.name, ignoreCase = false) }
        }
        val existingCopiesByName = if (isBasic) {
            existing?.quantity ?: 0
        } else {
            state.seeds.filter { it.card.name.equals(card.name, ignoreCase = false) }.sumOf { it.quantity }
        }
        val maxCopies = CopyPolicy.maxSeedCopies(card, format)
        if (existingCopiesByName >= maxCopies) {
            crashReporter.log("deck_wizard_seed_copy_cap_reached")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_seed_copy_cap, maxCopies)))
            }
            return
        }
        if (state.seedCopies >= state.seedCap) {
            crashReporter.log("deck_wizard_seed_cap_reached")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_seed_cap_reached, state.seedCap)))
            }
            return
        }
        val updatedSeeds = if (existing != null) {
            // Increments the EXISTING entry's own printing (e.g. printing A already seeded), never
            // the newly-tapped [card] (printing B) -- a second WizardSeed for the same name must
            // never be created.
            state.seeds.map { seed -> if (seed.card.scryfallId == existing.card.scryfallId) seed.copy(quantity = seed.quantity + 1) else seed }
        } else {
            state.seeds + WizardSeed(card, 1)
        }
        _uiState.update { it.copy(seeds = updatedSeeds) }
        if (state.entryFlow == WizardEntryFlow.CARDS) recomputeSeedLockedColors()
        if (state.phase == WizardPhase.PLAN_SECTIONS) recomputePlanAnalysis()
    }

    /** Color identity ⊆ [DeckWizardUiState.colorIdentity] + the ONE legality predicate
     * ([isLegalForFormat]) the build engine, Browse and the analysis all apply. */
    private fun isCommanderManualAddValid(card: Card, state: DeckWizardUiState): Boolean {
        val identitySymbols = state.colorIdentity.map { it.symbol }.toSet()
        if (!card.colorIdentity.all { it in identitySymbols }) return false
        return isLegalForFormat(card, state.selectedFormat ?: DeckFormat.COMMANDER)
    }

    /** Removes ONE copy of [card] (decrements; removes the entry entirely at 0). Used by the seed
     * queue sheet's `-` stepper and [com.mmg.manahub.feature.decks.presentation.components
     * .CardDetailSheet]'s seed-selection mode. */
    fun onRemoveSeedCopy(card: Card) {
        val state = _uiState.value
        val existing = state.seeds.firstOrNull { it.card.scryfallId == card.scryfallId } ?: return
        val updated = if (existing.quantity <= 1) {
            state.seeds.filterNot { it.card.scryfallId == card.scryfallId }
        } else {
            state.seeds.map { seed -> if (seed.card.scryfallId == card.scryfallId) seed.copy(quantity = seed.quantity - 1) else seed }
        }
        _uiState.update { it.copy(seeds = updated) }
        if (state.selectedFormat?.isCommanderFormat == true || state.entryFlow == WizardEntryFlow.CARDS) recomputeSeedLockedColors()
        if (state.phase == WizardPhase.PLAN_SECTIONS) recomputePlanAnalysis()
    }

    /** Removes EVERY copy of [card] (the seed queue sheet's trash icon, and PLAN_SECTIONS' own
     * "Your cards" row remove for Commander, whose seeds are always quantity 1 so this is
     * equivalent to [onRemoveSeedCopy] there). */
    fun onRemoveSeed(card: Card) {
        val state = _uiState.value
        val remaining = state.seeds.filterNot { it.card.scryfallId == card.scryfallId }
        _uiState.update { it.copy(seeds = remaining) }
        if (state.selectedFormat?.isCommanderFormat == true || state.entryFlow == WizardEntryFlow.CARDS) recomputeSeedLockedColors()
        if (state.phase == WizardPhase.PLAN_SECTIONS) recomputePlanAnalysis()
    }

    /** Recomputes [DeckWizardUiState.lockedColors] from scratch as the union of the CURRENT seeds'
     * identities. In the 60-card CARDS flow the seeds are the ONLY identity source, so
     * [DeckWizardUiState.colorIdentity] is REPLACED (removing a seed shrinks it); a Commander build's
     * identity is the commander's, period -- seed colours are never unioned into it. */
    private fun recomputeSeedLockedColors() {
        _uiState.update { state ->
            val locked = state.seeds.flatMap { it.card.colorIdentity }.toManaColorSet()
            val identity = when {
                state.selectedFormat?.isCommanderFormat == true -> state.selectedCommander?.colorIdentity?.toManaColorSet() ?: state.colorIdentity
                state.entryFlow == WizardEntryFlow.CARDS -> locked
                else -> state.colorIdentity
            }
            state.copy(lockedColors = locked, colorIdentity = identity)
        }
    }

    /**
     * Deck Engine Unification plan D7 (4.3) — resolves a combo's component card NAMES (from the
     * synergy browser's "Use as seed" hand-off) into actual [Card]s and adds them via [onAddSeed]
     * (60-card wave v6, plan §5 Phase 5.1: rewritten to reuse the ONE seed-add validation/dedupe/cap
     * path rather than re-implementing it) -- a combo hand-off can never behave differently from a
     * manual pick this way.
     *
     * Resolution order per name: (1) [collectionSnapshot] first (owned, no network -- most combo
     * pieces the user already has), (2) a [searchCardsUseCase] lookup for names not owned. Best-
     * effort: a name that resolves to nothing is silently skipped rather than blocking the rest.
     */
    private suspend fun resolveComboSeeds(names: List<String>) {
        val ownedByLowerName = collectionSnapshot.associateBy { it.card.name.lowercase() }
        for (name in names) {
            if (_uiState.value.seedCopies >= _uiState.value.seedCap) break
            val owned = ownedByLowerName[name.lowercase()]?.card
            val resolved = owned ?: runCatching {
                when (val res = searchCardsUseCase(name)) {
                    is DataResult.Success -> res.data.cards.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    is DataResult.Error -> null
                }
            }.getOrElse { t ->
                if (t is CancellationException) throw t
                null
            }
            if (resolved != null) onAddSeed(resolved)
        }
    }

    // ── Navigation between phases ────────────────────────────────────────────

    /**
     * Deck Wizard 60-card wave (v6), plan §5 Phase 5.1 -- ENTRY's flow picker. Resets the shared
     * seed/color/strategy scratch state ([resetDirectionScratchState]) and lands on the flow's own
     * first step ([WizardPhase.SEED_PICK]/`.COLOR_PICK`/`.STRATEGY_PICK`). Re-tapping the flow the
     * user is ALREADY in is a no-op-ish transition (never wipes state on the screen they haven't
     * left) -- same precedent as the pre-v6 handler.
     */
    fun onSelectEntryFlow(flow: WizardEntryFlow) {
        logStep("entry_${flow.name.lowercase()}")
        val targetPhase = when (flow) {
            WizardEntryFlow.CARDS -> WizardPhase.SEED_PICK
            WizardEntryFlow.COLORS -> WizardPhase.COLOR_PICK
            WizardEntryFlow.STRATEGY -> WizardPhase.STRATEGY_PICK
        }
        if (_uiState.value.entryFlow == flow) {
            _uiState.update { it.copy(entryFlow = flow, phase = targetPhase) }
            return
        }
        cancelDirectionSearchJobs()
        _uiState.update { it.resetDirectionScratchState().copy(entryFlow = flow, phase = targetPhase) }
        // Deck Wizard 60-card wave (v6), plan §5 Phase 5.3: STRATEGY_PICK ranks the WHOLE catalog
        // by owned support ONCE on entry (an empty identity/seeds anchor) -- unlike COLOR_PICK
        // (nothing to rank before a color is picked) or SEED_PICK (ranks on its own "Next").
        if (flow == WizardEntryFlow.STRATEGY) recomputeStrategyRecommendations()
    }

    /** Back navigation between the wizard's steps (Deck Wizard 60-card wave v6, plan §5 Phase 5.1).
     * Returns `true` when the caller should pop the whole wizard screen instead. */
    fun onBackPressed(): Boolean {
        val state = _uiState.value
        return when (state.phase) {
            WizardPhase.ENTRY -> true
            WizardPhase.COMMANDER_PICK -> true
            WizardPhase.SEED_PICK, WizardPhase.COLOR_PICK, WizardPhase.STRATEGY_PICK -> {
                _uiState.update { it.copy(phase = WizardPhase.ENTRY) }
                false
            }
            WizardPhase.STRATEGY -> {
                val target = if (state.selectedFormat?.isCommanderFormat == true) WizardPhase.COMMANDER_PICK else WizardPhase.SEED_PICK
                // The step's Next re-enters via onNextFromSeedPick/onNextFromCommanderPick, which
                // recompute from scratch -- nothing stale may survive to render before that lands.
                commanderStrategyJob?.cancel()
                _uiState.update {
                    it.copy(phase = target, strategyRecommendations = emptyList(), isLoadingCommanderStrategies = false).clearStalePick()
                }
                false
            }
            WizardPhase.PLAN_SECTIONS -> {
                val target = when {
                    state.selectedFormat?.isCommanderFormat == true -> WizardPhase.STRATEGY
                    state.entryFlow == WizardEntryFlow.CARDS -> WizardPhase.STRATEGY
                    state.entryFlow == WizardEntryFlow.COLORS -> WizardPhase.COLOR_PICK
                    else -> WizardPhase.STRATEGY_PICK
                }
                planAnalysisJob?.cancel()
                clearPlanSectionsSearchState()
                // STRATEGY_PICK's identity exists only as part of a committed combo pick, which
                // recomputeStrategyRecommendations below drops.
                val identity = if (target == WizardPhase.STRATEGY_PICK) emptySet() else state.colorIdentity
                _uiState.update { it.copy(phase = target, colorIdentity = identity).clearPlanAnalysis(isAnalyzing = false) }
                if (target == WizardPhase.STRATEGY_PICK) pruneSeedsOutsideIdentity(emptySet())
                // The target step has no Next-driven re-entry here, so it re-ranks itself now
                // (synchronously loading) instead of showing the previous list/pick as still valid.
                recomputeStrategyRecommendations()
                false
            }
            WizardPhase.REVIEW -> { _uiState.update { it.copy(phase = WizardPhase.PLAN_SECTIONS) }; false }
            WizardPhase.GENERATING -> { onCancelGeneration(); false }
            // W7 Task B (R10): abandoning Choice writes nothing -- see onAbandonChoice's own KDoc.
            WizardPhase.CHOICE -> { onAbandonChoice(); false }
        }
    }

    // ── Generation ────────────────────────────────────────────────────────────

    fun onGenerate() {
        val state = _uiState.value
        // Re-entrancy guard: the state write to GENERATING below is synchronous, so a second
        // invocation from a double-tap (before Compose recomposes the Review CTA away) reads the
        // already-updated phase here and returns instead of launching a second build / clobbering
        // `generateJob`.
        if (state.phase != WizardPhase.REVIEW || state.selectedFormat == null) return
        logStep("generating")
        val format = state.selectedFormat
        isWritingCommanderDeck = false

        _uiState.update {
            it.copy(
                phase = WizardPhase.GENERATING,
                commanderBuildStage = null,
                commanderCompletedStages = emptyList(),
                buildError = null,
                isBuildErrorRetryable = true,
            )
        }
        // Deck Wizard 60-card wave (v6), plan §5 Phase 5.4: ONE build path for every format now --
        // generateCasualDeck/the deleted Motor A wizard build use case are gone; the Sixty-anchor branch that
        // used to fork here is what generateWizardDeck's own anchor resolution replaces.
        generateJob = viewModelScope.launch { generateWizardDeck(state, format) }
    }

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.4: the ONE build path for every format
     * (generalizes the pre-v6 Commander-only `generateCommanderDeck`) -- runs
     * [buildWizardDeckUseCase]'s placement loop only; a build with at least one ambiguity group OR
     * a fallback placement hands the in-memory draft to [WizardPhase.CHOICE] instead of finalizing
     * immediately (R10/v5 D4), a clean build calls [finalizeWizardDraft] straight away. The anchor
     * is [BuildAnchor.Commander] for a Commander-shaped [format] (the ONLY case with a non-null
     * [DeckWizardUiState.selectedCommander]) or [BuildAnchor.Sixty] otherwise (identity = the
     * picked colors, `{}` for a colorless build; seeds = every current [DeckWizardUiState.seeds]
     * card).
     */
    private suspend fun generateWizardDeck(state: DeckWizardUiState, format: DeckFormat) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.log("deck_wizard_generate_started")
        crashlytics.setCustomKey("deck_wizard_format", format.name)
        crashlytics.setCustomKey("deck_wizard_seed_count", state.seeds.size)
        crashlytics.setCustomKey("deck_wizard_entry_flow", state.entryFlow.name)
        crashlytics.setCustomKey("deck_wizard_land_mode", if (state.includeNonBasicLands) "non_basic_included" else "basics_only")
        commanderGenerationStartAtMs = System.currentTimeMillis()
        manuallyToggledChoiceRoles.clear()
        autoFilledChoiceRoles.clear()

        val commander = state.selectedCommander
        if (format.isCommanderFormat && commander == null) {
            crashlytics.log("deck_wizard_generate_failed_no_commander")
            crashReporter.recordException(IllegalStateException("[DeckWizardViewModel] generateWizardDeck reached REVIEW with no selectedCommander"))
            _uiState.update { it.copy(buildError = appContext.getString(R.string.deck_wizard_build_error)) }
            return
        }
        val identity = commander?.colorIdentity?.toManaColorSet() ?: state.engineIdentity
        val anchor: BuildAnchor = if (commander != null) {
            BuildAnchor.Commander(commander)
        } else {
            BuildAnchor.Sixty(identity, state.seeds.map { it.card })
        }
        val strategyPick = resolveStrategyPick(state)

        var ownedCollection = collectionSnapshot
            .groupBy { it.card.scryfallId }
            .map { (_, entries) -> OwnedCard(entries.first().card, entries.sumOf { entry -> entry.userCard.quantity }) }
        ownedCollection = guaranteeBasicsAvailable(ownedCollection, identity)
        val manualAdds = resolveManualAdds(state)

        val draft = runCatching {
            buildWizardDeckUseCase.buildWithGroups(
                format = format,
                anchor = anchor,
                strategyPick = strategyPick,
                ownedCollection = ownedCollection,
                manualAdds = manualAdds,
                includeNonBasicLands = state.includeNonBasicLands,
                onStage = { stage ->
                    _uiState.update { s ->
                        s.copy(
                            commanderCompletedStages = s.commanderBuildStage?.let { s.commanderCompletedStages + it } ?: s.commanderCompletedStages,
                            commanderBuildStage = stage,
                        )
                    }
                },
                deckId = launchedFromDeckId.orEmpty(), // seeds placement tie-breaks (E4)
                preferenceStore = wizardPreferenceStore,
            )
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            logFailure("deck_wizard_generate_crashed", t)
            _uiState.update { it.copy(buildError = appContext.getString(R.string.deck_wizard_build_error)) }
            return
        }

        // Engine-side defence in depth for identity pruning: whatever it dropped is reported, never silent.
        val droppedOffIdentity = draft.droppedOffIdentityIds
        if (droppedOffIdentity.isNotEmpty()) {
            reportSeedsRemovedOutsideIdentity(manualAdds.filter { it.card.scryfallId in droppedOffIdentity }.sumOf { it.quantity })
        }

        val hasFallback = draft.fallbackStandaloneIds.isNotEmpty() || draft.fallbackOffPlanIds.isNotEmpty()
        if (draft.ambiguityGroups.isEmpty() && !hasFallback) {
            pendingFinalize = PendingFinalize(state, format, commander, strategyPick, manualAdds, draft, resolutions = emptyMap())
            finalizeWizardDraft(state, format, commander, strategyPick, manualAdds, draft, resolutions = emptyMap())
        } else {
            crashlytics.log("deck_wizard_choice_shown")
            crashlytics.setCustomKey("deck_wizard_choice_group_count_bucket", countBucket(draft.ambiguityGroups.size))
            crashlytics.setCustomKey(
                "deck_wizard_choice_shown_reason",
                if (draft.ambiguityGroups.isNotEmpty()) "ambiguity" else "fallback_only",
            )
            _uiState.update {
                it.copy(
                    phase = WizardPhase.CHOICE,
                    commanderDraftBuild = draft,
                    choiceSelections = emptyMap(),
                )
            }
        }
    }

    /** D4/D6: re-resolves the [StrategyPick] the build engine wants from the STRATEGY step's
     * persisted pin fields -- shared by [generateWizardDeck] and [onFinishChoices] so both read
     * the SAME strategy a Choice-screen build was actually run against. */
    private fun resolveStrategyPick(state: DeckWizardUiState): StrategyPick =
        state.selectedCuratedStrategyId
            ?.let { id -> CuratedStrategyCatalog.byId(id) }
            ?.let { strategy -> StrategyPick.Curated(strategy, state.selectedTribeKey) }
            ?: StrategyPick.Custom

    /** Shared by [generateWizardDeck] and [onFinishChoices]. */
    private fun resolveManualAdds(state: DeckWizardUiState): List<ManualAdd> =
        state.seeds.map { seed ->
            ManualAdd(card = seed.card, isOwned = cardSnapshot.any { it.scryfallId == seed.card.scryfallId }, quantity = seed.quantity)
        }

    /**
     * W7 Task B (E11); generalized to every anchor by Deck Wizard 60-card wave (v6, plan §5 Phase
     * 5.4) — completes and persists a [WizardDraftBuild] exactly ONCE: applies [resolutions], runs
     * land fill/verify/refine, then writes atomically into [launchedFromDeckId] when the wizard was
     * launched from an existing draft, or a freshly created deck otherwise (D12).
     *
     * [commander] is `null` for every 60-card anchor (S14/R13): 60-card NEVER writes
     * `commanderCardId`/`coverCardId` and NEVER creates a fresh deck -- [launchedFromDeckId] is
     * ALWAYS present for a 60-card build (Studio's own "Build from seed"/"Rebuild with the Wizard"
     * both always pass a real `deckId`, S15) — the `?: run { createDeck() }` branch below stays
     * reachable ONLY for Commander, exactly as before this generalization.
     */
    private suspend fun finalizeWizardDraft(
        state: DeckWizardUiState,
        format: DeckFormat,
        commander: Card?,
        strategyPick: StrategyPick,
        manualAdds: List<ManualAdd>,
        draft: WizardDraftBuild,
        resolutions: Map<RoleKey, List<String>>,
    ) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        val outcome = runCatching {
            buildWizardDeckUseCase.finalize(
                draft = draft,
                resolutions = resolutions,
                fillLands = true,
                onStage = { stage ->
                    _uiState.update { s ->
                        s.copy(
                            commanderCompletedStages = s.commanderBuildStage?.let { s.commanderCompletedStages + it } ?: s.commanderCompletedStages,
                            commanderBuildStage = stage,
                        )
                    }
                },
            )
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            logFailure("deck_wizard_generate_crashed", t)
            _uiState.update { it.copy(buildError = appContext.getString(R.string.deck_wizard_build_error)) }
            return
        }

        val manualIds = manualAdds.map { it.card.scryfallId }.toSet()

        if (launchedFromDeckId != null && !replaceConfirmed) {
            val existing = deckRepository.observeDeckWithCards(launchedFromDeckId!!).first()
            // persistWizardBuild clears BOTH boards; a commander equal to the one being written is not a loss.
            val hasCardsToLose = existing != null &&
                (
                    existing.mainboard.sumOf { it.quantity } > 0 ||
                        existing.sideboard.sumOf { it.quantity } > 0 ||
                        (existing.deck.commanderCardId != null && existing.deck.commanderCardId != commander?.scryfallId)
                    )
            if (hasCardsToLose) {
                crashReporter.log("deck_wizard_write_refused_unconfirmed")
                commanderGenerationStartAtMs = null
                // Retry would hit this same guard: the attempt is dropped and the error renders with Back only.
                pendingFinalize = null
                _uiState.update {
                    it.copy(buildError = appContext.getString(R.string.deck_wizard_replace_not_confirmed), isBuildErrorRetryable = false)
                }
                _events.send(
                    DeckWizardEvent.ShowToast(
                        appContext.getString(R.string.deck_wizard_replace_not_confirmed),
                        MagicToastType.ERROR,
                    )
                )
                return
            }
        }

        isWritingCommanderDeck = true
        val writeOutcome = runCatching {
            val deckId = if (format.isCommanderFormat) {
                launchedFromDeckId ?: run {
                    val newId = deckRepository.createDeck(
                        name = commander?.name ?: format.displayName,
                        description = "Draft",
                        format = format.name,
                        source = DeckCreationSource.WIZARD,
                    )
                    pendingDeckId = newId
                    newId
                }
            } else {
                // R13/S14: a 60-card build is ALWAYS launched from an existing Studio draft -- never
                // creates one of its own.
                checkNotNull(launchedFromDeckId) { "[DeckWizardViewModel] 60-card generation reached finalize with no launchedFromDeckId" }
            }
            // A still-empty Studio draft the wizard fills is a wizard deck for progression.
            deckRepository.tagDeckCreationSource(deckId, DeckCreationSource.WIZARD)
            // commanderCardId/coverCardId travel inside persist's single transaction, never a prior updateDeck.
            val anchor: BuildAnchor = if (commander != null) BuildAnchor.Commander(commander) else BuildAnchor.Sixty(state.engineIdentity, state.seeds.map { it.card })
            buildWizardDeckUseCase.persist(deckRepository, deckId, anchor, manualIds, outcome)
            deckId
        }.getOrElse { t ->
            isWritingCommanderDeck = false
            if (t is CancellationException) throw t
            logFailure("deck_wizard_write_failed", t)
            _uiState.update { it.copy(buildError = appContext.getString(R.string.deck_wizard_build_error)) }
            return
        }
        isWritingCommanderDeck = false
        pendingFinalize = null // W7 Fix 2: only clear the resumable attempt on real success.

        val genuineAlternatives = resolutions.flatMap { (role, ids) ->
            val tentative = draft.tentativeByRole[role].orEmpty()
            ids.filterNot { it in tentative }
        }
        if (genuineAlternatives.isNotEmpty()) {
            viewModelScope.launch { genuineAlternatives.forEach { wizardPreferenceStore.recordPick(it) } }
        }

        crashlytics.log("deck_wizard_generate_succeeded")
        // Deck Wizard 60-card wave (v6), plan §7: value renamed -- this key now logs for every
        // format, not just Commander.
        crashlytics.setCustomKey("deck_wizard_template_source", "WIZARD_V3_ENGINE")
        crashlytics.setCustomKey(
            "deck_wizard_choice_resolution_mode",
            classifyChoiceResolutionMode(draft, resolutions),
        )
        crashlytics.setCustomKey(
            "deck_wizard_preference_bonus_applied_count_bucket",
            countBucket(outcome.result.fillStats.preferenceBonusAppliedCount),
        )
        commanderGenerationStartAtMs?.let { startedAt ->
            crashlytics.setCustomKey("deck_wizard_generate_duration_ms_bucket", durationBucket(System.currentTimeMillis() - startedAt))
        }
        commanderGenerationStartAtMs = null
        logWizardBuildTelemetry(state, strategyPick, draft.ownedCollection.size, outcome.result, draft)
        _uiState.update {
            it.copy(
                createdDeckId = writeOutcome,
                commanderDraftBuild = null,
                choiceSelections = emptyMap(),
            )
        }
        _events.send(DeckWizardEvent.OpenDeckStudio(writeOutcome))
    }

    // ── Choice (W7 Task B, R10; quantity-aware since Deck Wizard 60-card wave v6, plan §5 Phase 5.4, S6) ──

    /**
     * Changes [cardId]'s selected COPY count within [role] by [delta] (a UI row calls this once per
     * tap -- Commander's boolean row derives the sign from its own current selection, a 60-card
     * row's +/- stepper passes it directly, see `DeckWizardChoiceStep.kt`'s own row wiring).
     * Adding is blocked (existing cap toast) when the section's total selected copies would reach
     * [AmbiguityGroup.remainingSlots], OR [cardId]'s own remaining headroom
     * ([WizardDraftBuild.candidateMaxCopies] plus however many of its OWN tentative copies it may
     * always keep, per that field's own KDoc) is exhausted. Removing decrements; the id drops out
     * of the map entirely at 0 (never a stray zero entry -- keeps `Map.values.sum()` the single
     * source of truth for "copies selected so far").
     */
    fun onChangeChoiceQuantity(role: RoleKey, cardId: String, delta: Int) {
        val state = _uiState.value
        val draft = state.commanderDraftBuild ?: return
        val group = draft.ambiguityGroups.firstOrNull { it.sectionId == role } ?: return
        val tentative = draft.tentativeCopies(role)
        val current = state.choiceSelections[role] ?: tentative
        val currentForId = current[cardId] ?: 0
        when {
            delta > 0 -> {
                val sectionFull = current.values.sum() >= group.remainingSlots
                val copyHeadroom = draft.choiceCopyHeadroom(state.choiceSelections, role, cardId)
                if (sectionFull || copyHeadroom <= 0) {
                    // Two different caps, two different messages: the section's slot budget vs this card's copies.
                    val message = if (sectionFull) {
                        appContext.getString(R.string.deck_wizard_choice_cap_reached, group.remainingSlots)
                    } else {
                        appContext.getString(R.string.deck_wizard_seed_copy_cap, draft.globalChoiceCap(cardId))
                    }
                    crashReporter.log(if (sectionFull) "deck_wizard_choice_section_cap_reached" else "deck_wizard_choice_copy_cap_reached")
                    viewModelScope.launch { _events.send(DeckWizardEvent.ShowToast(message, MagicToastType.INFO)) }
                    return
                }
                manuallyToggledChoiceRoles += role
                _uiState.update { it.copy(choiceSelections = it.choiceSelections + (role to (current + (cardId to currentForId + 1)))) }
            }
            delta < 0 -> {
                if (currentForId <= 0) return
                manuallyToggledChoiceRoles += role
                val updated = if (currentForId <= 1) current - cardId else current + (cardId to currentForId - 1)
                _uiState.update { it.copy(choiceSelections = it.choiceSelections + (role to updated)) }
            }
        }
    }

    /** "Choose the remaining N for me" (per-section) — fills whichever of [role]'s remaining COPIES
     * the user has not yet decided with the engine's own tentative defaults, in tentative order. */
    fun onAutoFillChoiceSection(role: RoleKey) {
        val state = _uiState.value
        val draft = state.commanderDraftBuild ?: return
        val group = draft.ambiguityGroups.firstOrNull { it.sectionId == role } ?: return
        val tentative = draft.tentativeCopies(role)
        val current = (state.choiceSelections[role] ?: tentative).toMutableMap()
        val startTotal = current.values.sum()
        var missing = group.remainingSlots - startTotal
        if (missing <= 0) return
        for (id in draft.tentativeByRole[role].orEmpty().distinct()) {
            if (missing <= 0) break
            val have = current[id] ?: 0
            // Headroom is read against the live map so copies added earlier in this loop count.
            val headroom = draft.choiceCopyHeadroom(state.choiceSelections + (role to current), role, id)
            val add = minOf(headroom, missing)
            if (add > 0) {
                current[id] = have + add
                missing -= add
            }
        }
        if (current.values.sum() == startTotal) return
        autoFilledChoiceRoles += role
        _uiState.update { it.copy(choiceSelections = it.choiceSelections + (role to current)) }
    }

    /** Global "Let the wizard finish" — resolves every section the user has already decided exactly
     * as chosen, and every untouched section with the engine's own tentative defaults, then persists
     * ONCE via [finalizeWizardDraft]. [commander] is `null` for a 60-card build (finalize's own
     * contract, see its KDoc). */
    fun onFinishChoices() {
        val state = _uiState.value
        if (state.phase != WizardPhase.CHOICE) return
        val draft = state.commanderDraftBuild ?: return
        val format = state.selectedFormat ?: return
        val commander = state.selectedCommander
        val strategyPick = resolveStrategyPick(state)
        val manualAdds = resolveManualAdds(state)
        // S6/Phase-1 finalize contract: the map expands into ONE id per selected copy (a repeated
        // id means multiple copies of that same card in the role's swappable slots).
        val resolutions: Map<RoleKey, List<String>> = state.choiceSelections.mapValues { (_, counts) ->
            counts.flatMap { (id, count) -> List(count) { id } }
        }
        _uiState.update {
            it.copy(
                phase = WizardPhase.GENERATING,
                commanderBuildStage = null,
                commanderCompletedStages = emptyList(),
                buildError = null,
                isBuildErrorRetryable = true,
            )
        }
        pendingFinalize = PendingFinalize(state, format, commander, strategyPick, manualAdds, draft, resolutions = resolutions)
        generateJob = viewModelScope.launch {
            finalizeWizardDraft(state, format, commander, strategyPick, manualAdds, draft, resolutions = resolutions)
        }
    }

    /** Abandoning the Choice screen (UI/system back) writes NOTHING — the in-memory draft is simply
     * dropped. */
    private fun onAbandonChoice() {
        FirebaseCrashlytics.getInstance().log("deck_wizard_choice_abandoned")
        _uiState.update {
            it.copy(
                phase = WizardPhase.REVIEW,
                commanderDraftBuild = null,
                choiceSelections = emptyMap(),
                commanderBuildStage = null,
                commanderCompletedStages = emptyList(),
            )
        }
    }

    /**
     * R12/E13: basic lands are an unconditional, unlimited resource the wizard may always place,
     * regardless of collection ownership. Synthesizes [OwnedCard] entries for whichever WUBRG basics
     * (or Wastes, for a colourless identity) [ownedCollection] has zero real [Card] object for.
     */
    private suspend fun guaranteeBasicsAvailable(ownedCollection: List<OwnedCard>, identity: Set<ManaColor>): List<OwnedCard> {
        val neededNames = if (identity.isEmpty()) {
            setOf("Wastes")
        } else {
            identity.mapNotNull { color -> BasicLandCalculator.LAND_FOR_COLOR[color.symbol] }.toSet()
        }
        val missingNames = neededNames.filterNot { name -> ownedCollection.any { it.card.name == name } }
        if (missingNames.isEmpty()) return ownedCollection
        val fetched = missingNames.mapNotNull { name ->
            runCatching { cardRepository.searchCardByName(name) }
                .getOrElse { t -> if (t is CancellationException) throw t else null }
                .let { it as? DataResult.Success }?.data
                ?.let { card -> OwnedCard(card, BASIC_LAND_SYNTHETIC_QUANTITY) }
        }
        return ownedCollection + fetched
    }

    /** Deck Wizard Commander v3 plan, Phase 7.3; generalized to every anchor by the 60-card wave
     * (v6, plan §7) -- self-evaluation telemetry for a completed wizard build. [draft] supplies the
     * fields that are per-BUILD, not per-final-result (anchor kind, resolved identity). */
    private fun logWizardBuildTelemetry(
        state: DeckWizardUiState,
        strategyPick: StrategyPick,
        poolSize: Int,
        result: WizardBuildResult,
        draft: WizardDraftBuild,
    ) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        val analysis = result.analysis
        crashlytics.setCustomKey("deck_wizard_self_score_bucket", scoreBucket(analysis.totalScore))
        val weakestPillar = analysis.pillars.filterNot { it.notApplicable }.minByOrNull { it.subscore }?.id ?: PillarId.PLAN_ROLES
        crashlytics.setCustomKey("deck_wizard_weakest_pillar", weakestPillar.name)
        crashlytics.setCustomKey("deck_wizard_gap_count_bucket", countBucket(result.gapSections.size))
        // Deck Wizard 60-card wave (v6), plan §7 -- new keys, every anchor.
        crashlytics.setCustomKey(
            "deck_wizard_anchor",
            if (draft.anchor is BuildAnchor.Commander) "commander" else state.entryFlow.name.lowercase(),
        )
        crashlytics.setCustomKey("deck_wizard_seed_copies_bucket", seedCopiesBucket(state.seedCopies))
        crashlytics.setCustomKey("deck_wizard_four_of_count_bucket", fourOfCountBucket(result.fillStats.fourOfCount))
        crashlytics.setCustomKey("deck_wizard_distinct_names_bucket", distinctNamesBucket(result.fillStats.distinctNames))
        crashlytics.setCustomKey("deck_wizard_colorless_build", draft.identity.isEmpty())

        val nonLand = result.entries.filterNot { BasicLandCalculator.isLand(it.card) }
        val wizardPlacedNonLand = nonLand.filterNot { it.card.scryfallId == state.selectedCommander?.scryfallId }
        val offplanIds = analysis.pillars.flatMap { it.sections }.filter { it.id == "offplan" }
            .flatMap { section -> section.contributions.map { it.scryfallId } }.toSet()
        val wizardCopies = wizardPlacedNonLand.sumOf { it.quantity }
        val offplanCopies = wizardPlacedNonLand.filter { it.card.scryfallId in offplanIds }.sumOf { it.quantity }
        val offplanShare = if (wizardCopies > 0) offplanCopies.toFloat() / wizardCopies else 0f
        crashlytics.setCustomKey("deck_wizard_offplan_share_bucket", offplanShareBucket(offplanShare))

        crashlytics.setCustomKey("deck_wizard_manual_adds_count", result.fillStats.placedManual)
        crashlytics.setCustomKey("deck_wizard_pool_size_bucket", poolSizeBucket(poolSize))

        val topRecommendationId = state.strategyRecommendations.firstOrNull()?.strategy?.id
        val strategySource = when (strategyPick) {
            StrategyPick.Custom -> "custom"
            is StrategyPick.Curated -> if (strategyPick.strategy.id == topRecommendationId) "recommended" else "other"
        }
        crashlytics.setCustomKey("deck_wizard_strategy_source", strategySource)
        crashlytics.setCustomKey("deck_wizard_refinement_swaps", result.refinementSwaps)

        val fallbackTier = when {
            result.fillStats.fallbackOffPlanCount > 0 -> "offplan"
            result.fillStats.fallbackStandaloneCount > 0 -> "standalone_only"
            else -> "none"
        }
        crashlytics.setCustomKey("deck_wizard_fallback_tier", fallbackTier)
    }

    /** W8 (telemetry) -- classifies HOW the Choice screen's ambiguity groups were resolved. */
    private fun classifyChoiceResolutionMode(draft: WizardDraftBuild, resolutions: Map<RoleKey, List<String>>): String {
        val groups = draft.ambiguityGroups
        if (groups.isEmpty()) return "wizard_finish_all"
        val perGroup = groups.map { group ->
            when {
                group.sectionId in manuallyToggledChoiceRoles -> "manual"
                group.sectionId in autoFilledChoiceRoles -> "autofill"
                group.sectionId !in resolutions -> "wizard"
                else -> "manual"
            }
        }
        val distinct = perGroup.toSet()
        return when {
            distinct == setOf("manual") -> "fully_manual"
            distinct == setOf("autofill") -> "per_section_autofill"
            distinct == setOf("wizard") -> "wizard_finish_all"
            else -> "mixed"
        }
    }

    private fun durationBucket(durationMs: Long): String = when {
        durationMs < 2_000L -> "<2s"
        durationMs < 5_000L -> "2-5s"
        durationMs < 10_000L -> "5-10s"
        durationMs < 30_000L -> "10-30s"
        else -> "30s+"
    }

    private fun scoreBucket(score: Int): String = when {
        score < 40 -> "0-39"
        score < 60 -> "40-59"
        score < 80 -> "60-79"
        else -> "80-100"
    }

    private fun countBucket(count: Int): String = when {
        count <= 0 -> "0"
        count <= 2 -> "1-2"
        count <= 5 -> "3-5"
        else -> "6+"
    }

    private fun offplanShareBucket(share: Float): String = when {
        share <= 0f -> "0%"
        share <= 0.10f -> "1-10%"
        share <= 0.25f -> "11-25%"
        share <= 0.50f -> "26-50%"
        else -> "51%+"
    }

    private fun poolSizeBucket(size: Int): String = when {
        size < 20 -> "<20"
        size < 50 -> "20-49"
        size < 100 -> "50-99"
        size < 250 -> "100-249"
        else -> "250+"
    }

    /** Deck Wizard 60-card wave (v6), plan §7 -- bucket edges given explicitly by the plan. */
    private fun seedCopiesBucket(count: Int): String = when {
        count <= 0 -> "0"
        count <= 4 -> "1-4"
        count <= 12 -> "5-12"
        count <= 24 -> "13-24"
        else -> "25+"
    }

    /** Deck Wizard 60-card wave (v6), plan §7 -- bucket edges given explicitly by the plan. */
    private fun fourOfCountBucket(count: Int): String = when {
        count <= 0 -> "0"
        count <= 2 -> "1-2"
        count <= 5 -> "3-5"
        else -> "6+"
    }

    /** Deck Wizard 60-card wave (v6), plan §7 -- the plan named this key without bucket edges (unlike
     * [seedCopiesBucket]/[fourOfCountBucket] above); these are a judgment call sized to a real deck's
     * distinct-name range (60-card ~15-60, Commander ~35-99), not a plan-specified table. */
    private fun distinctNamesBucket(count: Int): String = when {
        count < 20 -> "<20"
        count < 40 -> "20-39"
        count < 60 -> "40-59"
        count < 80 -> "60-79"
        else -> "80+"
    }

    /** Best-effort deletes a dangling partially-created deck (a Commander build that got as far as
     * [finalizeWizardDraft]'s own `createDeck()` call but failed/was cancelled before finishing --
     * 60-card never creates one, see that function's own KDoc). */
    private fun cleanupPendingDeck() {
        val orphanId = pendingDeckId ?: return
        pendingDeckId = null
        viewModelScope.launch {
            runCatching { deckRepository.deleteDeck(orphanId) }
                .onFailure { t ->
                    if (t is CancellationException) throw t
                    logFailure("deck_wizard_cancel_cleanup_failed", t)
                }
        }
    }

    /** Cancels an in-flight build. Best-effort deletes a partially-created deck so backing out of
     * generation never leaves an orphaned draft. */
    fun onCancelGeneration() {
        if (isWritingCommanderDeck) return
        generateJob?.cancel()
        cleanupPendingDeck()
        pendingFinalize = null // Cancel means start over -- Retry must not resume a cancelled attempt.
        _uiState.update {
            it.copy(
                phase = WizardPhase.REVIEW,
                commanderBuildStage = null,
                commanderCompletedStages = emptyList(),
                buildError = null,
                isBuildErrorRetryable = true,
                commanderDraftBuild = null,
                choiceSelections = emptyMap(),
            )
        }
    }

    /** W7 Fix 2: retries generation after a [DeckWizardUiState.buildError] without re-walking the
     * steps, resuming the SAME finalize/persist call when a [pendingFinalize] attempt exists. */
    fun onRetryGeneration() {
        cleanupPendingDeck()
        val retry = pendingFinalize
        if (retry != null) {
            FirebaseCrashlytics.getInstance().log("deck_wizard_finalize_retry_resumed")
            _uiState.update { it.copy(buildError = null, isBuildErrorRetryable = true, phase = WizardPhase.GENERATING) }
            generateJob = viewModelScope.launch {
                finalizeWizardDraft(retry.state, retry.format, retry.commander, retry.strategyPick, retry.manualAdds, retry.draft, retry.resolutions)
            }
            return
        }
        _uiState.update { it.copy(buildError = null, isBuildErrorRetryable = true, phase = WizardPhase.REVIEW) }
    }

    private fun logFailure(tag: String, t: Throwable) {
        crashReporter.log(tag)
        crashReporter.recordException(RuntimeException("[DeckWizard] $tag", t))
    }

    /** Breadcrumb for the step being ENTERED (not exited) -- tells us where users progress to, and
     * by omission, where they stop. */
    private fun logStep(step: String) {
        crashReporter.log("deck_wizard_step_$step")
    }

    private companion object {
        const val SEARCH_MIN_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 400L
        const val TRIBE_PICKER_CANDIDATE_LIMIT = 8
        const val PLAN_ANALYSIS_DEBOUNCE_MS = 300L
        /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.3: COLOR_PICK's own chip-toggle debounce
         * for [recomputeStrategyRecommendations] -- a burst of chip taps fires ONE recompute, not
         * one per tap. */
        const val COLOR_PICK_STRATEGY_DEBOUNCE_MS = 150L
        /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.3: the seeds' own `card_strategy_tags`
         * fetch inside [recomputeStrategyRecommendations] is capped to keep it cheap -- the first N
         * seeds by quantity desc, not every seed. */
        const val MAX_SEED_TAGS_FETCH = 10
        /** W0.2: a generous synthetic owned quantity for a fetched-not-owned basic land -- basics
         * are effectively unlimited, this only needs to exceed any realistic land-fill target. */
        const val BASIC_LAND_SYNTHETIC_QUANTITY = 40
    }
}

/**
 * Deck Wizard Commander v3 plan (Phase 5, 5.1) — a cheap, self-contained reproduction of "which
 * [CardSection]s would this owned candidate count toward", NOT a re-exposure of
 * [com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase]'s own private
 * candidate-pool machinery.
 *
 * [sections] is every [com.mmg.manahub.feature.decks.domain.engine.PillarResult.sections] for the
 * CURRENT [DeckAnalysis]. [ownedCards] is the full collection snapshot; [excludeIds] removes the
 * commander and every already-manually-added card. [format] legality is the ONE
 * [isLegalForFormat] predicate; [identity] gates containment the same way `onAddSeed` does.
 */
internal fun computeOwnedAvailabilityBySection(
    sections: List<CardSection>,
    ownedCards: List<Card>,
    excludeIds: Set<String>,
    identity: Set<ManaColor>,
    format: DeckFormat,
    // false for the CARDS flow, where seeds define the identity and no card is identity-rejected.
    enforceIdentity: Boolean = true,
    // Deck Wizard 60-card wave (v6), plan §5 Phase 5.4: owned COPIES per card name -- the count
    // this hint reports is now "how many copies of this card the build could actually place"
    // (CopyPolicy.maxPlaceable), not "1 per distinct owned card". `null`/absent name = ownership-
    // exempt (matches CopyPolicy.maxPlaceable's own `owned: Int?` contract).
    ownedQuantityByName: Map<String, Int> = emptyMap(),
): Map<String, Int> {
    val sectionIds = sections.map { it.id }.toSet()
    val identitySymbols = identity.map { it.symbol }.toSet()
    val candidates = ownedCards
        .asSequence()
        .filter { it.scryfallId !in excludeIds }
        .filter { card -> !enforceIdentity || card.colorIdentity.all { it in identitySymbols } }
        .filter { card -> isLegalForFormat(card, format) }
        .distinctBy { it.name }

    val counts = mutableMapOf<String, Int>()
    candidates.forEach { card ->
        val matched = mutableSetOf<String>()
        ArchetypeRoleClassifier.classify(card).forEach { (role, confidence) ->
            if (confidence > 0f) {
                val id = "role:$role"
                if (id in sectionIds) matched += id
            }
        }
        val mvId = PlacementScorer.mvBucketId(card)
        if (mvId in sectionIds) matched += mvId
        if (BasicLandCalculator.isLand(card)) {
            ManaColor.entries.forEach { color ->
                val id = "produces:${color.symbol}"
                if (id in sectionIds && card.producedMana.contains(color.symbol.first())) matched += id
            }
        }
        val tagKeys = (card.tags + card.userTags).map { it.key }.toSet() + TribeDeriver.tribeKeys(card)
        tagKeys.forEach { key ->
            if (key in sectionIds) matched += key
            val fingerprintId = "fingerprint:$key"
            if (fingerprintId in sectionIds) matched += fingerprintId
        }
        if (matched.isEmpty()) return@forEach
        val placeable = CopyPolicy.maxPlaceable(card, format, ownedQuantityByName[card.name])
            .let { if (it == Int.MAX_VALUE) 1 else it } // a basic land's own "unlimited" reads as one real owned copy for this hint
        matched.forEach { id -> counts[id] = (counts[id] ?: 0) + placeable }
    }
    return counts
}

private fun List<String>.toManaColorSet(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()

/** Deck Wizard 60-card wave (v6), plan §5 Phase 5.4 (S6): [role]'s tentative slots as id -> copy
 * count (a repeated id in [WizardDraftBuild.tentativeByRole] means multiple tentative copies of
 * that same card -- see that field's own KDoc). This is the Choice screen's "current selection"
 * baseline before the user touches a section -- shared by [DeckWizardViewModel] (the state
 * transition logic) and `DeckWizardChoiceStep.kt` (rendering, same package). */
internal fun WizardDraftBuild.tentativeCopies(role: RoleKey): Map<String, Int> =
    tentativeByRole[role].orEmpty().groupingBy { it }.eachCount()

/** [id]'s absolute copy cap across ALL sections: fresh headroom plus every tentative copy it
 * already holds anywhere (mirrors `BuildWizardDeckUseCase.finalize`'s own global gate). */
internal fun WizardDraftBuild.globalChoiceCap(id: String): Int =
    (candidateMaxCopies[id] ?: 0) + tentativeByRole.values.sumOf { ids -> ids.count { it == id } }

/** How many MORE copies of [id] the user may still add in [role] given [selections] (absent
 * section = its tentative defaults): the tighter of the section's own cap (`finalize`'s per-role
 * `candidateMaxCopies + tentative` contract) and the global cap net of copies selected in OTHER
 * sections. Shared by the VM gate and the Choice row's `+` affordance so they never disagree. */
internal fun WizardDraftBuild.choiceCopyHeadroom(selections: Map<RoleKey, Map<String, Int>>, role: RoleKey, id: String): Int {
    val tentative = tentativeCopies(role)
    val own = (selections[role] ?: tentative)[id] ?: 0
    val sectionCap = (candidateMaxCopies[id] ?: 0) + (tentative[id] ?: 0)
    val elsewhere = ambiguityGroups
        .filter { it.sectionId != role }
        .sumOf { group -> (selections[group.sectionId] ?: tentativeCopies(group.sectionId))[id] ?: 0 }
    return minOf(sectionCap - own, globalChoiceCap(id) - own - elsewhere).coerceAtLeast(0)
}
