package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.PlaytestSurveyAnswers
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all playtest persistence operations.
 *
 * Only persists data when the user explicitly taps "Guardar test".
 * All in-flight redraw/mulligan loops remain purely in memory.
 */
interface PlaytestRepository {

    /**
     * Atomically saves a playtest session together with its per-card statistics.
     *
     * @param deckId UUID of the deck being tested.
     * @param deckFormat Format string of the deck.
     * @param configuredDrawCount The draw count chosen on the setup screen (e.g. 7).
     *   NOTE: this is the CONFIGURED count, not the post-mulligan hand size.
     *   finalHandSize = configuredDrawCount - mulligansUsed.
     * @param mulligansUsed Number of London mulligans taken.
     * @param librarySize Library size at game start (99 for commander, mainboard size otherwise).
     * @param onThePlay True if the user was on the play.
     * @param startedAt Epoch-millis when the first draw occurred.
     * @param cardCountsInHand Map of scryfallId → copies in the FINAL kept hand.
     * @param cardCountsBottomed Map of scryfallId → total copies sent to library bottom
     *   across all mulligan steps.
     * @return The auto-generated session id.
     */
    suspend fun saveTest(
        deckId: String,
        deckFormat: String,
        configuredDrawCount: Int,
        mulligansUsed: Int,
        librarySize: Int,
        onThePlay: Boolean,
        startedAt: Long,
        cardCountsInHand: Map<String, Int>,
        cardCountsBottomed: Map<String, Int>,
    ): Long

    /**
     * Saves optional survey answers for a previously saved test.
     *
     * Replaces any existing answers for the session (idempotent re-submission).
     *
     * @param playtestSessionId The session id returned by [saveTest].
     * @param deckId Denormalized deck UUID for per-deck survey aggregates.
     * @param answers Map of questionId → serialized answer value.
     * @param questionTypes Map of questionId → question type string.
     * @param cardReferences Map of questionId → scryfallId (for CardImpact questions; null otherwise).
     */
    suspend fun saveSurveyAnswers(
        playtestSessionId: Long,
        deckId: String,
        answers: PlaytestSurveyAnswers,
        questionTypes: Map<String, String>,
        cardReferences: Map<String, String?>,
    )

    /** Emits the total number of saved tests for a deck. */
    fun observeTestCountForDeck(deckId: String): Flow<Int>
}
