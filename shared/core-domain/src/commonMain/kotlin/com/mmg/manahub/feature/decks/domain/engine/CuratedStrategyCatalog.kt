package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat

// ═══════════════════════════════════════════════════════════════════════════════
//  CuratedStrategyCatalog — Deck Analysis Engine v2 plan (docs/plans/deck-analysis-engine-v2-plan.md),
//  Phase 1 / §3.2 "Curated Strategy Catalog".
//
//  Root cause this fixes (plan §3.2 / D2, user-approved 2026-08-19): the free
//  archetype x <=2-theme combinatorics `ArchetypePlanSheet` currently exposes makes the picker
//  unreliable (incoherent combos are pickable, e.g. any archetype + `ThemeId.TRIBAL` with no
//  tribe). This file replaces that free combination with a curated FLAT list of ~24-28 v1
//  player-facing "strategies", each one a fixed (archetype, themes[, tribe]) composition that is
//  guaranteed to pass [StrategyCatalog.isValidCombination] (enforced by
//  `CuratedStrategyCatalogTest`) and therefore resolves cleanly through the EXISTING
//  [ArchetypeSkeletonResolver]/[ArchetypeData] machinery -- nothing about the resolver or the
//  band data is touched by this file.
//
//  Placement: sibling of [StrategyCatalog] on purpose -- [StrategyCatalog] stays the low-level
//  compatibility/copy source of truth (per-theme rationale, `isValidCombination`); this file is a
//  layer ABOVE it, curating a specific subset of the compatibility matrix into player-facing
//  named "strategies" (D2's "single flat list of curated strategies"). Both consume the SAME
//  [ArchetypeId]/[ThemeId] enums (never renamed/added to here, CLAUDE.md hard rule -- these are
//  persisted `Deck.archetypeOverride`/`themesOverride` `.name` columns).
//
//  UI IS OUT OF SCOPE for this file (`StrategyPickerSheet` is explicitly Phase 3, plan §4).
//  This is data + a display-mapper only.
//
//  Curation study (per-entry rationale + EDHREC theme-popularity cross-check + rejected combos):
//  see "Appendix A -- Curation study (Phase 1)" appended to
//  docs/plans/deck-analysis-engine-v2-plan.md.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One player-facing curated strategy: a stable [id] + display copy, resolving internally to a
 * fixed (archetype, themes[, tribe]) composition that the EXISTING [ArchetypeSkeletonResolver]/
 * [ArchetypeData] bands already know how to resolve -- no new engine data is introduced by this
 * catalog.
 *
 * @property id stable snake_case key. Persisted nowhere by this phase (Phase 3's picker will
 *           write [archetype]/[themes] into the existing `Deck.archetypeOverride`/
 *           `themesOverride` columns per plan §3.2 -- `id` itself is a UI/display convenience,
 *           not a persistence key, so it CAN be freely renamed later without a migration).
 * @property displayName player-facing name shown in the (future) picker.
 * @property description player-facing 1-3 sentence game-plan copy -- reuses [StrategyCatalog]'s
 *           existing archetype/theme copy verbatim (plan §3.2: "reuse StrategyCatalog copy where
 *           it exists") rather than inventing new prose.
 * @property archetype the internal composition's macro archetype -- resolves via
 *           [ArchetypeSkeletonResolver] exactly like a manual archetype+theme pin does today.
 * @property themes 0..2 themes (mirrors [StrategyCatalog.MAX_THEMES]) layered on [archetype].
 * @property requiresTribe `true` only for the Tribal entry -- mirrors
 *           [ThemeCatalogEntry.requiresTribe]; the (future) picker must collect a concrete
 *           creature type before this strategy resolves to a complete skeleton.
 * @property formats which [DeckFormat]s this strategy is offered for (Wave 2 Part B, plan
 *           `docs/plans/deck-analysis-engine-v2-wave2-standard-plan.md` B0/B1 -- see the KDoc on
 *           this property below for the full per-`DeckFormat` design contract). Most entries are
 *           at least Commander + Casual; a subset is Commander-only either because the underlying
 *           theme is structurally Commander-only ([ArchetypeData.THEMES]'s `commanderOnly` flag --
 *           Group Hug/Group Slug/Clones & Theft) or because this v1 catalog deliberately curates it
 *           that way even though the theme itself has a valid 60-card resolution (Voltron -- see
 *           the curation study for the rationale).
 */
data class CuratedStrategy(
    val id: String,
    val displayName: String,
    val description: String,
    val archetype: ArchetypeId,
    val themes: List<ThemeId>,
    val requiresTribe: Boolean = false,
    val formats: Set<DeckFormat>,
)

// ═══════════════════════════════════════════════════════════════════════════════
//  B0 design contract (Wave 2 Part B, plan §B0 "per-DeckFormat layering") -- binding for ALL
//  future engine work, not just this file:
//
//  Exactly TWO extension points in the whole Deck Analysis Engine v2 are keyed by [DeckFormat]
//  (the app's real, user-facing format enum -- Standard/Pioneer/Modern/Legacy/Vintage/Pauper/
//  Commander/Casual/Draft):
//    1. THIS property, [CuratedStrategy.formats] -- which curated strategies a format offers in
//       the picker (Wave 2 Part B1, this file).
//    2. `SixtyFormatProfile` (Wave 2 Part B2, sibling file) -- the numeric lands/curve modulation
//       layered on top of the shared SIXTY skeleton.
//
//  EVERYTHING else in the engine stays keyed by the coarser [ArchetypeFormat] (COMMANDER/SIXTY)
//  exactly as before this wave: [ArchetypeData]'s bands, [ArchetypeSkeletonResolver],
//  `COLOR_MODULATION`, `LAND_MIX`, the Karsten source tables. [ArchetypeData] is NEVER forked per
//  [DeckFormat] -- the per-format layer is a THIN modulation on top of the single SIXTY skeleton,
//  exactly the same layering position color modulation already occupies. Only [DeckFormat.STANDARD]
//  has a non-default/non-passthrough entry at either extension point in this wave; every other
//  60-card format (Pioneer/Modern/Legacy/Vintage/Pauper) is documented-but-empty FUTURE DEBT (the
//  app cannot even create those decks yet -- `STUDIO_FORMATS` in `DeckEditorComponents.kt` has them
//  commented out) -- do NOT populate them ahead of that work.
//
//  Adding a future 60-card format (e.g. Modern, once deck creation supports it) must require ONLY:
//    a) a new `SixtyFormatProfile` entry,
//    b) a new strategy-availability row here (adding that `DeckFormat` to the relevant entries'
//       `formats` sets -- exactly the mechanism this file builds),
//    c) uncommenting its `STUDIO_FORMATS` chip,
//    d) realistic-density calibration fixtures.
//  Zero engine-SHAPE changes (no new resolver params beyond the one `deckFormat` param B2 adds, no
//  forked band tables, no new `ArchetypeFormat` members).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The v1 curated strategy list (plan §3.2/§4 Phase 1) -- 7 pure archetypes + 22 themed presets,
 * every entry validated against [StrategyCatalog.isValidCombination] by
 * `CuratedStrategyCatalogTest`. See this file's header for placement/scope and the plan doc's
 * Appendix A for the full per-entry curation rationale.
 */
object CuratedStrategyCatalog {

    /**
     * Version handle for this data table (plan §3.3: "so a future remote-config/JSON delivery
     * has a version handle" -- remote delivery itself is explicitly NOT built in this phase).
     * Bump whenever [ALL] changes (an entry added/removed/re-pointed to a different composition).
     */
    const val CATALOG_VERSION: Int = 1

    // ── DeckFormat sets (Wave 2 B1 -- replaces the old ArchetypeFormat-keyed BOTH_FORMATS/
    //    COMMANDER_ONLY pair). Draft is deliberately never listed in ANY of these -- it is handled
    //    as an unconditional "no restriction" passthrough by [CuratedStrategy.availableIn] (mirrors
    //    the picker's pre-B1 behavior: [ArchetypeFormat.of] returns `null` for Draft, since Draft
    //    has no archetype skeleton at all, so a strategy pin there is inert either way).

    /** Commander + Casual -- the pre-B1 "both formats" baseline, byte-identical in EFFECT to the
     * old `setOf(ArchetypeFormat.COMMANDER, ArchetypeFormat.SIXTY)` today (Casual is the only
     * currently-creatable 60-card [DeckFormat] until Standard's [STUDIO_FORMATS] chip is
     * re-enabled -- Wave 2 B5). Used by every entry NOT curated into the Standard v1 list. */
    private val COMMANDER_CASUAL: Set<DeckFormat> = setOf(DeckFormat.COMMANDER, DeckFormat.CASUAL)

    /** [COMMANDER_CASUAL] plus Standard -- the Wave 2 B1 Standard v1 curation list (see this
     * object's KDoc / the plan's Appendix A "Wave 2 Part B curation study" for the full per-entry
     * rationale + current-meta cross-check). */
    private val COMMANDER_CASUAL_STANDARD: Set<DeckFormat> = COMMANDER_CASUAL + DeckFormat.STANDARD

    /** Commander only -- structurally locked (Group Hug/Group Slug/Clones & Theft,
     * [ArchetypeData.THEMES]'s `commanderOnly` flag) or a deliberate v1 curation choice (Voltron).
     * Never gets Casual or Standard -- byte-identical to the pre-B1 `setOf(ArchetypeFormat
     * .COMMANDER)`, since Casual mapped through `ArchetypeFormat.of(CASUAL) == SIXTY` before, which
     * none of these four entries carried. */
    private val COMMANDER_ONLY: Set<DeckFormat> = setOf(DeckFormat.COMMANDER)

    /**
     * The full v1 catalog, in declaration order: 7 pure archetypes first, then 22 themed
     * presets (declaration order mirrors the plan §3.2 list order; the future picker's "Core
     * plans" / "Build-around themes" grouping, plan §3.4, is a Phase 3 UI concern layered on top,
     * not encoded here).
     */
    val ALL: List<CuratedStrategy> = listOf(
        // ── Pure archetypes (both formats) ──────────────────────────────────────────────────
        // The neutral "no specific plan" default -- resolves via ArchetypeSkeletonResolver's own
        // GENERIC base band (every resolve() starts from GENERIC before archetype/theme overrides
        // apply), so no new engine data is introduced. Closes Wave 1 open question 3 (plan
        // docs/plans/deck-analysis-engine-v2-plan.md): previously GENERIC had no catalog entry and
        // nearestFor() fell through to null ("Custom"), a confusing default for a brand-new,
        // never-pinned deck.
        CuratedStrategy(
            id = "balanced",
            displayName = "Balanced",
            description = StrategyCatalog.description(ArchetypeId.GENERIC),
            archetype = ArchetypeId.GENERIC,
            themes = emptyList(),
            // Included in Standard v1 as a judgment call, not a meta-driven pick (Wave 2 Appendix
            // A): this entry is the neutral "no specific plan" default every format needs -- the
            // SAME rationale that earned it a catalog entry at all (A3, closing Wave 1 open
            // question 3). Excluding it from Standard would leave a real gap: `nearestFor` already
            // resolves a fresh/unpinned Standard deck onto this entry for DISPLAY (the header chip
            // reads "Plan: Balanced (detected)") regardless of `formats` -- omitting STANDARD here
            // would make that same strategy unpickable from the Standard picker, a display/picker
            // mismatch. Mirrors CASUAL's own permissive-by-default philosophy.
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "aggro",
            displayName = ArchetypeId.AGGRO.displayName,
            description = StrategyCatalog.description(ArchetypeId.AGGRO),
            archetype = ArchetypeId.AGGRO,
            themes = emptyList(),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "midrange",
            displayName = ArchetypeId.MIDRANGE.displayName,
            description = StrategyCatalog.description(ArchetypeId.MIDRANGE),
            archetype = ArchetypeId.MIDRANGE,
            themes = emptyList(),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "control",
            displayName = ArchetypeId.CONTROL.displayName,
            description = StrategyCatalog.description(ArchetypeId.CONTROL),
            archetype = ArchetypeId.CONTROL,
            themes = emptyList(),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "tempo",
            displayName = ArchetypeId.TEMPO.displayName,
            description = StrategyCatalog.description(ArchetypeId.TEMPO),
            archetype = ArchetypeId.TEMPO,
            themes = emptyList(),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "combo",
            displayName = ArchetypeId.COMBO.displayName,
            description = StrategyCatalog.description(ArchetypeId.COMBO),
            archetype = ArchetypeId.COMBO,
            themes = emptyList(),
            // Excluded from Standard v1 (plan-proposed exclusion, confirmed by the meta check --
            // no dedicated "pure combo" archetype cracked the current top-10 Standard breakdown;
            // see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        // Plan §3.2 names this "Big Mana" (a friendlier player-facing label than the internal
        // RAMP archetype id) -- description still reuses RAMP's own StrategyCatalog copy
        // verbatim, which already reads as a "big mana" game plan ("cast big, powerful spells
        // early").
        CuratedStrategy(
            id = "big_mana",
            displayName = "Big Mana",
            description = StrategyCatalog.description(ArchetypeId.RAMP),
            archetype = ArchetypeId.RAMP,
            themes = emptyList(),
            formats = COMMANDER_CASUAL_STANDARD,
        ),

        // ── Themed presets (composition = catalog defaultArchetype + theme unless noted) ─────
        CuratedStrategy(
            id = "tokens",
            displayName = ThemeId.TOKENS.displayName,
            description = StrategyCatalog.description(ThemeId.TOKENS),
            archetype = ArchetypeId.AGGRO,
            themes = listOf(ThemeId.TOKENS),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "aristocrats",
            displayName = ThemeId.ARISTOCRATS.displayName,
            description = StrategyCatalog.description(ThemeId.ARISTOCRATS),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.ARISTOCRATS),
            // Excluded from Standard v1 (plan-proposed exclusion; not a distinct top-10 Standard
            // archetype at authoring time -- see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "spellslinger",
            displayName = ThemeId.SPELLSLINGER.displayName,
            description = StrategyCatalog.description(ThemeId.SPELLSLINGER),
            archetype = ArchetypeId.TEMPO,
            themes = listOf(ThemeId.SPELLSLINGER),
            // Confirmed Standard-meta-relevant by the Wave 2 B1 meta check: Izzet Prowess +
            // Izzet Spellementals combined ~17% of the current Standard field (see Appendix A).
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        // Commander-only in v1 by deliberate curation choice, NOT because the theme itself is
        // data-locked (ArchetypeData.THEMES[VOLTRON].commanderOnly is false -- a 60-card Voltron
        // resolution exists). See the curation study for the rationale.
        CuratedStrategy(
            id = "voltron",
            displayName = ThemeId.VOLTRON.displayName,
            description = StrategyCatalog.description(ThemeId.VOLTRON),
            archetype = ArchetypeId.AGGRO,
            themes = listOf(ThemeId.VOLTRON),
            formats = COMMANDER_ONLY,
        ),
        CuratedStrategy(
            id = "reanimator",
            displayName = ThemeId.REANIMATOR.displayName,
            description = StrategyCatalog.description(ThemeId.REANIMATOR),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.REANIMATOR),
            // DEVIATION from the plan's proposed Standard v1 exclusion list, WITH rationale (Wave
            // 2 B1 meta check): the plan proposed excluding Reanimator as "not meta-real ... at
            // Standard power level", but the current live Standard metagame (mtggoldfish-sourced
            // snapshot, 2026-08) shows "Reanimator" (~11%) and "Superior Reanimator" (~4%) as
            // real, independently-tracked top-10 archetypes -- combined ~15% of the field, well
            // above noise. Included per the plan's own escape hatch ("if a ... reanimator shell is
            // genuinely meta right now, include it"). See Appendix A.
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "stax",
            displayName = ThemeId.STAX.displayName,
            description = StrategyCatalog.description(ThemeId.STAX),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.STAX),
            // Excluded from Standard v1 (plan-proposed exclusion, confirmed by the meta check --
            // no dedicated Stax shell in the current top-10 Standard breakdown; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "landfall",
            displayName = ThemeId.LANDFALL.displayName,
            description = StrategyCatalog.description(ThemeId.LANDFALL),
            archetype = ArchetypeId.RAMP,
            themes = listOf(ThemeId.LANDFALL),
            // DEVIATION from the plan's proposed Standard v1 exclusion list, WITH rationale (Wave
            // 2 B1 meta check): the plan proposed excluding Landfall, but the current live
            // Standard metagame (mtggoldfish-sourced snapshot, 2026-08) shows "Mono Green Landfall"
            // as the #1 tracked archetype at ~13.7% of the field -- clearly meta-real, not a niche
            // brew. Included per the plan's own escape hatch. See Appendix A.
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "lifegain",
            displayName = ThemeId.LIFEGAIN.displayName,
            description = StrategyCatalog.description(ThemeId.LIFEGAIN),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.LIFEGAIN),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "plus1_counters",
            displayName = ThemeId.PLUS1_COUNTERS.displayName,
            description = StrategyCatalog.description(ThemeId.PLUS1_COUNTERS),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.PLUS1_COUNTERS),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "tribal",
            displayName = ThemeId.TRIBAL.displayName,
            description = StrategyCatalog.description(ThemeId.TRIBAL),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.TRIBAL),
            requiresTribe = true,
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "artifacts",
            displayName = ThemeId.ARTIFACTS.displayName,
            description = StrategyCatalog.description(ThemeId.ARTIFACTS),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.ARTIFACTS),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "enchantress",
            displayName = ThemeId.ENCHANTRESS.displayName,
            description = StrategyCatalog.description(ThemeId.ENCHANTRESS),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.ENCHANTRESS),
            // Excluded from Standard v1 (plan-proposed exclusion; not a distinct top-10 Standard
            // archetype at authoring time -- see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "blink",
            displayName = ThemeId.BLINK.displayName,
            description = StrategyCatalog.description(ThemeId.BLINK),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.BLINK),
            // Excluded from Standard v1 (plan-proposed exclusion; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "mill",
            displayName = ThemeId.MILL.displayName,
            description = StrategyCatalog.description(ThemeId.MILL),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.MILL),
            // Excluded from Standard v1 (plan-proposed exclusion; the meta check found no
            // dedicated mill/self-mill archetype at meaningful Standard share -- see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "wheels",
            displayName = ThemeId.WHEELS.displayName,
            description = StrategyCatalog.description(ThemeId.WHEELS),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.WHEELS),
            // Excluded from Standard v1 (plan-proposed exclusion; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "superfriends",
            displayName = ThemeId.SUPERFRIENDS.displayName,
            description = StrategyCatalog.description(ThemeId.SUPERFRIENDS),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.SUPERFRIENDS),
            // Excluded from Standard v1 (plan-proposed exclusion; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "toolbox",
            displayName = ThemeId.TOOLBOX.displayName,
            description = StrategyCatalog.description(ThemeId.TOOLBOX),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.TOOLBOX),
            // Excluded from Standard v1 (plan-proposed exclusion; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        // Commander-only per ArchetypeData.THEMES[GROUP_HUG].commanderOnly (structural, not a
        // curation choice -- no 60-card shell exists for this theme at all).
        CuratedStrategy(
            id = "group_hug",
            displayName = ThemeId.GROUP_HUG.displayName,
            description = StrategyCatalog.description(ThemeId.GROUP_HUG),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.GROUP_HUG),
            formats = COMMANDER_ONLY,
        ),
        // Commander-only per ArchetypeData.THEMES[GROUP_SLUG].commanderOnly (structural).
        CuratedStrategy(
            id = "group_slug",
            displayName = ThemeId.GROUP_SLUG.displayName,
            description = StrategyCatalog.description(ThemeId.GROUP_SLUG),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.GROUP_SLUG),
            formats = COMMANDER_ONLY,
        ),
        // Commander-only per ArchetypeData.THEMES[CLONES_THEFT].commanderOnly (structural).
        CuratedStrategy(
            id = "clones_theft",
            displayName = ThemeId.CLONES_THEFT.displayName,
            description = StrategyCatalog.description(ThemeId.CLONES_THEFT),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.CLONES_THEFT),
            formats = COMMANDER_ONLY,
        ),
        CuratedStrategy(
            id = "vehicles",
            displayName = ThemeId.VEHICLES.displayName,
            description = StrategyCatalog.description(ThemeId.VEHICLES),
            archetype = ArchetypeId.AGGRO,
            themes = listOf(ThemeId.VEHICLES),
            formats = COMMANDER_CASUAL_STANDARD,
        ),
        CuratedStrategy(
            id = "self_mill",
            displayName = ThemeId.SELF_MILL.displayName,
            description = StrategyCatalog.description(ThemeId.SELF_MILL),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.SELF_MILL),
            // Excluded from Standard v1 (plan-proposed exclusion; Reanimator's inclusion above
            // covers the graveyard-plan angle already meta-relevant -- a SEPARATE dedicated
            // self-mill archetype is not independently tracked at meaningful Standard share --
            // see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
    )

    /** O(1) lookup by [CuratedStrategy.id], built once from [ALL]. */
    private val BY_ID: Map<String, CuratedStrategy> = ALL.associateBy { it.id }

    /** The catalog entry for [id], or `null` if unknown. */
    fun byId(id: String): CuratedStrategy? = BY_ID[id]
}

/**
 * Deck Analysis Engine v2 plan §3.2: "a small mapper snaps the inferred pair to the nearest
 * curated strategy for display". Maps an (archetype, themes) pair -- as produced by
 * [com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase] (inference) or read
 * back off a legacy `Deck.archetypeOverride`/`themesOverride` pin -- onto the nearest
 * [CuratedStrategy].
 *
 * Resolution order:
 * 1. Exact match: a catalog entry with the SAME [archetype] and the SAME theme SET (order-
 *    insensitive -- inference's own ordering is "by descending confidence", which is not part of
 *    a strategy's identity). This now also covers `(`[ArchetypeId.GENERIC]`, emptyList())` --
 *    the "balanced" entry added to close Wave 1 open question 3 -- since that entry's own
 *    (archetype, themes) IS `(GENERIC, emptyList())`, no special-casing is needed for it here.
 * 2. Fallback: the PURE-archetype catalog entry for [archetype] (an entry with that archetype and
 *    an empty theme list) -- covers "confident archetype, but the detected theme combination
 *    itself isn't curated" (e.g. 2 confident themes that don't form a single curated preset).
 *    [ArchetypeId.GENERIC] is deliberately EXCLUDED from this fallback (see step 3) even though it
 *    now has a pure entry ("balanced") -- unlike every other archetype, GENERIC's pure entry means
 *    literally "no plan pinned", so a GENERIC pin that carries a theme must NOT be coerced into
 *    "balanced"; it's an incoherent/unusual legacy state that should read as "Custom" instead.
 * 3. No match at all -- returns `null`, the "Custom" sentinel. Applies to: a `null` archetype, OR
 *    [ArchetypeId.GENERIC] paired with one or more themes (see step 2's rationale -- this is the
 *    one case where an archetype has a pure catalog entry but still doesn't participate in the
 *    generic pure-archetype fallback). `null` over a dedicated marker type: every OTHER call site
 *    in this package that resolves an enum/string to a catalog value uses a plain nullable return
 *    for "no match" (see [ThemeId.fromDisplayName]), so this keeps the same, already-established
 *    convention rather than introducing a second "not found" shape into the same file. Callers
 *    that need a player-facing "Custom" label (Phase 3's picker) render that themselves off the
 *    `null`.
 *
 * @param format Wave 2 B4 -- optional [DeckFormat] to restrict the search to. When non-null, BOTH
 *   resolution steps above (exact match, then pure-archetype fallback) only ever consider entries
 *   where [CuratedStrategy.availableIn] is true for [format] -- same fallback-chain SHAPE, just
 *   filtered at each step. This closes the gap where a format-blind exact match could surface a
 *   catalog entry the picker doesn't even offer for that format (e.g. inference detects STAX on a
 *   Standard deck -- "stax" is excluded from Standard v1, Appendix A -- the caller must not be
 *   handed a strategy the Standard picker can't show; this degrades to the pure "control" entry
 *   instead, since pure-archetype entries are Standard-available for every non-Commander-only
 *   archetype). Defaults to `null` (no restriction) so every pre-B4 call site and test -- most
 *   notably `AnalysisEngine.kt`'s `nearestFor(archetype, themes)` call, still 2-arg and therefore
 *   unaffected by this default -- keeps its exact prior behavior unchanged. `format` is NOT itself
 *   validated against [archetype]/[themes]; a caller passing an unmatched combination simply gets
 *   whatever the filtered fallback chain resolves to, same as the unfiltered version.
 */
fun CuratedStrategyCatalog.nearestFor(
    archetype: ArchetypeId?,
    themes: List<ThemeId>,
    format: DeckFormat? = null,
): CuratedStrategy? {
    val themeSet = themes.toSet()
    val candidates = if (format == null) ALL else ALL.filter { it.availableIn(format) }
    val exact = candidates.firstOrNull { it.archetype == archetype && it.themes.toSet() == themeSet }
    if (exact != null) return exact
    if (archetype == null || archetype == ArchetypeId.GENERIC) return null
    return candidates.firstOrNull { it.archetype == archetype && it.themes.isEmpty() }
}

/**
 * Wave 2 B1: is [this] strategy offered for [format] -- the single source of truth the picker
 * (`CuratedStrategyPickerSheet.kt`) filters against, replacing that file's previous inline
 * `archetypeFormat in entry.formats` check.
 *
 * [DeckFormat.DRAFT] (and any other future [DeckFormat] with no [ArchetypeFormat] mapping) is an
 * unconditional passthrough -- `true` regardless of [CuratedStrategy.formats] -- mirroring the
 * pre-B1 picker behavior: [ArchetypeFormat.of] returns `null` for Draft because Draft has no
 * archetype skeleton at all ([ArchetypeFormat]'s own KDoc), so a strategy pin there is inert
 * either way and every entry stays pickable.
 */
fun CuratedStrategy.availableIn(format: DeckFormat): Boolean {
    if (ArchetypeFormat.of(format) == null) return true
    return format in formats
}
