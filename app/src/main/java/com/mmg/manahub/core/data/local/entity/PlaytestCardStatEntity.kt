package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-card statistics for one saved playtest session.
 *
 * One row per distinct scryfallId that appeared in the test (either in the kept
 * opening hand or sent to the bottom during any mulligan step).
 *
 * Why counts, not booleans:
 *   Standard MTG decks allow up to 4 copies of a card (basic lands excluded).
 *   A single opening hand can contain 2 or 3 copies of the same scryfallId.
 *   Storing only a boolean "appeared: yes/no" would lose the signal that a card
 *   is consistently flooding the hand. Using INT counts lets the UI compute:
 *     - Average copies per opening hand: SUM(copies_in_opening_hand) / COUNT(tests)
 *     - Flood/screw detection: how often ≥2 copies appear
 *     - Bottomed-copies rate: SUM(copies_bottomed) / SUM(copies_in_opening_hand + copies_bottomed)
 *
 * Phase 2 columns (not yet added — reserve these names to avoid future migration pain):
 *   - copies_drawn_in_game: Int    — drawn beyond the opening hand during play
 *   - turns_until_played: Int?     — optional; turn when this card was cast
 */
@Entity(
    tableName = "playtest_card_stats",
    foreignKeys = [
        ForeignKey(
            entity = PlaytestSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["playtest_session_id"],
            // CASCADE: deleting a session cleans up all its card-stat rows automatically.
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["playtest_session_id"], name = "index_playtest_card_stats_session_id"),
        Index(value = ["scryfall_id"], name = "index_playtest_card_stats_scryfall_id"),
        // Composite: supports per-(deck) aggregate queries via a JOIN to playtest_sessions.
        // Kept separate from deck_id because this table does not directly store deckId —
        // queries join to playtest_sessions for the deck filter.
        Index(value = ["playtest_session_id", "scryfall_id"], name = "index_playtest_card_stats_session_card"),
    ],
)
data class PlaytestCardStatEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** FK → playtest_sessions.id. Cascades on delete. */
    @ColumnInfo(name = "playtest_session_id")
    val playtestSessionId: Long,

    /**
     * Scryfall card ID. Not a FK to cards table — cards may not be locally cached
     * for all deck formats, and the stat row must survive card-cache eviction.
     */
    @ColumnInfo(name = "scryfall_id")
    val scryfallId: String,

    /**
     * Number of copies of this card that ended up in the FINAL kept opening hand.
     * For a kept 7-card hand with 2 Lightning Bolts, this is 2.
     * Minimum 0 (card was only bottomed, never in the final hand).
     */
    @ColumnInfo(name = "copies_in_opening_hand")
    val copiesInOpeningHand: Int,

    /**
     * Total copies sent to the bottom of the library across ALL mulligan steps
     * in this test. London mulligan: each time the user mulligans to N cards they
     * bottom (7 - N) cards. This accumulates across multiple mulligan rounds.
     * Minimum 0 (card was never bottomed).
     */
    @ColumnInfo(name = "copies_bottomed_on_mulligan")
    val copiesBottomedOnMulligan: Int,

    // ── Phase 2 columns (reserved — add in a future migration, DO NOT add now) ──
    // val copiesDrawnInGame: Int = 0
    // val turnsUntilPlayed: Int? = null
)
