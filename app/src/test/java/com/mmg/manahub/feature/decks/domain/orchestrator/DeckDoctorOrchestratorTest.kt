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
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck Wizard & Engine Rework plan, Workstream 8.4 -- direct unit tests for
 * [DeckDoctorOrchestrator]'s staged-progress machinery ([DoctorAnalysisStage]). No test file for
 * this class existed before (its behavior was only exercised indirectly through
 * `DeckStudioViewModelTest`); this file goes straight at the orchestrator with lightweight fakes
 * so the staged-emission ORDER and CANCEL-SAFETY guarantees can be asserted precisely, without
 * fighting Compose/StateFlow-collector plumbing.
 *
 * Real engine instances ([DeckScorer]/[EvaluateDeckUseCase]/[SuggestCutsUseCase]/
 * [InferDeckIdentityUseCase]) are used exactly like `DeckStudioViewModelTest`'s
 * `createVmWithMockedAdds()` pattern -- only [SuggestAddsFromCollectionUseCase] (Motor A) is
 * mocked, since that is the one call this workstream needs precise timing control over.
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
    private val suggestCutsUseCase = SuggestCutsUseCase(scorer, dispatcher)
    private val inferDeckIdentityUseCase = InferDeckIdentityUseCase()
    private val mockSuggestAddsFromCollectionUseCase = mockk<SuggestAddsFromCollectionUseCase>()

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
        suggestCutsUseCase = suggestCutsUseCase,
        suggestAddsFromCollectionUseCase = mockSuggestAddsFromCollectionUseCase,
        inferDeckIdentityUseCase = inferDeckIdentityUseCase,
        crashReporter = crashReporter,
        resolveCard = { id -> if (id == landCard.scryfallId) landCard else null },
        weightsProvider = { ScoreWeightOverrides.NONE },
        // Motor B and the Scryfall backstop are left null/defaulted -- SEARCHING_COMMUNITY never
        // fires in this configuration, keeping the emission-order test fully deterministic (Motor
        // B's concurrency is intentionally NOT part of this test, per the plan's own "don't force
        // it fully sequential" caution).
    )

    companion object {
        private const val DECK_ID = "deck-1"
    }

    @Test
    fun `full analysis emits the documented stage sequence and clears to null`() = runTest(dispatcher) {
        // Note on approach: DeckDoctorState.stage is exposed via a MutableStateFlow, which
        // CONFLATES rapid-fire updates -- a collector only sees the LATEST value each time it is
        // resumed, not every intermediate one, so a slow-vs-fast collector race would make
        // asserting on collected snapshots flaky here (every step in this scenario is synchronous/
        // instant -- no real suspension exists between stages to force an interleave). The
        // reliable, deterministic proof of "fired in the documented order" is
        // [DeckDoctorState.completedStages] itself: it is appended to synchronously, in call
        // order, by the exact same code path that drives the UI's checklist -- so asserting its
        // exact final order IS asserting the real transition sequence, independent of any
        // collector's scheduling.
        stubDeck()
        coEvery { mockSuggestAddsFromCollectionUseCase(any(), any(), any(), any(), any(), any()) } returns emptyList()
        val orchestrator = createOrchestrator(this)

        orchestrator.loadAnalysis(DECK_ID, BudgetConstraints())
        advanceUntilIdle()

        assertEquals(
            listOf(
                DoctorAnalysisStage.READING_DECK_PLAN,
                DoctorAnalysisStage.EVALUATING_COLLECTION,
                DoctorAnalysisStage.SEARCHING_CARD_POOL,
                DoctorAnalysisStage.RANKING_SUGGESTIONS,
            ),
            orchestrator.state.value.completedStages,
        )
        assertNull("stage must clear to null once the full pass settles", orchestrator.state.value.stage)
        assertTrue(orchestrator.state.value.isLoaded)
    }

    @Test
    fun `recomputeIncremental (add-cut) never touches doctorStage`() = runTest(dispatcher) {
        stubDeck()
        coEvery { mockSuggestAddsFromCollectionUseCase(any(), any(), any(), any(), any(), any()) } returns emptyList()
        val orchestrator = createOrchestrator(this)

        orchestrator.loadAnalysis(DECK_ID, BudgetConstraints())
        advanceUntilIdle()
        assertNull("stage must be null once the full pass settles", orchestrator.state.value.stage)

        // Act -- an incremental add (cachedMainboardQuantity / onAddCard flow) must stay
        // instant/unstaged: this only works because the card is already resolvable via
        // resolveCard, mirroring the plan's explicit "recomputeIncremental never stages" rule.
        orchestrator.onCutCard(landCard.scryfallId, BudgetConstraints())
        advanceUntilIdle()

        assertNull("an incremental recompute must never set a stage", orchestrator.state.value.stage)
    }

    @Test
    fun `a stale pass never leaves the doctor stuck on a stage after a newer pass supersedes it`() = runTest(dispatcher) {
        stubDeck()
        var motorACallCount = 0
        lateinit var orchestrator: DeckDoctorOrchestrator
        coEvery { mockSuggestAddsFromCollectionUseCase(any(), any(), any(), any(), any(), any()) } coAnswers {
            motorACallCount++
            if (motorACallCount == 1) {
                // A second, superseding loadAnalysis() call arrives while pass #1's own Motor A
                // call is still "in flight" from the mock's perspective -- exactly the scenario
                // stagingGeneration guards against (recomputeAddsJob/communityJob are SIBLINGS of
                // analysisJob, not its children, so analysisJob.cancel() alone would not stop a
                // stale recomputeAddsJob without the generation check).
                orchestrator.loadAnalysis(DECK_ID, BudgetConstraints())
            }
            emptyList()
        }
        orchestrator = createOrchestrator(this)

        orchestrator.loadAnalysis(DECK_ID, BudgetConstraints())
        advanceUntilIdle()

        assertEquals("Motor A must have been invoked once per pass", 2, motorACallCount)
        assertNull("the doctor must never be left stuck displaying a stale stage", orchestrator.state.value.stage)
        assertTrue(orchestrator.state.value.isLoaded)
    }

    @Test
    fun `a superseded pass resuming mid-flight never fast-forwards a genuinely in-progress newer pass's stage`() = runTest(dispatcher) {
        // Fix 4 (edge-case audit, 2026-07-28) -- the previous test above proves the doctor never
        // gets STUCK on a stage, but that alone doesn't catch this bug: `advanceDoctorStage` no-ops
        // once `stage` is already null, so a superseded pass resuming AFTER the newer pass has
        // fully FINISHED can never corrupt anything (the guard already protects that case). The
        // actual regression only manifests while the newer pass is still MID-FLIGHT (a non-null
        // stage) when the superseded pass's own (pre-fix, stale-cached) stage-advance calls fire
        // with a HIGHER target ordinal -- that passes `advanceDoctorStage`'s "never backward" guard
        // and corrupts the CURRENT pass's genuinely-in-progress display. This test forces exactly
        // that interleaving with two controllable suspension points (one per pass's own Motor A
        // call).
        stubDeck()
        val pass1MotorA = CompletableDeferred<Unit>()
        val pass2MotorA = CompletableDeferred<Unit>()
        var motorACallCount = 0
        coEvery { mockSuggestAddsFromCollectionUseCase(any(), any(), any(), any(), any(), any()) } coAnswers {
            motorACallCount++
            if (motorACallCount == 1) pass1MotorA.await() else pass2MotorA.await()
            emptyList()
        }
        val orchestrator = createOrchestrator(this)

        // Pass #1 (generation 1): runs up to its own Motor A call and suspends there, having
        // already (correctly, for itself) set EVALUATING_COLLECTION.
        orchestrator.loadAnalysis(DECK_ID, BudgetConstraints())
        advanceUntilIdle()
        assertEquals(DoctorAnalysisStage.EVALUATING_COLLECTION, orchestrator.state.value.stage)

        // Pass #2 (generation 2, supersedes #1): also runs up to ITS OWN Motor A call and suspends
        // there -- genuinely mid-flight, showing its OWN real EVALUATING_COLLECTION.
        orchestrator.loadAnalysis(DECK_ID, BudgetConstraints())
        advanceUntilIdle()
        assertEquals(
            "pass #2 must show its own real EVALUATING_COLLECTION, unaffected by pass #1's reset",
            DoctorAnalysisStage.EVALUATING_COLLECTION, orchestrator.state.value.stage,
        )

        // Unblock ONLY pass #1's Motor A call. Pass #1 is superseded (generation 1 != the current
        // generation 2) but its coroutine still runs to completion, including its own 2
        // stage-advance calls (SEARCHING_CARD_POOL / RANKING_SUGGESTIONS).
        pass1MotorA.complete(Unit)
        advanceUntilIdle()

        // The core Fix 4 assertion: pass #2's genuinely-in-progress stage must be COMPLETELY
        // unaffected by pass #1's now-superseded completion -- it must still read
        // EVALUATING_COLLECTION (pass #2's own Motor A call is STILL suspended on pass2MotorA right
        // now), never a stage pass #1 fabricated on top of it.
        assertEquals(
            "a superseded pass must never fast-forward the CURRENT pass's genuinely in-progress stage",
            DoctorAnalysisStage.EVALUATING_COLLECTION, orchestrator.state.value.stage,
        )

        // Let pass #2 finish normally -- the doctor must still settle cleanly.
        pass2MotorA.complete(Unit)
        advanceUntilIdle()
        assertNull(orchestrator.state.value.stage)
        assertTrue(orchestrator.state.value.isLoaded)
        assertEquals(2, motorACallCount)
    }
}
