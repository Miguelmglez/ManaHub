package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.GamificationCatchUp
import kotlinx.coroutines.CancellationException

/**
 * The catch-up run on every enable transition (D2). Each pass is idempotent: the backfill preserves
 * existing unlocks and dedupes tier XP on ledger keys, `reconcileAll` only inserts missing
 * entitlements, the quest reconciler only settles/generates what is missing.
 *
 * Order matters: entitlements read the achievements the backfill just unlocked.
 */
class DefaultGamificationCatchUp(
    private val achievementBackfill: AchievementBackfill,
    private val entitlementGranter: EntitlementGranter,
    private val questReconciler: QuestReconciler,
    private val crashReporter: CrashReporter,
) : GamificationCatchUp {

    override suspend fun run() {
        step("gamification_backfill_failed") { achievementBackfill.run() }
        step("gamification_entitlement_reconcile_failed") { entitlementGranter.reconcileAll() }
        step("gamification_quest_reconcile_failed") { questReconciler.reconcile() }
    }

    private suspend inline fun step(logEvent: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashReporter.log(logEvent)
            crashReporter.recordException(RuntimeException("[$logEvent] ${e::class.simpleName}"))
        }
    }
}
