package com.mmg.manahub.core.gamification.domain.usecase

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.model.ClaimResult
import com.mmg.manahub.core.gamification.domain.model.XpSourceCategory
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claims the XP reward of a COMPLETED quest instance (ADR-002, Phase 2).
 *
 * The XP grant is idempotent via the ledger key `quest_claim:{instanceId}` and goes through the ONLY
 * sanctioned write path, [GamificationDao.grantXpAtomically] (ledger insert + progression update in one
 * transaction, applied only if the ledger insert succeeded). A duplicate claim never double-grants:
 * if the ledger already has the row, the use case still ensures the instance is CLAIMED and reports
 * [ClaimResult.AlreadyClaimed].
 *
 * Auto-claim (the [QuestReconciler] on expiry) calls the same path, so earned XP is never lost when a
 * completed-but-unclaimed quest rolls over.
 */
@Singleton
class ClaimQuestRewardUseCase @Inject constructor(
    private val dao: GamificationDao,
    private val clock: Clock,
) {

    suspend operator fun invoke(instanceId: String): ClaimResult {
        val instance = dao.getQuest(instanceId) ?: return ClaimResult.NotFound

        when (instance.status) {
            STATUS_CLAIMED -> return ClaimResult.AlreadyClaimed
            STATUS_COMPLETED -> Unit // proceed
            else -> return ClaimResult.NotCompleted
        }

        val now = clock.now().toEpochMilliseconds()
        // Delta-based grant: the new total/level are computed inside the transaction (race-safe).
        val result = dao.grantXpAtomically(
            txn = XpTransactionEntity(
                idempotencyKey = "quest_claim:$instanceId",
                amount = instance.xpReward,
                sourceCategory = XpSourceCategory.QUEST.name,
                sourceRef = instanceId,
                createdAt = now,
            ),
            amount = instance.xpReward,
            updatedAt = now,
            levelForTotalXp = LevelCurve::levelForTotalXp,
        )

        return if (result.applied) {
            dao.upsertQuest(instance.copy(status = STATUS_CLAIMED))
            ClaimResult.Claimed(
                xpAwarded = instance.xpReward,
                newLevel = result.newLevel,
                leveledUp = result.newLevel > result.previousLevel,
            )
        } else {
            // Ledger already had the row (a prior claim that didn't finish flipping the status, or a
            // replay). Make the status consistent and report idempotently.
            if (instance.status != STATUS_CLAIMED) {
                dao.upsertQuest(instance.copy(status = STATUS_CLAIMED))
            }
            ClaimResult.AlreadyClaimed
        }
    }

    private companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CLAIMED = "CLAIMED"
    }
}
