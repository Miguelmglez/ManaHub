package com.mmg.manahub.feature.communitydecks.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Paged search response from `GET /api/decks/v3/` (Phase 2).
 *
 * [next] is the absolute URL of the next page (null on the last page). Every field is
 * defaulted so the deserializer tolerates missing/unknown keys.
 */
@Serializable
data class ArchidektSearchResultDto(
    val count: Int = 0,
    val next: String? = null,
    val results: List<ArchidektDeckSummaryDto> = emptyList(),
)

@Serializable
data class ArchidektDeckSummaryDto(
    val id: Int,
    val name: String = "",
    val size: Int = 0,
    @SerialName("deckFormat") val deckFormat: Int = 7,
    val owner: ArchidektOwnerDto? = null,
    val viewCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val colors: Map<String, Int> = emptyMap(),
)
