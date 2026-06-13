package com.mmg.manahub.feature.game

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProcessedOutcome
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import com.mmg.manahub.core.gamification.domain.model.XpLineItem
import com.mmg.manahub.core.gamification.domain.model.XpSourceCategory
import com.mmg.manahub.feature.game.presentation.GameResultStripViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [GameResultStripViewModel] (ADR-002 §8.3, Phase 1, Chunk B).
 *
 * Covers: the strip surfaces ONLY the outcome whose `GameFinished.sessionId` matches the shown
 * session (correlation by id, not "latest"); ignores outcomes for other sessions and non-game
 * events; stays null when gamification is disabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameResultStripViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val engine = mockk<GamificationEngine>()
    private val dataStore = mockk<UserPreferencesDataStore>(relaxed = true)

    private val outcomes = MutableSharedFlow<ProcessedOutcome>(
        replay = 8,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val enabledFlow = MutableStateFlow(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { engine.outcomes } returns outcomes
        every { dataStore.gamificationEnabledFlow } returns enabledFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun gameFinished(sessionId: Long) = ProgressionEvent.GameFinished(
        sessionId = sessionId,
        isLocalWin = true,
        mode = "STANDARD",
        playerCount = 2,
        durationMs = 1000L,
        winTurn = 5,
        localFinalLife = 20,
        occurredAt = Instant.EPOCH,
    )

    private fun outcome(xp: Int) = ProgressionOutcome(
        xpGranted = xp,
        breakdown = listOf(XpLineItem(XpSourceCategory.GAME, xp, "Game logged")),
        newLevel = 2,
        leveledUp = true,
    )

    private fun buildViewModel() = GameResultStripViewModel(engine, dataStore)

    @Test
    fun `given matching session outcome when observed then exposes that outcome`() = runTest {
        val vm = buildViewModel()
        vm.observe(sessionId = 42L)
        advanceUntilIdle()

        outcomes.emit(ProcessedOutcome(gameFinished(42L), outcome(50)))
        advanceUntilIdle()

        assertEquals(50, vm.outcome.value?.xpGranted)
    }

    @Test
    fun `given outcome for a different session when observed then outcome stays null`() = runTest {
        val vm = buildViewModel()
        vm.observe(sessionId = 42L)
        advanceUntilIdle()

        outcomes.emit(ProcessedOutcome(gameFinished(99L), outcome(50)))
        advanceUntilIdle()

        assertNull(vm.outcome.value)
    }

    @Test
    fun `given outcome buffered before observe when observe runs then it is still delivered`() = runTest {
        // Replay buffer means an outcome processed before subscription is still received.
        outcomes.emit(ProcessedOutcome(gameFinished(7L), outcome(20)))

        val vm = buildViewModel()
        vm.observe(sessionId = 7L)
        advanceUntilIdle()

        assertEquals(20, vm.outcome.value?.xpGranted)
    }

    @Test
    fun `given gamification disabled when matching outcome emitted then outcome stays null`() = runTest {
        enabledFlow.value = false
        val vm = buildViewModel()
        vm.observe(sessionId = 42L)
        advanceUntilIdle()

        outcomes.emit(ProcessedOutcome(gameFinished(42L), outcome(50)))
        advanceUntilIdle()

        assertNull(vm.outcome.value)
    }

    @Test
    fun `given non-positive sessionId when observe then it is ignored`() = runTest {
        val vm = buildViewModel()
        vm.observe(sessionId = 0L)
        advanceUntilIdle()

        outcomes.emit(ProcessedOutcome(gameFinished(0L), outcome(50)))
        advanceUntilIdle()

        assertNull(vm.outcome.value)
    }
}
