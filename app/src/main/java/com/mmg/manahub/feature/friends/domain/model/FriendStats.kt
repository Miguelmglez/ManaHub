package com.mmg.manahub.feature.friends.domain.model

/**
 * Domain model representing a snapshot of a friend's collection statistics,
 * as stored in the `user_collection_stats` Supabase table.
 *
 * @property userId           Auth UUID of the user these stats belong to.
 * @property uniqueCards      Number of distinct card printings in the collection.
 * @property totalCards       Total quantity of cards (sum of all quantities).
 * @property totalValueEur    Estimated collection value in EUR.
 * @property totalValueUsd    Estimated collection value in USD.
 * @property favouriteColor   MTG colour identity with the most cards (e.g. "W", "U", "B", "R", "G")
 *                            or null if not available.
 * @property mostValuableColor MTG colour identity with the highest total value, or null if not set.
 * @property updatedAt        Epoch millis when the snapshot was last updated.
 */
data class FriendStats(
    val userId: String,
    val uniqueCards: Int,
    val totalCards: Int,
    val totalValueEur: Double,
    val totalValueUsd: Double,
    val favouriteColor: String?,
    val mostValuableColor: String?,
    val updatedAt: Long,
)
