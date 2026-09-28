package com.mmg.manahub.core.gamification.data.repository

import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.gamification.FixedClock
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableCatalog
import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Availability filtering (D4/D5) and catalog-driven mapping of [GamificationRepositoryImpl]. */
class GamificationRepositoryImplTest {

    private val dao: GamificationDao = mockk(relaxed = true)
    private val dataStore: UserPreferencesDataStore = mockk(relaxed = true)

    private val repository = GamificationRepositoryImpl(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-09-24T10:00:00Z")),
        timeZoneProvider = { TimeZone.UTC },
        userPreferencesDataStore = dataStore,
    )

    @Test
    fun `observeAchievements hides unavailable defs and keeps every available one`() = runTest {
        every { dao.observeAchievements() } returns flowOf(emptyList())

        val ids = repository.observeAchievements().first().map { it.id }

        assertFalse("TOURNAMENT_WIN" in ids)
        assertEquals(FeatureFlags.Puzzle.PUZZLE_ENABLED, "PUZZLE_SOLVER" in ids)
        assertEquals(AchievementCatalog.all.filter { it.isAvailable }.map { it.id }, ids)
    }

    @Test
    fun `observeAchievements maps persisted progress and the fixed copy`() = runTest {
        every { dao.observeAchievements() } returns flowOf(
            listOf(AchievementProgressEntity("WIN_STREAK_5", currentValue = 4, tierReached = 0, unlockedAt = null, celebratedAt = null)),
        )

        val streak5 = repository.observeAchievements().first().first { it.id == "WIN_STREAK_5" }

        assertEquals(4, streak5.currentValue)
        assertEquals("Win 5 games in a row", streak5.description)
    }

    @Test
    fun `pending celebrations never surface an unavailable def`() = runTest {
        every { dao.observePendingCelebrations() } returns flowOf(
            listOf(
                AchievementProgressEntity("TOURNAMENT_WIN", 1, 1, unlockedAt = 1L, celebratedAt = null),
                AchievementProgressEntity("FIRST_WIN", 1, 1, unlockedAt = 2L, celebratedAt = null),
            ),
        )

        val ids = repository.observePendingCelebrations().first().map { it.id }

        assertEquals(listOf("FIRST_WIN"), ids)
    }

    @Test
    fun `observeRewards hides the tournament champion title`() = runTest {
        every { dao.observeEntitlements() } returns flowOf(emptyList())
        every { dataStore.equippedCosmeticsFlow } returns flowOf(EquippedCosmetics())

        val ids = repository.observeRewards().first().all.map { it.id }

        assertFalse("title_tournament_champion" in ids)
        assertEquals(UnlockableCatalog.all.count { it.isAvailable }, ids.size)
        assertTrue(ids.isNotEmpty())
    }
}
