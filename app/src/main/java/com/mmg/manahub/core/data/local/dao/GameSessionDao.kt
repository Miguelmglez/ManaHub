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
    val deckId:     String?,   // deck UUID (was Long? before v37)
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

/**
 * Session history projection resolved against the local seat (`is_local = 1`).
 *
 * Win/loss is read from the seat's [PlayerSessionEntity.isWinner] flag rather than a
 * fragile `winnerName == playerName` string match (see ADR-001). The per-seat deck
 * fields are exposed for forward use; the survey-set [GameSessionEntity.deckId] remains
 * the canonical per-deck stats field for backward compatibility.
 */
data class LocalSessionHistoryRow(
    val sessionId: Long,
    val mode: String,
    val totalTurns: Int,
    val durationMs: Long,
    val playedAt: Long,
    val winnerName: String,
    val surveyStatus: String,
    val localIsWinner: Boolean,
    val localDeckId: String?,
    val localDeckName: String?,
)

/** Matchup win-rate aggregate grouped by the opponent seat's classified archetype. */
data class ArchetypeMatchupRow(
    val opponentArchetype: String,
    val totalGames: Int,
    val wins: Int,
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

    /**
     * Count of sessions where the local seat (`is_local = 1`) won.
     *
     * Authoritative win count: the local seat's [PlayerSessionEntity.isWinner] flag is set
     * at game end and corrected by the survey result write-back, so it never depends on a
     * `winnerName == playerName` string match (which breaks when the stored name differs
     * from the current UserPreferences name, e.g. the default "Wizard").
     */
    @Query("""
        SELECT COUNT(*) FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.is_local = 1 AND ps.isWinner = 1
    """)
    abstract fun observeLocalWins(): Flow<Int>

    /** All sessions with their local-seat result, ordered most-recent first, for history display. */
    @Query("""
        SELECT gs.id AS sessionId,
               gs.mode,
               gs.totalTurns,
               gs.durationMs,
               gs.playedAt,
               gs.winnerName,
               gs.surveyStatus,
               ps.isWinner AS localIsWinner,
               ps.deck_id  AS localDeckId,
               ps.deckName AS localDeckName
        FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id AND ps.is_local = 1
        ORDER BY gs.playedAt DESC
        LIMIT :limit
    """)
    abstract fun observeLocalSessionHistory(limit: Int = 50): Flow<List<LocalSessionHistoryRow>>

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
        SELECT ps.deck_id AS deckId, ps.deckName AS deckName,
               COUNT(*) AS totalGames,
               COALESCE(SUM(CASE WHEN ps.isWinner = 1 THEN 1 ELSE 0 END), 0) AS wins
        FROM player_sessions ps
        WHERE ps.deck_id IS NOT NULL
        GROUP BY ps.deck_id
    """)
    abstract fun observeDeckStats(): Flow<List<DeckStatsRow>>

    @Query("DELETE FROM game_sessions WHERE id = :sessionId")
    abstract suspend fun deleteSession(sessionId: Long)

    // ── Survey lifecycle ───────────────────────────────────────────────────────

    @Query("UPDATE game_sessions SET surveyStatus = :status, surveyCompletedAt = :completedAt WHERE id = :sessionId")
    abstract suspend fun updateSurveyStatus(sessionId: Long, status: String, completedAt: Long?)

    @Query("UPDATE game_sessions SET deckId = :deckId WHERE id = :sessionId")
    abstract suspend fun updateSessionDeck(sessionId: Long, deckId: String?)

    // ── Result write-back (survey can correct the recorded outcome) ──────────────

    /** Records a concrete winner for the session (used when the survey result is a win). */
    @Query("UPDATE game_sessions SET winnerId = :winnerId, winnerName = :winnerName WHERE id = :sessionId")
    abstract suspend fun updateSessionResult(sessionId: Long, winnerId: Int, winnerName: String)

    /** Records a draw for the session (winnerId = -1 sentinel, winnerName = "Draw"). */
    @Query("UPDATE game_sessions SET winnerId = -1, winnerName = 'Draw' WHERE id = :sessionId")
    abstract suspend fun updateSessionResultDraw(sessionId: Long)

    /**
     * Updates the local seat's [PlayerSessionEntity.isWinner] flag without touching any
     * opponent seat. [localIsWinner] is applied only to the seat with `is_local = 1`.
     */
    @Query("UPDATE player_sessions SET isWinner = CASE WHEN is_local = 1 THEN :localIsWinner ELSE isWinner END WHERE sessionId = :sessionId")
    abstract suspend fun updateLocalSeatWinner(sessionId: Long, localIsWinner: Boolean)

    /** Sets the deck archetype on a specific opponent seat (matched by playerId). */
    @Query("UPDATE player_sessions SET archetype = :archetype WHERE sessionId = :sessionId AND playerId = :playerId")
    abstract suspend fun updateSeatArchetype(sessionId: Long, playerId: Int, archetype: String)

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

    /**
     * Per-deck win/loss resolved against the local seat (`is_local = 1`) instead of a
     * `playerName` match. Reads `gs.deckId` (the survey-set deck UUID) so games where the
     * user picked the deck in the survey are still counted, regardless of seat name.
     */
    @Query("""
        SELECT gs.deckId AS deckId,
               NULL AS deckName,
               COUNT(*) AS totalGames,
               COALESCE(SUM(CASE WHEN ps.isWinner = 1 THEN 1 ELSE 0 END), 0) AS wins
        FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.is_local = 1
          AND gs.deckId IS NOT NULL
        GROUP BY gs.deckId
    """)
    abstract fun observeLocalDeckGameStats(): Flow<List<DeckStatsRow>>

    /**
     * Matchup win-rate grouped by the opponent seat's classified archetype.
     *
     * Joins the local seat (`local.is_local = 1`) to every opponent seat
     * (`opp.is_local = 0`) within the same session and counts local wins per opponent
     * archetype. Sessions whose opponent has no archetype are excluded.
     */
    @Query("""
        SELECT opp.archetype AS opponentArchetype,
               COUNT(*) AS totalGames,
               COALESCE(SUM(CASE WHEN local.isWinner = 1 THEN 1 ELSE 0 END), 0) AS wins
        FROM player_sessions local
        INNER JOIN player_sessions opp ON opp.sessionId = local.sessionId
        WHERE local.is_local = 1
          AND opp.is_local = 0
          AND opp.archetype IS NOT NULL
        GROUP BY opp.archetype
        ORDER BY totalGames DESC
    """)
    abstract fun observeArchetypeMatchups(): Flow<List<ArchetypeMatchupRow>>

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
        WHERE is_local = 1 AND eliminationReason IS NOT NULL
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
