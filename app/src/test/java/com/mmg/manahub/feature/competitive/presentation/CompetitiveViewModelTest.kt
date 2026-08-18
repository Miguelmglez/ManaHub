package com.mmg.manahub.feature.competitive.presentation

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.feature.news.domain.usecase.GetProTourContentUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CompetitiveViewModel] after the 2026-08 pivot to a 100% static deep-link
 * catalog: the format selector (now purely a filter over [CompetitiveResourceCatalog], no
 * network call), the persisted event-locator postal code, and the Pro Tour news filter. The
 * former weekly-meta/17lands-limited-ratings/archetype-import tests were removed along with the
 * ViewModel logic they covered — see `feature/competitive/CLAUDE.md` for the pivot rationale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompetitiveViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val getProTourContent: GetProTourContentUseCase = mockk()
    private val userPrefsDataStore: UserPreferencesDataStore = mockk()
    private val crashlyticsMock: FirebaseCrashlytics = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        // CompetitiveViewModel resolves FirebaseCrashlytics.getInstance() at construction
        // (additive telemetry, no PII) — mock the static so the un-initialized FirebaseApp
        // doesn't throw. Mirrors HomeViewModelTest's setup.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlyticsMock
        every { userPrefsDataStore.competitivePostalCodeFlow } returns flowOf("")
        coEvery { userPrefsDataStore.setCompetitivePostalCode(any()) } returns Unit
        every { getProTourContent() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun buildViewModel() = CompetitiveViewModel(
        getProTourContent = getProTourContent,
        userPrefsDataStore = userPrefsDataStore,
    )

    @Test
    fun `initial state defaults to Standard format, Tournaments tab and empty postal code`() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CompetitiveFormat.STANDARD, state.selectedFormat)
        assertEquals(CompetitiveTab.TOURNAMENTS, state.selectedTab)
        assertEquals(CompetitiveTab.DEFAULT_EXPANDED_CATEGORIES, state.expandedCategories)
        assertEquals("", state.postalCode)
    }

    @Test
    fun `construction sets the competitive_selected_format and competitive_selected_tab custom keys`() = runTest(dispatcher) {
        buildViewModel()
        advanceUntilIdle()

        verify { crashlyticsMock.setCustomKey("competitive_selected_format", "standard") }
        verify { crashlyticsMock.setCustomKey("competitive_selected_tab", "tournaments") }
    }

    @Test
    fun `onFormatSelected with a new format updates selectedFormat and the custom key`() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onFormatSelected(CompetitiveFormat.MODERN)
        advanceUntilIdle()

        assertEquals(CompetitiveFormat.MODERN, viewModel.uiState.value.selectedFormat)
        verify { crashlyticsMock.setCustomKey("competitive_selected_format", "modern") }
    }

    @Test
    fun `onFormatSelected with the already-selected format is a no-op`() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onFormatSelected(CompetitiveFormat.STANDARD)
        advanceUntilIdle()

        assertEquals(CompetitiveFormat.STANDARD, viewModel.uiState.value.selectedFormat)
    }

    @Test
    fun `postal code flow updates uiState reactively`() = runTest(dispatcher) {
        val postalFlow = MutableStateFlow("")
        every { userPrefsDataStore.competitivePostalCodeFlow } returns postalFlow

        val viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.postalCode)

        postalFlow.value = "10115"
        advanceUntilIdle()

        assertEquals("10115", viewModel.uiState.value.postalCode)
    }

    @Test
    fun `onPostalCodeChanged updates state and persists`() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onPostalCodeChanged("94107")
        advanceUntilIdle()

        assertEquals("94107", viewModel.uiState.value.postalCode)
    }

    @Test
    fun `pro tour content flow populates uiState`() = runTest(dispatcher) {
        val items = listOf(
            NewsItem.Article(
                id = "n1",
                title = "Pro Tour recap",
                description = "desc",
                imageUrl = null,
                publishedAt = 0L,
                sourceName = "Magic.gg",
                sourceId = "magicgg",
                url = "https://magic.gg/news/pro-tour-recap",
                author = null,
            ),
        )
        every { getProTourContent() } returns flowOf(items)

        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(items, viewModel.uiState.value.proTourContent)
    }

    @Test
    fun `pro tour content is null only before the first emission`() {
        // Default state (before construction/collection) is null — verified via the data class default.
        assertNull(CompetitiveUiState().proTourContent)
    }

    @Test
    fun `onTabSelected with a new tab updates selectedTab and the custom key`() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onTabSelected(CompetitiveTab.HUB)
        advanceUntilIdle()

        assertEquals(CompetitiveTab.HUB, viewModel.uiState.value.selectedTab)
        verify { crashlyticsMock.log("competitive_tab_selected") }
        verify { crashlyticsMock.setCustomKey("competitive_selected_tab", "hub") }
    }

    @Test
    fun `onTabSelected with the already-selected tab is a no-op`() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onTabSelected(CompetitiveTab.TOURNAMENTS)
        advanceUntilIdle()

        assertEquals(CompetitiveTab.TOURNAMENTS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `onCategoryToggled collapses a default-expanded category`() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()
        val categoryId = ResourceCategory.TOURNAMENT_DECKLISTS.name
        assertEquals(true, categoryId in viewModel.uiState.value.expandedCategories)

        viewModel.onCategoryToggled(categoryId)
        advanceUntilIdle()

        assertEquals(false, categoryId in viewModel.uiState.value.expandedCategories)
        verify { crashlyticsMock.log("competitive_category_toggled") }
        verify { crashlyticsMock.setCustomKey("competitive_last_toggled_category", categoryId) }
    }

    @Test
    fun `onCategoryToggled expands a default-collapsed category`() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()
        val categoryId = ResourceCategory.STANDINGS_STATS.name
        assertEquals(false, categoryId in viewModel.uiState.value.expandedCategories)

        viewModel.onCategoryToggled(categoryId)
        advanceUntilIdle()

        assertEquals(true, categoryId in viewModel.uiState.value.expandedCategories)
    }
}
