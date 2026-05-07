package com.mmg.manahub.feature.trades.domain.repository

import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun observeLocal(): Flow<List<WishlistEntry>>
    fun observeUnsyncedCount(): Flow<Int>
    suspend fun addLocal(entry: WishlistEntry): Result<Unit>
    suspend fun removeLocal(id: String): Result<Unit>
    suspend fun getRemote(userId: String): Result<List<WishlistEntry>>
    suspend fun addRemote(entry: WishlistEntry): Result<Unit>
    suspend fun removeRemote(id: String): Result<Unit>
    suspend fun migrateLocalToRemote(userId: String): Result<Int>
}
