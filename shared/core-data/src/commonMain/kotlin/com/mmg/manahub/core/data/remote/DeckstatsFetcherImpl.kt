package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.remote.dto.DeckstatsGetDeckResponseDto
import com.mmg.manahub.feature.decks.domain.usecase.DeckstatsCard
import com.mmg.manahub.feature.decks.domain.usecase.DeckstatsDeck
import com.mmg.manahub.feature.decks.domain.usecase.DeckstatsFetcher
import kotlinx.serialization.json.Json

/**
 * `core-data`-side implementation of the `core-domain` [DeckstatsFetcher] seam (Deck Doctor
 * Community/Archetype plan, Phase 6, D17). Owns EVERYTHING `core-domain` must not: the Ktor
 * [DeckstatsClient] call, URL parsing, and the JSON→domain-model mapping — see
 * [DeckstatsFetcher]'s KDoc for why this split exists (the exact `CommunityAggregateApiContract`/
 * `SixtyFallbackFetcher` testing-seam precedent from Phase 3).
 *
 * Every failure path is caught HERE and mapped to a `null` return (never a propagated exception) —
 * [com.mmg.manahub.feature.decks.domain.usecase.ImportDeckCardsUseCase] only needs "it worked" vs.
 * "it didn't."
 */
class DeckstatsFetcherImpl(
    private val client: DeckstatsClient,
    private val crashReporter: CrashReporter,
) : DeckstatsFetcher {

    override suspend fun fetchDeck(url: String): DeckstatsDeck? {
        val (ownerId, deckId) = parseDeckstatsUrl(url) ?: run {
            crashReporter.log("deck_import_deckstats_bad_url")
            return null
        }

        val raw = try {
            client.getDeckRaw(ownerId, deckId)
        } catch (t: Throwable) {
            crashReporter.log("deck_import_deckstats_network_failed")
            // Security audit finding (2026-07-12, LOW): never pass the raw Ktor exception through —
            // its message embeds the full request URL (owner_id/deck_id, third-party deckstats.net
            // data). Record only the exception TYPE for diagnostic value, mirroring this codebase's
            // "never log raw external/free-text data" telemetry convention (CLAUDE.md's Telemetry
            // section) even though these ids are not ManaHub user PII.
            crashReporter.recordException(RuntimeException("deckstats_network_failed: ${t::class.simpleName}"))
            return null
        }

        if (!raw.isSuccess) {
            // Confirmed live (ADR-004 §4): a 400 body is PLAIN TEXT ("Deck not found (N)."), not
            // JSON, despite `response_type=json` — never fed to the JSON parser below.
            crashReporter.log("deck_import_deckstats_not_found")
            return null
        }

        val dto = try {
            JSON.decodeFromString(DeckstatsGetDeckResponseDto.serializer(), raw.body)
        } catch (t: Throwable) {
            // The success-path shape was never verified live (ADR-004 §4) — a shape mismatch here
            // is EXPECTED until this adapter is checked against a real response; a dedicated
            // (non-generic) telemetry tag makes that failure mode easy to distinguish from a
            // genuine network/not-found error.
            crashReporter.log("deck_import_deckstats_unparseable_shape")
            crashReporter.recordException(t)
            return null
        }

        val deck = dto.deck ?: return null
        val cards = deck.sections.flatMap { section ->
            val isSideboard = section.name.contains("side", ignoreCase = true)
            section.cards.map { c -> DeckstatsCard(name = c.name, quantity = c.amount, isSideboard = isSideboard, isCommander = c.isCommander) }
        }
        if (cards.isEmpty()) return null
        return DeckstatsDeck(name = deck.name.ifBlank { null }, cards = cards)
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
