package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape of the Supabase `get_community_stats()` RPC (Home feature overhaul Phase 1.2.b).
 *
 * Contract confirmed against the deployed migration `home_community_stats_contract_fixes`:
 * ```json
 * { "most_wishlisted": [ { "card_id": "...", "name": null, "count": 3 } ],
 *   "milestones": [ { "id": "active_collectors", "label": "Active collectors", "value": "4" },
 *                    { "id": "decks_built", "label": "Decks built", "value": "29" } ] }
 * ```
 * [MostWishlistedRowDto.name] is ALWAYS null server-side (the `cards` catalog table the RPC would
 * join against is unpopulated) — the display name is resolved client-side from [MostWishlistedRowDto.cardId]
 * via the local Scryfall/Room card cache (see `CommunityStatsRepositoryImpl`/`HomeViewModel`).
 * [MilestoneRowDto.value] is a pre-formatted string, not a number.
 */
@Serializable
data class CommunityStatsRpcDto(
    @SerialName("most_wishlisted") val mostWishlisted: List<MostWishlistedRowDto> = emptyList(),
    @SerialName("milestones") val milestones: List<MilestoneRowDto> = emptyList(),
)

@Serializable
data class MostWishlistedRowDto(
    @SerialName("card_id") val cardId: String,
    val name: String? = null,
    val count: Int = 0,
)

@Serializable
data class MilestoneRowDto(
    val id: String,
    val label: String,
    val value: String,
)
