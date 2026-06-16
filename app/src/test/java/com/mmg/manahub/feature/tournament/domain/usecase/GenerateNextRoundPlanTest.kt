package com.mmg.manahub.feature.tournament.domain.usecase

import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only tests for [GenerateNextRoundUseCase.plan] — the SINGLE pure advancement decision used by
 * both the standalone [GenerateNextRoundUseCase.invoke] and the repository's atomic finish path
 * (audit C1). `plan` touches no DB, so the [dao] mock is never invoked here.
 *
 * Covers: H2 round-aware advancement (lowest fully-finished round without a successor), M1 globally
 * monotonic scheduledOrder for the next round, and the per-structure outcomes (SWISS / SINGLE_ELIM /
 * ROUND_ROBIN) including the M4 knockout-final completion.
 */
class GenerateNextRoundPlanTest {

    private val dao = mockk<com.mmg.manahub.core.data.local.dao.TournamentDao>(relaxed = true)
    private val useCase = GenerateNextRoundUseCase(dao, UnconfinedTestDispatcher())

    private fun tournament(structure: String, id: Long = 1L) = TournamentEntity(
        id = id, name = "T", format = "STANDARD", structure = structure, status = "ACTIVE",
        matchesPerPairing = 1, isRandomPairings = false,
    )

    private fun player(id: Long) =
        TournamentPlayerEntity(id = id, tournamentId = 1L, playerName = "P$id", playerColor = "#FFF", seed = id.toInt())

    private fun match(
        id: Long, p1: Long, p2: Long?, winnerId: Long?, status: String, round: Int, order: Int,
    ) = TournamentMatchEntity(
        id = id, tournamentId = 1L, round = round,
        playerIds = if (p2 == null) "[$p1]" else "[$p1,$p2]",
        winnerId = winnerId, status = status, scheduledOrder = order,
    )

    // ── ROUND_ROBIN ──────────────────────────────────────────────────────────────

    @Test
    fun `ROUND_ROBIN with a finished round is reported finished, never generates a round`() {
        val matches = listOf(match(1, 1, 2, winnerId = 1, status = "FINISHED", round = 1, order = 0))
        val plan = useCase.plan(tournament("ROUND_ROBIN"), listOf(player(1), player(2)), matches)
        assertEquals(NextRoundResult.TournamentFinished, plan.result)
        assertTrue(plan.tournamentFinished)
        assertTrue(plan.matchesToInsert.isEmpty())
    }

    @Test
    fun `empty match list is reported finished`() {
        val plan = useCase.plan(tournament("SWISS"), listOf(player(1)), emptyList())
        assertEquals(NextRoundResult.TournamentFinished, plan.result)
        assertTrue(plan.tournamentFinished)
    }

    // ── SWISS advancement (C1) ───────────────────────────────────────────────────

    @Test
    fun `SWISS round 1 complete with rounds remaining generates round 2`() {
        // 4 players → 2 rounds. Round 1 finished → round 2 must be generated.
        val players = (1L..4L).map { player(it) }
        val r1 = listOf(
            match(1, 1, 2, winnerId = 1, status = "FINISHED", round = 1, order = 0),
            match(2, 3, 4, winnerId = 3, status = "FINISHED", round = 1, order = 1),
        )
        val plan = useCase.plan(tournament("SWISS"), players, r1)
        assertTrue(plan.result is NextRoundResult.RoundGenerated)
        assertEquals(2, (plan.result as NextRoundResult.RoundGenerated).round)
        assertTrue(plan.matchesToInsert.isNotEmpty())
        assertTrue("generated matches are round 2", plan.matchesToInsert.all { it.round == 2 })
    }

    @Test
    fun `SWISS final round complete reports tournament finished`() {
        // 2 players → 1 round. After round 1 there is nothing left.
        val players = listOf(player(1), player(2))
        val r1 = listOf(match(1, 1, 2, winnerId = 1, status = "FINISHED", round = 1, order = 0))
        val plan = useCase.plan(tournament("SWISS"), players, r1)
        assertEquals(NextRoundResult.TournamentFinished, plan.result)
        assertTrue(plan.tournamentFinished)
    }

    @Test
    fun `SWISS round with a pending match is not complete`() {
        val players = (1L..4L).map { player(it) }
        val r1 = listOf(
            match(1, 1, 2, winnerId = 1, status = "FINISHED", round = 1, order = 0),
            match(2, 3, 4, winnerId = null, status = "PENDING", round = 1, order = 1),
        )
        val plan = useCase.plan(tournament("SWISS"), players, r1)
        assertEquals(NextRoundResult.RoundNotComplete, plan.result)
        assertTrue(plan.matchesToInsert.isEmpty())
    }

    // ── M1: globally monotonic scheduledOrder ────────────────────────────────────

    @Test
    fun `generated next round scheduledOrder is offset past every existing match`() {
        // Round 1 used scheduledOrder 0 and 1; round 2 must start at 2 (never tie round 1).
        val players = (1L..4L).map { player(it) }
        val r1 = listOf(
            match(1, 1, 2, winnerId = 1, status = "FINISHED", round = 1, order = 0),
            match(2, 3, 4, winnerId = 3, status = "FINISHED", round = 1, order = 1),
        )
        val plan = useCase.plan(tournament("SWISS"), players, r1)
        val nextOrders = plan.matchesToInsert.map { it.scheduledOrder }
        assertTrue("round-2 scheduledOrder must be >= 2 (offset past round 1)", nextOrders.all { it >= 2 })
    }

    // ── H2: round-aware advancement ──────────────────────────────────────────────

    @Test
    fun `advances the lowest fully-finished round that has no successor, not maxOf round`() {
        // Round 1 finished AND round 2 already exists (has a successor) → round 1 is NOT re-advanced.
        // Round 2 still pending → nothing to advance → RoundNotComplete (never a duplicate round 2).
        val players = (1L..4L).map { player(it) }
        val matches = listOf(
            match(1, 1, 2, winnerId = 1, status = "FINISHED", round = 1, order = 0),
            match(2, 3, 4, winnerId = 3, status = "FINISHED", round = 1, order = 1),
            match(3, 1, 3, winnerId = null, status = "PENDING", round = 2, order = 2),
        )
        val plan = useCase.plan(tournament("SWISS"), players, matches)
        assertEquals(NextRoundResult.RoundNotComplete, plan.result)
        assertTrue(plan.matchesToInsert.isEmpty())
    }

    // ── SINGLE_ELIM (C1 + M4) ────────────────────────────────────────────────────

    @Test
    fun `SINGLE_ELIM semifinal complete generates the final`() {
        // 4-player bracket, round 1 (semis) finished → round 2 (final) generated.
        val players = (1L..4L).map { player(it) }
        val r1 = listOf(
            match(1, 1, 2, winnerId = 1, status = "FINISHED", round = 1, order = 0),
            match(2, 3, 4, winnerId = 3, status = "FINISHED", round = 1, order = 1),
        )
        val plan = useCase.plan(tournament("SINGLE_ELIM"), players, r1)
        assertTrue(plan.result is NextRoundResult.RoundGenerated)
        assertEquals(1, plan.matchesToInsert.size) // a single final between the two winners
        assertEquals(2, plan.matchesToInsert[0].round)
    }

    @Test
    fun `SINGLE_ELIM final with a single champion reports tournament finished`() {
        val players = (1L..2L).map { player(it) }
        val finalMatch = listOf(match(1, 1, 2, winnerId = 1, status = "FINISHED", round = 1, order = 0))
        val plan = useCase.plan(tournament("SINGLE_ELIM"), players, finalMatch)
        assertEquals(NextRoundResult.TournamentFinished, plan.result)
        assertTrue(plan.tournamentFinished)
    }
}
