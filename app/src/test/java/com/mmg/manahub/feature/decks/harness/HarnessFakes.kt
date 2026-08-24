package com.mmg.manahub.feature.decks.harness

import com.mmg.manahub.core.domain.repository.CardPriceUpdate
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.PaginatedCards
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.TrendingSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// ═══════════════════════════════════════════════════════════════════════════════
//  Wizard Quality Campaign -- Phase H harness fakes. Mirrors the hand-rolled pattern already
//  established in shared/core-domain's commonTest `TemplateTestFakes.kt` (only the members the
//  build/Doctor pipeline actually exercises are implemented meaningfully; every other member
//  `error("unused")`s -- a signal a NEW call path needs wiring, never a silent wrong answer).
//  commonTest fakes are not visible from :app's test source set (test sources are not exported
//  cross-module), hence this harness-local duplicate rather than a shared reference.
// ═══════════════════════════════════════════════════════════════════════════════

/** Exact-name lookups resolve from the fixture pool (collection + basics) -- no network, ever.
 * An unknown name (renamed card, fixture gap) returns a failure, exactly like production's
 * "resolveByExactName returns null on any miss, callers skip silently" contract. */
class FixtureCardRepository(
    private val cardsByName: Map<String, Card>,
    private val basicsByName: Map<String, Card>,
) : CardRepository {
    var searchWithRawQueryCallCount: Int = 0
        private set

    override suspend fun getCardByExactName(name: String): Result<Card> {
        val card = cardsByName[name] ?: basicsByName[name]
        return if (card != null) Result.success(card) else Result.failure(NoSuchElementException(name))
    }

    /** MUST stay unreachable during a wizard build (D-something "zero alphabetical Scryfall
     * searches" invariant) -- tracked so the harness can assert it the same way
     * BuildDeckFromTemplateUseCaseTest does. */
    override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> {
        searchWithRawQueryCallCount++
        return emptyList()
    }

    override suspend fun searchCardByName(query: String): DataResult<Card> = error("unused")
    override suspend fun searchCardPrintedName(name: String, lang: String): DataResult<Card> = error("unused")
    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
    override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<PaginatedCards> = error("unused")
    override suspend fun getCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
    override suspend fun backfillMissingStrategyTags(limit: Int) = error("unused")
    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> = error("unused")
    override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> = error("unused")
    override suspend fun getCardPrints(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> = error("unused")
    override suspend fun getPlayableSets(): DataResult<List<MagicSet>> = error("unused")
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

/** ALWAYS returns [DataResult.Error] (the community engine flag is off / Worker down) -- forces
 * [com.mmg.manahub.feature.decks.domain.template.DeckTemplateResolver] onto its SYNTHETIC
 * fallback path unconditionally, per the campaign plan's "deterministic, offline" harness design
 * (D9: synthetic-first mirrors the real offline-user reality this campaign is testing). */
class NoCommunityAggregateRepository : CommunityAggregateRepository {
    override suspend fun getCommanderAggregate(commanderName: String): DataResult<CommunityAggregate.Commander> =
        DataResult.Error("harness: community engine disabled")

    override suspend fun getSixtyAggregate(signatureCards: List<String>, format: Int): DataResult<CommunityAggregate.Sixty> =
        DataResult.Error("harness: community engine disabled")

    override suspend fun getSimilarDecks(commanderName: String, limit: Int): DataResult<List<String>> =
        DataResult.Error("harness: community engine disabled")

    override suspend fun getTrending(week: String?): DataResult<TrendingSnapshot> =
        DataResult.Error("harness: community engine disabled")
}
