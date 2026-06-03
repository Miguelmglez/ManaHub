package com.mmg.manahub.feature.trades.domain.repository

import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun observeLocal(): Flow<List<WishlistEntry>>
    fun observeByScryfallId(scryfallId: String): Flow<List<WishlistEntry>>
    fun observeUnsyncedCount(): Flow<Int>
    suspend fun addLocal(entry: WishlistEntry): Result<Unit>
    suspend fun removeLocal(id: String): Result<Unit>
    suspend fun updateQuantityLocal(id: String, quantity: Int): Result<Unit>
    suspend fun getRemote(userId: String): Result<List<WishlistEntry>>
    suspend fun addRemote(entry: WishlistEntry): Result<Unit>
    suspend fun removeRemote(id: String): Result<Unit>
    suspend fun migrateLocalToRemote(userId: String): Result<Int>

    /**
     * Inserts or increments a [WishlistEntry] in the local Room store and immediately
     * pushes it to Supabase, marking it as synced on success.
     *
     * Use this method when the user is authenticated so offline-add lag is avoided.
     *
     * @param entry The entry to add.
     * @param userId The authenticated user's UUID used for the remote payload.
     */
    suspend fun addAndSync(entry: WishlistEntry, userId: String): Result<Unit>

    /**
     * Decrements the quantity of every local wishlist entry for [scryfallId] by [quantity].
     * Entries whose resulting quantity falls to zero or below are deleted.
     * Used when the user receives a card via a trade and wants their wishlist updated.
     */
    suspend fun decrementByScryfallId(scryfallId: String, quantity: Int): Result<Unit>

    /**
     * Decrements the best-matching wishlist entry for the received card.
     * Priority: exact attribute match > matchAnyVariant entry > any entry for that scryfallId.
     * Entries whose resulting quantity reaches zero are deleted.
     */
    suspend fun decrementByAttributes(
        scryfallId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
    ): Result<Unit>

    suspend fun syncFromRemote(userId: String): Result<Unit>
}
