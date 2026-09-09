package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-08

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
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
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyEntry
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.PlacementScorer
import com.mmg.manahub.feature.decks.domain.engine.PostureId
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.engine.toPin
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.CategorySuggestions
import com.mmg.manahub.feature.decks.domain.template.CollectionProfile
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.CollectionTribeSignal
import com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec
import com.mmg.manahub.feature.decks.domain.template.OwnedCard
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildProgress
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.template.TemplateCardSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.RankOwnedCardsForProfileUseCase
import com.mmg.manahub.feature.decks.domain.usecase.RecommendCommanderStrategiesUseCase
import com.mmg.manahub.feature.decks.domain.usecase.StrategyRecommendation
import com.mmg.manahub.feature.decks.domain.usecase.SeedStrategyCandidate
import com.mmg.manahub.feature.decks.domain.usecase.SeedStrategySuggestion
import com.mmg.manahub.feature.decks.domain.usecase.SuggestStrategiesForSeedsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The wizard's internal phases (plan §3.4; [ENTRY] added by the Deck Engine Unification plan §5
 * Phase 3.1) — ONE screen, ONE ViewModel, no per-step nav destination (mirrors the Playtest
 * mulligan/battle-phase-in-one-screen precedent).
 *
 * [COMMANDER_PICK]/[STRATEGY]/[MANUAL_ADDS] (Deck Wizard & Engine Rework plan, Workstream 2) are the
 * NEW Commander-only sequence: `FORMAT → COMMANDER_PICK → STRATEGY → MANUAL_ADDS → REVIEW`,
 * REPLACING Commander's old `FORMAT → DIRECTION → IDENTITY → REVIEW` path (Commander now never
 * visits [DIRECTION]/[IDENTITY] — those two phases, and [ENTRY], stay Casual-only, byte-identical to
 * before this workstream).
 */
enum class WizardPhase { FORMAT, ENTRY, COMMANDER_PICK, STRATEGY, MANUAL_ADDS, DIRECTION, IDENTITY, REVIEW, GENERATING, RESULT }

/**
 * Deck Engine Unification plan (§5 Phase 3.1) — the three ways a build can start. [CARDS] is the
 * ORIGINAL wizard flow (commander/seed picker, unchanged) and is the ONLY flow [DeckFormat.COMMANDER]
 * ever uses (the commander itself is the mandatory first seed — plan: "Commander = cards-flow variant
 * with a mandatory commander slot as seed #1"), so [WizardPhase.ENTRY] is skipped entirely for
 * Commander builds. [COLORS] and [STRATEGY] are Casual-only and both reuse [WizardPhase.DIRECTION] —
 * that phase's content composable dispatches on this field rather than adding two more phases.
 */
enum class WizardEntryFlow { CARDS, COLORS, STRATEGY }

/** Deck Wizard Commander v3 plan (Phase 3.2): the two result tabs COMMANDER_PICK shows once a
 * structured search ([DeckWizardUiState.commanderStructuredQuery]) is applied via the search bar's
 * Tune icon. Before any structured search, the step shows the local candidate grid instead and no
 * tabs render at all. */
enum class CommanderResultTab { COLLECTION, ALL_CARDS }

/** A rankable color-combination pick for Flow C (strategy-first) — the curated
 * [ColorStrategyAffinity.combosFor] weight blended with how strong the user's OWN collection already
 * is in those colors ([CollectionProfile.colorShares]), so a taxonomy pick favors combos the user can
 * actually build today over a purely abstract ranking. */
data class ColorComboSuggestion(val colors: Set<ManaColor>, val score: Float)

/** One-shot side effects, delivered via a buffered [Channel] (never a nullable [MutableStateFlow]
 * — see the project-wide "one-shot events" convention documented on every other feature VM). */
sealed interface DeckWizardEvent {
    data class ShowToast(val message: String) : DeckWizardEvent

    /** The Result screen's "Open in Deck Studio" CTA — the caller navigates + pops the wizard. */
    data class OpenDeckStudio(val deckId: String) : DeckWizardEvent
}

data class DeckWizardUiState(
    val phase: WizardPhase = WizardPhase.FORMAT,

    // ── Step 1 — Format ────────────────────────────────────────────────────────
    val selectedFormat: DeckFormat? = null,

    // ── Entry chooser (Deck Engine Unification plan §5 Phase 3.1) ────────────────
    /** Casual only — Commander always forces [WizardEntryFlow.CARDS] and skips [WizardPhase.ENTRY]
     * entirely (see that enum's KDoc), so this default is only ever OBSERVED for Casual. */
    val entryFlow: WizardEntryFlow = WizardEntryFlow.CARDS,

    // ── Step 2 — Direction ────────────────────────────────────────────────────
    val isLoadingProfile: Boolean = true,
    val collectionProfile: CollectionProfile? = null,
    /** Deck Wizard & Engine Rework plan, Workstream 2.3 -- the full owned-card snapshot (unlike
     * [collectionProfile], which only surfaces DERIVED top-N signals), exposed for MANUAL_ADDS'
     * search-over-the-collection surface. Populated once, alongside [collectionProfile], from the
     * SAME `init` collection load -- the VM's private `cardSnapshot` is otherwise never exposed to
     * the UI layer. */
    val ownedCards: List<Card> = emptyList(),
    val selectedCommander: Card? = null,
    val commanderQuery: String = "",
    val commanderSearchResults: List<Card> = emptyList(),
    val isSearchingCommander: Boolean = false,
    /** Flow A (cards-first, plan §5 3.2) — recomputed from [seedCards] (+ [selectedCommander]) on
     * every seed add/remove. `null` while no seed is picked yet (nothing to rank). */
    val seedStrategySuggestion: SeedStrategySuggestion? = null,
    /** Flow B (colors-first, plan §5 3.3) — [ColorStrategyAffinity.forColors] for the CURRENT
     * [colorIdentity] pick; empty until at least one color is toggled on. */
    val colorAffinityEntries: List<ColorStrategyEntry> = emptyList(),
    val selectedColorAffinityEntry: ColorStrategyEntry? = null,
    /** Flow C (strategy-first, plan §5 3.4) — the taxonomy browser's free-text filter and the color
     * combos ranked once an archetype/theme is picked. */
    val taxonomyQuery: String = "",
    val colorComboSuggestions: List<ColorComboSuggestion> = emptyList(),
    /** Flow B/C shared (plan §5 3.3/3.4) — owned cards ranked against the just-picked profile,
     * rendered as a check/uncheck list that writes into [seedCards] via the SAME add/remove handlers
     * Flow A's seed picker uses (RC5 — the user always controls which exact cards seed the build). */
    val suggestedSeedCards: List<Card> = emptyList(),
    /** Deck Wizard & Engine Rework plan, Workstream 3.1 — Flow A only. Colors derived from the union
     * of currently picked [seedCards]' own [Card.colorIdentity], recomputed from scratch on every
     * seed add/remove ([DeckWizardViewModel.recomputeSeedLockedColors]). The UI renders every color
     * in this set as pre-selected AND visually locked (cannot be deselected) inside [colorIdentity] —
     * removing the seed that contributed a color immediately unlocks it (it stays picked as an
     * ordinary, now-deselectable choice until the user removes it explicitly;
     * [DeckWizardViewModel.onToggleCardsFlowColor] is a no-op while a color is still locked). Always
     * a subset of [colorIdentity] by construction. Harmless (and unused by the UI) for Commander/Flow
     * B/C, whose own MANUAL_ADDS/suggested-seed adds always stay within an already-resolved
     * [colorIdentity] anyway. */
    val lockedColors: Set<ManaColor> = emptySet(),
    /** Deck Engine Unification plan (D2): the Direction step's picked [ArchetypeId], resolved from
     * a tapped collection-lean [CardTag] chip via [DeckIdentitySeedTags.archetypeForTag]. Mutually
     * exclusive with [selectedDirectionTheme] and the tribe pick -- selecting one clears the
     * others, since all three represent the SAME "one Direction pick" slot in
     * [com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec.strategyProfile]. */
    val selectedArchetype: ArchetypeId? = null,
    /** The Direction step's picked [ThemeId] -- resolved from a tapped chip whose [CardTag] has no
     * [ArchetypeId] equivalent but does resolve to a theme via [DeckIdentitySeedTags.themeForTag]
     * (e.g. `plus_counters` -> PLUS1_COUNTERS, `spellslinger` -> SPELLSLINGER). Same "one Direction
     * pick" slot as [selectedArchetype]. */
    val selectedDirectionTheme: ThemeId? = null,
    /** Edge-case audit (Wizard Quality Campaign final gate, 2026-07-19): the raw
     * [com.mmg.manahub.feature.decks.domain.template.CollectionTribeSignal.tribeKey] (e.g.
     * `"tribe:elf"`) of the picked tribe Direction chip -- threaded into
     * [StrategyProfile.tribe]. [selectedTribeLabel] stays the pluralized DISPLAY string (e.g.
     * "Elves") for the Review-screen echo; this is the stable machine key the build actually
     * consumes. Same "one Direction pick" slot as [selectedArchetype]/[selectedDirectionTheme] --
     * selecting a tribe clears both of those, and vice versa. */
    val selectedTribeKey: String? = null,
    val selectedTribeLabel: String? = null,
    val showSeedPicker: Boolean = false,
    val seedCards: List<Card> = emptyList(),
    val seedQuery: String = "",
    val seedSearchResults: List<Card> = emptyList(),
    val isSearchingSeeds: Boolean = false,

    // ── Commander flow (Deck Wizard & Engine Rework plan, Workstream 2) ──────────
    /** COMMANDER_PICK step (2.1) -- color-identity filter row (D-G local-collection candidates OR,
     * behind [includeOutsideCollection], a Scryfall `is:commander` search). Reuses
     * [selectedCommander]/[commanderQuery]/[commanderSearchResults]/[isSearchingCommander] above --
     * this is the SAME one-commander pick, now reached via its own dedicated step instead of being
     * embedded inside the old Flow-A Direction step (which Commander no longer visits). */
    /** COMMANDER_PICK's OWN color-identity filter row was DELETED (Deck Wizard Commander v3 plan,
     * Phase 3.2, D7/R2) -- this field has no remaining UI producer ([onToggleCommanderColorFilter]
     * is unused dead code kept only because [onCommanderQueryChange]'s Casual-Direction-flow body
     * still reads it; see that function's KDoc). Always [emptySet] in practice from now on. */
    val commanderColorFilter: Set<ManaColor> = emptySet(),
    /** D-A -- ONE shared toggle for MANUAL_ADDS' card search (and, unchanged, the Casual Direction
     * flow's own embedded commander search): default collection-only, opt-in to a live Scryfall
     * search. Deck Wizard Commander v3 plan (Phase 3.2, D7/R2): COMMANDER_PICK's OWN outside-
     * collection toggle was DELETED — that step is now collection-only by default, with unowned
     * commanders reachable only through the structured search's All-cards tab
     * ([commanderStructuredQuery]/[commanderResultTab] below). This flag stays session-level (not
     * per-step) -- excluded from [resetDirectionScratchState]. */
    val includeOutsideCollection: Boolean = false,
    /** Deck Wizard Commander v3 plan (Phase 3.2/3.3/3.4): the structured query applied via
     * COMMANDER_PICK's search-bar Tune icon ([com.mmg.manahub.core.ui.components.search
     * .AdvancedSearchSheet], locked to [SearchCriterion.CommanderEligible] +, for a strict
     * Commander build, `Format(commander, legal)`). `null` while none is active — the step then
     * shows the local eligible-commander grid ([DeckWizardUiState.collectionProfile]
     * .commanderCandidates) instead of the two result tabs. */
    val commanderStructuredQuery: AdvancedSearchQuery? = null,
    /** Which of [commanderStructuredQuery]'s two result tabs is showing -- meaningless (and
     * ignored by the UI) while [commanderStructuredQuery] is null. */
    val commanderResultTab: CommanderResultTab = CommanderResultTab.COLLECTION,

    /** STRATEGY step (Deck Wizard Commander v3 plan, Phase 4) -- the ranked recommendation list
     * from [RecommendCommanderStrategiesUseCase], sorted best-first; the UI takes the first
     * [STRATEGY_RECOMMENDED_COUNT] as "Recommended" and the rest as collapsed "Other plans"
     * (Custom is a UI-level sentinel, always offered separately -- see
     * [DeckWizardCommanderSteps.CUSTOM_STRATEGY_ID]). Replaces the old `DeriveCommanderStrategiesUseCase`
     * candidate-union list + the 3-axis `StrategyPickerSheet` this step used to mount. */
    val commanderStrategyRecommendations: List<StrategyRecommendation> = emptyList(),
    val isLoadingCommanderStrategies: Boolean = false,
    /** The currently-selected catalog entry's id, or `null` for Custom (D6) -- drives the STRATEGY
     * step's single-select highlight. Distinct from [selectedArchetype]/[selectedStrategyThemes]/
     * [selectedTribeKey], which stay the ACTUAL pin fields the rest of the wizard (skeleton preview,
     * build) reads -- this id is a display-only pointer back into
     * [commanderStrategyRecommendations], since a (archetype, themes) pair alone cannot always be
     * mapped back to exactly one catalog id. */
    val selectedCuratedStrategyId: String? = null,
    /** The STRATEGY step's own tribe sub-picker (shown when the user taps a `requiresTribe` entry
     * with no tribe the recommender could derive on its own) -- the entry awaiting a tribe pick, or
     * `null` when the sub-picker is closed. */
    val pendingTribeStrategy: CuratedStrategy? = null,
    /** [pendingTribeStrategy]'s own candidate list -- the collection's dominant tribes within the
     * selected commander's identity (reuses [CollectionProfileUseCase.dominantTribes] over an
     * identity-filtered slice of [ownedCards], per the plan's "reuse whatever the app already
     * computes for dominant tribes in identity" instruction -- no new dominant-tribe computation). */
    val commanderTribePickerCandidates: List<CollectionTribeSignal> = emptyList(),
    /** The STRATEGY step's OWN theme pick(s) -- up to [com.mmg.manahub.feature.decks.domain.engine
     * .StrategyCatalog.MAX_THEMES]. Kept SEPARATE from [selectedDirectionTheme] (Casual's existing
     * single-theme Direction/Identity slot) deliberately: unifying them would force
     * [selectedDirectionTheme] to become a `List<ThemeId>` and ripple through every Casual
     * Direction/Identity call site this workstream must leave byte-identical (see
     * [DeckWizardViewModel.onGenerate]'s Commander branch for where this feeds the build spec).
     * [selectedArchetype]/[selectedTribeKey]/[selectedTribeLabel] ARE shared with Casual's existing
     * one-slot fields (both flows use the SAME "one Direction/Strategy pick" contract) -- only the
     * theme axis needed its own field, since it is the only axis where the two flows' cardinality
     * differs (Casual: 1 theme; Commander STRATEGY: up to 2). */
    val selectedStrategyThemes: List<ThemeId> = emptyList(),
    /** Deck Wizard Commander v3 plan (Phase 0 F3 gap, closed in Phase 5): [CuratedStrategy.toPin]'s
     * posture, previously computed by [selectCommanderStrategy] and immediately discarded -- there
     * was no field to store it in. Mirrors [selectedArchetype]/[selectedStrategyThemes]/
     * [selectedTribeKey]'s "one Strategy pick" contract; forwarded to
     * [com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline.analyze]'s
     * `postureOverride` so [planAnalysis] scores the SAME plan the engine will score at generation
     * time (once Phase 6 wires generation). */
    val selectedPosture: PostureId? = null,

    /** PLAN_SECTIONS step (Deck Wizard Commander v3 plan, Phase 5, R4 -- Commander only; Casual
     * still mounts [manualAddsSkeleton]/[MANUAL_ADDS]). The ONLY analysis engine call this step
     * ever makes -- attribution comes from here, never a wizard-side classifier (see the
     * campaign's own D2/§2 "no third vocabulary" rule). `null` while no build has been analyzed
     * yet (before the first recompute lands) or the pipeline degraded (see
     * [DeckWizardViewModel.recomputePlanAnalysis]'s KDoc). */
    val planAnalysis: DeckAnalysis? = null,
    /** True while a debounced [planAnalysis] recompute is in flight. Only meaningful together with
     * [planAnalysis] == null (the loading-vs-error distinction the UI needs) -- a `true` with a
     * non-null [planAnalysis] just means a newer recompute is already running, the UI keeps
     * showing the last-good result. */
    val isAnalyzingPlan: Boolean = false,
    /** [CardSection.id] -> count of owned, identity-legal, format-legal candidates (excluding the
     * commander and any card already in [seedCards], deduped by name) that the SAME classification
     * signal used to BUILD that section's own id would credit -- the "N in your collection" hint.
     * Absent key (not zero) for a section with no sensible availability signal (`offplan`/
     * `standalone`/`interaction`/`legal`/`illegal`) -- see
     * [DeckWizardViewModel.computeOwnedAvailabilityBySection]'s KDoc for exactly how each section id
     * shape is matched. This is a CHEAP REPRODUCTION of the same signals [ArchetypeRoleClassifier]/
     * [PlacementScorer]/[TribeDeriver] already expose -- never a re-exposure of
     * [com.mmg.manahub.feature.decks.domain.template.BuildCommanderDeckUseCase]'s own private
     * candidate-pool machinery (same precedent as Phase 4's `ownedRoleCounts`, see the progress
     * tracker's Run 6 log). */
    val ownedAvailabilityBySection: Map<String, Int> = emptyMap(),
    /** PLAN_SECTIONS' "Browse for &lt;Category&gt;" sheet -- mirrors [manualAddsQuery]/
     * [manualAddsSearchResults]'s shape but as its OWN state (a different tab/context from
     * [manualAddsQuery], which stays Casual/MANUAL_ADDS-only): the plain search-bar text. */
    val planSectionsQuery: String = "",
    /** The structured query currently APPLIED via the browse sheet's Advanced Search sheet (Tune
     * icon or a section's own "Browse for X" preset) -- `null` means nothing is filtering the two
     * result tabs beyond [planSectionsQuery]'s plain name filter. */
    val planSectionsStructuredQuery: AdvancedSearchQuery? = null,
    /** [com.mmg.manahub.core.model.CardTag] keys applied via [planSectionsStructuredQuery]'s
     * accompanying collection-tag preset (`SectionSearchQuery.collectionTagKeysFor`) -- a DIFFERENT
     * key space from the structured criteria above, ANDed together (mirrors
     * `DeckStudioUiState.activeCollectionTagFilter`'s own contract). */
    val planSectionsTagFilter: Set<String> = emptySet(),
    /** Collection tab results -- local, lenient [StructuredCardSearch.collectionMatches] over
     * [ownedCards], filtered by [planSectionsStructuredQuery] + [planSectionsTagFilter] +
     * [planSectionsQuery]'s plain name filter. */
    val planSectionsCollectionResults: List<Card> = emptyList(),
    /** All-cards tab results -- a real Scryfall search combining [planSectionsQuery] +
     * [planSectionsStructuredQuery]'s Scryfall fragment (mirrors
     * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.searchScryfallDirect]'s own
     * combination rule). */
    val planSectionsScryfallResults: List<Card> = emptyList(),
    val isSearchingPlanSectionsScryfall: Boolean = false,

    /** MANUAL_ADDS step (2.3, SHARED -- WS3 mounts this same composable/state shape for the Casual
     * flows; Commander formats now mount [DeckWizardCommanderSteps.PlanSectionsStepContent] on this
     * SAME [WizardPhase.MANUAL_ADDS] phase instead, see [DeckWizardScreen]'s phase dispatch --
     * [manualAddsSkeleton]/[manualAddsRoleFilter] below stay Casual-only from Phase 5 onward, still
     * computed for Commander too since [DeckWizardViewModel.onNextFromStrategy] is shared, but never
     * read by the Commander UI). Resolved once on entering the step, from the picked archetype/
     * themes + [colorIdentity]'s size ([DeckWizardViewModel.onNextFromStrategy]). */
    val manualAddsSkeleton: ResolvedArchetypeSkeleton? = null,
    /** Single-select role-key filter chip (tap toggles) -- narrows the search list to cards
     * [com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier.classify] scores > 0 for
     * this key. `null` = no filter. */
    val manualAddsRoleFilter: String? = null,
    val manualAddsQuery: String = "",
    val manualAddsSearchResults: List<Card> = emptyList(),
    val isSearchingManualAdds: Boolean = false,

    // ── Step 3 — Identity ─────────────────────────────────────────────────────
    val colorIdentity: Set<ManaColor> = emptySet(),
    val availableThemeTags: List<String> = emptyList(),
    val isLoadingThemeTags: Boolean = false,
    val selectedThemeHint: String? = null,

    // ── Step 4 — Review ────────────────────────────────────────────────────────
    val fillLands: Boolean = true,
    /** Deck Engine Unification plan (§5 Phase 3.5) — the shared source step. Motor B only ever
     * re-ranks OWNED cards by community popularity (never pulls in unowned cards), so this is a
     * plain on/off "also use community trends" toggle rather than a 3-way collection/community/both
     * selector (see [DeckWizardSpec.useCommunityData]'s KDoc). Hidden entirely in the UI when
     * [communityEngineAvailable] is false (the GLOBAL `communityEngineEnabledFlow` feature flag is
     * off) — the per-build toggle can never turn on a capability the global flag has disabled.
     */
    val communityEngineAvailable: Boolean = false,
    val useCommunityData: Boolean = false,

    // ── Generation ─────────────────────────────────────────────────────────────
    val buildStage: com.mmg.manahub.feature.decks.domain.template.BuildStage? = null,
    val completedStages: List<com.mmg.manahub.feature.decks.domain.template.BuildStage> = emptyList(),
    val buildError: String? = null,

    // ── Result ─────────────────────────────────────────────────────────────────
    val buildResult: TemplateBuildResult? = null,
    val createdDeckId: String? = null,
) {
    /** Casual + 3-or-more colors: a non-blocking hint, never a hard gate (D9). */
    val showColorDisciplineHint: Boolean
        get() = selectedFormat?.isSixtyCardConstructed == true && colorIdentity.count { it != ManaColor.C } > 2
}

/**
 * QA fix (Deck Engine Unification plan RUN 3b, edge-case audit 2026-07-20): both
 * [WizardPhase.FORMAT] and [WizardPhase.ENTRY] are reachable via normal back-navigation at ANY
 * point after the user has already populated state in a flow ([DeckWizardViewModel.onBackPressed]
 * routes DIRECTION→ENTRY/FORMAT and ENTRY→FORMAT) — so a user can pick a Direction (commander,
 * archetype, colors, seeds...), back out, and pick a DIFFERENT format or entry flow without that
 * state ever being cleared. Without this reset, stale scratch state from an ABANDONED flow rode
 * into the build: a stale [DeckWizardUiState.selectedArchetype] survived a Casual→Commander format
 * switch straight into [StrategyProfile] via `onGenerate`, and a stale [DeckWizardUiState
 * .selectedCommander] survived a Commander→Casual switch into [DeckWizardViewModel.wizardDeckName]
 * and (pre-fix) [BuildDeckFromTemplateUseCase]'s seed-tag inference.
 *
 * Every field here represents "one Direction/Identity/Review-step attempt" scratch state, NOT a
 * session-level preference — [DeckWizardUiState.useCommunityData]/[DeckWizardUiState.fillLands]
 * are deliberately excluded (legitimate across a format/flow switch). [DeckWizardUiState.entryFlow]
 * is also excluded: both call sites ([DeckWizardViewModel.onSelectFormat]/[DeckWizardViewModel
 * .onSelectEntryFlow]) set it themselves right after calling this, to the value that call is
 * actually selecting.
 */
private fun DeckWizardUiState.resetDirectionScratchState(): DeckWizardUiState = copy(
    selectedCommander = null,
    commanderQuery = "",
    commanderSearchResults = emptyList(),
    isSearchingCommander = false,
    seedStrategySuggestion = null,
    colorAffinityEntries = emptyList(),
    selectedColorAffinityEntry = null,
    taxonomyQuery = "",
    colorComboSuggestions = emptyList(),
    suggestedSeedCards = emptyList(),
    lockedColors = emptySet(),
    selectedArchetype = null,
    selectedDirectionTheme = null,
    selectedTribeKey = null,
    selectedTribeLabel = null,
    showSeedPicker = false,
    seedCards = emptyList(),
    seedQuery = "",
    seedSearchResults = emptyList(),
    isSearchingSeeds = false,
    colorIdentity = emptySet(),
    availableThemeTags = emptyList(),
    isLoadingThemeTags = false,
    selectedThemeHint = null,
    // Deck Wizard & Engine Rework plan, Workstream 2 -- "one Commander-flow attempt" scratch state,
    // same rationale as every other field above. [includeOutsideCollection] is deliberately EXCLUDED
    // (session-level, see its own KDoc).
    commanderColorFilter = emptySet(),
    commanderStructuredQuery = null,
    commanderResultTab = CommanderResultTab.COLLECTION,
    commanderStrategyRecommendations = emptyList(),
    isLoadingCommanderStrategies = false,
    selectedCuratedStrategyId = null,
    pendingTribeStrategy = null,
    commanderTribePickerCandidates = emptyList(),
    selectedStrategyThemes = emptyList(),
    selectedPosture = null,
    planAnalysis = null,
    isAnalyzingPlan = false,
    ownedAvailabilityBySection = emptyMap(),
    planSectionsQuery = "",
    planSectionsStructuredQuery = null,
    planSectionsTagFilter = emptySet(),
    planSectionsCollectionResults = emptyList(),
    planSectionsScryfallResults = emptyList(),
    isSearchingPlanSectionsScryfall = false,
    manualAddsSkeleton = null,
    manualAddsRoleFilter = null,
    manualAddsQuery = "",
    manualAddsSearchResults = emptyList(),
    isSearchingManualAdds = false,
)

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.4) — drives the wizard spec through
 * [BuildDeckFromTemplateUseCase] and writes the result into a FRESH deck this ViewModel creates
 * itself (unlike [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel], which may open
 * an EXISTING deck — the wizard's whole purpose is a single guided build, so it always starts a new
 * draft and hands off to Deck Studio only once the build succeeds).
 *
 * The deck is created lazily, on [onGenerate] — NOT on init — so backing out of the wizard before
 * generating never orphans a draft (no discard-if-empty machinery needed here, unlike Deck Studio).
 */
class DeckWizardViewModel(
    private val deckRepository: DeckRepository,
    private val userCardRepository: UserCardRepository,
    private val collectionProfileUseCase: CollectionProfileUseCase,
    private val buildDeckFromTemplateUseCase: BuildDeckFromTemplateUseCase,
    private val searchCardsUseCase: SearchCardsUseCase,
    private val communityAggregateRepository: CommunityAggregateRepository,
    private val crashReporter: CrashReporter,
    private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    // Deck Engine Unification plan (§5 Phase 3) — appended last so no existing positional-arg-free
    // call site needs to change. Pure, dependency-free use cases (no defaults needed in production;
    // Koin always supplies real instances) plus the ONE stateful dependency (the global community
    // feature flag) the Review step's source toggle needs to know whether to render at all.
    private val suggestStrategiesForSeedsUseCase: SuggestStrategiesForSeedsUseCase = SuggestStrategiesForSeedsUseCase(),
    private val rankOwnedCardsForProfileUseCase: RankOwnedCardsForProfileUseCase = RankOwnedCardsForProfileUseCase(),
    private val userPreferences: UserPreferencesDataStore? = null,
    // Deck Wizard & Engine Rework plan, Workstream 2 — the STRATEGY step's source 1
    // (`card_strategy_tags` payload). Required (like every other repository above), appended last
    // for the same positional-arg-free-call-site reason as the pure use cases below.
    private val cardStrategyTagsRepository: CardStrategyTagsRepository,
    // Deck Wizard Commander v3 plan, Phase 4.1 -- replaces the retired DeriveCommanderStrategiesUseCase.
    private val recommendCommanderStrategiesUseCase: RecommendCommanderStrategiesUseCase = RecommendCommanderStrategiesUseCase(),
    // Deck Wizard Commander v3 plan, Phase 5 (D2) -- the SINGLE analysis entry point PLAN_SECTIONS
    // scores against; the SAME shared singleton DeckDoctorOrchestrator/the harness use (never a
    // second instance). Required (no default, like every other repository param above): it has its
    // own non-trivial dependency graph (EvaluateDeckUseCase/InferDeckIdentityUseCase), so there is
    // no cheap fake default the way the pure use cases above get one.
    private val deckAnalysisPipeline: DeckAnalysisPipeline,
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
    private var seedSearchJob: Job? = null
    private var themeTagsJob: Job? = null
    private var generateJob: Job? = null
    private var commanderStrategyJob: Job? = null
    private var manualAddsSearchJob: Job? = null
    private var planAnalysisJob: Job? = null
    private var planSectionsSearchJob: Job? = null

    /** Set only once [onGenerate] has actually created the deck row -- lets [onCancelGeneration]
     * clean up a partial build instead of orphaning an empty draft. */
    private var pendingDeckId: String? = null

    init {
        // Discoveries v2 "Build this" hand-off (D11) — optional, all blank by default. Deck Engine
        // Unification (D2): nav args carry the unified taxonomy directly (raw ArchetypeId/ThemeId
        // enum names + a tribe key) instead of the old SeedStrategy-name/free-text-theme pair.
        val archetypeArg = savedStateHandle.get<String?>("archetype")?.takeIf { it.isNotEmpty() }
        val themeArg = savedStateHandle.get<String?>("theme")?.takeIf { it.isNotEmpty() }
        val tribeArg = savedStateHandle.get<String?>("tribe")?.takeIf { it.isNotEmpty() }
        val colorsArg = savedStateHandle.get<String?>("colors")?.takeIf { it.isNotEmpty() }
        // Deck Engine Unification plan D7 (4.3): a Commander Spellbook combo's card names --
        // percent-decoded by Navigation before this reads it, `|`-joined (NOT `,` -- many real
        // MTG card names contain a literal comma, e.g. "Urza, Lord High Artificer"; see
        // Screen.DeckWizard.createRoute's KDoc). Forces Flow A (cards-first): a combo hand-off IS
        // a card-first pick by construction.
        val seedsArg = savedStateHandle.get<String?>("seeds")?.takeIf { it.isNotEmpty() }
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (archetypeArg != null || themeArg != null || tribeArg != null || colorsArg != null) {
            _uiState.update {
                it.copy(
                    selectedArchetype = archetypeArg?.let { name -> ArchetypeId.entries.firstOrNull { a -> a.name == name } },
                    selectedDirectionTheme = themeArg?.let { name -> ThemeId.entries.firstOrNull { t -> t.name == name } },
                    selectedTribeKey = tribeArg,
                    selectedTribeLabel = tribeArg?.let { key -> key.removePrefix("tribe:").replaceFirstChar(Char::uppercase) },
                    colorIdentity = colorsArg?.let(::parseColorString).orEmpty(),
                )
            }
        }
        if (seedsArg.isNotEmpty()) {
            _uiState.update { it.copy(entryFlow = WizardEntryFlow.CARDS) }
        }

        viewModelScope.launch {
            runCatching {
                val collection = userCardRepository.observeCollection().first()
                collectionSnapshot = collection
                cardSnapshot = collection.map { it.card }
                collectionProfileUseCase(cardSnapshot)
            }.onSuccess { profile ->
                _uiState.update { it.copy(collectionProfile = profile, ownedCards = cardSnapshot, isLoadingProfile = false) }
                // QA fix (RUN 3b follow-up): collectionSnapshot only lands here, asynchronously --
                // if the user already picked a Flow B color-affinity entry or a Flow C color combo
                // WHILE this load was still in flight, onSelectColorAffinityEntry/onSelectColorCombo
                // ranked against a still-empty snapshot and silently produced an empty
                // suggestedSeedCards list (no crash, but the user's own owned cards never showed up
                // as suggestions). Re-rank against whichever pick is CURRENTLY active now that the
                // real snapshot is in, so a fast tap during the loading window still ends up correct.
                recomputeActiveSuggestedSeeds()
                if (seedsArg.isNotEmpty()) resolveComboSeeds(seedsArg)
            }.onFailure { t ->
                logFailure("deck_wizard_profile_load_failed", t)
                _uiState.update { it.copy(isLoadingProfile = false) }
            }
        }

        // Deck Engine Unification plan (§5 Phase 3.5) — the Review step's community-source toggle
        // only ever RENDERS when the global flag is on; a fetch failure degrades to "unavailable"
        // (never blocks the wizard, mirrors every other best-effort community-data read in this VM).
        viewModelScope.launch {
            val available = runCatching { userPreferences?.communityEngineEnabledFlow?.first() ?: false }.getOrDefault(false)
            _uiState.update { it.copy(communityEngineAvailable = available) }
        }
    }

    private fun parseColorString(raw: String): Set<ManaColor> =
        raw.mapNotNull { ch -> ManaColor.entries.firstOrNull { it.symbol == ch.toString() } }.toSet()

    /** QA fix (RUN 3b): cancels any in-flight commander/seed search or theme-tag fetch BEFORE a
     * format/entry-flow switch wipes the state those coroutines write into -- without this, a
     * debounced search from the ABANDONED flow could land after the reset and silently repopulate
     * `commanderSearchResults`/`seedSearchResults`/`availableThemeTags` for the NEW flow. */
    private fun cancelDirectionSearchJobs() {
        commanderSearchJob?.cancel()
        seedSearchJob?.cancel()
        themeTagsJob?.cancel()
        commanderStrategyJob?.cancel()
        manualAddsSearchJob?.cancel()
        planAnalysisJob?.cancel()
        planSectionsSearchJob?.cancel()
    }

    // ── Step 1 — Format ───────────────────────────────────────────────────────

    fun onSelectFormat(format: DeckFormat) {
        // v1 targets Commander (+ Commander Casual) and Casual only (plan D1) — the other 6
        // restored 60-card formats render "coming soon" and disabled in the UI, but guard here too
        // since this is the actual source of truth (never trust the UI-only disabled state).
        if (!format.isCommanderFormat && format != DeckFormat.CASUAL) return
        // QA fix (RUN 3b): FORMAT is reachable via back-navigation at any point after Direction-step
        // state has already been populated (see resetDirectionScratchState's KDoc) -- an ACTUAL
        // format change wipes every per-flow scratch field and resets the entry chooser to CARDS, so
        // a stale commander/archetype/colors pick from the abandoned format can never leak into a
        // build under the new one. A re-tap of the CURRENTLY selected format is a pure no-op-ish
        // write (never wipes state the user hasn't actually left).
        if (_uiState.value.selectedFormat == format) {
            _uiState.update { it.copy(selectedFormat = format) }
            return
        }
        cancelDirectionSearchJobs()
        _uiState.update { it.resetDirectionScratchState().copy(selectedFormat = format, entryFlow = WizardEntryFlow.CARDS) }
    }

    // ── Step 2 — Direction ───────────────────────────────────────────────────

    /**
     * Deck Engine Unification plan (D2): resolves a collection-lean STRATEGY [CardTag] chip tap
     * onto the unified taxonomy via [DeckIdentitySeedTags.archetypeForTag]/`.themeForTag` --
     * replaces the old `SeedStrategy.forTag` + raw-tagHint-fallback split
     * (`onSelectStrategyDirection`/`onSelectTagHintDirection`, Wizard Quality Campaign B1) with ONE
     * handler. [CollectionLeanSection] only renders a chip whose tag resolves to one or the other
     * (never a dead tap -- see that composable's KDoc), so the `else` branch below is defensive
     * only and never observed in practice.
     */
    fun onSelectDirectionTag(tag: CardTag) {
        val archetype = DeckIdentitySeedTags.archetypeForTag(tag)
        val theme = DeckIdentitySeedTags.themeForTag(tag)
        _uiState.update { state ->
            when {
                archetype != null -> state.copy(
                    selectedArchetype = if (state.selectedArchetype == archetype) null else archetype,
                    selectedDirectionTheme = null,
                    selectedTribeKey = null,
                    selectedTribeLabel = null,
                )
                theme != null -> state.copy(
                    selectedDirectionTheme = if (state.selectedDirectionTheme == theme) null else theme,
                    selectedArchetype = null,
                    selectedTribeKey = null,
                    selectedTribeLabel = null,
                )
                else -> state
            }
        }
    }

    /** Edge-case audit (Wizard Quality Campaign final gate, 2026-07-19): the tribe Direction chip's
     * fix for the same "dead chip" bug class B1 fixed for the strategy/tag chips -- previously this
     * only toggled [DeckWizardUiState.selectedTribeLabel] for Review-screen display with no effect
     * on the build. Carries [CollectionTribeSignal.tribeKey] (the stable `tribe:<subtype>` key, not
     * the pluralized display label) into [StrategyProfile.tribe] via [onGenerate]. Same "one
     * Direction pick" slot as archetype/theme -- clears both. */
    fun onSelectTribeDirection(tribe: CollectionTribeSignal) {
        _uiState.update {
            it.copy(
                selectedTribeKey = if (it.selectedTribeKey == tribe.tribeKey) null else tribe.tribeKey,
                selectedTribeLabel = if (it.selectedTribeKey == tribe.tribeKey) null else tribe.displayLabel,
                selectedArchetype = null,
                selectedDirectionTheme = null,
            )
        }
    }

    /**
     * Deck Wizard & Engine Rework plan, Workstream 2.1 (D-A) — outside-collection search is OPT-IN
     * ([DeckWizardUiState.includeOutsideCollection], default off): with it off, no network call
     * fires at all and [DeckWizardUiState.commanderSearchResults] stays empty (the COMMANDER_PICK
     * step renders the local-collection candidate list instead, computed in the UI layer from
     * [DeckWizardUiState.collectionProfile] — no VM state needed for that path, it's a pure filter
     * of already-loaded data). With it on, the query is a real Scryfall search restricted to
     * `is:commander` (D-G — this is exactly the Scryfall-side equivalent of [CommanderEligibility]'s
     * local-collection rule) plus an `id<=` color-identity clause from
     * [DeckWizardUiState.commanderColorFilter].
     */
    fun onCommanderQueryChange(query: String) {
        _uiState.update { it.copy(commanderQuery = query) }
        commanderSearchJob?.cancel()
        val includeOutside = _uiState.value.includeOutsideCollection
        if (!includeOutside || query.trim().length < SEARCH_MIN_LENGTH) {
            _uiState.update { it.copy(commanderSearchResults = emptyList(), isSearchingCommander = false) }
            return
        }
        commanderSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingCommander = true) }
            val colorFilter = _uiState.value.commanderColorFilter
            val results = when (val res = searchCardsUseCase(commanderSearchQuery(query.trim(), colorFilter))) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_commander_search_failed")
                    emptyList()
                }
            }
            _uiState.update { it.copy(commanderSearchResults = results, isSearchingCommander = false) }
        }
    }

    /** `is:commander` + an optional `id<=` color-identity clause + the free-text name — routed
     * through [searchCardsUseCase] -> [com.mmg.manahub.core.domain.repository.CardRepository
     * .searchCardsPaginated], which is ALREADY wrapped in `ScryfallRequestQueue.execute { }` at the
     * remote-data-source layer (verified: `ScryfallRemoteDataSource.searchCardsPaginated`) — no
     * second wrapping needed here. */
    private fun commanderSearchQuery(name: String, colors: Set<ManaColor>): String {
        val identityClause = if (colors.isNotEmpty()) " id<=${colors.joinToString("") { it.symbol }}" else ""
        return "is:commander$identityClause $name"
    }

    /** Deck Wizard Commander v3 plan (Phase 3.2, D7/R2): COMMANDER_PICK no longer renders this row
     * (deleted along with the outside-collection toggle) -- kept only because
     * [onCommanderQueryChange]'s body still reads [DeckWizardUiState.commanderColorFilter] for the
     * Casual Direction flow's own (color-filter-less) embedded commander search, where this
     * function has never been wired either. Effectively dead; not deleted to avoid a needless
     * ripple into that field's other read site. */
    fun onToggleCommanderColorFilter(code: String) {
        _uiState.update { state ->
            if (code == "All") {
                state.copy(commanderColorFilter = emptySet())
            } else {
                val color = ManaColor.entries.firstOrNull { it.symbol == code } ?: return@update state
                val updated = if (color in state.commanderColorFilter) state.commanderColorFilter - color else state.commanderColorFilter + color
                state.copy(commanderColorFilter = updated)
            }
        }
        if (_uiState.value.commanderQuery.trim().length >= SEARCH_MIN_LENGTH) {
            onCommanderQueryChange(_uiState.value.commanderQuery)
        }
    }

    /** D-A's single shared "include outside your collection" toggle (COMMANDER_PICK + MANUAL_ADDS +,
     * as of Workstream 3.1, Flow A's own seed search). Turning it OFF clears any in-flight
     * outside-collection results immediately (rather than leaving a stale list the user can no
     * longer explain the presence of). */
    fun onToggleIncludeOutsideCollection() {
        _uiState.update { it.copy(includeOutsideCollection = !it.includeOutsideCollection) }
        if (!_uiState.value.includeOutsideCollection) {
            commanderSearchJob?.cancel()
            manualAddsSearchJob?.cancel()
            seedSearchJob?.cancel()
            _uiState.update {
                it.copy(
                    commanderSearchResults = emptyList(),
                    isSearchingCommander = false,
                    manualAddsSearchResults = emptyList(),
                    isSearchingManualAdds = false,
                    seedSearchResults = emptyList(),
                    isSearchingSeeds = false,
                )
            }
        }
    }

    fun onSelectCommander(card: Card) {
        commanderSearchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedCommander = card,
                colorIdentity = card.colorIdentity.toManaColorSet(),
                commanderQuery = "",
                commanderSearchResults = emptyList(),
                commanderStructuredQuery = null,
                commanderResultTab = CommanderResultTab.COLLECTION,
                availableThemeTags = emptyList(),
                selectedThemeHint = null,
            )
        }
        loadThemeTags(card)
        recomputeSeedStrategySuggestion()
        recommendCommanderStrategies(card)
    }

    fun onClearCommander() {
        _uiState.update {
            it.copy(
                selectedCommander = null,
                colorIdentity = emptySet(),
                availableThemeTags = emptyList(),
                selectedThemeHint = null,
                commanderStrategyRecommendations = emptyList(),
                selectedCuratedStrategyId = null,
                pendingTribeStrategy = null,
                commanderTribePickerCandidates = emptyList(),
            )
        }
        recomputeSeedStrategySuggestion()
    }

    /** Deck Wizard Commander v3 plan (Phase 3.2): COMMANDER_PICK's own plain name filter over the
     * local candidate grid ([commanderPickLocalCandidates]) and, once a structured search is
     * active, the Collection tab ([commanderPickCollectionTabResults]) -- collection-only, no
     * network call (D7/R2 deleted this step's outside-collection search; unowned commanders now
     * arrive ONLY through the Tune icon's All-cards tab, see [applyCommanderStructuredSearch]).
     * Distinct from [onCommanderQueryChange], which stays the Casual Direction flow's own
     * outside-collection commander search, unchanged by this campaign. */
    fun onCommanderNameFilterChange(query: String) {
        _uiState.update { it.copy(commanderQuery = query) }
    }

    /**
     * Deck Wizard Commander v3 plan (Phase 3.2/3.3/3.4): the Tune icon's [com.mmg.manahub.core.ui
     * .components.search.AdvancedSearchSheet] result for COMMANDER_PICK -- filters BOTH result
     * tabs from one [AdvancedSearchQuery], mirroring [com.mmg.manahub.feature.decks.presentation
     * .DeckStudioViewModel.applyStructuredSearch]'s own "one query, two tabs" contract via the
     * shared [StructuredCardSearch] helper (3.3): the Collection tab is computed locally by the UI
     * layer ([commanderPickCollectionTabResults], a pure filter of already-loaded
     * [DeckWizardUiState.ownedCards] -- no VM round-trip needed), while the All-cards tab fires a
     * real Scryfall search here. The sheet itself force-merges the step's locked criteria
     * ([commanderLockedCriteria]) into [query] before this is ever called (D14), so this function
     * never needs to re-add them.
     */
    fun applyCommanderStructuredSearch(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(commanderStructuredQuery = query.takeIf { q -> !q.isEmpty() }) }
        commanderSearchJob?.cancel()
        val fragment = StructuredCardSearch.scryfallFragment(query)
        if (fragment == null) {
            _uiState.update { it.copy(commanderSearchResults = emptyList(), isSearchingCommander = false) }
            return
        }
        commanderSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingCommander = true) }
            val results = when (val res = searchCardsUseCase(fragment)) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_commander_structured_search_failed")
                    emptyList()
                }
            }
            _uiState.update { it.copy(commanderSearchResults = results, isSearchingCommander = false) }
        }
    }

    /** Deck Wizard Commander v3 plan (Phase 3.2): switches COMMANDER_PICK's Collection/All-cards
     * result tab -- a pure UI-state toggle, no re-fetch (the All-cards results are already cached
     * from [applyCommanderStructuredSearch]). */
    fun onSelectCommanderResultTab(tab: CommanderResultTab) {
        _uiState.update { it.copy(commanderResultTab = tab) }
    }

    /**
     * Deck Wizard Commander v3 plan, Phase 4.1 — ranks [com.mmg.manahub.feature.decks.domain.engine
     * .CuratedStrategyCatalog.ALL] for [commander] via [RecommendCommanderStrategiesUseCase]:
     * the commander's own `card_strategy_tags` row, the EDHREC per-commander aggregate's theme tags
     * (the SAME fetch [loadThemeTags] already makes — issued again here rather than shared with that
     * function's result, since [loadThemeTags] feeds the now Commander-unreachable Identity-step
     * theme picker and keeping the two call sites independent avoids coupling an old,
     * soon-superseded consumer to a new one), [ColorStrategyAffinity], and owned-role coverage over
     * [cardSnapshot]. Best-effort throughout — ANY failure degrades the corresponding signal to zero
     * contribution, never blocks the step (the use case itself never throws). Preselects the #1
     * recommendation on arrival (product default, plan §8) via [selectCommanderStrategy].
     */
    private fun recommendCommanderStrategies(commander: Card) {
        commanderStrategyJob?.cancel()
        commanderStrategyJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCommanderStrategies = true) }
            val format = _uiState.value.selectedFormat ?: DeckFormat.COMMANDER
            val source1 = runCatching { cardStrategyTagsRepository.getStrategyTags(commander.oracleId) }
                .onFailure { crashReporter.log("deck_wizard_commander_strategy_tags_fetch_failed") }
                .getOrNull() as? CardStrategyTagsResult.Found
            val edhrecThemeNames = runCatching {
                val result = communityAggregateRepository.getCommanderAggregate(commander.name)
                (result as? DataResult.Success)?.data?.themeTags?.map { it.name }.orEmpty()
            }.getOrDefault(emptyList())

            val recommendations = recommendCommanderStrategiesUseCase(
                format = format,
                commander = commander,
                identity = commander.colorIdentity.toManaColorSet(),
                ownedCollection = cardSnapshot.map { OwnedCard(it, 1) },
                ownTags = source1?.tags.orEmpty(),
                ownTribes = source1?.tribes.orEmpty(),
                edhrecThemeNames = edhrecThemeNames,
            )
            _uiState.update { it.copy(commanderStrategyRecommendations = recommendations, isLoadingCommanderStrategies = false) }
            val topPick = recommendations.firstOrNull()
            if (topPick != null) selectCommanderStrategy(topPick.strategy, topPick.tribe) else selectCommanderStrategy(null, null)
        }
    }

    /** COMMANDER_PICK's "Next" — requires a commander (same guard/toast precedent as the old
     * Commander-direction check, [onNextFromDirection]'s Commander branch, which this replaces). */
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
    }

    // ── STRATEGY step (Deck Wizard Commander v3 plan, Phase 4.2) ─────────────────

    /** Writes [strategy]/[tribe]'s pin into the shared archetype/themes/tribe fields (D4:
     * `CuratedStrategy.toPin`, the SAME function Deck Studio's own strategy picker uses) plus the
     * STRATEGY step's own [DeckWizardUiState.selectedCuratedStrategyId] display pointer.
     * `strategy == null` is Custom (D6): every pin field clears. */
    private fun selectCommanderStrategy(strategy: CuratedStrategy?, tribe: String?) {
        val pin = strategy?.toPin(tribe)
        _uiState.update {
            it.copy(
                selectedCuratedStrategyId = strategy?.id,
                selectedArchetype = pin?.archetype,
                selectedStrategyThemes = pin?.themes.orEmpty(),
                selectedTribeKey = pin?.tribe,
                selectedTribeLabel = pin?.tribe?.let { key -> key.removePrefix("tribe:").replaceFirstChar(Char::uppercase) },
                // Phase 5 fix (F3 gap, closed): `pin.posture` used to be computed here and dropped
                // on the floor -- there was no field to store it in, so PLAN_SECTIONS' analysis
                // could never reflect a Voltron/Ramp/Tempo/Toolbox/Group Hug posture pick.
                selectedPosture = pin?.posture,
            )
        }
    }

    /** Selects a "Recommended"/"Other plans" row. A [CuratedStrategy.requiresTribe] entry the
     * recommender could already resolve a concrete tribe for (its own
     * [StrategyRecommendation.tribe]) applies immediately; one it could NOT resolve opens the tribe
     * sub-picker instead ([onRequestTribeForStrategy]) rather than applying with a `null` tribe. */
    fun onSelectCommanderStrategy(strategy: CuratedStrategy) {
        val recommendation = _uiState.value.commanderStrategyRecommendations.firstOrNull { it.strategy.id == strategy.id }
        val tribe = recommendation?.tribe
        if (strategy.requiresTribe && tribe == null) {
            onRequestTribeForStrategy(strategy)
        } else {
            selectCommanderStrategy(strategy, tribe)
        }
    }

    /** D6: Custom is always separately offered, regardless of what the recommender returned. */
    fun onSelectCustomStrategy() = selectCommanderStrategy(null, null)

    /** Opens the tribe sub-picker for [strategy], seeded with the collection's dominant tribes
     * within the selected commander's identity — reuses [CollectionProfileUseCase.dominantTribes]
     * over an identity-filtered slice of [cardSnapshot] (no new dominant-tribe computation). */
    fun onRequestTribeForStrategy(strategy: CuratedStrategy) {
        val identitySymbols = _uiState.value.colorIdentity.map { it.symbol }.toSet()
        viewModelScope.launch {
            val identityCards = cardSnapshot.filter { card -> identitySymbols.containsAll(card.colorIdentity) }
            val profile = collectionProfileUseCase(identityCards, limit = TRIBE_PICKER_CANDIDATE_LIMIT)
            _uiState.update {
                it.copy(pendingTribeStrategy = strategy, commanderTribePickerCandidates = profile.dominantTribes)
            }
        }
    }

    fun onCancelTribePickForStrategy() {
        _uiState.update { it.copy(pendingTribeStrategy = null, commanderTribePickerCandidates = emptyList()) }
    }

    fun onPickTribeForStrategy(tribeKey: String) {
        val strategy = _uiState.value.pendingTribeStrategy ?: return
        selectCommanderStrategy(strategy, tribeKey)
        _uiState.update { it.copy(pendingTribeStrategy = null, commanderTribePickerCandidates = emptyList()) }
    }

    /**
     * STRATEGY's "Next" (2.4) — ALWAYS enabled (no gate): an untouched pick already means GENERIC
     * ("Balanced"), which is a valid, complete Commander plan (unlike D-B's Casual Flow A rule,
     * which requires a real pick — Commander keeps its short escape hatch, see [WizardPhase]'s and
     * [DeckWizardUiState.commanderStrategyCandidates]' own KDoc). Resolves the skeleton ONCE here
     * (not lazily in the MANUAL_ADDS composable) so that step's role sections/filter chips render
     * immediately on entry.
     */
    fun onNextFromStrategy() {
        val state = _uiState.value
        // Deck Analysis Engine v3: ArchetypeId.GENERIC no longer exists -- an unpinned selection is
        // `null` directly (see BuildDeckFromTemplateUseCase.resolveArchetypeSkeleton's own compat
        // note for the same substitution).
        val archetype = state.selectedArchetype
        val themes = state.selectedStrategyThemes
        // Fix 5 (edge-case audit, 2026-07-28): mirrors BuildDeckFromTemplateUseCase
        // .resolveArchetypeSkeleton's own GENERIC-with-no-themes gate -- the REAL build never
        // resolves a skeleton for a GENERIC ("Balanced") pick with no themes (Motor A scores with
        // zero theme bonus in that case), so this UI-only preview must not either. Without this
        // gate, a Commander player picking "Balanced" would see a MANUAL_ADDS role chip reflecting
        // an archetype-flavored skeleton the real build never actually applies.
        val skeleton = if (archetype == null && themes.isEmpty()) {
            null
        } else {
            ArchetypeSkeletonResolver.resolveWithColor(
                format = ArchetypeFormat.COMMANDER,
                archetype = archetype,
                themes = themes,
                identity = state.colorIdentity,
            )
        }
        logStep("manual_adds")
        _uiState.update { it.copy(phase = WizardPhase.MANUAL_ADDS, manualAddsSkeleton = skeleton) }
        // Deck Wizard Commander v3 plan (Phase 5, 5.1) -- PLAN_SECTIONS needs a real DeckAnalysis
        // the moment it's entered (commander + no manual adds yet is still a real, analyzable plan).
        recomputePlanAnalysis()
    }

    // ── PLAN_SECTIONS step (Deck Wizard Commander v3 plan, Phase 5, R4 -- Commander only) ─────────

    /**
     * The ONLY analysis call this step makes -- debounced (so a burst of manual add/remove taps
     * doesn't fire one [com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline.analyze]
     * per tap) and off the main thread by construction (a `suspend` call inside [viewModelScope],
     * same idiom every other async VM function in this file already uses -- this codebase has no
     * separate injected-dispatcher convention for ViewModels, see
     * `feedback_koin_named_dispatcher_pattern`'s scope: that pattern is for Koin-provided
     * repositories/use cases, not ViewModel-internal coroutine launches).
     *
     * [mainboard] mirrors EXACTLY what [com.mmg.manahub.feature.decks.domain.template
     * .BuildCommanderDeckUseCase] itself passes to `analyze` for a REAL build (verified against its
     * `fullMainboard` construction, Phase 2/2.5): the commander as its own [DeckEntry] first, then
     * every [DeckWizardUiState.seedCards] manual add -- so [DeckWizardUiState.planAnalysis] is
     * exactly the object the real build's own verify pass will produce once Phase 6 wires
     * generation, never an approximation.
     *
     * `emitProgression = false` (same reason [com.mmg.manahub.feature.decks.domain.template
     * .BuildCommanderDeckUseCase] passes it): this fires on every manual-add change, and must never
     * spam the Deck Doctor exploration quest.
     *
     * A thrown exception (the pipeline's own internal `runCatching` around its v3 half can still let
     * a seed-inference or pin-fold exception through -- see [DeckAnalysisPipeline]'s own KDoc, which
     * does not claim full exception-safety) degrades to `planAnalysis = null` -- the step's empty/
     * error state, never a VM crash.
     */
    private fun recomputePlanAnalysis() {
        planAnalysisJob?.cancel()
        planAnalysisJob = viewModelScope.launch {
            delay(PLAN_ANALYSIS_DEBOUNCE_MS)
            _uiState.update { it.copy(isAnalyzingPlan = true) }
            val state = _uiState.value
            val commander = state.selectedCommander
            val format = state.selectedFormat ?: DeckFormat.COMMANDER
            val mainboard = buildList {
                commander?.let { add(DeckEntry(card = it, quantity = 1, isOwned = true, isSideboard = false)) }
                state.seedCards.forEach { card ->
                    add(DeckEntry(card = card, quantity = 1, isOwned = cardSnapshot.any { owned -> owned.scryfallId == card.scryfallId }, isSideboard = false))
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
            }.onFailure { t -> logFailure("deck_wizard_plan_analysis_failed", t) }.getOrNull()
            val excludeIds = (listOfNotNull(commander?.scryfallId) + state.seedCards.map { it.scryfallId }).toSet()
            val availability = analysis?.let {
                computeOwnedAvailabilityBySection(it.pillars.flatMap { pillar -> pillar.sections }, cardSnapshot, excludeIds, state.colorIdentity, format)
            }.orEmpty()
            _uiState.update { it.copy(planAnalysis = analysis, isAnalyzingPlan = false, ownedAvailabilityBySection = availability) }
        }
    }

    /** PLAN_SECTIONS' "Browse for &lt;Category&gt;" sheet -- plain search-bar text, updates the
     * Collection tab only (mirrors [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel
     * .onAddCardsQueryChange]'s own split between a live local re-filter and a separate, explicit
     * Scryfall fetch trigger, [searchPlanSectionsScryfall]). */
    fun onPlanSectionsQueryChange(query: String) {
        _uiState.update { it.copy(planSectionsQuery = query) }
        publishPlanSectionsCollectionResults()
    }

    /** The Tune icon's [com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet] result, OR a
     * section's own "Browse for X" preset (Phase 5, 5.2) -- filters BOTH result tabs from one
     * [AdvancedSearchQuery] via the shared [StructuredCardSearch] helper (Phase 3.3), same "one
     * applyStructuredSearch -> Scryfall + local matcher" contract [applyCommanderStructuredSearch]/
     * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.applyStructuredSearch] already
     * use. */
    fun applyPlanSectionsStructuredSearch(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(planSectionsStructuredQuery = query.takeIf { q -> !q.isEmpty() }) }
        publishPlanSectionsCollectionResults()
        searchPlanSectionsScryfall(_uiState.value.planSectionsQuery)
    }

    /** The Analysis-tab-style category [com.mmg.manahub.core.model.CardTag] pre-filter for the
     * Collection tab ([com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery
     * .collectionTagKeysFor]) -- a DIFFERENT key space from [applyPlanSectionsStructuredSearch]'s
     * criteria, ANDed together (mirrors `DeckStudioViewModel.searchCollectionByTags`). Empty [keys]
     * is a no-op filter, never a falsely-empty tab (many sections have no tag-key equivalent). */
    fun searchPlanSectionsCollectionByTags(keys: Set<String>) {
        _uiState.update { it.copy(planSectionsTagFilter = keys) }
        publishPlanSectionsCollectionResults()
    }

    private fun publishPlanSectionsCollectionResults() {
        val state = _uiState.value
        val matches = state.ownedCards.filter { card ->
            StructuredCardSearch.matches(card, state.planSectionsStructuredQuery) &&
                (state.planSectionsTagFilter.isEmpty() || (card.tags + card.userTags).any { it.key in state.planSectionsTagFilter }) &&
                (state.planSectionsQuery.isBlank() || card.name.contains(state.planSectionsQuery, ignoreCase = true))
        }
        _uiState.update { it.copy(planSectionsCollectionResults = matches) }
    }

    /** The All-cards tab's real Scryfall search -- [query] (typed free text) combined with
     * [DeckWizardUiState.planSectionsStructuredQuery]'s own Scryfall fragment, same combination
     * rule as [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.searchScryfallDirect]. */
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

    /** Resets every PLAN_SECTIONS browse-sheet field -- called on the sheet's `onDismiss`, mirrors
     * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.clearActiveStructuredSearchFragment]. */
    fun clearPlanSectionsSearchState() {
        planSectionsSearchJob?.cancel()
        _uiState.update {
            it.copy(
                planSectionsQuery = "",
                planSectionsStructuredQuery = null,
                planSectionsTagFilter = emptySet(),
                planSectionsCollectionResults = emptyList(),
                planSectionsScryfallResults = emptyList(),
                isSearchingPlanSectionsScryfall = false,
            )
        }
    }

    // ── MANUAL_ADDS step (Deck Wizard & Engine Rework plan, Workstream 2.3 — SHARED, WS3 reuses) ──

    /** Same D-A/outside-collection contract as [onCommanderQueryChange], over the collection instead
     * of a commander candidate pool; the resulting [Card]s are hard-filtered by [colorIdentity]
     * subset (colorless always included) by the CALLER (the composable), same as the local-only
     * collection-search branch — this function only fills [DeckWizardUiState.manualAddsSearchResults]
     * for the outside-collection branch, mirroring [onCommanderQueryChange]'s own split. */
    fun onManualAddsQueryChange(query: String) {
        _uiState.update { it.copy(manualAddsQuery = query) }
        manualAddsSearchJob?.cancel()
        val includeOutside = _uiState.value.includeOutsideCollection
        if (!includeOutside || query.trim().length < SEARCH_MIN_LENGTH) {
            _uiState.update { it.copy(manualAddsSearchResults = emptyList(), isSearchingManualAdds = false) }
            return
        }
        manualAddsSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingManualAdds = true) }
            val colors = _uiState.value.colorIdentity
            val results = when (val res = searchCardsUseCase(manualAddsSearchQuery(query.trim(), colors))) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_manual_adds_search_failed")
                    emptyList()
                }
            }
            _uiState.update { it.copy(manualAddsSearchResults = results, isSearchingManualAdds = false) }
        }
    }

    /** Free-text name + an `id<=` color-identity clause — no role/theme `otag:` encoding here (that
     * query-quality backstop is Workstream 4's scope, not this one). Same
     * `ScryfallRequestQueue`-wrapped [searchCardsUseCase] path as [commanderSearchQuery]. */
    private fun manualAddsSearchQuery(name: String, colors: Set<ManaColor>): String {
        val identityClause = if (colors.isNotEmpty()) " id<=${colors.joinToString("") { it.symbol }}" else ""
        return "$name$identityClause"
    }

    /** Single-select role-key filter chip (toggle) over [DeckWizardUiState.manualAddsSkeleton]'s role
     * keys — see that field's KDoc. */
    fun onSelectManualAddsRoleFilter(roleKey: String?) =
        _uiState.update { it.copy(manualAddsRoleFilter = if (it.manualAddsRoleFilter == roleKey) null else roleKey) }

    /** MANUAL_ADDS is fully skippable (2.3) — no gate, ever. */
    fun onNextFromManualAdds() {
        logStep("review")
        _uiState.update { it.copy(phase = WizardPhase.REVIEW) }
    }

    /** Best-effort commander-aggregate theme-tag fetch (Identity step's theme picker, plan §3.4
     * Step 3). ANY failure (Worker down, obscure commander) leaves [DeckWizardUiState
     * .availableThemeTags] empty — never blocks the wizard, mirrors every other community-data
     * fetch in this codebase ("degraded, never dead"). */
    private fun loadThemeTags(commander: Card) {
        themeTagsJob?.cancel()
        themeTagsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingThemeTags = true) }
            val tags = runCatching {
                val result = communityAggregateRepository.getCommanderAggregate(commander.name)
                (result as? DataResult.Success)?.data?.themeTags?.map { it.name }.orEmpty()
            }.onFailure { crashReporter.log("deck_wizard_theme_tags_fetch_failed") }.getOrDefault(emptyList())
            _uiState.update { it.copy(availableThemeTags = tags, isLoadingThemeTags = false) }
        }
    }

    fun onToggleSeedPicker() = _uiState.update { it.copy(showSeedPicker = !it.showSeedPicker) }

    /**
     * Deck Wizard & Engine Rework plan, Workstream 3.1 (D-A) — same shared "include outside your
     * collection" contract as [onCommanderQueryChange]/[onManualAddsQueryChange]: OFF (default) means
     * no network call at all, and the composable renders a LOCAL candidate list filtered from
     * [DeckWizardUiState.ownedCards] instead (minus the legendary-only restriction commander search
     * applies — any owned card is a valid seed). ON fires this SAME debounced, unrestricted Scryfall
     * search as before this workstream.
     */
    fun onSeedQueryChange(query: String) {
        _uiState.update { it.copy(seedQuery = query) }
        seedSearchJob?.cancel()
        val includeOutside = _uiState.value.includeOutsideCollection
        if (!includeOutside || query.trim().length < SEARCH_MIN_LENGTH) {
            _uiState.update { it.copy(seedSearchResults = emptyList(), isSearchingSeeds = false) }
            return
        }
        seedSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingSeeds = true) }
            val results = when (val res = searchCardsUseCase(query.trim())) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_card_search_failed")
                    emptyList()
                }
            }
            _uiState.update { it.copy(seedSearchResults = results, isSearchingSeeds = false) }
        }
    }

    /**
     * Deck Wizard Commander v3 plan (Phase 5, 5.1/R5) -- for a Commander format, a manual add is
     * ALWAYS kept (D7), but must still be identity-legal and format-legal (a color-identity/legality
     * violation is not "off-plan", it's a rules violation the wizard must never write) and deduped
     * BY NAME (not just [Card.scryfallId] -- two different printings of the same card would
     * otherwise both slip into [DeckWizardUiState.seedCards], violating the singleton rule
     * `feature/decks/CLAUDE.md`'s Phase 4 construction-validation block documents; basics are
     * exempt, same rule [BasicLandCalculator.isBasicLand] already encodes elsewhere in this engine).
     * Casual's own [onAddSeed] behavior (scryfallId-only dedupe, no identity/legality gate) stays
     * byte-identical -- this whole block is gated on [DeckFormat.isCommanderFormat].
     */
    fun onAddSeed(card: Card) {
        val state = _uiState.value
        val isCommander = state.selectedFormat?.isCommanderFormat == true
        if (isCommander && !isCommanderManualAddValid(card, state)) {
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_manual_add_rejected)))
            }
            return
        }
        val current = state.seedCards
        val isDuplicateByName = isCommander &&
            !BasicLandCalculator.isBasicLand(card) &&
            current.any { it.name.equals(card.name, ignoreCase = false) }
        if (current.any { it.scryfallId == card.scryfallId } || isDuplicateByName || current.size >= MAX_SEED_CARDS) return
        _uiState.update { it.copy(seedCards = current + card) }
        recomputeSeedLockedColors()
        recomputeSeedStrategySuggestion()
        if (isCommander) recomputePlanAnalysis()
    }

    /** Color identity ⊆ [DeckWizardUiState.colorIdentity] + format legality
     * ([DeckFormat.COMMANDER] requires `legal`, [DeckFormat.COMMANDER_CASUAL] only excludes
     * `banned`) -- the SAME rule [com.mmg.manahub.feature.decks.domain.template
     * .BuildCommanderDeckUseCase.isLegalForCommanderFormat] applies to engine-placed candidates, so
     * a manual add can never be a card the build itself would refuse to place for a rules reason. */
    private fun isCommanderManualAddValid(card: Card, state: DeckWizardUiState): Boolean {
        val identitySymbols = state.colorIdentity.map { it.symbol }.toSet()
        if (!card.colorIdentity.all { it in identitySymbols }) return false
        return if (state.selectedFormat == DeckFormat.COMMANDER) card.legalityCommander == "legal" else card.legalityCommander != "banned"
    }

    fun onRemoveSeed(card: Card) {
        val isCommander = _uiState.value.selectedFormat?.isCommanderFormat == true
        _uiState.update { state ->
            val remaining = state.seedCards.filterNot { s -> s.scryfallId == card.scryfallId }
            if (remaining.isEmpty()) {
                // Fix 6 (edge-case audit, 2026-07-28): removing the LAST seed must also clear any
                // strategy pick that was justified by it -- otherwise the visible strategy-candidate
                // list recomputes to empty (recomputeSeedStrategySuggestion below, nothing left to
                // rank), yet `hasStrategyPick` in onNextFromDirection stays true from the stale
                // selection, silently advancing to MANUAL_ADDS on a plan the user can no longer see
                // or reconsider. `lockedColors`/`colorIdentity` are deliberately NOT reset here --
                // recomputeSeedLockedColors' own contract is additive-only ("removing a seed unlocks
                // its color, but the color itself stays picked until manually deselected"); only the
                // ARCHETYPE/THEME/TRIBE pick is unjustified by an empty seed list, not the colors.
                state.copy(
                    seedCards = remaining,
                    selectedArchetype = null,
                    selectedDirectionTheme = null,
                    selectedTribeKey = null,
                    selectedTribeLabel = null,
                )
            } else {
                state.copy(seedCards = remaining)
            }
        }
        recomputeSeedLockedColors()
        recomputeSeedStrategySuggestion()
        if (isCommander) recomputePlanAnalysis()
    }

    /**
     * Deck Wizard & Engine Rework plan, Workstream 3.1 — recomputes [DeckWizardUiState.lockedColors]
     * from scratch as the union of every currently picked [DeckWizardUiState.seedCards]' own
     * [Card.colorIdentity], then folds it INTO [DeckWizardUiState.colorIdentity] (never replaces —
     * an explicit color the user already toggled on survives untouched, union not overwrite).
     * Recomputing from scratch (rather than incrementally adding/removing per seed) is what makes
     * "removing a seed unlocks its color" correct by construction: a color only remains locked while
     * SOME currently-picked seed still needs it.
     *
     * Called from every [seedCards] mutation ([onAddSeed]/[onRemoveSeed]/[resolveComboSeeds]) — safe
     * to call unconditionally regardless of which flow/format is adding the seed: MANUAL_ADDS'
     * (Commander AND Casual, Workstream 2.3) candidates are already hard-filtered to
     * `card.colorIdentity ⊆ colorIdentity`, and Flow B/C's suggested-seed toggles rank only cards
     * already within the picked [DeckWizardUiState.colorIdentity] ([RankOwnedCardsForProfileUseCase]),
     * so in every path except Flow A's own seed picker this union is a guaranteed no-op.
     */
    private fun recomputeSeedLockedColors() {
        _uiState.update { state ->
            val locked = state.seedCards.flatMap { it.colorIdentity }.toManaColorSet()
            state.copy(lockedColors = locked, colorIdentity = state.colorIdentity + locked)
        }
    }

    /**
     * Flow A's own color picker (Workstream 3.1) — toggles a color in/out of
     * [DeckWizardUiState.colorIdentity]. A no-op while [color] is in [DeckWizardUiState.lockedColors]
     * (cannot deselect a color a currently-picked seed's identity requires) — the UI never renders a
     * clickable affordance for a locked chip either ([com.mmg.manahub.feature.decks.presentation.wizard
     * .ColorToggleChip]'s `readOnly` param), this is the defensive VM-side backstop, same "never
     * trust the UI-only disabled state" precedent as every other gate in this class. Re-ranks
     * [seedStrategySuggestion] afterward since [SeedStrategyCandidate.misfitColors] depends on the
     * currently selected colors.
     */
    fun onToggleCardsFlowColor(color: ManaColor) {
        val state = _uiState.value
        if (color in state.lockedColors) return
        val updated = if (color in state.colorIdentity) state.colorIdentity - color else state.colorIdentity + color
        _uiState.update { it.copy(colorIdentity = updated) }
        recomputeSeedStrategySuggestion()
    }

    /** Flow A (cards-first, plan §5 3.2) — re-ranks [SuggestStrategiesForSeedsUseCase] over the
     * current seeds PLUS the commander when one is picked (plan: "Commander = cards-flow variant
     * with a mandatory commander slot as seed #1" — its identity tags count toward strategy fit and
     * seed coherence exactly like any other seed). `null` while nothing is picked yet. Workstream
     * 3.1 also threads the CURRENT [DeckWizardUiState.colorIdentity] pick so candidates carry
     * up-to-date [SeedStrategyCandidate.misfitColors]. */
    private fun recomputeSeedStrategySuggestion() {
        val state = _uiState.value
        val seedsForRanking = listOfNotNull(state.selectedCommander) + state.seedCards
        val suggestion = if (seedsForRanking.isEmpty()) null else suggestStrategiesForSeedsUseCase(seedsForRanking, state.colorIdentity)
        if (suggestion != null && !suggestion.isCoherent) {
            crashReporter.log("deck_wizard_seed_coherence_low")
            crashReporter.setCustomKey("deck_wizard_seed_coherence_score", suggestion.coherenceScore.toString())
            crashReporter.setCustomKey("deck_wizard_seed_count", seedsForRanking.size.toString())
        }
        _uiState.update { it.copy(seedStrategySuggestion = suggestion) }
    }

    /**
     * Deck Engine Unification plan D7 (4.3) — resolves a combo's component card NAMES (from the
     * synergy browser's "Use as seed" hand-off) into actual [Card]s and pre-populates
     * [DeckWizardUiState.seedCards], mirroring [onAddSeed]'s exact dedupe/[MAX_SEED_CARDS] cap so
     * a combo hand-off can never behave differently from a manual pick.
     *
     * Resolution order per name: (1) [collectionSnapshot] first (owned, no network -- most combo
     * pieces the user already has), (2) a [searchCardsUseCase] lookup for names not owned (a combo
     * missing exactly one card is the WHOLE POINT of "almost there" -- that piece is by definition
     * not owned, so skipping network resolution would silently drop it). Best-effort: a name that
     * resolves to nothing (typo-adjacent Spellbook naming drift, or the search itself failing) is
     * silently skipped rather than blocking the rest -- the user still gets whatever DID resolve
     * and can search-add the rest manually.
     */
    private suspend fun resolveComboSeeds(names: List<String>) {
        val ownedByLowerName = collectionSnapshot.associateBy { it.card.name.lowercase() }
        val resolved = mutableListOf<Card>()
        for (name in names) {
            if (resolved.size >= MAX_SEED_CARDS) break
            val owned = ownedByLowerName[name.lowercase()]?.card
            if (owned != null) {
                resolved += owned
                continue
            }
            val found = runCatching {
                when (val res = searchCardsUseCase(name)) {
                    is DataResult.Success -> res.data.cards.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    is DataResult.Error -> null
                }
            }.getOrNull()
            if (found != null) resolved += found
        }
        if (resolved.isEmpty()) return
        val current = _uiState.value.seedCards
        val merged = (current + resolved).distinctBy { it.scryfallId }.take(MAX_SEED_CARDS)
        _uiState.update { it.copy(seedCards = merged) }
        recomputeSeedLockedColors()
        recomputeSeedStrategySuggestion()
    }

    /**
     * Flow A's ranked "Suggested strategies" list tap handler — resolves a [SeedStrategyCandidate]
     * onto the SAME one-slot Direction pick [onSelectDirectionTag]/[onSelectTribeDirection] already
     * write (archetype/theme/tribe are mutually exclusive), so [onGenerate] needs no awareness this
     * pick came from ranking rather than a quick-pick chip. Tapping the CURRENTLY selected candidate
     * again clears the pick (toggle, same UX as every other Direction chip in this screen).
     */
    fun onSelectSeedStrategyCandidate(candidate: SeedStrategyCandidate) {
        val profile = candidate.profile
        val state = _uiState.value
        val hasPick = profile.archetype != null || profile.themes.isNotEmpty() || profile.tribe != null
        val alreadySelected = hasPick &&
            state.selectedArchetype == profile.archetype &&
            state.selectedDirectionTheme == profile.themes.firstOrNull() &&
            state.selectedTribeKey == profile.tribe
        _uiState.update {
            if (alreadySelected) {
                it.copy(selectedArchetype = null, selectedDirectionTheme = null, selectedTribeKey = null, selectedTribeLabel = null)
            } else {
                it.copy(
                    selectedArchetype = profile.archetype,
                    selectedDirectionTheme = profile.themes.firstOrNull(),
                    selectedTribeKey = profile.tribe,
                    selectedTribeLabel = profile.tribe?.let { key -> key.removePrefix("tribe:").replaceFirstChar(Char::uppercase) },
                )
            }
        }
    }

    // ── Entry chooser (Deck Engine Unification plan §5 Phase 3.1) ────────────────

    /** Casual only — Commander never reaches [WizardPhase.ENTRY] (see [WizardEntryFlow]'s KDoc).
     *
     * QA fix (RUN 3b): ENTRY is reachable via back-navigation from DIRECTION after the previously
     * active flow has already populated its own scratch state (e.g. Strategy flow's archetype +
     * colors) -- switching to a DIFFERENT flow resets that scratch state (see
     * [resetDirectionScratchState]'s KDoc) so it can never render as a stale pre-filled pick in the
     * newly chosen flow, or ride silently into the build. Re-tapping the flow the user is ALREADY
     * in is a no-op-ish transition (never wipes state on the screen they haven't left). */
    fun onSelectEntryFlow(flow: WizardEntryFlow) {
        logStep("direction_${flow.name.lowercase()}")
        if (_uiState.value.entryFlow == flow) {
            _uiState.update { it.copy(entryFlow = flow, phase = WizardPhase.DIRECTION) }
            return
        }
        cancelDirectionSearchJobs()
        _uiState.update { it.resetDirectionScratchState().copy(entryFlow = flow, phase = WizardPhase.DIRECTION) }
    }

    // ── Flow B — colors-first (plan §5 3.3) ───────────────────────────────────────

    fun onToggleColorFlowColor(color: ManaColor) {
        _uiState.update { state ->
            val updated = if (color in state.colorIdentity) state.colorIdentity - color else state.colorIdentity + color
            state.copy(
                colorIdentity = updated,
                colorAffinityEntries = if (updated.isEmpty()) emptyList() else ColorStrategyAffinity.forColors(updated),
                selectedColorAffinityEntry = null,
                selectedArchetype = null,
                selectedDirectionTheme = null,
                suggestedSeedCards = emptyList(),
            )
        }
    }

    /** Picks one [ColorStrategyEntry] from the current [DeckWizardUiState.colorAffinityEntries] list
     * (writes the SAME archetype/theme Direction-pick fields as every other flow), then ranks the
     * user's owned collection against the resulting [StrategyProfile] as a suggested-seed
     * check/uncheck list (RC5). Tapping the same entry again clears the pick. */
    fun onSelectColorAffinityEntry(entry: ColorStrategyEntry) {
        val state = _uiState.value
        val alreadySelected = state.selectedColorAffinityEntry == entry
        if (alreadySelected) {
            _uiState.update {
                it.copy(selectedColorAffinityEntry = null, selectedArchetype = null, selectedDirectionTheme = null, suggestedSeedCards = emptyList())
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedColorAffinityEntry = entry,
                selectedArchetype = entry.archetype,
                selectedDirectionTheme = entry.themes.firstOrNull(),
                selectedTribeKey = null,
                selectedTribeLabel = null,
            )
        }
        val suggested = rankOwnedCardsForProfileUseCase(entry.toStrategyProfile(state.colorIdentity), cardSnapshot)
        _uiState.update { it.copy(suggestedSeedCards = suggested) }
    }

    // ── Flow C — strategy-first (plan §5 3.4) ─────────────────────────────────────

    fun onTaxonomyQueryChange(query: String) = _uiState.update { it.copy(taxonomyQuery = query) }

    fun onSelectTaxonomyArchetype(archetype: ArchetypeId) {
        val alreadySelected = _uiState.value.selectedArchetype == archetype
        _uiState.update {
            it.copy(
                selectedArchetype = if (alreadySelected) null else archetype,
                selectedDirectionTheme = null,
                selectedTribeKey = null,
                selectedTribeLabel = null,
                colorComboSuggestions = emptyList(),
                suggestedSeedCards = emptyList(),
                // QA fix (edge-case audit follow-up, RUN 3b): a color-combo pick is DOWNSTREAM of
                // the archetype/theme pick -- it stops being valid the instant the upstream pick
                // changes (select-different or deselect), so it must be invalidated here too.
                // Without this, a combo picked for a since-abandoned archetype/theme rode into the
                // build alongside the newly picked one (e.g. Boros {R,W} colors surviving a switch
                // to Ramp, an archetype whose curated combos are green-leaning).
                colorIdentity = emptySet(),
            )
        }
        if (!alreadySelected) recomputeColorComboSuggestions(archetype = archetype, theme = null)
    }

    fun onSelectTaxonomyTheme(theme: ThemeId) {
        val alreadySelected = _uiState.value.selectedDirectionTheme == theme
        _uiState.update {
            it.copy(
                selectedDirectionTheme = if (alreadySelected) null else theme,
                selectedArchetype = null,
                selectedTribeKey = null,
                selectedTribeLabel = null,
                colorComboSuggestions = emptyList(),
                suggestedSeedCards = emptyList(),
                // Same downstream-invalidation rule as onSelectTaxonomyArchetype -- see its comment.
                colorIdentity = emptySet(),
            )
        }
        if (!alreadySelected) recomputeColorComboSuggestions(archetype = null, theme = theme)
    }

    /** [ColorStrategyAffinity.combosFor] (curated ranking) blended with how strong the user's OWN
     * collection already is in each candidate combo ([CollectionProfile.colorShares]) -- a taxonomy
     * pick favors color combos the user can actually build today, not a purely abstract ranking. */
    private fun recomputeColorComboSuggestions(archetype: ArchetypeId?, theme: ThemeId?) {
        val shareByColor = _uiState.value.collectionProfile?.colorShares?.associate { it.color to it.share }.orEmpty()
        val combos = ColorStrategyAffinity.combosFor(archetype, theme)
            .map { (colors, weight) ->
                val avgShare = if (colors.isEmpty()) 0f else colors.map { shareByColor[it] ?: 0f }.average().toFloat()
                ColorComboSuggestion(colors, weight * (0.5f + avgShare))
            }
            .sortedByDescending { it.score }
        _uiState.update { it.copy(colorComboSuggestions = combos) }
    }

    /** Picks one [ColorComboSuggestion] (writes [DeckWizardUiState.colorIdentity]), then ranks
     * suggested seeds against the now-complete [StrategyProfile] (archetype/theme + these colors),
     * same as Flow B. Tapping the same combo again clears the color pick. */
    fun onSelectColorCombo(combo: ColorComboSuggestion) {
        val state = _uiState.value
        val alreadySelected = state.colorIdentity == combo.colors
        val colors = if (alreadySelected) emptySet() else combo.colors
        _uiState.update { it.copy(colorIdentity = colors) }
        if (alreadySelected) {
            _uiState.update { it.copy(suggestedSeedCards = emptyList()) }
            return
        }
        val profile = StrategyProfile(archetype = state.selectedArchetype, themes = listOfNotNull(state.selectedDirectionTheme), colors = colors)
        val suggested = rankOwnedCardsForProfileUseCase(profile, cardSnapshot)
        _uiState.update { it.copy(suggestedSeedCards = suggested) }
    }

    /** QA fix (RUN 3b follow-up) -- see the call site in `init` for the race this closes. Re-derives
     * the SAME [StrategyProfile] [onSelectColorAffinityEntry] (Flow B)/[onSelectColorCombo] (Flow C)
     * would have ranked against, from whichever pick is CURRENTLY active, and re-ranks
     * [collectionSnapshot] now that it has actually loaded. A no-op when nothing is picked yet (the
     * common case -- this only ever does real work when the user picked during the loading window). */
    private fun recomputeActiveSuggestedSeeds() {
        val state = _uiState.value
        val affinityEntry = state.selectedColorAffinityEntry
        val profile = when {
            affinityEntry != null -> affinityEntry.toStrategyProfile(state.colorIdentity)
            state.entryFlow == WizardEntryFlow.STRATEGY &&
                state.colorIdentity.isNotEmpty() &&
                (state.selectedArchetype != null || state.selectedDirectionTheme != null) ->
                StrategyProfile(
                    archetype = state.selectedArchetype,
                    themes = listOfNotNull(state.selectedDirectionTheme),
                    colors = state.colorIdentity,
                )
            else -> null
        } ?: return
        val suggested = rankOwnedCardsForProfileUseCase(profile, cardSnapshot)
        _uiState.update { it.copy(suggestedSeedCards = suggested) }
    }

    // ── Flow B/C shared — suggested-seed check/uncheck (plan §5 3.3/3.4) ─────────

    /** Toggles a [DeckWizardUiState.suggestedSeedCards] entry into/out of [DeckWizardUiState
     * .seedCards] via the SAME [onAddSeed]/[onRemoveSeed] handlers Flow A's seed picker uses (RC5 —
     * one seed-selection mechanism, three ways to arrive at it). */
    fun onToggleSuggestedSeed(card: Card) {
        val isSeeded = _uiState.value.seedCards.any { it.scryfallId == card.scryfallId }
        if (isSeeded) onRemoveSeed(card) else onAddSeed(card)
    }

    // ── Step 3 — Identity ─────────────────────────────────────────────────────

    /** No-op for Commander (colors are read-only, derived from the commander). */
    fun onToggleColor(color: ManaColor) {
        val state = _uiState.value
        if (state.selectedFormat?.isCommanderFormat == true) return
        _uiState.update {
            it.copy(colorIdentity = if (color in it.colorIdentity) it.colorIdentity - color else it.colorIdentity + color)
        }
    }

    fun onSelectThemeHint(theme: String?) =
        _uiState.update { it.copy(selectedThemeHint = if (it.selectedThemeHint == theme) null else theme) }

    // ── Step 4 — Review ───────────────────────────────────────────────────────

    fun onToggleFillLands() = _uiState.update { it.copy(fillLands = !it.fillLands) }

    /** Deck Engine Unification plan (§5 Phase 3.5) — no-op when [DeckWizardUiState
     * .communityEngineAvailable] is false (the UI never renders the toggle in that case either; this
     * guard is the actual source of truth, mirrors [onSelectFormat]'s "never trust the UI-only
     * disabled state" precedent). */
    fun onToggleUseCommunityData() {
        if (!_uiState.value.communityEngineAvailable) return
        _uiState.update { it.copy(useCommunityData = !it.useCommunityData) }
    }

    // ── Navigation between phases ────────────────────────────────────────────

    /**
     * Deck Wizard & Engine Rework plan, Workstream 2 — Commander now routes to the NEW
     * [WizardPhase.COMMANDER_PICK] step (replacing its old `DIRECTION` entry, see [WizardPhase]'s
     * KDoc); it still forces [WizardEntryFlow.CARDS] and skips [WizardPhase.ENTRY] entirely (the
     * commander IS the mandatory first pick; there is nothing for the chooser to offer). Casual
     * routes through the entry chooser instead — byte-identical to before this workstream.
     */
    fun onNextFromFormat() {
        val format = _uiState.value.selectedFormat ?: return
        if (format.isCommanderFormat) {
            logStep("commander_pick")
            _uiState.update { it.copy(phase = WizardPhase.COMMANDER_PICK, entryFlow = WizardEntryFlow.CARDS) }
        } else {
            logStep("entry")
            _uiState.update { it.copy(phase = WizardPhase.ENTRY) }
        }
    }

    /**
     * Deck Wizard & Engine Rework plan, Workstream 3 — as of this workstream, EVERY Casual flow
     * (A/B/C) requires both a real strategy pick (archetype/theme/tribe — D-B's "no GENERIC escape"
     * rule, generalized from Flow A alone to all three flows) AND a non-empty color set before
     * advancing, and lands on the SAME shared [WizardPhase.MANUAL_ADDS] step Commander uses (never
     * [WizardPhase.IDENTITY], which is now unreachable dead code for Casual — see [WizardPhase]'s own
     * KDoc precedent for Commander's DIRECTION/IDENTITY). This mirrors
     * [onNextFromStrategy]'s skeleton-resolution + phase transition exactly.
     */
    fun onNextFromDirection() {
        val state = _uiState.value
        if (state.selectedFormat?.isCommanderFormat == true && state.selectedCommander == null) {
            // Unreachable post-Workstream-2 (Commander never visits DIRECTION anymore) -- defensive
            // dead branch only, kept for the same reason WS2 left its own dead branches in place.
            crashReporter.log("deck_wizard_step_direction_blocked_no_commander")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_commander_required)))
            }
            return
        }

        val hasStrategyPick = state.selectedArchetype != null || state.selectedDirectionTheme != null || state.selectedTribeKey != null

        // Deck Engine Unification plan (§5 Phase 3.3/3.4) -- Flow B/C's own sticky-button `enabled`
        // gate mirrors this (design review RUN 3b), but the VM guard is the actual source of truth
        // (same "never trust the UI-only disabled state" precedent as the Commander check above and
        // onToggleUseCommunityData's KDoc).
        if (state.entryFlow == WizardEntryFlow.COLORS) {
            if (state.colorIdentity.isEmpty()) {
                crashReporter.log("deck_wizard_step_direction_blocked_no_colors")
                viewModelScope.launch {
                    _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_colors_required)))
                }
                return
            }
            // Workstream 3 -- unifies Flow B with Flow A/C: colors alone aren't a real plan yet.
            if (!hasStrategyPick) {
                crashReporter.log("deck_wizard_step_direction_blocked_no_strategy")
                viewModelScope.launch {
                    _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_strategy_required)))
                }
                return
            }
        }
        if (state.entryFlow == WizardEntryFlow.STRATEGY) {
            if (!hasStrategyPick) {
                crashReporter.log("deck_wizard_step_direction_blocked_no_strategy")
                viewModelScope.launch {
                    _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_strategy_required)))
                }
                return
            }
            // Edge-case audit follow-up (android-edge-case-tester, RUN 3b QA fix): Flow C's own
            // design is "pick strategy -> pick matching colors" -- BOTH steps expected, not just the
            // first. An archetype/theme pick with zero colors chosen (e.g. tapping "Next" right
            // after onSelectTaxonomyArchetype resets colorIdentity to emptySet(), without ever
            // tapping a combo chip) would otherwise sail through to a fully colorless build under a
            // color-hungry archetype (RAMP, etc) -- spec.colorIdentity = emptySet() is the LEGITIMATE
            // "Colorless" filter state per analyzeCollection's D9 comment in
            // BuildDeckFromTemplateUseCase, so this would silently construct a deck that's
            // structurally incoherent with the picked archetype instead of erroring or nudging the
            // user. Reuses the SAME colors-required toast the COLORS-flow guard above uses -- the
            // underlying problem (no colors picked) is identical.
            if (state.colorIdentity.isEmpty()) {
                crashReporter.log("deck_wizard_step_direction_blocked_no_colors_for_strategy")
                viewModelScope.launch {
                    _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_colors_required)))
                }
                return
            }
        }
        // Workstream 3.1 (D-B, generalized to Flow A): a seed-first build now ALSO requires a real
        // strategy pick + a color set before advancing -- no GENERIC escape for Casual (Commander
        // keeps its own short escape hatch in the STRATEGY step, see onNextFromStrategy's KDoc).
        // colorIdentity is populated well before this point via recomputeSeedLockedColors (every seed
        // add/remove) plus any manual onToggleCardsFlowColor pick -- there is no separate "prefill at
        // Next time" step to run anymore.
        if (state.entryFlow == WizardEntryFlow.CARDS && state.selectedFormat?.isCommanderFormat != true) {
            if (!hasStrategyPick) {
                crashReporter.log("deck_wizard_step_direction_blocked_no_strategy_for_cards")
                viewModelScope.launch {
                    _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_strategy_required)))
                }
                return
            }
            if (state.colorIdentity.isEmpty()) {
                crashReporter.log("deck_wizard_step_direction_blocked_no_colors_for_cards")
                viewModelScope.launch {
                    _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_colors_required)))
                }
                return
            }
        }

        // Workstream 3 -- every Casual flow now resolves the SAME shared MANUAL_ADDS skeleton
        // Commander's STRATEGY step resolves (mirrors onNextFromStrategy exactly) and lands on that
        // step, never IDENTITY/REVIEW directly.
        // Deck Analysis Engine v3: ArchetypeId.GENERIC no longer exists -- an unpinned selection is
        // `null` directly (see onNextFromStrategy's own compat note for the same substitution).
        val direction = state.selectedArchetype
        val directionThemes = listOfNotNull(state.selectedDirectionTheme)
        // Fix 5 (edge-case audit, 2026-07-28): same GENERIC-with-no-themes gate as
        // onNextFromStrategy -- mirrors BuildDeckFromTemplateUseCase.resolveArchetypeSkeleton so
        // this UI-only preview never shows a role chip the real build wouldn't apply. In practice
        // Casual's own mandatory-strategy gate (D-B, just above / onNextFromDirection's earlier
        // guards) already requires a real pick before reaching this line for every entry flow that
        // enforces it -- this gate is defense-in-depth for any current/future path that reaches here
        // without a pick.
        val skeleton = if (direction == null && directionThemes.isEmpty()) {
            null
        } else {
            ArchetypeSkeletonResolver.resolveWithColor(
                format = ArchetypeFormat.SIXTY,
                archetype = direction,
                themes = directionThemes,
                identity = state.colorIdentity,
            )
        }
        logStep("manual_adds")
        _uiState.update { it.copy(phase = WizardPhase.MANUAL_ADDS, manualAddsSkeleton = skeleton) }
    }

    fun onNextFromIdentity() {
        logStep("review")
        _uiState.update { it.copy(phase = WizardPhase.REVIEW) }
    }

    /** Back navigation between steps 1-4 (+ the new [WizardPhase.ENTRY], plan §5 Phase 3.1). Returns
     * `true` when the caller should pop the whole wizard screen instead (already at step 1, or
     * Result — nothing left for the VM to unwind). */
    fun onBackPressed(): Boolean {
        val state = _uiState.value
        return when (state.phase) {
            WizardPhase.FORMAT -> true
            WizardPhase.ENTRY -> { _uiState.update { it.copy(phase = WizardPhase.FORMAT) }; false }
            // Deck Wizard & Engine Rework plan, Workstream 2 -- the new Commander-only sequence.
            WizardPhase.COMMANDER_PICK -> { _uiState.update { it.copy(phase = WizardPhase.FORMAT) }; false }
            WizardPhase.STRATEGY -> { _uiState.update { it.copy(phase = WizardPhase.COMMANDER_PICK) }; false }
            // Workstream 3 -- MANUAL_ADDS is now shared by BOTH Commander (from STRATEGY) and every
            // Casual flow (from DIRECTION, see onNextFromDirection). Format-aware back target.
            WizardPhase.MANUAL_ADDS -> {
                val target = if (state.selectedFormat?.isCommanderFormat == true) WizardPhase.STRATEGY else WizardPhase.DIRECTION
                _uiState.update { it.copy(phase = target) }
                false
            }
            // Commander never reaches DIRECTION/IDENTITY anymore (it routes through COMMANDER_PICK/
            // STRATEGY/MANUAL_ADDS above) -- this branch is Casual-only now, kept byte-identical
            // (the `selectedFormat == COMMANDER` arm is defensive dead code, harmless to leave).
            WizardPhase.DIRECTION -> {
                val target = if (state.selectedFormat?.isCommanderFormat == true) WizardPhase.FORMAT else WizardPhase.ENTRY
                _uiState.update { it.copy(phase = target) }
                false
            }
            // Workstream 3 -- IDENTITY is now unreachable dead code for EVERY flow (Commander since
            // WS2, Casual since this workstream: onNextFromDirection never routes here anymore).
            // Kept, not deleted, matching this file's established "defensive dead code, harmless to
            // leave" precedent (see WizardPhase's own KDoc for Commander's DIRECTION/IDENTITY).
            WizardPhase.IDENTITY -> { _uiState.update { it.copy(phase = WizardPhase.DIRECTION) }; false }
            // Workstream 3 -- EVERY flow (Commander AND all three Casual flows) now reaches REVIEW
            // from MANUAL_ADDS; the old per-flow branching (Commander vs. Flow A's IDENTITY vs.
            // Flow B/C's DIRECTION) collapses to one target.
            WizardPhase.REVIEW -> { _uiState.update { it.copy(phase = WizardPhase.MANUAL_ADDS) }; false }
            WizardPhase.GENERATING -> { onCancelGeneration(); false }
            WizardPhase.RESULT -> true
        }
    }

    // ── Generation ────────────────────────────────────────────────────────────

    fun onGenerate() {
        val state = _uiState.value
        // Re-entrancy guard: the state write to GENERATING below is synchronous, so a second
        // invocation from a double-tap (before Compose recomposes the Review CTA away) reads the
        // already-updated phase here and returns instead of launching a second build / clobbering
        // `generateJob`. See feedback_deck_wizard_reentrancy_and_orphan_cleanup memory.
        if (state.phase != WizardPhase.REVIEW || state.selectedFormat == null) return
        logStep("generating")
        val format = state.selectedFormat

        _uiState.update {
            it.copy(phase = WizardPhase.GENERATING, buildStage = null, completedStages = emptyList(), buildError = null)
        }
        generateJob = viewModelScope.launch {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("deck_wizard_generate_started")
            crashlytics.setCustomKey("deck_wizard_format", format.name)
            crashlytics.setCustomKey("deck_wizard_seed_count", state.seedCards.size)
            // Wizard Quality Campaign telemetry: tracks which Direction-step chip (archetype /
            // theme / tribe / none) actually drove this build, to confirm the taxonomy chip slots
            // land real hint data (see the "dead chip" fix in this campaign).
            // WS2: Commander's theme pick lives in selectedStrategyThemes, not
            // selectedDirectionTheme (Casual-only) -- checked too so a theme-only Commander pick
            // (no archetype/tribe) is never mis-reported as "none".
            val directionType = when {
                state.selectedArchetype != null -> "archetype"
                state.selectedDirectionTheme != null || state.selectedStrategyThemes.isNotEmpty() -> "theme"
                state.selectedTribeKey != null -> "tribe"
                else -> "none"
            }
            crashlytics.setCustomKey("deck_wizard_direction_type", directionType)
            // Deck Engine Unification plan (§5 Phase 3) telemetry: which entry flow actually built
            // this deck, and whether the community-source toggle was on (Motor B live-wired).
            crashlytics.setCustomKey("deck_wizard_entry_flow", state.entryFlow.name)
            crashlytics.setCustomKey("deck_wizard_use_community_data", state.useCommunityData)

            val colorIdentity = if (format.isCommanderFormat) {
                state.selectedCommander?.colorIdentity?.toManaColorSet() ?: state.colorIdentity
            } else {
                state.colorIdentity
            }
            // Deck Wizard & Engine Rework plan, Workstream 2.4 -- Commander's strategyProfile now
            // comes from the STRATEGY step's OWN theme field (up to 2, via the shared picker),
            // completely independent of Casual's Direction-theme + Identity-EDHREC-theme-hint pair
            // below (which stays byte-identical to before this workstream -- Commander never visits
            // either of those steps anymore, so `state.selectedDirectionTheme`/`selectedThemeHint`
            // are always their initial empty/null values for a Commander build here).
            val strategyProfile = if (format.isCommanderFormat) {
                StrategyProfile(
                    archetype = state.selectedArchetype,
                    themes = state.selectedStrategyThemes,
                    tribe = state.selectedTribeKey,
                    colors = colorIdentity,
                )
            } else {
                // Deck Engine Unification (D2): the Direction step's own theme pick and the Identity
                // step's SEPARATE EDHREC theme picker are two independent slots (mirrors the
                // pre-unification strategyHint/tagHint + themeHint split) -- both fold into the same
                // StrategyProfile.themes list, capped at 2 (ArchetypeSkeletonResolver's own cap).
                val identityTheme = ThemeId.fromDisplayName(state.selectedThemeHint)
                StrategyProfile(
                    archetype = state.selectedArchetype,
                    themes = listOfNotNull(state.selectedDirectionTheme, identityTheme).distinct().take(2),
                    tribe = state.selectedTribeKey,
                    colors = colorIdentity,
                )
            }
            val spec = DeckWizardSpec(
                format = format,
                commander = state.selectedCommander,
                strategyProfile = strategyProfile,
                colorIdentity = colorIdentity,
                seeds = state.seedCards,
                fillLands = state.fillLands,
                useCommunityData = state.useCommunityData,
                // Deck Wizard & Engine Rework plan, Workstream 4.1: the SAME session-level toggle
                // that already gates the wizard's own search call sites now also gates
                // BuildDeckFromTemplateUseCase's Scryfall backstop fill phase.
                includeOutsideCollection = state.includeOutsideCollection,
            )

            val outcome: Pair<TemplateBuildResult?, String?> = runCatching {
                var finalResult: TemplateBuildResult? = null
                var failure: String? = null
                buildDeckFromTemplateUseCase(spec, collectionSnapshot).collect { progress ->
                    when (progress) {
                        is TemplateBuildProgress.Stage -> _uiState.update { s ->
                            s.copy(
                                completedStages = s.buildStage?.let { s.completedStages + it } ?: s.completedStages,
                                buildStage = progress.stage,
                            )
                        }
                        is TemplateBuildProgress.Complete -> finalResult = progress.result
                        is TemplateBuildProgress.Failed -> failure = progress.message
                    }
                }
                finalResult to failure
            }.getOrElse { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                logFailure("deck_wizard_generate_crashed", t)
                null to appContext.getString(R.string.deck_wizard_build_error)
            }

            val (result, failureMessage) = outcome
            if (result == null) {
                crashlytics.log("deck_wizard_generate_failed")
                _uiState.update { it.copy(buildError = failureMessage ?: appContext.getString(R.string.deck_wizard_build_error)) }
                return@launch
            }

            val writeOutcome = runCatching { writeResultIntoNewDeck(spec, result) }
                .getOrElse { t ->
                    logFailure("deck_wizard_write_failed", t)
                    _uiState.update { it.copy(buildError = appContext.getString(R.string.deck_wizard_build_error)) }
                    return@launch
                }

            crashlytics.log("deck_wizard_generate_succeeded")
            crashlytics.setCustomKey("deck_wizard_template_source", result.templateSource.name)
            _uiState.update {
                it.copy(phase = WizardPhase.RESULT, buildResult = result, createdDeckId = writeOutcome)
            }
        }
    }

    /** Creates the fresh deck, writes commander/cover (Commander only) AND the commander's
     * qty-1 mainboard entry (BUG-1 fix, Deck Engine Unification plan §0.1 — see below), every
     * [TemplateBuildResult.deckCards] entry, and the archetype/theme/tribe override + strategy
     * lock — mirrors [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel
     * .generateFromSeeds]'s write-through convention (one repo call per card copy; a mid-write
     * cancellation leaves a partial but valid deck).
     *
     * BUG-1: the wizard previously wrote ONLY [com.mmg.manahub.core.model.Deck.commanderCardId] --
     * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.rebuildUiState] resolves the
     * commander from the deck's ENTRIES (`allEntries.find { it.scryfallId == commanderId }`), and
     * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.setCommander] (the convention
     * owner) ALSO inserts a qty-1 mainboard row when one is absent. Without that row the commander
     * was invisible in the built deck. [BuildDeckFromTemplateUseCase.mainboardTargetSize] already
     * reserves `targetDeckSize - 1` for the commander, so this row brings the total to exactly 100.
     *
     * D4 (hard no-cut guarantee): every placed card — commander included — writes
     * `source = DeckCardSource.WIZARD`, and the deck is marked `strategyLocked = true`. Both gates
     * are CONSUMED starting Phase 2 (RUN 2); this write only persists the provenance/lock.
     */
    private suspend fun writeResultIntoNewDeck(spec: DeckWizardSpec, result: TemplateBuildResult): String {
        val name = wizardDeckName(spec)
        val deckId = deckRepository.createDeck(name = name, description = "Draft", format = spec.format.name)
        pendingDeckId = deckId

        val commander = spec.commander
        if (spec.format.isCommanderFormat && commander != null) {
            val created = deckRepository.observeDeckWithCards(deckId).first()?.deck
            if (created != null) {
                deckRepository.updateDeck(
                    created.copy(commanderCardId = commander.scryfallId, coverCardId = commander.scryfallId)
                )
            }
            // BUG-1 fix: insert the commander as a qty-1 mainboard entry — see this method's KDoc.
            deckRepository.addCardToDeck(deckId, commander.scryfallId, 1, false, DeckCardSource.WIZARD)
        }

        result.deckCards.forEach { entry ->
            deckRepository.addCardToDeck(deckId, entry.card.scryfallId, entry.quantity, entry.isSideboard, DeckCardSource.WIZARD)
        }
        deckRepository.updateArchetypeOverride(deckId, result.archetypeOverride, result.themesOverride)
        deckRepository.updateTribeOverride(deckId, spec.strategyProfile.tribe)
        deckRepository.updateStrategyLocked(deckId, true)
        return deckId
    }

    private fun wizardDeckName(spec: DeckWizardSpec): String {
        // Defense in depth (RUN 3b): gate on format even though onSelectFormat's reset (above)
        // already makes a non-null commander on a non-Commander spec unreachable via normal UI
        // flow -- this function shouldn't silently trust an out-of-band-constructed spec.
        val commander = spec.commander.takeIf { spec.format.isCommanderFormat }
        val profile = spec.strategyProfile
        val archetypeName = profile.archetype?.displayName
        val themeName = profile.themes.firstOrNull()?.displayName
        return when {
            commander != null -> commander.name
            archetypeName != null ->
                String.format(appContext.getString(R.string.deck_wizard_name_suffix_deck), archetypeName)
            themeName != null ->
                String.format(appContext.getString(R.string.deck_wizard_name_suffix_deck), themeName)
            else -> String.format(appContext.getString(R.string.deck_wizard_name_default), spec.format.displayName)
        }
    }

    /** Best-effort deletes a dangling partially-created deck (a build that got as far as
     * [writeResultIntoNewDeck]'s `createDeck()` call but failed/was cancelled before finishing).
     * Nulls [pendingDeckId] FIRST so a concurrent second call is a no-op, then fires the delete on
     * [viewModelScope] (fire-and-forget — neither caller needs to await it).
     *
     * MUST be called from both [onCancelGeneration] AND [onRetryGeneration] — a partial-write
     * failure leaves [pendingDeckId] set (see [writeResultIntoNewDeck]'s KDoc), and "Retry" is the
     * more natural CTA on the error screen than "Back"/Cancel. Before this fix, Retry skipped
     * cleanup entirely: each failure-then-retry cycle created a brand-new deck and overwrote
     * [pendingDeckId], permanently orphaning the previous partial draft in the user's deck list. */
    private fun cleanupPendingDeck() {
        val orphanId = pendingDeckId ?: return
        pendingDeckId = null
        viewModelScope.launch {
            runCatching { deckRepository.deleteDeck(orphanId) }
                .onFailure { logFailure("deck_wizard_cancel_cleanup_failed", it) }
        }
    }

    /** Cancels an in-flight build. Best-effort deletes a partially-created deck so backing out of
     * generation never leaves an orphaned draft (the wizard's equivalent of Deck Studio's
     * discard-if-empty contract — simpler here since the wizard ALWAYS starts a fresh deck). */
    fun onCancelGeneration() {
        generateJob?.cancel()
        cleanupPendingDeck()
        _uiState.update { it.copy(phase = WizardPhase.REVIEW, buildStage = null, completedStages = emptyList(), buildError = null) }
    }

    /** Retries generation after a [DeckWizardUiState.buildError] without re-walking the steps.
     * Cleans up any deck orphaned by the failed attempt FIRST (see [cleanupPendingDeck]) so a
     * failure-then-retry cycle never leaves a dangling partial draft behind. */
    fun onRetryGeneration() {
        cleanupPendingDeck()
        _uiState.update { it.copy(buildError = null, phase = WizardPhase.REVIEW) }
    }

    // ── Result ────────────────────────────────────────────────────────────────

    /** In-flight guard for [onAddCommunitySuggestion] -- a rapid double-tap on the same suggestion's
     * "Add" icon (no disabled/in-flight UI state exists yet) would otherwise fire `addCardToDeck`
     * twice before the first call's success removes the row, silently doubling the added quantity.
     * Mirrors this codebase's established atomic double-tap-guard convention (see
     * `feedback_trades_negotiation_atomic_events` memory): a plain mutable set keyed by the card id,
     * `add()` returns `false` when already present. */
    private val pendingSuggestionAdds = mutableSetOf<String>()

    /** Writes one community-picked (unowned, D8) suggestion into the created deck, then removes it
     * from the view-only list so it is never shown as "addable" twice. */
    fun onAddCommunitySuggestion(categoryId: String, suggestion: TemplateCardSuggestion) {
        val deckId = _uiState.value.createdDeckId ?: return
        val cardId = suggestion.card.scryfallId
        if (!pendingSuggestionAdds.add(cardId)) return
        viewModelScope.launch {
            runCatching {
                // D4: still part of the guided wizard build (Result screen, pre-handoff to Studio)
                // -- WIZARD provenance, same as every other card this flow placed.
                deckRepository.addCardToDeck(deckId, cardId, suggestion.suggestedCopies, false, DeckCardSource.WIZARD)
            }.onSuccess {
                _uiState.update { s ->
                    val result = s.buildResult ?: return@update s
                    val updatedCommunity = result.communitySuggestions.mapNotNull { cat ->
                        if (cat.category.id != categoryId) return@mapNotNull cat
                        val remaining = cat.suggestions.filterNot { it.card.scryfallId == cardId }
                        if (remaining.isEmpty()) null else cat.copy(suggestions = remaining)
                    }
                    s.copy(buildResult = result.copy(communitySuggestions = updatedCommunity))
                }
                _events.send(
                    DeckWizardEvent.ShowToast(
                        String.format(appContext.getString(R.string.deck_wizard_suggestion_added), suggestion.card.name)
                    )
                )
            }.onFailure { t -> logFailure("deck_wizard_add_community_suggestion_failed", t) }
            pendingSuggestionAdds.remove(cardId)
        }
    }

    fun onOpenDeckStudio() {
        val deckId = _uiState.value.createdDeckId ?: return
        viewModelScope.launch { _events.send(DeckWizardEvent.OpenDeckStudio(deckId)) }
    }

    private fun logFailure(tag: String, t: Throwable) {
        crashReporter.log(tag)
        crashReporter.recordException(RuntimeException("[DeckWizard] $tag", t))
    }

    /** Breadcrumb for the step being ENTERED (not exited) -- tells us where users progress to, and
     * by omission, where they stop. Plan-mandated `deck_wizard_step_*` events for this multi-step,
     * abandonable flow (see the class KDoc). */
    private fun logStep(step: String) {
        crashReporter.log("deck_wizard_step_$step")
    }

    private companion object {
        const val SEARCH_MIN_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MAX_SEED_CARDS = 8
        const val TRIBE_PICKER_CANDIDATE_LIMIT = 8
        const val PLAN_ANALYSIS_DEBOUNCE_MS = 300L
    }
}

/**
 * Deck Wizard Commander v3 plan (Phase 5, 5.1) — a cheap, self-contained reproduction of "which
 * [CardSection]s would this owned candidate count toward", NOT a re-exposure of
 * [com.mmg.manahub.feature.decks.domain.template.BuildCommanderDeckUseCase]'s own private
 * candidate-pool machinery (that carries pip/curve/power scoring this hint has no use for — same
 * precedent as Phase 4's `RecommendCommanderStrategiesUseCase` rejecting that same shortcut for its
 * own owned-role-coverage signal, see the progress tracker's Run 6 log).
 *
 * [sections] is every [com.mmg.manahub.feature.decks.domain.engine.PillarResult.sections] for the
 * CURRENT [DeckAnalysis] (PLAN_ROLES/MANA_BASE/
 * CURVE/SYNERGY combined — LEGALITY's `legal`/`illegal` never match anything below). [ownedCards]
 * is the full collection snapshot; [excludeIds] removes the commander and every already-manually-
 * added card (no point telling the user "1 in your collection" for a card they already added).
 * [identity]/[format] gate legality the SAME way [DeckWizardViewModel.isCommanderManualAddValid]
 * does for a real add, so this hint never counts a card the user could not actually add.
 *
 * A candidate can match MULTIPLE section ids (e.g. a 2-mana token generator counts toward
 * `role:token_generator`, `mv:2`, and `fingerprint:tokens` all at once) — every match increments
 * its own counter independently, mirroring how [CardSection.contributions] already lets one real
 * card appear in more than one section. A section id with no matching signal below (`offplan`/
 * `standalone`/`interaction`/`legal`/`illegal`, or a `role:*`/`fingerprint:*` id this candidate pool
 * genuinely has zero owned support for) is simply ABSENT from the result map, never present at 0.
 */
internal fun computeOwnedAvailabilityBySection(
    sections: List<CardSection>,
    ownedCards: List<Card>,
    excludeIds: Set<String>,
    identity: Set<ManaColor>,
    format: DeckFormat,
): Map<String, Int> {
    val sectionIds = sections.map { it.id }.toSet()
    val identitySymbols = identity.map { it.symbol }.toSet()
    val candidates = ownedCards
        .asSequence()
        .filter { it.scryfallId !in excludeIds }
        .filter { card -> card.colorIdentity.all { it in identitySymbols } }
        .filter { card -> if (format == DeckFormat.COMMANDER) card.legalityCommander == "legal" else card.legalityCommander != "banned" }
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
        matched.forEach { id -> counts[id] = (counts[id] ?: 0) + 1 }
    }
    return counts
}

private fun List<String>.toManaColorSet(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
