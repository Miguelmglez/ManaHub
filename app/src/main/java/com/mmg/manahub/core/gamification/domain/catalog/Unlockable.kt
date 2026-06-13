package com.mmg.manahub.core.gamification.domain.catalog

import androidx.annotation.StringRes

/**
 * Cosmetic domain models for the Phase-3 unlockables system (ADR-002 §10).
 *
 * Everything in this file is a PURE, serialisable data table — NO Room, Android `Context`, or Compose
 * imports. Color choices are expressed as [CosmeticColorToken] enum references, NOT raw `Color`/hex, so
 * the Chunk-B renderer resolves them against `MaterialTheme.magicColors` at draw time and every cosmetic
 * adapts to all 12 themes (ADR-002 §10: "cosmetics must look correct in all 12 themes").
 *
 * Ownership is persisted in `entitlements` (keyed by [UnlockableId.value]); equipped selection lives in
 * DataStore. The catalog ([UnlockableCatalog]) is the source of truth for what exists and how each item
 * is unlocked.
 */

/** The four kinds of cosmetic a player can earn and equip. */
enum class UnlockableKind {
    /** A styled display name shown next to the player (e.g. "Aggressor"). */
    TITLE,

    /** A small glyph-in-a-frame emblem; up to 3 can be equipped at once. */
    BADGE,

    /** A decorative ring drawn around the avatar. */
    AVATAR_FRAME,

    /** A visual style applied to the level-progress ring (e.g. metallic, foil). */
    LEVEL_RING_STYLE,
}

/**
 * The condition that grants an [Unlockable].
 *
 * Kept as a sealed data table (no behavior) so the catalog stays pure; the
 * [com.mmg.manahub.core.gamification.engine.EntitlementGranter] interprets these against the current
 * player state. Chunk B reads the same rule to format a human "how to unlock" hint for locked items.
 */
sealed interface UnlockRule {

    /** Granted once the player reaches [level] (or higher). */
    data class LevelAtLeast(val level: Int) : UnlockRule

    /**
     * Granted once the achievement with [achievementId] is unlocked (any tier).
     *
     * [achievementId] MUST be a real, stable id in
     * [com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog] — validated by
     * `UnlockableCatalogTest`. Never reference an invented id.
     */
    data class AchievementUnlocked(val achievementId: String) : UnlockRule

    /**
     * Evaluates this rule against the current player state.
     *
     * @param currentLevel the player's current level.
     * @param unlockedAchievementIds the set of achievement ids that are unlocked.
     * @return true if the unlockable should be granted.
     */
    fun isSatisfied(currentLevel: Int, unlockedAchievementIds: Set<String>): Boolean = when (this) {
        is LevelAtLeast -> currentLevel >= level
        is AchievementUnlocked -> achievementId in unlockedAchievementIds
    }
}

/**
 * A semantic color slot resolved to the active theme's [com.mmg.manahub.core.ui.theme.MagicColors] at
 * render time (Chunk B). Never a raw `Color` — this is what lets a single cosmetic look correct across
 * all 12 themes.
 *
 * The mana tokens map onto `MagicColors.manaW/U/B/R/G` (every theme defines all five). Note `MANA_B`
 * (black) is a near-background dark in every palette, so the Chunk-B renderer should outline/contrast a
 * black-mana badge rather than fill it flat (documented here so the mapping is unambiguous).
 */
enum class CosmeticColorToken {
    /** `MagicColors.primaryAccent`. */
    PRIMARY_ACCENT,

    /** `MagicColors.goldMtg`. */
    GOLD,

    /** `MagicColors.lifePositive` (semantic "win/positive" green). */
    LIFE_POSITIVE,

    /** `MagicColors.lifeNegative` (semantic "loss/negative" red). */
    LIFE_NEGATIVE,

    /** `MagicColors.textPrimary`. */
    TEXT_PRIMARY,

    /** `MagicColors.textSecondary`. */
    TEXT_SECONDARY,

    /** `MagicColors.surface`. */
    SURFACE,

    /** White mana → `MagicColors.manaW`. */
    MANA_W,

    /** Blue mana → `MagicColors.manaU`. */
    MANA_U,

    /** Black mana → `MagicColors.manaB` (near-background dark; render with contrast — see class KDoc). */
    MANA_B,

    /** Red mana → `MagicColors.manaR`. */
    MANA_R,

    /** Green mana → `MagicColors.manaG`. */
    MANA_G,
}

/** Frame shape for a [UnlockableKind.BADGE] emblem. */
enum class BadgeFrameShape { CIRCLE, SHIELD, HEX }

/** Visual style for a [UnlockableKind.LEVEL_RING_STYLE]. */
enum class RingStyle { SOLID, GRADIENT_SWEEP, METALLIC, FOIL }

/** Visual style/material for a [UnlockableKind.AVATAR_FRAME]. */
enum class FrameStyle { BRONZE, SILVER, GOLD, FOIL }

/** Visual style for a [UnlockableKind.TITLE]'s text. */
enum class TitleStyle { PLAIN, GRADIENT }

/**
 * A small, serialisable description the Chunk-B renderer interprets to draw a cosmetic procedurally
 * (ADR-002 §10 — zero image assets in v1).
 *
 * Only the fields relevant to a given [UnlockableKind] are populated; the renderer reads the ones it
 * needs (e.g. a TITLE uses [primaryToken] + [titleStyle]; a BADGE uses [glyph] + [primaryToken] +
 * [badgeShape]). All color is via [CosmeticColorToken] (resolved later), never hardcoded.
 *
 * @param primaryToken the dominant color slot.
 * @param secondaryToken an optional second color slot (gradients, two-tone frames).
 * @param badgeShape frame shape for BADGE kinds.
 * @param ringStyle style for LEVEL_RING_STYLE kinds.
 * @param frameStyle style/material for AVATAR_FRAME kinds.
 * @param titleStyle text style for TITLE kinds.
 * @param glyph an optional emoji/mana symbol drawn inside a badge (PII-free, English-agnostic).
 * @param gradient convenience flag: true when the renderer should blend [primaryToken]→[secondaryToken].
 */
data class RenderSpec(
    val primaryToken: CosmeticColorToken,
    val secondaryToken: CosmeticColorToken? = null,
    val badgeShape: BadgeFrameShape? = null,
    val ringStyle: RingStyle? = null,
    val frameStyle: FrameStyle? = null,
    val titleStyle: TitleStyle? = null,
    val glyph: String? = null,
    val gradient: Boolean = false,
)

/**
 * Immutable definition of one unlockable cosmetic.
 *
 * @param id STABLE catalog id (the [UnlockableId.value] string). This is the persisted PK in
 *   `entitlements` AND the Phase-4 sync key — NEVER rename a shipped id.
 * @param kind which cosmetic family this is.
 * @param displayNameRes English display name (string resource).
 * @param renderSpec procedural render description for Chunk B.
 * @param unlockRule the condition that grants this item.
 * @param sortOrder display order within its [kind] (ascending); ties broken by id.
 */
data class Unlockable(
    val id: UnlockableId,
    val kind: UnlockableKind,
    @StringRes val displayNameRes: Int,
    val renderSpec: RenderSpec,
    val unlockRule: UnlockRule,
    val sortOrder: Int = 0,
)
