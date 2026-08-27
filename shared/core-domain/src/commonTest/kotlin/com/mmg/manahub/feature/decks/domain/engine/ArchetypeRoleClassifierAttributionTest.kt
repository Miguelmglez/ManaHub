package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  ArchetypeRoleClassifierAttributionTest — Deck Analysis Category Sections rework, W1/W8.
//
//  Covers [ArchetypeRoleClassifier.deckRoleAttribution], the per-card sibling of
//  [ArchetypeRoleClassifier.deckRoleCounts] that keeps the (card, quantity, confidence) triples the
//  rounded Int map throws away. Per the plan's own explicit W8 spec: same keys as deckRoleCounts, a
//  card in two roles appears in both lists, sorted by confidence descending.
// ═══════════════════════════════════════════════════════════════════════════════

class ArchetypeRoleClassifierAttributionTest {

    private fun roleTag(key: String): CardTag = CardTag(key, TagCategory.ROLE)

    @Test
    fun deckRoleAttribution_returnsSameKeysAsDeckRoleCounts() {
        val mainboard = listOf(
            entry(card(id = "c1", tags = listOf(roleTag("sac_outlet"))), quantity = 2),
            entry(card(id = "c2", tags = listOf(roleTag("death_payoff"))), quantity = 3),
            entry(card(id = "c3", tags = listOf(CardTag.RAMP)), quantity = 1),
        )

        val counts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)
        val attribution = ArchetypeRoleClassifier.deckRoleAttribution(mainboard)

        assertEquals(counts.keys, attribution.keys, "deckRoleAttribution must cover the SAME role keys deckRoleCounts collapses to")
    }

    @Test
    fun deckRoleAttribution_cardInTwoRoles_appearsInBothLists() {
        val dual = card(id = "dual-role-card", tags = listOf(roleTag("sac_outlet"), roleTag("death_payoff")))
        val mainboard = listOf(entry(dual, quantity = 1))

        val attribution = ArchetypeRoleClassifier.deckRoleAttribution(mainboard)

        assertTrue(attribution["sac_outlet"].orEmpty().any { it.scryfallId == "dual-role-card" }, "expected dual-role-card under sac_outlet")
        assertTrue(attribution["death_payoff"].orEmpty().any { it.scryfallId == "dual-role-card" }, "expected dual-role-card under death_payoff")
    }

    @Test
    fun deckRoleAttribution_isSortedByConfidenceDescending() {
        // finisherMatcher: a confirmed `win_con` tag hit is full confidence (1f); a structural big
        // body (cmc >= 5, power >= 4, no tag) is the softer 0.7f fallback — see ArchetypeRoleClassifier.
        val taggedFinisher = card(id = "tagged-finisher", tags = listOf(roleTag("win_con")), typeLine = "Creature", cmc = 6.0, power = "5")
        val structuralFinisher = card(id = "structural-finisher", typeLine = "Creature", cmc = 6.0, power = "4")
        val mainboard = listOf(entry(taggedFinisher, quantity = 1), entry(structuralFinisher, quantity = 1))

        val finishers = ArchetypeRoleClassifier.deckRoleAttribution(mainboard)["finisher"].orEmpty()

        assertEquals(listOf("tagged-finisher", "structural-finisher"), finishers.map { it.scryfallId })
        assertTrue(finishers.zipWithNext().all { (a, b) -> a.confidence >= b.confidence }, "expected non-increasing confidence order")
    }

    @Test
    fun deckRoleAttribution_carriesQuantityThroughUnchanged() {
        val mainboard = listOf(entry(card(id = "quad", tags = listOf(CardTag.RAMP)), quantity = 4))

        val ramp = ArchetypeRoleClassifier.deckRoleAttribution(mainboard)["ramp"].orEmpty()

        assertEquals(1, ramp.size)
        assertEquals(4, ramp.single().quantity)
    }
}
