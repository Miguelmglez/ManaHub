package com.mmg.manahub.core.model

/**
 * Every filter Archidekt's `GET /api/decks/v3/` search endpoint actually supports, verified live
 * 2026-07-15 (see `docs/adr/ADR-004-community-api-contracts.md` §1b). Replaces the old positional
 * `cardNames/deckFormat/orderBy/page/pageSize` parameter list on [com.mmg.manahub.core.data.remote.ArchidektClient.searchDecks]
 * / [com.mmg.manahub.core.domain.repository.CommunityDecksRepository.searchDecks] so new filters
 * can be added without growing a positional signature further.
 *
 * Every field is optional/defaulted and mapped to a query param only when non-null/non-empty by
 * the client — omitted parameters are simply not sent to Archidekt.
 *
 * @property deckName substring filter on the deck's own name (Archidekt `name`).
 * @property cardNames cards the deck must contain (Archidekt `cardName`). Archidekt's search API
 *   only accepts a SINGLE `cardName` per request — repeated `cardName` params reliably trigger a
 *   server-side statement timeout (verified live 2026-07-24, see
 *   `docs/adr/ADR-004-community-api-contracts.md` §1/§1b). Multi-card filtering is therefore
 *   decomposed client-side: `size <= 1` calls Archidekt directly
 *   ([com.mmg.manahub.core.data.remote.ArchidektClient.searchDecks] sends `singleOrNull()`);
 *   `size > 1` is fanned out to one search per card with the results intersected by deck id
 *   (`com.mmg.manahub.core.data.repository.CommunityDecksRepositoryImpl.searchDecksMultiCard`).
 * @property commanderName exact commander name (Archidekt `commanderName`).
 * @property ownerUsername exact Archidekt owner username (Archidekt `ownerUsername`; NOT `owner`,
 *   which is silently ignored by the API).
 * @property deckFormatId Archidekt's numeric `deckFormat` id, or `null` for no format filter.
 * @property edhBracket Commander bracket `1..5`, or `null` for no filter.
 * @property colors WUBRG letters (`W`,`U`,`B`,`R`,`G`; no colorless token exists on this API).
 *   Sent as a single comma-joined `colors` param — Archidekt treats this as an EXACT color-set
 *   match, there is no "includes"/"at most" mode.
 * @property size exact deck-card-count match (Archidekt `size`; `sizeComp` comparators are
 *   ignored server-side, so only equality is exposed here).
 * @property primersOnly when true, only decks with a primer (Archidekt `primers=true`).
 * @property deckTagName exact deck-tag name (Archidekt `deckTagName`, singular) — verified live
 *   2026-08-18 as a real, working, validated exact-match filter (a bad value returns a clean
 *   `{"count":-1,"message":"No deck tag name \`X\` was found"}` from Archidekt). This is NOT the
 *   same as `deckTags`/`tags`/`tagIds` (plural/alternate names), which stay dead/unexposed — see
 *   [com.mmg.manahub.core.data.remote.ArchidektClient.searchDecks]'s KDoc.
 * @property orderBy Archidekt `orderBy` value; only `-viewCount`/`-createdAt`/`-updatedAt` are
 *   verified credible (see the ADR).
 * @property page 1-based page number.
 * @property pageSize requested page size. Archidekt's `pageSize` is unreliable at both ends
 *   (small values return MORE rows, values > 60 are capped) — callers must still `.take(n)`
 *   client-side regardless of what is requested here.
 */
data class CommunityDeckSearchFilters(
    val deckName: String? = null,
    val cardNames: List<String> = emptyList(),
    val commanderName: String? = null,
    val ownerUsername: String? = null,
    val deckFormatId: Int? = null,
    val edhBracket: Int? = null,
    val colors: Set<String> = emptySet(),
    val size: Int? = null,
    val primersOnly: Boolean = false,
    val deckTagName: String? = null,
    val orderBy: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20,
)
