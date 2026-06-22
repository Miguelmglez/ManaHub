package com.mmg.manahub.core.domain.model

import com.mmg.manahub.core.model.Card

/**
 * A card the user owns in their collection.
 *
 * Stays in `:app` (not `:shared:core-model`) because [updatedAt]/[createdAt]
 * default to [System.currentTimeMillis], a JVM-only API. The deferred
 * `UserCardRepository` migration will revisit this once a multiplatform clock
 * default is introduced.
 */
data class UserCard(
    val id:               String,                              // UUID, client-generated
    val scryfallId:       String,
    val quantity:         Int     = 1,
    val isFoil:           Boolean = false,
    val condition:        String  = "NM",
    val language:         String  = "en",
    val isForTrade:       Boolean = false,
    val updatedAt:        Long    = System.currentTimeMillis(),
    val createdAt:        Long    = System.currentTimeMillis(),
)

/** Pairs a [UserCard] collection entry with its resolved [Card] details. */
data class UserCardWithCard(val userCard: UserCard, val card: Card)
