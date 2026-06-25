package com.mmg.manahub.core.gamification.domain.catalog

import com.mmg.manahub.core.gamification.domain.catalog.UnlockableCatalog.all

/**
 * The immutable, code-side catalog of all unlockable cosmetics (ADR-002 §10, Phase 3).
 *
 * ### Source of truth
 * [all] is the single source of truth for what cosmetics exist and how each is unlocked. The
 * [com.mmg.manahub.core.gamification.engine.EntitlementGranter] evaluates [Unlockable.unlockRule]
 * against player state; the Rewards UI (Chunk B) drives entirely off this list so locked items still
 * render with an "how to unlock" hint.
 *
 * ### Stable ids
 * Every [UnlockableId.value] is a STABLE persisted PK in `entitlements` and a Phase-4 sync key — chosen
 * final here and NEVER renamed (mirrors the [AchievementCatalog] stable-id discipline).
 *
 * ### Grandfathering (ADR-002 §"Grandfathers existing content")
 * There are deliberately NO theme unlockables here: the 12 existing `AppTheme` palettes stay free and
 * untouched. Only these procedural cosmetics are born locked.
 *
 * ### Achievement references
 * Every [UnlockRule.AchievementUnlocked] references a REAL stable id in [AchievementCatalog]
 * (validated by `UnlockableCatalogTest`). The four PlayStyle titles unlock by LEVEL (PlayStyle is a
 * derived label, not an achievement), so they are reachable by any player without a specific feat.
 */
object UnlockableCatalog {

    /**
     * The full catalog (~20 items). Order here is irrelevant; the UI groups by [Unlockable.kind] and
     * sorts by [Unlockable.sortOrder].
     */
    val all: List<Unlockable> = buildList {

        // ── TITLES — PlayStyle (level-gated; PlayStyle is derived, not an achievement) ──────────────
        // The four play-style archetypes mirror ProfileViewModel.PlayStyle. Low level gates so any
        // engaged player earns their identity title early. Gradient title style.
        add(
            title(
                id = "title_aggressor", displayName = "Aggressor",
                primary = CosmeticColorToken.LIFE_NEGATIVE, secondary = CosmeticColorToken.GOLD,
                rule = UnlockRule.LevelAtLeast(2), sortOrder = 0,
            )
        )
        add(
            title(
                id = "title_strategist", displayName = "Strategist",
                primary = CosmeticColorToken.MANA_U, secondary = CosmeticColorToken.TEXT_PRIMARY,
                rule = UnlockRule.LevelAtLeast(3), sortOrder = 1,
            )
        )
        add(
            title(
                id = "title_midrange", displayName = "Midrange",
                primary = CosmeticColorToken.MANA_G, secondary = CosmeticColorToken.GOLD,
                rule = UnlockRule.LevelAtLeast(4), sortOrder = 2,
            )
        )
        add(
            title(
                id = "title_balanced", displayName = "Balanced",
                primary = CosmeticColorToken.PRIMARY_ACCENT, secondary = CosmeticColorToken.TEXT_SECONDARY,
                rule = UnlockRule.LevelAtLeast(5), sortOrder = 3,
            )
        )

        // ── TITLES — achievement-line (reference REAL AchievementCatalog ids) ───────────────────────
        add(
            title(
                id = "title_collector", displayName = "Collector",
                primary = CosmeticColorToken.GOLD, secondary = CosmeticColorToken.TEXT_PRIMARY,
                rule = UnlockRule.AchievementUnlocked("COLLECTOR_50"), sortOrder = 10,
            )
        )
        add(
            title(
                id = "title_deck_architect", displayName = "Deck Architect",
                primary = CosmeticColorToken.PRIMARY_ACCENT, secondary = CosmeticColorToken.MANA_U,
                rule = UnlockRule.AchievementUnlocked("DECK_BUILDER"), sortOrder = 11,
            )
        )
        add(
            title(
                id = "title_tournament_champion", displayName = "Tournament Champion",
                primary = CosmeticColorToken.GOLD, secondary = CosmeticColorToken.LIFE_NEGATIVE,
                rule = UnlockRule.AchievementUnlocked("TOURNAMENT_WIN"), sortOrder = 12,
            )
        )
        add(
            title(
                id = "title_survey_sage", displayName = "Survey Sage",
                primary = CosmeticColorToken.MANA_U, secondary = CosmeticColorToken.LIFE_POSITIVE,
                rule = UnlockRule.AchievementUnlocked("SURVEY_VETERAN"), sortOrder = 13,
            )
        )

        // ── AVATAR FRAMES — level-gated material ramp (bronze→silver→gold→foil) ─────────────────────
        add(
            frame(
                id = "frame_bronze", displayName = "Bronze Frame",
                token = CosmeticColorToken.GOLD, style = FrameStyle.BRONZE,
                rule = UnlockRule.LevelAtLeast(5), sortOrder = 0,
            )
        )
        add(
            frame(
                id = "frame_silver", displayName = "Silver Frame",
                token = CosmeticColorToken.TEXT_SECONDARY, style = FrameStyle.SILVER,
                rule = UnlockRule.LevelAtLeast(10), sortOrder = 1,
            )
        )
        add(
            frame(
                id = "frame_gold", displayName = "Gold Frame",
                token = CosmeticColorToken.GOLD, style = FrameStyle.GOLD,
                rule = UnlockRule.LevelAtLeast(20), sortOrder = 2,
            )
        )
        add(
            frame(
                id = "frame_foil", displayName = "Foil Frame",
                token = CosmeticColorToken.PRIMARY_ACCENT, style = FrameStyle.FOIL,
                rule = UnlockRule.LevelAtLeast(35), sortOrder = 3,
            )
        )

        // ── LEVEL RING STYLES — level-gated ─────────────────────────────────────────────────────────
        add(
            ring(
                id = "ring_gradient_sweep", displayName = "Gradient Ring",
                primary = CosmeticColorToken.PRIMARY_ACCENT, secondary = CosmeticColorToken.GOLD,
                style = RingStyle.GRADIENT_SWEEP, rule = UnlockRule.LevelAtLeast(8), sortOrder = 0,
            )
        )
        add(
            ring(
                id = "ring_metallic", displayName = "Metallic Ring",
                primary = CosmeticColorToken.GOLD, secondary = CosmeticColorToken.TEXT_SECONDARY,
                style = RingStyle.METALLIC, rule = UnlockRule.LevelAtLeast(15), sortOrder = 1,
            )
        )
        add(
            ring(
                id = "ring_foil", displayName = "Foil Ring",
                primary = CosmeticColorToken.PRIMARY_ACCENT, secondary = CosmeticColorToken.MANA_U,
                style = RingStyle.FOIL, rule = UnlockRule.LevelAtLeast(30), sortOrder = 2,
            )
        )

        // ── BADGES — achievement-tied milestones ────────────────────────────────────────────────────
        add(
            badge(
                id = "badge_first_win", displayName = "First Win",
                token = CosmeticColorToken.LIFE_NEGATIVE, shape = BadgeFrameShape.SHIELD, glyph = "⚔️",
                rule = UnlockRule.AchievementUnlocked("FIRST_WIN"), sortOrder = 0,
            )
        )
        add(
            badge(
                id = "badge_veteran", displayName = "Veteran",
                token = CosmeticColorToken.GOLD, shape = BadgeFrameShape.HEX, glyph = "🏆",
                rule = UnlockRule.AchievementUnlocked("WINS_TIERED"), sortOrder = 1,
            )
        )

        // ── BADGES — WUBRG mana (level-gated, modest levels) ─────────────────────────────────────────
        add(manaBadge("badge_mana_w", "White Mana", CosmeticColorToken.MANA_W, "☀️", level = 2, sortOrder = 10))
        add(manaBadge("badge_mana_u", "Blue Mana", CosmeticColorToken.MANA_U, "💧", level = 3, sortOrder = 11))
        add(manaBadge("badge_mana_b", "Black Mana", CosmeticColorToken.MANA_B, "💀", level = 4, sortOrder = 12))
        add(manaBadge("badge_mana_r", "Red Mana", CosmeticColorToken.MANA_R, "🔥", level = 5, sortOrder = 13))
        add(manaBadge("badge_mana_g", "Green Mana", CosmeticColorToken.MANA_G, "🌳", level = 6, sortOrder = 14))
    }

    /** Looks up an unlockable by its stable id string, or null if unknown. */
    fun byId(id: String): Unlockable? = byIdMap[id]

    /** Convenience overload for the value-class id. */
    fun byId(id: UnlockableId): Unlockable? = byIdMap[id.value]

    private val byIdMap: Map<String, Unlockable> = all.associateBy { it.id.value }

    /**
     * Catalog grouped by kind, each group sorted by [Unlockable.sortOrder] then id. Built once; used by
     * the Rewards UI to render one section per kind.
     */
    val byKind: Map<UnlockableKind, List<Unlockable>> =
        all.groupBy { it.kind }
            .mapValues { (_, items) -> items.sortedWith(compareBy({ it.sortOrder }, { it.id.value })) }

    // ── Builders (keep the catalog declarations terse and consistent) ───────────────────────────────

    private fun title(
        id: String,
        displayName: String,
        primary: CosmeticColorToken,
        secondary: CosmeticColorToken,
        rule: UnlockRule,
        sortOrder: Int,
    ): Unlockable = Unlockable(
        id = UnlockableId(id),
        kind = UnlockableKind.TITLE,
        displayName = displayName,
        renderSpec = RenderSpec(
            primaryToken = primary,
            secondaryToken = secondary,
            titleStyle = TitleStyle.GRADIENT,
            gradient = true,
        ),
        unlockRule = rule,
        sortOrder = sortOrder,
    )

    private fun frame(
        id: String,
        displayName: String,
        token: CosmeticColorToken,
        style: FrameStyle,
        rule: UnlockRule,
        sortOrder: Int,
    ): Unlockable = Unlockable(
        id = UnlockableId(id),
        kind = UnlockableKind.AVATAR_FRAME,
        displayName = displayName,
        renderSpec = RenderSpec(
            primaryToken = token,
            secondaryToken = CosmeticColorToken.SURFACE,
            frameStyle = style,
            gradient = style == FrameStyle.FOIL,
        ),
        unlockRule = rule,
        sortOrder = sortOrder,
    )

    private fun ring(
        id: String,
        displayName: String,
        primary: CosmeticColorToken,
        secondary: CosmeticColorToken,
        style: RingStyle,
        rule: UnlockRule,
        sortOrder: Int,
    ): Unlockable = Unlockable(
        id = UnlockableId(id),
        kind = UnlockableKind.LEVEL_RING_STYLE,
        displayName = displayName,
        renderSpec = RenderSpec(
            primaryToken = primary,
            secondaryToken = secondary,
            ringStyle = style,
            gradient = style != RingStyle.SOLID,
        ),
        unlockRule = rule,
        sortOrder = sortOrder,
    )

    private fun badge(
        id: String,
        displayName: String,
        token: CosmeticColorToken,
        shape: BadgeFrameShape,
        glyph: String,
        rule: UnlockRule,
        sortOrder: Int,
    ): Unlockable = Unlockable(
        id = UnlockableId(id),
        kind = UnlockableKind.BADGE,
        displayName = displayName,
        renderSpec = RenderSpec(
            primaryToken = token,
            secondaryToken = CosmeticColorToken.SURFACE,
            badgeShape = shape,
            glyph = glyph,
        ),
        unlockRule = rule,
        sortOrder = sortOrder,
    )

    /** A WUBRG mana badge: a CIRCLE frame in the matching mana token with the mana glyph, level-gated. */
    private fun manaBadge(
        id: String,
        displayName: String,
        token: CosmeticColorToken,
        glyph: String,
        level: Int,
        sortOrder: Int,
    ): Unlockable = badge(
        id = id,
        displayName = displayName,
        token = token,
        shape = BadgeFrameShape.CIRCLE,
        glyph = glyph,
        rule = UnlockRule.LevelAtLeast(level),
        sortOrder = sortOrder,
    )
}
