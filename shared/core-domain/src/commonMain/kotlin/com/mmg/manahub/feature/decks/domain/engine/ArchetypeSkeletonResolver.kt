package com.mmg.manahub.feature.decks.domain.engine

import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  ArchetypeSkeletonResolver — Deck Doctor Community/Archetype plan, Phase 1.3
//
//  [resolve] is a LINE-FOR-LINE port of Appendix B's validated Python reference
//  resolver (docs/claude-code-prompt-deck-doctor-community.md, "Reference resolver
//  + evaluator + the 9 fixture vectors"). It is the function the golden skeleton
//  tests (Phase 1.8) pin down structurally/budget-wise — do NOT change its
//  semantics without re-running (and keeping green) that whole suite.
//
//  [resolveWithColor] is the PRODUCTION entry point (used by real deck wiring,
//  1.6): it calls [resolve] and then layers A.5 color modulation on top (mana_fix
//  role target + a land-band shift), exactly as A.4 step 4 / A.5 prose describe.
//  Appendix B's own Python `resolve()` does NOT apply color modulation to the
//  land band (only `evaluate()` checks the bare `mana_fix` minimum) — see this
//  file's KDoc on [resolveWithColor] and `project_archetype_engine` memory for the
//  documented reconciliation between the annex's prose (A.4/A.5) and its actual
//  validated code (Appendix B).
//
//  Rounding note: Python's `round()` is banker's-rounding (round-half-to-even);
//  this port uses standard round-half-up (`kotlin.math.roundToInt`). None of the
//  annex's actual scale factors (0.4/0.5/0.55/0.6/0.75 and their 2-theme *0.75
//  combinations) land exactly on a `.5` tie against any transcribed band value, so
//  this is a documented, inert deviation — see `project_archetype_engine` memory.
// ═══════════════════════════════════════════════════════════════════════════════

object ArchetypeSkeletonResolver {

    /**
     * Composes the skeleton for (format, archetype, themes) — Appendix A.4 steps 1-3 + 5
     * (color modulation, step 4, is NOT applied here; see [resolveWithColor]).
     *
     * Algorithm (verbatim from the Python reference):
     *  1. Start from [ArchetypeData.generic] for [format].
     *  2. If [archetype] is specialized (`!= GENERIC`), its `roleTargets` REPLACE the matching
     *     generic role bands; its own `lands`/`curve`/`antiRoles` replace the generic ones wholesale.
     *  3. For each theme (max 2 enforced by the caller; when 2 themes are present, EVERY theme's
     *     `adds`/`relaxes` bands are additionally scaled by 0.75 — the annex's 2-theme dilution
     *     factor): scale by [ThemeDefinition.sixtyScale] for [ArchetypeFormat.SIXTY] (1.0 for
     *     Commander), merge `adds` as MAX-per-bound into the roles map, REPLACE with `relaxes`,
     *     shift `lands`/`curve` by the theme's deltas.
     *  5. Resolve every anti-role to `[0, 0, priorMax]` (the tolerance is whatever max the role
     *     had BEFORE the anti-role rule fires — never a hardcoded 0).
     */
    fun resolve(
        format: ArchetypeFormat,
        archetype: ArchetypeId = ArchetypeId.GENERIC,
        themes: List<ThemeId> = emptyList(),
    ): ResolvedArchetypeSkeleton {
        val generic = ArchetypeData.generic(format)
        var roles: Map<RoleKey, RoleTarget> = generic.roleTargets
        var lands = generic.lands
        var curve = generic.curve
        var antiRoles: Set<RoleKey> = emptySet()
        var shape = generic.shape

        if (archetype.isSpecialized) {
            val a = ArchetypeData.ARCHETYPES[archetype]?.get(format)
                ?: error("No ArchetypeDefinition for $archetype/$format")
            // roles_override REPLACES the matching generic band; keys the archetype does not
            // mention keep their GENERIC band (A.4 step 2 "roles_override REPLACES").
            roles = roles + a.roleTargets
            lands = a.lands
            curve = a.curve
            antiRoles = a.antiRoles
            shape = a.shape
        }

        val multi = if (themes.size > 1) 0.75 else 1.0
        val curveExemptions = mutableSetOf<CurveExemption>()
        themes.forEach { themeId ->
            val theme = ArchetypeData.THEMES.getValue(themeId)
            val scale = (if (format == ArchetypeFormat.COMMANDER) 1.0 else theme.sixtyScale) * multi

            val mutableRoles = roles.toMutableMap()
            theme.adds.forEach { (key, band) ->
                val scaled = band.scaledBy(scale)
                val existing = mutableRoles[key]
                mutableRoles[key] = if (existing != null) {
                    // merge = MAX per bound (A.4: a theme never lowers an existing expectation).
                    RoleTarget(
                        min = maxOf(existing.min, scaled.min),
                        ideal = maxOf(existing.ideal, scaled.ideal),
                        max = maxOf(existing.max, scaled.max),
                    )
                } else {
                    scaled
                }
            }
            theme.relaxes.forEach { (key, band) -> mutableRoles[key] = band.scaledBy(scale) }
            roles = mutableRoles

            if (theme.landsDelta != 0) {
                lands = RoleTarget(lands.min + theme.landsDelta, lands.ideal + theme.landsDelta, lands.max + theme.landsDelta)
            }
            if (theme.curveDelta != 0.0) {
                curve = CurveBand(
                    roundTo2(curve.min + theme.curveDelta),
                    roundTo2(curve.ideal + theme.curveDelta),
                    roundTo2(curve.max + theme.curveDelta),
                )
            }
            theme.curveExemption?.let { curveExemptions += it }
        }

        // Step 5 — anti-roles resolve to [0, 0, priorMax]; tolerance = whatever max the role had
        // (from generic/archetype/theme composition), defaulting to 1 if the role was never set.
        if (antiRoles.isNotEmpty()) {
            val mutableRoles = roles.toMutableMap()
            antiRoles.forEach { key ->
                val tolerance = mutableRoles[key]?.max ?: 1
                mutableRoles[key] = RoleTarget(0, 0, tolerance)
            }
            roles = mutableRoles
        }

        return ResolvedArchetypeSkeleton(
            format = format,
            archetype = archetype,
            themes = themes,
            roleTargets = roles,
            antiRoles = antiRoles,
            lands = lands,
            curve = curve,
            shape = shape,
            curveExemptions = curveExemptions,
        )
    }

    /**
     * Production entry point (1.6 wiring): [resolve] plus A.5 color modulation — sets the
     * `mana_fix` role target from [ArchetypeData.COLOR_MODULATION] and shifts the land band by
     * the modulation's `landsDelta`. `colorCount <= 0` (unknown identity) skips modulation
     * entirely and returns the unmodulated [resolve] result (fail-closed per D14 is enforced by
     * the CALLER's suggestibility filter, not here — this function only shapes the skeleton).
     */
    fun resolveWithColor(
        format: ArchetypeFormat,
        archetype: ArchetypeId = ArchetypeId.GENERIC,
        themes: List<ThemeId> = emptyList(),
        colorCount: Int,
    ): ResolvedArchetypeSkeleton {
        val base = resolve(format, archetype, themes)
        if (colorCount <= 0) return base
        val bucket = ArchetypeData.colorCountBucket(colorCount)
        val modulation = ArchetypeData.COLOR_MODULATION.getValue(format).getValue(bucket)
        val lands = if (modulation.landsDelta == 0) base.lands else RoleTarget(
            base.lands.min + modulation.landsDelta,
            base.lands.ideal + modulation.landsDelta,
            base.lands.max + modulation.landsDelta,
        )
        return base.copy(
            roleTargets = base.roleTargets + (ArchetypeData.MANA_FIX_KEY to modulation.manaFix),
            lands = lands,
        )
    }

    private fun RoleTarget.scaledBy(scale: Double): RoleTarget = RoleTarget(
        min = (min * scale).roundToInt(),
        ideal = (ideal * scale).roundToInt(),
        max = (max * scale).roundToInt(),
    )

    private fun roundTo2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
