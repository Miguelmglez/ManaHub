package com.mmg.manahub.core.model

/**
 * The author/owner of a community deck (Archidekt).
 *
 * A missing owner in the upstream payload is mapped to a sentinel owner
 * (`id = 0`, `username = "Unknown"`, blank avatar) by the mappers so the
 * domain layer never has to deal with a null owner.
 */
data class CommunityDeckOwner(
    val id: Int,
    val username: String,
    val avatarUrl: String,
)
