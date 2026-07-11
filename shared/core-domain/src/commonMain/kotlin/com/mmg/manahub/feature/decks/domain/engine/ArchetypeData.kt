package com.mmg.manahub.feature.decks.domain.engine

// ═══════════════════════════════════════════════════════════════════════════════
//  ArchetypeData — Deck Doctor Community/Archetype plan, Phase 1.2 (D15)
//
//  Verbatim transcription of Appendix A.2's `skeletons.json` (docs/claude-code-prompt-
//  deck-doctor-community.md, lines ~406-1557) into Kotlin data. LOCKED per D15: every
//  number here must match the annex exactly. Do NOT "improve" or re-derive a number —
//  tuning happens ONLY through the golden skeleton tests (Phase 1.8,
//  `ArchetypeSkeletonGoldenTest`/`ArchetypeReferenceDeckGoldenTest`), never by editing
//  this file ad-hoc.
//
//  One deliberate transcription note: the TOKENS theme's JSON entry carries a third
//  `"removal_mass_own_note"` key with band `[0,0,0]` — clearly a documentation artifact
//  (an inert zero band, not a real role in the Appendix A vocabulary), but it is
//  transcribed here anyway for strict 1:1 fidelity per D15. A `[0,0,0]` band never
//  fires a "missing" warning (min 0) and is not in [OVERLAY_ROLES], so it has zero
//  behavioral effect either way.
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
    // ─────────────────────────────────────────────────────────────────────────

    fun generic(format: ArchetypeFormat): ArchetypeDefinition = when (format) {
        ArchetypeFormat.COMMANDER -> ArchetypeDefinition(
            id = ArchetypeId.GENERIC,
            format = ArchetypeFormat.COMMANDER,
            lands = RoleTarget(34, 37, 39),
            roleTargets = mapOf(
                "ramp" to RoleTarget(8, 11, 14),
                "card_draw" to RoleTarget(8, 10, 13),
                "removal_spot" to RoleTarget(6, 8, 11),
                "removal_mass" to RoleTarget(2, 3, 5),
                "finisher" to RoleTarget(6, 8, 11),
                "recursion" to RoleTarget(0, 2, 5),
                "tutor" to RoleTarget(0, 2, 6),
            ),
            antiRoles = emptySet(),
            curve = CurveBand(2.6, 3.1, 3.6),
            shape = CurveShape.BELL,
        )
        ArchetypeFormat.SIXTY -> ArchetypeDefinition(
            id = ArchetypeId.GENERIC,
            format = ArchetypeFormat.SIXTY,
            lands = RoleTarget(23, 24, 26),
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
    // ─────────────────────────────────────────────────────────────────────────

    val ARCHETYPES: Map<ArchetypeId, Map<ArchetypeFormat, ArchetypeDefinition>> = mapOf(
        ArchetypeId.AGGRO to mapOf(
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.AGGRO, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(30, 32, 35),
                roleTargets = mapOf(
                    "ramp" to RoleTarget(3, 5, 8),
                    "card_draw" to RoleTarget(5, 7, 10),
                    "threat_early" to RoleTarget(12, 16, 22),
                    "finisher" to RoleTarget(6, 9, 12),
                    "removal_spot" to RoleTarget(4, 6, 9),
                    "removal_mass" to RoleTarget(0, 0, 1),
                ),
                antiRoles = setOf("removal_mass"),
                curve = CurveBand(1.9, 2.3, 2.7),
                shape = CurveShape.FRONT,
            ),
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.AGGRO, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(19, 21, 23),
                roleTargets = mapOf(
                    "threat_early" to RoleTarget(14, 18, 26),
                    "finisher" to RoleTarget(10, 15, 26),
                    "removal_spot" to RoleTarget(2, 5, 10),
                    "card_draw" to RoleTarget(0, 2, 6),
                    "removal_mass" to RoleTarget(0, 0, 0),
                ),
                antiRoles = setOf("removal_mass"),
                curve = CurveBand(1.3, 1.8, 2.3),
                shape = CurveShape.FRONT,
            ),
        ),
        ArchetypeId.CONTROL to mapOf(
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.CONTROL, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(34, 37, 40),
                roleTargets = mapOf(
                    "removal_spot" to RoleTarget(9, 12, 16),
                    "removal_mass" to RoleTarget(4, 5, 7),
                    "counterspell" to RoleTarget(5, 8, 14),
                    "card_draw" to RoleTarget(10, 12, 16),
                    "finisher" to RoleTarget(4, 6, 8),
                    "ramp" to RoleTarget(8, 10, 12),
                    "threat_early" to RoleTarget(0, 0, 4),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.8, 3.3, 3.9),
                shape = CurveShape.BACK,
            ),
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.CONTROL, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(25, 26, 28),
                roleTargets = mapOf(
                    "counterspell" to RoleTarget(6, 9, 14),
                    "removal_spot" to RoleTarget(5, 8, 12),
                    "removal_mass" to RoleTarget(3, 4, 6),
                    "card_draw" to RoleTarget(6, 9, 12),
                    "finisher" to RoleTarget(3, 5, 8),
                    "threat_early" to RoleTarget(0, 0, 3),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.6, 3.1, 3.8),
                shape = CurveShape.BACK,
            ),
        ),
        ArchetypeId.MIDRANGE to mapOf(
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.MIDRANGE, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(34, 36, 39),
                roleTargets = mapOf(
                    "finisher" to RoleTarget(8, 10, 13),
                    "removal_spot" to RoleTarget(7, 9, 12),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.7, 3.1, 3.5),
                shape = CurveShape.BELL,
            ),
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.MIDRANGE, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(23, 24, 26),
                roleTargets = mapOf(
                    "removal_spot" to RoleTarget(8, 11, 15),
                    "finisher" to RoleTarget(10, 14, 18),
                    "threat_early" to RoleTarget(4, 7, 11),
                    "card_draw" to RoleTarget(2, 4, 8),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.4, 2.9, 3.4),
                shape = CurveShape.BELL,
            ),
        ),
        ArchetypeId.TEMPO to mapOf(
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.TEMPO, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(32, 34, 37),
                roleTargets = mapOf(
                    "threat_early" to RoleTarget(8, 12, 16),
                    "counterspell" to RoleTarget(6, 9, 12),
                    "removal_spot" to RoleTarget(5, 7, 10),
                    "card_draw" to RoleTarget(8, 10, 12),
                    "ramp" to RoleTarget(5, 7, 10),
                    "removal_mass" to RoleTarget(0, 1, 2),
                ),
                antiRoles = setOf("removal_mass"),
                curve = CurveBand(2.1, 2.5, 2.9),
                shape = CurveShape.FRONT,
            ),
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.TEMPO, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(20, 22, 24),
                roleTargets = mapOf(
                    "threat_early" to RoleTarget(8, 12, 16),
                    "counterspell" to RoleTarget(6, 9, 12),
                    "removal_spot" to RoleTarget(4, 6, 10),
                    "card_draw" to RoleTarget(4, 7, 10),
                    "removal_mass" to RoleTarget(0, 0, 1),
                ),
                antiRoles = setOf("removal_mass"),
                curve = CurveBand(1.6, 2.0, 2.5),
                shape = CurveShape.FRONT,
            ),
        ),
        ArchetypeId.COMBO to mapOf(
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.COMBO, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(31, 34, 38),
                roleTargets = mapOf(
                    "tutor" to RoleTarget(6, 9, 14),
                    "card_draw" to RoleTarget(10, 13, 16),
                    "protection" to RoleTarget(4, 6, 10),
                    "finisher" to RoleTarget(3, 5, 8),
                    "removal_spot" to RoleTarget(4, 6, 9),
                    "ramp" to RoleTarget(9, 12, 15),
                    "removal_mass" to RoleTarget(0, 1, 3),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.0, 2.6, 3.2),
                shape = CurveShape.FRONT,
            ),
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.COMBO, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(20, 22, 25),
                roleTargets = mapOf(
                    "tutor" to RoleTarget(4, 7, 12),
                    "card_draw" to RoleTarget(8, 12, 18),
                    "protection" to RoleTarget(2, 4, 8),
                    "finisher" to RoleTarget(4, 7, 10),
                    "removal_spot" to RoleTarget(0, 3, 8),
                    "threat_early" to RoleTarget(0, 0, 8),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(1.8, 2.3, 3.0),
                shape = CurveShape.FRONT,
            ),
        ),
        ArchetypeId.RAMP to mapOf(
            ArchetypeFormat.COMMANDER to ArchetypeDefinition(
                id = ArchetypeId.RAMP, format = ArchetypeFormat.COMMANDER,
                lands = RoleTarget(35, 38, 41),
                roleTargets = mapOf(
                    "ramp" to RoleTarget(12, 15, 20),
                    "finisher" to RoleTarget(8, 11, 14),
                    "card_draw" to RoleTarget(8, 10, 13),
                    "removal_mass" to RoleTarget(2, 4, 6),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(3.2, 3.7, 4.3),
                shape = CurveShape.BACK,
            ),
            ArchetypeFormat.SIXTY to ArchetypeDefinition(
                id = ArchetypeId.RAMP, format = ArchetypeFormat.SIXTY,
                lands = RoleTarget(24, 26, 28),
                roleTargets = mapOf(
                    "ramp" to RoleTarget(8, 11, 14),
                    "finisher" to RoleTarget(8, 11, 14),
                    "removal_mass" to RoleTarget(2, 4, 6),
                    "card_draw" to RoleTarget(3, 5, 8),
                    "threat_early" to RoleTarget(0, 2, 6),
                ),
                antiRoles = emptySet(),
                curve = CurveBand(2.8, 3.3, 4.0),
                shape = CurveShape.BACK,
            ),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  themes.<THEME> (A.4 step 3 — adds merge max-per-bound, relaxes replace)
    // ─────────────────────────────────────────────────────────────────────────

    val THEMES: Map<ThemeId, ThemeDefinition> = mapOf(
        ThemeId.REANIMATOR to ThemeDefinition(
            id = ThemeId.REANIMATOR,
            adds = mapOf(
                "graveyard_enabler" to RoleTarget(7, 10, 14),
                "reanimation" to RoleTarget(6, 9, 13),
                "finisher" to RoleTarget(7, 10, 13),
                "recursion" to RoleTarget(2, 4, 7),
            ),
            sixtyScale = 0.55,
            curveExemption = CurveExemption.REANIMATOR_HIGH_MV,
        ),
        ThemeId.SELF_MILL to ThemeDefinition(
            id = ThemeId.SELF_MILL,
            adds = mapOf(
                "graveyard_enabler" to RoleTarget(8, 12, 16),
                "self_mill_payoff" to RoleTarget(10, 14, 20),
                "recursion" to RoleTarget(4, 6, 10),
            ),
            sixtyScale = 0.55,
            overlayRoles = setOf("self_mill_payoff"),
        ),
        ThemeId.ARISTOCRATS to ThemeDefinition(
            id = ThemeId.ARISTOCRATS,
            adds = mapOf(
                "sac_outlet" to RoleTarget(6, 9, 13),
                "death_payoff" to RoleTarget(8, 11, 15),
                "token_generator" to RoleTarget(7, 10, 14),
                "recursion" to RoleTarget(3, 5, 8),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("death_payoff"),
        ),
        ThemeId.TOKENS to ThemeDefinition(
            id = ThemeId.TOKENS,
            adds = mapOf(
                "token_generator" to RoleTarget(12, 16, 22),
                "counters_payoff" to RoleTarget(0, 3, 8),
                // Transcription note: a stray zero-band documentation artifact in the annex — see
                // this file's header KDoc. Inert (min 0 never warns; not in OVERLAY_ROLES).
                "removal_mass_own_note" to RoleTarget(0, 0, 0),
                "finisher" to RoleTarget(6, 9, 12),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("counters_payoff"),
        ),
        ThemeId.SPELLSLINGER to ThemeDefinition(
            id = ThemeId.SPELLSLINGER,
            adds = mapOf(
                "spell_payoff" to RoleTarget(8, 12, 16),
                "card_draw" to RoleTarget(10, 13, 17),
                "counterspell" to RoleTarget(4, 7, 11),
                "removal_spot" to RoleTarget(7, 9, 12),
            ),
            sixtyScale = 0.55,
            overlayRoles = setOf("spell_payoff"),
        ),
        ThemeId.VOLTRON to ThemeDefinition(
            id = ThemeId.VOLTRON,
            adds = mapOf(
                "equipment_or_aura" to RoleTarget(14, 18, 24),
                "protection" to RoleTarget(6, 9, 12),
                "evasion" to RoleTarget(4, 6, 9),
                "tutor" to RoleTarget(2, 4, 8),
            ),
            relaxes = mapOf(
                "finisher" to RoleTarget(0, 2, 5),
                "threat_early" to RoleTarget(0, 0, 99),
            ),
            sixtyScale = 0.55,
            landsDelta = -2,
            curveDelta = -0.4,
        ),
        ThemeId.STAX to ThemeDefinition(
            id = ThemeId.STAX,
            adds = mapOf(
                "stax_piece" to RoleTarget(10, 14, 20),
                "protection" to RoleTarget(3, 5, 8),
                "finisher" to RoleTarget(3, 5, 8),
            ),
            relaxes = mapOf("card_draw" to RoleTarget(6, 8, 12)),
            sixtyScale = 0.6,
            curveDelta = -0.2,
        ),
        ThemeId.LANDFALL to ThemeDefinition(
            id = ThemeId.LANDFALL,
            adds = mapOf(
                "landfall_payoff" to RoleTarget(8, 11, 15),
                "ramp" to RoleTarget(12, 16, 22),
                "recursion" to RoleTarget(2, 4, 7),
            ),
            sixtyScale = 0.5,
            landsDelta = 2,
            overlayRoles = setOf("landfall_payoff"),
        ),
        ThemeId.LIFEGAIN to ThemeDefinition(
            id = ThemeId.LIFEGAIN,
            adds = mapOf(
                "lifegain_payoff" to RoleTarget(8, 11, 15),
                "token_generator" to RoleTarget(0, 4, 9),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("lifegain_payoff"),
        ),
        ThemeId.PLUS1_COUNTERS to ThemeDefinition(
            id = ThemeId.PLUS1_COUNTERS,
            adds = mapOf(
                "counters_payoff" to RoleTarget(10, 14, 19),
                "protection" to RoleTarget(2, 4, 7),
            ),
            sixtyScale = 0.5,
            overlayRoles = setOf("counters_payoff"),
        ),
        ThemeId.TRIBAL to ThemeDefinition(
            id = ThemeId.TRIBAL,
            adds = mapOf(
                "tribe_members" to RoleTarget(22, 28, 36),
                "tribe_payoff" to RoleTarget(6, 9, 13),
            ),
            sixtyScale = 0.55,
            overlayRoles = setOf("tribe_members", "tribe_payoff"),
        ),
        ThemeId.ARTIFACTS to ThemeDefinition(
            id = ThemeId.ARTIFACTS,
            adds = mapOf(
                "artifact_payoff" to RoleTarget(8, 12, 16),
                "ramp" to RoleTarget(10, 13, 17),
            ),
            sixtyScale = 0.55,
            overlayRoles = setOf("artifact_payoff"),
        ),
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
        ThemeId.MILL to ThemeDefinition(
            id = ThemeId.MILL,
            adds = mapOf(
                "mill_engine" to RoleTarget(10, 14, 18),
                "finisher" to RoleTarget(4, 6, 9),
            ),
            sixtyScale = 0.6,
        ),
        ThemeId.GROUP_HUG to ThemeDefinition(
            id = ThemeId.GROUP_HUG,
            adds = mapOf(
                "group_effect" to RoleTarget(10, 14, 18),
                "finisher" to RoleTarget(2, 4, 7),
            ),
            commanderOnly = true,
            overlayRoles = setOf("group_effect"),
        ),
        ThemeId.GROUP_SLUG to ThemeDefinition(
            id = ThemeId.GROUP_SLUG,
            adds = mapOf(
                "group_effect" to RoleTarget(10, 14, 18),
                "lifegain_payoff" to RoleTarget(0, 2, 6),
            ),
            commanderOnly = true,
            overlayRoles = setOf("group_effect", "lifegain_payoff"),
        ),
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
        ThemeId.VEHICLES to ThemeDefinition(
            id = ThemeId.VEHICLES,
            adds = mapOf(
                "vehicle" to RoleTarget(8, 11, 15),
                "threat_early" to RoleTarget(10, 14, 18),
            ),
            sixtyScale = 0.6,
            overlayRoles = setOf("vehicle"),
        ),
        ThemeId.TOOLBOX to ThemeDefinition(
            id = ThemeId.TOOLBOX,
            adds = mapOf(
                "tutor" to RoleTarget(8, 12, 18),
                "finisher" to RoleTarget(4, 6, 9),
            ),
            sixtyScale = 0.5,
        ),
        ThemeId.CLONES_THEFT to ThemeDefinition(
            id = ThemeId.CLONES_THEFT,
            adds = mapOf(
                "clone_theft_effect" to RoleTarget(10, 14, 18),
                "sac_outlet" to RoleTarget(0, 3, 7),
            ),
            commanderOnly = true,
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  color_modulation (A.5). Bucketed by color COUNT: 1, 2, 3, or 4+ ("4-5").
    // ─────────────────────────────────────────────────────────────────────────

    /** Maps a raw color-identity count to the annex's bucket code (1/2/3/4 == "4-5"). */
    fun colorCountBucket(colorCount: Int): Int = colorCount.coerceIn(1, 4)

    val COLOR_MODULATION: Map<ArchetypeFormat, Map<Int, ColorModulationEntry>> = mapOf(
        ArchetypeFormat.COMMANDER to mapOf(
            1 to ColorModulationEntry(RoleTarget(0, 0, 3), landsDelta = 0),
            2 to ColorModulationEntry(RoleTarget(3, 6, 10), landsDelta = 0),
            3 to ColorModulationEntry(RoleTarget(8, 12, 16), landsDelta = 0),
            4 to ColorModulationEntry(RoleTarget(12, 16, 22), landsDelta = 1),
        ),
        ArchetypeFormat.SIXTY to mapOf(
            1 to ColorModulationEntry(RoleTarget(0, 0, 2), landsDelta = 0),
            2 to ColorModulationEntry(RoleTarget(4, 8, 12), landsDelta = 0),
            3 to ColorModulationEntry(RoleTarget(8, 12, 16), landsDelta = 1),
            4 to ColorModulationEntry(RoleTarget(12, 16, 20), landsDelta = 2),
        ),
    )
}
