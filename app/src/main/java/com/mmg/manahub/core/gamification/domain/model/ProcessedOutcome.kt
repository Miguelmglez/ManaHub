package com.mmg.manahub.core.gamification.domain.model

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent

/**
 * A processed [ProgressionOutcome] paired with the event that produced it (ADR-002, Phase 1).
 *
 * Published on the engine's outcome [kotlinx.coroutines.flow.SharedFlow] so a UI screen can CORRELATE
 * an outcome with the action it is showing. In particular Chunk B's `GameResultScreen` filters this
 * stream for `sourceEvent is GameFinished && sourceEvent.sessionId == myShownSessionId` to render the
 * XP/level/achievement strip for exactly the game it is displaying.
 *
 * @param sourceEvent the event that was processed.
 * @param outcome the resulting XP/level/achievement outcome.
 */
data class ProcessedOutcome(
    val sourceEvent: ProgressionEvent,
    val outcome: ProgressionOutcome,
)
