package com.mmg.manahub.core.gamification.data.sync

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.StreakEntity
import com.mmg.manahub.core.data.sync.CursorDrainResult
import com.mmg.manahub.core.data.sync.SyncCursor
import com.mmg.manahub.core.data.sync.advancePastPulledRows
import com.mmg.manahub.core.data.sync.drainFromCursor
import com.mmg.manahub.core.data.sync.pushInSlices
import com.mmg.manahub.core.gamification.data.remote.AchievementProgressPageDto
import com.mmg.manahub.core.gamification.data.remote.EntitlementPageDto
import com.mmg.manahub.core.gamification.data.remote.GamificationRemoteDataSource
import com.mmg.manahub.core.gamification.data.remote.StreakPageDto
import com.mmg.manahub.core.gamification.data.remote.XpTransactionPageDto
import com.mmg.manahub.core.gamification.data.remote.toDto
import com.mmg.manahub.core.gamification.data.remote.toEntity
import com.mmg.manahub.core.gamification.data.remote.toUploadDto
import com.mmg.manahub.core.gamification.domain.LevelCurve
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bidirectional Room ↔ Supabase sync for the gamification engine (ADR-002 §11, ADR-008).
 *
 * **Progression is monotonic — NEVER last-write-wins.** Correctness rests on three invariants:
 *  1. **XP is a set-union of ledger rows** keyed by a UNIQUE `idempotency_key`. The server PK
 *     `(user_id, idempotency_key)` with `ON CONFLICT DO NOTHING` and the local UNIQUE index mean a row
 *     can be pushed/pulled any number of times and counted exactly once. Progression is recomputed
 *     locally as `SUM(amount)` after every pull — never incremented per remote row.
 *  2. **The small tables merge non-destructively**: achievements GREATEST(current/tier) + earliest
 *     unlocked/celebrated; entitlements union + earliest unlocked; streaks GREATEST(longest) + latest
 *     last_active_date. Both the server RPCs AND this manager's client-side pull apply that logic, so
 *     over-pushing full state or re-pulling a page can never regress a value.
 *  3. **Cursors only cover applied rows (G-01).** Every pull drains a keyset-paged RPC ordered by a
 *     SERVER-assigned cursor (`server_seq` for the ledger, `(changed_at, pk)` for the mutable tables)
 *     and persists the cursor after each applied page, so a truncated feed, a failed page or a row
 *     pushed late by another device is always fetched again. The push watermark is the local ledger
 *     `id`, advanced per confirmed slice of at most 500 rows, then only over rows this cycle pulled.
 *
 * Deletes do not propagate (the ledger is append-only; the small tables never delete). Quests are
 * deliberately NOT synced (deterministically regenerable). A [Mutex] serializes [sync] and
 * [reconcileOnSignIn] so the two never interleave.
 */
class GamificationSyncManager(
    private val gamificationDao: GamificationDao,
    private val remote: GamificationRemoteDataSource,
    private val syncPrefs: SyncPreferencesStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val crashReporter: CrashReporter,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val syncMutex = Mutex()

    /**
     * Runs a full push-then-pull gamification sync cycle for [userId].
     *
     * PUSH: ledger rows above the `id` watermark in slices, then full-state achievements, entitlements
     * and streaks (monotonic merge). PULL: the ledger from its `server_seq` cursor, a local progression
     * recompute from the ledger sum, then the three keyset tables from their cursors.
     *
     * Returns [Result.failure] when any push or page failed; cursors already persisted stay (they cover
     * only applied rows) and the worker retries the rest.
     */
    suspend fun sync(userId: String): Result<Unit> = withContext(ioDispatcher) {
        syncMutex.withLock { runSync(userId) }
    }

    /**
     * Clears [userId]'s gamification watermarks and cursors then runs a full [sync] — the
     * guest→account merge. Only valid for a guest-owned store being claimed; account scoping decides
     * when (see [GamificationAccountScopeImpl]).
     *
     * The push re-sends EVERY local ledger row and the pull re-fetches the account's full history, so
     * the guest's local progress merges INTO the account without loss or double counting.
     */
    suspend fun reconcileOnSignIn(userId: String): Result<Unit> = withContext(ioDispatcher) {
        syncMutex.withLock {
            syncPrefs.clearGamificationWatermarks(userId)
            runSync(userId)
        }
    }

    /** Mutex-free core of [sync]; callers hold [syncMutex]. */
    private suspend fun runSync(userId: String): Result<Unit> = runCatching {
        if (syncPrefs.resetLegacyGamificationPullWatermark(userId)) {
            crashReporter.log("gamification_sync_legacy_watermark_reset")
        }
        val syncStartTime = clock()

        // ── PUSH: XP ledger (append; server no-ops duplicates) ───────────────
        // The push RPC cannot reject a single row, so a confirmed slice means every row in it is durable.
        var pushedLedgerId = syncPrefs.getGamificationPushedLedgerId(userId)
        pushInSlices(
            rows = gamificationDao.getLedgerAbove(pushedLedgerId),
            keyOf = { it.idempotencyKey },
            push = { slice -> remote.pushXpTransactions(slice.map { it.toUploadDto() }) },
            onSliceConfirmed = { slice ->
                pushedLedgerId = slice.last().id
                syncPrefs.saveGamificationPushedLedgerId(userId, pushedLedgerId)
            },
        ).getOrThrow()

        // ── PUSH: small tables (full state; server merge is GREATEST/earliest/union) ──
        pushInSlices(
            rows = gamificationDao.getAllAchievements(),
            keyOf = { it.achievementId },
            push = { slice -> remote.mergeAchievements(slice.map { it.toDto(syncStartTime) }) },
        ).getOrThrow()
        pushInSlices(
            rows = gamificationDao.getAllEntitlements(),
            keyOf = { it.unlockableId },
            push = { slice -> remote.mergeEntitlements(slice.map { it.toDto(syncStartTime) }) },
        ).getOrThrow()
        pushInSlices(
            rows = gamificationDao.getAllStreaks(),
            keyOf = { it.type },
            push = { slice -> remote.mergeStreaks(slice.map { it.toDto(syncStartTime) }) },
        ).getOrThrow()

        // ── PULL: XP ledger → recompute progression ──────────────────────────
        val pulledLedgerIds = mutableSetOf<Long>()
        val ledgerStart = syncPrefs.getGamificationLedgerCursor(userId)
        val ledgerDrain = drainFromCursor<XpTransactionPageDto>(
            start = if (ledgerStart > 0L) SyncCursor(ledgerStart, "") else null,
            cursorOf = { SyncCursor(it.serverSeq, it.idempotencyKey) },
            fetchPage = { after, limit -> remote.getXpTransactionsPage(after?.position ?: 0L, limit) },
            applyPage = { page ->
                val ids = gamificationDao.insertLedgerRowsIfAbsent(page.map { it.toEntity() })
                ids.filterTo(pulledLedgerIds) { it > 0L }
                true
            },
            saveCursor = { syncPrefs.saveGamificationLedgerCursor(userId, it.position) },
        )
        // Recompute even after a partial drain: whatever was applied is already in the ledger. DIRECT
        // SET, never a grant, so no phantom ledger row is minted.
        val total = gamificationDao.sumAllXp()
        gamificationDao.recomputeProgression(total, LevelCurve.levelForTotalXp(total), syncStartTime)
        ledgerDrain.requireFullyDrained("xp_transactions")

        // Rows this cycle pulled are already on the server; skip them unless a local grant interleaved.
        val advancedLedgerId = advancePastPulledRows(
            watermark = pushedLedgerId,
            idsAbove = gamificationDao.getLedgerIdsAbove(pushedLedgerId),
            pulledIds = pulledLedgerIds,
        )
        if (advancedLedgerId > pushedLedgerId) {
            syncPrefs.saveGamificationPushedLedgerId(userId, advancedLedgerId)
        }

        // ── PULL: achievements (client-side monotonic merge) ─────────────────
        drainKeysetTable<AchievementProgressPageDto>(
            userId = userId,
            table = CURSOR_ACHIEVEMENTS,
            cursorOf = { SyncCursor(it.changedAt, it.achievementId) },
            fetchPage = { after, limit -> remote.getAchievementProgressPage(after?.position, after?.key, limit) },
            applyRow = { dto -> gamificationDao.upsertAchievement(mergeAchievement(dto)) },
        )

        // ── PULL: entitlements (insert-only union keeps the earliest local unlocked_at) ──
        drainKeysetTable<EntitlementPageDto>(
            userId = userId,
            table = CURSOR_ENTITLEMENTS,
            cursorOf = { SyncCursor(it.changedAt, it.unlockableId) },
            fetchPage = { after, limit -> remote.getEntitlementsPage(after?.position, after?.key, limit) },
            applyRow = { dto -> gamificationDao.insertEntitlementIfAbsent(dto.toEntity()) },
        )

        // ── PULL: streaks (GREATEST longest; latest last_active_date wins) ───
        drainKeysetTable<StreakPageDto>(
            userId = userId,
            table = CURSOR_STREAKS,
            cursorOf = { SyncCursor(it.changedAt, it.type) },
            fetchPage = { after, limit -> remote.getStreaksPage(after?.position, after?.key, limit) },
            applyRow = { dto -> gamificationDao.upsertStreak(mergeStreak(dto)) },
        )
        Unit
    }.onFailure { error ->
        if (error is CancellationException) throw error
        crashReporter.apply {
            log("gamification_sync_failed")
            setCustomKey("gamification_sync_error_type", error::class.simpleName ?: "Unknown")
            recordException(error)
        }
    }

    private suspend fun <T> drainKeysetTable(
        userId: String,
        table: String,
        cursorOf: (T) -> SyncCursor,
        fetchPage: suspend (after: SyncCursor?, limit: Int) -> Result<List<T>>,
        applyRow: suspend (T) -> Unit,
    ) {
        drainFromCursor(
            start = syncPrefs.getGamificationKeysetCursor(userId, table),
            cursorOf = cursorOf,
            fetchPage = fetchPage,
            applyPage = { page ->
                page.forEach { applyRow(it) }
                true
            },
            saveCursor = { syncPrefs.saveGamificationKeysetCursor(userId, table, it) },
        ).requireFullyDrained(table)
    }

    private fun CursorDrainResult.requireFullyDrained(table: String) {
        if (!fullyDrained) {
            throw failure ?: IllegalStateException("gamification_pull_incomplete:$table")
        }
    }

    private suspend fun mergeAchievement(dto: AchievementProgressPageDto): AchievementProgressEntity {
        val localRow = gamificationDao.getAchievement(dto.achievementId) ?: return dto.toEntity()
        return localRow.copy(
            currentValue = maxOf(localRow.currentValue, dto.currentValue),
            tierReached = maxOf(localRow.tierReached, dto.tierReached),
            unlockedAt = earliestNonNull(localRow.unlockedAt, dto.unlockedAt),
            // Earliest wins, so a re-pull never re-fires a celebration another device already showed.
            celebratedAt = earliestNonNull(localRow.celebratedAt, dto.celebratedAt),
        )
    }

    private suspend fun mergeStreak(dto: StreakPageDto): StreakEntity {
        val localRow = gamificationDao.getStreak(dto.type) ?: return dto.toEntity()
        return if (dto.lastActiveDate > localRow.lastActiveDate) {
            localRow.copy(
                current = dto.current,
                freezeTokens = dto.freezeTokens,
                lastActiveDate = dto.lastActiveDate,
                longest = maxOf(localRow.longest, dto.longest),
            )
        } else {
            localRow.copy(longest = maxOf(localRow.longest, dto.longest))
        }
    }

    /** Earliest of two nullable epoch-millis timestamps; null only when both are null. */
    private fun earliestNonNull(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }

    private companion object {
        // Stable DataStore tags for the keyset cursors; renaming one forces a full re-pull of that table.
        const val CURSOR_ACHIEVEMENTS = "ach"
        const val CURSOR_ENTITLEMENTS = "ent"
        const val CURSOR_STREAKS = "streak"
    }
}
