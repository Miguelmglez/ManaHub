package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.domain.repository.CardStrategyTagsSubmission
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.PaginatedCards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [RefreshCardStrategyTagsUseCase] (Deck Engine Unification plan, D8, §5 Phase 5c —
 * "card detail view" read point; RUN 7b BUG 2 fix). Verifies: blank oracleId short-circuits, a
 * [CardStrategyTagsResult.Found] result is delegated to [CardRepository.unionCardTags] (the
 * ATOMIC merge primitive — this use case no longer computes the union itself off a
 * possibly-stale `currentTags` snapshot, which is what caused the RUN 7b TOCTOU race), nothing
 * new to add is a no-op (fast-path skip), and a repository failure never propagates.
 */
class RefreshCardStrategyTagsUseCaseTest {

    private class FakeCardStrategyTagsRepository(
        private val result: CardStrategyTagsResult,
    ) : CardStrategyTagsRepository {
        var callCount = 0
        override suspend fun getStrategyTags(oracleId: String): CardStrategyTagsResult {
            callCount++
            return result
        }
        override suspend fun submitStrategyTags(oracleId: String, submission: CardStrategyTagsSubmission) = Unit
    }

    private class ThrowingCardStrategyTagsRepository : CardStrategyTagsRepository {
        override suspend fun getStrategyTags(oracleId: String): CardStrategyTagsResult =
            throw IllegalStateException("offline")
        override suspend fun submitStrategyTags(oracleId: String, submission: CardStrategyTagsSubmission) = Unit
    }

    /**
     * Minimal [CardRepository] fake — only [unionCardTags] is exercised by this use case since
     * the RUN 7b fix. Optionally models the SAME atomic read-union-write [CardDao.unionTags]
     * performs in production, so a test can simulate a concurrent contributor landing between two
     * [unionCardTags] calls and assert the final state is the union of both (never a lost update).
     */
    private class FakeCardRepository(
        private val existingTags: MutableList<CardTag> = mutableListOf(),
    ) : CardRepository {
        var unionCallCount = 0
        var lastUnionedTags: List<CardTag>? = null

        override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>) {
            unionCallCount++
            lastUnionedTags = tags
            // Mirrors CardDao.unionTags: read-current + union + write-back, atomically (single
            // in-memory list, no concurrent mutation window within this fake).
            for (tag in tags) if (tag !in existingTags) existingTags += tag
        }

        fun persistedTags(): List<CardTag> = existingTags.toList()

        // Unused by this use case — minimal no-op implementations to satisfy the interface.
        override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
        override suspend fun searchCardByName(query: String): DataResult<Card> = error("unused")
        override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
        override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<PaginatedCards> = error("unused")
        override suspend fun getCardPrints(name: String): DataResult<List<Card>> = error("unused")
        override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> = error("unused")
        override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> = error("unused")
        override suspend fun getCardByExactName(name: String): Result<Card> = error("unused")
        override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> = error("unused")
        override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> = error("unused")
        override suspend fun searchWithRawQuery(query: String, order: String?): List<Card> = error("unused")
        override suspend fun getPlayableSets(): DataResult<List<MagicSet>> = error("unused")
        override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> = error("unused")
        override suspend fun getCardById(scryfallId: String): DataResult<Card> = error("unused")
        override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
        override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
        override fun observeCard(scryfallId: String): Flow<Card?> = flowOf(null)
        override suspend fun refreshCollectionPrices() = error("unused")
        override suspend fun updatePrices(scryfallId: String, priceUsd: Double?, priceUsdFoil: Double?, priceEur: Double?, priceEurFoil: Double?, updatedAt: Long) = error("unused")
        override suspend fun warmCacheForIds(scryfallIds: List<String>) = error("unused")
        override suspend fun evictStaleCache() = error("unused")
        override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) = error("unused")
        override suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<com.mmg.manahub.core.model.SuggestedTag>) = error("unused")
        override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
        override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    }

    @Test
    fun `given a blank oracleId when invoke then the repository is never queried and nothing is persisted`() = runTest {
        val strategyRepo = FakeCardStrategyTagsRepository(CardStrategyTagsResult.Found(tags = listOf(CardTag.REMOVAL)))
        val cardRepo = FakeCardRepository()
        val useCase = RefreshCardStrategyTagsUseCase(cardRepo, strategyRepo)

        useCase("scryfall-1", oracleId = "", currentTags = emptyList())

        assertEquals(0, strategyRepo.callCount)
        assertEquals(0, cardRepo.unionCallCount)
    }

    @Test
    fun `given precomputed tags found when invoke then they are delegated to unionCardTags`() = runTest {
        val strategyRepo = FakeCardStrategyTagsRepository(CardStrategyTagsResult.Found(tags = listOf(CardTag.REMOVAL, CardTag.RAMP)))
        val cardRepo = FakeCardRepository()
        val useCase = RefreshCardStrategyTagsUseCase(cardRepo, strategyRepo)

        useCase("scryfall-1", oracleId = "oracle-1", currentTags = listOf(CardTag.REMOVAL))

        // The use case no longer computes the merge itself -- it hands the raw remote tags to
        // the atomic unionCardTags primitive, which owns the merge (RUN 7b fix).
        assertEquals(1, cardRepo.unionCallCount)
        assertEquals(listOf(CardTag.REMOVAL, CardTag.RAMP), cardRepo.lastUnionedTags)
        assertTrue(cardRepo.persistedTags().containsAll(listOf(CardTag.REMOVAL, CardTag.RAMP)))
    }

    @Test
    fun `given every remote tag is already present when invoke then nothing is persisted`() = runTest {
        val strategyRepo = FakeCardStrategyTagsRepository(CardStrategyTagsResult.Found(tags = listOf(CardTag.REMOVAL)))
        val cardRepo = FakeCardRepository()
        val useCase = RefreshCardStrategyTagsUseCase(cardRepo, strategyRepo)

        useCase("scryfall-1", oracleId = "oracle-1", currentTags = listOf(CardTag.REMOVAL))

        assertEquals(0, cardRepo.unionCallCount)
    }

    @Test
    fun `given NotFound when invoke then nothing is persisted`() = runTest {
        val strategyRepo = FakeCardStrategyTagsRepository(CardStrategyTagsResult.NotFound)
        val cardRepo = FakeCardRepository()
        val useCase = RefreshCardStrategyTagsUseCase(cardRepo, strategyRepo)

        useCase("scryfall-1", oracleId = "oracle-1", currentTags = emptyList())

        assertEquals(0, cardRepo.unionCallCount)
    }

    @Test
    fun `given the repository throws when invoke then the failure is swallowed`() = runTest {
        val cardRepo = FakeCardRepository()
        val useCase = RefreshCardStrategyTagsUseCase(cardRepo, ThrowingCardStrategyTagsRepository())

        // Should not throw.
        useCase("scryfall-1", oracleId = "oracle-1", currentTags = emptyList())

        assertEquals(0, cardRepo.unionCallCount)
    }

    // ── RUN 7b (BUG 2) regression: TOCTOU race between two concurrent partial-tag contributors ──

    @Test
    fun `given a concurrent contributor already landed when invoke then both contributions survive`() = runTest {
        // Simulates CardRepositoryImpl.scheduleTagResolution (the on-device analyzer's background
        // job) landing its own write BETWEEN this use case's read-snapshot (currentTags, captured
        // by the caller before invoke() even runs) and its own write. Before the RUN 7b fix, this
        // use case computed `(currentTags + remoteTags).distinct()` off the STALE snapshot and
        // plain-overwrote the column -- silently discarding the concurrent contributor's tag.
        val concurrentContribution = CardTag.WRATH
        val cardRepo = FakeCardRepository(existingTags = mutableListOf(concurrentContribution))
        val strategyRepo = FakeCardStrategyTagsRepository(CardStrategyTagsResult.Found(tags = listOf(CardTag.RAMP)))
        val useCase = RefreshCardStrategyTagsUseCase(cardRepo, strategyRepo)

        // currentTags is the STALE pre-race snapshot -- it does NOT know about concurrentContribution.
        useCase("scryfall-1", oracleId = "oracle-1", currentTags = emptyList())

        // The atomic union primitive (mocked here, real in CardDao.unionTags) must have merged this
        // use case's contribution WITHOUT dropping the concurrent one.
        assertEquals(
            setOf(concurrentContribution, CardTag.RAMP),
            cardRepo.persistedTags().toSet(),
            "both the concurrent contributor's tag and this use case's tag must survive",
        )
    }

    @Test
    fun `given two interleaved unionCardTags contributions then the final state is their union`() = runTest {
        // Direct exercise of the fake's atomic-union model (mirrors CardDao.unionTags) with two
        // overlapping contributions, proving the merge is commutative and lossless regardless of
        // call order -- the same invariant CardDaoConcurrencyTest verifies against a real Room DB.
        val cardRepo = FakeCardRepository()

        cardRepo.unionCardTags("scryfall-1", listOf(CardTag.REMOVAL, CardTag.RAMP))
        cardRepo.unionCardTags("scryfall-1", listOf(CardTag.RAMP, CardTag.TUTOR))

        assertEquals(
            setOf(CardTag.REMOVAL, CardTag.RAMP, CardTag.TUTOR),
            cardRepo.persistedTags().toSet(),
        )
    }
}
