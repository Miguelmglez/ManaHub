package com.mmg.manahub.feature.game.domain.model

/**
 * Phases in a Magic: The Gathering turn, in order.
 */
enum class GamePhase(
    val displayName: String,
    val shortName: String,
) {
    UNTAP("Untap", "Untap"),
    UPKEEP("Upkeep", "Upkeep"),
    DRAW("Draw", "Draw"),
    MAIN1("Main Phase I", "Main I"),
    BEGIN_COMBAT("Begin Combat", "Combat ▶"),
    DECLARE_ATTACKERS("Declare Attackers", "Attackers"),
    DECLARE_BLOCKERS("Declare Blockers", "Blockers"),
    COMBAT_DAMAGE("Combat Damage", "Damage"),
    END_COMBAT("End Combat", "End Combat"),
    MAIN2("Main Phase II", "Main II"),
    END_STEP("End Step", "End"),
    CLEANUP("Cleanup", "Cleanup"),
}
