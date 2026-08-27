package com.mmg.manahub.core.model

/**
 * Groups of the Scryfall "Function" tagger vocabulary (`otag:`/`oracletag:`/`function:` — all three
 * are confirmed 1:1 aliases against the live API, identical `total_cards`), used by the "Card
 * function" Advanced Search facet (Deck Analysis — Category Sections plan, W4).
 *
 * This is a CLOSED, hand-curated list — not a full dump of Scryfall's tagger namespace. Every value
 * was validated live against `api.scryfall.com` to have a non-empty result set at authoring time; a
 * value that returned 0 results was deliberately excluded (see the plan's "do NOT add" list). Where
 * two Scryfall tags are server-side aliases of the same concept (e.g. `anthem`/`lord`), only ONE is
 * kept here to avoid a duplicate, functionally-identical chip in the picker UI.
 */
enum class CardFunctionSection(val label: String) {
    REMOVAL("Removal & Interaction"),
    CARD_ADVANTAGE("Card Advantage"),
    TUTORS("Tutors"),
    MANA("Mana"),
    LANDS("Lands"),
    GRAVEYARD("Graveyard"),
    COMBAT("Combat & Threats"),
    THEMES("Payoffs & Themes"),
}

/**
 * Represents a Scryfall "function" oracle-tag for advanced search.
 * [scryfallValue] is the literal value sent in the Scryfall API search query (`function:<value>`).
 * [section] groups related functions for the picker UI, mirroring [CardFunctionSection].
 */
data class CardFunctionOption(
    val scryfallValue: String,
    val label: String,
    val section: CardFunctionSection,
    /**
     * [CardTag] keys (as registered in `TagDictionary`) equivalent to this Scryfall function, used to
     * filter the LOCAL collection (no Scryfall call). Hand-mapped against the current tag vocabulary
     * — Scryfall's `function:` values use different word order/abbreviations than local tag keys
     * (e.g. `function:spot-removal` vs. the local `removal` role tag), so this is not a mechanical
     * transform. Empty when no local tag captures the same concept (structural land facts like
     * `fetch-land`, or a concept the local tagging engine doesn't detect at all, e.g. `cantrip`) —
     * those options are Scryfall-search-only, no local collection filter available.
     *
     * NOTE: this does NOT go through `SectionSearchQuery.collectionTagKeysFor` (Deck Analysis —
     * Category Sections plan, W3) — that function maps a pillar *section id* (e.g. `"role:removal_spot"`)
     * to tag keys, a different key space than this option's raw Scryfall function value
     * (e.g. `"spot-removal"`). Routing through it would need an extra function-value→RoleKey→sectionId
     * hop that doesn't exist for most of these ~90 values anyway (many, like `cantrip`/`loot`/`bounce`,
     * have no `RoleKey`/`ArchetypeData` counterpart at all). This field is the direct, correct mapping.
     */
    val collectionTagKeys: Set<String> = emptySet(),
) {
    companion object {
        val allFunctions = listOf(
            // ── Removal & Interaction ────────────────────────────────────────────
            CardFunctionOption("removal", "Removal", CardFunctionSection.REMOVAL, setOf("removal")),
            CardFunctionOption("spot-removal", "Spot Removal", CardFunctionSection.REMOVAL, setOf("removal")),
            CardFunctionOption("creature-removal", "Creature Removal", CardFunctionSection.REMOVAL, setOf("removal")),
            CardFunctionOption("board-wipe", "Board Wipe", CardFunctionSection.REMOVAL, setOf("board_wipe")),
            CardFunctionOption("artifact-removal", "Artifact Removal", CardFunctionSection.REMOVAL, setOf("removal")),
            CardFunctionOption("enchantment-removal", "Enchantment Removal", CardFunctionSection.REMOVAL, setOf("removal")),
            CardFunctionOption("graveyard-hate", "Graveyard Hate", CardFunctionSection.REMOVAL, setOf("graveyard_hate")),
            CardFunctionOption("counterspell", "Counterspell", CardFunctionSection.REMOVAL, setOf("counterspell")),
            CardFunctionOption("soft-counter", "Soft Counter", CardFunctionSection.REMOVAL, setOf("counterspell")),
            CardFunctionOption("edict", "Edict", CardFunctionSection.REMOVAL),
            CardFunctionOption("pacifism", "Pacifism Effect", CardFunctionSection.REMOVAL),
            CardFunctionOption("bounce", "Bounce", CardFunctionSection.REMOVAL, setOf("bounce")),
            CardFunctionOption("tax", "Tax Effect", CardFunctionSection.REMOVAL, setOf("stax_piece", "stax")),
            CardFunctionOption("hate-bear", "Hate Bear", CardFunctionSection.REMOVAL),
            CardFunctionOption("punisher", "Punisher", CardFunctionSection.REMOVAL),
            CardFunctionOption("fog", "Fog", CardFunctionSection.REMOVAL),
            CardFunctionOption("pillowfort", "Pillowfort", CardFunctionSection.REMOVAL, setOf("pillow_fort")),
            CardFunctionOption("ward", "Ward", CardFunctionSection.REMOVAL, setOf("ward")),
            CardFunctionOption("protection", "Protection", CardFunctionSection.REMOVAL, setOf("protection")),

            // ── Card Advantage ────────────────────────────────────────────────────
            CardFunctionOption("card-advantage", "Card Advantage", CardFunctionSection.CARD_ADVANTAGE, setOf("card_draw")),
            CardFunctionOption("draw", "Draw", CardFunctionSection.CARD_ADVANTAGE, setOf("card_draw")),
            CardFunctionOption("cantrip", "Cantrip", CardFunctionSection.CARD_ADVANTAGE),
            CardFunctionOption("draw-engine", "Draw Engine", CardFunctionSection.CARD_ADVANTAGE, setOf("card_draw")),
            CardFunctionOption("topdeck-manipulation", "Topdeck Manipulation", CardFunctionSection.CARD_ADVANTAGE, setOf("card_selection")),
            CardFunctionOption("scry", "Scry", CardFunctionSection.CARD_ADVANTAGE, setOf("scry")),
            CardFunctionOption("surveil", "Surveil", CardFunctionSection.CARD_ADVANTAGE, setOf("surveil")),
            CardFunctionOption("impulse-draw", "Impulse Draw", CardFunctionSection.CARD_ADVANTAGE, setOf("impulse_draw")),
            CardFunctionOption("loot", "Loot", CardFunctionSection.CARD_ADVANTAGE, setOf("loot")),
            CardFunctionOption("rummage", "Rummage", CardFunctionSection.CARD_ADVANTAGE, setOf("loot")),
            CardFunctionOption("discard-outlet", "Discard Outlet", CardFunctionSection.CARD_ADVANTAGE, setOf("discard")),
            CardFunctionOption("discard", "Discard", CardFunctionSection.CARD_ADVANTAGE, setOf("discard")),
            CardFunctionOption("wheel", "Wheel", CardFunctionSection.CARD_ADVANTAGE, setOf("wheel")),

            // ── Tutors ────────────────────────────────────────────────────────────
            CardFunctionOption("tutor", "Tutor", CardFunctionSection.TUTORS, setOf("tutor")),
            CardFunctionOption("tutor-creature", "Creature Tutor", CardFunctionSection.TUTORS, setOf("tutor")),
            CardFunctionOption("tutor-land", "Land Tutor", CardFunctionSection.TUTORS, setOf("tutor")),
            CardFunctionOption("tutor-artifact", "Artifact Tutor", CardFunctionSection.TUTORS, setOf("tutor")),

            // ── Mana ──────────────────────────────────────────────────────────────
            CardFunctionOption("ramp", "Ramp", CardFunctionSection.MANA, setOf("ramp")),
            CardFunctionOption("mana-dork", "Mana Dork", CardFunctionSection.MANA, setOf("mana_dork")),
            CardFunctionOption("mana-rock", "Mana Rock", CardFunctionSection.MANA, setOf("mana_rock")),
            CardFunctionOption("mana-producer", "Mana Producer", CardFunctionSection.MANA, setOf("mana_rock", "mana_dork")),
            CardFunctionOption("mana-sink", "Mana Sink", CardFunctionSection.MANA),
            CardFunctionOption("rampant-growth", "Rampant Growth", CardFunctionSection.MANA, setOf("ramp")),
            CardFunctionOption("fixing", "Mana Fixing", CardFunctionSection.MANA, setOf("mana_fix")),
            CardFunctionOption("cost-reducer", "Cost Reducer", CardFunctionSection.MANA, setOf("cost_reduction")),
            CardFunctionOption("ritual", "Ritual", CardFunctionSection.MANA),
            CardFunctionOption("extra-land", "Extra Land Drop", CardFunctionSection.MANA, setOf("ramp")),

            // ── Lands ─────────────────────────────────────────────────────────────
            CardFunctionOption("utility-land", "Utility Land", CardFunctionSection.LANDS),
            CardFunctionOption("dual-land", "Dual Land", CardFunctionSection.LANDS),
            CardFunctionOption("fetch-land", "Fetch Land", CardFunctionSection.LANDS),
            CardFunctionOption("shockland", "Shock Land", CardFunctionSection.LANDS),
            CardFunctionOption("creature-land", "Creature Land", CardFunctionSection.LANDS),
            CardFunctionOption("lands-matter", "Lands Matter", CardFunctionSection.LANDS, setOf("lands_matter")),

            // ── Graveyard ─────────────────────────────────────────────────────────
            CardFunctionOption("recursion", "Recursion", CardFunctionSection.GRAVEYARD, setOf("recursion")),
            CardFunctionOption("reanimate", "Reanimate", CardFunctionSection.GRAVEYARD, setOf("reanimation", "reanimator")),
            CardFunctionOption("self-mill", "Self-Mill", CardFunctionSection.GRAVEYARD, setOf("self_mill_payoff")),
            CardFunctionOption("mill", "Mill", CardFunctionSection.GRAVEYARD, setOf("mill", "mill_engine")),
            CardFunctionOption("threshold", "Threshold", CardFunctionSection.GRAVEYARD, setOf("threshold")),

            // ── Combat & Threats ──────────────────────────────────────────────────
            CardFunctionOption("evasion", "Evasion", CardFunctionSection.COMBAT, setOf("evasion")),
            CardFunctionOption("burn", "Burn", CardFunctionSection.COMBAT, setOf("burn")),
            CardFunctionOption("pinger", "Pinger", CardFunctionSection.COMBAT, setOf("pinger")),
            CardFunctionOption("combat-trick", "Combat Trick", CardFunctionSection.COMBAT),
            CardFunctionOption("untapper", "Untapper", CardFunctionSection.COMBAT, setOf("untapper")),
            CardFunctionOption("extra-turn", "Extra Turn", CardFunctionSection.COMBAT, setOf("extra_turns")),
            CardFunctionOption("extra-combat", "Extra Combat", CardFunctionSection.COMBAT, setOf("extra_combats")),
            CardFunctionOption("cheat-into-play", "Cheat Into Play", CardFunctionSection.COMBAT, setOf("sneak")),
            CardFunctionOption("threaten", "Threaten Effect", CardFunctionSection.COMBAT),
            CardFunctionOption("theft", "Theft", CardFunctionSection.COMBAT, setOf("theft", "clone_theft_effect")),
            CardFunctionOption("clone", "Clone", CardFunctionSection.COMBAT, setOf("clones")),
            CardFunctionOption("copy-spell", "Copy Spell", CardFunctionSection.COMBAT, setOf("spell_copy")),
            CardFunctionOption("copy-permanent", "Copy Permanent", CardFunctionSection.COMBAT, setOf("clones")),
            CardFunctionOption("crew", "Crew", CardFunctionSection.COMBAT, setOf("crew")),

            // ── Payoffs & Themes ──────────────────────────────────────────────────
            CardFunctionOption("landfall", "Landfall", CardFunctionSection.THEMES, setOf("landfall", "landfall_payoff")),
            CardFunctionOption("lifegain", "Lifegain", CardFunctionSection.THEMES, setOf("lifegain")),
            CardFunctionOption("lifegain-matters", "Lifegain Matters", CardFunctionSection.THEMES, setOf("lifegain_payoff")),
            CardFunctionOption("lifeloss-matters", "Lifeloss Matters", CardFunctionSection.THEMES, setOf("lifedrain")),
            CardFunctionOption("lifedrain", "Lifedrain", CardFunctionSection.THEMES, setOf("lifedrain")),
            CardFunctionOption("counters-matter", "Counters Matter", CardFunctionSection.THEMES, setOf("counters_payoff", "plus_counters")),
            CardFunctionOption("sacrifice-outlet", "Sacrifice Outlet", CardFunctionSection.THEMES, setOf("sac_outlet")),
            CardFunctionOption("sacrifice-matters", "Sacrifice Matters", CardFunctionSection.THEMES, setOf("sacrifice")),
            CardFunctionOption("death-trigger", "Death Trigger", CardFunctionSection.THEMES, setOf("death_triggers", "death_payoff")),
            CardFunctionOption("attack-trigger", "Attack Trigger", CardFunctionSection.THEMES),
            CardFunctionOption("cast-trigger", "Cast Trigger", CardFunctionSection.THEMES, setOf("spell_payoff")),
            CardFunctionOption("typal", "Typal", CardFunctionSection.THEMES, setOf("tribal", "tribe_payoff")),
            CardFunctionOption("changeling", "Changeling", CardFunctionSection.THEMES),
            CardFunctionOption("planeswalker-support", "Planeswalker Support", CardFunctionSection.THEMES),
            CardFunctionOption("enchantress", "Enchantress", CardFunctionSection.THEMES, setOf("enchantress")),
            CardFunctionOption("constellation", "Constellation", CardFunctionSection.THEMES),
            CardFunctionOption("magecraft", "Magecraft", CardFunctionSection.THEMES),
            CardFunctionOption("affinity", "Affinity", CardFunctionSection.THEMES, setOf("affinity")),
            CardFunctionOption("blink", "Blink", CardFunctionSection.THEMES, setOf("blink", "blink_effect")),
            CardFunctionOption("group-hug", "Group Hug", CardFunctionSection.THEMES, setOf("group_hug")),
            CardFunctionOption("win-condition", "Win Condition", CardFunctionSection.THEMES, setOf("win_con")),
            // NOTE: `lord` (512 results) is a server-side alias of `anthem` (512 results, identical
            // total_cards) — only `anthem` is kept per the plan's alias-collision policy.
            CardFunctionOption("anthem", "Anthem", CardFunctionSection.THEMES, setOf("anthem")),
        )

        val bySection: Map<CardFunctionSection, List<CardFunctionOption>> by lazy {
            allFunctions.groupBy { it.section }
        }
    }
}
