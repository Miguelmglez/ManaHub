package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.DeckStatsRow
import com.mmg.manahub.core.data.local.dao.EliminationCount
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.LocalSessionHistoryRow
import com.mmg.manahub.core.data.local.dao.ModeCount
import com.mmg.manahub.core.data.local.entity.GameSessionEntity
import com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers
import com.mmg.manahub.core.data.local.entity.PlayerSessionEntity
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.game.domain.model.GameResult
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameSessionRepositoryImpl @Inject constructor(
    private val dao: GameSessionDao,
    private val progressionEventBus: ProgressionEventBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GameSessionRepository {

    override suspend fun saveGameSession(result: GameResult): Long = withContext(ioDispatcher) {
        val sessionEntity = GameSessionEntity(
            durationMs  = result.durationMs,
            mode        = result.gameMode.name,
            totalTurns  = result.totalTurns,
            playerCount = result.allPlayers.size,
            winnerId    = result.winner.id,
            winnerName  = result.winner.name,
        )
        // Players are passed without sessionId=0; insertSessionWithPlayers sets the real id.
        val playerEntities = result.playerResults.map { pr ->
            PlayerSessionEntity(
                sessionId               = 0L,  // filled atomically inside insertSessionWithPlayers
                playerId                = pr.player.id,
                playerName              = pr.player.name,
                finalLife               = pr.finalLife,
                finalPoison             = pr.finalPoison,
                eliminationReason       = pr.eliminationReason?.name,
                commanderDamageDealt    = pr.totalCommanderDamageDealt,
                commanderDamageReceived = pr.totalCommanderDamageReceived,
                isWinner                = pr.player.id == result.winner.id,
                // Persist the app user's seat so the post-game survey can determine
                // win/loss reliably (see ADR-001). isWinner alone is insufficient —
                // every finished game has a winner.
                isLocal                 = pr.player.isAppUser,
            )
        }
        val sessionId = dao.insertSessionWithPlayers(sessionEntity, playerEntities)

        // Emit the progression event AFTER the commit succeeds (ADR-002 §1).
        // isLocalWin derives from the seat with isLocal = true (isAppUser), never from
        // name matching — the stored seat name can diverge from UserPreferences and
        // would silently zero win-rate (see memory feedback_survey_winloss_isLocal).
        val localPlayerEntity = playerEntities.firstOrNull { it.isLocal }
        progressionEventBus.emit(
            ProgressionEvent.GameFinished(
                sessionId      = sessionId,
                isLocalWin     = localPlayerEntity?.isWinner == true,
                mode           = result.gameMode.name,
                playerCount    = playerEntities.size,
                durationMs     = result.durationMs,
                winTurn        = result.totalTurns.takeIf { it > 0 },
                localFinalLife = localPlayerEntity?.finalLife,
                occurredAt     = Instant.now(),
            )
        )

        sessionId
    }

    override suspend fun getSessionById(sessionId: Long): GameSessionWithPlayers? =
        withContext(ioDispatcher) { dao.getSessionById(sessionId) }

    override fun observeRecentSessions(limit: Int): Flow<List<GameSessionWithPlayers>> =
        dao.observeRecentSessions(limit)

    override fun observeTotalGames(): Flow<Int> =
        dao.observeTotalGames()

    override fun observeWins(playerName: String): Flow<Int> =
        dao.observeWins(playerName)

    override fun observeLocalWins(): Flow<Int> =
        dao.observeLocalWins()

    override fun observeLocalSessionHistory(limit: Int): Flow<List<LocalSessionHistoryRow>> =
        dao.observeLocalSessionHistory(limit)

    override fun observeAvgLifeOnWin(): Flow<Double?> =
        dao.observeAvgLifeOnWin()

    override fun observeAvgLifeOnLoss(): Flow<Double?> =
        dao.observeAvgLifeOnLoss()

    override fun observeDeckStats(): Flow<List<DeckStatsRow>> =
        dao.observeDeckStats()

    override fun observeFavoriteMode(): Flow<ModeCount?> =
        dao.observeFavoriteMode()

    override fun observeAvgDurationMs(): Flow<Double?> =
        dao.observeAvgDurationMs()

    override fun observeMostFrequentElimination(): Flow<EliminationCount?> =
        dao.observeMostFrequentElimination()

    override fun observeAvgWinTurn(playerName: String): Flow<Double?> =
        dao.observeAvgWinTurn(playerName)

    override fun observeCurrentStreak(playerName: String): Flow<Int> =
        dao.observeAllSessionSummaries().map { sessions ->
            var streak = 0
            for (session in sessions) {
                if (session.winnerName == playerName) streak++ else break
            }
            streak
        }

    override suspend fun deleteSession(sessionId: Long) =
        withContext(ioDispatcher) { dao.deleteSession(sessionId) }
}
