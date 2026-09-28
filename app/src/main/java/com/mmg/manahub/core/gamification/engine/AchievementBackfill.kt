package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.catalog.AchievementDef
import com.mmg.manahub.core.gamification.domain.catalog.Family
import com.mmg.manahub.core.gamification.domain.model.XpSourceCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Idempotent Family-A achievement backfill (ADR-002 §4), run by the catch-up on every OFF→ON gate
 * transition (restore plan D2).
 *
 * Evaluates every AVAILABLE Family-A (DERIVED) achievement against the user's current Room data and
 * persists the resulting progress + retroactive unlocks. To avoid spamming the celebration queue with
 * achievements the user "already had", backfilled unlocks set `celebrated_at = unlocked_at`.
 *
 * The pure computation lives in [computeBackfillRows] (takes resolved values + existing rows, returns
 * the rows to persist) so it is unit-testable without Room; [run] is the IO orchestrator.
 *
 * Family-B (COUNTER) achievements are intentionally NOT backfilled — streaks and remote-backed
 * social/tournament counts cannot be reconstructed from local Room data.
 */
class AchievementBackfill(
    private val dao: GamificationDao,
    private val statsDao: GamificationStatsDao,
    private val clock: Clock,
    private val defaultDispatcher: CoroutineDispatcher,
) {

    private val derivedResolver = DerivedAchievementResolver(statsDao)

    /**
     * Executes the backfill: resolves each Family-A def's value, loads existing progress, computes the
     * rows + tier-XP grants, and persists them atomically. Returns the number of NEW unlocks created
     * (for logging/testing). Idempotent at the row level even if the flag guard is bypassed: existing
     * `unlocked_at` values are preserved and tier XP is ledger-deduped.
     */
    suspend fun run(): Int = withContext(defaultDispatcher) {
        val now = clock.now().toEpochMilliseconds()
        val derivedDefs = AchievementCatalog.all.filter { it.family == Family.DERIVED && it.isAvailable }

        val resolved: Map<String, Int> = derivedDefs.associate { def ->
            def.id to derivedResolver.resolve(def.resolver!!)
        }
        val existing: Map<String, AchievementProgressEntity> = derivedDefs
            .mapNotNull { def -> dao.getAchievement(def.id) }
            .associateBy { it.achievementId }

        val plan = computeBackfillRows(derivedDefs, resolved, existing, now)

        persist(plan)
        plan.rows.count { it.unlockedAt != null && existing[it.achievementId]?.unlockedAt == null }
    }

    /** Persists the computed rows + grants the deduped tier XP, all inside a single transaction. */
    private suspend fun persist(plan: BackfillPlan) {
        for (row in plan.rows) dao.upsertAchievement(row)
        for (grant in plan.tierGrants) grantTierXp(grant)
    }

    /**
     * PURE computation (no Room): given each def's [resolvedValues] and the [existing] rows, build the
     * progress rows + the tier-XP grants. Backfilled unlocks set `celebrated_at = unlocked_at` so the
     * celebration queue ignores them. NEVER overwrites an existing `unlocked_at`/`celebrated_at`.
     */
    fun computeBackfillRows(
        defs: List<AchievementDef>,
        resolvedValues: Map<String, Int>,
        existing: Map<String, AchievementProgressEntity>,
        now: Long,
    ): BackfillPlan {
        val rows = mutableListOf<AchievementProgressEntity>()
        val grants = mutableListOf<TierGrant>()

        for (def in defs) {
            val value = resolvedValues[def.id] ?: 0
            val newTier = def.tierReachedFor(value)
            val prior = existing[def.id]
            val priorTier = prior?.tierReached ?: 0
            val effectiveTier = maxOf(newTier, priorTier)

            // Preserve any existing unlock/celebration stamps; only stamp NOW for a first unlock here.
            val unlockedAt = prior?.unlockedAt ?: if (effectiveTier > 0) now else null
            // Backfill suppresses celebration: celebrated_at mirrors unlocked_at for NEW unlocks.
            val celebratedAt = prior?.celebratedAt
                ?: if (prior?.unlockedAt == null && unlockedAt != null) unlockedAt else prior?.celebratedAt

            rows += AchievementProgressEntity(
                achievementId = def.id,
                currentValue = value,
                tierReached = effectiveTier,
                unlockedAt = unlockedAt,
                celebratedAt = celebratedAt,
            )

            // Grant XP for every tier newly crossed by the backfill (priorTier+1 .. newTier).
            if (newTier > priorTier) {
                for (tier in (priorTier + 1)..newTier) {
                    grants += TierGrant(def.id, tier, def.tiers[tier - 1].xpReward, now)
                }
            }
        }
        return BackfillPlan(rows, grants)
    }

    private suspend fun grantTierXp(grant: TierGrant) {
        if (grant.xpReward <= 0) return
        val key = "achievement:${grant.achievementId}:tier:${grant.tier}"
        if (dao.hasTransaction(key)) return
        // Delta-based grant: the new total/level are computed inside the transaction (race-safe).
        dao.grantXpAtomically(
            txn = XpTransactionEntity(
                idempotencyKey = key,
                amount = grant.xpReward,
                sourceCategory = XpSourceCategory.ACHIEVEMENT.name,
                sourceRef = grant.achievementId,
                createdAt = grant.now,
            ),
            amount = grant.xpReward,
            updatedAt = grant.now,
            levelForTotalXp = LevelCurve::levelForTotalXp,
        )
    }

    /** The rows to persist + the tier XP to grant, produced by [computeBackfillRows]. */
    data class BackfillPlan(
        val rows: List<AchievementProgressEntity>,
        val tierGrants: List<TierGrant>,
    )

    /** A single tier-XP grant produced by the backfill (deduped by the ledger key on persist). */
    data class TierGrant(
        val achievementId: String,
        val tier: Int,
        val xpReward: Int,
        val now: Long,
    )
}
