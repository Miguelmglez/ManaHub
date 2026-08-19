package com.mmg.manahub.feature.decks.domain.engine

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
 * @property formats which [ArchetypeFormat]s this strategy is offered for. Most entries are both
 *           formats; a subset is Commander-only either because the underlying theme is
 *           structurally Commander-only ([ArchetypeData.THEMES]'s `commanderOnly` flag -- Group
 *           Hug/Group Slug/Clones & Theft) or because this v1 catalog deliberately curates it
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
    val formats: Set<ArchetypeFormat>,
)

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

    private val BOTH_FORMATS: Set<ArchetypeFormat> = setOf(ArchetypeFormat.COMMANDER, ArchetypeFormat.SIXTY)
    private val COMMANDER_ONLY: Set<ArchetypeFormat> = setOf(ArchetypeFormat.COMMANDER)

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
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "aggro",
            displayName = ArchetypeId.AGGRO.displayName,
            description = StrategyCatalog.description(ArchetypeId.AGGRO),
            archetype = ArchetypeId.AGGRO,
            themes = emptyList(),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "midrange",
            displayName = ArchetypeId.MIDRANGE.displayName,
            description = StrategyCatalog.description(ArchetypeId.MIDRANGE),
            archetype = ArchetypeId.MIDRANGE,
            themes = emptyList(),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "control",
            displayName = ArchetypeId.CONTROL.displayName,
            description = StrategyCatalog.description(ArchetypeId.CONTROL),
            archetype = ArchetypeId.CONTROL,
            themes = emptyList(),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "tempo",
            displayName = ArchetypeId.TEMPO.displayName,
            description = StrategyCatalog.description(ArchetypeId.TEMPO),
            archetype = ArchetypeId.TEMPO,
            themes = emptyList(),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "combo",
            displayName = ArchetypeId.COMBO.displayName,
            description = StrategyCatalog.description(ArchetypeId.COMBO),
            archetype = ArchetypeId.COMBO,
            themes = emptyList(),
            formats = BOTH_FORMATS,
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
            formats = BOTH_FORMATS,
        ),

        // ── Themed presets (composition = catalog defaultArchetype + theme unless noted) ─────
        CuratedStrategy(
            id = "tokens",
            displayName = ThemeId.TOKENS.displayName,
            description = StrategyCatalog.description(ThemeId.TOKENS),
            archetype = ArchetypeId.AGGRO,
            themes = listOf(ThemeId.TOKENS),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "aristocrats",
            displayName = ThemeId.ARISTOCRATS.displayName,
            description = StrategyCatalog.description(ThemeId.ARISTOCRATS),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.ARISTOCRATS),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "spellslinger",
            displayName = ThemeId.SPELLSLINGER.displayName,
            description = StrategyCatalog.description(ThemeId.SPELLSLINGER),
            archetype = ArchetypeId.TEMPO,
            themes = listOf(ThemeId.SPELLSLINGER),
            formats = BOTH_FORMATS,
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
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "stax",
            displayName = ThemeId.STAX.displayName,
            description = StrategyCatalog.description(ThemeId.STAX),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.STAX),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "landfall",
            displayName = ThemeId.LANDFALL.displayName,
            description = StrategyCatalog.description(ThemeId.LANDFALL),
            archetype = ArchetypeId.RAMP,
            themes = listOf(ThemeId.LANDFALL),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "lifegain",
            displayName = ThemeId.LIFEGAIN.displayName,
            description = StrategyCatalog.description(ThemeId.LIFEGAIN),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.LIFEGAIN),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "plus1_counters",
            displayName = ThemeId.PLUS1_COUNTERS.displayName,
            description = StrategyCatalog.description(ThemeId.PLUS1_COUNTERS),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.PLUS1_COUNTERS),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "tribal",
            displayName = ThemeId.TRIBAL.displayName,
            description = StrategyCatalog.description(ThemeId.TRIBAL),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.TRIBAL),
            requiresTribe = true,
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "artifacts",
            displayName = ThemeId.ARTIFACTS.displayName,
            description = StrategyCatalog.description(ThemeId.ARTIFACTS),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.ARTIFACTS),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "enchantress",
            displayName = ThemeId.ENCHANTRESS.displayName,
            description = StrategyCatalog.description(ThemeId.ENCHANTRESS),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.ENCHANTRESS),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "blink",
            displayName = ThemeId.BLINK.displayName,
            description = StrategyCatalog.description(ThemeId.BLINK),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.BLINK),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "mill",
            displayName = ThemeId.MILL.displayName,
            description = StrategyCatalog.description(ThemeId.MILL),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.MILL),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "wheels",
            displayName = ThemeId.WHEELS.displayName,
            description = StrategyCatalog.description(ThemeId.WHEELS),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.WHEELS),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "superfriends",
            displayName = ThemeId.SUPERFRIENDS.displayName,
            description = StrategyCatalog.description(ThemeId.SUPERFRIENDS),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.SUPERFRIENDS),
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "toolbox",
            displayName = ThemeId.TOOLBOX.displayName,
            description = StrategyCatalog.description(ThemeId.TOOLBOX),
            archetype = ArchetypeId.CONTROL,
            themes = listOf(ThemeId.TOOLBOX),
            formats = BOTH_FORMATS,
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
            formats = BOTH_FORMATS,
        ),
        CuratedStrategy(
            id = "self_mill",
            displayName = ThemeId.SELF_MILL.displayName,
            description = StrategyCatalog.description(ThemeId.SELF_MILL),
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.SELF_MILL),
            formats = BOTH_FORMATS,
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
 */
fun CuratedStrategyCatalog.nearestFor(archetype: ArchetypeId?, themes: List<ThemeId>): CuratedStrategy? {
    val themeSet = themes.toSet()
    val exact = ALL.firstOrNull { it.archetype == archetype && it.themes.toSet() == themeSet }
    if (exact != null) return exact
    if (archetype == null || archetype == ArchetypeId.GENERIC) return null
    return ALL.firstOrNull { it.archetype == archetype && it.themes.isEmpty() }
}
