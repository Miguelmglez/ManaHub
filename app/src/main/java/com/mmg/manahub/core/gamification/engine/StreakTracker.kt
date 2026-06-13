package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advances streak counters for an event (Phase 2).
 *
 * STUB for Phase 0: the engine wiring calls [process] but it is a no-op until Phase 2 adds
 * streak logic + freeze tokens (streaks never punish — ADR-002 §Context).
 */
@Singleton
class StreakTracker @Inject constructor() {

    /** No-op until Phase 2 (streak counters + freeze tokens). */
    suspend fun process(event: ProgressionEvent) {
        // TODO(Phase 2): advance StreakEntity counters on AppOpenedToday / GameFinished, honour
        //  freeze tokens, and never decrement below the floor.
    }
}
