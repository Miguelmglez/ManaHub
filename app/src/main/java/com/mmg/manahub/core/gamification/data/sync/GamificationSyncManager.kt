package com.mmg.manahub.core.gamification.data.sync

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.gamification.data.remote.GamificationRemoteDataSource
import com.mmg.manahub.core.gamification.data.remote.toDto
import com.mmg.manahub.core.gamification.data.remote.toEntity
import com.mmg.manahub.core.gamification.data.remote.toUploadDto
import com.mmg.manahub.core.gamification.domain.LevelCurve
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bidirectional Room ↔ Supabase sync for the gamification engine (ADR-002 §11, Phase 4).
 *
 * **Progression is monotonic — NEVER last-write-wins.** Correctness rests on three invariants:
 *  1. **XP is a set-union of ledger rows** keyed by a UNIQUE `idempotency_key`. The server PK
 *     `(user_id, idempotency_key)` with `ON CONFLICT DO NOTHING` and the local UNIQUE index mean a row
 *     can be pushed/pulled any number of times and counted exactly once. Progression is recomputed
 *     locally as `SUM(amount)` after every pull — never incremented per remote row.
 *  2. **The small tables merge non-destructively**: achievements GREATEST(current/tier) + earliest
 *     unlocked/celebrated; entitlements union + earliest unlocked; streaks GREATEST(longest) + latest
 *     last_active_date. Both the server RPCs AND this manager's client-side pull apply that logic, so
 *     over-pushing full state can never regress a value.
 *  3. **Watermarks are crash-safe**: the PUSH cursor is the strictly-increasing ledger `id`; the PULL
 *     cursor is `syncStartTime` snapshotted BEFORE the pull (rows written during the pull are caught
 *     next cycle). Watermarks advance ONLY after a fully successful cycle. The PULL cursor is saved with
 *     a `PULL_WATERMARK_SAFETY_MARGIN_MS` subtracted (clamped monotonic via `coerceAtLeast(lastSyncMs)`)
 *     so the next pull re-fetches a small overlapping window: `syncStartTime` is THIS device's clock, and
 *     if it runs ahead of the server a remote row with a slightly-lower server `updated_at` could
 *     otherwise be skipped. The overlap is free because all pull merges are idempotent/monotonic.
 *     RESIDUAL RISK: this only widens the window — a true fix would key the cursor on a SERVER-provided
 *     timestamp (returned by the change RPCs) instead of the device clock, which is an RPC change and
 *     out of scope here.
 *
 * Quests are deliberately NOT synced (deterministically regenerable). A [Mutex] serializes [sync] and
 * [reconcileOnSignIn] so the two never interleave.
 */
@Singleton
class GamificationSyncManager @Inject constructor(
    private val gamificationDao: GamificationDao,
    private val remote: GamificationRemoteDataSource,
    private val syncPrefs: SyncPreferencesStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val syncMutex = Mutex()

    private companion object {
        /**
         * Safety margin subtracted from the saved PULL watermark so the next pull re-fetches a small
         * overlapping time window. The pull cursor is THIS device's `System.currentTimeMillis()`, compared
         * against server `updated_at`; if this device's clock runs ahead of the server, a row written by
         * ANOTHER device with a server `updated_at` slightly below this device's `syncStartTime` would fall
         * under the cursor and be skipped on the next pull. Re-fetching the overlap is free because EVERY
         * pull merge is idempotent/monotonic (insert-if-absent ledger; GREATEST/earliest/union small tables).
         */
        private const val PULL_WATERMARK_SAFETY_MARGIN_MS = 5 * 60 * 1000L // 5 min
    }

    /**
     * Runs a full push-then-pull gamification sync cycle for [userId].
     *
     * PUSH: ledger rows above the `id` watermark (append/no-op on the server) + full-state achievements,
     * entitlements and streaks (monotonic merge). PULL: ledger changes since the millis watermark
     * (inserted with idempotency dedupe), then a local progression recompute from the ledger sum,
     * then achievement/entitlement/streak changes merged client-side. COMMIT: push watermark =
     * `MAX(id)` (covers pre-existing AND just-pulled rows so they aren't re-pushed), pull watermark =
     * `syncStartTime`.
     *
     * On any failure the watermarks are NOT advanced and a [Result.failure] is returned (the caller —
     * the worker — retries). Re-running after a partial failure is safe: every operation is idempotent.
     */
    suspend fun sync(userId: String): Result<Unit> = withContext(ioDispatcher) {
        syncMutex.withLock { runSync(userId) }
    }

    /**
     * Clears [userId]'s gamification watermarks then runs a full [sync] — the guest→account merge.
     *
     * Resetting the watermarks forces the PUSH to re-send EVERY local ledger row and the PULL to
     * re-fetch the account's full history, so an anonymous/guest's local progress merges INTO the
     * account. The monotonic server + client merges make this loss-free and duplication-free even when
     * the account already had progress (GREATEST/earliest/union give the correct merged truth; the
     * ledger UNIQUE key prevents any XP double-count on replay).
     *
     * Runs under the same [Mutex] as [sync] so a periodic sync cannot interleave with the reconcile.
     */
    suspend fun reconcileOnSignIn(userId: String): Result<Unit> = withContext(ioDispatcher) {
        syncMutex.withLock {
            syncPrefs.clearGamificationWatermarks(userId)
            runSync(userId)
        }
    }

    /** Mutex-free core of [sync]; callers hold [syncMutex]. */
    private suspend fun runSync(userId: String): Result<Unit> = runCatching {
        val pushedLedgerId = syncPrefs.getGamificationPushedLedgerId(userId)
        val lastSyncMs = syncPrefs.getGamificationSyncMillis(userId)

        // ── PUSH: XP ledger (append; server no-ops duplicates) ───────────────
        // PUSH-WATERMARK CONTRACT (read before touching the watermark commit below): the push RPCs are
        // APPEND-ONLY with `ON CONFLICT DO NOTHING` on the server PK `(user_id, idempotency_key)`. They
        // return Unit and CANNOT reject a single row — a valid ledger row either inserts or no-ops a
        // duplicate. Therefore a non-throwing `pushXpTransactions` result means EVERY pushed row is
        // durably applied; there is no per-row rejection to reconcile. This is precisely why the push
        // watermark may advance to MAX(id) after a fully-successful cycle (see the commit block below).
        //
        // ⚠️ FUTURE BRITTLENESS: if any push RPC ever gains partial-success semantics (e.g. it returns a
        // set of applied/rejected ids), this contract breaks — advancing to MAX(id) would permanently
        // skip a rejected row (it falls below the watermark and is never re-pushed). In that case the
        // watermark MUST be reworked to advance only to the highest CONFIRMED-applied id (e.g. the max id
        // present in a returned applied-id set), NOT to MAX(local id). Do not loosen this without that.
        val ledgerToPush = gamificationDao.getLedgerAbove(pushedLedgerId)
        if (ledgerToPush.isNotEmpty()) {
            remote.pushXpTransactions(ledgerToPush.map { it.toUploadDto() }).getOrThrow()
        }

        // Snapshot the clock BEFORE the pull (and stamp it on every pushed small-table row). Any row
        // written to Supabase between this instant and when the PULL resolves has updated_at >=
        // syncStartTime, so saving syncStartTime as the pull watermark re-fetches it next cycle.
        val syncStartTime = System.currentTimeMillis()

        // ── PUSH: small tables (full state; server merge is GREATEST/earliest/union) ──
        val localAchievements = gamificationDao.getAllAchievements()
        if (localAchievements.isNotEmpty()) {
            remote.mergeAchievements(localAchievements.map { it.toDto(syncStartTime) }).getOrThrow()
        }
        val localEntitlements = gamificationDao.getAllEntitlements()
        if (localEntitlements.isNotEmpty()) {
            remote.mergeEntitlements(localEntitlements.map { it.toDto(syncStartTime) }).getOrThrow()
        }
        val localStreaks = gamificationDao.getAllStreaks()
        if (localStreaks.isNotEmpty()) {
            remote.mergeStreaks(localStreaks.map { it.toDto(syncStartTime) }).getOrThrow()
        }

        // ── PULL: XP ledger → recompute progression ──────────────────────────
        val remoteLedger = remote.getXpChangesSince(lastSyncMs).getOrThrow()
        for (dto in remoteLedger) {
            // IGNORE on the UNIQUE idempotency_key — a row already present locally is a no-op.
            gamificationDao.insertLedgerRowIfAbsent(dto.toEntity())
        }
        // Recompute the denormalized progression from the (now merged) ledger. DIRECT SET — never a
        // grant — so no phantom ledger row is minted. The client recomputes rather than trusting the
        // remote progression row (the ledger is the monotonic source of truth, ADR-002 §11).
        val total = gamificationDao.sumAllXp()
        val level = LevelCurve.levelForTotalXp(total)
        gamificationDao.recomputeProgression(total, level, syncStartTime)

        // ── PULL: achievements (client-side monotonic merge) ─────────────────
        val remoteAchievements = remote.getAchievementChangesSince(lastSyncMs).getOrThrow()
        for (dto in remoteAchievements) {
            val localRow = gamificationDao.getAchievement(dto.achievementId)
            val merged = if (localRow == null) {
                dto.toEntity()
            } else {
                localRow.copy(
                    currentValue = maxOf(localRow.currentValue, dto.currentValue),
                    tierReached = maxOf(localRow.tierReached, dto.tierReached),
                    unlockedAt = earliestNonNull(localRow.unlockedAt, dto.unlockedAt),
                    // Prefer a local celebrated stamp so a re-pull never re-fires a celebration; if only
                    // the remote has one, adopt it (the achievement was celebrated on another device).
                    celebratedAt = earliestNonNull(localRow.celebratedAt, dto.celebratedAt),
                )
            }
            gamificationDao.upsertAchievement(merged)
        }

        // ── PULL: entitlements (insert-only union, earliest unlocked_at) ─────
        val remoteEntitlements = remote.getEntitlementChangesSince(lastSyncMs).getOrThrow()
        for (dto in remoteEntitlements) {
            // insertEntitlementIfAbsent preserves the existing local row (and thus its earliest
            // unlocked_at) when one already exists — union semantics, never overwrite.
            gamificationDao.insertEntitlementIfAbsent(dto.toEntity())
        }

        // ── PULL: streaks (GREATEST longest; latest last_active_date wins) ───
        val remoteStreaks = remote.getStreakChangesSince(lastSyncMs).getOrThrow()
        for (dto in remoteStreaks) {
            val localRow = gamificationDao.getStreak(dto.type)
            val merged = if (localRow == null) {
                dto.toEntity()
            } else if (dto.lastActiveDate > localRow.lastActiveDate) {
                // Remote is more recent — take its current/freeze_tokens/date, keep the best longest.
                localRow.copy(
                    current = dto.current,
                    freezeTokens = dto.freezeTokens,
                    lastActiveDate = dto.lastActiveDate,
                    longest = maxOf(localRow.longest, dto.longest),
                )
            } else {
                // Local is as-recent-or-newer — keep local current/date, still take the best longest.
                localRow.copy(longest = maxOf(localRow.longest, dto.longest))
            }
            gamificationDao.upsertStreak(merged)
        }

        // ── COMMIT watermarks (only after a fully successful cycle) ──────────
        // PUSH watermark: advances to MAX(id), which covers pre-existing local rows AND rows just pulled
        // (which got fresh local ids), so pulled rows are never echoed back on the next push. This is
        // SOUND ONLY because of the append-only / no-per-row-rejection contract documented at the PUSH
        // block above: a non-throwing push means every pushed row is durably applied, so no id below
        // MAX(id) can be an unapplied row. `getMaxLedgerId()` is read here, AFTER the pull/insert, so it
        // already reflects just-pulled rows — keep this ordering. If a future RPC gains partial-success
        // semantics, this MUST advance only to the highest CONFIRMED-applied id (see the PUSH note).
        syncPrefs.saveGamificationPushedLedgerId(userId, gamificationDao.getMaxLedgerId())
        // PULL watermark: subtract a safety margin so the next pull re-fetches a small overlap, protecting
        // against device-vs-server clock skew (see PULL_WATERMARK_SAFETY_MARGIN_MS). `coerceAtLeast` keeps
        // the watermark MONOTONIC — it can never regress below the previous watermark (`lastSyncMs`) even
        // if the device clock jumps backward. `syncStartTime` is strictly increasing per cycle, so under a
        // well-behaved clock `syncStartTime - margin` already increases; the coerce is the backward-jump guard.
        val newPullWatermark = (syncStartTime - PULL_WATERMARK_SAFETY_MARGIN_MS).coerceAtLeast(lastSyncMs)
        syncPrefs.saveGamificationSyncMillis(userId, newPullWatermark)
        Unit
    }.onFailure { error ->
        FirebaseCrashlytics.getInstance().apply {
            log("gamification_sync_failed: userId=$userId")
            setCustomKey("gamification_sync_error_type", error::class.simpleName ?: "Unknown")
            recordException(error)
        }
    }

    /**
     * Earliest (smallest) of two nullable epoch-millis timestamps, treating null as "absent".
     * Returns null only when both are null. Used for monotonic unlocked_at/celebrated_at merges — the
     * FIRST time something happened is the truth and must never be pushed later.
     */
    private fun earliestNonNull(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }
}
