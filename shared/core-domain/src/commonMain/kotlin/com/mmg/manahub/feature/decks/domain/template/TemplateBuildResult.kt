package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleKey

/** The staged progress [BuildDeckFromTemplateUseCase] emits (plan §3.3). [TOP_UP_FROM_COLLECTION]
 * (Wizard Quality Campaign B2b) runs after [RESOLVING_GAPS] and before [FILLING_LANDS]: it tops the
 * mainboard up to the format's target size from the owned pool regardless of category, closing the
 * gap a category-only fill can leave (the 2026-07-18 "Casual build returned 35 cards instead of 60"
 * bug). */
enum class BuildStage {
    VALIDATING, ANALYZING_COLLECTION, FETCHING_COMMUNITY, MAPPING_CATEGORIES,
    FILLING_FROM_COLLECTION, RESOLVING_GAPS, TOP_UP_FROM_COLLECTION, FILLING_LANDS, DONE,
}

/** One [BuildDeckFromTemplateUseCase] progress event -- a [Stage] label, a [Failed] validation/
 * fetch abort, or the final [Complete] result. */
sealed class TemplateBuildProgress {
    data class Stage(val stage: BuildStage) : TemplateBuildProgress()
    data class Complete(val result: TemplateBuildResult) : TemplateBuildProgress()
    data class Failed(val stage: BuildStage, val message: String) : TemplateBuildProgress()
}

/** One unowned card the template wants for a category -- view-only (D8): never auto-added, the UI
 * offers an explicit per-card "Add" button. */
data class TemplateCardSuggestion(
    val card: Card,
    val weight: Float,
    val suggestedCopies: Int = 1,
)

/** Unowned suggestions for one category -- ALWAYS structurally separate from [TemplateBuildResult
 * .deckCards] (D3: collection and community suggestions are never mixed in the same list). */
data class CategorySuggestions(
    val category: SuggestionCategory,
    val suggestions: List<TemplateCardSuggestion>,
)

/** How much of a category's target the fill stage actually reached -- feeds both the result UI and
 * (via [TemplateBuildResult.archetypeOverride]/[TemplateBuildResult.themesOverride]) keeps the Deck
 * Doctor coherent with what the builder just did. */
data class CategoryFill(
    val category: SuggestionCategory,
    val filled: Int,
    val target: Int,
)

/**
 * Deck Engine Unification plan (D3): a structured, honest declaration of "the build could not fill
 * this many slots from the owned collection above [com.mmg.manahub.feature.decks.domain.engine.CATEGORY_FILL_FIT_FLOOR]"
 * -- REPLACES the old scalar [TemplateBuildResult] `shortfall: Int` (Wizard Quality Campaign B2b).
 * The wizard Result screen and Deck Studio's Suggestions tab render the SAME shape ("missing 3 Ramp
 * in {G}" / "missing 5 Other").
 *
 * @property categoryId the [TemplateCategory.id] this gap was attributed to, or [OTHER_GAP_CATEGORY_ID]
 *   for the reconciliation residual (see [BuildDeckFromTemplateUseCase]'s gap-building KDoc: the sum
 *   of every [DeckGap.missingCount] across the list is ALWAYS EXACTLY the true numeric shortfall --
 *   any slack the per-category attribution can't explain lands in one catch-all "Other" bucket rather
 *   than silently under/over-reporting).
 * @property colors the deck's own non-colorless color identity -- a coarse, honest simplification
 *   (per-category color-weighted attribution is future work): "missing N {label} in {colors}" reads
 *   the deck's overall colors, not a per-card color breakdown.
 * @property missingCount always > 0 (a satisfied category never appears in [TemplateBuildResult.gaps]).
 */
data class DeckGap(
    val categoryId: String,
    val categoryLabel: String,
    val colors: Set<ManaColor>,
    val missingCount: Int,
)

/** [DeckGap.categoryId] for the reconciliation residual bucket -- see [DeckGap]'s KDoc. */
const val OTHER_GAP_CATEGORY_ID = "other"

/** [BuildDeckFromTemplateUseCase]'s terminal result (plan §3.3 step 8). */
data class TemplateBuildResult(
    /** Owned cards written to the live draft -- seeds first, then collection fills, then
     * materialized basics. Never mixed with [communitySuggestions]. */
    val deckCards: List<DeckEntry>,
    val communitySuggestions: List<CategorySuggestions>,
    val report: List<CategoryFill>,
    val templateSource: TemplateSource,
    val archetypeInfo: DeckTemplateArchetypeInfo,
    /** Raw `ArchetypeId.name`, or `null` for no macro pin -- write straight onto
     * `Deck.archetypeOverride` (CLAUDE.md: never `.valueOf()`, always the raw enum-name string;
     * mirrors `DeckDoctorOrchestrator`'s own `archetypeId?.name` precedent) so
     * `EvaluateDeckUseCase` evaluates against the SAME skeleton this build just filled against
     * (plan §3.3 "Builder<->Doctor coherence"). Deck Analysis Engine v3 removed
     * `ArchetypeId.GENERIC` -- `null` is the new "no macro pin" value. */
    val archetypeOverride: String?,
    val themesOverride: List<String>,
    /** D9: Casual/60-card recommends <=2 colors; true when the wizard's chosen color count is 3+. */
    val colorConsistencyWarning: Boolean,
    /** One-sentence strategy line (`SeedStrategy.description`) surfaced on the result screen. */
    val gamePlan: String?,
    /**
     * Deck Engine Unification plan (D3) -- REPLACES the old scalar `shortfall: Int` (Wizard Quality
     * Campaign B2b). Empty means the mainboard reached the format's full target size (lands
     * included). A non-empty list is an HONEST declaration that the owned collection had nothing
     * left clearing [com.mmg.manahub.feature.decks.domain.engine.CATEGORY_FILL_FIT_FLOOR] -- the build NEVER places
     * a weak filler card just to hit the format's size; it reports the gap instead. See [DeckGap]'s
     * KDoc for the exact reconciliation guarantee (`gaps.sumOf { it.missingCount }` is always the
     * true numeric shortfall).
     */
    val gaps: List<DeckGap> = emptyList(),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard Commander v3 plan, Phase 1.2 — Commander-path contracts. Neither type below is
//  wired into any use case yet: `BuildCommanderDeckUseCase` (Phase 2) is the first real producer of
//  a [WizardBuildResult]; [CommanderBuildStage] is not yet emitted by any build loop. Pure type
//  additions, zero behavior change.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * How many mainboard slots [WizardBuildResult.entries] came from, by source — the Commander
 * result screen's provenance summary (D13: WIZARD-placed vs. USER manual adds vs. lands).
 * [placedByWizard]/[placedManual] count NON-LAND cards only; [lands] counts every land placed
 * (owned non-basics + basics), regardless of source, since D10's land fill is a single stage that
 * does not itself distinguish "wizard-chosen" from "user-added" lands beyond what
 * [WizardBuildResult.entries]' own [DeckEntry] already carries.
 */
data class WizardFillStats(
    val placedByWizard: Int,
    val placedManual: Int,
    val lands: Int,
    /** W8 (telemetry): [CommanderDraftBuild.preferenceBonusAppliedCount], carried through so the
     * caller can report preference-prior hit rate without re-deriving it from the draft. */
    val preferenceBonusAppliedCount: Int = 0,
    /** Deck Wizard Commander v5 (D4/S8): count-only telemetry for [WizardBuildResult
     * .fallbackStandaloneIds]/[WizardBuildResult.fallbackOffPlanIds] -- never card names/ids. */
    val fallbackStandaloneCount: Int = 0,
    val fallbackOffPlanCount: Int = 0,
    /** Deck Wizard 60-card wave (v6, plan §5 Phase 1.3): distinct non-land CARD NAMES on the final
     * board (Commander == [placedByWizard] + [placedManual], since Commander never places more
     * than 1 copy of anything; a 60-card build's copies collapse this below the raw card count). */
    val distinctNames: Int = 0,
    /** Deck Wizard 60-card wave (v6): how many of those distinct names reached exactly 4 copies —
     * telemetry/harness signal for [com.mmg.manahub.feature.decks.domain.engine.PlacementScorer
     * .CONSISTENCY_CREDIT]'s calibration (Phase 6), never used by the build itself. Always 0 for
     * Commander (every copy count is 1). */
    val fourOfCount: Int = 0,
)

/**
 * The Commander build path's terminal result (Deck Wizard Commander v3 plan, Phase 1.2) — replaces
 * [TemplateBuildResult] for Commander/Commander Casual builds (`BuildCommanderDeckUseCase`, Phase
 * 2). Unlike [TemplateBuildResult], every field here speaks the SAME vocabulary the Studio Analysis
 * tab already renders (D2/D8): no `SuggestionCategory`/`DeckGap`/`CategoryFill` third vocabulary.
 *
 * @property entries the finished mainboard (commander included, per D12/D13's write contract),
 *           source-tagged (WIZARD for engine-placed + commander, USER for manual adds — D13).
 * @property analysis the SAME [DeckAnalysis] `DeckAnalysisPipeline.analyze` produces for this
 *           mainboard — the Result screen renders this directly (D15: "the Result screen shows the
 *           same DeckAnalysis Studio will show").
 * @property gapSections D8: the verification analysis's own [CardSection]s with `current < min` —
 *           the same sections/ids the Analysis tab shows, never a separate gap vocabulary.
 * @property fillStats provenance summary for the result screen's "X placed by the wizard, Y kept
 *           from your manual adds, Z lands" copy.
 * @property refinementSwaps D11: how many of the refinement pass's local-search swaps were
 *           actually accepted (each one strictly increased `analysis.totalScore`).
 * @property ambiguityGroups W6 Task 4 (E6) — sections the engine could not resolve on its own: a
 *           role band still short of its ideal at the end of placement, with the real leftover
 *           candidates that would have advanced it, when several of them cleared within a relative
 *           epsilon of each other's marginal gain. Empty by default (Casual/`TemplateBuildResult`
 *           paths never populate this). W7 (a later run) renders these as the Choice screen; this
 *           run only produces and exposes the data.
 */
data class WizardBuildResult(
    val entries: List<DeckEntry>,
    val analysis: DeckAnalysis,
    val gapSections: List<CardSection>,
    val fillStats: WizardFillStats,
    val refinementSwaps: Int = 0,
    val ambiguityGroups: List<AmbiguityGroup> = emptyList(),
    /** Deck Wizard Commander v5 (D4/S8) -- non-manual [entries] placed only once the main
     * placement loop had no more skeleton/axis-relevant candidate: has its own classified role,
     * just not one the skeleton targets ("Standalone" in [com.mmg.manahub.feature.decks.domain
     * .engine.AnalysisEngine]'s own final-analysis sense). Always resolvable against [entries]. */
    val fallbackStandaloneIds: List<String> = emptyList(),
    /** The genuine last resort: no classified role and no axis edge, placed only because neither
     * the loop nor the Standalone fallback had anything left. Empty whenever an on-plan or
     * Standalone candidate remained. */
    val fallbackOffPlanIds: List<String> = emptyList(),
)

/**
 * One unresolved section (W6 Task 4, E6): [sectionId] is a [RoleKey] still short of
 * [remainingSlots] copies of its [com.mmg.manahub.feature.decks.domain.engine.RoleTarget.ideal] at
 * the end of placement, and [candidateIds] are the real leftover (never-placed) candidates whose
 * recomputed marginal gain cleared within [BuildCommanderDeckUseCase.AMBIGUITY_EPSILON] of the
 * strongest one — i.e. genuinely plausible alternatives, not every card that merely touches the
 * role. Always `candidateIds.size >= 2` (a lone remaining candidate is not an ambiguity, the engine
 * would simply place it) and `remainingSlots > 0` (a filled section never appears here).
 */
data class AmbiguityGroup(
    val sectionId: RoleKey,
    val candidateIds: List<String>,
    val remainingSlots: Int,
)

/**
 * Deck Wizard Commander v3 plan, Phase 1.2 — the Commander build loop's own staged-progress list
 * (plan §3's stage list: "plan resolve → manual adds → placement → land fill → verify+refine →
 * write"). Deliberately a SEPARATE enum from [BuildStage], not an extension of it:
 * `DeckWizardGenerationResult.kt`'s `BuildStage.label()` mapper is an EXHAUSTIVE `when` with no
 * `else` branch (one `R.string` per case) — appending cases to the shared enum would force an
 * unrelated Casual-UI change (new strings) for stages the Commander build (Phase 2+) does not use
 * yet, and would blur "which stages can a Casual build actually emit" (Casual keeps
 * [BuildDeckFromTemplateUseCase]/[BuildStage] untouched, D7's escape hatch). Emitted by
 * [BuildCommanderDeckUseCase]'s own `onStage` callback (Phase 6, 6.3) and consumed by the wizard
 * VM's Generating step.
 */
enum class CommanderBuildStage {
    RESOLVING_PLAN, PLACING_MANUAL_ADDS, PLACING_CARDS, FILLING_LANDS, VERIFYING_AND_REFINING, WRITING_DECK, DONE,
}
