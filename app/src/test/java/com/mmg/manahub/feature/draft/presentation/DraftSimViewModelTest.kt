package com.mmg.manahub.feature.draft.presentation

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.engine.DraftDeckBuilder
import com.mmg.manahub.core.model.BasicLandSlot
import com.mmg.manahub.core.model.BoosterPack
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftCard
import com.mmg.manahub.core.model.DraftCuration
import com.mmg.manahub.core.model.DraftDeck
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.model.BoosterConfig
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftError
import com.mmg.manahub.core.model.DraftMode
import com.mmg.manahub.core.model.DraftSeat
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftStatus
import com.mmg.manahub.core.model.DraftableSet
import com.mmg.manahub.core.model.PassDirection
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.draft.domain.usecase.AutoPickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.CompleteDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSimSetUseCase
import com.mmg.manahub.feature.draft.domain.usecase.MakePickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.ObserveDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.StartDraftUseCase
import com.mmg.manahub.feature.draft.engine.DraftTestFixtures
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

/**
 * Unit tests for [DraftSimViewModel].
 *
 * Strategy: the use cases are the ViewModel's direct dependency boundary, so they are mocked
 * with MockK. The active-session Flow is backed by a fake [DraftSimRepository] (a real
 * [MutableStateFlow]) so the observe-driven UI transitions can be exercised without Room.
 */
@ExperimentalCoroutinesApi
class DraftSimViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val startDraft: StartDraftUseCase = mockk()
    private val makePick: MakePickUseCase = mockk()
    private val autoPick: AutoPickUseCase = mockk()
    private val completeDraft: CompleteDraftUseCase = mockk()
    private val getDraftableSimSet: GetDraftableSimSetUseCase = mockk()
    private val analytics: AnalyticsHelper = mockk(relaxed = true)
    private val botDrafter: com.mmg.manahub.core.domain.engine.BotDrafter = mockk(relaxed = true)
    private val deckBuilder: DraftDeckBuilder = mockk(relaxed = true)

    /** Fake repository backing [ObserveDraftUseCase] with a real, controllable Flow. */
    private val fakeRepository = FakeDraftSimRepository()
    private val observeDraft = ObserveDraftUseCase(fakeRepository)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The ViewModel tags the Crashlytics session in startDraft(); stub the static
        // getInstance() so JVM tests don't crash trying to reach the real SDK.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    private fun savedStateFor(setCode: String?): SavedStateHandle =
        if (setCode == null) SavedStateHandle() else SavedStateHandle(mapOf("setCode" to setCode))

    private fun buildViewModel(setCode: String? = "tst"): DraftSimViewModel =
        DraftSimViewModel(
            savedStateHandle = savedStateFor(setCode),
            startDraft = startDraft,
            makePick = makePick,
            autoPick = autoPick,
            observeDraft = observeDraft,
            completeDraft = completeDraft,
            getDraftableSimSet = getDraftableSimSet,
            analytics = analytics,
            botDrafter = botDrafter,
            deckBuilder = deckBuilder,
            draftSimRepository = fakeRepository,
            defaultDispatcher = testDispatcher,
        )

    private fun fakeDraftableSet(): DraftableSet = DraftableSet(
        set = DraftSet("tst", "tst", "Test Set", "2025-01-01", "", "v1", "v1", "v1"),
        cards = emptyList(),
        booster = BoosterConfig(setCode = "tst", schemaVersion = 1, boosters = emptyList(), sheets = emptyMap()),
        ratings = emptyMap(),
    )

    private fun draftingState(config: DraftConfig = DraftConfig(setCode = "tst")): DraftState =
        DraftState(
            config = config,
            round = 1,
            pickNumber = 1,
            seats = listOf(DraftSeat(index = 0, isHuman = true)),
            packsInFlight = emptyMap(),
            passDirection = PassDirection.LEFT,
            status = DraftStatus.DRAFTING,
        )

    // ── Tests ───────────────────────────────────────────────────────────────────

    @Test
    fun `loadSet success emits SetupReady`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DraftSimUiState.SetupReady)
        state as DraftSimUiState.SetupReady
        assertEquals("tst", state.setCode)
        assertEquals("Test Set", state.setName)
    }

    @Test
    fun `loadSet error maps known DraftError token to its case`() = runTest(testDispatcher) {
        // The repository serializes DraftError via .toString(); the ViewModel's parseDraftError
        // reverses that mapping. Feeding the serialized OfflineNoCache token must round-trip back
        // to the OfflineNoCache case (the exact contract parseDraftError implements).
        coEvery { getDraftableSimSet("tst") } returns
            DataResult.Error(DraftError.OfflineNoCache.toString())

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DraftSimUiState.Error)
        assertEquals(DraftError.OfflineNoCache, (state as DraftSimUiState.Error).error)
    }

    @Test
    fun `loadSet error with unrecognized message emits Unexpected`() = runTest(testDispatcher) {
        // A raw message containing none of the known DraftError tokens must fall through to
        // Unexpected, preserving the original message for diagnostics.
        coEvery { getDraftableSimSet("tst") } returns DataResult.Error("network down")

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DraftSimUiState.Error)
        assertEquals(DraftError.Unexpected("network down"), (state as DraftSimUiState.Error).error)
    }

    @Test
    fun `startDraft transitions to Drafting`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
        val started = draftingState()
        coEvery { startDraft("tst", any()) } coAnswers {
            // The use case persists the session; the fake repo emits it to observeDraft().
            fakeRepository.emit(started)
            DataResult.Success(started)
        }

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()

        viewModel.startDraft(DraftConfig(setCode = "tst", mode = DraftMode.DRAFT))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is DraftSimUiState.Drafting)
    }

    @Test
    fun `onConfirmPicks forwards the id list to MakePickUseCase`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
        val drafting = draftingState()
        coEvery { startDraft("tst", any()) } coAnswers {
            fakeRepository.emit(drafting)
            DataResult.Success(drafting)
        }
        coEvery { makePick(any(), any()) } returns DataResult.Success(drafting)

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()
        viewModel.startDraft(DraftConfig(setCode = "tst"))
        advanceUntilIdle()

        // Pick-1 UX (Phase B): the UI always wraps the single confirmed id as a one-element list.
        viewModel.onConfirmPicks(listOf("card-1"))
        advanceUntilIdle()

        coVerify { makePick(any(), listOf("card-1")) }
    }

    @Test
    fun `onConfirmPicks forwards a multi-id Pick 2 turn to MakePickUseCase`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
        val drafting = draftingState(DraftConfig(setCode = "tst", picksPerTurn = 2))
        coEvery { startDraft("tst", any()) } coAnswers {
            fakeRepository.emit(drafting)
            DataResult.Success(drafting)
        }
        coEvery { makePick(any(), any()) } returns DataResult.Success(drafting)

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()
        viewModel.startDraft(DraftConfig(setCode = "tst", picksPerTurn = 2))
        advanceUntilIdle()

        viewModel.onConfirmPicks(listOf("card-1", "card-2"))
        advanceUntilIdle()

        coVerify { makePick(any(), listOf("card-1", "card-2")) }
    }

    @Test
    fun `onCompleteDraft emits Complete with deckId`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
        val building = draftingState().copy(status = DraftStatus.BUILDING)
        coEvery { startDraft("tst", any()) } coAnswers {
            fakeRepository.emit(building)
            DataResult.Success(building)
        }
        coEvery { completeDraft(any(), any()) } returns DataResult.Success("deck-uuid-123")

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()
        viewModel.startDraft(DraftConfig(setCode = "tst"))
        advanceUntilIdle()

        viewModel.onCompleteDraft()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DraftSimUiState.Complete)
        assertEquals("deck-uuid-123", (state as DraftSimUiState.Complete).deckId)
    }

    // ── Phase D: Deck tab curation (D.4/D.7/D.8), reworked Phase E (E.2/E.4/E.6) ───

    @Test
    fun `toggleCardActive adds then removes a pool index from inactivePoolIndices`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(setCode = null)
        advanceUntilIdle()

        viewModel.toggleCardActive(0)
        assertEquals(setOf(0), viewModel.inactivePoolIndices.value)

        viewModel.toggleCardActive(0)
        assertTrue(viewModel.inactivePoolIndices.value.isEmpty())
    }

    @Test
    fun `toggleCardActive on one duplicated-card copy does not affect the other copy`() =
        runTest(testDispatcher) {
            // E.2 regression: two separate pool entries sharing the same scryfallId (drafted twice)
            // must toggle INDEPENDENTLY — the old scryfallId-keyed Set<String> conflated them.
            coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
            val pool = listOf(DraftTestFixtures.fakeCard(1), DraftTestFixtures.fakeCard(1)).map { DraftCard(it) }
            val building = draftingState().copy(
                status = DraftStatus.BUILDING,
                seats = listOf(DraftSeat(index = 0, isHuman = true, pool = pool)),
            )
            coEvery { startDraft("tst", any()) } coAnswers {
                fakeRepository.emit(building)
                DataResult.Success(building)
            }

            val viewModel = buildViewModel(setCode = "tst")
            advanceUntilIdle()
            viewModel.startDraft(DraftConfig(setCode = "tst"))
            advanceUntilIdle()

            viewModel.toggleCardActive(0)

            assertEquals(setOf(0), viewModel.inactivePoolIndices.value)
        }

    @Test
    fun `setBasicLandCount stores a positive count and clears the key at zero`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(setCode = null)
        advanceUntilIdle()

        viewModel.setBasicLandCount("Plains", 5)
        assertEquals(5, viewModel.basicLandCounts.value["Plains"])

        viewModel.setBasicLandCount("Plains", 0)
        assertFalse(viewModel.basicLandCounts.value.containsKey("Plains"))
    }

    @Test
    fun `setBasicLandCount clamps a negative count to removed`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(setCode = null)
        advanceUntilIdle()

        viewModel.setBasicLandCount("Island", -3)
        assertFalse(viewModel.basicLandCounts.value.containsKey("Island"))
    }

    @Test
    fun `setTargetLandCount clamps into the 0 to 30 range`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(setCode = null)
        advanceUntilIdle()

        viewModel.setTargetLandCount(-5)
        assertEquals(0, viewModel.targetLandCount.value)

        viewModel.setTargetLandCount(999)
        assertEquals(30, viewModel.targetLandCount.value)
    }

    @Test
    fun `applyLandSuggestionAutofill uses BasicLandCalculator over the ACTIVE non-basic-land pool`() =
        runTest(testDispatcher) {
            // E.4: the engine is now BasicLandCalculator (same one DeckStudioViewModel uses), fed
            // the active (non-toggled-off) mainboard's color pips — not DraftSeat.colorCommitment.
            // Card at index 1 is toggled INACTIVE and a drafted basic land sits at index 2; neither
            // may influence the pip count, isolating index 0's two W pips as the sole signal.
            coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
            val pool = listOf(
                DraftCard(DraftTestFixtures.fakeCard(1).copy(manaCost = "{W}{W}")),
                DraftCard(DraftTestFixtures.fakeCard(2).copy(manaCost = "{U}")),
                DraftCard(DraftTestFixtures.fakeCard(3).copy(typeLine = "Basic Land — Plains", name = "Plains")),
            )
            val building = draftingState().copy(
                status = DraftStatus.BUILDING,
                seats = listOf(DraftSeat(index = 0, isHuman = true, pool = pool)),
            )
            coEvery { startDraft("tst", any()) } coAnswers {
                fakeRepository.emit(building)
                DataResult.Success(building)
            }

            val viewModel = buildViewModel(setCode = "tst")
            advanceUntilIdle()
            viewModel.startDraft(DraftConfig(setCode = "tst"))
            advanceUntilIdle()

            viewModel.toggleCardActive(1) // bench the U-pip card
            viewModel.setTargetLandCount(10)

            viewModel.applyLandSuggestionAutofill()

            // Only the W pip is active → the whole 10-land target goes to Plains.
            assertEquals(mapOf("Plains" to 10), viewModel.basicLandCounts.value)
        }

    @Test
    fun `onCompleteDraft assembles a DraftDeck split by inactivePoolIndices and basicLandCounts`() =
        runTest(testDispatcher) {
            // E.6: no automatic curation seed anymore — every drafted card starts ACTIVE, and this
            // test's own toggleCardActive call below is the ONLY source of a benched card.
            coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
            val pool = listOf(
                DraftCard(DraftTestFixtures.fakeCard(1)),
                DraftCard(DraftTestFixtures.fakeCard(2)),
                DraftCard(DraftTestFixtures.fakeCard(3)),
            )
            val building = draftingState().copy(
                status = DraftStatus.BUILDING,
                seats = listOf(DraftSeat(index = 0, isHuman = true, pool = pool)),
            )
            coEvery { startDraft("tst", any()) } coAnswers {
                fakeRepository.emit(building)
                DataResult.Success(building)
            }

            val deckSlot = slot<DraftDeck>()
            coEvery { completeDraft(any(), capture(deckSlot)) } returns DataResult.Success("deck-uuid-456")

            val viewModel = buildViewModel(setCode = "tst")
            advanceUntilIdle()
            viewModel.startDraft(DraftConfig(setCode = "tst"))
            advanceUntilIdle()

            // Bench pool index 1 ("id-2") to the sideboard and set a basic-land count, mirroring
            // the Deck tab's hide icon.
            viewModel.toggleCardActive(1)
            viewModel.setBasicLandCount("Forest", 8)

            viewModel.onCompleteDraft()
            advanceUntilIdle()

            val savedDeck = deckSlot.captured
            assertEquals(listOf("id-1", "id-3"), savedDeck.mainboard.map { it.card.scryfallId })
            assertEquals(listOf("id-2"), savedDeck.sideboard.map { it.card.scryfallId })
            assertEquals(listOf(BasicLandSlot(scryfallId = "", name = "Forest", count = 8)), savedDeck.basics)
        }

    @Test
    fun `onCancelDraft asks the repository to delete the active session`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
        val drafting = draftingState()
        coEvery { startDraft("tst", any()) } coAnswers {
            fakeRepository.emit(drafting)
            DataResult.Success(drafting)
        }

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()
        viewModel.startDraft(DraftConfig(setCode = "tst"))
        advanceUntilIdle()

        viewModel.onCancelDraft()
        advanceUntilIdle()

        assertEquals(listOf(drafting), fakeRepository.cancelledSessions)
    }

    // ── Phase G: fix pass on Draft Simulator navigation/state ─────────────────────

    @Test
    fun `G2 onCompleteDraft ignores a second call while the first is still in flight`() =
        runTest(testDispatcher) {
            coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
            val building = draftingState().copy(status = DraftStatus.BUILDING)
            coEvery { startDraft("tst", any()) } coAnswers {
                fakeRepository.emit(building)
                DataResult.Success(building)
            }
            coEvery { completeDraft(any(), any()) } returns DataResult.Success("deck-uuid-789")

            val viewModel = buildViewModel(setCode = "tst")
            advanceUntilIdle()
            viewModel.startDraft(DraftConfig(setCode = "tst"))
            advanceUntilIdle()

            // Two rapid taps before the first save completes: isCompletingDraft is set SYNCHRONOUSLY
            // (before the coroutine is even launched), so the second call's guard check sees it
            // immediately — no artificial concurrency needed to exercise this, unlike G.8's suspend
            // fun below.
            viewModel.onCompleteDraft()
            assertTrue(viewModel.isCompletingDraft.value)
            viewModel.onCompleteDraft()
            advanceUntilIdle()

            coVerify(exactly = 1) { completeDraft(any(), any()) }
            assertFalse(viewModel.isCompletingDraft.value)
        }

    @Test
    fun `G4 resuming a BUILDING session with persisted curation re-seeds the Deck tab state`() =
        runTest(testDispatcher) {
            val pool = listOf(DraftCard(DraftTestFixtures.fakeCard(1)), DraftCard(DraftTestFixtures.fakeCard(2)))
            val curation = DraftCuration(
                inactivePoolIndices = setOf(1),
                basicLandCounts = mapOf("Plains" to 4),
                targetLandCount = 17,
            )
            val building = draftingState().copy(
                status = DraftStatus.BUILDING,
                seats = listOf(DraftSeat(index = 0, isHuman = true, pool = pool)),
                curation = curation,
            )

            // setCode = null so the ViewModel attaches straight to observeActiveSession(), mirroring
            // how the Drafting/Result screens' instance resumes a session after a process death.
            val viewModel = buildViewModel(setCode = null)
            advanceUntilIdle()
            fakeRepository.emit(building)
            advanceUntilIdle()

            assertEquals(setOf(1), viewModel.inactivePoolIndices.value)
            assertEquals(mapOf("Plains" to 4), viewModel.basicLandCounts.value)
            assertEquals(17, viewModel.targetLandCount.value)
        }

    @Test
    fun `G4 toggleCardActive persists curation into the session while BUILDING`() =
        runTest(testDispatcher) {
            coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
            val pool = listOf(DraftCard(DraftTestFixtures.fakeCard(1)), DraftCard(DraftTestFixtures.fakeCard(2)))
            val building = draftingState().copy(
                status = DraftStatus.BUILDING,
                seats = listOf(DraftSeat(index = 0, isHuman = true, pool = pool)),
            )
            coEvery { startDraft("tst", any()) } coAnswers {
                fakeRepository.emit(building)
                DataResult.Success(building)
            }

            val viewModel = buildViewModel(setCode = "tst")
            advanceUntilIdle()
            viewModel.startDraft(DraftConfig(setCode = "tst"))
            advanceUntilIdle()

            viewModel.toggleCardActive(1)
            advanceUntilIdle()

            val saved = fakeRepository.savedSessions.lastOrNull()
            assertEquals(setOf(1), saved?.curation?.inactivePoolIndices)
        }

    @Test
    fun `G5 onAutoPick with a partial Pick 2 selection commits it then auto-fills the remainder`() =
        runTest(testDispatcher) {
            coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
            val pack = listOf(DraftTestFixtures.fakeCard(1), DraftTestFixtures.fakeCard(2)).map { DraftCard(it) }
            val drafting = draftingState(DraftConfig(setCode = "tst", picksPerTurn = 2)).copy(
                packsInFlight = mapOf(0 to BoosterPack("pack-1", pack)),
            )
            coEvery { startDraft("tst", any()) } coAnswers {
                fakeRepository.emit(drafting)
                DataResult.Success(drafting)
            }
            val partialResultState = drafting.copy(picksTakenInTurn = 1)
            coEvery { makePick(any(), listOf("id-1")) } returns DataResult.Success(partialResultState)
            coEvery { autoPick(partialResultState) } returns DataResult.Success(drafting)

            val viewModel = buildViewModel(setCode = "tst")
            advanceUntilIdle()
            viewModel.startDraft(DraftConfig(setCode = "tst", picksPerTurn = 2))
            advanceUntilIdle()

            viewModel.toggleCardSelection("id-1") // partial: 1 of 2 required this turn
            viewModel.onAutoPick() // timer-expiry / manual auto-pick with a partial selection present
            advanceUntilIdle()

            // The user's manual tap is COMMITTED first (never discarded), then autoPick fills the
            // rest of the turn using the state that already reflects that commit.
            coVerify { makePick(any(), listOf("id-1")) }
            coVerify { autoPick(partialResultState) }
        }

    @Test
    fun `G6 onConfirmPicks preserves the selection when the pick errors`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
        val pack = listOf(DraftTestFixtures.fakeCard(1), DraftTestFixtures.fakeCard(2)).map { DraftCard(it) }
        val drafting = draftingState(DraftConfig(setCode = "tst", picksPerTurn = 2)).copy(
            packsInFlight = mapOf(0 to BoosterPack("pack-1", pack)),
        )
        coEvery { startDraft("tst", any()) } coAnswers {
            fakeRepository.emit(drafting)
            DataResult.Success(drafting)
        }
        coEvery { makePick(any(), listOf("id-1", "id-2")) } returns DataResult.Error("boom")

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()
        viewModel.startDraft(DraftConfig(setCode = "tst", picksPerTurn = 2))
        advanceUntilIdle()

        viewModel.toggleCardSelection("id-1")
        viewModel.toggleCardSelection("id-2")
        assertEquals(listOf("id-1", "id-2"), viewModel.selectedCardIds.value)

        viewModel.onConfirmPicks(viewModel.selectedCardIds.value)
        advanceUntilIdle()

        // The confirm errored — the user's selection must NOT be silently dropped, unlike the old
        // unconditional `selectedCardIds = emptyList()` the Composable used to do on every tap.
        assertEquals(listOf("id-1", "id-2"), viewModel.selectedCardIds.value)
        assertTrue(viewModel.uiState.value is DraftSimUiState.Error)
    }

    @Test
    fun `G7 setBasicLandCount clamps to the max target land count`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(setCode = null)
        advanceUntilIdle()

        viewModel.setBasicLandCount("Mountain", 999)
        assertEquals(30, viewModel.basicLandCounts.value["Mountain"])
    }

    @Test
    fun `G8 onCancelDraft guards a double-tap from double-firing`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
        val drafting = draftingState()
        coEvery { startDraft("tst", any()) } coAnswers {
            fakeRepository.emit(drafting)
            DataResult.Success(drafting)
        }

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()
        viewModel.startDraft(DraftConfig(setCode = "tst"))
        advanceUntilIdle()

        // Gate cancelSession so the first onCancelDraft() call is genuinely still "in flight" (not
        // just synchronously done) when the second one fires — otherwise a synchronous fake body
        // would never actually overlap under a StandardTestDispatcher.
        val gate = CompletableDeferred<Unit>()
        fakeRepository.cancelGate = gate

        val job1 = launch { viewModel.onCancelDraft() }
        advanceUntilIdle() // job1 is now suspended awaiting the gate, inside the guarded section
        val job2 = launch { viewModel.onCancelDraft() } // must no-op: isCancellingDraft is still true
        advanceUntilIdle()

        gate.complete(Unit)
        job1.join()
        job2.join()

        assertEquals(1, fakeRepository.cancelledSessions.size)
    }

    // ── Fake repository ──────────────────────────────────────────────────────────

    /**
     * Minimal fake backing [ObserveDraftUseCase]. Only [observeActiveSession] is exercised;
     * the write paths are routed through mocked use cases, so they are no-ops here.
     */
    private class FakeDraftSimRepository : DraftSimRepository {
        private val sessionFlow = MutableStateFlow<DraftState?>(null)

        /** Records every [cancelSession] call for [onCancelDraft]'s assertion. */
        val cancelledSessions = mutableListOf<DraftState>()

        /** Records every [saveSession] call (Phase G, G.4's persistCuration assertions). */
        val savedSessions = mutableListOf<DraftState>()

        /**
         * When set, [cancelSession] suspends on this until externally completed — lets a test
         * (G.8) deterministically simulate two overlapping [DraftSimViewModel.onCancelDraft] calls
         * under a `StandardTestDispatcher`, where a synchronous fake body would otherwise never
         * actually overlap.
         */
        var cancelGate: CompletableDeferred<Unit>? = null

        fun emit(state: DraftState?) {
            sessionFlow.value = state
        }

        override suspend fun getDraftableSimSet(setCode: String): DataResult<DraftableSet> =
            DataResult.Error("not used")

        override suspend fun getEngineConfig(
            setCode: String,
        ): com.mmg.manahub.core.model.EngineConfig? = null

        override fun observeActiveSession() = sessionFlow

        override suspend fun saveSession(state: DraftState) {
            savedSessions += state
        }

        override suspend fun completeAndSaveDeck(
            result: com.mmg.manahub.core.model.DraftResult,
        ): DataResult<String> = DataResult.Error("not used")

        override suspend fun cancelSession(state: DraftState) {
            cancelGate?.await()
            cancelledSessions += state
        }
    }
}
