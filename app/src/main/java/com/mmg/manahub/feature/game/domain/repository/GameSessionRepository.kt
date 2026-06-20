package com.mmg.manahub.feature.game.domain.repository

import com.mmg.manahub.core.data.local.dao.DeckStatsRow
import com.mmg.manahub.core.data.local.dao.EliminationCount
import com.mmg.manahub.core.data.local.dao.LocalSessionHistoryRow
import com.mmg.manahub.core.data.local.dao.ModeCount
import com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers
import com.mmg.manahub.feature.game.domain.model.GameResult
import kotlinx.coroutines.flow.Flow

interface GameSessionRepository {
    suspend fun saveGameSession(result: GameResult): Long
    suspend fun getSessionById(sessionId: Long): GameSessionWithPlayers?
    fun observeRecentSessions(limit: Int = 10): Flow<List<GameSessionWithPlayers>>
    fun observeTotalGames(): Flow<Int>
    fun observeWins(playerName: String): Flow<Int>
    /** Count of sessions won by the local seat (`is_local = 1`); name-agnostic. */
    fun observeLocalWins(): Flow<Int>
    /** Session history resolved against the local seat, most-recent first. */
    fun observeLocalSessionHistory(limit: Int = 50): Flow<List<LocalSessionHistoryRow>>
    fun observeAvgLifeOnWin(): Flow<Double?>
    fun observeAvgLifeOnLoss(): Flow<Double?>
    fun observeDeckStats(): Flow<List<DeckStatsRow>>
    // Profile stats
    fun observeFavoriteMode(): Flow<ModeCount?>
    fun observeAvgDurationMs(): Flow<Double?>
    fun observeMostFrequentElimination(): Flow<EliminationCount?>
    fun observeAvgWinTurn(playerName: String): Flow<Double?>
    fun observeCurrentStreak(playerName: String): Flow<Int>
    suspend fun deleteSession(sessionId: Long)
}
