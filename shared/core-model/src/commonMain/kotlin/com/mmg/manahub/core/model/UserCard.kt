package com.mmg.manahub.core.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A card the user owns in their collection.
 *
 * Pure (KMP-safe) domain model: the [createdAt] / [updatedAt] defaults use [Clock.System]
 * (multiplatform epoch millis) instead of the JVM-only `System.currentTimeMillis()`, so this
 * type can live in `:shared:core-model` `commonMain` and be shared by Android + Web.
 */
@OptIn(ExperimentalTime::class)
data class UserCard(
    val id:               String,                              // UUID, client-generated
    val scryfallId:       String,
    val quantity:         Int     = 1,
    val isFoil:           Boolean = false,
    val condition:        String  = "NM",
    val language:         String  = "en",
    val isForTrade:       Boolean = false,
    val updatedAt:        Long    = Clock.System.now().toEpochMilliseconds(),
    val createdAt:        Long    = Clock.System.now().toEpochMilliseconds(),
)

/** Pairs a [UserCard] collection entry with its resolved [Card] details. */
data class UserCardWithCard(val userCard: UserCard, val card: Card)
