package com.mmg.manahub.feature.draft.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.model.BoosterConfig
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftError
import com.mmg.manahub.feature.draft.domain.model.DraftMode
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.DraftSet
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftStatus
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import com.mmg.manahub.feature.draft.domain.model.PassDirection
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.draft.domain.usecase.AutoPickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.CompleteDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSimSetUseCase
import com.mmg.manahub.feature.draft.domain.usecase.MakePickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.ObserveDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.StartDraftUseCase
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    /** Fake repository backing [ObserveDraftUseCase] with a real, controllable Flow. */
    private val fakeRepository = FakeDraftSimRepository()
    private val observeDraft = ObserveDraftUseCase(fakeRepository)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun `loadSet error emits OfflineNoCache`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Error("network down")

        val viewModel = buildViewModel(setCode = "tst")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DraftSimUiState.Error)
        assertEquals(DraftError.OfflineNoCache, (state as DraftSimUiState.Error).error)
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
    fun `onPick forwards to MakePickUseCase`() = runTest(testDispatcher) {
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

        viewModel.onPick("card-1")
        advanceUntilIdle()

        coVerify { makePick(any(), "card-1") }
    }

    @Test
    fun `onCompleteDraft emits Complete with deckId`() = runTest(testDispatcher) {
        coEvery { getDraftableSimSet("tst") } returns DataResult.Success(fakeDraftableSet())
        val building = draftingState().copy(status = DraftStatus.BUILDING)
        coEvery { startDraft("tst", any()) } coAnswers {
            fakeRepository.emit(building)
            DataResult.Success(building)
        }
        coEvery { completeDraft(any()) } returns DataResult.Success("deck-uuid-123")

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

    // ── Fake repository ──────────────────────────────────────────────────────────

    /**
     * Minimal fake backing [ObserveDraftUseCase]. Only [observeActiveSession] is exercised;
     * the write paths are routed through mocked use cases, so they are no-ops here.
     */
    private class FakeDraftSimRepository : DraftSimRepository {
        private val sessionFlow = MutableStateFlow<DraftState?>(null)

        fun emit(state: DraftState?) {
            sessionFlow.value = state
        }

        override suspend fun getDraftableSimSet(setCode: String): DataResult<DraftableSet> =
            DataResult.Error("not used")

        override fun observeActiveSession() = sessionFlow

        override suspend fun saveSession(state: DraftState) = Unit

        override suspend fun completeAndSaveDeck(
            result: com.mmg.manahub.feature.draft.domain.model.DraftResult,
        ): DataResult<String> = DataResult.Error("not used")
    }
}
