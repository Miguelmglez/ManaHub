package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** No-op [CrashReporter] fake — every test in this file asserts on OUTCOMES, not telemetry. */
private object NoOpCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * Minimal, hand-written [CardRepository] fake resolving by exact name from [byName], plus a
 * batch-by-id path ([byId]) exercising the known-scryfallId fast path (Bug fix, 2026-07-22).
 */
private class FakeImportCardRepository(
    private val byName: Map<String, Card>,
    private val byId: Map<String, Card> = emptyMap(),
) : CardRepository {
    /** Ids passed to [warmCacheForIds], in call order — one call per invocation (not flattened). */
    val warmCacheCalls = mutableListOf<List<String>>()

    override suspend fun searchCardByName(query: String): DataResult<Card> =
        byName[query]?.let { DataResult.Success(it) } ?: DataResult.Error("not found")
    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
    override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<com.mmg.manahub.core.model.PaginatedCards> = error("unused")
    override suspend fun getCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
    override suspend fun backfillMissingStrategyTags(limit: Int) = error("unused")
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
    override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> = error("unused")
    override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> =
        scryfallIds.mapNotNull { byId[it] }
    override fun observeCard(scryfallId: String): Flow<Card?> = flowOf(null)
    override suspend fun updatePrices(scryfallId: String, priceUsd: Double?, priceUsdFoil: Double?, priceEur: Double?, priceEurFoil: Double?, updatedAt: Long) = error("unused")
    override suspend fun updatePricesBatch(updates: List<com.mmg.manahub.core.domain.repository.CardPriceUpdate>) = error("unused")
    override suspend fun evictStaleCache() = error("unused")
    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) = error("unused")
    override suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>) = error("unused")
    override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun warmCacheForIds(scryfallIds: List<String>) {
        warmCacheCalls += scryfallIds
    }
}

/** Minimal, hand-written [DeckRepository] fake — an in-memory single-deck store, enough for
 * [ImportDeckCardsUseCase]'s write path (create/add/attribute/updateDeck). */
private class FakeDeckRepository(private var nextDeckId: String = "created-deck-id") : DeckRepository {
    val createdDecks = mutableListOf<Triple<String, String, String>>() // name, description, format
    val addedCards = mutableListOf<AddCall>()
    /** One entry per [replaceAllCards] call — (deckId, slots). */
    val replaceAllCardsCalls = mutableListOf<Pair<String, List<Triple<String, Int, Boolean>>>>()
    var attribution: Attribution? = null
    private val deckFlow = MutableStateFlow<Deck?>(null)

    data class AddCall(val deckId: String, val scryfallId: String, val quantity: Int, val isSideboard: Boolean)
    data class Attribution(val deckId: String, val sourceUrl: String?, val sourceAuthor: String?, val sourceService: String?)

    override fun observeAllDecks(): Flow<List<Deck>> = error("unused")
    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> = error("unused")
    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> = error("unused")
    override fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?> = kotlinx.coroutines.flow.flow {
        emit(deckFlow.value?.let { DeckWithCards(deck = it, mainboard = emptyList(), sideboard = emptyList()) })
    }
    override suspend fun createDeck(name: String, description: String, format: String): String {
        createdDecks.add(Triple(name, description, format))
        deckFlow.value = Deck(id = nextDeckId, name = name, format = format)
        return nextDeckId
    }
    var updateDeckCallCount = 0
    /** Test hook to seed an already-existing deck (simulating an EXISTING live deck, distinct
     * from a deck this repository itself created). */
    fun seedExistingDeck(deck: Deck) { deckFlow.value = deck }
    override suspend fun updateDeck(deck: Deck) { updateDeckCallCount++; deckFlow.value = deck }
    override suspend fun deleteDeck(deckId: String) = Unit
    override suspend fun addCardToDeck(deckId: String, scryfallId: String, quantity: Int, isSideboard: Boolean, source: DeckCardSource) {
        addedCards.add(AddCall(deckId, scryfallId, quantity, isSideboard))
    }
    override suspend fun removeCardFromDeck(deckId: String, scryfallId: String, isSideboard: Boolean) = Unit
    override suspend fun moveCardQuantity(deckId: String, scryfallId: String, fromSideboard: Boolean, quantity: Int) = Unit
    override suspend fun clearDeck(deckId: String) = Unit
    override suspend fun updateDeckAttribution(deckId: String, sourceUrl: String?, sourceAuthor: String?, sourceService: String?, importedAt: Long?) {
        attribution = Attribution(deckId, sourceUrl, sourceAuthor, sourceService)
    }
    override suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>) {
        replaceAllCardsCalls += deckId to slots
    }
    override suspend fun updateArchetypeOverride(deckId: String, archetypeOverride: String?, themesOverride: List<String>) = Unit
    override suspend fun updateTribeOverride(deckId: String, tribeOverride: String?) = Unit
    override suspend fun updateStrategyLocked(deckId: String, locked: Boolean) = Unit
}

/**
 * Deck Doctor Community/Archetype plan, Phase 6 — commonTest coverage for [ImportDeckCardsUseCase]
 * focused on what the two thin-adapter tests (`ImportDeckUseCaseTest`/`ImportCommunityDeckUseCaseTest`,
 * both `app/src/test`, MockK) do NOT exercise: the [ImportSource.DeckstatsUrl] path and the
 * commander-scoping fix (auto-assignment only on NEW-deck creation, never on an existing
 * `targetDeckId`).
 */
class ImportDeckCardsUseCaseTest {

    private val bolt = card(id = "bolt-1", name = "Lightning Bolt", colors = listOf("R"), colorIdentity = listOf("R"))
    private val atraxa = card(id = "atraxa-1", name = "Atraxa", typeLine = "Legendary Creature — Angel", colors = listOf("W"), colorIdentity = listOf("W", "U", "B", "G"))

    @Test
    fun deckstatsUrlWithNoFetcherWiredReturnsError() = runTest {
        val useCase = ImportDeckCardsUseCase(
            deckRepository = FakeDeckRepository(),
            cardRepository = FakeImportCardRepository(emptyMap()),
            crashReporter = NoOpCrashReporter,
            deckstatsFetcher = null,
        )

        val outcome = useCase(source = ImportSource.DeckstatsUrl("https://deckstats.net/decks/1/2-my-deck"))

        assertTrue(outcome is ImportOutcome.Error)
    }

    @Test
    fun deckstatsUrlWhereFetcherReturnsNullSurfacesOneClearError() = runTest {
        val useCase = ImportDeckCardsUseCase(
            deckRepository = FakeDeckRepository(),
            cardRepository = FakeImportCardRepository(emptyMap()),
            crashReporter = NoOpCrashReporter,
            deckstatsFetcher = DeckstatsFetcher { null },
        )

        val outcome = useCase(source = ImportSource.DeckstatsUrl("https://deckstats.net/decks/1/2-my-deck"))

        assertTrue(outcome is ImportOutcome.Error)
    }

    @Test
    fun deckstatsUrlHappyPathCreatesADeckAndWritesResolvedCards() = runTest {
        val deckRepository = FakeDeckRepository()
        val useCase = ImportDeckCardsUseCase(
            deckRepository = deckRepository,
            cardRepository = FakeImportCardRepository(mapOf("Lightning Bolt" to bolt)),
            crashReporter = NoOpCrashReporter,
            deckstatsFetcher = DeckstatsFetcher { url ->
                assertEquals("https://deckstats.net/decks/1/2-my-deck", url)
                DeckstatsDeck(name = "My Deckstats Deck", cards = listOf(DeckstatsCard("Lightning Bolt", 4, isSideboard = false, isCommander = false)))
            },
        )

        val outcome = useCase(source = ImportSource.DeckstatsUrl("https://deckstats.net/decks/1/2-my-deck"))

        val success = outcome as ImportOutcome.Success
        assertEquals(1, success.resolvedCount)
        assertEquals(0, success.failedCount)
        // Bug fix, 2026-07-22 (Resolve-then-write): a brand-new deck (targetDeckId == null) writes
        // its resolved cards via ONE atomic replaceAllCards call, never per-card addCardToDeck.
        assertEquals(0, deckRepository.addedCards.size)
        assertEquals(1, deckRepository.replaceAllCardsCalls.size)
        assertEquals(listOf(Triple(bolt.scryfallId, 4, false)), deckRepository.replaceAllCardsCalls.single().second)
        assertEquals("deckstats", deckRepository.attribution?.sourceService)
    }

    @Test
    fun commanderIsAutoAssignedOnlyWhenCreatingANewDeckNeverOnAnExistingTargetDeckId() = runTest {
        val deckRepository = FakeDeckRepository()
        deckRepository.seedExistingDeck(Deck(id = "existing-deck-id", name = "Existing", format = "commander"))
        val cardRepository = FakeImportCardRepository(mapOf("Atraxa" to atraxa))
        val useCase = ImportDeckCardsUseCase(deckRepository, cardRepository, NoOpCrashReporter)

        // Importing INTO an existing deck (targetDeckId != null) — commander must NOT be
        // auto-set, mirroring the original ImportDeckUseCase contract exactly (see the
        // "targetDeckId == null" gate in ImportDeckCardsUseCase.writeImport).
        val commanderSource = ImportSource.PastedText("Commander\n1 Atraxa")
        useCase(source = commanderSource, targetDeckId = "existing-deck-id")

        assertEquals(0, deckRepository.createdDecks.size)
        assertEquals(0, deckRepository.updateDeckCallCount)
        // An EXISTING deck (targetDeckId != null) keeps the original incremental per-card merge —
        // never the atomic replaceAllCards path (that would wipe whatever the deck already had).
        assertEquals(1, deckRepository.addedCards.size)
        assertEquals(0, deckRepository.replaceAllCardsCalls.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Known-scryfallId batch fast path (Bug fix, 2026-07-22)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun knownScryfallIdFoundInWarmedCacheNeverCallsSearchCardByName() = runTest {
        val deckRepository = FakeDeckRepository()
        val cardRepository = FakeImportCardRepository(byName = emptyMap(), byId = mapOf(bolt.scryfallId to bolt))
        val useCase = ImportDeckCardsUseCase(deckRepository, cardRepository, NoOpCrashReporter)

        val deck = com.mmg.manahub.core.model.CommunityDeck(
            archidektId = 1, name = "D", description = "", format = "modern",
            owner = com.mmg.manahub.core.model.CommunityDeckOwner(1, "u", ""),
            viewCount = 0, createdAt = "", updatedAt = "",
            cards = listOf(
                com.mmg.manahub.core.model.CommunityDeckCard(
                    name = "Lightning Bolt", quantity = 4, categories = emptyList(),
                    oracleId = "oracle-bolt", scryfallId = bolt.scryfallId,
                ),
            ),
            sourceUrl = "https://archidekt.com/decks/1",
        )

        val outcome = useCase(source = ImportSource.FromCommunityDeck(deck), targetDeckId = null)

        val success = outcome as ImportOutcome.Success
        assertEquals(1, success.resolvedCount)
        assertEquals(0, success.failedCount)
        assertEquals(listOf(listOf(bolt.scryfallId)), cardRepository.warmCacheCalls)
        assertEquals(1, deckRepository.replaceAllCardsCalls.size)
        assertEquals(listOf(Triple(bolt.scryfallId, 4, false)), deckRepository.replaceAllCardsCalls.single().second)
    }

    @Test
    fun knownScryfallIdMissingFromWarmedCacheFallsBackToSearchCardByName() = runTest {
        val deckRepository = FakeDeckRepository()
        // The known id is a dead/retired printing id that the batch fetch can't resolve (absent
        // from `byId`); the card's ORACLE name is still resolvable via a fuzzy fallback search.
        val cardRepository = FakeImportCardRepository(byName = mapOf("Lightning Bolt" to bolt), byId = emptyMap())
        val useCase = ImportDeckCardsUseCase(deckRepository, cardRepository, NoOpCrashReporter)

        val deck = com.mmg.manahub.core.model.CommunityDeck(
            archidektId = 1, name = "D", description = "", format = "modern",
            owner = com.mmg.manahub.core.model.CommunityDeckOwner(1, "u", ""),
            viewCount = 0, createdAt = "", updatedAt = "",
            cards = listOf(
                com.mmg.manahub.core.model.CommunityDeckCard(
                    name = "Lightning Bolt", quantity = 1, categories = emptyList(),
                    oracleId = "oracle-bolt", scryfallId = "dead-printing-id",
                ),
            ),
            sourceUrl = "https://archidekt.com/decks/1",
        )

        val outcome = useCase(source = ImportSource.FromCommunityDeck(deck), targetDeckId = null)

        val success = outcome as ImportOutcome.Success
        assertEquals(1, success.resolvedCount)
        assertEquals(0, success.failedCount)
        assertEquals(listOf(listOf("dead-printing-id")), cardRepository.warmCacheCalls)
        assertEquals(listOf(Triple(bolt.scryfallId, 1, false)), deckRepository.replaceAllCardsCalls.single().second)
    }

    @Test
    fun pastedTextSourceNeverCallsWarmCacheForIds() = runTest {
        val deckRepository = FakeDeckRepository()
        val cardRepository = FakeImportCardRepository(mapOf("Lightning Bolt" to bolt))
        val useCase = ImportDeckCardsUseCase(deckRepository, cardRepository, NoOpCrashReporter)

        useCase(source = ImportSource.PastedText("4 Lightning Bolt"), targetDeckId = "existing-deck-id")

        // PastedText never carries a known scryfallId — zero regression: the known-id batch path
        // must never engage for a source that has no id to give it.
        assertTrue(cardRepository.warmCacheCalls.isEmpty())
        assertEquals(1, deckRepository.addedCards.size)
    }

    @Test
    fun deckstatsUrlSourceNeverCallsWarmCacheForIds() = runTest {
        val deckRepository = FakeDeckRepository()
        val cardRepository = FakeImportCardRepository(mapOf("Lightning Bolt" to bolt))
        val useCase = ImportDeckCardsUseCase(
            deckRepository = deckRepository,
            cardRepository = cardRepository,
            crashReporter = NoOpCrashReporter,
            deckstatsFetcher = DeckstatsFetcher {
                DeckstatsDeck(name = "D", cards = listOf(DeckstatsCard("Lightning Bolt", 4, isSideboard = false, isCommander = false)))
            },
        )

        useCase(source = ImportSource.DeckstatsUrl("https://deckstats.net/decks/1/2-d"))

        assertTrue(cardRepository.warmCacheCalls.isEmpty())
    }

    @Test
    fun commanderIsAutoAssignedWhenCreatingANewDeck() = runTest {
        val deckRepository = FakeDeckRepository()
        val cardRepository = FakeImportCardRepository(mapOf("Atraxa" to atraxa))
        val useCase = ImportDeckCardsUseCase(deckRepository, cardRepository, NoOpCrashReporter)

        useCase(
            source = ImportSource.FromCommunityDeck(
                com.mmg.manahub.core.model.CommunityDeck(
                    archidektId = 1, name = "D", description = "", format = "commander",
                    owner = com.mmg.manahub.core.model.CommunityDeckOwner(1, "u", ""),
                    viewCount = 0, createdAt = "", updatedAt = "",
                    cards = listOf(com.mmg.manahub.core.model.CommunityDeckCard("Atraxa", 1, listOf("Commander"), "oracle-atraxa")),
                    sourceUrl = "https://archidekt.com/decks/1",
                )
            ),
            targetDeckId = null,
        )

        assertEquals(1, deckRepository.updateDeckCallCount)
    }
}
