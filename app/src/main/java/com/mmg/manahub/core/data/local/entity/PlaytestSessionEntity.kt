package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per SAVED playtest session ("Guardar test").
 *
 * Persistence contract:
 *   - Rows are written ONLY when the user explicitly taps "Guardar test".
 *   - In-flight redraw/mulligan loops are purely in-memory and never touch this table.
 *   - Saving a session and its card-stat rows is always done via
 *     [PlaytestDao.saveTestAtomically] to guarantee atomicity.
 *
 * Why no hard FK on deckId:
 *   Decks are soft-deleted (is_deleted = true) rather than physically removed in
 *   normal flow, so a FK would not protect against orphan rows in practice.
 *   More importantly, a hard FK would couple playtest history to deck row lifecycle:
 *   if a deck were ever hard-deleted (e.g. data migration, guest → logged-in
 *   account merge), all its playtest history would cascade-delete or block the
 *   delete entirely.  Storing deckId as an indexed plain TEXT column lets us keep
 *   historical insights alive even when the deck entity is gone, and keeps
 *   per-deck aggregate queries trivially simple (WHERE deck_id = :deckId).
 */
@Entity(
    tableName = "playtest_sessions",
    indices = [
        Index(value = ["deck_id"], name = "index_playtest_sessions_deck_id"),
    ],
)
data class PlaytestSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** UUID of the deck being tested. Indexed; no FK (see KDoc above). */
    @ColumnInfo(name = "deck_id")
    val deckId: String,

    /** Format string matching DeckEntity.format (e.g. "standard", "commander", "casual"). */
    @ColumnInfo(name = "format")
    val format: String,

    /**
     * The configured draw count chosen on the setup screen (e.g. 7).
     * This is NOT the final hand size. finalHandSize = drawCount - mulligansUsed.
     */
    @ColumnInfo(name = "draw_count")
    val drawCount: Int,

    /** Number of times the user took a London mulligan before keeping. */
    @ColumnInfo(name = "mulligans_used")
    val mulligansUsed: Int,

    /** Total cards in library at game start (deck size, used to compute draw percentages). */
    @ColumnInfo(name = "library_size")
    val librarySize: Int,

    /** True if the user chose to be on the play; false = on the draw. */
    @ColumnInfo(name = "on_the_play")
    val onThePlay: Boolean,

    /** Epoch-millis when the test simulation was started (first draw). */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    /** Epoch-millis when the user tapped "Guardar test" and this row was committed. */
    @ColumnInfo(name = "saved_at")
    val savedAt: Long = System.currentTimeMillis(),
)
