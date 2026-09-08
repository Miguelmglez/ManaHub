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
 * @property archetypes Deck Analysis Engine v3 (spec §4.2) -- the SET of macro archetypes this
 *           strategy is compatible with, DOWN from a single `archetype` field. Today 11+ of the 23
 *           themed entries hardcoded `archetype = MIDRANGE`, which was the third layer reinforcing
 *           the MIDRANGE bias the v3 spec exists to kill ("Tokens is not Midrange -- it works in
 *           Aggro, Midrange and Combo"). [nearestFor]'s exact-match step now checks SET membership
 *           (`archetype in it.archetypes`) rather than equality.
 * @property postures Deck Analysis Engine v3 (spec §3, NEW) -- the ex-macros (`RAMP`/`TEMPO`) and
 *           ex-themes (`ATTRITION`/`TOOLBOX`/`VOLTRON`/`GROUP_HUG`/`GROUP_SLUG`) that moved to
 *           [PostureId] still need a curated entry (a player still wants to pick "Big Mana"/
 *           "Voltron" from the picker) -- this is that entry's posture identity. Empty for every
 *           entry that is a pure archetype/theme composition with no posture involved.
 * @property themes 0..2 themes (mirrors [StrategyCatalog.MAX_THEMES]) layered on [archetypes].
 * @property requiresTribe `true` only for the Tribal entry -- mirrors
 *           [ThemeCatalogEntry.requiresTribe]; the (future) picker must collect a concrete
 *           creature type before this strategy resolves to a complete skeleton.
 * @property formats which [DeckFormat]s this strategy is offered for (Wave 2 Part B, plan
 *           `docs/plans/deck-analysis-engine-v2-wave2-standard-plan.md` B0/B1 -- see the KDoc on
 *           this property below for the full per-`DeckFormat` design contract). Most entries are
 *           at least Commander + Casual; a subset is Commander-only either because the underlying
 *           theme is structurally Commander-only ([ArchetypeData.THEMES]'s `commanderOnly` flag --
 *           Clones & Theft) or a deliberate v1 curation choice.
 */
data class CuratedStrategy(
    val id: String,
    val displayName: String,
    val description: String,
    val archetypes: Set<ArchetypeId>,
    val postures: Set<PostureId> = emptySet(),
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
//  exactly the same layering position color modulation already occupies. As of the Wave 2
//  future-debt closeout (2026-09-06), every 60-card constructed [DeckFormat] (STANDARD, MODERN,
//  PIONEER, LEGACY, VINTAGE, PAUPER) has a non-default/non-passthrough entry at both extension
//  points -- `STUDIO_FORMATS` (the old gate this comment used to cite) no longer exists as a
//  symbol in the codebase; `DeckCreationSheet.kt` already lists all nine [DeckFormat]s and users
//  could already create decks in every one of these formats before this closeout -- the only real
//  gap was the picker/inference layer this file (and `SixtyFormatProfile`) now closes.
//
//  Adding a future 60-card format required ONLY:
//    a) a new `SixtyFormatProfile` entry,
//    b) a new strategy-availability row here (adding that `DeckFormat` to the relevant entries'
//       `formats` sets -- exactly the mechanism this file builds),
//    c) deck creation already supporting the format (it did, for all five -- see below),
//    d) realistic-density calibration fixtures.
//  Zero engine-SHAPE changes (no new resolver params beyond the one `deckFormat` param B2 adds, no
//  forked band tables, no new `ArchetypeFormat` members).
//
//  Wave 2 future-debt closeout (2026-09-06): MODERN/PIONEER/LEGACY/VINTAGE/PAUPER now get both
//  extension points populated (see [SixtyFormatProfile] for (a); [COMMANDER_CASUAL_SIXTY] below
//  for (b)). (c) had already shipped independently of this file: `DeckCreationSheet.kt` lists all
//  nine [DeckFormat]s and users could already create decks in these five formats -- the gap this
//  closeout fixes was purely that [nearestFor] had ZERO curated candidates for any of them (every
//  one of these formats maps to a non-null [ArchetypeFormat] via [ArchetypeFormat.of], so
//  [availableIn] was never the unconditional-Draft-style passthrough -- it was filtering the whole
//  29-entry catalog down to nothing).
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
     *
     * v2 (Deck Analysis Engine v3, spec §4.2): [CuratedStrategy.archetype] widened to
     * [CuratedStrategy.archetypes] (a set); the "balanced"/GENERIC entry removed (`ArchetypeId
     * .GENERIC` no longer exists -- an unpinned/ambiguous deck now reads "Custom" with no catalog
     * entry, per spec §2.1); RAMP/TEMPO/VOLTRON/TOOLBOX/GROUP_HUG/GROUP_SLUG entries re-pointed
     * onto [CuratedStrategy.postures]; STAX re-pointed onto the new `ArchetypeId.PRISON` macro;
     * `mill` retargeted onto `ThemeId.MILL_OPPONENT`.
     */
    const val CATALOG_VERSION: Int = 2

    // ── DeckFormat sets (Wave 2 B1 -- replaces the old ArchetypeFormat-keyed BOTH_FORMATS/
    //    COMMANDER_ONLY pair). Draft is deliberately never listed in ANY of these -- it is handled
    //    as an unconditional "no restriction" passthrough by [CuratedStrategy.availableIn] (mirrors
    //    the picker's pre-B1 behavior: [ArchetypeFormat.of] returns `null` for Draft, since Draft
    //    has no archetype skeleton at all, so a strategy pin there is inert either way).

    /** Commander + Casual -- entries that are NOT curated for any 60-card constructed format's
     * picker (Commander-shaped or v1-Standard-excluded themes: Voltron, Group Hug, Aristocrats,
     * etc.). Gates a strategy OUT of every 60-card constructed format's picker (Standard, Modern,
     * Pioneer, Legacy, Vintage, Pauper) while remaining a Casual/Commander pick, either because the
     * underlying theme is structurally Commander-only ([ArchetypeData.THEMES]'s `commanderOnly`
     * flag) or a deliberate v1 curation choice (not a distinct top-10 archetype in any curated
     * format's metagame at authoring time). */
    private val COMMANDER_CASUAL: Set<DeckFormat> = setOf(DeckFormat.COMMANDER, DeckFormat.CASUAL)

    /** All six 60-card constructed [DeckFormat]s this catalog curates strategies for. */
    private val ALL_SIXTY_CONSTRUCTED: Set<DeckFormat> = setOf(
        DeckFormat.STANDARD, DeckFormat.PIONEER, DeckFormat.MODERN,
        DeckFormat.LEGACY, DeckFormat.VINTAGE, DeckFormat.PAUPER,
    )

    /** [COMMANDER_CASUAL] plus every 60-card constructed format ([ALL_SIXTY_CONSTRUCTED]) -- the
     * Wave 2 B1 Standard v1 curation list (see this object's KDoc / the plan's Appendix A "Wave 2
     * Part B curation study" for the full per-entry rationale + current-meta cross-check), widened
     * in the Wave 2 future-debt closeout (2026-09-06) to MODERN/PIONEER/LEGACY/VINTAGE/PAUPER too.
     * These are generic macro archetypes/themes (Aggro/Midrange/Control/Tempo/Big Mana/Tokens/
     * Spellslinger/Reanimator/Landfall/Lifegain/+1 Counters/Tribal/Artifacts/Vehicles/Equipment) --
     * real, recognizable shells across every constructed format, not Standard-specific metagame
     * calls, so the SAME 15-strategy list is reused rather than re-deriving a per-format top-10
     * from scratch (a live per-format metagame study 5x over would be disproportionate to the
     * payoff -- see this file's header comment). A light WebSearch sanity check (2026-09-06) found
     * no entry on this list implausible in any of the five new formats -- see the bespoke `storm`
     * entry below for the one archetype that WAS specifically checked (and confirmed, not
     * excluded) per format. */
    private val COMMANDER_CASUAL_SIXTY: Set<DeckFormat> = COMMANDER_CASUAL + ALL_SIXTY_CONSTRUCTED

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
        // "balanced"/GENERIC REMOVED (Deck Analysis Engine v3): ArchetypeId.GENERIC no longer
        // exists -- an unpinned/ambiguous deck now reads "Custom" (or a hybrid label) directly off
        // AnalysisEngine.evaluate's own displayName fallback, per spec §2.1 ("never silently
        // coerce"), with no catalog entry standing in for it.
        CuratedStrategy(
            id = "aggro",
            displayName = ArchetypeId.AGGRO.displayName,
            description = StrategyCatalog.description(ArchetypeId.AGGRO),
            archetypes = setOf(ArchetypeId.AGGRO),
            themes = emptyList(),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "midrange",
            displayName = ArchetypeId.MIDRANGE.displayName,
            description = StrategyCatalog.description(ArchetypeId.MIDRANGE),
            archetypes = setOf(ArchetypeId.MIDRANGE),
            themes = emptyList(),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "control",
            displayName = ArchetypeId.CONTROL.displayName,
            description = StrategyCatalog.description(ArchetypeId.CONTROL),
            archetypes = setOf(ArchetypeId.CONTROL),
            themes = emptyList(),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        // TEMPO moved to PostureId (spec §2/§3) -- no longer its own macro. Kept as a curated
        // pick via `postures`, matching against the two macros a tempo shell most naturally
        // overlays (an aggressive or a controlling shell, per its own "aggro-control" identity).
        CuratedStrategy(
            id = "tempo",
            displayName = PostureId.TEMPO.displayName,
            description = "Efficient threats backed by protection and disruption -- keep the initiative and never let go.",
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.CONTROL),
            postures = setOf(PostureId.TEMPO),
            themes = emptyList(),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "combo",
            displayName = ArchetypeId.COMBO.displayName,
            description = StrategyCatalog.description(ArchetypeId.COMBO),
            archetypes = setOf(ArchetypeId.COMBO),
            themes = emptyList(),
            // Excluded from Standard v1 (plan-proposed exclusion, confirmed by the meta check --
            // no dedicated "pure combo" archetype cracked the current top-10 Standard breakdown;
            // see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        // Deck Analysis Engine v3 (spec §2.2, NEW macro): a stax skeleton wants its own BASE bands
        // (3-5 finishers, 3-7 card draw), not an overlay on top of another archetype.
        CuratedStrategy(
            id = "prison",
            displayName = ArchetypeId.PRISON.displayName,
            description = "Slow the whole table down with resource-denial lock pieces -- taxes, resets, and asymmetric restrictions, then close with a lean finisher suite.",
            archetypes = setOf(ArchetypeId.PRISON),
            themes = emptyList(),
            // Excluded from Standard v1 (mirrors the old STAX theme's own Standard exclusion --
            // no dedicated prison/stax shell in the current top-10 Standard breakdown).
            formats = COMMANDER_CASUAL,
        ),
        // Plan §3.2 named this "Big Mana" -- description reuses the RETIRED RAMP archetype's old
        // StrategyCatalog copy verbatim (still reads correctly as a "big mana" game plan). RAMP
        // moved to PostureId (spec §2/§3: "ramp into threats = Midrange, into a combo = Combo,
        // into inevitability = Control") -- matches the 3 macros the posture naturally overlays.
        CuratedStrategy(
            id = "big_mana",
            displayName = "Big Mana",
            description = "Accelerate your mana ahead of schedule to cast big, powerful spells early.",
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            postures = setOf(PostureId.RAMP),
            themes = emptyList(),
            formats = COMMANDER_CASUAL_SIXTY,
        ),

        // ── Themed presets (composition = compatible archetypes + theme unless noted) ────────
        // Deck Analysis Engine v3 (spec §4.2's own worked example): "Tokens is not Midrange -- it
        // works in Aggro (go-wide), Midrange and Combo" -- widened from the old MIDRANGE-only entry.
        CuratedStrategy(
            id = "tokens",
            displayName = ThemeId.TOKENS.displayName,
            description = StrategyCatalog.description(ThemeId.TOKENS),
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE, ArchetypeId.COMBO),
            themes = listOf(ThemeId.TOKENS),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "aristocrats",
            displayName = ThemeId.ARISTOCRATS.displayName,
            description = StrategyCatalog.description(ThemeId.ARISTOCRATS),
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE, ArchetypeId.COMBO),
            themes = listOf(ThemeId.ARISTOCRATS),
            // Excluded from Standard v1 (plan-proposed exclusion; not a distinct top-10 Standard
            // archetype at authoring time -- see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        // TEMPO moved to PostureId -- SPELLSLINGER's compatible-archetype set widens to the 3
        // remaining macros a spellslinger shell commonly overlays (mirrors StrategyCatalog's own
        // THEME_CATALOG[SPELLSLINGER].compatibleArchetypes minus the now-retired TEMPO entry).
        CuratedStrategy(
            id = "spellslinger",
            displayName = ThemeId.SPELLSLINGER.displayName,
            description = StrategyCatalog.description(ThemeId.SPELLSLINGER),
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            themes = listOf(ThemeId.SPELLSLINGER),
            // Confirmed Standard-meta-relevant by the Wave 2 B1 meta check: Izzet Prowess +
            // Izzet Spellementals combined ~17% of the current Standard field (see Appendix A).
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        // VOLTRON moved to PostureId (spec §4.1) -- no longer a theme. Commander-only in v1 by
        // deliberate curation choice (unchanged from the prior pass's own rationale).
        CuratedStrategy(
            id = "voltron",
            displayName = PostureId.VOLTRON.displayName,
            description = "Stack auras, equipment, and buffs onto a single evasive threat and win through commander damage or raw power.",
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE),
            postures = setOf(PostureId.VOLTRON),
            themes = emptyList(),
            formats = COMMANDER_ONLY,
        ),
        CuratedStrategy(
            id = "reanimator",
            displayName = ThemeId.REANIMATOR.displayName,
            description = StrategyCatalog.description(ThemeId.REANIMATOR),
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            themes = listOf(ThemeId.REANIMATOR),
            // DEVIATION from the plan's proposed Standard v1 exclusion list, WITH rationale (Wave
            // 2 B1 meta check): the plan proposed excluding Reanimator as "not meta-real ... at
            // Standard power level", but the current live Standard metagame (mtggoldfish-sourced
            // snapshot, 2026-08) shows "Reanimator" (~11%) and "Superior Reanimator" (~4%) as
            // real, independently-tracked top-10 archetypes -- combined ~15% of the field, well
            // above noise. Included per the plan's own escape hatch ("if a ... reanimator shell is
            // genuinely meta right now, include it"). See Appendix A.
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        // LANDFALL: RAMP moved to PostureId -- widened to MIDRANGE/CONTROL/COMBO (the 3 macros a
        // ramp-flavored theme most naturally overlays), keeping the RAMP posture as an optional
        // (non-required) hint for the exact-match step.
        CuratedStrategy(
            id = "landfall",
            displayName = ThemeId.LANDFALL.displayName,
            description = StrategyCatalog.description(ThemeId.LANDFALL),
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            postures = setOf(PostureId.RAMP),
            themes = listOf(ThemeId.LANDFALL),
            // DEVIATION from the plan's proposed Standard v1 exclusion list, WITH rationale (Wave
            // 2 B1 meta check): the plan proposed excluding Landfall, but the current live
            // Standard metagame (mtggoldfish-sourced snapshot, 2026-08) shows "Mono Green Landfall"
            // as the #1 tracked archetype at ~13.7% of the field -- clearly meta-real, not a niche
            // brew. Included per the plan's own escape hatch. See Appendix A.
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "lifegain",
            displayName = ThemeId.LIFEGAIN.displayName,
            description = StrategyCatalog.description(ThemeId.LIFEGAIN),
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE, ArchetypeId.CONTROL),
            themes = listOf(ThemeId.LIFEGAIN),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "plus1_counters",
            displayName = ThemeId.PLUS1_COUNTERS.displayName,
            description = StrategyCatalog.description(ThemeId.PLUS1_COUNTERS),
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE),
            themes = listOf(ThemeId.PLUS1_COUNTERS),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "tribal",
            displayName = ThemeId.TRIBAL.displayName,
            description = StrategyCatalog.description(ThemeId.TRIBAL),
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE),
            themes = listOf(ThemeId.TRIBAL),
            requiresTribe = true,
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "artifacts",
            displayName = ThemeId.ARTIFACTS.displayName,
            description = StrategyCatalog.description(ThemeId.ARTIFACTS),
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.COMBO, ArchetypeId.CONTROL),
            themes = listOf(ThemeId.ARTIFACTS),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "enchantress",
            displayName = ThemeId.ENCHANTRESS.displayName,
            description = StrategyCatalog.description(ThemeId.ENCHANTRESS),
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            themes = listOf(ThemeId.ENCHANTRESS),
            // Excluded from Standard v1 (plan-proposed exclusion; not a distinct top-10 Standard
            // archetype at authoring time -- see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "blink",
            displayName = ThemeId.BLINK.displayName,
            description = StrategyCatalog.description(ThemeId.BLINK),
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            themes = listOf(ThemeId.BLINK),
            // Excluded from Standard v1 (plan-proposed exclusion; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        // Retargeted onto ThemeId.MILL_OPPONENT (spec §4.2 split -- this entry is the win-condition
        // half; SELF_MILL below is the enabler half).
        CuratedStrategy(
            id = "mill",
            displayName = ThemeId.MILL_OPPONENT.displayName,
            description = StrategyCatalog.description(ThemeId.MILL_OPPONENT),
            archetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.COMBO),
            themes = listOf(ThemeId.MILL_OPPONENT),
            // Excluded from Standard v1 (plan-proposed exclusion; the meta check found no
            // dedicated mill/self-mill archetype at meaningful Standard share -- see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "wheels",
            displayName = ThemeId.WHEELS.displayName,
            description = StrategyCatalog.description(ThemeId.WHEELS),
            archetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.COMBO),
            themes = listOf(ThemeId.WHEELS),
            // Excluded from Standard v1 (plan-proposed exclusion; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "superfriends",
            displayName = ThemeId.SUPERFRIENDS.displayName,
            description = StrategyCatalog.description(ThemeId.SUPERFRIENDS),
            archetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.MIDRANGE),
            themes = listOf(ThemeId.SUPERFRIENDS),
            // Excluded from Standard v1 (plan-proposed exclusion; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        // TOOLBOX moved to PostureId (spec §4.1) -- no longer a theme.
        CuratedStrategy(
            id = "toolbox",
            displayName = PostureId.TOOLBOX.displayName,
            description = "Tutor for the exact answer or piece you need out of a wide toolbox of one-of effects.",
            archetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.MIDRANGE, ArchetypeId.COMBO),
            postures = setOf(PostureId.TOOLBOX),
            themes = emptyList(),
            // Excluded from Standard v1 (plan-proposed exclusion; see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        // GROUP_HUG/GROUP_SLUG moved to PostureId (spec §4.1) -- Commander-only per
        // [PostureDefinition.commanderOnly] (structural, not a curation choice -- no 60-card shell
        // exists for a social/multiplayer-only posture at all).
        CuratedStrategy(
            id = "group_hug",
            displayName = PostureId.GROUP_HUG.displayName,
            description = "Give every player extra resources -- mana, cards, or turns -- to keep the table peaceful and open-ended.",
            archetypes = setOf(ArchetypeId.CONTROL),
            postures = setOf(PostureId.GROUP_HUG),
            themes = emptyList(),
            formats = COMMANDER_ONLY,
        ),
        CuratedStrategy(
            id = "group_slug",
            displayName = PostureId.GROUP_SLUG.displayName,
            description = "Deal damage to every opponent equally with symmetrical effects that punish the whole table.",
            archetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.AGGRO),
            postures = setOf(PostureId.GROUP_SLUG),
            themes = emptyList(),
            formats = COMMANDER_ONLY,
        ),
        // Commander-only per ArchetypeData.THEMES[CLONES_THEFT].commanderOnly (structural).
        CuratedStrategy(
            id = "clones_theft",
            displayName = ThemeId.CLONES_THEFT.displayName,
            description = StrategyCatalog.description(ThemeId.CLONES_THEFT),
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.COMBO, ArchetypeId.CONTROL),
            themes = listOf(ThemeId.CLONES_THEFT),
            formats = COMMANDER_ONLY,
        ),
        CuratedStrategy(
            id = "vehicles",
            displayName = ThemeId.VEHICLES.displayName,
            description = StrategyCatalog.description(ThemeId.VEHICLES),
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE),
            themes = listOf(ThemeId.VEHICLES),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        CuratedStrategy(
            id = "self_mill",
            displayName = ThemeId.SELF_MILL.displayName,
            description = StrategyCatalog.description(ThemeId.SELF_MILL),
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            themes = listOf(ThemeId.SELF_MILL),
            // Excluded from Standard v1 (plan-proposed exclusion; Reanimator's inclusion above
            // covers the graveyard-plan angle already meta-relevant -- a SEPARATE dedicated
            // self-mill archetype is not independently tracked at meaningful Standard share --
            // see Appendix A).
            formats = COMMANDER_CASUAL,
        ),
        // Deck Analysis Engine v3 (spec §4.2, NEW themes).
        CuratedStrategy(
            id = "treasure",
            displayName = ThemeId.TREASURE.displayName,
            description = StrategyCatalog.description(ThemeId.TREASURE),
            archetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.COMBO),
            themes = listOf(ThemeId.TREASURE),
            formats = COMMANDER_CASUAL,
        ),
        CuratedStrategy(
            id = "equipment",
            displayName = ThemeId.EQUIPMENT.displayName,
            description = StrategyCatalog.description(ThemeId.EQUIPMENT),
            archetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE),
            themes = listOf(ThemeId.EQUIPMENT),
            formats = COMMANDER_CASUAL_SIXTY,
        ),
        // sixtyOnly (spec §4.2) -- Commander is structurally excluded, not a curation choice.
        // Wave 2 future-debt closeout (2026-09-06): widened to all five new 60-card formats after
        // a WebSearch sanity check (this is the one archetype the task explicitly flagged as
        // worth checking, since "Storm" reads as a narrow/dated shell). Storm turned out to be a
        // real, independently-tracked archetype in every one of them today, not just Standard:
        // Legacy (ANT / The Epic Storm are format-defining, long-running archetypes), Vintage
        // (Doomsday/TES, built around Power Nine fast mana), Pioneer (Temur/Gruul "Possibility
        // Storm", 20+ tracked decks on mtgdecks.net), and even Pauper (Cycling Storm / Gruul Storm,
        // 500+ tracked decks on mtgdecks.net as of Sept 2026, using Manamorphose/Grapeshot-style
        // commons-legal payoffs) -- so no format is excluded here.
        CuratedStrategy(
            id = "storm",
            displayName = ThemeId.STORM.displayName,
            description = StrategyCatalog.description(ThemeId.STORM),
            archetypes = setOf(ArchetypeId.COMBO),
            themes = listOf(ThemeId.STORM),
            formats = setOf(DeckFormat.CASUAL) + ALL_SIXTY_CONSTRUCTED,
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
 * Resolution order (Deck Analysis Engine v3, spec §4.2: "entries declare a SET of compatible
 * archetypes" -- every membership check below is `archetype in it.archetypes`, not equality):
 * 1. Posture-aware exact match (NEW): when [posture] is non-null, an entry whose [archetype] is in
 *    [CuratedStrategy.archetypes], whose [posture] is in [CuratedStrategy.postures], AND whose
 *    theme SET matches exactly. Tried FIRST so a posture-carrying entry (e.g. "Big Mana",
 *    `postures = {RAMP}`) is preferred over a plain archetype/theme match when a real posture was
 *    detected/pinned.
 * 2. Exact match (no posture requirement): a catalog entry whose [CuratedStrategy.archetypes]
 *    contains [archetype] and whose theme SET matches exactly (order-insensitive -- inference's
 *    own ordering is "by descending confidence", not part of a strategy's identity).
 * 3. Fallback: the PURE-archetype catalog entry compatible with [archetype] (an entry with that
 *    archetype in its set and an empty theme list) -- covers "confident archetype, but the
 *    detected theme combination itself isn't curated" (e.g. 2 confident themes that don't form a
 *    single curated preset).
 * 4. No match at all -- returns `null`, the "Custom" sentinel. Applies to a `null` archetype (Deck
 *    Analysis Engine v3 removed `ArchetypeId.GENERIC` -- an ambiguous/unpinned resolution has no
 *    catalog entry to fall back to at all, unlike the old GENERIC "balanced" entry). `null` over a
 *    dedicated marker type: every OTHER call site in this package that resolves an enum/string to a
 *    catalog value uses a plain nullable return for "no match" (see [ThemeId.fromDisplayName]), so
 *    this keeps the same, already-established convention. Callers that need a player-facing
 *    "Custom" label (Phase 3's picker, or [AnalysisEngine.evaluate]'s own displayName fallback)
 *    render that themselves off the `null`.
 *
 * @param format Wave 2 B4 -- optional [DeckFormat] to restrict the search to. When non-null, EVERY
 *   resolution step above only ever considers entries where [CuratedStrategy.availableIn] is true
 *   for [format] -- same fallback-chain SHAPE, just filtered at each step. This closes the gap
 *   where a format-blind exact match could surface a catalog entry the picker doesn't even offer
 *   for that format (e.g. inference detects PRISON on a Standard deck -- "prison" is excluded from
 *   Standard v1 -- the caller must not be handed a strategy the Standard picker can't show; this
 *   degrades to the pure "control" entry instead). Defaults to `null` (no restriction) so every
 *   pre-B4 call site and test keeps its exact prior behavior unchanged. `format` is NOT itself
 *   validated against [archetype]/[themes]; a caller passing an unmatched combination simply gets
 *   whatever the filtered fallback chain resolves to, same as the unfiltered version.
 * @param posture Deck Analysis Engine v3 (spec §3), NEW, appended LAST and defaulted so every
 *   pre-existing call site keeps compiling unchanged -- the second-stage posture classification
 *   (or a user pin), used ONLY to prefer a posture-carrying catalog entry in step 1 above.
 */
fun CuratedStrategyCatalog.nearestFor(
    archetype: ArchetypeId?,
    themes: List<ThemeId>,
    format: DeckFormat? = null,
    posture: PostureId? = null,
): CuratedStrategy? {
    val themeSet = themes.toSet()
    val candidates = if (format == null) ALL else ALL.filter { it.availableIn(format) }

    if (archetype != null && posture != null) {
        val postureExact = candidates.firstOrNull {
            archetype in it.archetypes && posture in it.postures && it.themes.toSet() == themeSet
        }
        if (postureExact != null) return postureExact
    }

    val exact = candidates.firstOrNull { archetype in it.archetypes && it.themes.toSet() == themeSet }
    if (exact != null) return exact
    if (archetype == null) return null
    return candidates.firstOrNull { archetype in it.archetypes && it.themes.isEmpty() }
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
