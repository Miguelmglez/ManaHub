package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a Draft Simulator session.
 *
 * One row per active or completed draft/sealed session. The full in-progress
 * [com.mmg.manahub.feature.draft.domain.model.DraftState] is serialised to JSON in
 * [stateJson] rather than normalised into child tables, because the state is a
 * deeply nested, fast-changing graph (packs in flight, per-seat pools, pending
 * packs) that is only ever read/written as a whole.
 *
 * [stateSchemaVersion] guards against deserialising a state shape from a previous
 * app version: when the stored value differs from the current schema version, the
 * session is discarded rather than crash-deserialised.
 *
 * @property id Stable session id derived from set code + mode (one active session per set+mode).
 * @property setCode The set being drafted (e.g. "tdm").
 * @property mode "DRAFT" or "SEALED".
 * @property status "DRAFTING", "BUILDING", or "COMPLETE".
 * @property createdAt Epoch millis when the session was first saved.
 * @property updatedAt Epoch millis of the last save.
 * @property stateSchemaVersion Version of the serialised state shape (see CURRENT_SCHEMA_VERSION).
 * @property stateJson Gson-serialised [com.mmg.manahub.feature.draft.domain.model.DraftState].
 * @property resultDeckId UUID of the deck created on completion, or null while in progress.
 */
@Entity(
    tableName = "draft_sessions",
    indices = [Index(value = ["status", "updatedAt"])],
)
data class DraftSessionEntity(
    @PrimaryKey val id: String,
    val setCode: String,
    val mode: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val stateSchemaVersion: Int,
    val stateJson: String,
    @ColumnInfo(name = "result_deck_id") val resultDeckId: String? = null,
)
