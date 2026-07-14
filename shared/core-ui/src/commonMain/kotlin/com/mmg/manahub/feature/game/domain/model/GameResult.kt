package com.mmg.manahub.feature.game.domain.model

// EliminationReason moved to :shared:core-model so it can be referenced by GameSessionData
// (which lives in commonMain without any core-ui / Compose dependency).
import com.mmg.manahub.feature.game.domain.model.EliminationReason

data class GameResult(
    val winner:           Player,
    val allPlayers:       List<Player>,
    val gameMode:         GameMode,
    val totalTurns:       Int,
    val durationMs:       Long,
    val playerResults:    List<PlayerResult>,
    val appUserWon:       Boolean = false,
    val appUserFinalLife: Int     = 0,
    val appUserName:      String  = "",
)

data class PlayerResult(
    val player:                       Player,
    val finalLife:                    Int,
    val finalPoison:                  Int,
    val totalCommanderDamageDealt:    Int,
    val totalCommanderDamageReceived: Int,
    val eliminationReason:            EliminationReason?,
)
