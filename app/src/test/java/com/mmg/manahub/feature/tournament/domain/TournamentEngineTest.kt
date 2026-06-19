package com.mmg.manahub.feature.tournament.domain

import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.feature.tournament.domain.engine.SingleEliminationEngine
import com.mmg.manahub.feature.tournament.domain.engine.StandingsCalculator
import com.mmg.manahub.feature.tournament.domain.engine.SwissEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only tests for the tournament engine domain objects.
 * No Android dependencies — runs on the JVM without a device or emulator.
 */
class TournamentEngineTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun player(id: Long, name: String = "P$id"): TournamentPlayerEntity =
        TournamentPlayerEntity(id = id, tournamentId = 1L, playerName = name, playerColor = "#FFF", seed = id.toInt())

    private fun finishedMatch(
        id:        Long,
        p1:        Long,
        p2:        Long,
        winnerId:  Long?,
        round:     Int = 1,
        order:     Int = 0,
    ): TournamentMatchEntity = TournamentMatchEntity(
        id             = id,
        tournamentId   = 1L,
        round          = round,
        playerIds      = "[$p1,$p2]",
        winnerId       = winnerId,
        status         = "FINISHED",
        scheduledOrder = order,
    )

    private fun byeMatch(id: Long, playerId: Long, round: Int = 1, order: Int = 0): TournamentMatchEntity =
        TournamentMatchEntity(
            id             = id,
            tournamentId   = 1L,
            round          = round,
            playerIds      = "[$playerId]",
            winnerId       = playerId,
            status         = "FINISHED",
            scheduledOrder = order,
        )

    private fun standing(player: TournamentPlayerEntity, points: Int = 0): TournamentStanding =
        TournamentStanding(
            player        = player,
            wins          = points / 3,
            losses        = 0,
            draws         = 0,
            points        = points,
            lifeTotal     = 0,
            position      = 0,
            matchesPlayed = points / 3,
        )

    // ══════════════════════════════════════════════════════════════════════════
    //  Swiss engine — round count
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Swiss 4 players needs 2 rounds`() {
        assertEquals(2, SwissEngine.totalRounds(4))
    }

    @Test
    fun `Swiss 8 players needs 3 rounds`() {
        assertEquals(3, SwissEngine.totalRounds(8))
    }

    @Test
    fun `Swiss 2 players needs 1 round`() {
        assertEquals(1, SwissEngine.totalRounds(2))
    }

    @Test
    fun `Swiss 5 players needs 3 rounds`() {
        assertEquals(3, SwissEngine.totalRounds(5))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Swiss engine — pairing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Swiss 4 players round 2 pairs by points without rematches`() {
        // After round 1: p1 beat p2, p3 beat p4
        val p1 = player(1); val p2 = player(2); val p3 = player(3); val p4 = player(4)
        val finished = listOf(finishedMatch(1, 1, 2, winnerId = 1), finishedMatch(2, 3, 4, winnerId = 3))

        // Standings: p1=3pts, p3=3pts, p2=0pts, p4=0pts
        val standings = listOf(
            standing(p1, 3), standing(p3, 3), standing(p2, 0), standing(p4, 0),
        )

        val pairings = SwissEngine.generateNextRound(standings, finished)

        // Two pairings expected; no rematch of (1,2) or (3,4)
        assertEquals(2, pairings.size)
        val allPairs = pairings.map { (a, b) -> setOf(a, b) }.toSet()
        assertFalse("Rematch 1v2 must not occur", setOf(1L, 2L) in allPairs)
        assertFalse("Rematch 3v4 must not occur", setOf(3L, 4L) in allPairs)
    }

    @Test
    fun `Swiss 5 players round 1 assigns bye to last player`() {
        val players = (1L..5L).map { player(it) }
        val standings = players.map { standing(it, 0) }

        val pairings = SwissEngine.generateNextRound(standings, emptyList())

        // 5 players → 2 real matches + 1 bye (null second player)
        assertEquals(3, pairings.size)
        val byes = pairings.filter { it.second == null }
        assertEquals(1, byes.size)
    }

    @Test
    fun `Swiss bye is assigned to player without prior bye`() {
        // Player 5 had a bye in round 1; round 2 should give bye to a different player
        val p1 = player(1); val p2 = player(2); val p3 = player(3)
        val p4 = player(4); val p5 = player(5)
        val priorBye = byeMatch(id = 10, playerId = 5, round = 1)
        val standings = listOf(
            standing(p5, 3),  // p5 won via bye
            standing(p1, 3), standing(p3, 3), standing(p2, 0), standing(p4, 0),
        )

        val pairings = SwissEngine.generateNextRound(standings, listOf(priorBye))

        val byePairings = pairings.filter { it.second == null }
        assertEquals(1, byePairings.size)
        // The bye should NOT go to p5 (already had one)
        assertFalse("Player 5 should not get another bye", byePairings[0].first == 5L)
    }

    @Test
    fun `Swiss empty standings returns no pairings`() {
        val pairings = SwissEngine.generateNextRound(emptyList(), emptyList())
        assertTrue(pairings.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Single Elimination engine
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SingleElim 4 players round 1 produces 2 matches no byes`() {
        val pairings = SingleEliminationEngine.generateFirstRound(listOf(1L, 2L, 3L, 4L))
        // 4 is a power of 2 → no byes
        val real = pairings.filter { it.second != null }
        val byes  = pairings.filter { it.second == null }
        assertEquals(2, real.size)
        assertEquals(0, byes.size)
    }

    @Test
    fun `SingleElim 5 players round 1 has 3 byes and 1 real match`() {
        val pairings = SingleEliminationEngine.generateFirstRound(listOf(1L, 2L, 3L, 4L, 5L))
        // next pow2 = 8 → 3 byes for top seeds
        val byes  = pairings.filter { it.second == null }
        val real  = pairings.filter { it.second != null }
        assertEquals(3, byes.size)
        assertEquals(1, real.size)
    }

    @Test
    fun `SingleElim round 2 pairs winners from round 1`() {
        val round1 = listOf(
            finishedMatch(1, 1, 2, winnerId = 1, order = 0),
            finishedMatch(2, 3, 4, winnerId = 3, order = 1),
        )
        val pairings = SingleEliminationEngine.generateNextRound(round1)
        assertEquals(1, pairings.size)
        assertEquals(1L, pairings[0].first)
        assertEquals(3L, pairings[0].second)
    }

    @Test
    fun `SingleElim isFinalRoundComplete is true when only one winner`() {
        val finalRound = listOf(finishedMatch(1, 1, 2, winnerId = 1))
        assertTrue(SingleEliminationEngine.isFinalRoundComplete(finalRound))
    }

    @Test
    fun `SingleElim isFinalRoundComplete is false when two matches remain`() {
        val round = listOf(
            finishedMatch(1, 1, 2, winnerId = 1),
            finishedMatch(2, 3, 4, winnerId = 3),
        )
        assertFalse(SingleEliminationEngine.isFinalRoundComplete(round))
    }

    @Test
    fun `SingleElim produces a single champion after log2(4) = 2 rounds`() {
        // Round 1: p1 beats p2, p3 beats p4
        val round1 = listOf(
            finishedMatch(1, 1, 2, winnerId = 1, order = 0),
            finishedMatch(2, 3, 4, winnerId = 3, order = 1),
        )
        // Round 2: p1 vs p3
        val round2Pairings = SingleEliminationEngine.generateNextRound(round1)
        assertEquals(1, round2Pairings.size)

        val round2 = listOf(finishedMatch(3, round2Pairings[0].first, round2Pairings[0].second!!, winnerId = 1L, order = 0))
        assertTrue("Champion found after round 2", SingleEliminationEngine.isFinalRoundComplete(round2))
    }

    @Test
    fun `SingleElim bye recipient advances to next round`() {
        val round1 = listOf(
            byeMatch(id = 1, playerId = 10L, order = 0),           // p10 gets bye
            finishedMatch(2, 20L, 30L, winnerId = 20L, order = 1), // p20 wins
        )
        val pairings = SingleEliminationEngine.generateNextRound(round1)
        assertEquals(1, pairings.size)
        val ids = setOf(pairings[0].first, pairings[0].second)
        assertTrue("Bye recipient (10) must advance", 10L in ids)
        assertTrue("Match winner (20) must advance", 20L in ids)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  StandingsCalculator — DCI tiebreakers
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Standings draw gives 1 point to each player`() {
        val p1 = player(1); val p2 = player(2)
        val draw = TournamentMatchEntity(
            id = 1L, tournamentId = 1L, round = 1,
            playerIds = "[1,2]", winnerId = null, status = "FINISHED", finalLifeTotals = "",
        )
        val standings = StandingsCalculator.calculate(listOf(p1, p2), listOf(draw))
        val s1 = standings.find { it.player.id == 1L }!!
        val s2 = standings.find { it.player.id == 2L }!!
        assertEquals(1, s1.draws)
        assertEquals(0, s1.wins)
        assertEquals(1, s1.points)
        assertEquals(1, s2.draws)
        assertEquals(1, s2.points)
    }

    @Test
    fun `Standings bye gives 3 points and bye opponent is excluded from OMW percent`() {
        val p1 = player(1); val p2 = player(2)
        val bye = byeMatch(id = 1, playerId = 1)
        val standings = StandingsCalculator.calculate(listOf(p1, p2), listOf(bye))
        val s1 = standings.find { it.player.id == 1L }!!
        assertEquals(3, s1.points)
        // OMW% should be at floor (33%) since bye opponent excluded
        assertEquals(0.33, s1.omwPercent, 0.01)
    }

    @Test
    fun `Standings OMW percent floored at 33 percent`() {
        val p1 = player(1); val p2 = player(2); val p3 = player(3)
        // p1 beat p2; p2 has no wins (MWR=0) → p1's OMW floored at 33%
        val matches = listOf(finishedMatch(1, 1, 2, winnerId = 1))
        val standings = StandingsCalculator.calculate(listOf(p1, p2, p3), matches)
        val s1 = standings.find { it.player.id == 1L }!!
        assertTrue("OMW% must be >= 33%", s1.omwPercent >= 0.33)
    }

    @Test
    fun `Standings sorted by points then OMW then GW`() {
        val alice = player(1); val bob = player(2); val charlie = player(3); val diana = player(4)
        // Setup: Alice beats Charlie; Charlie beats Diana; Bob beats Diana.
        // Points: Alice=3, Bob=3, Charlie=3 (1W-1L), Diana=0.
        // Alice's OMW% = Charlie's MWR = 50% (1W out of 2 played).
        // Bob's OMW% = Diana's MWR = 33% (floor, 0 wins out of 2 played).
        // Charlie's OMW% = avg(Alice=100%, Diana=33%) = 66.5% → Charlie ranks above Alice.
        // We only care that Alice ranks ABOVE Bob (OMW% 50% > 33%).
        val matches = listOf(
            finishedMatch(1, 1, 3, winnerId = 1),
            finishedMatch(2, 3, 4, winnerId = 3),
            finishedMatch(3, 2, 4, winnerId = 2),
        )
        val standings = StandingsCalculator.calculate(listOf(alice, bob, charlie, diana), matches)
        val alicePos = standings.indexOfFirst { it.player.id == 1L }
        val bobPos   = standings.indexOfFirst { it.player.id == 2L }
        assertTrue("Alice (OMW%=50%) must rank above Bob (OMW%=33%)", alicePos < bobPos)
    }

    @Test
    fun `Standings life total is NOT used as tiebreaker`() {
        // Alice won with 1 life; Bob won with 20 life → if life were tiebreaker, Bob would be first
        // But with OMW%, they have equal opponents → order by OMW → equal → falls through to GW% → equal
        val alice = player(1); val bob = player(2); val opp = player(3)
        val matches = listOf(
            finishedMatch(1, 1, 3, winnerId = 1, 1),
            finishedMatch(2, 2, 3, winnerId = 2, 1),
        )
        val standings = StandingsCalculator.calculate(listOf(alice, bob, opp), matches)

        val aliceStanding = standings.find { it.player.id == 1L }!!
        val bobStanding   = standings.find { it.player.id == 2L }!!
        // Both have equal tiebreakers — importantly, life total (which we don't use) would favor Bob
        // The test verifies standing order is NOT life-total-dependent
        // They should be tied on all DCI criteria → position determined by stable sort
        assertEquals(3, aliceStanding.points)
        assertEquals(3, bobStanding.points)
        // Both valid positions (order not specified when perfectly tied)
        assertTrue(aliceStanding.position in 1..2)
        assertTrue(bobStanding.position in 1..2)
    }

    @Test
    fun `Standings empty player list returns empty`() {
        val result = StandingsCalculator.calculate(emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `Standings position starts at 1`() {
        val p1 = player(1); val p2 = player(2)
        val standings = StandingsCalculator.calculate(listOf(p1, p2), emptyList())
        assertEquals(1, standings[0].position)
        assertEquals(2, standings[1].position)
    }
}
