package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-17

import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.RoleKey

// Deck Wizard 60-card wave (v6), plan §5 Phase 7.1 -- survivors of the deleted TemplateBuildResult.kt
// (BuildStage/TemplateBuildProgress/TemplateCardSuggestion/CategorySuggestions/CategoryFill/DeckGap/
// OTHER_GAP_CATEGORY_ID/TemplateBuildResult were Motor A-only and went with the deleted Motor A wizard build use case).

/**
 * How many mainboard slots [WizardBuildResult.entries] came from, by source — the wizard result
 * screen's provenance summary (D13: WIZARD-placed vs. USER manual adds vs. lands).
 * [placedByWizard]/[placedManual] count NON-LAND cards only; [lands] counts every land placed
 * (owned non-basics + basics), regardless of source, since D10's land fill is a single stage that
 * does not itself distinguish "wizard-chosen" from "user-added" lands beyond what
 * [WizardBuildResult.entries]' own [DeckEntry] already carries.
 */
data class WizardFillStats(
    val placedByWizard: Int,
    val placedManual: Int,
    val lands: Int,
    /** W8 (telemetry): [WizardDraftBuild.preferenceBonusAppliedCount], carried through so the
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
 * The wizard build path's terminal result — the SAME vocabulary the Studio Analysis tab already
 * renders (D2/D8): no `SuggestionCategory`/`DeckGap`/`CategoryFill` third vocabulary.
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
 *           epsilon of each other's marginal gain. The Choice screen renders these.
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
 * recomputed marginal gain cleared within [com.mmg.manahub.feature.decks.domain.template
 * .BuildWizardDeckUseCase.AMBIGUITY_EPSILON] of the strongest one — i.e. genuinely plausible
 * alternatives, not every card that merely touches the role. Always `candidateIds.size >= 2` (a
 * lone remaining candidate is not an ambiguity, the engine would simply place it) and
 * `remainingSlots > 0` (a filled section never appears here).
 */
data class AmbiguityGroup(
    val sectionId: RoleKey,
    val candidateIds: List<String>,
    val remainingSlots: Int,
)

/**
 * The wizard build loop's own staged-progress list (plan §3's stage list: "plan resolve → manual
 * adds → placement → land fill → verify+refine → write"). Emitted by [BuildWizardDeckUseCase]'s own
 * `onStage` callback and consumed by the wizard VM's Generating step, for every anchor/format.
 *
 * Deck Wizard 60-card wave (v6, plan §5 Phase 7.1): renamed from `CommanderBuildStage` now that
 * every format (not only Commander) drives this same enum.
 */
enum class WizardBuildStage {
    RESOLVING_PLAN, PLACING_MANUAL_ADDS, PLACING_CARDS, FILLING_LANDS, VERIFYING_AND_REFINING, WRITING_DECK, DONE,
}
