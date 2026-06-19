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
import com.mmg.manahub.feature.draft.engine.BotHarnessTest.Companion.SEEDS
import com.mmg.manahub.feature.draft.engine.BotHarnessTest.Companion.SHARE_BAR
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Behavioural harness for [BotDrafter] implementations. Runs full 8-person drafts and asserts
 * deck-quality properties of the resulting pools:
 * - bots commit to two colors (measured via top-2-color concentration / share),
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

        override fun pick(
            seat: DraftSeat,
            pack: BoosterPack,
            round: Int,
            pickNumber: Int,
            engine: com.mmg.manahub.feature.draft.domain.model.EngineConfig?,
        ): DraftCard =
            pack.cards.maxByOrNull { rank(it.card.rarity) } ?: pack.cards.first()
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    /**
     * The heuristic drafter must commit to two colors. We measure that with **top-2-color
     * concentration** ([top2ColorShare]) rather than the old "≤2 colors with ≥3 cards each"
     * count. In a 45-card pool from an 8-seat mirror pod, a genuinely 2-color-committed drafter
     * still grabs a handful of off-color speculation picks while colors are still open early in a
     * pack; several of those incidental colors clear a flat ≥3-card bar, so the old count reports
     * "3 colors" and over-penalizes a seat that is clearly committed to two. Top-2-share instead
     * asks what fraction of a seat's COLORED card instances fall in its two most-common colors,
     * which ignores those incidental splashes and captures real commitment.
     *
     * Empirically measured on this fixture (50 seeds × 8 seats × 3 packs):
     * - [HeuristicBotDrafter]: avg top-2-share 0.862; fraction of seats with share ≥ 0.75 = 0.900.
     * - rarity-only [RaredraftBot]: avg top-2-share 0.510; fraction with share ≥ 0.75 = 0.000.
     *
     * So we assert ≥ 0.85 of heuristic seats clear the 0.75 share bar (measured 0.900, comfortable
     * margin), and that this rate strictly beats the rarity-only baseline (measured 0.000) — proving
     * the commitment comes from the heuristic, not from an absolute floor any drafter would clear.
     */
    @Test
    fun botsAreColorCommittedByTop2Share() {
        val heuristicRate = sharePassRate(HeuristicBotDrafter())
        val raredraftRate = sharePassRate(RaredraftBot)
        assertTrue(
            "Expected ≥85% of heuristic seats with top-2-color share ≥ $SHARE_BAR, " +
                "got ${heuristicRate * 100}%",
            heuristicRate >= 0.85,
        )
        assertTrue(
            "Heuristic share-pass rate $heuristicRate should exceed rarity-only baseline " +
                "$raredraftRate",
            heuristicRate > raredraftRate,
        )
    }

    /**
     * The heuristic drafter must build a stronger *own* pool than the rarity-only baseline.
     *
     * Why we measure SEAT 0 only (and not every seat's pool): in [runDraft] all 8 seats are driven
     * by the same drafter, and together they consume every card in every pack. The UNION of all 8
     * pools is therefore always the entire generated card set — identical for any drafter — so any
     * statistic over all pools is drafter-invariant (the previous version of this test compared two
     * such averages and only ever saw float-summation noise: 0.5086…86 vs 0.5086…89).
     *
     * Seat 0's pool, by contrast, is exactly the set of cards THIS drafter chose for itself while
     * competing against seven mirror opponents — the one thing the drafter actually controls. The
     * fixture's premiums are high-power COMMONS (rank decoupled from rarity): the heuristic picks
     * by power and grabs them, while [RaredraftBot] chases rares/mythics that sit on the flat floor,
     * so the heuristic's pool is measurably stronger. We compare the full seat-0 pool; the gap is
     * driven by the early picks where the contested premiums are still on the table.
     */
    @Test
    fun heuristicBuildsStrongerSeat0PoolThanRaredraft() {
        val heuristicPower = seat0AvgPower(HeuristicBotDrafter())
        val raredraftPower = seat0AvgPower(RaredraftBot)
        assertTrue(
            "Heuristic seat-0 avg power $heuristicPower should exceed raredraft $raredraftPower",
            heuristicPower > raredraftPower,
        )
    }

    // ── Harness ────────────────────────────────────────────────────────────────

    /**
     * Runs one full 8-person, 3-pack draft where every seat (including seat 0) is driven by
     * [drafter]. Returns the final pool of each seat.
     */
    private fun runDraft(drafter: BotDrafter, seed: Int): List<List<DraftCard>> {
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
            val pick = drafter.pick(humanSeat, humanPack, state.round, state.pickNumber, engine = null)
            state = engine.applyHumanPick(state, pick.card.scryfallId, engine = null)
            guard++
        }

        return state.seats.map { it.pool }
    }

    /**
     * Average tier power of SEAT 0's pool only, over all [SEEDS]. Seat 0 (`isHuman = true`) is the
     * seat [runDraft] drives explicitly with [drafter]; the returned list is seat-index ordered, so
     * the first pool is seat 0's. Unlike an all-seats average this is NOT drafter-invariant, because
     * the seven opponents take the cards seat 0 leaves behind.
     */
    private fun seat0AvgPower(drafter: BotDrafter): Double {
        val scores = SEEDS.flatMap { seed ->
            val seat0Pool = runDraft(drafter, seed).first()
            seat0Pool.map { ratingScore(it) }
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

    /** Fraction of [SEEDS] × seats whose pool clears the top-2-color [SHARE_BAR]. */
    private fun sharePassRate(drafter: BotDrafter): Double {
        val pools = SEEDS.flatMap { seed -> runDraft(drafter, seed) }
        return pools.count { pool -> top2ColorShare(pool) >= SHARE_BAR }.toDouble() / pools.size
    }

    /**
     * Top-2-color concentration of a pool: the sum of the two largest per-color card counts divided
     * by the total colored card instances. Lands and colorless cards contribute no colors and are
     * ignored. Returns 1.0 for a pool with zero colored cards (vacuously concentrated; avoids
     * division by zero). A multicolor card contributes once to each of its colors.
     */
    private fun top2ColorShare(pool: List<DraftCard>): Double {
        val colorCounts = pool.flatMap { it.card.colors }
            .groupingBy { it }
            .eachCount()
        val totalColored = colorCounts.values.sum()
        if (totalColored == 0) return 1.0
        val top2 = colorCounts.values.sortedDescending().take(2).sum()
        return top2.toDouble() / totalColored
    }

    private companion object {
        val SEEDS = (1..50)
        const val MAX_RANK = 200.0
        const val SHARE_BAR = 0.75
        const val MAX_PICKS = 1000
    }
}
