package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.NamedCount
import com.mmg.manahub.core.model.PaginatedCards
import com.mmg.manahub.core.model.TrendingSnapshot
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
 * Unit tests for [CommunityDecksSearchViewModel] (Community Hub Discover/Search overhaul,
 * 2026-07-15).
 *
 * Covers the deck-name search lifecycle (fresh / ByCard deep-link resolving to the CARD advanced
 * filter), advanced-filter mutation + active count, pagination (loadMore), one-shot Channel
 * events, feature flag, search cancellation, Discover's parallel multi-section load with
 * independent per-section degradation, and Discover-term→advanced-filter click mapping.
 *
 * The ViewModel calls [FirebaseCrashlytics.getInstance] in its `init` block, so a static mock is
 * mandatory to avoid "Default FirebaseApp is not initialized".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityDecksSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val searchUseCase: SearchCommunityDecksUseCase = mockk()
    private val userPreferences: UserPreferencesDataStore = mockk()
    private val communityAggregateRepository: CommunityAggregateRepository = mockk(relaxed = true)
    private val communityEngineEnabledFlow = MutableStateFlow(false)
    private val cardRepository: CardRepository = mockk()
    private val searchCards: SearchCardsUseCase = mockk()

    // ── Fixtures ────────────────────────────────────────────────────────────

    private fun fakeCard(name: String, id: String = name) = Card(
        scryfallId = id, name = name, printedName = null,
        manaCost = "{2}{U}", cmc = 3.0, colors = listOf("U"), colorIdentity = listOf("U"),
        typeLine = "Creature", printedTypeLine = null, oracleText = null, printedText = null,
        keywords = emptyList(), power = "1", toughness = "1", loyalty = null,
        setCode = "TST", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2025-01-01", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal", legalityModern = "legal",
        legalityCommander = "legal", flavorText = null, artist = null,
        scryfallUri = "https://scryfall.com/$id",
    )

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

        every { userPreferences.communityEngineEnabledFlow } returns communityEngineEnabledFlow
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
        return CommunityDecksSearchViewModel(
            handle, searchUseCase, userPreferences, communityAggregateRepository, cardRepository, searchCards,
        )
    }

    // ── Group 1: Initial state (no cardName) ────────────────────────────────

    @Test
    fun `given no cardName when created then query is empty and hasSearched is false`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("", state.query)
        assertFalse(state.hasSearched)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `given no cardName when created then search is not triggered`() = runTest {
        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any()) }
    }

    // ── Group 2: ByCard deep-link resolves into the CARD advanced filter ────

    @Test
    fun `given resolvable cardName when created then card filter is set and search auto-triggers`() = runTest {
        val solRing = fakeCard("Sol Ring")
        coEvery { cardRepository.getCardByExactName("Sol Ring") } returns Result.success(solRing)
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)

        val vm = createViewModel(cardName = "Sol Ring")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(solRing), state.advancedFilters.cards)
        assertEquals("", state.query)
        assertTrue(state.hasSearched)
        assertEquals(1, state.results.size)
        coVerify(exactly = 1) {
            searchUseCase(match { it.cardNames == listOf("Sol Ring") })
        }
    }

    @Test
    fun `given unresolvable cardName when created then falls back to the deckName query bar`() = runTest {
        coEvery { cardRepository.getCardByExactName("Unknown Card") } returns Result.failure(RuntimeException("not found"))
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)

        val vm = createViewModel(cardName = "Unknown Card")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.advancedFilters.cards.isEmpty())
        assertEquals("Unknown Card", state.query)
        assertTrue(state.hasSearched)
        coVerify(exactly = 1) {
            searchUseCase(match { it.deckName == "Unknown Card" })
        }
    }

    // ── Group 3: onQueryChange never clears existing results ────────────────

    @Test
    fun `given any state when onQueryChange then only the query text changes`() = runTest {
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.results.size)

        // Act — edit the field again after results are already showing.
        vm.onQueryChange("Sol Ring II")

        // Assert — results/hasSearched are untouched by a query edit alone.
        val state = vm.uiState.value
        assertEquals("Sol Ring II", state.query)
        assertEquals(1, state.results.size)
        assertTrue(state.hasSearched)
    }

    // ── Group 4: search() behavior ───────────────────────────────────────────

    @Test
    fun `given blank query and no active filters when search then does nothing`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("   ")

        vm.search()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.hasSearched)
        coVerify(exactly = 0) { searchUseCase(any()) }
    }

    @Test
    fun `given blank query but an active advanced filter when search then still searches`() = runTest {
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFormatFilterSelected(CommunityDeckFormatFilter.COMMANDER)

        vm.search()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasSearched)
        coVerify(exactly = 1) { searchUseCase(match { it.deckFormatId == 3 }) }
    }

    @Test
    fun `given valid deckName when search then sets hasSearched and populates results`() = runTest {
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")

        vm.search()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.hasSearched)
        assertFalse(state.isLoading)
        assertEquals(1, state.results.size)
        assertEquals(1, state.totalCount)
        assertFalse(state.hasMore)
        assertNull(state.error)
        coVerify(exactly = 1) { searchUseCase(match { it.deckName == "Sol Ring" }) }
    }

    @Test
    fun `given search error when search then sets error in state`() = runTest {
        coEvery { searchUseCase(any()) } returns DataResult.Error("Search failed: 500")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")

        vm.search()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Search failed: 500", state.error)
    }

    // ── Group 5: Sort re-triggers search ─────────────────────────────────────

    @Test
    fun `given hasSearched when onSortSelected then search is re-triggered`() = runTest {
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        vm.onSortSelected(CommunityDeckSort.RECENT)
        advanceUntilIdle()

        coVerify(atLeast = 2) { searchUseCase(any()) }
        assertEquals(CommunityDeckSort.RECENT, vm.uiState.value.selectedSort)
    }

    @Test
    fun `given not searched yet when onSortSelected then search is not triggered`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSortSelected(CommunityDeckSort.RECENT)
        advanceUntilIdle()

        assertEquals(CommunityDeckSort.RECENT, vm.uiState.value.selectedSort)
        coVerify(exactly = 0) { searchUseCase(any()) }
    }

    // ── Group 6: loadMore ────────────────────────────────────────────────────

    @Test
    fun `given hasMore when loadMore then appends results and increments page`() = runTest {
        val page1Result = buildSearchResult(totalCount = 2, hasMore = true)
        val page2Summary = testSummary.copy(archidektId = 200, name = "Second Deck")
        val page2Result = buildSearchResult(totalCount = 2, hasMore = false, decks = listOf(page2Summary))

        coEvery { searchUseCase(match { it.page == 1 }) } returns DataResult.Success(page1Result)
        coEvery { searchUseCase(match { it.page == 2 }) } returns DataResult.Success(page2Result)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.results.size)
        assertEquals("Test Deck", state.results[0].name)
        assertEquals("Second Deck", state.results[1].name)
        assertFalse(state.hasMore)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `given hasMore is false when loadMore then does nothing`() = runTest {
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase(any()) }
    }

    @Test
    fun `given loadMore error then emits ShowError event`() = runTest {
        val page1Result = buildSearchResult(totalCount = 10, hasMore = true)
        coEvery { searchUseCase(match { it.page == 1 }) } returns DataResult.Success(page1Result)
        coEvery { searchUseCase(match { it.page == 2 }) } returns DataResult.Error("Connection lost")

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()

        vm.events.test {
            vm.loadMore()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is CommunityDecksSearchEvent.ShowError)
            assertEquals("Connection lost", (event as CommunityDecksSearchEvent.ShowError).message)

            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    // ── Group 7: onDeckClick ──────────────────────────────────────────────────

    @Test
    fun `given any state when onDeckClick then emits NavigateToDeck event`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onDeckClick(42)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is CommunityDecksSearchEvent.NavigateToDeck)
            assertEquals(42, (event as CommunityDecksSearchEvent.NavigateToDeck).archidektId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 9: Search cancellation ──────────────────────────────────────────

    @Test
    fun `given ongoing search when new search is triggered then previous is cancelled`() = runTest {
        coEvery {
            searchUseCase(match { it.deckName == "First" })
        } coAnswers {
            kotlinx.coroutines.delay(10_000)
            DataResult.Success(testSearchResult)
        }
        coEvery {
            searchUseCase(match { it.deckName == "Second" })
        } returns DataResult.Success(buildSearchResult(totalCount = 99, decks = listOf(testSummary.copy(name = "Second Result"))))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onQueryChange("First")
        vm.search()

        vm.onQueryChange("Second")
        vm.search()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Second Result", state.results.first().name)
        assertEquals(99, state.totalCount)
    }

    // ── Group 10: Advanced filters — mutation + active count ─────────────────

    @Test
    fun `given no filters applied then activeCount is zero`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.advancedFilters.activeCount)
    }

    @Test
    fun `given several filters applied then activeCount reflects each distinct filter`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onFormatFilterSelected(CommunityDeckFormatFilter.COMMANDER)
        vm.onColorToggled("U")
        vm.onColorToggled("W")
        vm.onBracketSelected(3)
        vm.onUsernameChanged("archmage")
        vm.onDeckSizeChanged("100")
        vm.onPrimersOnlyToggled(true)

        val filters = vm.uiState.value.advancedFilters
        assertEquals(CommunityDeckFormatFilter.COMMANDER, filters.format)
        assertEquals(setOf("U", "W"), filters.colors)
        assertEquals(3, filters.edhBracket)
        assertEquals("archmage", filters.ownerUsername)
        assertEquals("100", filters.deckSize)
        assertTrue(filters.primersOnly)
        // format + colors + bracket + username + size + primers = 6 distinct active filters.
        assertEquals(6, filters.activeCount)
    }

    @Test
    fun `given active filters when onClearAdvancedFilters then everything resets`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFormatFilterSelected(CommunityDeckFormatFilter.COMMANDER)
        vm.onUsernameChanged("archmage")

        vm.onClearAdvancedFilters()

        assertEquals(0, vm.uiState.value.advancedFilters.activeCount)
        assertEquals(CommunityDeckFormatFilter.ALL, vm.uiState.value.advancedFilters.format)
        assertEquals("", vm.uiState.value.advancedFilters.ownerUsername)
    }

    // ── Group 11: Commander/Card pickers (debounced Scryfall search) ─────────

    @Test
    fun `given a 2+ char commander query when debounce elapses then results are populated`() = runTest {
        val atraxa = fakeCard("Atraxa, Praetors' Voice")
        coEvery { searchCards("Atr", 1) } returns DataResult.Success(PaginatedCards(cards = listOf(atraxa), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCommanderQueryChange("Atr")
        advanceUntilIdle()

        assertEquals(listOf(atraxa), vm.uiState.value.commanderResults)
        assertFalse(vm.uiState.value.isCommanderSearching)
    }

    @Test
    fun `given a commander selection then the commander filter is set and the picker resets`() = runTest {
        val atraxa = fakeCard("Atraxa, Praetors' Voice")
        coEvery { searchCards("Atr", 1) } returns DataResult.Success(PaginatedCards(cards = listOf(atraxa), hasMore = false))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onCommanderQueryChange("Atr")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.commanderResults.isNotEmpty())

        vm.onCommanderSelected(atraxa)

        val state = vm.uiState.value
        assertEquals(atraxa, state.advancedFilters.commander)
        assertEquals("", state.commanderQuery)
        assertTrue(state.commanderResults.isEmpty())
    }

    @Test
    fun `given a selected commander when onCommanderCleared then the filter is removed`() = runTest {
        val atraxa = fakeCard("Atraxa, Praetors' Voice")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onCommanderSelected(atraxa)

        vm.onCommanderCleared()

        assertNull(vm.uiState.value.advancedFilters.commander)
    }

    // ── Group 12: Discover — parallel section load + degradation ─────────────

    private fun enableDiscoverAndCreate(): CommunityDecksSearchViewModel {
        communityEngineEnabledFlow.value = true
        val vm = createViewModel()
        return vm
    }

    @Test
    fun `given all discover sources succeed then every section is populated`() = runTest {
        val atraxa = fakeCard("Atraxa, Praetors' Voice", id = "atraxa")
        val solRing = fakeCard("Sol Ring", id = "sol-ring")
        coEvery { communityAggregateRepository.getTrending() } returns DataResult.Success(
            TrendingSnapshot(week = "2026-W29", topCommanders = listOf(NamedCount("Atraxa, Praetors' Voice", 10)), topCards = listOf(NamedCount("Sol Ring", 20))),
        )
        coEvery { cardRepository.getCardByExactName("Atraxa, Praetors' Voice") } returns Result.success(atraxa)
        coEvery { cardRepository.getCardByExactName("Sol Ring") } returns Result.success(solRing)
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)

        val vm = enableDiscoverAndCreate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isDiscoverLoading)
        assertFalse(state.discoverUnavailable)
        assertEquals(listOf(atraxa), state.trendingCommanderCards)
        assertEquals(listOf(solRing), state.trendingCardCards)
        assertTrue(state.popularDecks.isNotEmpty())
        assertTrue(state.recentDecks.isNotEmpty())
        assertTrue(state.updatedDecks.isNotEmpty())
        assertTrue(state.primerDecks.isNotEmpty())
        assertTrue(state.featuredFormatDecks.isNotEmpty())
    }

    @Test
    fun `given trending fails but deck sections succeed then discover is NOT unavailable`() = runTest {
        coEvery { communityAggregateRepository.getTrending() } returns DataResult.Error("boom")
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)

        val vm = enableDiscoverAndCreate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.discoverUnavailable)
        assertTrue(state.trendingCommanderCards.isEmpty())
        assertTrue(state.trendingCardCards.isEmpty())
        assertTrue(state.popularDecks.isNotEmpty())
    }

    @Test
    fun `given every discover source fails then discoverUnavailable is true`() = runTest {
        coEvery { communityAggregateRepository.getTrending() } returns DataResult.Error("boom")
        coEvery { searchUseCase(any()) } returns DataResult.Error("boom")

        val vm = enableDiscoverAndCreate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.discoverUnavailable)
        assertTrue(state.popularDecks.isEmpty())
        assertTrue(state.recentDecks.isEmpty())
    }

    // ── Group 13: Discover term click → advanced filter mapping ──────────────

    @Test
    fun `given a trending commander tile tap then the commander filter is set and search runs`() = runTest {
        val atraxa = fakeCard("Atraxa, Praetors' Voice")
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onTrendingCommanderClick(atraxa)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CommunityHubTab.SEARCH, state.hubTab)
        assertEquals(atraxa, state.advancedFilters.commander)
        coVerify { searchUseCase(match { it.commanderName == "Atraxa, Praetors' Voice" }) }
    }

    @Test
    fun `given a trending card tile tap then the card filter is set and search runs`() = runTest {
        val solRing = fakeCard("Sol Ring")
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onTrendingCardClick(solRing)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CommunityHubTab.SEARCH, state.hubTab)
        assertEquals(listOf(solRing), state.advancedFilters.cards)
        coVerify { searchUseCase(match { it.cardNames == listOf("Sol Ring") }) }
    }

    // ── Group 14: Card advanced filter — add/remove/cap/dedupe (Archidekt multi-card search
    //    expansion, 2026-07-24) ─────────────────────────────────────────────

    @Test
    fun `given no cards selected when onCardFilterSelected then the card is added`() = runTest {
        val solRing = fakeCard("Sol Ring")
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCardFilterSelected(solRing)

        assertEquals(listOf(solRing), vm.uiState.value.advancedFilters.cards)
    }

    @Test
    fun `given one card selected when onCardFilterSelected with a different card then both are kept in selection order`() = runTest {
        val solRing = fakeCard("Sol Ring")
        val lightningBolt = fakeCard("Lightning Bolt")
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCardFilterSelected(solRing)
        vm.onCardFilterSelected(lightningBolt)

        assertEquals(listOf(solRing, lightningBolt), vm.uiState.value.advancedFilters.cards)
    }

    @Test
    fun `given a card already selected when onCardFilterSelected with the same name then it is not duplicated`() = runTest {
        val solRing = fakeCard("Sol Ring")
        val solRingDuplicateId = fakeCard("Sol Ring", id = "other-printing")
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCardFilterSelected(solRing)
        vm.onCardFilterSelected(solRingDuplicateId)

        assertEquals(listOf(solRing), vm.uiState.value.advancedFilters.cards)
    }

    @Test
    fun `given three cards already selected when onCardFilterSelected with a fourth then it is silently ignored`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        val cards = (1..3).map { fakeCard("Card $it") }
        cards.forEach { vm.onCardFilterSelected(it) }

        vm.onCardFilterSelected(fakeCard("Card 4"))

        assertEquals(cards, vm.uiState.value.advancedFilters.cards)
    }

    @Test
    fun `given cards selected when onCardFilterSelected then the card picker query and results reset`() = runTest {
        val solRing = fakeCard("Sol Ring")
        coEvery { searchCards("Sol", 1) } returns DataResult.Success(PaginatedCards(cards = listOf(solRing), hasMore = false))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onCardQueryChange("Sol")
        advanceUntilIdle()

        vm.onCardFilterSelected(solRing)

        assertEquals("", vm.uiState.value.cardQuery)
        assertTrue(vm.uiState.value.cardResults.isEmpty())
    }

    @Test
    fun `given two cards selected when onCardFilterRemoved with one of them then only that card is removed`() = runTest {
        val solRing = fakeCard("Sol Ring")
        val lightningBolt = fakeCard("Lightning Bolt")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onCardFilterSelected(solRing)
        vm.onCardFilterSelected(lightningBolt)

        vm.onCardFilterRemoved(solRing)

        assertEquals(listOf(lightningBolt), vm.uiState.value.advancedFilters.cards)
    }

    // ── Group 15: activeCount + toSearchFilters — each card counts individually ─────

    @Test
    fun `given two selected cards when reading activeCount then each card contributes one`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCardFilterSelected(fakeCard("Sol Ring"))
        vm.onCardFilterSelected(fakeCard("Lightning Bolt"))

        assertEquals(2, vm.uiState.value.advancedFilters.activeCount)
    }

    @Test
    fun `given cards plus another filter when reading activeCount then both contribute additively`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCardFilterSelected(fakeCard("Sol Ring"))
        vm.onCardFilterSelected(fakeCard("Lightning Bolt"))
        vm.onFormatFilterSelected(CommunityDeckFormatFilter.COMMANDER)

        // 2 cards + 1 format = 3.
        assertEquals(3, vm.uiState.value.advancedFilters.activeCount)
    }

    @Test
    fun `given multiple cards selected when search then cardNames preserves selection order`() = runTest {
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onCardFilterSelected(fakeCard("Sol Ring"))
        vm.onCardFilterSelected(fakeCard("Lightning Bolt"))

        vm.search()
        advanceUntilIdle()

        coVerify { searchUseCase(match { it.cardNames == listOf("Sol Ring", "Lightning Bolt") }) }
    }

    // ── Group 16: deep-link / trending-card tap — replace, never append ─────────────

    @Test
    fun `given a card already selected when a ByCard deep-link resolves then it replaces the existing selection`() = runTest {
        val solRing = fakeCard("Sol Ring")
        val lightningBolt = fakeCard("Lightning Bolt")
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } returns Result.success(lightningBolt)
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)

        val vm = createViewModel(cardName = "Lightning Bolt")
        // Pre-seed a selection before the init block's deep-link resolution would normally run in
        // isolation; since resolution is async, select first, then let it settle.
        vm.onCardFilterSelected(solRing)
        advanceUntilIdle()

        assertEquals(listOf(lightningBolt), vm.uiState.value.advancedFilters.cards)
    }

    @Test
    fun `given cards already selected when a trending card tile is tapped then it replaces the existing selection`() = runTest {
        val solRing = fakeCard("Sol Ring")
        val lightningBolt = fakeCard("Lightning Bolt")
        coEvery { searchUseCase(any()) } returns DataResult.Success(testSearchResult)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onCardFilterSelected(solRing)

        vm.onTrendingCardClick(lightningBolt)
        advanceUntilIdle()

        assertEquals(listOf(lightningBolt), vm.uiState.value.advancedFilters.cards)
    }

    // ── Group 17: loadMore is a no-op on a multi-card (hasMore = false) result ──────

    @Test
    fun `given a multi-card search result with hasMore false when loadMore then it does nothing`() = runTest {
        coEvery { searchUseCase(any()) } returns DataResult.Success(
            buildSearchResult(totalCount = 2, hasMore = false),
        )
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onCardFilterSelected(fakeCard("Sol Ring"))
        vm.onCardFilterSelected(fakeCard("Lightning Bolt"))
        vm.search()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        // Exactly the one search() call — loadMore never issued a second one.
        coVerify(exactly = 1) { searchUseCase(any()) }
    }
}
