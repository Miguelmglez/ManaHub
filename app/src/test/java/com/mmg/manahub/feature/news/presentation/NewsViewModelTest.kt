package com.mmg.manahub.feature.news.presentation

import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.NewsFilterPrefs
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [NewsViewModel] — News feature improvements Phase 3 (partial-failure event
 * surfacing via [NewsEvent]) and the filters/search combine rewrite (no `.value` reads inside
 * the transform).
 *
 * GROUP 1 — refresh(force): init auto-refresh + manual force param
 * GROUP 2 — NewsEvent.ShowPartialRefreshFailure emission
 * GROUP 3 — total-failure path: error state + Crashlytics, no partial-failure event
 * GROUP 4 — uiState item filtering (language / type / enabled source / search / sourceIds prune)
 * GROUP 5 — onFiltersApplied: empty-set fallback + DataStore write-through
 * GROUP 6 — search query debounce
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val getNewsFeed = mockk<GetNewsFeedUseCase>()
    private val refreshNewsFeed = mockk<RefreshNewsFeedUseCase>()
    private val manageSources = mockk<ManageSourcesUseCase>()
    private val userPrefsDataStore = mockk<UserPreferencesDataStore>()

    private val newsFlow = MutableStateFlow<List<NewsItem>>(emptyList())
    private val sourcesFlow = MutableStateFlow<List<ContentSource>>(emptyList())
    private val filtersFlow = MutableStateFlow(NewsFilterPrefs.DEFAULT)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        every { getNewsFeed() } returns newsFlow
        every { manageSources.observeSources() } returns sourcesFlow
        every { userPrefsDataStore.observeNewsFilters() } returns filtersFlow
        coEvery { userPrefsDataStore.setNewsFilters(any(), any(), any()) } returns Unit
        coEvery { refreshNewsFeed(any()) } returns Result.success(RefreshResult(fetched = 0, failed = 0, notModified = 0))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun createViewModel() = NewsViewModel(getNewsFeed, refreshNewsFeed, manageSources, userPrefsDataStore)

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun articleSource(id: String, language: String = "en", enabled: Boolean = true) = ContentSource(
        id = id, name = "Source $id", feedUrl = "https://example.com/$id", type = SourceType.ARTICLE,
        isEnabled = enabled, isDefault = true, language = language,
    )

    private fun article(id: String, sourceId: String, title: String = "Title $id", description: String = "Desc") =
        NewsItem.Article(
            id = id, title = title, description = description, imageUrl = null, publishedAt = 0L,
            sourceName = "Source", sourceId = sourceId, url = "https://example.com/$id", author = null,
        )

    private fun video(id: String, sourceId: String) = NewsItem.Video(
        id = id, title = "Video $id", description = "Desc", imageUrl = null, publishedAt = 0L,
        sourceName = "Source", sourceId = sourceId, url = "https://example.com/$id",
        videoId = id, channelName = "Channel",
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — refresh(force)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given the ViewModel is created then init auto-refreshes with force=false`() = runTest {
        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshNewsFeed(false) }
    }

    @Test
    fun `given refresh is called with force=true then refreshNewsFeed is invoked with true`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.refresh(force = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshNewsFeed(true) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — NewsEvent.ShowPartialRefreshFailure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given refreshNewsFeed returns a RefreshResult with failed greater than 0 then a partial-failure event is emitted with the count`() = runTest {
        coEvery { refreshNewsFeed(any()) } returns Result.success(RefreshResult(fetched = 2, failed = 3, notModified = 1))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            val event = awaitItem()
            assertTrue(event is NewsEvent.ShowPartialRefreshFailure)
            assertEquals(3, (event as NewsEvent.ShowPartialRefreshFailure).failedCount)
        }
    }

    @Test
    fun `given refreshNewsFeed returns a RefreshResult with failed=0 then no partial-failure event is emitted`() = runTest {
        coEvery { refreshNewsFeed(any()) } returns Result.success(RefreshResult(fetched = 5, failed = 0, notModified = 0))

        val vm = createViewModel()
        advanceUntilIdle()

        // Trigger a second refresh so we have a bounded window to assert "nothing arrived".
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            expectNoEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — total failure: error state + Crashlytics, no partial-failure event
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given refreshNewsFeed returns a total failure then the error state is set to the exception message`() = runTest {
        coEvery { refreshNewsFeed(any()) } returns Result.failure(RuntimeException("network unreachable"))

        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        assertEquals("network unreachable", vm.uiState.value.error)
    }

    @Test
    fun `given refreshNewsFeed returns a total failure then no partial-failure event is emitted`() = runTest {
        coEvery { refreshNewsFeed(any()) } returns Result.failure(RuntimeException("network unreachable"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `given refreshNewsFeed returns a total failure then Crashlytics logs the failure`() = runTest {
        coEvery { refreshNewsFeed(any()) } returns Result.failure(RuntimeException("network unreachable"))
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        createViewModel()
        advanceUntilIdle()

        coVerify { crashlytics.recordException(any()) }
    }

    @Test
    fun `given onErrorDismissed is called then the error state is cleared`() = runTest {
        coEvery { refreshNewsFeed(any()) } returns Result.failure(RuntimeException("boom"))
        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.error)

        vm.onErrorDismissed()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — uiState item filtering
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given items from disabled and enabled sources then only enabled-source items are shown`() = runTest {
        sourcesFlow.value = listOf(articleSource("enabled"), articleSource("disabled", enabled = false))
        newsFlow.value = listOf(article("a1", "enabled"), article("a2", "disabled"))

        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        assertEquals(listOf("a1"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `given items across languages then only items whose source language is in filters are shown`() = runTest {
        sourcesFlow.value = listOf(articleSource("en-src", language = "en"), articleSource("es-src", language = "es"))
        newsFlow.value = listOf(article("a1", "en-src"), article("a2", "es-src"))
        filtersFlow.value = NewsFilterPrefs.DEFAULT // English-only

        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        assertEquals(listOf("a1"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `given both languages selected then items from both are shown and showLanguageBadge is true`() = runTest {
        sourcesFlow.value = listOf(articleSource("en-src", language = "en"), articleSource("es-src", language = "es"))
        newsFlow.value = listOf(article("a1", "en-src"), article("a2", "es-src"))
        filtersFlow.value = NewsFilterPrefs(languages = setOf("en", "es"), types = SourceType.entries.toSet(), sourceIds = null)

        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        assertEquals(setOf("a1", "a2"), vm.uiState.value.items.map { it.id }.toSet())
        assertTrue(vm.uiState.value.showLanguageBadge)
    }

    @Test
    fun `given only ARTICLE type filter then video items are excluded`() = runTest {
        sourcesFlow.value = listOf(articleSource("src1"))
        newsFlow.value = listOf(article("a1", "src1"), video("v1", "src1"))
        filtersFlow.value = NewsFilterPrefs(languages = setOf("en"), types = setOf(SourceType.ARTICLE), sourceIds = null)

        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        assertEquals(listOf("a1"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `given a sourceIds allowlist referencing a since-deleted source then that phantom id is pruned`() = runTest {
        sourcesFlow.value = listOf(articleSource("still-here"))
        newsFlow.value = listOf(article("a1", "still-here"))
        filtersFlow.value = NewsFilterPrefs(
            languages = setOf("en"), types = SourceType.entries.toSet(),
            sourceIds = setOf("still-here", "deleted-source"),
        )

        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        assertEquals(setOf("still-here"), vm.uiState.value.filterSourceIds)
    }

    @Test
    fun `given a search query when set then only matching items (title or description) are shown`() = runTest {
        sourcesFlow.value = listOf(articleSource("src1"))
        newsFlow.value = listOf(
            article("a1", "src1", title = "Standard set review"),
            article("a2", "src1", title = "Unrelated news", description = "nothing about standard here"),
            article("a3", "src1", title = "Commander picks"),
        )

        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        vm.onSearchQueryChanged("standard")
        advanceTimeBy(301)
        advanceUntilIdle()

        assertEquals(setOf("a1", "a2"), vm.uiState.value.items.map { it.id }.toSet())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — onFiltersApplied
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given onFiltersApplied is called with empty types then it falls back to the default types`() = runTest {
        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        vm.onFiltersApplied(types = emptySet(), languages = setOf("en"), sourceIds = null)
        advanceUntilIdle()

        assertEquals(NewsFilterPrefs.DEFAULT.types, vm.uiState.value.filterTypes)
        coVerify { userPrefsDataStore.setNewsFilters(setOf("en"), NewsFilterPrefs.DEFAULT.types, null) }
    }

    @Test
    fun `given onFiltersApplied is called with empty languages then it falls back to the default languages`() = runTest {
        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        vm.onFiltersApplied(types = SourceType.entries.toSet(), languages = emptySet(), sourceIds = null)
        advanceUntilIdle()

        assertEquals(NewsFilterPrefs.DEFAULT.languages, vm.uiState.value.filterLanguages)
    }

    @Test
    fun `given onFiltersApplied is called then it writes through to DataStore with the effective values`() = runTest {
        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        vm.onFiltersApplied(types = setOf(SourceType.VIDEO), languages = setOf("de"), sourceIds = setOf("x"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            userPrefsDataStore.setNewsFilters(setOf("de"), setOf(SourceType.VIDEO), setOf("x"))
        }
    }

    @Test
    fun `given onFiltersApplied is called then the local override is reflected immediately without waiting for DataStore`() = runTest {
        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        // The persisted flow (filtersFlow) is deliberately NOT updated here — only the VM's local
        // override should drive the immediately-visible uiState (optimistic UI), without needing
        // to wait on the DataStore round-trip started by the write-through launch below.
        vm.onFiltersApplied(types = setOf(SourceType.VIDEO), languages = setOf("de"), sourceIds = null)
        advanceUntilIdle()

        assertEquals(setOf(SourceType.VIDEO), vm.uiState.value.filterTypes)
        assertEquals(setOf("de"), vm.uiState.value.filterLanguages)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — search query debounce
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a search query change then the raw searchQuery updates immediately`() = runTest {
        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        vm.onSearchQueryChanged("dragon")

        assertEquals("dragon", vm.searchQuery.value)
    }

    @Test
    fun `given a search query change then filtering is NOT applied before the debounce window elapses`() = runTest {
        sourcesFlow.value = listOf(articleSource("src1"))
        newsFlow.value = listOf(article("a1", "src1", title = "Dragon deck tech"), article("a2", "src1", title = "Unrelated"))

        val vm = createViewModel()
        backgroundScopeTest(this, vm)
        advanceUntilIdle()

        vm.onSearchQueryChanged("dragon")
        advanceTimeBy(100) // well under the 300ms debounce window

        assertEquals(
            "before the debounce fires, filtering must still reflect the previous (empty) query",
            setOf("a1", "a2"),
            vm.uiState.value.items.map { it.id }.toSet(),
        )
    }

    // ── Test helper: subscribes to uiState in the background so WhileSubscribed activates ──

    private fun backgroundScopeTest(scope: TestScope, vm: NewsViewModel) {
        scope.backgroundScope.launch { vm.uiState.collect {} }
    }
}
