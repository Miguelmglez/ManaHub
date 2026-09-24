package com.mmg.manahub.feature.today.presentation.events

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.SetType
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.ReleaseStatus
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.news.domain.usecase.GetProTourContentUseCase
import com.mmg.manahub.feature.news.domain.usecase.GetUpcomingReleasesUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val cardRepository = mockk<CardRepository>()
    private val newsRepository = mockk<NewsRepository>()
    private val userPrefsDataStore = mockk<UserPreferencesDataStore>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val today = LocalDate(2026, 9, 24)
    private val newsFlow = MutableStateFlow<List<NewsItem>>(emptyList())
    private val sourcesFlow = MutableStateFlow<List<ContentSource>>(emptyList())
    private val postalCodeFlow = MutableStateFlow("")

    private val sets = listOf(
        set("new", "2026-10-03"),
        set("out", "2026-09-19"),
        set("old", "2026-06-01"),
        set("cmd", "2026-09-20", SetType.COMMANDER),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { cardRepository.getPlayableSets() } returns DataResult.Success(sets)
        every { newsRepository.observeNews() } returns newsFlow
        every { newsRepository.observeSources() } returns sourcesFlow
        every { userPrefsDataStore.eventsPostalCodeFlow } returns postalCodeFlow
        coEvery { userPrefsDataStore.setEventsPostalCode(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()) = EventsViewModel(
        getUpcomingReleases = GetUpcomingReleasesUseCase(cardRepository, today = { today }),
        getProTourContent = GetProTourContentUseCase(newsRepository),
        manageSources = ManageSourcesUseCase(newsRepository),
        userPrefsDataStore = userPrefsDataStore,
        crashReporter = crashReporter,
        savedStateHandle = handle,
    )

    private fun set(code: String, releasedAt: String, type: SetType = SetType.EXPANSION) = MagicSet(
        code = code, name = "Set $code", setType = type, releasedAt = releasedAt, cardCount = 1, iconSvgUri = "",
    )

    private fun source(id: String, followed: Boolean = true) = ContentSource(
        id = id, name = id, feedUrl = "https://example.com/$id.xml", type = SourceType.ARTICLE, isEnabled = followed,
    )

    private fun article(id: String, sourceId: String, title: String) = NewsItem.Article(
        id = id, title = title, description = "", imageUrl = null, publishedAt = 0L,
        sourceName = sourceId, sourceId = sourceId, url = "https://example.com/$id", author = null,
    )

    // ── Releases ──────────────────────────────────────────────────────────────

    @Test
    fun `given the set list loads then recent and upcoming releases and the latest Limited set are shown`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.releasesLoading)
        assertFalse(state.releasesFailed)
        assertEquals(listOf("out", "cmd", "new"), state.releases.map { it.set.code })
        assertEquals(listOf(ReleaseStatus.OUT_NOW, ReleaseStatus.OUT_NOW, ReleaseStatus.UPCOMING), state.releases.map { it.status })
        assertEquals("out", state.latestLimitedSet?.code)
    }

    @Test
    fun `given the set list fails then an error shows and retry recovers`() = runTest(testDispatcher) {
        coEvery { cardRepository.getPlayableSets() } returns DataResult.Error("offline")
        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.releasesFailed)
        assertFalse(vm.uiState.value.releasesLoading)
        assertNull(vm.uiState.value.latestLimitedSet)
        verify { crashReporter.log("events_releases_load_failed") }

        coEvery { cardRepository.getPlayableSets() } returns DataResult.Success(sets)
        vm.loadReleases()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.releasesFailed)
        assertEquals(3, vm.uiState.value.releases.size)
    }

    // ── Event locator ─────────────────────────────────────────────────────────

    @Test
    fun `given a saved postal code then it seeds the field but never overwrites an edit`() = runTest(testDispatcher) {
        postalCodeFlow.value = "28001"
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals("28001", vm.uiState.value.postalCode)

        vm.onPostalCodeChanged("08002")
        postalCodeFlow.value = "99999"
        advanceUntilIdle()

        assertEquals("08002", vm.uiState.value.postalCode)
    }

    @Test
    fun `given a postal code edit then it is capped and persisted`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onPostalCodeChanged("1234567890123456789")
        advanceUntilIdle()

        assertEquals("1234567890123456", vm.uiState.value.postalCode)
        coVerify { userPrefsDataStore.setEventsPostalCode("1234567890123456") }
    }

    // ── Pro Tour strip ────────────────────────────────────────────────────────

    @Test
    fun `given Pro Tour coverage then only followed sources are shown, capped at five`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"), source("b", followed = false))
        newsFlow.value = (1..7).map { article("a$it", "a", "Pro Tour day $it") } +
            article("b1", "b", "Pro Tour from an unfollowed site") +
            article("a_other", "a", "Set review")
        val vm = createViewModel()
        advanceUntilIdle()

        val items = vm.uiState.value.proTourItems
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5"), items?.map { it.id })
    }

    @Test
    fun `given the news cache cannot be read then the strip is empty rather than loading forever`() = runTest(testDispatcher) {
        every { newsRepository.observeNews() } returns flow { throw IllegalStateException("corrupt") }
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(emptyList<NewsItem>(), vm.uiState.value.proTourItems)
        verify { crashReporter.log("events_pro_tour_feed_failed") }
    }

    // ── Metagame format + telemetry ──────────────────────────────────────────

    @Test
    fun `given a format is selected then it survives process death`() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val vm = createViewModel(handle)

        vm.onFormatSelected(MetagameFormat.PAUPER)

        assertEquals(MetagameFormat.PAUPER, vm.uiState.value.selectedFormat)
        assertEquals(MetagameFormat.PAUPER, createViewModel(handle).uiState.value.selectedFormat)
    }

    @Test
    fun `given links are opened then breadcrumbs name the link and never the URL`() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onReleaseOpened()
        vm.onLocatorOpened()
        vm.onLinkOpened("mtgo_decklists")

        verify { crashReporter.log("events_release_opened") }
        verify { crashReporter.log("events_locator_opened") }
        verify { crashReporter.log("events_link_opened:mtgo_decklists") }
    }
}
