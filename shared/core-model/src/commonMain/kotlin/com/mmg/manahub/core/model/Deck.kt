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
    // ── Deck Engine Unification (v46->v47, D2/D4) ───────────────────────────
    // Raw `tribe:<subtype>` key (TribeDeriver.TRIBE_PREFIX-prefixed), or null = no tribe pin.
    // A SEPARATE pin slot from archetypeOverride/themesOverride -- never folded into
    // themesOverride's ThemeId-name-only list, so DeckDoctorOrchestrator.pinSeedTags' stale-pin
    // detection (which maps every themesOverride entry through ThemeId.entries) is never confused
    // by a tribe key. Written ONLY by the wizard (DeckWizardViewModel.writeResultIntoNewDeck) via
    // DeckRepository.updateTribeOverride -- Deck Studio's "Deck plan" editor
    // (DeckDoctorOrchestrator.setArchetypeOverride) has no tribe UI yet and never touches this
    // field, so a manual archetype/theme edit can never silently clear a wizard-set tribe pin.
    val tribeOverride: String? = null,
    /**
     * True for a deck built by the wizard (D4 hard no-cut guarantee). While true, the Deck Doctor
     * (i) never lists a `DeckCardSource.WIZARD` card in cuts and (ii) the Suggestions tab hides the
     * "Deck plan" editor -- both gates are consumed in Phase 2 (RUN 2), this field only PERSISTS the
     * flag in RUN 1. Unlock = explicit user action in Studio, flips this back to false.
     */
    val strategyLocked: Boolean = false,
)

/**
 * Per-card provenance (Deck Engine Unification plan, D4 hard no-cut guarantee). Persisted as a raw
 * TEXT column (`deck_cards.source`) -- [fromRaw] parses defensively (CLAUDE.md: never `.valueOf()`),
 * an unknown/stale string falls back to [USER] rather than crashing or guessing.
 *
 * [protectionRank] orders the three sources from LEAST to MOST cut-protected (`USER` < `SUGGESTION`
 * < `WIZARD`). Edge-case audit Fix 3 (2026-07-28): a mainboard<->sideboard board move that MERGES
 * onto an existing stack of the same card must never downgrade that stack's provenance -- see
 * [moreProtected] and its sole caller, `DeckRepositoryImpl.moveCardQuantity`.
 */
enum class DeckCardSource(private val protectionRank: Int) {
    /** Placed manually by the user in Deck Studio (the default -- also every pre-migration row). */
    USER(protectionRank = 0),
    /** Added by accepting a Deck Doctor Suggestions-tab add. */
    SUGGESTION(protectionRank = 1),
    /** Placed by the wizard's build (seeds, commander, category fill, lands). */
    WIZARD(protectionRank = 2);

    /**
     * Returns whichever of `this`/[other] carries the STRONGER cut-protection guarantee. A board
     * move that merges two differently-sourced stacks of the SAME card can only ever GAIN
     * protection, never lose it (edge-case audit Fix 3) -- e.g. merging a USER-sourced sideboard
     * copy onto a WIZARD-sourced mainboard stack must leave the merged row `WIZARD`, not silently
     * downgrade it to `USER` just because the USER side happened to initiate the move.
     */
    fun moreProtected(other: DeckCardSource): DeckCardSource =
        if (other.protectionRank > this.protectionRank) other else this

    companion object {
        fun fromRaw(raw: String?): DeckCardSource = entries.firstOrNull { it.name == raw } ?: USER
    }
}

data class DeckSlot(
    val scryfallId: String,
    val quantity:   Int,
    /** Deck Engine Unification plan, D4 -- defaults to [DeckCardSource.USER] so every existing
     * 2-arg call site (tests, legacy construction) keeps compiling unchanged. */
    val source: DeckCardSource = DeckCardSource.USER,
)

data class DeckWithCards(
    val deck:      Deck,
    val mainboard: List<DeckSlot>,
    val sideboard: List<DeckSlot>,
) {
    val totalCards: Int get() = mainboard.sumOf { it.quantity }
}

val BASIC_LAND_NAMES = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
