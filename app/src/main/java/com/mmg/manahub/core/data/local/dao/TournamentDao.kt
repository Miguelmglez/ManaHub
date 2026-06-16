package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TournamentDao {

    // ── Tournaments ───────────────────────────────────────────────────────────

    @Insert
    abstract suspend fun insertTournament(t: TournamentEntity): Long

    @Update
    abstract suspend fun updateTournament(t: TournamentEntity)

    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    abstract fun observeAllTournaments(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    abstract fun observeTournament(id: Long): Flow<TournamentEntity?>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    abstract suspend fun getTournamentById(id: Long): TournamentEntity?

    @Query("SELECT * FROM tournament_matches WHERE id = :matchId LIMIT 1")
    abstract suspend fun getMatchById(matchId: Long): TournamentMatchEntity?

    @Query("UPDATE tournaments SET status = :status WHERE id = :id")
    abstract suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE tournaments SET finishedAt = :time, status = 'FINISHED' WHERE id = :id")
    abstract suspend fun finishTournament(id: Long, time: Long)

    // ── Players ───────────────────────────────────────────────────────────────

    @Insert
    abstract suspend fun insertPlayers(players: List<TournamentPlayerEntity>): List<Long>

    @Query("SELECT * FROM tournament_players WHERE tournamentId = :tournamentId")
    abstract suspend fun getPlayers(tournamentId: Long): List<TournamentPlayerEntity>

    @Query("SELECT * FROM tournament_players WHERE tournamentId = :tournamentId")
    abstract fun observePlayers(tournamentId: Long): Flow<List<TournamentPlayerEntity>>

    // ── Matches ───────────────────────────────────────────────────────────────

    @Insert
    abstract suspend fun insertMatches(matches: List<TournamentMatchEntity>)

    @Query("""
        SELECT * FROM tournament_matches
        WHERE tournamentId = :tournamentId
        ORDER BY scheduledOrder ASC
    """)
    abstract fun observeMatches(tournamentId: Long): Flow<List<TournamentMatchEntity>>

    @Query("""
        SELECT * FROM tournament_matches
        WHERE tournamentId = :tournamentId AND status = 'PENDING'
        ORDER BY scheduledOrder ASC
        LIMIT 1
    """)
    abstract suspend fun getNextPendingMatch(tournamentId: Long): TournamentMatchEntity?

    /**
     * First-writer-wins guarded finish. Only updates a match that is NOT already FINISHED and
     * returns the affected row count: 1 when this call won the race and finished the match, 0 when
     * the match was already FINISHED (a concurrent/repeated finish).
     *
     * The `status != 'FINISHED'` predicate is the correctness gate for the advancement path: a
     * second finish on an already-finished match returns 0 so the caller can short-circuit and NOT
     * generate a duplicate next round / over-grant completion XP (audit C3).
     */
    @Query("""
        UPDATE tournament_matches
        SET winnerId        = :winnerId,
            status          = 'FINISHED',
            gameSessionId   = :sessionId,
            finalLifeTotals = :lifeTotals
        WHERE id = :matchId AND status != 'FINISHED'
    """)
    abstract suspend fun finishMatchGuarded(
        matchId:    Long,
        winnerId:   Long?,
        sessionId:  Long?,
        lifeTotals: String,
    ): Int

    @Query("UPDATE tournament_matches SET status = 'ACTIVE' WHERE id = :matchId")
    abstract suspend fun startMatch(matchId: Long)

    @Query("UPDATE tournament_matches SET status = 'PENDING', winnerId = NULL, gameSessionId = NULL, finalLifeTotals = '' WHERE id = :matchId")
    abstract suspend fun resetMatchToPending(matchId: Long)

    @Query("SELECT * FROM tournament_matches WHERE tournamentId = :tournamentId AND status = 'FINISHED'")
    abstract suspend fun getFinishedMatches(tournamentId: Long): List<TournamentMatchEntity>

    @Query("SELECT * FROM tournament_matches WHERE tournamentId = :tournamentId ORDER BY scheduledOrder ASC")
    abstract suspend fun getAllMatches(tournamentId: Long): List<TournamentMatchEntity>

    @Query("SELECT COUNT(*) FROM tournament_matches WHERE tournamentId = :tournamentId AND status = 'PENDING'")
    abstract suspend fun getPendingMatchCount(tournamentId: Long): Int

    @Query("DELETE FROM tournaments WHERE id = :tournamentId")
    abstract suspend fun deleteTournament(tournamentId: Long)

    // ── Atomic creation ───────────────────────────────────────────────────────

    /**
     * Atomically inserts a tournament, its players, and the first round of matches
     * inside a single SQLite transaction.
     *
     * Without @Transaction a crash between any of the three inserts would leave
     * the tournament in a partially-created, unrecoverable state (players without
     * matches, or a tournament id with no players at all).
     *
     * @param tournament  The tournament entity (id will be auto-generated).
     * @param players     Players without tournamentId set — filled inside this method.
     * @param buildMatches  Lambda that receives the DB-generated player ids and returns
     *                    the match list. Kept as a lambda so match generation (pure
     *                    Kotlin logic in the repository) can use the real player ids
     *                    without breaking the transaction boundary.
     * @return The auto-generated tournament id.
     */
    @Transaction
    open suspend fun insertTournamentAtomically(
        tournament:   TournamentEntity,
        players:      List<TournamentPlayerEntity>,
        buildMatches: (tournamentId: Long, playerIds: List<Long>) -> List<TournamentMatchEntity>,
    ): Long {
        val tournamentId = insertTournament(tournament)
        val playerIds    = insertPlayers(players.map { it.copy(tournamentId = tournamentId) })
        val matches      = buildMatches(tournamentId, playerIds)
        if (matches.isNotEmpty()) insertMatches(matches)
        return tournamentId
    }

    // ── Atomic finish + advancement ─────────────────────────────────────────────

    /**
     * Classification of an atomic finish-and-advance, so the repository can emit gamification XP and
     * set UI state AFTER the transaction commits (XP must be emitted post-commit, ADR-002 §1, and never
     * from inside a Room transaction).
     *
     * [NO_OP] is the DAO's own guard outcome (match already FINISHED). The other three mirror the pure
     * advancement decision the caller supplies via `buildAdvancement` so the repo never has to re-query
     * to disambiguate "round generated" from "round not complete".
     */
    enum class AdvanceKind { NO_OP, ROUND_NOT_COMPLETE, ROUND_GENERATED, TOURNAMENT_FINISHED }

    /**
     * Atomically: (1) first-writer-wins finishes [matchId]; (2) if this call won the race, asks
     * [buildAdvancement] for the advancement decision given the post-finish match list; (3) inserts any
     * generated matches and, when the tournament is complete, stamps `status='FINISHED'`/`finishedAt`.
     *
     * Mirrors [insertTournamentAtomically]: without a single @Transaction a crash between the match
     * UPDATE and the next-round INSERT would leave a finished round with no successor and no FINISHED
     * flag — the exact unrecoverable mid-state the atomic creation path was built to prevent (audit C3).
     *
     * @param buildAdvancement Pure decision function. Receives the full match list AFTER this finish
     *   committed and returns (matches-to-insert, classification). The classification is one of
     *   [AdvanceKind.ROUND_NOT_COMPLETE] / [AdvanceKind.ROUND_GENERATED] / [AdvanceKind.TOURNAMENT_FINISHED]
     *   (NEVER [AdvanceKind.NO_OP], which only the guard returns). It must not touch the DB.
     * @return [AdvanceKind.NO_OP] when the match was already FINISHED (guard returned 0 rows); otherwise
     *   the classification supplied by [buildAdvancement].
     */
    @Transaction
    open suspend fun finishMatchAndAdvanceAtomically(
        tournamentId:     Long,
        matchId:          Long,
        winnerId:         Long?,
        sessionId:        Long?,
        lifeTotals:       String,
        finishedAt:       Long,
        buildAdvancement: (matchesAfterFinish: List<TournamentMatchEntity>) -> Pair<List<TournamentMatchEntity>, AdvanceKind>,
    ): AdvanceKind {
        val finishedRows = finishMatchGuarded(matchId, winnerId, sessionId, lifeTotals)
        if (finishedRows == 0) return AdvanceKind.NO_OP   // already FINISHED → do not double-advance

        val matchesAfterFinish = getAllMatches(tournamentId)
        val (nextRoundMatches, kind) = buildAdvancement(matchesAfterFinish)

        if (nextRoundMatches.isNotEmpty()) insertMatches(nextRoundMatches)
        if (kind == AdvanceKind.TOURNAMENT_FINISHED) finishTournament(tournamentId, finishedAt)

        return kind
    }
}
