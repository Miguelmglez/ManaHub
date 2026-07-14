package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.local.dao.LocalWishlistWithCard
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`/`@Singleton`); built as a
 * native Koin `single` in [com.mmg.manahub.app.di.coreBridgeKoinModule] (shared across the Trades,
 * Home, CardDetail, Collection and Decks Koin islands).
 *
 * Uses `java.util.UUID` / `System.currentTimeMillis()` (JVM-only, not available on `wasmJs`).
 * Deferred (trades audit §4.3, 2026-07-10) for the same reason as
 * [com.mmg.manahub.feature.trades.data.repository.OpenForTradeRepositoryImpl]: swap alongside the
 * eventual `androidMain`/`wasmJsMain` data-source split for this repository, not in isolation.
 */
class WishlistRepositoryImpl(
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
        // A synced row also exists server-side — deleting it locally only, with no remote
        // call, means the next syncFromRemote() re-downloads and "resurrects" the entry the
        // user just removed. Remote-first (mirrors the OpenForTrade §2.2 fix): only delete
        // locally once the server row is confirmed gone (trades audit §2.3, 2026-07-10).
        val existing = dao.getById(id)
        if (existing?.synced == true) {
            remote.removeWishlistEntry(id).getOrThrow()
        }
        dao.deleteById(id)
    }

    override suspend fun updateQuantityLocal(id: String, quantity: Int): Result<Unit> = runCatching {
        val existing = dao.getById(id)
        if (quantity <= 0) {
            if (existing?.synced == true) {
                remote.removeWishlistEntry(id).getOrThrow()
            }
            dao.deleteById(id)
        } else {
            // Same remote-first resurrection guard as removeLocal() above, for the
            // decrement-not-delete path (trades audit §2.3, 2026-07-10).
            if (existing?.synced == true) {
                remote.updateWishlistQuantity(id, quantity).getOrThrow()
            }
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
                // Project-wide fallback convention (trades audit §2.13, 2026-07-10): an
                // unparseable createdAt falls back to epoch (0L), not "now" — deterministic,
                // and it sorts a malformed timestamp to the bottom of a recency-DESC list
                // instead of falsely surfacing it as newest. Mirrors OpenForTradeRepositoryImpl
                // and TradesRepositoryImpl.parseIso().
                createdAt = runCatching { Instant.parse(dto.createdAt).toEpochMilliseconds() }
                    .getOrDefault(0L),
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
                // Remote-first resurrection guard for synced rows, same as removeLocal() /
                // updateQuantityLocal() above (trades audit §2.3, 2026-07-10).
                if (newQty <= 0) {
                    if (entry.synced) remote.removeWishlistEntry(entry.id).getOrThrow()
                    dao.deleteById(entry.id)
                } else {
                    if (entry.synced) remote.updateWishlistQuantity(entry.id, newQty).getOrThrow()
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
        val target = exactMatch ?: anyVariant
        if (target == null) {
            // No confident match for this scryfallId's variant (foil/condition/language). The
            // old fallback (entries.first()) could decrement a completely different variant
            // than the one actually traded — a silent data-corruption risk. No-op instead and
            // report so genuine drift (a wishlist row that should match but never does) is
            // caught (trades audit §2.11, 2026-07-10).
            recordSafeNonFatal(
                "wishlist_decrement_no_match",
                IllegalStateException("scryfallId has ${entries.size} candidate(s), none matched"),
            )
            return@runCatching
        }
        val newQty = target.quantity - quantity
        if (newQty <= 0) {
            if (target.synced) remote.removeWishlistEntry(target.id).getOrThrow()
            dao.deleteById(target.id)
        } else {
            if (target.synced) remote.updateWishlistQuantity(target.id, newQty).getOrThrow()
            dao.updateQuantity(target.id, newQty)
        }
    }

    override suspend fun addAndSync(entry: WishlistEntry, userId: String): Result<Unit> {
        // Only the local read-modify-write is serialised by the mutex — the remote push runs
        // outside the lock so one slow network call doesn't block every other concurrent
        // wishlist add (trades audit §2.12, 2026-07-10).
        val entity = addMutex.withLock {
            runCatching {
                val existing = dao.getByAttributes(
                    scryfallId = entry.cardId,
                    matchAnyVariant = entry.matchAnyVariant,
                    isFoil = entry.isFoil,
                    condition = entry.condition,
                    language = entry.language,
                )
                if (existing != null) {
                    existing.copy(quantity = existing.quantity + entry.quantity)
                        .also { dao.update(it) }
                } else {
                    entry.toEntity().also { dao.insert(it) }
                }
            }
        }.getOrElse { return Result.failure(it) }

        // The local write already succeeded — the card IS on the wishlist at this point. A
        // remote failure here is sync lag, not a user-facing failure: report it as a non-fatal
        // instead of returning Result.failure (which previously made the caller show an error
        // even though the add worked locally). The row stays synced=false and is retried by the
        // next migrateLocalToRemote()/addAndSync call (trades audit §2.12, 2026-07-10).
        remote.batchAddWishlistEntries(listOf(entity.toDto(userId)))
            .onSuccess { dao.markSynced(listOf(entity.id)) }
            .onFailure { e -> recordSafeNonFatal("wishlist_add_and_sync_remote_failed", e) }

        return Result.success(Unit)
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
        createdAt = Instant.fromEpochMilliseconds(createdAt).toString(),
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
        createdAt = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrDefault(0L),
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
        createdAt = Instant.fromEpochMilliseconds(createdAt).toString(),
    )
}
