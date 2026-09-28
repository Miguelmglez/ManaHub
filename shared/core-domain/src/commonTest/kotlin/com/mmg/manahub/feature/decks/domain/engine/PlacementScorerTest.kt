package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Wizard Commander v3 plan, Phase 2.2 gate: [PlacementScorer]'s five documented behaviors --
 * role plateau, anti-role hard filter, axis producer-before-payoff ordering, curve deficit, and the
 * D8 filler floor. Uses a minimal hand-built [WizardPlan] rather than a real
 * [WizardPlanResolver.resolve] call so each behavior is isolated from skeleton-resolution noise.
 * Deck Wizard 60-card wave (v6), plan §5 Phase 1.4: also covers [PlacementScorer.marginalGain]'s
 * `copyIndex` consistency-credit behavior (S5).
 */
class PlacementScorerTest {

    private val identity = setOf(ManaColor.G)

    private fun plan(
        roleTargets: Map<RoleKey, RoleTarget>,
        antiRoles: Set<RoleKey> = emptySet(),
        targetAxes: Set<AxisKey> = emptySet(),
    ): WizardPlan {
        val skeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER,
            archetype = null,
            posture = null,
            themes = emptyList(),
            identity = identity,
            deckFormat = DeckFormat.COMMANDER,
        )
        val patchedSkeleton = skeleton.copy(
            roleTargets = skeleton.roleTargets + roleTargets,
            antiRoles = skeleton.antiRoles + antiRoles,
        )
        return WizardPlan(
            skeleton = patchedSkeleton,
            targetAxes = targetAxes,
            curveTargets = CurveTargets.forSkeleton(patchedSkeleton, nonLandCount = 60),
            landBand = patchedSkeleton.lands,
            manaFixBand = patchedSkeleton.manaFixTarget(),
        )
    }

    private fun profile(
        card: Card = card(id = "cand-1"),
        roleConfidence: Map<RoleKey, Float> = emptyMap(),
        produces: Map<AxisKey, Float> = emptyMap(),
        consumes: Map<AxisKey, Float> = emptyMap(),
        mvBucketId: String = "mv:2",
    ): PlacementScorer.CandidateProfile = PlacementScorer.CandidateProfile(
        card = card,
        roleConfidence = roleConfidence,
        axisProfile = SynergyGraph.CardAxisProfile(card.scryfallId, 1, produces, consumes, emptyMap()),
        mvBucketId = mvBucketId,
        powerNormalized = 0.5f,
    )

    private fun axisIdeals(vararg pairs: Pair<AxisKey, Int>): Map<AxisKey, SynergyGraph.AxisIdeal> =
        pairs.associate { (axis, ideal) -> axis to SynergyGraph.AxisIdeal(producerIdeal = ideal, payoffIdeal = ideal) }

    @Test
    fun `role gain has a small plateau credit between ideal and max, and zero at or above max`() {
        val myPlan = plan(roleTargets = mapOf("ramp" to RoleTarget(min = 2, ideal = 8, max = 12)))
        val underIdeal = profile(roleConfidence = mapOf("ramp" to 1f))
        val atIdeal = PlacementScorer.PlacementState(roleCounts = mapOf("ramp" to 8))
        val atMax = PlacementScorer.PlacementState(roleCounts = mapOf("ramp" to 12))

        val gainUnderIdeal = PlacementScorer.marginalGain(
            underIdeal, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f,
        )
        val gainAtIdealPlateau = PlacementScorer.marginalGain(
            underIdeal, atIdeal, myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f,
        )
        val gainAtMax = PlacementScorer.marginalGain(
            underIdeal, atMax, myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f,
        )

        assertTrue((gainUnderIdeal ?: 0f) > (gainAtIdealPlateau ?: 0f), "gain below ideal must exceed the plateau credit")
        assertNull(gainAtMax, "a role already at max contributes zero roleGain and zero axisGain -> filler floor -> null")
    }

    @Test
    fun `anti-role is a hard filter regardless of how well the card otherwise scores`() {
        val myPlan = plan(
            roleTargets = mapOf("ramp" to RoleTarget(min = 2, ideal = 8, max = 12)),
            antiRoles = setOf("ramp"),
        )
        val candidate = profile(roleConfidence = mapOf("ramp" to 1f))
        val gain = PlacementScorer.marginalGain(
            candidate, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f,
        )
        assertNull(gain, "a role in the skeleton's antiRoles must never be placed by the engine")
    }

    @Test
    fun `payoff axis gain rises only once producers exist on the same axis`() {
        val myPlan = plan(roleTargets = emptyMap(), targetAxes = setOf("TOKENS"))
        val ideals = axisIdeals("TOKENS" to 10)
        val payoffCandidate = profile(consumes = mapOf("TOKENS" to 1f))

        val noProducers = PlacementScorer.PlacementState()
        val someProducers = PlacementScorer.PlacementState(axisProducerCounts = mapOf("TOKENS" to 5))

        val gainNoProducers = PlacementScorer.marginalGain(
            payoffCandidate, noProducers, myPlan, myPlan.curveTargets, ideals, pipFactor = 1f,
        )
        val gainWithProducers = PlacementScorer.marginalGain(
            payoffCandidate, someProducers, myPlan, myPlan.curveTargets, ideals, pipFactor = 1f,
        )

        assertNull(gainNoProducers, "a payoff with zero producers on its own axis clears no role/axis gain -> filler floor")
        assertTrue((gainWithProducers ?: 0f) > 0f, "the same payoff must gain once producers exist")
    }

    @Test
    fun `curve gain favors the deficit bucket and never applies to a role-only candidate's other buckets`() {
        val myPlan = plan(roleTargets = mapOf("ramp" to RoleTarget(min = 2, ideal = 8, max = 12)))
        val deficitBucketCandidate = profile(roleConfidence = mapOf("ramp" to 1f), mvBucketId = "mv:1")
        val fullBucketCandidate = profile(roleConfidence = mapOf("ramp" to 1f), mvBucketId = "mv:1")

        val stateWithFullBucket = PlacementScorer.PlacementState(curveBucketCounts = mapOf("mv:1" to 9_999))
        val gainDeficit = PlacementScorer.marginalGain(
            deficitBucketCandidate, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f,
        )
        val gainFullBucket = PlacementScorer.marginalGain(
            fullBucketCandidate, stateWithFullBucket, myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f,
        )
        assertTrue((gainDeficit ?: 0f) > (gainFullBucket ?: 0f), "an already-saturated MV bucket must contribute zero curveGain")
    }

    @Test
    fun `filler floor rejects a candidate with zero role gain and zero axis gain`() {
        val myPlan = plan(roleTargets = emptyMap())
        val filler = profile() // no roles, no axis edges at all
        val gain = PlacementScorer.marginalGain(
            filler, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f,
        )
        assertNull(gain, "D8: a candidate with zero role gain and zero axis gain is never placed")
    }

    // ── S5 (v6, plan §5 Phase 1.4): copyIndex consistency credit ──────────────────────────────

    @Test
    fun `copy 1 of a high-power card with role gain beats copy 0 of a low-power card with the same role gain`() {
        val myPlan = plan(roleTargets = mapOf("ramp" to RoleTarget(2, 8, 12)))
        val strong = profile(card = card(id = "strong"), roleConfidence = mapOf("ramp" to 1f)).copy(powerNormalized = 0.9f)
        val weak = profile(card = card(id = "weak"), roleConfidence = mapOf("ramp" to 1f)).copy(powerNormalized = 0.2f)

        val gainStrongCopy1 = PlacementScorer.marginalGain(
            strong, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f, copyIndex = 1,
        )
        val gainWeakCopy0 = PlacementScorer.marginalGain(
            weak, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f, copyIndex = 0,
        )
        assertTrue((gainStrongCopy1 ?: 0f) > (gainWeakCopy0 ?: 0f))
    }

    @Test
    fun `copy 1 with zero role and zero axis gain is still null -- D8 floor unaffected by copyIndex`() {
        val myPlan = plan(roleTargets = emptyMap())
        val filler = profile()
        val gain = PlacementScorer.marginalGain(
            filler, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f, copyIndex = 1,
        )
        assertNull(gain, "D8 filler floor applies regardless of copyIndex")
    }

    @Test
    fun `copyIndex 0 is byte-identical to the pre-1_4 call`() {
        val myPlan = plan(roleTargets = mapOf("ramp" to RoleTarget(2, 8, 12)))
        val candidate = profile(roleConfidence = mapOf("ramp" to 1f))
        val withoutCopyIndex = PlacementScorer.marginalGain(
            candidate, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f,
        )
        val withCopyIndexZero = PlacementScorer.marginalGain(
            candidate, PlacementScorer.PlacementState(), myPlan, myPlan.curveTargets, emptyMap(), pipFactor = 1f, copyIndex = 0,
        )
        assertEquals(withoutCopyIndex, withCopyIndexZero)
    }
}
