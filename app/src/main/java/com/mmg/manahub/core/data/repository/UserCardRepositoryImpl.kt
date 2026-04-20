package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.UserCardDao
import com.mmg.manahub.core.data.local.entity.SyncStatus
import com.mmg.manahub.core.data.local.mapper.toDomain
import com.mmg.manahub.core.data.local.mapper.toEntity
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.toEntity
import com.mmg.manahub.core.data.remote.collection.toUpsertParams
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.repository.UserCardRepository
// Auth is injected here (not in a ViewModel) so that deleteCard() can queue a soft-delete
// using the current userId without requiring callers to pass it explicitly. The Auth
// instance is read-only and never writes session state from this layer.
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserCardRepositoryImpl @Inject constructor(
    private val userCardDao: UserCardDao,
    private val remoteDataSource: CollectionRemoteDataSource,
    private val prefsDataStore: UserPreferencesDataStore,
    private val supabaseAuth: Auth,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserCardRepository {

    private val syncMutex = Mutex()

    override fun observeCollection(): Flow<List<UserCardWithCard>> =
        userCardDao.observeCollection().map { it.map { r -> r.toDomain() } }

    override fun observeByColor(color: String): Flow<List<UserCardWithCard>> =
        userCardDao.observeByColor(color).map { it.map { r -> r.toDomain() } }

    override fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>> =
        userCardDao.observeByRarity(rarity).map { it.map { r -> r.toDomain() } }

    override fun searchInCollection(query: String): Flow<List<UserCardWithCard>> =
        userCardDao.searchInCollection(query).map { it.map { r -> r.toDomain() } }

    override fun observeByScryfallId(scryfallId: String): Flow<List<UserCard>> =
        userCardDao.observeByScryfallId(scryfallId).map { it.map { entity -> entity.toDomain() } }

    override fun observePendingCount(): Flow<Int> = userCardDao.observePendingCount()

    override suspend fun addOrIncrement(userCard: UserCard) = withContext(ioDispatcher) {
        // insertOrIncrement already marks the row as PENDING_UPLOAD atomically.
        userCardDao.insertOrIncrement(userCard.toEntity())
    }

    override suspend fun updateCard(userCard: UserCard) = withContext(ioDispatcher) {
        // toEntity() now preserves syncStatus + remoteId from the domain model,
        // so the remote_id column is not erased on every edit.
        userCardDao.update(userCard.toEntity())
        userCardDao.markPendingUpload(userCard.id)
    }

    override suspend fun deleteCard(id: Long) = withContext(ioDispatcher) {
        // Capture userId BEFORE any suspend calls so a session expiry mid-operation
        // doesn't prevent us from queuing the soft-delete.
        val userId = supabaseAuth.currentUserOrNull()?.id
        val entity = userCardDao.getById(id)
        entity?.remoteId?.let { remoteId ->
            if (userId != null) {
                prefsDataStore.addPendingDeleteRemoteId(userId, remoteId)
            }
        }
        userCardDao.deleteById(id)
    }

    override suspend fun incrementQuantity(id: Long) = withContext(ioDispatcher) {
        userCardDao.incrementQuantity(id)
        userCardDao.markPendingUpload(id)
    }

    override suspend fun updateQuantity(id: Long, quantity: Int) = withContext(ioDispatcher) {
        require(quantity >= 0) { "Quantity cannot be negative" }
        if (quantity == 0) {
            deleteCard(id)
        } else {
            userCardDao.updateQuantity(id, quantity)
            userCardDao.markPendingUpload(id)
        }
    }

    override suspend fun getScryfallIds(): List<String> = withContext(ioDispatcher) {
        userCardDao.getAllScryfallIds()
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    override suspend fun pushPendingChanges(userId: String): Result<Unit> =
        withContext(ioDispatcher) {
            syncMutex.withLock {
                runCatching {
                    // 1. Upload PENDING_UPLOAD rows.
                    val pending = userCardDao.getPendingUpload()
                    for (entity in pending) {
                        val result = remoteDataSource.upsertCard(entity.toUpsertParams(userId))
                        val remoteId = result.getOrNull()
                        if (!remoteId.isNullOrBlank()) {
                            userCardDao.markAsSynced(entity.id, remoteId)
                        }
                    }

                    // 2. Process soft-deletes; preserve any that fail so they retry next push.
                    val pendingDeletes = prefsDataStore.getPendingDeleteRemoteIds(userId)
                    val failedDeletes = mutableSetOf<String>()
                    for (remoteId in pendingDeletes) {
                        remoteDataSource.softDeleteCard(remoteId)
                            .onFailure { failedDeletes.add(remoteId) }
                    }
                    if (pendingDeletes.isNotEmpty()) {
                        prefsDataStore.clearPendingDeleteRemoteIds(userId)
                        for (remoteId in failedDeletes) {
                            prefsDataStore.addPendingDeleteRemoteId(userId, remoteId)
                        }
                    }

                    // 3. Record the sync timestamp and today's date.
                    val nowIso = Instant.now().toString()
                    prefsDataStore.saveLastSyncTimestamp(userId, nowIso)
                    prefsDataStore.saveLastSyncDate(
                        userId,
                        LocalDate.now(ZoneOffset.UTC).toString(),
                    )
                }
            }
        }

    override suspend fun pullChanges(userId: String): Result<Unit> =
        withContext(ioDispatcher) {
            syncMutex.withLock {
                runCatching {
                    // 1. Check if the server has anything newer than our last sync.
                    val serverLastModified = remoteDataSource.getLastModified(userId)
                    val localLastSync = prefsDataStore.getLastSyncTimestamp(userId)
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() }

                    val hasRemoteChanges = serverLastModified != null &&
                        (localLastSync == null || serverLastModified.isAfter(localLastSync))

                    if (!hasRemoteChanges) return@runCatching

                    // 2. Fetch only the rows that changed since the last sync.
                    val since = localLastSync ?: Instant.EPOCH
                    val changes = remoteDataSource.getChangesSince(userId, since).getOrThrow()

                    // 3. Merge: soft-deleted rows are removed locally; active rows are upserted.
                    //    Each row is processed independently so a single FK violation doesn't
                    //    abort the entire pull (FK RESTRICT from cards table may fire if a
                    //    referenced card hasn't been cached yet).
                    for (dto in changes) {
                        runCatching {
                            if (dto.isDeleted) {
                                dto.id?.let { remoteId ->
                                    val local = userCardDao.getByRemoteId(remoteId)
                                    local?.let { userCardDao.deleteById(it.id) }
                                }
                            } else {
                                // Skip rows that have a local unsent edit — they will be pushed
                                // on the next push and their remote state will update then.
                                val existing = dto.id?.let { userCardDao.getByRemoteId(it) }
                                if (existing == null || existing.syncStatus != SyncStatus.PENDING_UPLOAD) {
                                    userCardDao.insertOrReplace(dto.toEntity())
                                }
                            }
                        }
                    }

                    // 4. Store the server's own last-modified timestamp so the next delta pull
                    //    starts from the correct watermark (not from our local clock, which may
                    //    differ and could miss rows written between our clock and the server's).
                    prefsDataStore.saveLastSyncTimestamp(userId, serverLastModified.toString())
                    prefsDataStore.saveLastSyncDate(
                        userId,
                        LocalDate.now(ZoneOffset.UTC).toString(),
                    )
                }
            }
        }
}
