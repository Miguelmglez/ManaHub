package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.PuzzleDao
import com.mmg.manahub.core.data.local.entity.PuzzleResultEntity
import com.mmg.manahub.core.data.remote.PuzzleApiContract
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.domain.repository.PuzzleRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.puzzle.Puzzle
import com.mmg.manahub.core.model.puzzle.PuzzleGuessResult
import com.mmg.manahub.core.model.puzzle.PuzzleResult
import com.mmg.manahub.core.model.puzzle.PuzzleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * KMP migration — Koin-native (plain class, no `@Inject`/`@Singleton`); built as a Koin `single` in
 * [com.mmg.manahub.feature.puzzle.di.puzzleKoinModule].
 *
 * Composes a KMP-pure remote fetch ([remote]) with a Room-backed local store ([puzzleDao]) — Room
 * stays `androidMain`-only per the KMP migration's data-layer rule, so this repository impl (which
 * touches [PuzzleDao] directly) lives in `:app`, exactly like `TradesRepositoryImpl`
 * (`feature/trades/data/repository/`) touches `CardDao` directly.
 */
class PuzzleRepositoryImpl(
    private val remote: PuzzleApiContract,
    private val puzzleDao: PuzzleDao,
    private val progressionEventBus: ProgressionEventBus,
) : PuzzleRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getTodayPuzzle(): DataResult<Puzzle> =
        runCatching { remote.getTodayPuzzle().toDomain() }
            .fold(
                onSuccess = { DataResult.Success(it) },
                onFailure = { e -> DataResult.Error(e.message ?: "Unknown error") },
            )

    override suspend fun getPuzzleResult(date: LocalDate): PuzzleResult? =
        puzzleDao.getByDate(date.toString())?.toDomain(json)

    override suspend fun savePuzzleResult(result: PuzzleResult) {
        puzzleDao.upsert(result.toEntity(json))

        // Emit only after a successful write (ADR-002 §1), mirroring TradeCompleted's emission in
        // TradesRepositoryImpl.acceptProposal. Idempotency key puzzle:{date}:solved means a repeat
        // save of the same solved day (e.g. a retry) grants XP at most once.
        if (result.solved) {
            progressionEventBus.emit(
                ProgressionEvent.PuzzleSolved(
                    puzzleDate = result.puzzleDate,
                    type = result.type.name,
                    attemptsUsed = result.attempts,
                    perfect = result.perfect,
                    occurredAt = Clock.System.now(),
                )
            )
        }
    }

    override fun observeResults(): Flow<List<PuzzleResult>> =
        puzzleDao.observeAll().map { entities -> entities.map { it.toDomain(json) } }

    private fun PuzzleResultEntity.toDomain(json: Json): PuzzleResult = PuzzleResult(
        puzzleDate = LocalDate.parse(puzzleDate),
        type = PuzzleType.fromWire(type),
        attempts = attempts,
        solved = solved,
        perfect = perfect,
        elapsedMs = elapsedMs,
        startedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(startedAt),
        guesses = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(PuzzleGuessResult.serializer()),
            guessesJson,
        ),
        completedAt = completedAt?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it) },
    )

    private fun PuzzleResult.toEntity(json: Json): PuzzleResultEntity = PuzzleResultEntity(
        puzzleDate = puzzleDate.toString(),
        type = type.name,
        attempts = attempts,
        solved = solved,
        perfect = perfect,
        elapsedMs = elapsedMs,
        startedAt = startedAt.toEpochMilliseconds(),
        guessesJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(PuzzleGuessResult.serializer()),
            guesses,
        ),
        completedAt = completedAt?.toEpochMilliseconds(),
    )
}
