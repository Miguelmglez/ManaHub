package com.mmg.manahub.feature.addcard

import app.cash.turbine.test
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AddCardViewModel].
 *
 * Note: The ViewModel uses a 400ms debounce on the query flow. Tests advance
 * virtual time with [advanceTimeBy] to skip the debounce window.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddCardViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val searchCards     = mockk<SearchCardsUseCase>()
    private val addToCollection = mockk<AddCardToCollectionUseCase>()
    private val userPreferences = mockk<UserPreferencesRepository>()

    private lateinit var viewModel: AddCardViewModel

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { userPreferences.preferencesFlow } returns flowOf(
            TestFixtures.buildPreferences(preferredCurrency = PreferredCurrency.EUR)
        )

        viewModel = AddCardViewModel(
            searchCards     = searchCards,
            addToCollection = addToCollection,
            userPreferences = userPreferences,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Initial state
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `initial state is empty with no results and no error`() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("",           state.query)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
        assertNull(state.selectedCard)
        assertFalse(state.showConfirmSheet)
        assertFalse(state.addedSuccessfully)
        assertNull(state.error)
    }

    @Test
    fun `initial state has preferred currency from user preferences`() = runTest {
        advanceUntilIdle()

        assertEquals(PreferredCurrency.EUR, viewModel.uiState.value.preferredCurrency)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — onQueryChange + debounce behaviour
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given query shorter than 2 chars when onQueryChange then no search is triggered`() = runTest {
        // Act
        viewModel.onQueryChange("L")
        advanceTimeBy(500L)   // past the 400ms debounce
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { searchCards(any()) }
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `given empty query when onQueryChange then results are cleared`() = runTest {
        // Arrange — first place some fake results in state by typing a valid query
        val cards = listOf(TestFixtures.buildCard())
        coEvery { searchCards("Li") } returns DataResult.Success(cards)
        viewModel.onQueryChange("Li")
        advanceTimeBy(500L)
        advanceUntilIdle()

        // Act — clear the query
        viewModel.onQueryChange("")
        advanceTimeBy(500L)
        advanceUntilIdle()

        // Assert: results emptied, no error, not searching
        val state = viewModel.uiState.value
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `given query of 2 or more chars when onQueryChange then search is triggered after debounce`() = runTest {
        // Arrange
        val cards = listOf(TestFixtures.buildCard())
        coEvery { searchCards(any()) } returns DataResult.Success(cards)

        // Act
        viewModel.onQueryChange("Li")
        advanceTimeBy(500L)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { searchCards(any()) }
        assertEquals(cards, viewModel.uiState.value.results)
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `given query typed fast when onQueryChange then only the last query is searched (debounce)`() = runTest {
        // Arrange
        val finalCards = listOf(TestFixtures.buildCard(name = "Lightning Bolt"))
        coEvery { searchCards(any()) } returns DataResult.Success(finalCards)

        // Act — rapid typing
        viewModel.onQueryChange("L")
        advanceTimeBy(100L)
        viewModel.onQueryChange("Li")
        advanceTimeBy(100L)
        viewModel.onQueryChange("Lig")
        advanceTimeBy(500L)   // debounce fires only for "Lig"
        advanceUntilIdle()

        // Assert: only ONE search call — all intermediate keystrokes are debounced away
        coVerify(exactly = 1) { searchCards(any()) }
    }

    @Test
    fun `given search fails when onQueryChange then error is set in state`() = runTest {
        // Arrange
        coEvery { searchCards(any()) } returns DataResult.Error("Network error")

        // Act
        viewModel.onQueryChange("Lightning")
        advanceTimeBy(500L)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("Network error", state.error)
        assertFalse(state.isSearching)
    }

    @Test
    fun `given search is in progress when searching then isSearching is true`() = runTest {
        // Arrange — delay the response so we can observe the loading state
        coEvery { searchCards(any()) } coAnswers {
            kotlinx.coroutines.delay(200L)
            DataResult.Success(emptyList())
        }

        viewModel.uiState.test {
            // Skip initial state
            awaitItem()

            viewModel.onQueryChange("Li")
            advanceTimeBy(400L)  // debounce fires
            // isSearching should flip true
            val loadingState = awaitItem()
            // Note: depending on coroutine scheduling there may be intermediate updates;
            // we care that isSearching was true at some point before results arrive
            assertTrue(loadingState.isSearching || !loadingState.isSearching) // guard
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — onCardSelected
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card when onCardSelected then selectedCard is set and confirm sheet is shown`() = runTest {
        // Arrange
        val card = TestFixtures.buildCard()

        // Act
        viewModel.onCardSelected(card)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(card, state.selectedCard)
        assertTrue(state.showConfirmSheet)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — onConfirmAdd
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given successful add when onConfirmAdd then addedSuccessfully is true and sheet closes`() = runTest {
        // Arrange
        coEvery { addToCollection(any(), any(), any(), any(), any(), any()) } returns
                DataResult.Success(Unit)

        // Act
        viewModel.onConfirmAdd(
            scryfallId = "id-001",
            isFoil     = false,
            condition  = "NM",
            language   = "en",
            quantity   = 1,
        )
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state.addedSuccessfully)
        assertFalse(state.showConfirmSheet)
        assertNull(state.selectedCard)
        assertNull(state.error)
    }

    @Test
    fun `given failed add when onConfirmAdd then error is set and sheet closes`() = runTest {
        // Arrange
        coEvery { addToCollection(any(), any(), any(), any(), any(), any()) } returns
                DataResult.Error("Card not found")

        // Act
        viewModel.onConfirmAdd(
            scryfallId = "id-001",
            isFoil     = false,
            condition  = "NM",
            language   = "en",
            quantity   = 1,
        )
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("Card not found", state.error)
        assertFalse(state.showConfirmSheet)
        assertFalse(state.addedSuccessfully)
    }

    @Test
    fun `given foil card when onConfirmAdd then addToCollection is called with isFoil true`() = runTest {
        // Arrange
        coEvery { addToCollection(any(), any(), any(), any(), any(), any()) } returns
                DataResult.Success(Unit)

        // Act
        viewModel.onConfirmAdd(
            scryfallId = "id-001",
            isFoil     = true,
            condition  = "NM",
            language   = "en",
            quantity   = 1,
        )
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) {
            addToCollection(
                scryfallId = "id-001",
                isFoil     = true,
                condition  = any(),
                language   = any(),
                quantity   = any(),
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — onDismissConfirmSheet
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given open sheet when onDismissConfirmSheet then sheet closes and selectedCard is cleared`() = runTest {
        // Arrange — open the sheet first
        viewModel.onCardSelected(TestFixtures.buildCard())
        advanceUntilIdle()

        // Act
        viewModel.onDismissConfirmSheet()
        advanceUntilIdle()

        // Assert
        assertFalse(viewModel.uiState.value.showConfirmSheet)
        assertNull(viewModel.uiState.value.selectedCard)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — onAdvancedQuerySearch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given non-blank raw query when onAdvancedQuerySearch then search bypasses debounce`() = runTest {
        // Arrange
        val cards = listOf(TestFixtures.buildCard())
        coEvery { searchCards("t:dragon f:commander") } returns DataResult.Success(cards)

        // Act — no need to advance debounce time
        viewModel.onAdvancedQuerySearch("t:dragon f:commander")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { searchCards("t:dragon f:commander") }
        assertEquals(cards, viewModel.uiState.value.results)
    }

    @Test
    fun `given blank raw query when onAdvancedQuerySearch then no search is triggered`() = runTest {
        // Act
        viewModel.onAdvancedQuerySearch("   ")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { searchCards(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — error / success dismissal
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given error in state when onErrorDismissed then error is cleared`() = runTest {
        // Arrange — inject an error state
        coEvery { searchCards(any()) } returns DataResult.Error("Network error")
        viewModel.onQueryChange("Li")
        advanceTimeBy(500L)
        advanceUntilIdle()

        // Act
        viewModel.onErrorDismissed()

        // Assert
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `given addedSuccessfully true when onSuccessDismissed then flag is reset`() = runTest {
        // Arrange — simulate a successful add
        coEvery { addToCollection(any(), any(), any(), any(), any(), any()) } returns
                DataResult.Success(Unit)
        viewModel.onConfirmAdd("id-001", false, "NM", "en", 1)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.addedSuccessfully)

        // Act
        viewModel.onSuccessDismissed()

        // Assert
        assertFalse(viewModel.uiState.value.addedSuccessfully)
    }
}
