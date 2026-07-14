package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit tests for [ManageSourcesUseCase] — a thin delegate over [FakeNewsRepository]. */
class ManageSourcesUseCaseTest {

    private fun useCase(repository: FakeNewsRepository = FakeNewsRepository()) =
        repository to ManageSourcesUseCase(repository)

    @Test
    fun given_sourcesInTheRepository_then_observeSources_emitsTheSameList() = runTest {
        val (repository, useCase) = useCase()
        val source = ContentSource(id = "s1", name = "N", feedUrl = "https://example.com", type = SourceType.ARTICLE)
        repository.sourcesFlow.value = listOf(source)

        assertEquals(listOf(source), useCase.observeSources().first())
    }

    @Test
    fun given_toggleSource_then_theIdAndEnabledFlagAreForwarded() = runTest {
        val (repository, useCase) = useCase()

        useCase.toggleSource("s1", false)

        assertEquals("s1", repository.lastToggleSourceId)
        assertEquals(false, repository.lastToggleEnabled)
    }

    @Test
    fun given_addCustomSource_withNoLanguageArgument_then_itDefaultsToEnglish() = runTest {
        val (repository, useCase) = useCase()

        useCase.addCustomSource(name = "N", feedUrl = "https://example.com/feed", type = SourceType.ARTICLE)

        assertEquals("en", repository.lastAddCustomSourceArgs?.language)
    }

    @Test
    fun given_addCustomSource_withAnExplicitLanguage_then_itIsForwardedUnchanged() = runTest {
        val (repository, useCase) = useCase()

        useCase.addCustomSource(name = "N", feedUrl = "https://example.com/feed", type = SourceType.ARTICLE, language = "es")

        assertEquals("es", repository.lastAddCustomSourceArgs?.language)
    }

    @Test
    fun given_deleteSource_then_theIdIsForwarded() = runTest {
        val (repository, useCase) = useCase()

        useCase.deleteSource("s1")

        assertEquals("s1", repository.lastDeleteSourceId)
    }

    @Test
    fun given_validateFeed_then_theUrlAndTypeAreForwarded() = runTest {
        val (repository, useCase) = useCase()

        useCase.validateFeed("https://example.com/feed", SourceType.VIDEO)

        assertEquals("https://example.com/feed" to SourceType.VIDEO, repository.lastValidateFeedArgs)
    }

    @Test
    fun given_refreshSource_then_theIdIsForwarded() = runTest {
        val (repository, useCase) = useCase()

        useCase.refreshSource("s1")

        assertEquals("s1", repository.lastRefreshSourceId)
    }

    @Test
    fun given_detectFeedLanguage_then_theUrlIsForwarded_andTheResultIsReturnedUnchanged() = runTest {
        val (repository, useCase) = useCase()
        repository.detectFeedLanguageResult = "de"

        val result = useCase.detectFeedLanguage("https://example.com/feed")

        assertEquals("https://example.com/feed", repository.lastDetectFeedLanguageUrl)
        assertEquals("de", result)
    }

    @Test
    fun given_detectFeedLanguage_returnsNull_then_theUseCaseReturnsNull() = runTest {
        val (repository, useCase) = useCase()
        repository.detectFeedLanguageResult = null

        val result = useCase.detectFeedLanguage("https://example.com/feed")

        assertNull(result)
    }
}
