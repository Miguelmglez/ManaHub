package com.mmg.manahub.feature.collection

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncResult
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.collection.presentation.CollectionViewModel
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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
import org.junit.Rule
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

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val getCollection          = mockk<GetCollectionUseCase>()
    private val cardRepository         = mockk<CardRepository>(relaxed = true)
    private val userCardRepository     = mockk<UserCardRepository>(relaxed = true)
    private val authRepository         = mockk<AuthRepository>(relaxed = true)
    private val syncManager            = mockk<SyncManager>(relaxed = true)
    private val workManager            = mockk<WorkManager>(relaxed = true)
    private val migrateLocalTradeLists = mockk<MigrateLocalTradeListsUseCase>(relaxed = true)
    private val getLocalWishlist          = mockk<GetLocalWishlistUseCase>(relaxed = true)
    private val wishlistRepository        = mockk<WishlistRepository>(relaxed = true)
    private val openForTradeRepository    = mockk<OpenForTradeRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val analyticsHelper           = mockk<AnalyticsHelper>(relaxed = true)
    private val collectionMergeConflictResolver = mockk<com.mmg.manahub.core.sync.CollectionMergeConflictResolver>(relaxed = true)

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
        // onSync()'s trade-list migration failure branch reports directly via
        // FirebaseCrashlytics.getInstance() (not through an injected abstraction) — must be
        // statically mocked, or any test exercising a migration failure crashes on the real,
        // uninitialized Firebase singleton instead of exercising the intended syncError path.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        every { getCollection() } returns flowOf(emptyList())
        every { syncManager.syncState } returns MutableStateFlow(SyncState.IDLE)
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)
        every { getLocalWishlist() } returns flowOf(emptyList())
        every { userPreferencesRepository.collectionViewModeFlow } returns flowOf(CollectionViewMode.GRID)
        coEvery { authRepository.getCurrentUser() } returns null
        coEvery { migrateLocalTradeLists(any()) } returns Result.success(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private fun buildViewModel(): CollectionViewModel = CollectionViewModel(
        savedStateHandle       = SavedStateHandle(),
        getCollection          = getCollection,
        cardRepository         = cardRepository,
        userCardRepository     = userCardRepository,
        authRepository         = authRepository,
        syncManager            = syncManager,
        workManager            = workManager,
        migrateLocalTradeLists = migrateLocalTradeLists,
        getLocalWishlist          = getLocalWishlist,
        wishlistRepository        = wishlistRepository,
        openForTradeRepository    = openForTradeRepository,
        userPreferencesRepository = userPreferencesRepository,
        analyticsHelper           = analyticsHelper,
        collectionMergeConflictResolver = collectionMergeConflictResolver,
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
    fun `given trade list migration fails when onSync called then syncError is populated`() = runTest {
        // Collection sync data-loss fix, Phase 5 (2026-09-06): onSync() no longer awaits
        // syncManager.sync(...) directly (it is now dispatched as WorkManager unique work — see
        // the GROUP 2b tests below), so syncError can only be populated by the trade-list
        // migration branch now. The collection-sync outcome itself is surfaced separately via
        // uiState.syncState (observeSyncState()).
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        every { wishlistRepository.observeUnsyncedCount() } returns MutableStateFlow(1)
        coEvery { migrateLocalTradeLists(USER_ID) } returns Result.failure(RuntimeException("migration failed"))

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onSync()
        advanceUntilIdle()

        assertEquals("migration failed", vm.uiState.value.syncError)
    }

    @Test
    fun `given no logged-in user when onSync called then no sync work is enqueued`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onSync()
        advanceUntilIdle()

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>())
        }
    }

    @Test
    fun `given logged-in user when onSync called then a one-time collection sync work request is enqueued`() = runTest {
        // Collection sync data-loss fix, Phase 5 (2026-09-06): onSync() dispatches
        // CollectionSyncWorker.enqueueOneTimeSync (durable WorkManager unique work) instead of
        // calling syncManager.sync(userId) inline on viewModelScope -- the inline call used to be
        // cancelled if the user navigated away from this screen mid-sync.
        coEvery { authRepository.getCurrentUser() } returns loggedInUser

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onSync()
        advanceUntilIdle()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                eq(com.mmg.manahub.core.sync.CollectionSyncWorker.WORK_NAME_ONE_TIME),
                any<androidx.work.ExistingWorkPolicy>(),
                any<androidx.work.OneTimeWorkRequest>(),
            )
        }
        coVerify(exactly = 0) { syncManager.sync(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — onSyncDismissed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given syncState is ERROR when onSyncDismissed then syncState resets to IDLE and syncError is cleared`() = runTest {
        // Drive syncError via the trade-list migration failure path (the only remaining source of
        // syncError text since Phase 5 -- see the GROUP 2b rewrite above) and syncState via the
        // mocked syncManager.syncState StateFlow observeSyncState() forwards into uiState.
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        every { wishlistRepository.observeUnsyncedCount() } returns MutableStateFlow(1)
        coEvery { migrateLocalTradeLists(USER_ID) } returns Result.failure(RuntimeException("some error"))
        val syncStateFlow = MutableStateFlow(SyncState.IDLE)
        every { syncManager.syncState } returns syncStateFlow

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onSync()
        advanceUntilIdle()
        syncStateFlow.value = SyncState.ERROR
        advanceUntilIdle()
        assertEquals("some error", vm.uiState.value.syncError)
        assertEquals(SyncState.ERROR, vm.uiState.value.syncState)

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
    fun `given guest user when they first authenticate then CollectionViewModel never calls assignUserIdAndSync directly`() = runTest {
        // Collection sync data-loss fix, Phase 5 (2026-09-06): the offline-to-online first-login
        // full pull used to be launched here on viewModelScope, so navigating away from this
        // screen mid-pull cancelled it. It is now dispatched as durable WorkManager unique work
        // from the APP-SCOPED session observer in ManaHubApp.kt
        // (CollectionSyncWorker.enqueueFirstLoginSync) instead -- this ViewModel must never call
        // syncManager.assignUserIdAndSync directly any more.
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        every { authRepository.sessionState } returns sessionFlow

        buildViewModel()
        advanceUntilIdle()

        // Simulate login
        sessionFlow.value = SessionState.Authenticated(loggedInUser)
        advanceUntilIdle()

        coVerify(exactly = 0) { syncManager.assignUserIdAndSync(any()) }
    }

    @Test
    fun `given already authenticated user when session emits authenticated again then CollectionViewModel still never calls assignUserIdAndSync`() = runTest {
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

        coVerify(exactly = 0) { syncManager.assignUserIdAndSync(any()) }
    }

    @Test
    fun `given unauthenticated user then assignUserIdAndSync is never called`() = runTest {
        buildViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncManager.assignUserIdAndSync(any()) }
    }
}
