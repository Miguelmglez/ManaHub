package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.dao.PuzzleDao
import com.mmg.manahub.core.data.local.entity.PuzzleResultEntity
import com.mmg.manahub.core.data.remote.PuzzleApiContract
import com.mmg.manahub.core.data.remote.dto.PuzzleTodayResponseDto
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PuzzleRepositoryImpl], added by the 2026-08-06 telemetry/edge-case audit pass
 * (F1/F2 + the `observeResults` per-row isolation fix). Covers: [PuzzleRepositoryImpl.getTodayPuzzle]
 * reporting a Worker fetch failure via the injected [CrashReporter] instead of swallowing it
 * silently, [PuzzleRepositoryImpl.getPuzzleResult] degrading a corrupt `guesses_json` row to `null`
 * (instead of throwing) while still reporting it, and [PuzzleRepositoryImpl.observeResults] skipping
 * a corrupt row rather than killing the whole flow for every collector.
 */
class PuzzleRepositoryImplTest {

    private val remote: PuzzleApiContract = mockk()
    private val puzzleDao: PuzzleDao = mockk()
    private val crashReporter: CrashReporter = mockk(relaxed = true)
    private val progressionEventBus = ProgressionEventBus()

    private lateinit var repository: PuzzleRepositoryImpl

    @Before
    fun setUp() {
        repository = PuzzleRepositoryImpl(
            remote = remote,
            puzzleDao = puzzleDao,
            progressionEventBus = progressionEventBus,
            crashReporter = crashReporter,
        )
    }

    private fun validEntity(date: String = "2026-08-06") = PuzzleResultEntity(
        puzzleDate = date,
        type = "GUESS_CARD",
        attempts = 1,
        solved = true,
        perfect = true,
        elapsedMs = 1_000L,
        startedAt = 0L,
        guessesJson = "[]",
        completedAt = 1_000L,
    )

    // ── F1: getTodayPuzzle Worker-fetch failure ───────────────────────────────────────────────────

    @Test
    fun `getTodayPuzzle failure reports a non-fatal and returns DataResult Error`() = runTest {
        val failure = RuntimeException("Worker unreachable")
        coEvery { remote.getTodayPuzzle() } throws failure

        val result = repository.getTodayPuzzle()

        assertTrue(result is DataResult.Error)
        verify { crashReporter.recordException(failure) }
        verify { crashReporter.log("puzzle_fetch_failed") }
        verify { crashReporter.setCustomKey("puzzle_fetch_error_type", "RuntimeException") }
    }

    @Test
    fun `getTodayPuzzle success maps the DTO without touching the CrashReporter`() = runTest {
        val dto = PuzzleTodayResponseDto(date = "2026-08-06", type = "GUESS_CARD", payload = JsonNull)
        coEvery { remote.getTodayPuzzle() } returns dto

        val result = repository.getTodayPuzzle()

        assertTrue(result is DataResult.Success)
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    // ── F2: getPuzzleResult corrupt guesses_json ──────────────────────────────────────────────────

    @Test
    fun `getPuzzleResult with corrupt guesses_json returns null instead of throwing`() = runTest {
        val corrupt = validEntity().copy(guessesJson = "not valid json")
        coEvery { puzzleDao.getByDate("2026-08-06") } returns corrupt

        val result = repository.getPuzzleResult(LocalDate.parse("2026-08-06"))

        assertNull(result)
        verify { crashReporter.recordException(any()) }
        verify { crashReporter.log("puzzle_resume_parse_failed") }
    }

    @Test
    fun `getPuzzleResult with a valid row decodes normally without touching the CrashReporter`() = runTest {
        coEvery { puzzleDao.getByDate("2026-08-06") } returns validEntity()

        val result = repository.getPuzzleResult(LocalDate.parse("2026-08-06"))

        assertEquals(LocalDate.parse("2026-08-06"), result?.puzzleDate)
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    @Test
    fun `getPuzzleResult with no local row returns null`() = runTest {
        coEvery { puzzleDao.getByDate("2026-08-06") } returns null

        val result = repository.getPuzzleResult(LocalDate.parse("2026-08-06"))

        assertNull(result)
    }

    // ── observeResults per-row isolation ──────────────────────────────────────────────────────────

    @Test
    fun `observeResults skips a corrupt row instead of killing the flow`() = runTest {
        val corrupt = validEntity(date = "2026-08-05").copy(guessesJson = "not valid json")
        val valid = validEntity(date = "2026-08-06")
        val rows = MutableStateFlow(listOf(valid, corrupt)).asStateFlow()
        every { puzzleDao.observeAll() } returns rows

        // The flow must emit successfully (not throw) with only the valid row kept.
        val emitted = repository.observeResults().first()

        assertEquals(1, emitted.size)
        assertEquals("2026-08-06", emitted.first().puzzleDate.toString())
    }
}
