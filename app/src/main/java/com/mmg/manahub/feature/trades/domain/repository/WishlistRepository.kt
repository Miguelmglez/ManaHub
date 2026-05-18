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
}
