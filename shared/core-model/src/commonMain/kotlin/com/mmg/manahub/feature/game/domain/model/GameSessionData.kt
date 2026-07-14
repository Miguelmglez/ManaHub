package com.mmg.manahub.feature.game.domain.model

/**
 * Platform-agnostic save model for a finished game session.
 *
 * Replaces [com.mmg.manahub.feature.game.domain.model.GameResult] in the
 * [com.mmg.manahub.feature.game.domain.repository.GameSessionRepository] interface so that the
 * interface can live in :shared:core-domain (zero platform dependencies) while GameResult stays in
 * :shared:core-ui (it references Player → PlayerThemeColors → Compose Color).
 */
data class PlayerSaveData(
    val id: Int,
    val name: String,
    val isAppUser: Boolean,
)

data class PlayerResultData(
    val player: PlayerSaveData,
    val finalLife: Int,
    val finalPoison: Int,
    val eliminationReason: EliminationReason?,
    val totalCommanderDamageDealt: Int,
    val totalCommanderDamageReceived: Int,
)

data class GameSessionData(
    val winner: PlayerSaveData,
    val allPlayerCount: Int,
    val gameMode: GameMode,
    val totalTurns: Int,
    val durationMs: Long,
    val playerResults: List<PlayerResultData>,
)
