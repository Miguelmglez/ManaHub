package com.mmg.manahub.feature.today.presentation.saved

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SavedNewsItem
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.ObserveSavedItemsUseCase
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<NewsRepository>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val savedFlow = MutableStateFlow<List<SavedNewsItem>>(emptyList())
    private val sourcesFlow = MutableStateFlow<List<ContentSource>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeSaved() } returns savedFlow
        every { repository.observeSources() } returns sourcesFlow
        coEvery { repository.unsave(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SavedViewModel(
        observeSavedItems = ObserveSavedItemsUseCase(repository),
        manageSources = ManageSourcesUseCase(repository),
        toggleSavedItem = ToggleSavedItemUseCase(repository),
        crashReporter = crashReporter,
        savedStateHandle = SavedStateHandle(),
    )

    private fun TestScope.collecting(vm: SavedViewModel): SavedViewModel {
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private fun article(id: String, sourceId: String = "a") = NewsItem.Article(
        id = id, title = "Article $id", description = "", imageUrl = null, publishedAt = 0L,
        sourceName = "Source", sourceId = sourceId, url = "https://example.com/$id", author = null,
    )

    private fun video(id: String) = NewsItem.Video(
        id = id, title = "Video $id", description = "", imageUrl = null, publishedAt = 0L,
        sourceName = "Channel", sourceId = "v", url = "https://youtube.com/watch?v=$id", videoId = id, channelName = "Channel",
    )

    @Test
    fun `given saved snapshots then they are shown in repository order`() = runTest(testDispatcher) {
        savedFlow.value = listOf(SavedNewsItem(article("2"), savedAt = 2L), SavedNewsItem(article("1"), savedAt = 1L))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        assertEquals(listOf("2", "1"), vm.uiState.value.items.map { it.id })
        assertTrue(vm.uiState.value.hasAnySaved)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `given the videos filter then articles are hidden but the tab still knows items exist`() = runTest(testDispatcher) {
        savedFlow.value = listOf(SavedNewsItem(article("1"), savedAt = 1L))
        val vm = collecting(createViewModel())

        vm.onContentFilterSelected(FeedContentFilter.VIDEOS)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertTrue(vm.uiState.value.hasAnySaved)

        savedFlow.value = listOf(SavedNewsItem(article("1"), savedAt = 1L), SavedNewsItem(video("2"), savedAt = 2L))
        advanceUntilIdle()
        assertEquals(listOf("2"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `given sources with and without a site then only known sites are offered`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(
            ContentSource(id = "a", name = "A", feedUrl = "https://a.com/feed", type = SourceType.ARTICLE, siteUrl = "https://a.com"),
            ContentSource(id = "b", name = "B", feedUrl = "https://b.com/feed", type = SourceType.ARTICLE, siteUrl = null),
        )
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        assertEquals(mapOf("a" to "https://a.com"), vm.uiState.value.sourceSiteUrls)
    }

    @Test
    fun `given an item is removed then it is unsaved and the user is told`() = runTest(testDispatcher) {
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.remove(article("1"))
            assertEquals(SavedEvent.Removed, awaitItem())
        }
        coVerify { repository.unsave("1") }
        verify { crashReporter.log("news_item_unsaved") }
    }

    @Test
    fun `given removing fails then an action failed event is sent`() = runTest(testDispatcher) {
        coEvery { repository.unsave(any()) } throws IllegalStateException("db closed")
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.remove(article("1"))
            assertEquals(SavedEvent.ActionFailed, awaitItem())
        }
        verify { crashReporter.recordException(any()) }
    }

    @Test
    fun `given the saved table cannot be read then the tab shows empty instead of spinning`() = runTest(testDispatcher) {
        every { repository.observeSaved() } returns flow { throw IllegalStateException("corrupt") }
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.hasAnySaved)
        verify { crashReporter.log("today_saved_load_failed") }
    }
}
