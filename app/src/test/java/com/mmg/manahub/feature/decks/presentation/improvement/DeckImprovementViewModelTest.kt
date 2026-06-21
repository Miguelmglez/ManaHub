package com.mmg.manahub.feature.decks.presentation.improvement

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
import com.mmg.manahub.feature.decks.domain.usecase.BudgetOptimizer
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 7 unit tests for [DeckImprovementViewModel].
 *
 * Wiring is exercised against REAL use cases / engine (deterministic fixed PowerResolver), with only
 * the repositories and the network surface ([CardRepository.searchWithRawQuery]) mocked. This lets the
 * tests assert end-to-end behaviour:
 *  - (B4) the inferred strategy seed reaches the evaluation profile,
 *  - (E4) a cut→add round-trip with an unchanged gap set issues ZERO Scryfall calls,
 *  - (E6) an unresolved mainboard slot surfaces [DeckWarning.UnresolvedCards].
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class DeckImprovementViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val deckRepository = mockk<DeckRepository>(relaxed = true)
    private val cardRepository = mockk<CardRepository>()
    private val userCardRepository = mockk<UserCardRepository>()
    private val wishlistRepository = mockk<com.mmg.manahub.feature.trades.domain.repository.WishlistRepository>()
    private val userPreferences = mockk<UserPreferencesDataStore>()

    // Real engine + use cases (the wiring under test).
    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.6f))
    private val eventBus = ProgressionEventBus()
    private val evaluateDeckUseCase = EvaluateDeckUseCase(scorer, eventBus, dispatcher)
    private val inferDeckIdentityUseCase = InferDeckIdentityUseCase()
    private val suggestCutsUseCase = SuggestCutsUseCase(scorer, dispatcher)
    private val candidatePoolGenerator = CandidatePoolGenerator(cardRepository, dispatcher)
    private val budgetOptimizer = BudgetOptimizer()
    private val suggestAddsWithBudgetUseCase = SuggestAddsWithBudgetUseCase(
        deckScorer = scorer,
        candidatePoolGenerator = candidatePoolGenerator,
        budgetOptimizer = budgetOptimizer,
        cardRepository = cardRepository,
        ioDispatcher = dispatcher,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        // F2 — no debug override persisted → NONE maps to default ScoreWeights() (zero behavior change).
        every { userPreferences.observeScoreWeightOverrides() } returns flowOf(ScoreWeightOverrides.NONE)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val DECK_ID = "deck-1"

    /** A tribal Elf commander carrying TRIBAL identity tags so inference yields a non-empty seed. */
    private val commander = card(
        id = "cmd",
        name = "Elf Lord",
        typeLine = "Legendary Creature — Elf",
        colorIdentity = listOf("G"),
        colors = listOf("G"),
        tags = listOf(CardTag.TRIBAL, CardTag.TOKENS),
    )

    /** A handful of resolvable mainboard spells; one removal so a SPOT_REMOVAL gap can open/close. */
    private fun resolvableMainboard(): List<Card> = buildList {
        add(commander)
        add(card(id = "removal-1", name = "Naturalize", colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL)))
        repeat(8) { i ->
            add(card(id = "elf-$i", name = "Elf $i", typeLine = "Creature — Elf",
                colorIdentity = listOf("G"), colors = listOf("G"), tags = listOf(CardTag.TRIBAL)))
        }
    }

    private fun deckWithCards(slots: List<DeckSlot>, commanderId: String? = "cmd") = DeckWithCards(
        deck = Deck(id = DECK_ID, name = "Elves", format = "commander", commanderCardId = commanderId),
        mainboard = slots,
        sideboard = emptyList(),
    )

    private fun userCardWith(card: Card) = UserCardWithCard(
        userCard = UserCard(id = "uc-${card.scryfallId}", scryfallId = card.scryfallId),
        card = card,
    )

    private fun createViewModel(): DeckImprovementViewModel = DeckImprovementViewModel(
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        userCardRepository = userCardRepository,
        evaluateDeckUseCase = evaluateDeckUseCase,
        inferDeckIdentityUseCase = inferDeckIdentityUseCase,
        suggestCutsUseCase = suggestCutsUseCase,
        suggestAddsWithBudgetUseCase = suggestAddsWithBudgetUseCase,
        wishlistRepository = wishlistRepository,
        userPreferences = userPreferences,
        savedStateHandle = SavedStateHandle(mapOf("deckId" to DECK_ID)),
    )

    /** Wires up the common happy-path mocks for a fully-resolvable deck. */
    private fun stubResolvableDeck() {
        val cards = resolvableMainboard()
        val slots = cards.map { DeckSlot(scryfallId = it.scryfallId, quantity = 1) }
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards(slots))
        cards.forEach { c ->
            coEvery { cardRepository.getCardById(c.scryfallId) } returns DataResult.Success(c)
        }
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())
        // External pool: return an empty list (no Scryfall hit needed for these assertions).
        coEvery { cardRepository.searchWithRawQuery(any()) } returns emptyList()
    }

    // ── (B4) inferred seeds reach the evaluation ────────────────────────────────

    @Test
    fun `inferred strategy seed reaches the evaluation profile`() = runTest(dispatcher) {
        stubResolvableDeck()
        val vm = createViewModel()
        advanceUntilIdle()

        val seedTags = vm.uiState.value.health!!.profile.seedTags
        assertTrue("seedTags must be non-empty (inference seeded the profile)", seedTags.isNotEmpty())
        // The Elf commander's TRIBAL identity must be part of the seed.
        assertTrue(
            "TRIBAL seed expected from the commander",
            seedTags.any { it.key == CardTag.TRIBAL.key },
        )
    }

    // ── (E4) cut→add round-trip with unchanged gaps does ZERO Scryfall calls ─────

    @Test
    fun `cut then add with unchanged gap set issues zero external pool fetches`() = runTest(dispatcher) {
        stubResolvableDeck()
        val vm = createViewModel()
        advanceUntilIdle()

        // The initial full analysis may fetch the external pool exactly once (one query per gap role).
        // We only care that the cut→add round-trip below adds NO further Scryfall calls.
        io.mockk.clearMocks(cardRepository, answers = false, recordedCalls = true, verificationMarks = true)

        // Cut a plain Elf and re-add the same one: the queryable gap set (SPOT_REMOVAL is unchanged,
        // PAYOFF/SYNERGY/THREAT have no query fragment) does not change, so the cached pool is reused.
        vm.onCut("elf-0", "Elf 0")
        advanceUntilIdle()
        vm.onAdd("elf-0", "Elf 0")
        advanceUntilIdle()

        // ACCEPTANCE: not a single external Scryfall query during the incremental round-trip.
        coVerify(exactly = 0) { cardRepository.searchWithRawQuery(any()) }
    }

    // ── (E6) unresolved mainboard slots produce a warning ───────────────────────

    @Test
    fun `unresolved mainboard slots produce an UnresolvedCards warning`() = runTest(dispatcher) {
        val cards = resolvableMainboard()
        // Add a slot whose card cannot be resolved (offline / missing from cache).
        val slots = cards.map { DeckSlot(scryfallId = it.scryfallId, quantity = 1) } +
            DeckSlot(scryfallId = "missing-1", quantity = 2)

        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(deckWithCards(slots))
        cards.forEach { c ->
            coEvery { cardRepository.getCardById(c.scryfallId) } returns DataResult.Success(c)
        }
        coEvery { cardRepository.getCardById("missing-1") } returns DataResult.Error("not found")
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())
        coEvery { cardRepository.searchWithRawQuery(any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val warnings = vm.uiState.value.health!!.evaluation.warnings
        val unresolved = warnings.filterIsInstance<DeckWarning.UnresolvedCards>().singleOrNull()
        assertTrue("UnresolvedCards warning expected", unresolved != null)
        assertEquals(2, unresolved!!.count) // the 2-copy missing slot
    }
}
