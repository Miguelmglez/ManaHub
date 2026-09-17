package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-10

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.availableIn
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.StrategyPin
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.WizardPreferenceStore
import com.mmg.manahub.feature.decks.domain.template.AmbiguityGroup
import com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase
import com.mmg.manahub.feature.decks.domain.template.CommanderBuildOutcome
import com.mmg.manahub.feature.decks.domain.template.WizardDraftBuild
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.OwnedCard
import com.mmg.manahub.feature.decks.domain.template.WizardBuildResult
import com.mmg.manahub.feature.decks.domain.template.WizardFillStats
import com.mmg.manahub.feature.decks.domain.template.ManualAdd
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.4) -- [DeckWizardViewModel] phase
 * transitions, event emission, generation happy/failure paths, and cancellation cleanup.
 *
 * [the deleted Motor A wizard build use case] is mocked so the Flow<TemplateBuildProgress> sequence is fully
 * controllable per test (mirrors [DeckStudioViewModelTest]'s split between real and mocked
 * use cases). [CollectionProfileUseCase] runs REAL (cheap, deterministic, avoids stubbing burden).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckWizardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val deckRepository = mockk<DeckRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>()
    private val searchCardsUseCase = mockk<SearchCardsUseCase>()
    private val communityAggregateRepository = mockk<CommunityAggregateRepository>()
    // Deck Wizard & Engine Rework plan, Workstream 2.2 -- STRATEGY step's source 1. Non-relaxed
    // (matches this file's own convention for the other collaborators above), but every call site
    // inside DeckWizardViewModel.deriveCommanderStrategies wraps it in runCatching, so an unstubbed
    // invocation degrades safely (source 1 empty, falls through to source 2/3) rather than crashing
    // a test that never touches this behavior directly.
    private val cardStrategyTagsRepository = mockk<CardStrategyTagsRepository>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val appContext = mockk<Context>()
    // Deck Wizard Commander v3 plan, Phase 5 -- stubbed to a real DeckHealth (analysis defaults
    // null) rather than left relaxed: `analyze` is `suspend` and every Commander STRATEGY-step test
    // now exercises `recomputePlanAnalysis()` via `onNextFromStrategy()`, so an unstubbed call would
    // throw. Individual PLAN_SECTIONS tests override this per-case with a real `DeckAnalysis`.
    private val deckAnalysisPipeline = mockk<DeckAnalysisPipeline>()
    // Deck Wizard Commander v3 plan, Phase 6 -- the Commander build path's own use case. A REAL
    // spy (not a full mock): a Commander-format `onGenerate()` test stubs the placement engine's
    // own `invoke()` (its internals are BuildWizardDeckUseCaseTest's job, not this VM test's),
    // but leaves `persist()` running for real so this file's write-path assertions
    // (replaceAllCardsWithSource/updateArchetypeOverride/etc.) still exercise real behavior against
    // the mocked deckRepository above.
    private val buildCommanderDeckUseCase = spyk(BuildWizardDeckUseCase(deckAnalysisPipeline, crashReporter))
    // Deck Wizard v4, W0.2/W5.3 -- relaxed: no existing test exercises the basic-land pre-warm path
    // directly, and an unstubbed call is swallowed by guaranteeBasicsAvailable's own runCatching, so
    // this only needs to exist, never to be configured.
    private val cardRepository = mockk<CardRepository>(relaxed = true)
    // Deck Wizard v4, W7 Task B (E4/E8) -- relaxed: recordPick/preferredCardIds are fire-and-forget
    // from this VM's own perspective (no test asserts on the store's OWN persisted state), so this
    // only needs to exist as a valid collaborator, never to be configured.
    private val wizardPreferenceStore = mockk<WizardPreferenceStore>(relaxed = true)

    private val collectionProfileUseCase = CollectionProfileUseCase(ioDispatcher = dispatcher)

    private val commander = card(
        id = "cmd-1",
        name = "Elf Lord",
        typeLine = "Legendary Creature — Elf",
        colorIdentity = listOf("G"),
        colors = listOf("G"),
        tags = listOf(CardTag.TRIBAL),
    )

    /** Deck Wizard Commander v3 plan, Phase 6 -- a minimal, valid [CommanderBuildOutcome] fixture
     * for stubbing [buildCommanderDeckUseCase] in an `onGenerate()` test. [entries] defaults to
     * just the commander's own qty-1 mainboard slot. */
    private fun commanderOutcome(entries: List<DeckEntry> = listOf(DeckEntry(card = commander, quantity = 1, isOwned = true, isSideboard = false))) =
        CommanderBuildOutcome(
            result = WizardBuildResult(
                entries = entries,
                analysis = mockk(relaxed = true),
                gapSections = emptyList(),
                fillStats = WizardFillStats(placedByWizard = 0, placedManual = 0, lands = 0),
            ),
            plan = mockk(relaxed = true),
            pin = StrategyPin(archetype = null, posture = null, themes = emptyList(), tribe = null),
        )

    /** Deck Wizard v4, W7 Task B -- a minimal, relaxed [WizardDraftBuild] fixture for stubbing
     * [buildCommanderDeckUseCase]'s [BuildWizardDeckUseCase.buildWithGroups] in a Commander-format
     * `onGenerate()` test. Every unspecified property reads back as an empty/relaxed default
     * (mockk's own contract) -- a test overrides only what it actually inspects: [ambiguityGroups]
     * (empty by default -- the zero-group path is what most existing tests exercise, matching the
     * pre-W7 single-shot build they were written against), [tentativeByRole], [candidatesById]. */
    /** Deck Wizard 60-card wave (v6), plan §5 Phase 5.4: [candidateMaxCopies] defaults every id in
     * [candidatesById] to 1 -- the REAL Commander invariant (every candidate's own `maxPlaceable`
     * is exactly 1, see [WizardDraftBuild.candidateMaxCopies]'s own KDoc) for an ALREADY-PLACED
     * tentative id, whose real headroom is 0 (see [onChangeChoiceQuantity]'s own cap formula, which
     * adds the id's tentative-copy count back on top) -- so a genuinely tentative id defaults to 0
     * here, and every non-tentative (alternative) id defaults to 1. A test that needs a different
     * shape (e.g. the multi-copy 60-card Choice tests run C2 adds) overrides this explicitly. */
    private fun commanderDraft(
        ambiguityGroups: List<AmbiguityGroup> = emptyList(),
        tentativeByRole: Map<RoleKey, List<String>> = emptyMap(),
        candidatesById: Map<String, Card> = emptyMap(),
        fallbackStandaloneIds: List<String> = emptyList(),
        fallbackOffPlanIds: List<String> = emptyList(),
        placedNonLand: List<DeckEntry> = emptyList(),
        candidateMaxCopies: Map<String, Int> = candidatesById.keys.associateWith { id ->
            if (tentativeByRole.values.any { id in it }) 0 else 1
        },
    ): WizardDraftBuild {
        val draft = mockk<WizardDraftBuild>(relaxed = true)
        every { draft.ambiguityGroups } returns ambiguityGroups
        every { draft.tentativeByRole } returns tentativeByRole
        every { draft.candidatesById } returns candidatesById
        every { draft.fallbackStandaloneIds } returns fallbackStandaloneIds
        every { draft.fallbackOffPlanIds } returns fallbackOffPlanIds
        every { draft.placedNonLand } returns placedNonLand
        every { draft.candidateMaxCopies } returns candidateMaxCopies
        return draft
    }

    private fun viewModel(
        savedState: Map<String, Any?> = emptyMap(),
    ) = DeckWizardViewModel(
        deckRepository = deckRepository,
        userCardRepository = userCardRepository,
        collectionProfileUseCase = collectionProfileUseCase,
        searchCardsUseCase = searchCardsUseCase,
        communityAggregateRepository = communityAggregateRepository,
        crashReporter = crashReporter,
        appContext = appContext,
        savedStateHandle = SavedStateHandle(savedState),
        cardStrategyTagsRepository = cardStrategyTagsRepository,
        deckAnalysisPipeline = deckAnalysisPipeline,
        buildWizardDeckUseCase = buildCommanderDeckUseCase,
        cardRepository = cardRepository,
        wizardPreferenceStore = wizardPreferenceStore,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { appContext.getString(any()) } returns "TPL"
        // W7 fix 4.1: onToggleChoiceCard's cap-reached toast uses the vararg getString overload.
        every { appContext.getString(any(), *anyVararg()) } returns "TPL"
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        coEvery { deckRepository.createDeck(any(), any(), any()) } returns "wizard-deck-1"
        // Default: no analysis available (degrades PLAN_SECTIONS to its error state) -- tests that
        // need a real DeckAnalysis re-stub this call with their own fixture.
        coEvery {
            deckAnalysisPipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns DeckHealth(evaluation = mockk(relaxed = true), profile = mockk(relaxed = true))
        // A relaxed mock's default Flow-returning stub never emits, which would hang
        // writeResultIntoNewDeck's `observeDeckWithCards(deckId).first()` forever for a Commander
        // build -- explicit stub so .first() resolves immediately (no existing deck to update).
        every { deckRepository.observeDeckWithCards(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Phase navigation ─────────────────────────────────────────────────────

    // Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: a missing/unsupported format nav arg (a
    // fixture built with an empty savedState, mirroring a corrupted deep link, or DRAFT) no longer
    // silently falls back to CASUAL -- it shows an error toast and emits Exit, leaving
    // selectedFormat untouched. Supersedes the pre-v6 "falls back to CASUAL" test.
    @Test
    fun `a missing format nav arg shows an error toast and emits Exit, never falling back to CASUAL`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.events.test {
            advanceUntilIdle()
            val toast = awaitItem()
            assertTrue(toast is DeckWizardEvent.ShowToast)
            val exit = awaitItem()
            assertTrue(exit is DeckWizardEvent.Exit)
        }
        assertNull(vm.uiState.value.selectedFormat)
    }

    @Test
    fun `a DRAFT format nav arg is unsupported -- shows an error toast and emits Exit`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "DRAFT"))
        vm.events.test {
            advanceUntilIdle()
            val toast = awaitItem()
            assertTrue(toast is DeckWizardEvent.ShowToast)
            val exit = awaitItem()
            assertTrue(exit is DeckWizardEvent.Exit)
        }
        assertNull(vm.uiState.value.selectedFormat)
    }

    @Test
    fun `a real format nav arg lands on the correct starting phase for that format`() = runTest(dispatcher) {
        val commanderVm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        assertEquals(WizardPhase.COMMANDER_PICK, commanderVm.uiState.value.phase)

        val casualVm = viewModel(mapOf("format" to "CASUAL"))
        advanceUntilIdle()
        assertEquals(WizardPhase.ENTRY, casualVm.uiState.value.phase)
    }


    // ── Entry chooser (Deck Engine Unification plan §5 Phase 3.1) ────────────












    // ── Commander flow (Deck Wizard & Engine Rework plan, Workstream 2) ──────────








    // ── PLAN_SECTIONS step (Deck Wizard Commander v3 plan, Phase 5) ────────────────────────────

    @Test
    fun `PLAN_SECTIONS -- entering the step (onNextFromStrategy) recomputes planAnalysis`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        // The shared setUp() default stub returns DeckHealth(analysis = null) on purpose (the
        // "degraded" case) -- this test needs a REAL non-null DeckAnalysis to prove the field
        // actually gets populated, so it re-stubs with one for this test only.
        val realAnalysis = com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis(
            totalScore = 80,
            pillars = emptyList(),
            strategy = com.mmg.manahub.feature.decks.domain.engine.ResolvedStrategyInfo(
                curatedStrategyId = null,
                displayName = "Custom",
                archetype = null,
                themes = emptyList(),
                isManualOverride = false,
                confidence = 0f,
            ),
            limiter = com.mmg.manahub.feature.decks.domain.engine.ScoreLimiter.None,
        )
        coEvery {
            deckAnalysisPipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns DeckHealth(evaluation = mockk(relaxed = true), profile = mockk(relaxed = true), analysis = realAnalysis)

        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()

        assertNull(vm.uiState.value.planAnalysis)
        vm.onNextFromStrategy()
        advanceUntilIdle()

        assertEquals(realAnalysis, vm.uiState.value.planAnalysis)
        coVerify(atLeast = 1) {
            deckAnalysisPipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(false), any(), any())
        }
    }

    @Test
    fun `PLAN_SECTIONS -- a manual add outside the commander's color identity is rejected with a toast, never added`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        vm.onSelectCommander(commander) // identity = {G}
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val offColorCard = card(id = "off-1", name = "Off Color Card", colorIdentity = listOf("U"))

        vm.events.test {
            vm.onAddSeed(offColorCard)
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.ShowToast)
        }
        assertTrue(vm.uiState.value.seeds.none { it.card.scryfallId == offColorCard.scryfallId })
    }

    @Test
    fun `PLAN_SECTIONS -- a manual add banned in strict Commander is rejected`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val bannedCard = card(id = "banned-1", name = "Banned Card", colorIdentity = listOf("G"), legalityCommander = "banned")
        vm.onAddSeed(bannedCard)

        assertTrue(vm.uiState.value.seeds.none { it.card.scryfallId == bannedCard.scryfallId })
    }

    @Test
    fun `PLAN_SECTIONS -- two different printings of the same card name are deduped, basics exempt`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val printingA = card(id = "dup-a", name = "Sol Ring", colorIdentity = emptyList())
        val printingB = card(id = "dup-b", name = "Sol Ring", colorIdentity = emptyList())
        vm.onAddSeed(printingA)
        vm.onAddSeed(printingB)
        assertEquals(1, vm.uiState.value.seeds.size)

        val forestA = card(id = "forest-a", name = "Forest", typeLine = "Basic Land — Forest", colorIdentity = emptyList())
        val forestB = card(id = "forest-b", name = "Forest", typeLine = "Basic Land — Forest", colorIdentity = emptyList())
        vm.onAddSeed(forestA)
        vm.onAddSeed(forestB)
        assertEquals(3, vm.uiState.value.seeds.size)
    }

    @Test
    fun `PLAN_SECTIONS -- an unowned manual add is still kept, flagged isOwned = false in the analyzed mainboard`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        // recomputePlanAnalysis fires more than once in this scenario (step entry + the manual
        // add) -- a single mockk `slot` only ever keeps the LAST invocation's argument and mockk
        // refuses to `coVerify { capture(slot) }` against more than one matching call, so every
        // mainboard this mock is called with is recorded into a list instead (the error message's
        // own recommended pattern).
        val capturedMainboards = mutableListOf<List<DeckEntry>>()
        coEvery {
            deckAnalysisPipeline.analyze(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            capturedMainboards += firstArg<List<DeckEntry>>()
            DeckHealth(evaluation = mockk(relaxed = true), profile = mockk(relaxed = true))
        }

        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val unownedCard = card(id = "unowned-1", name = "Unowned Card", colorIdentity = listOf("G"))
        vm.onAddSeed(unownedCard)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.seeds.any { it.card.scryfallId == unownedCard.scryfallId })
        val entry = capturedMainboards.last().first { it.card.scryfallId == unownedCard.scryfallId }
        assertFalse(entry.isOwned)
    }

    @Test
    fun `Commander -- removing the LAST manual add does NOT clear the strategy pick`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onSelectCommanderStrategy(CuratedStrategyCatalog.ALL.first { it.availableIn(DeckFormat.COMMANDER) })
        vm.onNextFromStrategy()
        advanceUntilIdle()
        val pickedStrategyId = vm.uiState.value.selectedCuratedStrategyId

        val onlyManualAdd = card(id = "only-1", name = "Only Manual Add", colorIdentity = listOf("G"))
        vm.onAddSeed(onlyManualAdd)
        assertEquals(listOf(onlyManualAdd), vm.uiState.value.seeds.map { it.card })

        vm.onRemoveSeed(onlyManualAdd)
        assertTrue(vm.uiState.value.seeds.isEmpty())
        // Unlike the (now-deleted) pre-v6 Casual seed-inference rule, the STRATEGY pick is an
        // explicit choice made on its OWN shared step -- removing a seed must never silently clear it.
        assertEquals(pickedStrategyId, vm.uiState.value.selectedCuratedStrategyId)
    }




    // ── Deck Wizard Commander v3 plan (Phase 3.2): the two-tab structured search ────────────────








    @Test
    fun `Commander locks a strict Format legality clause -- Commander Casual does not (R2)`() {
        assertTrue(
            commanderLockedCriteria(DeckFormat.COMMANDER)
                .any { it is com.mmg.manahub.core.model.SearchCriterion.Format }
        )
        assertTrue(
            commanderLockedCriteria(DeckFormat.COMMANDER_CASUAL)
                .none { it is com.mmg.manahub.core.model.SearchCriterion.Format }
        )
    }




    // ── Flow A -- Workstream 3.1 locked-color invariants ─────────────────────────





    // ── Flow A -- Workstream 3.1 coherence-hint VM wiring (exhaustive logic already covered by
    //    SuggestStrategiesForSeedsUseCaseTest) ────────────────────────────────────


    // ── Flow A/B/C -- Workstream 3 per-flow full step chains ─────────────────────







    // ── Discoveries v2 hand-off (D11) ────────────────────────────────────────


    // ── Deck Wizard Commander v3 plan (Phase 6, D12/6.1): format/deckId nav args ─

    @Test
    fun `a format nav arg preselects the format and skips the FORMAT step, landing on COMMANDER_PICK for Commander`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(DeckFormat.COMMANDER, state.selectedFormat)
        assertEquals(WizardPhase.COMMANDER_PICK, state.phase)
    }

    @Test
    fun `a format nav arg for CASUAL preselects the format and lands on ENTRY`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "CASUAL"))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(DeckFormat.CASUAL, state.selectedFormat)
        assertEquals(WizardPhase.ENTRY, state.phase)
    }

    // ── Combo "Use as seed" hand-off (Deck Engine Unification plan D7, 4.3) ──

    @Test
    fun `a seeds nav arg forces Flow A immediately, before the collection loads`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "CASUAL", "seeds" to "Sol Ring"))
        // Asserted BEFORE advanceUntilIdle -- the entryFlow=CARDS write is synchronous (init's
        // first block), independent of the async collection load.
        assertEquals(WizardEntryFlow.CARDS, vm.uiState.value.entryFlow)
    }

    @Test
    fun `an owned combo card resolves from the collection snapshot with no network call`() = runTest(dispatcher) {
        val solRing = card(id = "sol-ring", name = "Sol Ring")
        every { userCardRepository.observeCollection() } returns flowOf(
            listOf(com.mmg.manahub.core.model.UserCardWithCard(
                userCard = com.mmg.manahub.core.model.UserCard(id = "uc-1", scryfallId = "sol-ring", quantity = 1),
                card = solRing,
            ))
        )

        val vm = viewModel(mapOf("format" to "CASUAL", "seeds" to "Sol Ring"))
        advanceUntilIdle()

        assertEquals(listOf(solRing), vm.uiState.value.seeds.map { it.card })
        coVerify(exactly = 0) { searchCardsUseCase(any(), any()) }
    }

    @Test
    fun `an unowned combo card (the missing piece) resolves via a network search`() = runTest(dispatcher) {
        val basaltMonolith = card(id = "basalt-1", name = "Basalt Monolith")
        coEvery { searchCardsUseCase("Basalt Monolith", any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(basaltMonolith), hasMore = false, totalCards = 1)
        )

        val vm = viewModel(mapOf("format" to "CASUAL", "seeds" to "Basalt Monolith"))
        advanceUntilIdle()

        assertEquals(listOf(basaltMonolith), vm.uiState.value.seeds.map { it.card })
    }

    @Test
    fun `card names split on the pipe delimiter -- a comma inside a real card name is preserved`() = runTest(dispatcher) {
        // "Urza, Lord High Artificer" has a literal comma -- splitting on "," (instead of "|")
        // would have mangled this into two bogus names.
        val urza = card(id = "urza-1", name = "Urza, Lord High Artificer")
        val solRing = card(id = "sol-ring", name = "Sol Ring")
        every { userCardRepository.observeCollection() } returns flowOf(
            listOf(
                com.mmg.manahub.core.model.UserCardWithCard(
                    userCard = com.mmg.manahub.core.model.UserCard(id = "uc-1", scryfallId = "urza-1", quantity = 1),
                    card = urza,
                ),
                com.mmg.manahub.core.model.UserCardWithCard(
                    userCard = com.mmg.manahub.core.model.UserCard(id = "uc-2", scryfallId = "sol-ring", quantity = 1),
                    card = solRing,
                ),
            )
        )

        val vm = viewModel(mapOf("format" to "CASUAL", "seeds" to "Urza, Lord High Artificer|Sol Ring"))
        advanceUntilIdle()

        assertEquals(setOf("Urza, Lord High Artificer", "Sol Ring"), vm.uiState.value.seeds.map { it.card.name }.toSet())
    }

    @Test
    fun `a combo card that resolves nowhere is silently skipped, never blocking the others`() = runTest(dispatcher) {
        val solRing = card(id = "sol-ring", name = "Sol Ring")
        every { userCardRepository.observeCollection() } returns flowOf(
            listOf(com.mmg.manahub.core.model.UserCardWithCard(
                userCard = com.mmg.manahub.core.model.UserCard(id = "uc-1", scryfallId = "sol-ring", quantity = 1),
                card = solRing,
            ))
        )
        coEvery { searchCardsUseCase("Nonexistent Card", any()) } returns DataResult.Error("not found")

        val vm = viewModel(mapOf("format" to "CASUAL", "seeds" to "Sol Ring|Nonexistent Card"))
        advanceUntilIdle()

        assertEquals(listOf(solRing), vm.uiState.value.seeds.map { it.card })
    }





    // ── Generation ────────────────────────────────────────────────────────────


    // ── BUG-1 regression (Deck Engine Unification plan §0.1) ────────────────────





    // ── R15 structural guard: an unconfirmed launch never silently replaces a non-empty deck ──

    private fun nonEmptyDeckWithCards(deckId: String, commanderId: String? = "cmd-1") = DeckWithCards(
        deck = com.mmg.manahub.core.model.Deck(id = deckId, name = "Existing Deck", format = "commander", commanderCardId = commanderId),
        mainboard = listOf(com.mmg.manahub.core.model.DeckSlot(scryfallId = "spell-1", quantity = 1)),
        sideboard = emptyList(),
    )

    private fun emptyDeckWithCards(deckId: String) = DeckWithCards(
        deck = com.mmg.manahub.core.model.Deck(id = deckId, name = "Existing Deck", format = "commander", commanderCardId = null),
        mainboard = emptyList(),
        sideboard = emptyList(),
    )










    // ── Result ────────────────────────────────────────────────────────────────




    // ── Flow A -- suggested strategies from seeds (Deck Engine Unification plan §5 Phase 3.2) ──







    // ── Flow B -- colors-first (Deck Engine Unification plan §5 Phase 3.3) ──────



    // ── Flow C -- strategy-first (Deck Engine Unification plan §5 Phase 3.4) ────




    // ── Review — community-source toggle (Deck Engine Unification plan §5 Phase 3.5) ────────



    private fun userCardWith(card: Card) = UserCardWithCard(
        userCard = UserCard(id = "uc-${card.scryfallId}", scryfallId = card.scryfallId),
        card = card,
    )

    // ── QA fix (RUN 3b) -- format/flow-switch scratch-state leaks ────────────────






    // ── QA fix (edge-case audit follow-up) -- Flow C stale color-combo leak ─────


    // ── QA fix (RUN 3b follow-up) -- Flow B/C suggested-seeds race with the async collection load ──




    // ── W7 Task B -- the Choice screen (2026-09-15) ─────────────────────────────

    private val choiceTentative1 = card(id = "tent-1", name = "Tentative One", colorIdentity = listOf("G"))
    private val choiceTentative2 = card(id = "tent-2", name = "Tentative Two", colorIdentity = listOf("G"))
    private val choiceAltA = card(id = "alt-a", name = "Alt A", colorIdentity = listOf("G"))
    private val choiceAltB = card(id = "alt-b", name = "Alt B", colorIdentity = listOf("G"))
    private val choiceAltC = card(id = "alt-c", name = "Alt C", colorIdentity = listOf("G"))

    /** Two ambiguity groups (mirrors the real harness -- median 3-4 groups/build, never just one)
     * so "resolve one, leave the other untouched" is a genuine test of [DeckWizardViewModel
     * .onFinishChoices]'s "honour decided, default undecided" contract, not a single-group
     * coincidence. */
    private fun twoGroupChoiceDraft() = commanderDraft(
        ambiguityGroups = listOf(
            AmbiguityGroup(sectionId = "removal_spot", candidateIds = listOf("alt-a", "alt-b"), remainingSlots = 1),
            AmbiguityGroup(sectionId = "card_draw", candidateIds = listOf("alt-c"), remainingSlots = 1),
        ),
        tentativeByRole = mapOf("removal_spot" to listOf("tent-1"), "card_draw" to listOf("tent-2")),
        candidatesById = mapOf(
            "tent-1" to choiceTentative1, "tent-2" to choiceTentative2,
            "alt-a" to choiceAltA, "alt-b" to choiceAltB, "alt-c" to choiceAltC,
        ),
    )

    // Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: format is resolved at CONSTRUCTION time now
    // (SavedStateHandle, not onSelectFormat/onNextFromFormat -- both deleted) -- every call site
    // constructs its own `viewModel(mapOf("format" to "COMMANDER"))` before calling this; this helper
    // only drives the remaining Commander step chain. `onNextFromManualAdds` renamed
    // `onNextFromPlanSections` (MANUAL_ADDS -> PLAN_SECTIONS).
    private fun kotlinx.coroutines.test.TestScope.advanceCommanderToReview(vm: DeckWizardViewModel) {
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromPlanSections()
    }

    @Test
    fun `a build with ambiguity groups lands on CHOICE and persists nothing yet`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        assertEquals(WizardPhase.CHOICE, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.commanderDraftBuild)
        assertTrue(vm.uiState.value.choiceSelections.isEmpty())
        coVerify(exactly = 0) { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.persistWizardBuild(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a zero-group build skips CHOICE and finalizes with empty resolutions, straight to opening Deck Studio`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)

        // W7 Task D (plan 7.5): never lands on the deleted RESULT phase -- fires OpenDeckStudio directly.
        vm.events.test {
            vm.onGenerate()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
        }
        assertNull(vm.uiState.value.commanderDraftBuild)
        coVerify(exactly = 1) { buildCommanderDeckUseCase.finalize(any(), emptyMap(), any(), any()) }
    }

    @Test
    fun `a build with fallback ids and zero ambiguity groups still lands on CHOICE, not a silent direct persist`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val fallbackDraft = commanderDraft(
            candidatesById = mapOf("standalone-1" to choiceAltA, "offplan-1" to choiceAltB),
            fallbackStandaloneIds = listOf("standalone-1"),
            fallbackOffPlanIds = listOf("offplan-1"),
        )
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns fallbackDraft
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        assertEquals(WizardPhase.CHOICE, vm.uiState.value.phase)
        assertEquals(fallbackDraft, vm.uiState.value.commanderDraftBuild)
        coVerify(exactly = 0) { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.persistWizardBuild(any(), any(), any(), any(), any(), any(), any()) }

        // The only way out of this phase-with-zero-groups build is "Let the wizard finish" --
        // it must still finalize exactly once, with no resolutions (nothing was ever selectable).
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        vm.events.test {
            vm.onFinishChoices()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
        }
        coVerify(exactly = 1) { buildCommanderDeckUseCase.finalize(fallbackDraft, emptyMap(), any(), any()) }
        coVerify(exactly = 1) { deckRepository.persistWizardBuild(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `W7 Fix 7 -- a group with more than 10 alternatives resolves from the engine's full candidate pool, never the UI's display cap`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        // CHOICE_ALTERNATIVES_CAP (DeckWizardChoiceStep.kt) is 10 -- this group's real pool is
        // deliberately larger, so a regression that sources "Choose for me"/"Let the wizard
        // finish" from the CAPPED display list instead of the engine's own data would surface here.
        val manyAlternativeIds = (1..15).map { "alt-$it" }
        val manyAlternativeCards = manyAlternativeIds.associateWith { id -> card(id = id, name = "Alt $id", colorIdentity = listOf("G")) }
        val bigDraft = commanderDraft(
            ambiguityGroups = listOf(AmbiguityGroup(sectionId = "removal_spot", candidateIds = manyAlternativeIds, remainingSlots = 2)),
            tentativeByRole = mapOf("removal_spot" to listOf("tent-1", "tent-2")),
            candidatesById = manyAlternativeCards + mapOf("tent-1" to choiceTentative1, "tent-2" to choiceTentative2),
        )
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns bigDraft
        val draftSlot = slot<WizardDraftBuild>()
        coEvery { buildCommanderDeckUseCase.finalize(capture(draftSlot), any(), any(), any()) } returns commanderOutcome()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        assertEquals(WizardPhase.CHOICE, vm.uiState.value.phase)
        // The VM's in-memory draft keeps the FULL 15-candidate group -- CHOICE_ALTERNATIVES_CAP is
        // purely a DeckWizardChoiceStep.kt display concern, never applied at the domain/VM layer.
        assertEquals(15, vm.uiState.value.commanderDraftBuild?.ambiguityGroups?.first()?.candidateIds?.size)

        // "Choose the remaining N for me" -- deselect one tentative default, then auto-fill: the
        // replacement must come from the engine's own tentative set, never the 15-alternative pool.
        vm.onChangeChoiceQuantity("removal_spot", "tent-2", -1)
        vm.onAutoFillChoiceSection("removal_spot")
        assertEquals(mapOf("tent-1" to 1, "tent-2" to 1), vm.uiState.value.choiceSelections["removal_spot"])

        // "Let the wizard finish" -- resolves cleanly even though the group's real pool is far
        // beyond the display cap, and the draft handed to finalize is the SAME unmodified one.
        vm.events.test {
            vm.onFinishChoices()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
        }
        assertEquals(15, draftSlot.captured.ambiguityGroups.first().candidateIds.size)
    }

    @Test
    fun `onToggleChoiceCard enforces the remainingSlots cap and a deselect frees the slot`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // Untouched -- the effective selection is the tentative default, which already occupies the
        // section's ONE slot; the cap must be respected against that IMPLICIT default too.
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1)
        assertNull("selecting an alternative while the tentative default still occupies the only slot must be a no-op", vm.uiState.value.choiceSelections["removal_spot"])

        // Deselect the default first -- frees the slot.
        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        assertEquals(emptyMap<String, Int>(), vm.uiState.value.choiceSelections["removal_spot"])

        // Now the alternative can be selected.
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1)
        assertEquals(mapOf("alt-a" to 1), vm.uiState.value.choiceSelections["removal_spot"])

        // A second alternative cannot be added on top of a full section.
        vm.onChangeChoiceQuantity("removal_spot", "alt-b", 1)
        assertEquals(mapOf("alt-a" to 1), vm.uiState.value.choiceSelections["removal_spot"])

        // Deselecting the chosen alternative frees the slot again.
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", -1)
        assertEquals(emptyMap<String, Int>(), vm.uiState.value.choiceSelections["removal_spot"])
    }

    // W7 Fix 3 (E8) -- superseded the pre-fix `onToggleChoiceCard records a preference ONLY for an
    // actively-selected alternative, never a kept default` test, which asserted recordPick fired
    // the instant the user tapped an alternative. That was the defect: a tap the user later
    // reversed, or made right before abandoning Choice, was ALREADY recorded as a preference by
    // then. Preferences now record once, at successful-persist time, for exactly the alternatives
    // in the FINAL resolution -- the 4 tests below cover select-then-deselect, select-then-abandon,
    // a failed persist, and a genuinely successful resolution.

    @Test
    fun `W7 Fix 3 -- selecting then deselecting an alternative before finishing records nothing`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1) // deselect the kept default
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1) // actively choose an alternative
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", -1) // then reverse the choice
        vm.onChangeChoiceQuantity("removal_spot", "tent-1", 1) // back to the kept default
        vm.onFinishChoices()
        advanceUntilIdle()

        coVerify(exactly = 0) { wizardPreferenceStore.recordPick(any()) }
    }

    @Test
    fun `W7 Fix 3 -- selecting an alternative then abandoning Choice records nothing`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1)
        vm.onBackPressed() // abandons Choice -- see onAbandonChoice's own KDoc
        advanceUntilIdle()

        coVerify(exactly = 0) { wizardPreferenceStore.recordPick(any()) }
    }

    @Test
    fun `W7 Fix 3 -- a failed persist records nothing`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        coEvery {
            deckRepository.persistWizardBuild(any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("persist boom")
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1)
        vm.onFinishChoices()
        advanceUntilIdle()

        assertEquals("TPL", vm.uiState.value.buildError)
        coVerify(exactly = 0) { wizardPreferenceStore.recordPick(any()) }
    }

    @Test
    fun `W7 Fix 3 -- a successful resolution records exactly the user-chosen alternatives, never tentative defaults`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // "removal_spot" -- the user swaps the default for an alternative (genuine pick).
        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1)
        // "card_draw" is left untouched -- resolved by the engine's own tentative default, never a
        // preference (mirrors "Choose the remaining N for me"/"Let the wizard finish" defaults).
        vm.onFinishChoices()
        advanceUntilIdle()

        coVerify(exactly = 1) { wizardPreferenceStore.recordPick("alt-a") }
        coVerify(exactly = 0) { wizardPreferenceStore.recordPick("tent-1") }
        coVerify(exactly = 0) { wizardPreferenceStore.recordPick("tent-2") }
    }

    @Test
    fun `onAutoFillChoiceSection fills only the section's still-unselected slots with tentative defaults`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // The section is already "full" via its implicit tentative default -- auto-fill has nothing
        // left to do and must be a no-op (never duplicate/exceed remainingSlots).
        vm.onAutoFillChoiceSection("removal_spot")
        assertNull(vm.uiState.value.choiceSelections["removal_spot"])

        // Deselect the default, leaving a real gap -- auto-fill puts it right back.
        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        vm.onAutoFillChoiceSection("removal_spot")
        assertEquals(mapOf("tent-1" to 1), vm.uiState.value.choiceSelections["removal_spot"])
    }

    @Test
    fun `onFinishChoices honours a decided section exactly, defaults an undecided one, and persists ONCE`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val resolutionsSlot = slot<Map<RoleKey, List<String>>>()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), capture(resolutionsSlot), any(), any())
        } returns commanderOutcome()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // The user decides "removal_spot" (swaps its default for alt-a) and never touches "card_draw".
        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1)

        // W7 Task D (plan 7.5): never lands on the deleted RESULT phase -- fires OpenDeckStudio directly.
        vm.events.test {
            vm.onFinishChoices()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
        }
        assertNull(vm.uiState.value.commanderDraftBuild)
        coVerify(exactly = 1) { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) }
        assertEquals(listOf("alt-a"), resolutionsSlot.captured["removal_spot"])
        assertTrue("an untouched section must be ABSENT from resolutions, not defaulted explicitly", "card_draw" !in resolutionsSlot.captured)
    }

    @Test
    fun `W7 Fix 2 -- retrying after a finalize failure re-runs finalize with the SAME resolved draft, never a fresh buildWithGroups`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        var finalizeCallCount = 0
        val resolutionsSeen = mutableListOf<Map<RoleKey, List<String>>>()
        coEvery { buildCommanderDeckUseCase.finalize(any(), capture(resolutionsSeen), any(), any()) } answers {
            finalizeCallCount++
            if (finalizeCallCount == 1) throw RuntimeException("finalize boom")
            commanderOutcome()
        }
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // The user resolves "removal_spot" before the finalize step fails.
        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1)
        vm.onFinishChoices()
        advanceUntilIdle()

        assertEquals("TPL", vm.uiState.value.buildError)
        coVerify(exactly = 1) { buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any()) }

        vm.events.test {
            vm.onRetryGeneration()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
        }

        // Still only ONE buildWithGroups call across the whole failure-then-retry cycle -- the
        // retry reused the already-resolved draft instead of re-walking from REVIEW.
        coVerify(exactly = 1) { buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) }
        // Both the failed attempt and the successful retry carried the user's ORIGINAL selection.
        assertEquals(listOf(listOf("alt-a"), listOf("alt-a")), resolutionsSeen.map { it["removal_spot"] })
        assertNull(vm.uiState.value.buildError)
    }

    @Test
    fun `W7 Fix 2 -- retrying after a persist failure deletes the orphan and re-persists the SAME resolved draft`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        var persistCallCount = 0
        coEvery {
            deckRepository.persistWizardBuild(any(), any(), any(), any(), any(), any(), any())
        } answers {
            persistCallCount++
            if (persistCallCount == 1) throw RuntimeException("persist boom")
        }
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        vm.onChangeChoiceQuantity("removal_spot", "alt-a", 1)
        vm.onFinishChoices()
        advanceUntilIdle()

        assertEquals("TPL", vm.uiState.value.buildError)
        coVerify(exactly = 0) { deckRepository.deleteDeck(any()) }

        vm.events.test {
            vm.onRetryGeneration()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
        }

        // The orphan from the failed write is cleaned up, buildWithGroups never re-runs, and the
        // retry's persist call carries the same user-chosen alternative.
        coVerify(exactly = 1) { deckRepository.deleteDeck("wizard-deck-1") }
        coVerify(exactly = 1) { buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { deckRepository.persistWizardBuild(any(), any(), any(), any(), any(), any(), any()) }
        assertNull(vm.uiState.value.buildError)
    }

    @Test
    fun `abandoning the Choice screen via back writes nothing and returns to REVIEW`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()
        assertEquals(WizardPhase.CHOICE, vm.uiState.value.phase)

        // Make a selection, THEN abandon -- the in-memory selection must not leak into a later build.
        vm.onChangeChoiceQuantity("removal_spot", "tent-1", -1)
        val shouldPopWizard = vm.onBackPressed()

        assertFalse("abandoning Choice unwinds internally -- the wizard itself must stay open", shouldPopWizard)
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)
        assertNull(vm.uiState.value.commanderDraftBuild)
        assertTrue(vm.uiState.value.choiceSelections.isEmpty())
        coVerify(exactly = 0) { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.persistWizardBuild(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.createDeck(any(), any(), any()) }
    }

    // ── Deck Wizard 60-card wave (v6), plan §5 Phase 5.4, run C2 -- new test coverage per plan §6 ──

    // (1) STANDARD nav arg lands on ENTRY -- DRAFT/missing arg already covered above.
    @Test
    fun `a STANDARD format nav arg preselects the format and lands on ENTRY`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        assertEquals(DeckFormat.STANDARD, vm.uiState.value.selectedFormat)
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
    }

    // (2) CARDS flow chain, forward and back.
    @Test
    fun `CARDS flow chain -- ENTRY to REVIEW forward, onBackPressed mirrors it exactly`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)

        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        assertEquals(WizardPhase.SEED_PICK, vm.uiState.value.phase)

        vm.onAddSeed(card(id = "seed-1", name = "Seed One", colorIdentity = emptyList()))
        vm.onNextFromSeedPick()
        advanceUntilIdle()
        assertEquals(WizardPhase.STRATEGY, vm.uiState.value.phase)

        vm.onNextFromStrategy()
        advanceUntilIdle()
        assertEquals(WizardPhase.PLAN_SECTIONS, vm.uiState.value.phase)

        vm.onNextFromPlanSections()
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)

        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.PLAN_SECTIONS, vm.uiState.value.phase)
        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.STRATEGY, vm.uiState.value.phase)
        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.SEED_PICK, vm.uiState.value.phase)
        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
        assertTrue(vm.onBackPressed())
    }

    // (3) COLORS flow chain, forward and back.
    @Test
    fun `COLORS flow chain -- ENTRY to REVIEW forward, onBackPressed mirrors it exactly`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()

        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        assertEquals(WizardPhase.COLOR_PICK, vm.uiState.value.phase)

        vm.onToggleColorFlowColor(ManaColor.U)
        advanceTimeBy(200)
        advanceUntilIdle()
        vm.onSelectCustomStrategy()

        vm.onNextFromColorPick()
        assertEquals(WizardPhase.PLAN_SECTIONS, vm.uiState.value.phase)

        vm.onNextFromPlanSections()
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)

        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.PLAN_SECTIONS, vm.uiState.value.phase)
        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.COLOR_PICK, vm.uiState.value.phase)
        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
        assertTrue(vm.onBackPressed())
    }

    // (3) STRATEGY flow chain, forward and back.
    @Test
    fun `STRATEGY flow chain -- ENTRY to REVIEW forward, onBackPressed mirrors it exactly`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()

        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        advanceUntilIdle()
        assertEquals(WizardPhase.STRATEGY_PICK, vm.uiState.value.phase)

        val strategy = CuratedStrategyCatalog.ALL.first { it.availableIn(DeckFormat.STANDARD) && !it.requiresTribe }
        vm.onSelectStrategyPickEntry(strategy)
        vm.onSelectStrategyPickCombo(strategy, ColorComboSuggestion(setOf(ManaColor.U), 1f))

        vm.onNextFromStrategyPick()
        assertEquals(WizardPhase.PLAN_SECTIONS, vm.uiState.value.phase)

        vm.onNextFromPlanSections()
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)

        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.PLAN_SECTIONS, vm.uiState.value.phase)
        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.STRATEGY_PICK, vm.uiState.value.phase)
        assertFalse(vm.onBackPressed())
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
        assertTrue(vm.onBackPressed())
    }

    // (4) onAddSeed copy-cap rules.
    @Test
    fun `onAddSeed increments a seed's quantity up to 4, rejects the 5th with the copy-cap toast`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        val seed = card(id = "seed-1", name = "Seed One", colorIdentity = emptyList())
        repeat(4) { vm.onAddSeed(seed) }
        assertEquals(4, vm.uiState.value.seeds.first().quantity)

        vm.events.test {
            vm.onAddSeed(seed)
            assertTrue(awaitItem() is DeckWizardEvent.ShowToast)
        }
        assertEquals(4, vm.uiState.value.seeds.first().quantity)
        verify { appContext.getString(R.string.deck_wizard_seed_copy_cap, 4) }
    }

    @Test
    fun `a Vintage-restricted card is capped at 1 copy, the 2nd rejected with the copy-cap toast`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "VINTAGE"))
        advanceUntilIdle()
        val restricted = card(id = "sol-1", name = "Sol Ring", colorIdentity = emptyList(), legalityVintage = "restricted")
        vm.onAddSeed(restricted)
        assertEquals(1, vm.uiState.value.seeds.first().quantity)

        vm.events.test {
            vm.onAddSeed(restricted)
            assertTrue(awaitItem() is DeckWizardEvent.ShowToast)
        }
        assertEquals(1, vm.uiState.value.seeds.first().quantity)
        verify { appContext.getString(R.string.deck_wizard_seed_copy_cap, 1) }
    }

    @Test
    fun `an illegal card is rejected in STANDARD, accepted in CASUAL`() = runTest(dispatcher) {
        val illegalCard = card(id = "illegal-1", name = "Not Standard Legal", colorIdentity = emptyList(), legalityStandard = "not_legal")

        val standardVm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        standardVm.events.test {
            standardVm.onAddSeed(illegalCard)
            assertTrue(awaitItem() is DeckWizardEvent.ShowToast)
        }
        assertTrue(standardVm.uiState.value.seeds.isEmpty())
        verify { appContext.getString(R.string.deck_wizard_manual_add_rejected) }

        val casualVm = viewModel(mapOf("format" to "CASUAL"))
        advanceUntilIdle()
        casualVm.onAddSeed(illegalCard)
        assertEquals(listOf(illegalCard), casualVm.uiState.value.seeds.map { it.card })
    }

    // (5) Seed cap: 60 total copies for a 60-card format, Commander cap 99.
    @Test
    fun `seed cap -- 60 total copies for a 60-card format, the 61st rejected with the seed-cap toast`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        val cards = (1..15).map { i -> card(id = "seed-$i", name = "Seed $i", colorIdentity = emptyList()) }
        cards.forEach { c -> repeat(4) { vm.onAddSeed(c) } }
        assertEquals(60, vm.uiState.value.seedCopies)

        val extra = card(id = "seed-extra", name = "Seed Extra", colorIdentity = emptyList())
        vm.events.test {
            vm.onAddSeed(extra)
            assertTrue(awaitItem() is DeckWizardEvent.ShowToast)
        }
        assertEquals(60, vm.uiState.value.seedCopies)
        verify { appContext.getString(R.string.deck_wizard_seed_cap_reached, 60) }
    }

    @Test
    fun `Commander seed cap is 99, silently ignoring further manual adds`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val cards = (1..99).map { i -> card(id = "cmd-seed-$i", name = "Commander Seed $i", colorIdentity = listOf("G")) }
        cards.forEach { vm.onAddSeed(it) }
        assertEquals(99, vm.uiState.value.seeds.size)

        vm.onAddSeed(card(id = "cmd-seed-extra", name = "Commander Seed Extra", colorIdentity = listOf("G")))
        assertEquals(99, vm.uiState.value.seeds.size)
    }

    // (6) Colorless chip exclusivity + identity {} reaches the build engine.
    @Test
    fun `the colorless chip clears WUBRG and vice versa`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)

        vm.onToggleColorFlowColor(ManaColor.U)
        assertEquals(setOf(ManaColor.U), vm.uiState.value.colorIdentity)

        vm.onToggleColorFlowColor(ManaColor.C)
        assertEquals(setOf(ManaColor.C), vm.uiState.value.colorIdentity)

        vm.onToggleColorFlowColor(ManaColor.W)
        assertEquals(setOf(ManaColor.W), vm.uiState.value.colorIdentity)
    }

    @Test
    fun `a colorless build passes identity {} to the build engine`() = runTest(dispatcher) {
        val anchorSlot = slot<BuildAnchor>()
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), capture(anchorSlot), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome(entries = emptyList())

        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        vm.onToggleColorFlowColor(ManaColor.C)
        advanceTimeBy(200)
        advanceUntilIdle()
        vm.onSelectCustomStrategy()
        vm.onNextFromColorPick()
        vm.onNextFromPlanSections()

        vm.onGenerate()
        advanceUntilIdle()

        val anchor = anchorSlot.captured as BuildAnchor.Sixty
        assertEquals(emptySet<ManaColor>(), anchor.identity)
    }

    // (7) SEED_PICK's idle grid excludes illegal owned cards.
    @Test
    fun `SEED_PICK idle grid excludes illegal owned cards for PAUPER`() {
        val legalCommon = card(id = "legal-1", name = "Legal Common", legalityPauper = "legal")
        val illegalRare = card(id = "illegal-1", name = "Illegal Rare", legalityPauper = "not_legal")
        val state = DeckWizardUiState(selectedFormat = DeckFormat.PAUPER, ownedCards = listOf(legalCommon, illegalRare))

        assertEquals(listOf(legalCommon), seedPickLocalCandidates(state))
    }

    // (8) Seed search locks the format's own legality clause.
    @Test
    fun `SEED_PICK search locks Format(pauper) into the built query`() {
        val locked = seedLockedCriteria(DeckFormat.PAUPER)
        val state = DeckWizardUiState(selectedFormat = DeckFormat.PAUPER, seedPickQuery = "Lightning Bolt")

        val query = buildSeedPickSearchQuery(state, locked)

        assertTrue(query?.criteria?.any { it is SearchCriterion.Format && it.format == listOf("pauper") } == true)
    }

    // (9) recomputeStrategyRecommendations preselects #1, fires on onNextFromSeedPick and on every
    //     color toggle (debounced 150ms).
    @Test
    fun `recomputeStrategyRecommendations preselects #1 on onNextFromSeedPick`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(card(id = "seed-1", name = "Seed One", colorIdentity = emptyList()))

        vm.onNextFromSeedPick()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.strategyRecommendations.isNotEmpty())
        assertEquals(vm.uiState.value.strategyRecommendations.first().strategy.id, vm.uiState.value.selectedCuratedStrategyId)
    }

    @Test
    fun `recomputeStrategyRecommendations re-ranks on every color toggle, debounced 150ms`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        assertTrue(vm.uiState.value.strategyRecommendations.isEmpty())

        vm.onToggleColorFlowColor(ManaColor.U)
        // Before the debounce elapses, nothing has landed yet.
        assertTrue(vm.uiState.value.strategyRecommendations.isEmpty())

        advanceTimeBy(200)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.strategyRecommendations.isNotEmpty())
        assertEquals(vm.uiState.value.strategyRecommendations.first().strategy.id, vm.uiState.value.selectedCuratedStrategyId)
    }

    // (10) 60-card generate: BuildAnchor.Sixty, ManualAdd.quantity, basics pre-warmed, never writes
    //      commanderCardId, persists into launchedFromDeckId, emits OpenDeckStudio.
    @Test
    fun `60-card generate builds a BuildAnchor Sixty with real seed quantities, never writes commanderCardId, persists into launchedFromDeckId`() = runTest(dispatcher) {
        val manualAddsSlot = slot<List<ManualAdd>>()
        val anchorSlot = slot<BuildAnchor>()
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), capture(anchorSlot), any(), any(), capture(manualAddsSlot), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome(entries = emptyList())

        val vm = viewModel(mapOf("format" to "STANDARD", "deckId" to "existing-deck-1"))
        advanceUntilIdle()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        val seedA = card(id = "seed-a", name = "Seed A", colorIdentity = listOf("U"))
        val seedB = card(id = "seed-b", name = "Seed B", colorIdentity = listOf("U"))
        vm.onAddSeed(seedA)
        vm.onAddSeed(seedB)
        vm.onAddSeed(seedB) // a 2nd copy of seedB
        vm.onNextFromSeedPick()
        advanceUntilIdle()
        vm.onNextFromStrategy()
        advanceUntilIdle()
        vm.onNextFromPlanSections()

        vm.events.test {
            vm.onGenerate()
            advanceUntilIdle()
            assertTrue(awaitItem() is DeckWizardEvent.OpenDeckStudio)
        }

        val anchor = anchorSlot.captured as BuildAnchor.Sixty
        assertEquals(setOf(ManaColor.U), anchor.identity)
        assertEquals(setOf(seedA, seedB), anchor.seeds.toSet())

        val manualAdds = manualAddsSlot.captured
        assertEquals(1, manualAdds.first { it.card.scryfallId == seedA.scryfallId }.quantity)
        assertEquals(2, manualAdds.first { it.card.scryfallId == seedB.scryfallId }.quantity)

        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
        coVerify(exactly = 1) { deckRepository.persistWizardBuild(eq("existing-deck-1"), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.createDeck(any(), any(), any()) }
    }

    @Test
    fun `a colorless 60-card build pre-warms Wastes via the card repository`() = runTest(dispatcher) {
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome(entries = emptyList())
        val wastes = card(id = "wastes-1", name = "Wastes", typeLine = "Basic Land", colorIdentity = emptyList())
        coEvery { cardRepository.searchCardByName("Wastes") } returns DataResult.Success(wastes)

        val vm = viewModel(mapOf("format" to "STANDARD", "deckId" to "existing-deck-2"))
        advanceUntilIdle()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        vm.onToggleColorFlowColor(ManaColor.C)
        advanceTimeBy(200)
        advanceUntilIdle()
        vm.onSelectCustomStrategy()
        vm.onNextFromColorPick()
        vm.onNextFromPlanSections()

        vm.onGenerate()
        advanceUntilIdle()

        coVerify(exactly = 1) { cardRepository.searchCardByName("Wastes") }
    }

    // (11) Choice quantity: + blocked at candidateMaxCopies and at the section cap, - frees;
    //      onFinishChoices expands the map to repeated ids.
    @Test
    fun `Choice quantity -- plus is blocked at candidateMaxCopies, minus frees a copy`() = runTest(dispatcher) {
        val multiCard = card(id = "multi-1", name = "Multi Copy Card", colorIdentity = emptyList())
        val draft = commanderDraft(
            ambiguityGroups = listOf(AmbiguityGroup(sectionId = "removal_spot", candidateIds = listOf("multi-1"), remainingSlots = 3)),
            candidatesById = mapOf("multi-1" to multiCard),
            candidateMaxCopies = mapOf("multi-1" to 2),
        )
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns draft
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onChangeChoiceQuantity("removal_spot", "multi-1", 1)
        assertEquals(mapOf("multi-1" to 1), vm.uiState.value.choiceSelections["removal_spot"])

        vm.onChangeChoiceQuantity("removal_spot", "multi-1", 1)
        assertEquals(mapOf("multi-1" to 2), vm.uiState.value.choiceSelections["removal_spot"])

        // Blocked at candidateMaxCopies (2), even though remainingSlots (3) would allow one more.
        vm.onChangeChoiceQuantity("removal_spot", "multi-1", 1)
        assertEquals(mapOf("multi-1" to 2), vm.uiState.value.choiceSelections["removal_spot"])

        vm.onChangeChoiceQuantity("removal_spot", "multi-1", -1)
        assertEquals(mapOf("multi-1" to 1), vm.uiState.value.choiceSelections["removal_spot"])
    }

    @Test
    fun `Choice quantity -- plus is blocked once the section's remainingSlots is reached`() = runTest(dispatcher) {
        val cardA = card(id = "a-1", name = "Card A", colorIdentity = emptyList())
        val cardB = card(id = "b-1", name = "Card B", colorIdentity = emptyList())
        val draft = commanderDraft(
            ambiguityGroups = listOf(AmbiguityGroup(sectionId = "removal_spot", candidateIds = listOf("a-1", "b-1"), remainingSlots = 1)),
            candidatesById = mapOf("a-1" to cardA, "b-1" to cardB),
            candidateMaxCopies = mapOf("a-1" to 1, "b-1" to 1),
        )
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns draft
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onChangeChoiceQuantity("removal_spot", "a-1", 1)
        assertEquals(mapOf("a-1" to 1), vm.uiState.value.choiceSelections["removal_spot"])

        vm.onChangeChoiceQuantity("removal_spot", "b-1", 1)
        assertEquals("the section is full -- b-1 must NOT be added", mapOf("a-1" to 1), vm.uiState.value.choiceSelections["removal_spot"])
    }

    @Test
    fun `onFinishChoices expands the copy-count map into a repeated-id list for finalize`() = runTest(dispatcher) {
        val multiCard = card(id = "multi-1", name = "Multi Copy Card", colorIdentity = emptyList())
        val draft = commanderDraft(
            ambiguityGroups = listOf(AmbiguityGroup(sectionId = "removal_spot", candidateIds = listOf("multi-1"), remainingSlots = 3)),
            candidatesById = mapOf("multi-1" to multiCard),
            candidateMaxCopies = mapOf("multi-1" to 3),
        )
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any<BuildAnchor>(), any(), any(), any(), any(), any(), any(), any())
        } returns draft
        val resolutionsSlot = slot<Map<RoleKey, List<String>>>()
        coEvery { buildCommanderDeckUseCase.finalize(any(), capture(resolutionsSlot), any(), any()) } returns commanderOutcome()
        val vm = viewModel(mapOf("format" to "COMMANDER"))
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onChangeChoiceQuantity("removal_spot", "multi-1", 1)
        vm.onChangeChoiceQuantity("removal_spot", "multi-1", 1)
        vm.onChangeChoiceQuantity("removal_spot", "multi-1", 1)
        vm.onFinishChoices()
        advanceUntilIdle()

        assertEquals(listOf("multi-1", "multi-1", "multi-1"), resolutionsSlot.captured["removal_spot"])
    }

    // (12) The existing Commander test suite (above this section) stays green -- verified by the
    // gate run, not re-asserted here.

    // ── Gate 5 audits (compose-design-reviewer + android-edge-case-tester) -- P1 regression tests ──

    @Test
    fun `onAddSeed collapses two printings of the same name into ONE entry, capped by name across printings`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("format" to "STANDARD"))
        advanceUntilIdle()
        val printingA = card(id = "bolt-a", name = "Lightning Bolt", colorIdentity = emptyList())
        val printingB = card(id = "bolt-b", name = "Lightning Bolt", colorIdentity = emptyList())

        repeat(4) { vm.onAddSeed(printingA) }
        assertEquals(1, vm.uiState.value.seeds.size)
        assertEquals(4, vm.uiState.value.seeds.first().quantity)

        // Adding a DIFFERENT printing of the SAME name must increment printing A's entry (or be
        // rejected at the cap) -- never create a second WizardSeed for "Lightning Bolt".
        vm.events.test {
            vm.onAddSeed(printingB)
            assertTrue(awaitItem() is DeckWizardEvent.ShowToast)
        }
        assertEquals(1, vm.uiState.value.seeds.size)
        assertEquals(4, vm.uiState.value.seeds.sumOf { it.quantity })
        verify { appContext.getString(R.string.deck_wizard_seed_copy_cap, 4) }
    }

    @Test
    fun `combo-seed resolution is cancelled when the flow is switched before it completes`() = runTest(dispatcher) {
        val basaltMonolith = card(id = "basalt-1", name = "Basalt Monolith")
        // Never resolves within this test's own virtual-time window -- runCurrent() below lets the
        // resolution START (and suspend here) without letting runTest's scheduler fast-forward
        // through the delay the way advanceUntilIdle() would.
        coEvery { searchCardsUseCase("Basalt Monolith", any()) } coAnswers {
            delay(5_000)
            DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(cards = listOf(basaltMonolith), hasMore = false, totalCards = 1))
        }

        val vm = viewModel(mapOf("format" to "CASUAL", "seeds" to "Basalt Monolith"))
        runCurrent() // profile loads; resolveComboSeeds launches and suspends inside its own network lookup
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS) // switches flow BEFORE the combo card resolves
        advanceUntilIdle()

        assertTrue(
            "a card resolved by an already-cancelled combo-seed job must never be added",
            vm.uiState.value.seeds.isEmpty(),
        )
    }
}
