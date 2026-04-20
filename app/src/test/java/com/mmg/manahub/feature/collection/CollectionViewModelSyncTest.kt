package com.mmg.manahub.feature.collection

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RemoveCardUseCase
import com.mmg.manahub.feature.auth.domain.model.AuthUser
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
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Unit tests for the sync-related behaviour in [CollectionViewModel].
 *
 * These tests focus exclusively on:
 *  - autoSyncIfFirstRunOfDay() triggered during init
 *  - onPushCollection() / onPullCollection() state transitions
 *  - onSyncDismissed() reset
 *  - observePendingCount() propagation to uiState
 *
 * Non-sync behaviour is covered by [CollectionViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelSyncTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val getCollection      = mockk<GetCollectionUseCase>()
    private val removeCard         = mockk<RemoveCardUseCase>(relaxed = true)
    private val cardRepository     = mockk<CardRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val authRepository     = mockk<AuthRepository>(relaxed = true)
    private val prefsDataStore     = mockk<UserPreferencesDataStore>(relaxed = true)

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID   = "user-uuid-001"
    private val TODAY     = LocalDate.now(ZoneOffset.UTC).toString()
    private val YESTERDAY = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString()

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
        // Default: empty collection, pending count 0
        every { getCollection() } returns flowOf(emptyList())
        every { userCardRepository.observePendingCount() } returns flowOf(0)
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
        prefsDataStore     = prefsDataStore,
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — autoSyncIfFirstRunOfDay
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given logged-in user and last sync was yesterday when ViewModel inits then pushPendingChanges is called`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns YESTERDAY
        coEvery { userCardRepository.pushPendingChanges(USER_ID) } returns Result.success(Unit)

        // Act
        buildViewModel()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userCardRepository.pushPendingChanges(USER_ID) }
    }

    @Test
    fun `given logged-in user and last sync was today when ViewModel inits then pushPendingChanges is NOT called`() = runTest {
        // Arrange: already synced today — skip auto-sync
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns TODAY

        // Act
        buildViewModel()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { userCardRepository.pushPendingChanges(any()) }
    }

    @Test
    fun `given no logged-in user when ViewModel inits then pushPendingChanges is NOT called`() = runTest {
        // Arrange: user is not authenticated
        coEvery { authRepository.getCurrentUser() } returns null

        // Act
        buildViewModel()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { userCardRepository.pushPendingChanges(any()) }
    }

    @Test
    fun `given logged-in user and no prior sync date when ViewModel inits then pushPendingChanges IS called`() = runTest {
        // null last sync date means first ever run → must sync
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns null
        coEvery { userCardRepository.pushPendingChanges(USER_ID) } returns Result.success(Unit)

        buildViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { userCardRepository.pushPendingChanges(USER_ID) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — onPushCollection: state transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given logged-in user when onPushCollection called and push succeeds then syncState transitions to SUCCESS`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns TODAY  // skip auto-sync
        coEvery { userCardRepository.pushPendingChanges(USER_ID) } returns Result.success(Unit)

        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.onPushCollection()
        advanceUntilIdle()

        // Assert
        assertEquals(SyncState.SUCCESS, vm.uiState.value.syncState)
        assertNull(vm.uiState.value.syncError)
    }

    @Test
    fun `given logged-in user when onPushCollection called and push fails then syncState transitions to ERROR`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns TODAY
        coEvery { userCardRepository.pushPendingChanges(USER_ID) } returns
            Result.failure(RuntimeException("network timeout"))

        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.onPushCollection()
        advanceUntilIdle()

        // Assert
        assertEquals(SyncState.ERROR, vm.uiState.value.syncState)
        assertEquals("network timeout", vm.uiState.value.syncError)
    }

    @Test
    fun `given no logged-in user when onPushCollection called then pushPendingChanges is NOT called`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns null

        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.onPushCollection()
        advanceUntilIdle()

        // Assert: auth guard returns early
        coVerify(exactly = 0) { userCardRepository.pushPendingChanges(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — onPullCollection: state transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given logged-in user when onPullCollection called and pull succeeds then syncState transitions to SUCCESS`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns TODAY
        coEvery { userCardRepository.pullChanges(USER_ID) } returns Result.success(Unit)

        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.onPullCollection()
        advanceUntilIdle()

        // Assert
        assertEquals(SyncState.SUCCESS, vm.uiState.value.syncState)
        assertNull(vm.uiState.value.syncError)
    }

    @Test
    fun `given logged-in user when onPullCollection called and pull fails then syncState transitions to ERROR`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns TODAY
        coEvery { userCardRepository.pullChanges(USER_ID) } returns
            Result.failure(RuntimeException("server unavailable"))

        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.onPullCollection()
        advanceUntilIdle()

        // Assert
        assertEquals(SyncState.ERROR, vm.uiState.value.syncState)
        assertEquals("server unavailable", vm.uiState.value.syncError)
    }

    @Test
    fun `given no logged-in user when onPullCollection called then pullChanges is NOT called`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns null

        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.onPullCollection()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { userCardRepository.pullChanges(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — onSyncDismissed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given syncState is ERROR when onSyncDismissed then syncState resets to IDLE and syncError is cleared`() = runTest {
        // Arrange: force an error state first
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns TODAY
        coEvery { userCardRepository.pushPendingChanges(USER_ID) } returns
            Result.failure(RuntimeException("some error"))

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onPushCollection()
        advanceUntilIdle()
        assertEquals(SyncState.ERROR, vm.uiState.value.syncState)

        // Act
        vm.onSyncDismissed()

        // Assert: state and error are both cleared
        assertEquals(SyncState.IDLE, vm.uiState.value.syncState)
        assertNull(vm.uiState.value.syncError)
    }

    @Test
    fun `given syncState is SUCCESS when onSyncDismissed then syncState resets to IDLE`() = runTest {
        // Arrange: force a success state
        coEvery { authRepository.getCurrentUser() } returns loggedInUser
        coEvery { prefsDataStore.getLastSyncDate(USER_ID) } returns TODAY
        coEvery { userCardRepository.pushPendingChanges(USER_ID) } returns Result.success(Unit)

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onPushCollection()
        advanceUntilIdle()
        assertEquals(SyncState.SUCCESS, vm.uiState.value.syncState)

        // Act
        vm.onSyncDismissed()

        // Assert
        assertEquals(SyncState.IDLE, vm.uiState.value.syncState)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — observePendingCount: propagation to uiState
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given repository emits pending count 3 when ViewModel inits then pendingUploadCount is 3`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns null
        every { userCardRepository.observePendingCount() } returns flowOf(3)

        // Act
        val vm = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(3, vm.uiState.value.pendingUploadCount)
    }

    @Test
    fun `given pending count updates from 2 to 0 when rows are synced then uiState reflects the new count`() = runTest {
        // Arrange: simulate flow emitting 2 then 0
        coEvery { authRepository.getCurrentUser() } returns null
        val countFlow = MutableStateFlow(2)
        every { userCardRepository.observePendingCount() } returns countFlow

        val vm = buildViewModel()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.pendingUploadCount)

        // Act: simulate sync completing — pending count drops to 0
        countFlow.value = 0
        advanceUntilIdle()

        // Assert
        assertEquals(0, vm.uiState.value.pendingUploadCount)
    }

    @Test
    fun `given repository emits pending count 0 when ViewModel inits then pendingUploadCount is 0`() = runTest {
        // Arrange
        coEvery { authRepository.getCurrentUser() } returns null
        every { userCardRepository.observePendingCount() } returns flowOf(0)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.pendingUploadCount)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — syncState starts as IDLE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given ViewModel just initialized then syncState is IDLE and syncError is null`() = runTest {
        // Arrange: no user so auto-sync won't fire
        coEvery { authRepository.getCurrentUser() } returns null

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(SyncState.IDLE, vm.uiState.value.syncState)
        assertNull(vm.uiState.value.syncError)
    }
}
