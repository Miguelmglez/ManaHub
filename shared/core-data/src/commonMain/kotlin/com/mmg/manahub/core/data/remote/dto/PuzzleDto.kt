package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Response DTO for the `manahub-draft-api` Cloudflare Worker's `GET puzzle/today` endpoint (Daily
 * Puzzle feature, Batch B1 foundation — same Worker that serves draft content, see
 * [com.mmg.manahub.core.data.remote.CloudflareContentClient]).
 *
 * [payload] is kept as a raw [JsonElement] rather than a typed model: its shape depends on [type]
 * (e.g. a `GuessCardPayload` shape for `"GUESS_CARD"`), and this module has no per-puzzle-type
 * coupling — [com.mmg.manahub.core.data.remote.mapper] re-serializes it back to a JSON string for
 * the domain-layer `Puzzle.payloadJson`, which the consuming use case parses.
 */
@Serializable
data class PuzzleTodayResponseDto(
    val date: String = "",
    val type: String = "",
    val payload: JsonElement = JsonNull,
)
