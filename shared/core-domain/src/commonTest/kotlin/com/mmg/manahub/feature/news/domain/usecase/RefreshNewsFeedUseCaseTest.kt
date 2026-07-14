package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.model.news.RefreshResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit tests for [RefreshNewsFeedUseCase] — a thin delegate over [FakeNewsRepository.refreshAll]. */
class RefreshNewsFeedUseCaseTest {

    @Test
    fun given_noArgument_then_forceDefaultsToFalse() = runTest {
        val repository = FakeNewsRepository()
        val useCase = RefreshNewsFeedUseCase(repository)

        useCase()

        assertEquals(false, repository.lastRefreshAllForce)
    }

    @Test
    fun given_forceTrue_then_itIsForwardedToTheRepository() = runTest {
        val repository = FakeNewsRepository()
        val useCase = RefreshNewsFeedUseCase(repository)

        useCase(force = true)

        assertEquals(true, repository.lastRefreshAllForce)
    }

    @Test
    fun given_theRepositorySucceeds_then_theSameResultIsReturned() = runTest {
        val expected = RefreshResult(fetched = 3, failed = 1, notModified = 2)
        val repository = FakeNewsRepository().apply { refreshAllResult = Result.success(expected) }
        val useCase = RefreshNewsFeedUseCase(repository)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun given_theRepositoryFails_then_theFailureIsPropagatedUnchanged() = runTest {
        val failure = RuntimeException("db exploded")
        val repository = FakeNewsRepository().apply { refreshAllResult = Result.failure(failure) }
        val useCase = RefreshNewsFeedUseCase(repository)

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
