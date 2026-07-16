package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeWithCard
import com.mmg.manahub.core.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.core.data.remote.trades.OpenForTradeRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.OpenForTradeEntryDto
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`/`@Singleton`); built as a
 * native Koin `single` in [com.mmg.manahub.app.di.coreBridgeKoinModule] (shared across the Trades,
 * CardDetail and Collection Koin islands).
 *
 * Uses `java.util.UUID` / `System.currentTimeMillis()` (JVM-only, not available on `wasmJs`).
 * Deferred (trades audit §4.3, 2026-07-10): swapping to `kotlin.uuid.Uuid` /
 * `kotlinx.datetime.Clock` here is safe on its own, but this class also builds
 * [LocalOpenForTradeEntity] directly (a Room entity whose `id`/`createdAt` columns are
 * `String`/`Long`) — do this together with the eventual `androidMain`/`wasmJsMain` data-source
 * split for this repository, not in isolation, to avoid a partial type mismatch between the two
 * platform mappers.
 */
class OpenForTradeRepositoryImpl(
    private val dao: LocalOpenForTradeDao,
    private val remote: OpenForTradeRemoteDataSource,
) : OpenForTradeRepository {

    // Serialises concurrent addLocal/addAndSync calls to prevent the TOCTOU race on the
    // read-modify-write by collection id. Without this, two rapid taps for the same
    // localCollectionId both see null from getByCollectionId() and both insert, producing
    // duplicate rows instead of a single upserted one. Mirrors WishlistRepositoryImpl.addMutex
    // (trades audit §2.1, 2026-07-10).
    private val addMutex = Mutex()

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

    // A9 (edge-case audit, 2026-07-15): unlike observeLocal() above, this does NOT de-duplicate
    // rows that share the same (scryfallId, isFoil, condition, language) tuple across different
    // localCollectionIds — it returns the raw per-row DAO output, one row per underlying
    // local_open_for_trade entry. Safe today because CardDetailViewModel.observeTradeEntries (the
    // only call site) only reads `.userCardId -> .quantity` into a Map keyed by the OWNING
    // collection row's id, so duplicate physical tuples across different collection rows are
    // expected and correct here. A future caller that renders these entries as a flat list should
    // first apply observeLocal()'s GroupKey dedup/sum convention (see [GroupKey] above).
    override fun observeVersionsByOracle(oracleId: String, name: String): Flow<List<OpenForTradeEntry>> =
        dao.observeVersionsByOracle(oracleId, name).map { list -> list.map { it.toDomain() } }

    override fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    override suspend fun addLocal(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
    ): Result<Unit> = addMutex.withLock {
        runCatching {
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
    }

    override suspend fun removeByCollectionId(localCollectionId: String): Result<Unit> =
        runCatching { dao.deleteByCollectionId(localCollectionId) }

    override suspend fun removeByCollectionIdAndSync(localCollectionId: String): Result<Unit> =
        runCatching {
            // Remote-first: only delete the local row after the server row is confirmed gone.
            // The old local-then-remote order could leave the server row alive after a failed
            // remote call, and the next syncFromRemote() would re-insert ("resurrect") the entry
            // the user just removed (trades audit §2.2, 2026-07-10).
            remote.removeByUserCardId(localCollectionId).getOrThrow()
            dao.deleteByCollectionId(localCollectionId)
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
    ): Result<Unit> = addMutex.withLock {
        runCatching {
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
                // Project-wide fallback convention (trades audit §2.13, 2026-07-10): an
                // unparseable createdAt falls back to epoch (0L), not "now" — deterministic,
                // and it sorts a malformed timestamp to the bottom of a recency-DESC list
                // instead of falsely surfacing it as newest. Mirrors WishlistRepositoryImpl
                // and TradesRepositoryImpl.parseIso().
                createdAt = runCatching { Instant.parse(dto.createdAt).toEpochMilliseconds() }
                    .getOrDefault(0L),
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
        createdAt = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrDefault(0L),
    )
}
