package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.model.Card

/**
 * The wizard's placement objective IS the analysis objective (Deck Wizard Commander v3 plan, Phase
 * 2.2/2.3, D1) -- reuses the P1-P4 primitives the Analysis tab already scores with. Pure and
 * deterministic; D8 filler floor: [marginalGain] returns `null` when both roleGain and axisGain are
 * `<= 0` (an OR gate, so a theme with no mapped axis still places on role gain alone).
 *
 * Rescaled W6 Task 2 (G11/E5): every gain term is normalised to `[0,1]` before weighting (was a raw,
 * unbounded count-gap, drowning out `powerPrior`'s real `[0,1]` signal), and a card touching several
 * live needs combines them with diminishing returns ([combineDiminishing]) instead of summing raw
 * gaps -- rewarding real versatility without one card counting as filling several slots at once.
 */
object PlacementScorer {

    /** Calibrated by hand against [com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich]
     * (W6 Task 2, normative per plan §6 -- never fit to a real user collection). ROLE/AXIS raised to
     * equal weight (was 0.45/0.30) because axis alignment (the commander's own produce/consume
     * profile) turned out to be at least as strong a skeleton-specific signal as role bands once
     * both are on a comparable [0,1] scale; CURVE/POWER stayed at their pre-normalisation ratio.
     * Known residual (reported, not hidden -- see [com.mmg.manahub.feature.decks.domain.template
     * .MockCollectionRichReconstructionTest]'s own KDoc): this weighting resolves 3 of 4 fixtures'
     * Custom-build macro exactly and leaves the 4th (Edgar/AGGRO) at a razor-thin ambiguous margin
     * (~0.0796 vs the 0.08 decisive threshold) rather than a clean win -- real, measurable progress
     * over the pre-Task-2 baseline (which read a confidently WRONG MIDRANGE there), not a full close. */
    const val ROLE_WEIGHT = 0.40f
    const val AXIS_WEIGHT = 0.40f
    const val CURVE_WEIGHT = 0.10f
    const val POWER_WEIGHT = 0.10f

    /** Deck Wizard Commander v5 (S7): a role's overflow past [RoleTarget.max] is the negative
     * mirror of [roleGain]'s own under-ideal credit, so it is charged at the SAME weight
     * ([ROLE_WEIGHT]) rather than a separately-tuned constant — a card whose overflow is its only
     * meaningful signal on a role loses exactly what a real need on that same role would have
     * earned it, never more (other channels -- axis/curve/power -- are untouched by this penalty). */
    const val ROLE_OVERFLOW_WEIGHT = ROLE_WEIGHT

    /** Normalised credit while a role sits between [RoleTarget.ideal] and [RoleTarget.max] -- not
     * zero (still a legal, wanted card) but below a typical under-ideal fraction, so the loop
     * naturally prefers an under-filled band over topping off one already at its ideal. */
    private const val ROLE_PLATEAU_CREDIT = 0.01f

    /** W6 Task 5 (E8) -- a card the user previously chose on the Choice screen gets this bonus
     * added to its ALREADY-computed marginal gain (never contributes on its own): bounded well
     * below a typical under-ideal role/axis contribution (roughly a third of [ROLE_PLATEAU_CREDIT]
     * scaled by [ROLE_WEIGHT]), same "bias, never override" discipline as the retired community
     * prior and [com.mmg.manahub.feature.decks.domain.engine.CommanderArchetypeBias] -- it can
     * reorder a genuine near-tie, it can never satisfy the D8 filler floor or beat a real band need. */
    const val PREFERENCE_BONUS = 0.01f

    /** [combineDiminishing]'s per-step decay: the 2nd-strongest need counts at 40% of its own value,
     * the 3rd at 16%, etc. Judgment call verified by hand against [MockCollectionRich] (W6 Task 2):
     * strong enough that a genuinely multi-purpose card still separates from a single-purpose one,
     * weak enough that it does not flatten the skeleton-specific differentiation a pure product
     * combinator did (see [combineDiminishing]'s own KDoc for the regression that motivated this). */
    private const val SECONDARY_NEED_DECAY = 0.4f

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
        val overflowPenalty = overflowCost(candidate, plan, state.roleCounts) * ROLE_OVERFLOW_WEIGHT

        val weighted = (
            roleGain * ROLE_WEIGHT + axisGain * AXIS_WEIGHT + curveGain * CURVE_WEIGHT + powerPrior * POWER_WEIGHT - overflowPenalty
            ).coerceAtLeast(0f)
        return weighted * pipFactor.coerceIn(0f, 1f)
    }

    /** S7 -- whether placing [candidate] on top of [roleCounts] pushes at least one non-anti role
     * it matches past its own [RoleTarget.max]. Separate from [overflowCost] (a soft, per-candidate
     * penalty) because "loses to any on-plan alternative" needs cross-candidate knowledge (does a
     * non-overflowing option exist THIS iteration) that a single-candidate score cannot carry on
     * its own -- the caller (the placement loop) hard-excludes an overflowing candidate whenever
     * this is true for it AND a non-overflowing alternative clears the D8 floor. */
    fun causesRoleOverflow(candidate: CandidateProfile, plan: CommanderPlan, roleCounts: Map<RoleKey, Int>): Boolean =
        candidate.roleConfidence.any { (role, confidence) ->
            confidence > 0f && role !in plan.skeleton.antiRoles &&
                plan.skeleton.roleTargets[role]?.let { (roleCounts[role] ?: 0) >= it.max } == true
        }

    /** S7 -- normalised overflow depth of the WORST offending role this candidate matches (never
     * combined across roles the way [combineDiminishing] combines positive gains: one badly
     * overflowing role is already the whole problem, a second one does not make placing the card
     * twice as bad). `(current - max + 1)` counts the copy THIS placement would itself add;
     * dividing by `max` keeps it on the same `[0,1]` scale [roleGain]'s own fraction uses. */
    private fun overflowCost(candidate: CandidateProfile, plan: CommanderPlan, roleCounts: Map<RoleKey, Int>): Float {
        val costs = candidate.roleConfidence.mapNotNull { (role, confidence) ->
            if (confidence <= 0f || role in plan.skeleton.antiRoles) return@mapNotNull null
            val target = plan.skeleton.roleTargets[role] ?: return@mapNotNull null
            val current = roleCounts[role] ?: 0
            if (current < target.max) return@mapNotNull null
            val overflowDepth = (current - target.max + 1).toFloat()
            confidence * (overflowDepth / target.max.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
        return costs.maxOrNull() ?: 0f
    }

    /**
     * Combines several independent [0,1] need-contributions into one [0,1] value with diminishing
     * returns (W6 Task 2, E5): the STRONGEST contribution counts at full weight, each subsequent one
     * (sorted descending) counts at a geometrically decaying fraction ([SECONDARY_NEED_DECAY] per
     * step) of its own value, and the total is capped at 1. A card never counts as filling several
     * slots at once (closing G11's double-counting defect) while a genuine second/third live need
     * still raises its score over a single-purpose equivalent (rewarding real versatility).
     *
     * Chosen over the mathematically simpler `1 - Π(1 - contribution)` (tried first, W6 Task 2):
     * that formula saturates too fast once a card touches 2-3 bands — and nearly every real card
     * touches multiple SKELETON-INDEPENDENT bands that most archetypes share (ramp/removal/draw are
     * wanted almost everywhere) — so it flattened the composition differences BETWEEN skeletons,
     * regressing [com.mmg.manahub.feature.decks.domain.template.MockCollectionRichReconstructionTest]
     * ("Custom build resolves the fixture's own expected macro") from 1 real mismatch to 3. The
     * geometric-decay version keeps the single dominant, skeleton-SPECIFIC need in control of the
     * score (preserving differentiation between what an AGGRO skeleton and a MIDRANGE skeleton each
     * reward) while still giving a genuinely multi-purpose card a real, bounded edge.
     */
    private fun combineDiminishing(contributions: List<Float>): Float {
        if (contributions.isEmpty()) return 0f
        val sorted = contributions.map { it.coerceIn(0f, 1f) }.sortedDescending()
        var total = 0f
        var weight = 1f
        sorted.forEach { c ->
            total += c * weight
            weight *= SECONDARY_NEED_DECAY
        }
        return total.coerceIn(0f, 1f)
    }

    /**
     * Normalised role gain (W6 Task 2, rescaled from the raw count-gap version -- see this file's
     * header): for every role the card matches at confidence > 0 (the classifier's own matchers
     * already embed their per-role floor -- see [ArchetypeRoleClassifier.classify]'s own D11
     * discipline), the FRACTION of the remaining need one more copy fills
     * (`(ideal - current) / ideal`, in [0,1] regardless of the band's absolute size -- this is what
     * closes G11(b): a big-ideal band like ramp=12 no longer dominates a small one like tutor=3 just
     * because its raw gap is larger) while under ideal; a flat, smaller [ROLE_PLATEAU_CREDIT] while
     * between ideal and max; zero at/above max. Multiple matched roles combine via
     * [combineDiminishing] rather than summing (G11(c): a card in three under-ideal bands no longer
     * collects all three full gaps while occupying one slot).
     */
    private fun roleGain(candidate: CandidateProfile, plan: CommanderPlan, roleCounts: Map<RoleKey, Int>): Float {
        val contributions = candidate.roleConfidence.mapNotNull { (role, confidence) ->
            if (confidence <= 0f) return@mapNotNull null
            val target = plan.skeleton.roleTargets[role] ?: return@mapNotNull null
            if (role in plan.skeleton.antiRoles) return@mapNotNull null // never a positive contributor either
            val current = roleCounts[role] ?: 0
            when {
                current < target.ideal -> confidence * ((target.ideal - current).toFloat() / target.ideal).coerceIn(0f, 1f)
                current < target.max -> confidence * ROLE_PLATEAU_CREDIT
                else -> null
            }
        }
        return combineDiminishing(contributions)
    }

    /**
     * Normalised producer/payoff axis gain (W6 Task 2, rescaled -- see roleGain's own KDoc for the
     * same normalisation rationale): mirrors [SynergyGraph]'s own `health = min(1,
     * producer/producerIdeal) * min(1, payoff/payoffIdeal)` multiplicative shape -- a payoff's gain
     * is scaled by how filled the SAME axis's producer side already is (`producerFillRatio`), so the
     * loop cannot stack payoffs onto an axis with zero producers. Axes in [CommanderPlan.targetAxes]
     * count at full weight; any other axis the card happens to touch counts at half weight. Every
     * produce/consume edge the card touches combines via [combineDiminishing], never a raw sum.
     */
    private fun axisGain(
        candidate: CandidateProfile,
        plan: CommanderPlan,
        axisIdeals: Map<AxisKey, SynergyGraph.AxisIdeal>,
        producerCounts: Map<AxisKey, Int>,
        payoffCounts: Map<AxisKey, Int>,
    ): Float {
        val profile = candidate.axisProfile
        val contributions = mutableListOf<Float>()

        profile.produces.forEach { (axis, confidence) ->
            if (confidence <= 0f) return@forEach
            val ideal = axisIdeals[axis] ?: return@forEach
            if (ideal.producerIdeal <= 0) return@forEach
            val current = producerCounts[axis] ?: 0
            if (current >= ideal.producerIdeal) return@forEach
            // S8/H9: a bare type-line density producer (SPELLS/ARTIFACTS/ENCHANTMENTS -- no
            // dedicated role backing its credit for THIS axis) only counts once the plan actually
            // means to build the axis out (targeted, or a payoff already placed) -- otherwise it
            // is exactly the "structurally connected to nothing" leak that read off-plan cards as
            // on-plan (see AnalysisEngine's own offplan 3-way split for the final-analysis shape
            // this mirrors).
            val densityRole = SynergyGraph.DENSITY_PRODUCER_AXES[axis]
            val isBareDensity = densityRole != null && (candidate.roleConfidence[densityRole] ?: 0f) <= 0f
            if (isBareDensity && axis !in plan.targetAxes && (payoffCounts[axis] ?: 0) <= 0) return@forEach
            val weight = if (axis in plan.targetAxes) 1f else 0.5f
            val fraction = ((ideal.producerIdeal - current).toFloat() / ideal.producerIdeal).coerceIn(0f, 1f)
            contributions += confidence * fraction * weight
        }

        profile.consumes.forEach { (axis, confidence) ->
            if (confidence <= 0f) return@forEach
            val ideal = axisIdeals[axis] ?: return@forEach
            if (ideal.payoffIdeal <= 0 || ideal.producerIdeal <= 0) return@forEach
            val currentPayoff = payoffCounts[axis] ?: 0
            if (currentPayoff >= ideal.payoffIdeal) return@forEach
            val currentProducer = producerCounts[axis] ?: 0
            val producerFillRatio = (currentProducer.toFloat() / ideal.producerIdeal).coerceIn(0f, 1f)
            if (producerFillRatio <= 0f) return@forEach // "producers first" -- see this fun's KDoc
            val weight = if (axis in plan.targetAxes) 1f else 0.5f
            val fraction = ((ideal.payoffIdeal - currentPayoff).toFloat() / ideal.payoffIdeal).coerceIn(0f, 1f)
            contributions += confidence * fraction * producerFillRatio * weight
        }

        return combineDiminishing(contributions)
    }

    /** Normalised curve gain (W6 Task 2, rescaled): the FRACTION of the deficit bucket's remaining
     * target one more copy fills -- zero once the bucket is full or over target. A candidate occupies
     * exactly one MV bucket, so no combinator is needed here. Cheap and low-weight by design (see
     * [CurveTargets]'s own KDoc: this is new derived logic, not a mirror of any real `evaluateCurve`
     * computation). */
    private fun curveGain(
        candidate: CandidateProfile,
        curveTargets: List<CurveTargets.CurveBucketTarget>,
        curveBucketCounts: Map<String, Int>,
    ): Float {
        val bucket = curveTargets.firstOrNull { it.bucketId == candidate.mvBucketId } ?: return 0f
        val target = bucket.targetCount ?: return 0f
        if (target <= 0) return 0f
        val current = curveBucketCounts[candidate.mvBucketId] ?: 0
        return if (current < target) ((target - current).toFloat() / target).coerceIn(0f, 1f) else 0f
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

        // No REAL land base exists yet during the non-land placement loop (plan §3: lands are
        // filled AFTER placement, D10). W6 Task 2 (G11d) feeds this an ESTIMATED manabase (an even
        // split of the deck's own land target across its identity colours) instead of the previous
        // permanently-inert emptyMap()/0 -- see BuildCommanderDeckUseCase's own call site KDoc --
        // so the shortage term is live throughout the whole loop rather than always neutral. Still
        // skipped entirely (neutral 1f) when a caller genuinely has no land plan yet (totalLands<=0,
        // e.g. a test exercising this function in isolation).
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
