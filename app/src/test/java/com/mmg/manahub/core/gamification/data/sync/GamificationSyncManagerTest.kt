package com.mmg.manahub.core.gamification.data.sync

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.EntitlementEntity
import com.mmg.manahub.core.data.local.entity.StreakEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.data.sync.SyncCursor
import com.mmg.manahub.core.gamification.data.remote.AchievementProgressDto
import com.mmg.manahub.core.gamification.data.remote.AchievementProgressPageDto
import com.mmg.manahub.core.gamification.data.remote.EntitlementDto
import com.mmg.manahub.core.gamification.data.remote.EntitlementPageDto
import com.mmg.manahub.core.gamification.data.remote.GamificationRemoteDataSource
import com.mmg.manahub.core.gamification.data.remote.StreakDto
import com.mmg.manahub.core.gamification.data.remote.StreakPageDto
import com.mmg.manahub.core.gamification.data.remote.XpTransactionPageDto
import com.mmg.manahub.core.gamification.data.remote.XpTransactionUploadDto
import com.mmg.manahub.core.gamification.domain.LevelCurve
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GamificationSyncManager] (ADR-002 §11, ADR-008, drift audit G-01).
 *
 * The remote is an in-memory fake that pages exactly like the `get_*_page` RPCs (cap 500, ascending
 * server cursor); the prefs and the local ledger are in-memory too, so multi-page drains, cursor
 * persistence and chunked pushes are exercised end to end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GamificationSyncManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    private val dao = mockk<GamificationDao>(relaxed = true)
    private val prefs = mockk<SyncPreferencesStore>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val remote = FakeRemote()

    private val prefStore = mutableMapOf<String, Any>()
    private val localLedger = mutableListOf<XpTransactionEntity>()

    private lateinit var manager: GamificationSyncManager

    private val userId = "user-uuid-001"

    @Before
    fun setUp() {
        manager = GamificationSyncManager(
            gamificationDao = dao,
            remote = remote,
            syncPrefs = prefs,
            ioDispatcher = testDispatcher,
            crashReporter = crashReporter,
            clock = { 1_000_000L },
        )
        stubPrefs()
        stubLocalLedger()
        coEvery { dao.getAllAchievements() } returns emptyList()
        coEvery { dao.getAllEntitlements() } returns emptyList()
        coEvery { dao.getAllStreaks() } returns emptyList()
        coEvery { dao.getAchievement(any()) } returns null
        coEvery { dao.getStreak(any()) } returns null
    }

    // ── PUSH ─────────────────────────────────────────────────────────────────

    @Test
    fun `push reads ledger strictly above the id watermark`() = runTest(testDispatcher) {
        prefStore["pushed"] = 42L
        localLedger += ledgerEntity(id = 42, key = "k42", amount = 5)
        localLedger += ledgerEntity(id = 43, key = "k43", amount = 10)

        val result = manager.sync(userId)

        assertTrue(result.isSuccess)
        val pushed = remote.pushedLedgerSlices.single()
        assertEquals(listOf("k43"), pushed.map { it.idempotencyKey })
        assertEquals(10, pushed.single().amount)
        assertEquals(43L, prefStore["pushed"])
    }

    @Test
    fun `empty ledger above watermark skips the push call`() = runTest(testDispatcher) {
        manager.sync(userId)

        assertTrue(remote.pushedLedgerSlices.isEmpty())
    }

    @Test
    fun `ledger push is chunked into slices of at most 500 and the watermark advances per slice`() =
        runTest(testDispatcher) {
            (1L..1_200L).forEach { localLedger += ledgerEntity(id = it, key = "k$it", amount = 1) }
            remote.failLedgerPushAtSlice = 2

            val result = manager.sync(userId)

            assertTrue(result.isFailure)
            assertEquals(listOf(500, 500), remote.pushedLedgerSlices.map { it.size })
            // Slice 1 confirmed, slice 2 failed: the watermark covers exactly the confirmed rows.
            assertEquals(500L, prefStore["pushed"])
            assertTrue(remote.pushedLedgerSlices.all { slice -> slice.map { it.idempotencyKey }.toSet().size == slice.size })

            remote.failLedgerPushAtSlice = null
            remote.pushedLedgerSlices.clear()
            assertTrue(manager.sync(userId).isSuccess)
            assertEquals(listOf(500, 200), remote.pushedLedgerSlices.map { it.size })
            assertEquals(1_200L, prefStore["pushed"])
        }

    @Test
    fun `small tables push full local state`() = runTest(testDispatcher) {
        coEvery { dao.getAllAchievements() } returns listOf(achievementEntity(id = "ach1", current = 3, tier = 1))
        coEvery { dao.getAllEntitlements() } returns listOf(entitlementEntity(id = "ent1"))
        coEvery { dao.getAllStreaks() } returns listOf(streakEntity(type = "daily", current = 5))

        manager.sync(userId)

        assertEquals("ach1", remote.mergedAchievements.single().single().achievementId)
        assertEquals("ent1", remote.mergedEntitlements.single().single().unlockableId)
        assertEquals("daily", remote.mergedStreaks.single().single().type)
    }

    // ── PULL: ledger ───────────────────────────────────────────────────────────

    @Test
    fun `ledger pull drains more than 500 rows across pages and recomputes progression`() =
        runTest(testDispatcher) {
            (1L..1_203L).forEach { remote.serverLedger += pageDto(seq = it, key = "r$it", amount = 1) }

            val result = manager.sync(userId)

            assertTrue(result.isSuccess)
            assertEquals(listOf(0L, 500L, 1_000L), remote.ledgerRequests)
            assertEquals(1_203, localLedger.size)
            assertEquals(1_203L, prefStore["xp_seq"])
            coVerify { dao.recomputeProgression(1_203L, LevelCurve.levelForTotalXp(1_203L), 1_000_000L) }
        }

    @Test
    fun `a row pushed late with an old created_at is still pulled through server_seq`() =
        runTest(testDispatcher) {
            remote.serverLedger += pageDto(seq = 1, key = "early", amount = 10, createdAt = 5_000L)
            assertTrue(manager.sync(userId).isSuccess)
            assertEquals(1L, prefStore["xp_seq"])

            // Another device pushes a row created long before this device's last sync.
            remote.serverLedger += pageDto(seq = 2, key = "late", amount = 20, createdAt = 1L)
            assertTrue(manager.sync(userId).isSuccess)

            assertEquals(listOf(0L, 1L), remote.ledgerRequests)
            assertTrue(localLedger.any { it.idempotencyKey == "late" })
            assertEquals(2L, prefStore["xp_seq"])
        }

    @Test
    fun `a failed page keeps the cursor on the last applied row and the next sync resumes there`() =
        runTest(testDispatcher) {
            (1L..700L).forEach { remote.serverLedger += pageDto(seq = it, key = "r$it", amount = 1) }
            remote.failLedgerPageAfter = 500L

            val result = manager.sync(userId)

            assertTrue(result.isFailure)
            assertEquals(500L, prefStore["xp_seq"])
            // Progression reflects what was applied even though the cycle failed.
            coVerify { dao.recomputeProgression(500L, any(), any()) }

            remote.failLedgerPageAfter = null
            remote.ledgerRequests.clear()
            assertTrue(manager.sync(userId).isSuccess)
            assertEquals(listOf(500L), remote.ledgerRequests)
            assertEquals(700, localLedger.size)
            assertEquals(700L, prefStore["xp_seq"])
        }

    @Test
    fun `a local grant made during the pull is not skipped by the echo watermark`() =
        runTest(testDispatcher) {
            remote.serverLedger += pageDto(seq = 1, key = "remote1", amount = 1)
            remote.serverLedger += pageDto(seq = 2, key = "remote2", amount = 1)
            remote.onLedgerPageServed = {
                // A local grant lands between the pulled rows' inserts and the watermark update.
                localLedger += ledgerEntity(id = nextLocalId(), key = "local-grant", amount = 5)
            }

            assertTrue(manager.sync(userId).isSuccess)
            // Local grant got id 1; the pulled rows got 2 and 3 -> the watermark must stay below 1.
            assertEquals(0L, prefStore["pushed"] ?: 0L)

            remote.onLedgerPageServed = null
            assertTrue(manager.sync(userId).isSuccess)
            assertTrue(remote.pushedLedgerSlices.flatten().any { it.idempotencyKey == "local-grant" })
        }

    @Test
    fun `pulled rows above the push watermark advance it so they are never re-pushed`() =
        runTest(testDispatcher) {
            remote.serverLedger += pageDto(seq = 1, key = "remote1", amount = 1)

            assertTrue(manager.sync(userId).isSuccess)
            assertEquals(1L, prefStore["pushed"])

            assertTrue(manager.sync(userId).isSuccess)
            assertTrue(remote.pushedLedgerSlices.isEmpty())
        }

    // ── PULL: keyset tables ────────────────────────────────────────────────────

    @Test
    fun `achievement pull pages on changed_at plus id and persists the cursor per page`() =
        runTest(testDispatcher) {
            (1..501).forEach { i ->
                remote.serverAchievements += achievementPage(id = "a%04d".format(i), changedAt = 10L)
            }

            assertTrue(manager.sync(userId).isSuccess)

            assertEquals(listOf<SyncCursor?>(null, SyncCursor(10L, "a0500")), remote.achievementRequests)
            assertEquals(SyncCursor(10L, "a0501"), prefStore["cursor_ach"])
        }

    @Test
    fun `achievement merge takes max value-tier and earliest unlocked, keeps local celebrated`() =
        runTest(testDispatcher) {
            coEvery { dao.getAchievement("ach1") } returns achievementEntity(
                id = "ach1", current = 2, tier = 1, unlockedAt = 5_000L, celebratedAt = 6_000L,
            )
            remote.serverAchievements += achievementPage(
                id = "ach1", current = 9, tier = 0, unlockedAt = 3_000L, celebratedAt = null,
            )
            val merged = slot<AchievementProgressEntity>()
            coEvery { dao.upsertAchievement(capture(merged)) } just Runs

            manager.sync(userId)

            assertEquals(9, merged.captured.currentValue)
            assertEquals(1, merged.captured.tierReached)
            assertEquals(3_000L, merged.captured.unlockedAt)
            assertEquals(6_000L, merged.captured.celebratedAt)
        }

    @Test
    fun `achievement merge inserts remote row when none exists locally`() = runTest(testDispatcher) {
        coEvery { dao.getAchievement("ach2") } returns null
        remote.serverAchievements += achievementPage(
            id = "ach2", current = 4, tier = 2, unlockedAt = 7_000L, celebratedAt = 7_500L,
        )
        val merged = slot<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(merged)) } just Runs

        manager.sync(userId)

        assertEquals("ach2", merged.captured.achievementId)
        assertEquals(4, merged.captured.currentValue)
        assertEquals(7_000L, merged.captured.unlockedAt)
    }

    @Test
    fun `entitlement pull inserts via insertIfAbsent preserving existing local row`() =
        runTest(testDispatcher) {
            remote.serverEntitlements += EntitlementPageDto(
                unlockableId = "cosmetic1", unlockedAt = 9_000L, source = "ACHIEVEMENT",
                updatedAt = 1L, changedAt = 3L,
            )

            manager.sync(userId)

            coVerify { dao.insertEntitlementIfAbsent(match { it.unlockableId == "cosmetic1" }) }
            assertEquals(SyncCursor(3L, "cosmetic1"), prefStore["cursor_ent"])
        }

    @Test
    fun `streak merge adopts remote when its date is newer, keeps greatest longest`() =
        runTest(testDispatcher) {
            coEvery { dao.getStreak("daily") } returns streakEntity(
                type = "daily", current = 3, longest = 10, lastActiveDate = "2026-06-10", freeze = 1,
            )
            remote.serverStreaks += streakPage(
                type = "daily", current = 7, longest = 8, lastActiveDate = "2026-06-12", freeze = 2,
            )
            val merged = slot<StreakEntity>()
            coEvery { dao.upsertStreak(capture(merged)) } just Runs

            manager.sync(userId)

            assertEquals(7, merged.captured.current)
            assertEquals(2, merged.captured.freezeTokens)
            assertEquals("2026-06-12", merged.captured.lastActiveDate)
            assertEquals(10, merged.captured.longest)
        }

    @Test
    fun `streak merge keeps local current when local date is newer-or-equal but raises longest`() =
        runTest(testDispatcher) {
            coEvery { dao.getStreak("daily") } returns streakEntity(
                type = "daily", current = 9, longest = 9, lastActiveDate = "2026-06-12", freeze = 2,
            )
            remote.serverStreaks += streakPage(
                type = "daily", current = 4, longest = 20, lastActiveDate = "2026-06-11", freeze = 0,
            )
            val merged = slot<StreakEntity>()
            coEvery { dao.upsertStreak(capture(merged)) } just Runs

            manager.sync(userId)

            assertEquals(9, merged.captured.current)
            assertEquals("2026-06-12", merged.captured.lastActiveDate)
            assertEquals(20, merged.captured.longest)
        }

    @Test
    fun `a keyset page failure fails the cycle without touching that table's cursor`() =
        runTest(testDispatcher) {
            remote.serverStreaks += streakPage(type = "daily")
            remote.failStreakPages = true

            val result = manager.sync(userId)

            assertTrue(result.isFailure)
            assertNull(prefStore["cursor_streak"])
        }

    // ── Legacy watermark migration ─────────────────────────────────────────────

    @Test
    fun `the legacy millis watermark is reset once, forcing one full paged pull`() =
        runTest(testDispatcher) {
            prefStore["legacy_ms"] = 999_999L
            prefStore["xp_seq"] = 77L
            prefStore["cursor_ach"] = SyncCursor(5L, "stale")
            remote.serverLedger += pageDto(seq = 3, key = "r3", amount = 1)

            assertTrue(manager.sync(userId).isSuccess)
            assertEquals(listOf(0L), remote.ledgerRequests)
            assertEquals(listOf<SyncCursor?>(null), remote.achievementRequests)
            assertFalse(prefStore.containsKey("legacy_ms"))

            remote.ledgerRequests.clear()
            assertTrue(manager.sync(userId).isSuccess)
            assertEquals(listOf(3L), remote.ledgerRequests)
            coVerify(exactly = 1) { crashReporter.log("gamification_sync_legacy_watermark_reset") }
        }

    // ── reconcileOnSignIn ──────────────────────────────────────────────────────

    @Test
    fun `reconcileOnSignIn clears watermarks then runs a full sync`() = runTest(testDispatcher) {
        prefStore["pushed"] = 5L
        prefStore["xp_seq"] = 9L
        localLedger += ledgerEntity(id = 1, key = "guest1", amount = 3)

        val result = manager.reconcileOnSignIn(userId)

        assertTrue(result.isSuccess)
        coVerify { prefs.clearGamificationWatermarks(userId) }
        assertEquals(listOf("guest1"), remote.pushedLedgerSlices.flatten().map { it.idempotencyKey })
        assertEquals(listOf(0L), remote.ledgerRequests)
    }

    // ── Fakes ──────────────────────────────────────────────────────────────────

    private fun stubPrefs() {
        coEvery { prefs.resetLegacyGamificationPullWatermark(userId) } answers {
            if (prefStore.remove("legacy_ms") != null) {
                prefStore.keys.removeAll { it == "xp_seq" || it.startsWith("cursor_") }
                true
            } else {
                false
            }
        }
        coEvery { prefs.clearGamificationWatermarks(userId) } answers {
            prefStore.keys.removeAll { it == "pushed" || it == "xp_seq" || it == "legacy_ms" || it.startsWith("cursor_") }
        }
        coEvery { prefs.getGamificationPushedLedgerId(userId) } answers { prefStore["pushed"] as Long? ?: 0L }
        coEvery { prefs.saveGamificationPushedLedgerId(userId, any()) } answers { prefStore["pushed"] = secondArg<Long>() }
        coEvery { prefs.getGamificationLedgerCursor(userId) } answers { prefStore["xp_seq"] as Long? ?: 0L }
        coEvery { prefs.saveGamificationLedgerCursor(userId, any()) } answers { prefStore["xp_seq"] = secondArg<Long>() }
        coEvery { prefs.getGamificationKeysetCursor(userId, any()) } answers {
            prefStore["cursor_${secondArg<String>()}"] as SyncCursor?
        }
        coEvery { prefs.saveGamificationKeysetCursor(userId, any(), any()) } answers {
            prefStore["cursor_${secondArg<String>()}"] = thirdArg<SyncCursor>()
        }
    }

    private fun nextLocalId(): Long = (localLedger.maxOfOrNull { it.id } ?: 0L) + 1

    private fun stubLocalLedger() {
        coEvery { dao.getLedgerAbove(any()) } answers {
            localLedger.filter { it.id > firstArg<Long>() }.sortedBy { it.id }
        }
        coEvery { dao.getLedgerIdsAbove(any()) } answers {
            localLedger.filter { it.id > firstArg<Long>() }.map { it.id }.sorted()
        }
        coEvery { dao.insertLedgerRowsIfAbsent(any()) } answers {
            firstArg<List<XpTransactionEntity>>().map { row ->
                if (localLedger.any { it.idempotencyKey == row.idempotencyKey }) {
                    -1L
                } else {
                    val id = nextLocalId()
                    localLedger += row.copy(id = id)
                    id
                }
            }
        }
        coEvery { dao.sumAllXp() } answers { localLedger.sumOf { it.amount.toLong() } }
    }

    /** Pages exactly like the `get_*_page` RPCs: ascending server cursor, strictly after, cap 500. */
    private class FakeRemote : GamificationRemoteDataSource {
        val serverLedger = mutableListOf<XpTransactionPageDto>()
        val serverAchievements = mutableListOf<AchievementProgressPageDto>()
        val serverEntitlements = mutableListOf<EntitlementPageDto>()
        val serverStreaks = mutableListOf<StreakPageDto>()

        val pushedLedgerSlices = mutableListOf<List<XpTransactionUploadDto>>()
        val mergedAchievements = mutableListOf<List<AchievementProgressDto>>()
        val mergedEntitlements = mutableListOf<List<EntitlementDto>>()
        val mergedStreaks = mutableListOf<List<StreakDto>>()
        val ledgerRequests = mutableListOf<Long>()
        val achievementRequests = mutableListOf<SyncCursor?>()

        var failLedgerPushAtSlice: Int? = null
        var failLedgerPageAfter: Long? = null
        var failStreakPages = false
        var onLedgerPageServed: (() -> Unit)? = null

        override suspend fun pushXpTransactions(rows: List<XpTransactionUploadDto>): Result<Unit> {
            pushedLedgerSlices += rows
            return if (pushedLedgerSlices.size == failLedgerPushAtSlice) {
                Result.failure(RuntimeException("push failed"))
            } else {
                Result.success(Unit)
            }
        }

        override suspend fun getXpTransactionsPage(afterSeq: Long, limit: Int): Result<List<XpTransactionPageDto>> {
            ledgerRequests += afterSeq
            if (failLedgerPageAfter == afterSeq) return Result.failure(RuntimeException("page failed"))
            val page = serverLedger.filter { it.serverSeq > afterSeq }.sortedBy { it.serverSeq }.take(minOf(limit, 500))
            onLedgerPageServed?.invoke()
            onLedgerPageServed = null
            return Result.success(page)
        }

        override suspend fun mergeAchievements(rows: List<AchievementProgressDto>): Result<Unit> {
            mergedAchievements += rows
            return Result.success(Unit)
        }

        override suspend fun getAchievementProgressPage(
            afterChangedAt: Long?,
            afterAchievementId: String?,
            limit: Int,
        ): Result<List<AchievementProgressPageDto>> {
            achievementRequests += if (afterChangedAt != null && afterAchievementId != null) {
                SyncCursor(afterChangedAt, afterAchievementId)
            } else {
                null
            }
            return Result.success(
                keysetPage(serverAchievements, afterChangedAt, afterAchievementId, limit, { it.changedAt }, { it.achievementId }),
            )
        }

        override suspend fun mergeEntitlements(rows: List<EntitlementDto>): Result<Unit> {
            mergedEntitlements += rows
            return Result.success(Unit)
        }

        override suspend fun getEntitlementsPage(
            afterChangedAt: Long?,
            afterUnlockableId: String?,
            limit: Int,
        ): Result<List<EntitlementPageDto>> = Result.success(
            keysetPage(serverEntitlements, afterChangedAt, afterUnlockableId, limit, { it.changedAt }, { it.unlockableId }),
        )

        override suspend fun mergeStreaks(rows: List<StreakDto>): Result<Unit> {
            mergedStreaks += rows
            return Result.success(Unit)
        }

        override suspend fun getStreaksPage(
            afterChangedAt: Long?,
            afterType: String?,
            limit: Int,
        ): Result<List<StreakPageDto>> {
            if (failStreakPages) return Result.failure(RuntimeException("page failed"))
            return Result.success(
                keysetPage(serverStreaks, afterChangedAt, afterType, limit, { it.changedAt }, { it.type }),
            )
        }

        private fun <T> keysetPage(
            rows: List<T>,
            afterChangedAt: Long?,
            afterKey: String?,
            limit: Int,
            changedAt: (T) -> Long,
            key: (T) -> String,
        ): List<T> = rows
            .sortedWith(compareBy<T>({ changedAt(it) }, { key(it) }))
            .filter { row ->
                afterChangedAt == null || afterKey == null ||
                    changedAt(row) > afterChangedAt ||
                    (changedAt(row) == afterChangedAt && key(row) > afterKey)
            }
            .take(minOf(limit, 500))
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private fun ledgerEntity(id: Long, key: String, amount: Int) = XpTransactionEntity(
        id = id, idempotencyKey = key, amount = amount,
        sourceCategory = "GAME_RESULT", sourceRef = null, createdAt = 1_000L,
    )

    private fun pageDto(seq: Long, key: String, amount: Int, createdAt: Long = 1_000L) = XpTransactionPageDto(
        serverSeq = seq, idempotencyKey = key, amount = amount,
        sourceCategory = "GAME_RESULT", sourceRef = null, createdAt = createdAt,
    )

    private fun achievementPage(
        id: String,
        current: Int = 1,
        tier: Int = 0,
        unlockedAt: Long? = null,
        celebratedAt: Long? = null,
        changedAt: Long = 1L,
    ) = AchievementProgressPageDto(
        achievementId = id, currentValue = current, tierReached = tier, unlockedAt = unlockedAt,
        celebratedAt = celebratedAt, updatedAt = 1L, changedAt = changedAt,
    )

    private fun streakPage(
        type: String,
        current: Int = 1,
        longest: Int = 1,
        lastActiveDate: String = "2026-06-10",
        freeze: Int = 0,
    ) = StreakPageDto(
        type = type, current = current, longest = longest, lastActiveDate = lastActiveDate,
        freezeTokens = freeze, updatedAt = 1L, changedAt = 1L,
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
