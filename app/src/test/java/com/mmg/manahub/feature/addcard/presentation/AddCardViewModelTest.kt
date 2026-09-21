package com.mmg.manahub.feature.addcard.presentation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.mmg.manahub.core.data.queue.InMemoryCardQueueStore
import com.mmg.manahub.core.data.queue.PersistentCardQueueRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.GetSpotlightFeedUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SpotlightFeedResult
import com.mmg.manahub.core.domain.usecase.collection.CommitScanResult
import com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase
import com.mmg.manahub.core.domain.usecase.queue.CardQueueActions
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AppLanguage
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.NewsLanguage
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.model.SetType
import com.mmg.manahub.core.model.UserPreferences
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class AddCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val searchCards: SearchCardsUseCase = mockk()
    private val userPreferences: UserPreferencesRepository = mockk()
    private val buildScryfallQuery: BuildScryfallQueryUseCase = mockk()
    private val getSpotlightFeed: GetSpotlightFeedUseCase = mockk()
    private val userCardRepository: UserCardRepository = mockk(relaxed = true)
    private val cardRepository: CardRepository = mockk(relaxed = true)
    private val commitScannedCards: CommitScannedCardsUseCase = mockk(relaxed = true)
    private val addToWishlist: AddToWishlistUseCase = mockk()

    private val testSet = MagicSet(
        code = "tst",
        name = "Test Set",
        setType = SetType.EXPANSION,
        releasedAt = "2024-01-01",
        cardCount = 100,
        iconSvgUri = "https://example.com/icon.svg"
    )

    private val testPreferences = UserPreferences(
        appLanguage = AppLanguage.ENGLISH,
        cardLanguage = CardLanguage.ENGLISH,
        newsLanguages = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = PreferredCurrency.EUR,
        collectionViewMode = CollectionViewMode.GRID,
    )

    private val bolt = TestFixtures.buildCard(scryfallId = "bolt-1", name = "Lightning Bolt", setCode = "lea")
    private val counterspell = TestFixtures.buildCard(scryfallId = "counter-1", name = "Counterspell", setCode = "mh2")

    private lateinit var appScope: CoroutineScope
    private lateinit var queueRepository: PersistentCardQueueRepository
    private lateinit var viewModel: AddCardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)

        coEvery { userPreferences.preferencesFlow } returns flowOf(testPreferences)
        coEvery { getSpotlightFeed(any()) } returns DataResult.Success(
            SpotlightFeedResult(
                cards = emptyList(),
                sourceSet = testSet,
                nextSetIndex = 1
            )
        )
        every { userCardRepository.observeCollection() } returns emptyFlow()

        appScope = CoroutineScope(SupervisorJob())
        queueRepository = PersistentCardQueueRepository(store = InMemoryCardQueueStore())
        viewModel = buildViewModel()
    }

    private fun buildViewModel() = AddCardViewModel(
        searchCards = searchCards,
        userPreferences = userPreferences,
        buildScryfallQuery = buildScryfallQuery,
        getSpotlightFeed = getSpotlightFeed,
        queueRepository = queueRepository,
        queueActions = CardQueueActions(
            queueRepository = queueRepository,
            commitScannedCards = commitScannedCards,
            addToWishlist = addToWishlist,
        ),
        userCardRepository = userCardRepository,
        cardRepository = cardRepository,
        appScope = appScope,
        nowMillis = { 1_000L },
    )

    private fun scannedEntry(card: com.mmg.manahub.core.model.Card, isFoil: Boolean) = QueuedCard(
        card = card,
        quantity = 2,
        isFoil = isFoil,
        language = "en",
        condition = "LP",
        setCode = card.setCode,
        timestamp = 1L,
    )

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty query and no results`() = runTest(dispatcher) {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("", state.query)
            assertEquals(emptyList<Any>(), state.results)
            assertEquals(false, state.isSearching)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onClearAll resets all search state`() = runTest(dispatcher) {
        viewModel.uiState.test {
            awaitItem() // Initial

            viewModel.onQueryChange("lotus")
            awaitItem() // Query change

            viewModel.onClearAll()
            val finalState = awaitItem()

            assertEquals("", finalState.query)
            assertEquals(null, finalState.activeQuery)
            assertEquals(emptyList<Any>(), finalState.results)
            assertEquals(false, finalState.isSearching)
            assertEquals(null, finalState.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Multi-select mode ───────────────────────────────────────────────────

    @Test
    fun `multi-select mode toggles on and off and turning it off closes the queue sheet`() = runTest(dispatcher) {
        assertFalse(viewModel.uiState.value.isMultiSelectMode)

        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)
        viewModel.onOpenQueueSheet()
        assertTrue(viewModel.uiState.value.isMultiSelectMode)
        assertTrue(viewModel.uiState.value.showQueueSheet)

        viewModel.onToggleMultiSelectMode()
        val state = viewModel.uiState.value
        assertFalse(state.isMultiSelectMode)
        assertFalse(state.showQueueSheet)
        assertEquals("the queue survives leaving the mode", 1, state.queueCount)
    }

    @Test
    fun `tap on an unselected card queues it once with default attributes`() = runTest(dispatcher) {
        val spanishBolt = bolt.copy(lang = "es")
        viewModel.onToggleMultiSelectMode()

        viewModel.onToggleCardSelection(spanishBolt)

        val entry = queueRepository.queue.value.single()
        assertEquals("bolt-1", entry.card.scryfallId)
        assertEquals(1, entry.quantity)
        assertFalse(entry.isFoil)
        assertEquals("NM", entry.condition)
        assertEquals("es", entry.language)
        assertEquals("lea", entry.setCode)
        assertEquals(setOf("bolt-1"), viewModel.uiState.value.selectedScryfallIds)
        assertTrue(viewModel.uiState.value.showProceedCta)
    }

    @Test
    fun `tap on a selected card deselects it`() = runTest(dispatcher) {
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)

        viewModel.onToggleCardSelection(bolt)

        assertTrue(queueRepository.queue.value.isEmpty())
        assertEquals(emptySet<String>(), viewModel.uiState.value.selectedScryfallIds)
        assertFalse(viewModel.uiState.value.showProceedCta)
    }

    @Test
    fun `tap outside multi-select mode never touches the queue`() = runTest(dispatcher) {
        viewModel.onToggleCardSelection(bolt)

        assertTrue(queueRepository.queue.value.isEmpty())
    }

    @Test
    fun `deselecting removes every entry with that scryfallId including scanned ones`() = runTest(dispatcher) {
        queueRepository.add(scannedEntry(bolt, isFoil = false))
        queueRepository.add(scannedEntry(bolt, isFoil = true))
        queueRepository.add(scannedEntry(counterspell, isFoil = false))
        val vm = buildViewModel()
        vm.onToggleMultiSelectMode()
        assertEquals(setOf("bolt-1", "counter-1"), vm.uiState.value.selectedScryfallIds)

        vm.onToggleCardSelection(bolt)

        assertEquals(listOf("counter-1"), queueRepository.queue.value.map { it.card.scryfallId })
        assertEquals(setOf("counter-1"), vm.uiState.value.selectedScryfallIds)
    }

    @Test
    fun `selected set follows queue changes made from another screen`() = runTest(dispatcher) {
        advanceUntilIdle()

        queueRepository.add(scannedEntry(counterspell, isFoil = false))
        advanceUntilIdle()

        assertEquals(setOf("counter-1"), viewModel.uiState.value.selectedScryfallIds)
        assertEquals(1, viewModel.uiState.value.queueCount)
    }

    @Test
    fun `queue sheet opens only with a non-empty queue and closes on demand`() = runTest(dispatcher) {
        viewModel.onToggleMultiSelectMode()
        viewModel.onOpenQueueSheet()
        assertFalse(viewModel.uiState.value.showQueueSheet)

        viewModel.onToggleCardSelection(bolt)
        viewModel.onOpenQueueSheet()
        assertTrue(viewModel.uiState.value.showQueueSheet)

        viewModel.onCloseQueueSheet()
        assertFalse(viewModel.uiState.value.showQueueSheet)
    }

    @Test
    fun `clearing the queue empties it and closes the sheet`() = runTest(dispatcher) {
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)
        viewModel.onToggleCardSelection(counterspell)
        viewModel.onOpenQueueSheet()

        viewModel.onClearQueue()

        assertTrue(queueRepository.queue.value.isEmpty())
        assertFalse(viewModel.uiState.value.showQueueSheet)
        assertEquals(emptySet<String>(), viewModel.uiState.value.selectedScryfallIds)
    }

    @Test
    fun `add all to collection success empties the queue, closes the sheet and toasts`() = runTest(dispatcher) {
        coEvery { commitScannedCards(any()) } returns CommitScanResult(
            committedCopies = 2, failedEntries = 0, entrySucceeded = listOf(true, true),
        )
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)
        viewModel.onToggleCardSelection(counterspell)
        viewModel.onOpenQueueSheet()

        viewModel.onAddAllToCollection()
        assertTrue(viewModel.uiState.value.isCommittingQueue)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(queueRepository.queue.value.isEmpty())
        assertFalse(state.showQueueSheet)
        assertFalse(state.isCommittingQueue)
        assertEquals(AddCardQueueToast.AddedAllToCollection(2), state.queueToast)

        viewModel.onQueueToastShown()
        assertEquals(null, viewModel.uiState.value.queueToast)
    }

    @Test
    fun `add all to collection completes even after the ViewModel is cleared`() = runTest(dispatcher) {
        coEvery { commitScannedCards(any()) } returns CommitScanResult(
            committedCopies = 1, failedEntries = 0, entrySucceeded = listOf(true),
        )
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)

        viewModel.onAddAllToCollection()
        // Simulates leaving the screen: viewModelScope is cancelled, the commit runs in appScope.
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()

        assertTrue(queueRepository.queue.value.isEmpty())
    }

    @Test
    fun `failed wishlist add reports an error instead of success`() = runTest(dispatcher) {
        coEvery { addToWishlist(any()) } returns Result.failure(IllegalStateException("boom"))
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)
        val entry = queueRepository.queue.value.single()

        viewModel.onAddEntryToWishlist(entry)
        advanceUntilIdle()

        assertEquals(AddCardQueueToast.AddFailed("Lightning Bolt"), viewModel.uiState.value.queueToast)
        assertEquals(1, queueRepository.queue.value.size)
    }

    @Test
    fun `quantity, duplicate and remove act on the shared queue`() = runTest(dispatcher) {
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)
        val entry = queueRepository.queue.value.single()

        viewModel.onIncrementQueuedCardQuantity(entry)
        assertEquals(2, viewModel.uiState.value.queue.single().quantity)

        viewModel.onDuplicateQueuedCard(viewModel.uiState.value.queue.single())
        assertEquals(2, viewModel.uiState.value.queueCount)

        viewModel.onRemoveQueuedCard(entry)
        assertEquals(1, viewModel.uiState.value.queueCount)
        assertEquals(setOf("bolt-1"), viewModel.uiState.value.selectedScryfallIds)
    }
}
