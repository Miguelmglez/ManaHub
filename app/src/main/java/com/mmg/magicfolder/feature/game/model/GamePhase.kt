package com.mmg.magicfolder.feature.game.model

enum class GamePhase {
    UNTAP, UPKEEP, DRAW, MAIN1,
    BEGIN_COMBAT, DECLARE_ATTACKERS, DECLARE_BLOCKERS,
    COMBAT_DAMAGE, END_COMBAT,
    MAIN2, END_STEP, CLEANUP,
}

val GamePhase.displayName: String get() = when (this) {
    GamePhase.UNTAP              -> "Untap"
    GamePhase.UPKEEP             -> "Upkeep"
    GamePhase.DRAW               -> "Draw"
    GamePhase.MAIN1              -> "Main Phase I"
    GamePhase.BEGIN_COMBAT       -> "Begin Combat"
    GamePhase.DECLARE_ATTACKERS  -> "Declare Attackers"
    GamePhase.DECLARE_BLOCKERS   -> "Declare Blockers"
    GamePhase.COMBAT_DAMAGE      -> "Combat Damage"
    GamePhase.END_COMBAT         -> "End Combat"
    GamePhase.MAIN2              -> "Main Phase II"
    GamePhase.END_STEP           -> "End Step"
    GamePhase.CLEANUP            -> "Cleanup"
}

val GamePhase.shortName: String get() = when (this) {
    GamePhase.UNTAP              -> "Untap"
    GamePhase.UPKEEP             -> "Upkeep"
    GamePhase.DRAW               -> "Draw"
    GamePhase.MAIN1              -> "Main I"
    GamePhase.BEGIN_COMBAT       -> "Combat ▶"
    GamePhase.DECLARE_ATTACKERS  -> "Attackers"
    GamePhase.DECLARE_BLOCKERS   -> "Blockers"
    GamePhase.COMBAT_DAMAGE      -> "Damage"
    GamePhase.END_COMBAT         -> "End Combat"
    GamePhase.MAIN2              -> "Main II"
    GamePhase.END_STEP           -> "End"
    GamePhase.CLEANUP            -> "Cleanup"
}
