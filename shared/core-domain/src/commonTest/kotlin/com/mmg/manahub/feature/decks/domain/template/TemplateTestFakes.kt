package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.TrendingSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deck Builder v2 (Phase 0/1/2) test fakes. Mirrors the hand-written [CardRepository] fake
 * established in `SuggestAddsFromCommunityUseCaseTest.kt` (only the members these use cases
 * actually exercise are implemented meaningfully; every other member throws -- a signal the test
 * needs updating, not a silent wrong answer). `commonTest` cannot use MockK (JVM-only), hence the
 * hand-rolled fakes here rather than mocks.
 */
class FakeCardRepository(
    private val byExactName: MutableMap<String, Card> = mutableMapOf(),
) : CardRepository {
    var searchWithRawQueryCallCount: Int = 0
        private set

    fun seed(card: Card) {
        byExactName[card.name] = card
    }

    override suspend fun searchCardByName(query: String): DataResult<Card> = error("unused")
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

    override suspend fun getCardByExactName(name: String): Result<Card> {
        val card = byExactName[name]
        return if (card != null) Result.success(card) else Result.failure(NoSuchElementException(name))
    }

    /** MUST stay unreachable in Deck Builder v2 tests -- asserting `searchWithRawQueryCallCount ==
     * 0` after a build is how the "zero alphabetical Scryfall searches" invariant is verified. */
    override suspend fun searchWithRawQuery(query: String, order: String?): List<Card> {
        searchWithRawQueryCallCount++
        return emptyList()
    }

    override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> = error("unused")
    override fun observeCard(scryfallId: String): Flow<Card?> = flowOf(null)
    override suspend fun refreshCollectionPrices() = error("unused")
    override suspend fun updatePrices(scryfallId: String, priceUsd: Double?, priceUsdFoil: Double?, priceEur: Double?, priceEurFoil: Double?, updatedAt: Long) = error("unused")
    override suspend fun evictStaleCache() = error("unused")
    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) = error("unused")
    override suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>) = error("unused")
    override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun warmCacheForIds(scryfallIds: List<String>) = error("unused")
}

/** Configurable-by-lambda fake -- each method defaults to a Worker-down [DataResult.Error] so a
 * test only wires the ONE call path it cares about. */
class FakeCommunityAggregateRepository(
    private val commanderResult: (suspend (String) -> DataResult<CommunityAggregate.Commander>)? = null,
    private val sixtyResult: (suspend (List<String>, Int) -> DataResult<CommunityAggregate.Sixty>)? = null,
) : CommunityAggregateRepository {

    var getSixtyAggregateCallCount: Int = 0
        private set

    override suspend fun getCommanderAggregate(commanderName: String): DataResult<CommunityAggregate.Commander> =
        commanderResult?.invoke(commanderName) ?: DataResult.Error("Worker down")

    override suspend fun getSixtyAggregate(signatureCards: List<String>, format: Int): DataResult<CommunityAggregate.Sixty> {
        getSixtyAggregateCallCount++
        return sixtyResult?.invoke(signatureCards, format) ?: DataResult.Error("Worker down")
    }

    override suspend fun getSimilarDecks(commanderName: String, limit: Int): DataResult<List<String>> =
        DataResult.Error("unused")

    override suspend fun getTrending(week: String?): DataResult<TrendingSnapshot> = DataResult.Error("unused")
}
