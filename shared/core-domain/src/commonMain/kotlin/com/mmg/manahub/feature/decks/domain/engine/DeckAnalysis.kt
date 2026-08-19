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

    /** An anti-role (a role this plan actively does NOT want) runs over its resolved tolerance. */
    data class AntiRoleOverMax(val roleKey: RoleKey, val label: String, val current: Int, val max: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.WARNING
    }

    // ── P4 — Synergy ────────────────────────────────────────────────────────────────────────
    /** Fraction of non-land copies aligned with the deck's dominant tag fingerprint is below the
     * legacy density floor (0.35, only checked once the deck has >10 non-lands). */
    data class LowSynergyDensity(val density: Float) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.INFO
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

    /** One or more mainboard slots could not be resolved to a full [com.mmg.manahub.core.model.Card]
     * (mirrors [DeckWarning.UnresolvedCards]) — appended by the orchestrator, never by
     * [AnalysisEngine] itself (the engine only ever sees resolved entries). See [withUnresolvedFinding]. */
    data class UnresolvedCards(val count: Int) : Finding {
        override val severity: FindingSeverity get() = FindingSeverity.INFO
    }
}

/**
 * One pillar's evaluation: a 0-100 [subscore], its (budgeted, ≤3 unless BLOCKERs push past that —
 * see [AnalysisEngine]) [findings], and — for [PillarId.PLAN_ROLES] only — the full
 * [roleCoverage] table.
 *
 * @property collapsedFindingsCount how many additional (budgeted-out) findings exist beyond
 *           [findings] — the Phase 3 UI's "N more" affordance. `0` when nothing was collapsed.
 */
data class PillarResult(
    val id: PillarId,
    val subscore: Int,
    val findings: List<Finding>,
    val collapsedFindingsCount: Int = 0,
    val roleCoverage: List<RoleCoverageEntry> = emptyList(),
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
    val archetype: ArchetypeId,
    val themes: List<ThemeId>,
    val isManualOverride: Boolean,
    val confidence: Float,
)

/**
 * The Engine v2 unified result: ONE [totalScore] (0-100), the 5 [pillars], the resolved [strategy],
 * and a [catalogVersion] handle (mirrors [CuratedStrategyCatalog.CATALOG_VERSION] — bumped whenever
 * a re-tune of [AnalysisWeights]/the underlying [ArchetypeData] bands would invalidate a cached
 * client-side result; NOT used for remote delivery in this phase, see the plan's §3.3 note).
 */
data class DeckAnalysis(
    val totalScore: Int,
    val pillars: List<PillarResult>,
    val strategy: ResolvedStrategyInfo,
    val catalogVersion: Int = CuratedStrategyCatalog.CATALOG_VERSION,
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
 * duplicated — see [toAnalysisWeights]). Defaults are a documented starting point; CALIBRATION is
 * explicitly Phase 4, not this phase — [planRoles] gets the largest share since the plan-role table
 * ("Removal 3/8") is the score's own literal explanation (D3).
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
}
