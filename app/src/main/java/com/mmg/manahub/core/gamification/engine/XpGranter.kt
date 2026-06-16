package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.XpConfig
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import com.mmg.manahub.core.gamification.domain.model.XpLineItem
import com.mmg.manahub.core.gamification.domain.model.XpSourceCategory
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
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
 *
 * Local-id-derived idempotency keys are prefixed with a stable per-device id via
 * [IdempotencyKeyScoper] before they reach the ledger, so two guest devices never collide on the
 * server's `(user_id, idempotency_key)` PK (ADR-002 §L3). The per-device id is the SAME random UUID
 * the quests feature persists ([UserPreferencesDataStore.getOrCreateGamificationDeviceId]) — never a
 * second device-id source.
 */
@Singleton
class XpGranter @Inject constructor(
    private val dao: GamificationDao,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) {

    /**
     * Computes and persists the XP grant for [event]. Returns the outcome (xpGranted = 0 on a
     * duplicate or an event that maps to no XP).
     */
    suspend fun grant(event: ProgressionEvent): ProgressionOutcome {
        // Resolve the device-scoped ledger key ONCE up front and use it everywhere below — the
        // pre-check, the row insert, and the dedup gate must all see the same (possibly prefixed) key.
        val ledgerKey = resolveLedgerKey(event)

        // Cheap pre-check: skip all work if this event was already granted.
        if (dao.hasTransaction(ledgerKey)) return ProgressionOutcome.none

        val plan = planGrant(event) ?: return ProgressionOutcome.none
        if (plan.amount <= 0) return ProgressionOutcome.none

        val nowMillis = clock.millis()

        // The new total/level are computed INSIDE grantXpAtomically from the row read in the same
        // transaction (lost-update-safe); we pass the delta, never a precomputed total.
        val result = dao.grantXpAtomically(
            txn = XpTransactionEntity(
                idempotencyKey = ledgerKey,
                amount = plan.amount,
                sourceCategory = plan.category.name,
                sourceRef = plan.sourceRef,
                createdAt = nowMillis,
            ),
            amount = plan.amount,
            updatedAt = nowMillis,
            levelForTotalXp = LevelCurve::levelForTotalXp,
        )
        if (!result.applied) return ProgressionOutcome.none

        return ProgressionOutcome(
            xpGranted = plan.amount,
            breakdown = plan.breakdown,
            newLevel = result.newLevel,
            leveledUp = result.newLevel > result.previousLevel,
        )
    }

    /**
     * Resolves the ledger idempotency key for [event], applying the per-device prefix only when the
     * event's key is local-id-derived ([ProgressionEvent.isDeviceScoped]). Globally-stable keys are
     * returned verbatim so two devices dedupe to a single grant after a sign-in merge.
     */
    private suspend fun resolveLedgerKey(event: ProgressionEvent): String =
        IdempotencyKeyScoper.scope(
            rawKey = event.idempotencyKey,
            deviceId = userPreferencesDataStore.getOrCreateGamificationDeviceId(),
            deviceScoped = event.isDeviceScoped,
        )

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

    /**
     * Epoch-millis at local midnight on the Monday of the current ISO week.
     *
     * The week always starts on Monday (ISO-8601), NOT the locale's first-day-of-week, so the weekly
     * friend cap window is deterministic and consistent with the rest of the gamification feature
     * (quests use ISO week-based-year keys). This makes the boundary independent of
     * `Locale.getDefault()` and therefore unit-testable.
     */
    private fun startOfThisWeekMillis(): Long {
        val today = clock.instant().atZone(zoneId).toLocalDate()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return weekStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
