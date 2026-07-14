package com.mmg.manahub.core.model

/**
 * Pure domain representation of a tournament participant.
 *
 * Mirrors [com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity] without Room annotations.
 *
 * Moved from `:app` entity layer to `:shared:core-model` as part of KMP Phase 4.
 */
data class TournamentPlayer(
    val id: Long = 0,
    val tournamentId: Long,
    val playerName: String,
    /** Hex colour string, e.g. `"#E63946"`. Used to tint player slots in the UI. */
    val playerColor: String,
    val deckId: Long? = null,
    val seed: Int = 0,
)
