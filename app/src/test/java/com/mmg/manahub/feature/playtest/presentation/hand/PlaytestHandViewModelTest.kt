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
import com.mmg.manahub.feature.playtest.domain.model.PlaytestPhase
import com.mmg.manahub.feature.playtest.domain.model.PlaytestSetup
import com.mmg.manahub.feature.playtest.domain.model.PlayZone
import com.mmg.manahub.feature.playtest.domain.usecase.BuildLibraryUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.DrawHandUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.LondonMulliganUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.SavePlaytestSurveyUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.SavePlaytestUseCase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
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
        // The ViewModel logs to Crashlytics outside any runCatching block (e.g. in
        // initWithSetup / enterPlayPhase), so the static getInstance() must be mocked
        // or every test throws "Default FirebaseApp is not initialized".
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
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
        unmockkStatic(FirebaseCrashlytics::class)
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

        viewModel.events.test {
            viewModel.onKeep()
            viewModel.onSaveWithoutSurvey()
            advanceUntilIdle()

            assertEquals(PlaytestHandEvent.SaveSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given successful save with survey when onSaveAndOpenSurvey called then survey sheet is shown`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup())
        advanceUntilIdle()

        coEvery { savePlaytestUseCase.invoke(any(), any(), any()) } returns 42L

        viewModel.events.test {
            viewModel.onKeep()
            viewModel.onSaveAndOpenSurvey()
            advanceUntilIdle()

            assertTrue("survey sheet must be shown after save with survey", viewModel.uiState.value.showSurveySheet)
            // SaveSuccess must NOT be emitted until the survey is dismissed.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 8: save failure emits ShowError ─────────────────────────────────

    @Test
    fun `given save throws exception when onSaveWithoutSurvey called then ShowError event is emitted`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup())
        advanceUntilIdle()

        coEvery { savePlaytestUseCase.invoke(any(), any(), any()) } throws RuntimeException("DB error")

        viewModel.events.test {
            viewModel.onKeep()
            viewModel.onSaveWithoutSurvey()
            advanceUntilIdle()

            assertTrue(
                "ShowError event must be emitted when save throws",
                awaitItem() is PlaytestHandEvent.ShowError,
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse("isSaving must be reset to false after failure", viewModel.uiState.value.isSaving)
    }

    // ── Group 9: PLAY phase (battlefield) ─────────────────────────────────────

    @Test
    fun `given kept hand with 0 mulligans when onKeep then enters PLAY phase and does not save`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        viewModel.onKeep()

        val state = viewModel.uiState.value
        assertEquals(PlaytestPhase.PLAY, state.phase)
        assertNotNull("battlefield must be built on entering PLAY", state.battlefield)
        assertEquals("hand carries over to the battlefield", 7, state.battlefield!!.hand.size)
        assertFalse("Keep must not open the (dormant) save sheet", state.showSaveSheet)
        coVerify(exactly = 0) { savePlaytestUseCase.invoke(any(), any(), any()) }
    }

    @Test
    fun `given PLAY phase when drawCard then top library card moves to hand`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        val before = viewModel.uiState.value.battlefield!!
        val handBefore = before.hand.size
        val libraryBefore = before.library.size

        viewModel.drawCard()

        val after = viewModel.uiState.value.battlefield!!
        assertEquals(handBefore + 1, after.hand.size)
        assertEquals(libraryBefore - 1, after.library.size)
    }

    @Test
    fun `given each drawn card when drawCard then instanceIds are unique`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        viewModel.drawCard()
        viewModel.drawCard()

        val hand = viewModel.uiState.value.battlefield!!.hand
        val ids = hand.map { it.instanceId }
        assertEquals("instanceIds must be unique across the hand", ids.size, ids.toSet().size)
    }

    @Test
    fun `given a hand card when moveCard to LANDS then it leaves hand and enters lands`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        val target = viewModel.uiState.value.battlefield!!.hand.first()
        viewModel.moveCard(target.instanceId, PlayZone.LANDS)

        val bf = viewModel.uiState.value.battlefield!!
        assertFalse("card must leave hand", bf.hand.any { it.instanceId == target.instanceId })
        assertTrue("card must be in lands", bf.lands.any { it.instanceId == target.instanceId })
    }

    @Test
    fun `given unknown instanceId when moveCard then battlefield is unchanged`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        val before = viewModel.uiState.value.battlefield
        viewModel.moveCard(instanceId = -999L, toZone = PlayZone.GRAVEYARD)

        assertEquals("unknown instanceId must be a no-op", before, viewModel.uiState.value.battlefield)
    }

    @Test
    fun `given PLAY phase when confirmEndTest then NavigateBack is emitted and nothing is saved`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        viewModel.requestEndTest()
        assertTrue(viewModel.uiState.value.showEndTestConfirm)

        viewModel.events.test {
            viewModel.confirmEndTest()

            assertEquals(PlaytestHandEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.showEndTestConfirm)
        coVerify(exactly = 0) { savePlaytestUseCase.invoke(any(), any(), any()) }
    }

    // ── Group 10: battlefield card-conservation regressions (C1) ──────────────

    /**
     * Helper: total physical cards present anywhere (hand + lands + permanents +
     * graveyard + library). This sum is the conservation invariant — it must stay
     * constant across any draw/move (a draw moves a card from library to hand; a move
     * relocates a card between zones; neither creates nor destroys cards).
     */
    private fun PlaytestHandViewModel.totalCards(): Int {
        val bf = uiState.value.battlefield!!
        return bf.hand.size + bf.lands.size + bf.permanents.size + bf.graveyard.size + bf.library.size
    }

    @Test
    fun `given PLAY phase when drawCard called twice rapidly then total cards is conserved`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        val totalBefore = viewModel.totalCards()
        val libraryBefore = viewModel.uiState.value.battlefield!!.library.size

        // Two rapid draws — the stale-capture bug would mint a phantom extra card here.
        viewModel.drawCard()
        viewModel.drawCard()

        val bf = viewModel.uiState.value.battlefield!!
        assertEquals("total cards must be conserved across two draws", totalBefore, viewModel.totalCards())
        assertEquals("library must drop by exactly 2", libraryBefore - 2, bf.library.size)
        // No phantom duplicate instanceIds.
        val ids = (bf.hand + bf.lands + bf.permanents + bf.graveyard).map { it.instanceId }
        assertEquals("instanceIds must stay unique", ids.size, ids.toSet().size)
    }

    @Test
    fun `given empty library when drawCard called then state is unchanged and ShowInfo is emitted`() = runTest {
        // drawCount equal to the whole deck leaves the library empty after the opening hand.
        stubDeckWithCards(cardCount = 7)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        assertEquals("library must be empty for this setup", 0, viewModel.uiState.value.battlefield!!.library.size)
        val before = viewModel.uiState.value.battlefield

        viewModel.events.test {
            viewModel.drawCard()

            assertTrue("empty-library draw must emit ShowInfo", awaitItem() is PlaytestHandEvent.ShowInfo)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("empty-library draw must not mutate the battlefield", before, viewModel.uiState.value.battlefield)
    }

    @Test
    fun `given draw until library empty then total cards is conserved and library is zero`() = runTest {
        stubDeckWithCards(cardCount = 10)
        viewModel.initWithSetup(makeSetup(drawCount = 3))
        advanceUntilIdle()
        viewModel.onKeep()

        val totalBefore = viewModel.totalCards()

        // Draw far more than the library holds; extra draws are no-ops.
        repeat(20) { viewModel.drawCard() }

        assertEquals("total cards conserved when draining the library", totalBefore, viewModel.totalCards())
        assertEquals("library must be drained to zero", 0, viewModel.uiState.value.battlefield!!.library.size)
    }

    // ── Group 11: moveCard semantics (C1) ─────────────────────────────────────

    @Test
    fun `given a card in LANDS when moveCard to same zone then it is idempotent and preserves isTapped`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        // Put a card on the field and tap it.
        val handCard = viewModel.uiState.value.battlefield!!.hand.first()
        viewModel.moveCard(handCard.instanceId, PlayZone.LANDS)
        viewModel.toggleTap(handCard.instanceId)

        val landCard = viewModel.uiState.value.battlefield!!.lands.first { it.instanceId == handCard.instanceId }
        assertTrue("card must be tapped before the same-zone move", landCard.isTapped)
        val before = viewModel.uiState.value.battlefield

        // Move to the same zone — must be a no-op and keep the tapped state.
        viewModel.moveCard(handCard.instanceId, PlayZone.LANDS)

        assertEquals("same-zone move must be idempotent", before, viewModel.uiState.value.battlefield)
        val after = viewModel.uiState.value.battlefield!!.lands.first { it.instanceId == handCard.instanceId }
        assertTrue("isTapped must be preserved on same-zone move", after.isTapped)
    }

    @Test
    fun `given a tapped land when moveCard back to HAND then isTapped resets to false`() = runTest {
        // Documented semantics: returning a card to the hand UNTAPS it (a hand card has no
        // tapped concept; re-playing it should start untapped).
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        val handCard = viewModel.uiState.value.battlefield!!.hand.first()
        viewModel.moveCard(handCard.instanceId, PlayZone.LANDS)
        viewModel.toggleTap(handCard.instanceId)
        assertTrue(viewModel.uiState.value.battlefield!!.lands.first().isTapped)

        viewModel.moveCard(handCard.instanceId, PlayZone.HAND)

        val backInHand = viewModel.uiState.value.battlefield!!.hand.first { it.instanceId == handCard.instanceId }
        assertFalse("returning to hand must untap the card", backInHand.isTapped)
    }

    // ── Group 12: enterPlayPhase re-entrancy (C1 idempotence) ─────────────────

    @Test
    fun `given already in PLAY phase when onKeep called again then instanceIds are not re-minted`() = runTest {
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        viewModel.onKeep()
        val idsAfterFirst = viewModel.uiState.value.battlefield!!.hand.map { it.instanceId }

        // Re-entering must be a no-op — same battlefield, same instanceIds.
        viewModel.enterPlayPhase()
        val idsAfterSecond = viewModel.uiState.value.battlefield!!.hand.map { it.instanceId }

        assertEquals("enterPlayPhase must be idempotent and not re-mint ids", idsAfterFirst, idsAfterSecond)
    }

    @Test
    fun `given a battlefield with in-progress field state when enterPlayPhase called again then the whole battlefield is preserved`() = runTest {
        // B2: enterPlayPhase guards on BOTH phase == PLAY and battlefield != null. After the
        // user has moved cards onto the field, a stray re-entry must NOT rebuild the
        // battlefield from the snapshot (which would discard the field state and re-mint ids).
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        // Put a card on the field and tap it so the battlefield diverges from the snapshot.
        val handCard = viewModel.uiState.value.battlefield!!.hand.first()
        viewModel.moveCard(handCard.instanceId, PlayZone.LANDS)
        viewModel.toggleTap(handCard.instanceId)

        val battlefieldBefore = viewModel.uiState.value.battlefield

        // A second enterPlayPhase must be a complete no-op.
        viewModel.enterPlayPhase()

        assertEquals(
            "enterPlayPhase must preserve the exact battlefield (no rebuild) when one already exists",
            battlefieldBefore,
            viewModel.uiState.value.battlefield,
        )
    }

    // ── Group 12b: originalLibrarySize nullable sentinel (B4) ─────────────────

    @Test
    fun `given a deck whose library builds 0 cards when saved then librarySize 0 is recorded not treated as uninitialized`() = runTest {
        // B4: `originalLibrarySize` uses `null` (not 0) as the "not yet registered" sentinel.
        // A commander-only deck legitimately builds a 0-card library (the single mainboard
        // card IS the commander and is excluded). With the old `== 0` sentinel this 0 would be
        // misread as "uninitialized" and re-registered on every draw. We verify the save path
        // receives librarySize == 0, proving 0 is a valid recorded value.
        val commander = makeCard(id = "commander-1", name = "Test Commander")
        val slots = listOf(DeckSlot(scryfallId = "commander-1", quantity = 1))
        val deckWithCards = DeckWithCards(
            deck      = Deck(id = "deck-test", name = "Test Deck", format = "commander"),
            mainboard = slots,
            sideboard = emptyList(),
        )
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(deckWithCards)
        coEvery { cardDao.getByIds(any()) } returns listOf(makeCardEntity("commander-1"))

        val setup = PlaytestSetup(
            deckId        = "deck-test",
            deckName      = "Test Deck",
            deckFormat    = "commander",
            drawCount     = 0,
            isOnThePlay   = true,
            commanderCard = commander,
        )

        viewModel.initWithSetup(setup)
        advanceUntilIdle()

        // Redraw once to confirm the (already-registered) 0 size is NOT re-registered/desynced.
        viewModel.onRedraw()
        advanceUntilIdle()

        val capturedLibrarySize = slot<Int>()
        coEvery {
            savePlaytestUseCase.invoke(any(), any(), capture(capturedLibrarySize))
        } returns 1L

        // Drive the dormant save path directly to observe the recorded librarySize.
        viewModel.onSaveWithoutSurvey()
        advanceUntilIdle()

        coVerify(exactly = 1) { savePlaytestUseCase.invoke(any(), any(), any()) }
        assertEquals(
            "a legitimately empty library must be recorded as 0, not re-registered as uninitialized",
            0,
            capturedLibrarySize.captured,
        )
    }

    // ── Group 13: Channel events deliver each emission exactly once (C2) ───────

    @Test
    fun `given confirmEndTest called twice then NavigateBack is delivered exactly once per call`() = runTest {
        // With a StateFlow, the second identical NavigateBack would equality-collapse and be
        // lost. With a buffered Channel each call delivers its own event.
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        viewModel.events.test {
            viewModel.confirmEndTest()
            assertEquals("first confirm must deliver NavigateBack", PlaytestHandEvent.NavigateBack, awaitItem())

            // A second confirm (e.g. a double tap) must deliver a distinct event, not collapse.
            viewModel.confirmEndTest()
            assertEquals("second confirm must also deliver NavigateBack", PlaytestHandEvent.NavigateBack, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Group 14: buildAndDraw underfetch + null-deck + toggleTap HAND guard ───

    @Test
    fun `given cardDao returns a subset of requested ids when initialized then library and hand reflect only the returned subset`() = runTest {
        // buildAndDraw underfetch path (cache eviction / stale data): cardDao.getByIds returns
        // fewer cards than the mainboard requested. The session must still build cleanly using
        // only the resolved cards — no crash, and the total physical card count equals the
        // returned subset (cards with no resolved entity are dropped by the library builder).
        val slots = (1..20).map { DeckSlot(scryfallId = "card-$it", quantity = 1) }
        val deckWithCards = DeckWithCards(
            deck      = Deck(id = "deck-test", name = "Test Deck", format = "standard"),
            mainboard = slots,
            sideboard = emptyList(),
        )
        // Only the first 12 of the 20 requested ids resolve to a CardEntity.
        val returnedSubset = slots.take(12).map { makeCardEntity(it.scryfallId) }
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(deckWithCards)
        coEvery { cardDao.getByIds(any()) } returns returnedSubset

        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        val snapshot = viewModel.uiState.value.snapshot
        assertNotNull("session must build even when the card cache underfetches", snapshot)
        // hand + remaining library must equal the resolved subset size — no phantom cards.
        val handPlusLibrary = snapshot!!.hand.size + snapshot.library.size
        assertEquals(
            "hand + library must equal only the resolved subset (12), not the requested 20",
            returnedSubset.size,
            handPlusLibrary,
        )
        assertTrue("no crash and a hand was drawn", snapshot.hand.isNotEmpty())
    }

    @Test
    fun `given observeDeckWithCards emits null when initialized then errorMessage is set and isLoading is false`() = runTest {
        // The deck flow yields null (deck deleted / not found): buildAndDraw must surface the
        // load failure rather than crash, and clear the loading state.
        every { deckRepository.observeDeckWithCards("deck-test") } returns flowOf(null)

        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Failed to load deck cards", state.errorMessage)
        assertFalse("isLoading must be cleared on load failure", state.isLoading)
    }

    @Test
    fun `given a HAND card when toggleTap then battlefield is unchanged`() = runTest {
        // toggleTap only affects LANDS / PERMANENTS. A card still in HAND has no tapped concept,
        // so toggling it must be a complete no-op (guard at PlaytestHandViewModel.kt:520).
        stubDeckWithCards(cardCount = 20)
        viewModel.initWithSetup(makeSetup(drawCount = 7))
        advanceUntilIdle()
        viewModel.onKeep()

        val before = viewModel.uiState.value.battlefield
        val handCard = before!!.hand.first()

        viewModel.toggleTap(handCard.instanceId)

        assertEquals(
            "toggleTap on a HAND card must leave the battlefield unchanged",
            before,
            viewModel.uiState.value.battlefield,
        )
    }
}
