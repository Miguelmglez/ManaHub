package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.network.ScryfallCache
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.dto.CardCollectionRequestDto
import com.mmg.manahub.core.data.remote.dto.CardCollectionResponseDto
import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.CardIdentifierDto
import com.mmg.manahub.core.data.remote.dto.SearchResultDto
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.PLAYABLE_SET_TYPES
import com.mmg.manahub.core.model.SetType
import kotlinx.coroutines.withContext

/**
 * Remote data source for the Scryfall API.
 *
 * All calls are rate-limited by [ScryfallRequestQueue] and memoised by [ScryfallCache].
 * Network calls run on [DispatcherProvider.io] (KMP-safe replacement for `Dispatchers.IO`).
 *
 * @param api            Ktor-based [ScryfallClient].
 * @param requestQueue   Rate-limiting queue (max 10 req/s, 100 ms min between requests).
 * @param cache          In-memory LRU cache with per-resource TTL.
 * @param dispatcherProvider Platform dispatcher abstraction.
 * @param crashReporter  Optional platform-neutral crash/log reporter (see [CrashReporter]). `null`
 *   (the default) keeps every telemetry hook a silent no-op -- safe for tests and any construction
 *   site that doesn't need the WS7 cache-ratio signal. The sole prod DI site is
 *   `SharedDomainUseCaseModule.provideScryfallRemoteDataSource`.
 */
class ScryfallRemoteDataSource(
    private val api: ScryfallClient,
    private val requestQueue: ScryfallRequestQueue,
    private val cache: ScryfallCache,
    private val dispatcherProvider: DispatcherProvider,
    private val crashReporter: CrashReporter? = null,
) {
    /**
     * WS7 telemetry (backend-performance-optimization-plan.md, 2026-07-29): approximate, non-atomic
     * (by design -- see the WS7 audit's "approximate counters are fine for telemetry" note) session
     * hit/miss counters for [searchCardsPaginated]'s cache, the hottest fix in the WS4a batch (feeds
     * every debounced Add Card keystroke and the Home spotlight feed's page-walk).
     */
    private var paginatedCacheHits = 0L
    private var paginatedCacheMisses = 0L

    suspend fun searchCardByName(query: String, set: String? = null): Result<Card> =
        safeCall {
            val cacheKey = "fuzzy:${query.lowercase().trim()}:${set.orEmpty()}"
            cache.cardNames.getOrFetch(cacheKey) {
                val card = requestQueue.execute { api.getCardByName(query, set) }.toDomain()
                // Also populate the ID-based card cache
                cache.cards.put(card.scryfallId, card)
                card
            }
        }

    suspend fun getCardByExactName(name: String): Result<Card> =
        safeCall {
            val cacheKey = "exact:${name.lowercase().trim()}"
            cache.cardNames.getOrFetch(cacheKey) {
                val card = requestQueue.execute { api.getCardByExactName(name) }.toDomain()
                cache.cards.put(card.scryfallId, card)
                card
            }
        }

    /**
     * Searches Scryfall for cards matching [query], page [page].
     *
     * Results are memoised in [ScryfallCache.searches] keyed by `query:page`. When [bypassCache]
     * is true the memoised path is skipped and the loader always runs -- required for queries that
     * embed `order:random`, where a stable cache key would otherwise return the same page on every
     * "refresh" (see Home Discover/Random-card widgets). The individual card cache is still
     * populated either way. The request remains rate-limited inside [ScryfallRequestQueue].
     *
     * NOTE: bypassing the in-memory [ScryfallCache.searches] map alone is NOT enough -- the OkHttp
     * disk cache also serves `/cards/search` responses (the network interceptor forces
     * `Cache-Control: public, max-age=300` on them), so the identical `order:random` URL would still
     * be replayed from disk for 5 minutes. On the [bypassCache] path the loader therefore calls the
     * no-cache endpoint [ScryfallClient.searchCardsNoCache] (`Cache-Control: no-cache`) and forces
     * `order = "random"` so every refresh hits the network and yields genuinely different cards.
     */
    suspend fun searchCards(
        query: String,
        page: Int = 1,
        bypassCache: Boolean = false,
    ): Result<List<Card>> =
        safeCall {
            val loader: suspend () -> List<Card> = {
                val response = requestQueue.execute {
                    if (bypassCache) {
                        // Bypass the OkHttp disk cache and force server-side random ordering.
                        api.searchCardsNoCache(query, order = "random", page = page)
                    } else {
                        api.searchCards(query, page = page)
                    }
                }
                val cards = response.data.toDomain()
                // Populate card cache with individual results
                cards.forEach { card -> cache.cards.put(card.scryfallId, card) }
                cards
            }
            if (bypassCache) {
                loader()
            } else {
                val cacheKey = "${query.lowercase().trim()}:$page"
                cache.searches.getOrFetch(cacheKey, loader)
            }
        }

    /**
     * Same shape as [searchCards], but also surfaces Scryfall's `has_more` flag via
     * [com.mmg.manahub.core.model.PaginatedCards] -- used by [query]-paged/paginating callers
     * (Add Card search, [com.mmg.manahub.core.domain.usecase.card.GetSpotlightFeedUseCase]'s
     * page-walk over an entire set).
     *
     * WS4a finding 1 (Backend & Performance Optimization plan, 2026-07-28): this used to bypass
     * caching entirely on the non-[bypassCache] path (it needed a `TimedLruCache<String,
     * PaginatedCards>`, which didn't exist -- [cache.searches] is `List<Card>`-typed and doesn't fit
     * the `hasMore` flag). Now routes through [ScryfallCache.paginatedSearches], mirroring
     * [searchCards]'s [cache.searches] usage exactly. Individual results are still additionally
     * cached in [cache.cards].
     */
    suspend fun searchCardsPaginated(
        query: String,
        page: Int = 1,
        bypassCache: Boolean = false,
    ): Result<com.mmg.manahub.core.model.PaginatedCards> =
        safeCall {
            val loader: suspend () -> com.mmg.manahub.core.model.PaginatedCards = {
                val response = requestQueue.execute {
                    if (bypassCache) {
                        api.searchCardsNoCache(query, order = "random", page = page)
                    } else {
                        api.searchCards(query, page = page)
                    }
                }
                val cards = response.data.toDomain()
                cards.forEach { card -> cache.cards.put(card.scryfallId, card) }
                com.mmg.manahub.core.model.PaginatedCards(cards, response.hasMore, response.totalCards)
            }
            if (bypassCache) {
                loader()
            } else {
                val cacheKey = "paginated:${query.lowercase().trim()}:$page"
                // WS7 telemetry: peek before getOrFetch's own (internal, also-a-hit-check) lookup to
                // classify this call as a hit/miss for the session ratio. The extra lookup is cheap
                // (same Mutex-guarded map) and an approximate/racy count here is an accepted
                // telemetry trade-off -- see the class KDoc.
                val wasCached = cache.paginatedSearches.get(cacheKey) != null
                if (wasCached) paginatedCacheHits++ else paginatedCacheMisses++
                crashReporter?.setCustomKey(
                    "scryfall_paginated_cache_ratio_session",
                    "$paginatedCacheHits/$paginatedCacheMisses",
                )
                cache.paginatedSearches.getOrFetch(cacheKey, loader)
            }
        }

    suspend fun getCardById(scryfallId: String): Result<Card> =
        safeCall {
            cache.cards.getOrFetch(scryfallId) {
                requestQueue.execute { api.getCardById(scryfallId) }.toDomain()
            }
        }

    suspend fun getCardBySetAndNumber(set: String, number: String): Result<Card> =
        safeCall {
            val cacheKey = "setnum:${set.lowercase()}:$number"
            cache.cardNames.getOrFetch(cacheKey) {
                val card = requestQueue.execute { api.getCardBySetAndNumber(set, number) }.toDomain()
                cache.cards.put(card.scryfallId, card)
                card
            }
        }

    /**
     * Batch-fetches cards by Scryfall ID. Used for price refresh -- intentionally
     * bypasses the in-memory cache so that fresh price data is always returned.
     */
    suspend fun getCardCollection(
        scryfallIds: List<String>,
    ): CardCollectionResponseDto {
        val identifiers = scryfallIds.map { CardIdentifierDto(id = it) }
        return requestQueue.execute {
            api.getCardCollection(CardCollectionRequestDto(identifiers))
        }
    }

    /**
     * @param order optional Scryfall `order` param. Null preserves the historic default (name-ASC).
     *   MUST be part of the cache key (see below) -- otherwise a cached alphabetical result for a
     *   query would silently be served back for a later `order = "edhrec"` request on the same query.
     * @param page the Scryfall results page (1-based, plan Workstream 4.1). MUST also be part of the
     *   cache key -- otherwise a cached page-1 result would silently be served back for a later
     *   page-2+ request on the same query/order.
     */
    suspend fun searchWithRawQuery(query: String, order: String? = null, page: Int = 1): List<Card> =
        safeCall {
            val cacheKey = "raw:${query.lowercase().trim()}:${order ?: "name"}:page$page"
            cache.searches.getOrFetch(cacheKey) {
                val cards = requestQueue.execute { api.searchCards(query, order = order ?: "name", page = page) }
                    .data.toDomain()
                cards.forEach { card -> cache.cards.put(card.scryfallId, card) }
                cards
            }
        }.getOrDefault(emptyList())

    suspend fun getCardsBatch(scryfallIds: List<String>): Result<List<Card>> =
        safeCall {
            // Guard: Scryfall enforces a hard cap of 75 identifiers per /cards/collection
            // request. We also apply an overall limit so a caller with a very large
            // collection cannot trigger hundreds of consecutive API requests in a single
            // call, which would exhaust the rate-limit budget and stall other operations.
            val MAX_TOTAL = 1_000
            val sanitized = scryfallIds.take(MAX_TOTAL)

            // Check which cards are already in the in-memory cache
            val cached = mutableListOf<Card>()
            val missing = mutableListOf<String>()
            for (id in sanitized) {
                val card = cache.cards.get(id)
                if (card != null) cached.add(card)
                else missing.add(id)
            }

            if (missing.isEmpty()) return@safeCall cached

            // Only fetch the cards that aren't cached
            val allCards = mutableListOf<CardDto>()
            missing.chunked(75).forEach { chunk ->
                val identifiers = chunk.map { CardIdentifierDto(id = it) }
                val response = requestQueue.execute {
                    api.getCardCollection(CardCollectionRequestDto(identifiers))
                }
                allCards.addAll(response.data)
            }
            val fetched = allCards.toDomain()
            // Cache each newly fetched card
            fetched.forEach { card -> cache.cards.put(card.scryfallId, card) }

            cached + fetched
        }

    /**
     * Invalidates the in-memory [ScryfallCache.cards] entry for each of [scryfallIds] (Backend &
     * Performance Optimization plan, WS1+WS3 Part B item 7g). Call after writing fresh data for
     * these ids through a path that bypasses [cache] (e.g. [getCardCollection], used by price
     * refresh) so a subsequent [getCardById]/[getCardsBatch] re-reads the fresh Room row instead of
     * serving a stale-but-not-yet-expired cached [Card].
     */
    suspend fun invalidateCachedCards(scryfallIds: Collection<String>) {
        cache.invalidateCards(scryfallIds)
    }

    suspend fun getAllSets(): List<MagicSet> =
        cache.sets.getOrFetch("all") {
            requestQueue.execute { api.getSets() }
                .data
                .filter { dto ->
                    !dto.digital &&
                    SetType.from(dto.setType) in PLAYABLE_SET_TYPES
                }
                .map { dto ->
                    MagicSet(
                        code       = dto.code,
                        name       = dto.name,
                        setType    = SetType.from(dto.setType),
                        releasedAt = dto.releasedAt,
                        cardCount  = dto.cardCount,
                        iconSvgUri = dto.iconSvgUri,
                    )
                }
                .sortedByDescending { it.releasedAt ?: "" }
        }

    // Reutilizes /cards/search with unique=art to get one entry per unique artwork
    suspend fun searchPlaneswalkerArts(
        query: String,
        page:  Int = 1,
    ): SearchResultDto = requestQueue.execute {
        api.searchCards(
            query  = query,
            order  = "name",
            unique = "art",
            page   = page,
        )
    }

    suspend fun getCardArtVariants(name: String): Result<List<Card>> =
        safeCall {
            val safeName = name.replace("\"", "").replace("\\", "").trim()
            if (safeName.isBlank()) return@safeCall emptyList()
            cache.artVariants.getOrFetch(safeName.lowercase()) {
                // unique=art collapses every reprint sharing an illustration down to ONE result.
                // We want every paper-printed version, so use unique=prints (Scryfall's
                // "all printings" mode) restricted to game:paper (excludes Arena/MTGO-only
                // digital variants that were never actually printed).
                val cards = requestQueue.execute {
                    api.searchCards(query = "!\"$safeName\" (game:paper)", unique = "prints", order = "released")
                }.data.toDomain()
                cards.forEach { card -> cache.cards.put(card.scryfallId, card) }
                cards
            }
        }

    /**
     * Card Versions & Languages, Phase 1A. Fetches every language printed for the exact printing
     * identified by [setCode] + [collectorNumber], via Scryfall's `set:<code> cn:"<number>"
     * lang:any unique:prints` search (feeds CardDetail's language selector, Phase 1B).
     *
     * Sanitisation mirrors [getCardArtVariants]/[searchCards]: [setCode] is lower-cased and
     * restricted to `[a-z0-9]` (mirrors the set-code allowlist already applied to Draft Sim's
     * `extraPoolSets` query-building — set codes are always short lowercase alphanumerics), and
     * [collectorNumber] has quotes/backslashes stripped before being embedded in the `cn:"..."`
     * clause. Not memoised in [ScryfallCache] (results are set/collector-number specific and used
     * once per language-selector open, unlike the by-name caches above) — the call still goes
     * through [requestQueue] for rate-limiting.
     */
    suspend fun getLanguagePrints(setCode: String, collectorNumber: String): Result<List<Card>> =
        safeCall {
            val safeSet = setCode.lowercase().filter { it.isLetterOrDigit() }
            val safeNumber = collectorNumber.replace("\"", "").replace("\\", "").trim()
            if (safeSet.isBlank() || safeNumber.isBlank()) return@safeCall emptyList()
            val cards = requestQueue.execute {
                api.searchCards(
                    query = "set:$safeSet cn:\"$safeNumber\" lang:any",
                    unique = "prints",
                    order = "released",
                )
            }.data.toDomain()
            cards.forEach { card -> cache.cards.put(card.scryfallId, card) }
            cards
        }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        withContext(dispatcherProvider.io) { runCatching { block() } }
}
