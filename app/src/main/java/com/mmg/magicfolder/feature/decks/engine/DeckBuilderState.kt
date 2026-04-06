package com.mmg.magicfolder.feature.decks.engine

import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.CardTag

// ═══════════════════════════════════════════════════════════════════════════════
//  Step
// ═══════════════════════════════════════════════════════════════════════════════

enum class DeckBuilderStep { SETUP, BUILDING, REVIEW }

// ═══════════════════════════════════════════════════════════════════════════════
//  Format / Color
// ═══════════════════════════════════════════════════════════════════════════════

enum class GameFormat(
    val displayName: String,
    val deckSize: Int,
    val sideboardSize: Int,
) {
    STANDARD ("Standard",  60, 15),
    PIONEER  ("Pioneer",   60, 15),
    MODERN   ("Modern",    60, 15),
    COMMANDER("Commander", 99,  0),
    LEGACY   ("Legacy",    60, 15),
    VINTAGE  ("Vintage",   60, 15),
    PAUPER   ("Pauper",    60, 15),
    CASUAL   ("Casual",    60, 15),
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
    val card:     Card,
    val score:    Float,
    val reasons:  List<String>,
    val isOwned:  Boolean,
)

data class DeckEntry(
    val card:        Card,
    val quantity:    Int,
    val isOwned:     Boolean,
    val isSideboard: Boolean = false,
)

enum class PathDecision { ADD, SKIP, SIDEBOARD }

// ═══════════════════════════════════════════════════════════════════════════════
//  Filters + top-level UiState
// ═══════════════════════════════════════════════════════════════════════════════

data class DeckBuilderFilters(
    val maxCmc: Int = 7,
)

data class DeckBuilderUiState(
    val step: DeckBuilderStep = DeckBuilderStep.SETUP,

    // ── SETUP fields ─────────────────────────────────────────────────────────
    val deckName:       String              = "",
    val format:         GameFormat          = GameFormat.CASUAL,
    val selectedColors: Set<ManaColor>      = emptySet(),
    val seedStrategy:   SeedStrategy?       = null,
    val filters:        DeckBuilderFilters  = DeckBuilderFilters(),

    // ── BUILDING fields ───────────────────────────────────────────────────────
    val currentSuggestion: CardSuggestion?   = null,
    val suggestionQueue:   List<CardSuggestion> = emptyList(),
    val mainboard:         List<DeckEntry>   = emptyList(),
    val sideboard:         List<DeckEntry>   = emptyList(),
    val skippedCount:      Int               = 0,
    val isLoadingQueue:    Boolean           = false,

    // ── REVIEW fields ─────────────────────────────────────────────────────────
    val isSaving:    Boolean = false,
    val savedDeckId: Long?   = null,
    val error:       String? = null,
) {
    val mainboardCount: Int   get() = mainboard.sumOf { it.quantity }
    val sideboardCount: Int   get() = sideboard.sumOf { it.quantity }
    val targetSize:     Int   get() = format.deckSize
    val progressFraction: Float get() = (mainboardCount.toFloat() / targetSize).coerceIn(0f, 1f)
    val isComplete:     Boolean get() = mainboardCount >= targetSize
    val canAdvanceSetup: Boolean get() = deckName.isNotBlank() && seedStrategy != null
    val queueIsExhausted: Boolean get() = currentSuggestion == null && !isLoadingQueue
}
