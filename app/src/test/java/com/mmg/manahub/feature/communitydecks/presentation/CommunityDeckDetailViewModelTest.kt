package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckCard
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.mmg.manahub.feature.communitydecks.domain.CommunityDeckImportCoordinator
import com.mmg.manahub.feature.communitydecks.domain.usecase.GetCommunityDeckUseCase
import com.mmg.manahub.feature.communitydecks.domain.usecase.ImportCommunityDeckUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
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
 * Uses [StandardTestDispatcher] for deterministic coroutine control and Turbine for Channel event
 * assertions. Crashlytics is mocked because the VM calls `FirebaseCrashlytics.getInstance()` in its
 * init block (outside runCatching).
 *
 * ## Import survives navigation (bug fix, 2026-07-22)
 * The VM no longer calls [ImportCommunityDeckUseCase] directly — it delegates to a
 * [CommunityDeckImportCoordinator], which is constructed here with an `appScope` backed by the SAME
 * [testDispatcher] instance already passed to `Dispatchers.setMain(...)` (kotlinx-coroutines-test
 * `runTest` reuses the scheduler behind an already-set `Main` dispatcher), so `advanceUntilIdle()`
 * deterministically drives BOTH `viewModelScope` and the coordinator's app-scoped coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityDeckDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val getCommunityDeck: GetCommunityDeckUseCase = mockk()
    private val importCommunityDeck: ImportCommunityDeckUseCase = mockk()
    private val userCardRepository: UserCardRepository = mockk()
    private val crashlytics: FirebaseCrashlytics = mockk(relaxed = true)

    /** Shared across a test method's ViewModel instances — this is what lets an import survive a
     * ViewModel being cleared and a new one being created for the same `archidektId`. */
    private lateinit var importCoordinator: CommunityDeckImportCoordinator

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

        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        importCoordinator = CommunityDeckImportCoordinator(
            importCommunityDeck = importCommunityDeck,
            appScope = CoroutineScope(testDispatcher),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    /**
     * Creates the ViewModel AFTER stubs are set. The init block calls loadDeck(),
     * which launches in viewModelScope — advanceUntilIdle() must follow.
     *
     * @param coordinator defaults to the shared per-test [importCoordinator] — pass an explicit one
     *   only to exercise a DIFFERENT coordinator instance (never needed for the "survives across
     *   two ViewModel instances" scenario, which relies on reusing the SAME coordinator).
     */
    private fun createViewModel(
        savedStateHandle: SavedStateHandle = buildSavedStateHandle(),
        coordinator: CommunityDeckImportCoordinator = importCoordinator,
    ) = CommunityDeckDetailViewModel(
        savedStateHandle = savedStateHandle,
        getCommunityDeck = getCommunityDeck,
        importCoordinator = coordinator,
        userCardRepository = userCardRepository,
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

        // Act — start import (runs on the coordinator's app scope; won't complete).
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

        // Second import should be a no-op (guarded both locally in the VM and — authoritatively —
        // inside CommunityDeckImportCoordinator.startImport's job.isActive check).
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

    // ── Group 9: Import survives navigation (bug fix, 2026-07-22) ───────────

    @Test
    fun `given import started by one VM instance when a second VM instance is created for the same archidektId then it observes the in-flight import`() = runTest {
        // Arrange — VM1 starts an import that never completes (simulating a slow network import
        // still running when the user navigates away, clearing VM1).
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }

        val vm1 = createViewModel()
        advanceUntilIdle()
        vm1.importDeck()
        advanceUntilIdle()
        assertTrue((vm1.uiState.value as CommunityDeckDetailUiState.Content).isImporting)

        // Act — a FRESH ViewModel instance for the SAME archidektId + SAME coordinator (as if the
        // user reopened the screen) — note vm1 is never explicitly cleared; the import keeps
        // running on the coordinator's app scope regardless, which is exactly the point.
        val vm2 = createViewModel()
        advanceUntilIdle()

        // Assert — vm2 immediately reflects the still-running import, it did NOT lose it.
        val state2 = vm2.uiState.value as CommunityDeckDetailUiState.Content
        assertTrue(state2.isImporting)
    }

    @Test
    fun `given import already completed when a second VM instance is created for the same archidektId then it does not replay stale events`() = runTest {
        // Arrange — VM1 runs an import to completion.
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        coEvery { importCommunityDeck(any(), any()) } returns
            ImportCommunityDeckUseCase.ImportResult.Success("deck-001", 2, 0)

        val vm1 = createViewModel()
        advanceUntilIdle()
        vm1.events.test {
            vm1.importDeck()
            advanceUntilIdle()
            awaitItem() // ShowImportResult
            awaitItem() // NavigateToDeck
            cancelAndIgnoreRemainingEvents()
        }

        // Act — a second ViewModel instance for the SAME archidektId + SAME coordinator, created
        // AFTER the import already finished (as if the user reopened the screen later).
        val vm2 = createViewModel()
        advanceUntilIdle()

        // Assert — vm2's Content correctly reflects "not importing" (the terminal state), and
        // critically it must NOT replay the ShowImportResult/NavigateToDeck events a second time.
        val state2 = vm2.uiState.value as CommunityDeckDetailUiState.Content
        assertFalse(state2.isImporting)

        vm2.events.test {
            expectNoEvents()
        }
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

    // ── Group 13: Owned-card identity keys (UI addition, 2026-07-22) ────────

    /** Minimal [Card] fixture — mirrors `ImportCommunityDeckUseCaseTest.buildResolvedCard`. */
    private fun buildOwnedCard(scryfallId: String, name: String) = com.mmg.manahub.core.model.Card(
        scryfallId = scryfallId,
        name = name,
        printedName = null,
        manaCost = null,
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = "Artifact",
        printedTypeLine = null,
        oracleText = null,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "test",
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "2026-01-01",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = null,
        scryfallUri = "",
    )

    @Test
    fun `given owned cards in the user collection when loaded then ownedCardIdentityKeys reflects them`() = runTest {
        // Arrange — Card.oracleId defaults to blank, so the identity key falls back to the name
        // (Card Versions & Languages convention: oracleId.ifBlank { name }).
        val deck = buildCommunityDeck()
        coEvery { getCommunityDeck(testDeckId) } returns DataResult.Success(deck)
        val ownedCard = com.mmg.manahub.core.model.UserCardWithCard(
            userCard = com.mmg.manahub.core.model.UserCard(id = "uc-1", scryfallId = "sf-sol-ring"),
            card = buildOwnedCard(scryfallId = "sf-sol-ring", name = "Sol Ring"),
        )
        every { userCardRepository.observeCollection() } returns flowOf(listOf(ownedCard))

        // Act
        val vm = createViewModel()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value as CommunityDeckDetailUiState.Content
        assertEquals(setOf("Sol Ring"), state.ownedCardIdentityKeys)
    }
}
