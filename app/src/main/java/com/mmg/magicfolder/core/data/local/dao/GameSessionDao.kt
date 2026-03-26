package com.mmg.magicfolder.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mmg.magicfolder.core.data.local.entity.GameSessionEntity
import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers
import com.mmg.magicfolder.core.data.local.entity.PlayerSessionEntity
import kotlinx.coroutines.flow.Flow

/** Proyección para estadísticas por mazo. */
data class DeckStatsRow(
    val deckId:     Long?,
    val deckName:   String?,
    val totalGames: Int,
    val wins:       Int,
)

@Dao
interface GameSessionDao {

    @Insert
    suspend fun insertSession(session: GameSessionEntity): Long

    @Insert
    suspend fun insertPlayerSessions(players: List<PlayerSessionEntity>)

    @Transaction
    @Query("SELECT * FROM game_sessions ORDER BY playedAt DESC")
    fun observeAllSessions(): Flow<List<GameSessionWithPlayers>>

    @Transaction
    @Query("SELECT * FROM game_sessions ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int = 10): Flow<List<GameSessionWithPlayers>>

    @Transaction
    @Query("SELECT * FROM game_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): GameSessionWithPlayers?

    @Query("SELECT COUNT(*) FROM game_sessions")
    fun observeTotalGames(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.isWinner = 1 AND ps.playerName = :playerName
    """)
    fun observeWins(playerName: String): Flow<Int>

    @Query("""
        SELECT AVG(ps.finalLife) FROM player_sessions ps
        WHERE ps.isWinner = 1
    """)
    fun observeAvgLifeOnWin(): Flow<Double?>

    @Query("""
        SELECT AVG(ps.finalLife) FROM player_sessions ps
        WHERE ps.isWinner = 0 AND ps.eliminationReason IS NOT NULL
    """)
    fun observeAvgLifeOnLoss(): Flow<Double?>

    @Query("""
        SELECT ps.deckId, ps.deckName,
               COUNT(*) AS totalGames,
               SUM(CASE WHEN ps.isWinner = 1 THEN 1 ELSE 0 END) AS wins
        FROM player_sessions ps
        WHERE ps.deckId IS NOT NULL
        GROUP BY ps.deckId
    """)
    fun observeDeckStats(): Flow<List<DeckStatsRow>>

    @Query("DELETE FROM game_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
