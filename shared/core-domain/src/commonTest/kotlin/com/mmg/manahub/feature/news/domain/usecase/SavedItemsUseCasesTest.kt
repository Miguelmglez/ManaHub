package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SavedNewsItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedItemsUseCasesTest {

    private val article = NewsItem.Article(
        id = "a1", title = "T", description = "D", imageUrl = null, publishedAt = 1L,
        sourceName = "S", sourceId = "s1", url = "https://example.com/a1", author = null,
    )

    @Test
    fun given_anUnsavedItem_then_toggleSavesIt_andReportsSaved() = runTest {
        val repository = FakeNewsRepository()

        val nowSaved = ToggleSavedItemUseCase(repository)(article, isCurrentlySaved = false)

        assertTrue(nowSaved)
        assertEquals<List<NewsItem>>(listOf(article), repository.savedItems)
        assertTrue(repository.unsavedIds.isEmpty())
    }

    @Test
    fun given_aSavedItem_then_toggleRemovesIt_byId() = runTest {
        val repository = FakeNewsRepository()

        val nowSaved = ToggleSavedItemUseCase(repository)(article, isCurrentlySaved = true)

        assertFalse(nowSaved)
        assertEquals(listOf("a1"), repository.unsavedIds)
        assertTrue(repository.savedItems.isEmpty())
    }

    @Test
    fun given_savedSnapshots_then_observeReturnsThem_andTheIdSet() = runTest {
        val repository = FakeNewsRepository()
        repository.savedFlow.value = listOf(SavedNewsItem(article, savedAt = 5L))
        repository.savedIdsFlow.value = setOf("a1")
        val useCase = ObserveSavedItemsUseCase(repository)

        assertEquals(5L, useCase().first().single().savedAt)
        assertEquals(setOf("a1"), useCase.savedIds().first())
    }
}
