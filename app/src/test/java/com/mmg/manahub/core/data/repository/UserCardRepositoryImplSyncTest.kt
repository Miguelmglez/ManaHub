package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.UserCardDao
import com.mmg.manahub.core.data.local.entity.SyncStatus
import com.mmg.manahub.core.data.local.entity.UserCardEntity
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto
import com.mmg.manahub.core.domain.model.UserCard
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.user.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for the bidirectional sync logic in [UserCardRepositoryImpl].
 *
 * All dependencies are mocked with MockK. No real database or network is used.
 *
 * Covers:
 *  - pushPendingChanges: happy path, partial failure, no-op when empty, soft-deletes
 *  - pullChanges: no remote changes, delta pull, first-ever pull (EPOCH), remote failures
 *  - Mutations that mark rows PENDING_UPLOAD (updateCard, incrementQuantity, updateQuantity)
 *  - deleteCard soft-delete logic (with/without remoteId, with/without logged-in user)
 */
class UserCardRepositoryImplSyncTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val userCardDao         = mockk<UserCardDao>(relaxed = true)
    private val remoteDataSource    = mockk<CollectionRemoteDataSource>(relaxed = true)
    private val prefsDataStore      = mockk<UserPreferencesDataStore>(relaxed = true)
    private val supabaseAuth        = mockk<Auth>(relaxed = true)

    private lateinit var repository: UserCardRepositoryImpl

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID    = "user-uuid-001"
    private val REMOTE_ID  = "remote-uuid-abc"
    private val PAST_TS    = Instant.parse("2024-01-01T00:00:00Z").toString()
    private val FUTURE_TS  = Instant.now().plusSeconds(3600)   // server has newer data

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = UserCardRepositoryImpl(
            userCardDao      = userCardDao,
            remoteDataSource = remoteDataSource,
            prefsDataStore   = prefsDataStore,
            supabaseAuth     = supabaseAuth,
            ioDispatcher     = UnconfinedTestDispatcher(),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun pendingEntity(
        id:        Long    = 1L,
        remoteId:  String? = null,
        syncStatus: Int    = SyncStatus.PENDING_UPLOAD,
    ) = UserCardEntity(
        id         = id,
        scryfallId = "card-001",
        syncStatus = syncStatus,
        remoteId   = remoteId,
    )

    private fun remoteDto(
        id:        String? = REMOTE_ID,
        isDeleted: Boolean = false,
    ) = UserCardCollectionDto(
        id         = id,
        userId     = USER_ID,
        scryfallId = "card-001",
        isDeleted  = isDeleted,
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — pushPendingChanges: happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given N pending rows when pushPendingChanges then upsertCard is called N times`() = runTest {
        // Arrange
        val entities = listOf(pendingEntity(id = 1L), pendingEntity(id = 2L), pendingEntity(id = 3L))
        coEvery { userCardDao.getPendingUpload() } returns entities
        coEvery { remoteDataSource.upsertCard(any()) } returns Result.success(REMOTE_ID)
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(any()) } returns emptySet()

        // Act
        val result = repository.pushPendingChanges(USER_ID)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 3) { remoteDataSource.upsertCard(any()) }
    }

    @Test
    fun `given N pending rows when pushPendingChanges succeeds then markAsSynced is called N times with remoteId`() = runTest {
        // Arrange
        val entities = listOf(pendingEntity(id = 1L), pendingEntity(id = 2L))
        coEvery { userCardDao.getPendingUpload() } returns entities
        coEvery { remoteDataSource.upsertCard(any()) } returns Result.success(REMOTE_ID)
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(any()) } returns emptySet()

        // Act
        repository.pushPendingChanges(USER_ID)

        // Assert: each row is marked synced with the returned remote id
        coVerify(exactly = 1) { userCardDao.markAsSynced(1L, REMOTE_ID) }
        coVerify(exactly = 1) { userCardDao.markAsSynced(2L, REMOTE_ID) }
    }

    @Test
    fun `given successful push when pushPendingChanges then lastSyncTimestamp and lastSyncDate are saved`() = runTest {
        // Arrange
        coEvery { userCardDao.getPendingUpload() } returns emptyList()
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(any()) } returns emptySet()

        // Act
        repository.pushPendingChanges(USER_ID)

        // Assert
        coVerify(exactly = 1) { prefsDataStore.saveLastSyncTimestamp(eq(USER_ID), any()) }
        coVerify(exactly = 1) { prefsDataStore.saveLastSyncDate(eq(USER_ID), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — pushPendingChanges: partial failure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given upsertCard fails for one row when pushPendingChanges then that row is NOT marked synced`() = runTest {
        // Arrange — first call fails, second succeeds
        val entities = listOf(pendingEntity(id = 1L), pendingEntity(id = 2L))
        coEvery { userCardDao.getPendingUpload() } returns entities
        coEvery { remoteDataSource.upsertCard(any()) }
            .returnsMany(
                Result.failure(RuntimeException("network error")),
                Result.success(REMOTE_ID),
            )
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(any()) } returns emptySet()

        // Act
        val result = repository.pushPendingChanges(USER_ID)

        // Assert: overall result is still success (per-row errors are swallowed)
        assertTrue(result.isSuccess)
        // Row 1 failed → must NOT be marked synced
        coVerify(exactly = 0) { userCardDao.markAsSynced(1L, any()) }
        // Row 2 succeeded → must be marked synced
        coVerify(exactly = 1) { userCardDao.markAsSynced(2L, REMOTE_ID) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — pushPendingChanges: no pending rows
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty pending queue when pushPendingChanges then upsertCard is never called`() = runTest {
        // Arrange
        coEvery { userCardDao.getPendingUpload() } returns emptyList()
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(any()) } returns emptySet()

        // Act
        repository.pushPendingChanges(USER_ID)

        // Assert
        coVerify(exactly = 0) { remoteDataSource.upsertCard(any()) }
    }

    @Test
    fun `given empty pending queue when pushPendingChanges then timestamps are still saved`() = runTest {
        // Timestamps are saved regardless of whether there was anything to upload
        coEvery { userCardDao.getPendingUpload() } returns emptyList()
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(any()) } returns emptySet()

        repository.pushPendingChanges(USER_ID)

        coVerify(exactly = 1) { prefsDataStore.saveLastSyncTimestamp(eq(USER_ID), any()) }
        coVerify(exactly = 1) { prefsDataStore.saveLastSyncDate(eq(USER_ID), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — pushPendingChanges: soft-delete pending
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given pending delete remote ids when pushPendingChanges then softDeleteCard is called for each`() = runTest {
        // Arrange
        coEvery { userCardDao.getPendingUpload() } returns emptyList()
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(USER_ID) } returns setOf("remote-1", "remote-2")

        // Act
        repository.pushPendingChanges(USER_ID)

        // Assert
        coVerify(exactly = 1) { remoteDataSource.softDeleteCard("remote-1") }
        coVerify(exactly = 1) { remoteDataSource.softDeleteCard("remote-2") }
    }

    @Test
    fun `given pending delete remote ids when pushPendingChanges then clearPendingDeleteRemoteIds is called`() = runTest {
        // Arrange
        coEvery { userCardDao.getPendingUpload() } returns emptyList()
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(USER_ID) } returns setOf("remote-1")

        // Act
        repository.pushPendingChanges(USER_ID)

        // Assert
        coVerify(exactly = 1) { prefsDataStore.clearPendingDeleteRemoteIds(USER_ID) }
    }

    @Test
    fun `given no pending delete ids when pushPendingChanges then clearPendingDeleteRemoteIds is NOT called`() = runTest {
        // Arrange
        coEvery { userCardDao.getPendingUpload() } returns emptyList()
        coEvery { prefsDataStore.getPendingDeleteRemoteIds(USER_ID) } returns emptySet()

        // Act
        repository.pushPendingChanges(USER_ID)

        // Assert: clear must not fire when the set is empty
        coVerify(exactly = 0) { prefsDataStore.clearPendingDeleteRemoteIds(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — pushPendingChanges: empty userId guard
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty userId when pushPendingChanges then call completes without crash`() = runTest {
        // An empty userId is an edge case (unauthenticated path calling push directly).
        // The repository should not throw; the remote data source may receive the call
        // but that is gated at the ViewModel level in production.
        coEvery { userCardDao.getPendingUpload() } returns emptyList()
        coEvery { prefsDataStore.getPendingDeleteRemoteIds("") } returns emptySet()

        val result = repository.pushPendingChanges("")

        assertTrue(result.isSuccess)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — pullChanges: no remote changes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given server timestamp equals local timestamp when pullChanges then getChangesSince is NOT called`() = runTest {
        // Arrange: server and local timestamps are identical — nothing to pull
        val ts = Instant.parse(PAST_TS)
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns ts
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns PAST_TS

        // Act
        repository.pullChanges(USER_ID)

        // Assert
        coVerify(exactly = 0) { remoteDataSource.getChangesSince(any(), any()) }
    }

    @Test
    fun `given server timestamp is before local timestamp when pullChanges then getChangesSince is NOT called`() = runTest {
        // Local is ahead of server (already up to date)
        val serverTs = Instant.parse("2024-01-01T00:00:00Z")
        val localTs  = "2025-01-01T00:00:00Z"
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns serverTs
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns localTs

        repository.pullChanges(USER_ID)

        coVerify(exactly = 0) { remoteDataSource.getChangesSince(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — pullChanges: delta pull with active and deleted rows
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given server has newer active rows when pullChanges then rows are inserted via insertOrReplace`() = runTest {
        // Arrange: server has changes after local sync timestamp
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns FUTURE_TS
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns PAST_TS
        coEvery { remoteDataSource.getChangesSince(eq(USER_ID), any()) } returns
            Result.success(listOf(remoteDto(isDeleted = false)))

        // Act
        repository.pullChanges(USER_ID)

        // Assert
        coVerify(exactly = 1) { userCardDao.insertOrReplace(any()) }
    }

    @Test
    fun `given server has soft-deleted rows when pullChanges then matching local rows are deleted`() = runTest {
        // Arrange
        val localEntity = pendingEntity(id = 5L, remoteId = REMOTE_ID, syncStatus = SyncStatus.SYNCED)
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns FUTURE_TS
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns PAST_TS
        coEvery { remoteDataSource.getChangesSince(eq(USER_ID), any()) } returns
            Result.success(listOf(remoteDto(id = REMOTE_ID, isDeleted = true)))
        coEvery { userCardDao.getByRemoteId(REMOTE_ID) } returns localEntity

        // Act
        repository.pullChanges(USER_ID)

        // Assert: local row is removed
        coVerify(exactly = 1) { userCardDao.deleteById(5L) }
        // Active insert path must NOT fire for a deleted row
        coVerify(exactly = 0) { userCardDao.insertOrReplace(any()) }
    }

    @Test
    fun `given soft-deleted row with no local match when pullChanges then no delete is attempted`() = runTest {
        // Remote says deleted but local has no such remoteId — idempotent
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns FUTURE_TS
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns PAST_TS
        coEvery { remoteDataSource.getChangesSince(eq(USER_ID), any()) } returns
            Result.success(listOf(remoteDto(id = REMOTE_ID, isDeleted = true)))
        coEvery { userCardDao.getByRemoteId(REMOTE_ID) } returns null

        // Act
        repository.pullChanges(USER_ID)

        // Assert
        coVerify(exactly = 0) { userCardDao.deleteById(any()) }
    }

    @Test
    fun `given successful pull when pullChanges then sync timestamp is updated`() = runTest {
        // Arrange
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns FUTURE_TS
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns PAST_TS
        coEvery { remoteDataSource.getChangesSince(eq(USER_ID), any()) } returns Result.success(emptyList())

        // Act
        repository.pullChanges(USER_ID)

        // Assert: timestamp is refreshed
        coVerify(exactly = 1) { prefsDataStore.saveLastSyncTimestamp(eq(USER_ID), any()) }
        coVerify(exactly = 1) { prefsDataStore.saveLastSyncDate(eq(USER_ID), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — pullChanges: first-ever pull (no local timestamp)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no prior sync timestamp when pullChanges then getChangesSince uses Instant EPOCH`() = runTest {
        // Arrange: no local timestamp → localLastSync is null → since = EPOCH
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns FUTURE_TS
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns null
        coEvery { remoteDataSource.getChangesSince(eq(USER_ID), any()) } returns Result.success(emptyList())

        // Act
        repository.pullChanges(USER_ID)

        // Assert: getChangesSince must be called (no local ts means we must download everything)
        coVerify(exactly = 1) { remoteDataSource.getChangesSince(eq(USER_ID), eq(Instant.EPOCH)) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — pullChanges: remote failure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given getChangesSince returns failure when pullChanges then result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns FUTURE_TS
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns PAST_TS
        coEvery { remoteDataSource.getChangesSince(any(), any()) } returns
            Result.failure(RuntimeException("server error"))

        // Act
        val result = repository.pullChanges(USER_ID)

        // Assert
        assertTrue(result.isFailure)
        // No local writes should have occurred
        coVerify(exactly = 0) { userCardDao.insertOrReplace(any()) }
        coVerify(exactly = 0) { userCardDao.deleteById(any()) }
    }

    @Test
    fun `given getLastModified returns null when pullChanges then no changes are downloaded`() = runTest {
        // null means the remote collection is empty
        coEvery { remoteDataSource.getLastModified(USER_ID) } returns null
        coEvery { prefsDataStore.getLastSyncTimestamp(USER_ID) } returns PAST_TS

        // Act
        val result = repository.pullChanges(USER_ID)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { remoteDataSource.getChangesSince(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — Mutations: updateCard marks PENDING_UPLOAD
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a user card when updateCard then dao update and markPendingUpload are both called`() = runTest {
        // Arrange
        val userCard = buildUserCard(id = 42L)

        // Act
        repository.updateCard(userCard)

        // Assert: both calls must happen; order is guaranteed by the implementation
        coVerify(exactly = 1) { userCardDao.update(any()) }
        coVerify(exactly = 1) { userCardDao.markPendingUpload(42L) }
    }

    @Test
    fun `given a user card id when incrementQuantity then dao incrementQuantity and markPendingUpload are both called`() = runTest {
        // Act
        repository.incrementQuantity(id = 7L)

        // Assert
        coVerify(exactly = 1) { userCardDao.incrementQuantity(7L) }
        coVerify(exactly = 1) { userCardDao.markPendingUpload(7L) }
    }

    @Test
    fun `given positive quantity when updateQuantity then dao updateQuantity and markPendingUpload are both called`() = runTest {
        // Act
        repository.updateQuantity(id = 3L, quantity = 5)

        // Assert
        coVerify(exactly = 1) { userCardDao.updateQuantity(3L, 5) }
        coVerify(exactly = 1) { userCardDao.markPendingUpload(3L) }
    }

    @Test
    fun `given quantity zero when updateQuantity then deleteCard path is taken and markPendingUpload is NOT called`() = runTest {
        // Arrange: getById returns null (no remoteId) so delete is local-only
        coEvery { userCardDao.getById(3L) } returns null

        // Act
        repository.updateQuantity(id = 3L, quantity = 0)

        // Assert: deletion path — no mark-pending call
        coVerify(exactly = 1) { userCardDao.deleteById(3L) }
        coVerify(exactly = 0) { userCardDao.markPendingUpload(any()) }
        coVerify(exactly = 0) { userCardDao.updateQuantity(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 11 — deleteCard soft-delete logic
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card with remoteId and logged-in user when deleteCard then addPendingDeleteRemoteId is called`() = runTest {
        // Arrange
        val entity = pendingEntity(id = 10L, remoteId = REMOTE_ID, syncStatus = SyncStatus.SYNCED)
        coEvery { userCardDao.getById(10L) } returns entity
        coEvery { supabaseAuth.currentUserOrNull() } returns aUserInfo(USER_ID)

        // Act
        repository.deleteCard(10L)

        // Assert
        coVerify(exactly = 1) { prefsDataStore.addPendingDeleteRemoteId(USER_ID, REMOTE_ID) }
        coVerify(exactly = 1) { userCardDao.deleteById(10L) }
    }

    @Test
    fun `given card without remoteId when deleteCard then addPendingDeleteRemoteId is NOT called`() = runTest {
        // Arrange: remoteId is null — card was never synced to Supabase
        val entity = pendingEntity(id = 10L, remoteId = null)
        coEvery { userCardDao.getById(10L) } returns entity

        // Act
        repository.deleteCard(10L)

        // Assert
        coVerify(exactly = 0) { prefsDataStore.addPendingDeleteRemoteId(any(), any()) }
        coVerify(exactly = 1) { userCardDao.deleteById(10L) }
    }

    @Test
    fun `given card with remoteId but user NOT logged in when deleteCard then addPendingDeleteRemoteId is NOT called`() = runTest {
        // Arrange: user is null (not authenticated)
        val entity = pendingEntity(id = 10L, remoteId = REMOTE_ID, syncStatus = SyncStatus.SYNCED)
        coEvery { userCardDao.getById(10L) } returns entity
        coEvery { supabaseAuth.currentUserOrNull() } returns null

        // Act
        repository.deleteCard(10L)

        // Assert
        coVerify(exactly = 0) { prefsDataStore.addPendingDeleteRemoteId(any(), any()) }
        // But the local delete still happens
        coVerify(exactly = 1) { userCardDao.deleteById(10L) }
    }

    @Test
    fun `given non-existent card id when deleteCard then only deleteById is called`() = runTest {
        // Arrange: getById returns null (already deleted or invalid id)
        coEvery { userCardDao.getById(99L) } returns null

        // Act
        repository.deleteCard(99L)

        // Assert
        coVerify(exactly = 0) { prefsDataStore.addPendingDeleteRemoteId(any(), any()) }
        coVerify(exactly = 1) { userCardDao.deleteById(99L) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUserCard(id: Long = 1L) = UserCard(
        id               = id,
        scryfallId       = "card-001",
        quantity         = 1,
        isFoil           = false,
        isAlternativeArt = false,
        condition        = "NM",
        language         = "en",
        isForTrade       = false,
        isInWishlist     = false,
        addedAt          = System.currentTimeMillis(),
    )

    /** Creates a minimal [UserInfo] stub accepted by MockK. */
    private fun aUserInfo(userId: String): UserInfo = mockk<UserInfo>(relaxed = true) {
        every { id } returns userId
    }
}
