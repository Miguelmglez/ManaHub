package com.mmg.manahub.feature.decks.domain.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan (docs/plans/deck-wizard-rework-plan.md), Workstream 5.1 — the
 * skeleton-differentiation acceptance test for root-cause F3 ("Doctor shows the same warnings
 * regardless of plan"). Written BEFORE the WS5.3 retune, per the plan's own instruction: this test
 * is EXPECTED TO FAIL against the pre-retune [ArchetypeData] (that failure is the acceptance
 * evidence the root cause is real, not a guess) and must PASS once the retune lands. Do not loosen
 * the thresholds below to make it pass artificially — retune the data instead.
 *
 * Resolving a skeleton needs only (format, archetype, themes) — no `Card`/mainboard involved — so
 * this works purely against [ArchetypeSkeletonResolver.resolve] (never `resolveWithColor`; color
 * modulation is WS9's job and would let a pair "pass" for a colorCount reason unrelated to
 * archetype/theme identity). Combos are enumerated from [StrategyCatalog] — the ONE compatibility
 * source of truth (WS1) — never a hand-rolled second list.
 */
class SkeletonDifferentiationTest {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  "Meaningfully different" (WS5.1's own required definition)
    // ═══════════════════════════════════════════════════════════════════════════════
    //
    // A pair of resolved skeletons differs meaningfully when EITHER:
    //  (a) at least [MIN_DIFFERING_ROLE_BANDS] role keys have an `ideal` that differs by >=
    //      [MIN_IDEAL_DELTA] copies. A lone +/-1 nudge is rounding noise from a single scale
    //      factor; >=2 bands moving by >=2 copies each is a real shape change a player building
    //      the deck — or a Doctor warning firing/not firing — would actually notice, OR
    //  (b) the land band's `ideal` differs by >= [MIN_LAND_DELTA]. A theme can legitimately
    //      differentiate ONLY via `landsDelta` (VOLTRON -2, LANDFALL +2) while touching very few
    //      role bands — that is still a real, distinguishable skeleton and must not be lost by
    //      requiring the role-count path alone, OR
    //  (c) the curve band's `ideal` differs by >= [MIN_CURVE_DELTA] (same rationale as (b), for
    //      `curveDelta` — e.g. VOLTRON's -0.4 / STAX's -0.2).
    //
    // `mana_fix` is EXCLUDED from the role-band comparison on purpose: it is colorCount-only
    // modulation applied by `resolveWithColor` (WS9, explicitly out of scope for this run), not a
    // fact about the archetype/theme identity that bare `resolve()` produces — comparing it would
    // let two skeletons "pass" for a reason that has nothing to do with archetype/theme identity.
    private object Threshold {
        const val MIN_DIFFERING_ROLE_BANDS = 2
        const val MIN_IDEAL_DELTA = 2
        const val MIN_LAND_DELTA = 2
        const val MIN_CURVE_DELTA = 0.3
    }

    private fun roleBands(skeleton: ResolvedArchetypeSkeleton): Map<RoleKey, RoleTarget> =
        skeleton.roleTargets.filterKeys { it != ArchetypeData.MANA_FIX_KEY }

    private fun meaningfullyDifferent(a: ResolvedArchetypeSkeleton, b: ResolvedArchetypeSkeleton): Boolean {
        val aRoles = roleBands(a)
        val bRoles = roleBands(b)
        val differingRoleBands = (aRoles.keys + bRoles.keys).count { key ->
            abs((aRoles[key]?.ideal ?: 0) - (bRoles[key]?.ideal ?: 0)) >= Threshold.MIN_IDEAL_DELTA
        }
        if (differingRoleBands >= Threshold.MIN_DIFFERING_ROLE_BANDS) return true
        if (abs(a.lands.ideal - b.lands.ideal) >= Threshold.MIN_LAND_DELTA) return true
        // Epsilon guard: curve deltas are composed of `curveDelta` additions through
        // ArchetypeSkeletonResolver's own `roundTo2` (round-to-nearest-cent Double arithmetic), so
        // a value that is EXACTLY the threshold on paper (e.g. a +0.3 curveDelta against a 3.2
        // ideal) can land a hair under it in IEEE 754 (3.4999999999999996 vs 3.5) purely from
        // binary floating-point representation -- not a real data difference. Compare with a small
        // tolerance so the intent ("differs by >= 0.3") isn't defeated by representation noise.
        if (abs(a.curve.ideal - b.curve.ideal) >= Threshold.MIN_CURVE_DELTA - CURVE_EPSILON) return true
        return false
    }

    private companion object {
        const val CURVE_EPSILON = 1e-9
    }

    private fun diffSummary(a: ResolvedArchetypeSkeleton, b: ResolvedArchetypeSkeleton): String {
        val aRoles = roleBands(a)
        val bRoles = roleBands(b)
        val roleDeltas = (aRoles.keys + bRoles.keys).associateWith { key ->
            (aRoles[key]?.ideal ?: 0) - (bRoles[key]?.ideal ?: 0)
        }.filterValues { it != 0 }
        return "roleDeltas=$roleDeltas landsIdeal=${a.lands.ideal}/${b.lands.ideal} curveIdeal=${a.curve.ideal}/${b.curve.ideal}"
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  Combo enumeration — StrategyCatalog is the single compatibility source (WS1)
    // ═══════════════════════════════════════════════════════════════════════════════

    /** A theme is only ever offered in Commander when [ThemeDefinition.commanderOnly] — `resolve()`
     * itself has no such gate, but the wizard/Doctor pickers never construct this combo for a
     * 60-card build, so exercising it here would test something production never builds. */
    private fun ArchetypeFormat.supports(theme: ThemeId): Boolean =
        this == ArchetypeFormat.COMMANDER || !ArchetypeData.THEMES.getValue(theme).commanderOnly

    private fun resolveCombo(format: ArchetypeFormat, archetype: ArchetypeId, theme: ThemeId? = null) =
        ArchetypeSkeletonResolver.resolve(format, archetype, listOfNotNull(theme))

    private fun label(archetype: ArchetypeId, theme: ThemeId? = null) =
        if (theme == null) archetype.displayName else "${archetype.displayName}+${theme.displayName}"

    private fun printMatrix(format: ArchetypeFormat) {
        println("── Skeleton matrix ($format) ──")
        ArchetypeId.entries.forEach { archetype ->
            val s = resolveCombo(format, archetype)
            println("  ${label(archetype).padEnd(24)} lands=${s.lands} curve=${s.curve}")
            if (archetype != ArchetypeId.GENERIC) {
                StrategyCatalog.compatibleThemes(archetype).forEach { theme ->
                    if (!format.supports(theme)) return@forEach
                    val ts = resolveCombo(format, archetype, theme)
                    println("    +${theme.displayName.padEnd(20)} lands=${ts.lands} curve=${ts.curve}")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  Group 1 — every archetype alone must differ from every other archetype (F3's core
    //  complaint: "every plan demands ramp/board-wipes/card-draw at similar levels").
    // ═══════════════════════════════════════════════════════════════════════════════

    private fun assertArchetypesDifferentiate(format: ArchetypeFormat) {
        val archetypes = ArchetypeId.entries
        val resolved = archetypes.associateWith { resolveCombo(format, it) }
        val failures = mutableListOf<String>()
        for (i in archetypes.indices) {
            for (j in i + 1 until archetypes.size) {
                val a = archetypes[i]
                val b = archetypes[j]
                if (!meaningfullyDifferent(resolved.getValue(a), resolved.getValue(b))) {
                    failures += "${label(a)} vs ${label(b)}: ${diffSummary(resolved.getValue(a), resolved.getValue(b))}"
                }
            }
        }
        assertTrue(
            failures.isEmpty(),
            "$format: archetypes that do NOT differ meaningfully:\n${failures.joinToString("\n")}",
        )
    }

    @Test
    fun commanderArchetypesDifferentiateFromEachOther() = assertArchetypesDifferentiate(ArchetypeFormat.COMMANDER)

    @Test
    fun sixtyCardArchetypesDifferentiateFromEachOther() = assertArchetypesDifferentiate(ArchetypeFormat.SIXTY)

    // ═══════════════════════════════════════════════════════════════════════════════
    //  Group 2 — picking a theme must visibly change the skeleton vs. the bare archetype.
    // ═══════════════════════════════════════════════════════════════════════════════

    private fun assertThemeAddsVisibleEffect(format: ArchetypeFormat) {
        val failures = mutableListOf<String>()
        ArchetypeId.entries.filter { it != ArchetypeId.GENERIC }.forEach { archetype ->
            val bare = resolveCombo(format, archetype)
            StrategyCatalog.compatibleThemes(archetype).forEach { theme ->
                if (!format.supports(theme)) return@forEach
                val withTheme = resolveCombo(format, archetype, theme)
                if (!meaningfullyDifferent(bare, withTheme)) {
                    failures += "${label(archetype, theme)} vs bare ${label(archetype)}: ${diffSummary(bare, withTheme)}"
                }
            }
        }
        assertTrue(
            failures.isEmpty(),
            "$format: theme picks with NO meaningful effect vs. the bare archetype:\n${failures.joinToString("\n")}",
        )
    }

    @Test
    fun commanderThemesVisiblyChangeTheBareArchetype() = assertThemeAddsVisibleEffect(ArchetypeFormat.COMMANDER)

    @Test
    fun sixtyCardThemesVisiblyChangeTheBareArchetype() = assertThemeAddsVisibleEffect(ArchetypeFormat.SIXTY)

    // ═══════════════════════════════════════════════════════════════════════════════
    //  Group 3 — every pair of themes compatible with the SAME archetype must differ from
    //  each other (themes must differentiate from each other, not just from "no theme").
    // ═══════════════════════════════════════════════════════════════════════════════

    private fun assertThemesDifferentiateWithinArchetype(format: ArchetypeFormat) {
        val failures = mutableListOf<String>()
        ArchetypeId.entries.filter { it != ArchetypeId.GENERIC }.forEach { archetype ->
            val themes = StrategyCatalog.compatibleThemes(archetype).filter { format.supports(it) }.toList()
            val resolved = themes.associateWith { resolveCombo(format, archetype, it) }
            for (i in themes.indices) {
                for (j in i + 1 until themes.size) {
                    val ta = themes[i]
                    val tb = themes[j]
                    if (!meaningfullyDifferent(resolved.getValue(ta), resolved.getValue(tb))) {
                        failures += "${label(archetype, ta)} vs ${label(archetype, tb)}: " +
                            diffSummary(resolved.getValue(ta), resolved.getValue(tb))
                    }
                }
            }
        }
        assertTrue(
            failures.isEmpty(),
            "$format: same-archetype theme pairs that do NOT differ meaningfully:\n${failures.joinToString("\n")}",
        )
    }

    @Test
    fun commanderThemesDifferentiateWithinTheSameArchetype() = assertThemesDifferentiateWithinArchetype(ArchetypeFormat.COMMANDER)

    @Test
    fun sixtyCardThemesDifferentiateWithinTheSameArchetype() = assertThemesDifferentiateWithinArchetype(ArchetypeFormat.SIXTY)

    // ═══════════════════════════════════════════════════════════════════════════════
    //  Group 4 — for a FIXED theme, every pair of its compatible archetypes must still
    //  differ (the archetype must keep mattering even once the theme is picked).
    // ═══════════════════════════════════════════════════════════════════════════════

    private fun assertArchetypesDifferentiateWithinTheme(format: ArchetypeFormat) {
        val failures = mutableListOf<String>()
        ThemeId.entries.forEach { theme ->
            if (!format.supports(theme)) return@forEach
            val archetypes = StrategyCatalog.compatibleArchetypes(theme).toList()
            if (archetypes.size < 2) return@forEach
            val resolved = archetypes.associateWith { resolveCombo(format, it, theme) }
            for (i in archetypes.indices) {
                for (j in i + 1 until archetypes.size) {
                    val aa = archetypes[i]
                    val ab = archetypes[j]
                    if (!meaningfullyDifferent(resolved.getValue(aa), resolved.getValue(ab))) {
                        failures += "${label(aa, theme)} vs ${label(ab, theme)}: " +
                            diffSummary(resolved.getValue(aa), resolved.getValue(ab))
                    }
                }
            }
        }
        assertTrue(
            failures.isEmpty(),
            "$format: same-theme archetype pairs that do NOT differ meaningfully:\n${failures.joinToString("\n")}",
        )
    }

    @Test
    fun commanderArchetypesDifferentiateWithinTheSameTheme() = assertArchetypesDifferentiateWithinTheme(ArchetypeFormat.COMMANDER)

    @Test
    fun sixtyCardArchetypesDifferentiateWithinTheSameTheme() = assertArchetypesDifferentiateWithinTheme(ArchetypeFormat.SIXTY)

    // ═══════════════════════════════════════════════════════════════════════════════
    //  Visibility: print the full matrix once (not an assertion — a human-readable report,
    //  per WS5.1's "output a matrix report" requirement).
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    fun printSkeletonMatrixForBothFormats() {
        ArchetypeFormat.entries.forEach { printMatrix(it) }
    }
}
