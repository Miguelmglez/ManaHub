package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
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
//  1.6): it calls [resolve] and then layers A.5 color-COUNT modulation on top (mana_fix
//  role target + a land-band shift), exactly as A.4 step 4 / A.5 prose describe.
//  Appendix B's own Python `resolve()` does NOT apply color modulation to the
//  land band (only `evaluate()` checks the bare `mana_fix` minimum) — see this
//  file's KDoc on [resolveWithColor] and `project_archetype_engine` memory for the
//  documented reconciliation between the annex's prose (A.4/A.5) and its actual
//  validated code (Appendix B).
//
//  Deck Wizard & Engine Rework plan WS9.2 (2026-07-28): [resolveWithColor] ALSO layers a
//  color-IDENTITY-aware pass on top of the color-count modulation — see [applyIdentityModulation]
//  below. This is a SEPARATE step from color-count modulation: count-based modulation shapes
//  `mana_fix`/lands from raw color COUNT; identity modulation reshapes the INTERACTION roles
//  (counterspell/removal_spot/removal_mass/protection/recursion) from which SPECIFIC colors are
//  in the identity — a 2-color G/W deck and a 2-color U/B deck get the same mana_fix band but
//  very different interaction-role feasibility. [resolveWithColorCount] is kept as the
//  identity-unaware entry point (count-only, pre-WS9.2 behavior) for callers/tests that
//  specifically want to isolate the color-count layer from the identity layer.
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
     * Color-COUNT-only entry point (pre-WS9.2 behavior): [resolve] plus A.5 color-count
     * modulation — sets the `mana_fix` role target from [ArchetypeData.COLOR_MODULATION] and
     * shifts the land band by the modulation's `landsDelta`. `colorCount <= 0` (unknown identity)
     * skips modulation entirely and returns the unmodulated [resolve] result (fail-closed per D14
     * is enforced by the CALLER's suggestibility filter, not here — this function only shapes the
     * skeleton). Does NOT apply WS9.2's identity-aware pass — kept public specifically so tests
     * (and any future caller that only knows a raw count, not a real identity set) can isolate
     * the color-count layer, and so WS9.2's own tests have a clean "before" comparison point.
     */
    fun resolveWithColorCount(
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

    /**
     * Production entry point (1.6 wiring, extended by WS9.2 and Wave 2 B2): [resolveWithColorCount]
     * plus a color-IDENTITY-aware pass ([applyIdentityModulation]). `identity`'s non-colorless count
     * drives the SAME color-count modulation [resolveWithColorCount] always did; when that count
     * is `> 0` (a real, at-least-mono-colored identity — the same fail-closed gate the count-only
     * function already used), the identity's SPECIFIC colors additionally reshape the
     * interaction-role bands via [ColorRoleAffinity]. An empty or fully-colorless `identity`
     * (count `<= 0`, e.g. unknown/unresolved) is a strict no-op beyond color-count modulation —
     * byte-identical to pre-WS9.2 behavior, matching [resolveWithColorCount]'s own fail-closed
     * convention.
     *
     * [deckFormat] (Wave 2 B2) is an OPTIONAL, LAST-appended param: when non-null and [format] is
     * [ArchetypeFormat.SIXTY], [SixtyFormatProfile.forDeckFormat] is applied as the final layering
     * step (see [applyFormatProfile]). `null` (every pre-existing call site) or a non-SIXTY
     * [format] (Commander) is a strict no-op, matching every other fail-closed convention in this
     * file.
     */
    fun resolveWithColor(
        format: ArchetypeFormat,
        archetype: ArchetypeId = ArchetypeId.GENERIC,
        themes: List<ThemeId> = emptyList(),
        identity: Set<ManaColor>,
        deckFormat: DeckFormat? = null,
    ): ResolvedArchetypeSkeleton {
        val colorCount = identity.count { it != ManaColor.C }
        val withColorCount = resolveWithColorCount(format, archetype, themes, colorCount)
        val withIdentity = if (colorCount <= 0) withColorCount else applyIdentityModulation(withColorCount, identity)
        return applyFormatProfile(withIdentity, format, deckFormat)
    }

    /**
     * Wave 2 B2 — the LAST layering step of [resolveWithColor]: shifts [skeleton]'s `lands`/`curve`
     * bands by [SixtyFormatProfile.landsDelta]/[SixtyFormatProfile.curveDelta] for [deckFormat],
     * but only when [format] is [ArchetypeFormat.SIXTY] (Commander has no [DeckFormat]-level
     * variation — see [ArchetypeFormat.of]). `deckFormat == null` (every legacy call site) or an
     * all-zero profile (every [DeckFormat] besides STANDARD, this wave) is a no-op that returns
     * [skeleton] unchanged, avoiding an unnecessary `.copy()` on the hot path.
     */
    private fun applyFormatProfile(
        skeleton: ResolvedArchetypeSkeleton,
        format: ArchetypeFormat,
        deckFormat: DeckFormat?,
    ): ResolvedArchetypeSkeleton {
        if (deckFormat == null || format != ArchetypeFormat.SIXTY) return skeleton
        val profile = SixtyFormatProfile.forDeckFormat(deckFormat)
        if (profile.landsDelta == 0 && profile.curveDelta == 0.0) return skeleton

        val lands = if (profile.landsDelta == 0) skeleton.lands else RoleTarget(
            skeleton.lands.min + profile.landsDelta,
            skeleton.lands.ideal + profile.landsDelta,
            skeleton.lands.max + profile.landsDelta,
        )
        val curve = if (profile.curveDelta == 0.0) skeleton.curve else CurveBand(
            roundTo2(skeleton.curve.min + profile.curveDelta),
            roundTo2(skeleton.curve.ideal + profile.curveDelta),
            roundTo2(skeleton.curve.max + profile.curveDelta),
        )
        return skeleton.copy(lands = lands, curve = curve)
    }

    /**
     * WS9.2 — reshapes [skeleton]'s roles so that a warning demanding a role [identity] cannot
     * supply becomes impossible by construction, per [ColorRoleAffinity]:
     *  - A genuinely-demanded (`min > 0`), TABLED role that is NOT [ColorRoleAffinity.isFeasible]
     *    for [identity] is relaxed to `[0, 0, priorMax]` (same tolerance convention as the
     *    anti-role rule in [resolve]'s step 5 — the tolerance is the role's own prior max, never a
     *    hardcoded 0), and its prior `ideal` is redistributed onto [ColorRoleAffinity
     *    .INTERACTION_ROLES] members that ARE feasible for [identity] AND already present in the
     *    skeleton (never fabricating a brand-new role key). The redistribution is split evenly
     *    (remainder biased to the first recipients), UP TO [REDISTRIBUTION_GROWTH_CAP_MULTIPLIER]
     *    times each recipient's OWN pre-redistribution `max` (edge-case audit Fix 1, 2026-07-28) —
     *    without this cap a single surviving recipient (e.g. a mono-color identity's only feasible
     *    interaction role) could absorb an ENTIRE archetype's worth of zeroed demand and inflate to
     *    an implausible peak (a real repro: Commander CONTROL, mono-Green — `removal_mass`(ideal 7)
     *    and `counterspell`(ideal 9) both zero out, and `recursion` — prior ideal 2 — used to absorb
     *    the full 16 and become ideal=18, a 9x blowup that then drove a bogus "Control wants 18
     *    Recursion" Suggestions-tab prompt). The interaction-role-subset ideal sum is preserved ONLY
     *    while every recipient has headroom under its own cap; any amount that would push a
     *    recipient past its cap is honestly DROPPED rather than fabricated onto an already-full
     *    role — per the plan's own framing, "spreading a shortfall thinner (or not redistributing
     *    it at all) beats fabricating an implausible peak on one role." [REDISTRIBUTION_GROWTH_CAP_MULTIPLIER]
     *    is a documented judgment call (not derived from an external source), chosen so the existing,
     *    ALREADY-ACCEPTED mono-Green MIDRANGE case (a single recipient absorbing a moderate +6 onto a
     *    prior max of 7) stays completely unaffected, while the CONTROL mono-Green case above is
     *    reined in to a sane multiple of its own prior ceiling instead of an arbitrary multiple of it.
     *  - A role that is [ColorRoleAffinity.isSubstituteOnly] (reduced but real access) has its
     *    `ideal`/`max` scaled down by [SUBSTITUTE_FACTOR] (min unchanged) — never zeroed, never
     *    redistributed elsewhere.
     *  - An untabled role (no [ColorRoleAffinity] judgement recorded) or a role whose `min <= 0`
     *    (already relaxed/an anti-role) is left completely untouched.
     *  - `mana_fix` is excluded — that key is [ColorRoleAffinity]-agnostic, entirely owned by the
     *    color-COUNT modulation layer.
     *
     * If [identity] has no feasible recipients at all for a zeroed role's redistribution (e.g. a
     * genuinely all-colorless-beyond-basics identity with no real color-pie access to any
     * interaction role), the demand is simply dropped rather than fabricated onto an infeasible
     * role — a documented, narrow edge case (see WS9 batch report).
     */
    private fun applyIdentityModulation(
        skeleton: ResolvedArchetypeSkeleton,
        identity: Set<ManaColor>,
    ): ResolvedArchetypeSkeleton {
        val toRelax = mutableListOf<Pair<RoleKey, RoleTarget>>()
        val toReduce = mutableListOf<Pair<RoleKey, RoleTarget>>()

        skeleton.roleTargets.forEach { (key, band) ->
            if (key == ArchetypeData.MANA_FIX_KEY) return@forEach
            if (band.min <= 0) return@forEach // already relaxed / an anti-role -- never touch
            if (!ColorRoleAffinity.isTabled(key)) return@forEach // no color-pie judgement recorded

            when {
                ColorRoleAffinity.isFeasible(identity, key) -> Unit // fully accessible, no change
                ColorRoleAffinity.isSubstituteOnly(identity, key) -> toReduce += key to band
                else -> toRelax += key to band
            }
        }

        if (toRelax.isEmpty() && toReduce.isEmpty()) return skeleton

        val mutableRoles = skeleton.roleTargets.toMutableMap()

        toReduce.forEach { (key, band) ->
            val reducedIdeal = (band.ideal * SUBSTITUTE_FACTOR).roundToInt().coerceAtLeast(band.min)
            val reducedMax = maxOf(reducedIdeal, (band.max * SUBSTITUTE_FACTOR).roundToInt())
            mutableRoles[key] = RoleTarget(band.min, reducedIdeal, reducedMax)
        }

        var redistributable = 0
        toRelax.forEach { (key, band) ->
            mutableRoles[key] = RoleTarget(0, 0, band.max)
            redistributable += band.ideal
        }

        if (redistributable > 0) {
            val zeroedKeys = toRelax.map { it.first }.toSet()
            val recipients = ColorRoleAffinity.INTERACTION_ROLES
                .filter { it !in zeroedKeys }
                .filter { key -> mutableRoles.containsKey(key) && ColorRoleAffinity.isFeasible(identity, key) }

            // else (recipients.isEmpty()): identity has zero feasible interaction recipients (e.g.
            // fully colorless) -- the demand is dropped, never fabricated onto an infeasible role.
            distributeRedistributableCapped(redistributable, recipients, mutableRoles)
        }

        return skeleton.copy(roleTargets = mutableRoles)
    }

    /**
     * Fix 1 (edge-case audit, 2026-07-28) — spreads [redistributable] across [recipients] the SAME
     * even-split-with-remainder-bias way the uncapped version did, except no single recipient's
     * `ideal` is ever allowed to grow past [REDISTRIBUTION_GROWTH_CAP_MULTIPLIER] times its OWN
     * pre-redistribution `max`. Water-fills round-by-round: each round splits whatever remains
     * evenly across recipients that still have headroom, clamping each recipient's share to its
     * remaining headroom; a recipient that fills its cap drops out of later rounds. Terminates in at
     * most `recipients.size` rounds (every round either fully distributes the remainder or removes
     * at least one recipient from contention). Any amount that cannot fit under ANY recipient's cap
     * is silently dropped -- never fabricated onto an already-full role, matching the anti-role
     * convention elsewhere in this file (drop rather than lie about a demand nothing can satisfy).
     */
    private fun distributeRedistributableCapped(
        redistributable: Int,
        recipients: List<RoleKey>,
        mutableRoles: MutableMap<RoleKey, RoleTarget>,
    ) {
        if (recipients.isEmpty() || redistributable <= 0) return

        // Headroom is computed against each recipient's ORIGINAL band (nothing here has been
        // touched yet -- SUBSTITUTE reductions run before this call and are never members of
        // `recipients`, see the isFeasible-only filter above).
        val headroom = recipients.associateWith { key ->
            val band = mutableRoles.getValue(key)
            (((band.max * REDISTRIBUTION_GROWTH_CAP_MULTIPLIER).roundToInt()) - band.ideal).coerceAtLeast(0)
        }.toMutableMap()
        val allocated = recipients.associateWith { 0 }.toMutableMap()

        var remaining = redistributable
        var active = recipients.filter { headroom.getValue(it) > 0 }
        while (remaining > 0 && active.isNotEmpty()) {
            val share = remaining / active.size
            val remainder = remaining % active.size
            var distributedThisRound = 0
            if (share > 0) {
                active.forEachIndexed { index, key ->
                    val want = share + if (index < remainder) 1 else 0
                    val give = minOf(want, headroom.getValue(key))
                    if (give > 0) {
                        allocated[key] = allocated.getValue(key) + give
                        headroom[key] = headroom.getValue(key) - give
                        distributedThisRound += give
                    }
                }
            } else {
                // Fewer than one full share per active recipient left -- hand out single units,
                // first-recipients-biased (same convention as the even split above).
                for (key in active) {
                    if (distributedThisRound >= remaining) break
                    val give = minOf(1, headroom.getValue(key))
                    if (give > 0) {
                        allocated[key] = allocated.getValue(key) + give
                        headroom[key] = headroom.getValue(key) - give
                        distributedThisRound += give
                    }
                }
            }
            remaining -= distributedThisRound
            active = recipients.filter { headroom.getValue(it) > 0 }
            if (distributedThisRound == 0) break // nobody has headroom left -- drop the remainder
        }

        allocated.forEach { (key, extra) ->
            if (extra > 0) {
                val current = mutableRoles.getValue(key)
                val newIdeal = current.ideal + extra
                mutableRoles[key] = RoleTarget(current.min, newIdeal, maxOf(current.max, newIdeal))
            }
        }
    }

    /** Fix 1 (edge-case audit, 2026-07-28) -- a redistribution recipient's `ideal` may grow to at
     * most this multiple of its OWN pre-redistribution `max`. A documented judgment call, chosen to
     * leave the already-accepted mono-Green MIDRANGE case (recursion absorbs +6 onto a prior max of
     * 7, well under 2x) untouched while capping the more extreme mono-Green CONTROL case (recursion
     * would otherwise absorb +16 onto a prior max of 5, a 9x-ideal blowup) down to a sane ceiling. */
    private const val REDISTRIBUTION_GROWTH_CAP_MULTIPLIER = 2.0

    /** WS9.2 — a SUBSTITUTE-only role keeps half its band (min unchanged): the plan's own framing
     * ("the substitutes are real but worse") is a qualitative claim, not a numerically cited one,
     * so a simple, documented halving is the deliberate choice here (not derived from an external
     * source — flagged as a judgment call, same discipline as WS5's undocumented bands). */
    private const val SUBSTITUTE_FACTOR = 0.5

    private fun RoleTarget.scaledBy(scale: Double): RoleTarget = RoleTarget(
        min = (min * scale).roundToInt(),
        ideal = (ideal * scale).roundToInt(),
        max = (max * scale).roundToInt(),
    )

    private fun roundTo2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
