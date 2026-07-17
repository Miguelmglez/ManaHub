package com.mmg.manahub.feature.decks.presentation.wizard

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.BuildStage
import com.mmg.manahub.feature.decks.domain.template.CategorySuggestions
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.DeckTemplateArchetypeInfo
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun viewModel(savedState: Map<String, Any?> = emptyMap()) = DeckWizardViewModel(
        deckRepository = deckRepository,
        userCardRepository = userCardRepository,
        collectionProfileUseCase = collectionProfileUseCase,
        buildDeckFromTemplateUseCase = buildDeckFromTemplateUseCase,
        searchCardsUseCase = searchCardsUseCase,
        communityAggregateRepository = communityAggregateRepository,
        crashReporter = crashReporter,
        appContext = appContext,
        savedStateHandle = SavedStateHandle(savedState),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { appContext.getString(any()) } returns "TPL"
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        coEvery { deckRepository.createDeck(any(), any(), any()) } returns "wizard-deck-1"
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
    fun `selecting a format then advancing moves to DIRECTION`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
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
    fun `Commander direction requires a commander before advancing, and shows a toast`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()

        vm.events.test {
            vm.onNextFromDirection()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.ShowToast)
        }
        assertEquals(WizardPhase.DIRECTION, vm.uiState.value.phase)
    }

    @Test
    fun `picking a commander pre-fills color identity from it`() = runTest(dispatcher) {
        coEvery { communityAggregateRepository.getCommanderAggregate(any()) } returns DataResult.Error("Worker down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.COMMANDER)
        vm.onNextFromFormat()
        vm.onSelectCommander(commander)
        advanceUntilIdle()

        assertEquals(setOf(ManaColor.G), vm.uiState.value.colorIdentity)
        vm.onNextFromDirection()
        assertEquals(WizardPhase.IDENTITY, vm.uiState.value.phase)
    }

    @Test
    fun `Casual direction advances without a commander`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onNextFromDirection()
        assertEquals(WizardPhase.IDENTITY, vm.uiState.value.phase)
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

    @Test
    fun `onNextFromDirection prefills colorIdentity from seeds for Casual when none chosen explicitly`() = runTest(dispatcher) {
        val seed = card(id = "seed-1", name = "Bicolor Seed", colorIdentity = listOf("U", "R"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onAddSeed(seed)

        vm.onNextFromDirection()

        assertEquals(setOf(ManaColor.U, ManaColor.R), vm.uiState.value.colorIdentity)
        assertEquals(WizardPhase.IDENTITY, vm.uiState.value.phase)
    }

    @Test
    fun `onNextFromDirection never clobbers an explicit color choice for Casual`() = runTest(dispatcher) {
        val seed = card(id = "seed-1", name = "Bicolor Seed", colorIdentity = listOf("U", "R"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onToggleColor(ManaColor.G)
        vm.onAddSeed(seed)

        vm.onNextFromDirection()

        assertEquals(setOf(ManaColor.G), vm.uiState.value.colorIdentity)
    }

    // ── Discoveries v2 hand-off (D11) ────────────────────────────────────────

    @Test
    fun `nav args pre-fill strategy, theme, and colors`() = runTest(dispatcher) {
        val vm = viewModel(mapOf("strategyHint" to "AGGRO", "themeHint" to "Vampires", "colors" to "BR"))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.AGGRO, state.selectedStrategyHint)
        assertEquals("Vampires", state.selectedThemeHint)
        assertEquals(setOf(ManaColor.B, ManaColor.R), state.colorIdentity)
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
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
        vm.onGenerate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WizardPhase.RESULT, state.phase)
        assertEquals("wizard-deck-1", state.createdDeckId)
        assertEquals(result, state.buildResult)
        coVerify { deckRepository.addCardToDeck("wizard-deck-1", "spell-1", 2, false) }
        coVerify { deckRepository.updateArchetypeOverride("wizard-deck-1", ArchetypeId.GENERIC.name, emptyList()) }
    }

    @Test
    fun `calling onGenerate twice in immediate succession only launches one build`() = runTest(dispatcher) {
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow { awaitCancellation() }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onNextFromDirection()
        vm.onNextFromIdentity()

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
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
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
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
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
        coEvery { deckRepository.addCardToDeck(any(), any(), any(), any()) } throws RuntimeException("write boom")

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
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
        coEvery { deckRepository.addCardToDeck(any(), any(), any(), any()) } coAnswers { awaitCancellation() }

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
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
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
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
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
        vm.onGenerate()
        advanceUntilIdle()

        val suggestion = result.communitySuggestions.first().suggestions.first()
        // Two rapid taps before either coroutine has a chance to run -- the guard is checked
        // synchronously at the top of the function, before viewModelScope.launch, so the second
        // call must be rejected regardless of dispatcher scheduling.
        vm.onAddCommunitySuggestion(SuggestionCategory.OTHER.id, suggestion)
        vm.onAddCommunitySuggestion(SuggestionCategory.OTHER.id, suggestion)
        advanceUntilIdle()

        coVerify(exactly = 1) { deckRepository.addCardToDeck("wizard-deck-1", "sugg-1", 1, false) }
    }

    @Test
    fun `onAddCommunitySuggestion can be retried after a failed attempt`() = runTest(dispatcher) {
        val result = suggestionResult()
        coEvery { buildDeckFromTemplateUseCase(any(), any()) } returns flow {
            emit(TemplateBuildProgress.Complete(result))
        }
        var callCount = 0
        coEvery { deckRepository.addCardToDeck("wizard-deck-1", "sugg-1", 1, false) } coAnswers {
            callCount++
            if (callCount == 1) throw RuntimeException("boom")
        }

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelectFormat(DeckFormat.CASUAL)
        vm.onNextFromFormat()
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
        vm.onGenerate()
        advanceUntilIdle()

        val suggestion = result.communitySuggestions.first().suggestions.first()
        vm.onAddCommunitySuggestion(SuggestionCategory.OTHER.id, suggestion)
        advanceUntilIdle()
        vm.onAddCommunitySuggestion(SuggestionCategory.OTHER.id, suggestion)
        advanceUntilIdle()

        coVerify(exactly = 2) { deckRepository.addCardToDeck("wizard-deck-1", "sugg-1", 1, false) }
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
        vm.onNextFromDirection()
        vm.onNextFromIdentity()
        vm.onGenerate()
        advanceUntilIdle()

        vm.events.test {
            vm.onOpenDeckStudio()
            val event = awaitItem()
            assertTrue(event is DeckWizardEvent.OpenDeckStudio)
            assertEquals("wizard-deck-1", (event as DeckWizardEvent.OpenDeckStudio).deckId)
        }
    }
}
