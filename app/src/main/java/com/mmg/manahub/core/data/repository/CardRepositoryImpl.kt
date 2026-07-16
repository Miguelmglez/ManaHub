package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.mapper.toEntityCard
import com.mmg.manahub.core.data.local.mapper.toSuggestedTagList
import com.mmg.manahub.core.data.local.mapper.toSuggestedTagsJson
import com.mmg.manahub.core.data.local.mapper.toTagList
import com.mmg.manahub.core.data.local.mapper.toTagsJson
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.di.DefaultDispatcher
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.usecase.card.ComputeCardTagsUseCase
import com.mmg.manahub.core.util.recordSafeNonFatal
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    private val cardDao:               CardDao,
    private val userCardCollectionDao: UserCardCollectionDao,
    private val remote:                ScryfallRemoteDataSource,
    private val computeCardTags:       ComputeCardTagsUseCase,
    private val userPrefs:             UserPreferencesDataStore,
    @IoDispatcher    private val ioDispatcher:      CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : CardRepository {

    override suspend fun searchCardByName(query: String): DataResult<Card> =
        withContext(ioDispatcher) {
            val result = remote.searchCardByName(query)
            if (result.isSuccess) {
                val card = result.getOrThrow()
                cardDao.upsert(entityWithComputedTags(card))
                DataResult.Success(card)
            } else {
                val exception = result.exceptionOrNull()
                if (exception is ClientRequestException && exception.response.status == HttpStatusCode.NotFound) {
                    DataResult.Error("SCRYFALL_404")
                } else {
                    DataResult.Error(exception?.message ?: "Unknown error")
                }
            }
        }

    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> =
        withContext(ioDispatcher) {
            val result = remote.searchCards(query, page, bypassCache)
            if (result.isSuccess) {
                val cards = result.getOrThrow()
                // Single batch DB read for existing cache entries; tag computation on defaultDispatcher.
                val cachedMap = cardDao.getByIds(cards.map { it.scryfallId }).associateBy { it.scryfallId }
                val entities = entitiesWithComputedTagsBatch(cards, cachedMap)
                cardDao.upsertAll(entities)
                DataResult.Success(cards)
            } else {
                val exception = result.exceptionOrNull()
                if (exception is ClientRequestException && exception.response.status == HttpStatusCode.NotFound) {
                    DataResult.Error("SCRYFALL_404")
                } else {
                    DataResult.Error(exception?.message ?: "Unknown error")
                }
            }
        }

    override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<com.mmg.manahub.core.model.PaginatedCards> =
        withContext(ioDispatcher) {
            val result = remote.searchCardsPaginated(query, page, bypassCache)
            if (result.isSuccess) {
                val paginated = result.getOrThrow()
                // Single batch DB read for existing cache entries; tag computation on defaultDispatcher.
                val cachedMap = cardDao.getByIds(paginated.cards.map { it.scryfallId }).associateBy { it.scryfallId }
                val entities = entitiesWithComputedTagsBatch(paginated.cards, cachedMap)
                cardDao.upsertAll(entities)
                DataResult.Success(paginated)
            } else {
                val exception = result.exceptionOrNull()
                if (exception is ClientRequestException && exception.response.status == HttpStatusCode.NotFound) {
                    DataResult.Error("SCRYFALL_404")
                } else {
                    DataResult.Error(exception?.message ?: "Unknown error")
                }
            }
        }

    override suspend fun getCardPrints(name: String): DataResult<List<Card>> =
        withContext(ioDispatcher) {
            val safeName = name.replace("\"", "").replace("\\", "").trim()
            val query = "!\"$safeName\" unique:prints"
            val result = remote.searchCards(query, 1)
            if (result.isSuccess) {
                DataResult.Success(result.getOrThrow())
            } else {
                val exception = result.exceptionOrNull()
                if (exception is ClientRequestException && exception.response.status == HttpStatusCode.NotFound) {
                    DataResult.Error("SCRYFALL_404")
                } else {
                    DataResult.Error(exception?.message ?: "Unknown error")
                }
            }
        }

    override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> =
        withContext(ioDispatcher) {
            val result = remote.getCardArtVariants(name)
            if (result.isSuccess) DataResult.Success(result.getOrThrow())
            else DataResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }

    override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> =
        withContext(ioDispatcher) {
            val result = remote.getLanguagePrints(setCode, collectorNumber)
            if (result.isSuccess) {
                val cards = result.getOrThrow()
                // Upsert every language/printing into Room so CardDetail's language selector
                // (Phase 1B) and the oracle-wide collection/wishlist/open-for-trade observers can
                // resolve them without a further network round-trip.
                val cachedMap = cardDao.getByIds(cards.map { it.scryfallId }).associateBy { it.scryfallId }
                val entities = entitiesWithComputedTagsBatch(cards, cachedMap)
                cardDao.upsertAll(entities)
                DataResult.Success(cards)
            } else {
                val exception = result.exceptionOrNull()
                if (exception is ClientRequestException && exception.response.status == HttpStatusCode.NotFound) {
                    DataResult.Error("SCRYFALL_404")
                } else {
                    // A genuine fetch failure (network/parse) blocks the language selector
                    // entirely — a real product friction point, worth a non-fatal. Skip the
                    // expected SCRYFALL_404 case above (a printing that genuinely has no other
                    // language prints is not an error).
                    exception?.let { recordSafeNonFatal("language_prints_fetch_failed", it) }
                    DataResult.Error(exception?.message ?: "Unknown error")
                }
            }
        }

    /**
     * Builds a [CardEntity] with auto-computed tags for a single card.
     *
     * Reads the cached entity once (for existing user tags), then offloads
     * tag computation to [defaultDispatcher] (CPU-bound regex work in [StrategyAnalyzer]).
     */
    private suspend fun entityWithComputedTags(card: Card) = run {
        val existing = cardDao.getById(card.scryfallId)
        val (tagsJson, suggestedJson) = computeTagsForCacheOnDefault(card, existing?.tags)
        card.toEntityCard().copy(
            tags = tagsJson,
            userTags = existing?.userTags ?: "[]",
            suggestedTags = suggestedJson,
        )
    }

    /**
     * Builds a batch of [CardEntity] objects with computed tags, using a single [cardDao.getByIds]
     * call for all cache lookups instead of N individual [cardDao.getById] calls.
     *
     * Tag computation is offloaded to [defaultDispatcher].
     */
    private suspend fun entitiesWithComputedTagsBatch(
        cards: List<Card>,
        cachedMap: Map<String, com.mmg.manahub.core.data.local.entity.CardEntity>,
    ): List<com.mmg.manahub.core.data.local.entity.CardEntity> =
        withContext(defaultDispatcher) {
            val auto    = userPrefs.tagAutoThresholdFlow.first()
            val suggest = userPrefs.tagSuggestThresholdFlow.first()
            cards.map { card ->
                val existing = cachedMap[card.scryfallId]
                val result = computeCardTags(
                    card             = card,
                    existingTagsJson = existing?.tags,
                    autoThreshold    = auto,
                    suggestThreshold = suggest,
                )
                card.toEntityCard().copy(
                    tags          = result.confirmedTags.toTagsJson(),
                    userTags      = existing?.userTags ?: "[]",
                    suggestedTags = result.suggestedTags.toSuggestedTagsJson(),
                )
            }
        }

    override suspend fun getCardByExactName(name: String): Result<Card> =
        withContext(ioDispatcher) {
            remote.getCardByExactName(name).also { result ->
                // Persist to Room so callers that re-key an observeCard()-style flow off the
                // returned scryfallId (e.g. CardDetailViewModel's foreign->English fallback)
                // find a row waiting for them instead of blocking forever on an empty flow.
                result.getOrNull()?.let { card -> cardDao.upsert(entityWithComputedTags(card)) }
            }
        }

    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> =
        withContext(ioDispatcher) {
            val result = remote.getCardBySetAndNumber(set, number)
            if (result.isSuccess) {
                val card = result.getOrThrow()
                cardDao.upsert(entityWithComputedTags(card))
                DataResult.Success(card)
            } else {
                val exception = result.exceptionOrNull()
                if (exception is ClientRequestException && exception.response.status == HttpStatusCode.NotFound) {
                    DataResult.Error("SCRYFALL_404")
                } else {
                    DataResult.Error(exception?.message ?: "Unknown error")
                }
            }
        }

    override suspend fun searchWithRawQuery(query: String): List<Card> =
        withContext(ioDispatcher) { remote.searchWithRawQuery(query) }

    override suspend fun getPlayableSets(): DataResult<List<com.mmg.manahub.core.model.MagicSet>> =
        withContext(ioDispatcher) {
            try {
                val sets = remote.getAllSets()
                DataResult.Success(sets)
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Unknown error")
            }
        }

    override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> = withContext(ioDispatcher) {
        if (scryfallIds.isEmpty()) return@withContext emptyList()
        cardDao.getByIds(scryfallIds).map { it.toDomainCard() }
    }

    override suspend fun getCardById(scryfallId: String): DataResult<Card> = withContext(ioDispatcher) {
        val cached = cardDao.getById(scryfallId)
        if (cached != null && CachePolicy.isFresh(cached.cachedAt) && cached.relatedUris != "{}")
            return@withContext DataResult.Success(cached.toDomainCard())

        fetchAndCacheFromRemote(scryfallId, cached)
    }

    // Edge-case audit A3 (2026-07-15). Unlike getCardById, always hits the network — used to
    // force-refresh a cached row that predates the oracle_id column, which would otherwise pass
    // getCardById's freshness check (isFresh/relatedUris) and never get a chance to re-fetch.
    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = withContext(ioDispatcher) {
        fetchAndCacheFromRemote(scryfallId, cardDao.getById(scryfallId))
    }

    override suspend fun backfillMissingOracleIds(limit: Int) = withContext(ioDispatcher) {
        val staleIds = runCatching { cardDao.getScryfallIdsWithBlankOracleId(limit) }.getOrElse { emptyList() }
        // Sequential, not parallel: ScryfallRequestQueue already rate-limits every network call
        // globally (≤10 req/s via a single Mutex) — firing these concurrently would just queue up
        // behind the same lock with no benefit, and sequential execution avoids this best-effort
        // background backfill starving other in-flight Scryfall calls at app start. Failure-silent
        // per card so one bad fetch never aborts the rest of the batch.
        staleIds.forEach { id -> runCatching { refreshCardById(id) } }
    }

    /**
     * Shared network-fetch-and-cache path for [getCardById] (which short-circuits on a fresh
     * cache hit before calling this) and [refreshCardById] (which always calls this directly).
     * Extracted so the two force-refresh callers can't drift from getCardById's original
     * success/stale/error handling.
     */
    private suspend fun fetchAndCacheFromRemote(
        scryfallId: String,
        cached: com.mmg.manahub.core.data.local.entity.CardEntity?,
    ): DataResult<Card> {
        val result = remote.getCardById(scryfallId)
        return when {
            result.isSuccess -> {
                val card = result.getOrThrow()

                // Preserve any user edits to confirmed tags; otherwise auto-tag.
                // Offloaded to defaultDispatcher inside computeTagsForCacheOnDefault.
                val (tagsJson, suggestedJson) = computeTagsForCacheOnDefault(card, cached?.tags)

                cardDao.upsert(
                    card.toEntityCard().copy(
                        tags = tagsJson,
                        userTags = cached?.userTags ?: "[]",
                        suggestedTags = suggestedJson,
                    )
                )
                cardDao.clearStale(scryfallId)
                DataResult.Success(cardDao.getById(scryfallId)!!.toDomainCard())
            }
            cached != null -> {
                if (CachePolicy.isStale(cached.cachedAt))
                    cardDao.markStale(scryfallId, buildStaleReason(result.exceptionOrNull()))
                DataResult.Success(
                    data    = cached.toDomainCard(),
                    isStale = CachePolicy.isStale(cached.cachedAt),
                )
            }
            else -> DataResult.Error(
                result.exceptionOrNull()?.message ?: "No local data and network unavailable"
            )
        }
    }

    override fun observeCard(scryfallId: String): Flow<Card?> =
        cardDao.observeById(scryfallId).map { it?.toDomainCard() }

    override suspend fun refreshCollectionPrices() = withContext(ioDispatcher) {
        val allIds = userCardCollectionDao.getAllScryfallIds()
        if (allIds.isEmpty()) return@withContext

        // Batch-load all cached entries in a single query instead of N getById() calls.
        val cachedMap = cardDao.getByIds(allIds).associateBy { it.scryfallId }
        val staleIds  = allIds.filter { id ->
            val c = cachedMap[id]
            c == null || !CachePolicy.isFresh(c.cachedAt)
        }
        if (staleIds.isEmpty()) return@withContext

        val result = remote.getCardsBatch(staleIds)
        if (result.isSuccess) {
            val cards = result.getOrThrow()

            // Build all entities first (reads only), then write in a single upsertAll
            // transaction instead of N individual upsert() calls.
            // Tag computation is offloaded to defaultDispatcher inside the batch helper.
            val entities = entitiesWithComputedTagsBatch(cards, cachedMap)
            cardDao.upsertAll(entities)

            // Clear stale flag for every card we successfully refreshed.
            val refreshed = cards.map { it.scryfallId }.toSet()
            refreshed.forEach { cardDao.clearStale(it) }

            // Mark cards that Scryfall did not return in the batch.
            (staleIds.toSet() - refreshed).forEach { id ->
                val c = cachedMap[id] ?: return@forEach
                if (CachePolicy.isStale(c.cachedAt))
                    cardDao.markStale(id, "Not found in Scryfall batch response")
            }
        } else {
            val reason = buildStaleReason(result.exceptionOrNull())
            staleIds.forEach { id ->
                val c = cachedMap[id] ?: return@forEach
                if (CachePolicy.isStale(c.cachedAt)) cardDao.markStale(id, reason)
            }
        }
    }

    override suspend fun updatePrices(
        scryfallId:   String,
        priceUsd:     Double?,
        priceUsdFoil: Double?,
        priceEur:     Double?,
        priceEurFoil: Double?,
        updatedAt:    Long,
    ) {
        cardDao.updatePrices(
            scryfallId = scryfallId,
            priceUsd = priceUsd,
            priceUsdFoil = priceUsdFoil,
            priceEur = priceEur,
            priceEurFoil = priceEurFoil,
            updatedAt = updatedAt,
        )
    }

    override suspend fun warmCacheForIds(scryfallIds: List<String>) = withContext(ioDispatcher) {
        if (scryfallIds.isEmpty()) return@withContext

        // Single DB read for all IDs instead of N individual getById() calls.
        val alreadyCached = cardDao.getByIds(scryfallIds).map { it.scryfallId }.toSet()
        val missing = scryfallIds.filterNot { it in alreadyCached }
        if (missing.isEmpty()) return@withContext

        missing.chunked(75).forEach { chunk ->
            remote.getCardsBatch(chunk)
                .onSuccess { cards ->
                    // Build a lookup map from the chunk's cached entities (may be empty for
                    // brand-new cards) to preserve any existing userTags.
                    val cachedMap = cardDao.getByIds(chunk).associateBy { it.scryfallId }
                    runCatching {
                        val entities = entitiesWithComputedTagsBatch(cards, cachedMap)
                        cardDao.upsertAll(entities)
                    }
                }
            // Failures are intentionally swallowed — callers fall back to individual getCardById
        }
    }

    override suspend fun evictStaleCache() =
        cardDao.evictStaleCache(System.currentTimeMillis() - CachePolicy.EVICT_MS)

    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) {
        cardDao.updateTags(scryfallId, tags.distinct().toTagsJson())
    }

    override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) {
        cardDao.updateUserTags(scryfallId, userTags.distinct().toTagsJson())
    }

    override suspend fun updateSuggestedTags(
        scryfallId: String, suggestions: List<SuggestedTag>,
    ) {
        cardDao.updateSuggestedTags(scryfallId, suggestions.toSuggestedTagsJson())
    }

    override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) {
        val cached = cardDao.getById(scryfallId) ?: return
        val confirmed   = (cached.tags.toTagList() + tag).distinct()
        val suggestions = cached.suggestedTags.toSuggestedTagList()
            .filterNot { it.tag.key == tag.key }
        cardDao.updateTagsAndSuggestions(
            scryfallId    = scryfallId,
            tagsJson      = confirmed.toTagsJson(),
            suggestedJson = suggestions.toSuggestedTagsJson(),
        )
    }

    override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) {
        val cached = cardDao.getById(scryfallId) ?: return
        val suggestions = cached.suggestedTags.toSuggestedTagList()
            .filterNot { it.tag.key == tag.key }
        cardDao.updateSuggestedTags(scryfallId, suggestions.toSuggestedTagsJson())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Computes the JSON pair (tagsJson, suggestedTagsJson) for a single card.
     *
     * Delegates to [ComputeCardTagsUseCase] and offloads the CPU-bound regex work
     * in [StrategyAnalyzer] to [defaultDispatcher].
     */
    private suspend fun computeTagsForCacheOnDefault(
        card: Card,
        existingTagsJson: String?,
    ): Pair<String, String> = withContext(defaultDispatcher) {
        val auto    = userPrefs.tagAutoThresholdFlow.first()
        val suggest = userPrefs.tagSuggestThresholdFlow.first()
        val result  = computeCardTags(
            card             = card,
            existingTagsJson = existingTagsJson,
            autoThreshold    = auto,
            suggestThreshold = suggest,
        )
        result.confirmedTags.toTagsJson() to result.suggestedTags.toSuggestedTagsJson()
    }

    private fun buildStaleReason(e: Throwable?): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return "${e?.message ?: "Network error"} — $date"
    }
}
