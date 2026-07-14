package com.mmg.manahub.core.model

/**
 * Pure domain representation of a single tournament match (pairing or bye).
 *
 * Mirrors [com.mmg.manahub.core.data.local.entity.TournamentMatchEntity] without Room annotations.
 * JSON-encoded fields ([playerIds], [finalLifeTotals]) are preserved as raw strings for
 * compatibility with [com.mmg.manahub.feature.tournament.domain.engine.TournamentIdCodec].
 *
 * Moved from `:app` entity layer to `:shared:core-model` as part of KMP Phase 4.
 */
data class TournamentMatch(
    val id: Long = 0,
    val tournamentId: Long,
    val round: Int,
    /** JSON-encoded participant ids: `"[id1, id2]"`. A single-element list denotes a bye. */
    val playerIds: String,
    val winnerId: Long? = null,
    val status: String = "PENDING",     // "PENDING" | "ACTIVE" | "FINISHED"
    val gameSessionId: Long? = null,
    val scheduledOrder: Int = 0,
    /** JSON-encoded life totals: `"{id: life, ...}"`. Empty string when not recorded. */
    val finalLifeTotals: String = "",
)
