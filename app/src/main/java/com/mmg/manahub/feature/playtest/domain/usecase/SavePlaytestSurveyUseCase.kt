package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.model.PlaytestSurveyAnswers
import com.mmg.manahub.core.domain.repository.PlaytestRepository

/**
 * Saves optional survey answers for a previously saved playtest session.
 *
 * Called after [SavePlaytestUseCase] returns the session id and the user
 * completes or partially fills the survey sheet.
 */
class SavePlaytestSurveyUseCase(
    private val repository: PlaytestRepository,
) {

    /**
     * @param playtestSessionId The session id from [SavePlaytestUseCase].
     * @param deckId Denormalized deck UUID for per-deck aggregate queries.
     * @param answers The collected survey answers (questionId → serialized value).
     * @param questionTypes Map of questionId → question type discriminator.
     * @param cardReferences Map of questionId → scryfallId for CardImpact questions.
     */
    suspend operator fun invoke(
        playtestSessionId: Long,
        deckId: String,
        answers: PlaytestSurveyAnswers,
        questionTypes: Map<String, String>,
        cardReferences: Map<String, String?>,
    ) {
        if (answers.isEmpty()) return
        repository.saveSurveyAnswers(
            playtestSessionId = playtestSessionId,
            deckId            = deckId,
            answers           = answers,
            questionTypes     = questionTypes,
            cardReferences    = cardReferences,
        )
    }
}
