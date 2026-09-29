package com.mmg.manahub.feature.tournament.presentation

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [TournamentListViewModel].
 *
 * GROUP 1 — delete: reaches the repository, and a failure is non-fatal (never crashes the list)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TournamentListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<TournamentRepository>(relaxed = true)
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        every { repository.observeTournaments() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    @Test
    fun `given a tournament id when delete then the repository removes it`() = runTest {
        coEvery { repository.deleteTournament(7L) } returns Unit
        val vm = TournamentListViewModel(repository)

        vm.delete(7L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteTournament(7L) }
    }

    @Test
    fun `given delete fails when delete then it is recorded and nothing crashes`() = runTest {
        coEvery { repository.deleteTournament(any()) } throws IOException("db locked")
        val vm = TournamentListViewModel(repository)

        vm.delete(9L)
        advanceUntilIdle()

        verify(exactly = 1) { crashlytics.recordException(any()) }
    }
}
