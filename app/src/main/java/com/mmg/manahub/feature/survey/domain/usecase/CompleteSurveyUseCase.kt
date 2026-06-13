package com.mmg.manahub.feature.survey.domain.usecase

import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import java.time.Instant
import javax.inject.Inject

/**
 * Signals that a post-game survey has transitioned to COMPLETED and emits the corresponding
 * [ProgressionEvent.SurveyCompleted] on the [ProgressionEventBus].
 *
 * The survey feature persists its answers directly through DAOs (it has no repository layer);
 * this use case is the single sanctioned place to raise the progression event, so the emission
 * stays out of the ViewModel/Composable layer (ADR-002 §1). It must be invoked ONLY after the
 * survey's status write to COMPLETED has succeeded.
 *
 * The survey has no identity distinct from its game session, so the game [sessionId] doubles as
 * the survey id. The event's idempotency key is `survey:{surveyId}`, so re-submitting a survey
 * (or re-opening a COMPLETED one and saving again) grants XP at most once.
 */
class CompleteSurveyUseCase @Inject constructor(
    private val progressionEventBus: ProgressionEventBus,
) {
    /**
     * @param sessionId the finished game's session id, used as both the survey id and the
     *   linked session id.
     */
    suspend operator fun invoke(sessionId: Long) {
        progressionEventBus.emit(
            ProgressionEvent.SurveyCompleted(
                surveyId = sessionId,
                sessionId = sessionId,
                occurredAt = Instant.now(),
            )
        )
    }
}
