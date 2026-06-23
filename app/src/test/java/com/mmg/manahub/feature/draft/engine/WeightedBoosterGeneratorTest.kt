package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.core.model.BoosterCardEntry
import com.mmg.manahub.core.model.BoosterConfig
import com.mmg.manahub.core.model.BoosterSheet
import com.mmg.manahub.core.model.BoosterVariant
import com.mmg.manahub.core.model.DraftConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeightedBoosterGeneratorTest {

    private fun fakeCard(i: Int) = DraftTestFixtures.fakeCard(i)

    private fun fakeDraftableSet() = DraftTestFixtures.fakeDraftableSet()

    @Test
    fun packCountMatchesExpected() {
        val gen = WeightedBoosterGenerator(Random(42))
        val set = fakeDraftableSet()
        val packs = gen.generate(set, DraftConfig("TST", seatCount = 8, packCount = 3))
        assertEquals(24, packs.size)
    }

    @Test
    fun cardCountPerPack() {
        val gen = WeightedBoosterGenerator(Random(42))
        val set = fakeDraftableSet()
        val packs = gen.generate(set, DraftConfig("TST", seatCount = 8, packCount = 3))
        packs.forEach { pack ->
            assertEquals("Pack should have 14 cards", 14, pack.cards.size)
        }
    }

    @Test
    fun noDuplicatesWithinPack() {
        val gen = WeightedBoosterGenerator(Random(42))
        val set = fakeDraftableSet()
        val packs = gen.generate(set, DraftConfig("TST", seatCount = 8, packCount = 3))
        packs.forEachIndexed { idx, pack ->
            val ids = pack.cards.map { it.card.scryfallId }
            assertEquals("Pack $idx has duplicates", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun colorBalanceDoesNotCrash() {
        val gen = WeightedBoosterGenerator(Random(42))
        val set = fakeDraftableSet()
        gen.generate(set, DraftConfig("TST", seatCount = 4, packCount = 3))
    }

    @Test
    fun foilSheetMarksCardsAsFoil() {
        val cards = (1..10).map { fakeCard(it) }
        val foilEntries = cards.map { BoosterCardEntry(it.scryfallId, 1) }
        val config = BoosterConfig(
            "TST", 1,
            listOf(BoosterVariant(1, mapOf("foil" to 1))),
            mapOf("foil" to BoosterSheet(foil = true, balanceColors = false, cards = foilEntries)),
        )
        val set = fakeDraftableSet().copy(booster = config)
        val gen = WeightedBoosterGenerator(Random(1))
        val packs = gen.generate(set, DraftConfig("TST", seatCount = 2, packCount = 1))
        assertTrue(packs.all { p -> p.cards.all { it.isFoil } })
    }
}
