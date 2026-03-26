package com.mmg.magicfolder.core.domain.repository

import com.mmg.magicfolder.core.data.local.dao.DeckStatsRow
import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers
import com.mmg.magicfolder.feature.game.model.GameResult
import kotlinx.coroutines.flow.Flow

interface GameSessionRepository {
    suspend fun saveGameSession(result: GameResult): Long
    fun observeRecentSessions(limit: Int = 10): Flow<List<GameSessionWithPlayers>>
    fun observeTotalGames(): Flow<Int>
    fun observeWins(playerName: String): Flow<Int>
    fun observeAvgLifeOnWin(): Flow<Double?>
    fun observeAvgLifeOnLoss(): Flow<Double?>
    fun observeDeckStats(): Flow<List<DeckStatsRow>>
}
