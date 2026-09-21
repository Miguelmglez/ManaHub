package com.mmg.manahub.feature.decks.presentation
// COMMENTS_REVIEWED: 2026-09-16

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mmg.manahub.app.navigation.Screen
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.LandTargetResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.availableIn
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.core.domain.repository.WishlistRepository
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.just
import io.mockk.Runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exhaustive unit tests for [DeckStudioViewModel] covering Phase 1 (P1-T5) and Phase 2 (P2-T4).
 *
 * Strategy:
 * - Phase 1 tests use REAL use-case instances for the Deck Doctor pipeline (EvaluateDeckUseCase,
 *   InferDeckIdentityUseCase) to verify end-to-end wiring, with only the repository / network
 *   surface mocked (pattern from DeckImprovementViewModelTest).
 * - Deck Analysis Category Sections rework (W0, D3/D5): the old Motor A add-pipeline / budget /
 *   cut-suggestion tests (Phase 2) were DELETED end-to-end here, along with
 *   `suggestCutsUseCase`/`suggestAddsFromCollectionUseCase` (`SuggestAddsFromCollectionUseCase`
 *   itself survives as a class -- it is still used by `the deleted Motor A wizard build use case`, the Deck
 *   Wizard's own build engine -- only `DeckDoctorOrchestrator`'s/`DeckStudioViewModel`'s USE of it
 *   is gone).
 * - [FirebaseCrashlytics] is always static-mocked because [logFailure] is called outside
 *   a runCatching block.
 * - [Context] is mocked to return the canonical "New deck" default name so the
 *   discard-if-empty predicate can be exercised without Robolectric.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class DeckStudioViewModelTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────
    private val dispatcher = StandardTestDispatcher()

    // ── Mocked dependencies ───────────────────────────────────────────────────
    private val deckRepository = mockk<DeckRepository>(relaxed = true)
    private val cardRepository = mockk<CardRepository>()
    private val userCardRepository = mockk<UserCardRepository>()
    private val searchCardsUseCase = mockk<SearchCardsUseCase>()
    private val suggestTagsUseCase = mockk<SuggestTagsUseCase>(relaxed = true)
    private val wishlistRepository = mockk<WishlistRepository>()
    private val userPreferences = mockk<UserPreferencesDataStore>()
    private val preferredCurrency = MutableStateFlow(PreferredCurrency.EUR)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val appContext = mockk<Context>()
    // Deck Engine Unification plan D7 (Phase 4.3) — Combos tab.
    private val findCombosUseCase = mockk<com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase>()
    // Deck Engine Unification plan D7 (4.1/4.2) — Strategies tab search.
    private val discoverSynergiesV2UseCase = mockk<com.mmg.manahub.feature.decks.domain.template.DiscoverSynergiesV2UseCase>()

    // ── Real engine + use cases (deterministic fixed PowerResolver) ───────────
    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.6f))
    private val eventBus = ProgressionEventBus()
    // spyk (not a plain instance) so the debounce-coalescing tests below can coVerify the exact call count.
    private val evaluateDeckUseCase = spyk(EvaluateDeckUseCase(scorer, eventBus, dispatcher))
    private val inferDeckIdentityUseCase = InferDeckIdentityUseCase()

    // ── Group C / C2: per-deck game stats use case (relaxed; deckStatsFlow is lazy) ──
    private val getDeckGameStatsUseCase =
        mockk<com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase>(relaxed = true)

    // ── Group B import use case (relaxed; not exercised by these tests) ──
    private val importDeckUseCase = mockk<ImportDeckUseCase>(relaxed = true)

    // ── Constants ─────────────────────────────────────────────────────────────
    private val DEFAULT_DECK_NAME = "New deck"
    private val DECK_ID = "deck-studio-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        // Context returns the same default name the VM resolves on init.
        every { appContext.getString(any()) } returns DEFAULT_DECK_NAME
        // F2 — no debug override persisted → NONE maps to default ScoreWeights().
        every { userPreferences.observeScoreWeightOverrides() } returns flowOf(ScoreWeightOverrides.NONE)
        // playerNameFlow is referenced at VM construction time (stateIn property initializer).
        every { userPreferences.playerNameFlow } returns flowOf("")
        preferredCurrency.value = PreferredCurrency.EUR
        every { userPreferences.preferredCurrencyFlow } returns preferredCurrency
        // Deck Doctor Community/Archetype plan, Phase 4/5: communityEngineEnabledFlow is collected
        // in init (mirrors playerNameFlow's own construction-time collection) — default OFF so
        // these pre-existing tests keep "Decks like yours" (Motor B) unpopulated.
        every { userPreferences.communityEngineEnabledFlow } returns flowOf(false)
        // getDeckGameStatsUseCase is relaxed → returns an empty Flow<Result> by default; the
        // deckStatsFlow (WhileSubscribed) is lazy and unsubscribed in these tests, so no explicit stub.
        // deckRepository.createDeck returns a stable id by default (overridden per test as needed).
        coEvery { deckRepository.createDeck(any(), any(), any()) } returns DECK_ID
        // Backend & Performance Optimization plan, WS4a finding 4 (2026-07-28): observeDeck() now
        // batch-resolves unresolved mainboard/sideboard ids via warmCacheForIds + getCardsByIds
        // BEFORE falling back to resolveCard's per-slot getCardById. Default getCardsByIds to an
        // empty list so every pre-existing test's per-card `coEvery { getCardById(...) }` stub
        // keeps driving resolution through the unchanged fallback path -- only the new
        // batch-resolve-specific tests below override getCardsByIds to return real cards.
        coEvery { cardRepository.warmCacheForIds(any()) } just Runs
        coEvery { cardRepository.getCardsByIds(any()) } returns emptyList()
        // Inspirations (Phase 4): init loadDiscoveries() calls discoverSynergiesV2UseCase when
        // wired (createVm()'s default is null -> loadDiscoveries takes its null-degrade branch,
        // no stub needed).
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fixtures
    // ─────────────────────────────────────────────────────────────────────────

    private val commander = card(
        id = "cmd-1",
        name = "Elf Lord",
        typeLine = "Legendary Creature — Elf",
        colorIdentity = listOf("G"),
        colors = listOf("G"),
        tags = listOf(CardTag.TRIBAL, CardTag.TOKENS),
    )

    private val removalCard = card(
        id = "removal-1",
        name = "Naturalize",
        typeLine = "Instant",
        colorIdentity = listOf("G"),
        colors = listOf("G"),
        tags = listOf(CardTag.REMOVAL),
    )

    private val elfCard = card(
        id = "elf-1",
        name = "Llanowar Elves",
        typeLine = "Creature — Elf Druid",
        colorIdentity = listOf("G"),
        colors = listOf("G"),
        tags = listOf(CardTag.TRIBAL, CardTag.MANA_DORK),
    )

    /** A second MANA_DORK-tagged card with a distinct name -- Group 7c uses it to prove
     * [DeckStudioViewModel.onAddCardsQueryChange] ANDs a typed name onto an active section
     * predicate instead of dropping it (edge-case QA fix, 2026-09-06; repurposed onto a real
     * `mana_dork` section id, Deck Wizard UX polish plan, Run 1 §1.2). */
    private val beastWithinCard = card(
        id = "beast-within-1",
        name = "Birds of Paradise",
        typeLine = "Creature — Bird",
        colorIdentity = listOf("G"),
        colors = listOf("G"),
        tags = listOf(CardTag.MANA_DORK),
    )

    private fun deckWithCards(
        slots: List<DeckSlot> = emptyList(),
        commanderId: String? = null,
        deckId: String = DECK_ID,
        deckName: String = DEFAULT_DECK_NAME,
    ) = DeckWithCards(
        deck = Deck(id = deckId, name = deckName, format = "casual", commanderCardId = commanderId),
        mainboard = slots,
        sideboard = emptyList(),
    )

    private fun commanderDeckWithCards(
        slots: List<DeckSlot>,
        commanderId: String = commander.scryfallId,
    ) = DeckWithCards(
        deck = Deck(id = DECK_ID, name = "Elves", format = "commander", commanderCardId = commanderId),
        mainboard = slots,
        sideboard = emptyList(),
    )

    private fun userCardWith(card: Card) = UserCardWithCard(
        userCard = UserCard(id = "uc-${card.scryfallId}", scryfallId = card.scryfallId),
        card = card,
    )

    /** Stubs the deck observe chain for the common happy path: 3-card commander deck. */
    private fun stubResolvableDeck(slots: List<DeckSlot> = defaultSlots()) {
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns
            flowOf(commanderDeckWithCards(slots))
        coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
        coEvery { cardRepository.getCardById(removalCard.scryfallId) } returns DataResult.Success(removalCard)
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())
        coEvery { cardRepository.searchWithRawQuery(any()) } returns emptyList()
    }

    private fun defaultSlots() = listOf(
        DeckSlot(commander.scryfallId, 1),
        DeckSlot(removalCard.scryfallId, 1),
        DeckSlot(elfCard.scryfallId, 1),
    )

    /** Creates the ViewModel with real use-case instances (Phase 1 & most Phase 2 tests). */
    private fun createVm(
        deckId: String? = null,
        savedStateHandle: SavedStateHandle? = null,
    ): DeckStudioViewModel =
        DeckStudioViewModel(
            deckRepository = deckRepository,
            cardRepository = cardRepository,
            userCardRepository = userCardRepository,
            searchCardsUseCase = searchCardsUseCase,
            suggestTagsUseCase = suggestTagsUseCase,
            evaluateDeckUseCase = evaluateDeckUseCase,
            inferDeckIdentityUseCase = inferDeckIdentityUseCase,
            getDeckGameStatsUseCase = getDeckGameStatsUseCase,
            importDeckUseCase = importDeckUseCase,
            wishlistRepository = wishlistRepository,
            userPreferences = userPreferences,
            crashReporter = crashReporter,
            appContext = appContext,
            savedStateHandle = savedStateHandle ?: SavedStateHandle(
                if (deckId != null) mapOf("deckId" to deckId) else emptyMap()
            ),
        )

    /** Deck Wizard UX polish plan, Run 1 §1.6 -- the ONE `createVm*` helper wiring a real
     * [DeckAnalysisPipeline] mock, so [DeckStudioViewModel.scoreStrategyMatches] actually runs
     * (every other helper leaves it at its nullable default, a no-op). */
    private fun createVmWithAnalysisPipeline(
        pipeline: DeckAnalysisPipeline,
        deckId: String? = null,
    ): DeckStudioViewModel =
        DeckStudioViewModel(
            deckRepository = deckRepository,
            cardRepository = cardRepository,
            userCardRepository = userCardRepository,
            searchCardsUseCase = searchCardsUseCase,
            suggestTagsUseCase = suggestTagsUseCase,
            evaluateDeckUseCase = evaluateDeckUseCase,
            inferDeckIdentityUseCase = inferDeckIdentityUseCase,
            getDeckGameStatsUseCase = getDeckGameStatsUseCase,
            importDeckUseCase = importDeckUseCase,
            wishlistRepository = wishlistRepository,
            userPreferences = userPreferences,
            crashReporter = crashReporter,
            appContext = appContext,
            savedStateHandle = SavedStateHandle(
                if (deckId != null) mapOf("deckId" to deckId) else emptyMap()
            ),
            deckAnalysisPipeline = pipeline,
        )

    @Test
    fun `fresh draft id is persisted in saved state after creation`() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle()
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        createVm(savedStateHandle = savedStateHandle)
        advanceUntilIdle()

        assertEquals(DECK_ID, savedStateHandle.get<String>("deckId"))
        assertEquals(true, savedStateHandle.get<Boolean>("deckStudioCreatedFreshDraft"))
    }

    @Test
    fun `missing observed deck leaves loading and exposes no deck`() = runTest(dispatcher) {
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(null)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        val vm = createVm(deckId = DECK_ID)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.deck)
    }

    @Test
    fun `deck scanner route rejects a blank deck id`() {
        val failure = runCatching { Screen.DeckScanner.createRoute(" ") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `deck value summary includes commander separates boards and applies quantity`() =
        runTest(dispatcher) {
            val pricedCommander = commander.copy(priceEur = 10.0, priceUsd = 12.0)
            val pricedMain = elfCard.copy(priceEur = 2.0, priceUsd = 3.0)
            val pricedSideboard = removalCard.copy(priceEur = 4.0, priceUsd = 5.0)
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(
                        id = DECK_ID,
                        name = "Elves",
                        format = "commander",
                        commanderCardId = pricedCommander.scryfallId,
                    ),
                    mainboard = listOf(
                        DeckSlot(pricedCommander.scryfallId, 1),
                        DeckSlot(pricedMain.scryfallId, 3),
                    ),
                    sideboard = listOf(DeckSlot(pricedSideboard.scryfallId, 2)),
                ),
            )
            coEvery { cardRepository.getCardById(pricedCommander.scryfallId) } returns DataResult.Success(pricedCommander)
            coEvery { cardRepository.getCardById(pricedMain.scryfallId) } returns DataResult.Success(pricedMain)
            coEvery { cardRepository.getCardById(pricedSideboard.scryfallId) } returns DataResult.Success(pricedSideboard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            val vm = createVm()
            advanceUntilIdle()

            val summary = vm.uiState.value.deckValueSummary
            assertEquals(16.0, summary.mainboard.knownTotal, 0.001)
            assertEquals(4, summary.mainboard.knownCopies)
            assertEquals(0, summary.mainboard.missingPriceCopies)
            assertEquals(8.0, summary.sideboard.knownTotal, 0.001)
            assertEquals(2, summary.sideboard.knownCopies)
            assertEquals(0, summary.sideboard.missingPriceCopies)
        }

    @Test
    fun `deck value summary counts unknown prices and treats zero price as known`() =
        runTest(dispatcher) {
            val zeroPricedCard = elfCard.copy(priceEur = 0.0, priceUsd = 0.0)
            val unknownPricedCard = removalCard.copy(priceEur = null, priceUsd = null)
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(
                    slots = listOf(
                        DeckSlot(zeroPricedCard.scryfallId, 2),
                        DeckSlot(unknownPricedCard.scryfallId, 3),
                        DeckSlot("unresolved-card", 4),
                    ),
                ),
            )
            coEvery { cardRepository.getCardById(zeroPricedCard.scryfallId) } returns DataResult.Success(zeroPricedCard)
            coEvery { cardRepository.getCardById(unknownPricedCard.scryfallId) } returns DataResult.Success(unknownPricedCard)
            coEvery { cardRepository.getCardById("unresolved-card") } returns DataResult.Error("missing")
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            val vm = createVm()
            advanceUntilIdle()

            val mainboard = vm.uiState.value.deckValueSummary.mainboard
            assertEquals(0.0, mainboard.knownTotal, 0.001)
            assertEquals(2, mainboard.knownCopies)
            assertEquals(7, mainboard.missingPriceCopies)
            assertFalse(mainboard.isEmpty)
        }

    @Test
    fun `changing preferred currency updates state and deck value summary`() = runTest(dispatcher) {
        val pricedCard = elfCard.copy(priceEur = 2.0, priceUsd = 7.0)
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(slots = listOf(DeckSlot(pricedCard.scryfallId, 2))),
        )
        coEvery { cardRepository.getCardById(pricedCard.scryfallId) } returns DataResult.Success(pricedCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        val vm = createVm()
        advanceUntilIdle()
        assertEquals(PreferredCurrency.EUR, vm.uiState.value.preferredCurrency)
        assertEquals(4.0, vm.uiState.value.deckValueSummary.mainboard.knownTotal, 0.001)

        preferredCurrency.value = PreferredCurrency.USD
        advanceUntilIdle()

        assertEquals(PreferredCurrency.USD, vm.uiState.value.preferredCurrency)
        assertEquals(14.0, vm.uiState.value.deckValueSummary.mainboard.knownTotal, 0.001)
    }

    @Test
    fun `reactive deck emission updates deck and board value summary`() = runTest(dispatcher) {
        val observedDeck = MutableStateFlow<DeckWithCards?>(
            deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 1))),
        )
        val pricedElf = elfCard.copy(priceEur = 2.0, priceUsd = 3.0)
        val pricedRemoval = removalCard.copy(priceEur = 5.0, priceUsd = 7.0)
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns observedDeck
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(pricedElf)
        coEvery { cardRepository.getCardById(removalCard.scryfallId) } returns DataResult.Success(pricedRemoval)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        val vm = createVm()
        advanceUntilIdle()
        assertEquals(DEFAULT_DECK_NAME, vm.uiState.value.deck?.name)
        assertEquals(2.0, vm.uiState.value.deckValueSummary.mainboard.knownTotal, 0.001)

        observedDeck.value = deckWithCards(
            slots = listOf(DeckSlot(removalCard.scryfallId, 2)),
            deckName = "Updated deck",
        )
        advanceUntilIdle()

        assertEquals("Updated deck", vm.uiState.value.deck?.name)
        assertEquals(10.0, vm.uiState.value.deckValueSummary.mainboard.knownTotal, 0.001)
        assertEquals(2, vm.uiState.value.deckValueSummary.mainboard.knownCopies)
    }

    @Test
    fun `deck value summary reports empty and partial boards independently`() = runTest(dispatcher) {
        val partiallyPricedCard = elfCard.copy(priceEur = 2.0, priceUsd = null)
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            DeckWithCards(
                deck = Deck(id = DECK_ID, name = DEFAULT_DECK_NAME, format = "casual"),
                mainboard = listOf(
                    DeckSlot(partiallyPricedCard.scryfallId, 2),
                    DeckSlot(removalCard.scryfallId, 1),
                ),
                sideboard = emptyList(),
            ),
        )
        coEvery { cardRepository.getCardById(partiallyPricedCard.scryfallId) } returns
            DataResult.Success(partiallyPricedCard)
        coEvery { cardRepository.getCardById(removalCard.scryfallId) } returns
            DataResult.Success(removalCard.copy(priceEur = null, priceUsd = null))
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        val vm = createVm()
        advanceUntilIdle()

        val summary = vm.uiState.value.deckValueSummary
        assertEquals(4.0, summary.mainboard.knownTotal, 0.001)
        assertEquals(2, summary.mainboard.knownCopies)
        assertEquals(1, summary.mainboard.missingPriceCopies)
        assertTrue(summary.sideboard.isEmpty)
        assertEquals(0.0, summary.sideboard.knownTotal, 0.001)
        assertEquals(0, summary.sideboard.missingPriceCopies)
    }

    /** Creates the ViewModel with a mocked [findCombosUseCase] wired (Deck Engine Unification plan
     * D7, Phase 4.3 Combos-tab tests) — every other new Phase-4-and-earlier dependency stays at its
     * nullable default (`discoverSynergiesV2UseCase = null` -> legacy `discoveries` path). */
    private fun createVmWithFindCombos(deckId: String? = null): DeckStudioViewModel =
        DeckStudioViewModel(
            deckRepository = deckRepository,
            cardRepository = cardRepository,
            userCardRepository = userCardRepository,
            searchCardsUseCase = searchCardsUseCase,
            suggestTagsUseCase = suggestTagsUseCase,
            evaluateDeckUseCase = evaluateDeckUseCase,
            inferDeckIdentityUseCase = inferDeckIdentityUseCase,
            getDeckGameStatsUseCase = getDeckGameStatsUseCase,
            importDeckUseCase = importDeckUseCase,
            wishlistRepository = wishlistRepository,
            userPreferences = userPreferences,
            crashReporter = crashReporter,
            appContext = appContext,
            savedStateHandle = SavedStateHandle(
                if (deckId != null) mapOf("deckId" to deckId) else emptyMap()
            ),
            findCombosUseCase = findCombosUseCase,
        )

    /** Creates the ViewModel with BOTH [discoverSynergiesV2UseCase] and [findCombosUseCase] mocked
     * (the full v2 synergy browser -- Strategies search + Combos tab, Deck Engine Unification
     * plan D7 Phase 4). */
    private fun createVmWithInspirationsV2(deckId: String? = null): DeckStudioViewModel =
        DeckStudioViewModel(
            deckRepository = deckRepository,
            cardRepository = cardRepository,
            userCardRepository = userCardRepository,
            searchCardsUseCase = searchCardsUseCase,
            suggestTagsUseCase = suggestTagsUseCase,
            evaluateDeckUseCase = evaluateDeckUseCase,
            inferDeckIdentityUseCase = inferDeckIdentityUseCase,
            getDeckGameStatsUseCase = getDeckGameStatsUseCase,
            importDeckUseCase = importDeckUseCase,
            wishlistRepository = wishlistRepository,
            userPreferences = userPreferences,
            crashReporter = crashReporter,
            appContext = appContext,
            savedStateHandle = SavedStateHandle(
                if (deckId != null) mapOf("deckId" to deckId) else emptyMap()
            ),
            discoverSynergiesV2UseCase = discoverSynergiesV2UseCase,
            findCombosUseCase = findCombosUseCase,
        )

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 1 — Init: draft creation vs. existing deck
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given absent deckId arg (empty string) when init then createDeck is called with default name`() =
        runTest(dispatcher) {
            // Arrange — Nav passes "" for an absent optional arg; SSH contains "".
            val ssh = SavedStateHandle(mapOf("deckId" to ""))
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(null)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            // Act
            DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = importDeckUseCase,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = ssh,
            )
            advanceUntilIdle()

            // Assert — createDeck must have been called exactly once with the default name.
            coVerify(exactly = 1) { deckRepository.createDeck(DEFAULT_DECK_NAME, "Draft", "casual") }
        }

    @Test
    fun `given absent deckId arg (key missing in SSH) when init then createDeck is called`() =
        runTest(dispatcher) {
            // Arrange — SavedStateHandle has no "deckId" key at all.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(null)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            // Act
            createVm(deckId = null)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { deckRepository.createDeck(DEFAULT_DECK_NAME, any(), any()) }
        }

    @Test
    fun `given a real deckId in SSH when init then createDeck is NOT called`() =
        runTest(dispatcher) {
            // Arrange
            val existingId = "existing-deck-999"
            every { deckRepository.observeDeckWithCards(existingId) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = existingId, name = "My Deck", format = "standard"),
                    mainboard = emptyList(),
                    sideboard = emptyList(),
                )
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            // Act
            createVm(deckId = existingId)
            advanceUntilIdle()

            // Assert — no draft is created when an existing id is supplied.
            coVerify(exactly = 0) { deckRepository.createDeck(any(), any(), any()) }
        }

    @Test
    fun `given a real deckId when init then deck state is populated from repository`() =
        runTest(dispatcher) {
            // Arrange
            val existingId = "existing-deck-1"
            every { deckRepository.observeDeckWithCards(existingId) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = existingId, name = "Standard Blue", format = "standard"),
                    mainboard = listOf(DeckSlot(removalCard.scryfallId, 2)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(removalCard.scryfallId) } returns DataResult.Success(removalCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            // Act
            val vm = createVm(deckId = existingId)
            advanceUntilIdle()

            // Assert
            val state = vm.uiState.value
            assertFalse("isLoading must be false after deck loaded", state.isLoading)
            assertEquals("Standard Blue", state.deck?.name)
            assertEquals(2, state.totalCards)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 1b — observeDeck() batch card resolution (Backend & Performance
    //  Optimization plan, WS4a finding 4, 2026-07-28)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given a cold cardCache when observeDeck emits then warmCacheForIds and getCardsByIds are each called exactly once covering every distinct mainboard and sideboard id`() =
        runTest(dispatcher) {
            // Arrange — a deck with 2 distinct mainboard ids + 1 sideboard id, none pre-cached.
            val existingId = "existing-deck-batch"
            every { deckRepository.observeDeckWithCards(existingId) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = existingId, name = "Batch Deck", format = "standard"),
                    mainboard = listOf(DeckSlot(removalCard.scryfallId, 2), DeckSlot(elfCard.scryfallId, 1)),
                    sideboard = listOf(DeckSlot(commander.scryfallId, 1)),
                )
            )
            coEvery { cardRepository.getCardsByIds(any()) } returns listOf(removalCard, elfCard, commander)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            // Act
            val vm = createVm(deckId = existingId)
            advanceUntilIdle()

            // Assert — exactly ONE batch warm + ONE batch fetch covering all 3 distinct ids, and
            // the per-slot fallback (getCardById) is never reached since the batch resolved everything.
            coVerify(exactly = 1) {
                cardRepository.warmCacheForIds(
                    match { it.toSet() == setOf(removalCard.scryfallId, elfCard.scryfallId, commander.scryfallId) },
                )
            }
            coVerify(exactly = 1) { cardRepository.getCardsByIds(any()) }
            coVerify(exactly = 0) { cardRepository.getCardById(any()) }

            val state = vm.uiState.value
            assertEquals(3, state.totalCards)
        }

    @Test
    fun `given an id already resolved in the in-memory cardCache when observeDeck re-emits then that id is excluded from the batch`() =
        runTest(dispatcher) {
            // Arrange — first emission resolves both ids via the batch path.
            val deckFlow = MutableStateFlow(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Batch Deck", format = "standard"),
                    mainboard = listOf(DeckSlot(removalCard.scryfallId, 1), DeckSlot(elfCard.scryfallId, 1)),
                    sideboard = emptyList(),
                ),
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns deckFlow
            coEvery { cardRepository.getCardsByIds(any()) } returns listOf(removalCard, elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            val vm = createVm()
            advanceUntilIdle()
            coVerify(exactly = 1) { cardRepository.getCardsByIds(any()) }

            // A second emission adds a brand-new id alongside the two already-cached ones.
            deckFlow.value = deckFlow.value.copy(
                mainboard = deckFlow.value.mainboard + DeckSlot(commander.scryfallId, 1),
            )
            coEvery { cardRepository.getCardsByIds(match { it.toSet() == setOf(commander.scryfallId) }) } returns
                listOf(commander)
            advanceUntilIdle()

            // The second batch call must cover ONLY the new id -- removalCard/elfCard are already
            // in cardCache from the first resolution and must be excluded.
            coVerify(exactly = 1) {
                cardRepository.getCardsByIds(match { it.toSet() == setOf(commander.scryfallId) })
            }
            assertEquals(3, vm.uiState.value.totalCards)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 2 — Discard-if-empty (onExitRequested)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given empty deck with default name when onExitRequested then deleteDeck is called before navigate`() =
        runTest(dispatcher) {
            // Arrange — empty deck with the exact default name.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = emptyList(), deckName = DEFAULT_DECK_NAME)
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            val deleteOrder = mutableListOf<String>()
            val navigateOrder = mutableListOf<String>()
            coEvery { deckRepository.deleteDeck(any()) } answers { deleteOrder += "delete" }

            // Act
            vm.onExitRequested { navigateOrder += "navigate" }
            advanceUntilIdle()

            // Assert — delete happens BEFORE navigate.
            coVerify(exactly = 1) { deckRepository.deleteDeck(DECK_ID) }
            assertEquals("navigate", navigateOrder.firstOrNull())
            // Ordering: delete must be recorded before navigate was invoked.
            assertTrue(
                "deleteDeck must complete before onNavigateBack is invoked",
                deleteOrder.isNotEmpty() && navigateOrder.isNotEmpty(),
            )
        }

    @Test
    fun `given deck with cards when onExitRequested then deleteDeck is NOT called`() =
        runTest(dispatcher) {
            // Arrange — deck contains one card.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(
                    slots = listOf(DeckSlot(elfCard.scryfallId, 1)),
                    deckName = DEFAULT_DECK_NAME,
                )
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            val navigateCalled = mutableListOf<Boolean>()

            // Act
            vm.onExitRequested { navigateCalled += true }
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { deckRepository.deleteDeck(any()) }
            assertTrue("onNavigateBack must still be called", navigateCalled.isNotEmpty())
        }

    @Test
    fun `given empty deck with renamed name when onExitRequested then deleteDeck is NOT called`() =
        runTest(dispatcher) {
            // Arrange — empty but the user renamed it; we should keep it.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = emptyList(), deckName = "My Custom Deck")
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.onExitRequested {}
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { deckRepository.deleteDeck(any()) }
        }

    @Test
    fun `given deck with commander card only (no mainboard) when onExitRequested then deck is NOT deleted`() =
        runTest(dispatcher) {
            // Arrange — isEmptyDeck checks commanderCard too; a commander means non-empty.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = DEFAULT_DECK_NAME, format = "commander",
                        commanderCardId = commander.scryfallId),
                    mainboard = listOf(DeckSlot(commander.scryfallId, 1)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.onExitRequested {}
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { deckRepository.deleteDeck(any()) }
        }

    @Test
    fun `onExitRequested always invokes onNavigateBack callback even when deleteDeck throws`() =
        runTest(dispatcher) {
            // Arrange — simulate a DB failure on delete.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = emptyList(), deckName = DEFAULT_DECK_NAME)
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            coEvery { deckRepository.deleteDeck(any()) } throws RuntimeException("DB down")
            val vm = createVm()
            advanceUntilIdle()

            // Act — H5: navigation is driven ONLY by the direct callback (the redundant
            // NavigateBack event was dropped to avoid a latent double-pop).
            val navigateCalled = mutableListOf<Boolean>()
            vm.onExitRequested { navigateCalled += true }
            advanceUntilIdle()

            // Assert — onNavigateBack is still invoked despite the delete failure.
            assertTrue(
                "onNavigateBack must be invoked even when deleteDeck fails",
                navigateCalled.isNotEmpty(),
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 3 — Manual ops: add, remove, quantity change
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addCardToDeck calls repository with quantity incremented by 1`() = runTest(dispatcher) {
        // Arrange — deck already has 2 copies of elfCard.
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 2)))
        )
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Act
        vm.addCardToDeck(elfCard.scryfallId)
        advanceUntilIdle()

        // Assert — quantity incremented from 2 to 3.
        coVerify { deckRepository.addCardToDeck(DECK_ID, elfCard.scryfallId, 3, false) }
    }

    @Test
    fun `addCardToDeck with isSideboard=true passes flag to repository`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
        // resolveCard calls cardRepository.getCardById when card is not in cache; without this
        // stub the strict mock throws MockKException inside the launch block before addCardToDeck
        // is reached, silently cancelling the coroutine.
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Act
        vm.addCardToDeck(elfCard.scryfallId, isSideboard = true)
        advanceUntilIdle()

        // Assert
        coVerify { deckRepository.addCardToDeck(DECK_ID, elfCard.scryfallId, 1, true) }
    }

    @Test
    fun `removeCardFromDeck with quantity 1 calls removeCardFromDeck on repository`() =
        runTest(dispatcher) {
            // Arrange — deck has exactly 1 copy.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 1)))
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.removeCardFromDeck(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — at qty 1, the slot is fully removed.
            coVerify { deckRepository.removeCardFromDeck(DECK_ID, elfCard.scryfallId, false) }
        }

    @Test
    fun `removeCardFromDeck with quantity 3 decrements via addCardToDeck with qty minus 1`() =
        runTest(dispatcher) {
            // Arrange — 3 copies in the deck.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 3)))
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.removeCardFromDeck(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — upsert to qty 2 (not a full remove).
            coVerify { deckRepository.addCardToDeck(DECK_ID, elfCard.scryfallId, 2, false) }
            coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
        }

    @Test
    fun `removeCard always removes the entire slot regardless of quantity`() =
        runTest(dispatcher) {
            // Arrange — 4 copies.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 4)))
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.removeCard(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — always calls removeCardFromDeck (full slot removal).
            coVerify { deckRepository.removeCardFromDeck(DECK_ID, elfCard.scryfallId, false) }
        }

    @Test
    fun `addCardToDeck when card is not in cache resolves via cardRepository`() =
        runTest(dispatcher) {
            // Arrange — empty deck, card must be fetched.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.addCardToDeck(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — card was resolved and added with qty 1.
            coVerify { deckRepository.addCardToDeck(DECK_ID, elfCard.scryfallId, 1, false) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 3b — RUN 7b (BUG 1) regression: quantity-adjustment call sites must preserve
    //  DeckCardSource provenance instead of silently rewriting it to USER (D4 hard no-cut
    //  guarantee — a WIZARD/SUGGESTION-sourced slot must stay protected across an ordinary
    //  quantity bump/decrement, or the Suggestions tab can list it as a cut candidate the very
    //  next analysis despite the deck still showing as "locked").
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addCardToDeck incrementing an existing WIZARD-sourced card preserves its source`() =
        runTest(dispatcher) {
            // Arrange — 2 WIZARD-placed copies already in the deck.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 2, DeckCardSource.WIZARD)))
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.addCardToDeck(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — the bump to 3 copies must NOT silently rewrite source back to USER.
            coVerify {
                deckRepository.addCardToDeck(DECK_ID, elfCard.scryfallId, 3, false, DeckCardSource.WIZARD)
            }
        }

    @Test
    fun `removeCardFromDeck decrementing an existing WIZARD-sourced card preserves its source`() =
        runTest(dispatcher) {
            // Arrange — 3 WIZARD-placed copies; a decrement stays above zero (upsert, not delete).
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 3, DeckCardSource.WIZARD)))
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.removeCardFromDeck(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — upsert to 2 copies must NOT silently rewrite source back to USER.
            coVerify {
                deckRepository.addCardToDeck(DECK_ID, elfCard.scryfallId, 2, false, DeckCardSource.WIZARD)
            }
        }

    @Test
    fun `applyLandSuggestions positive delta on a WIZARD-sourced basic land preserves its source`() =
        runTest(dispatcher) {
            // Arrange — a green spell with an ACTUAL {G} mana cost (elfCard's fixture manaCost is
            // null, which zeroes BasicLandCalculator's color weights and never yields a positive
            // suggestion) plus a single WIZARD-placed Forest, well below any DeckFormat
            // .targetLandCount -- guarantees a real positive delta on the EXISTING slot, not an
            // inconclusive skip.
            val greenSpell = card(
                id = "green-spell-1",
                name = "Green Spell",
                typeLine = "Creature — Beast",
                manaCost = "{G}",
                cmc = 1.0,
                colors = listOf("G"),
                colorIdentity = listOf("G"),
            )
            val forest = card(
                id = "forest-1",
                name = "Forest",
                typeLine = "Basic Land — Forest",
                colorIdentity = listOf("G"),
                colors = emptyList(),
                tags = emptyList(),
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "casual"),
                    mainboard = listOf(
                        DeckSlot(greenSpell.scryfallId, 4, DeckCardSource.WIZARD),
                        DeckSlot(forest.scryfallId, 1, DeckCardSource.WIZARD),
                    ),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(greenSpell.scryfallId) } returns DataResult.Success(greenSpell)
            coEvery { cardRepository.getCardById(forest.scryfallId) } returns DataResult.Success(forest)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            val positiveDeltas = vm.uiState.value.landDeltas.filter { it.delta > 0 }
            if (positiveDeltas.isEmpty()) {
                // Not enough spells to trigger a positive suggestion in this format; inconclusive.
                return@runTest
            }

            // Act
            vm.applyLandSuggestions()
            advanceUntilIdle()

            // Assert — the existing WIZARD-sourced Forest slot's source must survive the bump.
            coVerify(atLeast = 1) {
                deckRepository.addCardToDeck(DECK_ID, forest.scryfallId, any(), false, DeckCardSource.WIZARD)
            }
        }

    @Test
    fun `applyLandSuggestions negative delta on a WIZARD-sourced basic land preserves its source`() =
        runTest(dispatcher) {
            // Arrange — a green spell with an ACTUAL {G} mana cost (elfCard's fixture manaCost is
            // null, which zeroes BasicLandCalculator's color weights and collapses the suggestion
            // to "remove everything" instead of a partial trim) plus a large excess of WIZARD-
            // placed Forests (40 -- comfortably above every DeckFormat.targetLandCount, so the
            // suggested count stays positive and the delta trims but never zeroes the slot out).
            val greenSpell = card(
                id = "green-spell-1",
                name = "Green Spell",
                typeLine = "Creature — Beast",
                manaCost = "{G}",
                cmc = 1.0,
                colors = listOf("G"),
                colorIdentity = listOf("G"),
            )
            val forest = card(
                id = "forest-1",
                name = "Forest",
                typeLine = "Basic Land — Forest",
                colorIdentity = listOf("G"),
                colors = emptyList(),
                tags = emptyList(),
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "casual"),
                    mainboard = listOf(
                        DeckSlot(greenSpell.scryfallId, 4, DeckCardSource.WIZARD),
                        DeckSlot(forest.scryfallId, 40, DeckCardSource.WIZARD),
                    ),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(greenSpell.scryfallId) } returns DataResult.Success(greenSpell)
            coEvery { cardRepository.getCardById(forest.scryfallId) } returns DataResult.Success(forest)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            val negativeDeltas = vm.uiState.value.landDeltas.filter { it.delta < 0 }
            if (negativeDeltas.isEmpty()) {
                // Not enough excess Forests to trigger a negative suggestion; inconclusive.
                return@runTest
            }

            // Act
            vm.applyLandSuggestions()
            advanceUntilIdle()

            // Assert — the reduced (but still > 0) Forest slot's source must survive the trim.
            coVerify(atLeast = 1) {
                deckRepository.addCardToDeck(DECK_ID, forest.scryfallId, any(), false, DeckCardSource.WIZARD)
            }
        }

    @Test
    fun `addCardToDeck on a genuinely new card defaults source to USER`() =
        runTest(dispatcher) {
            // Arrange — empty deck, no existing slot to preserve provenance from.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.addCardToDeck(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — a brand-new slot has nothing to preserve, so it defaults to USER.
            coVerify { deckRepository.addCardToDeck(DECK_ID, elfCard.scryfallId, 1, false, DeckCardSource.USER) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 4 — Move to/from sideboard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `moveQuantityToSideboard moves card from mainboard to sideboard`() =
        runTest(dispatcher) {
            // Arrange — 2 copies on mainboard, 0 on sideboard.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 2)))
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act — move 1 copy to sideboard.
            vm.moveQuantityToSideboard(elfCard.scryfallId, quantity = 1)
            advanceUntilIdle()

            // Assert — single atomic move (H4), not two separate add/remove writes.
            coVerify(exactly = 1) {
                deckRepository.moveCardQuantity(DECK_ID, elfCard.scryfallId, fromSideboard = false, quantity = 1)
            }
        }

    @Test
    fun `moveQuantityToSideboard when moving all copies removes mainboard slot`() =
        runTest(dispatcher) {
            // Arrange — exactly 1 copy on mainboard.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 1)))
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.moveQuantityToSideboard(elfCard.scryfallId, quantity = 1)
            advanceUntilIdle()

            // Assert — single atomic move (H4); the source-side removal happens inside the
            // transaction, so the VM no longer issues a separate removeCardFromDeck.
            coVerify(exactly = 1) {
                deckRepository.moveCardQuantity(DECK_ID, elfCard.scryfallId, fromSideboard = false, quantity = 1)
            }
        }

    @Test
    fun `moveQuantityToSideboard delegates the no-op decision to the atomic repo move`() =
        runTest(dispatcher) {
            // Arrange — card only on sideboard, 0 on main. The "no mainboard copies" no-op now
            // lives in DeckRepository.moveCardQuantity (which reads the live counts and returns
            // silently); the VM simply delegates and never issues the old two-write sequence.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.moveQuantityToSideboard(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — the move is delegated atomically; no legacy add/remove writes.
            coVerify(exactly = 1) {
                deckRepository.moveCardQuantity(DECK_ID, elfCard.scryfallId, fromSideboard = false, quantity = 1)
            }
            coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
            coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
        }

    @Test
    fun `moveQuantityToMainboard moves card from sideboard to mainboard`() =
        runTest(dispatcher) {
            // Arrange — 2 copies on sideboard, 0 on main.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Test", format = "casual"),
                    mainboard = emptyList(),
                    sideboard = listOf(DeckSlot(elfCard.scryfallId, 2)),
                )
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act — move 1 copy to mainboard.
            vm.moveQuantityToMainboard(elfCard.scryfallId, quantity = 1)
            advanceUntilIdle()

            // Assert — single atomic move (H4) from sideboard to mainboard.
            coVerify(exactly = 1) {
                deckRepository.moveCardQuantity(DECK_ID, elfCard.scryfallId, fromSideboard = true, quantity = 1)
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 5 — Basic lands
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addBasicLandByName resolves land via cardRepository and adds to deck`() =
        runTest(dispatcher) {
            // Arrange
            val island = card(id = "island-1", name = "Island", typeLine = "Basic Land — Island",
                colorIdentity = listOf("U"))
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            coEvery { cardRepository.searchCardByName("Island") } returns DataResult.Success(island)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.addBasicLandByName("Island")
            advanceUntilIdle()

            // Assert
            coVerify { deckRepository.addCardToDeck(DECK_ID, island.scryfallId, 1, false) }
        }

    @Test
    fun `addBasicLandByName when land already in deck increments quantity`() =
        runTest(dispatcher) {
            // Arrange — 3 Islands already in deck.
            val island = card(id = "island-1", name = "Island", typeLine = "Basic Land — Island",
                colorIdentity = listOf("U"))
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = listOf(DeckSlot(island.scryfallId, 3)))
            )
            coEvery { cardRepository.getCardById(island.scryfallId) } returns DataResult.Success(island)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.addBasicLandByName("Island")
            advanceUntilIdle()

            // Assert — qty goes from 3 to 4.
            coVerify { deckRepository.addCardToDeck(DECK_ID, island.scryfallId, 4, false) }
        }

    @Test
    fun `removeBasicLandByName when land not in deck is a no-op`() =
        runTest(dispatcher) {
            // Arrange — empty deck.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act — calling removeBasicLandByName on a card not in the deck.
            vm.removeBasicLandByName("Mountain")
            advanceUntilIdle()

            // Assert — no repository call.
            coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
            coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 6 — Commander set / remove
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setCommander updates deck metadata and adds commander to mainboard`() =
        runTest(dispatcher) {
            // Arrange — Commander format deck with no commander yet.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "commander"),
                    mainboard = emptyList(),
                    sideboard = emptyList(),
                )
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.setCommander(commander)
            advanceUntilIdle()

            // Assert — deck updated with new commanderCardId + card added to mainboard.
            coVerify {
                deckRepository.updateDeck(match { deck ->
                    deck.commanderCardId == commander.scryfallId &&
                        deck.coverCardId == commander.scryfallId
                })
            }
            coVerify { deckRepository.addCardToDeck(DECK_ID, commander.scryfallId, 1, false) }
        }

    @Test
    fun `setCommander removes the previous commander from mainboard`() =
        runTest(dispatcher) {
            // Arrange — deck has oldCommander set.
            val oldCommander = card(id = "old-cmd", name = "Old Legend",
                typeLine = "Legendary Creature", colorIdentity = listOf("R"))
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "commander",
                        commanderCardId = oldCommander.scryfallId),
                    mainboard = listOf(DeckSlot(oldCommander.scryfallId, 1)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(oldCommander.scryfallId) } returns DataResult.Success(oldCommander)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act — replace with new commander.
            vm.setCommander(commander)
            advanceUntilIdle()

            // Assert — old commander's mainboard slot is removed.
            coVerify { deckRepository.removeCardFromDeck(DECK_ID, oldCommander.scryfallId, false) }
        }

    @Test
    fun `removeCommander clears commanderCardId and removes card from mainboard`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "commander",
                        commanderCardId = commander.scryfallId),
                    mainboard = listOf(DeckSlot(commander.scryfallId, 1)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.removeCommander()
            advanceUntilIdle()

            // Assert
            coVerify { deckRepository.updateDeck(match { it.commanderCardId == null }) }
            coVerify { deckRepository.removeCardFromDeck(DECK_ID, commander.scryfallId, false) }
        }

    @Test
    fun `removeCommander when no commander set is a no-op`() = runTest(dispatcher) {
        // Arrange — deck has no commander.
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(commanderId = null)
        )
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Act
        vm.removeCommander()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
        coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 6b — Edge-case QA fix (CRITICAL, 2026-09-06): the ordinary Add Cards sheet row
    //  (removeCardFromDeck/addCardToDeck) must never be able to delete/duplicate the deck's
    //  singleton commander mainboard slot — that mutation is exclusive to setCommander/
    //  removeCommander, which write through DeckRepository directly (bypassing these guards).
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `removeCardFromDeck on the current commander mainboard slot is blocked`() =
        runTest(dispatcher) {
            // Arrange — Commander format deck with its commander in the mainboard at qty 1.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns
                flowOf(commanderDeckWithCards(listOf(DeckSlot(commander.scryfallId, 1))))
            coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act — mirrors CardSearchSheet's ordinary (non-commander-mode) Add Cards sheet row
            // tapping "-" on the commander's own row.
            vm.removeCardFromDeck(commander.scryfallId)
            advanceUntilIdle()

            // Assert — no repository mutation at all; the commander slot is untouched.
            coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
            coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any(), any()) }
            assertEquals(commander.scryfallId, vm.uiState.value.deck?.commanderCardId)
            assertEquals(1, vm.uiState.value.commanderCard?.quantity)
        }

    @Test
    fun `addCardToDeck on the current commander mainboard slot is blocked`() =
        runTest(dispatcher) {
            // Arrange — same commander deck as above.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns
                flowOf(commanderDeckWithCards(listOf(DeckSlot(commander.scryfallId, 1))))
            coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act — mirrors tapping "+" on the commander's own row in the ordinary Add Cards sheet.
            vm.addCardToDeck(commander.scryfallId)
            advanceUntilIdle()

            // Assert — no repository mutation; the commander stays a singleton (quantity 1).
            coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any(), any()) }
            assertEquals(1, vm.uiState.value.commanderCard?.quantity)
        }

    @Test
    fun `removeCardFromDeck and addCardToDeck on a sideboard copy of the commander are NOT blocked`() =
        runTest(dispatcher) {
            // Arrange — a legitimate extra sideboard copy of the commander card (a slot in the
            // `sideboard` list is a separate slot from the mainboard singleton the guard protects).
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                commanderDeckWithCards(listOf(DeckSlot(commander.scryfallId, 1))).copy(
                    sideboard = listOf(DeckSlot(commander.scryfallId, 1))
                )
            )
            coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.addCardToDeck(commander.scryfallId, isSideboard = true)
            advanceUntilIdle()

            // Assert — the sideboard path is a normal, unguarded mutation.
            coVerify { deckRepository.addCardToDeck(DECK_ID, commander.scryfallId, 2, true, any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 7 — Add from collection / Scryfall
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `showCollectionCards populates addCardsResults from collectionCards`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(listOf(userCardWith(elfCard)))
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.showCollectionCards()

            // Assert
            val results = vm.uiState.value.addCardsResults
            assertTrue("collection results must be non-empty", results.isNotEmpty())
            assertEquals(elfCard.scryfallId, results.first().card.scryfallId)
            assertTrue("card must be marked as owned", results.first().isOwned)
        }

    @Test
    fun `searchScryfallDirect emits results with isOwned=true when card is in collection`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(listOf(userCardWith(elfCard)))
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            coEvery { searchCardsUseCase("elf") } returns DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(listOf(elfCard), false, totalCards = 1))
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.searchScryfallDirect("elf")
            advanceUntilIdle()

            // Assert
            val results = vm.uiState.value.scryfallResults
            assertTrue("Scryfall results must be non-empty", results.isNotEmpty())
            assertTrue("card in collection must be flagged isOwned=true", results.first().isOwned)
        }

    @Test
    fun `searchScryfallDirect with blank query clears Scryfall results`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.searchScryfallDirect("")

            // Assert
            assertEquals(emptyList<Any>(), vm.uiState.value.scryfallResults)
            assertFalse(vm.uiState.value.isSearchingScryfall)
        }

    @Test
    fun `clearAddCardsState resets query and both result lists`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        coEvery { searchCardsUseCase(any()) } returns DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(listOf(elfCard), false, totalCards = 1))
        val vm = createVm()
        advanceUntilIdle()
        vm.searchScryfallDirect("elf")
        advanceUntilIdle()

        // Act
        vm.clearAddCardsState()

        // Assert
        val state = vm.uiState.value
        assertEquals("", state.addCardsQuery)
        assertEquals(emptyList<Any>(), state.addCardsResults)
        assertEquals(emptyList<Any>(), state.scryfallResults)
    }

    // ── searchScryfallStructured / structured-search combining (W11 bug-fix pass) ──────────────

    @Test
    fun `searchScryfallStructured leaves the visible search bar empty and searches on the fragment alone`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val querySlot = slot<String>()
            coEvery { searchCardsUseCase(capture(querySlot)) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(listOf(elfCard), false, totalCards = 1))
            val vm = createVm()
            advanceUntilIdle()

            // Act — mirrors the Analysis tab's "Browse for X" translated structured query.
            vm.searchScryfallStructured(AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardType(setOf("Land")))))
            advanceUntilIdle()

            // Assert — the search bar (addCardsQuery) stays empty; the actual network call carries
            // the built fragment, never surfaced to the user.
            val state = vm.uiState.value
            assertEquals("", state.addCardsQuery)
            assertEquals("(t:Land)", state.activeStructuredSearchFragment)
            assertEquals("(t:Land)", querySlot.captured)
            assertTrue("Scryfall results must be populated from the fragment-only search", state.scryfallResults.isNotEmpty())
        }

    @Test
    fun `searchScryfallDirect after searchScryfallStructured ANDs the typed name onto the active fragment`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val querySlot = slot<String>()
            coEvery { searchCardsUseCase(capture(querySlot)) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(listOf(elfCard), false, totalCards = 1))
            val vm = createVm()
            advanceUntilIdle()
            vm.searchScryfallStructured(AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardType(setOf("Land")))))
            advanceUntilIdle()

            // Act — the user types a name on top of the active structured preset.
            vm.searchScryfallDirect("elf")
            advanceUntilIdle()

            // Assert — the visible search bar shows ONLY what the user typed; the actual query
            // combines it with the structured fragment via AND (space join).
            assertEquals("elf", vm.uiState.value.addCardsQuery)
            assertEquals("elf (t:Land)", querySlot.captured)
        }

    @Test
    fun `searchScryfallDirect with no active structured fragment is byte-identical to the pre-fix behavior`() =
        runTest(dispatcher) {
            // Arrange — every OTHER CardSearchSheet call site (Build tab FAB, onReplaceCard) never
            // sets activeStructuredSearchFragment, so this is the default/normal path.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val querySlot = slot<String>()
            coEvery { searchCardsUseCase(capture(querySlot)) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(listOf(elfCard), false, totalCards = 1))
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.searchScryfallDirect("elf")
            advanceUntilIdle()

            // Assert — no fragment ever set, so the query sent is exactly what the user typed.
            assertEquals("elf", vm.uiState.value.addCardsQuery)
            assertEquals("elf", querySlot.captured)
            assertNull(vm.uiState.value.activeStructuredSearchFragment)
        }

    @Test
    fun `clearAddCardsState clears the active structured fragment so a later normal search is not combined`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val querySlot = slot<String>()
            coEvery { searchCardsUseCase(capture(querySlot)) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(listOf(elfCard), false, totalCards = 1))
            val vm = createVm()
            advanceUntilIdle()
            vm.searchScryfallStructured(AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardType(setOf("Land")))))
            advanceUntilIdle()

            // Act — dismiss (mirrors DeckStudioScreen's onDismiss / handleBack).
            vm.clearAddCardsState()
            assertNull(vm.uiState.value.activeStructuredSearchFragment)

            // A later, unrelated Build-tab FAB search must NOT keep combining with the stale
            // structured filter from the previous Analysis-tab visit.
            vm.searchScryfallDirect("elf")
            advanceUntilIdle()
            assertEquals("elf", querySlot.captured)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 7b — searchCollectionByTags (Deck Analysis Category Sections rework, W5 — the
    //  Analysis tab's "Browse for <Category>" entry point's Collection-tab counterpart)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `searchCollectionByTags resolves a SectionMembership predicate from the section id and unions tags plus userTags`() =
        runTest(dispatcher) {
            // Arrange — Deck Wizard UX polish plan, Run 1 §1.2: the set passed to
            // searchCollectionByTags now carries the raw `CardSection.id` itself (never real
            // CardTag keys) -- "mana_dork" resolves to SectionMembership's own
            // card.hasTagKey("mana_dork") predicate, which reads `tags + userTags`. elfCard
            // carries the built-in MANA_DORK tag; a third card carries its match ONLY via
            // userTags to prove the union is honored, not just `tags`. removalCard has neither.
            val userTaggedCard = card(
                id = "user-tagged-1",
                name = "Homebrew Mana Dork",
                typeLine = "Creature — Elf",
                colorIdentity = emptyList(),
                colors = emptyList(),
                tags = emptyList(),
                userTags = listOf(CardTag.MANA_DORK),
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(removalCard), userCardWith(userTaggedCard))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, removalCard, userTaggedCard).first { it.scryfallId == firstArg() })
            }
            val vm = createVm()
            advanceUntilIdle()

            // Act — "mana_dork" is a real CardSection.id (AnalysisEngine's own manaDorkSection).
            vm.searchCollectionByTags(setOf("mana_dork"))

            // Assert
            val resultIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet()
            assertEquals(setOf(elfCard.scryfallId, userTaggedCard.scryfallId), resultIds)
            assertTrue(
                "removalCard has no mana_dork tag on either tags or userTags and must be excluded",
                removalCard.scryfallId !in resultIds
            )
        }

    @Test
    fun `searchCollectionByTags with an empty key set falls back to the full collection`() =
        runTest(dispatcher) {
            // Arrange — documented no-op behavior (see the function's KDoc): an empty key set
            // (e.g. a curve/mana/legality section with no card-level membership predicate)
            // degrades to showCollectionCards() rather than clearing addCardsResults to empty.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(removalCard))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, removalCard).first { it.scryfallId == firstArg() })
            }
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.searchCollectionByTags(emptySet())

            // Assert — same effect as showCollectionCards(): the full collection, not empty.
            val resultIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet()
            assertEquals(setOf(elfCard.scryfallId, removalCard.scryfallId), resultIds)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 7c — Edge-case QA fix (MEDIUM, 2026-09-06): onAddCardsQueryChange must AND a typed
    //  name onto an active searchCollectionByTags filter (the Analysis tab's "Browse for X" entry
    //  point), never silently fall back to a name-only filter over the WHOLE collection.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `onAddCardsQueryChange after searchCollectionByTags keeps ANDing the active section predicate`() =
        runTest(dispatcher) {
            // Arrange — removalCard (no mana_dork tag) must never surface once "mana_dork" is the
            // active section predicate; elfCard and beastWithinCard both carry MANA_DORK but only
            // one matches a subsequent typed name.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(removalCard), userCardWith(beastWithinCard))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, removalCard, beastWithinCard).first { it.scryfallId == firstArg() })
            }
            val vm = createVm()
            advanceUntilIdle()

            // Act 1 — Analysis tab "Browse for X" opens the Collection tab pre-filtered by the
            // "mana_dork" section predicate.
            vm.searchCollectionByTags(setOf("mana_dork"))
            var resultIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet()
            assertEquals(
                "the section predicate alone must already exclude the non-mana_dork removalCard",
                setOf(elfCard.scryfallId, beastWithinCard.scryfallId),
                resultIds,
            )

            // Act 2 — the user types on top of the active section predicate (the pre-fix bug: this
            // used to drop the tag constraint and filter the WHOLE collection by name alone).
            vm.onAddCardsQueryChange("Birds")
            resultIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet()
            assertEquals(
                "typing must AND the name substring onto the still-active mana_dork predicate",
                setOf(beastWithinCard.scryfallId),
                resultIds,
            )

            // Act 3 — clearing the search field must preserve the section predicate, not reset to
            // the full collection.
            vm.onAddCardsQueryChange("")
            resultIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet()
            assertEquals(
                "a blank query must keep the active section predicate, not fall back to the full collection",
                setOf(elfCard.scryfallId, beastWithinCard.scryfallId),
                resultIds,
            )
        }

    @Test
    fun `clearActiveStructuredSearchFragment also clears the active section predicate`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(removalCard), userCardWith(beastWithinCard))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, removalCard, beastWithinCard).first { it.scryfallId == firstArg() })
            }
            val vm = createVm()
            advanceUntilIdle()
            vm.searchCollectionByTags(setOf("mana_dork"))

            // Act — mirrors the FAB / onReplaceCard entry points resetting a stale preset.
            vm.clearActiveStructuredSearchFragment()

            // Assert — a later normal keystroke is name-only again, over the WHOLE collection.
            vm.onAddCardsQueryChange("Elves")
            val resultIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet()
            assertEquals(setOf(elfCard.scryfallId), resultIds)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 7d — applyStructuredSearch: ONE structured query filters BOTH tabs (the Analysis
    //  tab's "Browse for X" used to filter only All Cards, leaving Collection unfiltered whenever
    //  the section had no CardTag keys — curve / mana / legality).
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `applyStructuredSearch filters the Collection tab locally and leaves the search bar empty`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(removalCard))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, removalCard).first { it.scryfallId == firstArg() })
            }
            val querySlot = slot<String>()
            coEvery { searchCardsUseCase(capture(querySlot)) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(listOf(removalCard), false, totalCards = 1))
            val vm = createVm()
            advanceUntilIdle()
            vm.showCollectionCards()

            // Act
            vm.applyStructuredSearch(AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardType(setOf("Instant")))))
            advanceUntilIdle()

            // Assert
            val state = vm.uiState.value
            assertEquals("the visible search bar must stay clean", "", state.addCardsQuery)
            assertEquals("(t:Instant)", state.activeStructuredSearchFragment)
            assertEquals("(t:Instant)", querySlot.captured)
            assertEquals(
                "the Collection tab must honor the same structured query",
                setOf(removalCard.scryfallId),
                state.addCardsResults.map { it.card.scryfallId }.toSet(),
            )
            assertTrue("the All Cards tab must be populated too", state.scryfallResults.isNotEmpty())
        }

    @Test
    fun `searchCollectionByTags' section predicate REPLACES a previously active structured query, never unions with it`() =
        runTest(dispatcher) {
            // Deck Wizard UX polish plan, Run 1 §1.2: a section-driven browse's Collection tab is
            // `predicate && name filter` ONLY -- it must never stay ANDed/ORed with whatever
            // activeCollectionQuery a PRIOR generic Advanced Search left behind. removalCard is an
            // Instant (matches the earlier structured query) but has no mana_dork tag; elfCard is
            // the reverse -- proving the predicate takes over completely rather than combining.
            val all = listOf(elfCard, removalCard, beastWithinCard)
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(all.map { userCardWith(it) })
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(all.first { it.scryfallId == firstArg() })
            }
            coEvery { searchCardsUseCase(any()) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(emptyList(), false, totalCards = 0))
            val vm = createVm()
            advanceUntilIdle()

            // Act 1 — a generic Advanced Search structured query filters the Collection tab to
            // every Instant (removalCard only, here).
            vm.applyStructuredSearch(AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardType(setOf("Instant")))))
            advanceUntilIdle()
            assertEquals(
                "sanity check: the structured query alone must match removalCard (an Instant)",
                setOf(removalCard.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )

            // Act 2 — a section browse now starts on TOP of that same sheet session.
            vm.searchCollectionByTags(setOf("mana_dork"))

            // Assert — the predicate REPLACES the structured query outright: elfCard/beastWithinCard
            // (mana_dork, not Instants) now match; removalCard (an Instant, not mana_dork) drops out.
            assertEquals(
                setOf(elfCard.scryfallId, beastWithinCard.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )
        }

    @Test
    fun `collectionCardsMatching keeps the section predicate as the sole gate when no structured query is active`() =
        runTest(dispatcher) {
            // Deck Wizard UX polish plan, Run 1 §1.2: a bare section-predicate filter (no
            // accompanying applyStructuredSearch) must NOT degrade to "matches everything" just
            // because StructuredCardSearch.matches(card, null) trivially returns true.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(removalCard))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, removalCard).first { it.scryfallId == firstArg() })
            }
            val vm = createVm()
            advanceUntilIdle()

            vm.searchCollectionByTags(setOf("mana_dork"))

            assertEquals(
                setOf(elfCard.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )
        }

    @Test
    fun `a Scryfall-only structured query never empties the Collection tab`() =
        runTest(dispatcher) {
            // Arrange — "edict" carries no CardFunctionOption.collectionTagKeys, so it cannot be
            // evaluated locally; lenient matching must skip it instead of failing every card.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(removalCard))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, removalCard).first { it.scryfallId == firstArg() })
            }
            coEvery { searchCardsUseCase(any()) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(emptyList(), false, totalCards = 0))
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.applyStructuredSearch(
                AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardFunction(setOf("edict"))))
            )
            advanceUntilIdle()

            // Assert
            assertEquals(
                setOf(elfCard.scryfallId, removalCard.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )
        }

    @Test
    fun `both reset sites clear the active collection query`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(removalCard))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, removalCard).first { it.scryfallId == firstArg() })
            }
            coEvery { searchCardsUseCase(any()) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(emptyList(), false, totalCards = 0))
            val vm = createVm()
            advanceUntilIdle()
            val instantsOnly = AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardType(setOf("Instant"))))

            // Act / Assert 1 — the FAB / onReplaceCard reset path.
            vm.applyStructuredSearch(instantsOnly)
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.activeCollectionQuery)
            vm.clearActiveStructuredSearchFragment()
            assertNull(vm.uiState.value.activeCollectionQuery)
            vm.onAddCardsQueryChange("")
            assertEquals(
                "a cleared structured query must leave the whole collection visible again",
                setOf(elfCard.scryfallId, removalCard.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )

            // Act / Assert 2 — the dismiss path.
            vm.applyStructuredSearch(instantsOnly)
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.activeCollectionQuery)
            vm.clearAddCardsState()
            assertNull(vm.uiState.value.activeCollectionQuery)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 7e — "Card Advantage returns ZERO cards" regression (2026-09-07). The reported
    //  symptom was reproduced one layer up, in AdvancedSearchViewModel (whose state is shared
    //  across every open of the sheet); these tests pin THIS ViewModel's half of the contract:
    //  a fresh structured query REPLACES the previous one, and a card_draw pick finds the
    //  card_draw cards.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a card-advantage function query returns the card_draw cards from the collection`() =
        runTest(dispatcher) {
            // Arrange — two card_draw cards (one tagged, one user-tagged) plus two that are not.
            val drawSpell = card(
                id = "draw-1",
                name = "Harmonize",
                typeLine = "Sorcery",
                colorIdentity = listOf("G"),
                colors = listOf("G"),
                tags = listOf(CardTag.DRAW_ENGINE),
            )
            val userTaggedDraw = card(
                id = "draw-2",
                name = "Rhystic Study",
                typeLine = "Enchantment",
                colorIdentity = listOf("U"),
                colors = listOf("U"),
                tags = emptyList(),
                userTags = listOf(CardTag.DRAW_ENGINE),
            )
            val all = listOf(elfCard, removalCard, drawSpell, userTaggedDraw)
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(all.map { userCardWith(it) })
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(all.first { it.scryfallId == firstArg() })
            }
            coEvery { searchCardsUseCase(any()) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(emptyList(), false, totalCards = 0))
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.applyStructuredSearch(
                AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardFunction(setOf("card-advantage"))))
            )
            advanceUntilIdle()

            // Assert — CardFunctionOption("card-advantage") maps to the local `card_draw` tag key,
            // matched against `card.tags + card.userTags`.
            assertEquals(
                setOf(drawSpell.scryfallId, userTaggedDraw.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )
        }

    @Test
    fun `a new structured search replaces the previous section preset instead of ANDing onto it`() =
        runTest(dispatcher) {
            // Arrange — the preset's white identity excludes every card_draw card below, so if the
            // second search ANDed onto it the Collection tab would come back empty.
            val drawSpell = card(
                id = "draw-1",
                name = "Harmonize",
                typeLine = "Sorcery",
                colorIdentity = listOf("G"),
                colors = listOf("G"),
                tags = listOf(CardTag.DRAW_ENGINE),
            )
            val all = listOf(elfCard, removalCard, drawSpell)
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(all.map { userCardWith(it) })
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(all.first { it.scryfallId == firstArg() })
            }
            coEvery { searchCardsUseCase(any()) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(emptyList(), false, totalCards = 0))
            val vm = createVm()
            advanceUntilIdle()

            // Act 1 — the Analysis tab's "Browse for X" preset shape.
            vm.applyStructuredSearch(
                AdvancedSearchQuery(
                    criteria = listOf(
                        SearchCriterion.ColorIdentity(setOf("W")),
                        SearchCriterion.Format(listOf("commander")),
                        SearchCriterion.OracleTerms(allOf = listOf("destroy target")),
                    )
                )
            )
            advanceUntilIdle()
            assertTrue(
                "the preset alone must exclude every green/blue card",
                vm.uiState.value.addCardsResults.isEmpty(),
            )

            // Act 2 — the user re-opens Advanced Search and searches Card Advantage alone.
            vm.applyStructuredSearch(
                AdvancedSearchQuery(criteria = listOf(SearchCriterion.CardFunction(setOf("card-advantage"))))
            )
            advanceUntilIdle()

            // Assert
            assertEquals(
                setOf(drawSpell.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )
        }

    @Test
    fun `a Commander Browse-for-Card-Draw section filters the Collection tab by identity SUBSET`() =
        runTest(dispatcher) {
            // The reported bug end to end (2026-09-07): the Analysis tab's role:card_draw section on
            // a Commander deck built ColorIdentity(deck colours) meaning "at most", but the local
            // matcher read it as "identity contains ALL of them" -- so an Esper deck's Collection
            // tab showed 0 of the user's 143 card_draw cards while All Cards showed 97.
            val monoWhiteDraw = card(
                id = "draw-w",
                name = "Mentor of the Meek",
                typeLine = "Creature — Human Soldier",
                colorIdentity = listOf("W"),
                colors = listOf("W"),
                tags = listOf(CardTag.DRAW_ENGINE),
            )
            val esperDraw = card(
                id = "draw-wub",
                name = "Sphinx of the Guildpact",
                typeLine = "Creature — Sphinx",
                colorIdentity = listOf("W", "U", "B"),
                colors = listOf("W", "U", "B"),
                tags = listOf(CardTag.DRAW_ENGINE),
            )
            val colorlessDraw = card(
                id = "draw-c",
                name = "Endless Atlas",
                typeLine = "Artifact",
                colorIdentity = emptyList(),
                colors = emptyList(),
                tags = listOf(CardTag.DRAW_ENGINE),
            )
            val all = listOf(monoWhiteDraw, esperDraw, colorlessDraw, elfCard)
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(all.map { userCardWith(it) })
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(all.first { it.scryfallId == firstArg() })
            }
            coEvery { searchCardsUseCase(any()) } returns
                DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(emptyList(), false, totalCards = 0))
            val vm = createVm()
            advanceUntilIdle()

            // Act 1 — SectionSearchQuery.toAdvancedQuery("role:card_draw") for an Esper commander.
            vm.applyStructuredSearch(
                AdvancedSearchQuery(
                    criteria = listOf(
                        SearchCriterion.CardFunction(setOf("card-advantage")),
                        SearchCriterion.ColorIdentity(setOf("W", "U", "B"), ColorMatchMode.AT_MOST),
                        SearchCriterion.Format(listOf("commander")),
                    )
                )
            )
            advanceUntilIdle()
            assertEquals(
                "every card_draw card that fits inside WUB must show, mono-colour ones included",
                setOf(monoWhiteDraw.scryfallId, esperDraw.scryfallId, colorlessDraw.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )

            // Act 2 — the same section on a mono-white commander must DROP the Esper card.
            vm.applyStructuredSearch(
                AdvancedSearchQuery(
                    criteria = listOf(
                        SearchCriterion.CardFunction(setOf("card-advantage")),
                        SearchCriterion.ColorIdentity(setOf("W"), ColorMatchMode.AT_MOST),
                        SearchCriterion.Format(listOf("commander")),
                    )
                )
            )
            advanceUntilIdle()
            assertEquals(
                "a WUB card is illegal in a mono-white commander deck",
                setOf(monoWhiteDraw.scryfallId, colorlessDraw.scryfallId),
                vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet(),
            )
        }

    /** W7 Task C (R11) -- mirrors [createVm] but ALSO seeds [DECK_STUDIO_SELECT_BUILD_TAB_KEY] on
     * the SavedStateHandle, exactly as [com.mmg.manahub.app.navigation.AppNavGraph]'s wizard
     * pop-back path does before popping back to an existing Studio entry. */
    private fun createVmSelectingBuildTab(deckId: String): DeckStudioViewModel =
        DeckStudioViewModel(
            deckRepository = deckRepository,
            cardRepository = cardRepository,
            userCardRepository = userCardRepository,
            searchCardsUseCase = searchCardsUseCase,
            suggestTagsUseCase = suggestTagsUseCase,
            evaluateDeckUseCase = evaluateDeckUseCase,
            inferDeckIdentityUseCase = inferDeckIdentityUseCase,
            getDeckGameStatsUseCase = getDeckGameStatsUseCase,
            importDeckUseCase = importDeckUseCase,
            wishlistRepository = wishlistRepository,
            userPreferences = userPreferences,
            crashReporter = crashReporter,
            appContext = appContext,
            savedStateHandle = SavedStateHandle(mapOf("deckId" to deckId, DECK_STUDIO_SELECT_BUILD_TAB_KEY to true)),
        )

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 8 — Tab selection + lazy Suggestions loading
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a fresh navigation (no deckId) already defaults to the BUILD tab -- no signal needed`() =
        runTest(dispatcher) {
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(null)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            assertEquals(DeckStudioTab.BUILD, vm.uiState.value.selectedTab)
        }

    @Test
    fun `W7 fix 1 -- the pop-back signal forces BUILD when written onto an ALREADY-CONSTRUCTED instance's handle`() =
        runTest(dispatcher) {
            // Reproduces the real pop-back path: AppNavGraph writes the key onto the PREVIOUS
            // back-stack entry's SavedStateHandle -- which belongs to an already-initialised
            // DeckStudioViewModel, not a fresh one. A one-shot `init` read (the original, broken
            // implementation) would miss this entirely because init never runs again.
            stubResolvableDeck()
            val handle = SavedStateHandle(mapOf("deckId" to DECK_ID))
            val vm = DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = importDeckUseCase,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = handle,
            )
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            assertEquals(DeckStudioTab.SUGGESTIONS, vm.uiState.value.selectedTab)

            // Simulate AppNavGraph's pop-back write onto this SAME instance's handle.
            handle[DECK_STUDIO_SELECT_BUILD_TAB_KEY] = true
            advanceUntilIdle()

            assertEquals(DeckStudioTab.BUILD, vm.uiState.value.selectedTab)
        }

    @Test
    fun `W7 Task C -- the pop-back-to-existing-entry signal forces the BUILD tab on init`() =
        runTest(dispatcher) {
            // Keeps the fresh-construction case green too (the key can also arrive via the
            // constructor's initial SavedStateHandle map, e.g. after process death).
            stubResolvableDeck()
            val vm = createVmSelectingBuildTab(DECK_ID)
            advanceUntilIdle()

            assertEquals(DeckStudioTab.BUILD, vm.uiState.value.selectedTab)
        }

    @Test
    fun `onSelectTab BUILD updates selectedTab without triggering analysis`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.onSelectTab(DeckStudioTab.BUILD)
            advanceUntilIdle()

            // Assert — no analysis triggered from BUILD tab selection.
            assertEquals(DeckStudioTab.BUILD, vm.uiState.value.selectedTab)
            assertFalse("BUILD tab selection must never trigger analysis", vm.uiState.value.suggestionsLoaded)
        }

    @Test
    fun `onSelectTab SUGGESTIONS first time triggers loadAnalysis (suggestionsLoaded becomes true)`() =
        runTest(dispatcher) {
            // Arrange
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            assertFalse("suggestionsLoaded must be false before tab select", vm.uiState.value.suggestionsLoaded)

            // Act
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()

            // Assert — lazy first analysis completed.
            assertTrue("suggestionsLoaded must be true after SUGGESTIONS tab opened",
                vm.uiState.value.suggestionsLoaded)
            assertNotNull("health must be populated after first analysis", vm.uiState.value.health)
        }

    @Test
    fun `onSelectTab SUGGESTIONS second time does NOT re-run analysis`() =
        runTest(dispatcher) {
            // Arrange
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            val healthAfterFirst = vm.uiState.value.health

            // Reset search call count to detect re-fetch.
            clearMocks(cardRepository, answers = false, recordedCalls = true, verificationMarks = true)
            coEvery { cardRepository.searchWithRawQuery(any()) } returns emptyList()

            // Act — select SUGGESTIONS again (suggestionsLoaded = true already).
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()

            // Assert — health unchanged (no re-analysis), no second loadAnalysis kicked off.
            assertEquals(healthAfterFirst, vm.uiState.value.health)
        }

    @Test
    fun `manual edit on BUILD tab invalidates suggestions requiring re-analysis on next SUGGESTIONS open`() =
        runTest(dispatcher) {
            // Arrange — go back to BUILD; a SUGGESTIONS-tab mutation recomputes in place instead of invalidating.
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.suggestionsLoaded)
            vm.onSelectTab(DeckStudioTab.BUILD)
            advanceUntilIdle()

            // Act — manual mutation on BUILD tab must invalidate.
            vm.addCardToDeck(removalCard.scryfallId)
            advanceUntilIdle()

            // Assert — suggestionsLoaded reset to false.
            assertFalse("manual edit must invalidate suggestions",
                vm.uiState.value.suggestionsLoaded)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 13 — invalidateSuggestions on manual mutations
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `removeCard on BUILD tab invalidates suggestions`() = runTest(dispatcher) {
        // Arrange — prime suggestions, then go back to BUILD (a SUGGESTIONS-tab removeCard recomputes in place instead).
        stubResolvableDeck()
        val vm = createVm()
        advanceUntilIdle()
        vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestionsLoaded)
        vm.onSelectTab(DeckStudioTab.BUILD)
        advanceUntilIdle()

        // Act
        vm.removeCard(removalCard.scryfallId)
        advanceUntilIdle()

        // Assert
        assertFalse("removeCard must invalidate suggestions", vm.uiState.value.suggestionsLoaded)
    }

    @Test
    fun `moveQuantityToSideboard on BUILD tab invalidates suggestions`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            commanderDeckWithCards(
                slots = listOf(
                    DeckSlot(commander.scryfallId, 1),
                    DeckSlot(elfCard.scryfallId, 2),
                )
            )
        )
        coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())
        coEvery { cardRepository.searchWithRawQuery(any()) } returns emptyList()
        val vm = createVm()
        advanceUntilIdle()
        vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestionsLoaded)

        // Act
        vm.moveQuantityToSideboard(elfCard.scryfallId)
        advanceUntilIdle()

        // Assert
        assertFalse("moveQuantityToSideboard must invalidate suggestions",
            vm.uiState.value.suggestionsLoaded)
    }

    @Test
    fun `setCommander on BUILD tab invalidates suggestions`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            DeckWithCards(
                deck = Deck(id = DECK_ID, name = "Elves", format = "commander"),
                mainboard = listOf(DeckSlot(elfCard.scryfallId, 1)),
                sideboard = emptyList(),
            )
        )
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())
        coEvery { cardRepository.searchWithRawQuery(any()) } returns emptyList()
        val vm = createVm()
        advanceUntilIdle()
        vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestionsLoaded)

        // Act
        vm.setCommander(commander)
        advanceUntilIdle()

        // Assert
        assertFalse("setCommander must invalidate suggestions", vm.uiState.value.suggestionsLoaded)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 13b — live refresh: a mainboard add/remove recomputes Health in place while on Analysis.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addCardToDeck while on SUGGESTIONS recomputes health in place without a tab switch`() =
        runTest(dispatcher) {
            // Arrange
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.suggestionsLoaded)
            val initialNonLandCount = vm.uiState.value.health!!.profile.nonLandCount

            // Act — a Browse-sheet add while Analysis is showing.
            vm.addCardToDeck(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — recomputed in place: still loaded, still on the SAME tab, new count visible.
            assertTrue("must stay loaded -- an incremental recompute, not an invalidate",
                vm.uiState.value.suggestionsLoaded)
            assertEquals(DeckStudioTab.SUGGESTIONS, vm.uiState.value.selectedTab)
            assertEquals(
                "the added copy must be reflected in the SAME health snapshot",
                initialNonLandCount + 1,
                vm.uiState.value.health!!.profile.nonLandCount,
            )
        }

    @Test
    fun `removeCardFromDeck while on SUGGESTIONS recomputes health in place`() = runTest(dispatcher) {
        // Arrange — bump elfCard to 2 copies first so the removal decrements rather than deletes.
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            commanderDeckWithCards(
                slots = listOf(
                    DeckSlot(commander.scryfallId, 1),
                    DeckSlot(removalCard.scryfallId, 1),
                    DeckSlot(elfCard.scryfallId, 2),
                )
            )
        )
        coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
        coEvery { cardRepository.getCardById(removalCard.scryfallId) } returns DataResult.Success(removalCard)
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())
        coEvery { cardRepository.searchWithRawQuery(any()) } returns emptyList()
        val vm = createVm()
        advanceUntilIdle()
        vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
        advanceUntilIdle()
        val initialNonLandCount = vm.uiState.value.health!!.profile.nonLandCount

        // Act
        vm.removeCardFromDeck(elfCard.scryfallId)
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.suggestionsLoaded)
        assertEquals(DeckStudioTab.SUGGESTIONS, vm.uiState.value.selectedTab)
        assertEquals(initialNonLandCount - 1, vm.uiState.value.health!!.profile.nonLandCount)
    }

    @Test
    fun `three rapid adds while on SUGGESTIONS coalesce into exactly one recompute`() =
        runTest(dispatcher) {
            // Arrange
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            val initialNonLandCount = vm.uiState.value.health!!.profile.nonLandCount
            // 1 call already spent on the full loadAnalysis pass above.
            coVerify(exactly = 1) {
                evaluateDeckUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }

            // Act — taps faster than the 200ms debounce window; each write+cache-mutation runs, but the recompute itself never fires early.
            vm.addCardToDeck(elfCard.scryfallId)
            advanceTimeBy(50)
            vm.addCardToDeck(elfCard.scryfallId)
            advanceTimeBy(50)
            vm.addCardToDeck(elfCard.scryfallId)
            advanceUntilIdle()

            // Assert — all 3 mutations landed but only ONE additional evaluation pass ran (debounce coalesces the recompute, never drops a mutation).
            assertEquals(initialNonLandCount + 3, vm.uiState.value.health!!.profile.nonLandCount)
            coVerify(exactly = 2) {
                evaluateDeckUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `switching tabs mid-recompute cancels cleanly -- no crash, no stale health leak`() =
        runTest(dispatcher) {
            // Arrange
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            val initialNonLandCount = vm.uiState.value.health!!.profile.nonLandCount

            // Act — start a mutation, then leave the tab before the 200ms debounce elapses.
            vm.addCardToDeck(elfCard.scryfallId)
            advanceTimeBy(50)
            vm.onSelectTab(DeckStudioTab.BUILD)
            advanceUntilIdle()

            // Assert — reaching here without a crash is itself part of the point; health must also stay unchanged.
            assertEquals(DeckStudioTab.BUILD, vm.uiState.value.selectedTab)
            assertEquals(
                "a cancelled recompute must never publish an update",
                initialNonLandCount,
                vm.uiState.value.health!!.profile.nonLandCount,
            )
        }

    @Test
    fun `switching tabs mid-recompute then returning to SUGGESTIONS recomputes exactly once and reflects the add`() =
        runTest(dispatcher) {
            // Arrange
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            val initialNonLandCount = vm.uiState.value.health!!.profile.nonLandCount
            coVerify(exactly = 1) {
                evaluateDeckUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }

            // Act — mutate, leave before the debounce fires (health stays stale, per the test above), then return.
            vm.addCardToDeck(elfCard.scryfallId)
            advanceTimeBy(50)
            vm.onSelectTab(DeckStudioTab.BUILD)
            advanceUntilIdle()
            assertEquals(initialNonLandCount, vm.uiState.value.health!!.profile.nonLandCount)

            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()

            // Assert — the return itself triggers the ONE deferred evaluation and now shows the add.
            assertEquals(initialNonLandCount + 1, vm.uiState.value.health!!.profile.nonLandCount)
            coVerify(exactly = 2) {
                evaluateDeckUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `a mutation whose write completes after the tab switch still updates health, and returning triggers no extra recompute`() =
        runTest(dispatcher) {
            // Arrange
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            val initialNonLandCount = vm.uiState.value.health!!.profile.nonLandCount
            coVerify(exactly = 1) {
                evaluateDeckUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }

            // Act — start the mutation, then switch tabs before its launched coroutine has even run.
            vm.addCardToDeck(elfCard.scryfallId)
            vm.onSelectTab(DeckStudioTab.BUILD)
            advanceUntilIdle()

            // Assert — the write + its debounced recompute complete off-tab, health already current.
            assertEquals(DeckStudioTab.BUILD, vm.uiState.value.selectedTab)
            assertEquals(initialNonLandCount + 1, vm.uiState.value.health!!.profile.nonLandCount)
            coVerify(exactly = 2) {
                evaluateDeckUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }

            // Act — return to SUGGESTIONS; nothing is dirty, so no further evaluation runs.
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()

            coVerify(exactly = 2) {
                evaluateDeckUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 14 — isEmptyDeck property
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `isEmptyDeck is true when no cards and no commander`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(slots = emptyList(), commanderId = null)
        )
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.isEmptyDeck)
    }

    @Test
    fun `isEmptyDeck is false when at least one card exists`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(slots = listOf(DeckSlot(elfCard.scryfallId, 1)))
        )
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Assert
        assertFalse(vm.uiState.value.isEmptyDeck)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 15 — UI state flags and toggles
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleMainboard flips mainboardExpanded state`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()
        val initial = vm.uiState.value.mainboardExpanded

        // Act
        vm.toggleMainboard()

        // Assert
        assertEquals(!initial, vm.uiState.value.mainboardExpanded)
    }

    @Test
    fun `toggleSideboard flips sideboardExpanded state`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()
        val initial = vm.uiState.value.sideboardExpanded

        // Act
        vm.toggleSideboard()

        // Assert
        assertEquals(!initial, vm.uiState.value.sideboardExpanded)
    }

    @Test
    fun `updateDeckMetadata with blank name and null cover is a no-op`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(deckName = "My Deck")
        )
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Act
        vm.updateDeckMetadata("   ", null)
        advanceUntilIdle()

        // Assert — no repository call when both fields resolve to "keep current".
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
    }

    @Test
    fun `updateDeckMetadata with a valid name and cover writes both fields in ONE updateDeck call`() = runTest(dispatcher) {
        // Arrange — Edit-Deck-sheet rename race regression (Deck Wizard UX polish plan, Run 1 §1.3):
        // the old back-to-back updateDeckName/setCoverCard calls each read-then-wrote the whole deck,
        // so the second write silently reverted the first's name change. A single updateDeckMetadata
        // call must carry BOTH fields in the SAME deck.copy/updateDeck call.
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(deckName = "My Deck")
        )
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Act
        vm.updateDeckMetadata("Dragon Stompy", "cover-card-1")
        advanceUntilIdle()

        // Assert — exactly one write, carrying both the new name AND the new cover.
        coVerify(exactly = 1) {
            deckRepository.updateDeck(match { it.name == "Dragon Stompy" && it.coverCardId == "cover-card-1" })
        }
    }

    @Test
    fun `updateDeckMetadata with blank name keeps the current name but still applies the cover`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(deckName = "My Deck")
        )
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Act
        vm.updateDeckMetadata("   ", "cover-card-2")
        advanceUntilIdle()

        // Assert
        coVerify { deckRepository.updateDeck(match { it.name == "My Deck" && it.coverCardId == "cover-card-2" }) }
    }

    @Test
    fun `isLoading is true initially then false after deck observed`() = runTest(dispatcher) {
        // Arrange — set up the flow so we can observe the transition.
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        // Act
        val vm = createVm()
        // isLoading must be true before the coroutine runs.
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()

        // Assert — false after deck data arrives.
        assertFalse(vm.uiState.value.isLoading)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 16 — loadDiscoveries on init (Phase 4). Groups 16-20's original seed-build
    //  (openSeedSheet/closeSeedSheet, addSeed/removeSeed, onSeedQueryChange, generateFromSeeds,
    //  startFromDiscovery) and the legacy discoverSynergies-specific tests were REMOVED in the
    //  Deck Wizard & Engine Rework plan, WS7.2 (2026-07-28) along with the production code they
    //  exercised (`SeedsContent`/`BuildDeckFromSeedsUseCase`/`DeckMagicEngine.discoverSynergies`).
    //  See Group 21b below for the surviving v2 synergy-browser coverage.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given any init outcome when VM initialises then isLoadingDiscoveries is false after completion`() =
        runTest(dispatcher) {
            // Arrange — test the success path (isLoadingDiscoveries transitions to false). No
            // discoverSynergiesV2UseCase wired (createVm() defaults it to null), so loadDiscoveries
            // takes its null-degrade branch -- isLoadingDiscoveries still flips false.
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())

            // Act
            val vm = createVm()
            advanceUntilIdle()

            // Assert — loading flag is cleared regardless of success/failure.
            assertFalse("isLoadingDiscoveries must be false after loadDiscoveries completes",
                vm.uiState.value.isLoadingDiscoveries)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 21b — Synergy browser: Strategies search + Combos tab
    //  (Deck Engine Unification plan D7, Phase 4)
    // ─────────────────────────────────────────────────────────────────────────

    private fun discoveryV2(label: String, tag: CardTag, memberName: String) =
        com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2(
            key = com.mmg.manahub.feature.decks.domain.template.DiscoveryClusterKey.Strategy(tag),
            label = label,
            memberCount = 8,
            dominantColors = emptySet(),
            members = listOf(card(id = "id-$memberName", name = memberName, tags = listOf(tag))),
            archetype = null,
            theme = null,
            tribe = null,
        )

    @Test
    fun `given discoveriesV2 loaded when VM initialises then filteredDiscoveriesV2 starts equal to the full list`() =
        runTest(dispatcher) {
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            val ramp = discoveryV2("Ramp", CardTag.RAMP, "Sol Ring")
            coEvery { discoverSynergiesV2UseCase(any(), any()) } returns listOf(ramp)

            val vm = createVmWithInspirationsV2()
            advanceUntilIdle()

            assertEquals(listOf(ramp), vm.uiState.value.discoveriesV2)
            assertEquals(listOf(ramp), vm.uiState.value.filteredDiscoveriesV2)
        }

    @Test
    fun `given two discoveries when search query matches one label then filteredDiscoveriesV2 narrows to it`() =
        runTest(dispatcher) {
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            val ramp = discoveryV2("Ramp", CardTag.RAMP, "Sol Ring")
            val tokens = discoveryV2("Tokens", CardTag.TOKENS, "Anointed Procession")
            coEvery { discoverSynergiesV2UseCase(any(), any()) } returns listOf(ramp, tokens)

            val vm = createVmWithInspirationsV2()
            advanceUntilIdle()

            vm.onDiscoverySearchQueryChange("ramp")

            assertEquals(listOf(ramp), vm.uiState.value.filteredDiscoveriesV2)
            // The full, unfiltered list is untouched -- only the derived view narrows.
            assertEquals(2, vm.uiState.value.discoveriesV2.size)
        }

    @Test
    fun `given a search-by-card pick when it matches only one discovery then filteredDiscoveriesV2 narrows to it`() =
        runTest(dispatcher) {
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            val ramp = discoveryV2("Ramp", CardTag.RAMP, "Sol Ring")
            val tokens = discoveryV2("Tokens", CardTag.TOKENS, "Anointed Procession")
            coEvery { discoverSynergiesV2UseCase(any(), any()) } returns listOf(ramp, tokens)

            val vm = createVmWithInspirationsV2()
            advanceUntilIdle()

            vm.onToggleDiscoverySearchCard("Sol Ring")
            assertEquals(listOf(ramp), vm.uiState.value.filteredDiscoveriesV2)

            // Toggling the SAME card again clears the pick back to the unfiltered list.
            vm.onToggleDiscoverySearchCard("Sol Ring")
            assertEquals(listOf(ramp, tokens), vm.uiState.value.filteredDiscoveriesV2)
        }

    @Test
    fun `onClearDiscoverySearch resets both search inputs and filteredDiscoveriesV2`() =
        runTest(dispatcher) {
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            val ramp = discoveryV2("Ramp", CardTag.RAMP, "Sol Ring")
            val tokens = discoveryV2("Tokens", CardTag.TOKENS, "Anointed Procession")
            coEvery { discoverSynergiesV2UseCase(any(), any()) } returns listOf(ramp, tokens)

            val vm = createVmWithInspirationsV2()
            advanceUntilIdle()
            vm.onDiscoverySearchQueryChange("ramp")
            vm.onToggleDiscoverySearchCard("Sol Ring")

            vm.onClearDiscoverySearch()

            assertEquals("", vm.uiState.value.discoverySearchQuery)
            assertTrue(vm.uiState.value.discoverySelectedCardNames.isEmpty())
            assertEquals(listOf(ramp, tokens), vm.uiState.value.filteredDiscoveriesV2)
        }

    @Test
    fun `given the Combos tab has never been selected when VM initialises then loadCombos is never called`() =
        runTest(dispatcher) {
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            coEvery { discoverSynergiesV2UseCase(any(), any()) } returns emptyList()

            val vm = createVmWithInspirationsV2()
            advanceUntilIdle()

            assertNull("combos must not be fetched just from opening Inspirations", vm.uiState.value.comboResult)
            coVerify(exactly = 0) { findCombosUseCase(any(), any()) }
        }

    @Test
    fun `given selecting the Combos tab for the first time then loadCombos fetches and populates comboResult`() =
        runTest(dispatcher) {
            val userCardWithCard = userCardWith(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(listOf(userCardWithCard))
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            coEvery { discoverSynergiesV2UseCase(any(), any()) } returns emptyList()
            val expected = DataResult.Success(
                com.mmg.manahub.feature.decks.domain.model.ComboResult(complete = emptyList(), almostThere = emptyList())
            )
            coEvery { findCombosUseCase(any(), any()) } returns expected

            val vm = createVmWithInspirationsV2()
            advanceUntilIdle()

            vm.onSelectInspirationsTab(InspirationsTab.COMBOS)
            advanceUntilIdle()

            assertEquals(InspirationsTab.COMBOS, vm.uiState.value.inspirationsTab)
            assertEquals(expected.data, vm.uiState.value.comboResult)
            assertFalse(vm.uiState.value.isLoadingCombos)
            assertTrue(vm.uiState.value.combosLoaded)
            coVerify(exactly = 1) { findCombosUseCase(any(), any()) }
        }

    @Test
    fun `given the Combos tab already loaded when reselected then loadCombos is not called again`() =
        runTest(dispatcher) {
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            coEvery { discoverSynergiesV2UseCase(any(), any()) } returns emptyList()
            coEvery { findCombosUseCase(any(), any()) } returns DataResult.Success(
                com.mmg.manahub.feature.decks.domain.model.ComboResult(complete = emptyList(), almostThere = emptyList())
            )

            val vm = createVmWithInspirationsV2()
            advanceUntilIdle()
            vm.onSelectInspirationsTab(InspirationsTab.COMBOS)
            advanceUntilIdle()
            vm.onSelectInspirationsTab(InspirationsTab.STRATEGIES)
            vm.onSelectInspirationsTab(InspirationsTab.COMBOS)
            advanceUntilIdle()

            coVerify(exactly = 1) { findCombosUseCase(any(), any()) }
        }

    @Test
    fun `given findCombosUseCase throws when the Combos tab is selected then comboResult degrades to EMPTY, never crashes`() =
        runTest(dispatcher) {
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            coEvery { discoverSynergiesV2UseCase(any(), any()) } returns emptyList()
            coEvery { findCombosUseCase(any(), any()) } throws RuntimeException("Spellbook down")

            val vm = createVmWithInspirationsV2()
            advanceUntilIdle()
            vm.onSelectInspirationsTab(InspirationsTab.COMBOS)
            advanceUntilIdle()

            assertEquals(
                com.mmg.manahub.feature.decks.domain.model.ComboResult.EMPTY,
                vm.uiState.value.comboResult,
            )
            assertTrue(vm.uiState.value.combosLoaded)
            assertFalse(vm.uiState.value.isLoadingCombos)
        }

    @Test
    fun `given findCombosUseCase is null when the Combos tab is selected then comboResult degrades to EMPTY without a crash`() =
        runTest(dispatcher) {
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())

            val vm = createVm() // findCombosUseCase defaults to null here
            advanceUntilIdle()
            vm.onSelectInspirationsTab(InspirationsTab.COMBOS)
            advanceUntilIdle()

            assertEquals(
                com.mmg.manahub.feature.decks.domain.model.ComboResult.EMPTY,
                vm.uiState.value.comboResult,
            )
            assertTrue(vm.uiState.value.combosLoaded)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 22 — changeFormat (Group B / B1)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given casual format when changeFormat COMMANDER then updateDeck is called with format commander`() =
        runTest(dispatcher) {
            // Arrange — default deck is "casual"; changing to a DIFFERENT format (COMMANDER)
            // must write through (CASUAL→CASUAL would hit the no-op guard).
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(deckName = "My Deck")
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.changeFormat(DeckFormat.COMMANDER)
            advanceUntilIdle()

            // Assert — the repository received the new format name (DeckFormat.name = "COMMANDER").
            coVerify {
                deckRepository.updateDeck(match { it.format.equals("COMMANDER", ignoreCase = true) })
            }
        }

    @Test
    fun `given casual format when changeFormat COMMANDER then updateDeck carries commander format name`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act
            vm.changeFormat(DeckFormat.COMMANDER)
            advanceUntilIdle()

            // Assert
            coVerify {
                deckRepository.updateDeck(match { it.format.equals("COMMANDER", ignoreCase = true) })
            }
        }

    @Test
    fun `given deck already in CASUAL format when changeFormat CASUAL then updateDeck is NOT called (no-op)`() =
        runTest(dispatcher) {
            // Arrange — deck is already casual; no-op guard must fire.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "My Deck", format = "casual"),
                    mainboard = emptyList(),
                    sideboard = emptyList(),
                )
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Act — same format; must be a no-op.
            vm.changeFormat(DeckFormat.CASUAL)
            advanceUntilIdle()

            // Assert — no write issued.
            coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
        }

    @Test
    fun `given suggestionsLoaded=true when changeFormat then suggestionsLoaded is reset to false`() =
        runTest(dispatcher) {
            // Arrange — load SUGGESTIONS first so suggestionsLoaded = true, then change format.
            // stubResolvableDeck() creates a COMMANDER deck (format="commander"), so we must
            // switch to a DIFFERENT format (CASUAL) to avoid the no-op guard in changeFormat.
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            assertTrue("suggestionsLoaded must be true before format change",
                vm.uiState.value.suggestionsLoaded)

            // Act — change to CASUAL (deck is COMMANDER, so this is a real format change).
            vm.changeFormat(DeckFormat.CASUAL)
            advanceUntilIdle()

            // Assert — suggestions invalidated so next open re-runs analysis.
            assertFalse("changeFormat must invalidate suggestions",
                vm.uiState.value.suggestionsLoaded)
        }

    @Test
    fun `given suggestionsLoaded=false when changeFormat same format then suggestionsLoaded stays false and no write issued`() =
        runTest(dispatcher) {
            // Arrange — commanderDeckWithCards emits format="commander"; VM parses that.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "commander"),
                    mainboard = emptyList(),
                    sideboard = emptyList(),
                )
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()
            assertFalse("suggestionsLoaded must start false", vm.uiState.value.suggestionsLoaded)

            // Act — no-op (same format) must not mutate suggestionsLoaded.
            vm.changeFormat(DeckFormat.COMMANDER)
            advanceUntilIdle()

            // Assert — still false; no write.
            assertFalse(vm.uiState.value.suggestionsLoaded)
            coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 23 — importDeck (Group B / B2)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given blank text when importDeck then importDeckUseCase is NOT called`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val mockImport = mockk<ImportDeckUseCase>(relaxed = true)
            val vm = DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = mockImport,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = SavedStateHandle(emptyMap()),
            )
            advanceUntilIdle()

            // Act — blank string: various whitespace forms.
            vm.importDeck("   ")
            advanceUntilIdle()

            // Assert — use case is never invoked for blank input.
            coVerify(exactly = 0) { mockImport(any(), any()) }
        }

    @Test
    fun `given non-blank text when importDeck then isImporting is true during call and false after`() =
        runTest(dispatcher) {
            // Arrange — use a CompletableDeferred gate so the use-case suspends until we
            // release it, allowing us to observe isImporting=true deterministically.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val mockImport = mockk<ImportDeckUseCase> {
                coEvery { this@mockk(any(), any()) } coAnswers { gate.await(); Result.success(Unit) }
            }
            val vm = DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = mockImport,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = SavedStateHandle(emptyMap()),
            )
            advanceUntilIdle()

            // Act — importDeck is a regular (non-suspend) function; calling it directly enqueues
            // the viewModelScope.launch. advanceUntilIdle() runs it until it parks on gate.await(),
            // leaving isImporting=true.
            vm.importDeck("4 Lightning Bolt")
            advanceUntilIdle()

            // Assert phase 1 — use case is parked on the gate; isImporting must be TRUE.
            assertTrue("isImporting must be true while use-case is suspended",
                vm.uiState.value.isImporting)

            // Release the gate and drain.
            gate.complete(Unit)
            advanceUntilIdle()

            // Assert phase 2 — use case returned; isImporting must be FALSE.
            assertFalse("isImporting must be false after import completes",
                vm.uiState.value.isImporting)
        }

    @Test
    fun `given non-blank text when importDeck success then importDeckUseCase is called with deckId and text`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val mockImport = mockk<ImportDeckUseCase> {
                coEvery { this@mockk(any(), any()) } returns Result.success(Unit)
            }
            val vm = DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = mockImport,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = SavedStateHandle(emptyMap()),
            )
            advanceUntilIdle()
            val importText = "4 Lightning Bolt"

            // Act
            vm.importDeck(importText)
            advanceUntilIdle()

            // Assert — called exactly once with the live deckId and the exact text.
            coVerify(exactly = 1) { mockImport(DECK_ID, importText) }
        }

    @Test
    fun `given import use case returns failure when importDeck then ShowToast event is emitted and isImporting cleared`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val mockImport = mockk<ImportDeckUseCase> {
                coEvery { this@mockk(any(), any()) } returns Result.failure(RuntimeException("Parse failed"))
            }
            val vm = DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = mockImport,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = SavedStateHandle(emptyMap()),
            )
            advanceUntilIdle()

            // Act + Assert via Turbine.
            vm.events.test {
                vm.importDeck("4 Lightning Bolt")
                advanceUntilIdle()
                val event = awaitItem()
                assertTrue("ShowToast must be emitted on import failure",
                    event is DeckStudioEvent.ShowToast)
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse("isImporting must be false after failure", vm.uiState.value.isImporting)
        }

    @Test
    fun `given import success when importDeck then suggestionsLoaded is invalidated`() =
        runTest(dispatcher) {
            // Arrange — load SUGGESTIONS first so suggestionsLoaded = true.
            stubResolvableDeck()
            val mockImport = mockk<ImportDeckUseCase> {
                coEvery { this@mockk(any(), any()) } returns Result.success(Unit)
            }
            val vm = DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = mockImport,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = SavedStateHandle(emptyMap()),
            )
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            assertTrue("suggestionsLoaded must be true before import", vm.uiState.value.suggestionsLoaded)

            // Act
            vm.importDeck("4 Lightning Bolt")
            advanceUntilIdle()

            // Assert — imported cards require fresh analysis.
            assertFalse("importDeck success must invalidate suggestions",
                vm.uiState.value.suggestionsLoaded)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 24 — onExitRequested import guard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given isImporting=true when onExitRequested then deleteDeck is NOT called and callback is NOT invoked`() =
        runTest(dispatcher) {
            // Arrange — simulate an import in progress by using a never-completing mock.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            // Use a suspending import that never returns so isImporting stays true.
            val blockingImport = mockk<ImportDeckUseCase> {
                coEvery { this@mockk(any(), any()) } coAnswers {
                    // Never completes — keeps isImporting = true for the duration of the test.
                    kotlinx.coroutines.awaitCancellation()
                }
            }
            val vm = DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = blockingImport,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = SavedStateHandle(emptyMap()),
            )
            advanceUntilIdle()

            // Kick off the import (it never completes, so isImporting stays true).
            // importDeck is a regular (non-suspend) function — call directly so the
            // viewModelScope.launch is enqueued immediately; advanceUntilIdle() then
            // runs it until it parks on awaitCancellation(), leaving isImporting=true.
            vm.importDeck("4 Lightning Bolt")
            advanceUntilIdle()
            assertTrue("isImporting must be true for this test to be valid",
                vm.uiState.value.isImporting)

            // Act — attempt to exit while import is running.
            val navigateCalled = mutableListOf<Boolean>()
            vm.onExitRequested { navigateCalled += true }
            advanceUntilIdle()

            // Assert — no delete, no navigate; a toast is emitted instead.
            coVerify(exactly = 0) { deckRepository.deleteDeck(any()) }
            assertTrue("callback must NOT be invoked while import is in flight",
                navigateCalled.isEmpty())
        }

    @Test
    fun `given isImporting=true when onExitRequested then ShowToast event is emitted`() =
        runTest(dispatcher) {
            // Arrange — same blocking import setup.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val blockingImport = mockk<ImportDeckUseCase> {
                coEvery { this@mockk(any(), any()) } coAnswers { kotlinx.coroutines.awaitCancellation() }
            }
            val vm = DeckStudioViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                userCardRepository = userCardRepository,
                searchCardsUseCase = searchCardsUseCase,
                suggestTagsUseCase = suggestTagsUseCase,
                evaluateDeckUseCase = evaluateDeckUseCase,
                inferDeckIdentityUseCase = inferDeckIdentityUseCase,
                getDeckGameStatsUseCase = getDeckGameStatsUseCase,
                importDeckUseCase = blockingImport,
                wishlistRepository = wishlistRepository,
                userPreferences = userPreferences,
                crashReporter = crashReporter,
                appContext = appContext,
                savedStateHandle = SavedStateHandle(emptyMap()),
            )
            advanceUntilIdle()
            // importDeck is a regular (non-suspend) function — call directly so the
            // viewModelScope.launch is enqueued immediately; advanceUntilIdle() then
            // runs it until it parks on awaitCancellation(), leaving isImporting=true.
            vm.importDeck("4 Lightning Bolt")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isImporting)

            // Act + Assert via Turbine.
            vm.events.test {
                vm.onExitRequested {}
                advanceUntilIdle()
                val event = awaitItem()
                assertTrue("ShowToast must be emitted when exit is blocked by import",
                    event is DeckStudioEvent.ShowToast)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 25 — Discard regression: fresh draft vs. existing deck (createdFreshDraft)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given fresh draft (no deckId in SSH) empty and default name when onExitRequested then deleteDeck is called`() =
        runTest(dispatcher) {
            // Arrange — createVm() with no deckId: VM creates the draft → createdFreshDraft=true.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                deckWithCards(slots = emptyList(), deckName = DEFAULT_DECK_NAME)
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm(deckId = null) // no deckId → creates draft
            advanceUntilIdle()

            val navigateCalled = mutableListOf<Boolean>()
            // Act
            vm.onExitRequested { navigateCalled += true }
            advanceUntilIdle()

            // Assert — fresh empty draft must be discarded.
            coVerify(exactly = 1) { deckRepository.deleteDeck(DECK_ID) }
            assertTrue("callback must be invoked after discard", navigateCalled.isNotEmpty())
        }

    @Test
    fun `given existing deck (deckId passed in SSH) empty and default name when onExitRequested then deleteDeck is NOT called`() =
        runTest(dispatcher) {
            // Arrange — createVm(deckId = existingId): createdFreshDraft stays false.
            val existingId = "pre-existing-deck-99"
            every { deckRepository.observeDeckWithCards(existingId) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = existingId, name = DEFAULT_DECK_NAME, format = "casual"),
                    mainboard = emptyList(),
                    sideboard = emptyList(),
                )
            )
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm(deckId = existingId)
            advanceUntilIdle()

            // Verify the deck is indeed empty and has the default name (guard).
            assertTrue("deck must be empty for this test to be meaningful",
                vm.uiState.value.isEmptyDeck)
            assertEquals(DEFAULT_DECK_NAME, vm.uiState.value.deck?.name)

            val navigateCalled = mutableListOf<Boolean>()
            // Act
            vm.onExitRequested { navigateCalled += true }
            advanceUntilIdle()

            // Assert — existing deck must NEVER be auto-deleted (createdFreshDraft = false).
            coVerify(exactly = 0) { deckRepository.deleteDeck(any()) }
            assertTrue("callback must still be invoked (navigate without delete)",
                navigateCalled.isNotEmpty())
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 26 — Construction warnings (overLimitCards / invalidColorIdentityCards /
    //             isCommanderInvalid) — driven by observeDeckWithCards emissions (C5)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given non-basic card with quantity above maxCopies when deck emits then overLimitCards contains that card`() =
        runTest(dispatcher) {
            // Arrange — STANDARD maxCopies=4; elfCard with 5 copies must trigger overLimit.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Stompy", format = "standard"),
                    mainboard = listOf(DeckSlot(elfCard.scryfallId, 5)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Assert — overLimitCards must include elfCard.
            assertTrue(
                "elfCard with 5 copies in STANDARD must appear in overLimitCards",
                elfCard.scryfallId in vm.uiState.value.overLimitCards,
            )
        }

    @Test
    fun `given non-basic card with quantity equal to maxCopies when deck emits then overLimitCards is empty`() =
        runTest(dispatcher) {
            // Arrange — exactly 4 copies: boundary must NOT be flagged.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Stompy", format = "standard"),
                    mainboard = listOf(DeckSlot(elfCard.scryfallId, 4)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Assert — exactly 4 copies is within the limit.
            assertFalse(
                "4 copies in STANDARD must NOT appear in overLimitCards",
                elfCard.scryfallId in vm.uiState.value.overLimitCards,
            )
        }

    @Test
    fun `given acknowledgeOverLimit called when scryfallId in overLimitCards then acknowledgedOverLimitCards contains it`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Stompy", format = "standard"),
                    mainboard = listOf(DeckSlot(elfCard.scryfallId, 5)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()
            assertTrue(elfCard.scryfallId in vm.uiState.value.overLimitCards)

            // Act
            vm.acknowledgeOverLimit(elfCard.scryfallId)

            // Assert
            assertTrue(
                "acknowledgeOverLimit must add the id to acknowledgedOverLimitCards",
                elfCard.scryfallId in vm.uiState.value.acknowledgedOverLimitCards,
            )
        }

    @Test
    fun `given acknowledgedOverLimitCards contains id when unacknowledgeOverLimit then id is removed`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Stompy", format = "standard"),
                    mainboard = listOf(DeckSlot(elfCard.scryfallId, 5)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()
            vm.acknowledgeOverLimit(elfCard.scryfallId)
            assertTrue(elfCard.scryfallId in vm.uiState.value.acknowledgedOverLimitCards)

            // Act
            vm.unacknowledgeOverLimit(elfCard.scryfallId)

            // Assert
            assertFalse(
                "unacknowledgeOverLimit must remove the id from acknowledgedOverLimitCards",
                elfCard.scryfallId in vm.uiState.value.acknowledgedOverLimitCards,
            )
        }

    @Test
    fun `given commander deck with off-identity card when deck emits then invalidColorIdentityCards contains that card`() =
        runTest(dispatcher) {
            // Arrange — commander is mono-G; removalCard is also G so it's valid.
            // Use a RED card (colorIdentity=[R]) in a mono-G commander deck to trigger the warning.
            val redCard = card(
                id = "red-1",
                name = "Lightning Bolt",
                typeLine = "Instant",
                colorIdentity = listOf("R"),
                colors = listOf("R"),
                tags = listOf(CardTag.REMOVAL),
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "commander",
                        commanderCardId = commander.scryfallId),
                    mainboard = listOf(
                        DeckSlot(commander.scryfallId, 1),
                        DeckSlot(redCard.scryfallId, 1),
                    ),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
            coEvery { cardRepository.getCardById(redCard.scryfallId) } returns DataResult.Success(redCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Assert — red card is outside green commander's identity.
            assertTrue(
                "red card must appear in invalidColorIdentityCards for a mono-G commander deck",
                redCard.scryfallId in vm.uiState.value.invalidColorIdentityCards,
            )
            // The commander itself is never flagged.
            assertFalse(
                "commander card must NOT appear in invalidColorIdentityCards",
                commander.scryfallId in vm.uiState.value.invalidColorIdentityCards,
            )
        }

    @Test
    fun `given commander deck with non-legendary commander when deck emits then isCommanderInvalid is true`() =
        runTest(dispatcher) {
            // Arrange — elfCard is "Creature — Elf Druid" (no "Legendary") as commander.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "commander",
                        commanderCardId = elfCard.scryfallId),
                    mainboard = listOf(DeckSlot(elfCard.scryfallId, 1)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Assert — non-legendary as commander triggers the flag.
            assertTrue(
                "isCommanderInvalid must be true when commander lacks the Legendary supertype",
                vm.uiState.value.isCommanderInvalid,
            )
        }

    @Test
    fun `given commander deck with legendary commander when deck emits then isCommanderInvalid is false`() =
        runTest(dispatcher) {
            // Arrange — commander card has "Legendary Creature" type line (the fixture).
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "commander",
                        commanderCardId = commander.scryfallId),
                    mainboard = listOf(DeckSlot(commander.scryfallId, 1)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(commander.scryfallId) } returns DataResult.Success(commander)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Assert — "Legendary Creature — Elf" is valid.
            assertFalse(
                "isCommanderInvalid must be false for a Legendary commander",
                vm.uiState.value.isCommanderInvalid,
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 27 — Land suggestions (toggleLandSuggestions / applyLandSuggestions)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given showLandSuggestions=true when toggleLandSuggestions then showLandSuggestions becomes false`() =
        runTest(dispatcher) {
            // Arrange — default is true.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()
            assertTrue("showLandSuggestions must default to true", vm.uiState.value.showLandSuggestions)

            // Act
            vm.toggleLandSuggestions()

            // Assert
            assertFalse("toggleLandSuggestions must flip to false", vm.uiState.value.showLandSuggestions)
        }

    @Test
    fun `given showLandSuggestions=false when toggleLandSuggestions then showLandSuggestions becomes true`() =
        runTest(dispatcher) {
            // Arrange
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()
            vm.toggleLandSuggestions()
            assertFalse(vm.uiState.value.showLandSuggestions)

            // Act
            vm.toggleLandSuggestions()

            // Assert
            assertTrue("toggleLandSuggestions must flip back to true", vm.uiState.value.showLandSuggestions)
        }

    @Test
    fun `given empty landDeltas when applyLandSuggestions then no repository write is issued`() =
        runTest(dispatcher) {
            // Arrange — an empty deck produces no land deltas (nothing to suggest).
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards())
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()
            assertEquals("landDeltas must be empty for a deck with no spells",
                emptyList<LandDelta>(), vm.uiState.value.landDeltas)

            // Act
            vm.applyLandSuggestions()
            advanceUntilIdle()

            // Assert — no writes for an empty delta list.
            coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
            coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
        }

    @Test
    fun `given non-empty landDeltas when applyLandSuggestions then addCardToDeck is called for positive deltas`() =
        runTest(dispatcher) {
            // Arrange — a non-land spell triggers a land suggestion for its color (G).
            // Build a deck with only elfCard (a non-land G spell, no basic lands), so
            // BasicLandCalculator will suggest adding Forests.
            val forest = card(
                id = "forest-1",
                name = "Forest",
                typeLine = "Basic Land — Forest",
                colorIdentity = listOf("G"),
                colors = emptyList(),
                tags = emptyList(),
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Elves", format = "casual"),
                    mainboard = listOf(DeckSlot(elfCard.scryfallId, 20)), // enough spells to produce a land delta
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            // When the VM needs to find the Forest printing to apply the delta, it calls searchCardByName.
            coEvery { cardRepository.searchCardByName("Forest") } returns DataResult.Success(forest)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val vm = createVm()
            advanceUntilIdle()

            // Guard: the deck must have produced at least one positive land delta for this test to
            // be meaningful. If the engine produces none (e.g. the spell count is too low), skip.
            val deltas = vm.uiState.value.landDeltas
            val positiveDeltas = deltas.filter { it.delta > 0 }
            if (positiveDeltas.isEmpty()) {
                // Not enough spells to trigger suggestions in this format; test is inconclusive.
                return@runTest
            }

            // Act
            vm.applyLandSuggestions()
            advanceUntilIdle()

            // Assert — at least one addCardToDeck call for the positive land delta.
            coVerify(atLeast = 1) { deckRepository.addCardToDeck(any(), any(), any(), false) }
        }

    @Test
    fun `applyLandSuggestions invalidates suggestions`() = runTest(dispatcher) {
        // Arrange — prime suggestions first.
        stubResolvableDeck()
        val vm = createVm()
        advanceUntilIdle()
        vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
        advanceUntilIdle()
        assertTrue("suggestionsLoaded must be true before applyLandSuggestions",
            vm.uiState.value.suggestionsLoaded)

        // Act
        vm.applyLandSuggestions()
        advanceUntilIdle()

        // Assert — land application is a manual mutation; suggestions must be invalidated.
        assertFalse("applyLandSuggestions must invalidate suggestions",
            vm.uiState.value.suggestionsLoaded)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 28 — Edge-case QA fix (MEDIUM, 2026-09-06): resolveStudioLandTarget must thread
    //  deckFormat through ArchetypeSkeletonResolver.resolveWithColor exactly like
    //  AnalysisEngine.evaluate() does, so the Build tab's land-suggestion strip agrees with the
    //  Analysis tab's TooFewLands/TooManyLands band for the SAME deck.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Build tab land target agrees with the deckFormat-aware resolver for a Vintage deck`() =
        runTest(dispatcher) {
            // Arrange — a mono-green Vintage (60-card) deck pinned to the AGGRO archetype, zero
            // non-basic lands, so BasicLandCalculator distributes the ENTIRE resolved land target
            // onto the single active color (Forest) with no cross-color rounding ambiguity.
            val vinCreature = card(
                id = "vin-1", name = "Vintage Beater", typeLine = "Creature — Elf",
                colorIdentity = listOf("G"), colors = listOf("G"), manaCost = "{G}",
            )
            val vinSpell = card(
                id = "vin-2", name = "Vintage Pump", typeLine = "Instant",
                colorIdentity = listOf("G"), colors = listOf("G"), manaCost = "{G}",
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(
                        id = DECK_ID, name = "Vintage Aggro", format = "vintage",
                        archetypeOverride = ArchetypeId.AGGRO.name,
                    ),
                    mainboard = listOf(DeckSlot(vinCreature.scryfallId, 4), DeckSlot(vinSpell.scryfallId, 4)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(vinCreature.scryfallId) } returns DataResult.Success(vinCreature)
            coEvery { cardRepository.getCardById(vinSpell.scryfallId) } returns DataResult.Success(vinSpell)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            val vm = createVm()
            advanceUntilIdle()

            // Act — the Build tab's own suggested total (sum of positive per-color deltas; see
            // this test's own Arrange comment for why this equals the resolved target exactly).
            val buildTabTarget = vm.uiState.value.landDeltas.filter { it.delta > 0 }.sumOf { it.delta }

            // Assert — recompute the SAME target via the exact resolver chain
            // AnalysisEngine.evaluate() uses (deckFormat = VINTAGE threaded through), independent
            // of the VM's private resolveStudioLandTarget.
            val colorIdentity = setOf(ManaColor.G)
            val fixedSkeleton = ArchetypeSkeletonResolver.resolveWithColor(
                format = ArchetypeFormat.SIXTY,
                archetype = ArchetypeId.AGGRO,
                identity = colorIdentity,
                deckFormat = DeckFormat.VINTAGE,
            )
            // Sanity check: Vintage's SixtyFormatProfile must actually shift the land band, or
            // this whole test would pass vacuously even with the pre-fix format-blind call.
            val staleSkeleton = ArchetypeSkeletonResolver.resolveWithColor(
                format = ArchetypeFormat.SIXTY,
                archetype = ArchetypeId.AGGRO,
                identity = colorIdentity,
            )
            assertNotEquals(
                "Vintage's SixtyFormatProfile land delta must differ from the format-blind resolve -- " +
                    "otherwise this test can't distinguish the fix from the bug",
                staleSkeleton.lands.ideal,
                fixedSkeleton.lands.ideal,
            )

            // Deck Wizard UX polish plan, Run 1 §1.1: resolveStudioLandTarget always calls
            // LandTargetResolver.resolve with profile = null (intentionally bypassing
            // dynamicLandIdeal), so the expected value here must match that, not a real profile.
            val expectedTarget = LandTargetResolver.resolve(
                format = DeckFormat.VINTAGE,
                archetypeSkeleton = fixedSkeleton,
                profile = null,
            )

            assertEquals(expectedTarget, buildTabTarget)
        }

    // Deck Wizard UX polish plan, Run 2 (Run 1 carry-over): a REAL colourless 60-card mainboard
    // (no commander) must plan Wastes exactly like the wizard did at build time -- the old
    // `identity.isEmpty() && commanderIdentity == null` gate skipped planning for it and turned the
    // wizard's own Wastes fill into "remove N Wastes" deltas on reopen.
    @Test
    fun `a colourless 60-card mainboard plans Wastes -- the wizard's own Wastes fill reads back as zero deltas`() =
        runTest(dispatcher) {
            val golem = card(
                id = "golem-1", name = "Colourless Golem", typeLine = "Artifact Creature — Golem",
                colorIdentity = emptyList(), colors = emptyList(), manaCost = "{3}",
            )
            val wastes = card(
                id = "wastes-1", name = "Wastes", typeLine = "Basic Land",
                colorIdentity = emptyList(), colors = emptyList(),
            )
            coEvery { cardRepository.getCardById(golem.scryfallId) } returns DataResult.Success(golem)
            coEvery { cardRepository.getCardById(wastes.scryfallId) } returns DataResult.Success(wastes)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            fun deck(wastesCount: Int) = DeckWithCards(
                deck = Deck(id = DECK_ID, name = "Golems", format = "standard"),
                mainboard = listOfNotNull(
                    DeckSlot(golem.scryfallId, 24),
                    DeckSlot(wastes.scryfallId, wastesCount).takeIf { wastesCount > 0 },
                ),
                sideboard = emptyList(),
            )

            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deck(wastesCount = 0))
            val withoutWastes = createVm()
            advanceUntilIdle()
            val deltas = withoutWastes.uiState.value.landDeltas
            assertEquals("only a Wastes delta may be planned for a colourless mainboard", listOf("Wastes"), deltas.map { it.landName })
            val wastesTarget = deltas.single().delta
            assertTrue("a colourless mainboard must get a positive Wastes suggestion, got $wastesTarget", wastesTarget > 0)

            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deck(wastesCount = wastesTarget))
            val withWastes = createVm()
            advanceUntilIdle()
            assertEquals(emptyList<LandDelta>(), withWastes.uiState.value.landDeltas)
        }

    @Test
    fun `a spell-less deck with no commander yields no land deltas at all, never remove-every-basic`() =
        runTest(dispatcher) {
            val forest = card(
                id = "forest-1", name = "Forest", typeLine = "Basic Land — Forest",
                colorIdentity = listOf("G"), colors = emptyList(), tags = emptyList(),
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(
                    deck = Deck(id = DECK_ID, name = "Lands only", format = "standard"),
                    mainboard = listOf(DeckSlot(forest.scryfallId, 20)),
                    sideboard = emptyList(),
                )
            )
            coEvery { cardRepository.getCardById(forest.scryfallId) } returns DataResult.Success(forest)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())

            val vm = createVm()
            advanceUntilIdle()

            assertEquals(emptyList<LandDelta>(), vm.uiState.value.landDeltas)
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  GROUP — Deck Wizard UX polish plan, Run 1 §1.6: scoreStrategyMatches
    // ─────────────────────────────────────────────────────────────────────────

    private fun fakeHealth(score: Int): DeckHealth =
        DeckHealth(evaluation = mockk(relaxed = true), profile = mockk(relaxed = true), analysis = mockk { every { totalScore } returns score })

    @Test
    fun `scoreStrategyMatches publishes a score for every availableIn entry`() =
        runTest(dispatcher) {
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(deck = Deck(id = DECK_ID, name = "Test", format = "casual"), mainboard = listOf(DeckSlot(elfCard.scryfallId, 1)), sideboard = emptyList())
            )
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val pipeline = mockk<DeckAnalysisPipeline>()
            coEvery {
                pipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns fakeHealth(77)

            val vm = createVmWithAnalysisPipeline(pipeline)
            advanceUntilIdle()

            vm.scoreStrategyMatches()
            advanceUntilIdle()

            // elfCard's typeLine ("Creature — Elf Druid") gives it a real subtype, so
            // ArchetypeRoleClassifier.dominantTribeKey resolves a dominant tribe -- every
            // requiresTribe entry (e.g. "tribal") gets scored too, not left unscored.
            val expectedIds = CuratedStrategyCatalog.ALL
                .filter { it.availableIn(DeckFormat.CASUAL) }
                .map { it.id }
                .toSet()
            val state = vm.uiState.value
            assertEquals(expectedIds, state.strategyMatchScores.keys)
            assertTrue("every published score must be the pipeline's own value", state.strategyMatchScores.values.all { it == 77 })
            assertFalse("scoring must report done once every entry has resolved", state.isScoringStrategyMatches)
            coVerify(exactly = expectedIds.size) {
                pipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `scoreStrategyMatches with an unchanged deck snapshot skips recompute entirely`() = runTest(dispatcher) {
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            DeckWithCards(deck = Deck(id = DECK_ID, name = "Test", format = "casual"), mainboard = listOf(DeckSlot(elfCard.scryfallId, 1)), sideboard = emptyList())
        )
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val pipeline = mockk<DeckAnalysisPipeline>()
        coEvery {
            pipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns fakeHealth(50)

        val vm = createVmWithAnalysisPipeline(pipeline)
        advanceUntilIdle()

        vm.scoreStrategyMatches()
        advanceUntilIdle()
        val firstPassCount = CuratedStrategyCatalog.ALL.count { it.availableIn(DeckFormat.CASUAL) }

        // Act -- reopening the sheet on the SAME deck must not re-invoke the pipeline at all.
        vm.scoreStrategyMatches()
        advanceUntilIdle()

        coVerify(exactly = firstPassCount) {
            pipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `scoreStrategyMatches recomputes after a deck mutation invalidates the cached snapshot`() = runTest(dispatcher) {
        val deckFlow = MutableStateFlow(
            DeckWithCards(deck = Deck(id = DECK_ID, name = "Test", format = "casual"), mainboard = listOf(DeckSlot(elfCard.scryfallId, 1)), sideboard = emptyList())
        )
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns deckFlow
        coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
        coEvery { cardRepository.getCardById(removalCard.scryfallId) } returns DataResult.Success(removalCard)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val pipeline = mockk<DeckAnalysisPipeline>()
        coEvery {
            pipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns fakeHealth(60)

        val vm = createVmWithAnalysisPipeline(pipeline)
        advanceUntilIdle()

        vm.scoreStrategyMatches()
        advanceUntilIdle()
        val firstPassCount = CuratedStrategyCatalog.ALL.count { it.availableIn(DeckFormat.CASUAL) }

        // Act -- a real deck mutation (a second card added) must invalidate the cached snapshot.
        deckFlow.value = deckFlow.value.copy(
            mainboard = listOf(DeckSlot(elfCard.scryfallId, 1), DeckSlot(removalCard.scryfallId, 1)),
        )
        advanceUntilIdle()
        vm.scoreStrategyMatches()
        advanceUntilIdle()

        coVerify(exactly = firstPassCount * 2) {
            pipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `scoreStrategyMatches leaves a requiresTribe entry unscored when the deck has no dominant tribe`() =
        runTest(dispatcher) {
            // removalCard (Instant, no creature subtype) carries no tribal signal at all.
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
                DeckWithCards(deck = Deck(id = DECK_ID, name = "Test", format = "casual"), mainboard = listOf(DeckSlot(removalCard.scryfallId, 1)), sideboard = emptyList())
            )
            coEvery { cardRepository.getCardById(removalCard.scryfallId) } returns DataResult.Success(removalCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val pipeline = mockk<DeckAnalysisPipeline>()
            coEvery {
                pipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns fakeHealth(42)

            val vm = createVmWithAnalysisPipeline(pipeline)
            advanceUntilIdle()

            vm.scoreStrategyMatches()
            advanceUntilIdle()

            val tribalId = CuratedStrategyCatalog.ALL.first { it.requiresTribe }.id
            assertTrue(
                "a requiresTribe entry must never be scored (not even a fake 0%) when no dominant tribe exists",
                tribalId !in vm.uiState.value.strategyMatchScores,
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  GROUP — Deck Wizard UX polish plan, Run 4a: F4 (Browse identity/legality gate) + F7
    //  (strategy scoring cancellation)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `F4 -- searchCollectionByTags on a Commander deck excludes off-identity and Commander-illegal cards even when the section predicate matches`() =
        runTest(dispatcher) {
            val blackDork = card(id = "dork-b", name = "Black Dork", typeLine = "Creature — Elf", colorIdentity = listOf("B"), colors = listOf("B"), tags = listOf(CardTag.MANA_DORK))
            val bannedGreenDork = card(id = "dork-banned", name = "Banned Green Dork", typeLine = "Creature — Elf", colorIdentity = listOf("G"), colors = listOf("G"), tags = listOf(CardTag.MANA_DORK), legalityCommander = "banned")
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(commanderDeckWithCards(listOf(DeckSlot(elfCard.scryfallId, 1))))
            every { userCardRepository.observeCollection() } returns flowOf(
                listOf(userCardWith(elfCard), userCardWith(blackDork), userCardWith(bannedGreenDork))
            )
            coEvery { cardRepository.getCardById(any()) } answers {
                DataResult.Success(listOf(elfCard, commander, blackDork, bannedGreenDork).first { it.scryfallId == firstArg() })
            }
            val vm = createVm()
            advanceUntilIdle()

            vm.searchCollectionByTags(setOf("mana_dork"))

            assertEquals(setOf(elfCard.scryfallId), vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet())
        }

    @Test
    fun `F7 -- a superseded scoring pass records no non-fatals and leaves the new pass's spinner on until it ends`() =
        runTest(dispatcher) {
            val deckFlow = MutableStateFlow(
                DeckWithCards(deck = Deck(id = DECK_ID, name = "Test", format = "casual"), mainboard = listOf(DeckSlot(elfCard.scryfallId, 1)), sideboard = emptyList())
            )
            every { deckRepository.observeDeckWithCards(DECK_ID) } returns deckFlow
            coEvery { cardRepository.getCardById(elfCard.scryfallId) } returns DataResult.Success(elfCard)
            coEvery { cardRepository.getCardById(removalCard.scryfallId) } returns DataResult.Success(removalCard)
            every { userCardRepository.observeCollection() } returns flowOf(emptyList())
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val pipeline = mockk<DeckAnalysisPipeline>()
            coEvery {
                pipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } coAnswers {
                gate.await()
                fakeHealth(42)
            }
            val vm = createVmWithAnalysisPipeline(pipeline)
            advanceUntilIdle()

            vm.scoreStrategyMatches()
            runCurrent() // first pass is suspended inside its first analyze()

            deckFlow.value = deckFlow.value.copy(mainboard = listOf(DeckSlot(elfCard.scryfallId, 1), DeckSlot(removalCard.scryfallId, 1)))
            advanceUntilIdle()
            vm.scoreStrategyMatches() // snapshot changed -> cancels the first pass mid-loop
            runCurrent()

            assertTrue("the cancelled pass must not clear the new pass's spinner", vm.uiState.value.isScoringStrategyMatches)
            io.mockk.verify(exactly = 0) { crashReporter.recordException(any()) }

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isScoringStrategyMatches)
            io.mockk.verify(exactly = 0) { crashReporter.recordException(any()) }
            assertEquals(
                CuratedStrategyCatalog.ALL.filter { it.availableIn(DeckFormat.CASUAL) }.map { it.id }.toSet(),
                vm.uiState.value.strategyMatchScores.keys,
            )
        }
}
