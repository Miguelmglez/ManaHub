package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    @Query("""
        UPDATE tournament_matches
        SET winnerId        = :winnerId,
            status          = 'FINISHED',
            gameSessionId   = :sessionId,
            finalLifeTotals = :lifeTotals
        WHERE id = :matchId
    """)
    abstract suspend fun finishMatch(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: String,
    )

    @Query("UPDATE tournament_matches SET status = 'ACTIVE' WHERE id = :matchId")
    abstract suspend fun startMatch(matchId: Long)

    @Query("SELECT * FROM tournament_matches WHERE tournamentId = :tournamentId AND status = 'FINISHED'")
    abstract suspend fun getFinishedMatches(tournamentId: Long): List<TournamentMatchEntity>

    @Query("SELECT COUNT(*) FROM tournament_matches WHERE tournamentId = :tournamentId AND status = 'PENDING'")
    abstract suspend fun getPendingMatchCount(tournamentId: Long): Int

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
}
