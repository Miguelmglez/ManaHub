package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.feature.draft.domain.model.BoosterCardEntry
import com.mmg.manahub.feature.draft.domain.model.BoosterConfig
import com.mmg.manahub.feature.draft.domain.model.BoosterSheet
import com.mmg.manahub.feature.draft.domain.model.BoosterVariant
import com.mmg.manahub.feature.draft.domain.model.DraftSet
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import com.mmg.manahub.feature.draft.domain.model.TierCard

/**
 * Shared draft-test fixtures. Used by [WeightedBoosterGeneratorTest], [BotHarnessTest], and any
 * other draft engine test. Keep these deterministic — tests seed their own [kotlin.random.Random].
 */
internal object DraftTestFixtures {

    val COLORS = listOf("W", "U", "B", "R", "G")

    /**
     * A deterministic fake [Card]. Color cycles through WUBRG, CMC cycles 1..6, type is "Creature".
     */
    fun fakeCard(i: Int): Card = Card(
        scryfallId = "id-$i", name = "Card $i", printedName = null,
        manaCost = null, cmc = (i % 6 + 1).toDouble(),
        colors = listOf(COLORS[i % 5]),
        colorIdentity = listOf(COLORS[i % 5]),
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

    /**
     * The canonical draftable set used across booster/draft tests:
     * - 200 cards (WUBRG-cycling)
     * - sheets: 100 commons, 30 uncommons, 20 rares
     * - one booster variant: 10 commons + 3 uncommons + 1 rareMythic
     * - tier ratings on the first 20 cards (pickOrderRank 1..20)
     */
    fun fakeDraftableSet(): DraftableSet {
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

    /**
     * A richer draftable set for the bot harness. Identical pool/sheet structure to
     * [fakeDraftableSet], but rates ALL 200 cards with a spread of pick-order ranks and varied
     * rarities so a power-aware drafter can meaningfully out-pick a rarity-only baseline.
     *
     * Rank assignment: each card's rank is `1 + (scryfallId index) modulo-mapped` so that ranks
     * span 1..200, decoupled from rarity. This guarantees the highest-rarity card is NOT always the
     * highest-rated card — exactly the gap the heuristic should exploit over RaredraftBot.
     */
    fun fakeRatedDraftableSet(): DraftableSet {
        val cards = (1..200).map { i ->
            // Vary rarity so RaredraftBot has something to chase, decoupled from pick order.
            val rarity = when (i % 8) {
                0 -> "mythic"
                1, 2 -> "rare"
                3, 4 -> "uncommon"
                else -> "common"
            }
            fakeCard(i).copy(rarity = rarity)
        }
        val commonEntries = cards.take(100).map { BoosterCardEntry(it.scryfallId, 1) }
        val uncommonEntries = cards.drop(100).take(50).map { BoosterCardEntry(it.scryfallId, 1) }
        val rareEntries = cards.drop(150).take(50).map { BoosterCardEntry(it.scryfallId, 1) }
        val config = BoosterConfig(
            setCode = "TST", schemaVersion = 1,
            boosters = listOf(BoosterVariant(1, mapOf("common" to 10, "uncommon" to 3, "rareMythic" to 1))),
            sheets = mapOf(
                "common" to BoosterSheet(foil = false, balanceColors = true, cards = commonEntries),
                "uncommon" to BoosterSheet(foil = false, balanceColors = false, cards = uncommonEntries),
                "rareMythic" to BoosterSheet(foil = false, balanceColors = false, cards = rareEntries),
            ),
        )
        // Rate every card. Rank is a deterministic permutation of 1..200 unrelated to rarity,
        // so high rarity != high rank.
        val ratings = cards.mapIndexed { idx, c ->
            val rank = ((idx * 73) % 200) + 1 // 73 is coprime with 200 -> bijection over 1..200
            val tier = when {
                rank <= 20 -> "S"
                rank <= 60 -> "A"
                rank <= 110 -> "B"
                rank <= 160 -> "C"
                else -> "D"
            }
            c.scryfallId to TierCard(
                c.name, c.scryfallId, c.colors.first(), c.colors, c.rarity,
                rank, tier, "", "", "", "Creature",
            )
        }.toMap()
        val set = DraftSet("TST", "TST", "Test Set", "2025-01-01", "", "v1", "v1", "v1")
        return DraftableSet(set, cards, config, ratings)
    }
}
