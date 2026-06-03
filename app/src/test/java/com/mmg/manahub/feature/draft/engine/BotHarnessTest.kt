package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.feature.draft.data.engine.DefaultDraftEngine
import com.mmg.manahub.feature.draft.data.engine.HeuristicBotDrafter
import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.DraftStatus
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Behavioural harness for [BotDrafter] implementations. Runs full 8-person drafts and asserts
 * deck-quality properties of the resulting pools:
 * - bots converge to 1–2 colors (focus),
 * - the heuristic drafter builds stronger pools than a naive rarity-only baseline.
 *
 * The baseline [RaredraftBot] always takes the highest-rarity card, ignoring tier ratings entirely.
 */
class BotHarnessTest {

    /** Baseline drafter: always picks the highest-rarity card (mythic > rare > uncommon > common). */
    private object RaredraftBot : BotDrafter {
        private fun rank(rarity: String): Int = when (rarity.lowercase()) {
            "mythic" -> 4
            "rare" -> 3
            "uncommon" -> 2
            "common" -> 1
            else -> 0
        }

        override fun pick(seat: DraftSeat, pack: BoosterPack, round: Int, pickNumber: Int): DraftCard =
            pack.cards.maxByOrNull { rank(it.card.rarity) } ?: pack.cards.first()
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun botsAreAtLeast85PercentIn1or2Colors() {
        val pools = SEEDS.flatMap { seed -> runDraft(HeuristicBotDrafter(), seed) }
        val passRate = pools.count { pool -> topColorCount(pool) <= 2 }.toDouble() / pools.size
        assertTrue(
            "Expected ≥85% 1-2 color bots, got ${passRate * 100}%",
            passRate >= 0.85,
        )
    }

    @Test
    fun heuristicStrongerThanRaredraft() {
        val heuristicPower = avgPower(HeuristicBotDrafter())
        val raredraftPower = avgPower(RaredraftBot)
        assertTrue(
            "Heuristic avg power $heuristicPower should exceed raredraft $raredraftPower",
            heuristicPower > raredraftPower,
        )
    }

    // ── Harness ────────────────────────────────────────────────────────────────

    /**
     * Runs one full 8-person, 3-pack draft where every seat (including seat 0) is driven by
     * [drafter]. Returns the final pool of each seat.
     */
    private fun runDraft(drafter: BotDrafter, seed: Int): List<List<DraftCard>> {
        if (drafter is HeuristicBotDrafter) drafter.resetState()

        val generator = WeightedBoosterGenerator(Random(seed.toLong()))
        val engine = DefaultDraftEngine(generator, drafter, Random(seed.toLong()))
        val set = DraftTestFixtures.fakeRatedDraftableSet()

        var state = engine.start(set, DraftConfig("TST", seatCount = 8, packCount = 3))

        // Drive seat 0 (the "human") with the same drafter so all 8 seats use it.
        var guard = 0
        while (state.status == DraftStatus.DRAFTING && guard < MAX_PICKS) {
            val humanIndex = state.seats.indexOfFirst { it.isHuman }.takeIf { it >= 0 } ?: 0
            val humanSeat = state.seats[humanIndex]
            val humanPack = state.packsInFlight[humanIndex]
            if (humanPack == null || humanPack.cards.isEmpty()) break
            val pick = drafter.pick(humanSeat, humanPack, state.round, state.pickNumber)
            state = engine.applyHumanPick(state, pick.card.scryfallId)
            guard++
        }

        return state.seats.map { it.pool }
    }

    /** Average tier power of every card across every seat's pool, over all [SEEDS]. */
    private fun avgPower(drafter: BotDrafter): Double {
        val scores = SEEDS.flatMap { seed ->
            runDraft(drafter, seed).flatMap { pool -> pool.map { ratingScore(it) } }
        }
        return if (scores.isEmpty()) 0.0 else scores.average()
    }

    /**
     * Mirrors [HeuristicBotDrafter]'s rating normalisation so power comparisons are apples-to-apples:
     * lower [DraftCard.pickOrderRank] = stronger; tier-letter fallback otherwise.
     */
    private fun ratingScore(card: DraftCard): Double {
        val rank = card.pickOrderRank
        if (rank != null) {
            return (1.0 - (rank - 1).toDouble() / MAX_RANK).coerceIn(0.0, 1.0)
        }
        return when (card.tierRating?.uppercase()) {
            "S" -> 0.90
            "A" -> 0.75
            "B" -> 0.55
            "C" -> 0.35
            "D" -> 0.20
            "F" -> 0.05
            else -> 0.30
        }
    }

    /** Number of distinct colors with at least [COLOR_THRESHOLD] cards in the pool. */
    private fun topColorCount(pool: List<DraftCard>): Int =
        pool.flatMap { it.card.colors }
            .groupingBy { it }
            .eachCount()
            .count { it.value >= COLOR_THRESHOLD }

    private companion object {
        val SEEDS = (1..50)
        const val MAX_RANK = 200.0
        const val COLOR_THRESHOLD = 3
        const val MAX_PICKS = 1000
    }
}
