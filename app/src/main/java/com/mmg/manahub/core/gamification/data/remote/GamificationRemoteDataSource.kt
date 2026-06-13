package com.mmg.manahub.core.gamification.data.remote

/**
 * Contract for all Supabase operations backing Gamification Phase 4 sync (ADR-002 §11).
 *
 * Every method returns [Result] so the orchestrator can short-circuit on failure WITHOUT advancing a
 * watermark. Pushes are full-state / append (monotonic-safe) and pulls fetch rows changed since an
 * epoch-millis watermark. All RPCs resolve the user via `auth.uid()` server-side — no user_id is sent.
 */
interface GamificationRemoteDataSource {

    /**
     * Pushes XP ledger rows via `batch_upsert_xp_transactions` (`ON CONFLICT DO NOTHING` on
     * `(user_id, idempotency_key)`; the server recomputes progression). Re-pushing a key is a no-op.
     */
    suspend fun pushXpTransactions(rows: List<XpTransactionUploadDto>): Result<Unit>

    /** Fetches ledger rows changed after [since] via `get_xp_transactions_changes_since`. */
    suspend fun getXpChangesSince(since: Long): Result<List<XpTransactionChangeDto>>

    /**
     * Merges achievement progress via `merge_achievement_progress` (GREATEST current_value/tier_reached,
     * earliest-non-null unlocked_at/celebrated_at). Full-state push is safe — the merge never regresses.
     */
    suspend fun mergeAchievements(rows: List<AchievementProgressDto>): Result<Unit>

    /** Fetches achievement rows changed after [since] via `get_achievement_progress_changes_since`. */
    suspend fun getAchievementChangesSince(since: Long): Result<List<AchievementProgressDto>>

    /** Merges entitlements via `merge_entitlements` (insert-only union, earliest unlocked_at). */
    suspend fun mergeEntitlements(rows: List<EntitlementDto>): Result<Unit>

    /** Fetches entitlement rows changed after [since] via `get_entitlements_changes_since`. */
    suspend fun getEntitlementChangesSince(since: Long): Result<List<EntitlementDto>>

    /** Merges streaks via `merge_streaks` (GREATEST longest; latest last_active_date wins). */
    suspend fun mergeStreaks(rows: List<StreakDto>): Result<Unit>

    /** Fetches streak rows changed after [since] via `get_streaks_changes_since`. */
    suspend fun getStreakChangesSince(since: Long): Result<List<StreakDto>>

    /**
     * Fetches progression rows changed after [since] via `get_progression_changes_since`.
     *
     * Informational only — the client recomputes progression from the local ledger sum after the pull
     * (the ledger is the monotonic source of truth, ADR-002 §11).
     */
    suspend fun getProgressionChangesSince(since: Long): Result<List<PlayerProgressionChangeDto>>
}
