package com.mmg.manahub.feature.game.data.repository

import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.entity.GameSessionEntity
import com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers
import com.mmg.manahub.core.data.local.entity.PlayerSessionEntity
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.game.domain.model.ArchetypeMatchupData
import com.mmg.manahub.feature.game.domain.model.DeckStats
import com.mmg.manahub.feature.game.domain.model.EliminationStats
import com.mmg.manahub.feature.game.domain.model.GameModeCount
import com.mmg.manahub.feature.game.domain.model.GameSessionData
import com.mmg.manahub.feature.game.domain.model.PlayerSummaryData
import com.mmg.manahub.feature.game.domain.model.SessionDetail
import com.mmg.manahub.feature.game.domain.model.SessionHistoryEntry
import com.mmg.manahub.feature.game.domain.model.SessionSummaryData
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameSessionRepositoryImpl @Inject constructor(
    private val dao: GameSessionDao,
    private val progressionEventBus: ProgressionEventBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GameSessionRepository {

    override suspend fun saveGameSession(data: GameSessionData): Long = withContext(ioDispatcher) {
        val sessionEntity = GameSessionEntity(
            durationMs  = data.durationMs,
            mode        = data.gameMode.name,
            totalTurns  = data.totalTurns,
            playerCount = data.allPlayerCount,
            winnerId    = data.winner.id,
            winnerName  = data.winner.name,
        )
        // Players are passed without sessionId = 0; insertSessionWithPlayers sets the real id atomically.
        val playerEntities = data.playerResults.map { pr ->
            PlayerSessionEntity(
                sessionId               = 0L,  // filled atomically inside insertSessionWithPlayers
                playerId                = pr.player.id,
                playerName              = pr.player.name,
                finalLife               = pr.finalLife,
                finalPoison             = pr.finalPoison,
                eliminationReason       = pr.eliminationReason?.name,
                commanderDamageDealt    = pr.totalCommanderDamageDealt,
                commanderDamageReceived = pr.totalCommanderDamageReceived,
                isWinner                = pr.player.id == data.winner.id,
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
                mode           = data.gameMode.name,
                playerCount    = playerEntities.size,
                durationMs     = data.durationMs,
                winTurn        = data.totalTurns.takeIf { it > 0 },
                localFinalLife = localPlayerEntity?.finalLife,
                occurredAt     = Clock.System.now(),
            )
        )

        sessionId
    }

    override suspend fun getSessionById(sessionId: Long): SessionDetail? =
        withContext(ioDispatcher) { dao.getSessionById(sessionId)?.toDomain() }

    override fun observeRecentSessions(limit: Int): Flow<List<SessionDetail>> =
        dao.observeRecentSessions(limit).map { list -> list.map { it.toDomain() } }

    override fun observeTotalGames(): Flow<Int> =
        dao.observeTotalGames()

    override fun observeWins(playerName: String): Flow<Int> =
        dao.observeWins(playerName)

    override fun observeLocalWins(): Flow<Int> =
        dao.observeLocalWins()

    override fun observeLocalSessionHistory(limit: Int): Flow<List<SessionHistoryEntry>> =
        dao.observeLocalSessionHistory(limit).map { rows ->
            rows.map { row ->
                SessionHistoryEntry(
                    sessionId     = row.sessionId,
                    mode          = row.mode,
                    totalTurns    = row.totalTurns,
                    durationMs    = row.durationMs,
                    playedAt      = row.playedAt,
                    winnerName    = row.winnerName,
                    surveyStatus  = row.surveyStatus,
                    localIsWinner = row.localIsWinner,
                    localDeckId   = row.localDeckId,
                    localDeckName = row.localDeckName,
                )
            }
        }

    override fun observeAvgLifeOnWin(): Flow<Double?> =
        dao.observeAvgLifeOnWin()

    override fun observeAvgLifeOnLoss(): Flow<Double?> =
        dao.observeAvgLifeOnLoss()

    override fun observeDeckStats(): Flow<List<DeckStats>> =
        dao.observeDeckStats().map { rows ->
            rows.map { row ->
                DeckStats(
                    deckId     = row.deckId,
                    deckName   = row.deckName,
                    totalGames = row.totalGames,
                    wins       = row.wins,
                )
            }
        }

    override fun observeFavoriteMode(): Flow<GameModeCount?> =
        dao.observeFavoriteMode().map { mc -> mc?.let { GameModeCount(it.mode, it.count) } }

    override fun observeAvgDurationMs(): Flow<Double?> =
        dao.observeAvgDurationMs()

    override fun observeMostFrequentElimination(): Flow<EliminationStats?> =
        dao.observeMostFrequentElimination().map { ec ->
            ec?.let { EliminationStats(it.eliminationReason, it.count) }
        }

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

    override fun observePendingSurveyCount(): Flow<Int> =
        dao.observePendingSurveyCount()

    override fun observeLocalDeckGameStats(): Flow<List<DeckStats>> =
        dao.observeLocalDeckGameStats().map { rows ->
            rows.map { row ->
                DeckStats(
                    deckId     = row.deckId,
                    deckName   = row.deckName,
                    totalGames = row.totalGames,
                    wins       = row.wins,
                )
            }
        }

    override fun observeArchetypeMatchups(): Flow<List<ArchetypeMatchupData>> =
        dao.observeArchetypeMatchups().map { rows ->
            rows.map { row ->
                ArchetypeMatchupData(
                    opponentArchetype = row.opponentArchetype,
                    totalGames        = row.totalGames,
                    wins              = row.wins,
                )
            }
        }

    override suspend fun deleteSession(sessionId: Long) =
        withContext(ioDispatcher) { dao.deleteSession(sessionId) }

    // ── Private mapping helpers ───────────────────────────────────────────────

    /** Maps a Room [GameSessionWithPlayers] to the domain [SessionDetail]. */
    private fun GameSessionWithPlayers.toDomain(): SessionDetail = SessionDetail(
        session = SessionSummaryData(
            id           = session.id,
            playedAt     = session.playedAt,
            durationMs   = session.durationMs,
            mode         = session.mode,
            totalTurns   = session.totalTurns,
            playerCount  = session.playerCount,
            winnerName   = session.winnerName,
            surveyStatus = session.surveyStatus,
        ),
        players = players.map { p ->
            PlayerSummaryData(
                id                      = p.id,
                sessionId               = p.sessionId,
                playerId                = p.playerId,
                playerName              = p.playerName,
                finalLife               = p.finalLife,
                finalPoison             = p.finalPoison,
                eliminationReason       = p.eliminationReason,
                commanderDamageDealt    = p.commanderDamageDealt,
                commanderDamageReceived = p.commanderDamageReceived,
                deckId                  = p.deckId,
                deckName                = p.deckName,
                isWinner                = p.isWinner,
                isLocal                 = p.isLocal,
                archetype               = p.archetype,
            )
        },
    )
}
