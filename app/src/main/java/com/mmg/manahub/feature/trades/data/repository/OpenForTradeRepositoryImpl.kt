package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.feature.trades.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.feature.trades.data.local.dao.LocalOpenForTradeWithCard
import com.mmg.manahub.feature.trades.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.feature.trades.data.remote.OpenForTradeRemoteDataSource
import com.mmg.manahub.feature.trades.data.remote.dto.OpenForTradeEntryDto
import com.mmg.manahub.feature.trades.domain.model.OpenForTradeEntry
import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenForTradeRepositoryImpl @Inject constructor(
    private val dao: LocalOpenForTradeDao,
    private val remote: OpenForTradeRemoteDataSource,
) : OpenForTradeRepository {

    override fun observeLocal(): Flow<List<OpenForTradeEntry>> =
        dao.observeAllWithCard().map { list -> list.map { it.toDomain() } }

    override fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    override suspend fun addLocal(scryfallId: String, localCollectionId: String): Result<Unit> =
        runCatching {
            dao.insert(
                LocalOpenForTradeEntity(
                    id = UUID.randomUUID().toString(),
                    localCollectionId = localCollectionId,
                    scryfallId = scryfallId,
                    synced = false,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }

    override suspend fun removeLocal(id: String): Result<Unit> = runCatching {
        dao.deleteById(id)
    }

    override suspend fun getRemote(userId: String): Result<List<OpenForTradeEntry>> =
        remote.getOpenForTrade(userId).map { dtos -> dtos.map { it.toDomain() } }

    override suspend fun addRemote(userCardId: String): Result<Unit> =
        remote.addOpenForTradeEntry(userCardId)

    override suspend fun removeRemote(id: String): Result<Unit> =
        remote.removeOpenForTradeEntry(id)

    override suspend fun migrateLocalToRemote(userId: String): Result<Int> = runCatching {
        val unsynced = dao.getUnsynced()
        if (unsynced.isEmpty()) return@runCatching 0

        // local_collection_id maps 1:1 to user_card_collection.id in Supabase
        remote.batchAddOpenForTradeEntries(unsynced.map { it.localCollectionId }).getOrThrow()
        dao.markSynced(unsynced.map { it.id })
        dao.clearSynced()
        unsynced.size
    }

    private fun LocalOpenForTradeEntity.toDomain() = OpenForTradeEntry(
        id = id,
        userId = "",
        userCardId = localCollectionId,
        scryfallId = scryfallId,
        isFoil = false,
        condition = "NM",
        language = "en",
        isAltArt = false,
        createdAt = createdAt,
    )

    private fun LocalOpenForTradeWithCard.toDomain() = entity.toDomain().copy(
        card = card?.toDomainCard()
    )

    private fun OpenForTradeEntryDto.toDomain() = OpenForTradeEntry(
        id = id,
        userId = userId,
        userCardId = userCardId,
        scryfallId = scryfallId ?: "",
        isFoil = isFoil ?: false,
        condition = condition ?: "NM",
        language = language ?: "en",
        isAltArt = isAltArt ?: false,
        createdAt = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L),
    )
}
