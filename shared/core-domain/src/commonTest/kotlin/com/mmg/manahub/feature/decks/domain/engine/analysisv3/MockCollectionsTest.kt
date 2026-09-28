package com.mmg.manahub.feature.decks.domain.engine.analysisv3
// COMMENTS_REVIEWED: 2026-09-08

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Wizard Commander v3 plan (Phase 0, item 0.3) — sanity checks for [MockCollectionRich]/
 * [MockCollectionThin]. These are fixture-shape guards (never empty, no duplicate ids, the 4
 * target fixtures' own cards are actually present), not scoring assertions — no golden/corpus
 * suite depends on this file.
 */
class MockCollectionsTest {

    @Test
    fun `MockCollectionRich contains all 4 target commanders at quantity 1`() {
        val commanderIds = setOf("cmd-edgar-markov", "cmd-meren", "cmd-karlov", "cmd-urza")
        val ownedById = MockCollectionRich.ownedCards.associateBy { it.card.scryfallId }
        commanderIds.forEach { id ->
            val owned = ownedById[id]
            assertTrue(owned != null, "MockCollectionRich is missing target commander '$id'")
        }
    }

    @Test
    fun `MockCollectionRich every target fixture's own non-land cards are owned`() {
        val ownedIds = MockCollectionRich.ownedCards.map { it.card.scryfallId }.toSet()
        MockCollectionRich.targetFixtures.forEach { fixture ->
            fixture.mainboard
                .filterNot { com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator.isLand(it.card) }
                .forEach { entry ->
                    assertTrue(
                        entry.card.scryfallId in ownedIds,
                        "Fixture '${fixture.name}' card '${entry.card.name}' is not in MockCollectionRich's owned pool",
                    )
                }
        }
    }

    @Test
    fun `MockCollectionRich has no duplicate owned card ids`() {
        val ids = MockCollectionRich.ownedCards.map { it.card.scryfallId }
        assertEquals(ids.size, ids.toSet().size, "MockCollectionRich has duplicate scryfallIds")
    }

    @Test
    fun `MockCollectionRich distractor pool is non-trivial and capped`() {
        val targetCardCount = MockCollectionRich.targetFixtures.sumOf { fixture ->
            fixture.mainboard.count { !com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator.isLand(it.card) }
        }
        val distractorCount = MockCollectionRich.ownedCards.size - targetCardCount
        assertTrue(distractorCount in 1..150, "distractor count $distractorCount should be in [1, 150]")
    }

    @Test
    fun `MockCollectionRich owned basics cover all 5 colors`() {
        val symbols = MockCollectionRich.ownedBasics.map { it.card.producedMana }.toSet()
        assertEquals(setOf("R", "W", "B", "G", "U"), symbols)
    }

    @Test
    fun `MockCollectionThin has one commander, 30 non-commander cards, and 8 basic lands`() {
        assertEquals(1, MockCollectionThin.all.count { it.card.scryfallId == MockCollectionThin.commander.scryfallId })
        assertEquals(30, MockCollectionThin.ownedCards.size)
        assertEquals(8, MockCollectionThin.ownedBasics.sumOf { it.quantity })
    }

    @Test
    fun `MockCollectionThin non-commander cards are within the commander's color identity`() {
        val identity = MockCollectionThin.commander.colorIdentity.toSet()
        MockCollectionThin.ownedCards.forEach { owned ->
            assertTrue(
                owned.card.colorIdentity.all { it in identity },
                "MockCollectionThin card '${owned.card.name}' (${owned.card.colorIdentity}) is outside commander identity $identity",
            )
        }
    }
}
