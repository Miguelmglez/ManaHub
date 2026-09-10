package com.mmg.manahub.feature.decks.presentation.components

import com.mmg.manahub.feature.decks.domain.engine.CardContribution
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck Wizard v4 plan, W4.4/G8b/E2 -- [sectionRingState] (the pure, Compose-free half of
 * [CardSectionHeader]'s ring/label fix): the ring must show progress toward ideal clamped at 1.0,
 * never the pre-v4 shrinking "quality" ramp, and must agree with [CardSection.realCount], the same
 * number [CardSectionHeader] renders as the label (W0.3/E1).
 */
class CardSectionComponentsTest {

    private fun contributions(quantity: Int): List<CardContribution> =
        if (quantity <= 0) emptyList() else listOf(CardContribution(scryfallId = "c1", quantity = quantity, confidence = 1f))

    @Test
    fun `a section listing N cards has realCount equal to N`() {
        val section = CardSection(id = "role:ramp", label = "Ramp", current = 5, min = 4, ideal = 8, max = 12, contributions = contributions(9))
        assertEquals(9, section.realCount)
    }

    @Test
    fun `under ideal -- ring ramps proportionally and matches realCount over ideal`() {
        val section = CardSection(id = "role:ramp", label = "Ramp", current = 4, min = 4, ideal = 8, max = 12, contributions = contributions(4))
        val state = sectionRingState(section)
        assertEquals(0.5f, state.progress, 0.001f)
        assertEquals(SectionRingTone.RAMP, state.tone)
    }

    @Test
    fun `at ideal -- full ring, healthy tone`() {
        val section = CardSection(id = "role:ramp", label = "Ramp", current = 8, min = 4, ideal = 8, max = 12, contributions = contributions(8))
        val state = sectionRingState(section)
        assertEquals(1f, state.progress, 0.001f)
        assertEquals(SectionRingTone.HEALTHY, state.tone)
    }

    @Test
    fun `within band but over ideal -- full ring, healthy tone`() {
        val section = CardSection(id = "role:ramp", label = "Ramp", current = 10, min = 4, ideal = 8, max = 12, contributions = contributions(10))
        val state = sectionRingState(section)
        assertEquals(1f, state.progress, 0.001f)
        assertEquals(SectionRingTone.HEALTHY, state.tone)
    }

    @Test
    fun `a 22-of-8 section shows a FULL ring in its over-limit tone, never a shrinking ring`() {
        // The literal G8 reproduction: Ramp at 22 with ideal 8 / max 12. Pre-v4 this rendered a
        // half-filled amber ring (max/current = 12/22 = 0.545) next to a label reading "22/8".
        val section = CardSection(id = "role:ramp", label = "Ramp", current = 12, min = 4, ideal = 8, max = 12, contributions = contributions(22))
        val state = sectionRingState(section)
        assertEquals(1f, state.progress, 0.001f)
        assertEquals(SectionRingTone.OVER_LIMIT, state.tone)
    }

    @Test
    fun `well over max -- ring stays full, never shrinks further as more copies are added`() {
        val at22 = sectionRingState(CardSection(id = "role:ramp", label = "Ramp", current = 12, min = 4, ideal = 8, max = 12, contributions = contributions(22)))
        val at40 = sectionRingState(CardSection(id = "role:ramp", label = "Ramp", current = 12, min = 4, ideal = 8, max = 12, contributions = contributions(40)))
        assertEquals(1f, at22.progress, 0.001f)
        assertEquals(1f, at40.progress, 0.001f)
        assertEquals(SectionRingTone.OVER_LIMIT, at40.tone)
    }

    @Test
    fun `anti-role under max -- ramps up toward max, healthy tone (unchanged from pre-v4)`() {
        val section = CardSection(id = "role:board_wipe", label = "Board wipes", current = 0, min = 0, ideal = 0, max = 2, isAntiRole = true, contributions = contributions(1))
        val state = sectionRingState(section)
        assertEquals(0.5f, state.progress, 0.001f)
        assertEquals(SectionRingTone.HEALTHY, state.tone)
    }

    @Test
    fun `anti-role over max reads as an alert, ring full and never shrinking as more are added`() {
        val threeOverMaxTwo = sectionRingState(CardSection(id = "role:board_wipe", label = "Board wipes", current = 0, min = 0, ideal = 0, max = 2, isAntiRole = true, contributions = contributions(3)))
        val tenOverMaxTwo = sectionRingState(CardSection(id = "role:board_wipe", label = "Board wipes", current = 0, min = 0, ideal = 0, max = 2, isAntiRole = true, contributions = contributions(10)))
        assertEquals(1f, threeOverMaxTwo.progress, 0.001f)
        assertEquals(SectionRingTone.ALERT, threeOverMaxTwo.tone)
        assertEquals(1f, tenOverMaxTwo.progress, 0.001f)
        assertEquals(SectionRingTone.ALERT, tenOverMaxTwo.tone)
    }

    @Test
    fun `zero ideal never divides by zero -- treated as already-healthy`() {
        val section = CardSection(id = "role:x", label = "X", current = 0, min = 0, ideal = 0, max = 0, contributions = contributions(0))
        val state = sectionRingState(section)
        assertTrue(state.progress in 0f..1f)
        assertEquals(SectionRingTone.HEALTHY, state.tone)
    }

    @Test
    fun `score-facing current is untouched by the ring fix`() {
        val section = CardSection(id = "role:ramp", label = "Ramp", current = 8, min = 4, ideal = 8, max = 12, contributions = contributions(22))
        assertEquals(8, section.current)
        assertEquals(22, section.realCount)
    }
}
