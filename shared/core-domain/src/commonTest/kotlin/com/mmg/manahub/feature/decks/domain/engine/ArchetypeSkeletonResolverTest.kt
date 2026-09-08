package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Doctor Community/Archetype plan, Phase 1.8 — behavioral unit tests for
 * [ArchetypeSkeletonResolver] that exercise paths the golden Appendix-B port
 * ([ArchetypeSkeletonGoldenTest]) does not: [ArchetypeSkeletonResolver.resolveWithColorCount] (A.5
 * color-COUNT modulation, NOT part of the annex's own validated `resolve()`), the 2-theme 0.75
 * dilution factor, and the anti-role tolerance rule. These specifically use
 * [ArchetypeSkeletonResolver.resolveWithColorCount] (not [ArchetypeSkeletonResolver
 * .resolveWithColor]) to isolate the color-COUNT layer from WS9.2's color-IDENTITY layer --
 * identity-aware behavior has its own dedicated coverage in `ArchetypeIdentityModulationTest`.
 */
class ArchetypeSkeletonResolverTest {

    @Test
    fun resolveWithColorMergesManaFixAndShiftsLandsForFourPlusColors() {
        val base = ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE)
        val colored = ArchetypeSkeletonResolver.resolveWithColorCount(
            ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, colorCount = 5,
        )
        // WS9.3 retune (2026-07-28): 4-5 color commander mana_fix bumped (12,16,22) -> (14,20,28)
        // -- see ArchetypeData.kt's COLOR_MODULATION header for the citation. lands_delta stays +1.
        assertEquals(RoleTarget(14, 20, 28), colored.roleTargets[ArchetypeData.MANA_FIX_KEY])
        assertEquals(base.lands.min + 1, colored.lands.min)
        assertEquals(base.lands.ideal + 1, colored.lands.ideal)
        assertEquals(base.lands.max + 1, colored.lands.max)
    }

    @Test
    fun resolveWithColorSkipsModulationForUnknownColorCount() {
        val base = ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE)
        val colored = ArchetypeSkeletonResolver.resolveWithColorCount(
            ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, colorCount = 0,
        )
        assertEquals(base, colored)
    }

    @Test
    fun monoColorCommanderGetsAZeroFloorManaFixBand() {
        val colored = ArchetypeSkeletonResolver.resolveWithColorCount(
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
        // WS5 retune (2026-07-28): AGGRO commander's own roles_override sets max=2, matching the
        // plan's ep.658 anchor "mass disruption 1-2 (anti-role above that)" (plan line ~306) --
        // was max=1 pre-retune.
        assertEquals(2, removalMass.max)
        assertTrue("removal_mass" in resolved.antiRoles)
    }

    @Test
    fun twoThemesScaleAdditionallyBy075() {
        // Deck Analysis Engine v3 (spec §4.2): SELF_MILL retargeted onto `mill_self` (was
        // `graveyard_enabler`, now owned exclusively by REANIMATOR).
        val single = ArchetypeSkeletonResolver.resolve(
            ArchetypeFormat.SIXTY, ArchetypeId.COMBO, themes = listOf(ThemeId.SELF_MILL),
        )
        // SELF_MILL sixty_scale 0.55; single-theme scale = 0.55 * 1.0 = 0.55.
        // mill_self ideal 12 * 0.55 = 6.6 -> round 7.
        assertEquals(7, single.roleTargets.getValue("mill_self").ideal)

        val two = ArchetypeSkeletonResolver.resolve(
            ArchetypeFormat.SIXTY, ArchetypeId.COMBO, themes = listOf(ThemeId.SELF_MILL, ThemeId.ARISTOCRATS),
        )
        // With a 2nd theme, SELF_MILL's own scale becomes 0.55 * 0.75 = 0.4125.
        // mill_self ideal 12 * 0.4125 = 4.95 -> round 5 (< the single-theme 7).
        val twoThemeIdeal = two.roleTargets.getValue("mill_self").ideal
        assertTrue(twoThemeIdeal < 7, "2-theme dilution should shrink the band (was $twoThemeIdeal)")
    }

    @Test
    fun genericSkeletonHasNoAntiRolesAndMatchesAppendixLandBand() {
        val generic = ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER)
        assertTrue(generic.antiRoles.isEmpty())
        // WS5 retune (2026-07-28): re-anchored to the plan's ep.658 "Baseline" row (38 lands
        // ideal, plan line ~304) -- was (34,37,39) pre-retune (the prior D15/EDHREC-calibrated
        // band, whose ideal already matched 37 -- close to, but not exactly, ep.658's 38).
        assertEquals(RoleTarget(36, 38, 40), generic.lands)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Wave 2 B2 -- SixtyFormatProfile's deckFormat layer, applied by resolveWithColor as the
    //  LAST step (after archetype+theme+color-count+identity resolution). Isolated from B1's
    //  CuratedStrategyCatalog work -- these tests only exercise ArchetypeSkeletonResolver.
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun resolveWithColorAppliesStandardLandsAndCurveShift() {
        val withoutFormat = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = null,
        )
        val withStandard = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = DeckFormat.STANDARD,
        )

        // SixtyFormatProfile.STANDARD: landsDelta = +2, curveDelta = +0.3.
        assertEquals(withoutFormat.lands.min + 2, withStandard.lands.min)
        assertEquals(withoutFormat.lands.ideal + 2, withStandard.lands.ideal)
        assertEquals(withoutFormat.lands.max + 2, withStandard.lands.max)

        assertEquals(roundTo2ForTest(withoutFormat.curve.min + 0.3), withStandard.curve.min, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.ideal + 0.3), withStandard.curve.ideal, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.max + 0.3), withStandard.curve.max, 0.001)
    }

    @Test
    fun resolveWithColorIsPassthroughForNullAndCasualAndModernDeckFormats() {
        // MODERN swapped in for the old PIONEER example (Wave 2 future-debt closeout, 2026-09-06):
        // Pioneer now has its own non-zero SixtyFormatProfile entry (see the dedicated test below),
        // so it no longer demonstrates passthrough behavior. MODERN is the format this pass
        // deliberately calibrated to (0, 0.0) -- see SixtyFormatProfile.MODERN's KDoc for why --
        // so it still is a genuine passthrough example, same as CASUAL (which has no entry at all).
        val baseline = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.MIDRANGE,
            identity = setOf(ManaColor.G, ManaColor.W), deckFormat = null,
        )
        val casual = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.MIDRANGE,
            identity = setOf(ManaColor.G, ManaColor.W), deckFormat = DeckFormat.CASUAL,
        )
        val modern = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.MIDRANGE,
            identity = setOf(ManaColor.G, ManaColor.W), deckFormat = DeckFormat.MODERN,
        )

        assertEquals(baseline, casual)
        assertEquals(baseline, modern)
    }

    @Test
    fun resolveWithColorAppliesPioneerLandsAndCurveShift() {
        val withoutFormat = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = null,
        )
        val withPioneer = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = DeckFormat.PIONEER,
        )

        // SixtyFormatProfile.PIONEER: landsDelta = +1, curveDelta = +0.15.
        assertEquals(withoutFormat.lands.min + 1, withPioneer.lands.min)
        assertEquals(withoutFormat.lands.ideal + 1, withPioneer.lands.ideal)
        assertEquals(withoutFormat.lands.max + 1, withPioneer.lands.max)

        assertEquals(roundTo2ForTest(withoutFormat.curve.min + 0.15), withPioneer.curve.min, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.ideal + 0.15), withPioneer.curve.ideal, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.max + 0.15), withPioneer.curve.max, 0.001)
    }

    @Test
    fun resolveWithColorAppliesLegacyLandsAndCurveShift() {
        val withoutFormat = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = null,
        )
        val withLegacy = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = DeckFormat.LEGACY,
        )

        // SixtyFormatProfile.LEGACY: landsDelta = -1, curveDelta = -0.2.
        assertEquals(withoutFormat.lands.min - 1, withLegacy.lands.min)
        assertEquals(withoutFormat.lands.ideal - 1, withLegacy.lands.ideal)
        assertEquals(withoutFormat.lands.max - 1, withLegacy.lands.max)

        assertEquals(roundTo2ForTest(withoutFormat.curve.min - 0.2), withLegacy.curve.min, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.ideal - 0.2), withLegacy.curve.ideal, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.max - 0.2), withLegacy.curve.max, 0.001)
    }

    @Test
    fun resolveWithColorAppliesVintageLandsAndCurveShift() {
        val withoutFormat = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = null,
        )
        val withVintage = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = DeckFormat.VINTAGE,
        )

        // SixtyFormatProfile.VINTAGE: landsDelta = -2, curveDelta = -0.3.
        assertEquals(withoutFormat.lands.min - 2, withVintage.lands.min)
        assertEquals(withoutFormat.lands.ideal - 2, withVintage.lands.ideal)
        assertEquals(withoutFormat.lands.max - 2, withVintage.lands.max)

        assertEquals(roundTo2ForTest(withoutFormat.curve.min - 0.3), withVintage.curve.min, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.ideal - 0.3), withVintage.curve.ideal, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.max - 0.3), withVintage.curve.max, 0.001)
    }

    @Test
    fun resolveWithColorAppliesPauperLandsAndCurveShift() {
        val withoutFormat = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = null,
        )
        val withPauper = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO,
            identity = setOf(ManaColor.R), deckFormat = DeckFormat.PAUPER,
        )

        // SixtyFormatProfile.PAUPER: landsDelta = +1, curveDelta = -0.1.
        assertEquals(withoutFormat.lands.min + 1, withPauper.lands.min)
        assertEquals(withoutFormat.lands.ideal + 1, withPauper.lands.ideal)
        assertEquals(withoutFormat.lands.max + 1, withPauper.lands.max)

        assertEquals(roundTo2ForTest(withoutFormat.curve.min - 0.1), withPauper.curve.min, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.ideal - 0.1), withPauper.curve.ideal, 0.001)
        assertEquals(roundTo2ForTest(withoutFormat.curve.max - 0.1), withPauper.curve.max, 0.001)
    }

    @Test
    fun resolveWithColorIgnoresDeckFormatForCommander() {
        val withoutFormat = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL,
            identity = setOf(ManaColor.U, ManaColor.W), deckFormat = null,
        )
        val withStandard = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL,
            identity = setOf(ManaColor.U, ManaColor.W), deckFormat = DeckFormat.STANDARD,
        )

        // A nonsensical (Commander, STANDARD) combination must still be a no-op: the profile is
        // gated on `format == ArchetypeFormat.SIXTY`, never on deckFormat alone.
        assertEquals(withoutFormat, withStandard)
    }

    /** Mirrors the resolver's own private `roundTo2` (round-half-up to 2dp) so these tests can
     * predict the exact curve-shift result without depending on the resolver's implementation. */
    private fun roundTo2ForTest(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
