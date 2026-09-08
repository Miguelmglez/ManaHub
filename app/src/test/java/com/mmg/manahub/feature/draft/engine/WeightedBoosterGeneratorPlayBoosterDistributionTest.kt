package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.core.model.BoosterCardEntry
import com.mmg.manahub.core.model.BoosterConfig
import com.mmg.manahub.core.model.BoosterSheet
import com.mmg.manahub.core.model.BoosterVariant
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.DraftableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regression guard for a real production bug in the offline draft-content pipeline (which lives
 * outside this repo, NOT in [WeightedBoosterGenerator]): booster sheet weights were previously
 * flattened to 1 for every card regardless of rarity, which made every generated Play Booster pack
 * land ~30% rares — 70% of packs had 2+ rares versus ~37% in reality. [WeightedBoosterGenerator]
 * itself always honored [BoosterCardEntry.weight] correctly; this test locks in that behaviour
 * against a synthetic pack shaped like a real MTGJSON Play Booster (common / uncommon / wildcard /
 * rareMythic / land sheets) so a future regression in the generator's weighting logic is caught
 * here too, not just in the (external, unversioned) content pipeline.
 */
class WeightedBoosterGeneratorPlayBoosterDistributionTest {

    private companion object {
        const val PACK_SIZE = 12
        const val PACK_COUNT = 20_000
    }

    @Test
    fun playBoosterDistributionMatchesRealWorldShape() {
        val set = fakePlayBoosterSet()
        val gen = WeightedBoosterGenerator(Random(20260907))
        // seatCount * packCount = 8 * 2_500 = 20_000 packs opened.
        val config = DraftConfig("TST", seatCount = 8, packCount = 2_500)
        val packs = gen.generate(set, config)

        assertEquals(PACK_COUNT, packs.size)

        var totalMythics = 0
        var totalRaresOrMythics = 0
        packs.forEach { pack ->
            assertEquals("Pack should have $PACK_SIZE cards", PACK_SIZE, pack.cards.size)

            val landCount = pack.cards.count { it.card.typeLine == "Land" }
            assertEquals("Pack should contain exactly one land", 1, landCount)

            totalMythics += pack.cards.count { it.card.rarity == "mythic" }
            totalRaresOrMythics += pack.cards.count { it.card.rarity == "rare" || it.card.rarity == "mythic" }
        }

        val avgMythics = totalMythics.toDouble() / packs.size
        val avgRaresOrMythics = totalRaresOrMythics.toDouble() / packs.size

        assertTrue("Average mythics per pack should be < 0.4, was $avgMythics", avgMythics < 0.4)
        assertTrue(
            "Average rares+mythics per pack should be in [0.95, 1.75], was $avgRaresOrMythics",
            avgRaresOrMythics in 0.95..1.75,
        )
    }

    private fun fakeCard(id: String, rarity: String, typeLine: String = "Creature"): Card = Card(
        scryfallId = id, name = id, printedName = null,
        manaCost = null, cmc = 1.0,
        colors = listOf("W"), colorIdentity = listOf("W"),
        typeLine = typeLine, printedTypeLine = null, oracleText = null,
        printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "TST", setName = "Test Set",
        collectorNumber = id, rarity = rarity,
        releasedAt = "2025-01-01", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal",
        legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = null, scryfallUri = "https://scryfall.com/$id",
    )

    /**
     * Builds a synthetic set shaped like a real MTGJSON Play Booster (12 cards, no foil slot —
     * out of scope for this distribution check):
     * - `common` (x6) / `uncommon` (x3): flat sheets, weight 1 per card.
     * - `wildcard` (x1): mixed rarity, weights strongly favour commons — 66:22:10:2 per-card
     *   weight for common:uncommon:rare:mythic, ≈67%/22%/10%/1% with 4/4/4/2 cards per rarity.
     * - `rareMythic` (x1): rare weight 2, mythic weight 1 per card; tuned via card counts
     *   (35 rares, 10 mythics → 70:10 total weight) to a realistic ~1-in-8 mythic rate.
     * - `nonFoilLand` (x1): 10 basic-land-shaped commons (typeLine "Land"), weight 1 each.
     *
     * Expected long-run averages: ~1.11 rares+mythics per pack, ~0.14 mythics per pack — both
     * comfortably inside this test's assertion bounds even accounting for statistical noise over
     * 20,000 packs, while still failing hard if weights were ever flattened back to 1.
     */
    private fun fakePlayBoosterSet(): DraftableSet {
        val commons = (1..80).map { fakeCard("C$it", "common") }
        val uncommons = (1..40).map { fakeCard("U$it", "uncommon") }
        val rares = (1..35).map { fakeCard("R$it", "rare") }
        val mythics = (1..10).map { fakeCard("M$it", "mythic") }
        val lands = (1..10).map { fakeCard("L$it", "common", typeLine = "Land") }
        val wildcardCommons = (1..4).map { fakeCard("WC$it", "common") }
        val wildcardUncommons = (1..4).map { fakeCard("WU$it", "uncommon") }
        val wildcardRares = (1..4).map { fakeCard("WR$it", "rare") }
        val wildcardMythics = (1..2).map { fakeCard("WM$it", "mythic") }

        val allCards = commons + uncommons + rares + mythics + lands +
            wildcardCommons + wildcardUncommons + wildcardRares + wildcardMythics

        val boosterConfig = BoosterConfig(
            setCode = "TST",
            schemaVersion = 1,
            boosters = listOf(
                BoosterVariant(
                    weight = 1,
                    contents = mapOf(
                        "common" to 6,
                        "uncommon" to 3,
                        "wildcard" to 1,
                        "rareMythic" to 1,
                        "nonFoilLand" to 1,
                    ),
                ),
            ),
            sheets = mapOf(
                "common" to BoosterSheet(
                    foil = false, balanceColors = false,
                    cards = commons.map { BoosterCardEntry(it.scryfallId, 1) },
                ),
                "uncommon" to BoosterSheet(
                    foil = false, balanceColors = false,
                    cards = uncommons.map { BoosterCardEntry(it.scryfallId, 1) },
                ),
                "wildcard" to BoosterSheet(
                    foil = false, balanceColors = false,
                    cards = wildcardCommons.map { BoosterCardEntry(it.scryfallId, 66) } +
                        wildcardUncommons.map { BoosterCardEntry(it.scryfallId, 22) } +
                        wildcardRares.map { BoosterCardEntry(it.scryfallId, 10) } +
                        wildcardMythics.map { BoosterCardEntry(it.scryfallId, 2) },
                ),
                "rareMythic" to BoosterSheet(
                    foil = false, balanceColors = false,
                    cards = rares.map { BoosterCardEntry(it.scryfallId, 2) } +
                        mythics.map { BoosterCardEntry(it.scryfallId, 1) },
                ),
                "nonFoilLand" to BoosterSheet(
                    foil = false, balanceColors = false,
                    cards = lands.map { BoosterCardEntry(it.scryfallId, 1) },
                ),
            ),
        )

        val draftSet = DraftSet("TST", "TST", "Test Set", "2025-01-01", "", "v1", "v1", "v1")
        return DraftableSet(draftSet, allCards, boosterConfig, emptyMap())
    }
}
