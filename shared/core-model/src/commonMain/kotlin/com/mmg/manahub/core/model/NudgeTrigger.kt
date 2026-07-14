package com.mmg.manahub.core.model

/**
 * The reason an unauthenticated-user account nudge is being shown.
 *
 * Priority (highest → lowest) when more than one condition is met:
 * [ACTION_REQUIRED] > [COLLECTION_MILESTONE] > [DECK_MILESTONE] > [GAME_MILESTONE] > [SYNC_PENDING].
 * Priority resolution itself lives in `GetAccountNudgeUseCase`; this enum is the pure domain vocabulary.
 */
enum class NudgeTrigger {
    COLLECTION_MILESTONE, DECK_MILESTONE, GAME_MILESTONE, SYNC_PENDING, ACTION_REQUIRED
}
