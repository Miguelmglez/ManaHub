package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-10

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
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
import com.mmg.manahub.feature.decks.domain.template.BuildCommanderDeckUseCase
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.BuildStage
import com.mmg.manahub.feature.decks.domain.template.CommanderBuildOutcome
import com.mmg.manahub.feature.decks.domain.template.CommanderDraftBuild
import com.mmg.manahub.feature.decks.domain.template.CategorySuggestions
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.DeckTemplateArchetypeInfo
import com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec
import com.mmg.manahub.feature.decks.domain.template.OwnedCard
import com.mmg.manahub.feature.decks.domain.template.SuggestionCategory
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildProgress
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.template.TemplateCardSuggestion
import com.mmg.manahub.feature.decks.domain.template.TemplateSource
import com.mmg.manahub.feature.decks.domain.template.WizardBuildResult
import com.mmg.manahub.feature.decks.domain.template.WizardFillStats
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
 * [BuildDeckFromTemplateUseCase] is mocked so the Flow<TemplateBuildProgress> sequence is fully
 * controllable per test (mirrors [DeckStudioViewModelTest]'s split between real and mocked
 * use cases). [CollectionProfileUseCase] runs REAL (cheap, deterministic, avoids stubbing burden).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckWizardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val deckRepository = mockk<DeckRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>()
    private val buildDeckFromTemplateUseCase = mockk<BuildDeckFromTemplateUseCase>()
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
    // own `invoke()` (its internals are BuildCommanderDeckUseCaseTest's job, not this VM test's),
    // but leaves `persist()` running for real so this file's write-path assertions
    // (replaceAllCardsWithSource/updateArchetypeOverride/etc.) still exercise real behavior against
    // the mocked deckRepository above.
    private val buildCommanderDeckUseCase = spyk(BuildCommanderDeckUseCase(deckAnalysisPipeline, crashReporter))
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

    private fun buildResult(deckCards: List<DeckEntry> = emptyList()) = TemplateBuildResult(
        deckCards = deckCards,
        communitySuggestions = emptyList(),
        report = emptyList(),
        templateSource = TemplateSource.SYNTHETIC,
        // Deck Analysis Engine v3 removed ArchetypeId.GENERIC -- a null archetype is the new
        // "no macro pin" state.
        archetypeInfo = DeckTemplateArchetypeInfo(archetype = null, themes = emptyList()),
        archetypeOverride = null,
        themesOverride = emptyList(),
        colorConsistencyWarning = false,
        gamePlan = null,
    )

    /** Deck Wizard Commander v3 plan, Phase 6 -- a minimal, valid [CommanderBuildOutcome] fixture
     * for stubbing [buildCommanderDeckUseCase] in a Commander-format `onGenerate()` test. [entries]
     * defaults to just the commander's own qty-1 mainboard slot (mirrors [buildResult]'s
     * empty-deckCards convention above). */
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

    /** Deck Wizard v4, W7 Task B -- a minimal, relaxed [CommanderDraftBuild] fixture for stubbing
     * [buildCommanderDeckUseCase]'s [BuildCommanderDeckUseCase.buildWithGroups] in a Commander-format
     * `onGenerate()` test. Every unspecified property reads back as an empty/relaxed default
     * (mockk's own contract) -- a test overrides only what it actually inspects: [ambiguityGroups]
     * (empty by default -- the zero-group path is what most existing tests exercise, matching the
     * pre-W7 single-shot build they were written against), [tentativeByRole], [candidatesById]. */
    private fun commanderDraft(
        ambiguityGroups: List<AmbiguityGroup> = emptyList(),
        tentativeByRole: Map<RoleKey, List<String>> = emptyMap(),
        candidatesById: Map<String, Card> = emptyMap(),
    ): CommanderDraftBuild {
        val draft = mockk<CommanderDraftBuild>(relaxed = true)
        every { draft.ambiguityGroups } returns ambiguityGroups
        every { draft.tentativeByRole } returns tentativeByRole
        every { draft.candidatesById } returns candidatesById
        return draft
    }

    private val suggestionCard = card(id = "sugg-1", name = "Suggested Spell")

    /** [buildResult] with an empty [TemplateBuildResult.deckCards] (so [writeResultIntoNewDeck]
     * never calls `addCardToDeck` itself) plus one view-only community suggestion, isolating the
     * `addCardToDeck` call count to whatever [DeckWizardViewModel.onAddCommunitySuggestion] fires. */
    private fun suggestionResult() = buildResult().copy(
        communitySuggestions = listOf(
            CategorySuggestions(
                category = SuggestionCategory.OTHER,
                suggestions = listOf(TemplateCardSuggestion(card = suggestionCard, weight = 1f, suggestedCopies = 1)),
            )
        )
    )

    private fun viewModel(
        savedState: Map<String, Any?> = emptyMap(),
        userPreferences: UserPreferencesDataStore? = null,
    ) = DeckWizardViewModel(
        deckRepository = deckRepository,
        userCardRepository = userCardRepository,
        collectionProfileUseCase = collectionProfileUseCase,
        buildDeckFromTemplateUseCase = buildDeckFromTemplateUseCase,
        searchCardsUseCase = searchCardsUseCase,
        communityAggregateRepository = communityAggregateRepository,
        crashReporter = crashReporter,
        appContext = appContext,
        savedStateHandle = SavedStateHandle(savedState),
        // Deck Engine Unification plan (§5 Phase 3) -- both new use cases are pure/dependency-free,
        // so tests run them REAL (no stubbing burden, mirrors this file's own CollectionProfileUseCase
        // precedent) unless a test needs to isolate a specific ranking/coherence outcome.
        userPreferences = userPreferences,
        cardStrategyTagsRepository = cardStrategyTagsRepository,
        deckAnalysisPipeline = deckAnalysisPipeline,
        buildCommanderDeckUseCase = buildCommanderDeckUseCase,
        cardRepository = cardRepository,
        wizardPreferenceStore = wizardPreferenceStore,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { appContext.getString(any()) } returns "TPL"
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

    // Deck Wizard v4 (R13): there is no FORMAT phase any more -- a route without a "format" nav
    // arg (a fixture built with an empty savedState, mirroring a corrupted deep link) falls back to
    // CASUAL and lands on ENTRY, exactly as if a real Casual deck had launched the wizard.
    @Test
    fun `no format nav arg falls back to CASUAL and lands on ENTRY`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(DeckFormat.CASUAL, vm.uiState.value.selectedFormat)
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
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

    @Test
    fun `picking cards then colors then strategy from the entry chooser all reach DIRECTION`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)
        assertEquals(WizardEntryFlow.CARDS, vm.uiState.value.entryFlow)
    }

    // ── Entry chooser (Deck Engine Unification plan §5 Phase 3.1) ────────────

    @Test
    fun `a Casual format advances to ENTRY, not DIRECTION -- the chooser is a real new step`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
    }

    @Test
    fun `Commander skips ENTRY entirely, forces the CARDS flow, and routes to COMMANDER_PICK (Workstream 2)`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        assertEquals(WizardPhase.COMMANDER_PICK, vm.uiState.value.phase)
        assertEquals(WizardEntryFlow.CARDS, vm.uiState.value.entryFlow)
    }

    @Test
    fun `onSelectEntryFlow COLORS lands on DIRECTION with the colors flow active`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)
        assertEquals(WizardEntryFlow.COLORS, vm.uiState.value.entryFlow)
    }

    @Test
    fun `onSelectEntryFlow STRATEGY lands on DIRECTION with the strategy flow active`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)
        assertEquals(WizardEntryFlow.STRATEGY, vm.uiState.value.entryFlow)
    }

    @Test
    fun `back from ENTRY exits the wizard (R13 -- no FORMAT step to return to)`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
        assertTrue(vm.onBackPressed())
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
    }

    @Test
    fun `back from DIRECTION returns to ENTRY for Casual, but exits the wizard for Commander (W1_1)`() = runTest(dispatcher) {
        val casual = viewModel()
        advanceUntilIdle()
        casual.onSelectFormat(DeckFormat.CASUAL)
        casual.onNextFromFormat()
        casual.onSelectEntryFlow(WizardEntryFlow.COLORS)
        casual.onBackPressed()
        assertEquals(WizardPhase.ENTRY, casual.uiState.value.phase)

        // W1.1 (G1/R1): onNextFromFormat() lands Commander directly on COMMANDER_PICK (never
        // DIRECTION), and that step's own back action now exits the wizard -- no FORMAT step left.
        val commander = viewModel()
        advanceUntilIdle()
        commander.onSelectFormat(DeckFormat.COMMANDER)
        commander.onNextFromFormat()
        assertEquals(WizardPhase.COMMANDER_PICK, commander.uiState.value.phase)
        assertTrue(commander.onBackPressed())
        assertEquals(WizardPhase.COMMANDER_PICK, commander.uiState.value.phase)
    }

    @Test
    fun `Flow B-C now land on MANUAL_ADDS (Workstream 3), and back mirrors it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        vm.onToggleColorFlowColor(ManaColor.R)
        // Workstream 3 -- the COLORS flow now ALSO requires a real strategy pick before advancing
        // (unified with Flow A/C's own gate), not just a color pick.
        val entry = vm.uiState.value.colorAffinityEntries.first()
        vm.onSelectColorAffinityEntry(entry)
        vm.onNextFromDirection()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.manualAddsSkeleton)

        vm.onBackPressed()
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)
    }

    @Test
    fun `a non-v1 format is rejected -- selectedFormat is unchanged`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        // Deck Wizard v4 (R13): init already resolved a real format (CASUAL, the no-arg fallback) --
        // onSelectFormat's own v1-only guard must leave that untouched, not null it out.
        assertEquals(DeckFormat.CASUAL, vm.uiState.value.selectedFormat)
        vm.onSelectFormat(DeckFormat.STANDARD)
        assertEquals(DeckFormat.CASUAL, vm.uiState.value.selectedFormat)
    }

    @Test
    fun `COMMANDER_PICK requires a commander before advancing, and shows a toast (Workstream 2)`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        assertEquals(WizardPhase.COMMANDER_PICK, vm.uiState.value.phase)

        vm.events.test {
            vm.onNextFromCommanderPick()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.ShowToast)
        }
        assertEquals(WizardPhase.COMMANDER_PICK, vm.uiState.value.phase)
    }

    @Test
    fun `Strategy flow requires colors before advancing even with an archetype already picked, and shows a toast`() = runTest(dispatcher) {
        // Edge-case audit follow-up (RUN 3b QA fix): onSelectTaxonomyArchetype resets colorIdentity
        // to emptySet() -- a user who picks an archetype and taps Next WITHOUT ever tapping a combo
        // chip must be blocked, mirroring the COLORS-flow guard right above this one in the VM.
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)
        assertTrue(vm.uiState.value.colorIdentity.isEmpty())

        vm.events.test {
            vm.onNextFromDirection()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.ShowToast)
        }
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)

        // Picking a color combo unblocks it -- Workstream 3: lands on MANUAL_ADDS now, not REVIEW.
        val combo = vm.uiState.value.colorComboSuggestions.first()
        vm.onSelectColorCombo(combo)
        vm.onNextFromDirection()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
    }

    @Test
    fun `picking a commander pre-fills color identity from it, and Next advances to STRATEGY (Workstream 2)`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()

        assertEquals(setOf(ManaColor.G), vm.uiState.value.colorIdentity)
        vm.onNextFromCommanderPick()
        assertEquals(WizardPhase.STRATEGY, vm.uiState.value.phase)
    }

    // ── Commander flow (Deck Wizard & Engine Rework plan, Workstream 2) ──────────

    @Test
    fun `Commander full step chain -- COMMANDER_PICK to STRATEGY to MANUAL_ADDS to REVIEW, and back mirrors it`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        assertEquals(WizardPhase.COMMANDER_PICK, vm.uiState.value.phase)

        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        assertEquals(WizardPhase.STRATEGY, vm.uiState.value.phase)

        vm.onNextFromStrategy()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)

        vm.onNextFromManualAdds()
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)

        // Back navigation mirrors the forward chain exactly (2.4).
        vm.onBackPressed()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
        vm.onBackPressed()
        assertEquals(WizardPhase.STRATEGY, vm.uiState.value.phase)
        vm.onBackPressed()
        assertEquals(WizardPhase.COMMANDER_PICK, vm.uiState.value.phase)
        // W1.1 (G1/R1): COMMANDER_PICK is now the first Commander step -- its own back action exits
        // the wizard (no FORMAT step left to route to) instead of mutating phase.
        assertTrue(vm.onBackPressed())
        assertEquals(WizardPhase.COMMANDER_PICK, vm.uiState.value.phase)
    }

    @Test
    fun `STRATEGY step Custom pick advances with no pick at all (D6) and resolves NO skeleton`() = runTest(dispatcher) {
        // Deck Wizard Commander v3 plan (Phase 4): the #1 recommendation is now PRESELECTED on
        // arrival (plan §8 default) -- this test explicitly switches to Custom (D6, "no pin") to
        // exercise the "GENERIC/no theme" gate BuildDeckFromTemplateUseCase.resolveArchetypeSkeleton
        // also honors -- the real build never resolves an archetype-flavored skeleton for an unpinned
        // deck (Motor A scores with zero theme bonus), so this UI-only preview must not show one
        // either.
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()

        vm.onSelectCustomStrategy()
        assertNull(vm.uiState.value.selectedArchetype)
        assertNull(vm.uiState.value.selectedCuratedStrategyId)
        vm.onNextFromStrategy()

        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
        assertNull("a Custom pick with no themes must resolve NO skeleton, matching the real build's own gate", vm.uiState.value.manualAddsSkeleton)
    }

    @Test
    fun `selecting a commander preselects the #1 STRATEGY recommendation (plan default)`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()

        val recommendations = vm.uiState.value.commanderStrategyRecommendations
        assertTrue(recommendations.isNotEmpty())
        assertEquals(recommendations.first().strategy.id, vm.uiState.value.selectedCuratedStrategyId)
    }

    @Test
    fun `onSelectCommanderStrategy and onSelectCustomStrategy update the STRATEGY step's own selection`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()

        val tokensStrategy = CuratedStrategyCatalog.byId("tokens")!!
        vm.onSelectCommanderStrategy(tokensStrategy)
        assertEquals("tokens", vm.uiState.value.selectedCuratedStrategyId)
        assertEquals(listOf(ThemeId.TOKENS), vm.uiState.value.selectedStrategyThemes)

        // Casual's own single-theme slot (selectedDirectionTheme) is untouched -- the two flows use
        // SEPARATE fields for the theme axis (see DeckWizardUiState.selectedStrategyThemes' KDoc).
        assertNull(vm.uiState.value.selectedDirectionTheme)

        vm.onSelectCustomStrategy()
        assertNull(vm.uiState.value.selectedArchetype)
        assertNull(vm.uiState.value.selectedCuratedStrategyId)
    }

    @Test
    fun `Phase 5 F3 gap -- picking a posture-bearing strategy (Voltron) now persists selectedPosture`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()

        val voltronStrategy = CuratedStrategyCatalog.byId("voltron")!!
        vm.onSelectCommanderStrategy(voltronStrategy)

        assertEquals(com.mmg.manahub.feature.decks.domain.engine.PostureId.VOLTRON, vm.uiState.value.selectedPosture)

        // Custom clears every pin field, posture included.
        vm.onSelectCustomStrategy()
        assertNull(vm.uiState.value.selectedPosture)
    }

    @Test
    fun `a requiresTribe strategy with no derivable tribe opens the sub-picker instead of applying`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()

        val tribalStrategy = CuratedStrategyCatalog.byId("tribal")!!
        vm.onSelectCommanderStrategy(tribalStrategy)
        advanceUntilIdle()

        assertEquals(tribalStrategy, vm.uiState.value.pendingTribeStrategy)

        vm.onPickTribeForStrategy("tribe:elf")
        assertNull(vm.uiState.value.pendingTribeStrategy)
        assertEquals("tribal", vm.uiState.value.selectedCuratedStrategyId)
        assertEquals("tribe:elf", vm.uiState.value.selectedTribeKey)
    }

    @Test
    fun `cancelling the tribe sub-picker leaves the previous selection untouched`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        val previousSelection = vm.uiState.value.selectedCuratedStrategyId

        vm.onRequestTribeForStrategy(CuratedStrategyCatalog.byId("tribal")!!)
        advanceUntilIdle()
        vm.onCancelTribePickForStrategy()

        assertNull(vm.uiState.value.pendingTribeStrategy)
        assertEquals(previousSelection, vm.uiState.value.selectedCuratedStrategyId)
    }

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

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
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
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
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
        assertTrue(vm.uiState.value.seedCards.none { it.scryfallId == offColorCard.scryfallId })
    }

    @Test
    fun `PLAN_SECTIONS -- a manual add banned in strict Commander is rejected`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val bannedCard = card(id = "banned-1", name = "Banned Card", colorIdentity = listOf("G"), legalityCommander = "banned")
        vm.onAddSeed(bannedCard)

        assertTrue(vm.uiState.value.seedCards.none { it.scryfallId == bannedCard.scryfallId })
    }

    @Test
    fun `PLAN_SECTIONS -- two different printings of the same card name are deduped, basics exempt`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val printingA = card(id = "dup-a", name = "Sol Ring", colorIdentity = emptyList())
        val printingB = card(id = "dup-b", name = "Sol Ring", colorIdentity = emptyList())
        vm.onAddSeed(printingA)
        vm.onAddSeed(printingB)
        assertEquals(1, vm.uiState.value.seedCards.size)

        val forestA = card(id = "forest-a", name = "Forest", typeLine = "Basic Land — Forest", colorIdentity = emptyList())
        val forestB = card(id = "forest-b", name = "Forest", typeLine = "Basic Land — Forest", colorIdentity = emptyList())
        vm.onAddSeed(forestA)
        vm.onAddSeed(forestB)
        assertEquals(3, vm.uiState.value.seedCards.size)
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

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val unownedCard = card(id = "unowned-1", name = "Unowned Card", colorIdentity = listOf("G"))
        vm.onAddSeed(unownedCard)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.seedCards.any { it.scryfallId == unownedCard.scryfallId })
        val entry = capturedMainboards.last().first { it.card.scryfallId == unownedCard.scryfallId }
        assertFalse(entry.isOwned)
    }

    @Test
    fun `D-A -- commander search stays off by default, and only fires once includeOutsideCollection is toggled on`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        assertTrue(!vm.uiState.value.includeOutsideCollection)

        vm.onCommanderQueryChange("Zada")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.commanderSearchResults.isEmpty())
        coVerify(exactly = 0) { searchCardsUseCase(any(), any()) }

        vm.onToggleIncludeOutsideCollection()
        assertTrue(vm.uiState.value.includeOutsideCollection)
        coEvery { searchCardsUseCase("is:commander Zada", any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(commander), hasMore = false, totalCards = 1)
        )

        vm.onCommanderQueryChange("Zada")
        advanceUntilIdle()

        assertEquals(listOf(commander), vm.uiState.value.commanderSearchResults)
    }

    // ── Deck Wizard Commander v3 plan (Phase 3.2): the two-tab structured search ────────────────

    @Test
    fun `onCommanderNameFilterChange -- idle to search to idle-again`() = runTest(dispatcher) {
        coEvery { searchCardsUseCase(any(), any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(commander), hasMore = false, totalCards = 1)
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()

        // Idle: no query text, no filter -- no Scryfall call.
        assertTrue(vm.uiState.value.commanderQuery.isBlank())
        coVerify(exactly = 0) { searchCardsUseCase(any(), any()) }

        // Non-blank query text crosses into search -- debounced, then a real Scryfall call.
        vm.onCommanderNameFilterChange("Zada")
        assertEquals("Zada", vm.uiState.value.commanderQuery)
        coVerify(exactly = 0) { searchCardsUseCase(any(), any()) }
        advanceUntilIdle()
        coVerify(exactly = 1) { searchCardsUseCase(any(), any()) }
        assertEquals(listOf(commander), vm.uiState.value.commanderSearchResults)

        // Clearing the query text back to blank returns to idle -- stale results are cleared, not shown.
        vm.onCommanderNameFilterChange("")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.commanderSearchResults.isEmpty())
    }

    @Test
    fun `applyCommanderStructuredSearch with ONLY the locked criteria stays idle -- no Scryfall call`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()

        // Opening the sheet and hitting Search with nothing else picked -- the sheet force-merges
        // the locked criteria in, so this is exactly what a no-op Search tap looks like.
        vm.applyCommanderStructuredSearch(
            com.mmg.manahub.core.model.AdvancedSearchQuery(criteria = commanderLockedCriteria(DeckFormat.COMMANDER))
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { searchCardsUseCase(any(), any()) }
        assertTrue(vm.uiState.value.commanderSearchResults.isEmpty())
    }

    @Test
    fun `applyCommanderStructuredSearch with a real added criterion fetches results immediately (no debounce)`() = runTest(dispatcher) {
        coEvery { searchCardsUseCase("is:commander id<=g", any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(commander), hasMore = false, totalCards = 1)
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()

        vm.applyCommanderStructuredSearch(
            com.mmg.manahub.core.model.AdvancedSearchQuery(
                criteria = listOf(com.mmg.manahub.core.model.SearchCriterion.CommanderEligible, com.mmg.manahub.core.model.SearchCriterion.ColorIdentity(setOf("G"))),
            )
        )
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.commanderStructuredQuery)
        assertEquals(listOf(commander), vm.uiState.value.commanderSearchResults)
    }

    @Test
    fun `triggerCommanderPickSearch sets commanderSearchError on failure and clears it on the next successful search`() = runTest(dispatcher) {
        coEvery { searchCardsUseCase(any(), any()) } returns DataResult.Error("Scryfall down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()

        vm.onCommanderNameFilterChange("Zada")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.commanderSearchError)
        assertTrue(vm.uiState.value.commanderSearchResults.isEmpty())

        coEvery { searchCardsUseCase(any(), any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(commander), hasMore = false, totalCards = 1)
        )
        vm.onCommanderNameFilterChange("Zada Rovni")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.commanderSearchError)
        assertEquals(listOf(commander), vm.uiState.value.commanderSearchResults)
    }

    @Test
    fun `onClearCommanderFilters drops the structured query but keeps the search text`() = runTest(dispatcher) {
        coEvery { searchCardsUseCase(any(), any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(commander), hasMore = false, totalCards = 1)
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onCommanderNameFilterChange("Zada")
        advanceUntilIdle()
        vm.applyCommanderStructuredSearch(
            com.mmg.manahub.core.model.AdvancedSearchQuery(
                criteria = commanderLockedCriteria(DeckFormat.COMMANDER) + com.mmg.manahub.core.model.SearchCriterion.ColorIdentity(setOf("G")),
            )
        )
        advanceUntilIdle()

        vm.onClearCommanderFilters()
        advanceUntilIdle()

        assertNull(vm.uiState.value.commanderStructuredQuery)
        assertEquals("Zada", vm.uiState.value.commanderQuery)
    }

    @Test
    fun `onClearCommanderSearchAndFilters clears both and returns to the idle owned grid`() = runTest(dispatcher) {
        coEvery { searchCardsUseCase(any(), any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(commander), hasMore = false, totalCards = 1)
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onCommanderNameFilterChange("Zada")
        advanceUntilIdle()

        vm.onClearCommanderSearchAndFilters()
        advanceUntilIdle()

        assertNull(vm.uiState.value.commanderStructuredQuery)
        assertTrue(vm.uiState.value.commanderQuery.isBlank())
        assertTrue(vm.uiState.value.commanderSearchResults.isEmpty())
    }

    @Test
    fun `locked criteria survive a clear-and-repeat-search cycle, never droppable by the user`() = runTest(dispatcher) {
        val capturedFragments = mutableListOf<String>()
        coEvery { searchCardsUseCase(any(), any()) } answers {
            capturedFragments += firstArg<String>()
            DataResult.Success(com.mmg.manahub.core.model.PaginatedCards(cards = emptyList(), hasMore = false, totalCards = 0))
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()

        vm.onCommanderNameFilterChange("Meren")
        advanceUntilIdle()
        vm.onClearCommanderSearchAndFilters()
        advanceUntilIdle()
        vm.onCommanderNameFilterChange("Karlov")
        advanceUntilIdle()

        assertTrue(capturedFragments.all { it.contains("is:commander") })
    }

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

    @Test
    fun `selecting a commander clears any in-flight structured search state`() = runTest(dispatcher) {
        coEvery { searchCardsUseCase("is:commander", any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(commander), hasMore = false, totalCards = 1)
        )
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.applyCommanderStructuredSearch(
            com.mmg.manahub.core.model.AdvancedSearchQuery(criteria = listOf(com.mmg.manahub.core.model.SearchCriterion.CommanderEligible))
        )
        advanceUntilIdle()

        vm.onSelectCommander(commander)
        advanceUntilIdle()

        assertNull(vm.uiState.value.commanderStructuredQuery)
        assertTrue(vm.uiState.value.commanderSearchResults.isEmpty())
    }

    @Test
    fun `selecting a commander computes STRATEGY recommendations informed by its own card_strategy_tags (source 1)`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery { cardStrategyTagsRepository.getStrategyTags(any()) } returns CardStrategyTagsResult.Found(
            tags = listOf(CardTag.TRIBAL),
            tribes = listOf("elf"),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()

        vm.onSelectCommander(commander)
        advanceUntilIdle()

        val recommendations = vm.uiState.value.commanderStrategyRecommendations
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.all { it.strategy.availableIn(DeckFormat.COMMANDER) })
    }

    @Test
    fun `Casual CARDS direction requires a strategy pick and colors before advancing (Workstream 3, D-B generalized)`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)

        vm.events.test {
            vm.onNextFromDirection()
            assertTrue(awaitItem() is DeckWizardEvent.ShowToast)
        }
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)

        // A strategy pick alone (no colors) still blocks.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)
        vm.events.test {
            vm.onNextFromDirection()
            assertTrue(awaitItem() is DeckWizardEvent.ShowToast)
        }
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)

        // Both a strategy AND a color -- advances to the shared MANUAL_ADDS step.
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.manualAddsSkeleton)
    }

    // ── Flow A -- Workstream 3.1 locked-color invariants ─────────────────────────

    @Test
    fun `adding a seed locks its colors into colorIdentity immediately`() = runTest(dispatcher) {
        val seed = card(id = "seed-1", name = "Bicolor Seed", colorIdentity = listOf("U", "R"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)

        vm.onAddSeed(seed)

        assertEquals(setOf(ManaColor.U, ManaColor.R), vm.uiState.value.colorIdentity)
        assertEquals(setOf(ManaColor.U, ManaColor.R), vm.uiState.value.lockedColors)
    }

    @Test
    fun `removing a seed unlocks its color, but the color stays picked until manually deselected`() = runTest(dispatcher) {
        val seed = card(id = "seed-1", name = "Bicolor Seed", colorIdentity = listOf("U", "R"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(seed)

        vm.onRemoveSeed(seed)

        assertTrue(vm.uiState.value.lockedColors.isEmpty())
        assertEquals(setOf(ManaColor.U, ManaColor.R), vm.uiState.value.colorIdentity)
    }

    @Test
    fun `a locked color cannot be deselected, but an unlocked color can be freely toggled`() = runTest(dispatcher) {
        val seed = card(id = "seed-1", name = "Mono Seed", colorIdentity = listOf("G"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(seed)
        assertEquals(setOf(ManaColor.G), vm.uiState.value.lockedColors)

        // Cannot deselect the locked color.
        vm.onToggleCardsFlowColor(ManaColor.G)
        assertEquals(setOf(ManaColor.G), vm.uiState.value.colorIdentity)

        // Can freely add/remove an UNLOCKED color.
        vm.onToggleCardsFlowColor(ManaColor.U)
        assertEquals(setOf(ManaColor.G, ManaColor.U), vm.uiState.value.colorIdentity)
        vm.onToggleCardsFlowColor(ManaColor.U)
        assertEquals(setOf(ManaColor.G), vm.uiState.value.colorIdentity)
    }

    @Test
    fun `an explicit color pick survives adding a seed with a different color -- union, not replace`() = runTest(dispatcher) {
        val seed = card(id = "seed-1", name = "Mono Red Seed", colorIdentity = listOf("R"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onToggleCardsFlowColor(ManaColor.G)

        vm.onAddSeed(seed)

        assertEquals(setOf(ManaColor.G, ManaColor.R), vm.uiState.value.colorIdentity)
        assertEquals(setOf(ManaColor.R), vm.uiState.value.lockedColors)
    }

    // ── Flow A -- Workstream 3.1 coherence-hint VM wiring (exhaustive logic already covered by
    //    SuggestStrategiesForSeedsUseCaseTest) ────────────────────────────────────

    @Test
    fun `a seed strategy candidate's misfit hint reflects the currently picked seeds and colors`() = runTest(dispatcher) {
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.AGGRO))
        val offSeed = card(id = "gy-1", name = "Graveyard Piece", tags = listOf(CardTag.GRAVEYARD))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)
        vm.onAddSeed(offSeed)

        val rampCandidate = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.AGGRO }
        assertTrue(rampCandidate.misfitSeeds.any { it.scryfallId == "gy-1" })

        // Removing the misfit seed re-ranks: the SAME candidate no longer carries it as a misfit.
        vm.onRemoveSeed(offSeed)
        val reranked = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.AGGRO }
        assertTrue(reranked.misfitSeeds.isEmpty())
    }

    // ── Flow A/B/C -- Workstream 3 per-flow full step chains ─────────────────────

    @Test
    fun `Flow A full step chain -- FORMAT to ENTRY to DIRECTION to MANUAL_ADDS to REVIEW, and back mirrors it`() = runTest(dispatcher) {
        val seed = card(id = "seed-1", name = "Ramp Seed", tags = listOf(CardTag.AGGRO), colorIdentity = listOf("G"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)

        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)

        vm.onAddSeed(seed)
        val candidate = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.AGGRO }
        vm.onSelectSeedStrategyCandidate(candidate)

        vm.onNextFromDirection()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.manualAddsSkeleton)

        vm.onNextFromManualAdds()
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)

        vm.onBackPressed()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
        vm.onBackPressed()
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)
        vm.onBackPressed()
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
        // Deck Wizard v4 (R13): ENTRY is genuinely the first Casual step now -- back from here
        // exits the wizard instead of routing to a FORMAT step that no longer exists.
        assertTrue(vm.onBackPressed())
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
    }

    @Test
    fun `Flow B full step chain -- DIRECTION to MANUAL_ADDS to REVIEW`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        vm.onToggleColorFlowColor(ManaColor.R)
        val entry = vm.uiState.value.colorAffinityEntries.first()
        vm.onSelectColorAffinityEntry(entry)

        vm.onNextFromDirection()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)

        vm.onNextFromManualAdds()
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)
        vm.onBackPressed()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
        vm.onBackPressed()
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)
    }

    @Test
    fun `Flow C full step chain -- DIRECTION to MANUAL_ADDS to REVIEW`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)
        val combo = vm.uiState.value.colorComboSuggestions.first()
        vm.onSelectColorCombo(combo)

        vm.onNextFromDirection()
        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)

        vm.onNextFromManualAdds()
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)
    }

    @Test
    fun `toggling a color is a no-op for Commander format`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onToggleColor(ManaColor.U)
        assertTrue(vm.uiState.value.colorIdentity.isEmpty())
    }

    @Test
    fun `toggling a color works for Casual format`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onToggleColor(ManaColor.U)
        assertEquals(setOf(ManaColor.U), vm.uiState.value.colorIdentity)
        vm.onToggleColor(ManaColor.U)
        assertTrue(vm.uiState.value.colorIdentity.isEmpty())
    }

    @Test
    fun `3 or more colors on Casual raises the color-discipline hint`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onToggleColor(ManaColor.U)
        vm.onToggleColor(ManaColor.B)
        vm.onToggleColor(ManaColor.R)
        assertTrue(vm.uiState.value.showColorDisciplineHint)
    }

    // ── Discoveries v2 hand-off (D11) ────────────────────────────────────────

    @Test
    fun `nav args pre-fill archetype, theme, tribe, and colors`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("archetype" to "AGGRO", "theme" to "TOKENS", "tribe" to "tribe:elf", "colors" to "BR"))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(ArchetypeId.AGGRO, state.selectedArchetype)
        assertEquals(ThemeId.TOKENS, state.selectedDirectionTheme)
        assertEquals("tribe:elf", state.selectedTribeKey)
        assertEquals(setOf(ManaColor.B, ManaColor.R), state.colorIdentity)
    }

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

    @Test
    fun `no format nav arg falls back to CASUAL (R13 -- format is always required)`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(DeckFormat.CASUAL, state.selectedFormat)
        assertEquals(WizardPhase.ENTRY, state.phase)
    }

    // ── Combo "Use as seed" hand-off (Deck Engine Unification plan D7, 4.3) ──

    @Test
    fun `a seeds nav arg forces Flow A immediately, before the collection loads`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("seeds" to "Sol Ring"))
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

        val vm = viewModel(mapOf("seeds" to "Sol Ring"))
        advanceUntilIdle()

        assertEquals(listOf(solRing), vm.uiState.value.seedCards)
        coVerify(exactly = 0) { searchCardsUseCase(any(), any()) }
    }

    @Test
    fun `an unowned combo card (the missing piece) resolves via a network search`() = runTest(dispatcher) {
        val basaltMonolith = card(id = "basalt-1", name = "Basalt Monolith")
        coEvery { searchCardsUseCase("Basalt Monolith", any()) } returns DataResult.Success(
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(basaltMonolith), hasMore = false, totalCards = 1)
        )

        val vm = viewModel(mapOf("seeds" to "Basalt Monolith"))
        advanceUntilIdle()

        assertEquals(listOf(basaltMonolith), vm.uiState.value.seedCards)
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

        val vm = viewModel(mapOf("seeds" to "Urza, Lord High Artificer|Sol Ring"))
        advanceUntilIdle()

        assertEquals(setOf("Urza, Lord High Artificer", "Sol Ring"), vm.uiState.value.seedCards.map { it.name }.toSet())
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

        val vm = viewModel(mapOf("seeds" to "Sol Ring|Nonexistent Card"))
        advanceUntilIdle()

        assertEquals(listOf(solRing), vm.uiState.value.seedCards)
    }

    // ── Generation ────────────────────────────────────────────────────────────

    @Test
    fun `a successful generate writes the deck and moves to RESULT`() = runTest(dispatcher) {
        val entry = DeckEntry(card = card(id = "spell-1", name = "Forest Spell"), quantity = 2, isOwned = true)
        val result = buildResult(deckCards = listOf(entry))
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Stage(BuildStage.VALIDATING))
            emit(TemplateBuildProgress.Stage(BuildStage.DONE))
            emit(TemplateBuildProgress.Complete(result))
        }

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WizardPhase.RESULT, state.phase)
        assertEquals("wizard-deck-1", state.createdDeckId)
        assertEquals(result, state.buildResult)
        coVerify {
            deckRepository.replaceAllCardsWithSource(
                "wizard-deck-1",
                listOf(com.mmg.manahub.core.domain.repository.CardSlotWrite("spell-1", 2, false, DeckCardSource.WIZARD)),
            )
        }
        coVerify { deckRepository.updateArchetypeOverride("wizard-deck-1", null, emptyList()) }
        // D4: every wizard build pins strategyLocked=true and writes the (here absent) tribe pin.
        coVerify { deckRepository.updateTribeOverride("wizard-deck-1", null) }
        coVerify { deckRepository.updateStrategyLocked("wizard-deck-1", true) }
    }

    // ── BUG-1 regression (Deck Engine Unification plan §0.1) ────────────────────

    @Test
    fun `a Commander build writes the commander as a qty-1 mainboard entry, not just Deck-commanderCardId`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        // Deck Wizard Commander v3 plan, Phase 6 -- Commander now routes through
        // buildCommanderDeckUseCase, never buildDeckFromTemplateUseCase; the placement engine
        // itself is exercised by BuildCommanderDeckUseCaseTest, so this VM test stubs its outcome
        // and verifies the VM's OWN wiring (persist() is real -- see buildCommanderDeckUseCase's
        // spyk() construction above -- so the assertions below still exercise the real write path).
        // W7 Task B split invoke() into buildWithGroups()+finalize() -- a zero-group draft (the
        // default) still reaches finalize() straight away, byte-identical to the old single-shot path.
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), any(), any(), any())
        } returns commanderOutcome()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        // Deck Wizard & Engine Rework plan, Workstream 2 -- the new Commander step sequence
        // (COMMANDER_PICK -> STRATEGY -> MANUAL_ADDS), replacing the old DIRECTION/IDENTITY pair.
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
        // W7 Task D (plan 7.5): Commander never lands on WizardPhase.RESULT any more -- a successful
        // build fires OpenDeckStudio directly (see finalizeCommanderDraft's own KDoc).
        vm.events.test {
            vm.onGenerate()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
            assertEquals("wizard-deck-1", (event as DeckWizardEvent.OpenDeckStudio).deckId)
        }
        // BUG-1: DeckStudioViewModel.rebuildUiState resolves the commander from the deck's ENTRIES
        // (allEntries.find { it.scryfallId == commanderId }), not from Deck.commanderCardId alone --
        // without this write the commander was invisible in the built deck. Since the Phase 8 JOB 2
        // atomicity fix, the commander's qty-1 entry is the FIRST slot in the one
        // persistCommanderBuild call, not a separate addCardToDeck call.
        coVerify {
            deckRepository.persistCommanderBuild(
                deckId = "wizard-deck-1",
                slots = listOf(com.mmg.manahub.core.domain.repository.CardSlotWrite("cmd-1", 1, false, DeckCardSource.WIZARD)),
                archetypeOverride = any(),
                themesOverride = any(),
                posture = any(),
                tribeOverride = any(),
                strategyLocked = any(),
            )
        }
    }

    @Test
    fun `the commander mainboard entry plus BuildCommanderDeckUseCase's placement totals exactly 100`() = runTest(dispatcher) {
        // Deck Wizard Commander v3 plan, Phase 6: BuildCommanderDeckUseCase itself is responsible
        // for the 99-non-commander + 1-commander = 100 total (see its own NON_COMMANDER_SLOTS
        // constant / BuildCommanderDeckUseCaseTest) -- this VM test locks that the WRITE path
        // forwards every entry the outcome reports, in ONE replaceAllCardsWithSource call.
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val ninetyNineEntries = (1..99).map { i ->
            DeckEntry(card = card(id = "spell-$i", name = "Spell $i"), quantity = 1, isOwned = true)
        }
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), any(), any(), any())
        } returns commanderOutcome(entries = listOf(DeckEntry(card = commander, quantity = 1, isOwned = true, isSideboard = false)) + ninetyNineEntries)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        // Deck Wizard & Engine Rework plan, Workstream 2 -- the new Commander step sequence.
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        // 99 deckCards slots + 1 commander slot = 100 total, all in ONE persistCommanderBuild
        // call (Phase 8 JOB 2 atomicity fix).
        val slotsSlot = slot<List<com.mmg.manahub.core.domain.repository.CardSlotWrite>>()
        coVerify { deckRepository.persistCommanderBuild(deckId = "wizard-deck-1", slots = capture(slotsSlot), archetypeOverride = any(), themesOverride = any(), posture = any(), tribeOverride = any(), strategyLocked = any()) }
        assertEquals(100, slotsSlot.captured.size)
    }

    @Test
    fun `W0_2 -- the 94-card bug -- a basic the user owns zero copies of is pre-warmed before a Commander build`() = runTest(dispatcher) {
        // G10 reproduction: a mono-Green commander whose real collection contains ZERO "Forest"
        // rows (the default empty collectionSnapshot from setUp()) used to leave materializeBasics
        // unable to find a Forest Card object, silently dropping every basic land fill would have
        // allocated to Green -- landing short of the 100-card target. The fix pre-warms it here.
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val forest = card(id = "forest-real", name = "Forest", typeLine = "Basic Land — Forest", cmc = 0.0, colors = emptyList(), colorIdentity = listOf("G"))
        coEvery { cardRepository.searchCardByName("Forest") } returns DataResult.Success(forest)
        val ownedCollectionSlot = slot<List<OwnedCard>>()
        coEvery {
            // buildWithGroups' param order (format, commander, strategyPick, identity,
            // ownedCollection, ...) keeps ownedCollection at the SAME position invoke() had it.
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), capture(ownedCollectionSlot), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), any(), any(), any())
        } returns commanderOutcome()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        assertTrue(
            "a Commander build must pre-warm a real Forest Card object even though the collection owns zero, got ${ownedCollectionSlot.captured.map { it.card.name }}",
            ownedCollectionSlot.captured.any { it.card.name == "Forest" },
        )
    }

    @Test
    fun `W5_3 -- the 94-card bug fix still applies with includeNonBasicLands toggled ON`() = runTest(dispatcher) {
        // R12: guaranteeBasicsAvailable runs UNCONDITIONALLY (never gated on the Commander-only
        // includeNonBasicLands toggle) -- this proves the pre-warm survives the toggle either way,
        // not just at its default (OFF).
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val forest = card(id = "forest-real-2", name = "Forest", typeLine = "Basic Land — Forest", cmc = 0.0, colors = emptyList(), colorIdentity = listOf("G"))
        coEvery { cardRepository.searchCardByName("Forest") } returns DataResult.Success(forest)
        val ownedCollectionSlot = slot<List<OwnedCard>>()
        val includeNonBasicLandsSlot = slot<Boolean>()
        coEvery {
            // buildWithGroups has no `fillLands` param -- includeNonBasicLands shifts from the 8th
            // any() (invoke()) to the 7th here (format, commander, strategyPick, identity,
            // ownedCollection, manualAdds, includeNonBasicLands, ...).
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), capture(ownedCollectionSlot), any(), capture(includeNonBasicLandsSlot), any(), any(), any())
        } returns commanderDraft()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), any(), any(), any())
        } returns commanderOutcome()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
        vm.onToggleIncludeNonBasicLands()
        vm.onGenerate()
        advanceUntilIdle()

        assertTrue("the toggle must actually thread through as ON", includeNonBasicLandsSlot.captured)
        assertTrue(
            "the pre-warm must still fire with the toggle ON, got ${ownedCollectionSlot.captured.map { it.card.name }}",
            ownedCollectionSlot.captured.any { it.card.name == "Forest" },
        )
    }

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

    @Test
    fun `R15 -- an unconfirmed launch into a non-empty deck refuses to persist, deck untouched`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), any(), any(), any())
        } returns commanderOutcome()
        every { deckRepository.observeDeckWithCards("existing-deck-1") } returns flowOf(nonEmptyDeckWithCards("existing-deck-1"))

        val vm = viewModel(mapOf("deckId" to "existing-deck-1", "replaceConfirmed" to false))
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()

        vm.events.test {
            vm.onGenerate()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.ShowToast)
            assertEquals(com.mmg.manahub.core.ui.components.MagicToastType.ERROR, (event as DeckWizardEvent.ShowToast).type)
        }
        assertEquals(WizardPhase.GENERATING, vm.uiState.value.phase)
        assertNull(vm.uiState.value.createdDeckId)
        coVerify(exactly = 0) { deckRepository.persistCommanderBuild(any(), any(), any(), any(), any(), any(), any()) }
        coVerify { crashReporter.recordException(any()) }
    }

    @Test
    fun `R15 -- a confirmed launch into a non-empty deck persists normally`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), any(), any(), any())
        } returns commanderOutcome()
        every { deckRepository.observeDeckWithCards("existing-deck-1") } returns flowOf(nonEmptyDeckWithCards("existing-deck-1"))

        val vm = viewModel(mapOf("deckId" to "existing-deck-1", "replaceConfirmed" to true))
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
        // W7 Task D (plan 7.5): a successful Commander build fires OpenDeckStudio directly, never
        // lands on WizardPhase.RESULT.
        vm.events.test {
            vm.onGenerate()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
            assertEquals("existing-deck-1", (event as DeckWizardEvent.OpenDeckStudio).deckId)
        }
        assertEquals("existing-deck-1", vm.uiState.value.createdDeckId)
        coVerify { deckRepository.persistCommanderBuild(deckId = "existing-deck-1", slots = any(), archetypeOverride = any(), themesOverride = any(), posture = any(), tribeOverride = any(), strategyLocked = any()) }
    }

    @Test
    fun `R15 -- an unconfirmed launch into an EMPTY existing deck persists normally, nothing to lose`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), any(), any(), any())
        } returns commanderOutcome()
        every { deckRepository.observeDeckWithCards("existing-deck-2") } returns flowOf(emptyDeckWithCards("existing-deck-2"))

        val vm = viewModel(mapOf("deckId" to "existing-deck-2", "replaceConfirmed" to false))
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
        // W7 Task D (plan 7.5): a successful Commander build fires OpenDeckStudio directly, never
        // lands on WizardPhase.RESULT.
        vm.events.test {
            vm.onGenerate()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
            assertEquals("existing-deck-2", (event as DeckWizardEvent.OpenDeckStudio).deckId)
        }
        assertEquals("existing-deck-2", vm.uiState.value.createdDeckId)
        coVerify { deckRepository.persistCommanderBuild(deckId = "existing-deck-2", slots = any(), archetypeOverride = any(), themesOverride = any(), posture = any(), tribeOverride = any(), strategyLocked = any()) }
    }

    @Test
    fun `calling onGenerate twice in immediate succession only launches one build`() = runTest(dispatcher) {
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow { awaitCancellation() }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()

        // Simulates a double-tap: by the time the second call reads state, the first call's
        // synchronous GENERATING write already landed, so the re-entrancy guard short-circuits it.
        vm.onGenerate()
        vm.onGenerate()
        advanceUntilIdle()

        coVerify(exactly = 1) { buildDeckFromTemplateUseCase(any(), any()) }
        assertEquals(WizardPhase.GENERATING, vm.uiState.value.phase)
    }

    @Test
    fun `a build Failed progress event surfaces buildError and stays in GENERATING`() = runTest(dispatcher) {
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Stage(BuildStage.VALIDATING))
            emit(TemplateBuildProgress.Failed(BuildStage.VALIDATING, "no commander"))
        }

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WizardPhase.GENERATING, state.phase)
        assertEquals("no commander", state.buildError)
        coVerify(exactly = 0) { deckRepository.createDeck(any(), any(), any()) }
    }

    @Test
    fun `onRetryGeneration clears the error and returns to REVIEW`() = runTest(dispatcher) {
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Failed(BuildStage.VALIDATING, "boom"))
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        vm.onRetryGeneration()
        val state = vm.uiState.value
        assertEquals(WizardPhase.REVIEW, state.phase)
        assertNull(state.buildError)
    }

    @Test
    fun `retrying after a partial-write failure deletes the orphaned deck before the next generate`() = runTest(dispatcher) {
        // The build succeeds, but the atomic write inside writeResultIntoNewDeck throws AFTER
        // createDeck() already ran -- pendingDeckId is set to the dangling row.
        val entry = DeckEntry(card = card(id = "spell-1", name = "Forest Spell"), quantity = 1, isOwned = true)
        val result = buildResult(deckCards = listOf(entry))
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
        coEvery { deckRepository.replaceAllCardsWithSource(any(), any()) } throws RuntimeException("write boom")

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        // The write failed -- buildError is surfaced and the orphan has NOT been cleaned up yet.
        assertEquals("TPL", vm.uiState.value.buildError)
        coVerify(exactly = 0) { deckRepository.deleteDeck(any()) }

        vm.onRetryGeneration()
        advanceUntilIdle()

        coVerify(exactly = 1) { deckRepository.deleteDeck("wizard-deck-1") }
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)
        assertNull(vm.uiState.value.buildError)
    }

    @Test
    fun `cancelling generation mid-write deletes the partially-created deck`() = runTest(dispatcher) {
        val entry = DeckEntry(card = card(id = "spell-1", name = "Forest Spell"), quantity = 1, isOwned = true)
        val result = buildResult(deckCards = listOf(entry))
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
        // replaceAllCardsWithSource suspends forever -- lets the test cancel mid-write, AFTER the
        // deck row was already created (pendingDeckId set), verifying the orphan-cleanup delete fires.
        coEvery { deckRepository.replaceAllCardsWithSource(any(), any()) } coAnswers { awaitCancellation() }

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        vm.onCancelGeneration()
        advanceUntilIdle()

        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)
        coVerify { deckRepository.deleteDeck("wizard-deck-1") }
    }

    @Test
    fun `cancelling before the deck was ever created never calls delete`() = runTest(dispatcher) {
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow { awaitCancellation() }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        vm.onCancelGeneration()
        advanceUntilIdle()

        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)
        coVerify(exactly = 0) { deckRepository.deleteDeck(any()) }
    }

    // ── Result ────────────────────────────────────────────────────────────────

    @Test
    fun `onAddCommunitySuggestion double-tap only calls addCardToDeck once`() = runTest(dispatcher) {
        val result = suggestionResult()
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        val suggestion = result.communitySuggestions.first().suggestions.first()
        // Two rapid taps before either coroutine has a chance to run -- the guard is checked
        // synchronously at the top of the function, before viewModelScope.launch, so the second
        // call must be rejected regardless of dispatcher scheduling.
        vm.onAddCommunitySuggestion(SuggestionCategory.OTHER.id, suggestion)
        vm.onAddCommunitySuggestion(SuggestionCategory.OTHER.id, suggestion)
        advanceUntilIdle()

        coVerify(exactly = 1) { deckRepository.addCardToDeck("wizard-deck-1", "sugg-1", 1, false, DeckCardSource.WIZARD) }
    }

    @Test
    fun `onAddCommunitySuggestion can be retried after a failed attempt`() = runTest(dispatcher) {
        val result = suggestionResult()
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
        var callCount = 0
        coEvery { deckRepository.addCardToDeck("wizard-deck-1", "sugg-1", 1, false, DeckCardSource.WIZARD) } coAnswers {
            callCount++
            if (callCount == 1) throw RuntimeException("boom")
        }

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        val suggestion = result.communitySuggestions.first().suggestions.first()
        vm.onAddCommunitySuggestion(SuggestionCategory.OTHER.id, suggestion)
        advanceUntilIdle()
        vm.onAddCommunitySuggestion(SuggestionCategory.OTHER.id, suggestion)
        advanceUntilIdle()

        coVerify(exactly = 2) { deckRepository.addCardToDeck("wizard-deck-1", "sugg-1", 1, false, DeckCardSource.WIZARD) }
    }

    @Test
    fun `onOpenDeckStudio emits an OpenDeckStudio event with the created deck id`() = runTest(dispatcher) {
        val result = buildResult()
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing;
        // satisfying it here avoids a stray blocked-attempt ShowToast sitting in the buffered events
        // channel ahead of the OpenDeckStudio event this test actually asserts on.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        vm.events.test {
            vm.onOpenDeckStudio()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
            assertEquals("wizard-deck-1", (event as DeckWizardEvent.OpenDeckStudio).deckId)
        }
    }

    // ── Flow A -- suggested strategies from seeds (Deck Engine Unification plan §5 Phase 3.2) ──

    @Test
    fun `adding a seed populates a ranked seedStrategySuggestion`() = runTest(dispatcher) {
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.AGGRO))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)

        assertNull(vm.uiState.value.seedStrategySuggestion)
        vm.onAddSeed(rampSeed)
        val suggestion = vm.uiState.value.seedStrategySuggestion
        assertTrue(suggestion != null && suggestion.candidates.isNotEmpty())
        assertTrue(suggestion!!.candidates.any { it.profile.archetype == ArchetypeId.AGGRO })
    }

    @Test
    fun `removing every seed clears seedStrategySuggestion`() = runTest(dispatcher) {
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.AGGRO))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)
        vm.onRemoveSeed(rampSeed)
        assertNull(vm.uiState.value.seedStrategySuggestion)
    }

    @Test
    fun `removing the last seed after picking a strategy clears the stale pick and re-blocks onNextFromDirection (Fix 6)`() = runTest(dispatcher) {
        // Edge-case audit Fix 6: add a seed, select a suggested strategy for it, then remove that
        // SAME (last) seed. Pre-fix, `selectedArchetype` stayed set even though the visible
        // strategy-candidate list recomputes to empty (nothing left to rank against) -- the
        // mandatory-strategy gate in onNextFromDirection would then silently pass on the stale pick
        // and advance to MANUAL_ADDS on a plan the user can no longer see or reconsider.
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.AGGRO))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)
        val candidate = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.AGGRO }
        vm.onSelectSeedStrategyCandidate(candidate)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)

        vm.onRemoveSeed(rampSeed)

        assertNull("removing the last seed must clear the strategy pick it justified", vm.uiState.value.selectedArchetype)
        assertNull(vm.uiState.value.selectedDirectionTheme)
        assertNull(vm.uiState.value.selectedTribeKey)

        // Also need a color pick (CARDS flow's OTHER mandatory-gate condition) so the assertion
        // below proves the block is specifically about the cleared strategy, not colors.
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.events.test {
            vm.onNextFromDirection()
            assertTrue(awaitItem() is DeckWizardEvent.ShowToast)
        }
        assertEquals(
            "onNextFromDirection must NOT advance past DIRECTION on a cleared/stale strategy pick",
            WizardPhase.DIRECTION, vm.uiState.value.phase,
        )
    }

    @Test
    fun `Commander -- removing the LAST manual add does NOT clear the strategy pick (Run 7 follow-up finding)`() = runTest(dispatcher) {
        // Fix 6's Casual rule above is deliberately NOT applied to Commander: the strategy pick is
        // an explicit user choice made on its own STRATEGY step, never inferred from manual adds --
        // removing every Plan Sections manual add must leave it untouched.
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander) // identity = {G}
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        val tokensStrategy = CuratedStrategyCatalog.byId("tokens")!!
        vm.onSelectCommanderStrategy(tokensStrategy)
        vm.onNextFromStrategy()
        advanceUntilIdle()

        val onlyManualAdd = card(id = "manual-1", name = "Manual Add", colorIdentity = listOf("G"))
        vm.onAddSeed(onlyManualAdd)
        assertEquals(listOf(onlyManualAdd), vm.uiState.value.seedCards)

        vm.onRemoveSeed(onlyManualAdd)

        assertTrue(vm.uiState.value.seedCards.isEmpty())
        assertEquals("tokens", vm.uiState.value.selectedCuratedStrategyId)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)
        assertEquals(listOf(ThemeId.TOKENS), vm.uiState.value.selectedStrategyThemes)
    }

    @Test
    fun `onSelectSeedStrategyCandidate picks the same one-slot Direction fields, and toggles off on a second tap`() = runTest(dispatcher) {
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.AGGRO))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)
        val candidate = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.AGGRO }

        vm.onSelectSeedStrategyCandidate(candidate)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)

        vm.onSelectSeedStrategyCandidate(candidate)
        assertNull(vm.uiState.value.selectedArchetype)
    }

    @Test
    fun `the commander counts toward seedStrategySuggestion ranking -- mandatory first seed`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        assertNull(vm.uiState.value.seedStrategySuggestion)
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        // `commander` is a Legendary Creature -- Elf carrying CardTag.TRIBAL; the commander alone
        // (no other seeds picked) is enough to seed a non-empty ranking.
        assertTrue(vm.uiState.value.seedStrategySuggestion?.candidates?.isNotEmpty() == true)
    }

    // ── Flow B -- colors-first (Deck Engine Unification plan §5 Phase 3.3) ──────

    @Test
    fun `toggling a color in the colors flow populates colorAffinityEntries`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)

        assertTrue(vm.uiState.value.colorAffinityEntries.isEmpty())
        vm.onToggleColorFlowColor(ManaColor.R)
        assertEquals(setOf(ManaColor.R), vm.uiState.value.colorIdentity)
        assertTrue(vm.uiState.value.colorAffinityEntries.isNotEmpty())

        // Deselecting back to zero colors clears the ranked list entirely (never shows the generic
        // FALLBACK table as if it meant something for "no colors picked yet").
        vm.onToggleColorFlowColor(ManaColor.R)
        assertTrue(vm.uiState.value.colorAffinityEntries.isEmpty())
    }

    @Test
    fun `selecting a color affinity entry writes the Direction pick and ranks suggested seeds`() = runTest(dispatcher) {
        // colorIdentity = emptyList() (colorless) -- the ranking now filters suggested seeds by the
        // picked color identity (bug fix, RankOwnedCardsForProfileUseCase), so a colorless owned
        // card is a subset of ANY color pick and stays a stable fixture regardless of which color
        // combo this test's Direction pick resolves to.
        val ownedAggroCard = card(id = "aggro-1", name = "Aggro Beater", tags = listOf(CardTag.AGGRO), colorIdentity = emptyList())
        every { userCardRepository.observeCollection() } returns flowOf(listOf(userCardWith(ownedAggroCard)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        vm.onToggleColorFlowColor(ManaColor.R)

        val entry = vm.uiState.value.colorAffinityEntries.first { it.archetype == ArchetypeId.AGGRO }
        vm.onSelectColorAffinityEntry(entry)

        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)
        assertEquals(entry, vm.uiState.value.selectedColorAffinityEntry)
        assertTrue(vm.uiState.value.suggestedSeedCards.any { it.scryfallId == "aggro-1" })

        // Tapping the same entry again clears the pick (toggle, same UX as every other Direction chip).
        vm.onSelectColorAffinityEntry(entry)
        assertNull(vm.uiState.value.selectedArchetype)
        assertNull(vm.uiState.value.selectedColorAffinityEntry)
        assertTrue(vm.uiState.value.suggestedSeedCards.isEmpty())
    }

    // ── Flow C -- strategy-first (Deck Engine Unification plan §5 Phase 3.4) ────

    @Test
    fun `picking a taxonomy archetype ranks color combos, and picking a combo writes colors + suggested seeds`() = runTest(dispatcher) {
        // colorIdentity = emptyList() -- see the colorless-fixture note on the Flow B counterpart above.
        val ownedAggroCard = card(id = "aggro-1", name = "Aggro Beater", tags = listOf(CardTag.AGGRO), colorIdentity = emptyList())
        every { userCardRepository.observeCollection() } returns flowOf(listOf(userCardWith(ownedAggroCard)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)

        assertTrue(vm.uiState.value.colorComboSuggestions.isEmpty())
        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)
        assertTrue(vm.uiState.value.colorComboSuggestions.isNotEmpty())

        val combo = vm.uiState.value.colorComboSuggestions.first()
        vm.onSelectColorCombo(combo)
        assertEquals(combo.colors, vm.uiState.value.colorIdentity)
        assertTrue(vm.uiState.value.suggestedSeedCards.any { it.scryfallId == "aggro-1" })
    }

    @Test
    fun `picking an archetype then a theme is mutually exclusive -- the taxonomy pick is one slot`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)

        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)

        vm.onSelectTaxonomyTheme(ThemeId.TOKENS)
        assertNull(vm.uiState.value.selectedArchetype)
        assertEquals(ThemeId.TOKENS, vm.uiState.value.selectedDirectionTheme)
    }

    @Test
    fun `onToggleSuggestedSeed reuses the same seedCards list -- add then remove`() = runTest(dispatcher) {
        val ownedAggroCard = card(id = "aggro-1", name = "Aggro Beater", tags = listOf(CardTag.AGGRO))
        every { userCardRepository.observeCollection() } returns flowOf(listOf(userCardWith(ownedAggroCard)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        vm.onToggleColorFlowColor(ManaColor.R)
        val entry = vm.uiState.value.colorAffinityEntries.first { it.archetype == ArchetypeId.AGGRO }
        vm.onSelectColorAffinityEntry(entry)

        assertTrue(vm.uiState.value.seedCards.isEmpty())
        vm.onToggleSuggestedSeed(ownedAggroCard)
        assertTrue(vm.uiState.value.seedCards.any { it.scryfallId == "aggro-1" })
        vm.onToggleSuggestedSeed(ownedAggroCard)
        assertTrue(vm.uiState.value.seedCards.isEmpty())
    }

    // ── Review — community-source toggle (Deck Engine Unification plan §5 Phase 3.5) ────────

    @Test
    fun `the community toggle is unavailable, and a no-op, when the global flag is off`() = runTest(dispatcher) {
        val userPreferences = mockk<UserPreferencesDataStore>()
        every { userPreferences.communityEngineEnabledFlow } returns flowOf(false)
        val vm = viewModel(userPreferences = userPreferences)
        advanceUntilIdle()

        assertTrue(!vm.uiState.value.communityEngineAvailable)
        vm.onToggleUseCommunityData()
        assertTrue(!vm.uiState.value.useCommunityData)
    }

    @Test
    fun `the community toggle is available and threads into the build spec when the global flag is on`() = runTest(dispatcher) {
        val userPreferences = mockk<UserPreferencesDataStore>()
        every { userPreferences.communityEngineEnabledFlow } returns flowOf(true)
        val result = buildResult()
        var capturedSpec: DeckWizardSpec? = null
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } answers {
            capturedSpec = firstArg()
            flow { emit(TemplateBuildProgress.Complete(result)) }
        }

        val vm = viewModel(userPreferences = userPreferences)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.communityEngineAvailable)

        vm.onToggleUseCommunityData()
        assertTrue(vm.uiState.value.useCommunityData)

        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        assertEquals(true, capturedSpec?.useCommunityData)
    }

    private fun userCardWith(card: Card) = UserCardWithCard(
        userCard = UserCard(id = "uc-${card.scryfallId}", scryfallId = card.scryfallId),
        card = card,
    )

    // ── QA fix (RUN 3b) -- format/flow-switch scratch-state leaks ────────────────

    @Test
    fun `switching format from Casual to Commander clears the abandoned flow's archetype and colors`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)
        val combo = vm.uiState.value.colorComboSuggestions.first()
        vm.onSelectColorCombo(combo)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)
        assertTrue(vm.uiState.value.colorIdentity.isNotEmpty())

        // Deck Wizard v4 (R13): there is no FORMAT step to back-navigate into any more -- exercise
        // onSelectFormat directly (the thing actually under test), same as a fresh Commander launch
        // of this same VM instance would.
        vm.onSelectFormat(DeckFormat.COMMANDER)

        val state = vm.uiState.value
        assertNull(state.selectedArchetype)
        assertNull(state.selectedDirectionTheme)
        assertTrue(state.colorIdentity.isEmpty())
        assertTrue(state.colorComboSuggestions.isEmpty())
        assertTrue(state.suggestedSeedCards.isEmpty())
        assertEquals(WizardEntryFlow.CARDS, state.entryFlow)

        // The STALE Casual-taxonomy archetype (picked before backing out) must never reach the
        // Commander build -- Deck Wizard Commander v3 plan Phase 4 now preselects a REAL
        // recommendation for the NEW commander on its own (product default, plan §8), so the
        // no-stale-leak invariant is verified against THAT commander's own top pick, not `null`.
        // Deck Wizard Commander v3 plan, Phase 6: Commander now builds via buildCommanderDeckUseCase
        // -- capture the strategyPick it actually receives (a StrategyPick.Curated wrapping the
        // preselected recommendation, never the stale Casual selectedArchetype/strategyProfile).
        val strategyPickSlot = slot<com.mmg.manahub.feature.decks.domain.engine.StrategyPick>()
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            // strategyPick is position 3 in BOTH invoke() and buildWithGroups() -- unaffected by
            // buildWithGroups dropping the `fillLands` param (which sits AFTER this position).
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), capture(strategyPickSlot), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), any(), any(), any())
        } returns commanderOutcome()
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        val topPickArchetype = vm.uiState.value.commanderStrategyRecommendations.firstOrNull()?.strategy?.archetypes?.first()
        // Deck Wizard & Engine Rework plan, Workstream 2 -- the new Commander step sequence.
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        val capturedPick = strategyPickSlot.captured
        val capturedArchetype = (capturedPick as? com.mmg.manahub.feature.decks.domain.engine.StrategyPick.Curated)?.strategy?.archetypes?.first()
        assertEquals(topPickArchetype, capturedArchetype)
    }

    @Test
    fun `switching format from Commander to Casual clears the stale commander from naming and build`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        assertEquals(commander, vm.uiState.value.selectedCommander)

        // W1.1 (G1/R1): onBackPressed() from COMMANDER_PICK now exits the wizard instead of
        // routing to FORMAT (that step no longer exists in the Commander flow) -- this test's real
        // subject is onSelectFormat's own stale-commander-clearing guard, so it exercises that
        // directly rather than simulating a back-navigation path that is no longer reachable.
        vm.onSelectFormat(DeckFormat.CASUAL)

        val state = vm.uiState.value
        assertNull(state.selectedCommander)
        assertTrue(state.colorIdentity.isEmpty())

        var capturedSpec: DeckWizardSpec? = null
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } answers {
            capturedSpec = firstArg()
            flow { emit(TemplateBuildProgress.Complete(buildResult())) }
        }
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        // Workstream 3 -- CARDS now requires a real strategy pick + a color set before advancing,
        // and lands on the shared MANUAL_ADDS step (onNextFromIdentity is unreachable dead code post
        // this workstream, see DeckWizardViewModel.onNextFromDirection's KDoc) -- these tests only
        // care about the resulting REVIEW/GENERATING/RESULT-phase behavior below, not the specific
        // strategy/color picked.
        vm.onSelectDirectionTag(CardTag.AGGRO)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        // The stale commander must never reach the build spec, and the deck name must NOT be the
        // leftover commander's name.
        assertNull(capturedSpec?.commander)
        coVerify(exactly = 0) { deckRepository.createDeck(commander.name, any(), any()) }
    }

    @Test
    fun `switching entry flow from Strategy to Colors clears the abandoned flow's archetype`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)
        val combo = vm.uiState.value.colorComboSuggestions.first()
        vm.onSelectColorCombo(combo)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)
        assertTrue(vm.uiState.value.colorIdentity.isNotEmpty())

        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)

        val state = vm.uiState.value
        assertEquals(WizardEntryFlow.COLORS, state.entryFlow)
        assertNull(state.selectedArchetype)
        assertTrue(state.colorIdentity.isEmpty())
        assertTrue(state.colorComboSuggestions.isEmpty())
        assertTrue(state.suggestedSeedCards.isEmpty())
    }

    @Test
    fun `re-selecting the SAME entry flow does not wipe its own in-progress state`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)

        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)

        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)
    }

    @Test
    fun `re-selecting the SAME format does not wipe Direction-step state`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)

        vm.onSelectFormat(DeckFormat.CASUAL)

        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)
    }

    // ── QA fix (edge-case audit follow-up) -- Flow C stale color-combo leak ─────

    @Test
    fun `switching taxonomy archetype without re-picking a combo clears the stale colorIdentity`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)

        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)
        val combo = vm.uiState.value.colorComboSuggestions.first()
        vm.onSelectColorCombo(combo)
        assertEquals(combo.colors, vm.uiState.value.colorIdentity)

        // User changes their mind to a DIFFERENT archetype without ever picking a new combo.
        // (Was `ArchetypeId.AGGRO` twice pre-2026-09-14 -- a stale artifact of the taxonomy
        // migration that removed `ArchetypeId.RAMP`: re-selecting the SAME archetype is a
        // deselect/toggle-off by design (see onSelectTaxonomyArchetype), not a switch, so the
        // test was silently asserting its own toggle-off path instead of the switch path its
        // name and comment describe.)
        vm.onSelectTaxonomyArchetype(ArchetypeId.CONTROL)

        assertEquals(ArchetypeId.CONTROL, vm.uiState.value.selectedArchetype)
        assertTrue(vm.uiState.value.colorIdentity.isEmpty())
    }

    // ── QA fix (RUN 3b follow-up) -- Flow B/C suggested-seeds race with the async collection load ──

    @Test
    fun `a Flow B pick made before the collection snapshot loads is recomputed once it lands`() = runTest(dispatcher) {
        // colorIdentity = emptyList() -- see the colorless-fixture note further up in this file.
        val ownedAggroCard = card(id = "aggro-1", name = "Aggro Beater", tags = listOf(CardTag.AGGRO), colorIdentity = emptyList())
        every { userCardRepository.observeCollection() } returns flow {
            delay(1_000)
            emit(listOf(userCardWith(ownedAggroCard)))
        }
        val vm = viewModel()
        // Deliberately do NOT advanceUntilIdle() yet -- the init collection load hasn't resolved.
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.COLORS)
        vm.onToggleColorFlowColor(ManaColor.R)
        val entry = vm.uiState.value.colorAffinityEntries.first { it.archetype == ArchetypeId.AGGRO }
        vm.onSelectColorAffinityEntry(entry)

        // The collection hasn't loaded yet -- ranking against the still-empty snapshot finds nothing.
        assertTrue(vm.uiState.value.suggestedSeedCards.isEmpty())

        advanceUntilIdle()

        // Once the real snapshot lands, the SAME active Flow B pick is re-ranked against it.
        assertTrue(vm.uiState.value.suggestedSeedCards.any { it.scryfallId == "aggro-1" })
    }

    @Test
    fun `a Flow C pick made before the collection snapshot loads is recomputed once it lands`() = runTest(dispatcher) {
        // colorIdentity = emptyList() -- see the colorless-fixture note further up in this file.
        val ownedAggroCard = card(id = "aggro-1", name = "Aggro Beater", tags = listOf(CardTag.AGGRO), colorIdentity = emptyList())
        every { userCardRepository.observeCollection() } returns flow {
            delay(1_000)
            emit(listOf(userCardWith(ownedAggroCard)))
        }
        val vm = viewModel()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.STRATEGY)
        vm.onSelectTaxonomyArchetype(ArchetypeId.AGGRO)
        val combo = vm.uiState.value.colorComboSuggestions.first()
        vm.onSelectColorCombo(combo)

        assertTrue(vm.uiState.value.suggestedSeedCards.isEmpty())

        advanceUntilIdle()

        assertTrue(vm.uiState.value.suggestedSeedCards.any { it.scryfallId == "aggro-1" })
    }

    @Test
    fun `a Flow A pick is unaffected by the collection-load race -- it never depends on collectionSnapshot`() = runTest(dispatcher) {
        // Guards against a regression narrowing recomputeActiveSuggestedSeeds too broadly: Flow A's
        // seedStrategySuggestion is derived purely from user-picked seedCards/selectedCommander, not
        // collectionSnapshot, so it must populate correctly even while the collection load is pending.
        every { userCardRepository.observeCollection() } returns flow {
            delay(1_000)
            emit(emptyList())
        }
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.AGGRO))
        val vm = viewModel()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)

        val suggestion = vm.uiState.value.seedStrategySuggestion
        assertTrue(suggestion != null && suggestion.candidates.any { it.profile.archetype == ArchetypeId.AGGRO })
    }

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

    private fun kotlinx.coroutines.test.TestScope.advanceCommanderToReview(vm: DeckWizardViewModel) {
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
    }

    @Test
    fun `a build with ambiguity groups lands on CHOICE and persists nothing yet`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        assertEquals(WizardPhase.CHOICE, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.commanderDraftBuild)
        assertTrue(vm.uiState.value.choiceSelections.isEmpty())
        coVerify(exactly = 0) { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.persistCommanderBuild(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a zero-group build skips CHOICE and finalizes with empty resolutions, straight to opening Deck Studio`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns commanderDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)

        // W7 Task D (plan 7.5): never lands on WizardPhase.RESULT -- fires OpenDeckStudio directly.
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
    fun `onToggleChoiceCard enforces the remainingSlots cap and a deselect frees the slot`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // Untouched -- the effective selection is the tentative default, which already occupies the
        // section's ONE slot; the cap must be respected against that IMPLICIT default too.
        vm.onToggleChoiceCard("removal_spot", "alt-a")
        assertNull("selecting an alternative while the tentative default still occupies the only slot must be a no-op", vm.uiState.value.choiceSelections["removal_spot"])

        // Deselect the default first -- frees the slot.
        vm.onToggleChoiceCard("removal_spot", "tent-1")
        assertEquals(emptyList<String>(), vm.uiState.value.choiceSelections["removal_spot"])

        // Now the alternative can be selected.
        vm.onToggleChoiceCard("removal_spot", "alt-a")
        assertEquals(listOf("alt-a"), vm.uiState.value.choiceSelections["removal_spot"])

        // A second alternative cannot be added on top of a full section.
        vm.onToggleChoiceCard("removal_spot", "alt-b")
        assertEquals(listOf("alt-a"), vm.uiState.value.choiceSelections["removal_spot"])

        // Deselecting the chosen alternative frees the slot again.
        vm.onToggleChoiceCard("removal_spot", "alt-a")
        assertEquals(emptyList<String>(), vm.uiState.value.choiceSelections["removal_spot"])
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
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onToggleChoiceCard("removal_spot", "tent-1") // deselect the kept default
        vm.onToggleChoiceCard("removal_spot", "alt-a") // actively choose an alternative
        vm.onToggleChoiceCard("removal_spot", "alt-a") // then reverse the choice
        vm.onToggleChoiceCard("removal_spot", "tent-1") // back to the kept default
        vm.onFinishChoices()
        advanceUntilIdle()

        coVerify(exactly = 0) { wizardPreferenceStore.recordPick(any()) }
    }

    @Test
    fun `W7 Fix 3 -- selecting an alternative then abandoning Choice records nothing`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onToggleChoiceCard("removal_spot", "tent-1")
        vm.onToggleChoiceCard("removal_spot", "alt-a")
        vm.onBackPressed() // abandons Choice -- see onAbandonChoice's own KDoc
        advanceUntilIdle()

        coVerify(exactly = 0) { wizardPreferenceStore.recordPick(any()) }
    }

    @Test
    fun `W7 Fix 3 -- a failed persist records nothing`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        coEvery {
            deckRepository.persistCommanderBuild(any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("persist boom")
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onToggleChoiceCard("removal_spot", "tent-1")
        vm.onToggleChoiceCard("removal_spot", "alt-a")
        vm.onFinishChoices()
        advanceUntilIdle()

        assertEquals("TPL", vm.uiState.value.buildError)
        coVerify(exactly = 0) { wizardPreferenceStore.recordPick(any()) }
    }

    @Test
    fun `W7 Fix 3 -- a successful resolution records exactly the user-chosen alternatives, never tentative defaults`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // "removal_spot" -- the user swaps the default for an alternative (genuine pick).
        vm.onToggleChoiceCard("removal_spot", "tent-1")
        vm.onToggleChoiceCard("removal_spot", "alt-a")
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
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // The section is already "full" via its implicit tentative default -- auto-fill has nothing
        // left to do and must be a no-op (never duplicate/exceed remainingSlots).
        vm.onAutoFillChoiceSection("removal_spot")
        assertNull(vm.uiState.value.choiceSelections["removal_spot"])

        // Deselect the default, leaving a real gap -- auto-fill puts it right back.
        vm.onToggleChoiceCard("removal_spot", "tent-1")
        vm.onAutoFillChoiceSection("removal_spot")
        assertEquals(listOf("tent-1"), vm.uiState.value.choiceSelections["removal_spot"])
    }

    @Test
    fun `onFinishChoices honours a decided section exactly, defaults an undecided one, and persists ONCE`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val resolutionsSlot = slot<Map<RoleKey, List<String>>>()
        coEvery {
            buildCommanderDeckUseCase.finalize(any(), capture(resolutionsSlot), any(), any())
        } returns commanderOutcome()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // The user decides "removal_spot" (swaps its default for alt-a) and never touches "card_draw".
        vm.onToggleChoiceCard("removal_spot", "tent-1")
        vm.onToggleChoiceCard("removal_spot", "alt-a")

        // W7 Task D (plan 7.5): never lands on WizardPhase.RESULT -- fires OpenDeckStudio directly.
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
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        var finalizeCallCount = 0
        val resolutionsSeen = mutableListOf<Map<RoleKey, List<String>>>()
        coEvery { buildCommanderDeckUseCase.finalize(any(), capture(resolutionsSeen), any(), any()) } answers {
            finalizeCallCount++
            if (finalizeCallCount == 1) throw RuntimeException("finalize boom")
            commanderOutcome()
        }
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        // The user resolves "removal_spot" before the finalize step fails.
        vm.onToggleChoiceCard("removal_spot", "tent-1")
        vm.onToggleChoiceCard("removal_spot", "alt-a")
        vm.onFinishChoices()
        advanceUntilIdle()

        assertEquals("TPL", vm.uiState.value.buildError)
        coVerify(exactly = 1) { buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }

        vm.events.test {
            vm.onRetryGeneration()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
        }

        // Still only ONE buildWithGroups call across the whole failure-then-retry cycle -- the
        // retry reused the already-resolved draft instead of re-walking from REVIEW.
        coVerify(exactly = 1) { buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) }
        // Both the failed attempt and the successful retry carried the user's ORIGINAL selection.
        assertEquals(listOf(listOf("alt-a"), listOf("alt-a")), resolutionsSeen.map { it["removal_spot"] })
        assertNull(vm.uiState.value.buildError)
    }

    @Test
    fun `W7 Fix 2 -- retrying after a persist failure deletes the orphan and re-persists the SAME resolved draft`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        coEvery { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) } returns commanderOutcome()
        var persistCallCount = 0
        coEvery {
            deckRepository.persistCommanderBuild(any(), any(), any(), any(), any(), any(), any())
        } answers {
            persistCallCount++
            if (persistCallCount == 1) throw RuntimeException("persist boom")
        }
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()

        vm.onToggleChoiceCard("removal_spot", "tent-1")
        vm.onToggleChoiceCard("removal_spot", "alt-a")
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
        coVerify(exactly = 1) { buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { deckRepository.persistCommanderBuild(any(), any(), any(), any(), any(), any(), any()) }
        assertNull(vm.uiState.value.buildError)
    }

    @Test
    fun `abandoning the Choice screen via back writes nothing and returns to REVIEW`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery {
            buildCommanderDeckUseCase.buildWithGroups(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns twoGroupChoiceDraft()
        val vm = viewModel()
        advanceUntilIdle()
        advanceCommanderToReview(vm)
        vm.onGenerate()
        advanceUntilIdle()
        assertEquals(WizardPhase.CHOICE, vm.uiState.value.phase)

        // Make a selection, THEN abandon -- the in-memory selection must not leak into a later build.
        vm.onToggleChoiceCard("removal_spot", "tent-1")
        val shouldPopWizard = vm.onBackPressed()

        assertFalse("abandoning Choice unwinds internally -- the wizard itself must stay open", shouldPopWizard)
        assertEquals(WizardPhase.REVIEW, vm.uiState.value.phase)
        assertNull(vm.uiState.value.commanderDraftBuild)
        assertTrue(vm.uiState.value.choiceSelections.isEmpty())
        coVerify(exactly = 0) { buildCommanderDeckUseCase.finalize(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.persistCommanderBuild(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.createDeck(any(), any(), any()) }
    }
}
