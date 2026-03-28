package com.mmg.magicfolder.core.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  PlayerThemeColors — identity tokens for a single player slot
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Color identity for one player slot in the life counter.
 *
 * @param accent    The player's signature color — used for the life total,
 *                  card border, and interactive elements.
 * @param background Dark tinted background for the player card.
 * @param glow      Translucent version of [accent] used for neon-glow effects
 *                  (inner shadows, bloom layers).
 * @param name      Human-readable name for this color identity (e.g. "Crimson").
 */
data class PlayerThemeColors(
    val accent:     Color,
    val background: Color,
    val glow:       Color,
    val name:       String,
)

// ═══════════════════════════════════════════════════════════════════════════════
//  MagicColors — full semantic token set
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Semantic color tokens for the ManaHub design system.
 *
 * Access via `MaterialTheme.magicColors.xxx` inside any `@Composable`.
 * Hard-coded `Color(0xFF…)` values are FORBIDDEN in composables.
 */
data class MagicColors(

    // ── Backgrounds ──────────────────────────────────────────────────────────
    /** Deepest background layer — behind all content. */
    val background: Color,
    /** Slightly lighter background used for alternating sections or drawers. */
    val backgroundSecondary: Color,
    /** Standard card / sheet surface. */
    val surface: Color,
    /** Elevated surface for dialogs, menus, bottom-sheet handles. */
    val surfaceVariant: Color,

    // ── Accents ───────────────────────────────────────────────────────────────
    /** Primary app accent — FAB, bottom-nav selection, progress rings. */
    val primaryAccent: Color,
    /** Secondary accent — highlights, secondary CTAs. */
    val secondaryAccent: Color,
    /** MTG gold — legendary frames, set symbols, premium elements. */
    val goldMtg: Color,

    // ── Text ──────────────────────────────────────────────────────────────────
    val textPrimary:   Color,
    val textSecondary: Color,
    val textDisabled:  Color,

    // ── Game — life counter ───────────────────────────────────────────────────
    /** Color for healing / life gain indicators (+N). */
    val lifePositive: Color,
    /** Color for damage / life loss indicators (−N). */
    val lifeNegative: Color,
    /** Poison counter chip color. */
    val poisonColor: Color,
    /** Commander-damage counter accent (matches [primaryAccent] in NeonVoid). */
    val commanderAccent: Color,

    // ── Mana symbols ──────────────────────────────────────────────────────────
    val manaW: Color,   // White mana
    val manaU: Color,   // Blue mana
    val manaB: Color,   // Black mana
    val manaR: Color,   // Red mana
    val manaG: Color,   // Green mana
    val manaC: Color,   // Colorless mana

    // ── Player identity ───────────────────────────────────────────────────────
    /**
     * Exactly 10 player color identities, indexed 0–9.
     * Access via `playerColors[playerIndex]`.
     * Supports games with up to 10 players.
     */
    val playerColors: List<PlayerThemeColors>,
)

// ═══════════════════════════════════════════════════════════════════════════════
//  NeonVoid instance
// ═══════════════════════════════════════════════════════════════════════════════

internal val NeonVoidColors = MagicColors(

    background          = NV_Background,
    backgroundSecondary = NV_BackgroundSecondary,
    surface             = NV_Surface,
    surfaceVariant      = NV_SurfaceVariant,

    primaryAccent       = NV_PrimaryAccent,
    secondaryAccent     = NV_SecondaryAccent,
    goldMtg             = NV_GoldMtg,

    textPrimary         = NV_TextPrimary,
    textSecondary       = NV_TextSecondary,
    textDisabled        = NV_TextDisabled,

    lifePositive        = NV_LifePositive,
    lifeNegative        = NV_LifeNegative,
    poisonColor         = NV_PoisonColor,
    commanderAccent     = NV_CommanderAccent,

    manaW               = NV_ManaW,
    manaU               = NV_ManaU,
    manaB               = NV_ManaB,
    manaR               = NV_ManaR,
    manaG               = NV_ManaG,
    manaC               = NV_ManaC,

    playerColors        = listOf(
        PlayerThemeColors(NV_P0_Accent, NV_P0_Bg, NV_P0_Accent.copy(alpha = 0.40f), "Crimson"),
        PlayerThemeColors(NV_P1_Accent, NV_P1_Bg, NV_P1_Accent.copy(alpha = 0.40f), "Azure"),
        PlayerThemeColors(NV_P2_Accent, NV_P2_Bg, NV_P2_Accent.copy(alpha = 0.40f), "Emerald"),
        PlayerThemeColors(NV_P3_Accent, NV_P3_Bg, NV_P3_Accent.copy(alpha = 0.40f), "Gold"),
        PlayerThemeColors(NV_P4_Accent, NV_P4_Bg, NV_P4_Accent.copy(alpha = 0.40f), "Violet"),
        PlayerThemeColors(NV_P5_Accent, NV_P5_Bg, NV_P5_Accent.copy(alpha = 0.40f), "Copper"),
        PlayerThemeColors(NV_P6_Accent, NV_P6_Bg, NV_P6_Accent.copy(alpha = 0.40f), "Ice"),
        PlayerThemeColors(NV_P7_Accent, NV_P7_Bg, NV_P7_Accent.copy(alpha = 0.40f), "Rose"),
        PlayerThemeColors(NV_P8_Accent, NV_P8_Bg, NV_P8_Accent.copy(alpha = 0.40f), "Obsidian"),
        PlayerThemeColors(NV_P9_Accent, NV_P9_Bg, NV_P9_Accent.copy(alpha = 0.40f), "Lime"),
    ),
)
