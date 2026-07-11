package com.mmg.manahub.core.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A user-owned deck (pure KMP model).
 *
 * The [createdAt] / [updatedAt] defaults use [Clock.System] (KMP-safe epoch millis)
 * instead of the JVM-only `System.currentTimeMillis()` so this type can live in
 * `commonMain`. The millisecond semantics are identical to the previous source.
 */
@OptIn(ExperimentalTime::class)
data class Deck(
    val id:          String,                                   // UUID, client-generated
    val userId:      String? = null,
    val name:        String,
    val description: String  = "",
    val format:      String  = "casual",
    val coverCardId: String? = null,
    val commanderCardId: String? = null,
    val isDeleted:   Boolean = false,
    val createdAt:   Long    = Clock.System.now().toEpochMilliseconds(),
    val updatedAt:   Long    = Clock.System.now().toEpochMilliseconds(),
    // ── Community Decks attribution (v41) ──────────────────────────────────
    // Set only for decks imported from an external community source (e.g. Archidekt).
    val sourceUrl:     String? = null,
    val sourceAuthor:  String? = null,
    val sourceService: String? = null,
    val importedAt:    Long?   = null,
    // ── Archetype-aware Deck Doctor (v42→v43, D2) ───────────────────────────
    // `null` archetypeOverride = the engine INFERS the deck's macro archetype every
    // analysis (see InferDeckArchetypeUseCase); a non-null value is a user pin that always
    // wins over inference. Raw enum-name strings (never `ArchetypeId`/`ThemeId` directly —
    // this module is below `:shared:core-domain`, so the layering rule forbids depending on
    // the engine's archetype enums here); callers parse via `entries.firstOrNull { ... }`,
    // NEVER `.valueOf()`, per the standing CLAUDE.md convention. themesOverride is at most 2
    // entries; empty means "no theme pin" (inference decides, or the macro pin runs theme-less).
    val archetypeOverride: String? = null,
    val themesOverride: List<String> = emptyList(),
)

data class DeckSlot(
    val scryfallId: String,
    val quantity:   Int,
)

data class DeckWithCards(
    val deck:      Deck,
    val mainboard: List<DeckSlot>,
    val sideboard: List<DeckSlot>,
) {
    val totalCards: Int get() = mainboard.sumOf { it.quantity }
}

val BASIC_LAND_NAMES = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
