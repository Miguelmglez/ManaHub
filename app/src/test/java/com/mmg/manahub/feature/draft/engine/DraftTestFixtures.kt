package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.feature.draft.domain.model.BoosterCardEntry
import com.mmg.manahub.feature.draft.domain.model.BoosterConfig
import com.mmg.manahub.feature.draft.domain.model.BoosterSheet
import com.mmg.manahub.feature.draft.domain.model.BoosterVariant
import com.mmg.manahub.core.domain.model.DraftSet
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
     * A draftable set engineered specifically for [BotHarnessTest]. Used by NO other test, so its
     * shape can be tuned for two independent behavioural assertions without affecting the booster /
     * engine tests (which use [fakeDraftableSet]).
     *
     * The fixture is designed so that a *correct* drafter exhibits two observable behaviours that a
     * naive one does not:
     *
     * 1. **Power separation (`heuristicBuildsStrongerSeat0PoolThanRaredraft`).** A small "premium"
     *    tier of 20 cards (4 per colour) carries strong [TierCard.pickOrderRank] values (1..20);
     *    the other 180 cards carry NO rating entry and so score the identical neutral floor (0.30).
     *    Crucially every premium is a COMMON, so the strongest cards are NOT the highest-rarity ones.
     *    A power-aware drafter (heuristic) snaps up the premium commons; a rarity-only drafter
     *    (RaredraftBot) chases rares/mythics that sit on the flat floor, so its pool is measurably
     *    weaker. Rank is therefore deliberately *decoupled* from rarity.
     *
     * 2. **Colour commitment (`botsAreAtLeast80PercentIn1or2Colors`).** Two design choices give the
     *    heuristic enough signal to genuinely commit to ≤2 colours:
     *    - **A deep, tied floor.** The 180 unrated cards (90% of the set) share the exact same rating
     *      (0.30). When ratings tie, the heuristic's colour bonus becomes the deciding factor, so
     *      each pick is pulled toward the seat's already-leading colours — a snowball that drives the
     *      seat past the commitment margin and triggers the off-colour penalty.
     *    - **Uniform CMC (= 3) and a single card type (Creature).** This removes the curve/synergy
     *      tie-breakers (which key off CMC) so they cannot fight the colour snowball during the
     *      speculation phase. With a wide *spread* of unique ratings the colour signal is drowned
     *      out and the bot never commits (the historical failure mode); a tied floor restores it.
     *
     * Colours remain mono-coloured and cycle W,U,B,R,G (40 cards per colour) exactly as
     * [HeuristicBotDrafter] reads them from [Card.colors].
     */
    fun fakeRatedDraftableSet(): DraftableSet {
        val cards = (1..200).map { i ->
            val color = COLORS[(i - 1) % 5]
            // Vary rarity so RaredraftBot has something to chase. Decoupled from rating: see below.
            val rarity = when (i % 8) {
                0 -> "mythic"
                1, 2 -> "rare"
                3, 4 -> "uncommon"
                else -> "common"
            }
            // Fix CMC = 3 (neither the ≤2 nor the ≥5 curve band) so curve/synergy bonuses are a
            // constant across every candidate and cannot override the colour snowball.
            fakeCard(i).copy(
                rarity = rarity,
                colors = listOf(color),
                colorIdentity = listOf(color),
                cmc = 3.0,
                typeLine = "Creature",
            )
        }

        // Sheets keep the same 100/50/50 split and balanceColors so every pack offers all 5 colours
        // (a realistic, harder test for colour commitment than single-colour packs).
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

        // Pick the premium cards: [PREMIUM_PER_COLOR] per colour, chosen from each colour's COMMONS
        // so they are exactly the cards a rarity-only drafter passes over. This is what makes power
        // and rarity diverge — the strongest cards are commons, while the chased rares sit on the
        // flat floor.
        val premiumIds: List<String> = COLORS.flatMap { color ->
            cards.asSequence()
                .filter { it.colors.firstOrNull() == color && it.rarity == "common" }
                .take(PREMIUM_PER_COLOR)
                .map { it.scryfallId }
                .toList()
        }
        // Assign each premium a unique strong rank (1..premiumIds.size), interleaved by colour so no
        // single colour owns the very top — this lets different seats seed into different colour
        // pairs instead of the whole 8-seat pod fighting over one pair.
        val premiumRankById: Map<String, Int> =
            premiumIds.mapIndexed { idx, id -> id to (idx + 1) }.toMap()

        // ONLY premium cards get a rating entry. Floor cards are deliberately absent: with no
        // TierCard, WeightedBoosterGenerator sets their DraftCard.pickOrderRank/tierRating to null,
        // so DraftRatingNormalizer scores every floor card the identical neutral 0.30 — the exact
        // tie the colour snowball needs. (TierCard.pickOrderRank is non-null, so a floor entry could
        // not represent "no rank" anyway.)
        val ratings = premiumRankById.entries.associate { (id, rank) ->
            val c = cards.first { it.scryfallId == id }
            id to TierCard(
                c.name, c.scryfallId, c.colors.first(), c.colors, c.rarity,
                rank, "S", "", "", "", "Creature",
            )
        }

        val set = DraftSet("TST", "TST", "Test Set", "2025-01-01", "", "v1", "v1", "v1")
        return DraftableSet(set, cards, config, ratings)
    }

    /** Premium (ranked) commons per colour in [fakeRatedDraftableSet]; the rest are the tied floor. */
    private const val PREMIUM_PER_COLOR = 4
}
