package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.coroutines.CoroutineScope

/**
 * Processes [ProgressionEvent]s into progression changes (XP, achievements, quests, streaks).
 *
 * Features never call this directly — they emit on the [ProgressionEventBus]; the engine collects
 * the bus on an application-scope coroutine started via [start] (ADR-002 §1, §2).
 */
interface GamificationEngine {

    /**
     * Processes a single [event] and returns its [ProgressionOutcome]. Idempotent: a duplicate
     * event (same idempotency key) yields [ProgressionOutcome.none].
     */
    suspend fun process(event: ProgressionEvent): ProgressionOutcome

    /**
     * Starts collecting the event bus on [scope]. Called once from `ManaHubApp` with the app
     * scope (NEXT chunk). Safe to call once; subsequent calls are ignored.
     */
    fun start(scope: CoroutineScope)
}
