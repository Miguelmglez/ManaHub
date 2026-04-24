package com.mmg.manahub.core.domain.model

import com.mmg.manahub.core.tagging.TagDictionary

// ═══════════════════════════════════════════════════════════════════════════════
//  Tag taxonomy
//
//  CardTag is now a data class identified by a stable canonical English [key].
//  Localization is delegated to [TagDictionary], so the same tag may render as
//  "Flying" / "Volar" / "Fliegend" depending on the user's locale, while
//  search and persistence remain key-based and unambiguous.
// ═══════════════════════════════════════════════════════════════════════════════

enum class TagCategory { ARCHETYPE, STRATEGY, ROLE, TRIBAL, KEYWORD, TYPE, CUSTOM }

data class CardTag(
    val key: String,
    val category: TagCategory,
) {
    /** Localized label for the current device locale, falls back to key. */
    val label: String
        get() = TagDictionary.localize(this) ?: key
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() }

    companion object {
        // ── Archetypes ────────────────────────────────────────────────────────
        val AGGRO     = CardTag("aggro",     TagCategory.ARCHETYPE)
        val CONTROL   = CardTag("control",   TagCategory.ARCHETYPE)
        val COMBO     = CardTag("combo",     TagCategory.ARCHETYPE)
        val MIDRANGE  = CardTag("midrange",  TagCategory.ARCHETYPE)
        val RAMP      = CardTag("ramp",      TagCategory.ARCHETYPE)
        val TEMPO     = CardTag("tempo",     TagCategory.ARCHETYPE)

        // ── Strategies ────────────────────────────────────────────────────────
        val TOKENS        = CardTag("tokens",         TagCategory.STRATEGY)
        val PLUS_COUNTERS = CardTag("plus_counters",  TagCategory.STRATEGY)
        val PROLIFERATE   = CardTag("proliferate",    TagCategory.STRATEGY)
        val GRAVEYARD     = CardTag("graveyard",      TagCategory.STRATEGY)
        val ENCHANTRESS   = CardTag("enchantress",    TagCategory.STRATEGY)
        val TRIBAL        = CardTag("tribal",         TagCategory.STRATEGY)
        val BURN          = CardTag("burn",           TagCategory.STRATEGY)
        val LIFEGAIN      = CardTag("lifegain",       TagCategory.STRATEGY)
        val SACRIFICE     = CardTag("sacrifice",      TagCategory.STRATEGY)
        val BLINK         = CardTag("blink",          TagCategory.STRATEGY)
        val INFINITE      = CardTag("infinite_combo", TagCategory.STRATEGY)
        val STAX          = CardTag("stax",           TagCategory.STRATEGY)

        // ── Roles ─────────────────────────────────────────────────────────────
        val MANA_ROCK    = CardTag("mana_rock",    TagCategory.ROLE)
        val MANA_DORK    = CardTag("mana_dork",    TagCategory.ROLE)
        val DRAW_ENGINE  = CardTag("card_draw",    TagCategory.ROLE)
        val REMOVAL      = CardTag("removal",      TagCategory.ROLE)
        val WRATH        = CardTag("board_wipe",   TagCategory.ROLE)
        val TUTOR        = CardTag("tutor",        TagCategory.ROLE)
        val PROTECTION   = CardTag("protection",   TagCategory.ROLE)
        val WIN_CON      = CardTag("win_con",      TagCategory.ROLE)
        val COUNTERSPELL  = CardTag("counterspell",  TagCategory.ROLE)
        val GAME_CHANGER  = CardTag("game_changer",  TagCategory.ROLE)

        // ── Tribal (canonical, common tribes) ─────────────────────────────────
        val GOBLIN  = CardTag("goblin",  TagCategory.TRIBAL)
        val ELF     = CardTag("elf",     TagCategory.TRIBAL)
        val DRAGON  = CardTag("dragon",  TagCategory.TRIBAL)
        val ZOMBIE  = CardTag("zombie",  TagCategory.TRIBAL)
        val WIZARD  = CardTag("wizard",  TagCategory.TRIBAL)
        val VAMPIRE = CardTag("vampire", TagCategory.TRIBAL)

        /** All "well-known" tags exposed in the manual TagPicker UI. */
        val canonical: List<CardTag> = listOf(
            AGGRO, CONTROL, COMBO, MIDRANGE, RAMP, TEMPO,
            TOKENS, PLUS_COUNTERS, PROLIFERATE, GRAVEYARD, ENCHANTRESS, TRIBAL,
            BURN, LIFEGAIN, SACRIFICE, BLINK, INFINITE, STAX,
            MANA_ROCK, MANA_DORK, DRAW_ENGINE, REMOVAL, WRATH, TUTOR,
            PROTECTION, WIN_CON, COUNTERSPELL, GAME_CHANGER,
            GOBLIN, ELF, DRAGON, ZOMBIE, WIZARD, VAMPIRE,
        )
    }
}
