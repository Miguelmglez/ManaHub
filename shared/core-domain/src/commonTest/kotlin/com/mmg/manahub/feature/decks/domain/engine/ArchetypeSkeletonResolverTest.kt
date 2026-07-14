package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Doctor Community/Archetype plan, Phase 1.8 — behavioral unit tests for
 * [ArchetypeSkeletonResolver] that exercise paths the golden Appendix-B port
 * ([ArchetypeSkeletonGoldenTest]) does not: [ArchetypeSkeletonResolver.resolveWithColor] (A.5
 * color modulation, NOT part of the annex's own validated `resolve()`), the 2-theme 0.75 dilution
 * factor, and the anti-role tolerance rule.
 */
class ArchetypeSkeletonResolverTest {

    @Test
    fun resolveWithColorMergesManaFixAndShiftsLandsForFourPlusColors() {
        val base = ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE)
        val colored = ArchetypeSkeletonResolver.resolveWithColor(
            ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, colorCount = 5,
        )
        // 4-5 color commander: mana_fix [12,16,22], lands_delta +1.
        assertEquals(RoleTarget(12, 16, 22), colored.roleTargets[ArchetypeData.MANA_FIX_KEY])
        assertEquals(base.lands.min + 1, colored.lands.min)
        assertEquals(base.lands.ideal + 1, colored.lands.ideal)
        assertEquals(base.lands.max + 1, colored.lands.max)
    }

    @Test
    fun resolveWithColorSkipsModulationForUnknownColorCount() {
        val base = ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE)
        val colored = ArchetypeSkeletonResolver.resolveWithColor(
            ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, colorCount = 0,
        )
        assertEquals(base, colored)
    }

    @Test
    fun monoColorCommanderGetsAZeroFloorManaFixBand() {
        val colored = ArchetypeSkeletonResolver.resolveWithColor(
            ArchetypeFormat.COMMANDER, ArchetypeId.AGGRO, colorCount = 1,
        )
        assertEquals(RoleTarget(0, 0, 3), colored.roleTargets[ArchetypeData.MANA_FIX_KEY])
    }

    @Test
    fun antiRoleResolvesToZeroFloorWithPriorMaxAsTolerance() {
        val resolved = ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER, ArchetypeId.AGGRO)
        val removalMass = resolved.roleTargets.getValue("removal_mass")
        assertEquals(0, removalMass.min)
        assertEquals(0, removalMass.ideal)
        assertEquals(1, removalMass.max) // AGGRO commander's own roles_override sets max=1
        assertTrue("removal_mass" in resolved.antiRoles)
    }

    @Test
    fun twoThemesScaleAdditionallyBy075() {
        // ARISTOCRATS alone (commander, scale 1.0): sac_outlet ideal 9.
        val single = ArchetypeSkeletonResolver.resolve(
            ArchetypeFormat.SIXTY, ArchetypeId.COMBO, listOf(ThemeId.SELF_MILL),
        )
        // SELF_MILL sixty_scale 0.55; single-theme scale = 0.55 * 1.0 = 0.55.
        // graveyard_enabler ideal 12 * 0.55 = 6.6 -> round 7.
        assertEquals(7, single.roleTargets.getValue("graveyard_enabler").ideal)

        val two = ArchetypeSkeletonResolver.resolve(
            ArchetypeFormat.SIXTY, ArchetypeId.COMBO, listOf(ThemeId.SELF_MILL, ThemeId.ARISTOCRATS),
        )
        // With a 2nd theme, SELF_MILL's own scale becomes 0.55 * 0.75 = 0.4125.
        // graveyard_enabler ideal 12 * 0.4125 = 4.95 -> round 5 (< the single-theme 7).
        val twoThemeIdeal = two.roleTargets.getValue("graveyard_enabler").ideal
        assertTrue(twoThemeIdeal < 7, "2-theme dilution should shrink the band (was $twoThemeIdeal)")
    }

    @Test
    fun genericSkeletonHasNoAntiRolesAndMatchesAppendixLandBand() {
        val generic = ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER)
        assertTrue(generic.antiRoles.isEmpty())
        assertEquals(RoleTarget(34, 37, 39), generic.lands)
    }
}
