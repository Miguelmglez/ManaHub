package com.mmg.manahub.core.gamification.domain.model

/**
 * Minimal snapshot of a quest instance required for claim processing.
 *
 * Returned by [com.mmg.manahub.core.gamification.domain.repository.GamificationRepository.getQuestForClaim]
 * so that [com.mmg.manahub.core.gamification.domain.usecase.ClaimQuestRewardUseCase] does not need
 * to depend on the Room entity layer (which stays in `androidMain`).
 */
data class QuestClaimData(
    /** Raw status string ("ACTIVE", "COMPLETED", "CLAIMED", "EXPIRED"). */
    val status: String,
    /** XP delta to grant when the quest is claimed. */
    val xpReward: Int,
)
