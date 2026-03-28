package com.mmg.magicfolder.core.data.local.dao

import androidx.room.*
import com.mmg.magicfolder.core.data.local.entity.TournamentEntity
import com.mmg.magicfolder.core.data.local.entity.TournamentMatchEntity
import com.mmg.magicfolder.core.data.local.entity.TournamentPlayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentDao {

    // ── Tournaments ───────────────────────────────────────────────────────────

    @Insert
    suspend fun insertTournament(t: TournamentEntity): Long

    @Update
    suspend fun updateTournament(t: TournamentEntity)

    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    fun observeAllTournaments(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    fun observeTournament(id: Long): Flow<TournamentEntity?>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getTournamentById(id: Long): TournamentEntity?

    @Query("UPDATE tournaments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE tournaments SET finishedAt = :time, status = 'FINISHED' WHERE id = :id")
    suspend fun finishTournament(id: Long, time: Long)

    // ── Players ───────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertPlayers(players: List<TournamentPlayerEntity>): List<Long>

    @Query("SELECT * FROM tournament_players WHERE tournamentId = :tournamentId")
    suspend fun getPlayers(tournamentId: Long): List<TournamentPlayerEntity>

    @Query("SELECT * FROM tournament_players WHERE tournamentId = :tournamentId")
    fun observePlayers(tournamentId: Long): Flow<List<TournamentPlayerEntity>>

    // ── Matches ───────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertMatches(matches: List<TournamentMatchEntity>)

    @Query("""
        SELECT * FROM tournament_matches
        WHERE tournamentId = :tournamentId
        ORDER BY scheduledOrder ASC
    """)
    fun observeMatches(tournamentId: Long): Flow<List<TournamentMatchEntity>>

    @Query("""
        SELECT * FROM tournament_matches
        WHERE tournamentId = :tournamentId AND status = 'PENDING'
        ORDER BY scheduledOrder ASC
        LIMIT 1
    """)
    suspend fun getNextPendingMatch(tournamentId: Long): TournamentMatchEntity?

    @Query("""
        UPDATE tournament_matches
        SET winnerId        = :winnerId,
            status          = 'FINISHED',
            gameSessionId   = :sessionId,
            finalLifeTotals = :lifeTotals
        WHERE id = :matchId
    """)
    suspend fun finishMatch(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: String,
    )

    @Query("UPDATE tournament_matches SET status = 'ACTIVE' WHERE id = :matchId")
    suspend fun startMatch(matchId: Long)

    @Query("SELECT * FROM tournament_matches WHERE tournamentId = :tournamentId AND status = 'FINISHED'")
    suspend fun getFinishedMatches(tournamentId: Long): List<TournamentMatchEntity>

    @Query("SELECT COUNT(*) FROM tournament_matches WHERE tournamentId = :tournamentId AND status = 'PENDING'")
    suspend fun getPendingMatchCount(tournamentId: Long): Int
}
