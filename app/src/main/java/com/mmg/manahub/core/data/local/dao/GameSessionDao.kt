package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mmg.manahub.core.data.local.entity.GameSessionEntity
import com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers
import com.mmg.manahub.core.data.local.entity.PlayerSessionEntity
import kotlinx.coroutines.flow.Flow

/** Projection for per-deck game statistics. */
data class DeckStatsRow(
    val deckId:     Long?,
    val deckName:   String?,
    val totalGames: Int,
    val wins:       Int,
)

data class ModeCount(val mode: String, val count: Int)

data class EliminationCount(val eliminationReason: String, val count: Int)

data class SessionSummary(
    val id:                Long,
    val playedAt:          Long,
    val mode:              String,
    val durationMs:        Long,
    val winnerName:        String,
    val surveyStatus:      String,
    val surveyCompletedAt: Long?,
    val deckId:            String?,
)

/** Per-deck aggregate keyed by deck UUID (new TEXT id). */
data class DeckGameStatsRow(
    val deckId:        String,
    val totalGames:    Int,
    val wins:          Int,
    val avgDurationMs: Double,
)

@Dao
abstract class GameSessionDao {

    @Insert
    abstract suspend fun insertSession(session: GameSessionEntity): Long

    @Insert
    abstract suspend fun insertPlayerSessions(players: List<PlayerSessionEntity>)

    /**
     * Atomically persists a game session and all its player entries.
     *
     * Without @Transaction, a crash between insertSession and insertPlayerSessions
     * would leave an orphaned game_sessions row with no player data, corrupting
     * stats queries that JOIN the two tables.
     */
    @Transaction
    open suspend fun insertSessionWithPlayers(
        session: GameSessionEntity,
        players: List<PlayerSessionEntity>,
    ): Long {
        val sessionId = insertSession(session)
        insertPlayerSessions(players.map { it.copy(sessionId = sessionId) })
        return sessionId
    }

    @Transaction
    @Query("SELECT * FROM game_sessions ORDER BY playedAt DESC")
    abstract fun observeAllSessions(): Flow<List<GameSessionWithPlayers>>

    @Transaction
    @Query("SELECT * FROM game_sessions ORDER BY playedAt DESC LIMIT :limit")
    abstract fun observeRecentSessions(limit: Int): Flow<List<GameSessionWithPlayers>>

    @Transaction
    @Query("SELECT * FROM game_sessions WHERE id = :sessionId")
    abstract suspend fun getSessionById(sessionId: Long): GameSessionWithPlayers?

    @Query("SELECT COUNT(*) FROM game_sessions")
    abstract fun observeTotalGames(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.isWinner = 1 AND ps.playerName = :playerName
    """)
    abstract fun observeWins(playerName: String): Flow<Int>

    @Query("""
        SELECT AVG(ps.finalLife) FROM player_sessions ps
        WHERE ps.isWinner = 1
    """)
    abstract fun observeAvgLifeOnWin(): Flow<Double?>

    @Query("""
        SELECT AVG(ps.finalLife) FROM player_sessions ps
        WHERE ps.isWinner = 0 AND ps.eliminationReason IS NOT NULL
    """)
    abstract fun observeAvgLifeOnLoss(): Flow<Double?>

    @Query("""
        SELECT ps.deckId, ps.deckName,
               COUNT(*) AS totalGames,
               COALESCE(SUM(CASE WHEN ps.isWinner = 1 THEN 1 ELSE 0 END), 0) AS wins
        FROM player_sessions ps
        WHERE ps.deckId IS NOT NULL
        GROUP BY ps.deckId
    """)
    abstract fun observeDeckStats(): Flow<List<DeckStatsRow>>

    @Query("DELETE FROM game_sessions WHERE id = :sessionId")
    abstract suspend fun deleteSession(sessionId: Long)

    // ── Survey lifecycle ───────────────────────────────────────────────────────

    @Query("UPDATE game_sessions SET surveyStatus = :status, surveyCompletedAt = :completedAt WHERE id = :sessionId")
    abstract suspend fun updateSurveyStatus(sessionId: Long, status: String, completedAt: Long?)

    @Query("UPDATE game_sessions SET deckId = :deckId WHERE id = :sessionId")
    abstract suspend fun updateSessionDeck(sessionId: Long, deckId: String?)

    @Query("SELECT COUNT(*) FROM game_sessions WHERE surveyStatus IN ('PENDING','PARTIAL')")
    abstract fun observePendingSurveyCount(): Flow<Int>

    // ── Per-deck stats (new UUID-based) ────────────────────────────────────────

    @Query("""
        SELECT gs.deckId AS deckId,
               COUNT(*)   AS totalGames,
               COALESCE(SUM(CASE WHEN ps.isWinner = 1 THEN 1 ELSE 0 END), 0) AS wins,
               COALESCE(AVG(gs.durationMs), 0.0) AS avgDurationMs
        FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE gs.deckId IS NOT NULL
        AND ps.playerName = :playerName
        GROUP BY gs.deckId
    """)
    abstract fun observeDeckGameStats(playerName: String): Flow<List<DeckGameStatsRow>>

    @Query("""
        SELECT gs.deckId AS deckId,
               COUNT(*)   AS totalGames,
               COALESCE(SUM(CASE WHEN ps.isWinner = 1 THEN 1 ELSE 0 END), 0) AS wins,
               COALESCE(AVG(gs.durationMs), 0.0) AS avgDurationMs
        FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE gs.deckId = :deckId
        AND ps.playerName = :playerName
    """)
    abstract fun observeSingleDeckStats(deckId: String, playerName: String): Flow<DeckGameStatsRow?>

    // ── Profile statistics ─────────────────────────────────────────────────────

    @Query("""
        SELECT mode, COUNT(*) AS count
        FROM game_sessions
        GROUP BY mode
        ORDER BY count DESC
        LIMIT 1
    """)
    abstract fun observeFavoriteMode(): Flow<ModeCount?>

    @Query("SELECT AVG(durationMs) FROM game_sessions")
    abstract fun observeAvgDurationMs(): Flow<Double?>

    @Query("""
        SELECT eliminationReason, COUNT(*) AS count
        FROM player_sessions
        WHERE eliminationReason IS NOT NULL
        GROUP BY eliminationReason
        ORDER BY count DESC
        LIMIT 1
    """)
    abstract fun observeMostFrequentElimination(): Flow<EliminationCount?>

    @Query("""
        SELECT AVG(gs.totalTurns)
        FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.isWinner = 1 AND ps.playerName = :playerName
    """)
    abstract fun observeAvgWinTurn(playerName: String): Flow<Double?>

    @Query("SELECT id, playedAt, mode, durationMs, winnerName, surveyStatus, surveyCompletedAt, deckId FROM game_sessions ORDER BY playedAt DESC")
    abstract fun observeAllSessionSummaries(): Flow<List<SessionSummary>>

    @Query("SELECT id, playedAt, mode, durationMs, winnerName, surveyStatus, surveyCompletedAt, deckId FROM game_sessions WHERE deckId = :deckId ORDER BY playedAt DESC")
    abstract fun observeSessionsForDeck(deckId: String): Flow<List<SessionSummary>>
}
