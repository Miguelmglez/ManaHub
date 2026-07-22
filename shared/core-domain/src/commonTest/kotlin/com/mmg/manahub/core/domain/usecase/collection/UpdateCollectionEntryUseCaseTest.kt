package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Minimal, hand-written [CardRepository] fake resolving [getCardById] from [byId]. */
private class FakeCardRepository(
    private val byId: Map<String, Card> = emptyMap(),
    private val errorMessage: String? = null,
    // Broken-image fix (2026-07-17). Controls the best-effort English-sibling warm-cache call —
    // absent by default (DataResult.Error, matching "sibling not cached/reachable yet").
    private val bySetAndNumber: Map<Pair<String, String>, Card> = emptyMap(),
) : CardRepository {
    // Broken-image fix (2026-07-17). Call-tracking for the warm-cache invocation, mirroring
    // FakeUserCardRepository's mergeCallCount/lastCall pattern below.
    var getCardBySetAndNumberCallCount = 0
        private set
    var lastSetAndNumberCall: Pair<String, String>? = null
        private set

    override suspend fun searchCardByName(query: String): DataResult<Card> = error("unused")
    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
    override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<com.mmg.manahub.core.model.PaginatedCards> = error("unused")
    override suspend fun getCardById(scryfallId: String): DataResult<Card> =
        errorMessage?.let { DataResult.Error(it) }
            ?: byId[scryfallId]?.let { DataResult.Success(it) }
            ?: DataResult.Error("not found")
    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> {
        getCardBySetAndNumberCallCount++
        lastSetAndNumberCall = set to number
        return bySetAndNumber[set to number]?.let { DataResult.Success(it) } ?: DataResult.Error("not found")
    }
    override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> = error("unused")
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

/**
 * Minimal, hand-written [UserCardRepository] fake. Only [updateEntryWithMerge] is exercised by
 * [UpdateCollectionEntryUseCase] — every other member is unused by this use case and throws if
 * accidentally invoked.
 */
private class FakeUserCardRepository(
    private val outcome: UpdateEntryOutcome = UpdateEntryOutcome.UPDATED,
) : UserCardRepository {
    var mergeCallCount = 0
        private set
    var lastCall: MergeCall? = null
        private set

    data class MergeCall(
        val entryId: String,
        val newScryfallId: String,
        val isFoil: Boolean,
        val condition: String,
        val language: String,
        val quantity: Int,
        val userId: String?,
    )

    override fun observeCollection(): Flow<List<UserCardWithCard>> = error("unused")
    override fun observeByColor(color: String): Flow<List<UserCardWithCard>> = error("unused")
    override fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>> = error("unused")
    override fun searchInCollection(query: String): Flow<List<UserCardWithCard>> = error("unused")
    override fun observeByScryfallId(scryfallId: String, userId: String?): Flow<List<UserCard>> = error("unused")
    override fun observeCount(userId: String?): Flow<Int> = error("unused")
    override fun observeRecentlyAdded(limit: Int): Flow<List<UserCardWithCard>> = error("unused")
    override fun observeVersionsByOracle(oracleId: String, name: String, userId: String?): Flow<List<UserCardWithCard>> = error("unused")
    override suspend fun addOrIncrement(scryfallId: String, isFoil: Boolean, condition: String, language: String, isForTrade: Boolean, userId: String?, quantity: Int) = error("unused")
    override suspend fun updateAttributes(id: String, isForTrade: Boolean, quantity: Int) = error("unused")
    override suspend fun deleteCard(id: String) = error("unused")
    override suspend fun getScryfallIds(): List<String> = error("unused")
    override suspend fun decrementOrRemove(userId: String, scryfallId: String, isFoil: Boolean, condition: String, language: String, quantityToDeduct: Int) = error("unused")

    override suspend fun updateEntryWithMerge(
        entryId: String,
        newScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantity: Int,
        userId: String?,
    ): UpdateEntryOutcome {
        mergeCallCount++
        lastCall = MergeCall(entryId, newScryfallId, isFoil, condition, language, quantity, userId)
        return outcome
    }
}

private fun buildCard(scryfallId: String = "scry-new") = Card(
    scryfallId = scryfallId,
    name = "Lightning Bolt",
    printedName = null,
    manaCost = "{R}",
    cmc = 1.0,
    colors = listOf("R"),
    colorIdentity = listOf("R"),
    typeLine = "Instant",
    printedTypeLine = null,
    oracleText = null,
    printedText = null,
    keywords = emptyList(),
    power = null,
    toughness = null,
    loyalty = null,
    setCode = "lea",
    setName = "Test Set",
    collectorNumber = "1",
    rarity = "common",
    releasedAt = "1993-08-05",
    frameEffects = emptyList(),
    promoTypes = emptyList(),
    lang = "en",
    imageNormal = null,
    imageArtCrop = null,
    imageBackNormal = null,
    priceUsd = null,
    priceUsdFoil = null,
    priceEur = null,
    priceEurFoil = null,
    legalityStandard = "legal",
    legalityPioneer = "legal",
    legalityModern = "legal",
    legalityCommander = "legal",
    flavorText = null,
    artist = null,
    scryfallUri = "https://scryfall.com/card/lea/1",
)

/**
 * Card Versions & Languages, Phase 1A. Covers [UpdateCollectionEntryUseCase]'s two-step
 * contract: resolve/cache the target printing via [CardRepository.getCardById], THEN delegate the
 * atomic merge to [UserCardRepository.updateEntryWithMerge] — see that method's KDoc for the
 * merge semantics themselves (tested directly against the Room-backed implementation in
 * `UserCardRepositoryImplUpdateEntryTest`, `:app` module).
 */
class UpdateCollectionEntryUseCaseTest {

    @Test
    fun invoke_onSuccess_resolvesCardThenDelegatesToRepositoryWithSameArgs() = runTest {
        val card = buildCard(scryfallId = "scry-new")
        val userCardRepository = FakeUserCardRepository(outcome = UpdateEntryOutcome.UPDATED)
        val useCase = UpdateCollectionEntryUseCase(
            cardRepository = FakeCardRepository(byId = mapOf("scry-new" to card)),
            userCardRepository = userCardRepository,
        )

        val result = useCase(
            entryId = "entry-1",
            newScryfallId = "scry-new",
            isFoil = true,
            condition = "LP",
            language = "es",
            quantity = 3,
            userId = "user-1",
        )

        assertTrue(result is DataResult.Success)
        assertEquals(UpdateEntryOutcome.UPDATED, result.data)
        assertEquals(1, userCardRepository.mergeCallCount)
        assertEquals(
            FakeUserCardRepository.MergeCall("entry-1", "scry-new", true, "LP", "es", 3, "user-1"),
            userCardRepository.lastCall,
        )
    }

    @Test
    fun invoke_whenCardLookupFails_propagatesErrorAndNeverCallsRepositoryMerge() = runTest {
        val userCardRepository = FakeUserCardRepository()
        val useCase = UpdateCollectionEntryUseCase(
            cardRepository = FakeCardRepository(errorMessage = "SCRYFALL_404"),
            userCardRepository = userCardRepository,
        )

        val result = useCase(
            entryId = "entry-1",
            newScryfallId = "missing-card",
            isFoil = false,
            condition = "NM",
            language = "en",
            quantity = 1,
        )

        assertTrue(result is DataResult.Error)
        assertEquals("SCRYFALL_404", result.message)
        assertEquals(0, userCardRepository.mergeCallCount)
    }

    @Test
    fun invoke_whenRepositoryReportsEntryNotFound_propagatesThatOutcomeAsSuccess() = runTest {
        val card = buildCard(scryfallId = "scry-new")
        val useCase = UpdateCollectionEntryUseCase(
            cardRepository = FakeCardRepository(byId = mapOf("scry-new" to card)),
            userCardRepository = FakeUserCardRepository(outcome = UpdateEntryOutcome.ENTRY_NOT_FOUND),
        )

        val result = useCase(
            entryId = "stale-entry",
            newScryfallId = "scry-new",
            isFoil = false,
            condition = "NM",
            language = "en",
            quantity = 1,
        )

        // The card-lookup step succeeded, so this is a DataResult.Success — the caller (VM) must
        // branch on the WRAPPED UpdateEntryOutcome, not treat every Success as "entry updated".
        assertTrue(result is DataResult.Success)
        assertEquals(UpdateEntryOutcome.ENTRY_NOT_FOUND, result.data)
    }

    @Test
    fun invoke_defaultUserId_isNull() = runTest {
        val card = buildCard(scryfallId = "scry-new")
        val userCardRepository = FakeUserCardRepository()
        val useCase = UpdateCollectionEntryUseCase(
            cardRepository = FakeCardRepository(byId = mapOf("scry-new" to card)),
            userCardRepository = userCardRepository,
        )

        useCase(
            entryId = "entry-1",
            newScryfallId = "scry-new",
            isFoil = false,
            condition = "NM",
            language = "en",
            quantity = 1,
        )

        assertEquals(null, userCardRepository.lastCall?.userId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Broken-image fix (2026-07-17): best-effort English-sibling warm-cache
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun invoke_nonEnglishCard_warmsEnglishSiblingViaGetCardBySetAndNumber() = runTest {
        val foreignCard = buildCard(scryfallId = "scry-es").copy(lang = "es", setCode = "lea", collectorNumber = "5")
        val cardRepository = FakeCardRepository(
            byId = mapOf("scry-es" to foreignCard),
            bySetAndNumber = mapOf(("lea" to "5") to buildCard(scryfallId = "scry-en")),
        )
        val useCase = UpdateCollectionEntryUseCase(
            cardRepository = cardRepository,
            userCardRepository = FakeUserCardRepository(),
        )

        useCase(
            entryId = "entry-1",
            newScryfallId = "scry-es",
            isFoil = false,
            condition = "NM",
            language = "es",
            quantity = 1,
        )

        assertEquals(1, cardRepository.getCardBySetAndNumberCallCount)
        assertEquals("lea" to "5", cardRepository.lastSetAndNumberCall)
    }

    @Test
    fun invoke_englishCard_neverCallsGetCardBySetAndNumber() = runTest {
        val card = buildCard(scryfallId = "scry-new") // lang = "en" per the fixture default
        val cardRepository = FakeCardRepository(byId = mapOf("scry-new" to card))
        val useCase = UpdateCollectionEntryUseCase(
            cardRepository = cardRepository,
            userCardRepository = FakeUserCardRepository(),
        )

        useCase(
            entryId = "entry-1",
            newScryfallId = "scry-new",
            isFoil = false,
            condition = "NM",
            language = "en",
            quantity = 1,
        )

        assertEquals(0, cardRepository.getCardBySetAndNumberCallCount)
    }

    @Test
    fun invoke_nonEnglishCard_whenSiblingFetchFails_stillDelegatesToRepositoryMerge() = runTest {
        // No matching entry in bySetAndNumber → FakeCardRepository.getCardBySetAndNumber returns
        // DataResult.Error, exercising the runCatching{} best-effort path in the use case.
        val foreignCard = buildCard(scryfallId = "scry-de").copy(lang = "de", setCode = "lea", collectorNumber = "9")
        val cardRepository = FakeCardRepository(byId = mapOf("scry-de" to foreignCard))
        val userCardRepository = FakeUserCardRepository(outcome = UpdateEntryOutcome.UPDATED)
        val useCase = UpdateCollectionEntryUseCase(
            cardRepository = cardRepository,
            userCardRepository = userCardRepository,
        )

        val result = useCase(
            entryId = "entry-1",
            newScryfallId = "scry-de",
            isFoil = false,
            condition = "NM",
            language = "de",
            quantity = 1,
        )

        assertTrue(result is DataResult.Success)
        assertEquals(1, userCardRepository.mergeCallCount)
    }
}
