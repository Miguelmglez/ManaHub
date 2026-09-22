package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import com.mmg.manahub.core.domain.repository.CardLookupResult
import com.mmg.manahub.core.domain.repository.CardPriceUpdate
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResolveCollectionImportUseCaseTest {

    private fun printing(id: String, name: String, set: String, number: String): Card =
        card(id = id, name = name).copy(setCode = set, collectorNumber = number)

    private fun line(
        name: String? = null,
        set: String? = null,
        number: String? = null,
        id: String? = null,
        quantity: Int = 1,
        isFoil: Boolean = false,
        raw: String = name ?: id ?: "$set $number",
    ) = CollectionImportLine(quantity, name, set, number, id, isFoil, "NM", "en", raw)

    private fun parsed(vararg lines: CollectionImportLine, rejected: List<String> = emptyList()) =
        ParsedCollectionImport(CollectionFileFormat.TEXT, lines.toList(), rejected)

    @Test
    fun `lookups are chunked at 75 identifiers per call`() = runTest {
        val cards = (1..160).map { printing("id-$it", "Card $it", "set", "$it") }
        val repo = FakeLookupRepository(cards)
        val lines = cards.map { line(name = it.name, set = "set", number = it.collectorNumber) }

        val result = ResolveCollectionImportUseCase(repo)(parsed(*lines.toTypedArray()))

        assertEquals(listOf(75, 75, 10), repo.lookupCalls.map { it.size })
        val resolved = assertIs<CollectionImportResolution.Resolved>(result)
        assertEquals(160, resolved.entries.size)
        assertTrue(resolved.unresolvedLines.isEmpty())
    }

    @Test
    fun `identifier priority is id, then printing, then name and set, then name`() {
        val id = ResolveCollectionImportUseCase.identifierFor(line(name = "Opt", set = "xln", number = "65", id = "abc"))
        val printing = ResolveCollectionImportUseCase.identifierFor(line(name = "Opt", set = "xln", number = "65"))
        val nameSet = ResolveCollectionImportUseCase.identifierFor(line(name = "Opt", set = "xln"))
        val name = ResolveCollectionImportUseCase.identifierFor(line(name = "Opt"))

        assertEquals(CardLookupIdentifier(scryfallId = "abc"), id)
        assertEquals(CardLookupIdentifier(setCode = "xln", collectorNumber = "65"), printing)
        assertEquals(CardLookupIdentifier(name = "Opt", setCode = "xln"), nameSet)
        assertEquals(CardLookupIdentifier(name = "Opt"), name)
    }

    @Test
    fun `not found identifiers fall back to fuzzy name search before being unresolved`() = runTest {
        val bolt = printing("bolt", "Lightning Bolt", "2x2", "117")
        val opt = printing("opt", "Opt", "xln", "65")
        val repo = FakeLookupRepository(cards = listOf(bolt), fuzzy = mapOf("Opt" to opt))

        val result = ResolveCollectionImportUseCase(repo)(
            parsed(
                line(name = "Lightning Bolt", set = "2x2", number = "117", quantity = 4, isFoil = true),
                line(id = "dead-id", name = "Opt"),
                line(name = "Nonexistent Card", raw = "1 Nonexistent Card"),
                rejected = listOf("garbage"),
            )
        )

        val resolved = assertIs<CollectionImportResolution.Resolved>(result)
        assertEquals(listOf("bolt", "opt"), resolved.entries.map { it.card.scryfallId })
        assertEquals(4, resolved.entries[0].quantity)
        assertTrue(resolved.entries[0].isFoil)
        assertEquals(listOf("Opt", "Nonexistent Card"), repo.fuzzyCalls)
        assertEquals(listOf("garbage", "1 Nonexistent Card"), resolved.unresolvedLines)
        assertEquals(2, resolved.resolvedLineCount)
    }

    @Test
    fun `lines resolving to the same printing and attributes are merged`() = runTest {
        val opt = printing("opt", "Opt", "xln", "65")
        val repo = FakeLookupRepository(listOf(opt))

        val result = ResolveCollectionImportUseCase(repo)(
            parsed(line(name = "Opt", quantity = 2), line(set = "xln", number = "65", quantity = 3))
        )

        val resolved = assertIs<CollectionImportResolution.Resolved>(result)
        assertEquals(1, resolved.entries.size)
        assertEquals(5, resolved.entries.single().quantity)
    }

    @Test
    fun `split card front face name matches the full card`() = runTest {
        val fireIce = printing("fi", "Fire // Ice", "mh2", "290")
        val repo = FakeLookupRepository(listOf(fireIce))

        val result = ResolveCollectionImportUseCase(repo)(parsed(line(name = "Fire")))

        assertEquals(listOf("fi"), assertIs<CollectionImportResolution.Resolved>(result).entries.map { it.card.scryfallId })
    }

    @Test
    fun `rate limit exhaustion is a typed outcome`() = runTest {
        val repo = FakeLookupRepository(emptyList(), lookupError = "RATE_LIMIT_EXHAUSTED:4200")

        val result = ResolveCollectionImportUseCase(repo)(parsed(line(name = "Opt")))

        assertEquals(CollectionImportResolution.RateLimited(4200), result)
    }

    @Test
    fun `a non rate-limit lookup failure fails the import`() = runTest {
        val repo = FakeLookupRepository(emptyList(), lookupError = "timeout")

        val result = ResolveCollectionImportUseCase(repo)(parsed(line(name = "Opt")))

        assertEquals(CollectionImportResolution.Failed, result)
    }

    @Test
    fun `progress ends at the total line count`() = runTest {
        val repo = FakeLookupRepository(listOf(printing("opt", "Opt", "xln", "65")))
        val progress = mutableListOf<Pair<Int, Int>>()

        ResolveCollectionImportUseCase(repo)(parsed(line(name = "Opt"), line(name = "Missing"))) { done, total ->
            progress += done to total
        }

        assertEquals(2 to 2, progress.last())
    }
}

/** [CardRepository] fake answering batched lookups from [cards] (by id, printing or name). */
private class FakeLookupRepository(
    private val cards: List<Card>,
    private val fuzzy: Map<String, Card> = emptyMap(),
    private val lookupError: String? = null,
) : CardRepository {
    val lookupCalls = mutableListOf<List<CardLookupIdentifier>>()
    val fuzzyCalls = mutableListOf<String>()

    override suspend fun lookupCardsByIdentifiers(identifiers: List<CardLookupIdentifier>): DataResult<CardLookupResult> {
        lookupCalls += identifiers
        lookupError?.let { return DataResult.Error(it) }
        val found = mutableListOf<Card>()
        val missing = mutableListOf<CardLookupIdentifier>()
        identifiers.forEach { id ->
            val match = cards.firstOrNull { c ->
                when {
                    id.scryfallId != null -> c.scryfallId == id.scryfallId
                    id.collectorNumber != null -> c.setCode == id.setCode && c.collectorNumber == id.collectorNumber
                    else -> (c.name.equals(id.name, true) || c.name.substringBefore(" // ").equals(id.name, true)) &&
                        (id.setCode == null || c.setCode == id.setCode)
                }
            }
            if (match != null) found += match else missing += id
        }
        return DataResult.Success(CardLookupResult(found, missing))
    }

    override suspend fun searchCardByName(query: String): DataResult<Card> {
        fuzzyCalls += query
        return fuzzy[query]?.let { DataResult.Success(it) } ?: DataResult.Error("SCRYFALL_404")
    }

    override suspend fun searchCardPrintedName(name: String, lang: String): DataResult<Card> = error("unused")
    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
    override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<com.mmg.manahub.core.model.PaginatedCards> = error("unused")
    override suspend fun getCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
    override suspend fun backfillMissingStrategyTags(limit: Int) = error("unused")
    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> = error("unused")
    override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> = error("unused")
    override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> = error("unused")
    override suspend fun getPlayableSets(): DataResult<List<com.mmg.manahub.core.model.MagicSet>> = error("unused")
    override suspend fun getCardPrints(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardByExactName(name: String): Result<Card> = error("unused")
    override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> = error("unused")
    override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> = error("unused")
    override fun observeCard(scryfallId: String): Flow<Card?> = flowOf(null)
    override suspend fun updatePrices(scryfallId: String, priceUsd: Double?, priceUsdFoil: Double?, priceEur: Double?, priceEurFoil: Double?, updatedAt: Long) = error("unused")
    override suspend fun updatePricesBatch(updates: List<CardPriceUpdate>) = error("unused")
    override suspend fun evictStaleCache() = error("unused")
    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) = error("unused")
    override suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>) = error("unused")
    override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun warmCacheForIds(scryfallIds: List<String>) = error("unused")
}
