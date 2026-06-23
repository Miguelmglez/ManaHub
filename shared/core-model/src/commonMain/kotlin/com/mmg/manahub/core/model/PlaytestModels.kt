package com.mmg.manahub.core.model

// ── Ephemeral state models (never persisted until user taps "Save test") ─────

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

// ── Battlefield ("fake game") ephemeral models ──────────────────────────────
// These models are NEVER persisted. They back the in-memory PLAY phase that runs
// after the user keeps an opening hand. Like every playtest state, they live only
// in the ViewModel and disappear when the test ends (explicit-save-only applies —
// the PLAY phase performs zero DB writes).

/**
 * The phase the playtest hand screen is currently rendering.
 *
 * A single screen + single ViewModel drives both phases; the battlefield is
 * conditional content selected by this value, NOT a separate nav destination.
 *
 * @see PlaytestHandUiState.phase
 */
enum class PlaytestPhase {
    /** Mulligan decision loop (draw / redraw / mulligan / keep). */
    MULLIGAN,

    /** Simulated battlefield where cards are dragged between zones and drawn. */
    PLAY,
}

/**
 * The zones a [PlayCard] can occupy on the simulated battlefield.
 *
 * No game rules are enforced — the user may drag any card into any zone (e.g. a
 * land into [PERMANENTS]); these are organisational buckets, not legality gates.
 */
enum class PlayZone {
    /** Cards still in hand. */
    HAND,

    /** Lands played to the battlefield. */
    LANDS,

    /** Non-land permanents played to the battlefield. */
    PERMANENTS,

    /** Discarded / dead cards. */
    GRAVEYARD,
}

/**
 * A single physical instance of a card on the battlefield.
 *
 * @param instanceId Stable, unique id for this physical card instance. Assigned
 *   from a monotonic counter in the ViewModel — NOT the scryfallId. This is what
 *   every battlefield `LazyRow` keys by, so duplicate copies of the same card never
 *   collide on key (the duplicate-key crash class documented for the survey LazyRow).
 * @param card The hydrated domain card.
 * @param isTapped Whether the card is rotated 90 degrees (tapped). Display-only.
 */
data class PlayCard(
    val instanceId: Long,
    val card: Card,
    val isTapped: Boolean = false,
)

/**
 * The full ephemeral state of the simulated battlefield.
 *
 * @param hand Cards currently in hand (draggable to the field).
 * @param lands Lands on the battlefield.
 * @param permanents Non-land permanents on the battlefield.
 * @param graveyard Discarded cards.
 * @param library Remaining library in draw order (top = index 0). Held as plain
 *   [Card]s; a [PlayCard] with a fresh instanceId is minted only when a card is drawn.
 */
data class BattlefieldState(
    val hand: List<PlayCard>,
    val lands: List<PlayCard>,
    val permanents: List<PlayCard>,
    val graveyard: List<PlayCard>,
    val library: List<Card>,
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
