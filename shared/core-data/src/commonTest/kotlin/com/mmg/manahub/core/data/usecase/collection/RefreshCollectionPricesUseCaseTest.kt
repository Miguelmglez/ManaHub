package com.mmg.manahub.core.data.usecase.collection

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.network.ScryfallCache
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.CardCollectionResponseDto
import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.CardIdentifierDto
import com.mmg.manahub.core.data.remote.dto.LegalitiesDto
import com.mmg.manahub.core.data.remote.dto.PricesDto
import com.mmg.manahub.core.domain.repository.AddOutcome
import com.mmg.manahub.core.domain.repository.CardPriceUpdate
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Unit tests for [RefreshCollectionPricesUseCase] (Backend & Performance Optimization plan,
 * WS1+WS3 Part B item 7 — 2026-07-28; see `project_backend_perf_ws1_ws3_cold_start_hygiene`
 * memory). Sole owning suite for this use case's stale-only filtering, per-slice batching, and
 * [RefreshCollectionPricesUseCase.Result.Capped] boundary.
 *
 * KMP-first convention: [ScryfallRemoteDataSource] wraps a Ktor-backed [ScryfallClient] (a
 * CONCRETE class, not an interface), so a REAL [ScryfallRemoteDataSource] is constructed here
 * against a Ktor `MockEngine` (never MockWebServer, which is JVM/OkHttp-only and would not compile
 * for wasmJs) — no real network call. [CardRepository]/[UserCardRepository] are hand-written
 * per-file fakes (`error("unused")` for every unexercised member) per the established `commonTest`
 * convention (no MockK on wasmJs).
 *
 * Timing note: [ScryfallRequestQueue]'s spacing delay is a real coroutine `delay()`, and this use
 * case's `flow{}` is explicitly `.flowOn(dispatcherProvider.io)` — a REAL dispatcher (`Dispatchers
 * .IO` on the JVM actual), not the `runTest` virtual scheduler. Every test therefore incurs a small
 * amount of genuine wall-clock time (a handful of ~100ms spacing delays across chunks) — kept
 * bounded by using small collections everywhere except the SLICE_SIZE boundary test, which is
 * unavoidably larger (SLICE_SIZE is a hardcoded companion constant, not configurable).
 */
@OptIn(ExperimentalTime::class)
class RefreshCollectionPricesUseCaseTest {

    private val dtoJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeUserCardRepository(private val ids: List<String>) : UserCardRepository {
        override fun observeCollection(): Flow<List<UserCardWithCard>> = error("unused")
        override fun observeByColor(color: String): Flow<List<UserCardWithCard>> = error("unused")
        override fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>> = error("unused")
        override fun searchInCollection(query: String): Flow<List<UserCardWithCard>> = error("unused")
        override fun observeByScryfallId(scryfallId: String, userId: String?): Flow<List<UserCard>> = error("unused")
        override fun observeCount(userId: String?): Flow<Int> = error("unused")
        override fun observeRecentlyAdded(limit: Int): Flow<List<UserCardWithCard>> = error("unused")
        override fun observeVersionsByOracle(oracleId: String, name: String, userId: String?): Flow<List<UserCardWithCard>> =
            error("unused")
        override suspend fun addOrIncrement(
            scryfallId: String, isFoil: Boolean, condition: String, language: String,
            isForTrade: Boolean, userId: String?, quantity: Int,
        ): AddOutcome = error("unused")
        override suspend fun updateAttributes(id: String, isForTrade: Boolean, quantity: Int) = error("unused")
        override suspend fun deleteCard(id: String) = error("unused")
        override suspend fun getScryfallIds(): List<String> = ids
        override suspend fun decrementOrRemove(
            userId: String, scryfallId: String, isFoil: Boolean, condition: String,
            language: String, quantityToDeduct: Int,
        ) = error("unused")
        override suspend fun updateEntryWithMerge(
            entryId: String, newScryfallId: String, isFoil: Boolean, condition: String,
            language: String, quantity: Int, userId: String?,
        ): UpdateEntryOutcome = error("unused")
    }

    /** @param cachedAtById cachedAt timestamp per id already in Room; a MISSING id is "stale" too (never cached). */
    private inner class FakeCardRepository(private val cachedAtById: Map<String, Long>) : CardRepository {
        val updatePricesBatchCalls = mutableListOf<List<CardPriceUpdate>>()

        override suspend fun searchCardByName(query: String): DataResult<Card> = error("unused")
        override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
        override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean) = error("unused")
        override suspend fun getCardById(scryfallId: String): DataResult<Card> = error("unused")
        override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
        override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
        override suspend fun backfillMissingStrategyTags(limit: Int) = error("unused")
        override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> = error("unused")
        override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> = error("unused")
        override suspend fun getCardPrints(name: String): DataResult<List<Card>> = error("unused")
        override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> = error("unused")
        override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> = error("unused")
        override suspend fun getCardByExactName(name: String): Result<Card> = error("unused")
        override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> = error("unused")
        override suspend fun getPlayableSets(): DataResult<List<com.mmg.manahub.core.model.MagicSet>> = error("unused")

        override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> =
            scryfallIds.mapNotNull { id ->
                cachedAtById[id]?.let { cachedAt -> minimalCard(id, cachedAt) }
            }

        override fun observeCard(scryfallId: String): Flow<Card?> = error("unused")
        override suspend fun updatePrices(
            scryfallId: String, priceUsd: Double?, priceUsdFoil: Double?,
            priceEur: Double?, priceEurFoil: Double?, updatedAt: Long,
        ) = error("unused")

        override suspend fun updatePricesBatch(updates: List<CardPriceUpdate>) {
            updatePricesBatchCalls.add(updates)
        }

        override suspend fun evictStaleCache() = error("unused")
        override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
        override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
        override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) = error("unused")
        override suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>) = error("unused")
        override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
        override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
        override suspend fun warmCacheForIds(scryfallIds: List<String>) = error("unused")
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun minimalCard(id: String, cachedAt: Long) = Card(
        scryfallId = id, name = "Card $id", printedName = null, manaCost = null, cmc = 1.0,
        colors = emptyList(), colorIdentity = emptyList(), typeLine = "Instant", printedTypeLine = null,
        oracleText = null, printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "tst", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2020-01-01", frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
        imageNormal = null, imageArtCrop = null, imageBackNormal = null, priceUsd = null,
        priceUsdFoil = null, priceEur = null, priceEurFoil = null, legalityStandard = "legal",
        legalityPioneer = "legal", legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = null, scryfallUri = "https://scryfall.com/card/$id",
        cachedAt = cachedAt,
    )

    private fun cardDto(id: String, priceUsd: String? = "1.23") = CardDto(
        id = id, name = "Card $id", lang = "en", colorIdentity = emptyList(), keywords = emptyList(),
        setCode = "tst", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2020-01-01",
        prices = PricesDto(usd = priceUsd, usdFoil = null, eur = null, eurFoil = null),
        legalities = LegalitiesDto(
            standard = "legal", pioneer = "legal", modern = "legal", legacy = "legal",
            vintage = "legal", commander = "legal", pauper = "legal",
        ),
        scryfallUri = "https://scryfall.com/card/$id",
    )

    /**
     * A real [ScryfallRemoteDataSource] whose MockEngine returns [chunkResponses] IN ORDER, one per
     * `/cards/collection` POST call — deterministic because [RefreshCollectionPricesUseCase] chunks
     * `staleIds` (whose order comes straight from [FakeUserCardRepository.getScryfallIds]) in a
     * fixed, known sequence, so this avoids the fragility of decoding each request's JSON body just
     * to echo it back.
     */
    private fun scryfallDataSource(
        chunkResponses: List<CardCollectionResponseDto>,
        requestCounter: IntArray = IntArray(1),
        cache: ScryfallCache = ScryfallCache(),
    ): ScryfallRemoteDataSource {
        var callIndex = 0
        val engine = MockEngine { _ ->
            requestCounter[0]++
            val response = chunkResponses[callIndex]
            callIndex++
            respond(
                content = dtoJson.encodeToString(CardCollectionResponseDto.serializer(), response),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(dtoJson) } }
        val client = ScryfallClient(httpClient, baseUrl = "https://api.scryfall.com/")
        return ScryfallRemoteDataSource(
            api = client,
            requestQueue = ScryfallRequestQueue(),
            cache = cache,
            dispatcherProvider = DispatcherProvider(),
        )
    }

    /** A [ScryfallRemoteDataSource] whose engine must NEVER be called. */
    private fun explodingScryfallDataSource(): ScryfallRemoteDataSource {
        val engine = MockEngine { _ -> throw AssertionError("Scryfall must not be called for a fully-fresh collection") }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(dtoJson) } }
        val client = ScryfallClient(httpClient, baseUrl = "https://api.scryfall.com/")
        return ScryfallRemoteDataSource(
            api = client,
            requestQueue = ScryfallRequestQueue(),
            cache = ScryfallCache(),
            dispatcherProvider = DispatcherProvider(),
        )
    }

    private fun useCase(
        ids: List<String>,
        cachedAtById: Map<String, Long>,
        scryfallDataSource: ScryfallRemoteDataSource,
    ): Pair<RefreshCollectionPricesUseCase, FakeCardRepository> {
        val cardRepo = FakeCardRepository(cachedAtById)
        val uc = RefreshCollectionPricesUseCase(
            userCardRepository = FakeUserCardRepository(ids),
            cardRepository = cardRepo,
            scryfallDataSource = scryfallDataSource,
            dispatcherProvider = DispatcherProvider(),
        )
        return uc to cardRepo
    }

    private val now get() = Clock.System.now().toEpochMilliseconds()

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `given an empty collection when refreshing then Success with zero counts and zero network calls`() = runTest {
        val (uc, cardRepo) = useCase(ids = emptyList(), cachedAtById = emptyMap(), scryfallDataSource = explodingScryfallDataSource())

        val results = uc.invoke().toList()

        val success = results.filterIsInstance<RefreshCollectionPricesUseCase.Result.Success>().single()
        assertEquals(0, success.updatedCount)
        assertEquals(0, success.notFoundCount)
        assertTrue(cardRepo.updatePricesBatchCalls.isEmpty())
    }

    @Test
    fun `given every collection card is already fresh when refreshing then it is a near-no-op with zero network calls`() = runTest {
        val ids = listOf("a", "b", "c")
        val cachedAtById = ids.associateWith { now } // cached "just now" -> fresh
        val (uc, cardRepo) = useCase(ids = ids, cachedAtById = cachedAtById, scryfallDataSource = explodingScryfallDataSource())

        val results = uc.invoke().toList()

        val success = results.filterIsInstance<RefreshCollectionPricesUseCase.Result.Success>().single()
        assertEquals(0, success.updatedCount)
        assertTrue(cardRepo.updatePricesBatchCalls.isEmpty())
    }

    @Test
    fun `given a mix of stale and fresh cards when refreshing then only the stale ids are fetched and one updatePricesBatch call is made`() = runTest {
        val staleIds = listOf("stale-1", "stale-2")
        val freshIds = listOf("fresh-1")
        val cachedAtById = freshIds.associateWith { now } + staleIds.associateWith { 0L } // cachedAt=0 -> ancient -> stale
        val requestCounter = IntArray(1)
        val ds = scryfallDataSource(
            chunkResponses = listOf(
                CardCollectionResponseDto(data = staleIds.map { cardDto(it) }, notFound = emptyList()),
            ),
            requestCounter = requestCounter,
        )
        val (uc, cardRepo) = useCase(ids = staleIds + freshIds, cachedAtById = cachedAtById, scryfallDataSource = ds)

        val results = uc.invoke().toList()

        assertEquals(1, requestCounter[0], "only ONE Scryfall batch call for the single (2-id) chunk/slice")
        assertEquals(1, cardRepo.updatePricesBatchCalls.size, "ONE Room write for the single slice, never one per chunk")
        val written = cardRepo.updatePricesBatchCalls.single()
        assertEquals(staleIds.toSet(), written.map { it.scryfallId }.toSet())
        assertEquals(1.23, written.first().priceUsd)

        val success = results.filterIsInstance<RefreshCollectionPricesUseCase.Result.Success>().single()
        assertEquals(2, success.updatedCount)
        assertEquals(0, success.notFoundCount)
    }

    @Test
    fun `given a stale card already sitting in the in-memory ScryfallCache when refreshing then the cache entry is invalidated after the slice write`() = runTest {
        val staleId = "stale-1"
        val cache = ScryfallCache()
        cache.cards.put(staleId, minimalCard(staleId, cachedAt = 0L)) // pre-refresh, stale-but-live cache entry
        val ds = scryfallDataSource(
            chunkResponses = listOf(CardCollectionResponseDto(data = listOf(cardDto(staleId)), notFound = emptyList())),
            cache = cache,
        )
        val (uc, _) = useCase(ids = listOf(staleId), cachedAtById = mapOf(staleId to 0L), scryfallDataSource = ds)

        uc.invoke().toList()

        // Cache coherence (item 7g): a caller re-reading via getCardById/getCardsBatch after this
        // refresh must NOT keep serving the pre-refresh cached Card for the rest of its 24h TTL.
        assertEquals(null, cache.cards.get(staleId), "the in-memory cache entry must be invalidated after the slice write")
    }

    @Test
    fun `given a card missing from the Scryfall response when refreshing then notFoundCount reflects it`() = runTest {
        val staleIds = listOf("stale-1", "gone-1")
        val cachedAtById = staleIds.associateWith { 0L }
        val ds = scryfallDataSource(
            chunkResponses = listOf(
                CardCollectionResponseDto(
                    data = listOf(cardDto("stale-1")),
                    notFound = listOf(CardIdentifierDto(id = "gone-1")),
                ),
            ),
        )
        val (uc, _) = useCase(ids = staleIds, cachedAtById = cachedAtById, scryfallDataSource = ds)

        val results = uc.invoke().toList()

        val success = results.filterIsInstance<RefreshCollectionPricesUseCase.Result.Success>().single()
        assertEquals(1, success.updatedCount)
        assertEquals(1, success.notFoundCount)
    }

    @Test
    fun `given more stale ids than SLICE_SIZE times maxSlices when refreshing then it yields Capped with ONE updatePricesBatch call for the processed slice`() = runTest {
        // SLICE_SIZE (525) is a hardcoded companion constant -- this boundary can only be exercised
        // with a genuinely large id set. maxSlices=1 caps the run at exactly one slice, leaving one
        // id unprocessed, WITHOUT needing more than SLICE_SIZE+1 total ids.
        val staleIds = (1..RefreshCollectionPricesUseCase.SLICE_SIZE + 1).map { "stale-$it" }
        val cachedAtById = staleIds.associateWith { 0L }
        // 525 ids -> 7 chunks of 75 within the single processed slice.
        val chunkResponses = staleIds.take(RefreshCollectionPricesUseCase.SLICE_SIZE)
            .chunked(75)
            .map { chunk -> CardCollectionResponseDto(data = chunk.map { cardDto(it) }, notFound = emptyList()) }
        val requestCounter = IntArray(1)
        val ds = scryfallDataSource(chunkResponses = chunkResponses, requestCounter = requestCounter)
        val (uc, cardRepo) = useCase(ids = staleIds, cachedAtById = cachedAtById, scryfallDataSource = ds)

        val results = uc.invoke(maxSlices = 1).toList()

        assertEquals(7, requestCounter[0], "7 Scryfall chunk calls for the one 525-id slice processed")
        // The invariant this whole workstream exists to protect: ONE Room write per SLICE, never
        // one per 75-card CHUNK (that shape is the invalidation-storm regression the plan calls out).
        assertEquals(1, cardRepo.updatePricesBatchCalls.size)
        assertEquals(RefreshCollectionPricesUseCase.SLICE_SIZE, cardRepo.updatePricesBatchCalls.single().size)

        val capped = results.filterIsInstance<RefreshCollectionPricesUseCase.Result.Capped>().single()
        assertEquals(RefreshCollectionPricesUseCase.SLICE_SIZE, capped.updatedCount)
        assertEquals(1, capped.remainingStaleCount, "the 526th id was left stale -- must NOT claim the daily watermark")
        assertTrue(results.none { it is RefreshCollectionPricesUseCase.Result.Success }, "Capped and Success are mutually exclusive for one run")
    }
}
