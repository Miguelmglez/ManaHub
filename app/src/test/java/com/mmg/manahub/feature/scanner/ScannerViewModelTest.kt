package com.mmg.manahub.feature.scanner

import app.cash.turbine.test
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.usecase.card.SearchCardUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ScannerViewModel].
 *
 * Covers:
 * - Duplicate name detection guard (same name → single search)
 * - foundCard guard (name detected while card already found → ignored)
 * - In-progress search cancellation and restart
 * - searchCards returning results → foundCard set, confirm sheet shown
 * - searchCards returning empty list → fallback to searchCard
 * - searchCard returning success → foundCard set, confirm sheet shown
 * - Both searches returning error → error message set, then auto-cleared after 2s
 * - Exception inside coroutine → connection error shown, then auto-cleared
 * - onConfirmAdd success → state fully reset for next scan
 * - onConfirmAdd error → error shown, sheet dismissed
 * - onDismissConfirmSheet → state reset to initial defaults
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val searchCards      = mockk<SearchCardsUseCase>()
    private val searchCard       = mockk<SearchCardUseCase>()
    private val addToCollection  = mockk<AddCardToCollectionUseCase>()
    private val analyticsHelper  = mockk<AnalyticsHelper>(relaxed = true)

    private lateinit var viewModel: ScannerViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val sampleCard = TestFixtures.buildCard(
        scryfallId = "abc-001",
        name       = "Lightning Bolt",
    )

    private fun buildViewModel(): ScannerViewModel =
        ScannerViewModel(
            searchCards     = searchCards,
            searchCard      = searchCard,
            addToCollection = addToCollection,
            analyticsHelper = analyticsHelper,
        )

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — onCardNameDetected: guards
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given same card name detected twice when onCardNameDetected then searchCards is called only once`() = runTest {
        // Arrange
        coEvery { searchCards("Lightning Bolt") } returns DataResult.Success(listOf(sampleCard))

        // Act
        viewModel.onCardNameDetected("Lightning Bolt")
        viewModel.onCardNameDetected("Lightning Bolt") // duplicate — should be ignored
        advanceUntilIdle()

        // Assert: the use case was invoked exactly once despite two detections
        coVerify(exactly = 1) { searchCards("Lightning Bolt") }
    }

    @Test
    fun `given foundCard is already set when onCardNameDetected then search is not triggered`() = runTest {
        // Arrange — first detection sets foundCard
        coEvery { searchCards("Lightning Bolt") } returns DataResult.Success(listOf(sampleCard))
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        // Act — second detection with a different name, while foundCard != null
        viewModel.onCardNameDetected("Counterspell")
        advanceUntilIdle()

        // Assert: searchCards was called once only for the first detection
        coVerify(exactly = 1) { searchCards(any()) }
    }

    @Test
    fun `given showConfirmSheet is true when onCardNameDetected then search is ignored`() = runTest {
        // Arrange — put ViewModel into "confirm sheet shown" state
        coEvery { searchCards(any()) } returns DataResult.Success(listOf(sampleCard))
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showConfirmSheet)

        // Act — new detection while sheet is open
        viewModel.onCardNameDetected("Fireball")
        advanceUntilIdle()

        // Assert: searchCards not called for "Fireball"
        coVerify(exactly = 0) { searchCards("Fireball") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — onCardNameDetected: search cancellation / restart
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given isSearching when onCardNameDetected with new name then previous job is cancelled and new search starts`() = runTest {
        // Arrange — first name starts a delayed search (200ms debounce)
        coEvery { searchCards("Fireball") } returns DataResult.Success(listOf(sampleCard))
        coEvery { searchCards("Counterspell") } returns DataResult.Success(listOf(
            TestFixtures.buildCard(scryfallId = "cs-001", name = "Counterspell")
        ))

        // Act: trigger first search, then immediately trigger a second before debounce expires
        viewModel.onCardNameDetected("Fireball")
        advanceTimeBy(50) // less than 200ms debounce
        viewModel.onCardNameDetected("Counterspell")
        advanceUntilIdle()

        // Assert: only the second name's search resulted in a foundCard
        assertEquals("Counterspell", viewModel.uiState.value.foundCard?.name)
        // "Fireball" search was cancelled before it could complete
        coVerify(exactly = 0) { searchCards("Fireball") }
        coVerify(exactly = 1) { searchCards("Counterspell") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — searchCards returns results
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given searchCards returns non-empty list when onCardNameDetected then foundCard is set and confirmSheet shown`() = runTest {
        // Arrange
        coEvery { searchCards("Lightning Bolt") } returns DataResult.Success(listOf(sampleCard))

        // Act
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(sampleCard, state.foundCard)
        assertTrue(state.showConfirmSheet)
        assertFalse(state.isSearching)
        assertNull(state.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — searchCards empty → fallback to searchCard
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given searchCards returns empty list when onCardNameDetected then searchCard fallback is invoked`() = runTest {
        // Arrange
        coEvery { searchCards("Lightning Bolt") } returns DataResult.Success(emptyList())
        coEvery { searchCard("Lightning Bolt") } returns DataResult.Success(sampleCard)

        // Act
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        // Assert: exact-lookup was used after empty fuzzy result
        coVerify(exactly = 1) { searchCard("Lightning Bolt") }
        assertEquals(sampleCard, viewModel.uiState.value.foundCard)
        assertTrue(viewModel.uiState.value.showConfirmSheet)
    }

    @Test
    fun `given searchCards returns error when onCardNameDetected then searchCard fallback is invoked`() = runTest {
        // Arrange
        coEvery { searchCards("Lightning Bolt") } returns DataResult.Error("Network error")
        coEvery { searchCard("Lightning Bolt") } returns DataResult.Success(sampleCard)

        // Act
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        // Assert: error from searchCards did not abort — fallback ran
        coVerify(exactly = 1) { searchCard("Lightning Bolt") }
        assertEquals(sampleCard, viewModel.uiState.value.foundCard)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — searchCard returns success
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given searchCards empty and searchCard success when onCardNameDetected then foundCard and sheet are set`() = runTest {
        // Arrange
        coEvery { searchCards(any()) } returns DataResult.Success(emptyList())
        coEvery { searchCard(any()) } returns DataResult.Success(sampleCard)

        // Act
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(sampleCard, state.foundCard)
        assertTrue(state.showConfirmSheet)
        assertFalse(state.isSearching)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Both searches return error → error state, then auto-clear
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given both searches return error when onCardNameDetected then error message is shown`() = runTest {
        // Arrange
        coEvery { searchCards("Unknown") } returns DataResult.Success(emptyList())
        coEvery { searchCard("Unknown") } returns DataResult.Error("Not found")

        // Act
        viewModel.onCardNameDetected("Unknown")
        advanceUntilIdle()

        // Assert: error is shown immediately after failure
        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Unknown"))
        assertFalse(state.isSearching)
    }

    @Test
    fun `given both searches return error when 2 seconds pass then error and detectedName are cleared`() = runTest {
        // Arrange
        coEvery { searchCards("Unknown") } returns DataResult.Success(emptyList())
        coEvery { searchCard("Unknown") } returns DataResult.Error("Not found")

        // Act
        viewModel.onCardNameDetected("Unknown")
        advanceUntilIdle() // run coroutine up to the 2s delay

        // Advance past the 2-second auto-clear delay inside the coroutine
        advanceTimeBy(2_001)
        advanceUntilIdle()

        // Assert: error and detectedName are cleared after 2s
        val state = viewModel.uiState.value
        assertNull(state.error)
        assertNull(state.detectedName)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — Exception inside coroutine
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given searchCards throws exception when onCardNameDetected then connection error is shown`() = runTest {
        // Arrange
        coEvery { searchCards(any()) } throws RuntimeException("Socket timeout")

        // Act
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("Connection error", state.error)
        assertFalse(state.isSearching)
    }

    @Test
    fun `given exception in coroutine when 2 seconds pass then error is auto-cleared`() = runTest {
        // Arrange
        coEvery { searchCards(any()) } throws RuntimeException("timeout")

        // Act
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()
        advanceTimeBy(2_001)
        advanceUntilIdle()

        // Assert
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.detectedName)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — onConfirmAdd
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given addToCollection succeeds when onConfirmAdd then state is reset for next scan`() = runTest {
        // Arrange — put ViewModel in confirm-sheet state first
        coEvery { searchCards(any()) } returns DataResult.Success(listOf(sampleCard))
        coEvery { addToCollection(scryfallId = any(), isFoil = any(), condition = any(), language = any()) } returns DataResult.Success(Unit)

        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        // Act
        viewModel.onConfirmAdd(
            scryfallId = "abc-001",
            isFoil     = false,
            condition  = "NM",
            language   = "en",
            quantity   = 1,
        )
        advanceUntilIdle()

        // Assert: state resets to allow next scan
        val state = viewModel.uiState.value
        assertNull(state.foundCard)
        assertFalse(state.showConfirmSheet)
        assertTrue(state.addedSuccessfully)
        assertTrue(state.isScanning)
        assertNull(state.detectedName)
    }

    @Test
    fun `given addToCollection returns error when onConfirmAdd then error message is shown and sheet dismissed`() = runTest {
        // Arrange
        coEvery { searchCards(any()) } returns DataResult.Success(listOf(sampleCard))
        coEvery { addToCollection(scryfallId = any(), isFoil = any(), condition = any(), language = any()) } returns DataResult.Error("Server error")

        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        // Act
        viewModel.onConfirmAdd(
            scryfallId = "abc-001",
            isFoil     = false,
            condition  = "NM",
            language   = "en",
            quantity   = 1,
        )
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("Server error", state.error)
        assertFalse(state.showConfirmSheet)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — onDismissConfirmSheet
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given confirm sheet is shown when onDismissConfirmSheet then state resets to initial`() = runTest {
        // Arrange — navigate to confirm-sheet state
        coEvery { searchCards(any()) } returns DataResult.Success(listOf(sampleCard))
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showConfirmSheet)

        // Act
        viewModel.onDismissConfirmSheet()

        // Assert: full reset to defaults
        val state = viewModel.uiState.value
        assertNull(state.foundCard)
        assertFalse(state.showConfirmSheet)
        assertFalse(state.isSearching)
        assertNull(state.detectedName)
        assertNull(state.error)
        assertTrue(state.isScanning)
        assertFalse(state.addedSuccessfully)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — Utility actions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given addedSuccessfully is true when onSuccessDismissed then flag is cleared`() = runTest {
        // Arrange
        coEvery { searchCards(any()) } returns DataResult.Success(listOf(sampleCard))
        coEvery { addToCollection(scryfallId = any(), isFoil = any(), condition = any(), language = any()) } returns DataResult.Success(Unit)
        viewModel.onCardNameDetected("Lightning Bolt")
        advanceUntilIdle()
        viewModel.onConfirmAdd("abc-001", false, "NM", "en", 1)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.addedSuccessfully)

        // Act
        viewModel.onSuccessDismissed()

        // Assert
        assertFalse(viewModel.uiState.value.addedSuccessfully)
    }

    @Test
    fun `given error in state when onErrorDismissed then error is null`() = runTest {
        // Arrange
        coEvery { searchCards(any()) } throws RuntimeException("boom")
        viewModel.onCardNameDetected("Any Card")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        // Act
        viewModel.onErrorDismissed()

        // Assert
        assertNull(viewModel.uiState.value.error)
    }
}
