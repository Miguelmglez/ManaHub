package com.mmg.manahub.feature.decks.domain.orchestrator

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck Wizard & Engine Rework plan, Workstream 8.4 -- direct unit tests for
 * [DeckDoctorOrchestrator]'s staged-progress machinery ([DoctorAnalysisStage]). No test file for
 * this class existed before (its behavior was only exercised indirectly through
 * `DeckStudioViewModelTest`); this file goes straight at the orchestrator with lightweight fakes
 * so the staged-emission and cancel-safety guarantees can be asserted precisely, without fighting
 * Compose/StateFlow-collector plumbing.
 *
 * Deck Analysis Category Sections rework (W0, D3/D5): [DeckDoctorOrchestrator]'s old Motor A
 * ADD-ranking pipeline this file used to exercise heavily (`recomputeAddsInternal`, its
 * `suggestCutsUseCase`/`suggestAddsFromCollectionUseCase` ctor params, `BudgetConstraints`, and
 * the `EVALUATING_COLLECTION`/`SEARCHING_CARD_POOL`/`RANKING_SUGGESTIONS` stages) was DELETED
 * end-to-end. `SuggestAddsFromCollectionUseCase`/`SuggestCutsUseCase` (the CLASSES) still exist --
 * the orchestrator simply no longer takes or calls them; `SuggestCutsUseCase` in particular is
 * kept alive only for `harness/HarnessDoctorPipeline.kt`'s unrelated Wizard Quality Campaign QA
 * gate, not by any orchestrator/ViewModel consumer any more.
 * [DeckDoctorOrchestrator.loadAnalysis] now only computes Health (score/pillars/findings) and
 * clears [DeckDoctorState.stage] to `null` UNCONDITIONALLY right after, so the staged-progress
 * surface this file can still test shrinks to just [DoctorAnalysisStage.READING_DECK_PLAN] -> null
 * (Motor B / "Decks like yours" is intentionally left unwired in this fixture --
 * [DoctorAnalysisStage.SEARCHING_COMMUNITY] never fires here). The old multi-generation
 * Motor-A-staleness race tests (superseded pass mid-flight, etc.) tested a mechanism that no
 * longer exists and were removed rather than left asserting on dead code; a Motor-B-flavoured
 * equivalent (wiring `findSimilarDecksUseCase` + `isCommunityEngineEnabled`) is follow-up debt for
 * a future test pass, not reproduced here.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class DeckDoctorOrchestratorTest {

    private val dispatcher = StandardTestDispatcher()

    private val deckRepository = mockk<DeckRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>()
    private val wishlistRepository = mockk<WishlistRepository>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.6f))
    private val evaluateDeckUseCase = EvaluateDeckUseCase(scorer, ProgressionEventBus(), dispatcher)
    private val inferDeckIdentityUseCase = InferDeckIdentityUseCase()

    private val landCard = card(id = "land-1", name = "Forest", typeLine = "Basic Land — Forest", colors = emptyList(), colorIdentity = listOf("G"))

    private val slots = listOf(DeckSlot(landCard.scryfallId, 10))

    private fun stubDeck() {
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            DeckWithCards(
                deck = Deck(id = DECK_ID, name = "Test deck", format = "casual", commanderCardId = null),
                mainboard = slots,
                sideboard = emptyList(),
            )
        )
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())
    }

    private fun createOrchestrator(scope: kotlinx.coroutines.CoroutineScope) = DeckDoctorOrchestrator(
        scope = scope,
        deckRepository = deckRepository,
        userCardRepository = userCardRepository,
        wishlistRepository = wishlistRepository,
        evaluateDeckUseCase = evaluateDeckUseCase,
        inferDeckIdentityUseCase = inferDeckIdentityUseCase,
        crashReporter = crashReporter,
        resolveCard = { id -> if (id == landCard.scryfallId) landCard else null },
        weightsProvider = { ScoreWeightOverrides.NONE },
        // Motor B ("Decks like yours") is left null/defaulted -- SEARCHING_COMMUNITY never fires
        // in this configuration, keeping these tests focused on the READING_DECK_PLAN -> null
        // transition Health alone now drives.
    )

    companion object {
        private const val DECK_ID = "deck-1"
    }

    @Test
    fun `full analysis clears stage to null once Health is computed`() = runTest(dispatcher) {
        // Regression coverage for the real device-reproduced bug (2026-08-20): the Suggestions
        // tab's staged-progress gate in DeckStudioScreen is `doctorStage != null`, so the
        // meaningful assertion is on [DeckDoctorState.stage] itself, not `isSuggestionsLoading`.
        stubDeck()
        val orchestrator = createOrchestrator(this)

        orchestrator.loadAnalysis(DECK_ID)
        advanceUntilIdle()

        assertEquals(
            "the only stage this pass shows now (Motor A is gone) is READING_DECK_PLAN, folded " +
                "into completedStages once Health is done",
            listOf(DoctorAnalysisStage.READING_DECK_PLAN),
            orchestrator.state.value.completedStages,
        )
        assertNull("stage must clear to null once the full pass settles", orchestrator.state.value.stage)
        assertTrue(orchestrator.state.value.isLoaded)
        assertNotNull("Health must have been computed", orchestrator.state.value.health)
    }

    @Test
    fun `recomputeIncremental (add-cut) never touches doctorStage`() = runTest(dispatcher) {
        stubDeck()
        val orchestrator = createOrchestrator(this)

        orchestrator.loadAnalysis(DECK_ID)
        advanceUntilIdle()
        assertNull("stage must be null once the full pass settles", orchestrator.state.value.stage)

        orchestrator.onCutCard(landCard.scryfallId)
        advanceUntilIdle()

        assertNull("an incremental recompute must never set a stage", orchestrator.state.value.stage)
    }

    @Test
    fun `a newer pass supersedes an older one without getting stuck on a stage`() = runTest(dispatcher) {
        stubDeck()
        val orchestrator = createOrchestrator(this)

        orchestrator.loadAnalysis(DECK_ID)
        orchestrator.loadAnalysis(DECK_ID)
        advanceUntilIdle()

        assertNull("the doctor must never be left stuck displaying a stale stage", orchestrator.state.value.stage)
        assertTrue(orchestrator.state.value.isLoaded)
    }
}
