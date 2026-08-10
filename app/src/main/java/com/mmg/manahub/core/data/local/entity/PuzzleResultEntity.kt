package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted daily-puzzle attempt/result, one row per calendar day the user attempted a puzzle
 * (Daily Puzzle feature, Batch B1 foundation). `puzzle_date` (ISO `yyyy-MM-dd`) is the primary key
 * — a device only ever has one attempt per day.
 *
 * `guesses_json` stores the JSON-serialized `List<PuzzleGuessResult>` (kotlinx.serialization) —
 * see `PuzzleRepositoryImpl`'s entity mapper for the round-trip.
 */
@Entity(tableName = "puzzle_results")
data class PuzzleResultEntity(
    @PrimaryKey @ColumnInfo(name = "puzzle_date") val puzzleDate: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "attempts") val attempts: Int,
    @ColumnInfo(name = "solved") val solved: Boolean,
    @ColumnInfo(name = "perfect") val perfect: Boolean,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "guesses_json") val guessesJson: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
)
