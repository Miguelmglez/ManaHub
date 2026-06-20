package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckOwner
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckSearchResult
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckSummary
import com.mmg.manahub.feature.communitydecks.domain.usecase.SearchCommunityDecksUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CommunityDecksSearchViewModel].
 *
 * Covers the search lifecycle (fresh / pre-filled), format & sort filter
 * re-triggers, pagination (loadMore), one-shot Channel events, feature flag,
 * and search cancellation.
 *
 * The ViewModel calls [FirebaseCrashlytics.getInstance] in its `init` block,
 * so a static mock is mandatory to avoid "Default FirebaseApp is not initialized".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityDecksSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val searchUseCase: SearchCommunityDecksUseCase = mockk()
    private val userPreferences: UserPreferencesDataStore = mockk()
    private val featureFlagFlow = MutableStateFlow(true)

    // ── Fixtures ────────────────────────────────────────────────────────────

    private val testSummary = CommunityDeckSummary(
        archidektId = 100,
        name = "Test Deck",
        size = 100,
        format = "commander",
        owner = CommunityDeckOwner(id = 1, username = "user", avatarUrl = ""),
        viewCount = 500,
        createdAt = "2024-01-01",
        updatedAt = "2024-06-01",
        colorIdentity = listOf("W", "U"),
    )

    private val testSearchResult = CommunityDeckSearchResult(
        totalCount = 1,
        hasMore = false,
        decks = listOf(testSummary),
    )

    private fun buildSearchResult(
        totalCount: Int = 1,
        hasMore: Boolean = false,
        decks: List<CommunityDeckSummary> = listOf(testSummary),
    ) = CommunityDeckSearchResult(totalCount = totalCount, hasMore = hasMore, decks = decks)

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        every { userPreferences.communityDecksEnabledFlow } returns featureFlagFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    /** Creates a ViewModel with an optional pre-filled cardName in SavedStateHandle. */
    private fun createViewModel(cardName: String? = null): CommunityDecksSearchViewModel {
        val handle = SavedStateHandle().apply {
            if (cardName != null) set("cardName", cardName)
        }
        return CommunityDecksSearchViewModel(handle, searchUseCase, userPreferences)
    }

    // ── Group 1: Initial state (no cardName) ────────────────────────────────

    @Test
    fun `given no cardName when created then query is empty and hasSearched is false`() = runTest {
        // Arrange & Act
        val vm = createViewModel()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertEquals("", state.query)
        assertFalse(state.hasSearched)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `given no cardName when created then search is not triggered`() = runTest {
        // Arrange & Act
        createViewModel()
        advanceUntilIdle()

        // Assert — no search call on init without a cardName.
        coVerify(exactly = 0) { searchUseCase(any(), any(), any(), any(), any()) }
    }

    // ── Group 2: Initial state with cardName ────────────────────────────────

    @Test
    fun `given cardName when created then query is pre-filled and search auto-triggers`() = runTest {
        // Arrange
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        // Act
        val vm = createViewModel(cardName = "Sol Ring")
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertEquals("Sol Ring", state.query)
        assertTrue(state.hasSearched)
        assertEquals(1, state.results.size)
        assertEquals("Test Deck", state.results.first().name)
        assertFalse(state.isLoading)
    }

    @Test
    fun `given cardName when created then search is called with card name`() = runTest {
        // Arrange
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        // Act
        createViewModel(cardName = "Sol Ring")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) {
            searchUseCase(cardName = "Sol Ring", deckFormat = any(), orderBy = any(), page = 1, pageSize = any())
        }
    }

    // ── Group 3: onQueryChange ──────────────────────────────────────────────

    @Test
    fun `given any state when onQueryChange then query is updated in state`() = runTest {
        // Arrange
        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.onQueryChange("Lightning Bolt")

        // Assert
        assertEquals("Lightning Bolt", vm.uiState.value.query)
    }

    // ── Group 4: search behavior ────────────────────────────────────────────

    @Test
    fun `given blank query when search then does nothing`() = runTest {
        // Arrange
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("   ")

        // Act
        vm.search()
        advanceUntilIdle()

        // Assert — no call and isLoading stays false.
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.hasSearched)
        coVerify(exactly = 0) { searchUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given valid query when search then sets hasSearched and populates results`() = runTest {
        // Arrange
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")

        // Act
        vm.search()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertTrue(state.hasSearched)
        assertFalse(state.isLoading)
        assertEquals(1, state.results.size)
        assertEquals(1, state.totalCount)
        assertFalse(state.hasMore)
        assertNull(state.error)
    }

    @Test
    fun `given search error when search then sets error in state`() = runTest {
        // Arrange
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Error("Search failed: 500")

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")

        // Act
        vm.search()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Search failed: 500", state.error)
    }

    @Test
    fun `given search timeout error when search then error contains timeout text`() = runTest {
        // Arrange
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Error("Search timed out. Try a more specific query or remove the format filter.")

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Lightning Bolt")

        // Act
        vm.search()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertTrue(state.error!!.contains("timed out", ignoreCase = true))
    }

    // ── Group 5: Format filter re-triggers search ───────────────────────────

    @Test
    fun `given hasSearched when onFormatSelected then search is re-triggered`() = runTest {
        // Arrange — first search to set hasSearched = true.
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        // Act — change format after a search was done.
        vm.onFormatSelected(CommunityDeckFormatFilter.COMMANDER)
        advanceUntilIdle()

        // Assert — search is called a second time (once for initial + once after format change).
        coVerify(atLeast = 2) {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        }
        assertEquals(CommunityDeckFormatFilter.COMMANDER, vm.uiState.value.selectedFormat)
    }

    @Test
    fun `given not searched yet when onFormatSelected then search is not triggered`() = runTest {
        // Arrange
        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.onFormatSelected(CommunityDeckFormatFilter.COMMANDER)
        advanceUntilIdle()

        // Assert — format changes but no search runs.
        assertEquals(CommunityDeckFormatFilter.COMMANDER, vm.uiState.value.selectedFormat)
        coVerify(exactly = 0) { searchUseCase(any(), any(), any(), any(), any()) }
    }

    // ── Group 6: Sort re-triggers search ────────────────────────────────────

    @Test
    fun `given hasSearched when onSortSelected then search is re-triggered`() = runTest {
        // Arrange — first search to set hasSearched = true.
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        // Act
        vm.onSortSelected(CommunityDeckSort.RECENT)
        advanceUntilIdle()

        // Assert
        coVerify(atLeast = 2) {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        }
        assertEquals(CommunityDeckSort.RECENT, vm.uiState.value.selectedSort)
    }

    @Test
    fun `given not searched yet when onSortSelected then search is not triggered`() = runTest {
        // Arrange
        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.onSortSelected(CommunityDeckSort.RECENT)
        advanceUntilIdle()

        // Assert
        assertEquals(CommunityDeckSort.RECENT, vm.uiState.value.selectedSort)
        coVerify(exactly = 0) { searchUseCase(any(), any(), any(), any(), any()) }
    }

    // ── Group 7: loadMore — success ─────────────────────────────────────────

    @Test
    fun `given hasMore when loadMore then appends results and increments page`() = runTest {
        // Arrange — initial search with hasMore = true.
        val page1Result = buildSearchResult(totalCount = 2, hasMore = true)
        val page2Summary = testSummary.copy(archidektId = 200, name = "Second Deck")
        val page2Result = buildSearchResult(totalCount = 2, hasMore = false, decks = listOf(page2Summary))

        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = 1, pageSize = any())
        } returns DataResult.Success(page1Result)
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = 2, pageSize = any())
        } returns DataResult.Success(page2Result)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        // Act
        vm.loadMore()
        advanceUntilIdle()

        // Assert — results contain both pages.
        val state = vm.uiState.value
        assertEquals(2, state.results.size)
        assertEquals("Test Deck", state.results[0].name)
        assertEquals("Second Deck", state.results[1].name)
        assertFalse(state.hasMore)
        assertFalse(state.isLoadingMore)
    }

    // ── Group 8: loadMore — guards ──────────────────────────────────────────

    @Test
    fun `given hasMore is false when loadMore then does nothing`() = runTest {
        // Arrange — search with hasMore = false.
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        // Act
        vm.loadMore()
        advanceUntilIdle()

        // Assert — no second call (page 2 was never requested).
        coVerify(exactly = 1) { searchUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given loadMore error then emits ShowError event`() = runTest {
        // Arrange — initial search succeeds with hasMore = true.
        val page1Result = buildSearchResult(totalCount = 10, hasMore = true)
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = 1, pageSize = any())
        } returns DataResult.Success(page1Result)
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = 2, pageSize = any())
        } returns DataResult.Error("Connection lost")

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        // Act & Assert — collect events via Turbine.
        vm.events.test {
            vm.loadMore()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is CommunityDecksSearchEvent.ShowError)
            assertEquals("Connection lost", (event as CommunityDecksSearchEvent.ShowError).message)

            cancelAndIgnoreRemainingEvents()
        }

        // Assert — isLoadingMore is reset.
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    // ── Group 9: onDeckClick ────────────────────────────────────────────────

    @Test
    fun `given any state when onDeckClick then emits NavigateToDeck event`() = runTest {
        // Arrange
        val vm = createViewModel()
        advanceUntilIdle()

        // Act & Assert
        vm.events.test {
            vm.onDeckClick(42)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is CommunityDecksSearchEvent.NavigateToDeck)
            assertEquals(42, (event as CommunityDecksSearchEvent.NavigateToDeck).archidektId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 10: Feature flag ──────────────────────────────────────────────

    @Test
    fun `given feature flag enabled then isFeatureEnabled emits true`() = runTest {
        // Arrange
        featureFlagFlow.value = true
        val vm = createViewModel()

        // Act & Assert — Turbine subscribes (satisfies WhileSubscribed), then advance
        // so the upstream emission propagates through stateIn.
        vm.isFeatureEnabled.test {
            // Initial value from stateIn is false; the upstream `true` arrives next.
            advanceUntilIdle()
            // Consume items until we get the expected value (initial false may or may not appear).
            val latest = expectMostRecentItem()
            assertEquals(true, latest)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given feature flag disabled then isFeatureEnabled emits false`() = runTest {
        // Arrange
        featureFlagFlow.value = false
        val vm = createViewModel()

        // Act & Assert
        vm.isFeatureEnabled.test {
            advanceUntilIdle()
            val latest = expectMostRecentItem()
            assertEquals(false, latest)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 11: Search cancellation ───────────────────────────────────────

    @Test
    fun `given ongoing search when new search is triggered then previous is cancelled`() = runTest {
        // Arrange — first search is slow (will be cancelled), second returns immediately.
        var firstCallCount = 0
        coEvery {
            searchUseCase(cardName = "First", deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } coAnswers {
            firstCallCount++
            // Simulate a slow response — the coroutine will be cancelled before we reach here.
            kotlinx.coroutines.delay(10_000)
            DataResult.Success(testSearchResult)
        }
        coEvery {
            searchUseCase(cardName = "Second", deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(buildSearchResult(totalCount = 99, decks = listOf(testSummary.copy(name = "Second Result"))))

        val vm = createViewModel()
        advanceUntilIdle()

        // Act — start first search, then immediately start second.
        vm.onQueryChange("First")
        vm.search()
        // Don't advance — the first search is "suspended".

        vm.onQueryChange("Second")
        vm.search()
        advanceUntilIdle()

        // Assert — the final result should be from the second search.
        val state = vm.uiState.value
        assertEquals("Second Result", state.results.first().name)
        assertEquals(99, state.totalCount)
    }

    // ── Group 12: Format filter passes correct apiId ────────────────────────

    @Test
    fun `given commander format filter when search then passes apiId 3`() = runTest {
        // Arrange
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.onFormatSelected(CommunityDeckFormatFilter.COMMANDER)

        // Act
        vm.search()
        advanceUntilIdle()

        // Assert — format apiId 3 (commander) passed through.
        coVerify {
            searchUseCase(cardName = "Sol Ring", deckFormat = 3, orderBy = any(), page = 1, pageSize = any())
        }
    }

    @Test
    fun `given ALL format filter when search then passes null deckFormat`() = runTest {
        // Arrange
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.onFormatSelected(CommunityDeckFormatFilter.ALL)

        // Act
        vm.search()
        advanceUntilIdle()

        // Assert — ALL filter passes null (no filter).
        coVerify {
            searchUseCase(cardName = "Sol Ring", deckFormat = null, orderBy = any(), page = 1, pageSize = any())
        }
    }

    // ── Group 13: Sort passes correct apiValue ──────────────────────────────

    @Test
    fun `given RECENT sort when search then passes orderBy createdAt desc`() = runTest {
        // Arrange
        coEvery {
            searchUseCase(cardName = any(), deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(testSearchResult)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.onSortSelected(CommunityDeckSort.RECENT)

        // Act
        vm.search()
        advanceUntilIdle()

        // Assert
        coVerify {
            searchUseCase(cardName = "Sol Ring", deckFormat = any(), orderBy = "-createdAt", page = 1, pageSize = any())
        }
    }

    // ── Group 14: Search resets results from prior page ─────────────────────

    @Test
    fun `given previous results when new search then results are replaced not appended`() = runTest {
        // Arrange — first search returns one set of results.
        val firstResult = buildSearchResult(totalCount = 1, decks = listOf(testSummary))
        val secondSummary = testSummary.copy(archidektId = 999, name = "Completely Different")
        val secondResult = buildSearchResult(totalCount = 1, decks = listOf(secondSummary))

        coEvery {
            searchUseCase(cardName = "First", deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(firstResult)
        coEvery {
            searchUseCase(cardName = "Second", deckFormat = any(), orderBy = any(), page = any(), pageSize = any())
        } returns DataResult.Success(secondResult)

        val vm = createViewModel()
        advanceUntilIdle()

        // Act — perform first search, then second.
        vm.onQueryChange("First")
        vm.search()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.results.size)
        assertEquals("Test Deck", vm.uiState.value.results.first().name)

        vm.onQueryChange("Second")
        vm.search()
        advanceUntilIdle()

        // Assert — only second results remain (page reset to 1).
        val state = vm.uiState.value
        assertEquals(1, state.results.size)
        assertEquals("Completely Different", state.results.first().name)
    }
}
