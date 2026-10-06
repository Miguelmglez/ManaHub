package com.mmg.manahub.feature.today.presentation.sources

import app.cash.turbine.test
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.model.news.SourceResolveException
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.news.domain.usecase.FollowSourceUseCase
import com.mmg.manahub.feature.news.domain.usecase.ResolveSourceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AddSourceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<NewsRepository>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val resolved = ResolvedSource(
        name = "Star City Games",
        feedUrl = "https://starcitygames.com/feed/",
        siteUrl = "https://starcitygames.com",
        type = SourceType.ARTICLE,
        language = "en",
        preview = emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.resolveSource(any()) } returns Result.success(resolved)
        coEvery { repository.followResolvedSource(any(), any(), any()) } answers {
            Result.success(
                ContentSource(
                    id = "custom_1", name = secondArg(), feedUrl = resolved.feedUrl, type = resolved.type,
                    isDefault = false, language = thirdArg(),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AddSourceViewModel(
        resolveSource = ResolveSourceUseCase(repository),
        followSource = FollowSourceUseCase(repository),
        crashReporter = crashReporter,
    )

    // ── Find ──────────────────────────────────────────────────────────────────

    @Test
    fun `given blank input then find is disabled and does nothing`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onInputChanged("   ")

        vm.find()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canFind)
        coVerify(exactly = 0) { repository.resolveSource(any()) }
    }

    @Test
    fun `given a resolvable link then the preview is shown with the detected name and language`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onInputChanged(" starcitygames.com ")

        vm.find()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(resolved, state.resolved)
        assertEquals("Star City Games", state.name)
        assertEquals("en", state.language)
        assertFalse(state.isResolving)
        coVerify { repository.resolveSource("starcitygames.com") }
        verify { crashReporter.log("news_source_resolve_success") }
    }

    @Test
    fun `given an unsupported or missing detected language then English is preselected`() = runTest(testDispatcher) {
        coEvery { repository.resolveSource(any()) } returns Result.success(resolved.copy(language = "fr"))
        val vm = createViewModel()
        vm.onInputChanged("example.fr")
        vm.find()
        advanceUntilIdle()
        assertEquals("en", vm.uiState.value.language)

        coEvery { repository.resolveSource(any()) } returns Result.success(resolved.copy(language = "es"))
        vm.editLink()
        vm.find()
        advanceUntilIdle()
        assertEquals("es", vm.uiState.value.language)
    }

    @Test
    fun `given a typed resolve error then it is shown and logged without a non-fatal`() = runTest(testDispatcher) {
        coEvery { repository.resolveSource(any()) } returns Result.failure(SourceResolveException(SourceResolveError.NO_FEED_FOUND))
        val vm = createViewModel()
        vm.onInputChanged("example.com")

        vm.find()
        advanceUntilIdle()

        assertEquals(SourceResolveError.NO_FEED_FOUND, vm.uiState.value.error)
        assertNull(vm.uiState.value.resolved)
        verify { crashReporter.log("news_source_resolve_failed:NO_FEED_FOUND") }
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    @Test
    fun `given an unexpected failure then it maps to unreachable and the non-fatal carries no URL`() = runTest(testDispatcher) {
        coEvery { repository.resolveSource(any()) } returns Result.failure(IOException("failed to connect to https://private.example/feed"))
        val recorded = slot<Throwable>()
        val vm = createViewModel()
        vm.onInputChanged("private.example")

        vm.find()
        advanceUntilIdle()

        assertEquals(SourceResolveError.UNREACHABLE, vm.uiState.value.error)
        verify { crashReporter.recordException(capture(recorded)) }
        assertFalse(recorded.captured.message.orEmpty().contains("private.example"))
        assertNull(recorded.captured.cause)
        verify { crashReporter.log("news_source_resolve_failed:UNREACHABLE") }
    }

    @Test
    fun `given find is tapped again while resolving then the second tap is ignored`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Result<ResolvedSource>>()
        coEvery { repository.resolveSource(any()) } coAnswers { gate.await() }
        val vm = createViewModel()
        vm.onInputChanged("example.com")

        vm.find()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isResolving)
        assertFalse(vm.uiState.value.canFind)
        vm.find()
        gate.complete(Result.success(resolved))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.resolveSource(any()) }
        assertEquals(resolved, vm.uiState.value.resolved)
    }

    @Test
    fun `given an error then editing the input clears it`() = runTest(testDispatcher) {
        coEvery { repository.resolveSource(any()) } returns Result.failure(SourceResolveException(SourceResolveError.NOT_HTTPS))
        val vm = createViewModel()
        vm.onInputChanged("http://example.com")
        vm.find()
        advanceUntilIdle()

        vm.onInputChanged("https://example.com")

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `given edit link then the preview closes but the pasted input stays`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onInputChanged("starcitygames.com")
        vm.find()
        advanceUntilIdle()

        vm.editLink()

        assertNull(vm.uiState.value.resolved)
        assertEquals("starcitygames.com", vm.uiState.value.input)
    }

    // ── Follow ────────────────────────────────────────────────────────────────

    @Test
    fun `given an edited name and language then follow uses them and resets the sheet`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onInputChanged("starcitygames.com")
        vm.find()
        advanceUntilIdle()
        vm.onNameChanged("  SCG  ")
        vm.onLanguageSelected("es")

        vm.events.test {
            vm.follow()
            assertEquals(AddSourceEvent.Followed("SCG"), awaitItem())
        }

        coVerify { repository.followResolvedSource(resolved, "SCG", "es") }
        verify { crashReporter.log("news_source_followed") }
        assertEquals(AddSourceUiState(), vm.uiState.value)
    }

    @Test
    fun `given the source is already followed then the error is shown and the preview stays`() = runTest(testDispatcher) {
        coEvery { repository.followResolvedSource(any(), any(), any()) } returns
            Result.failure(SourceResolveException(SourceResolveError.ALREADY_FOLLOWING))
        val vm = createViewModel()
        vm.onInputChanged("starcitygames.com")
        vm.find()
        advanceUntilIdle()

        vm.follow()
        advanceUntilIdle()

        assertEquals(SourceResolveError.ALREADY_FOLLOWING, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isFollowing)
        assertEquals(resolved, vm.uiState.value.resolved)
        verify { crashReporter.log("news_source_follow_failed:ALREADY_FOLLOWING") }
    }

    @Test
    fun `given nothing is resolved then follow does nothing`() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.follow()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.followResolvedSource(any(), any(), any()) }
    }

    @Test
    fun `given reset mid resolution then the sheet returns to its empty state`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Result<ResolvedSource>>()
        coEvery { repository.resolveSource(any()) } coAnswers { gate.await() }
        val vm = createViewModel()
        vm.onInputChanged("example.com")
        vm.find()
        advanceUntilIdle()

        vm.reset()
        gate.complete(Result.success(resolved))
        advanceUntilIdle()

        assertEquals(AddSourceUiState(), vm.uiState.value)
    }
}
