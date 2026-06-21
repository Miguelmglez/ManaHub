package com.mmg.manahub.feature.playtest.presentation.setup

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.playtest.domain.model.PlaytestEligibility
import com.mmg.manahub.feature.playtest.domain.usecase.CanPlaytestDeckUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PlaytestSetupViewModel].
 *
 * Strategy: use a real [CanPlaytestDeckUseCase] (it is a pure function with no dependencies)
 * and mock [DeckRepository] + [CardDao] for deck-loading scenarios.
 */
@ExperimentalCoroutinesApi
class PlaytestSetupViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val deckRepository: DeckRepository = mockk()
    private val cardDao: CardDao = mockk()

    // ── Real use case ─────────────────────────────────────────────────────────

    private val canPlaytestDeckUseCase = CanPlaytestDeckUseCase()

    // ── SUT (created per test to reset state) ─────────────────────────────────

    private lateinit var viewModel: PlaytestSetupViewModel

    // ── Builder helpers ───────────────────────────────────────────────────────

    /**
     * Builds a minimal [CardEntity] with JSON-string list fields to satisfy the mapper.
     */
    private fun makeCardEntity(id: String): CardEntity = CardEntity(
        scryfallId        = id,
        name              = "Commander Card",
        printedName       = null,
        manaCost          = "{5}{G}{G}",
        cmc               = 7.0,
        colors            = "[\"G\"]",
        colorIdentity     = "[\"G\"]",
        typeLine          = "Legendary Creature — Beast",
        printedTypeLine   = null,
        oracleText        = null,
        printedText       = null,
        keywords          = "[]",
        power             = "7",
        toughness         = "7",
        loyalty           = null,
        setCode           = "CMD",
        setName           = "Commander",
        collectorNumber   = "1",
        rarity            = "mythic",
        releasedAt        = "2024-01-01",
        frameEffects      = "[]",
        promoTypes        = "[]",
        lang              = "en",
        imageNormal       = null,
        imageArtCrop      = null,
        imageBackNormal   = null,
        priceUsd          = null,
        priceUsdFoil      = null,
        priceEur          = null,
        priceEurFoil      = null,
        legalityStandard  = "not_legal",
        legalityPioneer   = "not_legal",
        legalityModern    = "not_legal",
        legalityCommander = "legal",
        flavorText        = null,
        artist            = null,
        scryfallUri       = "https://scryfall.com",
        cachedAt          = 0L,
        tags              = "[]",
        userTags          = "[]",
        suggestedTags     = "[]",
        relatedUris       = "{}",
        purchaseUris      = "{}",
        gameChanger       = false,
    )

    /**
     * Creates a [SavedStateHandle] with the required "deckId" argument.
     */
    private fun savedStateFor(deckId: String = "deck-test"): SavedStateHandle =
        SavedStateHandle(mapOf("deckId" to deckId))

    /**
     * Builds a [DeckWithCards] for a standard format with [cardCount] distinct cards,
     * quantity 1 each. Optionally sets commanderCardId.
     */
    private fun makeDeckWithCards(
        format: String,
        cardCount: Int,
        commanderCardId: String? = null,
        deckId: String = "deck-test",
    ): DeckWithCards {
        val slots = (1..cardCount).map { DeckSlot(scryfallId = "card-$it", quantity = 1) }
        return DeckWithCards(
            deck = Deck(
                id              = deckId,
                name            = "Test Deck",
                format          = format,
                commanderCardId = commanderCardId,
            ),
            mainboard = slots,
            sideboard = emptyList(),
        )
    }

    /**
     * Stubs [DeckRepository] and (if commanderCardId is non-null) [CardDao] for a given deck.
     */
    private fun stubDeck(
        deckWithCards: DeckWithCards,
        deckId: String = "deck-test",
    ) {
        every { deckRepository.observeDeckWithCards(deckId) } returns flowOf(deckWithCards)
        val commanderId = deckWithCards.deck.commanderCardId
        if (commanderId != null) {
            coEvery { cardDao.getById(commanderId) } returns makeCardEntity(commanderId)
        } else {
            coEvery { cardDao.getById(any()) } returns null
        }
    }

    private fun buildViewModel(deckId: String = "deck-test"): PlaytestSetupViewModel =
        PlaytestSetupViewModel(
            savedStateHandle      = savedStateFor(deckId),
            deckRepository        = deckRepository,
            cardDao               = cardDao,
            canPlaytestDeckUseCase = canPlaytestDeckUseCase,
            ioDispatcher          = testDispatcher,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // loadDeck() logs to Crashlytics outside any runCatching block, so the static
        // getInstance() must be mocked or every deck-loading test throws
        // "Default FirebaseApp is not initialized" and eligibility never resolves.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Group 1: Ineligible deck → no NavigateToHand event ────────────────────

    @Test
    fun `given standard deck with 59 cards when onDrawHand called then NavigateToHand is NOT emitted`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 59)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        val eligibility = viewModel.uiState.value.eligibility
        assertTrue("eligibility must be Ineligible for 59-card standard deck", eligibility is PlaytestEligibility.Ineligible)

        viewModel.events.test {
            viewModel.onDrawHand()
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given unsupported format when onDrawHand called then NavigateToHand is NOT emitted`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "casual", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onDrawHand()
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given ineligible deck when state is loaded then eligibility is Ineligible`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 0)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.eligibility is PlaytestEligibility.Ineligible)
    }

    // ── Group 2: Commander card missing from mainboard → Ineligible ───────────

    @Test
    fun `given commander deck where commanderCardId is NOT in mainboard then eligibility is Ineligible`() = runTest {
        // commanderCardId = "commander-001" but mainboard has no slot with that scryfallId.
        // This simulates the misconfigured commander scenario documented in CLAUDE.md invariant #4.
        val commanderId = "commander-001"
        val slots = (1..99).map { DeckSlot(scryfallId = "card-$it", quantity = 1) }
        // NOTE: commander is NOT included in the 99 mainboard slots.
        val deckWithCards = DeckWithCards(
            deck = Deck(
                id              = "deck-test",
                name            = "Commander Deck",
                format          = "commander",
                commanderCardId = commanderId,
            ),
            mainboard = slots,  // 99 cards — no commander slot
            sideboard = emptyList(),
        )
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(deckWithCards)
        coEvery { cardDao.getById(commanderId) } returns makeCardEntity(commanderId)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val eligibility = viewModel.uiState.value.eligibility
        assertTrue(
            "deck must be Ineligible when commander is not present in mainboard",
            eligibility is PlaytestEligibility.Ineligible,
        )
        val reason = (eligibility as PlaytestEligibility.Ineligible).reason
        assertTrue("reason must mention commander", reason.lowercase().contains("commander"))
    }

    @Test
    fun `given commander deck where commanderCardId IS in mainboard then eligibility is Eligible`() = runTest {
        // 100-card commander deck where one slot IS the commander.
        val commanderId = "commander-001"
        val commanderSlot = DeckSlot(scryfallId = commanderId, quantity = 1)
        val otherSlots = (1..99).map { DeckSlot(scryfallId = "card-$it", quantity = 1) }
        val deckWithCards = DeckWithCards(
            deck = Deck(
                id              = "deck-test",
                name            = "Commander Deck",
                format          = "commander",
                commanderCardId = commanderId,
            ),
            mainboard = otherSlots + commanderSlot,  // 100 cards including commander
            sideboard = emptyList(),
        )
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(deckWithCards)
        coEvery { cardDao.getById(commanderId) } returns makeCardEntity(commanderId)

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlaytestEligibility.Eligible, viewModel.uiState.value.eligibility)
    }

    // ── Group 3: onDrawHand double-tap guard ──────────────────────────────────

    @Test
    fun `given eligible deck when onDrawHand called twice rapidly then NavigateToHand is emitted only once`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            // Two rapid taps before the event is drained. The isNavigating guard must drop the
            // second one. We prove EXACTLY ONE navigation event is delivered (with the Channel,
            // a removed guard would deliver two distinct events rather than equality-collapsing
            // them as a StateFlow once did — so this assertion now genuinely fails if the guard
            // is removed).
            viewModel.onDrawHand()
            viewModel.onDrawHand()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue("first call must emit NavigateToHand", event is PlaytestSetupEvent.NavigateToHand)

            // The second tap must NOT have queued a second navigation event.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a fresh ViewModel after navigation then onDrawHand emits NavigateToHand again`() = runTest {
        // Navigation is a one-way transition: the guard is never reset within a ViewModel's
        // lifetime. On return to setup (Back) a NEW ViewModel instance is created, so a fresh
        // instance must allow navigation again. This replaces the old onEventConsumed-based
        // reset test, which no longer applies after the Channel migration.
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onDrawHand()
            advanceUntilIdle()
            assertTrue(
                "a fresh ViewModel must emit NavigateToHand on the first tap",
                awaitItem() is PlaytestSetupEvent.NavigateToHand,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 4: Standard boundary (60 / 59) ──────────────────────────────────

    @Test
    fun `given standard deck with exactly 60 cards then eligibility is Eligible`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlaytestEligibility.Eligible, viewModel.uiState.value.eligibility)
    }

    @Test
    fun `given standard deck with 59 cards then eligibility is Ineligible`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 59)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.eligibility is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given standard deck with 80 cards then eligibility is Eligible`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 80)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlaytestEligibility.Eligible, viewModel.uiState.value.eligibility)
    }

    // ── Group 5: Draft boundary (40 / 39) ────────────────────────────────────

    @Test
    fun `given draft deck with exactly 40 cards then eligibility is Eligible`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "draft", cardCount = 40)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlaytestEligibility.Eligible, viewModel.uiState.value.eligibility)
    }

    @Test
    fun `given draft deck with 39 cards then eligibility is Ineligible`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "draft", cardCount = 39)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.eligibility is PlaytestEligibility.Ineligible)
    }

    // ── Group 6: Commander boundary (99 / 100 / 101) ─────────────────────────

    @Test
    fun `given commander deck with exactly 100 cards including commander then eligibility is Eligible`() = runTest {
        val commanderId = "commander-001"
        val commanderSlot = DeckSlot(scryfallId = commanderId, quantity = 1)
        val otherSlots = (1..99).map { DeckSlot(scryfallId = "card-$it", quantity = 1) }
        val deckWithCards = DeckWithCards(
            deck = Deck(
                id              = "deck-test",
                name            = "Commander Deck",
                format          = "commander",
                commanderCardId = commanderId,
            ),
            mainboard = otherSlots + commanderSlot,
            sideboard = emptyList(),
        )
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(deckWithCards)
        coEvery { cardDao.getById(commanderId) } returns makeCardEntity(commanderId)

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlaytestEligibility.Eligible, viewModel.uiState.value.eligibility)
    }

    @Test
    fun `given commander deck with 99 cards total then eligibility is Ineligible`() = runTest {
        // commanderCardId is null (no commander assigned) — 99-card deck.
        val deckWithCards = makeDeckWithCards(format = "commander", cardCount = 99, commanderCardId = null)
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(deckWithCards)
        coEvery { cardDao.getById(any()) } returns null

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.eligibility is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given commander deck with 101 cards total then eligibility is Ineligible`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "commander", cardCount = 101, commanderCardId = null)
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(deckWithCards)
        coEvery { cardDao.getById(any()) } returns null

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.eligibility is PlaytestEligibility.Ineligible)
    }

    // ── Group 7: NavigateToHand setup carries correct deckId ──────────────────

    @Test
    fun `given eligible standard deck when onDrawHand called then NavigateToHand carries the deckId`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel(deckId = "deck-test")
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onDrawHand()
            advanceUntilIdle()
            val event = awaitItem() as PlaytestSetupEvent.NavigateToHand
            assertEquals("deck-test", event.setup.deckId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given eligible standard deck when onDrawHand called then NavigateToHand setup has correct drawCount`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setDrawCount(5)

        viewModel.events.test {
            viewModel.onDrawHand()
            advanceUntilIdle()
            val event = awaitItem() as PlaytestSetupEvent.NavigateToHand
            assertEquals(5, event.setup.drawCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 8: Deck not found scenario ─────────────────────────────────────

    @Test
    fun `given deck not found when loaded then errorMessage is set`() = runTest {
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(null)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertNull("eligibility must remain null when deck is not found", viewModel.uiState.value.eligibility)
        assertTrue("errorMessage must be set when deck is not found", !viewModel.uiState.value.errorMessage.isNullOrEmpty())
    }
}
