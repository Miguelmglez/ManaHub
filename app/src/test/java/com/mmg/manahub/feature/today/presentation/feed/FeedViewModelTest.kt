package com.mmg.manahub.feature.today.presentation.feed

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.ObserveSavedItemsUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ToggleSavedItemUseCase
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<NewsRepository>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val newsFlow = MutableStateFlow<List<NewsItem>>(emptyList())
    private val sourcesFlow = MutableStateFlow<List<ContentSource>>(emptyList())
    private val savedIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeNews() } returns newsFlow
        every { repository.observeSources() } returns sourcesFlow
        every { repository.observeSavedIds() } returns savedIdsFlow
        coEvery { repository.refreshAll(any()) } returns Result.success(RefreshResult(fetched = 1, failed = 0, notModified = 0))
        coEvery { repository.save(any()) } just Runs
        coEvery { repository.unsave(any()) } just Runs
        coEvery { repository.setSourceFollowed(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()) = FeedViewModel(
        getNewsFeed = GetNewsFeedUseCase(repository),
        refreshNewsFeed = RefreshNewsFeedUseCase(repository),
        manageSources = ManageSourcesUseCase(repository),
        observeSavedItems = ObserveSavedItemsUseCase(repository),
        toggleSavedItem = ToggleSavedItemUseCase(repository),
        crashReporter = crashReporter,
        savedStateHandle = handle,
    )

    private fun TestScope.collecting(vm: FeedViewModel): FeedViewModel {
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private fun source(id: String, followed: Boolean = true, language: String = "en", type: SourceType = SourceType.ARTICLE) =
        ContentSource(
            id = id, name = "Source $id", feedUrl = "https://example.com/$id.xml", type = type,
            isEnabled = followed, isDefault = true, language = language,
        )

    private fun article(id: String, sourceId: String, title: String = "Article $id") = NewsItem.Article(
        id = id, title = title, description = "Description", imageUrl = null, publishedAt = 0L,
        sourceName = "Source $sourceId", sourceId = sourceId, url = "https://example.com/$id", author = null,
    )

    private fun video(id: String, sourceId: String) = NewsItem.Video(
        id = id, title = "Video $id", description = "Description", imageUrl = null, publishedAt = 0L,
        sourceName = "Source $sourceId", sourceId = sourceId, url = "https://youtube.com/watch?v=$id",
        videoId = id, channelName = "Channel",
    )

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    fun `given the ViewModel is created then it refreshes once without forcing`() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.refreshAll(false) }
    }

    @Test
    fun `given a pull to refresh then the refresh is forced`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.refresh(force = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.refreshAll(true) }
    }

    @Test
    fun `given some sources fail then a partial failure event carries the count and no banner shows`() = runTest(testDispatcher) {
        coEvery { repository.refreshAll(any()) } returns Result.success(RefreshResult(fetched = 2, failed = 3, notModified = 0))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            assertEquals(FeedEvent.PartialRefreshFailure(3), awaitItem())
        }
        assertFalse(vm.uiState.value.showTotalFailure)
    }

    @Test
    fun `given every attempted source fails and nothing is cached then the total failure banner shows`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"))
        coEvery { repository.refreshAll(any()) } returns Result.success(RefreshResult(fetched = 0, failed = 2, notModified = 0))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showTotalFailure)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `given the refresh itself fails then the banner shows and a non-fatal is recorded`() = runTest(testDispatcher) {
        coEvery { repository.refreshAll(any()) } returns Result.failure(IllegalStateException("boom"))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showTotalFailure)
        verify { crashReporter.recordException(any()) }
    }

    // ── Following and narrowing ───────────────────────────────────────────────

    @Test
    fun `given items from followed and unfollowed sources then only followed ones are shown`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"), source("b", followed = false))
        newsFlow.value = listOf(article("1", "a"), article("2", "b"))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        assertEquals(listOf("1"), vm.uiState.value.items.map { it.id })
        assertEquals(listOf("a"), vm.uiState.value.followedSources.map { it.id })
        assertTrue(vm.uiState.value.sourcesLoaded)
        assertFalse(vm.uiState.value.isFiltered)
    }

    @Test
    fun `given the videos filter then only videos remain and the feed reports it is filtered`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"), source("v", type = SourceType.VIDEO))
        newsFlow.value = listOf(article("1", "a"), video("2", "v"))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.onContentFilterSelected(FeedContentFilter.VIDEOS)
        advanceUntilIdle()

        assertEquals(listOf("2"), vm.uiState.value.items.map { it.id })
        assertEquals(FeedContentFilter.VIDEOS, vm.uiState.value.contentFilter)
        assertTrue(vm.uiState.value.isFiltered)
    }

    @Test
    fun `given a source chip is tapped twice then the selection is set and then cleared`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"), source("b"))
        newsFlow.value = listOf(article("1", "a"), article("2", "b"))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.onSourceChipClicked("b")
        advanceUntilIdle()
        assertEquals("b", vm.uiState.value.selectedSource?.id)
        assertEquals(listOf("2"), vm.uiState.value.items.map { it.id })

        vm.onSourceChipClicked("b")
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedSource)
        assertEquals(listOf("1", "2"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `given a selected source that is not followed then the selection is ignored`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"), source("b", followed = false))
        newsFlow.value = listOf(article("1", "a"))
        val vm = collecting(createViewModel(SavedStateHandle(mapOf("feed_selected_source" to "b"))))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedSource)
        assertEquals(listOf("1"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `given a search query then it applies only after the debounce`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"))
        newsFlow.value = listOf(article("1", "a", title = "Bloomburrow spoilers"), article("2", "a", title = "Modern metagame"))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.onSearchActiveChanged(true)
        vm.onSearchQueryChanged("bloomburrow")
        advanceTimeBy(100)
        assertEquals("bloomburrow", vm.uiState.value.searchQuery)
        assertEquals(2, vm.uiState.value.items.size)

        advanceTimeBy(300)
        assertEquals(listOf("1"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `given search is closed then the query is cleared`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"))
        newsFlow.value = listOf(article("1", "a", title = "Alpha"), article("2", "a", title = "Beta"))
        val vm = collecting(createViewModel())
        vm.onSearchActiveChanged(true)
        vm.onSearchQueryChanged("alpha")
        advanceUntilIdle()

        vm.onSearchActiveChanged(false)
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.searchQuery)
        assertFalse(vm.uiState.value.searchActive)
        assertEquals(2, vm.uiState.value.items.size)
    }

    @Test
    fun `given followed sources in one language then no language badge is shown`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"), source("b"))
        val vm = collecting(createViewModel())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showLanguageBadge)

        sourcesFlow.value = listOf(source("a"), source("b", language = "es"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showLanguageBadge)
    }

    // ── Save / unfollow ───────────────────────────────────────────────────────

    @Test
    fun `given an unsaved item then toggling saves it and reports it`() = runTest(testDispatcher) {
        val item = article("1", "a")
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.toggleSaved(item)
            assertEquals(FeedEvent.SavedChanged(saved = true), awaitItem())
        }
        coVerify { repository.save(item) }
        verify { crashReporter.log("news_item_saved") }
    }

    @Test
    fun `given a saved item then toggling unsaves it`() = runTest(testDispatcher) {
        val item = article("1", "a")
        savedIdsFlow.value = setOf("1")
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.toggleSaved(item)
            assertEquals(FeedEvent.SavedChanged(saved = false), awaitItem())
        }
        coVerify { repository.unsave("1") }
        verify { crashReporter.log("news_item_unsaved") }
    }

    @Test
    fun `given saving fails then an action failed event is sent`() = runTest(testDispatcher) {
        coEvery { repository.save(any()) } throws IllegalStateException("disk full")
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.toggleSaved(article("1", "a"))
            assertEquals(FeedEvent.ActionFailed, awaitItem())
        }
        verify { crashReporter.recordException(any()) }
    }

    @Test
    fun `given the selected source is unfollowed then the selection clears and the user is told`() = runTest(testDispatcher) {
        val a = source("a")
        sourcesFlow.value = listOf(a, source("b"))
        val vm = collecting(createViewModel())
        vm.selectSource("a")
        advanceUntilIdle()

        vm.events.test {
            vm.unfollow(a)
            assertEquals(FeedEvent.Unfollowed("Source a"), awaitItem())
        }
        advanceUntilIdle()
        coVerify { repository.setSourceFollowed("a", false) }
        verify { crashReporter.log("news_source_unfollowed") }
        assertNull(vm.uiState.value.selectedSource)
    }

    // ── Telemetry ─────────────────────────────────────────────────────────────

    @Test
    fun `given a tab is selected then a breadcrumb and the tab key are recorded`() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onTabSelected("events")

        verify { crashReporter.log("today_tab_selected") }
        verify { crashReporter.setCustomKey("today_selected_tab", "events") }
    }

    @Test
    fun `given sources load then the followed count key is recorded`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("a"), source("b"), source("c", followed = false))
        collecting(createViewModel())
        advanceUntilIdle()

        verify { crashReporter.setCustomKey("news_followed_source_count", "2") }
    }
}
