package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeWithCard
import com.mmg.manahub.core.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.feature.trades.data.remote.OpenForTradeRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.OpenForTradeEntryDto
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
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
        dao.observeAllWithCard().map { list ->
            // Group rows that share the same card + variant attributes (foil, condition,
            // language), summing their quantities. This mirrors the deduplication
            // logic in WishlistRepositoryImpl.addLocal() but works at read-time so that
            // entries added from different collection copies (distinct localCollectionId)
            // still appear as a single aggregated row in the UI.
            list.map { it.toDomain() }
                .groupBy { entry ->
                    GroupKey(
                        scryfallId = entry.scryfallId,
                        isFoil     = entry.isFoil,
                        condition  = entry.condition,
                        language   = entry.language,
                    )
                }
                .values
                .map { group ->
                    // Use the oldest entry as the canonical row so the UI id is stable.
                    val representative = group.minBy { it.createdAt }
                    representative.copy(quantity = group.sumOf { it.quantity })
                }
        }

    /** Value class used as the grouping key for [observeLocal] deduplication. */
    private data class GroupKey(
        val scryfallId: String,
        val isFoil:     Boolean,
        val condition:  String,
        val language:   String,
    )

    override fun observeByScryfallId(scryfallId: String): Flow<List<OpenForTradeEntry>> =
        dao.observeByScryfallId(scryfallId).map { list -> list.map { it.toDomain() } }

    override fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    override suspend fun addLocal(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
    ): Result<Unit> = runCatching {
        val existing = dao.getByCollectionId(localCollectionId)
        if (existing != null) {
            // Update existing entry with new quantity and attributes
            dao.upsert(
                existing.copy(
                    quantity = quantity,
                    isFoil = isFoil,
                    condition = condition,
                    language = language,
                    synced = false,
                )
            )
        } else {
            dao.upsert(
                LocalOpenForTradeEntity(
                    id = UUID.randomUUID().toString(),
                    localCollectionId = localCollectionId,
                    scryfallId = scryfallId,
                    quantity = quantity,
                    isFoil = isFoil,
                    condition = condition,
                    language = language,
                    synced = false,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun removeByCollectionId(localCollectionId: String): Result<Unit> =
        runCatching { dao.deleteByCollectionId(localCollectionId) }

    override suspend fun removeByCollectionIdAndSync(localCollectionId: String): Result<Unit> =
        runCatching {
            dao.deleteByCollectionId(localCollectionId)
            remote.removeByUserCardId(localCollectionId).getOrThrow()
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

        // local_collection_id maps 1:1 to user_card_collection.id in Supabase.
        // Entries remain in Room after sync (clearSynced removed) so that
        // observeLocal() continues to show them without re-downloading from remote.
        remote.batchAddOpenForTradeEntries(unsynced.map { it.localCollectionId }).getOrThrow()
        dao.markSynced(unsynced.map { it.id })
        unsynced.size
    }

    override suspend fun addAndSync(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
        userId: String,
    ): Result<Unit> = runCatching {
        val existing = dao.getByCollectionId(localCollectionId)
        val entity: LocalOpenForTradeEntity = if (existing != null) {
            existing.copy(
                quantity = quantity,
                isFoil = isFoil,
                condition = condition,
                language = language,
                synced = false,
            ).also { dao.upsert(it) }
        } else {
            LocalOpenForTradeEntity(
                id = UUID.randomUUID().toString(),
                localCollectionId = localCollectionId,
                scryfallId = scryfallId,
                quantity = quantity,
                isFoil = isFoil,
                condition = condition,
                language = language,
                synced = false,
                createdAt = System.currentTimeMillis(),
            ).also { dao.upsert(it) }
        }
        // localCollectionId == user_card_collection.id in Supabase — no lookup needed.
        remote.batchAddOpenForTradeEntries(listOf(entity.localCollectionId)).getOrThrow()
        dao.markSynced(listOf(entity.id))
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        val dtos = remote.getOpenForTrade(userId).getOrThrow()
        val remoteIds = dtos.map { it.id }.toSet()
        val entities = dtos.map { dto ->
            LocalOpenForTradeEntity(
                id = dto.id,
                localCollectionId = dto.userCardId,
                scryfallId = dto.scryfallId ?: "",
                quantity = 1,
                isFoil = dto.isFoil ?: false,
                condition = dto.condition ?: "NM",
                language = dto.language ?: "en",
                synced = true,
                createdAt = runCatching { Instant.parse(dto.createdAt).toEpochMilli() }
                    .getOrDefault(System.currentTimeMillis()),
            )
        }
        dao.upsertAll(entities)
        // Evict synced rows that the server no longer returns (e.g. post-trade orphans).
        // Unsynced (locally-added, not yet pushed) rows are never touched.
        if (remoteIds.isEmpty()) {
            dao.clearSynced()
        } else {
            dao.deleteSyncedNotIn(remoteIds.toList())
        }
    }

    private fun LocalOpenForTradeEntity.toDomain() = OpenForTradeEntry(
        id = id,
        userId = "",
        userCardId = localCollectionId,
        scryfallId = scryfallId,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
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
        quantity = 1, // Remote entries are always 1:1
        isFoil = isFoil ?: false,
        condition = condition ?: "NM",
        language = language ?: "en",
        createdAt = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L),
    )
}
