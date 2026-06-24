package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProcessedOutcome
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

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
     * scope. Safe to call once; subsequent calls are ignored.
     */
    fun start(scope: CoroutineScope)

    /**
     * A hot stream of every processed outcome paired with its source event (Phase 1).
     *
     * Chunk B's `GameResultScreen` subscribes and correlates by `GameFinished.sessionId` to show the
     * progression strip for the game it is displaying. No-op outcomes ([ProgressionOutcome.none]) are
     * NOT emitted — only outcomes that carry something to surface. Replay buffer is small so a screen
     * that subscribes just after the engine processed its event still receives it.
     */
    val outcomes: SharedFlow<ProcessedOutcome>
}
