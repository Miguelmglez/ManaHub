package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
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
import com.mmg.manahub.core.di.ApplicationScope
import com.mmg.manahub.core.domain.repository.CardPriceUpdate
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.usecase.card.ResolveCardStrategyTagsUseCase
import com.mmg.manahub.core.util.recordSafeNonFatal
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    private val cardDao:               CardDao,
    private val remote:                ScryfallRemoteDataSource,
    private val resolveCardStrategyTags: ResolveCardStrategyTagsUseCase,
    private val userPrefs:             UserPreferencesDataStore,
    @IoDispatcher    private val ioDispatcher:      CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : CardRepository {

    override suspend fun searchCardByName(query: String): DataResult<Card> =
        withContext(ioDispatcher) {
            val result = remote.searchCardByName(query)
            if (result.isSuccess) {
                val card = result.getOrThrow()
                val (entity, existingTagsJson) = entityPreservingTags(card)
                cardDao.upsert(entity)
                scheduleTagResolution(card, existingTagsJson)
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
                // Single batch DB read for existing cache entries. Strategy tags are intentionally
                // NOT resolved here: search results can include many cards the user never opens, and
                // a strategy-tag lookup is a per-card Supabase network call. Tags are resolved lazily,
                // once, when a card is actually opened in CardDetailScreen via
                // RefreshCardStrategyTagsUseCase, which already has its own Room-backed cache
                // (CardStrategyTagsRepositoryImpl / CardStrategyTagsCache, 14-day TTL).
                val cachedMap = cardDao.getByIds(cards.map { it.scryfallId }).associateBy { it.scryfallId }
                val entities = entitiesPreservingTagsBatch(cards, cachedMap)
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
                // Single batch DB read for existing cache entries. Strategy tags are intentionally
                // NOT resolved here — see searchCards() above for the full rationale.
                val cachedMap = cardDao.getByIds(paginated.cards.map { it.scryfallId }).associateBy { it.scryfallId }
                val entities = entitiesPreservingTagsBatch(paginated.cards, cachedMap)
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
                // resolve them without a further network round-trip. Tag resolution deferred (see
                // scheduleTagResolutionBatch).
                val cachedMap = cardDao.getByIds(cards.map { it.scryfallId }).associateBy { it.scryfallId }
                val entities = entitiesPreservingTagsBatch(cards, cachedMap)
                cardDao.upsertAll(entities)
                scheduleTagResolutionBatch(cards, cachedMap)
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
     * Builds a [CardEntity] PRESERVING whatever tags already exist in cache (empty for a brand new
     * card). Deck Engine Unification plan, D8, §5 Phase 5c: tag computation/resolution itself is
     * deferred to a background job (see [scheduleTagResolution]) so this stays a pure, fast Room
     * read + copy — no CPU-bound regex work and no network call on the caller's critical path
     * (a card add to the collection must not wait on tag analysis).
     *
     * Returns the entity alongside the existing `tags` JSON (or null for a brand-new card) so the
     * caller can hand it straight to [scheduleTagResolution] without a second DB read.
     */
    private suspend fun entityPreservingTags(card: Card): Pair<com.mmg.manahub.core.data.local.entity.CardEntity, String?> {
        val existing = cardDao.getById(card.scryfallId)
        val entity = card.toEntityCard().copy(
            tags = existing?.tags ?: "[]",
            userTags = existing?.userTags ?: "[]",
            suggestedTags = existing?.suggestedTags ?: "[]",
        )
        return entity to existing?.tags
    }

    /**
     * Builds a batch of [CardEntity] objects PRESERVING each card's existing tags, using a single
     * [cardDao.getByIds]-sourced [cachedMap] instead of N individual [cardDao.getById] calls. Tag
     * resolution for the whole batch is deferred to [scheduleTagResolutionBatch] — see
     * [entityPreservingTags]'s KDoc for why.
     */
    private fun entitiesPreservingTagsBatch(
        cards: List<Card>,
        cachedMap: Map<String, com.mmg.manahub.core.data.local.entity.CardEntity>,
    ): List<com.mmg.manahub.core.data.local.entity.CardEntity> =
        cards.map { card ->
            val existing = cachedMap[card.scryfallId]
            card.toEntityCard().copy(
                tags          = existing?.tags ?: "[]",
                userTags      = existing?.userTags ?: "[]",
                suggestedTags = existing?.suggestedTags ?: "[]",
            )
        }

    /**
     * Fire-and-forget background tag resolution for a single freshly-cached [card] — the
     * non-blocking half of Deck Engine Unification plan D8/§5 Phase 5c. Runs on [appScope] (NOT
     * the caller's coroutine) so it survives the caller returning. [ResolveCardStrategyTagsUseCase]
     * is STRICT FALLBACK as of the plan §8a addendum: the offline pipeline's precomputed Supabase
     * source is used EXCLUSIVELY when present (zero on-device CPU work — this is also the fix for
     * "adding many cards freezes the app"); the on-device analyzer only runs on a genuine miss, and
     * that miss result is pushed back to Supabase so the table converges over time. Any failure is
     * swallowed — this is best-effort enrichment, never a source of add/search failures.
     */
    private fun scheduleTagResolution(card: Card, existingTagsJson: String?) {
        appScope.launch(defaultDispatcher) {
            runCatching {
                val auto    = userPrefs.tagAutoThresholdFlow.first()
                val suggest = userPrefs.tagSuggestThresholdFlow.first()
                val result  = resolveCardStrategyTags(card, existingTagsJson, auto, suggest)
                cardDao.updateTagsAndSuggestions(
                    scryfallId    = card.scryfallId,
                    tagsJson      = result.confirmedTags.toTagsJson(),
                    suggestedJson = result.suggestedTags.toSuggestedTagsJson(),
                )
            }.onFailure { e -> recordSafeNonFatal("card_strategy_tags_background_resolve_failed", e) }
        }
    }

    /**
     * Batch sibling of [scheduleTagResolution] — processes [cards] SEQUENTIALLY within one
     * background job (not N concurrent launches) to avoid bursting the Supabase table with
     * simultaneous per-card lookups when a whole search page / collection refresh lands at once,
     * mirroring [backfillMissingOracleIds]'s identical "sequential, not parallel" rationale. A
     * single card's failure never aborts the rest of the batch.
     */
    private fun scheduleTagResolutionBatch(
        cards: List<Card>,
        cachedMap: Map<String, com.mmg.manahub.core.data.local.entity.CardEntity>,
    ) {
        if (cards.isEmpty()) return
        appScope.launch(defaultDispatcher) {
            val auto    = userPrefs.tagAutoThresholdFlow.first()
            val suggest = userPrefs.tagSuggestThresholdFlow.first()
            cards.forEach { card ->
                runCatching {
                    val existingTagsJson = cachedMap[card.scryfallId]?.tags
                    val result = resolveCardStrategyTags(card, existingTagsJson, auto, suggest)
                    cardDao.updateTagsAndSuggestions(
                        scryfallId    = card.scryfallId,
                        tagsJson      = result.confirmedTags.toTagsJson(),
                        suggestedJson = result.suggestedTags.toSuggestedTagsJson(),
                    )
                }.onFailure { e -> recordSafeNonFatal("card_strategy_tags_background_resolve_failed", e) }
            }
        }
    }

    override suspend fun getCardByExactName(name: String): Result<Card> =
        withContext(ioDispatcher) {
            remote.getCardByExactName(name).also { result ->
                // Persist to Room so callers that re-key an observeCard()-style flow off the
                // returned scryfallId (e.g. CardDetailViewModel's foreign->English fallback)
                // find a row waiting for them instead of blocking forever on an empty flow.
                result.getOrNull()?.let { card ->
                    val (entity, existingTagsJson) = entityPreservingTags(card)
                    cardDao.upsert(entity)
                    scheduleTagResolution(card, existingTagsJson)
                }
            }
        }

    override suspend fun searchCardPrintedName(name: String, lang: String): DataResult<Card> =
        withContext(ioDispatcher) {
            val result = remote.searchCardPrintedName(name, lang)
            if (result.isSuccess) {
                val card = result.getOrThrow()
                val (entity, existingTagsJson) = entityPreservingTags(card)
                cardDao.upsert(entity)
                scheduleTagResolution(card, existingTagsJson)
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

    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> =
        withContext(ioDispatcher) {
            val result = remote.getCardBySetAndNumber(set, number)
            if (result.isSuccess) {
                val card = result.getOrThrow()
                val (entity, existingTagsJson) = entityPreservingTags(card)
                cardDao.upsert(entity)
                scheduleTagResolution(card, existingTagsJson)
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

    override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> =
        withContext(ioDispatcher) {
            if (pairs.isEmpty()) return@withContext emptyMap()
            val setCodes = pairs.map { it.first }.distinct()
            val collectorNumbers = pairs.map { it.second }.distinct()
            cardDao.getEnglishSiblings(setCodes, collectorNumbers)
                .asSequence()
                .map { it.toDomainCard() }
                // The DAO query is a cross product of the two IN lists — keep only the exact
                // (setCode, collectorNumber) pairs actually requested.
                .filter { (it.setCode to it.collectorNumber) in pairs }
                .associateBy { it.setCode to it.collectorNumber }
        }

    override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> =
        withContext(ioDispatcher) { remote.searchWithRawQuery(query, order, page) }

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
     * One-time strategy-tags backfill (2026-07-22) -- see the [CardRepository.backfillMissingStrategyTags]
     * KDoc for the full rationale. Sequential (not parallel), same rationale as
     * [backfillMissingOracleIds]/[scheduleTagResolutionBatch]: avoid bursting Supabase with
     * simultaneous per-card lookups. Reuses the EXACT [resolveCardStrategyTags] ->
     * [CardDao.updateTagsAndSuggestions] path used by every other resolution site in this file, so a
     * resolved candidate also populates `card_strategy_tags_cache` -- the same write that makes
     * [CardDao.getScryfallIdsMissingStrategyTags] self-terminating. Failure-silent per card so one
     * bad resolution never aborts the rest of the batch.
     */
    override suspend fun backfillMissingStrategyTags(limit: Int) = withContext(ioDispatcher) {
        val candidateIds = runCatching { cardDao.getScryfallIdsMissingStrategyTags(limit) }.getOrElse { emptyList() }
        if (candidateIds.isEmpty()) return@withContext

        val auto    = userPrefs.tagAutoThresholdFlow.first()
        val suggest = userPrefs.tagSuggestThresholdFlow.first()
        candidateIds.forEach { scryfallId ->
            runCatching {
                val entity = cardDao.getById(scryfallId) ?: return@runCatching
                val result = resolveCardStrategyTags(entity.toDomainCard(), entity.tags, auto, suggest)
                cardDao.updateTagsAndSuggestions(
                    scryfallId    = scryfallId,
                    tagsJson      = result.confirmedTags.toTagsJson(),
                    suggestedJson = result.suggestedTags.toSuggestedTagsJson(),
                )
            }.onFailure { e -> recordSafeNonFatal("card_strategy_tags_backfill_failed", e) }
        }
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

                // Preserve whatever tags already exist (a brand-new card gets none yet); tag
                // resolution/computation is deferred to a background job (D8/§5 Phase 5c) so this
                // fetch-and-cache path never blocks on it.
                cardDao.upsert(
                    card.toEntityCard().copy(
                        tags = cached?.tags ?: "[]",
                        userTags = cached?.userTags ?: "[]",
                        suggestedTags = cached?.suggestedTags ?: "[]",
                    )
                )
                cardDao.clearStale(scryfallId)
                scheduleTagResolution(card, cached?.tags)
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

    // Backend & Performance Optimization plan, WS1+WS3 Part B item 7a (2026-07-28): this used to
    // hold `refreshCollectionPrices()`, an unguarded, whole-collection duplicate of
    // `RefreshCollectionPricesUseCase` (`shared/core-data`) called on every Collection screen open.
    // DELETED — `PriceRefreshWorker` -> `RefreshCollectionPricesUseCase` is now the SOLE price-
    // refresh path (stale-only, sliced, watermark-guarded). See that use case's KDoc for the
    // equivalent (and improved) logic. Zero remaining callers confirmed via repo-wide grep before
    // deleting both this override and the `CardRepository.refreshCollectionPrices()` interface
    // member.

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

    override suspend fun updatePricesBatch(updates: List<CardPriceUpdate>) {
        if (updates.isEmpty()) return
        withContext(ioDispatcher) {
            cardDao.updatePricesBatch(updates)
        }
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
                    // brand-new cards) to preserve any existing userTags. Tag resolution deferred
                    // (see scheduleTagResolutionBatch).
                    val cachedMap = cardDao.getByIds(chunk).associateBy { it.scryfallId }
                    runCatching {
                        val entities = entitiesPreservingTagsBatch(cards, cachedMap)
                        cardDao.upsertAll(entities)
                    }
                    scheduleTagResolutionBatch(cards, cachedMap)
                }
            // Failures are intentionally swallowed — callers fall back to individual getCardById
        }
    }

    override suspend fun evictStaleCache() =
        cardDao.evictStaleCache(System.currentTimeMillis() - CachePolicy.EVICT_MS)

    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) {
        cardDao.updateTags(scryfallId, tags.distinct().toTagsJson())
    }

    override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>) {
        cardDao.unionTags(scryfallId, tags)
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

    private fun buildStaleReason(e: Throwable?): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return "${e?.message ?: "Network error"} — $date"
    }
}
