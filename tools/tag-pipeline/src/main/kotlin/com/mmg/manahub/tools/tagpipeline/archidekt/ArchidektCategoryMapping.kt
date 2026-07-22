package com.mmg.manahub.tools.tagpipeline.archidekt

/**
 * Curated, hand-reviewed allowlist: Archidekt "default category" name → `:shared:core-model`
 * `CardTag.key` (Deck Engine Unification plan, "Pipeline production-readiness" run, secondary/
 * optional Archidekt enrichment signal — same "never guess, drop unmapped" discipline as
 * [com.mmg.manahub.tools.tagpipeline.mapping.TAGGER_TAG_TO_CARD_TAG] and
 * [com.mmg.manahub.feature.decks.domain.engine.EDHREC_SLUG_TO_THEME_ID]).
 *
 * ## What Archidekt's "default category" actually is (verified live 2026-07-21, see below)
 *
 * Archidekt has **no standalone per-card category lookup endpoint** — confirmed by probing
 * `archidekt.com/api/cards/` variants live (all return a `"Client Unavailable..."` routing error,
 * not card data) and by reading the public forum threads on API usage (no per-card endpoint is ever
 * mentioned; only deck-scoped queries). The "default category" feature itself (Archidekt dev blog,
 * `archidekt.com/news/4958603`) auto-assigns one of **~30 allowlisted functional categories**
 * (Ramp/Removal/Draw/Tutor/... — chosen by Archidekt from the categories most commonly hand-applied
 * by their users) to a card WHEN IT IS ADDED TO A DECK, unless the user has disabled the feature or
 * the deck predates/overrides it with custom categories. This is fundamentally **deck-membership
 * data**, not a per-card database attribute exposed anywhere standalone.
 *
 * The only bulk-friendly access pattern is therefore exactly what the plan anticipated: **sample
 * category assignments across many public decks and take the mode** (see [ArchidektCategorySampler]).
 * `GET /api/decks/{id}/` already returns every card's `categories: List<String>` in ONE response
 * (confirmed live: `ArchidektCardEntryDto.categories`, already used in-app), so sampling N decks
 * yields up to N × ~60-100 card-category observations per request — far better than a per-card
 * search+fetch loop would (searching per-card would need its own deck-search + deck-fetch round trip
 * PER CARD, for ~30k+ cards, which is the "impractical number of requests" case the plan explicitly
 * allows treating this as best-effort/secondary for).
 *
 * **`ArchidektOracleCardDto.uid` (the oracle-card uuid embedded in every deck's card list) was
 * verified live to be IDENTICAL to Scryfall's `oracle_id`** for the same card (cross-checked
 * "Malcolm, Keen-Eyed Navigator": Archidekt deck response returned `uid=a66f8b44-0163-4456-b152-
 * 4acefab896a4`; `GET https://api.scryfall.com/cards/named?exact=...` returned the SAME
 * `oracle_id`). This is the join key [ArchidektCategorySampler] uses — never assumed, checked.
 *
 * ## Why this is SECONDARY/optional, not a primary signal
 *
 * A real 150-deck live sample (2026-07-21, `orderBy=-viewCount`) produced 14,541 card-category
 * observations across ~30 distinct real categories, but the majority of that volume is **type-echo
 * noise** (`Land`/`Creature`/`Artifact`/`Sorcery`/`Instant`/`Enchantment`/`Planeswalker` — Archidekt's
 * fixed 7 fallback TYPE buckets used when auto-categorization is off) plus per-deck custom junk
 * (`"The Cranberries"`, `"Tavs"`, `"Mmmm tasty slide"` — real category names observed in that
 * sample). Only the genuinely functional, unambiguous categories below are mapped; every entry was
 * checked against BOTH the real sample's frequency table AND the live `TagDictionary` (a mapped
 * target must be a REAL existing tag key, never invented).
 *
 * **Deliberately excluded (not oversights):**
 *  - The 7 fixed type buckets (`Land`, `Creature`, `Artifact`, `Sorcery`, `Instant`, `Enchantment`,
 *    `Planeswalker`, `Battle`, and their plurals) — redundant with the oracle type line the rule
 *    engine already reads; including them would just be noise, not strategy signal.
 *  - `Maybeboard`/`Sideboard` — Archidekt's own "not really in this deck" buckets, not a function.
 *  - `Commander` — a deck-role marker (this card IS the commander), not a per-card function.
 *  - `Legendary Creature` — a rules-text echo, not a function.
 *  - Ambiguous ones with no single confident target (`Interaction`, `Damage`, `Value`, `Control`,
 *    `Buff`, `Copy`, `Counters`, `Engines`) — each plausibly maps to 2+ real tag keys (e.g. `Copy`
 *    could mean `spell_copy` OR `clone_theft_effect`; `Counters` could mean `plus_counters` OR
 *    `minus_counters` OR poison counters) with no way to disambiguate from the bare string — dropped
 *    per the "never guess" rule rather than picking one arbitrarily.
 *  - Type-name-shaped functional categories that collide with the type-echo buckets above
 *    (`Vehicle`, `Equipment`, `Planeswalkers`) — even though matching `CardTag` keys exist
 *    (`vehicle`, `equipment`, `planeswalker`), the bare string can't be told apart from a literal
 *    type-bucket echo, so these are excluded for the same reason as the fixed type buckets.
 *  - Archetype-flavored deck-section labels (`Aggro`, `Control`, `Combo`, `Midrange`, `Tempo`) — these
 *    describe a DECK's build role for that section, not a stable per-card function; a card living in
 *    someone's "Control" category doesn't mean the card itself is control-oriented.
 */
val ARCHIDEKT_CATEGORY_TO_CARD_TAG: Map<String, String> = buildMap {
    fun put(vararg names: String, tagKey: String) {
        names.forEach { put(it.lowercase(), tagKey) }
    }
    put("removal", tagKey = "removal")
    put("sweeper", "board wipe", "boardwipe", "wrath", "wraths", tagKey = "board_wipe")
    put("ramp", "mana ramp", tagKey = "ramp")
    put("draw", "card draw", tagKey = "card_draw")
    put("tutor", "tutors", tagKey = "tutor")
    put("protection", tagKey = "protection")
    put("recursion", tagKey = "recursion")
    put("reanimator", "reanimation", tagKey = "reanimator")
    put("tokens", "token generation", tagKey = "tokens")
    put("stax", tagKey = "stax")
    put("counterspell", "counterspells", tagKey = "counterspell")
    put("discard", tagKey = "discard")
    put("mill", tagKey = "mill")
    put("lifegain", "life gain", tagKey = "lifegain")
    put("land destruction", tagKey = "land_destruction")
    put("graveyard hate", tagKey = "graveyard_hate")
    put("anthem", "anthems", tagKey = "anthem")
    put("cost reduction", tagKey = "cost_reduction")
    put("free spells", tagKey = "free_spells")
    put("bounce", tagKey = "bounce")
    put("evasion", tagKey = "evasion")
    put("wheel", "wheels", tagKey = "wheel")
    put("extra turns", tagKey = "extra_turns")
    put("extra combats", tagKey = "extra_combats")
    put("treasure", "treasures", tagKey = "treasure")
    put("spellslinger", tagKey = "spellslinger")
    put("voltron", tagKey = "voltron")
    put("sacrifice", tagKey = "sacrifice")
    put("group hug", tagKey = "group_hug")
    put("pillow fort", tagKey = "pillow_fort")
    put("devotion", tagKey = "devotion")
    put("energy", tagKey = "energy")
    put("poison", tagKey = "poison")
    put("artifacts matter", tagKey = "artifacts_matter")
    put("lands matter", tagKey = "lands_matter")
    put("death triggers", tagKey = "death_triggers")
    put("looting", "loot", tagKey = "loot")
    put("impulse draw", tagKey = "impulse_draw")
    put("monarch", tagKey = "monarch")
    put("enchantress", tagKey = "enchantress")
    put("auras", tagKey = "auras")
}

/** Normalizes an Archidekt category string ([ARCHIDEKT_CATEGORY_TO_CARD_TAG]'s keys are all
 *  lowercase, trimmed) and looks it up. `null` for anything not on the allowlist. */
fun mapArchidektCategoryToCardTag(rawCategory: String): String? =
    ARCHIDEKT_CATEGORY_TO_CARD_TAG[rawCategory.trim().lowercase()]
