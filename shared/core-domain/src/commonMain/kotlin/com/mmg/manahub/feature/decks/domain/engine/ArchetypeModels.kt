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

/** The 7 macro archetypes from Appendix A.2 (plus GENERIC, the "no specific archetype" default). */
enum class ArchetypeId(val displayName: String) {
    GENERIC("Generic"),
    AGGRO("Aggro"),
    MIDRANGE("Midrange"),
    CONTROL("Control"),
    TEMPO("Tempo"),
    COMBO("Combo"),
    RAMP("Ramp");

    /** True for every archetype except the neutral default. */
    val isSpecialized: Boolean get() = this != GENERIC
}

/**
 * The 22 themes from Appendix A.2, layered on top of a macro [ArchetypeId]. [displayName] is a
 * plain English string (resource-free `commonMain`, mirrors `GameFormat.displayName`/
 * `SeedStrategy.displayName`'s precedent in [DeckEngineModels.kt]).
 */
enum class ThemeId(val displayName: String) {
    REANIMATOR("Reanimator"),
    SELF_MILL("Self-Mill"),
    ARISTOCRATS("Aristocrats"),
    TOKENS("Tokens"),
    SPELLSLINGER("Spellslinger"),
    VOLTRON("Voltron"),
    STAX("Stax"),
    LANDFALL("Landfall"),
    LIFEGAIN("Lifegain"),
    PLUS1_COUNTERS("+1/+1 Counters"),
    TRIBAL("Tribal"),
    ARTIFACTS("Artifacts"),
    ENCHANTRESS("Enchantress"),
    WHEELS("Wheels"),
    MILL("Mill"),
    GROUP_HUG("Group Hug"),
    GROUP_SLUG("Group Slug"),
    BLINK("Blink"),
    SUPERFRIENDS("Superfriends"),
    VEHICLES("Vehicles"),
    TOOLBOX("Toolbox"),
    CLONES_THEFT("Clones & Theft"),
}

/**
 * The two skeleton shapes Appendix A defines. [COMMANDER] = 99-card singleton (+ the commander,
 * 100 total). [SIXTY] = any 60-card constructed shell — in THIS build only [com.mmg.manahub
 * .core.model.DeckFormat.CASUAL] is a live 60-card format (Standard/Pioneer/Modern/Legacy/
 * Vintage/Pauper are commented out for the current release scope; see [ArchetypeFormat.of]).
 * [DeckFormat.DRAFT] has no archetype skeleton (Appendix A defines no Draft data) and always
 * resolves to `null` — the archetype layer is a no-op for Draft decks.
 */
enum class ArchetypeFormat {
    COMMANDER, SIXTY;

    companion object {
        /** Maps the engine's [com.mmg.manahub.core.model.DeckFormat] to an [ArchetypeFormat], or
         * `null` when the format has no archetype skeleton (Draft). */
        fun of(format: com.mmg.manahub.core.model.DeckFormat): ArchetypeFormat? = when (format) {
            com.mmg.manahub.core.model.DeckFormat.COMMANDER -> COMMANDER
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
 * @property roleTargets REPLACES (not merges with) the matching GENERIC role band for every key
 *           present here (`roles_override` semantics).
 * @property antiRoles roles this archetype actively does NOT want; resolved to `[0, 0, priorMax]`
 *           by the resolver (A.4 step 5) — the tolerance is the role's own max before the
 *           anti-role rule zeroes its min/ideal, never a hardcoded 0.
 */
data class ArchetypeDefinition(
    val id: ArchetypeId,
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
 * @property commanderOnly themes that only make sense in Commander (GROUP_HUG/GROUP_SLUG/
 *           CLONES_THEFT — no 60-card shell exists for social/multiplayer-only strategies).
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
    val overlayRoles: Set<RoleKey> = emptySet(),
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
    val archetype: ArchetypeId,
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
}

/**
 * A single dynamic role definition (Phase 1.1): a [key] in the Appendix A vocabulary, an English
 * [label] (no Android resource — this is `commonMain`), and a [matcher] returning a per-card
 * confidence in `[0,1]` (`0f` = no match). Registered in [ArchetypeRoleClassifier.ROLE_SPECS].
 */
data class RoleSpec(
    val key: RoleKey,
    val label: String,
    val matcher: (Card) -> Float,
)
