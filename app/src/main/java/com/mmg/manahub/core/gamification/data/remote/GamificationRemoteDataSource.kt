package com.mmg.manahub.core.gamification.data.remote

/**
 * Contract for all Supabase operations backing gamification sync (ADR-002 §11, ADR-008).
 *
 * Every method returns [Result] so the orchestrator can stop WITHOUT advancing a cursor. Pushes are
 * full-state / append (monotonic-safe) and at most 500 rows per call. Pulls are keyset pages capped at
 * 500 rows server-side, ordered by a server-assigned cursor. All RPCs resolve the user via
 * `auth.uid()` server-side — no user_id is sent.
 */
interface GamificationRemoteDataSource {

    /**
     * Pushes XP ledger rows via `batch_upsert_xp_transactions` (`ON CONFLICT DO NOTHING` on
     * `(user_id, idempotency_key)`; the server recomputes progression). Re-pushing a key is a no-op.
     */
    suspend fun pushXpTransactions(rows: List<XpTransactionUploadDto>): Result<Unit>

    /** Fetches ledger rows with `server_seq > afterSeq` via `get_xp_transactions_page`, ascending. */
    suspend fun getXpTransactionsPage(afterSeq: Long, limit: Int): Result<List<XpTransactionPageDto>>

    /**
     * Merges achievement progress via `merge_achievement_progress` (GREATEST current_value/tier_reached,
     * earliest-non-null unlocked_at/celebrated_at). Full-state push is safe — the merge never regresses.
     */
    suspend fun mergeAchievements(rows: List<AchievementProgressDto>): Result<Unit>

    /**
     * Fetches achievement rows after the `(changed_at, achievement_id)` cursor via
     * `get_achievement_progress_page`. Both cursor fields are null for the first page.
     */
    suspend fun getAchievementProgressPage(
        afterChangedAt: Long?,
        afterAchievementId: String?,
        limit: Int,
    ): Result<List<AchievementProgressPageDto>>

    /** Merges entitlements via `merge_entitlements` (insert-only union, earliest unlocked_at). */
    suspend fun mergeEntitlements(rows: List<EntitlementDto>): Result<Unit>

    /** Fetches entitlement rows after the `(changed_at, unlockable_id)` cursor via `get_entitlements_page`. */
    suspend fun getEntitlementsPage(
        afterChangedAt: Long?,
        afterUnlockableId: String?,
        limit: Int,
    ): Result<List<EntitlementPageDto>>

    /** Merges streaks via `merge_streaks` (GREATEST longest; latest last_active_date wins). */
    suspend fun mergeStreaks(rows: List<StreakDto>): Result<Unit>

    /** Fetches streak rows after the `(changed_at, type)` cursor via `get_streaks_page`. */
    suspend fun getStreaksPage(
        afterChangedAt: Long?,
        afterType: String?,
        limit: Int,
    ): Result<List<StreakPageDto>>
}
