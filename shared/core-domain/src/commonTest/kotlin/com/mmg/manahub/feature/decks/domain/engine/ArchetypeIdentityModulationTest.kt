package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan, WS9.2 — behavioral tests for
 * [ArchetypeSkeletonResolver.resolveWithColor]'s color-IDENTITY-aware pass
 * ([ArchetypeSkeletonResolver] private `applyIdentityModulation`, exercised only through the
 * public [ArchetypeSkeletonResolver.resolveWithColor] entry point). Compares against
 * [ArchetypeSkeletonResolver.resolveWithColorCount] (the identity-UNAWARE "before" snapshot) to
 * isolate exactly what the identity pass changed.
 */
class ArchetypeIdentityModulationTest {

    /** Sum of `ideal` across every [ColorRoleAffinity.INTERACTION_ROLES] member actually present
     * in [skeleton]'s roles -- the WS9.2 acceptance criterion's "interaction budget". */
    private fun interactionIdealSum(skeleton: ResolvedArchetypeSkeleton): Int =
        ColorRoleAffinity.INTERACTION_ROLES.sumOf { key -> skeleton.roleTargets[key]?.ideal ?: 0 }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Literal WS9.2 acceptance criterion: G/W CONTROL Commander can't cast counterspells.
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun gwControlDeckGetsAnImpossibleToWarnCounterspellBandAndPreservesInteractionBudget() {
        val before = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL, colorCount = 2,
        )
        val after = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL,
            identity = setOf(ManaColor.G, ManaColor.W),
        )

        // CONTROL's own counterspell demand (min=6) is impossible for G/W -- both colors are
        // ABSENT for "counterspell" (ColorRoleAffinityTest.counterspellIsOnlyFeasibleForBlue).
        // A [0,0,tolerance] band means ArchetypeEvaluator.evaluate's `have < band.min` check can
        // NEVER fire -- the warning becomes impossible by construction.
        val counterspellBefore = before.roleTargets.getValue("counterspell")
        val counterspellAfter = after.roleTargets.getValue("counterspell")
        assertEquals(0, counterspellAfter.min)
        assertEquals(0, counterspellAfter.ideal)
        assertEquals(counterspellBefore.max, counterspellAfter.max, "tolerance must be the PRIOR max, never a hardcoded ceiling")

        // The zeroed ideal (9) must reappear on FEASIBLE interaction roles (removal_spot,
        // removal_mass, recursion are all W/G-feasible per the plan's matrix), never vanish.
        assertTrue(after.roleTargets.getValue("removal_spot").ideal > before.roleTargets.getValue("removal_spot").ideal)
        assertTrue(after.roleTargets.getValue("removal_mass").ideal > before.roleTargets.getValue("removal_mass").ideal)
        assertTrue(after.roleTargets.getValue("recursion").ideal > before.roleTargets.getValue("recursion").ideal)

        // The core acceptance criterion: total interaction-role ideal is PRESERVED, not shrunk.
        assertEquals(
            interactionIdealSum(before), interactionIdealSum(after),
            "identity redistribution must move the budget, never lose it",
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Substitute-only reduction + redistribution to the SOLE feasible recipient.
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun monoGreenMidrangeReducesSubstituteRolesAndRedistributesInfeasibleOnesToRecursionOnly() {
        val before = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.MIDRANGE, colorCount = 1,
        )
        val after = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.MIDRANGE, identity = setOf(ManaColor.G),
        )

        // removal_spot is SUBSTITUTE-only for mono-Green (fight/bite) -- reduced, not zeroed. min
        // (8) exceeds ideal*0.5 (5), so the reduction floors at min per the documented
        // `.coerceAtLeast(band.min)` guard -- the band collapses to a single point (8,8,8), which
        // is still structurally valid (min <= ideal <= max) and strictly SMALLER than before.
        val removalSpotBefore = before.roleTargets.getValue("removal_spot")
        val removalSpotAfter = after.roleTargets.getValue("removal_spot")
        assertEquals(removalSpotBefore.min, removalSpotAfter.min, "SUBSTITUTE reduction never touches min")
        assertTrue(removalSpotAfter.ideal <= removalSpotBefore.ideal)
        assertEquals(removalSpotAfter.min, removalSpotAfter.ideal, "min>ideal*factor collapses the band to a point")

        // removal_mass is ABSENT for Green (inherited from GENERIC, min=4) -- fully infeasible,
        // zeroed. Its only FEASIBLE recipient among INTERACTION_ROLES present in this skeleton is
        // "recursion" (removal_spot is present but SUBSTITUTE-only, i.e. not a feasible
        // recipient; counterspell/protection aren't in this skeleton at all).
        val removalMassAfter = after.roleTargets.getValue("removal_mass")
        assertEquals(0, removalMassAfter.min)
        assertEquals(0, removalMassAfter.ideal)

        val recursionBefore = before.roleTargets.getValue("recursion")
        val recursionAfter = after.roleTargets.getValue("recursion")
        assertEquals(
            recursionBefore.ideal + before.roleTargets.getValue("removal_mass").ideal,
            recursionAfter.ideal,
            "recursion is the sole feasible recipient -- it must absorb removal_mass's ENTIRE zeroed ideal",
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Untabled roles and unknown/colorless identities are structural no-ops.
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun untabledRolesAreNeverTouchedByIdentityModulation() {
        val before = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.MIDRANGE, colorCount = 1,
        )
        val after = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.MIDRANGE, identity = setOf(ManaColor.G),
        )
        // "finisher" has no ColorRoleAffinity entry at all -- must be byte-identical.
        assertEquals(before.roleTargets.getValue("finisher"), after.roleTargets.getValue("finisher"))
    }

    @Test
    fun emptyIdentityIsAStructuralNoOpBeyondColorCountModulation() {
        val countOnly = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL, colorCount = 0,
        )
        val withEmptyIdentity = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL, identity = emptySet(),
        )
        assertEquals(countOnly, withEmptyIdentity, "unknown/empty identity must skip identity modulation entirely, same fail-closed rule as colorCount<=0")
    }

    @Test
    fun antiRoleBandsAreNeverReconsideredByIdentityModulation() {
        // AGGRO's own "removal_mass" anti-role band is already [0,0,max] before identity
        // modulation runs -- the `band.min <= 0` guard must leave it completely alone even though
        // "removal_mass" IS tabled and would otherwise be a candidate.
        val before = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.AGGRO, colorCount = 1,
        )
        val after = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.AGGRO, identity = setOf(ManaColor.G),
        )
        assertEquals(before.roleTargets.getValue("removal_mass"), after.roleTargets.getValue("removal_mass"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Fix 1 (edge-case audit, 2026-07-28) -- unbounded redistribution inflation.
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun monoGreenControlDeckCapsTheSoleRecipientsInflatedIdealInsteadOfDumpingTheWholeShortfallOntoIt() {
        // Real repro: Commander CONTROL, mono-Green -- removal_mass (ideal 7, CONTROL's own
        // override) AND counterspell (ideal 9, CONTROL's own override) BOTH zero out for a
        // single-Green identity (neither is feasible), and "recursion" (ideal 2, inherited
        // untouched from GENERIC) is the ONLY feasible INTERACTION_ROLES recipient present in the
        // CONTROL Commander skeleton (removal_spot is SUBSTITUTE-only for Green, so it's excluded
        // from the recipient pool; protection/counterspell aren't real recipients here either).
        // Pre-fix this absorbed the FULL zeroed total (16) unconditionally: 2 -> 18, a 9x blowup
        // that drove a bogus "Control wants 18 Recursion -- you have 2" Suggestions-tab prompt.
        val before = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL, colorCount = 1,
        )
        val after = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL, identity = setOf(ManaColor.G),
        )

        val removalMassBefore = before.roleTargets.getValue("removal_mass")
        val counterspellBefore = before.roleTargets.getValue("counterspell")
        val recursionBefore = before.roleTargets.getValue("recursion")
        val recursionAfter = after.roleTargets.getValue("recursion")

        // Both infeasible roles are zeroed, same as ever (the cap only bounds the RECIPIENT side).
        assertEquals(0, after.roleTargets.getValue("removal_mass").ideal)
        assertEquals(0, after.roleTargets.getValue("counterspell").ideal)

        val totalZeroed = removalMassBefore.ideal + counterspellBefore.ideal
        val uncappedIdeal = recursionBefore.ideal + totalZeroed
        val cap = (recursionBefore.max * 2.0).let { kotlin.math.round(it).toInt() }

        // The core fix: recursion grew (it's still the sole recipient), but NEVER all the way to
        // the uncapped 18 -- it must stay at or under 2x its own prior max (documented judgment
        // call, see ArchetypeSkeletonResolver.REDISTRIBUTION_GROWTH_CAP_MULTIPLIER's KDoc).
        assertTrue(recursionAfter.ideal > recursionBefore.ideal, "recursion must still absorb SOME of the shortfall")
        assertTrue(recursionAfter.ideal < uncappedIdeal, "recursion must NOT absorb the full uncapped shortfall (18)")
        assertTrue(recursionAfter.ideal <= cap, "recursion's inflated ideal (${recursionAfter.ideal}) must stay within the growth cap ($cap)")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Fix 7 (edge-case audit, 2026-07-28) -- the "zero eligible recipients" fallback branch was
    //  flagged as dead/untested against the LIVE ColorRoleAffinity table. It turns out it IS
    //  reachable via a real combo (no test-only seam needed): CONTROL(SIXTY) + mono-Green.
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun monoGreenControlSixtyCardHasZeroFeasibleRecipientsAndHonestlyDropsTheZeroedDemand() {
        // CONTROL(SIXTY)'s INTERACTION_ROLES members present in its own skeleton are exactly
        // {counterspell, removal_spot, removal_mass}. For mono-Green: counterspell is ABSENT
        // (zeroed) AND removal_mass is ALSO ABSENT (zeroed) -- the only survivor, removal_spot, is
        // SUBSTITUTE-only (fight/bite), which the recipient filter requires [ColorRoleAffinity
        // .isFeasible] (PRIMARY/SECONDARY), never SUBSTITUTE -- so the recipient pool is completely
        // empty. This forces the previously-flagged-dead "zero eligible recipients" branch through
        // the REAL production table (proving it is reachable today, just not by any fixture the
        // earlier WS9.2 tests happened to use).
        val before = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.CONTROL, colorCount = 1,
        )
        val after = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.CONTROL, identity = setOf(ManaColor.G),
        )

        // Both infeasible roles are zeroed as usual.
        assertEquals(0, after.roleTargets.getValue("counterspell").ideal)
        assertEquals(0, after.roleTargets.getValue("removal_mass").ideal)

        // The sole would-be recipient (removal_spot) is SUBSTITUTE-reduced, NOT a redistribution
        // target -- it must NOT have absorbed any of the dropped demand.
        val removalSpotBefore = before.roleTargets.getValue("removal_spot")
        val removalSpotAfter = after.roleTargets.getValue("removal_spot")
        assertTrue(
            removalSpotAfter.ideal <= removalSpotBefore.ideal,
            "the sole SUBSTITUTE role must only be REDUCED, never grown by a redistribution it isn't eligible to receive",
        )

        // No other role in the skeleton silently absorbed the dropped demand either.
        val untouchedKeys = after.roleTargets.keys - setOf("counterspell", "removal_mass", "removal_spot")
        untouchedKeys.forEach { key ->
            assertEquals(
                before.roleTargets.getValue(key), after.roleTargets.getValue(key),
                "role '$key' must be byte-identical -- the dropped demand must not leak anywhere",
            )
        }
    }
}
