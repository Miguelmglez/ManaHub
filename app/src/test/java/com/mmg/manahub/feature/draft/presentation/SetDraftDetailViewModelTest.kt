package com.mmg.manahub.feature.draft.presentation

// COMMENTS_REVIEWED: 2026-09-22

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.model.ArchetypeGuide
import com.mmg.manahub.core.model.ArchetypeKeyCard
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MechanicExamples
import com.mmg.manahub.core.model.MechanicGuide
import com.mmg.manahub.core.model.MechanicKeyCard
import com.mmg.manahub.core.model.SetDraftGuide
import com.mmg.manahub.core.model.SetTierList
import com.mmg.manahub.core.model.TierCard
import com.mmg.manahub.core.model.TierGroup
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.presentation.viewmodel.GuideSection
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailViewModel
import com.mmg.manahub.feature.draft.presentation.viewmodel.TIER_FILTERS_ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class SetDraftDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: DraftRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        coEvery { repository.getSetGuide(any()) } returns DataResult.Success(guide())
        coEvery { repository.getSetTierList(any()) } returns DataResult.Success(tierList())
        coEvery { repository.getDraftableSets(any()) } returns DataResult.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("setCode" to "tdm"))) =
        SetDraftDetailViewModel(
            savedStateHandle = savedStateHandle,
            getSetGuideUseCase = GetSetGuideUseCase(repository),
            getSetTierListUseCase = GetSetTierListUseCase(repository),
            getDraftableSetsUseCase = GetDraftableSetsUseCase(repository),
            defaultDispatcher = testDispatcher,
        )

    @Test
    fun `initially only the overview section is expanded`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(setOf(GuideSection.OVERVIEW.id), vm.uiState.value.expandedGuideIds)
    }

    @Test
    fun `toggling sections and sub-sections accumulates and survives a new ViewModel on the same handle`() =
        runTest(testDispatcher) {
            val handle = SavedStateHandle(mapOf("setCode" to "tdm"))
            val vm = viewModel(handle)
            advanceUntilIdle()

            vm.toggleGuideExpansion(GuideSection.ARCHETYPES.id)
            vm.toggleGuideExpansion("archetypes:0")
            vm.toggleGuideExpansion(GuideSection.OVERVIEW.id)

            val expected = setOf(GuideSection.ARCHETYPES.id, "archetypes:0")
            assertEquals(expected, vm.uiState.value.expandedGuideIds)

            val restored = viewModel(handle)
            advanceUntilIdle()
            assertEquals(expected, restored.uiState.value.expandedGuideIds)
        }

    @Test
    fun `tier filter panel starts collapsed and its expansion is persisted`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("setCode" to "tdm"))
        val vm = viewModel(handle)
        advanceUntilIdle()
        assertFalse(TIER_FILTERS_ID in vm.uiState.value.expandedGuideIds)

        vm.toggleGuideExpansion(TIER_FILTERS_ID)

        assertTrue(TIER_FILTERS_ID in viewModel(handle).uiState.value.expandedGuideIds)
    }

    @Test
    fun `collapsing a section keeps its sub-section state for the next expand`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleGuideExpansion(GuideSection.MECHANICS.id)
        vm.toggleGuideExpansion("mechanics:0")
        vm.toggleGuideExpansion(GuideSection.MECHANICS.id)

        assertEquals(setOf(GuideSection.OVERVIEW.id, "mechanics:0"), vm.uiState.value.expandedGuideIds)
    }

    @Test
    fun `guide model shares one Card instance for a card repeated across sections`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val guide = requireNotNull(vm.uiState.value.guide)
        val fromMechanic = guide.mechanics.single().overperformers.single()
        val fromArchetype = guide.archetypes.single().keyCards.single()
        assertSame(fromMechanic.card, fromArchetype.card)
        assertNotEquals(fromMechanic.key, fromArchetype.key)
    }

    @Test
    fun `tier list is prefetched once after the guide loads and tab switch does not reload it`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.tierListTiers)
            vm.onTabSelected(1)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.getSetTierList("tdm") }
        }

    @Test
    fun `tier filter is applied in the ViewModel and keys stay unique across tiers`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val allKeys = vm.uiState.value.filteredTiers.flatMap { tier -> tier.cards.map { it.key } }
        assertEquals(allKeys.size, allKeys.toSet().size)

        vm.toggleTierListColorFilter("R")
        advanceUntilIdle()
        assertEquals(listOf("Bolt"), vm.uiState.value.filteredTiers.flatMap { t -> t.cards.map { it.name } })

        vm.toggleTierListColorFilter("All")
        vm.onSearchQueryChanged("elf")
        advanceUntilIdle()
        assertEquals(listOf("Elf", "Elf"), vm.uiState.value.filteredTiers.flatMap { t -> t.cards.map { it.name } })
    }

    private fun guide() = SetDraftGuide(
        setCode = "TDM",
        setName = "Test",
        lastUpdated = "",
        summary = "**Fast** format {W}",
        colorRanking = listOf("{R} Red"),
        colorNotes = mapOf("{R} Red" to "Best colour"),
        keyGameplayNotes = listOf("Trade early"),
        mechanics = listOf(
            MechanicGuide(
                name = "Flurry",
                summary = "Cast two spells",
                performance = "Strong",
                keyExamples = MechanicExamples(
                    overperformers = listOf(MechanicKeyCard(name = "Bolt", scryfallId = "id-bolt")),
                ),
            ),
        ),
        archetypes = listOf(
            ArchetypeGuide(
                colors = "{R}{W}",
                name = "Boros",
                tier = "S",
                strategy = "Attack",
                difficulty = "Easy",
                keyCards = listOf(keyCard("Bolt", "id-bolt")),
            ),
        ),
    )

    private fun keyCard(name: String, id: String) = ArchetypeKeyCard(
        name = name,
        scryfallId = id,
        colors = listOf("R"),
        typeLine = "Instant",
        artCropUri = "",
        imageNormalUri = "",
        rarity = "common",
    )

    private fun tierCard(name: String, id: String, colors: List<String>) = TierCard(
        name = name,
        scryfallId = id,
        color = colors.joinToString(""),
        colors = colors,
        rarity = "common",
        pickOrderRank = 1,
        tierRating = "A",
        note = "",
        artCropUri = "",
        imageNormalUri = "",
        typeLine = "Creature",
    )

    private fun tierList() = SetTierList(
        setCode = "TDM",
        setName = "Test",
        lastUpdated = "",
        tierKey = emptyMap(),
        tiers = listOf(
            TierGroup("A", "Top", "", listOf(tierCard("Bolt", "id-bolt", listOf("R")), tierCard("Elf", "id-elf", listOf("G")))),
            TierGroup("B", "Good", "", listOf(tierCard("Elf", "id-elf", listOf("G")))),
        ),
    )
}
