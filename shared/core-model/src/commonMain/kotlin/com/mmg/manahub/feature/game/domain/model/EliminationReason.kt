package com.mmg.manahub.feature.game.domain.model

/** Reason a player was eliminated from a game session. Persisted as its [name] string in Room. */
enum class EliminationReason {
    LIFE,
    POISON,
    COMMANDER_DAMAGE,
    CONCEDE,
}
