package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.feature.trades.data.local.dao.LocalWishlistDao
import com.mmg.manahub.feature.trades.data.local.dao.LocalWishlistWithCard
import com.mmg.manahub.feature.trades.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.feature.trades.data.remote.WishlistRemoteDataSource
import com.mmg.manahub.feature.trades.data.remote.dto.WishlistEntryDto
import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
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

    override fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    override suspend fun addLocal(entry: WishlistEntry): Result<Unit> = addMutex.withLock {
        runCatching {
            val existing = dao.getByAttributes(
                scryfallId = entry.cardId,
                matchAnyVariant = entry.matchAnyVariant,
                isFoil = entry.isFoil,
                condition = entry.condition,
                language = entry.language,
                isAltArt = entry.isAltArt
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
        // succeeds but markSynced/clearSynced crash (extremely unlikely), the
        // next migration run will attempt to re-insert already-existing rows.
        // The Supabase wishlists table should have an ON CONFLICT DO NOTHING
        // (or UPSERT) policy to make that safe.
        remote.batchAddWishlistEntries(dtos).getOrThrow()
        dao.markSynced(unsynced.map { it.id })
        dao.clearSynced()
        unsynced.size
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
        isAltArt = isAltArt ?: false,
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
        isAltArt = isAltArt,
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
        isAltArt = isAltArt,
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
        isAltArt = isAltArt ?: false,
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
        isAltArt = isAltArt,
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
    )
}
