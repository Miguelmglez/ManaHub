package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.PuzzleTodayResponseDto
import com.mmg.manahub.core.model.puzzle.Puzzle
import com.mmg.manahub.core.model.puzzle.PuzzleType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * Maps a [PuzzleTodayResponseDto] to the domain [Puzzle] model. [PuzzleTodayResponseDto.payload]
 * (a raw [kotlinx.serialization.json.JsonElement]) is re-serialized back to a JSON string for
 * [Puzzle.payloadJson] — the per-[PuzzleType] shape is parsed one layer down by the use case that
 * consumes it (see [Puzzle]'s KDoc).
 */
fun PuzzleTodayResponseDto.toDomain(): Puzzle = Puzzle(
    date = LocalDate.parse(date),
    type = PuzzleType.fromWire(type),
    payloadJson = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), payload),
)
