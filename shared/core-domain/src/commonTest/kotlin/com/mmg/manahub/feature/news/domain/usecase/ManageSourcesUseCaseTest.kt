package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun given_setFollowed_then_theIdAndFollowedFlagAreForwarded() = runTest {
        val (repository, useCase) = useCase()

        useCase.setFollowed("s1", false)

        assertEquals("s1", repository.lastFollowedSourceId)
        assertEquals(false, repository.lastFollowed)
    }

    @Test
    fun given_deleteSource_then_theIdIsForwarded() = runTest {
        val (repository, useCase) = useCase()

        useCase.deleteSource("s1")

        assertEquals("s1", repository.lastDeleteSourceId)
    }

    @Test
    fun given_refreshSource_then_theIdIsForwarded() = runTest {
        val (repository, useCase) = useCase()

        useCase.refreshSource("s1")

        assertEquals("s1", repository.lastRefreshSourceId)
    }
}
