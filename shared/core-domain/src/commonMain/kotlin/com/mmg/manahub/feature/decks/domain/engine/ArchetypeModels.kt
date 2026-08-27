package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card

// ═══════════════════════════════════════════════════════════════════════════════
//  ArchetypeModels — Deck Doctor Community/Archetype plan, Phase 1.1-1.3
//  (docs/claude-code-prompt-deck-doctor-community.md)
//
//  Pure data model for the archetype-aware skeleton layer. This sits ABOVE the
//  existing [DeckScorer]/[RoleClassifier]/[DeckSkeleton] engine (Phase 3/5/6/8) —
//  it does NOT replace it. [DeckScorer.evaluate] and its `DeckRole` enum stay
//  completely untouched so the GENERIC path remains byte-identical (Phase 1 exit
//  criterion). The archetype layer is consumed ADDITIVELY by
//  [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase] only when a
//  non-GENERIC archetype (or at least one theme) is resolved for the deck.
//
//  All types here are pure `commonMain` — zero Android/AndroidX imports (KMP layering
//  rule). Placement mirrors Phase 0's `DeckDoctorOrchestrator`: the whole Deck Doctor
//  engine already lives in `shared/core-domain` `commonMain`, not `:app`'s
//  `feature/decks/domain/engine` (that package name was inherited when the engine was
//  promoted to the shared module during the KMP migration; see
//  `project_archetype_engine` memory for the placement rationale).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A role key in the archetype vocabulary (Appendix A of the plan document), e.g. `"ramp"`,
 * `"mana_fix"`, `"sac_outlet"`. A plain `String` (not an enum) so new roles can be added to
 * [ArchetypeData] without a code change to every consumer — mirrors [Card]'s `CardTag.key`
 * locale-safe string-keying convention. Distinct from the legacy [DeckRole] enum that
 * [DeckScorer]/[RoleClassifier] still use; see [ArchetypeRoleClassifier] for how the two
 * vocabularies relate.
 */
typealias RoleKey = String

/**
 * A synergy-graph axis key (Deck Analysis Engine v3, spec §5.1-5.2), e.g. `"LIFE"`, `"GRAVEYARD"`,
 * `"TRIBE"`. A plain `String` (not an enum), mirroring [RoleKey]'s own locale-safe string-keying
 * convention. **Derived, never hand-authored**: an axis exists iff at least one [RoleSpec] declares
 * it in [RoleSpec.produces] and at least one declares it in [RoleSpec.consumes] (the two
 * payoff-optional exceptions — `MILL_OPP` and `TRIBE` with a lord present — are called out
 * explicitly in the spec, not inferred). Phase 1 only populates these sets on [RoleSpec]s; the
 * graph that walks them (edges, health, live-axis detection) is Phase 2's [SynergyGraph] (not yet
 * built as of this file).
 */
typealias AxisKey = String

/**
 * The 5 macro archetypes -- Deck Analysis Engine v3 (spec §2) taxonomy, DOWN from the prior 7.
 * `RAMP`/`TEMPO` moved to [PostureId] (a ramp/tempo shell is an ENGINE overlaid on top of a real
 * macro -- "ramp into threats = Midrange, into a combo = Combo, into inevitability = Control" --
 * not a parallel quadrant, and keeping them as macros split the vote in the old 5-way argmax,
 * an undocumented cause of the MIDRANGE bias). `GENERIC` is REMOVED -- a "no confident macro"
 * result is no longer representable as an enum value; [ArchetypeInference] now argmaxes over a
 * REAL prototype distance for all 5 values below and separately flags ambiguity (spec §2.1)
 * rather than falling back to a 6th neutral member. `PRISON` is NEW (spec §2.2) -- a stax
 * skeleton is structurally incompatible with an overlay (it wants 3-5 finishers/3-7 card draw as
 * its BASE, which a theme's `relaxes` cannot express cleanly on top of another macro).
 */
enum class ArchetypeId(val displayName: String) {
    AGGRO("Aggro"),
    MIDRANGE("Midrange"),
    CONTROL("Control"),
    COMBO("Combo"),
    PRISON("Prison"),
}

/**
 * The posture layer (spec §3) -- a delta applied BETWEEN archetype and themes (layer 2.5 in
 * [ArchetypeSkeletonResolver]), reusing the exact same `adds`/`relaxes`/`landsDelta`/`curveDelta`
 * machinery a [ThemeDefinition] already uses. `RAMP`/`TEMPO` are the two ex-macros that moved here;
 * `ATTRITION`/`TOOLBOX`/`VOLTRON`/`GROUP_HUG`/`GROUP_SLUG` are themes that moved here because none
 * of them owns a producer<->payoff synergy axis -- they only reshape the skeleton, which is
 * exactly what a posture is for.
 */
enum class PostureId(val displayName: String) {
    RAMP("Ramp"),
    TEMPO("Tempo"),
    ATTRITION("Attrition"),
    TOOLBOX("Toolbox"),
    VOLTRON("Voltron"),
    GROUP_HUG("Group Hug"),
    GROUP_SLUG("Group Slug"),
}

/**
 * The 21 themes from Deck Analysis Engine v3 (spec §4), DOWN from 22 -- `VOLTRON`/`TOOLBOX`/
 * `GROUP_HUG`/`GROUP_SLUG` moved to [PostureId] (spec §4.1: "none owns a producer<->payoff axis;
 * they only reshape the skeleton") and `STAX` moved to the macro [ArchetypeId.PRISON]. `MILL` split
 * into `MILL_OPPONENT` (the win condition -- milling the OPPONENT) and a retargeted `SELF_MILL`
 * (the enabler for a graveyard payoff -- milling YOURSELF); spec §4.2: "milling yourself and
 * milling the opponent are opposite axes ... merging them into `mill_engine` was a modelling
 * error." `TREASURE`/`EQUIPMENT`/`STORM` are new (spec §4.2); `STORM` is `sixtyOnly` (see
 * [ThemeDefinition.sixtyOnly]). [displayName] is a plain English string (resource-free
 * `commonMain`, mirrors `GameFormat.displayName`/`SeedStrategy.displayName`'s precedent in
 * [DeckEngineModels.kt]).
 */
enum class ThemeId(val displayName: String) {
    REANIMATOR("Reanimator"),
    SELF_MILL("Self-Mill"),
    ARISTOCRATS("Aristocrats"),
    TOKENS("Tokens"),
    SPELLSLINGER("Spellslinger"),
    LANDFALL("Landfall"),
    LIFEGAIN("Lifegain"),
    PLUS1_COUNTERS("+1/+1 Counters"),
    TRIBAL("Tribal"),
    ARTIFACTS("Artifacts"),
    ENCHANTRESS("Enchantress"),
    WHEELS("Wheels"),
    MILL_OPPONENT("Mill"),
    BLINK("Blink"),
    SUPERFRIENDS("Superfriends"),
    VEHICLES("Vehicles"),
    CLONES_THEFT("Clones & Theft"),
    TREASURE("Treasure"),
    EQUIPMENT("Equipment"),
    STORM("Storm"),
    ;

    companion object {
        /**
         * Deck Engine Unification plan (D2): best-effort fuzzy match of a free-form display string
         * (e.g. an EDHREC theme tag from the wizard's Identity-step picker,
         * [com.mmg.manahub.core.model.CommunityAggregate.Commander.themeTags]) onto a [ThemeId] --
         * case-insensitive, matches either direction (substring of / superstring of). Returns `null`
         * on no match, never a guess -- mirrors
         * [com.mmg.manahub.feature.decks.domain.template.DeckTemplateResolver]'s own
         * `matchThemeTags` allowlist convention.
         */
        fun fromDisplayName(raw: String?): ThemeId? {
            val needle = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { theme ->
                val hay = theme.displayName.lowercase()
                hay == needle || needle.contains(hay) || hay.contains(needle)
            }
        }
    }
}

/**
 * The two skeleton shapes Appendix A defines. [COMMANDER] = 99-card singleton (+ the commander,
 * 100 total). [SIXTY] = any 60-card constructed shell — every 60-card [com.mmg.manahub
 * .core.model.DeckFormat] (Standard/Pioneer/Modern/Legacy/Vintage/Pauper/Casual, restored Deck
 * Builder v2 Phase 0) maps onto it; they share the same Appendix-A skeleton shape and differ only
 * by legality, not by archetype layout (see [ArchetypeFormat.of]).
 * [DeckFormat.DRAFT] has no archetype skeleton (Appendix A defines no Draft data) and always
 * resolves to `null` — the archetype layer is a no-op for Draft decks.
 */
enum class ArchetypeFormat {
    COMMANDER, SIXTY;

    companion object {
        /** Maps the engine's [com.mmg.manahub.core.model.DeckFormat] to an [ArchetypeFormat], or
         * `null` when the format has no archetype skeleton (Draft). */
        fun of(format: com.mmg.manahub.core.model.DeckFormat): ArchetypeFormat? = when (format) {
            com.mmg.manahub.core.model.DeckFormat.COMMANDER,
            com.mmg.manahub.core.model.DeckFormat.COMMANDER_CASUAL -> COMMANDER
            com.mmg.manahub.core.model.DeckFormat.STANDARD,
            com.mmg.manahub.core.model.DeckFormat.PIONEER,
            com.mmg.manahub.core.model.DeckFormat.MODERN,
            com.mmg.manahub.core.model.DeckFormat.LEGACY,
            com.mmg.manahub.core.model.DeckFormat.VINTAGE,
            com.mmg.manahub.core.model.DeckFormat.PAUPER,
            com.mmg.manahub.core.model.DeckFormat.CASUAL -> SIXTY
            com.mmg.manahub.core.model.DeckFormat.DRAFT -> null
        }
    }
}

/** Curve shape descriptor from Appendix A.2 (`"front"` / `"bell"` / `"back"`). Advisory only in
 * Phase 1 — not yet consumed by scoring, kept for future curve-shape-aware suggestions. */
enum class CurveShape { FRONT, BELL, BACK }

/**
 * The `[min, ideal, max]` triple every band in Appendix A.2 uses (roles, lands). `min <= ideal
 * <= max` is a structural invariant asserted by the golden skeleton tests (A.4 step "structural"
 * check), not enforced in the constructor (some intermediate compositions — e.g. a bare theme
 * `adds` entry — are validated only after full resolution).
 */
data class RoleTarget(val min: Int, val ideal: Int, val max: Int)

/** The `[min, ideal, max]` curve-average-MV band (Doubles, since avg CMC is fractional). */
data class CurveBand(val min: Double, val ideal: Double, val max: Double)

/**
 * One archetype's per-format template (Appendix A.2 `archetypes.<ARCH>.<fmt>`).
 *
 * @property id `null` ONLY for [ArchetypeData.generic] — the neutral base every [resolve][
 *           com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver.resolve] starts
 *           from before a real macro's overrides apply. Deck Analysis Engine v3 removed
 *           `ArchetypeId.GENERIC` as a pickable macro, but the RESOLVER STILL needs a neutral
 *           starting scaffold to merge a real macro's `roleTargets` onto — `null` is that
 *           scaffold's identity, distinct from (and never confused with) "no macro was resolved"
 *           at the inference layer (see [com.mmg.manahub.feature.decks.domain.usecase
 *           .ArchetypeInference]'s `isAmbiguous` flag for THAT concept instead).
 * @property roleTargets REPLACES (not merges with) the matching GENERIC role band for every key
 *           present here (`roles_override` semantics).
 * @property antiRoles roles this archetype actively does NOT want; resolved to `[0, 0, priorMax]`
 *           by the resolver (A.4 step 5) — the tolerance is the role's own max before the
 *           anti-role rule zeroes its min/ideal, never a hardcoded 0.
 */
data class ArchetypeDefinition(
    val id: ArchetypeId?,
    val format: ArchetypeFormat,
    val lands: RoleTarget,
    val roleTargets: Map<RoleKey, RoleTarget>,
    val antiRoles: Set<RoleKey>,
    val curve: CurveBand,
    val shape: CurveShape,
)

/**
 * One theme's definition (Appendix A.2 `themes.<THEME>`). Themes are Commander-calibrated by
 * default; [sixtyScale] scales `adds`/`relaxes` bands down for a 60-card shell (A.4 step 3).
 *
 * @property adds merged into the base skeleton's roles as MAX-per-bound (A.4: "merge = max per
 *           bound" — a theme never LOWERS an archetype's existing band, only raises it).
 * @property relaxes REPLACES (not merges) the matching role's band — a theme can deliberately
 *           shrink a base archetype's expectation (e.g. VOLTRON relaxing `finisher`).
 * @property landsDelta added to every element of the land band (min/ideal/max shift together).
 * @property curveDelta added to every element of the curve-average-MV band.
 * @property sixtyScale multiplies `adds`/`relaxes` band values for a 60-card resolve (ignored for
 *           Commander, where the scale is always 1.0). Defaults to 0.5 per A.2's convention note
 *           ("sixty_scale multiplies a theme's Commander-calibrated adds for 60-card") for the 3
 *           themes that omit an explicit `sixty_scale` in the JSON annex (GROUP_HUG/GROUP_SLUG/
 *           CLONES_THEFT) — moot in practice since those 3 are [commanderOnly].
 * @property curveExemption a documented A.3 guard (e.g. REANIMATOR's high-MV-creature exemption);
 *           `null` when the theme carries no exemption. Purely advisory in Phase 1's `resolve()` —
 *           consumed by [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase] to
 *           suppress a curve warning, never by the resolver itself.
 * @property commanderOnly themes that only make sense in Commander (CLONES_THEFT — no 60-card
 *           shell exists for a steal-your-opponents'-best-stuff strategy in a 1v1 shell).
 * @property sixtyOnly Deck Analysis Engine v3 (spec §4.2) — the mirror-image flag: a theme that
 *           only makes sense in a 60-card shell (`STORM` — Commander's higher average curve and
 *           lower spell density make a genuine storm-count kill implausible; storm decks are a
 *           60-card-constructed-specific archetype). Never `true` at the same time as
 *           [commanderOnly] on the same entry.
 * @property overlayRoles the subset of THIS theme's [adds] keys that belong to the global A.4
 *           OVERLAY set (payoff-style roles counted at 50% toward the budget cap). Informational —
 *           the canonical set lives in [ArchetypeData.OVERLAY_ROLES]; kept per-theme too so a
 *           theme's own role list is self-documenting.
 */
data class ThemeDefinition(
    val id: ThemeId,
    val adds: Map<RoleKey, RoleTarget>,
    val relaxes: Map<RoleKey, RoleTarget> = emptyMap(),
    val landsDelta: Int = 0,
    val curveDelta: Double = 0.0,
    val sixtyScale: Double = 0.5,
    val curveExemption: CurveExemption? = null,
    val commanderOnly: Boolean = false,
    val sixtyOnly: Boolean = false,
    val overlayRoles: Set<RoleKey> = emptySet(),
)

/**
 * One posture's definition (Deck Analysis Engine v3, spec §3) — deliberately the SAME shape as
 * [ThemeDefinition]'s `adds`/`relaxes`/`landsDelta`/`curveDelta`/`sixtyScale` fields (spec: "reusing
 * the existing adds/relaxes/landsDelta/curveDelta machinery verbatim" — a data change, not a new
 * mechanism). Applied by [ArchetypeSkeletonResolver] as layer 2.5, between the archetype override
 * (layer 2) and themes (layer 3).
 *
 * @property antiRoles spec §3: `TEMPO` "also carries `antiRoles = setOf(\"removal_mass\")`,
 *           inherited from its old macro definition" — the only posture with its own anti-role.
 * @property commanderOnly `GROUP_HUG`/`GROUP_SLUG` (spec §3's `*`) — no 60-card shell exists for a
 *           social/multiplayer-only posture.
 */
data class PostureDefinition(
    val id: PostureId,
    val adds: Map<RoleKey, RoleTarget>,
    val relaxes: Map<RoleKey, RoleTarget> = emptyMap(),
    val landsDelta: Int = 0,
    val curveDelta: Double = 0.0,
    val sixtyScale: Double = 0.5,
    val antiRoles: Set<RoleKey> = emptySet(),
    val commanderOnly: Boolean = false,
)

/**
 * A.3 curve-exemption guards. Each variant documents its own activation condition; the actual
 * gate check (does the deck clear the referenced role minimums) lives in
 * [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase] since it needs the deck's
 * live role counts, which the pure engine layer here does not hold.
 */
enum class CurveExemption {
    /** REANIMATOR: creatures with MV >= 6 are exempt from curve warnings IFF the deck clears
     * BOTH `graveyard_enabler` and `reanimation` minimums (A.3). */
    REANIMATOR_HIGH_MV,
}

/**
 * Per-format, per-color-count modulation table (Appendix A.2 `color_modulation`, A.5 prose).
 * Keyed by color-COUNT bucket: 1, 2, 3, or 4-5 (mono/two/three/four-plus color decks).
 */
data class ColorModulationEntry(val manaFix: RoleTarget, val landsDelta: Int)

/**
 * The fully-resolved skeleton for a (format, archetype, themes[, colorCount]) combination —
 * the output of [ArchetypeSkeletonResolver]. [roleTargets] carries every role band (archetype
 * overrides + theme adds/relaxes merged); [antiRoles] are present as `[0,0,tolerance]` entries
 * INSIDE [roleTargets] too (so callers can iterate ONE map for every role check) but also listed
 * separately here so `evaluate()` can special-case the ">" direction instead of "<".
 */
data class ResolvedArchetypeSkeleton(
    val format: ArchetypeFormat,
    /** `null` ONLY for a bare generic-baseline resolve (no macro override applied) — mirrors
     * [ArchetypeDefinition.id]'s own convention. Every real inference/pin path resolves a
     * non-null value (one of the 5 real macros). */
    val archetype: ArchetypeId?,
    /** Deck Analysis Engine v3 (spec §3) — layer 2.5, applied between [archetype] and [themes].
     * `null` when no posture was detected/pinned (the common case). */
    val posture: PostureId? = null,
    val themes: List<ThemeId>,
    val roleTargets: Map<RoleKey, RoleTarget>,
    val antiRoles: Set<RoleKey>,
    val lands: RoleTarget,
    val curve: CurveBand,
    val shape: CurveShape,
    /** Active curve exemptions from the resolved theme(s) — empty when none apply. */
    val curveExemptions: Set<CurveExemption> = emptySet(),
) {
    /** Convenience: does this skeleton apply any color-source-fixing requirement? */
    fun manaFixTarget(): RoleTarget? = roleTargets[ArchetypeData.MANA_FIX_KEY]

    /**
     * Deck Wizard & Engine Rework plan, WS5.4 -- a player-facing label for this resolved plan
     * (e.g. `"Aggro"`, `"Tokens"`, `"Aggro + Tokens"`), used by [ArchetypeEvaluator] to name the
     * plan inside archetype-aware [DeckWarning] copy ("Aggro wants..." instead of a generic
     * "this plan wants..."). Deck Analysis Engine v3: [ArchetypeId.GENERIC] no longer exists --
     * [archetype] is always a real, specialized macro now, so the archetype part is unconditional;
     * [posture], when present, is inserted between the archetype and its themes.
     */
    fun planLabel(): String {
        val archetypePart = archetype?.displayName
        val posturePart = posture?.displayName
        val themePart = themes.takeIf { it.isNotEmpty() }?.joinToString(" + ") { it.displayName }
        val head = when {
            archetypePart != null && posturePart != null -> "$archetypePart $posturePart"
            archetypePart != null -> archetypePart
            posturePart != null -> posturePart
            else -> null
        }
        return when {
            head != null && themePart != null -> "$head ($themePart)"
            head != null -> head
            themePart != null -> themePart
            else -> "This plan" // defensive -- no archetype/posture/themes never reaches ArchetypeEvaluator
        }
    }
}

/**
 * A single dynamic role definition (Phase 1.1): a [key] in the Appendix A vocabulary, an English
 * [label] (no Android resource — this is `commonMain`), and a [matcher] returning a per-card
 * confidence in `[0,1]` (`0f` = no match). Registered in [ArchetypeRoleClassifier.ROLE_SPECS].
 *
 * Deck Analysis Engine v3, Phase 1 (spec §5.1) adds the synergy-axis fields below, all defaulting
 * to empty so every pre-existing [RoleSpec] construction keeps compiling unchanged:
 *
 * @property produces [AxisKey]s this role feeds AS A PRODUCER (e.g. `token_generator` produces
 *           `"TOKENS"`).
 * @property consumes [AxisKey]s this role feeds AS A PAYOFF (e.g. `death_payoff` consumes
 *           `"DEATH"`). Named `consumes` (not `payoffs`) to mirror the produces/consumes verb pair
 *           the spec itself uses for a directed producer -> consumer graph edge.
 * @property amplifies [AxisKey]s this role modulates without being a producer or payoff on its own
 *           (e.g. `anthem` amplifies `"TOKENS"`/`"COUNTERS"`/`"TRIBE"`/`"ATTACK"`).
 *
 * These sets are populated ONLY for roles this classifier represents as an actual [RoleSpec]
 * instance. The 5 [ArchetypeRoleClassifier.LEGACY_ROLE_MAP] roles (`ramp`, `card_draw`,
 * `removal_spot`, `removal_mass`, `tutor`) are classified via the legacy [RoleClassifier] and never
 * become a [RoleSpec] here, so the spec's axis-table cells that name them (e.g. LANDFALL's
 * "land-based ramp" producer) cannot be attached to anything today — a known Phase 1 gap for
 * Phase 2's [SynergyGraph] to address, not silently dropped.
 */
data class RoleSpec(
    val key: RoleKey,
    val label: String,
    val matcher: (Card) -> Float,
    val produces: Set<AxisKey> = emptySet(),
    val consumes: Set<AxisKey> = emptySet(),
    val amplifies: Set<AxisKey> = emptySet(),
)
