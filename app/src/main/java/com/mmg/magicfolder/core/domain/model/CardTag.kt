package com.mmg.magicfolder.core.domain.model

// ═══════════════════════════════════════════════════════════════════════════════
//  Tag taxonomy
// ═══════════════════════════════════════════════════════════════════════════════

enum class TagCategory { ARCHETYPE, STRATEGY, ROLE, TRIBAL }

enum class CardTag(val label: String, val category: TagCategory) {

    // ── Arquetipos ────────────────────────────────────────────────────────────
    AGGRO("Aggro",        TagCategory.ARCHETYPE),
    CONTROL("Control",    TagCategory.ARCHETYPE),
    COMBO("Combo",        TagCategory.ARCHETYPE),
    MIDRANGE("Midrange",  TagCategory.ARCHETYPE),
    RAMP("Ramp",          TagCategory.ARCHETYPE),
    TEMPO("Tempo",        TagCategory.ARCHETYPE),

    // ── Estrategias ───────────────────────────────────────────────────────────
    TOKENS("+Tokens",              TagCategory.STRATEGY),
    PLUS_COUNTERS("+1/+1 Counters",TagCategory.STRATEGY),
    PROLIFERATE("Proliferate",     TagCategory.STRATEGY),
    GRAVEYARD("Graveyard",         TagCategory.STRATEGY),
    ENCHANTRESS("Enchantress",     TagCategory.STRATEGY),
    TRIBAL("Tribal",               TagCategory.STRATEGY),
    BURN("Burn",                   TagCategory.STRATEGY),
    LIFEGAIN("Lifegain",           TagCategory.STRATEGY),
    SACRIFICE("Sacrifice",         TagCategory.STRATEGY),
    BLINK("Blink/ETB",             TagCategory.STRATEGY),
    INFINITE("Infinite Combo",     TagCategory.STRATEGY),
    STAX("Stax/Prison",            TagCategory.STRATEGY),

    // ── Roles ─────────────────────────────────────────────────────────────────
    MANA_ROCK("Mana Rock",      TagCategory.ROLE),
    MANA_DORK("Mana Dork",      TagCategory.ROLE),
    DRAW_ENGINE("Card Draw",    TagCategory.ROLE),
    REMOVAL("Removal",          TagCategory.ROLE),
    WRATH("Board Wipe",         TagCategory.ROLE),
    TUTOR("Tutor",              TagCategory.ROLE),
    PROTECTION("Protection",    TagCategory.ROLE),
    WIN_CON("Win Condition",    TagCategory.ROLE),
    COUNTERSPELL("Counterspell",TagCategory.ROLE),

    // ── Tribal ────────────────────────────────────────────────────────────────
    GOBLIN("Goblin",   TagCategory.TRIBAL),
    ELF("Elf",         TagCategory.TRIBAL),
    DRAGON("Dragon",   TagCategory.TRIBAL),
    ZOMBIE("Zombie",   TagCategory.TRIBAL),
    WIZARD("Wizard",   TagCategory.TRIBAL),
    VAMPIRE("Vampire", TagCategory.TRIBAL),
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Auto-tagging logic — pure function on Card domain model.
//  Called by AutoTagCardUseCase and by CardRepositoryImpl when first caching.
// ═══════════════════════════════════════════════════════════════════════════════

fun Card.computeAutoTags(): List<CardTag> {
    val tags    = mutableSetOf<CardTag>()
    val oracle  = oracleText?.lowercase() ?: ""
    val type    = typeLine.lowercase()

    // ── Mana sources ─────────────────────────────────────────────────────────
    if ("add {" in oracle || "land" in type) {
        tags += CardTag.RAMP
        when {
            "land" in type     -> { /* pure land — just RAMP */ }
            "creature" in type -> tags += CardTag.MANA_DORK
            else               -> tags += CardTag.MANA_ROCK
        }
    }

    // ── Card draw ─────────────────────────────────────────────────────────────
    if ("draw" in oracle && "card" in oracle) tags += CardTag.DRAW_ENGINE

    // ── Tokens ───────────────────────────────────────────────────────────────
    if ("create" in oracle && "token" in oracle) tags += CardTag.TOKENS

    // ── +1/+1 counters ────────────────────────────────────────────────────────
    if ("+1/+1 counter" in oracle) tags += CardTag.PLUS_COUNTERS

    // ── Proliferate ───────────────────────────────────────────────────────────
    if ("proliferate" in oracle) { tags += CardTag.PROLIFERATE; tags += CardTag.PLUS_COUNTERS }

    // ── Counterspell ─────────────────────────────────────────────────────────
    if ("counter target" in oracle) { tags += CardTag.COUNTERSPELL; tags += CardTag.CONTROL }

    // ── Removal / Board wipe ─────────────────────────────────────────────────
    val hasDestructive = ("destroy" in oracle || "exile" in oracle) && "target" in oracle
    if (hasDestructive) {
        val isWrath = "each creature" in oracle || "all creatures" in oracle
        if (isWrath) tags += CardTag.WRATH else tags += CardTag.REMOVAL
    }
    if ("each creature" in oracle && ("destroy" in oracle || "exile" in oracle))
        tags += CardTag.WRATH

    // ── Tutor ─────────────────────────────────────────────────────────────────
    if ("search your library" in oracle) tags += CardTag.TUTOR

    // ── Protection ───────────────────────────────────────────────────────────
    if ("hexproof" in oracle || "indestructible" in oracle) tags += CardTag.PROTECTION

    // ── Aggro ─────────────────────────────────────────────────────────────────
    if ("haste" in oracle && cmc <= 3.0) tags += CardTag.AGGRO

    // ── Sacrifice ─────────────────────────────────────────────────────────────
    if ("sacrifice" in oracle) tags += CardTag.SACRIFICE

    // ── Blink / ETB ──────────────────────────────────────────────────────────
    if ("exile" in oracle && "return" in oracle && "battlefield" in oracle) tags += CardTag.BLINK

    // ── Lifegain ─────────────────────────────────────────────────────────────
    if ("gain" in oracle && "life" in oracle) tags += CardTag.LIFEGAIN

    // ── Burn ─────────────────────────────────────────────────────────────────
    if ("deals" in oracle && "damage" in oracle) tags += CardTag.BURN

    // ── Graveyard ─────────────────────────────────────────────────────────────
    if ("graveyard" in oracle) tags += CardTag.GRAVEYARD

    // ── Enchantress ───────────────────────────────────────────────────────────
    if ("enchantment" in type && "draw" in oracle && "card" in oracle)
        tags += CardTag.ENCHANTRESS

    // ── Tribal ────────────────────────────────────────────────────────────────
    val tribalMap = mapOf(
        "goblin"  to CardTag.GOBLIN,
        "elf"     to CardTag.ELF,
        "dragon"  to CardTag.DRAGON,
        "zombie"  to CardTag.ZOMBIE,
        "wizard"  to CardTag.WIZARD,
        "vampire" to CardTag.VAMPIRE,
    )
    tribalMap.forEach { (subtype, tag) ->
        if (subtype in type || subtype in oracle) { tags += CardTag.TRIBAL; tags += tag }
    }

    return tags.toList()
}
