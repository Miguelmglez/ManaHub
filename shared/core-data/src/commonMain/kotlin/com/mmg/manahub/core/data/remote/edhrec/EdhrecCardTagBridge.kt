package com.mmg.manahub.core.data.remote.edhrec

import com.mmg.manahub.feature.decks.domain.engine.ThemeId

/**
 * Curated bridge: [com.mmg.manahub.core.model.CardTag.key] (this app's own `TagDictionary`
 * vocabulary, `:shared:core-data`'s `core/data/tagging/TagDictionary.kt`) → [ThemeId] (the EDHREC-
 * mapping taxonomy, `:shared:core-domain`'s `EdhrecSlugMapping.kt`).
 *
 * **Why this table exists (plan §8a addendum) — a real, live-verified finding, not an assumption:**
 * the addendum originally asked for a per-card EDHREC lookup (`json.edhrec.com/pages/cards/
 * {slug}.json`). That endpoint was fetched live (2026-07-21, Sol Ring + Blood Artist) and inspected
 * field-by-field: it returns "cards commonly played alongside this card" + type-distribution +
 * combos — it does NOT list which themes/archetypes the card itself belongs to. No such per-card
 * "my own themes" endpoint exists on EDHREC. So the on-device fallback instead does a SHORTLISTED
 * verification against the SAME per-THEME pages the offline pipeline already harvests
 * ([EdhrecCardTagEnrichmentSource]): take the card's own on-device-SUGGESTED tags (cheap, already
 * computed, zero extra network), map each one through this table to a candidate [ThemeId], and
 * check (via a SINGLE page fetch per candidate, capped) whether that theme's EDHREC page lists this
 * exact card. A hit promotes a suggested tag to confirmed; it is never used to invent a brand-new
 * tag the rule engine didn't already propose.
 *
 * **Why a SEPARATE bridge table is needed (not just `ThemeId.name.lowercase()`):** verified by
 * reading `TagDictionary.kt` directly — the two vocabularies do NOT line up by naive string
 * matching. E.g. [ThemeId.WHEELS]'s dictionary key is `"wheel"` (singular), [ThemeId
 * .PLUS1_COUNTERS]'s is `"plus_counters"` (no digit), [ThemeId.VEHICLES]'s is `"vehicle"`
 * (singular, `TagCategory.ROLE`), [ThemeId.SUPERFRIENDS]'s closest analogue is `"planeswalker"`
 * (a card BEING a planeswalker, not a "superfriends payoff" tag) — a blind `.name.lowercase()`
 * bridge would silently under-match roughly half these pairs. Every entry below was individually
 * confirmed to exist as a real `TagDictionary` key before being added here.
 *
 * **Themes with NO entry here are not reachable from this on-device shortlist at all** —
 * [ThemeId.ARISTOCRATS] (the single most-cited EDHREC theme) has no corresponding per-card
 * `TagDictionary` key: "aristocrats" is a deck-level EMERGENT pattern (sac outlets + death triggers
 * together), not a single-card oracle-text rule, in this taxonomy. [ThemeId.TOOLBOX] likewise has
 * no dictionary key. This is an honest, documented coverage gap, not an oversight — per the
 * project's "never guess a mapping" discipline, no entry is invented here to paper over it. Those
 * two themes DO still get captured by the offline bulk pipeline (which harvests EDHREC pages
 * wholesale, not per-isolated-card) — only this on-device single-card shortcut is narrower.
 *
 * [ThemeId.TRIBAL]/[ThemeId.CLONES_THEFT] are safe to omit from consideration here even though
 * `"tribal"`/`"clones"`/`"theft"` dictionary keys DO exist, because [EDHREC_SLUG_TO_THEME_ID] itself
 * has no slug for either (deliberately unmapped there) — a lookup miss downstream, not a bridge bug.
 *
 * Archetypes ([com.mmg.manahub.feature.decks.domain.engine.ArchetypeId]) are deliberately absent
 * from this bridge entirely — see `EdhrecSlugMapping.kt`'s KDoc for why (whole-deck concept, no
 * per-card `TagDictionary` analogue).
 */
val CARD_TAG_KEY_TO_THEME_ID: Map<String, ThemeId> = mapOf(
    "tokens" to ThemeId.TOKENS,
    "lifegain" to ThemeId.LIFEGAIN,
    // "stax" removed (Deck Analysis Engine v3, 2026-08-26): STAX moved to the macro
    // ArchetypeId.PRISON, no longer a ThemeId (see EdhrecSlugMapping.kt's own compat note).
    "reanimator" to ThemeId.REANIMATOR,
    "mill" to ThemeId.MILL_OPPONENT,
    "wheel" to ThemeId.WHEELS,
    "spellslinger" to ThemeId.SPELLSLINGER,
    "artifacts_matter" to ThemeId.ARTIFACTS,
    "enchantress" to ThemeId.ENCHANTRESS,
    // "group_hug"/"group_slug" removed: GROUP_HUG/GROUP_SLUG moved to PostureId (spec §4.1).
    "blink" to ThemeId.BLINK,
    "landfall" to ThemeId.LANDFALL,
    "plus_counters" to ThemeId.PLUS1_COUNTERS,
    "vehicle" to ThemeId.VEHICLES,
    // "voltron" removed: VOLTRON moved to PostureId (spec §4.1).
    "planeswalker" to ThemeId.SUPERFRIENDS,
    // ThemeId.ARISTOCRATS, ThemeId.TRIBAL, ThemeId.CLONES_THEFT, ThemeId.SELF_MILL,
    // ThemeId.TREASURE, ThemeId.EQUIPMENT, ThemeId.STORM: intentionally absent — see KDoc above
    // (the 3 new themes have no verified TagDictionary-key bridge yet either, same discipline).
)
