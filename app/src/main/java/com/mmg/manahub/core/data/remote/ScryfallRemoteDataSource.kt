package com.mmg.manahub.core.data.remote

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
import com.mmg.manahub.core.network.ScryfallCache
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScryfallRemoteDataSource @Inject constructor(
    private val api: ScryfallClient,
    private val requestQueue: ScryfallRequestQueue,
    private val cache: ScryfallCache,
) {

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
     * is true the memoised path is skipped and the loader always runs — required for queries that
     * embed `order:random`, where a stable cache key would otherwise return the same page on every
     * "refresh" (see Home Discover/Random-card widgets). The individual card cache is still
     * populated either way. The request remains rate-limited inside [ScryfallRequestQueue].
     *
     * NOTE: bypassing the in-memory [ScryfallCache.searches] map alone is NOT enough — the OkHttp
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
     * Batch-fetches cards by Scryfall ID. Used for price refresh — intentionally
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

    suspend fun searchWithRawQuery(query: String): List<Card> =
        safeCall {
            val cacheKey = "raw:${query.lowercase().trim()}"
            cache.searches.getOrFetch(cacheKey) {
                val cards = requestQueue.execute { api.searchCards(query, page = 1) }
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
                val cards = requestQueue.execute {
                    api.searchCards(query = "!\"$safeName\"", unique = "art", order = "released")
                }.data.toDomain()
                cards.forEach { card -> cache.cards.put(card.scryfallId, card) }
                cards
            }
        }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }
}
