package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advances active quest progress for an event (Phase 2).
 *
 * STUB for Phase 0: the engine wiring calls [process] but it is a no-op until Phase 2 adds the
 * quest catalog, deterministic generation, and rotation worker.
 */
@Singleton
class QuestEvaluator @Inject constructor() {

    /** No-op until Phase 2 (quest catalog + seeded generation). */
    suspend fun process(event: ProgressionEvent) {
        // TODO(Phase 2): advance active QuestInstanceEntity rows whose template matches event::class.
    }
}
