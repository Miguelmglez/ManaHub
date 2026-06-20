package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeck
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckCard
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckOwner
import com.mmg.manahub.feature.communitydecks.domain.usecase.GetCommunityDeckUseCase
import com.mmg.manahub.feature.communitydecks.domain.usecase.ImportCommunityDeckUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CommunityDeckDetailViewModel].
 *
 * Uses [StandardTestDispatcher] for deterministic coroutine control and Turbine
 * for Channel event assertions. Crashlytics is mocked because the VM calls
 * `FirebaseCrashlytics.getInstance()` in its init block (outside runCatching).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityDeckDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val getCommunityDeck: GetCommunityDeckUseCase = mockk()
    private val importCommunityDeck: ImportCommunityDeckUseCase = mockk()
    private val userPreferences: UserPreferencesDataStore = mockk()
    private val crashlytics: FirebaseCrashlytics = mockk(relaxed = true)

    private val featureFlagFlow = MutableStateFlow(true)

    // ── Fixtures ────────────────────────────────────────────────────────────

    private val testDeckId = 12345

    private val testOwner = CommunityDeckOwner(id = 1, username = "TestUser", avatarUrl = "")

    private fun buildCommunityDeck(
        cards: List<CommunityDeckCard> = listOf(
            CommunityDeckCard("Sol Ring", 1, listOf("Mainboard"), "oracle-001"),
            CommunityDeckCard("Command Tower", 1, listOf("Mainboard"), "oracle-002"),
        ),
    ) = CommunityDeck(
        archidektId = testDeckId,
        name = "Test Deck",
        description = "A test deck",
        format = "commander",
        owner = testOwner,
        viewCount = 42,
        createdAt = "2026-01-01",
        updatedAt = "2026-06-01",
        cards = cards,
        sourceUrl = "https://archidekt.com/decks/$testDeckId",
    )

    private fun buildSavedStateHandle(id: Int = testDeckId) =
        SavedStateHandle(mapOf("archidektId" to id))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        every { userPreferences.communityDecksEnabledFlow } returns featureFlagFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    /**
     * Creates the ViewModel AFTER stubs are set. The init block calls loadDeck(),
     * which launches in viewModelScope — advanceUntilIdle() must follow.
     */
    private fun createViewModel(savedStateHandle: SavedStateHandle = buildSavedStateHandle()) =
        CommunityDeckDetailViewModel(
            savedStateHandle = savedStateHandle,
            getCommunityDeck = getCommunityDeck,
            importCommunityDeck = importCommunityDeck,
            userPreferences = userPreferences,
        )

    // ── Group 1: Loading state ──────────────────────────────────────────────

    @Test
    fun `given init when VM created then initial state is Loading`() = runTest {
        // Arrange — use case does not complete yet (suspended).
        coEvery { getCommunityDeck(testDeckId) } coAnswers {
            // Never return — keeps the VM in Loading state.
            kotlinx.coroutines.awaitCancellation()
        }

        // Act
        val vm = createViewModel()

        // Assert — before advancing, state is Loading.
        assertTrue(vm.uiState.value is CommunityDeckDetailUiState.Loading)
    }

    // ── Group 2: Success state ──────────────────────────────────────────────

    @Test
    fun `given use case returns Success when loaded then state is Content`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)

        // Act
        val vm = createViewModel()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertTrue(state is CommunityDeckDetailUiState.Content)
        val content = state as CommunityDeckDetailUiState.Content
        assertEquals("Test Deck", content.deck.name)
        assertEquals(2, content.deck.cards.size)
        assertFalse(content.isStale)
        assertFalse(content.isImporting)
    }

    // ── Group 3: Error state ────────────────────────────────────────────────

    @Test
    fun `given use case returns Error when loaded then state is Error`() = runTest {
        // Arrange
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Error("Deck not found on Archidekt")

        // Act
        val vm = createViewModel()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertTrue(state is CommunityDeckDetailUiState.Error)
        assertEquals("Deck not found on Archidekt", (state as CommunityDeckDetailUiState.Error).message)
    }

    // ── Group 4: Stale data ─────────────────────────────────────────────────

    @Test
    fun `given use case returns stale Success when loaded then Content has isStale true`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck, isStale = true)

        // Act
        val vm = createViewModel()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value as CommunityDeckDetailUiState.Content
        assertTrue(state.isStale)
    }

    // ── Group 5: Import triggers ────────────────────────────────────────────

    @Test
    fun `given Content state when importDeck then isImporting becomes true`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } coAnswers {
            // Suspend indefinitely so we can observe the importing state.
            kotlinx.coroutines.awaitCancellation()
        }

        val vm = createViewModel()
        advanceUntilIdle()

        // Act — start import (runs in viewModelScope; won't complete).
        vm.importDeck()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value as CommunityDeckDetailUiState.Content
        assertTrue(state.isImporting)
    }

    @Test
    fun `given import completes when importDeck then isImporting becomes false`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } returns
            ImportCommunityDeckUseCase.ImportResult.Success("deck-001", 2, 0)

        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.importDeck()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value as CommunityDeckDetailUiState.Content
        assertFalse(state.isImporting)
    }

    // ── Group 6: Import success event ───────────────────────────────────────

    @Test
    fun `given successful import when importDeck then ShowImportResult and NavigateToDeck events are emitted`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } returns
            ImportCommunityDeckUseCase.ImportResult.Success("deck-001", 2, 0)

        val vm = createViewModel()
        advanceUntilIdle()

        // Act + Assert
        vm.events.test {
            vm.importDeck()
            advanceUntilIdle()

            val showResult = awaitItem()
            assertTrue(showResult is CommunityDeckDetailEvent.ShowImportResult)
            val result = showResult as CommunityDeckDetailEvent.ShowImportResult
            assertFalse(result.isError)
            assertEquals(2, result.resolvedCount)
            assertEquals(2, result.totalCount)

            val navigateEvent = awaitItem()
            assertTrue(navigateEvent is CommunityDeckDetailEvent.NavigateToDeck)
            assertEquals("deck-001", (navigateEvent as CommunityDeckDetailEvent.NavigateToDeck).deckId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given partial import when importDeck then ShowImportResult reflects partial counts`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } returns
            ImportCommunityDeckUseCase.ImportResult.Success("deck-001", 1, 1)

        val vm = createViewModel()
        advanceUntilIdle()

        // Act + Assert
        vm.events.test {
            vm.importDeck()
            advanceUntilIdle()

            val showResult = awaitItem() as CommunityDeckDetailEvent.ShowImportResult
            assertFalse(showResult.isError)
            assertEquals(1, showResult.resolvedCount)
            assertEquals(2, showResult.totalCount)

            // NavigateToDeck also emitted on partial.
            assertTrue(awaitItem() is CommunityDeckDetailEvent.NavigateToDeck)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 7: Import error event ─────────────────────────────────────────

    @Test
    fun `given import fails when importDeck then ShowImportResult with isError true is emitted`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } returns
            ImportCommunityDeckUseCase.ImportResult.Error("DB full")

        val vm = createViewModel()
        advanceUntilIdle()

        // Act + Assert
        vm.events.test {
            vm.importDeck()
            advanceUntilIdle()

            val event = awaitItem() as CommunityDeckDetailEvent.ShowImportResult
            assertTrue(event.isError)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given import fails when importDeck then no NavigateToDeck event is emitted`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } returns
            ImportCommunityDeckUseCase.ImportResult.Error("fail")

        val vm = createViewModel()
        advanceUntilIdle()

        // Act + Assert
        vm.events.test {
            vm.importDeck()
            advanceUntilIdle()

            // Only ShowImportResult — no NavigateToDeck.
            val event = awaitItem()
            assertTrue(event is CommunityDeckDetailEvent.ShowImportResult)

            // No more events.
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 8: Double import guard ────────────────────────────────────────

    @Test
    fun `given already importing when importDeck called again then second call is no-op`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } coAnswers {
            // Suspend indefinitely so import stays in-progress.
            kotlinx.coroutines.awaitCancellation()
        }

        val vm = createViewModel()
        advanceUntilIdle()

        // Act — first import starts.
        vm.importDeck()
        advanceUntilIdle()

        // Second import should be a no-op.
        vm.importDeck()
        advanceUntilIdle()

        // Assert — importCommunityDeck invoked exactly once.
        coVerify(exactly = 1) { importCommunityDeck(any(), any()) }
    }

    @Test
    fun `given Error state when importDeck called then it is a no-op`() = runTest {
        // Arrange
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Error("Not found")

        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.importDeck()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { importCommunityDeck(any(), any()) }
    }

    // ── Group 9: Feature flag ───────────────────────────────────────────────

    @Test
    fun `given feature enabled flow emits true then isFeatureEnabled is true`() = runTest {
        // Arrange
        featureFlagFlow.value = true
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(buildCommunityDeck())

        val vm = createViewModel()

        // Must subscribe to stateIn(WhileSubscribed) before reading.
        val job = backgroundScope.launch { vm.isFeatureEnabled.collect {} }
        advanceUntilIdle()

        // Assert
        assertTrue(vm.isFeatureEnabled.value)

        job.cancel()
    }

    @Test
    fun `given feature enabled flow emits false then isFeatureEnabled is false`() = runTest {
        // Arrange
        featureFlagFlow.value = false
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(buildCommunityDeck())

        val vm = createViewModel()

        // Must subscribe for WhileSubscribed to start.
        val job = backgroundScope.launch { vm.isFeatureEnabled.collect {} }
        advanceUntilIdle()

        // Assert
        assertFalse(vm.isFeatureEnabled.value)

        job.cancel()
    }

    @Test
    fun `given feature flag changes when subscribed then isFeatureEnabled updates`() = runTest {
        // Arrange
        featureFlagFlow.value = true
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(buildCommunityDeck())

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.isFeatureEnabled.collect {} }
        advanceUntilIdle()

        // Act
        featureFlagFlow.value = false
        advanceUntilIdle()

        // Assert
        assertFalse(vm.isFeatureEnabled.value)

        job.cancel()
    }

    // ── Group 10: Retry / loadDeck ──────────────────────────────────────────

    @Test
    fun `given Error state when loadDeck called then state transitions back to Content on success`() = runTest {
        // Arrange — first load fails.
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Error("Network error")

        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CommunityDeckDetailUiState.Error)

        // Arrange — retry succeeds.
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(buildCommunityDeck())

        // Act
        vm.loadDeck()
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value is CommunityDeckDetailUiState.Content)
    }

    @Test
    fun `given loadDeck called then state transitions through Loading`() = runTest {
        // Arrange
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(buildCommunityDeck())

        val vm = createViewModel()
        advanceUntilIdle()

        // Capture states during reload.
        val states = mutableListOf<CommunityDeckDetailUiState>()
        val job = backgroundScope.launch { vm.uiState.collect { states.add(it) } }

        // Arrange — make next call slower so Loading is observable before conflation.
        coEvery { getCommunityDeck(testDeckId) } coAnswers {
            delay(100)
            DataResult.Success(buildCommunityDeck())
        }

        // Act
        vm.loadDeck()
        advanceUntilIdle()

        // Assert — Loading was emitted during the reload.
        assertTrue(states.any { it is CommunityDeckDetailUiState.Loading })

        job.cancel()
    }

    // ── Group 11: Import progress callback ──────────────────────────────────

    @Test
    fun `given import in progress when onProgress called then importProgress updates`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)

        // Capture the onProgress lambda and invoke it.
        coEvery { importCommunityDeck(any(), any()) } coAnswers {
            val onProgress = secondArg<(Int, Int) -> Unit>()
            onProgress(1, 2)
            ImportCommunityDeckUseCase.ImportResult.Success("deck-001", 2, 0)
        }

        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.importDeck()
        advanceUntilIdle()

        // Assert — after completion, progress is cleared (null).
        val state = vm.uiState.value as CommunityDeckDetailUiState.Content
        assertEquals(null, state.importProgress)
        assertFalse(state.isImporting)
    }

    // ── Group 12: Crashlytics logging ───────────────────────────────────────

    @Test
    fun `given successful load then Crashlytics logs success`() = runTest {
        // Arrange
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(buildCommunityDeck())

        // Act
        val vm = createViewModel()
        advanceUntilIdle()

        // Assert
        io.mockk.verify { crashlytics.log("community_deck_load_success") }
        io.mockk.verify { crashlytics.setCustomKey("community_deck_card_count", 2) }
    }

    @Test
    fun `given failed load then Crashlytics logs error`() = runTest {
        // Arrange
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Error("fail")

        // Act
        val vm = createViewModel()
        advanceUntilIdle()

        // Assert
        io.mockk.verify { crashlytics.log("community_deck_load_error") }
    }
}
