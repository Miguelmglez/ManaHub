package com.mmg.manahub.core.gamification.domain.usecase

import com.mmg.manahub.core.gamification.domain.model.ClaimResult
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import kotlinx.datetime.Clock

/**
 * Claims the XP reward of a COMPLETED quest instance (ADR-002, Phase 2).
 *
 * The XP grant is idempotent via the ledger key `quest_claim:{instanceId}` routed through
 * [GamificationRepository.grantQuestClaimXp], which delegates to `GamificationDao.grantXpAtomically`
 * (ledger insert + progression update in one transaction, applied only if the ledger insert
 * succeeded). A duplicate claim never double-grants: if the ledger already has the row, the use
 * case still ensures the instance is CLAIMED and reports [ClaimResult.AlreadyClaimed].
 *
 * Auto-claim (the [com.mmg.manahub.core.gamification.engine.QuestReconciler] on expiry) calls the
 * same path, so earned XP is never lost when a completed-but-unclaimed quest rolls over.
 *
 * This use case lives in `commonMain` with no platform or DI framework imports. Android callers
 * obtain it via a Hilt `@Provides` in `GamificationModule`; Koin callers receive it via the bridge
 * in `ManaHubApp`.
 */
class ClaimQuestRewardUseCase(
    private val gamificationRepository: GamificationRepository,
    private val clock: Clock,
) {

    /**
     * Claims the XP reward for quest [instanceId].
     *
     * @return [ClaimResult.NotFound]      if no quest instance with [instanceId] exists.
     * @return [ClaimResult.AlreadyClaimed] if the instance is already CLAIMED (idempotent).
     * @return [ClaimResult.NotCompleted]  if the instance is ACTIVE or EXPIRED (not yet done).
     * @return [ClaimResult.Claimed]       on the first successful claim; includes XP awarded and new level.
     */
    suspend operator fun invoke(instanceId: String): ClaimResult {
        val questData = gamificationRepository.getQuestForClaim(instanceId)
            ?: return ClaimResult.NotFound

        when (questData.status) {
            STATUS_CLAIMED -> return ClaimResult.AlreadyClaimed
            STATUS_COMPLETED -> Unit // proceed to grant
            else -> return ClaimResult.NotCompleted
        }

        val now = clock.now().toEpochMilliseconds()
        // Delta-based grant: the new total/level are computed inside the atomic transaction (race-safe).
        val result = gamificationRepository.grantQuestClaimXp(instanceId, questData.xpReward, now)

        // Ensure the status is CLAIMED regardless of whether this was the first grant or a replay
        // (duplicate key → applied=false). markQuestClaimed is idempotent.
        gamificationRepository.markQuestClaimed(instanceId)

        return if (result.applied) {
            ClaimResult.Claimed(
                xpAwarded = questData.xpReward,
                newLevel = result.newLevel,
                leveledUp = result.newLevel > result.previousLevel,
            )
        } else {
            // Ledger already had the row from a prior claim that did not finish flipping the status.
            ClaimResult.AlreadyClaimed
        }
    }

    private companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CLAIMED   = "CLAIMED"
    }
}
