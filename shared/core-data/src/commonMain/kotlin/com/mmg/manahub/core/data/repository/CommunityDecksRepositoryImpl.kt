package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CommunityDeckCache
import com.mmg.manahub.core.data.network.ArchidektRequestQueue
import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.domain.repository.CommunityDecksRepository
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.model.DataResult
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.withContext

/**
 * Maximum number of cards [CommunityDecksRepositoryImpl.searchDecksMultiCard] will fan out to —
 * any extra cards in [CommunityDeckSearchFilters.cardNames] beyond this are silently dropped
 * (the UI layer caps selection at this same number, see `CommunityDecksSearchUiState.kt`'s
 * `MAX_COMMUNITY_CARD_FILTERS`, so this is defense-in-depth, not the primary gate).
 */
private const val MULTI_CARD_MAX = 3

/** Pages fetched PER CARD when fanning out a multi-card search (bounded, one-shot — no deepening). */
private const val MULTI_CARD_PAGES_PER_CARD = 3

/** Archidekt's server-side page-size cap; every returned row is consumed, never `.take()`-truncated. */
private const val MULTI_CARD_PAGE_SIZE = 60

/**
 * Cache-first implementation of [CommunityDecksRepository].
 *
 * Strategy for [getDeckById]:
 * 1. Return a FRESH cache hit immediately (no network call).
 * 2. Otherwise fetch from Archidekt (rate-limited via [requestQueue]), cache it, return it.
 * 3. On network failure (HTTP or otherwise), fall back to a STALE cache hit flagged
 *    `isStale = true`; only when no cache exists at all is a [DataResult.Error] surfaced.
 *
 * All work runs on [DispatcherProvider.io].
 *
 * @param api           the Archidekt HTTP client (Ktor-based, already in commonMain).
 * @param requestQueue  rate-limiter / retry queue for Archidekt calls.
 * @param cache         platform-neutral cache abstraction (Room on Android, IndexedDB/no-op on web).
 * @param crashReporter platform-neutral crash/log reporter (Crashlytics on Android, no-op on web).
 * @param dispatcherProvider platform dispatchers (IO on Android, Default on web).
 */
class CommunityDecksRepositoryImpl(
    private val api: ArchidektClient,
    private val requestQueue: ArchidektRequestQueue,
    private val cache: CommunityDeckCache,
    private val crashReporter: CrashReporter,
    private val dispatcherProvider: DispatcherProvider,
) : CommunityDecksRepository {

    override suspend fun getDeckById(archidektId: Int): DataResult<CommunityDeck> =
        withContext(dispatcherProvider.io) {
            try {
                // 1. Fresh cache hit → serve directly.
                val cached = cache.getById(archidektId)
                if (cached != null && CachePolicy.isFresh(cached.cachedAt)) {
                    return@withContext DataResult.Success(cached.dto.toDomain())
                }

                // 2. Fetch from network (rate-limited + retried by the queue).
                val dto = requestQueue.execute { api.getDeckById(archidektId) }

                // Cache the response for offline re-render / stale fallback.
                cache.insert(dto)

                DataResult.Success(dto.toDomain())
            } catch (e: ResponseException) {
                // HTTP error → try stale cache before failing.
                val statusCode = e.response.status.value
                val stale = cache.getById(archidektId)
                crashReporter.log("community_deck_fetch")
                crashReporter.recordException(e)
                crashReporter.setCustomKey("community_deck_archidekt_id", archidektId.toString())
                crashReporter.setCustomKey("community_deck_http_code", statusCode.toString())
                crashReporter.setCustomKey("community_deck_stale_fallback", (stale != null).toString())
                if (stale != null) {
                    DataResult.Success(stale.dto.toDomain(), isStale = true)
                } else {
                    val message = when (statusCode) {
                        404 -> "Deck not found on Archidekt"
                        429 -> "Too many requests. Please try again later."
                        else -> "Network error: $statusCode"
                    }
                    DataResult.Error(message)
                }
            } catch (e: Exception) {
                // Coroutine cancellation (e.g. the ViewModel job was cancelled by a newer search)
                // is not a search failure — rethrow so the caller's coroutine actually stops
                // instead of racing a stale UI update against the cancelling caller. See
                // CommunityDecksRepositoryImplTest / feedback_cancellation_swallowed_in_repo_catch.
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Any other failure (IO, parse, …) → try stale cache before failing.
                val stale = cache.getById(archidektId)
                crashReporter.log("community_deck_fetch")
                crashReporter.recordException(e)
                crashReporter.setCustomKey("community_deck_archidekt_id", archidektId.toString())
                if (stale != null) {
                    DataResult.Success(stale.dto.toDomain(), isStale = true)
                } else {
                    DataResult.Error(e.message ?: "Unknown error")
                }
            }
        }

    override suspend fun searchDecks(
        filters: CommunityDeckSearchFilters,
    ): DataResult<CommunityDeckSearchResult> = withContext(dispatcherProvider.io) {
        // The most specific text signal driving this query, used only for length-only telemetry
        // (never the raw text — see CLAUDE.md telemetry rules).
        val querySignalLength =
            (filters.cardNames.firstOrNull() ?: filters.commanderName ?: filters.deckName)?.length ?: 0

        try {
            // Multi-card search (Archidekt multi-card expansion, 2026-07-24): Archidekt's API only
            // accepts a single `cardName` per request, so 2-3 cards is decomposed client-side into
            // one search per card intersected by deck id. Runs INSIDE this same try so a genuine
            // network/HTTP failure anywhere in the fan-out (e.g. a 429 mid-flow) is caught by the
            // SAME catch blocks below and gets the same error mapping as the single-card path.
            if (filters.cardNames.size > 1) {
                return@withContext searchDecksMultiCard(filters)
            }

            val dto = requestQueue.execute { api.searchDecks(filters) }

            // Archidekt signals a server-side statement timeout with count = -1 and an
            // empty result set (commonly on a popular card/commander + format combination).
            if (dto.count < 0) {
                crashReporter.log("community_deck_search_server_timeout")
                crashReporter.recordException(
                    IllegalStateException("Archidekt search timeout: count=${dto.count}"),
                )
                crashReporter.setCustomKey(
                    "community_search_timeout_format",
                    filters.deckFormatId?.toString() ?: "none",
                )
                crashReporter.setCustomKey(
                    "community_search_timeout_query_len",
                    querySignalLength.toString(),
                )
                return@withContext DataResult.Error(
                    "Search timed out. Try a more specific query or remove the format filter.",
                )
            }

            DataResult.Success(dto.toDomain())
        } catch (e: ResponseException) {
            val statusCode = e.response.status.value
            crashReporter.log("community_deck_search")
            crashReporter.recordException(e)
            crashReporter.setCustomKey("community_deck_search_http_code", statusCode.toString())
            val message = when (statusCode) {
                429 -> "Too many requests. Please try again later."
                else -> "Search failed: $statusCode"
            }
            DataResult.Error(message)
        } catch (e: Exception) {
            // Coroutine cancellation (e.g. searchJob?.cancel() from a newer search triggered while
            // the multi-card fan-out is still mid-flight — see searchDecksMultiCard's KDoc) is not
            // a search failure — rethrow so the caller's coroutine actually stops instead of
            // continuing past the cancellation point and racing a stale isLoading/error update
            // against the newer search's legitimate result.
            if (e is kotlinx.coroutines.CancellationException) throw e
            crashReporter.log("community_deck_search")
            crashReporter.recordException(e)
            crashReporter.setCustomKey(
                "community_deck_search_error_type",
                e::class.simpleName ?: "Unknown",
            )
            crashReporter.setCustomKey(
                "community_deck_search_query_len",
                querySignalLength.toString(),
            )
            DataResult.Error(e.message ?: "Search failed")
        }
    }

    /**
     * Multi-card fan-out for [searchDecks] (Archidekt multi-card search expansion, 2026-07-24).
     *
     * Archidekt's `cardName` filter only accepts ONE value per request — repeated `cardName`
     * params reliably trigger a server-side statement timeout (verified live, see
     * `docs/adr/ADR-004-community-api-contracts.md` §1/§1b), so [ArchidektClient] refuses more
     * than one. This method decomposes an N-card search (N capped at [MULTI_CARD_MAX]) into N
     * independent single-card searches, each paged up to [MULTI_CARD_PAGES_PER_CARD] times at
     * [MULTI_CARD_PAGE_SIZE] (Archidekt's own page-size cap), and intersects the resulting deck
     * ids client-side — the FIRST card's fetch order anchors the final result order.
     *
     * ## Known approximation
     * Each card's candidate pool is bounded to its top ~180 decks (3 pages × 60) under the
     * request's sort order — a deck that contains every requested card but doesn't rank in every
     * card's own top ~180 is missed. This is a deliberate, documented trade-off (bounded latency:
     * at most `[MULTI_CARD_MAX] * MULTI_CARD_PAGES_PER_CARD` = 9 queued requests) rather than a
     * bug; the UI surfaces this via a caption when 2+ cards are selected.
     *
     * [CommunityDeckSearchFilters.page] / [CommunityDeckSearchFilters.pageSize] on the INCOMING
     * [filters] are ignored on this path — pagination is fully internal to the fan-out (each
     * per-card sub-request builds its own `page`/`pageSize` via `filters.copy(...)`) — the
     * returned [CommunityDeckSearchResult.hasMore] is always `false` (deepening the fan-out would
     * both break the anchor sort order and multiply the request count further); `totalCount`
     * honestly reports the final intersected deck count, not Archidekt's per-card `count`.
     *
     * Any per-card server-side timeout (`count = -1`) fails the WHOLE search with an actionable,
     * card-naming error (D-E) rather than silently dropping that card's constraint. An empty
     * intersection after any card is a valid empty [DataResult.Success] — the fan-out exits early
     * (remaining cards are never queried) rather than continuing to burn requests on a search that
     * can no longer produce a match.
     */
    private suspend fun searchDecksMultiCard(
        filters: CommunityDeckSearchFilters,
    ): DataResult<CommunityDeckSearchResult> {
        val cards = filters.cardNames.take(MULTI_CARD_MAX)
        crashReporter.log("community_deck_search_multicard")
        crashReporter.setCustomKey("community_search_card_count", cards.size.toString())

        lateinit var anchor: LinkedHashMap<Int, ArchidektDeckSummaryDto>
        var intersection: Set<Int> = emptySet()

        for ((index, card) in cards.withIndex()) {
            when (val fetch = fetchCardDecks(card, filters)) {
                is MultiCardFetch.Timeout -> return multiCardTimeoutError(fetch.card, cardIndex = index)
                is MultiCardFetch.Ok -> {
                    intersection = if (index == 0) {
                        anchor = fetch.decks
                        fetch.decks.keys
                    } else {
                        intersection.intersect(fetch.decks.keys)
                    }
                }
            }

            if (intersection.isEmpty()) {
                // Empty intersection is a valid, honest empty result — exit before querying any
                // remaining cards (D-E: "early-exit remaining cards' requests").
                crashReporter.setCustomKey("community_search_multicard_matches", "0")
                return DataResult.Success(
                    CommunityDeckSearchResult(totalCount = 0, hasMore = false, decks = emptyList()),
                )
            }
        }

        val decks = anchor.values
            .filter { it.id in intersection }
            .map { it.toDomain() }

        crashReporter.setCustomKey("community_search_multicard_matches", decks.size.toString())
        return DataResult.Success(CommunityDeckSearchResult(totalCount = decks.size, hasMore = false, decks = decks))
    }

    /**
     * Fetches every page (up to [MULTI_CARD_PAGES_PER_CARD]) of a single-card search for [card],
     * deduping results by deck id (a deck can legitimately reappear across pages if Archidekt's
     * underlying ordering shifts between calls). Stops early once a page's `next` is null.
     */
    private suspend fun fetchCardDecks(
        card: String,
        filters: CommunityDeckSearchFilters,
    ): MultiCardFetch {
        val decks = LinkedHashMap<Int, ArchidektDeckSummaryDto>()
        for (page in 1..MULTI_CARD_PAGES_PER_CARD) {
            val dto = requestQueue.execute {
                api.searchDecks(
                    filters.copy(cardNames = listOf(card), page = page, pageSize = MULTI_CARD_PAGE_SIZE),
                )
            }
            if (dto.count < 0) return MultiCardFetch.Timeout(card)
            dto.results.forEach { decks[it.id] = it }
            if (dto.next == null) break
        }
        return MultiCardFetch.Ok(decks)
    }

    /** Actionable, card-naming error for a per-card statement timeout during a multi-card fan-out (D-E). */
    private fun multiCardTimeoutError(card: String, cardIndex: Int): DataResult.Error {
        crashReporter.log("community_deck_search_multicard_timeout")
        // Never embed the raw card name in the reported exception message (CLAUDE.md telemetry
        // rule: no raw free-text in Crashlytics) — it also fragments issue grouping, since
        // Crashlytics groups by exception type + message and every distinct card name would mint
        // its own "issue" instead of one aggregated timeout issue. Index-only, matching this
        // file's other telemetry (e.g. querySignalLength in searchDecks). The user-facing
        // DataResult.Error message below is allowed to name the card — it's shown in-app only.
        crashReporter.recordException(
            IllegalStateException("Archidekt multi-card search timeout at cardIndex=$cardIndex"),
        )
        crashReporter.setCustomKey("community_search_timeout_card_index", cardIndex.toString())
        return DataResult.Error(
            "'$card' is too popular to filter on with other cards. Try removing it or searching for it alone.",
        )
    }

    /** Outcome of one card's [fetchCardDecks] fan-out leg. */
    private sealed interface MultiCardFetch {
        /** [decks] is dedupe-by-id, insertion-ordered (page 1 first) across every fetched page. */
        data class Ok(val decks: LinkedHashMap<Int, ArchidektDeckSummaryDto>) : MultiCardFetch

        /** Archidekt's server-side statement timeout (`count = -1`) fired for [card]. */
        data class Timeout(val card: String) : MultiCardFetch
    }
}
