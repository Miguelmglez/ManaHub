package com.mmg.manahub.feature.decks.presentation.wizard

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
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
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.usecase.DeriveCommanderStrategiesUseCase
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.BuildStage
import com.mmg.manahub.feature.decks.domain.template.CategorySuggestions
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.DeckTemplateArchetypeInfo
import com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec
import com.mmg.manahub.feature.decks.domain.template.SuggestionCategory
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildProgress
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.template.TemplateCardSuggestion
import com.mmg.manahub.feature.decks.domain.template.TemplateSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
        archetypeInfo = DeckTemplateArchetypeInfo(archetype = ArchetypeId.GENERIC, themes = emptyList()),
        archetypeOverride = ArchetypeId.GENERIC.name,
        themesOverride = emptyList(),
        colorConsistencyWarning = false,
        gamePlan = null,
    )

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
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { appContext.getString(any()) } returns "TPL"
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        coEvery { deckRepository.createDeck(any(), any(), any()) } returns "wizard-deck-1"
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

    @Test
    fun `initial phase is FORMAT`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(WizardPhase.FORMAT, vm.uiState.value.phase)
    }

    @Test
    fun `onNextFromFormat is a no-op when no format is selected`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNextFromFormat()
        assertEquals(WizardPhase.FORMAT, vm.uiState.value.phase)
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
    fun `back from ENTRY returns to FORMAT`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)
        assertTrue(!vm.onBackPressed())
        assertEquals(WizardPhase.FORMAT, vm.uiState.value.phase)
    }

    @Test
    fun `back from DIRECTION returns to ENTRY for Casual, but FORMAT for Commander`() = runTest(dispatcher) {
        val casual = viewModel()
        advanceUntilIdle()
        casual.onSelectFormat(DeckFormat.CASUAL)
        casual.onNextFromFormat()
        casual.onSelectEntryFlow(WizardEntryFlow.COLORS)
        casual.onBackPressed()
        assertEquals(WizardPhase.ENTRY, casual.uiState.value.phase)

        val commander = viewModel()
        advanceUntilIdle()
        commander.onSelectFormat(DeckFormat.COMMANDER)
        commander.onNextFromFormat()
        commander.onBackPressed()
        assertEquals(WizardPhase.FORMAT, commander.uiState.value.phase)
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
    fun `a non-v1 format is rejected -- selectedFormat stays null`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.STANDARD)
        assertNull(vm.uiState.value.selectedFormat)
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
        vm.onSelectTaxonomyArchetype(ArchetypeId.RAMP)
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
        vm.onBackPressed()
        assertEquals(WizardPhase.FORMAT, vm.uiState.value.phase)
    }

    @Test
    fun `STRATEGY step advances with no pick at all -- Commander's GENERIC Balanced escape hatch (D-B), and resolves NO skeleton`() = runTest(dispatcher) {
        // Fix 5 (edge-case audit, 2026-07-28): a GENERIC ("Balanced") pick with no themes must
        // mirror BuildDeckFromTemplateUseCase.resolveArchetypeSkeleton's own gate -- the REAL build
        // never resolves an archetype-flavored skeleton for this case (Motor A scores with zero
        // theme bonus), so this UI-only preview must not show one either. Before this fix, this
        // test asserted the OPPOSITE (a non-null skeleton) as the intended behavior -- that was
        // itself the bug: a misleading MANUAL_ADDS role chip the real build never actually applied.
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()

        assertNull(vm.uiState.value.selectedArchetype)
        vm.onNextFromStrategy()

        assertEquals(WizardPhase.MANUAL_ADDS, vm.uiState.value.phase)
        assertNull("a GENERIC pick with no themes must resolve NO skeleton, matching the real build's own gate", vm.uiState.value.manualAddsSkeleton)
    }

    @Test
    fun `onSelectStrategyArchetype and onToggleStrategyTheme update the STRATEGY step's own selection`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        vm.onNextFromCommanderPick()

        vm.onSelectStrategyArchetype(ArchetypeId.AGGRO)
        assertEquals(ArchetypeId.AGGRO, vm.uiState.value.selectedArchetype)

        vm.onToggleStrategyTheme(ThemeId.TOKENS)
        assertEquals(listOf(ThemeId.TOKENS), vm.uiState.value.selectedStrategyThemes)

        // Toggling the same theme again removes it (StrategyPickerLogic.toggleTheme contract).
        vm.onToggleStrategyTheme(ThemeId.TOKENS)
        assertTrue(vm.uiState.value.selectedStrategyThemes.isEmpty())

        // Casual's own single-theme slot (selectedDirectionTheme) is untouched -- the two flows use
        // SEPARATE fields for the theme axis (see DeckWizardUiState.selectedStrategyThemes' KDoc).
        assertNull(vm.uiState.value.selectedDirectionTheme)

        vm.onSelectStrategyArchetype(null)
        assertNull(vm.uiState.value.selectedArchetype)
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
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(commander), hasMore = false)
        )

        vm.onCommanderQueryChange("Zada")
        advanceUntilIdle()

        assertEquals(listOf(commander), vm.uiState.value.commanderSearchResults)
    }

    @Test
    fun `selecting a commander derives STRATEGY candidates from its own card_strategy_tags (source 1)`() = runTest(dispatcher) {
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

        val candidates = vm.uiState.value.commanderStrategyCandidates
        assertTrue(candidates.tribes.any { it.key == "tribe:elf" })
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
        vm.onSelectDirectionTag(CardTag.RAMP)
        assertEquals(ArchetypeId.RAMP, vm.uiState.value.selectedArchetype)
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
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val offSeed = card(id = "gy-1", name = "Graveyard Piece", tags = listOf(CardTag.GRAVEYARD))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)
        vm.onAddSeed(offSeed)

        val rampCandidate = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.RAMP }
        assertTrue(rampCandidate.misfitSeeds.any { it.scryfallId == "gy-1" })

        // Removing the misfit seed re-ranks: the SAME candidate no longer carries it as a misfit.
        vm.onRemoveSeed(offSeed)
        val reranked = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.RAMP }
        assertTrue(reranked.misfitSeeds.isEmpty())
    }

    // ── Flow A/B/C -- Workstream 3 per-flow full step chains ─────────────────────

    @Test
    fun `Flow A full step chain -- FORMAT to ENTRY to DIRECTION to MANUAL_ADDS to REVIEW, and back mirrors it`() = runTest(dispatcher) {
        val seed = card(id = "seed-1", name = "Ramp Seed", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        assertEquals(WizardPhase.ENTRY, vm.uiState.value.phase)

        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)

        vm.onAddSeed(seed)
        val candidate = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.RAMP }
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
        vm.onBackPressed()
        assertEquals(WizardPhase.FORMAT, vm.uiState.value.phase)
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
        vm.onSelectTaxonomyArchetype(ArchetypeId.RAMP)
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
            com.mmg.manahub.core.model.PaginatedCards(cards = listOf(basaltMonolith), hasMore = false)
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
        vm.onSelectDirectionTag(CardTag.RAMP)
        vm.onToggleCardsFlowColor(ManaColor.G)
        vm.onNextFromDirection()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WizardPhase.RESULT, state.phase)
        assertEquals("wizard-deck-1", state.createdDeckId)
        assertEquals(result, state.buildResult)
        coVerify { deckRepository.addCardToDeck("wizard-deck-1", "spell-1", 2, false, DeckCardSource.WIZARD) }
        coVerify { deckRepository.updateArchetypeOverride("wizard-deck-1", ArchetypeId.GENERIC.name, emptyList()) }
        // D4: every wizard build pins strategyLocked=true and writes the (here absent) tribe pin.
        coVerify { deckRepository.updateTribeOverride("wizard-deck-1", null) }
        coVerify { deckRepository.updateStrategyLocked("wizard-deck-1", true) }
    }

    // ── BUG-1 regression (Deck Engine Unification plan §0.1) ────────────────────

    @Test
    fun `a Commander build writes the commander as a qty-1 mainboard entry, not just Deck-commanderCardId`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val result = buildResult(deckCards = emptyList())
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
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
        vm.onGenerate()
        advanceUntilIdle()

        assertEquals(WizardPhase.RESULT, vm.uiState.value.phase)
        // BUG-1: DeckStudioViewModel.rebuildUiState resolves the commander from the deck's ENTRIES
        // (allEntries.find { it.scryfallId == commanderId }), not from Deck.commanderCardId alone --
        // without this write the commander was invisible in the built deck.
        coVerify { deckRepository.addCardToDeck("wizard-deck-1", "cmd-1", 1, false, DeckCardSource.WIZARD) }
    }

    @Test
    fun `the commander mainboard entry plus BuildDeckFromTemplateUseCase's 99-card reservation totals exactly 100`() = runTest(dispatcher) {
        // BuildDeckFromTemplateUseCase.mainboardTargetSize reserves targetDeckSize - 1 (99) for
        // Commander -- this test locks the OTHER half of that contract: writeResultIntoNewDeck adds
        // exactly ONE more card (the commander), for exactly 100 total.
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val ninetyNineEntries = (1..99).map { i ->
            DeckEntry(card = card(id = "spell-$i", name = "Spell $i"), quantity = 1, isOwned = true)
        }
        val result = buildResult(deckCards = ninetyNineEntries)
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
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

        // 99 deckCards writes + 1 commander write = 100 total addCardToDeck calls for this build.
        coVerify(exactly = 100) { deckRepository.addCardToDeck("wizard-deck-1", any(), any(), any(), any()) }
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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        // The build succeeds, but the per-card write inside writeResultIntoNewDeck throws AFTER
        // createDeck() already ran -- pendingDeckId is set to the dangling row.
        val entry = DeckEntry(card = card(id = "spell-1", name = "Forest Spell"), quantity = 1, isOwned = true)
        val result = buildResult(deckCards = listOf(entry))
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
        coEvery { deckRepository.addCardToDeck(any(), any(), any(), any(), any()) } throws RuntimeException("write boom")

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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        // addCardToDeck suspends forever -- lets the test cancel mid-write, AFTER the deck row was
        // already created (pendingDeckId set), verifying the orphan-cleanup delete fires.
        coEvery { deckRepository.addCardToDeck(any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }

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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        vm.onSelectDirectionTag(CardTag.RAMP)
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
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)

        assertNull(vm.uiState.value.seedStrategySuggestion)
        vm.onAddSeed(rampSeed)
        val suggestion = vm.uiState.value.seedStrategySuggestion
        assertTrue(suggestion != null && suggestion.candidates.isNotEmpty())
        assertTrue(suggestion!!.candidates.any { it.profile.archetype == ArchetypeId.RAMP })
    }

    @Test
    fun `removing every seed clears seedStrategySuggestion`() = runTest(dispatcher) {
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
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
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)
        val candidate = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.RAMP }
        vm.onSelectSeedStrategyCandidate(candidate)
        assertEquals(ArchetypeId.RAMP, vm.uiState.value.selectedArchetype)

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
    fun `onSelectSeedStrategyCandidate picks the same one-slot Direction fields, and toggles off on a second tap`() = runTest(dispatcher) {
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)
        val candidate = vm.uiState.value.seedStrategySuggestion!!.candidates.first { it.profile.archetype == ArchetypeId.RAMP }

        vm.onSelectSeedStrategyCandidate(candidate)
        assertEquals(ArchetypeId.RAMP, vm.uiState.value.selectedArchetype)

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
        vm.onSelectDirectionTag(CardTag.RAMP)
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

        // Back out (DIRECTION -> ENTRY -> FORMAT) and pick Commander instead.
        vm.onBackPressed()
        vm.onBackPressed()
        assertEquals(WizardPhase.FORMAT, vm.uiState.value.phase)
        vm.onSelectFormat(DeckFormat.COMMANDER)

        val state = vm.uiState.value
        assertNull(state.selectedArchetype)
        assertNull(state.selectedDirectionTheme)
        assertTrue(state.colorIdentity.isEmpty())
        assertTrue(state.colorComboSuggestions.isEmpty())
        assertTrue(state.suggestedSeedCards.isEmpty())
        assertEquals(WizardEntryFlow.CARDS, state.entryFlow)

        // The stale archetype must never reach the build spec.
        var capturedSpec: DeckWizardSpec? = null
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } answers {
            capturedSpec = firstArg()
            flow { emit(TemplateBuildProgress.Complete(buildResult())) }
        }
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()
        // Deck Wizard & Engine Rework plan, Workstream 2 -- the new Commander step sequence.
        vm.onNextFromCommanderPick()
        vm.onNextFromStrategy()
        vm.onNextFromManualAdds()
        vm.onGenerate()
        advanceUntilIdle()

        assertNull(capturedSpec?.strategyProfile?.archetype)
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

        vm.onBackPressed()
        assertEquals(WizardPhase.FORMAT, vm.uiState.value.phase)
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
        vm.onSelectDirectionTag(CardTag.RAMP)
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

        // User changes their mind without ever picking a new combo for RAMP.
        vm.onSelectTaxonomyArchetype(ArchetypeId.RAMP)

        assertEquals(ArchetypeId.RAMP, vm.uiState.value.selectedArchetype)
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
        val rampSeed = card(id = "ramp-1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val vm = viewModel()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onSelectEntryFlow(WizardEntryFlow.CARDS)
        vm.onAddSeed(rampSeed)

        val suggestion = vm.uiState.value.seedStrategySuggestion
        assertTrue(suggestion != null && suggestion.candidates.any { it.profile.archetype == ArchetypeId.RAMP })
    }
}
