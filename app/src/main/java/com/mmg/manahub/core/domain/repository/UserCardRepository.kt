package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard
import kotlinx.coroutines.flow.Flow

interface UserCardRepository {
    fun observeCollection(): Flow<List<UserCardWithCard>>
    fun observeByColor(color: String): Flow<List<UserCardWithCard>>
    fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>>
    fun searchInCollection(query: String): Flow<List<UserCardWithCard>>
    fun observeByScryfallId(scryfallId: String): Flow<List<UserCard>>
    suspend fun addOrIncrement(userCard: UserCard)
    suspend fun updateCard(userCard: UserCard)
    suspend fun deleteCard(id: Long)
    suspend fun incrementQuantity(id: Long)
    suspend fun updateQuantity(id: Long, quantity: Int)
    suspend fun getScryfallIds(): List<String>

    // ── Sync ──────────────────────────────────────────────────────────────────

    /** Number of local rows not yet pushed to Supabase. */
    fun observePendingCount(): Flow<Int>

    /**
     * Uploads all PENDING_UPLOAD rows to Supabase and processes any pending soft-deletes.
     * No-op when the user is not authenticated.
     */
    suspend fun pushPendingChanges(userId: String): Result<Unit>

    /**
     * Downloads changes from Supabase that occurred after the last successful sync
     * and merges them into the local Room database.
     * No-op when the user is not authenticated or when local data is already up to date.
     */
    suspend fun pullChanges(userId: String): Result<Unit>
}