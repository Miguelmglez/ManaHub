package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.domain.repository.CardPriceUpdate
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.PaginatedCards
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan, Workstream 4.1 (F4 fix) -- [BuildDeckFromTemplateUseCase]'s
 * Scryfall backstop fill phase (`runScryfallBackstopLoop`), gated on
 * [DeckWizardSpec.includeOutsideCollection]. Uses a dedicated, fully-configurable [CardRepository]
 * fake (rather than the shared `FakeCardRepository` from `TemplateTestFakes.kt`, whose
 * `searchWithRawQuery` is hardcoded empty by design for the "zero alphabetical searches" invariant
 * other tests in this package rely on) so pagination/relaxation can be scripted per test.
 *
 * These tests double as the "before/after gap-count" harness demonstration this workstream calls
 * for: `docs/plans/deck-wizard-rework-plan.md`'s real-collection `WizardQualityMatrixTest` harness
 * has no way to demonstrate this workstream's effect without teaching its `FixtureCardRepository`
 * to fabricate Scryfall query results (that fake's `searchWithRawQuery` is a permanent no-op stub
 * OTHER harness specs rely on for a different invariant) -- a deterministic, self-contained
 * thin-collection/niche-strategy scenario here plays the same role with concrete, reportable numbers.
 */
class BuildDeckFromTemplateUseCaseScryfallBackstopTest {

    private val dispatcher = StandardTestDispatcher()
    private val scorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)

    /** A fully-configurable [CardRepository] fake: every [searchWithRawQuery] call routes through
     * [rawQueryHandler] (default: empty, matching [FakeCardRepository]'s own "unreachable unless
     * configured" convention) so each test can script exactly what a given page/query returns. */
    private class ConfigurableCardRepository(
        private val byExactName: MutableMap<String, Card> = mutableMapOf(),
        private val rawQueryHandler: (query: String, order: String?, page: Int) -> List<Card> = { _, _, _ -> emptyList() },
    ) : CardRepository {
        var searchWithRawQueryCallCount: Int = 0
            private set
        val pagesRequested = mutableListOf<Int>()

        fun seed(card: Card) {
            byExactName[card.name] = card
        }

        override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> {
            searchWithRawQueryCallCount++
            pagesRequested += page
            return rawQueryHandler(query, order, page)
        }

        override suspend fun getCardByExactName(name: String): Result<Card> {
            val card = byExactName[name]
            return if (card != null) Result.success(card) else Result.failure(NoSuchElementException(name))
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
        override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> = error("unused")
        override suspend fun getPlayableSets(): DataResult<List<MagicSet>> = error("unused")
        override suspend fun getCardPrints(name: String): DataResult<List<Card>> = error("unused")
        override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> = error("unused")
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

    private fun withBasics(repo: ConfigurableCardRepository): ConfigurableCardRepository {
        listOf("Plains", "Island", "Swamp", "Mountain", "Forest").forEach { name ->
            repo.seed(card(id = "basic-$name", name = name, typeLine = "Basic Land — $name", colors = emptyList(), colorIdentity = emptyList()))
        }
        return repo
    }

    /** A rich pool of distinct, on-color, above-fit-floor removal spells -- mirrors the exact fixture
     * shape ("Best Removal"/"Colorless Removal": Sorcery, `oracleText = "Destroy target creature."`)
     * already proven in `BuildDeckFromTemplateUseCaseTest` to clear [BuildDeckFromTemplateUseCase
     * .CATEGORY_FILL_FIT_FLOOR] and get placed by the SAME [SuggestAddsFromCollectionUseCase] this
     * backstop phase reuses. */
    private fun backstopPool(count: Int = 70): List<Card> = (1..count).map { i ->
        card(
            id = "backstop-$i", name = "Backstop Card $i", typeLine = "Sorcery",
            colors = listOf("R"), colorIdentity = listOf("R"), oracleText = "Destroy target creature.",
        )
    }

    private fun useCase(
        cardRepository: ConfigurableCardRepository,
        candidatePoolGenerator: CandidatePoolGenerator?,
    ) = BuildDeckFromTemplateUseCase(
        deckTemplateResolver = DeckTemplateResolver(FakeCommunityAggregateRepository(), ioDispatcher = dispatcher),
        deckScorer = scorer,
        cardRepository = cardRepository,
        suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(scorer),
        ioDispatcher = dispatcher,
        candidatePoolGenerator = candidatePoolGenerator,
    )

    private fun thinSpec(includeOutsideCollection: Boolean) = DeckWizardSpec(
        format = DeckFormat.CASUAL,
        colorIdentity = setOf(ManaColor.R),
        fillLands = false, // keeps targetNonLand == targetDeckSize (60), no land-engine interaction
        includeOutsideCollection = includeOutsideCollection,
    )

    private suspend fun runToCompletion(useCase: BuildDeckFromTemplateUseCase, spec: DeckWizardSpec): TemplateBuildResult {
        val events = useCase.invoke(spec, emptyList()).toList()
        val complete = events.filterIsInstance<TemplateBuildProgress.Complete>().singleOrNull()
        assertIs<TemplateBuildProgress.Complete>(complete ?: events.last(), "build did not complete: ${events.lastOrNull()}")
        return complete!!.result
    }

    @Test
    fun `includeOutsideCollection = false never invokes the generator, even with a thin collection`() = runTest(dispatcher) {
        val repo = withBasics(ConfigurableCardRepository(rawQueryHandler = { _, _, _ -> backstopPool() }))
        val generator = CandidatePoolGenerator(repo, dispatcher)
        val result = runToCompletion(useCase(repo, generator), thinSpec(includeOutsideCollection = false))

        assertEquals(0, repo.searchWithRawQueryCallCount, "the backstop must never call searchWithRawQuery when the toggle is off")
        assertTrue(result.gaps.isNotEmpty(), "an empty collection with the toggle off must still declare an honest gap (BEFORE numbers)")
    }

    @Test
    fun `includeOutsideCollection = true fills the shortfall from Scryfall candidates instead of declaring a gap`() = runTest(dispatcher) {
        val repo = withBasics(ConfigurableCardRepository(rawQueryHandler = { _, _, _ -> backstopPool() }))
        val generator = CandidatePoolGenerator(repo, dispatcher)
        val result = runToCompletion(useCase(repo, generator), thinSpec(includeOutsideCollection = true))

        assertTrue(result.gaps.isEmpty(), "a rich Scryfall backstop pool must fill the full 60-card mainboard, leaving zero gaps (AFTER numbers)")
        assertEquals(60, result.deckCards.sumOf { it.quantity })
        val placed = result.deckCards.filter { it.card.scryfallId.startsWith("backstop-") }
        assertTrue(placed.isNotEmpty(), "at least one Scryfall backstop candidate must be placed")
        assertTrue(placed.all { !it.isOwned }, "every Scryfall backstop entry must be recorded as NOT owned")
    }

    @Test
    fun `an empty page 1 pages forward to page 2 before giving up`() = runTest(dispatcher) {
        val repo = withBasics(
            ConfigurableCardRepository(rawQueryHandler = { _, _, page -> if (page == 1) emptyList() else backstopPool() }),
        )
        val generator = CandidatePoolGenerator(repo, dispatcher)
        val result = runToCompletion(useCase(repo, generator), thinSpec(includeOutsideCollection = true))

        assertTrue(repo.pagesRequested.contains(2), "page 1 being empty must page forward to page 2")
        assertTrue(result.gaps.isEmpty(), "page 2's candidates must fill the deck once found")
    }

    @Test
    fun `every page and the relaxed round coming up empty terminates the loop with an honest gap`() = runTest(dispatcher) {
        val repo = withBasics(ConfigurableCardRepository(rawQueryHandler = { _, _, _ -> emptyList() }))
        val generator = CandidatePoolGenerator(repo, dispatcher)
        val result = runToCompletion(useCase(repo, generator), thinSpec(includeOutsideCollection = true))

        assertTrue(result.gaps.isNotEmpty(), "an exhausted backstop (every page + the relaxed round empty) must still surface an honest gap, never hang")
        assertEquals(
            60 - result.deckCards.sumOf { it.quantity },
            result.gaps.sumOf { it.missingCount },
            "gaps.sumOf { missingCount } must reconcile EXACTLY with the true shortfall even when the backstop finds nothing (D3 invariant untouched)",
        )
    }

    @Test
    fun `every backstop-placed card is traceable to the injected CardRepository, never a bypass`() = runTest(dispatcher) {
        val repo = withBasics(ConfigurableCardRepository(rawQueryHandler = { _, _, _ -> backstopPool() }))
        val generator = CandidatePoolGenerator(repo, dispatcher)
        val result = runToCompletion(useCase(repo, generator), thinSpec(includeOutsideCollection = true))

        val backstopEntries = result.deckCards.filter { it.card.scryfallId.startsWith("backstop-") }
        assertTrue(backstopEntries.isNotEmpty())
        assertTrue(
            backstopEntries.all { it.card.scryfallId.substringAfter("backstop-").toIntOrNull() != null },
            "every backstop-sourced card must be one this test's fake CardRepository actually returned via searchWithRawQuery",
        )
        assertTrue(repo.searchWithRawQueryCallCount > 0, "the backstop must have gone through searchWithRawQuery to source these cards")
    }
}
