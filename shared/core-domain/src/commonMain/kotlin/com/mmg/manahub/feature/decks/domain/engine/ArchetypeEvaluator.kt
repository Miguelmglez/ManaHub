package com.mmg.manahub.feature.decks.domain.engine

import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════════════════
//  ArchetypeEvaluator — Deck Doctor Community/Archetype plan, Phase 1.6/1.8 (D18)
//
//  Pure port of Appendix B's Python `evaluate()` + `budget_check()`. Takes a
//  role-count snapshot (never a `Card`/mainboard directly — see
//  [com.mmg.manahub.feature.decks.domain.usecase.ArchetypeRoleCounter] for how the
//  real engine derives one) and the resolved skeleton, and returns the warnings a
//  faithful port of the annex's reference evaluator would emit. This is the
//  function BOTH the golden skeleton tests (1.8, hand-typed role-count fixtures —
//  no `Card`/classifier involved) and the live [com.mmg.manahub.feature.decks
//  .domain.usecase.EvaluateDeckUseCase] wiring (1.6, real deck role counts) call.
// ═══════════════════════════════════════════════════════════════════════════════

object ArchetypeEvaluator {

    /**
     * D18 UNION land rule: a land-count warning fires ONLY when the count is outside BOTH the
     * skeleton's static [ResolvedArchetypeSkeleton.lands] band AND the Karsten dynamic
     * expectation ± 2. Karsten formulas (exact constants, D18 — `ramp`/`cheapDraw` are the deck's
     * `"ramp"`/`"card_draw"` role counts, unweighted sum, exactly as Appendix B's Python):
     *  - 60-card: `19.59 + 1.90*avgMV − 0.28*(ramp+cheapDraw)`
     *  - Commander: `(99/60)*(19.59 + 1.90*avgMV + 0.27) − 0.28*(ramp+draw) − 1.35`
     *
     * @return the Karsten target land count (informational — used as the [DeckWarning
     *         .TooFewLands]/[DeckWarning.TooManyLands] `target`, matching the existing warning
     *         shape so no new UI string is needed for the land check specifically).
     */
    fun karstenLandTarget(format: ArchetypeFormat, avgMv: Double, rampCount: Int, cardDrawCount: Int): Double {
        val cheap = (rampCount + cardDrawCount).toDouble()
        return when (format) {
            ArchetypeFormat.SIXTY -> 19.59 + 1.90 * avgMv - 0.28 * cheap
            ArchetypeFormat.COMMANDER -> (99.0 / 60.0) * (19.59 + 1.90 * avgMv + 0.27) - 0.28 * cheap - 1.35
        }
    }

    /**
     * Faithful port of the Python `evaluate()` reference function.
     *
     * @param roleCounts current deck counts keyed by [RoleKey] (Appendix A vocabulary). A role
     *        absent from this map is treated as `0` (mirrors Python's `deck.get(k, 0)`).
     * @param lands current land count.
     * @param avgMv current average mana value (non-land spells only).
     * @param skeleton the resolved skeleton (archetype + themes, WITHOUT color modulation — the
     *        mana_fix check reads the SEPARATE [colorModulation] param, mirroring Appendix B's
     *        Python evaluate(), which checks `color_modulation[fmt][key]` directly rather than
     *        through the resolved skeleton's roles map).
     * @param colorModulation the A.5 entry for the deck's color count (from
     *        [ArchetypeData.COLOR_MODULATION]), or `null` to skip the mana_fix check entirely
     *        (unknown/unresolved color identity — never fabricate a mana_fix warning without it).
     * @param curveExemptionActive when `true`, the curve-band check ([DeckWarning
     *        .CurveOutsideArchetypeBand]) is suppressed entirely (A.3 guard already validated by
     *        the caller against the deck's live role counts).
     */
    fun evaluate(
        roleCounts: Map<RoleKey, Int>,
        lands: Int,
        avgMv: Double,
        skeleton: ResolvedArchetypeSkeleton,
        colorModulation: ColorModulationEntry?,
        curveExemptionActive: Boolean = false,
    ): List<DeckWarning> = buildList {
        skeleton.roleTargets.forEach { (key, band) ->
            val have = roleCounts[key] ?: 0
            if (key in skeleton.antiRoles) {
                if (have > band.max) add(DeckWarning.ArchetypeAntiRolePresent(key, have, band.max))
            } else if (have < band.min) {
                add(DeckWarning.ArchetypeRoleGap(key, have, band.min))
            }
        }

        // D18 UNION land rule.
        val rampCount = roleCounts["ramp"] ?: 0
        val cardDrawCount = roleCounts["card_draw"] ?: 0
        val karstenTarget = karstenLandTarget(skeleton.format, avgMv, rampCount, cardDrawCount)
        val inStaticBand = lands in skeleton.lands.min..skeleton.lands.max
        val inKarstenBand = abs(lands - karstenTarget) <= 2.0
        if (!inStaticBand && !inKarstenBand) {
            when {
                lands < skeleton.lands.min -> add(DeckWarning.TooFewLands(lands, karstenTarget.roundHalfUp()))
                lands > skeleton.lands.max -> add(DeckWarning.TooManyLands(lands, karstenTarget.roundHalfUp()))
                // Inside the static band but outside Karsten by construction cannot reach here
                // (inStaticBand would be true); kept exhaustive defensively.
                else -> add(DeckWarning.TooFewLands(lands, karstenTarget.roundHalfUp()))
            }
        }

        // Curve-band check (suppressed under an active A.3 exemption).
        if (!curveExemptionActive && avgMv !in skeleton.curve.min..skeleton.curve.max) {
            add(DeckWarning.CurveOutsideArchetypeBand(avgMv, skeleton.curve.min, skeleton.curve.max))
        }

        // Color modulation — mana_fix minimum only (Appendix B checks this SEPARATELY from the
        // roles loop above; null skips it entirely — never fabricate a shortage without a known
        // color count, matching D14's fail-closed intent).
        if (colorModulation != null) {
            val have = roleCounts[ArchetypeData.MANA_FIX_KEY] ?: 0
            if (have < colorModulation.manaFix.min) {
                add(DeckWarning.ArchetypeRoleGap(ArchetypeData.MANA_FIX_KEY, have, colorModulation.manaFix.min))
            }
        }
    }

    /**
     * A.4 step-6 budget invariant (structural/golden-test only — never called from live wiring):
     * `lands_ideal + sum(ideal role targets, OVERLAY roles at 50%) <= nonland * 1.30 + lands_ideal`
     * i.e. `sum(ideal role targets, OVERLAY at 50%) <= nonland * OVERLAP`, where `nonland =
     * deckSize - lands_ideal`. Mirrors the Python `budget_check()` exactly (mana_fix is NOT part
     * of [ResolvedArchetypeSkeleton.roleTargets] unless [ArchetypeSkeletonResolver.resolveWithColor]
     * was used — the golden budget tests call [ArchetypeSkeletonResolver.resolve], matching the
     * annex's reference budget numbers exactly).
     *
     * @return `idealSum` and `cap` so the caller/tests can assert `idealSum <= cap` and inspect
     *         the slack, exactly like the Python harness's PASS/slack reporting.
     */
    fun budgetCheck(deckSize: Int, skeleton: ResolvedArchetypeSkeleton): BudgetCheckResult {
        val nonLand = deckSize - skeleton.lands.ideal
        val idealSum = skeleton.roleTargets.entries.sumOf { (key, band) ->
            val weight = if (key in ArchetypeData.OVERLAY_ROLES) 0.5 else 1.0
            band.ideal * weight
        }
        val cap = nonLand * OVERLAP
        return BudgetCheckResult(idealSum = idealSum, cap = cap, nonLand = nonLand)
    }

    data class BudgetCheckResult(val idealSum: Double, val cap: Double, val nonLand: Int) {
        val withinBudget: Boolean get() = idealSum <= cap
        val slack: Double get() = cap - idealSum
    }

    /** The A.4 step-6 multi-role overlap allowance (a card can plausibly fill >1 role). */
    const val OVERLAP = 1.30

    private fun Double.roundHalfUp(): Int = kotlin.math.floor(this + 0.5).toInt()
}
