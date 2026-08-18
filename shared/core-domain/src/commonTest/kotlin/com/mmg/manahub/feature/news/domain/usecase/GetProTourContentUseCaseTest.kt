package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.model.news.NewsItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit tests for [GetProTourContentUseCase]'s in-memory keyword filter. */
class GetProTourContentUseCaseTest {

    private fun article(id: String, title: String, description: String = "") = NewsItem.Article(
        id = id,
        title = title,
        description = description,
        imageUrl = null,
        publishedAt = 0L,
        sourceName = "Test Source",
        sourceId = "test-source",
        url = "https://example.com/$id",
        author = null,
    )

    @Test
    fun given_titleContainsProTour_then_itemIsIncluded() = runTest {
        val repository = FakeNewsRepository()
        repository.newsFlow.value = listOf(article("1", "Pro Tour Fallen Suns recap"))
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    @Test
    fun given_descriptionContainsSetCodeAbbreviation_then_itemIsIncluded() = runTest {
        val repository = FakeNewsRepository()
        repository.newsFlow.value = listOf(
            article("1", "Weekend recap", description = "Highlights from PT DFT are in"),
        )
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertEquals(1, result.size)
    }

    @Test
    fun given_regionalOrWorldOrArenaChampionship_then_itemsAreIncluded() = runTest {
        val repository = FakeNewsRepository()
        repository.newsFlow.value = listOf(
            article("1", "Regional Championship results"),
            article("2", "World Championship qualifiers set"),
            article("3", "Arena Championship 5 schedule"),
        )
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertEquals(3, result.size)
    }

    @Test
    fun given_qualifierKeyword_then_itemIsIncluded() = runTest {
        val repository = FakeNewsRepository()
        repository.newsFlow.value = listOf(
            article("1", "Standard Qualifier bracket announced"),
        )
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertEquals(1, result.size)
    }

    @Test
    fun given_bareMetagameKeyword_then_itemIsExcluded() = runTest {
        // The bare "Metagame" branch was dropped from PRO_TOUR_KEYWORD_REGEX (Competitive
        // redesign, 2026-08): it matched almost any generic deck-tech/metagame-report article,
        // not just actual Pro Tour coverage.
        val repository = FakeNewsRepository()
        repository.newsFlow.value = listOf(
            article("1", "Modern metagame shakeup this week"),
        )
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun given_matchIsLowercase_then_itemIsStillIncluded() = runTest {
        val repository = FakeNewsRepository()
        repository.newsFlow.value = listOf(article("1", "pro tour coverage begins"))
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertEquals(1, result.size)
    }

    @Test
    fun given_noKeywordMatches_then_itemIsExcluded() = runTest {
        val repository = FakeNewsRepository()
        repository.newsFlow.value = listOf(article("1", "New set spoilers begin next week"))
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun given_emptyFeed_then_emptyListIsEmitted() = runTest {
        val repository = FakeNewsRepository()
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun given_mixedFeed_then_onlyMatchingItemsSurvive() = runTest {
        val repository = FakeNewsRepository()
        repository.newsFlow.value = listOf(
            article("1", "Pro Tour Fallen Suns recap"),
            article("2", "New set spoilers begin next week"),
            article("3", "Commander precon review"),
        )
        val useCase = GetProTourContentUseCase(repository)

        val result = useCase().first()

        assertEquals(listOf("1"), result.map { it.id })
    }
}
