package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.EntitlementEntity
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
import com.mmg.manahub.core.data.local.entity.QuestInstanceEntity
import com.mmg.manahub.core.data.local.entity.StreakEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the gamification engine.
 *
 * `abstract class` (not interface) so [grantXpAtomically] can be an `open` `@Transaction`
 * method composing the protected primitives. Per CLAUDE.md (CardDao discipline) NO entity
 * ever uses `OnConflictStrategy.REPLACE`; ledger inserts use IGNORE + rowId, and the
 * singleton progression row is upserted via INSERT-OR-IGNORE + `@Update`.
 */
@Dao
abstract class GamificationDao {

    // ── Player progression (singleton row id = 1) ───────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertProgressionIfAbsent(row: PlayerProgressionEntity): Long

    @Update
    protected abstract suspend fun updateProgression(row: PlayerProgressionEntity)

    @Query("SELECT * FROM player_progression WHERE id = :id")
    abstract suspend fun getProgression(id: Int = PlayerProgressionEntity.SINGLETON_ID): PlayerProgressionEntity?

    @Query("SELECT * FROM player_progression WHERE id = :id")
    abstract fun observeProgression(id: Int = PlayerProgressionEntity.SINGLETON_ID): Flow<PlayerProgressionEntity?>

    /**
     * Upserts the singleton progression row WITHOUT `OnConflictStrategy.REPLACE`.
     * INSERT-OR-IGNORE seeds the row on first run; if it already existed (rowId == -1) we
     * fall through to `@Update`. Never REPLACE (would DELETE + INSERT the row).
     */
    @Transaction
    protected open suspend fun upsertProgression(row: PlayerProgressionEntity) {
        val inserted = insertProgressionIfAbsent(row)
        if (inserted == -1L) updateProgression(row)
    }

    // ── XP ledger ────────────────────────────────────────────────────────────────

    /**
     * Inserts a ledger row. On a duplicate `idempotency_key` the UNIQUE index causes IGNORE,
     * and Room returns -1 (rowId) — the caller MUST treat -1 as "already granted, no-op".
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTransaction(txn: XpTransactionEntity): Long

    /**
     * Sum of XP granted for [category] since [sinceMillis] (inclusive). Used for the
     * collection daily cap. COALESCE so an empty window returns 0, not null.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM xp_transactions
        WHERE source_category = :category AND created_at >= :sinceMillis
        """
    )
    abstract suspend fun sumXpForCategorySince(category: String, sinceMillis: Long): Int

    /**
     * Count of DISTINCT non-null `source_ref` values granted for [category] since [sinceMillis].
     * Used for the per-day deck cap and per-week friend cap (count distinct decks/friends rewarded
     * in the window, regardless of how many events fired).
     */
    @Query(
        """
        SELECT COUNT(DISTINCT source_ref) FROM xp_transactions
        WHERE source_category = :category AND source_ref IS NOT NULL AND created_at >= :sinceMillis
        """
    )
    abstract suspend fun countDistinctSourceRefForCategorySince(category: String, sinceMillis: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM xp_transactions WHERE idempotency_key = :key)")
    abstract suspend fun hasTransaction(key: String): Boolean

    /**
     * Atomically appends a ledger row AND advances the singleton progression — but ONLY if the
     * ledger insert actually happened (rowId != -1). A duplicate idempotency key short-circuits
     * to a no-op, leaving progression untouched. This is the ONLY sanctioned XP-grant write path.
     *
     * @param txn the ledger row to append.
     * @param newTotalXp the progression total AFTER this grant (caller computes via LevelCurve).
     * @param newLevel the level AFTER this grant.
     * @param updatedAt epoch-millis to stamp on the progression row.
     * @return true if the grant was applied; false if it was a duplicate no-op.
     */
    @Transaction
    open suspend fun grantXpAtomically(
        txn: XpTransactionEntity,
        newTotalXp: Long,
        newLevel: Int,
        updatedAt: Long,
    ): Boolean {
        val rowId = insertTransaction(txn)
        if (rowId == -1L) return false
        upsertProgression(
            PlayerProgressionEntity(
                id = PlayerProgressionEntity.SINGLETON_ID,
                totalXp = newTotalXp,
                level = newLevel,
                updatedAt = updatedAt,
            )
        )
        return true
    }

    // ── Achievements ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAchievementIfAbsent(row: AchievementProgressEntity): Long

    @Update
    protected abstract suspend fun updateAchievement(row: AchievementProgressEntity)

    /** Upsert without REPLACE (INSERT-OR-IGNORE + Update). */
    @Transaction
    open suspend fun upsertAchievement(row: AchievementProgressEntity) {
        if (insertAchievementIfAbsent(row) == -1L) updateAchievement(row)
    }

    @Query("SELECT * FROM achievement_progress WHERE achievement_id = :id")
    abstract suspend fun getAchievement(id: String): AchievementProgressEntity?

    @Query("SELECT * FROM achievement_progress")
    abstract fun observeAchievements(): Flow<List<AchievementProgressEntity>>

    /**
     * Stable ids of every achievement that is currently unlocked (`unlocked_at IS NOT NULL`).
     *
     * One-shot snapshot used by the Phase-3
     * [com.mmg.manahub.core.gamification.engine.EntitlementGranter] to evaluate
     * [com.mmg.manahub.core.gamification.domain.catalog.UnlockRule.AchievementUnlocked] rules during
     * the retroactive `reconcileAll()` catch-up (so a player who already unlocked an achievement gets
     * its cosmetic). Per-event grants use the just-unlocked ids from the outcome instead.
     */
    @Query("SELECT achievement_id FROM achievement_progress WHERE unlocked_at IS NOT NULL")
    abstract suspend fun getUnlockedAchievementIds(): List<String>

    /**
     * Observes achievements that have been unlocked but NOT yet celebrated, oldest unlock first.
     *
     * Drives Chunk B's celebration queue (ADR-002, Phase 1): a row is pending when its real unlock
     * is stamped (`unlocked_at IS NOT NULL`) but the unlock celebration has not been shown
     * (`celebrated_at IS NULL`). Backfilled (retroactive) unlocks set `celebrated_at = unlocked_at`,
     * so they never appear here — the celebration is correctly suppressed for "you already had this".
     * Ordering by `unlocked_at` guarantees the queue plays unlocks in chronological order.
     */
    @Query(
        """
        SELECT * FROM achievement_progress
        WHERE unlocked_at IS NOT NULL AND celebrated_at IS NULL
        ORDER BY unlocked_at ASC
        """
    )
    abstract fun observePendingCelebrations(): Flow<List<AchievementProgressEntity>>

    /**
     * Marks a single achievement's unlock as celebrated by stamping `celebrated_at`.
     *
     * Idempotent at the queue level: once stamped, the row drops out of
     * [observePendingCelebrations]. Called after the celebration overlay has shown (or been skipped)
     * for that achievement. Never clears `unlocked_at` (the real first-unlock time is immutable —
     * memory `feedback_achievement_unlockedat_persistence`).
     *
     * @param id the achievement's stable catalog id.
     * @param celebratedAt epoch-millis to stamp.
     */
    @Query("UPDATE achievement_progress SET celebrated_at = :celebratedAt WHERE achievement_id = :id")
    abstract suspend fun markCelebrated(id: String, celebratedAt: Long)

    // ── Quests ───────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertQuestIfAbsent(row: QuestInstanceEntity): Long

    @Update
    protected abstract suspend fun updateQuest(row: QuestInstanceEntity)

    /** Upsert without REPLACE (INSERT-OR-IGNORE + Update). */
    @Transaction
    open suspend fun upsertQuest(row: QuestInstanceEntity) {
        if (insertQuestIfAbsent(row) == -1L) updateQuest(row)
    }

    @Query("SELECT * FROM quest_instances WHERE id = :id")
    abstract suspend fun getQuest(id: String): QuestInstanceEntity?

    @Query("SELECT * FROM quest_instances WHERE period_key = :periodKey")
    abstract fun observeQuestsForPeriod(periodKey: String): Flow<List<QuestInstanceEntity>>

    @Query("SELECT * FROM quest_instances WHERE status = :status")
    abstract fun observeQuestsByStatus(status: String): Flow<List<QuestInstanceEntity>>

    /**
     * One-shot (non-Flow) read of every instance for [periodKey]. Used by the reconciler to decide
     * whether the current period already has instances and to look up the previous period's template
     * ids for the no-repeat rule.
     */
    @Query("SELECT * FROM quest_instances WHERE period_key = :periodKey")
    abstract suspend fun getQuestsForPeriod(periodKey: String): List<QuestInstanceEntity>

    /** Count of instances generated for [periodKey] (reconciler "is this period already generated?"). */
    @Query("SELECT COUNT(*) FROM quest_instances WHERE period_key = :periodKey")
    abstract suspend fun countQuestsForPeriod(periodKey: String): Int

    /**
     * Instances whose expiry has passed and that are still ACTIVE or COMPLETED (i.e. not yet rolled
     * over). The reconciler EXPIREs the ACTIVE ones and auto-claims the COMPLETED ones so earned XP is
     * never lost. CLAIMED/EXPIRED rows are deliberately excluded (nothing left to do).
     */
    @Query("SELECT * FROM quest_instances WHERE expires_at < :nowMillis AND status IN ('ACTIVE','COMPLETED')")
    abstract suspend fun getStaleQuests(nowMillis: Long): List<QuestInstanceEntity>

    // ── Streaks ──────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertStreakIfAbsent(row: StreakEntity): Long

    @Update
    protected abstract suspend fun updateStreak(row: StreakEntity)

    /** Upsert without REPLACE (INSERT-OR-IGNORE + Update). */
    @Transaction
    open suspend fun upsertStreak(row: StreakEntity) {
        if (insertStreakIfAbsent(row) == -1L) updateStreak(row)
    }

    @Query("SELECT * FROM streaks WHERE type = :type")
    abstract suspend fun getStreak(type: String): StreakEntity?

    @Query("SELECT * FROM streaks")
    abstract fun observeStreaks(): Flow<List<StreakEntity>>

    // ── Entitlements ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertEntitlementIfAbsent(row: EntitlementEntity): Long

    @Query("SELECT * FROM entitlements WHERE unlockable_id = :id")
    abstract suspend fun getEntitlement(id: String): EntitlementEntity?

    @Query("SELECT * FROM entitlements")
    abstract fun observeEntitlements(): Flow<List<EntitlementEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM entitlements WHERE unlockable_id = :id)")
    abstract suspend fun hasEntitlement(id: String): Boolean
}
