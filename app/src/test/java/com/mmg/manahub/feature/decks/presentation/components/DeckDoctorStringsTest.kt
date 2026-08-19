package com.mmg.manahub.feature.decks.presentation.components

import com.mmg.manahub.feature.decks.domain.engine.CurveShape
import com.mmg.manahub.feature.decks.domain.engine.Finding
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck Analysis Engine v2 Phase 3 -- [Finding.key] (the pure, non-`@Composable` half of this
 * file's Finding localization; [Finding.label] needs `stringResource`/a Compose test harness this
 * codebase doesn't have for `DeckStudioScreen`, so it is intentionally left uncovered here — see
 * this phase's report). Mirrors the (currently untested) legacy `DeckWarning.key` shape one level
 * up, but IS covered since it is new logic written by this phase.
 */
class DeckDoctorStringsTest {

    @Test
    fun `key is stable across every Finding variant`() {
        assertEquals("land_count_off_target", Finding.LandCountOffTarget(current = 34, karstenTarget = 37, bandMin = 35, bandMax = 40).key)
        assertEquals("color_source_shortage_B", Finding.ColorSourceShortage(ManaColor.B, have = 3, need = 8).key)
        assertEquals("unfixed_splash_R", Finding.UnfixedSplash(ManaColor.R).key)
        assertEquals("mana_fix_shortage", Finding.ManaFixShortage(current = 1, min = 3).key)
        assertEquals("land_mix_off_target", Finding.LandMixOffTarget(basicsRatio = 0.4f, targetMin = 0.5f, targetMax = 0.7f).key)
        assertEquals("curve_off_band", Finding.CurveOffBand(avgMv = 4.2, bandMin = 2.0, bandMax = 3.0).key)
        assertEquals("curve_shape_mismatch", Finding.CurveShapeMismatch(CurveShape.BELL).key)
        assertEquals("role_gap_removal", Finding.RoleGap(roleKey = "removal", label = "Removal", current = 3, min = 8).key)
        assertEquals("anti_role_over_max_board_wipe", Finding.AntiRoleOverMax(roleKey = "board_wipe", label = "Board wipes", current = 3, max = 1).key)
        assertEquals("low_synergy_density", Finding.LowSynergyDensity(density = 0.2f).key)
        assertEquals("deck_too_small", Finding.DeckTooSmall(current = 40, minimum = 100).key)
        assertEquals("too_many_copies_Sol Ring", Finding.TooManyCopies(cardName = "Sol Ring", copies = 2, maxCopies = 1).key)
        assertEquals("singleton_violation_Sol Ring", Finding.SingletonViolation(cardName = "Sol Ring", copies = 2).key)
        assertEquals("off_color_identity_Lightning Bolt", Finding.OffColorIdentity(cardName = "Lightning Bolt").key)
        assertEquals("illegal_card_Black Lotus", Finding.IllegalCard(cardName = "Black Lotus").key)
        assertEquals("unresolved_cards", Finding.UnresolvedCards(count = 2).key)
    }

    @Test
    fun `key distinguishes two RoleGap findings on different roleKeys`() {
        val removal = Finding.RoleGap(roleKey = "removal", label = "Removal", current = 3, min = 8)
        val ramp = Finding.RoleGap(roleKey = "ramp", label = "Ramp", current = 1, min = 6)
        assertNotEquals(removal.key, ramp.key)
    }

    @Test
    fun `key distinguishes two TooManyCopies findings on different card names`() {
        val a = Finding.TooManyCopies(cardName = "Sol Ring", copies = 2, maxCopies = 1)
        val b = Finding.TooManyCopies(cardName = "Mana Crypt", copies = 2, maxCopies = 1)
        assertNotEquals(a.key, b.key)
    }

    @Test
    fun `every finding variant produces a non-blank key`() {
        val findings: List<Finding> = listOf(
            Finding.LandCountOffTarget(34, 37, 35, 40),
            Finding.ColorSourceShortage(ManaColor.U, 2, 6),
            Finding.UnfixedSplash(ManaColor.G),
            Finding.ManaFixShortage(1, 3),
            Finding.LandMixOffTarget(0.4f, 0.5f, 0.7f),
            Finding.CurveOffBand(4.2, 2.0, 3.0),
            Finding.CurveShapeMismatch(CurveShape.FRONT),
            Finding.RoleGap("removal", "Removal", 3, 8),
            Finding.AntiRoleOverMax("board_wipe", "Board wipes", 3, 1),
            Finding.LowSynergyDensity(0.2f),
            Finding.DeckTooSmall(40, 100),
            Finding.TooManyCopies("Sol Ring", 2, 1),
            Finding.SingletonViolation("Sol Ring", 2),
            Finding.OffColorIdentity("Lightning Bolt"),
            Finding.IllegalCard("Black Lotus"),
            Finding.UnresolvedCards(2),
        )
        findings.forEach { assertTrue("key must not be blank for $it", it.key.isNotBlank()) }
    }
}
