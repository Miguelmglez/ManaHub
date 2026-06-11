package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates achievement progress for an event (Phase 1).
 *
 * STUB for Phase 0: the engine wiring calls [process] but it is a no-op until Phase 1 fills
 * in the catalog + evaluator. Keeping the class injected now means the engine's collaborator
 * graph is complete and Phase 1 only fills the body.
 */
@Singleton
class AchievementEvaluator @Inject constructor() {

    /** No-op until Phase 1 (achievement catalog + Family-A backfill). */
    suspend fun process(event: ProgressionEvent) {
        // TODO(Phase 1): evaluate achievement rules registered for event::class and persist
        //  progress via GamificationDao.upsertAchievement.
    }
}
