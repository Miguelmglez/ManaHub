package com.mmg.manahub.feature.trades.domain.repository

import com.mmg.manahub.feature.trades.domain.model.OpenForTradeEntry
import kotlinx.coroutines.flow.Flow

interface OpenForTradeRepository {
    fun observeLocal(): Flow<List<OpenForTradeEntry>>
    fun observeByScryfallId(scryfallId: String): Flow<List<OpenForTradeEntry>>
    fun observeUnsyncedCount(): Flow<Int>
    suspend fun addLocal(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
        isAltArt: Boolean,
    ): Result<Unit>
    suspend fun removeByCollectionId(localCollectionId: String): Result<Unit>
    suspend fun removeLocal(id: String): Result<Unit>
    suspend fun getRemote(userId: String): Result<List<OpenForTradeEntry>>
    suspend fun addRemote(userCardId: String): Result<Unit>
    suspend fun removeRemote(id: String): Result<Unit>
    suspend fun migrateLocalToRemote(userId: String): Result<Int>
}
