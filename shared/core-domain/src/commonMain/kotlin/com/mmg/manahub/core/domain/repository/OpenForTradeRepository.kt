package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.OpenForTradeEntry
import kotlinx.coroutines.flow.Flow

interface OpenForTradeRepository {
    fun observeLocal(): Flow<List<OpenForTradeEntry>>
    fun observeByScryfallId(scryfallId: String): Flow<List<OpenForTradeEntry>>
    fun observeUnsyncedCount(): Flow<Int>
    suspend fun addLocal(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int = 1,
        isFoil: Boolean = false,
        condition: String = "NM",
        language: String = "en",
    ): Result<Unit>
    suspend fun removeByCollectionId(localCollectionId: String): Result<Unit>
    suspend fun removeByCollectionIdAndSync(localCollectionId: String): Result<Unit>
    suspend fun removeLocal(id: String): Result<Unit>
    suspend fun getRemote(userId: String): Result<List<OpenForTradeEntry>>
    suspend fun addRemote(userCardId: String): Result<Unit>
    suspend fun removeRemote(id: String): Result<Unit>
    suspend fun migrateLocalToRemote(userId: String): Result<Int>

    /**
     * Inserts or updates an Open-For-Trade entry in local Room and immediately pushes it
     * to Supabase using [localCollectionId] as the remote `user_card_id`, then marks it
     * as synced without deleting the local row.
     *
     * Use this method when the user is authenticated to keep the remote list current.
     *
     * @param userId The authenticated user's UUID (unused for the remote call but
     *   kept for symmetry with [WishlistRepository.addAndSync]).
     */
    suspend fun addAndSync(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int = 1,
        isFoil: Boolean = false,
        condition: String = "NM",
        language: String = "en",
        userId: String,
    ): Result<Unit>
    suspend fun syncFromRemote(userId: String): Result<Unit>
}
