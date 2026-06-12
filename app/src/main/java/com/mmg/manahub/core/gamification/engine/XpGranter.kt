package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.XpConfig
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import com.mmg.manahub.core.gamification.domain.model.XpLineItem
import com.mmg.manahub.core.gamification.domain.model.XpSourceCategory
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a [ProgressionEvent] to an XP grant, enforces daily/weekly caps from the ledger, computes
 * the new total/level via [LevelCurve], and persists atomically through
 * [GamificationDao.grantXpAtomically] keyed by the event's `idempotencyKey`.
 *
 * Cap enforcement queries the ledger sums for the current local window BEFORE granting, so caps
 * survive process death and are correct regardless of event ordering. A duplicate idempotency key
 * is a no-op ([ProgressionOutcome.none]).
 *
 * [clock] (and [zoneId]) are injected so tests can pin "today"/"this week".
 */
@Singleton
class XpGranter @Inject constructor(
    private val dao: GamificationDao,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {

    /**
     * Computes and persists the XP grant for [event]. Returns the outcome (xpGranted = 0 on a
     * duplicate or an event that maps to no XP).
     */
    suspend fun grant(event: ProgressionEvent): ProgressionOutcome {
        // Cheap pre-check: skip all work if this event was already granted.
        if (dao.hasTransaction(event.idempotencyKey)) return ProgressionOutcome.none

        val plan = planGrant(event) ?: return ProgressionOutcome.none
        if (plan.amount <= 0) return ProgressionOutcome.none

        val current = dao.getProgression()?.totalXp ?: 0L
        val newTotal = current + plan.amount
        val newLevel = LevelCurve.levelForTotalXp(newTotal)
        val previousLevel = LevelCurve.levelForTotalXp(current)
        val nowMillis = clock.millis()

        val applied = dao.grantXpAtomically(
            txn = XpTransactionEntity(
                idempotencyKey = event.idempotencyKey,
                amount = plan.amount,
                sourceCategory = plan.category.name,
                sourceRef = plan.sourceRef,
                createdAt = nowMillis,
            ),
            newTotalXp = newTotal,
            newLevel = newLevel,
            updatedAt = nowMillis,
        )
        if (!applied) return ProgressionOutcome.none

        return ProgressionOutcome(
            xpGranted = plan.amount,
            breakdown = plan.breakdown,
            newLevel = newLevel,
            leveledUp = newLevel > previousLevel,
        )
    }

    /** Internal grant plan: the cap-adjusted amount + category + ledger reference + UI breakdown. */
    private data class GrantPlan(
        val amount: Int,
        val category: XpSourceCategory,
        val sourceRef: String?,
        val breakdown: List<XpLineItem>,
    )

    /**
     * Resolves the (cap-adjusted) grant for [event], or null if the event maps to no XP. Cap
     * queries run here so the returned amount is already clamped to what may actually be granted.
     */
    private suspend fun planGrant(event: ProgressionEvent): GrantPlan? = when (event) {

        is ProgressionEvent.GameFinished -> {
            val lines = buildList {
                add(XpLineItem(XpSourceCategory.GAME, XpConfig.gameLogged, "Game logged"))
                if (event.isLocalWin) add(XpLineItem(XpSourceCategory.GAME, XpConfig.localWin, "Victory"))
            }
            GrantPlan(lines.sumOf { it.amount }, XpSourceCategory.GAME, event.sessionId.toString(), lines)
        }

        is ProgressionEvent.SurveyCompleted -> singleLine(
            XpSourceCategory.SURVEY, XpConfig.surveyCompleted, "Survey completed", event.surveyId.toString()
        )

        is ProgressionEvent.CardsAdded -> {
            val raw = event.addedUnique * XpConfig.newUniqueCard + event.addedCopies * XpConfig.additionalCopy
            val capped = clampToCollectionDailyCap(raw)
            if (capped <= 0) null
            else singleLine(XpSourceCategory.COLLECTION, capped, "Cards added", sourceRef = null)
        }

        is ProgressionEvent.CardScanned -> {
            val raw = event.count * XpConfig.cardScanned
            val capped = clampToCollectionDailyCap(raw)
            if (capped <= 0) null
            else singleLine(XpSourceCategory.COLLECTION, capped, "Cards scanned", event.scanBatchId)
        }

        is ProgressionEvent.DeckCreated -> {
            if (rewardedDecksToday() >= XpConfig.maxRewardedDecksPerDay) null
            else singleLine(XpSourceCategory.DECK, XpConfig.deckCreated, "Deck created", event.deckId)
        }

        // Plain deck saves grant no XP in v1 (event retained for Phase 1/2 quests).
        is ProgressionEvent.DeckSaved -> null

        is ProgressionEvent.TournamentCompleted -> {
            val lines = buildList {
                add(XpLineItem(XpSourceCategory.TOURNAMENT, XpConfig.tournamentCompleted, "Tournament completed"))
                if (event.isLocalWinner) {
                    add(XpLineItem(XpSourceCategory.TOURNAMENT, XpConfig.tournamentWon, "Tournament won"))
                }
            }
            GrantPlan(
                lines.sumOf { it.amount },
                XpSourceCategory.TOURNAMENT,
                event.tournamentId.toString(),
                lines,
            )
        }

        is ProgressionEvent.TradeCompleted -> singleLine(
            XpSourceCategory.TRADE, XpConfig.tradeCompleted, "Trade completed", event.tradeId
        )

        is ProgressionEvent.FriendAdded -> {
            if (rewardedFriendsThisWeek() >= XpConfig.maxRewardedFriendsPerWeek) null
            else singleLine(XpSourceCategory.SOCIAL, XpConfig.friendAdded, "Friend added", event.friendId)
        }

        is ProgressionEvent.AppOpenedToday -> singleLine(
            XpSourceCategory.DAILY_OPEN, XpConfig.dailyFirstOpen, "Daily check-in", event.localDate
        )

        // FeatureExplored grants no XP — it only advances exploration quests (no ledger row). Mapping
        // to null keeps the ledger clean and the `when` exhaustive (mirrors DeckSaved).
        is ProgressionEvent.FeatureExplored -> null
    }

    private fun singleLine(
        category: XpSourceCategory,
        amount: Int,
        label: String,
        sourceRef: String?,
    ): GrantPlan = GrantPlan(amount, category, sourceRef, listOf(XpLineItem(category, amount, label)))

    /**
     * Clamps [raw] so the COLLECTION-category ledger sum for today never exceeds
     * [XpConfig.collectionDailyCapXp]. Returns the grantable remainder (>= 0).
     */
    private suspend fun clampToCollectionDailyCap(raw: Int): Int {
        if (raw <= 0) return 0
        val alreadyToday = dao.sumXpForCategorySince(
            category = XpSourceCategory.COLLECTION.name,
            sinceMillis = startOfTodayMillis(),
        )
        val remaining = (XpConfig.collectionDailyCapXp - alreadyToday).coerceAtLeast(0)
        return raw.coerceAtMost(remaining)
    }

    private suspend fun rewardedDecksToday(): Int = dao.countDistinctSourceRefForCategorySince(
        category = XpSourceCategory.DECK.name,
        sinceMillis = startOfTodayMillis(),
    )

    private suspend fun rewardedFriendsThisWeek(): Int = dao.countDistinctSourceRefForCategorySince(
        category = XpSourceCategory.SOCIAL.name,
        sinceMillis = startOfThisWeekMillis(),
    )

    /** Epoch-millis at local midnight today. */
    private fun startOfTodayMillis(): Long =
        clock.instant().atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

    /** Epoch-millis at the start of the first day of the current local week. */
    private fun startOfThisWeekMillis(): Long {
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val today = clock.instant().atZone(zoneId).toLocalDate()
        val daysSinceWeekStart =
            ((today.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val weekStart = today.minus(daysSinceWeekStart.toLong(), ChronoUnit.DAYS)
        return weekStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
