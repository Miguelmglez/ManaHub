package com.mmg.manahub.feature.collection

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RemoveCardUseCase
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncResult
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for sync-related behaviour in [CollectionViewModel].
 *
 * Focuses on:
 *  - onSync() state transitions (success / error / no-user guard)
 *  - onSyncDismissed() reset
 *  - syncState starts as IDLE
 *  - Guest → Authenticated transition triggers assignUserIdAndSync()
 *
 * Non-sync behaviour is covered by [CollectionViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelSyncTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val getCollection      = mockk<GetCollectionUseCase>()
    private val removeCard         = mockk<RemoveCardUseCase>(relaxed = true)
    private val cardRepository     = mockk<CardRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val authRepository     = mockk<AuthRepository>(relaxed = true)
    private val syncManager        = mockk<SyncManager>(relaxed = true)
    private val workManager        = mockk<WorkManager>(relaxed = true)

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID = "user-uuid-001"

    private val loggedInUser = AuthUser(
        id        = USER_ID,
        email     = "user@example.com",
        nickname  = "TestUser",
        gameTag   = "#XYZ",
        avatarUrl = null,
        provider  = "email",
    )

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getCollection() } returns flowOf(emptyList())
        every { syncManager.syncState } returns MutableStateFlow(SyncState.IDLE)
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)
        coEvery { authRepository.getCurrentUser() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private fun buildViewModel(): CollectionViewModel = CollectionViewModel(
        getCollection      = getCollection,
        removeCard         = removeCard,
        cardRepository     = cardRepository,
        userCardRepository = userCardRepository,
        authRepository     = authRepository,
        syncManager        = syncManager,
        workManager        = workManager,
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — syncState starts as IDLE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given ViewModel just initialised then syncState is IDLE and syncError is null`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(SyncState.IDLE, vm.uiState.value.syncState)
        assertNull(vm.uiState.value.syncError)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — onSync: state transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given logged-in user when onSync called and sync succeeds then syncError is null`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { syncManager.sync(USER_ID) } returns SyncResult(state = SyncState.SUCCESS)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onSync()
        advanceUntilIdle()

        assertNull(vm.uiState.value.syncError)
    }

    @Test
    fun `given logged-in user when onSync called and sync fails then syncError is populated`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { syncManager.sync(USER_ID) } returns SyncResult(
            state = SyncState.ERROR,
            error = "network timeout",
        )

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onSync()
        advanceUntilIdle()

        assertEquals("network timeout", vm.uiState.value.syncError)
    }

    @Test
    fun `given no logged-in user when onSync called then syncManager sync is NOT called`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onSync()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncManager.sync(any()) }
    }

    @Test
    fun `given logged-in user when onSync called then workManager enqueues fallback work`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { syncManager.sync(USER_ID) } returns SyncResult(state = SyncState.SUCCESS)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onSync()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            workManager.enqueueUniqueWork(
                any<String>(),
                eq(ExistingWorkPolicy.REPLACE),
                any<OneTimeWorkRequest>(),
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — onSyncDismissed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given syncState is ERROR when onSyncDismissed then syncState resets to IDLE and syncError is cleared`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { syncManager.sync(USER_ID) } returns SyncResult(
            state = SyncState.ERROR,
            error = "some error",
        )

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onSync()
        advanceUntilIdle()
        assertEquals("some error", vm.uiState.value.syncError)

        vm.onSyncDismissed()

        assertEquals(SyncState.IDLE, vm.uiState.value.syncState)
        assertNull(vm.uiState.value.syncError)
    }

    @Test
    fun `given syncState is IDLE when onSyncDismissed called then state remains IDLE`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onSyncDismissed()

        assertEquals(SyncState.IDLE, vm.uiState.value.syncState)
        assertNull(vm.uiState.value.syncError)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Guest → Authenticated migration
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given guest user when they first authenticate then assignUserIdAndSync is called once`() = runTest {
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        every { authRepository.sessionState } returns sessionFlow

        buildViewModel()
        advanceUntilIdle()

        // Simulate login
        sessionFlow.value = SessionState.Authenticated(loggedInUser)
        advanceUntilIdle()

        coVerify(exactly = 1) { syncManager.assignUserIdAndSync(USER_ID) }
    }

    @Test
    fun `given already authenticated user when session emits authenticated again then assignUserIdAndSync is called only once`() = runTest {
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        every { authRepository.sessionState } returns sessionFlow

        buildViewModel()
        advanceUntilIdle()

        // First login
        sessionFlow.value = SessionState.Authenticated(loggedInUser)
        advanceUntilIdle()

        // Re-emit same authenticated state (e.g. config change)
        sessionFlow.value = SessionState.Authenticated(loggedInUser)
        advanceUntilIdle()

        coVerify(exactly = 1) { syncManager.assignUserIdAndSync(USER_ID) }
    }

    @Test
    fun `given unauthenticated user then assignUserIdAndSync is never called`() = runTest {
        buildViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncManager.assignUserIdAndSync(any()) }
    }
}
