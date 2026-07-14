package com.mmg.manahub.feature.news.presentation

import android.content.Context
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Unit tests for [NewsSourcesSettingsViewModel] — News feature improvements Phase 4 (YouTube URL
 * resolution) and Phase 5 low findings F9 (refresh-on-add) + the language pre-select bonus.
 *
 * GROUP 1 — resolveYouTubeUrl (exercised through validateAndAdd, type=VIDEO)
 * GROUP 2 — validateAndAdd guard clauses (blank fields, non-HTTPS)
 * GROUP 3 — F9: refreshSource is invoked right after a successful add
 * GROUP 4 — F3 bonus: language pre-select via detectFeedLanguage
 * GROUP 5 — simple state setters
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsSourcesSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val manageSources = mockk<ManageSourcesUseCase>()
    private val context = mockk<Context>(relaxed = true)

    private val sourcesFlow = MutableStateFlow<List<ContentSource>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { manageSources.observeSources() } returns sourcesFlow
        every { context.getString(R.string.news_sources_youtube_handle_unsupported) } returns
            "YouTube handles can't be added directly."
        coEvery { manageSources.refreshSource(any()) } returns Result.success(Unit)
        coEvery { manageSources.detectFeedLanguage(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = NewsSourcesSettingsViewModel(manageSources, context)

    private fun addedSource(id: String, name: String, feedUrl: String, type: SourceType, language: String) =
        ContentSource(id = id, name = name, feedUrl = feedUrl, type = type, language = language)

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — resolveYouTubeUrl (via validateAndAdd, type=VIDEO)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * SUSPECTED PRODUCTION BUG (found while writing this test, not fixed here per this agent's
     * scope — route through `android-kotlin-architect`): [NewsSourcesSettingsViewModel
     * .resolveYouTubeUrl]'s own KDoc documents "a bare `UC…` channel id (24 chars)" as a
     * supported input, and [BARE_CHANNEL_ID_REGEX] implements it — but [validateAndAdd] checks
     * `normalizedUrl.startsWith("https://")` and returns the "Only HTTPS feed URLs are accepted"
     * error BEFORE ever calling `resolveYouTubeUrl`. A bare channel id never starts with
     * `https://`, so this branch is unreachable dead code: pasting a bare channel id always fails
     * with the HTTPS-only error instead of resolving to the feed URL. This test documents the
     * INTENDED behavior (per the KDoc) and will start passing once the guard order is fixed
     * (e.g. special-case the bare-channel-id shape before the HTTPS check, or run
     * `resolveYouTubeUrl` on the raw untrimmed input ahead of the HTTPS guard for VIDEO sources).
     */
    @Test
    fun `given a bare 24-char UC channel id when adding a VIDEO source then it resolves to the feeds URL`() = runTest {
        val channelId = "UC" + "a".repeat(22)
        val urlSlot = slot<String>()
        coEvery { manageSources.validateFeed(capture(urlSlot), SourceType.VIDEO) } returns Result.success(3)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.success(addedSource("id", "Name", "url", SourceType.VIDEO, "en"))

        val vm = createViewModel()
        vm.onNameChanged("Test Channel")
        vm.onFeedUrlChanged(channelId)
        vm.onTypeChanged(SourceType.VIDEO)
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId", urlSlot.captured)
    }

    @Test
    fun `given a youtube-com-channel URL when adding a VIDEO source then the channel id is extracted and resolved`() = runTest {
        val channelId = "UC" + "b".repeat(22)
        val urlSlot = slot<String>()
        coEvery { manageSources.validateFeed(capture(urlSlot), SourceType.VIDEO) } returns Result.success(3)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.success(addedSource("id", "Name", "url", SourceType.VIDEO, "en"))

        val vm = createViewModel()
        vm.onNameChanged("Test Channel")
        vm.onFeedUrlChanged("https://www.youtube.com/channel/$channelId")
        vm.onTypeChanged(SourceType.VIDEO)
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId", urlSlot.captured)
    }

    @Test
    fun `given an already-valid feeds-videos-xml URL when adding a VIDEO source then it passes through unchanged`() = runTest {
        val channelId = "UC" + "c".repeat(22)
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val urlSlot = slot<String>()
        coEvery { manageSources.validateFeed(capture(urlSlot), SourceType.VIDEO) } returns Result.success(3)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.success(addedSource("id", "Name", "url", SourceType.VIDEO, "en"))

        val vm = createViewModel()
        vm.onNameChanged("Test Channel")
        vm.onFeedUrlChanged(feedUrl)
        vm.onTypeChanged(SourceType.VIDEO)
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals(feedUrl, urlSlot.captured)
    }

    @Test
    fun `given an at-handle YouTube URL when adding a VIDEO source then the specific unsupported-handle error is shown`() = runTest {
        val vm = createViewModel()
        vm.onNameChanged("Test Channel")
        vm.onFeedUrlChanged("https://www.youtube.com/@somehandle")
        vm.onTypeChanged(SourceType.VIDEO)
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("YouTube handles can't be added directly.", vm.addState.value.error)
        coVerify(exactly = 0) { manageSources.validateFeed(any(), any()) }
    }

    @Test
    fun `given a c-slash legacy custom URL when adding a VIDEO source then the specific unsupported-handle error is shown`() = runTest {
        val vm = createViewModel()
        vm.onNameChanged("Test Channel")
        vm.onFeedUrlChanged("https://www.youtube.com/c/somechannel")
        vm.onTypeChanged(SourceType.VIDEO)
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("YouTube handles can't be added directly.", vm.addState.value.error)
    }

    @Test
    fun `given a user-slash legacy URL when adding a VIDEO source then the specific unsupported-handle error is shown`() = runTest {
        val vm = createViewModel()
        vm.onNameChanged("Test Channel")
        vm.onFeedUrlChanged("https://www.youtube.com/user/somechannel")
        vm.onTypeChanged(SourceType.VIDEO)
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("YouTube handles can't be added directly.", vm.addState.value.error)
    }

    @Test
    fun `given a genuinely unparseable feed URL when adding a VIDEO source then the generic error surfaces`() = runTest {
        coEvery { manageSources.validateFeed(any(), SourceType.VIDEO) } returns
            Result.failure(RuntimeException("No items found in feed"))

        val vm = createViewModel()
        vm.onNameChanged("Weird Feed")
        vm.onFeedUrlChanged("https://example.com/not-a-youtube-url")
        vm.onTypeChanged(SourceType.VIDEO)
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("No items found in feed", vm.addState.value.error)
    }

    @Test
    fun `given an ARTICLE type source then resolveYouTubeUrl is never applied even to a youtube-looking URL`() = runTest {
        val urlSlot = slot<String>()
        coEvery { manageSources.validateFeed(capture(urlSlot), SourceType.ARTICLE) } returns Result.success(1)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.success(addedSource("id", "Name", "url", SourceType.ARTICLE, "en"))

        val vm = createViewModel()
        vm.onNameChanged("Article Feed")
        vm.onFeedUrlChanged("https://www.youtube.com/@somehandle")
        vm.onTypeChanged(SourceType.ARTICLE)
        vm.validateAndAdd()
        advanceUntilIdle()

        // ARTICLE type short-circuits resolveYouTubeUrl entirely (passthrough), so the URL is used
        // as-is instead of failing with the video-only unsupported-handle error.
        assertEquals("https://www.youtube.com/@somehandle", urlSlot.captured)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — validateAndAdd guard clauses
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a blank name when validateAndAdd is called then nothing happens`() = runTest {
        val vm = createViewModel()
        vm.onNameChanged("")
        vm.onFeedUrlChanged("https://example.com/feed")
        vm.validateAndAdd()
        advanceUntilIdle()

        coVerify(exactly = 0) { manageSources.validateFeed(any(), any()) }
    }

    @Test
    fun `given a blank feed URL when validateAndAdd is called then nothing happens`() = runTest {
        val vm = createViewModel()
        vm.onNameChanged("Some Name")
        vm.onFeedUrlChanged("")
        vm.validateAndAdd()
        advanceUntilIdle()

        coVerify(exactly = 0) { manageSources.validateFeed(any(), any()) }
    }

    @Test
    fun `given a non-HTTPS feed URL when validateAndAdd is called then it fails with an HTTPS-only error`() = runTest {
        val vm = createViewModel()
        vm.onNameChanged("Insecure")
        vm.onFeedUrlChanged("http://example.com/feed")
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("Only HTTPS feed URLs are accepted", vm.addState.value.error)
        coVerify(exactly = 0) { manageSources.validateFeed(any(), any()) }
    }

    @Test
    fun `given validateFeed fails when validateAndAdd is called then the error is surfaced and isValidating is cleared`() = runTest {
        coEvery { manageSources.validateFeed(any(), any()) } returns Result.failure(RuntimeException("No items found in feed"))

        val vm = createViewModel()
        vm.onNameChanged("Dead Feed")
        vm.onFeedUrlChanged("https://example.com/dead")
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("No items found in feed", vm.addState.value.error)
        assertEquals(false, vm.addState.value.isValidating)
    }

    @Test
    fun `given addCustomSource fails when validateAndAdd is called then the error is surfaced`() = runTest {
        coEvery { manageSources.validateFeed(any(), any()) } returns Result.success(2)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("duplicate source"))

        val vm = createViewModel()
        vm.onNameChanged("Dup Feed")
        vm.onFeedUrlChanged("https://example.com/dup")
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("duplicate source", vm.addState.value.error)
    }

    @Test
    fun `given a successful add when validateAndAdd is called then the form resets to a fresh AddSourceState`() = runTest {
        coEvery { manageSources.validateFeed(any(), any()) } returns Result.success(2)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.success(addedSource("id", "Name", "https://example.com/feed", SourceType.ARTICLE, "en"))

        val vm = createViewModel()
        vm.onNameChanged("Good Feed")
        vm.onFeedUrlChanged("https://example.com/feed")
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals(AddSourceState(), vm.addState.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — F9: refresh-on-add
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a source is added successfully then refreshSource is invoked with the new source id`() = runTest {
        coEvery { manageSources.validateFeed(any(), any()) } returns Result.success(2)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.success(addedSource("new-source-id", "Name", "https://example.com/feed", SourceType.ARTICLE, "en"))

        val vm = createViewModel()
        vm.onNameChanged("Good Feed")
        vm.onFeedUrlChanged("https://example.com/feed")
        vm.validateAndAdd()
        advanceUntilIdle()

        coVerify(exactly = 1) { manageSources.refreshSource("new-source-id") }
    }

    @Test
    fun `given addCustomSource fails then refreshSource is never invoked`() = runTest {
        coEvery { manageSources.validateFeed(any(), any()) } returns Result.success(2)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("fail"))

        val vm = createViewModel()
        vm.onNameChanged("Bad Feed")
        vm.onFeedUrlChanged("https://example.com/feed")
        vm.validateAndAdd()
        advanceUntilIdle()

        coVerify(exactly = 0) { manageSources.refreshSource(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — F3 bonus: language pre-select
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given an ARTICLE source with the default en language when adding then detectFeedLanguage is consulted`() = runTest {
        coEvery { manageSources.validateFeed(any(), SourceType.ARTICLE) } returns Result.success(2)
        coEvery { manageSources.detectFeedLanguage(any()) } returns "es"
        val addedSlot = slot<String>()
        coEvery { manageSources.addCustomSource(any(), any(), any(), capture(addedSlot)) } returns
            Result.success(addedSource("id", "Name", "https://example.com/feed", SourceType.ARTICLE, "es"))

        val vm = createViewModel()
        vm.onNameChanged("Spanish Feed")
        vm.onFeedUrlChanged("https://example.com/feed")
        vm.onTypeChanged(SourceType.ARTICLE)
        // language left at its default "en" — must trigger detection.
        vm.validateAndAdd()
        advanceUntilIdle()

        coVerify(exactly = 1) { manageSources.detectFeedLanguage("https://example.com/feed") }
        assertEquals("es", addedSlot.captured)
    }

    @Test
    fun `given the user already picked a non-default language then detectFeedLanguage is never consulted`() = runTest {
        coEvery { manageSources.validateFeed(any(), SourceType.ARTICLE) } returns Result.success(2)
        val addedSlot = slot<String>()
        coEvery { manageSources.addCustomSource(any(), any(), any(), capture(addedSlot)) } returns
            Result.success(addedSource("id", "Name", "https://example.com/feed", SourceType.ARTICLE, "de"))

        val vm = createViewModel()
        vm.onNameChanged("German Feed")
        vm.onFeedUrlChanged("https://example.com/feed")
        vm.onTypeChanged(SourceType.ARTICLE)
        vm.onLanguageChanged("de")
        vm.validateAndAdd()
        advanceUntilIdle()

        coVerify(exactly = 0) { manageSources.detectFeedLanguage(any()) }
        assertEquals("de", addedSlot.captured)
    }

    @Test
    fun `given a VIDEO source then detectFeedLanguage is never consulted regardless of language`() = runTest {
        val channelId = "UC" + "d".repeat(22)
        coEvery { manageSources.validateFeed(any(), SourceType.VIDEO) } returns Result.success(2)
        coEvery { manageSources.addCustomSource(any(), any(), any(), any()) } returns
            Result.success(addedSource("id", "Name", "url", SourceType.VIDEO, "en"))

        val vm = createViewModel()
        vm.onNameChanged("Video Channel")
        vm.onFeedUrlChanged(channelId)
        vm.onTypeChanged(SourceType.VIDEO)
        vm.validateAndAdd()
        advanceUntilIdle()

        coVerify(exactly = 0) { manageSources.detectFeedLanguage(any()) }
    }

    @Test
    fun `given detectFeedLanguage returns null then the language falls back to the user's current selection`() = runTest {
        coEvery { manageSources.validateFeed(any(), SourceType.ARTICLE) } returns Result.success(2)
        coEvery { manageSources.detectFeedLanguage(any()) } returns null
        val addedSlot = slot<String>()
        coEvery { manageSources.addCustomSource(any(), any(), any(), capture(addedSlot)) } returns
            Result.success(addedSource("id", "Name", "https://example.com/feed", SourceType.ARTICLE, "en"))

        val vm = createViewModel()
        vm.onNameChanged("Feed")
        vm.onFeedUrlChanged("https://example.com/feed")
        vm.onTypeChanged(SourceType.ARTICLE)
        vm.validateAndAdd()
        advanceUntilIdle()

        assertEquals("en", addedSlot.captured)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — simple state setters + delegation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given onNameChanged is called then addState name updates`() = runTest {
        val vm = createViewModel()
        vm.onNameChanged("My Feed")
        assertEquals("My Feed", vm.addState.value.name)
    }

    @Test
    fun `given onTypeChanged is called then addState type updates`() = runTest {
        val vm = createViewModel()
        vm.onTypeChanged(SourceType.VIDEO)
        assertEquals(SourceType.VIDEO, vm.addState.value.type)
    }

    @Test
    fun `given toggleSource is called then it delegates to ManageSourcesUseCase`() = runTest {
        coEvery { manageSources.toggleSource(any(), any()) } returns Unit
        val vm = createViewModel()

        vm.toggleSource("s1", false)
        advanceUntilIdle()

        coVerify(exactly = 1) { manageSources.toggleSource("s1", false) }
    }

    @Test
    fun `given deleteSource is called then it delegates to ManageSourcesUseCase`() = runTest {
        coEvery { manageSources.deleteSource(any()) } returns Unit
        val vm = createViewModel()

        vm.deleteSource("s1")
        advanceUntilIdle()

        coVerify(exactly = 1) { manageSources.deleteSource("s1") }
    }

    @Test
    fun `given no add attempted yet then addState has no error`() = runTest {
        val vm = createViewModel()
        assertNull(vm.addState.value.error)
    }
}
