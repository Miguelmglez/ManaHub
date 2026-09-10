package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.model.Card

// ═══════════════════════════════════════════════════════════════════════════════
//  PlacementScorer — Deck Wizard Commander v3 plan, Phase 2.2/2.3 (D1).
//
//  The wizard's placement objective IS the analysis objective: every term below reuses a P1-P4
//  primitive the Analysis tab already scores with (ArchetypeRoleClassifier role bands,
//  SynergyGraph axis ideals, CurveTargets buckets) -- no new classification/scoring vocabulary of
//  its own. Pure and deterministic: given the same [CandidateProfile]/[PlacementState]/[CommanderPlan]
//  inputs, [marginalGain] always returns the same value.
//
//  D8 filler floor: [marginalGain] returns `null` (never placed) when BOTH roleGain and axisGain are
//  <= 0 -- crucially this is an OR gate for placement eligibility (either term alone is enough), so a
//  theme whose CommanderPlan.targetAxes contribution is empty (CommanderPlanResolver.THEME_TARGET_AXES
//  intentionally omits WHEELS/CLONES_THEFT/VEHICLES/TREASURE -- see that file's own KDoc) never
//  degrades to "nothing can be placed": roleGain (always populated from the resolved skeleton's real
//  role bands, present for every strategy including Custom's generic baseline) alone can still clear
//  the floor. axisGain simply contributes 0 for those themes rather than blocking placement -- the
//  commander's OWN axis profile (already unioned into CommanderPlan.targetAxes by
//  CommanderPlanResolver, independent of the theme table) still lights whatever axes the commander
//  itself touches, so axisGain is never structurally zero for every deck, only for a themeless-and-
//  vanilla-commander corner case, which correctly falls back to role-only placement (D6's own
//  "Custom = baseline bands + commander axes" contract already anticipates this).
// ═══════════════════════════════════════════════════════════════════════════════

object PlacementScorer {

    const val ROLE_WEIGHT = 0.45f
    const val AXIS_WEIGHT = 0.30f
    const val CURVE_WEIGHT = 0.10f
    const val POWER_WEIGHT = 0.10f

    /** Credit while a role sits between [RoleTarget.ideal] and [RoleTarget.max] -- not zero (still
     * a legal, wanted card) but far below the steep pre-ideal slope, so the loop naturally prefers
     * an under-filled band over topping off one already at its ideal. */
    private const val ROLE_PLATEAU_CREDIT = 0.5f

    /** Ported from [com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase]
     * (Motor A's own pip-intensity/mana-shortage penalty, D1 "math unchanged") -- duplicated rather
     * than shared, since that class stays alive for the Casual path (plan's own escape hatch) and
     * this campaign must not touch it. */
    private const val MONO_PIP_PENALTY = 0.06f
    private const val HYBRID_PIP_PENALTY = 0.03f
    private const val PIP_MULTIPLIER_FLOOR = 0.5f
    private const val SHORTAGE_PENALTY_FLOOR = 0.6f

    /** One candidate's precomputed, reusable signal -- built ONCE per unique owned/manual card
     * (plan 2.1: "precompute once per candidate"), never recomputed inside the placement loop. */
    data class CandidateProfile(
        val card: Card,
        val roleConfidence: Map<RoleKey, Float>,
        val axisProfile: SynergyGraph.CardAxisProfile,
        val mvBucketId: String,
        val powerNormalized: Float,
    )

    /** Running placement counters the loop updates incrementally after every placement (plan 2.3). */
    data class PlacementState(
        val roleCounts: Map<RoleKey, Int> = emptyMap(),
        val axisProducerCounts: Map<AxisKey, Int> = emptyMap(),
        val axisPayoffCounts: Map<AxisKey, Int> = emptyMap(),
        val curveBucketCounts: Map<String, Int> = emptyMap(),
    )

    /** [curveTargets] must already carry [CurveTargets.CurveBucketTarget.targetCount] (i.e. built
     * with a real `nonLandCount`, unlike [CommanderPlan.curveTargets] itself -- see
     * `BuildCommanderDeckUseCase`'s own note on why it re-derives this list with the resolved
     * non-land target rather than trusting the plan's fraction-only list). */
    fun marginalGain(
        candidate: CandidateProfile,
        state: PlacementState,
        plan: CommanderPlan,
        curveTargets: List<CurveTargets.CurveBucketTarget>,
        axisIdeals: Map<AxisKey, SynergyGraph.AxisIdeal>,
        pipFactor: Float,
    ): Float? {
        // Hard filter (plan 2.2): a card matching one of the skeleton's anti-roles is never placed
        // by the engine, regardless of how well it scores elsewhere.
        val hasAntiRole = candidate.roleConfidence.any { (role, confidence) ->
            confidence > 0f && role in plan.skeleton.antiRoles
        }
        if (hasAntiRole) return null

        val roleGain = roleGain(candidate, plan, state.roleCounts)
        val axisGain = axisGain(candidate, plan, axisIdeals, state.axisProducerCounts, state.axisPayoffCounts)

        // D8 filler floor -- OR, not AND (see this object's header).
        if (roleGain <= 0f && axisGain <= 0f) return null

        val curveGain = curveGain(candidate, curveTargets, state.curveBucketCounts)
        val powerPrior = candidate.powerNormalized.coerceIn(0f, 1f)

        val weighted = roleGain * ROLE_WEIGHT + axisGain * AXIS_WEIGHT + curveGain * CURVE_WEIGHT + powerPrior * POWER_WEIGHT
        return weighted * pipFactor.coerceIn(0f, 1f)
    }

    /**
     * Ideal-weighted role gain (plan 2.2, mirrors P3's own ideal-weighting): for every role the
     * card matches at confidence > 0 (the classifier's own matchers already embed their per-role
     * floor -- see [ArchetypeRoleClassifier.classify]'s own D11 discipline -- so "confidence > 0"
     * IS "clears the classifier's own floor", no second threshold needed here), the raw count-gain
     * `(ideal - current)` scaled by the match confidence while under ideal; a flat, much smaller
     * [ROLE_PLATEAU_CREDIT] while between ideal and max; zero at/above max. Using the RAW count gain
     * (not a `[0,1]` normalized fraction) is what "weighted by ideal" means in practice: a role with
     * a big ideal (e.g. ramp=12) contributes proportionally more total gain than a role with a small
     * one (e.g. tutor=3) at the same fractional shortfall, exactly mirroring how P3's own
     * ideal-weighted average privileges high-ideal bands.
     */
    private fun roleGain(candidate: CandidateProfile, plan: CommanderPlan, roleCounts: Map<RoleKey, Int>): Float {
        var gain = 0f
        candidate.roleConfidence.forEach { (role, confidence) ->
            if (confidence <= 0f) return@forEach
            val target = plan.skeleton.roleTargets[role] ?: return@forEach
            if (role in plan.skeleton.antiRoles) return@forEach // never a positive contributor either
            val current = roleCounts[role] ?: 0
            gain += when {
                current < target.ideal -> confidence * (target.ideal - current)
                current < target.max -> confidence * ROLE_PLATEAU_CREDIT
                else -> 0f
            }
        }
        return gain
    }

    /**
     * Producer/payoff axis gain (plan 2.2): mirrors [SynergyGraph]'s own `health = min(1,
     * producer/producerIdeal) * min(1, payoff/payoffIdeal)` multiplicative shape -- a payoff's gain
     * is scaled by how filled the SAME axis's producer side already is (`producerFillRatio`), so the
     * loop cannot stack payoffs onto an axis with zero producers (mirrors axis health's own "payoffs
     * with no producers score zero" property). Axes in [CommanderPlan.targetAxes] count at full
     * weight; any other axis the card happens to touch counts at half weight (plan 2.2: "edges on
     * non-target but live axes count at half weight" -- approximated here as "any other axis the
     * candidate touches", since full liveness requires a built [DeckSynergyGraph] the incremental
     * loop does not maintain per-candidate).
     */
    private fun axisGain(
        candidate: CandidateProfile,
        plan: CommanderPlan,
        axisIdeals: Map<AxisKey, SynergyGraph.AxisIdeal>,
        producerCounts: Map<AxisKey, Int>,
        payoffCounts: Map<AxisKey, Int>,
    ): Float {
        var gain = 0f
        val profile = candidate.axisProfile

        profile.produces.forEach { (axis, confidence) ->
            if (confidence <= 0f) return@forEach
            val ideal = axisIdeals[axis] ?: return@forEach
            val current = producerCounts[axis] ?: 0
            if (current >= ideal.producerIdeal) return@forEach
            val weight = if (axis in plan.targetAxes) 1f else 0.5f
            gain += confidence * (ideal.producerIdeal - current) * weight
        }

        profile.consumes.forEach { (axis, confidence) ->
            if (confidence <= 0f) return@forEach
            val ideal = axisIdeals[axis] ?: return@forEach
            val currentPayoff = payoffCounts[axis] ?: 0
            if (currentPayoff >= ideal.payoffIdeal) return@forEach
            val currentProducer = producerCounts[axis] ?: 0
            val producerFillRatio = (currentProducer.toFloat() / ideal.producerIdeal).coerceIn(0f, 1f)
            if (producerFillRatio <= 0f) return@forEach // "producers first" -- see this fun's KDoc
            val weight = if (axis in plan.targetAxes) 1f else 0.5f
            gain += confidence * (ideal.payoffIdeal - currentPayoff) * producerFillRatio * weight
        }

        return gain
    }

    /** Distance-based curve gain (plan 2.2): how far the candidate's own MV bucket is BELOW its
     * remaining target -- zero once the bucket is full or over target. Cheap and low-weight by
     * design (see [CurveTargets]'s own KDoc: this is new derived logic, not a mirror of any real
     * `evaluateCurve` computation). */
    private fun curveGain(
        candidate: CandidateProfile,
        curveTargets: List<CurveTargets.CurveBucketTarget>,
        curveBucketCounts: Map<String, Int>,
    ): Float {
        val bucket = curveTargets.firstOrNull { it.bucketId == candidate.mvBucketId } ?: return 0f
        val target = bucket.targetCount ?: return 0f
        val current = curveBucketCounts[candidate.mvBucketId] ?: 0
        return if (current < target) (target - current).toFloat() else 0f
    }

    /**
     * [pipFactor] input (plan 2.2: "pipIntensityMultiplier + manaBaseShortagePenalty logic MOVED
     * from Motor A, math unchanged") -- ported verbatim from
     * [com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase]'s private
     * pair of the same name. [maxSinglePipIntensity]/[sourcesByColor]/[requiredSources] come from
     * [ManaBaseAnalyzer]'s own public per-colour helpers (never `analyze()`, which needs a Motor-A
     * `DeckProfile` this Commander path must not depend on -- D1).
     */
    fun pipFactor(
        card: Card,
        colorCount: Int,
        manaBaseAnalyzer: ManaBaseAnalyzer,
        sourcesByColor: Map<ManaColor, Int>,
        totalLands: Int,
    ): Float {
        if (colorCount < 2) return 1f
        val ownEntry = listOf(DeckEntry(card = card, quantity = 1, isOwned = true, isSideboard = false))
        val perCardIntensity = manaBaseAnalyzer.maxSinglePipIntensity(ownEntry)
        val (color, intensity) = perCardIntensity.maxByOrNull { it.value } ?: return 1f
        if (intensity <= 0) return 1f

        val isHybridFlexible = card.manaCost?.contains('/') == true
        val perPipPenalty = if (isHybridFlexible) HYBRID_PIP_PENALTY else MONO_PIP_PENALTY
        val intensityPenalty = (intensity - 1) * perPipPenalty * (colorCount - 1)
        val intensityMultiplier = (1f - intensityPenalty).coerceIn(PIP_MULTIPLIER_FLOOR, 1f)

        // No land base exists yet during the non-land placement loop (plan §3: lands are filled
        // AFTER placement, D10) -- the shortage term is meaningless before any land is placed
        // (every multi-pip card would read as "shorted" against zero sources), so it is skipped
        // entirely (neutral 1f) until a real [totalLands] > 0 is passed (the refinement pass, which
        // runs after land fill).
        if (totalLands <= 0) return intensityMultiplier

        val have = sourcesByColor[color] ?: 0
        val need = manaBaseAnalyzer.requiredSources(intensity, totalLands)
        val shortagePenalty = if (have >= need || need <= 0) {
            1f
        } else {
            val shortageRatio = (have.toFloat() / need).coerceIn(0f, 1f)
            (SHORTAGE_PENALTY_FLOOR + (1f - SHORTAGE_PENALTY_FLOOR) * shortageRatio).coerceIn(SHORTAGE_PENALTY_FLOOR, 1f)
        }
        return intensityMultiplier * shortagePenalty
    }

    /** MV bucket id, mirroring [CurveTargets]/`AnalysisEngine.evaluateCurve`'s own `mv:N`/`mv:7plus`
     * convention exactly (0..6 exact, 7+ collapsed). */
    fun mvBucketId(card: Card): String {
        val mv = card.cmc.toInt()
        return if (mv >= 7) "mv:7plus" else "mv:$mv"
    }
}
