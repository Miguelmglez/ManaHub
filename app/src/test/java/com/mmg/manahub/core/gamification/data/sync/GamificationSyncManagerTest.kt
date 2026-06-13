package com.mmg.manahub.core.gamification.data.sync

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.EntitlementEntity
import com.mmg.manahub.core.data.local.entity.StreakEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.data.remote.AchievementProgressDto
import com.mmg.manahub.core.gamification.data.remote.EntitlementDto
import com.mmg.manahub.core.gamification.data.remote.GamificationRemoteDataSource
import com.mmg.manahub.core.gamification.data.remote.StreakDto
import com.mmg.manahub.core.gamification.data.remote.XpTransactionChangeDto
import com.mmg.manahub.core.gamification.data.remote.XpTransactionUploadDto
import com.mmg.manahub.core.gamification.domain.LevelCurve
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GamificationSyncManager] (ADR-002 §11, Phase 4).
 *
 * Verifies the monotonic, idempotent sync contract:
 *  - PUSH reads only the ledger above the `id` watermark; small tables push full state.
 *  - PULL inserts pulled ledger rows then recomputes progression from `SUM(amount)`.
 *  - Client-side merges are monotonic (achievement max/earliest, entitlement union, streak
 *    latest-date-wins / GREATEST longest).
 *  - Watermarks are saved as `MAX(id)` + `syncStartTime` ONLY after a full success.
 *  - [GamificationSyncManager.reconcileOnSignIn] clears watermarks then syncs.
 *  - A failure short-circuits and does NOT advance any watermark.
 *
 * All collaborators (DAO, remote, prefs) are mocked; no real DB/network. The DAO's
 * `upsertAchievement`/`upsertStreak`/`recomputeProgression` are `open` `@Transaction` methods on an
 * abstract class — a relaxed mock stubs them as no-ops, which is fine since we verify the ARGUMENTS
 * passed to them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GamificationSyncManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    private val dao = mockk<GamificationDao>(relaxed = true)
    private val remote = mockk<GamificationRemoteDataSource>(relaxed = true)
    private val prefs = mockk<SyncPreferencesStore>(relaxed = true)

    private lateinit var manager: GamificationSyncManager

    private val USER_ID = "user-uuid-001"

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        manager = GamificationSyncManager(
            gamificationDao = dao,
            remote = remote,
            syncPrefs = prefs,
            ioDispatcher = testDispatcher,
        )

        // Sensible defaults: empty everything, watermarks at 0, remote calls succeed with empty lists.
        coEvery { prefs.getGamificationPushedLedgerId(USER_ID) } returns 0L
        coEvery { prefs.getGamificationSyncMillis(USER_ID) } returns 0L
        coEvery { dao.getLedgerAbove(any()) } returns emptyList()
        coEvery { dao.getAllAchievements() } returns emptyList()
        coEvery { dao.getAllEntitlements() } returns emptyList()
        coEvery { dao.getAllStreaks() } returns emptyList()
        coEvery { dao.sumAllXp() } returns 0L
        coEvery { dao.getMaxLedgerId() } returns 0L
        coEvery { remote.pushXpTransactions(any()) } returns Result.success(Unit)
        coEvery { remote.mergeAchievements(any()) } returns Result.success(Unit)
        coEvery { remote.mergeEntitlements(any()) } returns Result.success(Unit)
        coEvery { remote.mergeStreaks(any()) } returns Result.success(Unit)
        coEvery { remote.getXpChangesSince(any()) } returns Result.success(emptyList())
        coEvery { remote.getAchievementChangesSince(any()) } returns Result.success(emptyList())
        coEvery { remote.getEntitlementChangesSince(any()) } returns Result.success(emptyList())
        coEvery { remote.getStreakChangesSince(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── PUSH ─────────────────────────────────────────────────────────────────

    @Test
    fun `push reads ledger strictly above the id watermark`() = runTest(testDispatcher) {
        coEvery { prefs.getGamificationPushedLedgerId(USER_ID) } returns 42L
        coEvery { dao.getLedgerAbove(42L) } returns listOf(ledgerEntity(id = 43, key = "k43", amount = 10))

        val captured = slot<List<XpTransactionUploadDto>>()
        coEvery { remote.pushXpTransactions(capture(captured)) } returns Result.success(Unit)

        val result = manager.sync(USER_ID)

        assertTrue(result.isSuccess)
        coVerify { dao.getLedgerAbove(42L) }
        assertEquals(1, captured.captured.size)
        // Upload DTO carries the natural key, NOT the local autoincrement id.
        assertEquals("k43", captured.captured.first().idempotencyKey)
        assertEquals(10, captured.captured.first().amount)
    }

    @Test
    fun `empty ledger above watermark skips the push call`() = runTest(testDispatcher) {
        coEvery { dao.getLedgerAbove(0L) } returns emptyList()

        manager.sync(USER_ID)

        coVerify(exactly = 0) { remote.pushXpTransactions(any()) }
    }

    @Test
    fun `small tables push full local state`() = runTest(testDispatcher) {
        coEvery { dao.getAllAchievements() } returns listOf(
            achievementEntity(id = "ach1", current = 3, tier = 1),
        )
        coEvery { dao.getAllEntitlements() } returns listOf(entitlementEntity(id = "ent1"))
        coEvery { dao.getAllStreaks() } returns listOf(streakEntity(type = "daily", current = 5))

        val achSlot = slot<List<AchievementProgressDto>>()
        val entSlot = slot<List<EntitlementDto>>()
        val strSlot = slot<List<StreakDto>>()
        coEvery { remote.mergeAchievements(capture(achSlot)) } returns Result.success(Unit)
        coEvery { remote.mergeEntitlements(capture(entSlot)) } returns Result.success(Unit)
        coEvery { remote.mergeStreaks(capture(strSlot)) } returns Result.success(Unit)

        manager.sync(USER_ID)

        assertEquals("ach1", achSlot.captured.single().achievementId)
        assertEquals("ent1", entSlot.captured.single().unlockableId)
        assertEquals("daily", strSlot.captured.single().type)
    }

    // ── PULL: ledger + progression recompute ───────────────────────────────────

    @Test
    fun `pull inserts ledger rows and recomputes progression from the sum`() = runTest(testDispatcher) {
        coEvery { remote.getXpChangesSince(0L) } returns Result.success(
            listOf(
                changeDto(key = "remote1", amount = 100),
                changeDto(key = "remote2", amount = 200),
            )
        )
        // After insertion the local sum is 1703 → level 5 (parity boundary).
        coEvery { dao.sumAllXp() } returns 1703L

        manager.sync(USER_ID)

        coVerify(exactly = 2) { dao.insertLedgerRowIfAbsent(any()) }
        coVerify {
            dao.recomputeProgression(1703L, LevelCurve.levelForTotalXp(1703L), any())
        }
    }

    // ── PULL: achievement monotonic merge ──────────────────────────────────────

    @Test
    fun `achievement merge takes max value-tier and earliest unlocked, keeps local celebrated`() =
        runTest(testDispatcher) {
            val local = achievementEntity(
                id = "ach1", current = 2, tier = 1, unlockedAt = 5_000L, celebratedAt = 6_000L,
            )
            coEvery { dao.getAchievement("ach1") } returns local
            coEvery { remote.getAchievementChangesSince(0L) } returns Result.success(
                listOf(
                    AchievementProgressDto(
                        achievementId = "ach1",
                        currentValue = 9,          // higher → wins
                        tierReached = 0,           // lower → local wins
                        unlockedAt = 3_000L,       // earlier → wins
                        celebratedAt = null,       // local celebrated stamp kept
                        updatedAt = 1L,
                    )
                )
            )

            val merged = slot<AchievementProgressEntity>()
            coEvery { dao.upsertAchievement(capture(merged)) } just Runs

            manager.sync(USER_ID)

            assertEquals(9, merged.captured.currentValue)
            assertEquals(1, merged.captured.tierReached)
            assertEquals(3_000L, merged.captured.unlockedAt)
            assertEquals(6_000L, merged.captured.celebratedAt)
        }

    @Test
    fun `achievement merge inserts remote row when none exists locally`() = runTest(testDispatcher) {
        coEvery { dao.getAchievement("ach2") } returns null
        coEvery { remote.getAchievementChangesSince(0L) } returns Result.success(
            listOf(
                AchievementProgressDto(
                    achievementId = "ach2", currentValue = 4, tierReached = 2,
                    unlockedAt = 7_000L, celebratedAt = 7_500L, updatedAt = 1L,
                )
            )
        )

        val merged = slot<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(merged)) } just Runs

        manager.sync(USER_ID)

        assertEquals("ach2", merged.captured.achievementId)
        assertEquals(4, merged.captured.currentValue)
        assertEquals(7_000L, merged.captured.unlockedAt)
    }

    // ── PULL: entitlement union ────────────────────────────────────────────────

    @Test
    fun `entitlement pull inserts via insertIfAbsent preserving existing local row`() =
        runTest(testDispatcher) {
            coEvery { remote.getEntitlementChangesSince(0L) } returns Result.success(
                listOf(
                    EntitlementDto(
                        unlockableId = "cosmetic1", unlockedAt = 9_000L,
                        source = "ACHIEVEMENT", updatedAt = 1L,
                    )
                )
            )

            manager.sync(USER_ID)

            // Union semantics: insertIfAbsent keeps the earliest-existing local row.
            coVerify { dao.insertEntitlementIfAbsent(match { it.unlockableId == "cosmetic1" }) }
        }

    // ── PULL: streak latest-date-wins ──────────────────────────────────────────

    @Test
    fun `streak merge adopts remote when its date is newer, keeps greatest longest`() =
        runTest(testDispatcher) {
            val local = streakEntity(
                type = "daily", current = 3, longest = 10, lastActiveDate = "2026-06-10", freeze = 1,
            )
            coEvery { dao.getStreak("daily") } returns local
            coEvery { remote.getStreakChangesSince(0L) } returns Result.success(
                listOf(
                    StreakDto(
                        type = "daily", current = 7, longest = 8,
                        lastActiveDate = "2026-06-12", freezeTokens = 2, updatedAt = 1L,
                    )
                )
            )

            val merged = slot<StreakEntity>()
            coEvery { dao.upsertStreak(capture(merged)) } just Runs

            manager.sync(USER_ID)

            assertEquals(7, merged.captured.current)             // remote newer date
            assertEquals(2, merged.captured.freezeTokens)
            assertEquals("2026-06-12", merged.captured.lastActiveDate)
            assertEquals(10, merged.captured.longest)            // GREATEST(10, 8)
        }

    @Test
    fun `streak merge keeps local current when local date is newer-or-equal but raises longest`() =
        runTest(testDispatcher) {
            val local = streakEntity(
                type = "daily", current = 9, longest = 9, lastActiveDate = "2026-06-12", freeze = 2,
            )
            coEvery { dao.getStreak("daily") } returns local
            coEvery { remote.getStreakChangesSince(0L) } returns Result.success(
                listOf(
                    StreakDto(
                        type = "daily", current = 4, longest = 20,
                        lastActiveDate = "2026-06-11", freezeTokens = 0, updatedAt = 1L,
                    )
                )
            )

            val merged = slot<StreakEntity>()
            coEvery { dao.upsertStreak(capture(merged)) } just Runs

            manager.sync(USER_ID)

            assertEquals(9, merged.captured.current)             // local date >= remote → kept
            assertEquals("2026-06-12", merged.captured.lastActiveDate)
            assertEquals(20, merged.captured.longest)            // GREATEST(9, 20)
        }

    // ── Watermarks ─────────────────────────────────────────────────────────────

    @Test
    fun `watermarks saved as max id and sync start time after a successful cycle`() =
        runTest(testDispatcher) {
            coEvery { dao.getMaxLedgerId() } returns 99L

            val savedId = slot<Long>()
            val savedMs = slot<Long>()
            coEvery { prefs.saveGamificationPushedLedgerId(USER_ID, capture(savedId)) } just Runs
            coEvery { prefs.saveGamificationSyncMillis(USER_ID, capture(savedMs)) } just Runs

            manager.sync(USER_ID)

            assertEquals(99L, savedId.captured)
            assertTrue("sync time stamped", savedMs.captured > 0L)
        }

    @Test
    fun `failure short-circuits and does not advance any watermark`() = runTest(testDispatcher) {
        coEvery { remote.getXpChangesSince(any()) } returns
            Result.failure(RuntimeException("network down"))

        val result = manager.sync(USER_ID)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { prefs.saveGamificationPushedLedgerId(any(), any()) }
        coVerify(exactly = 0) { prefs.saveGamificationSyncMillis(any(), any()) }
    }

    // ── reconcileOnSignIn ──────────────────────────────────────────────────────

    @Test
    fun `reconcileOnSignIn clears watermarks then runs a full sync`() = runTest(testDispatcher) {
        coEvery { prefs.clearGamificationWatermarks(USER_ID) } just Runs
        coEvery { dao.getMaxLedgerId() } returns 5L

        val result = manager.reconcileOnSignIn(USER_ID)

        assertTrue(result.isSuccess)
        coVerify { prefs.clearGamificationWatermarks(USER_ID) }
        // The full sync ran afterwards (watermarks re-saved).
        coVerify { prefs.saveGamificationPushedLedgerId(USER_ID, 5L) }
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private fun ledgerEntity(id: Long, key: String, amount: Int) = XpTransactionEntity(
        id = id, idempotencyKey = key, amount = amount,
        sourceCategory = "GAME_RESULT", sourceRef = null, createdAt = 1_000L,
    )

    private fun changeDto(key: String, amount: Int) = XpTransactionChangeDto(
        userId = USER_ID, idempotencyKey = key, amount = amount,
        sourceCategory = "GAME_RESULT", sourceRef = null, createdAt = 1_000L,
    )

    private fun achievementEntity(
        id: String, current: Int, tier: Int, unlockedAt: Long? = null, celebratedAt: Long? = null,
    ) = AchievementProgressEntity(
        achievementId = id, currentValue = current, tierReached = tier,
        unlockedAt = unlockedAt, celebratedAt = celebratedAt,
    )

    private fun entitlementEntity(id: String) = EntitlementEntity(
        unlockableId = id, unlockedAt = 1_000L, source = "LEVEL_UP",
    )

    private fun streakEntity(
        type: String, current: Int = 1, longest: Int = 1,
        lastActiveDate: String = "2026-06-10", freeze: Int = 0,
    ) = StreakEntity(
        type = type, current = current, longest = longest,
        lastActiveDate = lastActiveDate, freezeTokens = freeze,
    )
}
