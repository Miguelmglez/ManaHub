package com.mmg.manahub.feature.game.model

/**
 * A phase stop placed by a player.
 *
 * @param playerId  Who placed this stop.
 * @param phase     Which phase to pause at.
 * @param forTurnOf Whose turn this stop triggers on.
 */
data class PhaseStop(
    val playerId:  Int,
    val phase:     GamePhase,
    val forTurnOf: Int,
)
