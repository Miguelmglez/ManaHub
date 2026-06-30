package com.mmg.manahub.core.model

/**
 * Pure domain representation of a tournament session.
 *
 * Mirrors [com.mmg.manahub.core.data.local.entity.TournamentEntity] but carries zero Room /
 * AndroidX annotations so it can live in `:shared:core-model` (`commonMain`) and be consumed by
 * the web target as well as Android.
 *
 * Moved from `:app` entity layer to `:shared:core-model` as part of KMP Phase 4.
 */
data class Tournament(
    val id: Long = 0,
    val name: String,
    val format: String,             // "COMMANDER" | "STANDARD" | "DRAFT"
    val structure: String,          // "ROUND_ROBIN" | "SWISS" | "SINGLE_ELIM"
    val status: String,             // "SETUP" | "ACTIVE" | "PAUSED" | "FINISHED"
    val matchesPerPairing: Int = 1,
    val isRandomPairings: Boolean = true,
    val createdAt: Long = 0L,
    val finishedAt: Long? = null,
)
