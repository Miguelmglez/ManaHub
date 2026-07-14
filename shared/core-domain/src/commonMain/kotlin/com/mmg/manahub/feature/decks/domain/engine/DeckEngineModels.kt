package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag

// ═══════════════════════════════════════════════════════════════════════════════
//  Format / Color
// ═══════════════════════════════════════════════════════════════════════════════

enum class GameFormat(
    val displayName: String,
    val deckSize: Int,
    val sideboardSize: Int,
) {

    COMMANDER("Commander", 99, 25),
    DRAFT("Draft", 40, 25),
    /*
    PIONEER  ("Pioneer",   60, 25),
    MODERN   ("Modern",    60, 25),
    LEGACY   ("Legacy",    60, 25),
    VINTAGE  ("Vintage",   60, 25),
    PAUPER   ("Pauper",    60, 25),
    STANDARD ("Standard",  60, 25),
    */
    CASUAL("Casual", 60, 25),
}

/**
 * Maps the builder's [GameFormat] to the engine's [com.mmg.manahub.core.model.DeckFormat]
 * (Phase 4, D1). 1:1 wherever a matching engine format/legality exists, so Modern/Pioneer/Legacy/
 * Vintage/Pauper each filter by their OWN legality and use their own skeleton instead of collapsing
 * to Standard. There is no Draft entry in [GameFormat]. Lives in the engine package so the engines
 * stay free of a presentation import.
 */
fun GameFormat.toEngineDeckFormat(): com.mmg.manahub.core.model.DeckFormat =
    when (this) {
        GameFormat.COMMANDER -> com.mmg.manahub.core.model.DeckFormat.COMMANDER
        /*GameFormat.STANDARD -> com.mmg.manahub.core.model.DeckFormat.STANDARD
        GameFormat.PIONEER -> com.mmg.manahub.core.model.DeckFormat.PIONEER
        GameFormat.MODERN -> com.mmg.manahub.core.model.DeckFormat.MODERN
        GameFormat.LEGACY -> com.mmg.manahub.core.model.DeckFormat.LEGACY
        GameFormat.VINTAGE -> com.mmg.manahub.core.model.DeckFormat.VINTAGE
        GameFormat.PAUPER -> com.mmg.manahub.core.model.DeckFormat.PAUPER*/
        GameFormat.DRAFT -> com.mmg.manahub.core.model.DeckFormat.DRAFT
        GameFormat.CASUAL -> com.mmg.manahub.core.model.DeckFormat.CASUAL
    }

enum class ManaColor(val symbol: String, val displayName: String) {
    W("W", "White"),
    U("U", "Blue"),
    B("B", "Black"),
    R("R", "Red"),
    G("G", "Green"),
    C("C", "Colorless"),
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Seed strategy — 9 archetypes, each with a tag seed list
// ═══════════════════════════════════════════════════════════════════════════════

enum class SeedStrategy(
    val displayName: String,
    val description: String,
    val icon: String,
    val primaryTags: List<CardTag>,
) {
    AGGRO(
        "Aggro", "Fast creatures, deal damage early", "⚡",
        listOf(CardTag.AGGRO, CardTag.BURN, CardTag.TOKENS),
    ),
    CONTROL(
        "Control", "Counterspells, wraths, grind wins", "🛡",
        listOf(CardTag.CONTROL, CardTag.COUNTERSPELL, CardTag.WRATH, CardTag.REMOVAL),
    ),
    COMBO(
        "Combo", "Infinite loops and win conditions", "∞",
        listOf(CardTag.COMBO, CardTag.INFINITE, CardTag.TUTOR),
    ),
    MIDRANGE(
        "Midrange", "Efficient threats and flexible answers", "⚔",
        listOf(CardTag.MIDRANGE, CardTag.REMOVAL, CardTag.DRAW_ENGINE),
    ),
    RAMP(
        "Ramp", "Accelerate mana, cast big spells", "🌲",
        listOf(CardTag.RAMP, CardTag.MANA_ROCK, CardTag.MANA_DORK, CardTag.WIN_CON),
    ),
    TOKENS(
        "Tokens", "Wide board of token creatures", "👥",
        listOf(CardTag.TOKENS, CardTag.AGGRO, CardTag.TRIBAL),
    ),
    GRAVEYARD(
        "Graveyard", "Sacrifice and reanimate", "💀",
        listOf(CardTag.GRAVEYARD, CardTag.SACRIFICE),
    ),
    LIFEGAIN(
        "Lifegain", "Gain life, drain opponents", "❤",
        listOf(CardTag.LIFEGAIN, CardTag.CONTROL),
    ),
    TRIBAL(
        "Tribal", "Synergistic creature tribe", "🐉",
        listOf(CardTag.TRIBAL, CardTag.AGGRO),
    ),
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Suggestion + deck entries
// ═══════════════════════════════════════════════════════════════════════════════

data class CardSuggestion(
    val card: Card,
    val score: Float,
    /**
     * Structured, localizable explanations (plan E5) — rendered via `ScoreReason.label()` in the
     * presentation layer, NOT the old `roles.map { it.name }` raw-enum-name leak.
     */
    val reasons: List<ScoreReason>,
    val isOwned: Boolean,
)

data class DeckEntry(
    val card: Card,
    val quantity: Int,
    val isOwned: Boolean,
    val isSideboard: Boolean = false,
)

enum class PathDecision { ADD, SKIP, SIDEBOARD }
