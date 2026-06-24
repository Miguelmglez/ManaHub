package com.mmg.manahub.core.gamification.domain.catalog

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.AchievementCategory
import kotlin.reflect.KClass

/**
 * Which progress family an achievement belongs to (ADR-002 §4).
 *
 * - [DERIVED] (Family A): progress is a pure aggregate over existing Room data (card counts, win
 *   totals, deck counts, survey counts). The evaluator RE-QUERIES the current value on each relevant
 *   event, so new achievements unlock retroactively and a one-shot backfill can fully populate them.
 * - [COUNTER] (Family B): temporal/non-derivable progress (win streaks, daily streak, and any state
 *   not reconstructable from Room — e.g. remote-backed social/tournament counts). The evaluator reads
 *   the persisted `current_value`, applies the event's increment, and writes it back. Counters only
 *   advance going forward (no retroactive backfill).
 */
enum class Family { DERIVED, COUNTER }

/**
 * Identifies the Family-A aggregate the evaluator must query for a [Family.DERIVED] achievement.
 *
 * The evaluator maps each [AchievementResolver] to a concrete suspend snapshot read on
 * [com.mmg.manahub.core.data.local.dao.GamificationStatsDao]. Keeping the mapping as an enum (rather
 * than a lambda in the catalog) keeps the catalog a pure, serialisable data table and centralises the
 * Room coupling in one `when` inside the evaluator.
 *
 * WINS resolvers MUST resolve against the local seat (`is_local = 1`) — never name matching
 * (ADR-002 §7, memory `feedback_survey_winloss_isLocal`).
 */
enum class AchievementResolver {
    /** Total quantity of cards owned (sum of UserCardEntity.quantity). */
    CARDS_OWNED,

    /** Distinct owned printings (COUNT of UserCardEntity rows with quantity > 0). */
    UNIQUE_CARDS,

    /** Distinct owned foil printings. */
    FOIL_CARDS,

    /** Number of the five WUBRG colors that have at least 20 owned cards (0..5). */
    COLORS_WITH_20_PLUS,

    /** Count of owned mythic-rarity printings. */
    MYTHIC_CARDS,

    /** Highest single-card USD price in the collection, floored to an Int for tiering. */
    MAX_CARD_VALUE_USD,

    /** Total games logged (COUNT of game_sessions). */
    GAMES_PLAYED,

    /** Wins by the local seat (`is_local = 1 AND isWinner = 1`). */
    LOCAL_WINS,

    /** Count of games won by the local seat in <= 7 turns (fast/aggro wins). */
    QUICK_WINS,

    /** Count of local wins that ended with the local seat at <= 5 life (comeback wins). */
    COMEBACK_WINS,

    /** Count of games that lasted >= 90 minutes (marathon). */
    MARATHON_GAMES,

    /** Count of local wins in COMMANDER mode. */
    COMMANDER_WINS,

    /** Count of games with 4 or more players. */
    MULTIPLAYER_GAMES,

    /** Number of non-deleted decks. */
    DECKS_BUILT,

    /** Number of distinct deck formats across non-deleted decks. */
    DISTINCT_DECK_FORMATS,

    /** Distinct sessions that have a completed survey. */
    SURVEYS_COMPLETED,

    /**
     * SECRET: count of games whose local seat finished at EXACTLY 1 life. A non-aggregate "trophy"
     * count — 0 or more; the secret unlocks at threshold 1.
     */
    GAMES_ENDED_AT_ONE_LIFE,
}

/**
 * One unlock tier of an achievement.
 *
 * @param threshold the progress value at which this tier is reached (strictly ascending across an
 *   achievement's tiers; the evaluator unlocks tier N when `currentValue >= threshold`).
 * @param xpReward XP granted ONCE when this tier is first crossed, via the XP ledger key
 *   `achievement:{id}:tier:{n}` (idempotent — a re-evaluation never re-grants).
 */
data class AchievementTier(
    val threshold: Int,
    val xpReward: Int,
)

/**
 * Immutable definition of one achievement (ADR-002, Phase 1).
 *
 * The catalog ([AchievementCatalog]) is a code-side list of these. Progress is persisted per-id in
 * `achievement_progress`; the def supplies everything static (title, tiers, which events advance it,
 * how progress is computed).
 *
 * @param id STABLE catalog id (the 15 migrated achievements keep their old `AchievementId` enum-name
 *   strings). NEVER rename a shipped id — it is the persisted PK in `achievement_progress`.
 * @param category catalog grouping.
 * @param titleRes UI title (English string resource).
 * @param descRes UI description (English string resource).
 * @param emoji glyph shown in the UI.
 * @param tiers 1..3 ascending tiers; the last tier's threshold is the achievement's "max".
 * @param reactsTo event types that can advance this achievement. MUST be non-empty (every def is
 *   advanced by at least one event so it re-evaluates without relying solely on the backfill).
 * @param family [Family.DERIVED] or [Family.COUNTER].
 * @param resolver for DERIVED defs, the Room aggregate to query; null for COUNTER defs.
 * @param isSecret hidden/masked as "???" until unlocked (Chunk B renders the mask).
 * @param unlocks RESERVED for Phase 3 (cosmetics). Always empty in Phase 1.
 */
data class AchievementDef(
    val id: String,
    val category: AchievementCategory,
    val titleRes: Int,
    val descRes: Int,
    val emoji: String,
    val tiers: List<AchievementTier>,
    val reactsTo: Set<KClass<out ProgressionEvent>>,
    val family: Family,
    val resolver: AchievementResolver? = null,
    val isSecret: Boolean = false,
    val unlocks: List<UnlockableId> = emptyList(),
) {
    init {
        require(tiers.isNotEmpty()) { "Achievement '$id' must declare at least one tier" }
        require(tiers.zipWithNext().all { (a, b) -> a.threshold < b.threshold }) {
            "Achievement '$id' tiers must be strictly ascending by threshold"
        }
        require(reactsTo.isNotEmpty()) { "Achievement '$id' must react to at least one event" }
        require(family != Family.DERIVED || resolver != null) {
            "DERIVED achievement '$id' must declare a resolver"
        }
    }

    /** The final (highest) threshold — the value at which the achievement is fully maxed. */
    val maxThreshold: Int get() = tiers.last().threshold

    /**
     * The tier index (1-based) reached for [value]; 0 if no tier is met. Tier N corresponds to
     * `tiers[N-1]`.
     */
    fun tierReachedFor(value: Int): Int = tiers.indexOfLast { value >= it.threshold } + 1
}
