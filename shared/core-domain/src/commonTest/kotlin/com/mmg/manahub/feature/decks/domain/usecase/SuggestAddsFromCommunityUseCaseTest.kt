package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.AggregateCardEntry
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Minimal, hand-written [CardRepository] fake — only [getCardById]/[searchCardByName] are exercised
 * by [SuggestAddsFromCommunityUseCase]; every other member throws if ever called (a signal the test
 * needs to be updated, not a silent wrong answer). Mirrors the `ktor-client-mock`-avoidance testing
 * seam already established for `CommunityAggregateRepositoryImplTest` (`project_community_aggregate_worker` memory). */
private class FakeCardRepository(private val byId: Map<String, Card>, private val byName: Map<String, Card>) : CardRepository {
    override suspend fun searchCardByName(query: String): DataResult<Card> =
        byName[query]?.let { DataResult.Success(it) } ?: DataResult.Error("not found")
    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
    override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<com.mmg.manahub.core.model.PaginatedCards> = error("unused")
    override suspend fun getCardById(scryfallId: String): DataResult<Card> =
        byId[scryfallId]?.let { DataResult.Success(it) } ?: DataResult.Error("not found")
    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> = error("unused")
    override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> = error("unused")
    // Pre-existing gap fixed while touching this fake for the A3 backfill additions above
    // (2026-07-15): getLanguagePrints was added to CardRepository for Card Versions & Languages
    // Phase 1A but this hand-written fake was never updated, breaking wasmJs test compilation.
    override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> = error("unused")
    override suspend fun getPlayableSets(): DataResult<List<com.mmg.manahub.core.model.MagicSet>> = error("unused")
    override suspend fun getCardPrints(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardByExactName(name: String): Result<Card> = error("unused")
    override suspend fun searchWithRawQuery(query: String, order: String?): List<Card> = error("unused")
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

private fun aggregateCard(name: String, uid: String?, inclusion: Float, synergy: Float) =
    AggregateCardEntry(name = name, scryfallUid = uid, inclusionPct = inclusion, synergy = synergy, category = "Top Cards", numDecks = 100)

/**
 * Deck Doctor Community/Archetype plan, Phase 4 (Motor B) — commonTest coverage for
 * [SuggestAddsFromCommunityUseCase]. Multiplatform (kotlin-test, no MockK), hand-written
 * [FakeCardRepository] instead of a mock.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SuggestAddsFromCommunityUseCaseTest {

    private lateinit var scorer: DeckScorer

    @BeforeTest
    fun setUp() {
        scorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)
    }

    @Test
    fun resolvesByScryfallUidWhenPresentAndByNameOtherwise() = runTest {
        val byId = card(id = "sol-ring-id", name = "Sol Ring", colors = emptyList(), colorIdentity = emptyList())
        val byName = card(id = "rampant-growth-id", name = "Rampant Growth", colors = listOf("G"), colorIdentity = listOf("G"))
        val repo = FakeCardRepository(byId = mapOf("sol-ring-id" to byId), byName = mapOf("Rampant Growth" to byName))
        val useCase = SuggestAddsFromCommunityUseCase(cardRepository = repo)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.COMMANDER, colorIdentity = setOf(ManaColor.G), seedTags = emptyList())

        val result = useCase(
            aggregateCards = listOf(
                aggregateCard("Sol Ring", uid = "sol-ring-id", inclusion = 0.9f, synergy = -0.1f),
                aggregateCard("Rampant Growth", uid = null, inclusion = 0.5f, synergy = 0.2f),
            ),
            mainboard = emptyList(),
            profile = profile,
            collection = emptyList(),
        )

        assertEquals(setOf("sol-ring-id", "rampant-growth-id"), result.map { it.card.scryfallId }.toSet())
    }

    @Test
    fun anUnresolvableCardIsSkippedNeverAbortingTheBatch() = runTest {
        val resolvable = card(id = "ok-id", name = "Ok Card", colors = listOf("G"), colorIdentity = listOf("G"))
        val repo = FakeCardRepository(byId = emptyMap(), byName = mapOf("Ok Card" to resolvable))
        val useCase = SuggestAddsFromCommunityUseCase(cardRepository = repo)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.G), seedTags = emptyList())

        val result = useCase(
            aggregateCards = listOf(
                aggregateCard("Unresolvable Card", uid = null, inclusion = 0.9f, synergy = 0.5f),
                aggregateCard("Ok Card", uid = null, inclusion = 0.5f, synergy = 0.2f),
            ),
            mainboard = emptyList(),
            profile = profile,
            collection = emptyList(),
        )

        assertEquals(listOf("ok-id"), result.map { it.card.scryfallId })
    }

    @Test
    fun excludesACardAlreadyInTheMainboard() = runTest {
        val already = card(id = "already-id", name = "Already In Deck", colors = listOf("U"), colorIdentity = listOf("U"))
        val repo = FakeCardRepository(byId = mapOf("already-id" to already), byName = emptyMap())
        val useCase = SuggestAddsFromCommunityUseCase(cardRepository = repo)
        val mainboard = listOf(com.mmg.manahub.feature.decks.domain.engine.entry(already, quantity = 1))
        val profile = scorer.profile(mainboard = mainboard, format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.U), seedTags = emptyList())

        val result = useCase(
            aggregateCards = listOf(aggregateCard("Already In Deck", uid = "already-id", inclusion = 0.9f, synergy = 0.5f)),
            mainboard = mainboard,
            profile = profile,
            collection = emptyList(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun commanderFailsClosedOnUnresolvedColorIdentity() = runTest {
        val unresolved = card(id = "bad-id", name = "Bad Card", colors = listOf("R", "W"), colorIdentity = listOf("R"))
        val repo = FakeCardRepository(byId = mapOf("bad-id" to unresolved), byName = emptyMap())
        val useCase = SuggestAddsFromCommunityUseCase(cardRepository = repo)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.COMMANDER, colorIdentity = setOf(ManaColor.R), seedTags = emptyList())

        val result = useCase(
            aggregateCards = listOf(aggregateCard("Bad Card", uid = "bad-id", inclusion = 0.9f, synergy = 0.5f)),
            mainboard = emptyList(),
            profile = profile,
            collection = emptyList(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun ownedInCollectionFlagReflectsTheUsersCollection() = runTest {
        val owned = card(id = "owned-id", name = "Owned Card", colors = listOf("B"), colorIdentity = listOf("B"))
        val notOwned = card(id = "not-owned-id", name = "Not Owned Card", colors = listOf("B"), colorIdentity = listOf("B"))
        val repo = FakeCardRepository(byId = mapOf("owned-id" to owned, "not-owned-id" to notOwned), byName = emptyMap())
        val useCase = SuggestAddsFromCommunityUseCase(cardRepository = repo)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.B), seedTags = emptyList())

        val result = useCase(
            aggregateCards = listOf(
                aggregateCard("Owned Card", uid = "owned-id", inclusion = 0.9f, synergy = 0.5f),
                aggregateCard("Not Owned Card", uid = "not-owned-id", inclusion = 0.9f, synergy = 0.5f),
            ),
            mainboard = emptyList(),
            profile = profile,
            collection = listOf(owned),
        )

        assertTrue(result.first { it.card.scryfallId == "owned-id" }.ownedInCollection)
        assertFalse(result.first { it.card.scryfallId == "not-owned-id" }.ownedInCollection)
    }

    @Test
    fun rankingIsOrderedBySynergyThenInclusionThenNameDeterministically() = runTest {
        val a = card(id = "a-id", name = "Alpha", colors = listOf("W"), colorIdentity = listOf("W"))
        val b = card(id = "b-id", name = "Beta", colors = listOf("W"), colorIdentity = listOf("W"))
        val c = card(id = "c-id", name = "Gamma", colors = listOf("W"), colorIdentity = listOf("W"))
        val repo = FakeCardRepository(byId = mapOf("a-id" to a, "b-id" to b, "c-id" to c), byName = emptyMap())
        val useCase = SuggestAddsFromCommunityUseCase(cardRepository = repo)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.W), seedTags = emptyList())

        val entries = listOf(
            aggregateCard("Alpha", uid = "a-id", inclusion = 0.5f, synergy = 0.2f),
            aggregateCard("Beta", uid = "b-id", inclusion = 0.5f, synergy = 0.8f),
            aggregateCard("Gamma", uid = "c-id", inclusion = 0.9f, synergy = 0.2f),
        )

        val firstRun = useCase(aggregateCards = entries, mainboard = emptyList(), profile = profile, collection = emptyList())
        val secondRun = useCase(aggregateCards = entries.reversed(), mainboard = emptyList(), profile = profile, collection = emptyList())

        // Beta has the highest synergy (0.8) so it must rank first; Gamma/Alpha tie on synergy
        // (0.2) so Gamma (higher inclusion 0.9 vs 0.5) must rank next.
        assertEquals(listOf("b-id", "c-id", "a-id"), firstRun.map { it.card.scryfallId })
        assertEquals(firstRun.map { it.card.scryfallId }, secondRun.map { it.card.scryfallId })
    }
}
