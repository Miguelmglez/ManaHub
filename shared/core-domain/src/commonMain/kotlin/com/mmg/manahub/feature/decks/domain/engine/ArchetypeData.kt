package com.mmg.manahub.feature.decks.domain.engine

// ═══════════════════════════════════════════════════════════════════════════════
//  ArchetypeData — Deck Doctor Community/Archetype plan, Phase 1.2 (D15 — SUPERSEDED
//  for this retune only, see below)
//
//  Originally a verbatim transcription of Appendix A.2's `skeletons.json` (docs/claude-code-prompt-
//  deck-doctor-community.md, lines ~406-1557), LOCKED per D15 so no number could be "improved" ad
//  hoc outside the golden skeleton tests.
//
//  ── WS5 RETUNE (Deck Wizard & Engine Rework plan, docs/plans/deck-wizard-rework-plan.md,
//  Workstream 5.3, 2026-07-28) — THE D15 LOCK IS DELIBERATELY SUPERSEDED HERE ──
//  Root cause F3 ("Doctor shows the same warnings regardless of plan"): the D15-locked bands were
//  too uniform across archetypes/themes (every plan demanded ramp/board-wipes/card-draw at similar
//  levels), so a differentiated skeleton produced undifferentiated warnings. This pass re-derives
//  every role/land/curve band for all 7 archetypes x 2 formats and all 22 themes from the plan's
//  own researched anchor tables (Command Zone ep. 658 Commander template, a 60-card
//  archetype-band table, and EDHREC-theme-guided per-theme deltas — plan lines ~292-353), with
//  `antiRoles`/`relaxes` used to stop board-wipes/ramp/removal from being universal demands where
//  the archetype/theme is genuinely anti-synergistic with them. Every changed band carries a
//  one-line citation comment. Acceptance gate: `SkeletonDifferentiationTest` (WS5.1, written BEFORE
//  this retune, confirmed failing against the pre-retune data) must pass afterward, while
//  `GoldenDeckHarnessTest` (the OLD, unrelated DeckScorer/DeckRole engine — never touches this file)
//  stays bit-for-bit unchanged since nothing here is read by that engine.
//
//  **This file is NOT re-locked to "never touch again."** Per the plan, future tuning should still
//  go through a deliberate, tested workstream (golden/differentiation tests updated alongside the
//  data) rather than silent ad-hoc edits — the lesson from D15 was "don't drift silently," not
//  "never retune." WS9.3 (2026-07-28, same Batch F run as this header's WS5 pass) retuned
//  `COLOR_MODULATION`'s bucket 3/4 bands and added the new `LAND_MIX` table — see that section's
//  own header below for its citations. Any FUTURE color-modulation retune should follow the same
//  discipline (golden fixtures re-checked, citations or explicit judgment-call flags).
//
//  One deliberate transcription artifact REMOVED in this pass: the TOKENS theme used to carry an
//  inert documentation-only `"removal_mass_own_note"` `[0,0,0]` key (a D15 transcription fidelity
//  note, zero behavioral effect). It is replaced by a REAL `relaxes["removal_mass"]` entry (see
//  THEMES.TOKENS below) implementing the plan's explicit "wipes reduced" guidance for Tokens.
// ═══════════════════════════════════════════════════════════════════════════════

object ArchetypeData {

    /** The color-modulation table's mana-fix role key (A.5) — also a normal role in every
     * resolved skeleton once [ArchetypeSkeletonResolver.resolveWithColor] merges it in. */
    const val MANA_FIX_KEY: RoleKey = "mana_fix"

    /**
     * The A.4 OVERLAY set: payoff-style roles that describe a QUALITY of a card which usually
     * ALSO fills a macro role (a token-generating creature is still a body; a landfall payoff is
     * still a spell). Counted at 50% toward the A.4 step-6 budget invariant. Exactly the 13 keys
     * from Appendix B's `OVERLAY_HALF` Python set.
     */
    val OVERLAY_ROLES: Set<RoleKey> = setOf(
        "tribe_members", "tribe_payoff", "etb_payoff", "counters_payoff", "lifegain_payoff",
        "landfall_payoff", "artifact_payoff", "enchantment_payoff", "death_payoff",
        "spell_payoff", "self_mill_payoff", "vehicle", "group_effect",
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  generic — the GENERIC[format] base every resolve() starts from (A.4 step 1)
    //
    //  WS5.3: re-anchored to the plan's Command Zone ep. 658 "Baseline" row (Commander: 38 lands /
    //  10 ramp / 12 card advantage / 12 targeted disruption / 6 mass disruption — plan line ~304)
    //  and the plan's 60-card MIDRANGE row as the closest analogue for a format-neutral baseline
    //  (60-card archetype table, plan line ~321: "24 lands default, BELL"). `finisher`/`recursion`/
    //  `tutor` have no ep.658 anchor (ep.658's own "~22 plan cards" bucket is qualitative, not
    //  role-keyed) — kept at their prior values, which the file's own cross-check note already
    //  validated against ep.658's land number. Curve is left UNCHANGED for the same reason: ep.658
    //  only describes "peaks at 2 MV, tapering upward" (no explicit average), and inventing a
    //  number from a qualitative description would be less trustworthy than the prior
    //  EDHREC-calibrated figure.
    // ─────────────────────────────────────────────────────────────────────────

    fun generic(format: ArchetypeFormat): ArchetypeDefinition = when (format) {
        ArchetypeFormat.COMMANDER -> ArchetypeDefinition(
            id = null,
            format = ArchetypeFormat.COMMANDER,
            lands = RoleTarget(36, 38, 40), // ep.658 baseline: 38 lands (plan line ~304)
            roleTargets = mapOf(
                "ramp" to RoleTarget(8, 10, 13), // ep.658 baseline: 10 ramp
                "card_draw" to RoleTarget(10, 12, 15), // ep.658 baseline: 12 card advantage
                "removal_spot" to RoleTarget(9, 12, 15), // ep.658 baseline: 12 targeted disruption
                "removal_mass" to RoleTarget(4, 6, 8), // ep.658 baseline: 6 mass disruption
                "finisher" to RoleTarget(6, 8, 11), // no ep.658 anchor -- unchanged from prior
                "recursion" to RoleTarget(0, 2, 5), // no ep.658 anchor -- unchanged from prior
                "tutor" to RoleTarget(0, 2, 6), // no ep.658 anchor -- unchanged from prior
            ),
            antiRoles = emptySet(),
            curve = CurveBand(2.6, 3.0, 3.6), // no explicit ep.658 average -- ideal nudged 3.1->3.0
            shape = CurveShape.BELL,
        )
        ArchetypeFormat.SIXTY -> ArchetypeDefinition(
            id = null,
            format = ArchetypeFormat.SIXTY,
            lands = RoleTarget(22, 24, 26), // 60-card MIDRANGE row: 24 lands "default" (plan line ~321)
            roleTargets = mapOf(
                "removal_spot" to RoleTarget(4, 7, 12),
                "card_draw" to RoleTarget(2, 5, 10),
                "finisher" to RoleTarget(8, 12, 18),
                "threat_early" to RoleTarget(4, 8, 14),
            ),
            antiRoles = emptySet(),
            curve = CurveBand(2.2, 2.7, 3.2),
            shape = CurveShape.BELL,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  archetypes.<ARCH>.<fmt> (A.4 step 2 — roles_override REPLACES the matching
    //  GENERIC band; anti_roles / lands / curve / shape come from here wholesale)
    //
    //  WS5.3: Commander bands re-anchored to the plan's ep.658 per-archetype rows (AGGRO/CONTROL/
    //  COMBO/RAMP have explicit rows; MIDRANGE/TEMPO do NOT -- ep.658's table only lists Baseline/
    //  CONTROL/AGGRO/COMBO/RAMP for Commander, plan line ~302). MIDRANGE/TEMPO Commander bands are
    //  therefore INTERPOLATED (flagged explicitly in the WS5 report) from (a) their own 60-card
    //  anchor row scaled to Commander norms and (b) MIDRANGE's defining "grindy incremental value"
    //  identity (StrategyCatalog's own description) -- MIDRANGE gets a distinct recursion/finisher
    //  bump so it stops reading as a copy of GENERIC (the exact F3 regression the differentiation
    //  test caught pre-retune: "Generic vs Midrange" was a 1-band, non-clearing diff).
    //  60-card bands re-anchored to the plan's 60-card archetype table (plan line ~317).
    // ─────────────────────────────────────────────────────────────────────────

    val ARCHETYPES: Map<ArchetypeId, Map<ArchetypeFormat, ArchetypeDefinition>> = mapOf(
        ArchetypeId.AGGRO to mapOf(
            // ep.658 AGGRO row: lands 34-36, ramp 8-10, card advantage 8-10, targeted disruption
            // 5-6, mass disruption 1-2 (anti-role) -- plan line ~306.
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.AGGRO, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(33, 35, 37),
                roleTargets = mapOf(
                    "ramp" to RoleTarget(6, 8, 11),
                    "card_draw" to RoleTarget(6, 8, 11),
                    "threat_early" to RoleTarget(14, 18, 24), // curve front-loaded; AGGRO's defining trait
                    "finisher" to RoleTarget(6, 9, 13),
                    "removal_spot" to RoleTarget(4, 6, 8),
                    "removal_mass" to RoleTarget(0, 0, 2), // anti-role tolerance ceiling per ep.658 "1-2"
                ),
                antiRoles = setOf("removal_mass"),
                curve = CurveBand(1.7, 2.1, 2.5), // "curve front-loaded (peak 1-2 MV)"
                shape = CurveShape.FRONT,
            ),
            // 60-card AGGRO row: lands 19-23 (20 only hyper-aggro), tops ~4MV bulk<3MV FRONT,
            // >=24-28 creature/threat slots, interaction <=6-8, wipes = anti-role -- plan line ~319.
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.AGGRO, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(19, 21, 23),
                roleTargets = mapOf(
                    "threat_early" to RoleTarget(16, 20, 28), // ">=24-28 creature/threat slots" (with finisher)
                    "finisher" to RoleTarget(8, 12, 20),
                    "removal_spot" to RoleTarget(2, 5, 8), // "interaction <= 6-8" total
                    "card_draw" to RoleTarget(0, 2, 5),
                    "removal_mass" to RoleTarget(0, 0, 1), // "wipes = anti-role"
                ),
                antiRoles = setOf("removal_mass"),
                curve = CurveBand(1.4, 1.8, 2.2),
                shape = CurveShape.FRONT,
            ),
        ),
        ArchetypeId.CONTROL to mapOf(
            // ep.658 CONTROL row: lands 37-38, ramp 10-12, card advantage 12-14, targeted
            // disruption 12-15, mass disruption 6-7 ("a PLAN here, not insurance") -- plan
            // line ~305.
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.CONTROL, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(36, 38, 40),
                roleTargets = mapOf(
                    "removal_spot" to RoleTarget(11, 13, 16),
                    "removal_mass" to RoleTarget(5, 7, 9), // "a PLAN here" -- bumped, not insurance-level
                    "counterspell" to RoleTarget(6, 9, 13),
                    "card_draw" to RoleTarget(11, 13, 16),
                    "finisher" to RoleTarget(4, 6, 9),
                    "ramp" to RoleTarget(9, 11, 13),
                    "threat_early" to RoleTarget(0, 0, 4),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.9, 3.4, 4.0),
                shape = CurveShape.BACK,
            ),
            // 60-card CONTROL row: lands 26 (historic optimum), BACK, 12-14 cheap counterspells,
            // 4-6 wipes, draw engines up, creatures near-zero -- plan line ~320.
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.CONTROL, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(24, 26, 28),
                roleTargets = mapOf(
                    "counterspell" to RoleTarget(10, 13, 16),
                    "removal_spot" to RoleTarget(5, 8, 12),
                    "removal_mass" to RoleTarget(4, 5, 7),
                    "card_draw" to RoleTarget(8, 11, 14), // "draw engines up"
                    "finisher" to RoleTarget(3, 5, 8),
                    "threat_early" to RoleTarget(0, 0, 2), // "creatures near-zero"
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.7, 3.2, 3.9),
                shape = CurveShape.BACK,
            ),
        ),
        ArchetypeId.MIDRANGE to mapOf(
            // No ep.658 Commander row exists (plan's Commander table only lists Baseline/CONTROL/
            // AGGRO/COMBO/RAMP) -- INTERPOLATED from the 60-card MIDRANGE row's identity ("balanced
            // threats+answers... hand disruption") plus MIDRANGE's own catalog description
            // ("apply pressure now, out-value them later" -- StrategyCatalog.kt): the differentiator
            // vs. GENERIC is a real recursion/finisher bump (the grindy, incremental-advantage
            // identity), not a land/ramp change (MIDRANGE Commander decks run baseline lands/ramp).
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.MIDRANGE, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(35, 37, 40),
                roleTargets = mapOf(
                    "ramp" to RoleTarget(6, 8, 11),
                    "removal_spot" to RoleTarget(8, 10, 13),
                    "recursion" to RoleTarget(2, 4, 7), // the grindy incremental-value identity
                    "finisher" to RoleTarget(9, 11, 14),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.8, 3.2, 3.7),
                shape = CurveShape.BELL,
            ),
            // 60-card MIDRANGE row: lands 24 (default), BELL, balanced threats+answers, 3-4 wipes
            // sideways, hand disruption where black -- plan line ~321. `removal_mass` is a NEW
            // override (GENERIC/60 has no removal_mass band at all) capturing "3-4 wipes sideways".
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.MIDRANGE, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(22, 24, 26),
                roleTargets = mapOf(
                    "removal_spot" to RoleTarget(9, 12, 16),
                    "finisher" to RoleTarget(9, 13, 17),
                    "threat_early" to RoleTarget(5, 8, 12),
                    "card_draw" to RoleTarget(2, 4, 7),
                    "removal_mass" to RoleTarget(2, 3, 4), // "3-4 wipes sideways"
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.3, 2.8, 3.3),
                shape = CurveShape.BELL,
            ),
        ),
        ArchetypeId.COMBO to mapOf(
            // ep.658 COMBO row: lands 37-38, ramp 10-12, card advantage 12, targeted disruption
            // 6-8, mass disruption 2-3, "+4-6 protection + tutors up (6-8)" -- plan line ~307.
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.COMBO, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(36, 38, 40),
                roleTargets = mapOf(
                    "tutor" to RoleTarget(6, 8, 11), // "tutors up (6-8)"
                    "card_draw" to RoleTarget(11, 12, 15),
                    "protection" to RoleTarget(4, 6, 9), // "+4-6 protection"
                    "finisher" to RoleTarget(3, 5, 8),
                    "removal_spot" to RoleTarget(5, 7, 9),
                    "ramp" to RoleTarget(9, 11, 13),
                    "removal_mass" to RoleTarget(1, 2, 4),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.0, 2.6, 3.2),
                shape = CurveShape.FRONT,
            ),
            // 60-card COMBO row: lands 22-24, curve depends on engine, combo pieces + tutors +
            // protection, interaction minimal, creature count irrelevant -- plan line ~323.
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.COMBO, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(21, 23, 25),
                roleTargets = mapOf(
                    "tutor" to RoleTarget(5, 8, 13),
                    "card_draw" to RoleTarget(9, 13, 19),
                    "protection" to RoleTarget(3, 5, 9),
                    "finisher" to RoleTarget(3, 6, 9),
                    "removal_spot" to RoleTarget(0, 2, 6), // "interaction minimal"
                    "threat_early" to RoleTarget(0, 0, 6), // "creature count irrelevant as a target"
                ),
                antiRoles = emptySet(),
                curve = CurveBand(1.8, 2.3, 3.0), // engine-dependent -- kept as prior, no anchor to move it
                shape = CurveShape.FRONT,
            ),
        ),
        // PRISON (Deck Analysis Engine v3, spec §2.2, NEW) -- a stax skeleton is structurally
        // incompatible with an overlay: it wants 3-5 finishers and 3-7 card draw as its BASE
        // (a `relaxes` cannot express "reduce a hypothetical band that was never set" cleanly on
        // top of another archetype), so it ships as a full 5th macro rather than the old `STAX`
        // theme it replaces. Bands transcribed verbatim from spec §2.2.
        ArchetypeId.PRISON to mapOf(
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.PRISON, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(34, 36, 38),
                roleTargets = mapOf(
                    "ramp" to RoleTarget(8, 10, 13),
                    "card_draw" to RoleTarget(3, 6, 9),
                    "removal_spot" to RoleTarget(6, 8, 11),
                    "removal_mass" to RoleTarget(3, 5, 7),
                    "stax_piece" to RoleTarget(10, 14, 20),
                    "finisher" to RoleTarget(3, 5, 8),
                    "protection" to RoleTarget(3, 5, 8),
                    "tutor" to RoleTarget(2, 4, 8),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.2, 2.6, 3.2),
                shape = CurveShape.FRONT,
            ),
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.PRISON, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(20, 22, 24),
                roleTargets = mapOf(
                    "stax_piece" to RoleTarget(12, 16, 20),
                    "removal_spot" to RoleTarget(4, 6, 9),
                    "card_draw" to RoleTarget(2, 4, 7),
                    "finisher" to RoleTarget(2, 4, 6),
                    "protection" to RoleTarget(2, 4, 6),
                    "tutor" to RoleTarget(2, 4, 8),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(1.8, 2.2, 2.8),
                shape = CurveShape.FRONT,
            ),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  postures.<POSTURE> (Deck Analysis Engine v3, spec §3) -- layer 2.5, applied by
    //  [ArchetypeSkeletonResolver] between the archetype override (layer 2) and themes (layer 3),
    //  reusing the SAME adds/relaxes/landsDelta/curveDelta merge machinery a [ThemeDefinition]
    //  already uses (spec: "a data change, not an engine change"). Bands transcribed verbatim from
    //  spec §3's table; `RAMP`/`TEMPO` are the two ex-macros (their OLD ArchetypeDefinition bands
    //  above are NOT reused here -- the posture table gives its own, smaller DELTA-shaped numbers,
    //  since a posture only nudges an already-resolved archetype skeleton, it does not define one
    //  from scratch). `ATTRITION`/`TOOLBOX`/`VOLTRON`/`GROUP_HUG`/`GROUP_SLUG` are the 5 ex-themes.
    //  `sixtyScale` for RAMP/TEMPO (0.75) mirrors the old RAMP/TEMPO archetypes' own Commander-vs-
    //  60-card SCALE ratio (their retired bands above, ~0.75-0.8x) since both postures still carry
    //  a "big/small mana" identity at roughly that same proportion; the newer postures
    //  (ATTRITION/TOOLBOX/VOLTRON/GROUP_HUG/GROUP_SLUG) default to 0.5, mirroring
    //  [ThemeDefinition.sixtyScale]'s own default. Both are documented judgment calls (spec §3
    //  gives one Commander-scale table only), not cited numbers.
    // ─────────────────────────────────────────────────────────────────────────

    val POSTURES: Map<PostureId, PostureDefinition> = mapOf(
        PostureId.RAMP to PostureDefinition(
            id = PostureId.RAMP,
            adds = mapOf(
                "ramp" to RoleTarget(12, 15, 19),
                "finisher" to RoleTarget(8, 11, 15),
            ),
            landsDelta = 2,
            curveDelta = 0.5,
            sixtyScale = 0.75,
        ),
        PostureId.TEMPO to PostureDefinition(
            id = PostureId.TEMPO,
            adds = mapOf(
                "threat_early" to RoleTarget(9, 13, 17),
                "counterspell" to RoleTarget(6, 9, 12),
            ),
            antiRoles = setOf("removal_mass"), // spec §3: "TEMPO also carries antiRoles = {removal_mass}"
            landsDelta = -2,
            curveDelta = -0.5,
            sixtyScale = 0.75,
        ),
        PostureId.ATTRITION to PostureDefinition(
            id = PostureId.ATTRITION,
            adds = mapOf(
                "recursion" to RoleTarget(4, 6, 9),
                // spec §3: "card_draw +2 shift / removal_spot +2 shift" -- expressed here as an
                // adds band whose ideal sits +2 above GENERIC's own card_draw/removal_spot ideal
                // (12/12, see ArchetypeData.generic(COMMANDER)) so the MAX-per-bound merge actually
                // raises a compatible archetype's band rather than being silently swallowed.
                "card_draw" to RoleTarget(10, 14, 17),
                "removal_spot" to RoleTarget(9, 14, 17),
            ),
            curveDelta = 0.2,
        ),
        PostureId.TOOLBOX to PostureDefinition(
            id = PostureId.TOOLBOX,
            adds = mapOf(
                "tutor" to RoleTarget(8, 12, 18),
                "recursion" to RoleTarget(3, 5, 8),
            ),
            relaxes = mapOf("finisher" to RoleTarget(3, 5, 8)),
            curveDelta = 0.3,
        ),
        PostureId.VOLTRON to PostureDefinition(
            id = PostureId.VOLTRON,
            adds = mapOf(
                "equipment" to RoleTarget(6, 9, 13),
                "aura_buff" to RoleTarget(0, 6, 12),
                "protection" to RoleTarget(6, 9, 12),
                "evasion" to RoleTarget(4, 6, 9),
            ),
            relaxes = mapOf(
                "threat_early" to RoleTarget(0, 0, 99),
                "finisher" to RoleTarget(0, 2, 5),
                "removal_mass" to RoleTarget(0, 0, 2),
            ),
            landsDelta = -2,
            curveDelta = -0.4,
        ),
        PostureId.GROUP_HUG to PostureDefinition(
            id = PostureId.GROUP_HUG,
            adds = mapOf(
                "group_effect" to RoleTarget(9, 12, 16),
                "finisher" to RoleTarget(7, 9, 12),
            ),
            landsDelta = 2,
            commanderOnly = true,
        ),
        PostureId.GROUP_SLUG to PostureDefinition(
            id = PostureId.GROUP_SLUG,
            adds = mapOf(
                "group_effect" to RoleTarget(11, 15, 20),
                "lifegain_source" to RoleTarget(0, 3, 7),
            ),
            commanderOnly = true,
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  themes.<THEME> (A.4 step 3 — adds merge max-per-bound, relaxes replace)
    //
    //  WS5.3: the plan gives EXPLICIT numeric anchors for a subset of themes (REANIMATOR,
    //  ARISTOCRATS, VOLTRON, TOKENS, STAX, LANDFALL — plan line ~330-337); these are re-derived
    //  directly from those numbers with a citation. The remaining themes have NO published
    //  per-theme count anywhere (verified live 2026-07-28: EDHREC's `json.edhrec.com/pages/tags/
    //  <slug>.json` endpoint — the SAME one `:tools:tag-pipeline` already fetches — returns card
    //  popularity/synergy lists, not an average-deck role/type breakdown; there is no fetchable
    //  "average decklist card-type counts" endpoint for a theme, contrary to what the plan's WS5.3
    //  section assumed was pullable). For those themes this pass keeps the prior D15 band where it
    //  already differentiates well, and otherwise widens/adds a band using the SAME reasoning
    //  pattern as the cited anchors (a per-theme judgment call, flagged in the WS5 report, not a
    //  guess presented as sourced data).
    //
    //  A second, structural finding from writing [SkeletonDifferentiationTest] (WS5.1) before this
    //  retune: a theme's `adds` band is MAX-merged against the archetype's own band for any
    //  OVERLAPPING role key, so a modest theme add is frequently swallowed whole (invisible) when
    //  the compatible archetype already has a comparable or higher band for that same key. Several
    //  bands below are deliberately pushed ABOVE every compatible archetype's own band for that key
    //  (not merely "a plausible number") specifically so the theme's contribution actually survives
    //  the merge — this is a differentiation-mechanics fix, not an anchor-table number.
    // ─────────────────────────────────────────────────────────────────────────

    val THEMES: Map<ThemeId, ThemeDefinition> = mapOf(
        // Plan anchor: "reanimation 6-9 + graveyard_enabler 8-12 + curve exemption already
        // modeled" (plan line ~332).
        ThemeId.REANIMATOR to ThemeDefinition(
            id = ThemeId.REANIMATOR,
            adds = mapOf(
                "graveyard_enabler" to RoleTarget(8, 10, 13),
                "reanimation" to RoleTarget(6, 8, 11),
                "finisher" to RoleTarget(7, 10, 13),
                "recursion" to RoleTarget(2, 4, 7),
            ),
            sixtyScale = 0.55,
            curveExemption = CurveExemption.REANIMATOR_HIGH_MV,
        ),
        // Deck Analysis Engine v3 (spec §4.2) -- RETARGETED onto the new `mill_self` producer role
        // (Phase 1) instead of the old, ambiguous `graveyard_enabler` (which REANIMATOR's own
        // `graveyard_enabler` band above already owns): "milling yourself and milling the opponent
        // are opposite axes -- one feeds a graveyard payoff, the other is a win condition. Merging
        // them into mill_engine was a modelling error." Bands per spec §4.2:
        // `mill_self 8-12-16 · self_mill_payoff 10-14-20 · recursion 4-6-10`.
        ThemeId.SELF_MILL to ThemeDefinition(
            id = ThemeId.SELF_MILL,
            adds = mapOf(
                "mill_self" to RoleTarget(8, 12, 16),
                "self_mill_payoff" to RoleTarget(10, 14, 20),
                "recursion" to RoleTarget(4, 6, 10),
            ),
            sixtyScale = 0.55,
            overlayRoles = setOf("self_mill_payoff"),
        ),
        // Plan anchor: "sac_outlet band 5-8 + death-trigger payoffs + token fodder" (plan
        // line ~332).
        ThemeId.ARISTOCRATS to ThemeDefinition(
            id = ThemeId.ARISTOCRATS,
            adds = mapOf(
                "sac_outlet" to RoleTarget(4, 6, 9), // "sac_outlet band 5-8"
                "death_payoff" to RoleTarget(7, 10, 14),
                "token_generator" to RoleTarget(6, 9, 13), // "token fodder"
                "recursion" to RoleTarget(3, 5, 8),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("death_payoff"),
        ),
        // Plan anchor: "producers 10-14 + anthems 4-6, wipes reduced" (plan line ~332). Deck
        // Analysis Engine v3 (spec §4.2/§5.1) finally gives TOKENS its real `anthem` band
        // (3-5-8) -- Phase 1 added the `anthem` RoleSpec this comment previously flagged as
        // missing ("no anthem role exists"); `counters_payoff` (the old proxy) is REMOVED now that
        // a real anthem key exists, since keeping both would double-count the same "go-wide +
        // buff" signal. "Wipes reduced" stays a REAL relax (see file header).
        ThemeId.TOKENS to ThemeDefinition(
            id = ThemeId.TOKENS,
            adds = mapOf(
                "token_generator" to RoleTarget(10, 12, 15), // "producers 10-14"
                "anthem" to RoleTarget(3, 5, 8), // spec §4.2: TOKENS' real anthem band
                "finisher" to RoleTarget(6, 9, 12),
            ),
            relaxes = mapOf("removal_mass" to RoleTarget(0, 0, 2)), // "wipes reduced"
            sixtyScale = 0.5,
            overlayRoles = setOf("anthem"),
        ),
        // No published per-theme anchor beyond "instant/sorcery density + payoffs" (plan
        // line ~333). `spell_payoff`/`counterspell` pushed above every compatible archetype's own
        // band (CONTROL's counterspell/card_draw in particular) so this theme's identity survives
        // the merge under CONTROL, not just under TEMPO/AGGRO/COMBO where it was already visible.
        ThemeId.SPELLSLINGER to ThemeDefinition(
            id = ThemeId.SPELLSLINGER,
            adds = mapOf(
                "spell_payoff" to RoleTarget(9, 13, 17),
                "card_draw" to RoleTarget(10, 13, 17),
                "counterspell" to RoleTarget(5, 11, 15), // above CONTROL's own 9-ideal band
                "removal_spot" to RoleTarget(6, 9, 13),
            ),
            curveDelta = -0.3, // "wants a low, cheap-spell curve" (StrategyCatalog's own description)
            sixtyScale = 0.55,
            overlayRoles = setOf("spell_payoff"),
        ),
        // VOLTRON: MOVED to PostureId.VOLTRON (Deck Analysis Engine v3, spec §4.1 -- "none owns a
        // producer<->payoff axis; they only reshape the skeleton"). See ArchetypeData.POSTURES.
        // STAX: MOVED to ArchetypeId.PRISON (spec §4.1 -- STAX -> macro PRISON). See
        // ArchetypeData.ARCHETYPES[PRISON].
        // Plan anchor: "lands UP +2-4 vs. baseline + landfall payoffs" (plan line ~334) -- delta
        // bumped from +2 to +3 (middle of the stated +2-4 range).
        ThemeId.LANDFALL to ThemeDefinition(
            id = ThemeId.LANDFALL,
            adds = mapOf(
                "landfall_payoff" to RoleTarget(8, 11, 15),
                "ramp" to RoleTarget(12, 16, 22),
                "recursion" to RoleTarget(2, 4, 7),
            ),
            sixtyScale = 0.5,
            landsDelta = 3, // "+2-4 vs. baseline", middle-high of the stated range
            overlayRoles = setOf("landfall_payoff"),
        ),
        // Plan anchor: "gain enablers + payoff band" (plan line ~335), no exact number -- payoff
        // band nudged up slightly for separation from the other lifegain-adjacent themes.
        ThemeId.LIFEGAIN to ThemeDefinition(
            id = ThemeId.LIFEGAIN,
            adds = mapOf(
                "lifegain_payoff" to RoleTarget(9, 12, 16),
                "token_generator" to RoleTarget(0, 4, 9),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("lifegain_payoff"),
        ),
        // No published per-theme anchor -- kept close to prior D15 band, nudged for separation.
        ThemeId.PLUS1_COUNTERS to ThemeDefinition(
            id = ThemeId.PLUS1_COUNTERS,
            adds = mapOf(
                "counters_payoff" to RoleTarget(10, 13, 18),
                "protection" to RoleTarget(2, 4, 7),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("counters_payoff"),
        ),
        // No published per-theme anchor -- unique tribe_members/tribe_payoff pair already
        // differentiates strongly (unchanged from prior D15 band).
        ThemeId.TRIBAL to ThemeDefinition(
            id = ThemeId.TRIBAL,
            adds = mapOf(
                "tribe_members" to RoleTarget(22, 28, 36),
                "tribe_payoff" to RoleTarget(6, 9, 13),
            ),
            sixtyScale = 0.55,
            overlayRoles = setOf("tribe_members", "tribe_payoff"),
        ),
        // No published per-theme anchor -- `ramp` pushed above every compatible archetype's own
        // ramp band (including RAMP itself) so "mana-rocks-matter" survives the merge everywhere,
        // matching StrategyCatalog's own "overlaps directly with a ramp identity" rationale.
        // `curveDelta` (NEW): efficient rocks/cost-reducers let an artifacts shell function on a
        // slightly cheaper effective curve -- this also gives the theme a 2nd, scale-independent
        // differentiation channel under RAMP specifically, where `ramp`'s scaled 60-card
        // contribution is otherwise swallowed by RAMP's own already-high ramp band.
        ThemeId.ARTIFACTS to ThemeDefinition(
            id = ThemeId.ARTIFACTS,
            adds = mapOf(
                "artifact_payoff" to RoleTarget(8, 12, 16),
                "ramp" to RoleTarget(12, 16, 20),
            ),
            curveDelta = -0.3,
            sixtyScale = 0.55,
            overlayRoles = setOf("artifact_payoff"),
        ),
        // No published per-theme anchor -- unique enchantment_payoff+aura_buff pair already
        // differentiates strongly (unchanged from prior D15 band).
        ThemeId.ENCHANTRESS to ThemeDefinition(
            id = ThemeId.ENCHANTRESS,
            adds = mapOf(
                "enchantment_payoff" to RoleTarget(7, 10, 14),
                "aura_buff" to RoleTarget(0, 6, 14),
                "card_draw" to RoleTarget(10, 13, 16),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("enchantment_payoff"),
        ),
        // No published per-theme anchor -- unique wheel key already differentiates strongly
        // (unchanged from prior D15 band).
        ThemeId.WHEELS to ThemeDefinition(
            id = ThemeId.WHEELS,
            adds = mapOf(
                "wheel" to RoleTarget(5, 8, 11),
                "spell_payoff" to RoleTarget(4, 7, 11),
                "card_draw" to RoleTarget(12, 15, 18),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("spell_payoff"),
        ),
        // Deck Analysis Engine v3 (spec §4.2) -- split from the old merged `MILL` into
        // `MILL_OPPONENT` (this is the WIN-CONDITION half -- milling the opponent out; the
        // enabler/self-mill half is the separately-retargeted `ThemeId.SELF_MILL` above). Bands per
        // spec §4.2: `mill_opponent 10-14-18 · removal_spot 4-6-9 · counterspell 0-4-8 ·
        // finisher 0-2-4`, curve Δ +0.3, sixtyScale 0.60.
        ThemeId.MILL_OPPONENT to ThemeDefinition(
            id = ThemeId.MILL_OPPONENT,
            adds = mapOf(
                "mill_opponent" to RoleTarget(10, 14, 18),
                "removal_spot" to RoleTarget(4, 6, 9),
                "counterspell" to RoleTarget(0, 4, 8),
                "finisher" to RoleTarget(0, 2, 4),
            ),
            curveDelta = 0.3,
            sixtyScale = 0.6,
        ),
        // GROUP_HUG/GROUP_SLUG: MOVED to PostureId.GROUP_HUG/GROUP_SLUG (spec §4.1). See
        // ArchetypeData.POSTURES.
        // No published per-theme anchor -- unique blink_effect+etb_payoff pair already
        // differentiates strongly (unchanged from prior D15 band).
        ThemeId.BLINK to ThemeDefinition(
            id = ThemeId.BLINK,
            adds = mapOf(
                "blink_effect" to RoleTarget(7, 10, 14),
                "etb_payoff" to RoleTarget(14, 18, 24),
                "recursion" to RoleTarget(2, 4, 7),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("etb_payoff"),
        ),
        // No published per-theme anchor -- unique planeswalker key already differentiates
        // strongly (unchanged from prior D15 band).
        ThemeId.SUPERFRIENDS to ThemeDefinition(
            id = ThemeId.SUPERFRIENDS,
            adds = mapOf(
                "planeswalker" to RoleTarget(10, 14, 20),
                "protection" to RoleTarget(5, 8, 12),
                "removal_mass" to RoleTarget(4, 6, 8),
                "token_generator" to RoleTarget(3, 5, 9),
            ),
            commanderOnly = false,
            sixtyScale = 0.4,
        ),
        // No published per-theme anchor -- added `evasion` (NEW) since a modest `vehicle`/
        // `threat_early` add alone was routinely swallowed by AGGRO/MIDRANGE's own already-high
        // threat_early band (many vehicles carry flying/menace, so this is a real structural
        // signal, not an arbitrary filler key).
        ThemeId.VEHICLES to ThemeDefinition(
            id = ThemeId.VEHICLES,
            adds = mapOf(
                "vehicle" to RoleTarget(8, 11, 15),
                "threat_early" to RoleTarget(10, 14, 18),
                "evasion" to RoleTarget(3, 5, 8),
            ),
            sixtyScale = 0.6,
            overlayRoles = setOf("vehicle"),
        ),
        // TOOLBOX: MOVED to PostureId.TOOLBOX (spec §4.1). See ArchetypeData.POSTURES.
        // No published per-theme anchor -- unique clone_theft_effect key + sac_outlet (an
        // uncovered key for all 3 compatible archetypes) already differentiate strongly (unchanged
        // from prior D15 band).
        ThemeId.CLONES_THEFT to ThemeDefinition(
            id = ThemeId.CLONES_THEFT,
            adds = mapOf(
                "clone_theft_effect" to RoleTarget(9, 13, 18),
                "sac_outlet" to RoleTarget(0, 3, 7),
            ),
            commanderOnly = true,
        ),
        // Deck Analysis Engine v3 (spec §4.2, NEW). Bands verbatim: `treasure_source 8-11-15 ·
        // artifact_payoff 5-8-12 · sac_outlet 0-3-7`, sixtyScale 0.55.
        ThemeId.TREASURE to ThemeDefinition(
            id = ThemeId.TREASURE,
            adds = mapOf(
                "treasure_source" to RoleTarget(8, 11, 15),
                "artifact_payoff" to RoleTarget(5, 8, 12),
                "sac_outlet" to RoleTarget(0, 3, 7),
            ),
            sixtyScale = 0.55,
            overlayRoles = setOf("artifact_payoff"),
        ),
        // Deck Analysis Engine v3 (spec §4.2, NEW -- split out of the old VOLTRON theme's
        // `equipment_or_aura` proxy now that Phase 1 gave `equipment` its own real RoleSpec). Bands
        // verbatim: `equipment 8-11-15 · threat_early 6-9-13 · evasion 3-5-8`, sixtyScale 0.60.
        ThemeId.EQUIPMENT to ThemeDefinition(
            id = ThemeId.EQUIPMENT,
            adds = mapOf(
                "equipment" to RoleTarget(8, 11, 15),
                "threat_early" to RoleTarget(6, 9, 13),
                "evasion" to RoleTarget(3, 5, 8),
            ),
            sixtyScale = 0.6,
        ),
        // Deck Analysis Engine v3 (spec §4.2, NEW, `sixtyOnly`). Bands verbatim: `spell_payoff
        // 4-6-9 · card_draw 12-16-20 · ramp 8-12-16 · tutor 4-6-9`, curve Δ -0.5. Commander's
        // higher average curve and lower spell density make a genuine storm-count kill implausible
        // -- storm is a 60-card-constructed-specific archetype (spec §4.2).
        ThemeId.STORM to ThemeDefinition(
            id = ThemeId.STORM,
            adds = mapOf(
                "spell_payoff" to RoleTarget(4, 6, 9),
                "card_draw" to RoleTarget(12, 16, 20),
                "ramp" to RoleTarget(8, 12, 16),
                "tutor" to RoleTarget(4, 6, 9),
            ),
            curveDelta = -0.5,
            sixtyOnly = true,
            overlayRoles = setOf("spell_payoff"),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  color_modulation (A.5). Bucketed by color COUNT: 1, 2, 3, or 4+ ("4-5").
    //
    //  WS9.3 RETUNE (Deck Wizard & Engine Rework plan, docs/plans/deck-wizard-rework-plan.md,
    //  Workstream 9.3, 2026-07-28) — buckets 1/2 are UNCHANGED (already reasonable, low-risk to
    //  touch); buckets 3/4 are retuned for BOTH formats. `manaFix` counts DEDICATED mana-fixing
    //  CARDS (rocks/fetches/tutors-for-land — [ArchetypeRoleClassifier]'s `manaFixMatcher`), a
    //  narrower category than "every nonbasic land" — this table is NOT trying to model the full
    //  land base (that's the NEW [LAND_MIX] table below), only the dedicated-fixing-spell count.
    //
    //  Cross-check anchor (plan WS9.3, already implemented in [ManaBaseAnalyzer]'s Karsten tables,
    //  UNCHANGED by this pass): per-color SOURCE counts scale roughly x1.6 from a 60-card to a
    //  99-card Commander shell (14/20/23 -> 19/28/32 single/double/triple pip @ 24/38 lands). The
    //  retune below applies the SAME "more colors need proportionally more dedicated fixing"
    //  logic to the mana_fix role count specifically.
    //
    //  Finding: the PRE-retune SIXTY bucket-3 band, (8,12,16), was numerically IDENTICAL to
    //  Commander's bucket-3 band despite a 60-card deck having ~40% fewer total cards and a
    //  shallower nonbasic-land pool per color pair than Commander's full cube — the same
    //  "bands too uniform across contexts" pattern WS5 found and fixed for archetype/theme bands.
    //  Retuned down to a competitive 3-color 60-card shell's actual fixing count (a Jund/Bant-style
    //  shell typically runs 6-10 dedicated fixing sources, not 12-16 — judgment-call, no single
    //  numbered external citation, same discipline as WS5's uncited bands).
    // ─────────────────────────────────────────────────────────────────────────

    /** Maps a raw color-identity count to the annex's bucket code (1/2/3/4 == "4-5"). */
    fun colorCountBucket(colorCount: Int): Int = colorCount.coerceIn(1, 4)

    val COLOR_MODULATION: Map<ArchetypeFormat, Map<Int, ColorModulationEntry>> = mapOf(
        ArchetypeFormat.COMMANDER to mapOf(
            // Mono-color: no dedicated fixing needed. UNCHANGED (WS9.3 cross-check: fine as-is).
            1 to ColorModulationEntry(RoleTarget(0, 0, 3), landsDelta = 0),
            // 2-color: mtgedh.com convention "8-12 basics, rest fixing" out of ~37 lands implies a
            // modest dedicated-fixing count beyond the color pair's own dual lands. UNCHANGED.
            2 to ColorModulationEntry(RoleTarget(3, 6, 10), landsDelta = 0),
            // 3-color: mtgedh.com convention "4-8 basics, ~25-27 lands doing fixing duty" implies
            // meaningfully MORE dedicated fixing than a 2-color deck's ideal=6 -- bumped ideal
            // 12->14, max 16->20 (min 8->9) to reflect a well-tuned 3-color EDH deck's typical
            // rock/fetch count (WS9.3 judgment call, scaled off the convention's basics delta).
            3 to ColorModulationEntry(RoleTarget(9, 14, 20), landsDelta = 0),
            // 4-5-color: the plan's own convention explicitly calls this "fixing-first" (2-5
            // basics) -- dedicated fixing commonly runs 18-24+ in optimized 4-5c EDH lists
            // (Command Zone 4-5-color primers). Bumped min 12->14, ideal 16->20, max 22->28.
            4 to ColorModulationEntry(RoleTarget(14, 20, 28), landsDelta = 1),
        ),
        ArchetypeFormat.SIXTY to mapOf(
            // Mono-color: UNCHANGED.
            1 to ColorModulationEntry(RoleTarget(0, 0, 2), landsDelta = 0),
            // 2-color: UNCHANGED (a handful of dual lands/rocks is typical, no strong reason to move).
            2 to ColorModulationEntry(RoleTarget(4, 8, 12), landsDelta = 0),
            // 3-color: retuned DOWN from (8,12,16) -- see the uniformity finding above -- but kept
            // ABOVE the 2-color bucket's (4,8,12) band (monotonic: more colors never need LESS
            // dedicated fixing). A competitive 60-card 3-color shell (Jund/Bant-style) typically
            // runs 6-10 dedicated fixing sources out of ~25 lands, a modest bump over 2-color's 8.
            3 to ColorModulationEntry(RoleTarget(6, 10, 14), landsDelta = 1),
            // 4-5-color: retuned DOWN from (12,16,20) proportionally (60/99 ~ 0.6x of the retuned
            // Commander bucket-4 band: 14*0.6~8, 20*0.6~13, 28*0.6~17~18), while staying ABOVE the
            // 3-color bucket (monotonic) -- a 60-card 5c shell needs real fixing but at a much
            // smaller absolute scale than a 99-card Commander deck.
            4 to ColorModulationEntry(RoleTarget(8, 13, 18), landsDelta = 2),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  land_mix (WS9.3, NEW — does not exist anywhere before this pass). Basics-vs-non-basic-
    //  fixing LAND COMPOSITION guideline, per format + color-count bucket. Distinct from
    //  [ColorModulationEntry.manaFix] above (dedicated fixing SPELLS/rocks) -- this describes,
    //  out of the resolved skeleton's `lands` band, what SHARE should be basic lands vs.
    //  non-basic (duals/triomes/fetches/utility). Data layer ONLY in this run -- WS9.4 (Batch G,
    //  NOT implemented here) is the consumer that wires this into the wizard's land-fill.
    //
    //  Commander convention (plan WS9.3 anchor, Command Zone / mtgedh.com "fixing-first" guidance):
    //   - 2-color: 8-12 basics, rest (~25-29 of ~37 lands) non-basic fixing.
    //   - 3-color: 4-8 basics, ~25-27 non-basic fixing/colored lands.
    //   - 4-5-color: 2-5 basics, fixing-first (the overwhelming majority of the land base).
    //  A 60-card deck runs proportionally MORE basics than Commander at the same color count
    //  (far fewer total lands, a shallower nonbasic-land pool per color pair in most 60-card
    //  formats, and less deck-space budget for a wide nonbasic base) -- the SIXTY ratios below are
    //  a documented judgment-call extrapolation (NOT separately cited), not derived from the same
    //  Commander-specific convention numbers.
    //
    //  SPLASH SEMANTICS (plan WS9.3): a splash color (few cards, single pips, mid/late curve) must
    //  NOT inflate that color's fixing demand to full-color levels. This table stays a COUNT-level
    //  (deck-wide) modulation on purpose -- per-card PIP-WEIGHTED demand is [ManaBaseAnalyzer]'s
    //  job (Karsten tables, UNCHANGED), never re-derived here.
    // ─────────────────────────────────────────────────────────────────────────

    /** One color-count bucket's basics-vs-fixing composition guideline. [basicsRatio] is the
     * SHARE (0.0-1.0) of the skeleton's total land count that should be basic lands; the
     * remainder is non-basic fixing. A sibling table to [COLOR_MODULATION] (same keying: format x
     * bucket) rather than a field ADDED to [ColorModulationEntry] -- basics-vs-fixing composition
     * is orthogonal to the dedicated-fixing-SPELL count and the land-count delta
     * [ColorModulationEntry] already carries, and keeping it separate avoids reshaping that data
     * class (and its 2 call sites in [ArchetypeSkeletonResolver.resolveWithColorCount]) for data
     * nothing consumes yet. */
    data class LandMixEntry(val basicsRatio: ClosedFloatingPointRange<Double>)

    val LAND_MIX: Map<ArchetypeFormat, Map<Int, LandMixEntry>> = mapOf(
        ArchetypeFormat.COMMANDER to mapOf(
            // Fix 8 (edge-case audit, 2026-07-28): was 0.90..1.0, which implied a small non-zero
            // dedicated-fixing-land target -- inconsistent with COLOR_MODULATION's OWN mono-color
            // framing just above ("no dedicated fixing needed"). 1.0..1.0 makes bucket 1 mean
            // EXACTLY what that comment says: basics are the WHOLE land base, zero dedicated fixing.
            1 to LandMixEntry(1.0..1.0), // mono-color: basics ARE the land base, no dedicated fixing
            2 to LandMixEntry(0.22..0.32), // 8-12 of ~37 lands (plan WS9.3 anchor)
            3 to LandMixEntry(0.11..0.22), // 4-8 of ~37 lands (plan WS9.3 anchor)
            4 to LandMixEntry(0.05..0.14), // 2-5 of ~37 lands, "fixing-first" (plan WS9.3 anchor)
        ),
        ArchetypeFormat.SIXTY to mapOf(
            1 to LandMixEntry(1.0..1.0), // Fix 8 -- same mono-color consistency fix as Commander above
            2 to LandMixEntry(0.35..0.50), // judgment call: proportionally more basics than Commander
            3 to LandMixEntry(0.18..0.30), // judgment call
            4 to LandMixEntry(0.08..0.18), // judgment call
        ),
    )

    /** Convenience lookup mirroring [COLOR_MODULATION]'s own bucket resolution. */
    fun landMixFor(format: ArchetypeFormat, colorCount: Int): LandMixEntry =
        LAND_MIX.getValue(format).getValue(colorCountBucket(colorCount))
}
