package com.mmg.manahub.feature.playtest.presentation.hand

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSlot
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.playtest.domain.model.HandSnapshot
import com.mmg.manahub.feature.playtest.domain.model.PlaytestSetup
import com.mmg.manahub.feature.playtest.domain.usecase.BuildLibraryUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.DrawHandUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.LondonMulliganUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.SavePlaytestSurveyUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.SavePlaytestUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PlaytestHandViewModel].
 *
 * Strategy: inject real use-case instances (BuildLibraryUseCase, DrawHandUseCase,
 * LondonMulliganUseCase) so the ViewModel's core flow logic is tested end-to-end
 * without mocking the shuffle internals. Only DeckRepository, CardDao, SavePlaytestUseCase
 * and SavePlaytestSurveyUseCase are mocked.
 */
@ExperimentalCoroutinesApi
class PlaytestHandViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val deckRepository: DeckRepository = mockk()
    private val cardDao: CardDao = mockk()
    private val savePlaytestUseCase: SavePlaytestUseCase = mockk()
    private val savePlaytestSurveyUseCase: SavePlaytestSurveyUseCase = mockk()

    // ── Real use cases ────────────────────────────────────────────────────────

    private val buildLibraryUseCase = BuildLibraryUseCase()
    private val drawHandUseCase = DrawHandUseCase()
    private val londonMulliganUseCase = LondonMulliganUseCase()

    // ── SUT ───────────────────────────────────────────────────────────────────

    private lateinit var viewModel: PlaytestHandViewModel

    // ── Test data helpers ─────────────────────────────────────────────────────

    /**
     * Creates a minimal [Card] domain object for test purposes.
     * Reuses the exact field set expected by the full Card constructor.
     */
    private fun makeCard(id: String, name: String = "Card $id"): Card = Card(
        scryfallId    = id,
        name          = name,
        printedName   = null,
        manaCost      = null,
        cmc           = 0.0,
        colors        = emptyList(),
        colorIdentity = emptyList(),
        typeLine      = "Creature",
        printedTypeLine = null,
        oracleText    = null,
        printedText   = null,
        keywords      = emptyList(),
        power         = null,
        toughness     = null,
        loyalty       = null,
        setCode       = "TST",
        setName       = "Test",
        collectorNumber = "1",
        rarity        = "common",
        releasedAt    = "2024-01-01",
        frameEffects  = emptyList(),
        promoTypes    = emptyList(),
        lang          = "en",
        imageNormal   = null,
        imageArtCrop  = null,
        imageBackNormal = null,
        priceUsd      = null,
        priceUsdFoil  = null,
        priceEur      = null,
        priceEurFoil  = null,
        legalityStandard  = "legal",
        legalityPioneer   = "legal",
        legalityModern    = "legal",
        legalityCommander = "legal",
        flavorText    = null,
        artist        = null,
        scryfallUri   = "https://scryfall.com",
    )

    /**
     * Creates a [CardEntity] that toDomainCard() can map from, with the given scryfallId.
     * CardEntity stores lists as JSON strings; use "[]" for empty list fields and
     * "{}" for empty map fields to satisfy the mapper.
     */
    private fun makeCardEntity(id: String): CardEntity = CardEntity(
        scryfallId        = id,
        name              = "Card $id",
        printedName       = null,
        manaCost          = null,
        cmc               = 0.0,
        colors            = "[]",
        colorIdentity     = "[]",
        typeLine          = "Creature",
        printedTypeLine   = null,
        oracleText        = null,
        printedText       = null,
        keywords          = "[]",
        power             = null,
        toughness         = null,
        loyalty           = null,
        setCode           = "TST",
        setName           = "Test",
        collectorNumber   = "1",
        rarity            = "common",
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
        legalityStandard  = "legal",
        legalityPioneer   = "legal",
        legalityModern    = "legal",
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
     * Builds a [PlaytestSetup] for a standard deck with [drawCount] cards.
     */
    private fun makeSetup(drawCount: Int = 7, format: String = "standard"): PlaytestSetup = PlaytestSetup(
        deckId       = "deck-test",
        deckName     = "Test Deck",
        deckFormat   = format,
        drawCount    = drawCount,
        isOnThePlay  = true,
        commanderCard = null,
    )

    /**
     * Stubs DeckRepository and CardDao so that buildAndDraw() can resolve a deck with
     * [cardCount] distinct cards (each with quantity 1).
     */
    private fun stubDeckWithCards(cardCount: Int) {
        val slots = (1..cardCount).map { DeckSlot(scryfallId = "card-$it", quantity = 1) }
        val deckWithCards = DeckWithCards(
            deck      = Deck(id = "deck-test", name = "Test Deck", format = "standard"),
            mainboard = slots,
            sideboard = emptyList(),
        )
        val entities = slots.map { makeCardEntity(it.scryfallId) }

        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(deckWithCards)
        coEvery { cardDao.getByIds(any()) } returns entities
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PlaytestHandViewModel(
            deckRepository           = deckRepository,
            cardDao                  = cardDao,
            buildLibraryUseCase      = buildLibraryUseCase,
            drawHandUseCase          = drawHandUseCase,
            londonMulliganUseCase    = londonMulliganUseCase,
            savePlaytestUseCase      = savePlaytestUseCase,
            savePlaytestSurveyUseCase = savePlaytestSurveyUseCase,
            ioDispatcher             = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Group 1: sessionStartedAt is fixed across redraws ─────────────────────

    @Test
    fun `given initialized session when onRedraw called then startedAt is unchanged`() = runTest {
        stubDeckWithCards(cardCount = 20)
        val setup = makeSetup(drawCount = 7)

        viewModel.initWithSetup(setup)
        advanceUntilIdle()

        val startedAtAfterInit = viewModel.uiState.value.snapshot!!.startedAt
        assertTrue("startedAt must be set after init", startedAtAfterInit > 0L)

        // Advance virtual time to simulate user thinking before redrawing.
        advanceTimeBy(2_000L)

        viewModel.onRedraw()
        advanceUntilIdle()

        val startedAtAfterRedraw = viewModel.uiState.value.snapshot!!.startedAt
        assertEquals(
            "startedAt must not change on redraw — it captures session start, not redraw time",
            startedAtAfterInit,
            startedAtAfterRedraw,
        )
    }

    @Test
    fun `given initialized session when onRedraw called multiple times then startedAt is always the same`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup())
        advanceUntilIdle()

        val originalStartedAt = viewModel.uiState.value.snapshot!!.startedAt

        repeat(3) {
            advanceTimeBy(500L)
            viewModel.onRedraw()
            advanceUntilIdle()
        }

        assertEquals(
            "startedAt must never be updated by subsequent redraws",
            originalStartedAt,
            viewModel.uiState.value.snapshot!!.startedAt,
        )
    }

    @Test
    fun `given initialized session when onRedraw called then mulligansUsed is reset to 0`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        // Take a mulligan so mulligansUsed becomes 1.
        viewModel.onMulligan()

        val stateAfterMulligan = viewModel.uiState.value.snapshot!!
        assertEquals(1, stateAfterMulligan.mulligansUsed)

        viewModel.onRedraw()
        advanceUntilIdle()

        // onRedraw is a full fresh draw — mulligansUsed must reset.
        assertEquals(0, viewModel.uiState.value.snapshot!!.mulligansUsed)
    }

    // ── Group 2: Mulligan block at limit ──────────────────────────────────────

    @Test
    fun `given drawCount 3 and mulligansUsed 2 when onMulligan called then hand is NOT changed`() = runTest {
        // drawCount=3, so the limit is drawCount-1=2. Two mulligans already taken.
        // The next mulligan would result in a 3-card hand where 2 must be bottomed, leaving 1 card.
        // But onMulligan at mulligansUsed==2 would push to mulligansUsed==3, kept=0 — blocked.
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 3))
        advanceUntilIdle()

        // Take 2 mulligans to reach the limit.
        viewModel.onMulligan()
        viewModel.onMulligan()

        val snapshotAtLimit = viewModel.uiState.value.snapshot!!
        assertEquals(2, snapshotAtLimit.mulligansUsed)
        val handAtLimit = snapshotAtLimit.hand

        // Attempt one more mulligan — must be a no-op.
        viewModel.onMulligan()

        val snapshotAfterBlockedMulligan = viewModel.uiState.value.snapshot!!
        assertEquals("mulligansUsed must not increment past drawCount-1", 2, snapshotAfterBlockedMulligan.mulligansUsed)
        assertEquals("hand must not change when mulligan is blocked", handAtLimit, snapshotAfterBlockedMulligan.hand)
    }

    @Test
    fun `given drawCount 2 and mulligansUsed 1 when onMulligan called then mulligan is blocked`() = runTest {
        // drawCount=2 → limit is 1. After 1 mulligan the button must be disabled.
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 2))
        advanceUntilIdle()

        viewModel.onMulligan()

        val snapshot = viewModel.uiState.value.snapshot!!
        assertEquals(1, snapshot.mulligansUsed)
        val handBefore = snapshot.hand

        // Second mulligan must be blocked.
        viewModel.onMulligan()

        val snapshotAfter = viewModel.uiState.value.snapshot!!
        assertEquals("mulligansUsed must not exceed drawCount-1=1", 1, snapshotAfter.mulligansUsed)
        assertEquals("hand must be unchanged when mulligan is blocked", handBefore, snapshotAfter.hand)
    }

    @Test
    fun `given drawCount 7 and mulligansUsed 0 when onMulligan called then mulligan is allowed`() = runTest {
        // Baseline: first mulligan is always allowed for drawCount >= 2.
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        val handBefore = viewModel.uiState.value.snapshot!!.hand

        viewModel.onMulligan()

        val snapshot = viewModel.uiState.value.snapshot!!
        assertEquals("first mulligan must be allowed", 1, snapshot.mulligansUsed)
    }

    // ── Group 3: onConfirmBottomN final hand size ─────────────────────────────

    @Test
    fun `given drawCount 7 and mulligansUsed 2 when onConfirmBottomN then final hand has 5 cards`() = runTest {
        // finalHandSize = drawCount - mulligansUsed = 7 - 2 = 5
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        // Take 2 mulligans.
        viewModel.onMulligan()
        viewModel.onMulligan()

        // Select exactly 2 cards to bottom (required = mulligansUsed = 2).
        val snapshot = viewModel.uiState.value.snapshot!!
        assertEquals(2, snapshot.mulligansUsed)
        viewModel.toggleBottomSelection(0)
        viewModel.toggleBottomSelection(1)

        viewModel.onConfirmBottomN()

        val finalHand = viewModel.uiState.value.snapshot!!.hand
        assertEquals("final hand must be drawCount - mulligansUsed = 5", 5, finalHand.size)
    }

    @Test
    fun `given drawCount 7 and mulligansUsed 1 when onConfirmBottomN then final hand has 6 cards`() = runTest {
        // finalHandSize = 7 - 1 = 6
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        viewModel.onMulligan()

        // Select exactly 1 card to bottom.
        viewModel.toggleBottomSelection(0)
        viewModel.onConfirmBottomN()

        val finalHand = viewModel.uiState.value.snapshot!!.hand
        assertEquals("final hand must be 6 after 1 mulligan", 6, finalHand.size)
    }

    @Test
    fun `given wrong number of bottom selections when onConfirmBottomN called then it is a no-op`() = runTest {
        // onConfirmBottomN requires exactly mulligansUsed selections — fewer is rejected.
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        viewModel.onMulligan()
        viewModel.onMulligan()

        // Select only 1 card when 2 are required.
        viewModel.toggleBottomSelection(0)
        // Do NOT toggle the second selection.

        val snapshotBefore = viewModel.uiState.value.snapshot

        viewModel.onConfirmBottomN()

        // State must be unchanged — confirm is rejected when count != mulligansUsed.
        assertEquals(snapshotBefore, viewModel.uiState.value.snapshot)
    }

    // ── Group 4: onReorderHand out-of-bounds guard ────────────────────────────

    @Test
    fun `given fromIndex out of bounds when onReorderHand called then hand is unchanged`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        val handBefore = viewModel.uiState.value.snapshot!!.hand
        val outOfBoundsIndex = handBefore.size + 10

        viewModel.onReorderHand(fromIndex = outOfBoundsIndex, toIndex = 0)

        assertEquals("out-of-bounds fromIndex must leave the hand unchanged", handBefore, viewModel.uiState.value.snapshot!!.hand)
    }

    @Test
    fun `given toIndex out of bounds when onReorderHand called then hand is unchanged`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        val handBefore = viewModel.uiState.value.snapshot!!.hand
        val outOfBoundsToIndex = handBefore.size + 5

        viewModel.onReorderHand(fromIndex = 0, toIndex = outOfBoundsToIndex)

        assertEquals("out-of-bounds toIndex must leave the hand unchanged", handBefore, viewModel.uiState.value.snapshot!!.hand)
    }

    @Test
    fun `given fromIndex equals toIndex when onReorderHand called then hand is unchanged`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        val handBefore = viewModel.uiState.value.snapshot!!.hand

        // fromIndex == toIndex must be a no-op per the guard in the ViewModel.
        viewModel.onReorderHand(fromIndex = 2, toIndex = 2)

        assertEquals("same-index reorder must be a no-op", handBefore, viewModel.uiState.value.snapshot!!.hand)
    }

    @Test
    fun `given valid fromIndex and toIndex when onReorderHand called then cards are reordered`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        val handBefore = viewModel.uiState.value.snapshot!!.hand
        assertTrue("hand must have at least 2 cards for reorder test", handBefore.size >= 2)

        val cardAtIndex0 = handBefore[0]
        val cardAtIndex1 = handBefore[1]

        viewModel.onReorderHand(fromIndex = 0, toIndex = 1)

        val handAfter = viewModel.uiState.value.snapshot!!.hand
        // After moving index 0 to index 1: the card at index 1 should be the original card at 0.
        assertEquals("card originally at index 0 should move to index 1", cardAtIndex0, handAfter[1])
    }

    // ── Group 5: double-save guard (isSaving) ─────────────────────────────────

    @Test
    fun `given isSaving is true when onSaveWithoutSurvey called again then repository is called only once`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        coEvery {
            savePlaytestUseCase.invoke(any(), any(), any())
        } returns 1L

        // Call keep to unlock save sheet, then call save twice in rapid succession.
        viewModel.onKeep()

        // First call — starts the coroutine and sets isSaving = true.
        viewModel.onSaveWithoutSurvey()
        // Second call — isSaving should be true, so this is a no-op.
        viewModel.onSaveWithoutSurvey()

        advanceUntilIdle()

        coVerify(exactly = 1) {
            savePlaytestUseCase.invoke(any(), any(), any())
        }
    }

    @Test
    fun `given isSaving is true when onSaveAndOpenSurvey called again then repository is called only once`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        coEvery {
            savePlaytestUseCase.invoke(any(), any(), any())
        } returns 1L

        viewModel.onKeep()

        viewModel.onSaveAndOpenSurvey()
        viewModel.onSaveAndOpenSurvey()

        advanceUntilIdle()

        coVerify(exactly = 1) {
            savePlaytestUseCase.invoke(any(), any(), any())
        }
    }

    // ── Group 6: onRedraw does NOT write to repository ────────────────────────

    @Test
    fun `given fresh session when onRedraw called then repository saveTest is never called`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        viewModel.onRedraw()
        advanceUntilIdle()

        coVerify(exactly = 0) {
            savePlaytestUseCase.invoke(any(), any(), any())
        }
    }

    @Test
    fun `given mulliganed session when onRedraw called then repository saveTest is never called`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        viewModel.onMulligan()
        viewModel.onRedraw()
        advanceUntilIdle()

        coVerify(exactly = 0) {
            savePlaytestUseCase.invoke(any(), any(), any())
        }
    }

    @Test
    fun `given onRedraw called then mulligansUsed is reset to 0`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        viewModel.onMulligan()
        viewModel.onMulligan()
        assertEquals(2, viewModel.uiState.value.snapshot!!.mulligansUsed)

        viewModel.onRedraw()
        advanceUntilIdle()

        assertEquals(
            "onRedraw must reset mulligansUsed to 0",
            0,
            viewModel.uiState.value.snapshot!!.mulligansUsed,
        )
    }

    // ── Group 7: save success emits correct event ─────────────────────────────

    @Test
    fun `given successful save without survey when onSaveWithoutSurvey called then SaveSuccess event is emitted`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup())
        advanceUntilIdle()

        coEvery { savePlaytestUseCase.invoke(any(), any(), any()) } returns 42L

        viewModel.onKeep()
        viewModel.onSaveWithoutSurvey()
        advanceUntilIdle()

        assertEquals(PlaytestHandEvent.SaveSuccess, viewModel.events.value)
    }

    @Test
    fun `given successful save with survey when onSaveAndOpenSurvey called then survey sheet is shown`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup())
        advanceUntilIdle()

        coEvery { savePlaytestUseCase.invoke(any(), any(), any()) } returns 42L

        viewModel.onKeep()
        viewModel.onSaveAndOpenSurvey()
        advanceUntilIdle()

        assertTrue("survey sheet must be shown after save with survey", viewModel.uiState.value.showSurveySheet)
        assertNull("SaveSuccess must NOT be emitted until survey is dismissed", viewModel.events.value)
    }

    // ── Group 8: save failure emits ShowError ─────────────────────────────────

    @Test
    fun `given save throws exception when onSaveWithoutSurvey called then ShowError event is emitted`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup())
        advanceUntilIdle()

        coEvery { savePlaytestUseCase.invoke(any(), any(), any()) } throws RuntimeException("DB error")

        viewModel.onKeep()
        viewModel.onSaveWithoutSurvey()
        advanceUntilIdle()

        assertTrue(
            "ShowError event must be emitted when save throws",
            viewModel.events.value is PlaytestHandEvent.ShowError,
        )
        assertFalse("isSaving must be reset to false after failure", viewModel.uiState.value.isSaving)
    }
}
