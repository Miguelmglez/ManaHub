package com.mmg.manahub.feature.addcard.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.queue.CardQueueStore
import com.mmg.manahub.core.data.queue.InMemoryCardQueueStore
import com.mmg.manahub.core.data.queue.PersistentCardQueueRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityDecksRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
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
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckCard
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.PaginatedCards
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.NewsLanguage
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.model.SetType
import com.mmg.manahub.core.model.UserPreferences
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.feature.addcard.di.SavedStateAddCardRestorableState
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
    private val deckRepository: DeckRepository = mockk()
    private val communityDecksRepository: CommunityDecksRepository = mockk()
    private val crashlytics: FirebaseCrashlytics = mockk(relaxed = true)

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
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

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

    private fun buildViewModel(
        launchArgs: AddCardLaunchArgs = AddCardLaunchArgs(),
        restorableState: AddCardRestorableState = InMemoryAddCardRestorableState(),
    ) = AddCardViewModel(
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
        deckRepository = deckRepository,
        communityDecksRepository = communityDecksRepository,
        appScope = appScope,
        launchArgs = launchArgs,
        restorableState = restorableState,
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
        unmockkStatic(FirebaseCrashlytics::class)
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

    // ── Deck source mode ────────────────────────────────────────────────────

    private val goblin = TestFixtures.buildCard(
        scryfallId = "goblin-1", name = "Goblin Guide", setCode = "zen", typeLine = "Creature — Goblin Scout",
    ).copy(oracleId = "oracle-goblin", printedName = "Guía goblin")
    private val sol = TestFixtures.buildCard(
        scryfallId = "sol-1", name = "Sol Ring", setCode = "c21", typeLine = "Artifact",
    ).copy(oracleId = "oracle-sol")
    private val boltWithOracle = bolt.copy(oracleId = "oracle-bolt")

    private val localDeckArgs = AddCardLaunchArgs(deckSource = AddCardDeckSource.Local("deck-1"))

    private fun stubLocalDeck() {
        every { deckRepository.observeDeckWithCards("deck-1") } returns flowOf(
            DeckWithCards(
                deck = Deck(id = "deck-1", name = "Burn", commanderCardId = "sol-1"),
                mainboard = listOf(DeckSlot("bolt-1", 4), DeckSlot("goblin-1", 4)),
                sideboard = listOf(DeckSlot("bolt-1", 1)),
            )
        )
        coEvery { cardRepository.getCardsByIds(any()) } returns listOf(goblin, boltWithOracle, sol)
    }

    private fun communityCard(name: String, scryfallId: String) = CommunityDeckCard(
        name = name,
        quantity = 1,
        categories = emptyList(),
        oracleId = "",
        scryfallId = scryfallId,
    )

    private fun enterLocalDeckMode(): AddCardViewModel {
        stubLocalDeck()
        return buildViewModel(localDeckArgs)
    }

    @Test
    fun `launch args parse deck and community sources and reject unusable ids`() {
        assertEquals(AddCardDeckSource.Local("d1"), AddCardLaunchArgs.from(false, "deck", "d1").deckSource)
        assertEquals(AddCardDeckSource.Community(42), AddCardLaunchArgs.from(false, "community", "42").deckSource)
        assertEquals(null, AddCardLaunchArgs.from(false, "community", "abc").deckSource)
        assertEquals(null, AddCardLaunchArgs.from(false, "deck", " ").deckSource)
        assertEquals(null, AddCardLaunchArgs.from(true, null, null).deckSource)
        assertTrue(AddCardLaunchArgs.from(true, null, null).multi)
    }

    @Test
    fun `multi launch arg opens in multi-select mode and enabling it again is idempotent`() = runTest(dispatcher) {
        val vm = buildViewModel(AddCardLaunchArgs(multi = true))
        assertTrue(vm.uiState.value.isMultiSelectMode)

        vm.enableMultiSelectMode()

        assertTrue(vm.uiState.value.isMultiSelectMode)
        assertFalse(vm.uiState.value.isDeckMode)
    }

    @Test
    fun `local deck source loads mainboard sideboard and commander distinct in deck order`() = runTest(dispatcher) {
        val vm = enterLocalDeckMode()
        assertTrue("a source forces multi mode", vm.uiState.value.isMultiSelectMode)
        assertTrue(vm.uiState.value.isDeckLoading)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isDeckLoading)
        assertFalse(state.deckLoadFailed)
        assertEquals("Burn", state.deckName)
        assertEquals(listOf("sol-1", "bolt-1", "goblin-1"), state.deckCards.map { it.scryfallId })
        assertEquals(state.deckCards, state.results)
        assertEquals(3, state.totalCards)
        coVerify { cardRepository.warmCacheForIds(listOf("sol-1", "bolt-1", "goblin-1")) }
        coVerify(exactly = 0) { searchCards(any(), any()) }
    }

    @Test
    fun `community deck source skips blank scryfall ids and batches the lookup`() = runTest(dispatcher) {
        coEvery { communityDecksRepository.getDeckById(7) } returns DataResult.Success(
            CommunityDeck(
                archidektId = 7,
                name = "Community Burn",
                description = "",
                format = "modern",
                owner = CommunityDeckOwner(id = 1, username = "u", avatarUrl = ""),
                viewCount = 0,
                createdAt = "",
                updatedAt = "",
                cards = listOf(
                    communityCard("Lightning Bolt", "bolt-1"),
                    communityCard("Unknown", ""),
                    communityCard("Goblin Guide", "goblin-1"),
                    communityCard("Lightning Bolt", "bolt-1"),
                ),
                sourceUrl = "",
            )
        )
        coEvery { cardRepository.getCardsByIds(listOf("bolt-1", "goblin-1")) } returns listOf(goblin, boltWithOracle)

        val vm = buildViewModel(AddCardLaunchArgs(deckSource = AddCardDeckSource.Community(7)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Community Burn", state.deckName)
        assertEquals(listOf("bolt-1", "goblin-1"), state.results.map { it.scryfallId })
        coVerify(exactly = 1) { cardRepository.warmCacheForIds(listOf("bolt-1", "goblin-1")) }
        coVerify(exactly = 1) { cardRepository.getCardsByIds(listOf("bolt-1", "goblin-1")) }
        coVerify(exactly = 0) { searchCards(any(), any()) }
    }

    @Test
    fun `community deck load failure exposes the error state and retry reloads`() = runTest(dispatcher) {
        coEvery { communityDecksRepository.getDeckById(7) } returns DataResult.Error("boom")
        val vm = buildViewModel(AddCardLaunchArgs(deckSource = AddCardDeckSource.Community(7)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deckLoadFailed)
        assertTrue(vm.uiState.value.isDeckMode)

        vm.onRetryDeckLoad()
        assertTrue(vm.uiState.value.isDeckLoading)
        advanceUntilIdle()

        coVerify(exactly = 2) { communityDecksRepository.getDeckById(7) }
    }

    @Test
    fun `search text filters the deck list locally by name and printed name`() = runTest(dispatcher) {
        val vm = enterLocalDeckMode()
        advanceUntilIdle()

        vm.onQueryChange("BOLT")
        assertEquals(listOf("bolt-1"), vm.uiState.value.results.map { it.scryfallId })

        vm.onQueryChange("guía")
        assertEquals(listOf("goblin-1"), vm.uiState.value.results.map { it.scryfallId })

        vm.onQueryChange("")
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.results.size)
        coVerify(exactly = 0) { searchCards(any(), any()) }
    }

    @Test
    fun `advanced search runs the local matcher over the deck list without Scryfall`() = runTest(dispatcher) {
        val vm = enterLocalDeckMode()
        advanceUntilIdle()

        vm.onAdvancedQuerySearch(
            AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardType(types = setOf("Creature"))))
        )
        advanceUntilIdle()

        assertEquals(listOf("goblin-1"), vm.uiState.value.results.map { it.scryfallId })
        coVerify(exactly = 0) { searchCards(any(), any()) }
        coVerify(exactly = 0) { buildScryfallQuery(any()) }

        vm.onClearFilters()
        assertEquals(3, vm.uiState.value.results.size)
    }

    @Test
    fun `clear deck cards returns to Scryfall search and keeps multi mode and the queue`() = runTest(dispatcher) {
        coEvery { searchCards("bolt", 1) } returns DataResult.Success(
            PaginatedCards(cards = listOf(bolt, counterspell), hasMore = false, totalCards = 2)
        )
        val vm = enterLocalDeckMode()
        advanceUntilIdle()
        vm.onToggleCardSelection(goblin)
        vm.onQueryChange("bolt")
        advanceUntilIdle()
        coVerify(exactly = 0) { searchCards(any(), any()) }

        vm.onClearDeckCards()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isDeckMode)
        assertEquals(null, state.deckName)
        assertTrue(state.deckCards.isEmpty())
        assertTrue(state.isMultiSelectMode)
        assertEquals(setOf("goblin-1"), state.selectedScryfallIds)
        assertEquals(listOf("bolt-1", "counter-1"), state.results.map { it.scryfallId })
        coVerify(exactly = 1) { searchCards("bolt", 1) }
    }

    @Test
    fun `select all queues only the visible cards that are not selected yet`() = runTest(dispatcher) {
        val vm = enterLocalDeckMode()
        advanceUntilIdle()
        vm.onToggleCardSelection(goblin)

        vm.onSelectAllDeckCards()

        val queued = queueRepository.queue.value
        assertEquals(3, queued.size)
        assertEquals(1, queued.count { it.card.scryfallId == "goblin-1" })
        queued.forEach {
            assertEquals(1, it.quantity)
            assertEquals("NM", it.condition)
            assertFalse(it.isFoil)
        }
        assertEquals(AddCardQueueToast.DeckCardsSelected(2), vm.uiState.value.queueToast)

        vm.onSelectAllDeckCards()
        assertEquals(3, queueRepository.queue.value.size)
        assertEquals(AddCardQueueToast.DeckCardsSelected(0), vm.uiState.value.queueToast)
    }

    @Test
    fun `select all respects the current local filter`() = runTest(dispatcher) {
        val vm = enterLocalDeckMode()
        advanceUntilIdle()
        vm.onQueryChange("sol")

        vm.onSelectAllDeckCards()

        assertEquals(listOf("sol-1"), queueRepository.queue.value.map { it.card.scryfallId })
    }

    @Test
    fun `select missing queues only cards owned under neither their oracleId nor their name`() = runTest(dispatcher) {
        val ownedBoltOtherPrinting = TestFixtures.buildCard(scryfallId = "bolt-2", name = "Lightning Bolt")
            .copy(oracleId = "oracle-bolt")
        val ownedSolByName = TestFixtures.buildCard(scryfallId = "sol-9", name = "Sol Ring")
        every { userCardRepository.observeCollection() } returns flowOf(
            listOf(
                TestFixtures.buildUserCardWithCard(card = ownedBoltOtherPrinting),
                TestFixtures.buildUserCardWithCard(card = ownedSolByName),
            )
        )
        val vm = enterLocalDeckMode()
        advanceUntilIdle()

        vm.onSelectMissingDeckCards()
        advanceUntilIdle()

        // Sol Ring's owned row predates the oracle backfill; it still matches by exact name.
        assertEquals(
            setOf("goblin-1"),
            queueRepository.queue.value.map { it.card.scryfallId }.toSet(),
        )
    }

    // ── Process restore ─────────────────────────────────────────────────────

    @Test
    fun `withoutDeckSource keeps multi mode and entry points map from the args`() {
        val cleared = AddCardLaunchArgs(deckSource = AddCardDeckSource.Local("d1")).withoutDeckSource()
        assertEquals(AddCardLaunchArgs(multi = true, deckSource = null), cleared)
        assertEquals(MultiSelectEntryPoint.DECK, localDeckArgs.entryPoint)
        assertEquals(
            MultiSelectEntryPoint.COMMUNITY,
            AddCardLaunchArgs(deckSource = AddCardDeckSource.Community(1)).entryPoint,
        )
        assertEquals(MultiSelectEntryPoint.HOME, AddCardLaunchArgs(multi = true).entryPoint)
        assertEquals(null, AddCardLaunchArgs().entryPoint)
    }

    @Test
    fun `a screen restored after clear deck cards does not reload the cleared deck`() = runTest(dispatcher) {
        coEvery { searchCards(any(), any()) } returns DataResult.Success(
            PaginatedCards(cards = emptyList(), hasMore = false, totalCards = 0)
        )
        stubLocalDeck()
        // The same handle survives process death while the nav args still carry the deck source.
        val savedStateHandle = SavedStateHandle()
        val first = buildViewModel(localDeckArgs, SavedStateAddCardRestorableState(savedStateHandle))
        advanceUntilIdle()
        assertTrue(first.uiState.value.isDeckMode)

        first.onClearDeckCards()
        advanceUntilIdle()

        val restored = buildViewModel(localDeckArgs, SavedStateAddCardRestorableState(savedStateHandle))
        advanceUntilIdle()

        val state = restored.uiState.value
        assertFalse(state.isDeckMode)
        assertFalse(state.isDeckLoading)
        assertTrue("multi mode survives the cleared source", state.isMultiSelectMode)
        verify(exactly = 1) { deckRepository.observeDeckWithCards("deck-1") }
    }

    @Test
    fun `a restored screen whose deck was never cleared reloads it`() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle()
        stubLocalDeck()
        buildViewModel(localDeckArgs, SavedStateAddCardRestorableState(savedStateHandle))
        advanceUntilIdle()

        val restored = buildViewModel(localDeckArgs, SavedStateAddCardRestorableState(savedStateHandle))
        advanceUntilIdle()

        assertTrue(restored.uiState.value.isDeckMode)
        assertEquals(3, restored.uiState.value.deckCards.size)
    }

    // ── Telemetry ───────────────────────────────────────────────────────────

    @Test
    fun `toggling multi-select logs on and off and the toolbar entry point`() = runTest(dispatcher) {
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleMultiSelectMode()

        verify(exactly = 1) { crashlytics.log("addcard_multiselect_toggled: on") }
        verify(exactly = 1) { crashlytics.log("addcard_multiselect_toggled: off") }
        verify(exactly = 1) { crashlytics.log("addcard_multiselect_opened_from: toolbar") }
    }

    @Test
    fun `the launch entry point is logged once per destination, not again on restore`() = runTest(dispatcher) {
        val state = InMemoryAddCardRestorableState()
        buildViewModel(AddCardLaunchArgs(multi = true), state)
        buildViewModel(AddCardLaunchArgs(multi = true), state)

        verify(exactly = 1) { crashlytics.log("addcard_multiselect_opened_from: home") }
    }

    @Test
    fun `opening the queue logs a count bucket`() = runTest(dispatcher) {
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)
        viewModel.onToggleCardSelection(counterspell)

        viewModel.onOpenQueueSheet()

        verify(exactly = 1) { crashlytics.log("addcard_multiselect_queue_opened: 2_5") }
    }

    @Test
    fun `select all and select missing log the added count bucket`() = runTest(dispatcher) {
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = enterLocalDeckMode()
        advanceUntilIdle()

        vm.onSelectAllDeckCards()
        vm.onSelectMissingDeckCards()
        advanceUntilIdle()

        verify(exactly = 1) { crashlytics.log("addcard_multiselect_select_all: 2_5") }
        verify(exactly = 1) { crashlytics.log("addcard_multiselect_select_missing: 0") }
    }

    @Test
    fun `deck source load failure records a non-fatal with the source and reason`() = runTest(dispatcher) {
        coEvery { communityDecksRepository.getDeckById(7) } returns DataResult.Error("boom")
        buildViewModel(AddCardLaunchArgs(deckSource = AddCardDeckSource.Community(7)))
        advanceUntilIdle()

        verify { crashlytics.setCustomKey("addcard_deck_source", "community") }
        verify { crashlytics.setCustomKey("addcard_deck_load_failure", "not_found") }
        verify(exactly = 1) { crashlytics.recordException(any()) }
    }

    @Test
    fun `count buckets never expose exact counts above one`() {
        assertEquals("0", AddCardTelemetry.countBucket(0))
        assertEquals("1", AddCardTelemetry.countBucket(1))
        assertEquals("2_5", AddCardTelemetry.countBucket(5))
        assertEquals("6_10", AddCardTelemetry.countBucket(6))
        assertEquals("11_25", AddCardTelemetry.countBucket(25))
        assertEquals("26_50", AddCardTelemetry.countBucket(50))
        assertEquals("51_100", AddCardTelemetry.countBucket(100))
        assertEquals("100_plus", AddCardTelemetry.countBucket(101))
    }

    // ── Concurrent writes, wishlist quantities, placeholders ───────────────

    private fun gatedCommit(gate: CompletableDeferred<Unit>) {
        coEvery { commitScannedCards(any()) } coAnswers {
            gate.await()
            CommitScanResult(committedCopies = 1, failedEntries = 0, entrySucceeded = listOf(true))
        }
    }

    private fun queueBolt(): QueuedCard {
        viewModel.onToggleMultiSelectMode()
        viewModel.onToggleCardSelection(bolt)
        return queueRepository.queue.value.single()
    }

    @Test
    fun `per-entry add to collection tapped twice commits the entry once`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        gatedCommit(gate)
        val entry = queueBolt()

        viewModel.onAddEntryToCollection(entry)
        assertEquals(setOf(entry.id), viewModel.uiState.value.inFlightQueueIds)
        viewModel.onAddEntryToCollection(entry)
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { commitScannedCards(any()) }
        assertTrue(viewModel.uiState.value.inFlightQueueIds.isEmpty())
    }

    @Test
    fun `per-entry add during add-all does not commit the same entry twice`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        gatedCommit(gate)
        val entry = queueBolt()

        viewModel.onAddAllToCollection()
        viewModel.onAddEntryToCollection(entry)
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { commitScannedCards(any()) }
    }

    @Test
    fun `quantity added while add-all is in flight stays queued`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        gatedCommit(gate)
        queueBolt()
        viewModel.onAddAllToCollection()
        advanceUntilIdle()

        viewModel.onIncrementQueuedCardQuantity(queueRepository.queue.value.single())
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1), queueRepository.queue.value.map { it.quantity })
    }

    @Test
    fun `scan merged into an entry during add-all keeps the merged copy queued`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        gatedCommit(gate)
        queueBolt()
        viewModel.onAddAllToCollection()
        advanceUntilIdle()

        // What ScannerViewModel.addToSession does for a new scan with the same attributes.
        queueRepository.addOrMerge(
            QueuedCard(card = bolt, quantity = 1, isFoil = false, language = bolt.lang,
                condition = "NM", setCode = bolt.setCode, timestamp = 2L)
        )
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1), queueRepository.queue.value.map { it.quantity })
    }

    @Test
    fun `deselecting a card while add-all is in flight is refused with an info toast`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        gatedCommit(gate)
        queueBolt()
        viewModel.onAddAllToCollection()
        advanceUntilIdle()

        viewModel.onToggleCardSelection(bolt)

        assertEquals(setOf("bolt-1"), viewModel.uiState.value.selectedScryfallIds)
        assertEquals(AddCardQueueToast.SelectionLockedWhileAdding("Lightning Bolt"), viewModel.uiState.value.queueToast)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(queueRepository.queue.value.isEmpty())
    }

    @Test
    fun `add all to wishlist keeps the queued quantity`() = runTest(dispatcher) {
        val captured = slot<WishlistEntry>()
        coEvery { addToWishlist(capture(captured)) } returns Result.success(Unit)
        val entry = queueBolt()
        viewModel.onIncrementQueuedCardQuantity(entry)
        viewModel.onIncrementQueuedCardQuantity(entry)

        viewModel.onAddAllToWishlist()
        advanceUntilIdle()

        assertEquals(3, captured.captured.quantity)
    }

    @Test
    fun `double tap on add all to wishlist adds each entry once`() = runTest(dispatcher) {
        coEvery { addToWishlist(any()) } returns Result.success(Unit)
        queueBolt()

        viewModel.onAddAllToWishlist()
        assertTrue(viewModel.uiState.value.isAddingAllToWishlist)
        viewModel.onAddAllToWishlist()
        advanceUntilIdle()

        coVerify(exactly = 1) { addToWishlist(any()) }
        assertFalse(viewModel.uiState.value.isAddingAllToWishlist)
    }

    @Test
    fun `removing the last queued entry closes the queue sheet`() = runTest(dispatcher) {
        val entry = queueBolt()
        viewModel.onOpenQueueSheet()
        assertTrue(viewModel.uiState.value.showQueueSheet)

        viewModel.onRemoveQueuedCard(entry)

        assertFalse(viewModel.uiState.value.showQueueSheet)
    }

    @Test
    fun `deck mode never lists pending-hydration placeholder cards`() = runTest(dispatcher) {
        val placeholder = TestFixtures.buildCard(scryfallId = "ph-1", name = "Unresolved card (ph-1)")
            .copy(staleReason = "pending_hydration", setCode = "", oracleId = "")
        every { deckRepository.observeDeckWithCards("deck-1") } returns flowOf(
            DeckWithCards(
                deck = Deck(id = "deck-1", name = "Burn"),
                mainboard = listOf(DeckSlot("bolt-1", 4), DeckSlot("ph-1", 1)),
                sideboard = emptyList(),
            )
        )
        coEvery { cardRepository.getCardsByIds(any()) } returns listOf(bolt, placeholder)

        val vm = buildViewModel(localDeckArgs)
        advanceUntilIdle()

        assertEquals(listOf("bolt-1"), vm.uiState.value.results.map { it.scryfallId })
    }

    @Test
    fun `select all persists the queue once, not once per card`() = runTest(dispatcher) {
        var writes = 0
        val countingStore = object : CardQueueStore {
            private var payload: String? = null
            override fun read(): String? = payload
            override fun write(payload: String) { writes++; this.payload = payload }
        }
        queueRepository = PersistentCardQueueRepository(store = countingStore)
        val cards = (1..60).map { TestFixtures.buildCard(scryfallId = "c-$it", name = "Card $it") }
        every { deckRepository.observeDeckWithCards("deck-1") } returns flowOf(
            DeckWithCards(
                deck = Deck(id = "deck-1", name = "Big"),
                mainboard = cards.map { DeckSlot(it.scryfallId, 1) },
                sideboard = emptyList(),
            )
        )
        coEvery { cardRepository.getCardsByIds(any()) } returns cards
        val vm = buildViewModel(localDeckArgs)
        advanceUntilIdle()

        vm.onSelectAllDeckCards()

        assertEquals(60, queueRepository.queue.value.size)
        assertEquals(1, writes)
    }

    @Test
    fun `the toolbar entry point is logged once however often multi-select is toggled`() = runTest(dispatcher) {
        repeat(3) {
            viewModel.onToggleMultiSelectMode()
            viewModel.onToggleMultiSelectMode()
        }

        verify(exactly = 1) { crashlytics.log("addcard_multiselect_opened_from: toolbar") }
    }

    @Test
    fun `a screen opened from home never logs the toolbar entry point`() = runTest(dispatcher) {
        val vm = buildViewModel(AddCardLaunchArgs(multi = true))
        vm.onToggleMultiSelectMode()
        vm.onToggleMultiSelectMode()

        verify(exactly = 0) { crashlytics.log("addcard_multiselect_opened_from: toolbar") }
    }

    @Test
    fun `retrying a failing deck load records the non-fatal once and breadcrumbs every failure`() = runTest(dispatcher) {
        coEvery { communityDecksRepository.getDeckById(7) } returns DataResult.Error("boom")
        val vm = buildViewModel(AddCardLaunchArgs(deckSource = AddCardDeckSource.Community(7)))
        advanceUntilIdle()

        vm.onRetryDeckLoad()
        advanceUntilIdle()
        vm.onRetryDeckLoad()
        advanceUntilIdle()

        verify(exactly = 1) { crashlytics.recordException(any()) }
        verify(exactly = 3) { crashlytics.log("addcard_deck_source_load_failed: community/not_found") }
    }

    @Test
    fun `typing while the deck is still loading never queries Scryfall`() = runTest(dispatcher) {
        val deckGate = CompletableDeferred<Unit>()
        stubLocalDeck()
        coEvery { cardRepository.getCardsByIds(any()) } coAnswers { deckGate.await(); listOf(bolt) }
        val vm = buildViewModel(localDeckArgs)
        advanceUntilIdle()

        vm.onQueryChange("bolt")
        vm.forceSearch()
        advanceUntilIdle()
        deckGate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 0) { searchCards(any(), any()) }
        assertEquals(listOf("bolt-1"), vm.uiState.value.results.map { it.scryfallId })
    }
}
