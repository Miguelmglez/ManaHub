package com.mmg.manahub.core.gamification.domain.catalog

import com.mmg.manahub.R
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
                id = "title_aggressor", nameRes = R.string.reward_title_aggressor,
                primary = CosmeticColorToken.LIFE_NEGATIVE, secondary = CosmeticColorToken.GOLD,
                rule = UnlockRule.LevelAtLeast(2), sortOrder = 0,
            )
        )
        add(
            title(
                id = "title_strategist", nameRes = R.string.reward_title_strategist,
                primary = CosmeticColorToken.MANA_U, secondary = CosmeticColorToken.TEXT_PRIMARY,
                rule = UnlockRule.LevelAtLeast(3), sortOrder = 1,
            )
        )
        add(
            title(
                id = "title_midrange", nameRes = R.string.reward_title_midrange,
                primary = CosmeticColorToken.MANA_G, secondary = CosmeticColorToken.GOLD,
                rule = UnlockRule.LevelAtLeast(4), sortOrder = 2,
            )
        )
        add(
            title(
                id = "title_balanced", nameRes = R.string.reward_title_balanced,
                primary = CosmeticColorToken.PRIMARY_ACCENT, secondary = CosmeticColorToken.TEXT_SECONDARY,
                rule = UnlockRule.LevelAtLeast(5), sortOrder = 3,
            )
        )

        // ── TITLES — achievement-line (reference REAL AchievementCatalog ids) ───────────────────────
        add(
            title(
                id = "title_collector", nameRes = R.string.reward_title_collector,
                primary = CosmeticColorToken.GOLD, secondary = CosmeticColorToken.TEXT_PRIMARY,
                rule = UnlockRule.AchievementUnlocked("COLLECTOR_50"), sortOrder = 10,
            )
        )
        add(
            title(
                id = "title_deck_architect", nameRes = R.string.reward_title_deck_architect,
                primary = CosmeticColorToken.PRIMARY_ACCENT, secondary = CosmeticColorToken.MANA_U,
                rule = UnlockRule.AchievementUnlocked("DECK_BUILDER"), sortOrder = 11,
            )
        )
        add(
            title(
                id = "title_tournament_champion", nameRes = R.string.reward_title_tournament_champion,
                primary = CosmeticColorToken.GOLD, secondary = CosmeticColorToken.LIFE_NEGATIVE,
                rule = UnlockRule.AchievementUnlocked("TOURNAMENT_WIN"), sortOrder = 12,
            )
        )
        add(
            title(
                id = "title_survey_sage", nameRes = R.string.reward_title_survey_sage,
                primary = CosmeticColorToken.MANA_U, secondary = CosmeticColorToken.LIFE_POSITIVE,
                rule = UnlockRule.AchievementUnlocked("SURVEY_VETERAN"), sortOrder = 13,
            )
        )

        // ── AVATAR FRAMES — level-gated material ramp (bronze→silver→gold→foil) ─────────────────────
        add(
            frame(
                id = "frame_bronze", nameRes = R.string.reward_frame_bronze,
                token = CosmeticColorToken.GOLD, style = FrameStyle.BRONZE,
                rule = UnlockRule.LevelAtLeast(5), sortOrder = 0,
            )
        )
        add(
            frame(
                id = "frame_silver", nameRes = R.string.reward_frame_silver,
                token = CosmeticColorToken.TEXT_SECONDARY, style = FrameStyle.SILVER,
                rule = UnlockRule.LevelAtLeast(10), sortOrder = 1,
            )
        )
        add(
            frame(
                id = "frame_gold", nameRes = R.string.reward_frame_gold,
                token = CosmeticColorToken.GOLD, style = FrameStyle.GOLD,
                rule = UnlockRule.LevelAtLeast(20), sortOrder = 2,
            )
        )
        add(
            frame(
                id = "frame_foil", nameRes = R.string.reward_frame_foil,
                token = CosmeticColorToken.PRIMARY_ACCENT, style = FrameStyle.FOIL,
                rule = UnlockRule.LevelAtLeast(35), sortOrder = 3,
            )
        )

        // ── LEVEL RING STYLES — level-gated ─────────────────────────────────────────────────────────
        add(
            ring(
                id = "ring_gradient_sweep", nameRes = R.string.reward_ring_gradient_sweep,
                primary = CosmeticColorToken.PRIMARY_ACCENT, secondary = CosmeticColorToken.GOLD,
                style = RingStyle.GRADIENT_SWEEP, rule = UnlockRule.LevelAtLeast(8), sortOrder = 0,
            )
        )
        add(
            ring(
                id = "ring_metallic", nameRes = R.string.reward_ring_metallic,
                primary = CosmeticColorToken.GOLD, secondary = CosmeticColorToken.TEXT_SECONDARY,
                style = RingStyle.METALLIC, rule = UnlockRule.LevelAtLeast(15), sortOrder = 1,
            )
        )
        add(
            ring(
                id = "ring_foil", nameRes = R.string.reward_ring_foil,
                primary = CosmeticColorToken.PRIMARY_ACCENT, secondary = CosmeticColorToken.MANA_U,
                style = RingStyle.FOIL, rule = UnlockRule.LevelAtLeast(30), sortOrder = 2,
            )
        )

        // ── BADGES — achievement-tied milestones ────────────────────────────────────────────────────
        add(
            badge(
                id = "badge_first_win", nameRes = R.string.reward_badge_first_win,
                token = CosmeticColorToken.LIFE_NEGATIVE, shape = BadgeFrameShape.SHIELD, glyph = "⚔️",
                rule = UnlockRule.AchievementUnlocked("FIRST_WIN"), sortOrder = 0,
            )
        )
        add(
            badge(
                id = "badge_veteran", nameRes = R.string.reward_badge_veteran,
                token = CosmeticColorToken.GOLD, shape = BadgeFrameShape.HEX, glyph = "🏆",
                rule = UnlockRule.AchievementUnlocked("WINS_TIERED"), sortOrder = 1,
            )
        )

        // ── BADGES — WUBRG mana (level-gated, modest levels) ─────────────────────────────────────────
        add(manaBadge("badge_mana_w", R.string.reward_badge_mana_w, CosmeticColorToken.MANA_W, "☀️", level = 2, sortOrder = 10))
        add(manaBadge("badge_mana_u", R.string.reward_badge_mana_u, CosmeticColorToken.MANA_U, "💧", level = 3, sortOrder = 11))
        add(manaBadge("badge_mana_b", R.string.reward_badge_mana_b, CosmeticColorToken.MANA_B, "💀", level = 4, sortOrder = 12))
        add(manaBadge("badge_mana_r", R.string.reward_badge_mana_r, CosmeticColorToken.MANA_R, "🔥", level = 5, sortOrder = 13))
        add(manaBadge("badge_mana_g", R.string.reward_badge_mana_g, CosmeticColorToken.MANA_G, "🌳", level = 6, sortOrder = 14))
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
        nameRes: Int,
        primary: CosmeticColorToken,
        secondary: CosmeticColorToken,
        rule: UnlockRule,
        sortOrder: Int,
    ): Unlockable = Unlockable(
        id = UnlockableId(id),
        kind = UnlockableKind.TITLE,
        displayNameRes = nameRes,
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
        nameRes: Int,
        token: CosmeticColorToken,
        style: FrameStyle,
        rule: UnlockRule,
        sortOrder: Int,
    ): Unlockable = Unlockable(
        id = UnlockableId(id),
        kind = UnlockableKind.AVATAR_FRAME,
        displayNameRes = nameRes,
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
        nameRes: Int,
        primary: CosmeticColorToken,
        secondary: CosmeticColorToken,
        style: RingStyle,
        rule: UnlockRule,
        sortOrder: Int,
    ): Unlockable = Unlockable(
        id = UnlockableId(id),
        kind = UnlockableKind.LEVEL_RING_STYLE,
        displayNameRes = nameRes,
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
        nameRes: Int,
        token: CosmeticColorToken,
        shape: BadgeFrameShape,
        glyph: String,
        rule: UnlockRule,
        sortOrder: Int,
    ): Unlockable = Unlockable(
        id = UnlockableId(id),
        kind = UnlockableKind.BADGE,
        displayNameRes = nameRes,
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
        nameRes: Int,
        token: CosmeticColorToken,
        glyph: String,
        level: Int,
        sortOrder: Int,
    ): Unlockable = badge(
        id = id,
        nameRes = nameRes,
        token = token,
        shape = BadgeFrameShape.CIRCLE,
        glyph = glyph,
        rule = UnlockRule.LevelAtLeast(level),
        sortOrder = sortOrder,
    )
}
