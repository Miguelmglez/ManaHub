package com.mmg.manahub.feature.playtest.domain.model

import com.mmg.manahub.core.domain.model.Card

// ── Ephemeral state models (never persisted until user taps "Guardar test") ───

/**
 * Represents the configuration chosen by the user on the setup screen.
 *
 * @param deckId UUID of the deck being tested.
 * @param deckName Display name shown in the hand screen top bar.
 * @param deckFormat Format string (e.g. "commander", "standard", "draft").
 * @param drawCount Number of cards to draw for the opening hand (1–10).
 * @param isOnThePlay True = on the play; false = on the draw.
 * @param commanderCard Hydrated commander card, set only when format == "commander".
 */
data class PlaytestSetup(
    val deckId: String,
    val deckName: String,
    val deckFormat: String,
    val drawCount: Int = 7,
    val isOnThePlay: Boolean = true,
    val commanderCard: Card? = null,
)

/**
 * A snapshot of the current hand and library state after a draw or mulligan.
 *
 * This is the central ephemeral unit — the ViewModel replaces it on every
 * shuffle/draw event without touching the database.
 *
 * @param id Monotonically increasing counter used as an AnimatedContent key.
 * @param hand Cards currently in the hand (ordered; the list order is preserved
 *   for display and can be re-ordered by drag-and-drop).
 * @param library The remaining library cards in draw order (top = index 0).
 * @param mulligansUsed Number of London mulligans taken before this snapshot.
 * @param bottomedScryfallIds Ordered list of scryfallIds sent to the bottom
 *   across ALL mulligan steps (used for save-mapping counts).
 * @param startedAt Epoch-millis of the first draw in this test session.
 */
data class HandSnapshot(
    val id: Int,
    val hand: List<Card>,
    val library: List<Card>,
    val mulligansUsed: Int,
    val bottomedScryfallIds: List<String>,
    val startedAt: Long,
)

/**
 * Result of the eligibility check performed by [CanPlaytestDeckUseCase].
 */
sealed class PlaytestEligibility {
    /** The deck can be playtested. */
    object Eligible : PlaytestEligibility()

    /**
     * The deck cannot be playtested.
     *
     * @param reason Human-readable explanation surfaced in the UI
     *   (e.g. "Needs 60 cards — you have 54").
     */
    data class Ineligible(val reason: String) : PlaytestEligibility()
}

/**
 * Collected answers for all survey panels, keyed by questionId.
 *
 * Values are serialised as strings to match [PlaytestSurveyAnswerEntity.answer].
 * Multi-choice answers are serialised as comma-separated choice IDs.
 * Star rating answers are serialised as the integer rating (e.g. "4").
 * Card-impact answers are serialised as "<scryfallId>:<choiceId>" pairs joined by ";".
 */
typealias PlaytestSurveyAnswers = Map<String, String>
