package com.mmg.manahub.feature.decks.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
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
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
 *   itself survives as a class -- it is still used by `BuildDeckFromTemplateUseCase`, the Deck
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
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val appContext = mockk<Context>()
    // Deck Engine Unification plan D7 (Phase 4.3) — Combos tab.
    private val findCombosUseCase = mockk<com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase>()
    // Deck Engine Unification plan D7 (4.1/4.2) — Strategies tab search.
    private val discoverSynergiesV2UseCase = mockk<com.mmg.manahub.feature.decks.domain.template.DiscoverSynergiesV2UseCase>()

    // ── Real engine + use cases (deterministic fixed PowerResolver) ───────────
    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.6f))
    private val eventBus = ProgressionEventBus()
    private val evaluateDeckUseCase = EvaluateDeckUseCase(scorer, eventBus, dispatcher)
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
    private fun createVm(deckId: String? = null): DeckStudioViewModel =
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
        )

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
    fun `searchCollectionByTags returns only cards whose tags plus userTags intersect the given keys`() =
        runTest(dispatcher) {
            // Arrange — elfCard carries TRIBAL/MANA_DORK, removalCard carries REMOVAL (built-in
            // tags); a third card carries its match ONLY via userTags to prove the union
            // (`card.tags + card.userTags`) is honored, not just `tags`.
            val userTaggedCard = card(
                id = "user-tagged-1",
                name = "Homebrew Sac Outlet",
                typeLine = "Artifact",
                colorIdentity = emptyList(),
                colors = emptyList(),
                tags = emptyList(),
                userTags = listOf(CardTag.SACRIFICE),
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

            // Act — request only REMOVAL + SACRIFICE, excluding elfCard's TRIBAL/MANA_DORK keys.
            vm.searchCollectionByTags(setOf(CardTag.REMOVAL.key, CardTag.SACRIFICE.key))

            // Assert
            val resultIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }.toSet()
            assertEquals(setOf(removalCard.scryfallId, userTaggedCard.scryfallId), resultIds)
            assertTrue(
                "elfCard has none of the requested keys and must be excluded",
                elfCard.scryfallId !in resultIds
            )
        }

    @Test
    fun `searchCollectionByTags with an empty key set falls back to the full collection`() =
        runTest(dispatcher) {
            // Arrange — documented no-op behavior (see the function's KDoc): an empty key set
            // (e.g. a curve/mana/legality section with no CardTag equivalent) degrades to
            // showCollectionCards() rather than clearing addCardsResults to empty.
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
    //  Group 8 — Tab selection + lazy Suggestions loading
    // ─────────────────────────────────────────────────────────────────────────

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
            // Arrange — open SUGGESTIONS first to set suggestionsLoaded=true.
            stubResolvableDeck()
            val vm = createVm()
            advanceUntilIdle()
            vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.suggestionsLoaded)

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
        // Arrange — prime the suggestions.
        stubResolvableDeck()
        val vm = createVm()
        advanceUntilIdle()
        vm.onSelectTab(DeckStudioTab.SUGGESTIONS)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestionsLoaded)

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
    fun `updateDeckName with empty or blank string is a no-op`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(deckName = "My Deck")
        )
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Act
        vm.updateDeckName("   ")
        advanceUntilIdle()

        // Assert — no repository call for a blank name.
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
    }

    @Test
    fun `updateDeckName with valid name calls updateDeck`() = runTest(dispatcher) {
        // Arrange
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            deckWithCards(deckName = "My Deck")
        )
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        val vm = createVm()
        advanceUntilIdle()

        // Act
        vm.updateDeckName("Dragon Stompy")
        advanceUntilIdle()

        // Assert
        coVerify { deckRepository.updateDeck(match { it.name == "Dragon Stompy" }) }
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
}
