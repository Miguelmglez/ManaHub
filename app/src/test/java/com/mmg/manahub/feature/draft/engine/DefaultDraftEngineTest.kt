package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.feature.draft.data.engine.DefaultDraftEngine
import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.model.BoosterCardEntry
import com.mmg.manahub.feature.draft.domain.model.BoosterConfig
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.BoosterSheet
import com.mmg.manahub.feature.draft.domain.model.BoosterVariant
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftMode
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.DraftStatus
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import com.mmg.manahub.feature.draft.domain.model.PassDirection
import com.mmg.manahub.feature.draft.domain.model.TierCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DefaultDraftEngineTest {

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
        val cards = (1..400).map { fakeCard(it) }
        val commonEntries = cards.take(200).map { BoosterCardEntry(it.scryfallId, 1) }
        val uncommonEntries = cards.drop(200).take(80).map { BoosterCardEntry(it.scryfallId, 1) }
        val rareEntries = cards.drop(280).take(40).map { BoosterCardEntry(it.scryfallId, 1) }
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
     * Test double that selects the strongest card in the pack — the lowest [DraftCard.pickOrderRank]
     * (rank 1 = first pick), with null-rank cards treated as weakest. This mirrors the real
     * drafters' selection intent so that [autoPick]'s delegation to the bot is genuinely validated
     * (a bot that always returned `pack.cards.first()` could never pick a non-leading card and would
     * make the autoPick assertion vacuous).
     */
    private val fakeBot = object : BotDrafter {
        override fun pick(
            seat: DraftSeat,
            pack: BoosterPack,
            round: Int,
            pickNumber: Int,
            engine: com.mmg.manahub.feature.draft.domain.model.EngineConfig?,
        ) = pack.cards.minByOrNull { it.pickOrderRank ?: Int.MAX_VALUE } ?: pack.cards.first()
    }

    private val gen = WeightedBoosterGenerator(Random(42))
    private val engine = DefaultDraftEngine(gen, fakeBot, Random(42))
    private val set = fakeDraftableSet()
    private val config = DraftConfig("TST", DraftMode.DRAFT, seatCount = 8, packCount = 3)

    private fun runFullDraft(): com.mmg.manahub.feature.draft.domain.model.DraftState {
        var state = engine.start(set, config)
        while (!engine.isComplete(state)) {
            val humanIndex = state.seats.indexOfFirst { it.isHuman }
            val pack = state.packsInFlight[humanIndex] ?: break
            val firstCard = pack.cards.firstOrNull() ?: break
            state = engine.applyHumanPick(state, firstCard.card.scryfallId, engine = null)
        }
        return state
    }

    @Test
    fun fullDraftCompletes() {
        val finalState = runFullDraft()
        assertEquals(DraftStatus.BUILDING, finalState.status)
    }

    @Test
    fun humanPoolSizeAfterDraft() {
        val finalState = runFullDraft()
        val humanSeat = finalState.seats.first { it.isHuman }
        val packSize = 14 // 10 common + 3 uncommon + 1 rare
        assertEquals(config.packCount * packSize, humanSeat.pool.size)
    }

    @Test
    fun eachBotPoolSize() {
        val finalState = runFullDraft()
        val packSize = 14
        finalState.seats.filter { !it.isHuman }.forEach { bot ->
            assertEquals("Bot ${bot.index} pool size", config.packCount * packSize, bot.pool.size)
        }
    }

    @Test
    fun passDirectionAlternatesAfterRound1() {
        var state = engine.start(set, config)
        assertEquals(PassDirection.LEFT, state.passDirection)

        // Exhaust round 1: packSize picks
        val packSize = state.packsInFlight[0]!!.cards.size
        repeat(packSize) {
            val humanIndex = state.seats.indexOfFirst { it.isHuman }
            val card = state.packsInFlight[humanIndex]!!.cards.firstOrNull() ?: return
            state = engine.applyHumanPick(state, card.card.scryfallId, engine = null)
        }

        if (state.round > 1) {
            assertEquals(PassDirection.RIGHT, state.passDirection)
        }
    }

    @Test
    fun autoPick_selectsLowestRank() {
        val cardA = fakeCard(901).copy(scryfallId = "rank-5")
        val cardB = fakeCard(902).copy(scryfallId = "rank-1")
        val cardC = fakeCard(903).copy(scryfallId = "rank-null")

        val draftA = DraftCard(cardA, pickOrderRank = 5, tierRating = "B")
        val draftB = DraftCard(cardB, pickOrderRank = 1, tierRating = "S")
        val draftC = DraftCard(cardC, pickOrderRank = null, tierRating = null)

        val pack = BoosterPack("test-pack", listOf(draftA, draftB, draftC))

        var state = engine.start(set, config)
        // Inject a custom pack for the human seat
        val humanIndex = state.seats.indexOfFirst { it.isHuman }
        state = state.copy(packsInFlight = state.packsInFlight + (humanIndex to pack))

        val after = engine.autoPick(state, engine = null)
        val humanSeat = after.seats.first { it.isHuman }
        assertTrue(
            "autoPick should pick rank-1 card",
            humanSeat.pool.any { it.card.scryfallId == "rank-1" },
        )
    }

    @Test
    fun twoSeatDraftCompletesAllThreeRounds() {
        // Edge case: the smallest legal pod (2 seats). All 3 rounds must complete and the human pool
        // must hold packCount × packSize = 3 × 14 = 42 cards (one pick per pack, every round).
        val twoSeatConfig = DraftConfig("TST", DraftMode.DRAFT, seatCount = 2, packCount = 3)
        var state = engine.start(set, twoSeatConfig)
        var guard = 0
        while (!engine.isComplete(state) && guard < 1_000) {
            val humanIndex = state.seats.indexOfFirst { it.isHuman }
            val pack = state.packsInFlight[humanIndex] ?: break
            val firstCard = pack.cards.firstOrNull() ?: break
            state = engine.applyHumanPick(state, firstCard.card.scryfallId, engine = null)
            guard++
        }

        assertEquals(DraftStatus.BUILDING, state.status)
        val humanSeat = state.seats.first { it.isHuman }
        val packSize = 14 // 10 common + 3 uncommon + 1 rareMythic
        assertEquals(twoSeatConfig.packCount * packSize, humanSeat.pool.size)
    }

    @Test
    fun autoPick_emptyHumanPack_returnsStateUnchanged() {
        // FIX 1: an empty in-flight pack for the human seat must NOT reach the drafter (which would
        // throw on first()/require). autoPick guards it and returns the state unchanged.
        var state = engine.start(set, config)
        val humanIndex = state.seats.indexOfFirst { it.isHuman }
        val emptyPack = BoosterPack("empty", emptyList())
        state = state.copy(packsInFlight = state.packsInFlight + (humanIndex to emptyPack))

        val after = engine.autoPick(state, engine = null)

        // No pick was made; the human pool is unchanged and the call did not throw.
        assertEquals(state.seats.first { it.isHuman }.pool.size, after.seats.first { it.isHuman }.pool.size)
    }

    @Test
    fun sealedMode_immediateBuildingStatus() {
        val sealedConfig = config.copy(mode = DraftMode.SEALED, packCount = 6)
        val state = engine.start(set, sealedConfig)
        assertEquals(DraftStatus.BUILDING, state.status)
    }

    @Test
    fun sealedMode_humanPoolSize() {
        val sealedConfig = config.copy(mode = DraftMode.SEALED, packCount = 6)
        val state = engine.start(set, sealedConfig)
        val humanSeat = state.seats.first { it.isHuman }
        val packSize = 14
        assertEquals(6 * packSize, humanSeat.pool.size)
    }
}
