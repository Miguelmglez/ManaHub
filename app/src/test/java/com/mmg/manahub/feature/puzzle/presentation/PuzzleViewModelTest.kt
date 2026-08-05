package com.mmg.manahub.feature.puzzle.presentation

import app.cash.turbine.test
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.PaginatedCards
import com.mmg.manahub.core.model.puzzle.Puzzle
import com.mmg.manahub.core.model.puzzle.PuzzleAttemptFeedback
import com.mmg.manahub.core.model.puzzle.PuzzleGuessResult
import com.mmg.manahub.core.model.puzzle.PuzzleResult
import com.mmg.manahub.core.model.puzzle.PuzzleType
import com.mmg.manahub.feature.puzzle.domain.usecase.GetPuzzleResultUseCase
import com.mmg.manahub.feature.puzzle.domain.usecase.GetTodayPuzzleUseCase
import com.mmg.manahub.feature.puzzle.domain.usecase.SavePuzzleResultUseCase
import com.mmg.manahub.feature.puzzle.domain.usecase.SubmitPuzzleGuessUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PuzzleViewModel] (Batch B2). Covers the resume-vs-fresh load decision, the
 * offline fallback, and the guess submission -> partial-save / solve-transition flow. Mirrors
 * [com.mmg.manahub.feature.addcard.presentation.AddCardViewModelTest]'s harness shape
 * (`StandardTestDispatcher` passed explicitly to `runTest`, MockK + Turbine).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PuzzleViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val getTodayPuzzleUseCase: GetTodayPuzzleUseCase = mockk()
    private val getPuzzleResultUseCase: GetPuzzleResultUseCase = mockk()
    private val submitPuzzleGuessUseCase: SubmitPuzzleGuessUseCase = mockk()
    private val savePuzzleResultUseCase: SavePuzzleResultUseCase = mockk()
    private val searchCardsUseCase: SearchCardsUseCase = mockk()

    /** Mirrors the ViewModel's own UTC-rollover "today" (ADR-006 Decision 2). */
    private val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

    private val testPuzzle = Puzzle(
        date = today,
        type = PuzzleType.GUESS_CARD,
        payloadJson = "{}",
    )

    private fun buildFeedback() = PuzzleAttemptFeedback(
        cmc = PuzzleAttemptFeedback.Comparison.MATCH,
        colorIdentity = PuzzleAttemptFeedback.MatchLevel.MATCH,
        rarity = PuzzleAttemptFeedback.MatchLevel.MATCH,
        typeLine = PuzzleAttemptFeedback.MatchLevel.MATCH,
        setCode = PuzzleAttemptFeedback.MatchLevel.MATCH,
        releasedYear = PuzzleAttemptFeedback.Comparison.MATCH,
        power = null,
        toughness = null,
    )

    private fun buildGuessResult(name: String, isCorrect: Boolean) = PuzzleGuessResult(
        guessedName = name,
        guessedScryfallId = "scryfall-$name",
        feedback = buildFeedback(),
        isCorrect = isCorrect,
        guessedAt = Clock.System.now(),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { getPuzzleResultUseCase(any()) } returns null
        coEvery { savePuzzleResultUseCase(any()) } returns Unit
        // Default stub for the debounced guess-suggestion search — several tests type into the
        // guess field, which schedules a search regardless of whether the assertions care about it.
        coEvery { searchCardsUseCase(any()) } returns DataResult.Success(PaginatedCards(emptyList(), false))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = PuzzleViewModel(
        getTodayPuzzleUseCase = getTodayPuzzleUseCase,
        getPuzzleResultUseCase = getPuzzleResultUseCase,
        submitPuzzleGuessUseCase = submitPuzzleGuessUseCase,
        savePuzzleResultUseCase = savePuzzleResultUseCase,
        searchCardsUseCase = searchCardsUseCase,
    )

    @Test
    fun `initial load happy path resolves a fresh Playing state`() = runTest(dispatcher) {
        coEvery { getTodayPuzzleUseCase() } returns DataResult.Success(testPuzzle)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(PuzzleUiState.Loading, awaitItem())
            val playing = awaitItem() as PuzzleUiState.Playing
            assertEquals(testPuzzle, playing.puzzle)
            assertTrue(playing.guesses.isEmpty())
            assertFalse(playing.resumedFromCache)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume-from-cache path seeds guesses from a matching local in-progress result`() = runTest(dispatcher) {
        val previousGuess = buildGuessResult(name = "Counterspell", isCorrect = false)
        val localResult = PuzzleResult(
            puzzleDate = today,
            type = PuzzleType.GUESS_CARD,
            attempts = 1,
            solved = false,
            perfect = false,
            elapsedMs = 5_000L,
            startedAt = Clock.System.now(),
            guesses = listOf(previousGuess),
            completedAt = null,
        )
        coEvery { getPuzzleResultUseCase(today) } returns localResult
        coEvery { getTodayPuzzleUseCase() } returns DataResult.Success(testPuzzle)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(PuzzleUiState.Loading, awaitItem())
            val playing = awaitItem() as PuzzleUiState.Playing
            assertTrue(playing.resumedFromCache)
            assertEquals(listOf(previousGuess), playing.guesses)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offline path with no local cache emits Offline`() = runTest(dispatcher) {
        coEvery { getTodayPuzzleUseCase() } returns DataResult.Error("network down")

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(PuzzleUiState.Loading, awaitItem())
            assertEquals(PuzzleUiState.Offline, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submitting a non-winning guess appends to guesses and saves partial progress`() = runTest(dispatcher) {
        coEvery { getTodayPuzzleUseCase() } returns DataResult.Success(testPuzzle)
        val guessResult = buildGuessResult(name = "Counterspell", isCorrect = false)
        coEvery { submitPuzzleGuessUseCase(testPuzzle, "Counterspell") } returns Result.success(guessResult)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(PuzzleUiState.Loading, awaitItem())
            awaitItem() // fresh Playing state

            viewModel.onGuessQueryChange("Counterspell")
            viewModel.submitGuess()
            advanceUntilIdle()

            val updated = awaitItem() as PuzzleUiState.Playing
            assertEquals(1, updated.guesses.size)
            assertEquals(guessResult, updated.guesses.first())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { savePuzzleResultUseCase(match { it.completedAt == null && it.attempts == 1 && !it.solved }) }
    }

    @Test
    fun `submitting a correct guess transitions to Solved`() = runTest(dispatcher) {
        coEvery { getTodayPuzzleUseCase() } returns DataResult.Success(testPuzzle)
        val guessResult = buildGuessResult(name = "Lightning Bolt", isCorrect = true)
        coEvery { submitPuzzleGuessUseCase(testPuzzle, "Lightning Bolt") } returns Result.success(guessResult)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(PuzzleUiState.Loading, awaitItem())
            awaitItem() // fresh Playing state

            viewModel.onGuessQueryChange("Lightning Bolt")
            viewModel.submitGuess()
            advanceUntilIdle()

            val solved = awaitItem() as PuzzleUiState.Solved
            assertTrue(solved.result.solved)
            assertEquals(1, solved.result.attempts)
            assertTrue(solved.result.perfect)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { savePuzzleResultUseCase(match { it.completedAt != null && it.solved }) }
    }
}
