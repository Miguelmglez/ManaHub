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

// ═══════════════════════════════════════════════════════════════════════════════
//  MedievalGrimoire instance
// ═══════════════════════════════════════════════════════════════════════════════

internal val MedievalGrimoireColors = MagicColors(
    background          = Color(0xFF1A1208),
    backgroundSecondary = Color(0xFF221A0E),
    surface             = Color(0xFF2E2010),
    surfaceVariant      = Color(0xFF3D2E18),
    primaryAccent       = Color(0xFFC9A84C),
    secondaryAccent     = Color(0xFF8B6914),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFF5E6C8),
    textSecondary       = Color(0xFFC4A882),
    textDisabled        = Color(0xFF7A6548),
    lifePositive        = Color(0xFF7AB648),
    lifeNegative        = Color(0xFFB84040),
    poisonColor         = Color(0xFF6BAA5A),
    commanderAccent     = Color(0xFFC9A84C),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF1A78C2),
    manaB               = Color(0xFF3D2B6E),
    manaR               = Color(0xFF8B1A1A),
    manaG               = Color(0xFF1A6640),
    manaC               = Color(0xFF5C4A2A),
    playerColors        = listOf(
        PlayerThemeColors(name = "Crimson",  accent = Color(0xFFB84040), background = Color(0xFF2A0A0A), glow = Color(0xFFD06060)),
        PlayerThemeColors(name = "Gold",     accent = Color(0xFFC9A84C), background = Color(0xFF1A1200), glow = Color(0xFFE8C870)),
        PlayerThemeColors(name = "Forest",   accent = Color(0xFF7AB648), background = Color(0xFF0A1A04), glow = Color(0xFF9AD068)),
        PlayerThemeColors(name = "Sapphire", accent = Color(0xFF4A7EC9), background = Color(0xFF04101A), glow = Color(0xFF6A9EE8)),
        PlayerThemeColors(name = "Shadow",   accent = Color(0xFF9966CC), background = Color(0xFF100A1A), glow = Color(0xFFB888E8)),
        PlayerThemeColors(name = "Bronze",   accent = Color(0xFF8B6914), background = Color(0xFF1A1000), glow = Color(0xFFAA8834)),
        PlayerThemeColors(name = "Ivory",    accent = Color(0xFFD4C4A0), background = Color(0xFF1A1808), glow = Color(0xFFEEDDC0)),
        PlayerThemeColors(name = "Rust",     accent = Color(0xFFAA5533), background = Color(0xFF1A0800), glow = Color(0xFFCC7755)),
        PlayerThemeColors(name = "Slate",    accent = Color(0xFF778899), background = Color(0xFF0A0E12), glow = Color(0xFF99AABB)),
        PlayerThemeColors(name = "Moss",     accent = Color(0xFF6B8B3A), background = Color(0xFF0A1204), glow = Color(0xFF8BAA5A)),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ArcaneCosmos instance
// ═══════════════════════════════════════════════════════════════════════════════

internal val ArcaneCosmosColors = MagicColors(
    background          = Color(0xFF040812),
    backgroundSecondary = Color(0xFF070D1A),
    surface             = Color(0xFF0D1525),
    surfaceVariant      = Color(0xFF152030),
    primaryAccent       = Color(0xFF7B61FF),
    secondaryAccent     = Color(0xFFFF61DC),
    goldMtg             = Color(0xFFFFD166),
    textPrimary         = Color(0xFFE8F0FF),
    textSecondary       = Color(0xFF8899BB),
    textDisabled        = Color(0xFF445566),
    lifePositive        = Color(0xFF00E5CC),
    lifeNegative        = Color(0xFFFF4466),
    poisonColor         = Color(0xFF00CCAA),
    commanderAccent     = Color(0xFF7B61FF),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF1A78C2),
    manaB               = Color(0xFF3D2B6E),
    manaR               = Color(0xFF8B1A1A),
    manaG               = Color(0xFF1A6640),
    manaC               = Color(0xFF334455),
    playerColors        = listOf(
        PlayerThemeColors(name = "Nova",      accent = Color(0xFF7B61FF), background = Color(0xFF08051A), glow = Color(0xFF9B81FF)),
        PlayerThemeColors(name = "Pulsar",    accent = Color(0xFFFF61DC), background = Color(0xFF1A0515), glow = Color(0xFFFF81EC)),
        PlayerThemeColors(name = "Aurora",    accent = Color(0xFF00E5CC), background = Color(0xFF00151A), glow = Color(0xFF00FFEE)),
        PlayerThemeColors(name = "Supernova", accent = Color(0xFFFF4466), background = Color(0xFF1A0208), glow = Color(0xFFFF6688)),
        PlayerThemeColors(name = "Nebula",    accent = Color(0xFF4499FF), background = Color(0xFF02081A), glow = Color(0xFF66BBFF)),
        PlayerThemeColors(name = "Quasar",    accent = Color(0xFFFFAA00), background = Color(0xFF1A0C00), glow = Color(0xFFFFCC44)),
        PlayerThemeColors(name = "Void",      accent = Color(0xFF8899BB), background = Color(0xFF08080F), glow = Color(0xFFAABBDD)),
        PlayerThemeColors(name = "Comet",     accent = Color(0xFFFF8844), background = Color(0xFF1A0800), glow = Color(0xFFFFAA66)),
        PlayerThemeColors(name = "Stardust",  accent = Color(0xFFEECCFF), background = Color(0xFF100A1A), glow = Color(0xFFFFEEFF)),
        PlayerThemeColors(name = "Eclipse",   accent = Color(0xFF335577), background = Color(0xFF020810), glow = Color(0xFF557799)),
    ),
)
