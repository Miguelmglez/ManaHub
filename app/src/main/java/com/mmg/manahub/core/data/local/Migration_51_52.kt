package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v51 → v52 — Deck Analysis Engine v3 (spec §2/§3/§4), Phase 3: the taxonomy migration.
 *
 * `ArchetypeId` dropped `RAMP`/`TEMPO`/`GENERIC` (moved to the new `PostureId`/removed entirely)
 * and added `PRISON`; `ThemeId` dropped `VOLTRON`/`TOOLBOX`/`GROUP_HUG`/`GROUP_SLUG` (moved to
 * `PostureId`) and `STAX` (moved to the macro `PRISON`), split `MILL` into `MILL_OPPONENT` +
 * retargeted `SELF_MILL`, and added `TREASURE`/`EQUIPMENT`/`STORM`. Every `Deck.archetypeOverride`/
 * `themesOverride` value already parses defensively via `ArchetypeId.entries.firstOrNull { it.name
 * == raw }` / `ThemeId.entries.mapNotNull { ... }` (never `.valueOf()`), so a stale persisted string
 * referencing a removed enum member degrades gracefully to "no pin" at read time — this migration
 * is not required for CORRECTNESS, only to avoid `DeckDoctorOrchestrator.pinSeedTags`' own
 * `deck_doctor_pin_seed_tags_unresolved` non-fatal firing for every previously-pinned deck the
 * first time it re-evaluates post-migration (a stale-pin string is real "enum drift" signal for
 * organic data corruption, not for a deliberate, app-wide taxonomy migration like this one).
 *
 * The feature (archetype/theme pinning) is not yet live to real users in a way that would make
 * silently clearing pins a regression worth a migration path of its own — per the task's own
 * instruction, existing rows are CLEARED rather than remapped, with no user-facing notification.
 *
 * Purely additive/destructive-of-two-nullable-columns-only: no schema shape change (both columns
 * were already nullable TEXT since v43, `MIGRATION_42_43`), no FK, no dependents — mirrors the
 * "safe to touch" precedent of a plain `UPDATE ... SET col = NULL` migration. CardDao upsert is
 * completely untouched (this migration never touches `cards`/`user_card_collection`).
 */
val MIGRATION_51_52 = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE decks SET archetype_override = NULL, themes_override = NULL")
    }
}
