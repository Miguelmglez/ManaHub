package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.feature.draft.data.engine.DefaultDraftEngine
import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.core.domain.engine.BotDrafter
import com.mmg.manahub.core.model.BoosterCardEntry
import com.mmg.manahub.core.model.BoosterConfig
import com.mmg.manahub.core.model.BoosterPack
import com.mmg.manahub.core.model.BoosterSheet
import com.mmg.manahub.core.model.BoosterVariant
import com.mmg.manahub.core.model.DraftCard
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftMode
import com.mmg.manahub.core.model.DraftSeat
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftStatus
import com.mmg.manahub.core.model.DraftableSet
import com.mmg.manahub.core.model.PassDirection
import com.mmg.manahub.core.model.TierCard
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
            engine: com.mmg.manahub.core.model.EngineConfig?,
        ) = pack.cards.minByOrNull { it.pickOrderRank ?: Int.MAX_VALUE } ?: pack.cards.first()
    }

    private val gen = WeightedBoosterGenerator(Random(42))
    private val engine = DefaultDraftEngine(gen, fakeBot, Random(42))
    private val set = fakeDraftableSet()
    private val config = DraftConfig("TST", DraftMode.DRAFT, seatCount = 8, packCount = 3)

    private fun runFullDraft(): com.mmg.manahub.core.model.DraftState {
        var state = engine.start(set, config)
        while (!engine.isComplete(state)) {
            val humanIndex = state.seats.indexOfFirst { it.isHuman }
            val pack = state.packsInFlight[humanIndex] ?: break
            val firstCard = pack.cards.firstOrNull() ?: break
            state = engine.applyHumanPick(state, listOf(firstCard.card.scryfallId), engine = null)
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
            state = engine.applyHumanPick(state, listOf(card.card.scryfallId), engine = null)
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
            state = engine.applyHumanPick(state, listOf(firstCard.card.scryfallId), engine = null)
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

    // ── Pick 2 mode (picksPerTurn) ──────────────────────────────────────────────

    @Test
    fun picksPerTurn1_resetsPicksTakenInTurnEveryCall() {
        // Regression: Pick-1 (the default) must behave byte-identical to before — one
        // applyHumanPick call always completes the turn (rotates), leaving picksTakenInTurn at 0.
        var state = engine.start(set, config) // config.picksPerTurn defaults to 1
        val humanIndex = state.seats.indexOfFirst { it.isHuman }
        val firstCard = state.packsInFlight[humanIndex]!!.cards.first()
        state = engine.applyHumanPick(state, listOf(firstCard.card.scryfallId), engine = null)

        assertEquals(0, state.picksTakenInTurn)
        assertEquals(1, state.seats.first { it.isHuman }.pool.size)
    }

    @Test
    fun picksPerTurn2_oddPackFinalTurnTakesExactlyOneCardBeforeRotating() {
        // A 3-seat, 1-pack, 3-card-per-pack table with picksPerTurn=2: turn 1 takes 2 (rotates,
        // 1 card left in every pack); turn 2 can only take the 1 remaining card even though 2 ids
        // are requested, and that short turn still rotates/completes the round.
        val picksPerTurnConfig = DraftConfig(
            "TST", DraftMode.DRAFT, seatCount = 3, packCount = 1, picksPerTurn = 2,
        )
        val packSize = 3
        fun packFor(seatIdx: Int) = BoosterPack(
            "pack-$seatIdx",
            (0 until packSize).map { DraftCard(fakeCard(seatIdx * 100 + it)) },
        )
        var state = DraftState(
            config = picksPerTurnConfig,
            round = 1,
            pickNumber = 1,
            seats = List(3) { i -> DraftSeat(index = i, isHuman = i == 0) },
            packsInFlight = (0 until 3).associateWith { packFor(it) },
            passDirection = PassDirection.LEFT,
            status = DraftStatus.DRAFTING,
        )

        val firstTwoIds = state.packsInFlight[0]!!.cards.take(2).map { it.card.scryfallId }
        state = engine.applyHumanPick(state, firstTwoIds, engine = null)

        // Turn 1 took exactly picksPerTurn (2) cards from every seat and rotated.
        assertEquals(0, state.picksTakenInTurn)
        assertTrue(
            "All packs must stay in lockstep after rotation",
            state.packsInFlight.values.all { it.cards.size == 1 },
        )

        // Turn 2: only 1 card remains, but we (defensively) request 2 ids — only the 1 available
        // card is taken, and the short turn still completes the round (packCount=1 -> BUILDING).
        val remainingId = state.packsInFlight[0]!!.cards.first().card.scryfallId
        state = engine.applyHumanPick(state, listOf(remainingId, "does-not-exist"), engine = null)

        val humanSeat = state.seats.first { it.isHuman }
        assertEquals("Human should have taken all $packSize cards", packSize, humanSeat.pool.size)
        assertEquals(DraftStatus.BUILDING, state.status)
    }

    @Test
    fun picksPerTurn2_allSeatsStayInLockstepThroughFullDraft() {
        // Full 8-seat, 3-pack draft with picksPerTurn=2 (packSize=14 divides evenly by 2, so
        // every turn takes exactly 2 — no odd-final-turn case here, see the dedicated test above).
        val picksPerTurnConfig = config.copy(picksPerTurn = 2)
        var state = engine.start(set, picksPerTurnConfig)
        var guard = 0
        while (!engine.isComplete(state) && guard < 1_000) {
            val humanIndex = state.seats.indexOfFirst { it.isHuman }
            val pack = state.packsInFlight[humanIndex] ?: break
            val ids = pack.cards.take(2).map { it.card.scryfallId }
            if (ids.isEmpty()) break
            state = engine.applyHumanPick(state, ids, engine = null)

            // Every seat's in-flight pack must be the same size after each call, whether or not
            // this particular call rotated (a partial-turn call would also keep lockstep because
            // only the human — never a bot — picks mid-turn, and no bot pack is touched then).
            val sizes = state.packsInFlight.values.map { it.cards.size }.toSet()
            assertTrue("All packs must be the same size, got $sizes", sizes.size <= 1)
            guard++
        }

        assertEquals(DraftStatus.BUILDING, state.status)
        val humanSeat = state.seats.first { it.isHuman }
        val packSize = 14
        assertEquals(config.packCount * packSize, humanSeat.pool.size)
    }
}
