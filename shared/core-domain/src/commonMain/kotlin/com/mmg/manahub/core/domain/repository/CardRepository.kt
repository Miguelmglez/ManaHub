package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SuggestedTag
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    suspend fun searchCardByName(query: String): DataResult<Card>

    /**
     * Searches Scryfall for cards matching [query].
     *
     * @param bypassCache when true, skips the in-memory search cache and always re-fetches.
     *   Required for `order:random` queries where a stable cache key would otherwise return the
     *   same page on every refresh. Defaults to false (cached) for all existing callers.
     */
    suspend fun searchCards(
        query: String,
        page: Int = 1,
        bypassCache: Boolean = false,
    ): DataResult<List<Card>>

    /**
     * Searches Scryfall for cards matching [query]. Returns paginated results with a [hasMore] flag.
     *
     * @param bypassCache when true, skips the in-memory search cache and always re-fetches.
     */
    suspend fun searchCardsPaginated(
        query: String,
        page: Int = 1,
        bypassCache: Boolean = false,
    ): DataResult<com.mmg.manahub.core.model.PaginatedCards>
    suspend fun getCardById(scryfallId: String): DataResult<Card>

    /** Fetches a card by set code and collector number (returns English version by default). */
    suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card>

    /** Fetches all prints (versions) of a card by its exact English name. */
    suspend fun getCardPrints(name: String): DataResult<List<Card>>

    /** Fetches all paper-printed versions (prints) of a card by its exact English name. */
    suspend fun getCardArtVariants(name: String): DataResult<List<Card>>

    /** Fetches a card by its exact English name (e.g. "Lightning Bolt"). */
    suspend fun getCardByExactName(name: String): Result<Card>

    /** Executes a raw Scryfall query string and returns matching cards. */
    suspend fun searchWithRawQuery(query: String): List<Card>

    /** Fetches a list of playable Magic sets sorted by release date descending. */
    suspend fun getPlayableSets(): DataResult<List<com.mmg.manahub.core.model.MagicSet>>

    /** Batch-resolves [scryfallIds] to [Card]s from the local cache. IDs not found locally are silently skipped (no network fetch). */
    suspend fun getCardsByIds(scryfallIds: List<String>): List<Card>
    fun observeCard(scryfallId: String): Flow<Card?>
    suspend fun refreshCollectionPrices()
    suspend fun updatePrices(
        scryfallId:   String,
        priceUsd:     Double?,
        priceUsdFoil: Double?,
        priceEur:     Double?,
        priceEurFoil: Double?,
        updatedAt:    Long,
    )
    suspend fun evictStaleCache()

    /** Replace the confirmed tag list for a card already in the local cache. */
    suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>)

    /** Replace the user-added tag list for a card. */
    suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>)

    /** Replace the suggested-tag list (used when the user dismisses suggestions). */
    suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>)

    /** Promote a suggested tag to a confirmed tag (and remove it from suggestions). */
    suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag)

    /** Drop a suggested tag without confirming it. */
    suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag)

    /**
     * Pre-warms the local Room cache for the given [scryfallIds] using a batch Scryfall fetch.
     * IDs already in Room are skipped. Called before bulk card lookups to avoid N sequential
     * network calls. Best-effort: failures are silently swallowed so callers aren't blocked.
     */
    suspend fun warmCacheForIds(scryfallIds: List<String>)
}
