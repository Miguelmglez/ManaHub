package com.mmg.manahub.feature.gamification

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.gamification.domain.model.AchievementCategory
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.feature.gamification.presentation.GamificationCelebrationViewModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GamificationCelebrationViewModel] (ADR-002, Phase 1, Chunk B).
 *
 * Covers: the queue exposes the oldest pending item, advances after [markCelebrated], suppresses
 * everything when gamification is disabled (and does NOT mark in that case).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GamificationCelebrationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val repository = mockk<GamificationRepository>(relaxed = true)
    private val dataStore = mockk<UserPreferencesDataStore>(relaxed = true)

    private val pendingFlow = MutableStateFlow<List<AchievementUiModel>>(emptyList())
    private val enabledFlow = MutableStateFlow(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observePendingCelebrations() } returns pendingFlow
        every { dataStore.gamificationEnabledFlow } returns enabledFlow
        coEvery { repository.markCelebrated(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(id: String, unlockedAt: Long) = AchievementUiModel(
        id = id,
        category = AchievementCategory.GAMES,
        titleRes = 0,
        descRes = 0,
        emoji = "⚔️",
        tierThresholds = listOf(1),
        currentValue = 1,
        tierReached = 1,
        maxTier = 1,
        unlockedAt = unlockedAt,
        isSecret = false,
    )

    private fun buildViewModel() = GamificationCelebrationViewModel(repository, dataStore)

    @Test
    fun `given pending celebrations when observed then exposes the oldest first`() = runTest {
        pendingFlow.value = listOf(model("FIRST_WIN", 100L), model("COLLECTOR_1", 200L))
        val vm = buildViewModel()

        vm.current.test {
            assertEquals("FIRST_WIN", awaitItem()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given empty queue when observed then current is null`() = runTest {
        pendingFlow.value = emptyList()
        val vm = buildViewModel()

        vm.current.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given gamification disabled when observed then current is null even with pending`() = runTest {
        enabledFlow.value = false
        pendingFlow.value = listOf(model("FIRST_WIN", 100L))
        val vm = buildViewModel()

        vm.current.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given an item shown when onCelebrationShown then repository markCelebrated is called`() = runTest {
        pendingFlow.value = listOf(model("FIRST_WIN", 100L))
        val vm = buildViewModel()

        vm.onCelebrationShown("FIRST_WIN")

        coVerify(exactly = 1) { repository.markCelebrated("FIRST_WIN") }
    }

    @Test
    fun `given queue advances when first item marked then next item surfaces`() = runTest {
        pendingFlow.value = listOf(model("FIRST_WIN", 100L), model("COLLECTOR_1", 200L))
        val vm = buildViewModel()

        vm.current.test {
            assertEquals("FIRST_WIN", awaitItem()?.id)
            // Simulate the DB dropping the celebrated row → the flow re-emits without it.
            vm.onCelebrationShown("FIRST_WIN")
            pendingFlow.value = listOf(model("COLLECTOR_1", 200L))
            assertEquals("COLLECTOR_1", awaitItem()?.id)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { repository.markCelebrated("FIRST_WIN") }
    }

    @Test
    fun `given gamification disabled when host would dismiss then it is never reached so no mark`() = runTest {
        // When disabled, current stays null, so the host never calls onCelebrationShown. Verify that
        // simply observing a disabled+pending queue does not mark anything.
        enabledFlow.value = false
        pendingFlow.value = listOf(model("FIRST_WIN", 100L))
        val vm = buildViewModel()

        vm.current.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.markCelebrated(any()) }
    }
}
