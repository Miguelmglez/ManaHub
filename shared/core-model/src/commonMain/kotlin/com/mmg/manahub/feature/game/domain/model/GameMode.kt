package com.mmg.manahub.feature.game.domain.model

/**
 * Available game modes with their starting life totals.
 */
enum class GameMode(
    val startingLife: Int,
    val displayName: String,
) {
    STANDARD(20, "Standard"),
    COMMANDER(40, "Commander"),
}
