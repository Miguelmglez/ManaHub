package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.model.news.SourceResolveException
import com.mmg.manahub.core.model.news.SourceType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceResolutionUseCasesTest {

    private val resolved = ResolvedSource(
        name = "Detected Name",
        feedUrl = "https://example.com/feed",
        siteUrl = "https://example.com",
        type = SourceType.ARTICLE,
        language = "es",
        preview = emptyList(),
    )

    @Test
    fun given_blankInput_then_resolveFailsWithInvalidInput_withoutTouchingTheRepository() = runTest {
        val repository = FakeNewsRepository()

        val result = ResolveSourceUseCase(repository)("   ")

        assertEquals(SourceResolveError.INVALID_INPUT, (result.exceptionOrNull() as SourceResolveException).error)
        assertNull(repository.lastResolveInput)
    }

    @Test
    fun given_paddedInput_then_itIsTrimmedBeforeResolving() = runTest {
        val repository = FakeNewsRepository().apply { resolveSourceResult = Result.success(resolved) }

        val result = ResolveSourceUseCase(repository)("  example.com ")

        assertTrue(result.isSuccess)
        assertEquals("example.com", repository.lastResolveInput)
    }

    @Test
    fun given_blankName_then_followUsesTheDetectedName() = runTest {
        val repository = FakeNewsRepository()

        FollowSourceUseCase(repository)(resolved, name = "  ", language = "es")

        assertEquals("Detected Name", repository.lastFollowArgs?.name)
        assertEquals("es", repository.lastFollowArgs?.language)
    }

    @Test
    fun given_longNameAndUnsupportedLanguage_then_nameIsCappedAndLanguageFallsBackToEnglish() = runTest {
        val repository = FakeNewsRepository()

        FollowSourceUseCase(repository)(resolved, name = "x".repeat(150), language = "fr")

        assertEquals(100, repository.lastFollowArgs?.name?.length)
        assertEquals("en", repository.lastFollowArgs?.language)
    }
}
