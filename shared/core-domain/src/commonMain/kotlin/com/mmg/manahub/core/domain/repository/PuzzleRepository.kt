package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.puzzle.Puzzle
import com.mmg.manahub.core.model.puzzle.PuzzleResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Contract for the Daily Puzzle feature's data layer. The implementation ([PuzzleRepositoryImpl] in
 * `:app`) composes a Ktor-backed remote fetch of today's puzzle with a Room-backed local store of
 * past attempts (Room stays `androidMain`-only per the KMP migration's data-layer rule — see
 * `docs/plans/kmp-migration-plan.md`).
 */
interface PuzzleRepository {

    /** Fetches today's puzzle from the puzzle-generation backend. */
    suspend fun getTodayPuzzle(): DataResult<Puzzle>

    /** Reads the locally-persisted attempt/result for [date], or null if the user never attempted it. */
    suspend fun getPuzzleResult(date: LocalDate): PuzzleResult?

    /**
     * Atomically persists [result]. When [result].solved is true, emits
     * [com.mmg.manahub.core.gamification.domain.event.ProgressionEvent.PuzzleSolved] on the
     * [com.mmg.manahub.core.gamification.domain.ProgressionEventBus] after a successful write (the
     * canonical write-path pattern — see ADR-002 §1 and `TradeCompleted`'s emission in
     * `TradesRepositoryImpl.acceptProposal`).
     */
    suspend fun savePuzzleResult(result: PuzzleResult)

    /** Observes every locally-persisted puzzle result, newest first. */
    fun observeResults(): Flow<List<PuzzleResult>>
}
