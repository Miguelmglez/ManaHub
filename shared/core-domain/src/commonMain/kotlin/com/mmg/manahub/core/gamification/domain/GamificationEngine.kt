package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProcessedOutcome
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow

/**
 * Processes [ProgressionEvent]s into progression changes (XP, achievements, quests, streaks).
 *
 * Features never call this directly — they emit on the [ProgressionEventBus]; the engine collects
 * the bus while [GamificationBackendGate] keeps it started (ADR-002 §1, §2).
 */
interface GamificationEngine {

    /**
     * Processes a single [event] and returns its [ProgressionOutcome]. Idempotent: a duplicate
     * event (same idempotency key) yields [ProgressionOutcome.none].
     */
    suspend fun process(event: ProgressionEvent): ProgressionOutcome

    /**
     * Starts collecting the event bus on [scope] and returns the collector [Job]; cancelling it stops
     * the engine. The bus subscription is registered before this returns, so an event emitted right
     * after [start] is never dropped. While a previous collector is still active, returns that job.
     */
    fun start(scope: CoroutineScope): Job

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
