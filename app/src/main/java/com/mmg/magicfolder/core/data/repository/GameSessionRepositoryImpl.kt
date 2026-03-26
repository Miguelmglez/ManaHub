package com.mmg.magicfolder.core.data.repository

import com.mmg.magicfolder.core.data.local.dao.DeckStatsRow
import com.mmg.magicfolder.core.data.local.dao.GameSessionDao
import com.mmg.magicfolder.core.data.local.entity.GameSessionEntity
import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers
import com.mmg.magicfolder.core.data.local.entity.PlayerSessionEntity
import com.mmg.magicfolder.core.domain.repository.GameSessionRepository
import com.mmg.magicfolder.feature.game.model.GameResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameSessionRepositoryImpl @Inject constructor(
    private val dao: GameSessionDao,
) : GameSessionRepository {

    override suspend fun saveGameSession(result: GameResult): Long {
        val sessionEntity = GameSessionEntity(
            durationMs  = result.durationMs,
            mode        = result.gameMode.name,
            totalTurns  = result.totalTurns,
            playerCount = result.allPlayers.size,
            winnerId    = result.winner.id,
            winnerName  = result.winner.name,
        )
        val sessionId = dao.insertSession(sessionEntity)

        val playerEntities = result.playerResults.map { pr ->
            PlayerSessionEntity(
                sessionId               = sessionId,
                playerId                = pr.player.id,
                playerName              = pr.player.name,
                finalLife               = pr.finalLife,
                finalPoison             = pr.finalPoison,
                eliminationReason       = pr.eliminationReason?.name,
                commanderDamageDealt    = pr.totalCommanderDamageDealt,
                commanderDamageReceived = pr.totalCommanderDamageReceived,
                isWinner                = pr.player.id == result.winner.id,
            )
        }
        dao.insertPlayerSessions(playerEntities)

        return sessionId
    }

    override fun observeRecentSessions(limit: Int): Flow<List<GameSessionWithPlayers>> =
        dao.observeRecentSessions(limit)

    override fun observeTotalGames(): Flow<Int> =
        dao.observeTotalGames()

    override fun observeWins(playerName: String): Flow<Int> =
        dao.observeWins(playerName)

    override fun observeAvgLifeOnWin(): Flow<Double?> =
        dao.observeAvgLifeOnWin()

    override fun observeAvgLifeOnLoss(): Flow<Double?> =
        dao.observeAvgLifeOnLoss()

    override fun observeDeckStats(): Flow<List<DeckStatsRow>> =
        dao.observeDeckStats()
}
