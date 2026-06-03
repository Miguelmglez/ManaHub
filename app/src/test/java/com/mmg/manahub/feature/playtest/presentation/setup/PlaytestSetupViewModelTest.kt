package com.mmg.manahub.feature.playtest.presentation.setup

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSlot
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.playtest.domain.model.PlaytestEligibility
import com.mmg.manahub.feature.playtest.domain.usecase.CanPlaytestDeckUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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

        viewModel.onDrawHand()

        assertNull("NavigateToHand must NOT be emitted when deck is ineligible", viewModel.events.value)
    }

    @Test
    fun `given unsupported format when onDrawHand called then NavigateToHand is NOT emitted`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "casual", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onDrawHand()

        assertNull("NavigateToHand must NOT be emitted for unsupported format", viewModel.events.value)
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
            viewModel.onDrawHand()
            viewModel.onDrawHand()  // second call while isNavigating=true — must be a no-op

            val event = awaitItem()
            assertTrue("first call must emit NavigateToHand", event is PlaytestSetupEvent.NavigateToHand)

            // After consuming the event the guard is reset.
            viewModel.onEventConsumed()

            // No second event should be queued.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given event consumed when onDrawHand called again then NavigateToHand is emitted`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onDrawHand()
        val firstEvent = viewModel.events.value
        assertTrue(firstEvent is PlaytestSetupEvent.NavigateToHand)

        // Simulate screen consuming the event and user pressing Back.
        viewModel.onEventConsumed()
        assertNull("event must be cleared after onEventConsumed", viewModel.events.value)

        // User presses "Draw Hand" again — must work.
        viewModel.onDrawHand()
        assertTrue(
            "NavigateToHand must be emitted again after isNavigating is reset",
            viewModel.events.value is PlaytestSetupEvent.NavigateToHand,
        )
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

        viewModel.onDrawHand()

        val event = viewModel.events.value as? PlaytestSetupEvent.NavigateToHand
        assertNull("event must not be null for eligible deck", null.takeIf { event == null })
        assertEquals("deck-test", event!!.setup.deckId)
    }

    @Test
    fun `given eligible standard deck when onDrawHand called then NavigateToHand setup has correct drawCount`() = runTest {
        val deckWithCards = makeDeckWithCards(format = "standard", cardCount = 60)
        stubDeck(deckWithCards)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setDrawCount(5)
        viewModel.onDrawHand()

        val event = viewModel.events.value as? PlaytestSetupEvent.NavigateToHand
        assertEquals(5, event!!.setup.drawCount)
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
