package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumented Room tests for [TournamentDao.finishMatchAndAdvanceAtomically] — the audit C3
 * first-writer-wins guard + atomic finish-and-advance.
 *
 * Requires a connected device or emulator. Uses an in-memory [MtgDatabase] so tests are hermetic.
 *
 * The concurrency test deliberately runs REAL concurrent transactions on `Dispatchers.IO` (via
 * `runBlocking`, NOT `runTest`) so Room's transaction locking is genuinely exercised — mirroring
 * [GamificationDaoConcurrencyTest]. The whole point: two racing finishes of the SAME match must
 * produce exactly ONE next-round generation (the `status != 'FINISHED'` guard makes the loser a no-op).
 *
 * NOTE: Written but NOT executed here (no connected device at authoring time). Run with
 * `./gradlew connectedAndroidTest` once a device/emulator is attached.
 */
@RunWith(AndroidJUnit4::class)
class TournamentDaoConcurrencyTest {

    private lateinit var db: MtgDatabase
    private lateinit var dao: TournamentDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // No allowMainThreadQueries() — the concurrency test needs real IO-thread transactions.
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java).build()
        dao = db.tournamentDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    // ── Fixture: a 2-player SINGLE_ELIM with one pending round-1 (final) match ─────

    /** Inserts a tournament + 2 players + a single pending round-1 match. Returns (tournamentId, matchId). */
    private suspend fun seedSingleMatchBracket(): Pair<Long, Long> {
        val tournamentId = dao.insertTournament(
            TournamentEntity(
                name = "T", format = "STANDARD", structure = "SINGLE_ELIM", status = "ACTIVE",
            )
        )
        val playerIds = dao.insertPlayers(
            listOf(
                TournamentPlayerEntity(tournamentId = tournamentId, playerName = "A", playerColor = "#FFF", seed = 0),
                TournamentPlayerEntity(tournamentId = tournamentId, playerName = "B", playerColor = "#000", seed = 1),
            )
        )
        dao.insertMatches(
            listOf(
                TournamentMatchEntity(
                    tournamentId = tournamentId, round = 1,
                    playerIds = "[${playerIds[0]},${playerIds[1]}]", status = "PENDING", scheduledOrder = 0,
                )
            )
        )
        val matchId = dao.getNextPendingMatch(tournamentId)!!.id
        return tournamentId to matchId
    }

    // ── C3: repeated finish of an already-FINISHED match is a no-op ───────────────

    @Test
    fun `finishing an already-FINISHED match is a no-op and does not finish the tournament twice`() = runBlocking {
        val (tournamentId, matchId) = seedSingleMatchBracket()
        val winnerId = dao.getMatchById(matchId)!!.playerIds.trim('[', ']').split(",")[0].toLong()

        // The single match IS the final → first finish must report TOURNAMENT_FINISHED.
        val first = dao.finishMatchAndAdvanceAtomically(
            tournamentId = tournamentId, matchId = matchId, winnerId = winnerId,
            sessionId = null, lifeTotals = "{}", finishedAt = 1000L,
            buildAdvancement = { _ ->
                // Single final → tournament finished, no new matches to insert.
                emptyList<TournamentMatchEntity>() to TournamentDao.AdvanceKind.TOURNAMENT_FINISHED
            },
        )
        assertEquals(TournamentDao.AdvanceKind.TOURNAMENT_FINISHED, first)
        assertEquals("FINISHED", dao.getTournamentById(tournamentId)!!.status)

        // A second finish of the SAME match must be a NO_OP (guard returns 0 rows) — buildAdvancement
        // must never even run.
        var advancementRan = false
        val second = dao.finishMatchAndAdvanceAtomically(
            tournamentId = tournamentId, matchId = matchId, winnerId = winnerId,
            sessionId = null, lifeTotals = "{}", finishedAt = 2000L,
            buildAdvancement = { _ ->
                advancementRan = true
                emptyList<TournamentMatchEntity>() to TournamentDao.AdvanceKind.TOURNAMENT_FINISHED
            },
        )
        assertEquals(TournamentDao.AdvanceKind.NO_OP, second)
        assertTrue("buildAdvancement must NOT run on an already-finished match", !advancementRan)
        // Still exactly one match, still FINISHED with the ORIGINAL finishedAt (not overwritten).
        assertEquals(1, dao.getAllMatches(tournamentId).size)
        assertEquals(1000L, dao.getTournamentById(tournamentId)!!.finishedAt)
    }

    // ── C3: two concurrent finishes generate exactly one next round ───────────────

    @Test
    fun `two concurrent finishMatch on the same match generate exactly one next round`() = runBlocking {
        // 4-player SINGLE_ELIM: both round-1 matches finished, the SECOND finish should generate round 2.
        // We race two finishes of the SAME (second) match: exactly one must win and generate the final.
        val tournamentId = dao.insertTournament(
            TournamentEntity(name = "T", format = "STANDARD", structure = "SINGLE_ELIM", status = "ACTIVE")
        )
        val pids = dao.insertPlayers(
            (1..4).map {
                TournamentPlayerEntity(tournamentId = tournamentId, playerName = "P$it", playerColor = "#FFF", seed = it - 1)
            }
        )
        dao.insertMatches(
            listOf(
                // Match 0 already finished (P0 beat P1); match 1 pending (P2 vs P3).
                TournamentMatchEntity(
                    tournamentId = tournamentId, round = 1, playerIds = "[${pids[0]},${pids[1]}]",
                    winnerId = pids[0], status = "FINISHED", scheduledOrder = 0,
                ),
                TournamentMatchEntity(
                    tournamentId = tournamentId, round = 1, playerIds = "[${pids[2]},${pids[3]}]",
                    status = "PENDING", scheduledOrder = 1,
                ),
            )
        )
        val pendingMatchId = dao.getNextPendingMatch(tournamentId)!!.id

        // Build advancement: round 1 fully finished → generate the round-2 final pairing winner0 vs winner1.
        val advancementInvocations = AtomicInteger(0)
        val buildAdvancement: (List<TournamentMatchEntity>) -> Pair<List<TournamentMatchEntity>, TournamentDao.AdvanceKind> =
            { matchesAfterFinish ->
                advancementInvocations.incrementAndGet()
                val round1 = matchesAfterFinish.filter { it.round == 1 }
                val anyPending = round1.any { it.status != "FINISHED" }
                if (anyPending) {
                    emptyList<TournamentMatchEntity>() to TournamentDao.AdvanceKind.ROUND_NOT_COMPLETE
                } else {
                    val winners = round1.sortedBy { it.scheduledOrder }.map { it.winnerId!! }
                    val finalMatch = TournamentMatchEntity(
                        tournamentId = tournamentId, round = 2,
                        playerIds = "[${winners[0]},${winners[1]}]", status = "PENDING",
                        scheduledOrder = round1.maxOf { it.scheduledOrder } + 1,
                    )
                    listOf(finalMatch) to TournamentDao.AdvanceKind.ROUND_GENERATED
                }
            }

        // Race two finishes of the SAME pending match (P2 wins).
        val results = coroutineScope {
            (1..2).map {
                async(Dispatchers.IO) {
                    dao.finishMatchAndAdvanceAtomically(
                        tournamentId = tournamentId, matchId = pendingMatchId, winnerId = pids[2],
                        sessionId = null, lifeTotals = "{}", finishedAt = 1000L,
                        buildAdvancement = buildAdvancement,
                    )
                }
            }.awaitAll()
        }

        // Exactly one winner advanced; the other was a guarded no-op.
        assertEquals("exactly one finish must be a NO_OP", 1, results.count { it == TournamentDao.AdvanceKind.NO_OP })
        assertEquals("exactly one finish must generate the round", 1, results.count { it == TournamentDao.AdvanceKind.ROUND_GENERATED })

        // The crux: exactly ONE round-2 (final) match exists — never a duplicate bracket.
        val round2 = dao.getAllMatches(tournamentId).filter { it.round == 2 }
        assertEquals("exactly one round-2 match must be generated", 1, round2.size)
    }
}
