package com.mmg.manahub.feature.decks.domain.engine

/**
 * Curated, hand-reviewed allowlist: EDHREC theme/tag-page `slug`
 * (`https://json.edhrec.com/pages/tags/{slug}.json`) → [ThemeId] (Deck Engine Unification plan,
 * plan §8 addendum — "unmapped themes dropped, logged for review — do not invent a mapping for a
 * slug you're unsure about").
 *
 * **Promoted from `:tools:tag-pipeline`'s `mapping/EdhrecThemeMapping.kt` to this module (plan
 * §8a addendum, 2026-07-21)** so the offline pipeline AND the app's on-device EDHREC fallback
 * (`:shared:core-data`'s `EdhrecCardTagEnrichmentSource`) share the IDENTICAL allowlist — zero
 * drift, same discipline as the [ThemeId]/[ArchetypeId] promotion itself. `:tools:tag-pipeline`
 * now consumes this table directly via its existing `implementation(project(":shared:core-domain"))`
 * dependency (no shim/wrapper needed — mirrors how it already consumes `TagDictionary` from
 * `:shared:core-data` directly).
 *
 * **Every slug below was verified against a real live EDHREC response** (2026-07-20, `curl
 * -H "User-Agent: <browser UA>" -H "Referer: https://edhrec.com/"
 * https://json.edhrec.com/pages/tags/{slug}.json` → HTTP 200) rather than assumed from [ThemeId
 * .displayName]. This mattered in practice: the URL PATTERN itself was only found by trial (an
 * initial guess at `pages/themes/{slug}.json` consistently 403'd; the real pattern is
 * `pages/tags/{slug}.json`), and at least one taxonomy member's obvious-looking slug guess was
 * wrong — [ThemeId.TRIBAL] is NOT `"tribal"` (403) but `"typal"` (200, EDHREC renamed the concept),
 * and [ThemeId.SUPERFRIENDS] is NOT `"superfriends"` (403) but `"planeswalkers"` (200).
 *
 * **[ThemeId.CLONES_THEFT] is deliberately UNMAPPED.** EDHREC has separate real pages for
 * `"clones"` and `"theft"` (both verified 200) but no single combined page — since our taxonomy
 * merges the two MTG concepts into one [ThemeId], picking just one of the two EDHREC pages would
 * misrepresent the other half of the theme. Per the "never guess" rule, this is left unmapped
 * rather than arbitrarily resolved.
 *
 * **[ThemeId.TRIBAL] is ALSO deliberately UNMAPPED** — `"typal"` returns HTTP 200 but its ONLY
 * cardlist (`tagsbypopularitysort`) holds TRIBE names ("Dragons", "Elves", ...), each linking to
 * that tribe's own sub-page — it is an INDEX/hub page over ~130 per-tribe pages, not a ranked card
 * list. This app already derives tribal identity robustly and per-tribe at RUNTIME from card
 * type-lines/oracle text via [TribeDeriver] — EDHREC tribal data would be redundant with, and
 * coarser than, what the app already computes for itself.
 *
 * See [EDHREC_SLUG_TO_ARCHETYPE_ID] for the analogous curated mapping onto [ArchetypeId].
 */
val EDHREC_SLUG_TO_THEME_ID: Map<String, ThemeId> = mapOf(
    "aristocrats" to ThemeId.ARISTOCRATS,
    "tokens" to ThemeId.TOKENS,
    "reanimator" to ThemeId.REANIMATOR,
    "self-mill" to ThemeId.SELF_MILL,
    "mill" to ThemeId.MILL,
    "spellslinger" to ThemeId.SPELLSLINGER,
    "voltron" to ThemeId.VOLTRON,
    "stax" to ThemeId.STAX,
    "landfall" to ThemeId.LANDFALL,
    "lifegain" to ThemeId.LIFEGAIN,
    "plus-1-plus-1-counters" to ThemeId.PLUS1_COUNTERS,
    "artifacts" to ThemeId.ARTIFACTS,
    "enchantress" to ThemeId.ENCHANTRESS,
    "wheels" to ThemeId.WHEELS,
    "group-hug" to ThemeId.GROUP_HUG,
    "group-slug" to ThemeId.GROUP_SLUG,
    "blink" to ThemeId.BLINK,
    "planeswalkers" to ThemeId.SUPERFRIENDS,
    "vehicles" to ThemeId.VEHICLES,
    "toolbox" to ThemeId.TOOLBOX,
    // ThemeId.CLONES_THEFT, ThemeId.TRIBAL: intentionally absent — see KDoc above.
)

/** Every EDHREC slug this pipeline/app should fetch a theme page for — the map's key set. */
val EDHREC_THEME_SLUGS: Set<String> = EDHREC_SLUG_TO_THEME_ID.keys

/**
 * Curated, hand-reviewed allowlist: EDHREC archetype/tag-page `slug` (the SAME endpoint and page
 * shape [EDHREC_SLUG_TO_THEME_ID] uses) → [ArchetypeId] (Deck Engine Unification plan §5 Phase 5a
 * / D6). Promoted alongside [EDHREC_SLUG_TO_THEME_ID] — see that table's KDoc for the promotion
 * rationale.
 *
 * **Every slug below was verified against a real live EDHREC response** (2026-07-21, HTTP 200,
 * non-empty `cardlists`) rather than assumed from [ArchetypeId.displayName]. Spot-checked content
 * sanity too, not just the HTTP status: each archetype's `highsynergycards` list contains
 * genuinely archetype-appropriate cards (e.g. `ramp` → Tatyova/The Gitrog Monster/Lotus Cobra/
 * Azusa; `control` → Mystic Remora/Ghostly Prison/Swan Song).
 *
 * [ArchetypeId.GENERIC] is deliberately UNMAPPED — the taxonomy's neutral "no specific archetype"
 * default, not a page-worthy archetype.
 *
 * **Note (plan §8a addendum):** the app's on-device per-card EDHREC fallback
 * (`EdhrecCardTagEnrichmentSource`) does NOT use this table — [ArchetypeId] is a whole-DECK
 * classification in this codebase (computed by `ArchetypeEvaluator`/`InferDeckIdentityUseCase`
 * across the full mainboard), with no corresponding single-card `CardTag` in the app's
 * `TagDictionary` to seed a per-card candidate query from. Only the offline pipeline (which
 * processes full EDHREC theme/archetype pages in bulk, not per isolated card) consumes this table.
 */
val EDHREC_SLUG_TO_ARCHETYPE_ID: Map<String, ArchetypeId> = mapOf(
    "aggro" to ArchetypeId.AGGRO,
    "midrange" to ArchetypeId.MIDRANGE,
    "control" to ArchetypeId.CONTROL,
    "tempo" to ArchetypeId.TEMPO,
    "combo" to ArchetypeId.COMBO,
    "ramp" to ArchetypeId.RAMP,
    // ArchetypeId.GENERIC: intentionally absent — see KDoc above.
)

/** Every EDHREC slug this pipeline should fetch an archetype page for — the map's key set. */
val EDHREC_ARCHETYPE_SLUGS: Set<String> = EDHREC_SLUG_TO_ARCHETYPE_ID.keys
