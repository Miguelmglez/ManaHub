package com.mmg.manahub.feature.game.domain.model

/**
 * Maps a completed [GameResult] to the persistence-safe [GameSessionData] that the
 * [com.mmg.manahub.feature.game.domain.repository.GameSessionRepository] accepts.
 *
 * [GameResult] cannot be used directly in the repository interface because it references
 * [Player] → [com.mmg.manahub.core.ui.theme.PlayerThemeColors] → Compose Color, coupling the
 * interface to the UI layer. [GameSessionData] carries only the fields needed for persistence.
 */
fun GameResult.toSessionData(): GameSessionData = GameSessionData(
    winner = PlayerSaveData(
        id = winner.id,
        name = winner.name,
        isAppUser = winner.isAppUser,
    ),
    allPlayerCount = allPlayers.size,
    gameMode = gameMode,
    totalTurns = totalTurns,
    durationMs = durationMs,
    playerResults = playerResults.map { pr ->
        PlayerResultData(
            player = PlayerSaveData(
                id = pr.player.id,
                name = pr.player.name,
                isAppUser = pr.player.isAppUser,
            ),
            finalLife = pr.finalLife,
            finalPoison = pr.finalPoison,
            eliminationReason = pr.eliminationReason,
            totalCommanderDamageDealt = pr.totalCommanderDamageDealt,
            totalCommanderDamageReceived = pr.totalCommanderDamageReceived,
        )
    },
)
