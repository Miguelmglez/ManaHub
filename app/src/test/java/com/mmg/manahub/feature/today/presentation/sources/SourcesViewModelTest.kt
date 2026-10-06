package com.mmg.manahub.feature.today.presentation.sources

import app.cash.turbine.test
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.SourceType
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
class SourcesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<NewsRepository>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val sourcesFlow = MutableStateFlow<List<ContentSource>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeSources() } returns sourcesFlow
        coEvery { repository.setSourceFollowed(any(), any()) } just Runs
        coEvery { repository.deleteSource(any()) } just Runs
        coEvery { repository.refreshSource(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SourcesViewModel(manageSources = ManageSourcesUseCase(repository), crashReporter = crashReporter)

    private fun TestScope.collecting(vm: SourcesViewModel): SourcesViewModel {
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private fun source(id: String, name: String = id, followed: Boolean = true, language: String = "en", isDefault: Boolean = true) =
        ContentSource(
            id = id, name = name, feedUrl = "https://example.com/$id.xml", type = SourceType.ARTICLE,
            isEnabled = followed, isDefault = isDefault, language = language,
        )

    @Test
    fun `given sources then following is sorted by name and discover is grouped en, es, de, then others`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(
            source("f2", name = "zeta"), source("f1", name = "Alpha"),
            source("d_fr", followed = false, language = "fr"),
            source("d_de", followed = false, language = "de"),
            source("d_en2", name = "Beta", followed = false), source("d_en1", name = "alpha", followed = false),
            source("d_es", followed = false, language = "es"),
        )
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("f1", "f2"), state.following.map { it.id })
        assertEquals(listOf("en", "es", "de", "fr"), state.discover.map { it.language })
        assertEquals(listOf("d_en1", "d_en2"), state.discover.first().sources.map { it.id })
    }

    @Test
    fun `given an unfollowed custom source then it stays in discover so it can be followed again`() = runTest(testDispatcher) {
        sourcesFlow.value = listOf(source("custom", followed = false, isDefault = false))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        assertEquals(listOf("custom"), vm.uiState.value.discover.single().sources.map { it.id })
    }

    @Test
    fun `given follow then the source is followed, fetched right away and the user is told`() = runTest(testDispatcher) {
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.follow(source("a", name = "Alpha", followed = false))
            assertEquals(SourcesEvent.Followed("Alpha"), awaitItem())
        }
        advanceUntilIdle()
        coVerify { repository.setSourceFollowed("a", true) }
        coVerify { repository.refreshSource("a") }
        verify { crashReporter.log("news_source_followed") }
    }

    @Test
    fun `given the first fetch after following fails then the follow still succeeds`() = runTest(testDispatcher) {
        coEvery { repository.refreshSource(any()) } returns Result.failure(IllegalStateException("offline"))
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.follow(source("a", followed = false))
            assertEquals(SourcesEvent.Followed("a"), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `given unfollow then the source is unfollowed and the user is told`() = runTest(testDispatcher) {
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.unfollow(source("a"))
            assertEquals(SourcesEvent.Unfollowed("a"), awaitItem())
        }
        coVerify { repository.setSourceFollowed("a", false) }
        verify { crashReporter.log("news_source_unfollowed") }
    }

    @Test
    fun `given a default source then delete is a no-op`() = runTest(testDispatcher) {
        val vm = collecting(createViewModel())

        vm.delete(source("a", isDefault = true))
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.deleteSource(any()) }
    }

    @Test
    fun `given a custom source then delete removes it and the user is told`() = runTest(testDispatcher) {
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.delete(source("c", isDefault = false))
            assertEquals(SourcesEvent.Deleted("c"), awaitItem())
        }
        coVerify { repository.deleteSource("c") }
        verify { crashReporter.log("news_source_deleted") }
    }

    @Test
    fun `given a write fails then an action failed event is sent and a non-fatal recorded`() = runTest(testDispatcher) {
        coEvery { repository.setSourceFollowed(any(), any()) } throws IllegalStateException("db closed")
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        vm.events.test {
            vm.unfollow(source("a"))
            assertEquals(SourcesEvent.ActionFailed, awaitItem())
        }
        verify { crashReporter.recordException(any()) }
    }

    @Test
    fun `given sources cannot be read then loading ends with empty lists`() = runTest(testDispatcher) {
        every { repository.observeSources() } returns flow { throw IllegalStateException("corrupt") }
        val vm = collecting(createViewModel())
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.following.isEmpty())
        verify { crashReporter.log("today_sources_load_failed") }
    }
}
