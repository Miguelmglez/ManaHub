package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.local.dao.LocalWishlistWithCard
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRepositoryImpl @Inject constructor(
    private val dao: LocalWishlistDao,
    private val remote: WishlistRemoteDataSource,
) : WishlistRepository {

    // Serialises concurrent addLocal calls to prevent the TOCTOU race on the
    // read-modify-write quantity increment. Without this, two rapid "Add to
    // wishlist" taps for the same card attributes both see null from
    // getByAttributes() and both insert — producing duplicate rows instead of
    // a single row with quantity = 2.
    private val addMutex = Mutex()

    override fun observeLocal(): Flow<List<WishlistEntry>> =
        dao.observeAllWithCard().map { list -> list.map { it.toDomain() } }

    override fun observeByScryfallId(scryfallId: String): Flow<List<WishlistEntry>> =
        dao.observeByScryfallIdWithCard(scryfallId).map { list -> list.map { it.toDomain() } }

    override fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    override suspend fun addLocal(entry: WishlistEntry): Result<Unit> = addMutex.withLock {
        runCatching {
            val existing = dao.getByAttributes(
                scryfallId = entry.cardId,
                matchAnyVariant = entry.matchAnyVariant,
                isFoil = entry.isFoil,
                condition = entry.condition,
                language = entry.language,
            )
            if (existing != null) {
                dao.update(existing.copy(quantity = existing.quantity + entry.quantity))
            } else {
                dao.insert(entry.toEntity())
            }
        }
    }

    override suspend fun removeLocal(id: String): Result<Unit> = runCatching {
        dao.deleteById(id)
    }

    override suspend fun updateQuantityLocal(id: String, quantity: Int): Result<Unit> = runCatching {
        if (quantity <= 0) {
            dao.deleteById(id)
        } else {
            dao.updateQuantity(id, quantity)
        }
    }

    override suspend fun getRemote(userId: String): Result<List<WishlistEntry>> =
        remote.getWishlist(userId).map { dtos -> dtos.map { it.toDomain() } }

    override suspend fun addRemote(entry: WishlistEntry): Result<Unit> =
        remote.addWishlistEntry(entry.toDto())

    override suspend fun removeRemote(id: String): Result<Unit> =
        remote.removeWishlistEntry(id)

    override suspend fun migrateLocalToRemote(userId: String): Result<Int> = runCatching {
        val unsynced = dao.getUnsynced()
        if (unsynced.isEmpty()) return@runCatching 0

        val dtos = unsynced.map { it.toDto(userId) }
        // getOrThrow() propagates the remote failure before any local state is
        // modified, keeping the two stores consistent. If the batch insert
        // succeeds but markSynced crashes (extremely unlikely), the next migration
        // run will attempt to re-insert already-existing rows — the Supabase
        // wishlists table should have an ON CONFLICT DO NOTHING / UPSERT policy.
        // Entries remain in Room after sync (clearSynced removed) so that
        // observeLocal() continues to show them without re-downloading from remote.
        remote.batchAddWishlistEntries(dtos).getOrThrow()
        dao.markSynced(unsynced.map { it.id })
        unsynced.size
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        val dtos = remote.getWishlist(userId).getOrThrow()
        val remoteIds = dtos.map { it.id }.toSet()
        val entities = dtos.map { dto ->
            LocalWishlistEntity(
                id = dto.id,
                scryfallId = dto.cardId,
                quantity = dto.quantity,
                matchAnyVariant = dto.matchAnyVariant,
                isFoil = dto.isFoil,
                condition = dto.condition,
                language = dto.language,
                synced = true,
                createdAt = runCatching { Instant.parse(dto.createdAt).toEpochMilli() }
                    .getOrDefault(System.currentTimeMillis()),
            )
        }
        dao.upsertAll(entities)
        // Evict synced rows that the server no longer returns.
        // Unsynced (locally-added, not yet pushed) rows are never touched.
        if (remoteIds.isEmpty()) {
            dao.clearSynced()
        } else {
            dao.deleteSyncedNotIn(remoteIds.toList())
        }
    }

    override suspend fun decrementByScryfallId(scryfallId: String, quantity: Int): Result<Unit> =
        runCatching {
            val entries = dao.getByScryfallId(scryfallId)
            entries.forEach { entry ->
                val newQty = entry.quantity - quantity
                if (newQty <= 0) {
                    dao.deleteById(entry.id)
                } else {
                    dao.updateQuantity(entry.id, newQty)
                }
            }
        }

    override suspend fun decrementByAttributes(
        scryfallId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
    ): Result<Unit> = runCatching {
        val entries = dao.getByScryfallId(scryfallId)
        if (entries.isEmpty()) return@runCatching
        val exactMatch = entries.firstOrNull { e ->
            (e.isFoil ?: false) == isFoil &&
                (e.condition == null || e.condition.equals(condition, ignoreCase = true)) &&
                (e.language == null || e.language.equals(language, ignoreCase = true))
        }
        val anyVariant = entries.firstOrNull { it.matchAnyVariant }
        val target = exactMatch ?: anyVariant ?: entries.first()
        val newQty = target.quantity - quantity
        if (newQty <= 0) dao.deleteById(target.id) else dao.updateQuantity(target.id, newQty)
    }

    override suspend fun addAndSync(entry: WishlistEntry, userId: String): Result<Unit> =
        addMutex.withLock {
            runCatching {
                val existing = dao.getByAttributes(
                    scryfallId = entry.cardId,
                    matchAnyVariant = entry.matchAnyVariant,
                    isFoil = entry.isFoil,
                    condition = entry.condition,
                    language = entry.language,
                )
                val entity: LocalWishlistEntity = if (existing != null) {
                    existing.copy(quantity = existing.quantity + entry.quantity)
                        .also { dao.update(it) }
                } else {
                    entry.toEntity().also { dao.insert(it) }
                }
                remote.batchAddWishlistEntries(listOf(entity.toDto(userId))).getOrThrow()
                dao.markSynced(listOf(entity.id))
            }
        }

    private fun LocalWishlistEntity.toDomain() = WishlistEntry(
        id = id,
        userId = "",
        cardId = scryfallId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil ?: false,
        condition = condition,
        language = language,
        createdAt = createdAt,
    )

    private fun LocalWishlistWithCard.toDomain() = entity.toDomain().copy(
        card = card?.toDomainCard()
    )

    private fun WishlistEntry.toEntity() = LocalWishlistEntity(
        id = id.ifBlank { UUID.randomUUID().toString() },
        scryfallId = cardId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil,
        condition = condition,
        language = language,
        synced = false,
        createdAt = createdAt,
    )

    private fun LocalWishlistEntity.toDto(userId: String) = WishlistEntryDto(
        id = id,
        userId = userId,
        cardId = scryfallId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil,
        condition = condition,
        language = language,
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
    )

    private fun WishlistEntryDto.toDomain() = WishlistEntry(
        id = id,
        userId = userId,
        cardId = cardId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil ?: false,
        condition = condition,
        language = language,
        createdAt = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L),
    )

    private fun WishlistEntry.toDto() = WishlistEntryDto(
        id = id,
        userId = userId,
        cardId = cardId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil,
        condition = condition,
        language = language,
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
    )
}
