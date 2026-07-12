package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTOs for deckstats.net's undocumented `api.php?action=get_deck` endpoint (D17 import-by-URL
 * adapter, Deck Doctor Community/Archetype plan, Phase 6).
 *
 * ## IMPORTANT — this shape is UNVERIFIED, per `docs/adr/ADR-004-community-api-contracts.md` §4
 * The Phase 3 API spike confirmed the endpoint is REACHABLE and confirmed the ERROR shape (a plain
 * TEXT 400 body, `"Deck not found (N)."`, despite `response_type=json` being requested — see
 * [com.mmg.manahub.core.data.remote.DeckstatsClient]'s KDoc), but a valid `(owner_id, id)` pair was
 * never available to probe the SUCCESS path live. This DTO is a best-effort transcription of the
 * shape documented by third-party deckstats importer implementations (a `sections`-array layout,
 * distinct from the alternative "single newline-delimited `cards` string blob" shape some tools
 * assume) — every field is defaulted/nullable and `ignoreUnknownKeys` is on so an unexpected real
 * shape degrades to an empty/unparsed deck (caught by [com.mmg.manahub.feature.decks.domain.usecase
 * .ImportDeckCardsUseCase], never a crash) rather than a hard parse failure. VERIFY against a real
 * public deck URL before this path is exposed to users at scale.
 */
@Serializable
data class DeckstatsGetDeckResponseDto(
    val deck: DeckstatsDeckDto? = null,
)

@Serializable
data class DeckstatsDeckDto(
    val name: String = "",
    val sections: List<DeckstatsSectionDto> = emptyList(),
)

@Serializable
data class DeckstatsSectionDto(
    val name: String = "",
    val cards: List<DeckstatsCardDto> = emptyList(),
)

@Serializable
data class DeckstatsCardDto(
    val name: String = "",
    val amount: Int = 1,
    val isCommander: Boolean = false,
)
