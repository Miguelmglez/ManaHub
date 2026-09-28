package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CachedComboEntry
import com.mmg.manahub.core.data.cache.ComboCache
import com.mmg.manahub.core.data.remote.CommanderSpellbookApiContract
import com.mmg.manahub.core.data.remote.dto.CardInVariantDto
import com.mmg.manahub.core.data.remote.dto.CardRefDto
import com.mmg.manahub.core.data.remote.dto.FeatureProducedByVariantDto
import com.mmg.manahub.core.data.remote.dto.FeatureRefDto
import com.mmg.manahub.core.data.remote.dto.FindMyCombosRequestDto
import com.mmg.manahub.core.data.remote.dto.FindMyCombosResponseDto
import com.mmg.manahub.core.data.remote.dto.FindMyCombosResultDto
import com.mmg.manahub.core.data.remote.dto.VariantDto
import com.mmg.manahub.core.data.remote.dto.VariantsPageDto
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.decks.domain.model.CardComboPage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Repository-layering coverage for [CommanderSpellbookRepositoryImpl] (Deck Engine Unification
 * plan D7, Phase 4.3) -- mirrors [CommunityAggregateRepositoryImplTest]'s hand-written-fake
 * pattern (no Ktor engine mock needed). `commonMain`/`commonTest` -- no Android dependency.
 */
class CommanderSpellbookRepositoryImplTest {

    private val noopCrashReporter = object : CrashReporter {
        override fun recordException(throwable: Throwable) = Unit
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
    }

    private val dispatcherProvider = DispatcherProvider()

    private class FakeCache : ComboCache {
        val store = mutableMapOf<String, CachedComboEntry>()
        override suspend fun get(key: String): CachedComboEntry? = store[key]
        override suspend fun insert(key: String, json: String, cachedAt: Long) {
            store[key] = CachedComboEntry(key, json, cachedAt)
        }
    }

    private class FakeApi(
        val result: (() -> FindMyCombosResponseDto)? = null,
        val variants: (() -> VariantsPageDto)? = null,
    ) : CommanderSpellbookApiContract {
        var callCount = 0
        var lastRequest: FindMyCombosRequestDto? = null
        var lastCardQuery: String? = null
        var lastOffset: Int? = null
        override suspend fun findMyCombos(request: FindMyCombosRequestDto): FindMyCombosResponseDto {
            callCount++
            lastRequest = request
            return result?.invoke() ?: throw IllegalStateException("Commander Spellbook down")
        }

        override suspend fun findVariants(cardQuery: String, limit: Int, offset: Int): VariantsPageDto {
            callCount++
            lastCardQuery = cardQuery
            lastOffset = offset
            return variants?.invoke() ?: throw IllegalStateException("Commander Spellbook down")
        }
    }

    private val comboJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun variant(id: String, cardNames: List<String>, description: String = "desc", produces: List<String> = listOf("Win the game")) =
        VariantDto(
            id = id,
            status = "OK",
            description = description,
            uses = cardNames.map { CardInVariantDto(card = CardRefDto(name = it)) },
            produces = produces.map { FeatureProducedByVariantDto(feature = FeatureRefDto(name = it)) },
        )

    private fun repository(
        api: CommanderSpellbookApiContract,
        cache: ComboCache = FakeCache(),
        clock: () -> Long = { 2_000_000L },
    ): CommanderSpellbookRepositoryImpl = CommanderSpellbookRepositoryImpl(
        api = api,
        cache = cache,
        crashReporter = noopCrashReporter,
        dispatcherProvider = dispatcherProvider,
        now = clock,
    )

    @Test
    fun `empty input returns EMPTY immediately without touching cache or API`() = runTest {
        val api = FakeApi()
        val cache = FakeCache()
        val repo = repository(api, cache)

        val result = repo.findCombos(cardNames = emptyList(), commanderNames = emptyList())

        val success = assertIs<DataResult.Success<com.mmg.manahub.feature.decks.domain.model.ComboResult>>(result)
        assertTrue(success.data.complete.isEmpty())
        assertTrue(success.data.almostThere.isEmpty())
        assertEquals(0, api.callCount)
        assertTrue(cache.store.isEmpty())
    }

    @Test
    fun `cache miss fetches from the API and caches the result`() = runTest {
        val response = FindMyCombosResponseDto(
            results = FindMyCombosResultDto(included = listOf(variant("v1", listOf("Sol Ring", "Basalt Monolith")))),
        )
        val api = FakeApi(result = { response })
        val cache = FakeCache()
        val repo = repository(api, cache)

        val result = repo.findCombos(listOf("Sol Ring", "Basalt Monolith"))

        val success = assertIs<DataResult.Success<com.mmg.manahub.feature.decks.domain.model.ComboResult>>(result)
        assertEquals(1, success.data.complete.size)
        assertEquals(listOf("Sol Ring", "Basalt Monolith"), success.data.complete.single().cardNames)
        assertEquals(1, api.callCount)
        assertTrue(cache.store.isNotEmpty())
    }

    @Test
    fun `fresh cache short-circuits the API call`() = runTest {
        val response = FindMyCombosResponseDto(results = FindMyCombosResultDto(included = listOf(variant("v1", listOf("Sol Ring")))))
        val api = FakeApi(result = { response })
        val cache = FakeCache()
        val now = 5_000_000L
        val key = com.mmg.manahub.core.data.remote.ComboCacheKeys.cacheKey(listOf("Sol Ring"), emptyList())
        cache.store[key] = CachedComboEntry(key, comboJson.encodeToString(FindMyCombosResponseDto.serializer(), response), now - 1000)
        val repo = repository(api, cache, clock = { now })

        val result = repo.findCombos(listOf("Sol Ring"))

        val success = assertIs<DataResult.Success<com.mmg.manahub.feature.decks.domain.model.ComboResult>>(result)
        assertEquals(1, success.data.complete.size)
        assertEquals(0, api.callCount)
        assertTrue(!success.isStale)
    }

    @Test
    fun `API down with a stale cache serves the stale entry flagged isStale`() = runTest {
        val response = FindMyCombosResponseDto(results = FindMyCombosResultDto(included = listOf(variant("v1", listOf("Sol Ring")))))
        val api = FakeApi() // throws -- API down
        val cache = FakeCache()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val now = 20_000_000L
        val key = com.mmg.manahub.core.data.remote.ComboCacheKeys.cacheKey(listOf("Sol Ring"), emptyList())
        cache.store[key] = CachedComboEntry(key, comboJson.encodeToString(FindMyCombosResponseDto.serializer(), response), now - sevenDaysMs - 1000)
        val repo = repository(api, cache, clock = { now })

        val result = repo.findCombos(listOf("Sol Ring"))

        val success = assertIs<DataResult.Success<com.mmg.manahub.feature.decks.domain.model.ComboResult>>(result)
        assertTrue(success.isStale)
        assertEquals(1, success.data.complete.size)
    }

    @Test
    fun `API down with no cache at all degrades to an empty Success, never blocks the caller`() = runTest {
        val api = FakeApi()
        val repo = repository(api, FakeCache())

        val result = repo.findCombos(listOf("Sol Ring"))

        val success = assertIs<DataResult.Success<com.mmg.manahub.feature.decks.domain.model.ComboResult>>(result)
        assertTrue(success.isStale)
        assertTrue(success.data.complete.isEmpty())
        assertTrue(success.data.almostThere.isEmpty())
    }

    @Test
    fun `almostIncluded maps the missing card by set-difference against the queried set`() = runTest {
        val response = FindMyCombosResponseDto(
            results = FindMyCombosResultDto(
                almostIncluded = listOf(variant("v2", listOf("Sol Ring", "Basalt Monolith", "Rings of Brighthearth"))),
            ),
        )
        val api = FakeApi(result = { response })
        val repo = repository(api)

        val result = repo.findCombos(listOf("Sol Ring", "Basalt Monolith"))

        val success = assertIs<DataResult.Success<com.mmg.manahub.feature.decks.domain.model.ComboResult>>(result)
        val almost = success.data.almostThere.single()
        assertEquals("Rings of Brighthearth", almost.missingCardName)
        assertEquals(setOf("Sol Ring", "Basalt Monolith"), almost.ownedCardNames.toSet())
    }

    @Test
    fun `an almostIncluded variant missing zero or more than one card is defensively dropped`() = runTest {
        val zeroMissing = variant("v3", listOf("Sol Ring")) // already fully owned -- not genuinely "almost"
        val twoMissing = variant("v4", listOf("Sol Ring", "Card X", "Card Y"))
        val response = FindMyCombosResponseDto(
            results = FindMyCombosResultDto(almostIncluded = listOf(zeroMissing, twoMissing)),
        )
        val api = FakeApi(result = { response })
        val repo = repository(api)

        val result = repo.findCombos(listOf("Sol Ring"))

        val success = assertIs<DataResult.Success<com.mmg.manahub.feature.decks.domain.model.ComboResult>>(result)
        assertTrue(success.data.almostThere.isEmpty())
    }

    @Test
    fun `commander names are excluded from the missing-card set-difference`() = runTest {
        val response = FindMyCombosResponseDto(
            results = FindMyCombosResultDto(
                included = listOf(variant("v5", listOf("Sol Ring", "Kenrith, the Returned King"))),
            ),
        )
        val api = FakeApi(result = { response })
        val repo = repository(api)

        val result = repo.findCombos(cardNames = listOf("Sol Ring"), commanderNames = listOf("Kenrith, the Returned King"))

        val success = assertIs<DataResult.Success<com.mmg.manahub.feature.decks.domain.model.ComboResult>>(result)
        assertEquals(1, success.data.complete.size)
        assertEquals(1, api.callCount)
    }

    // ── findCombosWithCard ─────────────────────────────────────────────────────────────────────

    @Test
    fun `per-card lookup keeps only variants using the card and maps command-zone and legality`() = runTest {
        val commanderPiece = CardInVariantDto(card = CardRefDto(name = "Kenrith, the Returned King"), mustBeCommander = true)
        val page = VariantsPageDto(
            count = 3,
            next = "https://backend.commanderspellbook.com/variants/?offset=50",
            results = listOf(
                variant("v1", listOf("Sol Ring", "Basalt Monolith")).copy(
                    legalities = buildJsonObject { put("modern", JsonPrimitive(false)); put("legacy", JsonPrimitive(true)); put("oddity", JsonPrimitive("n/a")) },
                ),
                variant("v2", listOf("Sol Ring")).copy(uses = variant("v2", listOf("Sol Ring")).uses + commanderPiece),
                variant("v3", listOf("Soldevi Adnate", "Basalt Monolith")),
            ),
        )
        val api = FakeApi(variants = { page })
        val cache = FakeCache()

        val result = repository(api, cache).findCombosWithCard("Sol Ring", page = 1)

        val data = assertIs<DataResult.Success<CardComboPage>>(result).data
        assertEquals(listOf("v1", "v2"), data.combos.map { it.id })
        assertEquals(mapOf("modern" to false, "legacy" to true), data.combos.first().legalities)
        assertTrue(data.combos[1].requiresCommandZone)
        assertTrue(data.hasMore)
        assertEquals(3, data.totalCount)
        assertEquals("card=\"Sol Ring\"", api.lastCardQuery)
        assertEquals(50, api.lastOffset)
        assertTrue(cache.store.keys.single().startsWith("combo-card:"))
    }

    @Test
    fun `a double-faced name is searched by its front face and matched client-side`() = runTest {
        val api = FakeApi(variants = { VariantsPageDto(results = listOf(variant("v1", listOf("Delver of Secrets", "Brainstorm")))) })

        val result = repository(api).findCombosWithCard("Delver of Secrets // Insectile Aberration", page = 0)

        assertEquals(1, assertIs<DataResult.Success<CardComboPage>>(result).data.combos.size)
        assertEquals("card:\"Delver of Secrets\"", api.lastCardQuery)
    }

    @Test
    fun `a fresh per-card cache entry short-circuits the API`() = runTest {
        val api = FakeApi(variants = { VariantsPageDto(results = listOf(variant("v1", listOf("Sol Ring", "Basalt Monolith")))) })
        val cache = FakeCache()
        val repo = repository(api, cache)
        repo.findCombosWithCard("Sol Ring", page = 0)

        val second = repo.findCombosWithCard("sol ring", page = 0)

        assertEquals(1, api.callCount)
        assertEquals(1, assertIs<DataResult.Success<CardComboPage>>(second).data.combos.size)
    }

    @Test
    fun `API down with no cache degrades to an empty stale page`() = runTest {
        val result = repository(FakeApi()).findCombosWithCard("Sol Ring", page = 0)

        val success = assertIs<DataResult.Success<CardComboPage>>(result)
        assertTrue(success.data.combos.isEmpty())
        assertTrue(success.isStale)
    }
}
