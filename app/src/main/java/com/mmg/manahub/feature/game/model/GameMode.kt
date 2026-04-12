package com.mmg.manahub.feature.game.model

enum class GameMode(
    val startingLife: Int,
    val displayName:  String,
) {
    STANDARD(20,  "Standard"),
    COMMANDER(40, "Commander"),
}
