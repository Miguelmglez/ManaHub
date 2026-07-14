package com.mmg.manahub.core.model

/**
 * DCI-style standing for a single [TournamentPlayer] in a finished or in-progress tournament.
 *
 * Replaces the Room-coupled projection at
 * `com.mmg.manahub.core.data.local.entity.projection.TournamentStanding` so the type can live
 * in `:shared:core-model` (`commonMain`) with zero platform dependencies.
 *
 * Sorting criteria (descending): points → OMW% → GW% → OGW%.
 * Life total is retained for display only and is NOT a sort criterion.
 *
 * Moved from `:app` projection layer to `:shared:core-model` as part of KMP Phase 4.
 */
data class TournamentStanding(
    val player: TournamentPlayer,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    /** Computed score: wins × 3 + draws × 1. */
    val points: Int,
    /** Accumulated life total across all finished matches (display only). */
    val lifeTotal: Int,
    /** Rank position; 1 = first place. */
    val position: Int,
    val matchesPlayed: Int,
    /** Next scheduled opponent, or null if none is determined yet. */
    val nextOpponent: TournamentPlayer? = null,
    /** Opponent Match Win % (DCI tiebreaker, floored at 33 %). */
    val omwPercent: Double = 0.33,
    /** Game Win % (DCI tiebreaker, floored at 33 %). */
    val gwPercent: Double = 0.33,
    /** Opponent Game Win % (DCI tiebreaker, floored at 33 %). */
    val ogwPercent: Double = 0.33,
)
