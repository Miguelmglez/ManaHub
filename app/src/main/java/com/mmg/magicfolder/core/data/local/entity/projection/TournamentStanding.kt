package com.mmg.magicfolder.core.data.local.entity.projection

import com.mmg.magicfolder.core.data.local.entity.TournamentPlayerEntity

data class TournamentStanding(
    val player:        TournamentPlayerEntity,
    val wins:          Int,
    val losses:        Int,
    val draws:         Int,
    val points:        Int,          // wins*3 + draws*1
    val lifeTotal:     Int,          // accumulated life (tiebreaker)
    val position:      Int,          // rank 1 = first
    val matchesPlayed: Int,
    val nextOpponent:  TournamentPlayerEntity? = null,
)
