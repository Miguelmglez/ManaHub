package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.feature.draft.domain.model.BoosterCardEntry
import com.mmg.manahub.feature.draft.domain.model.BoosterConfig
import com.mmg.manahub.feature.draft.domain.model.BoosterSheet
import com.mmg.manahub.feature.draft.domain.model.BoosterVariant
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftSet
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import com.mmg.manahub.feature.draft.domain.model.TierCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeightedBoosterGeneratorTest {

    private val colors = listOf("W", "U", "B", "R", "G")

    private fun fakeCard(i: Int) = Card(
        scryfallId = "id-$i", name = "Card $i", printedName = null,
        manaCost = null, cmc = (i % 6 + 1).toDouble(),
        colors = listOf(colors[i % 5]),
        colorIdentity = listOf(colors[i % 5]),
        typeLine = "Creature", printedTypeLine = null, oracleText = null,
        printedText = null, keywords = emptyList(), power = "1", toughness = "1",
        loyalty = null, setCode = "TST", setName = "Test Set",
        collectorNumber = "$i", rarity = "common",
        releasedAt = "2025-01-01", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal",
        legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = null, scryfallUri = "https://scryfall.com/$i",
    )

    private fun fakeDraftableSet(): DraftableSet {
        val cards = (1..200).map { fakeCard(it) }
        val commonEntries = cards.take(100).map { BoosterCardEntry(it.scryfallId, 1) }
        val uncommonEntries = cards.drop(100).take(30).map { BoosterCardEntry(it.scryfallId, 1) }
        val rareEntries = cards.drop(130).take(20).map { BoosterCardEntry(it.scryfallId, 1) }
        val config = BoosterConfig(
            setCode = "TST", schemaVersion = 1,
            boosters = listOf(BoosterVariant(1, mapOf("common" to 10, "uncommon" to 3, "rareMythic" to 1))),
            sheets = mapOf(
                "common" to BoosterSheet(foil = false, balanceColors = true, cards = commonEntries),
                "uncommon" to BoosterSheet(foil = false, balanceColors = false, cards = uncommonEntries),
                "rareMythic" to BoosterSheet(foil = false, balanceColors = false, cards = rareEntries),
            ),
        )
        val ratings = cards.take(20).mapIndexed { idx, c ->
            c.scryfallId to TierCard(
                c.name, c.scryfallId, "W", listOf("W"), "common",
                idx + 1, "A", "", "", "", "Creature",
            )
        }.toMap()
        val set = DraftSet("TST", "TST", "Test Set", "2025-01-01", "", "v1", "v1", "v1")
        return DraftableSet(set, cards, config, ratings)
    }

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
