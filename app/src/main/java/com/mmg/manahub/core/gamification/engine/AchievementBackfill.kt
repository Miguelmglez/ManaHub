package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.di.DefaultDispatcher
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.catalog.AchievementDef
import com.mmg.manahub.core.gamification.domain.catalog.AchievementResolver
import com.mmg.manahub.core.gamification.domain.catalog.Family
import com.mmg.manahub.core.gamification.domain.model.XpSourceCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * One-shot Family-A achievement backfill (ADR-002 §4).
 *
 * On the FIRST launch after the v39 migration it evaluates every Family-A (DERIVED) achievement
 * against the user's current Room data and persists the resulting progress + retroactive unlocks. To
 * avoid spamming the (Chunk B) celebration queue with achievements the user "already had", backfilled
 * unlocks set `celebrated_at = unlocked_at` so the celebration host ignores them.
 *
 * Run-once is guarded by the DataStore flag `gamificationBackfillDone`. The pure computation lives in
 * [computeBackfillRows] (takes resolved values + existing rows, returns the rows to persist) so it is
 * unit-testable without Room; [run] is the IO orchestrator that reads/writes the DAO.
 *
 * Family-B (COUNTER) achievements are intentionally NOT backfilled — streaks and remote-backed
 * social/tournament counts cannot be reconstructed from local Room data.
 */
@Singleton
class AchievementBackfill @Inject constructor(
    private val dao: GamificationDao,
    private val statsDao: GamificationStatsDao,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    private companion object {
        val WUBRG = listOf("W", "U", "B", "R", "G")
        const val QUICK_WIN_MAX_TURNS = 7
        const val COMEBACK_MAX_LIFE = 5
        const val MARATHON_MIN_MS = 90L * 60_000L
        const val MULTIPLAYER_MIN_PLAYERS = 4
        const val ONE_LIFE = 1
    }

    /**
     * Executes the backfill: resolves each Family-A def's value, loads existing progress, computes the
     * rows + tier-XP grants, and persists them atomically. Returns the number of NEW unlocks created
     * (for logging/testing). Idempotent at the row level even if the flag guard is bypassed: existing
     * `unlocked_at` values are preserved and tier XP is ledger-deduped.
     */
    suspend fun run(): Int = withContext(defaultDispatcher) {
        val now = clock.millis()
        val derivedDefs = AchievementCatalog.all.filter { it.family == Family.DERIVED }

        val resolved: Map<String, Int> = derivedDefs.associate { def ->
            def.id to resolveDerivedValue(def.resolver!!)
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
        val current = dao.getProgression()?.totalXp ?: 0L
        val newTotal = current + grant.xpReward
        dao.grantXpAtomically(
            txn = XpTransactionEntity(
                idempotencyKey = key,
                amount = grant.xpReward,
                sourceCategory = XpSourceCategory.ACHIEVEMENT.name,
                sourceRef = grant.achievementId,
                createdAt = grant.now,
            ),
            newTotalXp = newTotal,
            newLevel = LevelCurve.levelForTotalXp(newTotal),
            updatedAt = grant.now,
        )
    }

    private suspend fun resolveDerivedValue(resolver: AchievementResolver): Int = when (resolver) {
        AchievementResolver.CARDS_OWNED -> statsDao.totalCardsOwned()
        AchievementResolver.UNIQUE_CARDS -> statsDao.uniqueCardsOwned()
        AchievementResolver.FOIL_CARDS -> statsDao.foilCardsOwned()
        AchievementResolver.COLORS_WITH_20_PLUS -> WUBRG.count { statsDao.ownedCountForColor(it) >= 20 }
        AchievementResolver.MYTHIC_CARDS -> statsDao.mythicCardsOwned()
        AchievementResolver.MAX_CARD_VALUE_USD -> floor(statsDao.maxCardValueUsd()).toInt()
        AchievementResolver.GAMES_PLAYED -> statsDao.totalGames()
        AchievementResolver.LOCAL_WINS -> statsDao.localWins()
        AchievementResolver.QUICK_WINS -> statsDao.quickLocalWins(QUICK_WIN_MAX_TURNS)
        AchievementResolver.COMEBACK_WINS -> statsDao.comebackLocalWins(COMEBACK_MAX_LIFE)
        AchievementResolver.MARATHON_GAMES -> statsDao.marathonGames(MARATHON_MIN_MS)
        AchievementResolver.COMMANDER_WINS -> statsDao.commanderLocalWins()
        AchievementResolver.MULTIPLAYER_GAMES -> statsDao.multiplayerGames(MULTIPLAYER_MIN_PLAYERS)
        AchievementResolver.DECKS_BUILT -> statsDao.decksBuilt()
        AchievementResolver.DISTINCT_DECK_FORMATS -> statsDao.distinctDeckFormats()
        AchievementResolver.SURVEYS_COMPLETED -> statsDao.surveysCompleted()
        AchievementResolver.GAMES_ENDED_AT_ONE_LIFE -> statsDao.localWinsAtExactLife(ONE_LIFE)
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
