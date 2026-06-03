package com.mmg.manahub.core.data.local.entity.projection

import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity

data class TournamentStanding(
    val player:        TournamentPlayerEntity,
    val wins:          Int,
    val losses:        Int,
    val draws:         Int,
    val points:        Int,          // wins*3 + draws*1
    val lifeTotal:     Int,          // accumulated life (display only — NOT a sort criterion)
    val position:      Int,          // rank 1 = first
    val matchesPlayed: Int,
    val nextOpponent:  TournamentPlayerEntity? = null,
    // DCI tiebreakers (floored at 33%)
    val omwPercent:    Double = 0.33, // Opponent Match Win %
    val gwPercent:     Double = 0.33, // Game Win %
    val ogwPercent:    Double = 0.33, // Opponent Game Win %
)
