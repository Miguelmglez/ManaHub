package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.catalog.AchievementDef
import com.mmg.manahub.core.gamification.domain.catalog.AchievementResolver
import com.mmg.manahub.core.gamification.domain.catalog.Family
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.AchievementUnlock
import com.mmg.manahub.core.gamification.domain.model.XpSourceCategory
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Evaluates achievement progress for a [ProgressionEvent] (ADR-002, Phase 1).
 *
 * For each catalog def registered for the event's class (via [AchievementCatalog.defsByEventType] —
 * an O(defs-for-this-event) lookup, never a full scan):
 * - **Family A (DERIVED):** re-queries the current aggregate from Room ([GamificationStatsDao]) →
 *   supports retroactive unlocks. The five WUBRG colors are aggregated for the rainbow resolvers.
 * - **Family B (COUNTER):** reads the persisted `current_value`, applies the event's increment, writes
 *   it back. Streak defs read the (Phase-2 stub) streak counter, which stays 0 in Phase 1.
 *
 * Newly-crossed tiers: set `unlocked_at = now` ONLY if it is currently null (NEVER overwrite an
 * existing unlock — the old NOW-on-recompute bug), persist the new `tier_reached`, and grant each
 * newly-crossed tier's XP via the ledger key `achievement:{id}:tier:{n}` (idempotent — a duplicate
 * insert is a no-op, so a re-evaluation never re-grants). `celebrated_at` is left null so Chunk B's
 * celebration host can pick the unlock up.
 *
 * @return the list of tier unlocks produced by THIS event (for [com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome]).
 */
@Singleton
class AchievementEvaluator @Inject constructor(
    private val dao: GamificationDao,
    private val statsDao: GamificationStatsDao,
    private val clock: Clock,
) {

    /** The five MTG color tokens used by the rainbow resolvers. */
    private companion object {
        val WUBRG = listOf("W", "U", "B", "R", "G")
        const val QUICK_WIN_MAX_TURNS = 7
        const val COMEBACK_MAX_LIFE = 5
        const val MARATHON_MIN_MS = 90L * 60_000L // 90 minutes
        const val MULTIPLAYER_MIN_PLAYERS = 4
        const val ONE_LIFE = 1
    }

    /**
     * Processes [event] and returns the tier unlocks it produced. Never throws to the engine; the
     * engine wraps this in `runCatching`, but we also keep per-def isolation so one bad def can't
     * abort the rest of the batch.
     */
    suspend fun process(event: ProgressionEvent): List<AchievementUnlock> {
        val defs = AchievementCatalog.defsByEventType[event::class] ?: return emptyList()
        val unlocks = mutableListOf<AchievementUnlock>()
        for (def in defs) {
            runCatching { evaluateDef(def, event) }
                .getOrNull()
                ?.let { unlocks += it }
        }
        return unlocks
    }

    /**
     * Evaluates a single def against an event: computes the new progress value, persists it, grants
     * any newly-crossed tier XP, and returns the unlocks for tiers crossed by this evaluation.
     */
    private suspend fun evaluateDef(
        def: AchievementDef,
        event: ProgressionEvent,
    ): List<AchievementUnlock> {
        val existing = dao.getAchievement(def.id)
        val previousTier = existing?.tierReached ?: 0

        val newValue = when (def.family) {
            Family.DERIVED -> resolveDerivedValue(def.resolver!!)
            Family.COUNTER -> counterNextValue(def, event, existing?.currentValue ?: 0)
        }

        val newTier = def.tierReachedFor(newValue)
        val nowMillis = clock.now().toEpochMilliseconds()

        // unlocked_at: set ONCE on the first time any tier is reached; NEVER overwrite an existing
        // value (the regression fix). A backfill may have already stamped it.
        val unlockedAt = existing?.unlockedAt ?: if (newTier > 0) nowMillis else null

        dao.upsertAchievement(
            AchievementProgressEntity(
                achievementId = def.id,
                currentValue = newValue,
                tierReached = maxOf(newTier, previousTier),
                unlockedAt = unlockedAt,
                // Preserve any existing celebration stamp; new unlocks stay null → celebratable.
                celebratedAt = existing?.celebratedAt,
            )
        )

        if (newTier <= previousTier) return emptyList()

        // Grant XP + build unlock models for each newly-crossed tier (previousTier+1 .. newTier).
        val unlocks = mutableListOf<AchievementUnlock>()
        for (tier in (previousTier + 1)..newTier) {
            val tierDef = def.tiers[tier - 1]
            grantTierXp(def.id, tier, tierDef.xpReward)
            unlocks += AchievementUnlock(
                id = def.id,
                title = def.title,
                emoji = def.emoji,
                tier = tier,
                xpReward = tierDef.xpReward,
            )
        }
        return unlocks
    }

    /**
     * Computes the next value of a Family-B counter for [def] given [event] and the [current] value.
     *
     * - Win-streak defs track consecutive local wins: +1 on a local win, reset to 0 on a loss. The
     *   stored `current_value` is the running streak length; the threshold is the streak target. Once
     *   the tier is reached the unlock is permanent (unlocked_at is never cleared), even though the
     *   streak value itself may later reset.
     * - Tournament-win def increments only when `event.isLocalWinner` (currently always false — see
     *   the catalog comment / memory `project_gamification_phase0`).
     * - Daily-streak defs read the streak counter, a Phase-2 STUB that is always 0 in Phase 1, so they
     *   never advance yet.
     * - All other counters (friend/trade/tournament-completed) increment by +1 per event.
     */
    private fun counterNextValue(def: AchievementDef, event: ProgressionEvent, current: Int): Int =
        when {
            def.id.startsWith("WIN_STREAK_") && event is ProgressionEvent.GameFinished ->
                if (event.isLocalWin) current + 1 else 0

            def.id == "TOURNAMENT_WIN" && event is ProgressionEvent.TournamentCompleted ->
                if (event.isLocalWinner) current + 1 else current

            def.id.startsWith("STREAK_") ->
                // Phase-2 StreakTracker stub: the daily streak counter is always 0 in Phase 1.
                0

            else -> current + 1
        }

    /** Maps a [resolver] to the corresponding Room snapshot read (Family A). */
    private suspend fun resolveDerivedValue(resolver: AchievementResolver): Int = when (resolver) {
        AchievementResolver.CARDS_OWNED -> statsDao.totalCardsOwned()
        AchievementResolver.UNIQUE_CARDS -> statsDao.uniqueCardsOwned()
        AchievementResolver.FOIL_CARDS -> statsDao.foilCardsOwned()
        AchievementResolver.COLORS_WITH_20_PLUS -> colorsWith20Plus()
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

    /** Counts how many of the five WUBRG colors have >= 20 owned cards. */
    private suspend fun colorsWith20Plus(): Int =
        WUBRG.count { color -> statsDao.ownedCountForColor(color) >= 20 }

    /**
     * Grants a tier's XP through the ledger, idempotently keyed by `achievement:{id}:tier:{n}`.
     *
     * Reuses [GamificationDao.grantXpAtomically] (the only sanctioned XP write path): a duplicate key
     * is rejected by the UNIQUE index and the progression is left untouched, so re-processing the same
     * event NEVER double-grants a tier's XP.
     */
    private suspend fun grantTierXp(achievementId: String, tier: Int, xpReward: Int) {
        if (xpReward <= 0) return
        val key = "achievement:$achievementId:tier:$tier"
        if (dao.hasTransaction(key)) return

        val nowMillis = clock.now().toEpochMilliseconds()
        // Delta-based grant: the new total/level are computed inside the transaction (race-safe).
        dao.grantXpAtomically(
            txn = XpTransactionEntity(
                idempotencyKey = key,
                amount = xpReward,
                sourceCategory = XpSourceCategory.ACHIEVEMENT.name,
                sourceRef = achievementId,
                createdAt = nowMillis,
            ),
            amount = xpReward,
            updatedAt = nowMillis,
            levelForTotalXp = LevelCurve::levelForTotalXp,
        )
    }
}
