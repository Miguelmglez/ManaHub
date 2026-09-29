package com.mmg.manahub.feature.tournament.presentation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import com.mmg.manahub.core.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TournamentSetupViewModel].
 *
 * GROUP 1 — matchesPerPairing is only honoured by the round-robin generator, so Swiss and
 *           Single Elimination must persist 1 regardless of what the stepper last held.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TournamentSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<TournamentRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.createTournament(any(), any(), any(), any(), any(), any()) } returns 1L
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `given SWISS structure when createTournament then matchesPerPairing is forced to 1`() = runTest {
        val vm = TournamentSetupViewModel(repository)
        vm.onMatchesPerPairingChange(3)
        vm.onStructureChange("SWISS")

        vm.createTournament()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.createTournament(any(), any(), "SWISS", any(), 1, any())
        }
    }

    @Test
    fun `given SINGLE_ELIM structure when createTournament then matchesPerPairing is forced to 1`() = runTest {
        val vm = TournamentSetupViewModel(repository)
        vm.onMatchesPerPairingChange(2)
        vm.onStructureChange("SINGLE_ELIM")

        vm.createTournament()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.createTournament(any(), any(), "SINGLE_ELIM", any(), 1, any())
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — stale row indices from late keyboard commits
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a removed row when a late name commit lands then nothing crashes`() = runTest {
        val vm = TournamentSetupViewModel(repository)
        val lastIndex = vm.uiState.value.players.lastIndex

        vm.removePlayer(lastIndex)
        vm.updatePlayerName(lastIndex, "Ghost")

        assertEquals(lastIndex, vm.uiState.value.players.size)
        assertFalse(vm.uiState.value.players.any { it.name == "Ghost" })
    }

    @Test
    fun `given an out-of-range index when a theme commit lands then nothing crashes`() = runTest {
        val vm = TournamentSetupViewModel(repository)

        vm.updatePlayerTheme(99, PlayerTheme.ALL.first())

        assertEquals(4, vm.uiState.value.players.size)
    }

    @Test
    fun `given a valid index when updatePlayerName then the name is applied`() = runTest {
        val vm = TournamentSetupViewModel(repository)

        vm.updatePlayerName(0, "Alice")

        assertEquals("Alice", vm.uiState.value.players.first().name)
    }

    @Test
    fun `given ROUND_ROBIN structure when createTournament then the chosen matchesPerPairing is kept`() = runTest {
        val vm = TournamentSetupViewModel(repository)
        vm.onStructureChange("ROUND_ROBIN")
        vm.onMatchesPerPairingChange(3)

        vm.createTournament()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.createTournament(any(), any(), "ROUND_ROBIN", any(), 3, any())
        }
    }
}
