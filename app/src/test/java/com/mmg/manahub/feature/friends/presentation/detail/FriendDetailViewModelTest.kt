package com.mmg.manahub.feature.friends.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardPage
import com.mmg.manahub.core.model.FriendCardSearchException
import com.mmg.manahub.core.model.FriendCardSearchParams
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.feature.friends.domain.usecase.SearchFriendCardsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class FriendDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val friendRepo = mockk<FriendRepository>(relaxed = true)
    private val tradesRepo = mockk<TradesRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val searchUseCase = mockk<SearchFriendCardsUseCase>()

    private lateinit var viewModel: FriendDetailViewModel

    private fun card(id: String) = FriendCard(
        sourceList = "collection", scryfallId = id, name = "Card $id", imageNormal = null,
        imageArtCrop = null, setCode = "tst", setName = null, rarity = "rare", priceEur = null,
        priceUsd = null, priceEurFoil = null, priceUsdFoil = null, quantity = 1, isFoil = false,
        isStale = false, condition = null, language = null, rowId = "row-$id",
    )

    private fun page(
        vararg ids: String,
        cursor: FriendCardCursor? = null,
        unindexed: Int = 0,
        unresolved: Set<String> = emptySet(),
    ) = Result.success(FriendCardPage(ids.map(::card), cursor, hasMore = cursor != null, unindexed, unresolved))

    private fun stubSearch(result: Result<FriendCardPage>) {
        coEvery { searchUseCase(any(), any(), any(), any(), any()) } returns result
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepo.sessionState } returns MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        every { friendRepo.observeFriends() } returns flowOf(emptyList())
        every { tradesRepo.observeAllProposals() } returns flowOf(emptyList())
        stubSearch(page("a"))
        coEvery { searchUseCase.unindexedCount(any(), any()) } returns Result.success(0)
        coEvery { searchUseCase.hydrateMetadata(any()) } returns emptyMap()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(timeSource: TestTimeSource = TestTimeSource()): FriendDetailViewModel {
        viewModel = FriendDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("userId" to "friend-1")),
            friendRepo = friendRepo,
            searchFriendCards = searchUseCase,
            tradesRepo = tradesRepo,
            authRepo = authRepo,
            crashReporter = crashReporter,
            timeSource = timeSource,
        )
        advanceUntilIdle()
        return viewModel
    }

    private fun verifyNameCalls(name: String?, times: Int) {
        coVerify(exactly = times) { searchUseCase(any(), any(), match { it.name == name }, any(), any()) }
    }

    @Test
    fun `initial load browses the collection once with empty params`() = runTest(dispatcher) {
        createViewModel()

        coVerify(exactly = 1) { searchUseCase("friend-1", "collection", FriendCardSearchParams(), null, 50) }
        assertEquals(listOf("a"), viewModel.uiState.value.cards.map { it.scryfallId })
        assertFalse(viewModel.uiState.value.resultsFiltered)
    }

    @Test
    fun `rapid typing is coalesced into a single request after the debounce`() = runTest(dispatcher) {
        createViewModel()

        listOf("bo", "bol", "bolt").forEach {
            viewModel.onSearchQueryChange(it)
            advanceTimeBy(100)
        }
        advanceTimeBy(300)
        runCurrent()
        verifyNameCalls("bolt", 0)

        advanceUntilIdle()
        verifyNameCalls("bo", 0)
        verifyNameCalls("bol", 0)
        verifyNameCalls("bolt", 1)
        assertTrue(viewModel.uiState.value.resultsFiltered)
    }

    @Test
    fun `whitespace-only edits of the same query do not hit the server again`() = runTest(dispatcher) {
        createViewModel()
        viewModel.onSearchQueryChange("bolt")
        advanceUntilIdle()
        viewModel.onSearchQueryChange("  bolt ")
        advanceUntilIdle()
        viewModel.onSearchSubmit()
        advanceUntilIdle()

        coVerify(exactly = 2) { searchUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a single character never reaches the server and shows the hint`() = runTest(dispatcher) {
        createViewModel()
        viewModel.onSearchQueryChange("a")
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase(any(), any(), any(), any(), any()) }
        assertTrue(viewModel.uiState.value.showMinLengthHint)
    }

    @Test
    fun `IME submit skips the debounce`() = runTest(dispatcher) {
        createViewModel()
        viewModel.onSearchQueryChange("opt")
        advanceTimeBy(10)
        viewModel.onSearchSubmit()
        runCurrent()

        verifyNameCalls("opt", 1)
        advanceUntilIdle()
        verifyNameCalls("opt", 1)
    }

    @Test
    fun `a newer request cancels the in-flight one`() = runTest(dispatcher) {
        createViewModel()
        var boltCompleted = false
        coEvery { searchUseCase(any(), any(), match { it.name == "bolt" }, any(), any()) } coAnswers {
            delay(1_000)
            boltCompleted = true
            page("bolt")
        }
        coEvery { searchUseCase(any(), any(), match { it.name == "opt" }, any(), any()) } returns page("opt")

        viewModel.onSearchQueryChange("bolt")
        viewModel.onSearchSubmit()
        advanceTimeBy(100)
        viewModel.onSearchQueryChange("opt")
        viewModel.onSearchSubmit()
        advanceUntilIdle()

        assertFalse(boltCompleted)
        assertEquals(listOf("opt"), viewModel.uiState.value.cards.map { it.scryfallId })
    }

    @Test
    fun `previous results stay visible while a new search loads`() = runTest(dispatcher) {
        createViewModel()
        coEvery { searchUseCase(any(), any(), match { it.name == "bolt" }, any(), any()) } coAnswers {
            delay(500)
            page("bolt")
        }
        viewModel.onSearchQueryChange("bolt")
        viewModel.onSearchSubmit()
        advanceTimeBy(100)

        val loading = viewModel.uiState.value
        assertTrue(loading.isLoadingCards)
        assertEquals(listOf("a"), loading.cards.map { it.scryfallId })
    }

    @Test
    fun `loadMore passes the cursor, appends and stops on the last page`() = runTest(dispatcher) {
        val cursor = FriendCardCursor("0a", "row-a")
        stubSearch(page("a", cursor = cursor))
        coEvery { searchUseCase(any(), any(), any(), cursor, any()) } returns page("b")
        createViewModel()

        viewModel.loadMoreCards()
        viewModel.loadMoreCards()
        advanceUntilIdle()
        viewModel.loadMoreCards()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase(any(), any(), any(), cursor, any()) }
        val state = viewModel.uiState.value
        assertEquals(listOf("a", "b"), state.cards.map { it.scryfallId })
        assertFalse(state.hasMoreCards)
    }

    @Test
    fun `a next page from a superseded request is discarded`() = runTest(dispatcher) {
        val cursor = FriendCardCursor("0a", "row-a")
        stubSearch(page("a", cursor = cursor))
        coEvery { searchUseCase(any(), any(), any(), cursor, any()) } coAnswers {
            delay(1_000)
            page("stale")
        }
        coEvery { searchUseCase(any(), any(), match { it.name == "opt" }, null, any()) } returns page("opt")
        createViewModel()

        viewModel.loadMoreCards()
        advanceTimeBy(100)
        viewModel.onSearchQueryChange("opt")
        viewModel.onSearchSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("opt"), state.cards.map { it.scryfallId })
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `a failed next page is not retried automatically`() = runTest(dispatcher) {
        val cursor = FriendCardCursor("0a", "row-a")
        stubSearch(page("a", cursor = cursor))
        coEvery { searchUseCase(any(), any(), any(), cursor, any()) } returns
            Result.failure(FriendCardSearchException.Network(RuntimeException()))
        createViewModel()

        viewModel.loadMoreCards()
        advanceUntilIdle()
        viewModel.loadMoreCards()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loadMoreFailed)
        coVerify(exactly = 1) { searchUseCase(any(), any(), any(), cursor, any()) }

        viewModel.retryLoadMore()
        advanceUntilIdle()
        coVerify(exactly = 2) { searchUseCase(any(), any(), any(), cursor, any()) }
    }

    @Test
    fun `access denied maps to its own state and is not recorded as a non-fatal`() = runTest(dispatcher) {
        stubSearch(Result.failure(FriendCardSearchException.AccessDenied()))
        createViewModel()

        assertEquals(FolderCardsError.ACCESS_DENIED, viewModel.uiState.value.cardsError)
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    @Test
    fun `network failure maps to NETWORK and retry bypasses nothing cached`() = runTest(dispatcher) {
        stubSearch(Result.failure(FriendCardSearchException.Network(RuntimeException())))
        createViewModel()
        assertEquals(FolderCardsError.NETWORK, viewModel.uiState.value.cardsError)

        stubSearch(page("a"))
        viewModel.retryCards()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.cardsError)
        coVerify(exactly = 2) { searchUseCase(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    @Test
    fun `unexpected failure is generic and recorded without the raw query`() = runTest(dispatcher) {
        stubSearch(Result.failure(FriendCardSearchException.InvalidArgument("cursor")))
        createViewModel()

        assertEquals(FolderCardsError.GENERIC, viewModel.uiState.value.cardsError)
        verify(exactly = 1) { crashReporter.recordException(any()) }
    }

    @Test
    fun `switching list keeps the search and re-runs it against the new list`() = runTest(dispatcher) {
        createViewModel()
        viewModel.onSearchQueryChange("bolt")
        viewModel.onSearchSubmit()
        advanceUntilIdle()

        viewModel.selectFolderSubTab(FolderSubTab.WISHLIST)
        advanceUntilIdle()

        assertEquals("bolt", viewModel.uiState.value.searchText)
        coVerify(exactly = 1) { searchUseCase(any(), "wishlist", match { it.name == "bolt" }, any(), any()) }
    }

    @Test
    fun `leaving and returning to the folder tab keeps the text and is served from cache`() = runTest(dispatcher) {
        createViewModel()
        viewModel.onSearchQueryChange("bolt")
        viewModel.onSearchSubmit()
        advanceUntilIdle()

        viewModel.selectTab(FriendTab.HISTORY)
        viewModel.selectTab(FriendTab.FOLDER)
        viewModel.selectFolderSubTab(FolderSubTab.TRADE)
        advanceUntilIdle()
        viewModel.selectFolderSubTab(FolderSubTab.COLLECTION)
        advanceUntilIdle()

        assertEquals("bolt", viewModel.uiState.value.searchText)
        verifyNameCalls("bolt", 2)
    }

    @Test
    fun `advanced search moves the name to the search bar and drops unsupported criteria`() = runTest(dispatcher) {
        createViewModel()
        viewModel.applyAdvancedSearch(
            AdvancedSearchQuery(
                listOf(
                    SearchCriterion.Name("Sol Ring", exact = true),
                    SearchCriterion.Colors(setOf("U"), ColorMatchMode.AT_LEAST),
                    SearchCriterion.Price(5.0, "usd"),
                )
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Sol Ring", state.searchText)
        assertTrue(state.nameExact)
        assertEquals(listOf<SearchCriterion>(SearchCriterion.Colors(setOf("U"), ColorMatchMode.AT_LEAST)), state.advancedQuery.criteria)
        assertEquals(2, state.activeCriteriaCount)
        coVerify(exactly = 1) {
            searchUseCase(any(), any(), match { it.name == "Sol Ring" && it.nameExact && it.colors == listOf("U") }, any(), any())
        }
    }

    @Test
    fun `removing the last criterion returns to browse and uses the cached page`() = runTest(dispatcher) {
        createViewModel()
        val criterion = SearchCriterion.Rarity(listOf("rare"))
        viewModel.applyAdvancedSearch(AdvancedSearchQuery(listOf(criterion)))
        advanceUntilIdle()
        viewModel.removeCriterion(criterion)
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase(any(), any(), FriendCardSearchParams(), any(), any()) }
        assertFalse(viewModel.uiState.value.resultsFiltered)
    }

    private fun minimalCard(id: String, name: String) = com.mmg.manahub.core.model.Card(
        scryfallId = id, name = name, printedName = null, manaCost = null, cmc = 1.0,
        colors = emptyList(), colorIdentity = emptyList(), typeLine = "Instant", printedTypeLine = null,
        oracleText = null, printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "tst", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2020-01-01", frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
        imageNormal = "img", imageArtCrop = null, imageBackNormal = null, priceUsd = null,
        priceUsdFoil = null, priceEur = null, priceEurFoil = null, legalityStandard = "legal",
        legalityPioneer = "legal", legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = null, scryfallUri = "https://scryfall.com/card/$id",
    )

    @Test
    fun `appended rows already shown are deduplicated by row id`() = runTest(dispatcher) {
        val cursor = FriendCardCursor("0a", "row-a")
        stubSearch(page("a", cursor = cursor))
        coEvery { searchUseCase(any(), any(), any(), cursor, any()) } returns page("a", "b")
        createViewModel()

        viewModel.loadMoreCards()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), viewModel.uiState.value.cards.map { it.scryfallId })
    }

    @Test
    fun `a filtered empty page asks for the unindexed count`() = runTest(dispatcher) {
        createViewModel()
        coEvery { searchUseCase(any(), any(), match { it.name == "bolt" }, any(), any()) } returns page()
        coEvery { searchUseCase.unindexedCount("friend-1", "collection") } returns Result.success(7)

        viewModel.onSearchQueryChange("bolt")
        viewModel.onSearchSubmit()
        advanceUntilIdle()

        assertEquals(7, viewModel.uiState.value.unindexedCount)
        coVerify(exactly = 1) { searchUseCase.unindexedCount(any(), any()) }
    }

    @Test
    fun `a browse empty page or a filtered page with rows never calls the count rpc`() = runTest(dispatcher) {
        stubSearch(page())
        createViewModel()
        coEvery { searchUseCase(any(), any(), match { it.name == "bolt" }, any(), any()) } returns page("bolt", unindexed = 3)

        viewModel.onSearchQueryChange("bolt")
        viewModel.onSearchSubmit()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.unindexedCount)
        coVerify(exactly = 0) { searchUseCase.unindexedCount(any(), any()) }
    }

    @Test
    fun `missing metadata is hydrated once in the background and patched into the rows`() = runTest(dispatcher) {
        val cursor = FriendCardCursor("0a", "row-a")
        stubSearch(page("a", cursor = cursor, unresolved = setOf("a")))
        coEvery { searchUseCase(any(), any(), any(), cursor, any()) } returns page("b", unresolved = setOf("a", "b"))
        coEvery { searchUseCase.hydrateMetadata(listOf("a")) } returns mapOf("a" to minimalCard("a", "Resolved A"))
        createViewModel()

        assertEquals("Resolved A", viewModel.uiState.value.cards.first().name)
        assertEquals("img", viewModel.uiState.value.cards.first().imageNormal)

        viewModel.loadMoreCards()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase.hydrateMetadata(listOf("a")) }
        coVerify(exactly = 1) { searchUseCase.hydrateMetadata(listOf("b")) }
    }

    @Test
    fun `results version bumps on a new first page but not on append`() = runTest(dispatcher) {
        val cursor = FriendCardCursor("0a", "row-a")
        stubSearch(page("a", cursor = cursor))
        coEvery { searchUseCase(any(), any(), any(), cursor, any()) } returns page("b")
        createViewModel()
        val first = viewModel.uiState.value.resultsVersion

        viewModel.loadMoreCards()
        advanceUntilIdle()
        assertEquals(first, viewModel.uiState.value.resultsVersion)

        viewModel.onSearchQueryChange("bolt")
        viewModel.onSearchSubmit()
        advanceUntilIdle()
        assertEquals(first + 1, viewModel.uiState.value.resultsVersion)
    }

    @Test
    fun `switching list clears the previous list's cards while the new one loads`() = runTest(dispatcher) {
        createViewModel()
        coEvery { searchUseCase(any(), "trade", any(), any(), any()) } coAnswers {
            delay(500)
            page("t")
        }

        viewModel.selectFolderSubTab(FolderSubTab.TRADE)
        advanceTimeBy(200)

        val loading = viewModel.uiState.value
        assertTrue(loading.isLoadingCards)
        assertTrue(loading.cards.isEmpty())
    }

    @Test
    fun `rapid chip removals coalesce into one request`() = runTest(dispatcher) {
        createViewModel()
        val a = SearchCriterion.Rarity(listOf("rare"))
        val b = SearchCriterion.CardSet(setOf("neo"))
        viewModel.applyAdvancedSearch(AdvancedSearchQuery(listOf(a, b)))
        advanceUntilIdle()

        viewModel.removeCriterion(a)
        advanceTimeBy(50)
        viewModel.removeCriterion(b)
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any(), any(), match { it.rarities == null && it.setCodes != null }, any(), any()) }
    }

    @Test
    fun `a list switch during a pending typing debounce fires one request with the latest text`() = runTest(dispatcher) {
        createViewModel()

        viewModel.onSearchQueryChange("bolt")
        advanceTimeBy(100)
        viewModel.selectFolderSubTab(FolderSubTab.WISHLIST)
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any(), "collection", match { it.name == "bolt" }, any(), any()) }
        coVerify(exactly = 1) { searchUseCase(any(), "wishlist", match { it.name == "bolt" }, any(), any()) }
        coVerify(exactly = 2) { searchUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `session expiry and incomplete profile are terminal and not recorded`() = runTest(dispatcher) {
        stubSearch(Result.failure(FriendCardSearchException.SessionExpired()))
        createViewModel()
        assertEquals(FolderCardsError.SESSION_EXPIRED, viewModel.uiState.value.cardsError)

        coEvery { searchUseCase(any(), "trade", any(), any(), any()) } returns
            Result.failure(FriendCardSearchException.ProfileIncomplete())
        viewModel.selectFolderSubTab(FolderSubTab.TRADE)
        advanceUntilIdle()

        assertEquals(FolderCardsError.PROFILE_INCOMPLETE, viewModel.uiState.value.cardsError)
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    @Test
    fun `a cached result expires after its ttl`() = runTest(dispatcher) {
        val clock = TestTimeSource()
        createViewModel(clock)
        viewModel.selectFolderSubTab(FolderSubTab.TRADE)
        advanceUntilIdle()

        viewModel.selectFolderSubTab(FolderSubTab.COLLECTION)
        advanceUntilIdle()
        coVerify(exactly = 1) { searchUseCase(any(), "collection", any(), any(), any()) }

        viewModel.selectFolderSubTab(FolderSubTab.TRADE)
        advanceUntilIdle()
        clock += 61.seconds
        viewModel.selectFolderSubTab(FolderSubTab.COLLECTION)
        advanceUntilIdle()
        coVerify(exactly = 2) { searchUseCase(any(), "collection", any(), any(), any()) }
    }

    @Test
    fun `the oldest cached request is evicted past the cache size`() = runTest(dispatcher) {
        createViewModel()
        val names = (0 until 9).map { "q$it" }
        names.forEach {
            viewModel.onSearchQueryChange(it)
            viewModel.onSearchSubmit()
            advanceUntilIdle()
        }

        viewModel.onSearchQueryChange("q8")
        viewModel.onSearchSubmit()
        advanceUntilIdle()
        verifyNameCalls("q8", 1)

        viewModel.onSearchQueryChange("q0")
        viewModel.onSearchSubmit()
        advanceUntilIdle()
        verifyNameCalls("q0", 2)
    }
}
