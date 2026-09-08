package com.mmg.manahub.feature.decks.domain.engine

// ═══════════════════════════════════════════════════════════════════════════════
//  DeckAnalysis — Deck Analysis Engine v2 plan (docs/plans/deck-analysis-engine-v2-plan.md),
//  Phase 2 / §3.3 "Unified evaluation pipeline".
//
//  The result model for [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCaseV2]: ONE
//  engine producing a total [DeckAnalysis.totalScore], 5 pillar sub-scores, and severity-tiered
//  [Finding]s -- all derived from the SAME resolved [ResolvedArchetypeSkeleton] (plan D1 "total
//  unification"). This is the NEW replacement for the legacy [DeckWarning]/[DeckWarning
//  .ArchetypeRoleGap] duality (see the plan's §3.3 "what retires"): [Finding] is a single,
//  RoleKey-vocabulary-only sealed type, never a second competing model.
//
//  [DeckWarning]/[DeckEvaluation] are NOT deleted -- they still back
//  [com.mmg.manahub.feature.decks.domain.usecase.DeckHealth.evaluation], the legacy field the
//  currently-live `DeckStudioScreen` health ring/warnings section reads (Suggestions tab flag is
//  ON; only the Cuts/Adds/Community sections are gated off by
//  `DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED`). [DeckAnalysis] is purely ADDITIVE on
//  [com.mmg.manahub.feature.decks.domain.usecase.DeckHealth.analysis] until the Phase 3 UI migrates
//  the Suggestions ("Analysis") tab onto it.
// ═══════════════════════════════════════════════════════════════════════════════

/** Severity tier for a v2 [Finding]. Drives UI color AND the "never collapse" rule in the finding
 * budget (a pillar shows at most 3 findings; BLOCKERs are exempt from that cap — see
 * [AnalysisEngine]'s budgeting helper). */
enum class FindingSeverity { BLOCKER, WARNING, INFO }

/** The 5 fixed analysis pillars (plan §3.3). Declaration/enum order is the Phase 3 display order. */
enum class PillarId { MANA_BASE, CURVE, PLAN_ROLES, SYNERGY, LEGALITY }

/**
 * One row of the "Removal 3/8"-style plan-role table (P3's [PillarResult.roleCoverage]; RoleKey
 * vocabulary, [ArchetypeRoleClassifier.label] English copy). [current]/[min]/[ideal]/[max] mirror
 * [RoleTarget] verbatim; [isAntiRole] flips the intended read direction (over [max] is the problem,
 * not under [min]) so the UI can render an inverted bar without re-deriving anything.
 */
data class RoleCoverageEntry(
    val roleKey: RoleKey,
    val label: String,
    val current: Int,
    val min: Int,
    val ideal: Int,
    val max: Int,
    val isAntiRole: Boolean,
)

// ═══════════════════════════════════════════════════════════════════════════════
//  Category Sections rework (docs/plans/deck-analysis-category-sections-plan.md, W1) — per-card
//  attribution the engine previously discarded. See ArchetypeRoleClassifier.deckRoleAttribution's
//  KDoc for WHERE this data comes from; see CardSection's KDoc below for the count-vs-list caveat.
// ═══════════════════════════════════════════════════════════════════════════════

/** One card's contribution to a [CardSection]. [confidence] is 1f for structural buckets (curve/
 * mana-color/legality — anything not derived from [ArchetypeRoleClassifier.classify]'s fractional
 * role confidence). */
data class CardContribution(
    val scryfallId: String,
    val quantity: Int,
    val confidence: Float,
)

/**
 * One category row inside a pillar: a title, an engine-authoritative [current] count, an optional
 * target band, and the deck cards that fill it.
 *
 * [current] is NOT `contributions.sumOf { it.quantity }` — role counts are
 * `round(Σ quantity × confidence)` (see [ArchetypeRoleClassifier.deckRoleCounts]), so a
 * partially-confident card contributes a fraction. [current] is what the score uses; the list is
 * what produced it. Never reconcile one from the other.
 */
data class CardSection(
    /** "role:removal_spot" | "mv:3" | "mv:7plus" | "produces:B" | "fingerprint:tokens" |
     * "tribe:elf" | "interaction" | "standalone" | "offplan" | "legal" | "illegal".
     * "interaction"/"standalone"/"offplan" (Deck Analysis Engine v3, PHASE 4, spec §7) are the
     * 3-way split of the old catch-all "offplan" bucket -- see
     * [AnalysisEngine.evaluateSynergy]'s own KDoc for the split rule. See
     * [com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery] (W3) for how this id maps
     * to a browse query. */
    val id: String,
    val label: String,
    val current: Int,
    val min: Int? = null,
    val ideal: Int? = null,
    val max: Int? = null,
    val isAntiRole: Boolean = false,
    val contributions: List<CardContribution> = emptyList(),
)

/**
 * A single, severity-tagged, structured analysis finding — Engine v2's unified replacement for the
 * legacy [DeckWarning] on the analysis path (mirrors [ScoreReason]'s "structured, never a hardcoded
 * string" discipline: presentation localizes each variant to English copy from `strings.xml`, the
 * engine itself stays string-free). Grouped by which pillar produces it (comments below); every
 * variant carries enough data to render without re-deriving anything from the card list.
 */
sealed interface Finding {
    val severity: FindingSeverity

    // ── P1 — Mana base ──────────────────────────────────────────────────────────────────────
    /** Land count is outside BOTH the skeleton's static band AND the Karsten dynamic target ± 2
     * (D18 union rule, mirrors [DeckWarning.TooFewLands]/[DeckWarning.TooManyLands] but keyed on
     * the union check, not a single band). */
    data class LandCountOffTarget(val current: Int, val karstenTarget: Int, val bandMin: Int, val bandMax: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** A demanded colour's land sources fall short of the Karsten-style threshold (soft shortage). */
    data class ColorSourceShortage(val color: ManaColor, val have: Int, val need: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** A demanded colour is essentially un-fixed (near-zero sources) — louder than [ColorSourceShortage]. */
    data class UnfixedSplash(val color: ManaColor) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** Dedicated mana-fixing role count (`mana_fix`) is below the resolved colour-modulation minimum. */
    data class ManaFixShortage(val current: Int, val min: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** The land base's basics-vs-non-basic-fixing split falls outside [ArchetypeData.LAND_MIX]'s
     * guideline range for the deck's colour count (WS9.3 data table, wired into scoring for the
     * first time by this pillar). Advisory (INFO) — the guideline is a judgment-call range, not a
     * hard band like [bandMin]/[bandMax] elsewhere in this file. */
    data class LandMixOffTarget(val basicsRatio: Float, val targetMin: Float, val targetMax: Float) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.INFO
    }

    // ── P2 — Curve ──────────────────────────────────────────────────────────────────────────
    /** Average mana value falls outside the resolved archetype's curve band (suppressed entirely
     * under an active [CurveExemption], mirrors legacy [DeckWarning.CurveOutsideArchetypeBand]). */
    data class CurveOffBand(val avgMv: Double, val bandMin: Double, val bandMax: Double) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** The mana-value histogram does not follow the resolved skeleton's [CurveShape] (FRONT/BELL/
     * BACK) — a soft, advisory heuristic ([CurveShape] carries no scoring contract elsewhere yet). */
    data class CurveShapeMismatch(val shape: CurveShape) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.INFO
    }

    // ── P3 — Plan roles ─────────────────────────────────────────────────────────────────────
    /** A normal (non-anti) role falls below its resolved band minimum — THIS is the "Removal 3/8"
     * finding; the full table lives in [PillarResult.roleCoverage], this is just the alert form. */
    data class RoleGap(val roleKey: RoleKey, val label: String, val current: Int, val min: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** Deck Analysis Engine v3 (spec §6.2) — a normal (non-anti) role clears [min] but has not yet
     * reached [ideal]: the score/warning-disagreement fix. Before this, a role sitting exactly at
     * [min] lost points (P3's [bandRatioScore] ramps up to [ideal]) with NO explanation ([RoleGap]
     * only fires below [min]) — every point lost is now explained. Deliberately [FindingSeverity
     * .INFO], NOT [FindingSeverity.WARNING] (spec's own explicit requirement) — "you could add more"
     * is advisory, not a plan defect, and must not pollute the warning list next to a real
     * [RoleGap]/[AntiRoleOverMax]. Fires for `min <= current < ideal`; never fires alongside
     * [RoleGap] for the same [roleKey] (the two conditions are mutually exclusive by construction —
     * see [AnalysisEngine.evaluatePlanRoles]). */
    data class RoleBelowIdeal(val roleKey: RoleKey, val label: String, val current: Int, val ideal: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.INFO
    }

    /** An anti-role (a role this plan actively does NOT want) runs over its resolved tolerance. */
    data class AntiRoleOverMax(val roleKey: RoleKey, val label: String, val current: Int, val max: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    // ── P4 — Synergy ────────────────────────────────────────────────────────────────────────
    // Deck Analysis Engine v3, PHASE 4 (spec §5.4/§7) -- one Finding per [SynergyConflict] variant,
    // replacing the old density-only [LowSynergyDensity] (removed this phase). Each is the FIRST
    // time the underlying conflict, already detected in Phase 2's shadow-mode [SynergyGraph], is
    // both surfaced to the user AND applied as a bounded P4 penalty (capped at 0.15 raw, see
    // [AnalysisEngine.evaluateSynergy]) -- "9 token generators, 0 payoffs" is real, actionable
    // deckbuilding advice the engine could not express before this phase.

    /** `graveyard_hate` >= 3 copies while the deck's OWN `GRAVEYARD` axis is live -- the deck is
     * fighting its own graveyard plan. */
    data class SelfDefeatingGraveyardHate(val graveyardHateCopies: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** [producerCopies] already clears [producerIdeal] on this axis, but the axis has zero
     * payoffs. [axis] is the raw [AxisKey] (programmatic use, mirrors [RoleGap]'s `roleKey`);
     * [axisLabel] is the precomputed English fallback ([AnalysisEngine.synergyStrategyLabel]'s
     * KDoc explains why `core-domain` cannot reach `TagDictionary.localize` for this). */
    data class OrphanProducers(val axis: AxisKey, val axisLabel: String, val producerCopies: Int, val producerIdeal: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** Mirror image of [OrphanProducers]: [payoffCopies] already clears [payoffIdeal] on this
     * axis, but producers sit below 25% of their own ideal. */
    data class OrphanPayoffs(val axis: AxisKey, val axisLabel: String, val payoffCopies: Int, val payoffIdeal: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** `stax_piece` >= 8 while `card_draw` >= CONTROL's own resolved ideal for this format -- a
     * lock-piece count that also chokes the deck's OWN card-draw engine. [controlCardDrawIdeal] is
     * read live from [ArchetypeData] (never a hand-duplicated constant), see [SynergyConflict
     * .StaxVsOwnEngine]'s own KDoc. */
    data class StaxVsOwnEngine(val staxPieceCopies: Int, val cardDrawCopies: Int, val controlCardDrawIdeal: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    // ── P5 — Legality & construction ────────────────────────────────────────────────────────
    data class DeckTooSmall(val current: Int, val minimum: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.BLOCKER
    }

    data class TooManyCopies(val cardName: String, val copies: Int, val maxCopies: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.BLOCKER
    }

    data class SingletonViolation(val cardName: String, val copies: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.BLOCKER
    }

    data class OffColorIdentity(val cardName: String) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.BLOCKER
    }

    /** NEW in v2: a card is not legal in the deck's format. Never surfaced as a deck-level warning
     * before this phase (the legacy [DeckScorer.isLegal] only gated the `fit()`/`rankAdds()` add
     * path, never `evaluate()`'s warnings) — see this plan's Phase 2 report for the rationale. */
    data class IllegalCard(val cardName: String) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.BLOCKER
    }

    /** NEW in Wave 2 (B3): 60-card constructed formats
     * ([com.mmg.manahub.core.model.DeckFormat.isSixtyCardConstructed] — Standard/Pioneer/Modern/
     * Legacy/Vintage/Pauper/Casual) allow at most 15 sideboard cards. Advisory ([FindingSeverity
     * .WARNING], not BLOCKER) — the v2 analysis is mainboard-only by design, so this does not zero
     * [PillarResult.subscore] the way a construction BLOCKER does (see [AnalysisEngine
     * .evaluateLegality]'s KDoc). Deeper sideboard analysis (role coverage of the board, matchup
     * logic) is FUTURE DEBT, not this pass's scope. */
    data class SideboardOversized(val count: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    /** One or more mainboard slots could not be resolved to a full [com.mmg.manahub.core.model.Card]
     * (mirrors [DeckWarning.UnresolvedCards]) — appended by the orchestrator, never by
     * [AnalysisEngine] itself (the engine only ever sees resolved entries). See [withUnresolvedFinding]. */
    data class UnresolvedCards(val count: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.INFO
    }
}

/**
 * One pillar's evaluation: a 0-100 [subscore], its FULL BLOCKER-first, severity/magnitude-sorted
 * [findings] list, and — for [PillarId.PLAN_ROLES] only — the full [roleCoverage] table.
 *
 * Suggestions Tab UI Polish plan (W4, D5): [findings] used to be silently truncated by
 * [AnalysisEngine]'s `budgetFindings` (top 3 + a `collapsedFindingsCount` overflow number, with the
 * rest permanently discarded — "show more" was impossible since the discarded findings were never
 * even carried in this model). `budgetFindings` was renamed `sortFindings` and now only SORTS
 * (never drops); the `collapsedFindingsCount` field this KDoc used to document was deleted — the UI
 * ([com.mmg.manahub.feature.decks.presentation.components.FindingsList] in the app module) now
 * folds the initial 3 itself and shows the rest on "Show more" tap, entirely client-side. Verified
 * this was always a display-only concern: every pillar computes its `subscore` BEFORE calling
 * `sortFindings`, so uncapping the list changes nothing about [DeckAnalysis.totalScore].
 *
 * @property roleCoverage kept UNCHANGED for [PillarId.PLAN_ROLES] (existing golden/calibration
 *           tests depend on it) — test/compat surface only. UI reads [sections] instead (Category
 *           Sections rework, W1). Folding the two is deliberate future debt, not done in this pass.
 * @property sections Category Sections rework (W1) — per-category card attribution for every
 *           pillar (MANA_BASE/CURVE/PLAN_ROLES/LEGALITY populated in W1; SYNERGY in W2). Appended
 *           LAST so every pre-existing [PillarResult] construction site stays valid.
 * @property alignedNonLandCopies Category Sections rework (W2), [PillarId.SYNERGY] ONLY — the same
 *           `alignedCopies` [AnalysisEngine.evaluateSynergy]'s `subscore` is built from, exposed
 *           raw (not pre-formatted into a string — the engine stays string-free per [Finding]'s own
 *           KDoc discipline) so the UI can render the "aligned N / M non-lands" sub-caption without
 *           re-deriving N from [sections] (summing aligned sections' `current` would double-count a
 *           card aligned on 2+ keys). `0` for every other pillar.
 * @property totalNonLandCopies Category Sections rework (W2), [PillarId.SYNERGY] ONLY — the `M` in
 *           the same "aligned N / M non-lands" caption (`nonLandCount` at the call site). `0` for
 *           every other pillar.
 * @property notApplicable Deck Analysis Engine v3 — P4 SYNERGY scoring-semantics fix (2026-08-27):
 *           `true` only when [AnalysisEngine.evaluateSynergy] finds a deck with LITERALLY ZERO
 *           [DeckSynergyGraph.edges] — no theme axis, no ENGINE/LOCK macro-identity axis, zero
 *           producer/payoff pairs on any axis at all, so this pillar has nothing to measure. In
 *           that case reporting [subscore] = 0 would be FALSE PRECISION — it asserts "maximally
 *           incoherent" when the true state is "this pillar does not apply to this deck" (a real
 *           archetype-canonical deck like a themeless removal-and-card-advantage midrange plan can
 *           legitimately have zero edges, while a genuinely incoherent goodstuff pile usually still
 *           has a few accidental producer/payoff pairs and keeps a real, low, non-zero score).
 *           [subscore] is ALWAYS `0` on this branch — a formality with no scoring weight, never a
 *           real measurement — because [AnalysisEngine]'s own `compose` step REDISTRIBUTES this
 *           pillar's weight across the other 4 pillars (see `compose`'s own KDoc) rather than
 *           scoring it. Callers (the phase-5 UI) MUST check this flag before displaying [subscore]
 *           as a number — render "no detectable synergy plan" instead of "0/100". Defaults `false`
 *           for every OTHER pillar and every pre-existing [PillarResult] construction site
 *           (appended LAST, same convention as [alignedNonLandCopies]/[totalNonLandCopies] above).
 */
data class PillarResult(
    val id: PillarId,
    val subscore: Int,
    val findings: List<Finding>,
    val roleCoverage: List<RoleCoverageEntry> = emptyList(),
    val sections: List<CardSection> = emptyList(),
    val alignedNonLandCopies: Int = 0,
    val totalNonLandCopies: Int = 0,
    val notApplicable: Boolean = false,
)

/**
 * The strategy [DeckAnalysis] was evaluated against — the resolved (pin-or-inferred) archetype/
 * themes PLUS the nearest [CuratedStrategy] for display (Phase 1's `nearestFor` mapper).
 *
 * @property curatedStrategyId [CuratedStrategy.id] of the nearest curated match, or `null` for the
 *           "Custom" sentinel. A GENERIC archetype with NO theme now DOES map to a catalog entry
 *           (`"balanced"`); `null` instead covers a `null` archetype, GENERIC paired with one or
 *           more themes (an incoherent/unusual legacy pin — deliberately excluded from the
 *           "balanced" match, see [CuratedStrategyCatalog.nearestFor]'s KDoc), or any other
 *           archetype/theme combination with no matching catalog entry.
 * @property displayName player-facing name: the curated match's [CuratedStrategy.displayName] when
 *           one exists, else a plan-label fallback (see [ResolvedArchetypeSkeleton.planLabel]) or
 *           "Custom".
 */
data class ResolvedStrategyInfo(
    val curatedStrategyId: String?,
    val displayName: String,
    /** `null` = no confident macro (Deck Analysis Engine v3 removed `ArchetypeId.GENERIC`) — see
     * [AnalysisEngine.evaluate]'s own KDoc. [displayName] carries the "Custom"/hybrid label in
     * this case, so UI callers should prefer [displayName] over branching on [archetype] directly. */
    val archetype: ArchetypeId?,
    /** Deck Analysis Engine v3 (spec §3) — `null` when no posture was detected/pinned. */
    val posture: PostureId? = null,
    val themes: List<ThemeId>,
    val isManualOverride: Boolean,
    val confidence: Float,
)

/**
 * Diagnoses whether [DeckAnalysis.totalScore] is being dominantly held back by ONE specific thing,
 * so the UI can say so explicitly instead of leaving the player to guess. Added after a live-device
 * finding (2026-08-20): a Standard deck stuck at [AnalysisEngine.ILLEGAL_DECK_SCORE_CAP] scored the
 * same no matter which strategy the player picked, and was read as "the strategy switcher is
 * broken" — it wasn't; 3 format-illegal cards were hard-capping the total regardless of what the
 * (correctly recomputing) pillars underneath said. This is purely a READ on top of [AnalysisEngine]'s
 * existing score composition (see [AnalysisEngine]'s `compose` step) — it never changes
 * [DeckAnalysis.totalScore] itself, any pillar [PillarResult.subscore], or [AnalysisWeights].
 */
sealed interface ScoreLimiter {
    /** [AnalysisEngine.ILLEGAL_DECK_SCORE_CAP] actually reduced the score below what the weighted
     * pillars alone would have produced. [uncappedScore] is that pre-cap weighted total — surfaced
     * so the player can see their "real" plan score is fine and the cap is the only thing hiding it. */
    data class LegalityCapped(val uncappedScore: Int) : ScoreLimiter

    /** No hard cap engaged, but one pillar's shortfall accounts for a dominant share of the lost
     * points (see [AnalysisEngine]'s `computeScoreLimiter` for the exact thresholds). [lostPoints]
     * is how many of the 100 possible points this pillar ALONE is costing
     * (`round(weight_i * (100 - subscore_i))`). */
    data class DominantPillar(val pillarId: PillarId, val lostPoints: Int) : ScoreLimiter

    /** No single obvious blocker — the gap (if any, including none) is spread reasonably evenly
     * across pillars, or the deck simply scores well. The common case. */
    data object None : ScoreLimiter
}

/**
 * The Engine v2 unified result: ONE [totalScore] (0-100), the 5 [pillars], the resolved [strategy],
 * a [limiter] diagnosis of what (if anything) is dominantly holding [totalScore] back, and a
 * [catalogVersion] handle (mirrors [CuratedStrategyCatalog.CATALOG_VERSION] — bumped whenever
 * a re-tune of [AnalysisWeights]/the underlying [ArchetypeData] bands would invalidate a cached
 * client-side result; NOT used for remote delivery in this phase, see the plan's §3.3 note).
 */
data class DeckAnalysis(
    val totalScore: Int,
    val pillars: List<PillarResult>,
    val strategy: ResolvedStrategyInfo,
    val limiter: ScoreLimiter,
    val catalogVersion: Int = CuratedStrategyCatalog.CATALOG_VERSION,
    /**
     * Deck Analysis Engine v3, PHASE 2 (shadow mode) — the directed synergy graph + axis health,
     * populated ONLY when [AnalysisEngine.evaluate] is called with `includeDebugSynergyGraph = true`
     * (default `false`, mirroring [com.mmg.manahub.core.model.ScoreWeightOverrides]'s own
     * debug-tuning-surface convention). Computed AFTER every pillar has already run
     * ([AnalysisEngine.compose] never sees it), so no pillar can read it even in principle — see
     * [SynergyGraph]'s own file header. `null` for [com.mmg.manahub.core.model.DeckFormat.DRAFT] (no
     * archetype skeleton to build a graph against) and whenever the flag is left at its default.
     *
     * PHASE 5 (UI, 2026-08-27): [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase]
     * — the real Analysis tab pass — now passes `true`, so this is populated in production: the
     * Suggestions tab's synergy-package UI (per-live-axis producer -> payoff card sections) reads
     * it directly. The field keeps its `debug`-flavored name (avoids a wider rename across the one
     * existing test that names it) but is no longer debug-only in practice.
     */
    val debugSynergyGraph: DeckSynergyGraph? = null,
)

/**
 * Appends a [Finding.UnresolvedCards] into the LEGALITY pillar — mirrors the legacy
 * [DeckWarning.UnresolvedCards] append site
 * ([com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator.withUnresolvedWarning]).
 * A no-op for `count <= 0`. LEGALITY is the chosen home because an unresolved slot is a
 * construction-adjacent caveat ("this analysis is partial"), not a scored gap in any specific pillar.
 */
fun DeckAnalysis.withUnresolvedFinding(count: Int): DeckAnalysis {
    if (count <= 0) return this
    val updatedPillars = pillars.map { pillar ->
        if (pillar.id != PillarId.LEGALITY) pillar
        else pillar.copy(findings = pillar.findings + Finding.UnresolvedCards(count))
    }
    return copy(pillars = updatedPillars)
}

/**
 * Per-pillar weights for [DeckAnalysis.totalScore]'s composition (plan §3.3). Debug-tunable through
 * the existing [com.mmg.manahub.core.model.ScoreWeightOverrides] DataStore mechanism (extended, not
 * duplicated — see [toAnalysisWeights]). [planRoles] gets the largest share of every row since the
 * plan-role table ("Removal 3/8") is the score's own literal explanation (D3).
 *
 * ## Deck Analysis Engine v3 (spec §8) — archetype-dependent weights (2026-08-26)
 * The values on THIS data class's own constructor defaults are now used ONLY as: (a) [forMacro]'s
 * MIDRANGE row (an ambiguous/`null`-macro deck's weighting, byte-identical to pre-v3 behavior — see
 * [forMacro]'s own KDoc), and (b) [ScoreWeightOverridesMapper.toAnalysisWeights]'s fallback `defaults`
 * parameter for callers with no macro to derive a base from. Every REAL evaluation path
 * ([AnalysisEngine.evaluate]'s own default, [com.mmg.manahub.feature.decks.domain.usecase
 * .EvaluateDeckUseCaseV2]'s own default, and [com.mmg.manahub.feature.decks.domain.usecase
 * .EvaluateDeckUseCase]'s explicit resolution) now uses [forMacro] instead — see that function's KDoc
 * for the full per-macro table and the live-theme shift.
 *
 * ## Phase 4 calibration review (2026-08-19) — JUDGMENT CALL: NO CHANGE
 * The 6 [DeckAnalysisEngineGoldenTest][com.mmg.manahub.feature.decks.domain.engine.DeckAnalysisEngineGoldenTest]
 * reference decks all scored low and clustered (51-64/100) under these SAME default weights; this was
 * diagnosed as a FIXTURE-density artifact (those decks intentionally use ~17-28 real nonland cards +
 * one bulk basic-land entry, inflating land count to ~2x a real Commander deck's and starving
 * [planRoles]'s band minimums, which are sized for a real ~60-card nonland deck), not a weight
 * miscalibration — see [com.mmg.manahub.feature.decks.domain.engine.DeckAnalysisEngineCalibrationTest]'s
 * own header for the full analysis. Two REALISTIC-density Commander fixtures (~65 real nonland cards +
 * 35-38 real lands, the two archetype extremes AGGRO/CONTROL) built for this review score 94/100 and
 * 77/100 respectively under these UNCHANGED defaults — both comfortably inside a defensible
 * "well-built, on-plan deck" band — confirming the default weights need no retune once fixture density
 * is realistic. Left as-is.
 */
data class AnalysisWeights(
    val manaBase: Float = 0.20f,
    val curve: Float = 0.15f,
    val planRoles: Float = 0.35f,
    val synergy: Float = 0.15f,
    val legality: Float = 0.15f,
) {
    private val positiveSum get() = manaBase + curve + planRoles + synergy + legality

    /** Normalizes the 5 weights to sum to 1 (mirrors [ScoreWeights.normalized]'s pattern) — a
     * debug override that only tweaks relative emphasis (e.g. doubling [planRoles]) still composes
     * a 0-100 [DeckAnalysis.totalScore]. */
    fun normalized(): AnalysisWeights {
        val s = positiveSum.takeIf { it > 0f } ?: return this
        return copy(
            manaBase = manaBase / s,
            curve = curve / s,
            planRoles = planRoles / s,
            synergy = synergy / s,
            legality = legality / s,
        )
    }

    companion object {
        /** Live-theme modifier cap (spec §8): 0.05 shifted between P3/P4 per live theme, capped at
         * 2 themes (0.10 total) — matches spec §1's own "0-2 themes" ceiling, so the cap is a
         * defensive bound, not a value normally reached by a 3rd/4th theme. */
        private const val THEME_SHIFT_PER_THEME = 0.05f
        private const val MAX_SHIFTABLE_THEMES = 2

        /**
         * Deck Analysis Engine v3 (spec §8) — archetype-dependent pillar weights, replacing the flat
         * default as the engine's baseline. Each [ArchetypeId] row is transcribed verbatim from the
         * spec's table (all 5 already sum to 1.00); [liveThemeCount] then shifts 0.05 from P3
         * ([planRoles]) to P4 ([synergy]) per live theme (capped at [MAX_SHIFTABLE_THEMES]), or 0.05
         * the OTHER way (P4 to P3) when no theme is live — spec's own worked example: "a pure Control
         * deck ends at P4 = 0.08; an Aristocrats deck at P4 = 0.20-0.25."
         *
         * [macro] `null` (an ambiguous/"Custom" resolution, spec §2.1 — [MACRO_AMBIGUITY_MARGIN] not
         * cleared) is a case the spec's own weight table does not enumerate. JUDGMENT CALL: falls
         * back to the [ArchetypeId.MIDRANGE] row. Reasoning, not a default-by-omission: spec §2.1
         * itself frames MIDRANGE as "the centre of the space" the macro resolver's prototype argmax
         * competes around — the natural neutral choice for a deck that couldn't be pinned to any
         * quadrant confidently. It is ALSO this class's own pre-4.5 flat default
         * (`0.20/0.15/0.35/0.15/0.15`), so an ambiguous deck's weighting is byte-identical to
         * pre-4.5 behavior — the safest possible choice for an unenumerated case, not an arbitrary
         * pick.
         *
         * NOT pre-normalized here — every call site still runs the result through [normalized] at
         * the one true composition point ([AnalysisEngine.compose]), so a caller-supplied
         * [com.mmg.manahub.core.model.ScoreWeightOverrides] merge layered on top of this base ends
         * up normalized exactly once, the same as every other path through this class.
         */
        fun forMacro(macro: ArchetypeId?, liveThemeCount: Int): AnalysisWeights {
            val base = when (macro) {
                ArchetypeId.AGGRO -> AnalysisWeights(manaBase = 0.18f, curve = 0.22f, planRoles = 0.30f, synergy = 0.15f, legality = 0.15f)
                ArchetypeId.MIDRANGE, null -> AnalysisWeights(manaBase = 0.20f, curve = 0.15f, planRoles = 0.35f, synergy = 0.15f, legality = 0.15f)
                ArchetypeId.CONTROL -> AnalysisWeights(manaBase = 0.22f, curve = 0.15f, planRoles = 0.35f, synergy = 0.13f, legality = 0.15f)
                ArchetypeId.COMBO -> AnalysisWeights(manaBase = 0.15f, curve = 0.10f, planRoles = 0.30f, synergy = 0.30f, legality = 0.15f)
                ArchetypeId.PRISON -> AnalysisWeights(manaBase = 0.18f, curve = 0.12f, planRoles = 0.35f, synergy = 0.20f, legality = 0.15f)
            }
            val cappedThemes = liveThemeCount.coerceIn(0, MAX_SHIFTABLE_THEMES)
            val p3ToP4Shift = if (cappedThemes == 0) -THEME_SHIFT_PER_THEME else THEME_SHIFT_PER_THEME * cappedThemes
            return base.copy(
                planRoles = (base.planRoles - p3ToP4Shift).coerceAtLeast(0f),
                synergy = (base.synergy + p3ToP4Shift).coerceAtLeast(0f),
            )
        }
    }
}
