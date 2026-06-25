package com.mmg.manahub.feature.game.domain.model

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
    val player:                      Player,
    val finalLife:                   Int,
    val finalPoison:                 Int,
    val totalCommanderDamageDealt:   Int,
    val totalCommanderDamageReceived: Int,
    val eliminationReason:           EliminationReason?,
)

enum class EliminationReason {
    LIFE,
    POISON,
    COMMANDER_DAMAGE,
    CONCEDE,
}
