package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan, WS9.1 — matrix invariants for [ColorRoleAffinity]. Mirrors
 * the plan's own acceptance framing: every tabled role must have real color-pie access
 * somewhere, and the table's helper functions must behave sanely at the edges (untabled roles,
 * colorless identities).
 */
class ColorRoleAffinityTest {

    private val plan9Roles = listOf(
        "counterspell", "removal_spot", "removal_mass", "card_draw", "ramp", "tutor",
        "removal_artifact_enchant", "protection", "recursion",
    )
    private val extraRoles = listOf("graveyard_hate", "token_generator", "wheel")
    private val allTabledRoles = plan9Roles + extraRoles

    @Test
    fun everyTabledRoleHasAtLeastOnePrimaryColor() {
        allTabledRoles.forEach { role ->
            val primaries = ColorRoleAffinity.bestColorsFor(role)
            assertTrue(primaries.isNotEmpty(), "$role: expected at least one PRIMARY color, found none")
        }
    }

    @Test
    fun noTabledRoleIsUniversallyAbsent() {
        allTabledRoles.forEach { role ->
            val anyRealAccess = ManaColor.entries.filter { it != ManaColor.C }.any { color ->
                ColorRoleAffinity.affinity(color, role) != ColorRoleAffinity.ColorAffinityLevel.ABSENT
            }
            assertTrue(anyRealAccess, "$role: every color is ABSENT -- the color pie provides zero access")
        }
    }

    @Test
    fun bestColorsForReturnsNonEmptyForEveryTabledRole() {
        allTabledRoles.forEach { role ->
            assertTrue(ColorRoleAffinity.bestColorsFor(role).isNotEmpty(), "$role: bestColorsFor was empty")
        }
    }

    @Test
    fun untabledRoleIsAlwaysFeasibleRegardlessOfIdentity() {
        // "ramp" is tabled but e.g. "sac_outlet" is not -- an untabled role must never be blocked
        // for lack of data.
        assertTrue(ColorRoleAffinity.isFeasible(setOf(ManaColor.U), "sac_outlet"))
        assertTrue(ColorRoleAffinity.isFeasible(emptySet(), "sac_outlet"))
        assertEquals(ColorRoleAffinity.ColorAffinityLevel.ABSENT, ColorRoleAffinity.affinity(ManaColor.U, "sac_outlet"))
        assertTrue(!ColorRoleAffinity.isTabled("sac_outlet"))
    }

    @Test
    fun colorlessHasNoRoleAffinityForAnyTabledRole() {
        allTabledRoles.forEach { role ->
            assertEquals(ColorRoleAffinity.ColorAffinityLevel.ABSENT, ColorRoleAffinity.affinity(ManaColor.C, role))
        }
    }

    @Test
    fun counterspellIsOnlyFeasibleForBlue() {
        assertTrue(ColorRoleAffinity.isFeasible(setOf(ManaColor.U), "counterspell"))
        assertTrue(!ColorRoleAffinity.isFeasible(setOf(ManaColor.G, ManaColor.W), "counterspell"))
        // Selesnya has no SUBSTITUTE-level counterspell access either -- every cell is ABSENT.
        assertTrue(!ColorRoleAffinity.isSubstituteOnly(setOf(ManaColor.G, ManaColor.W), "counterspell"))
    }

    @Test
    fun substituteOnlyIsDistinctFromFullyFeasible() {
        // Green-only: removal_spot is SUBSTITUTE (fight/bite) -- reduced but real, not fully feasible.
        assertTrue(!ColorRoleAffinity.isFeasible(setOf(ManaColor.G), "removal_spot"))
        assertTrue(ColorRoleAffinity.isSubstituteOnly(setOf(ManaColor.G), "removal_spot"))
        // Black-only: removal_spot is PRIMARY -- fully feasible, not substitute-only.
        assertTrue(ColorRoleAffinity.isFeasible(setOf(ManaColor.B), "removal_spot"))
        assertTrue(!ColorRoleAffinity.isSubstituteOnly(setOf(ManaColor.B), "removal_spot"))
    }
}
