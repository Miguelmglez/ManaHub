package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  PlayerThemeColors — identity tokens for a single player slot
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Color identity for one player slot in the life counter.
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

data class MagicColors(
    val background: Color,
    val backgroundSecondary: Color,
    val surface: Color,
    val surfaceVariant: Color,

    val primaryAccent: Color,
    val secondaryAccent: Color,
    val goldMtg: Color,

    val textPrimary:   Color,
    val textSecondary: Color,
    val textDisabled:  Color,

    val lifePositive: Color,
    val lifeNegative: Color,
    val poisonColor: Color,
    val commanderAccent: Color,

    val manaW: Color,
    val manaU: Color,
    val manaB: Color,
    val manaR: Color,
    val manaG: Color,
    val manaC: Color,

    val playerColors: List<PlayerThemeColors>,
)

// ═══════════════════════════════════════════════════════════════════════════════
//  NeonVoid — "Pink Nebula" (Rosa Neón + Cian)
// ═══════════════════════════════════════════════════════════════════════════════

internal val NeonVoidColors = MagicColors(
    background          = Color(0xFF14020D),
    backgroundSecondary = Color(0xFF210416),
    surface             = Color(0xFF330621),
    surfaceVariant      = Color(0xFF4D0932),
    primaryAccent       = Color(0xFFFF6AD5),
    secondaryAccent     = Color(0xFF00E5FF),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFFFF0F9),
    textSecondary       = Color(0xFFB385A1),
    textDisabled        = Color(0xFF66445A),
    lifePositive        = Color(0xFF00FF9F),
    lifeNegative        = Color(0xFFFF0055),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFFFF6AD5),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF00B2FF),
    manaB               = Color(0xFF1A1226),
    manaR               = Color(0xFFFF0055),
    manaG               = Color(0xFF00FF9F),
    manaC               = Color(0xFF444444),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFFF6AD5), Color(0xFF1A0210), Color(0x66FF6AD5), "Pink"),
        PlayerThemeColors(Color(0xFF00E5FF), Color(0xFF00151A), Color(0x6600E5FF), "Cyan"),
        PlayerThemeColors(Color(0xFFBF00FF), Color(0xFF12001A), Color(0x66BF00FF), "Violet"),
        PlayerThemeColors(Color(0xFFCCFF00), Color(0xFF141A00), Color(0x66CCFF00), "Lime"),
        PlayerThemeColors(Color(0xFF00FFCC), Color(0xFF001A15), Color(0x6600FFCC), "Aqua"),
        PlayerThemeColors(Color(0xFFFF007F), Color(0xFF1A000D), Color(0x66FF007F), "HotPink"),
        PlayerThemeColors(Color(0xFF00B2FF), Color(0xFF000F1A), Color(0x6600B2FF), "Electric"),
        PlayerThemeColors(Color(0xFFFF8800), Color(0xFF1A0E00), Color(0x66FF8800), "Orange"),
        PlayerThemeColors(Color(0xFFF0F0F0), Color(0xFF1A1A1A), Color(0x66FFFFFF), "White"),
        PlayerThemeColors(Color(0xFFFF4400), Color(0xFF1A0700), Color(0x66FF4400), "Magma"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  MedievalGrimoire — "Blood Rite" (Rojo Carmesí + Oro)
// ═══════════════════════════════════════════════════════════════════════════════

internal val MedievalGrimoireColors = MagicColors(
    background          = Color(0xFF140202),
    backgroundSecondary = Color(0xFF210404),
    surface             = Color(0xFF330606),
    surfaceVariant      = Color(0xFF4D0909),
    primaryAccent       = Color(0xFFFF3131),
    secondaryAccent     = Color(0xFFFFD700),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFFFF0F0),
    textSecondary       = Color(0xFFB38585),
    textDisabled        = Color(0xFF664444),
    lifePositive        = Color(0xFF39FF14),
    lifeNegative        = Color(0xFFFF4400),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFFFF3131),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF0077FF),
    manaB               = Color(0xFF121A12),
    manaR               = Color(0xFFFF3300),
    manaG               = Color(0xFF00FF00),
    manaC               = Color(0xFF3D3322),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFFF3131), Color(0xFF140202), Color(0x66FF3131), "Blood"),
        PlayerThemeColors(Color(0xFFFFD700), Color(0xFF140F00), Color(0x66FFD700), "Gold"),
        PlayerThemeColors(Color(0xFFB71C1C), Color(0xFF0F0202), Color(0x66B71C1C), "Crimson"),
        PlayerThemeColors(Color(0xFF8D6E63), Color(0xFF0F0D0C), Color(0x668D6E63), "Tome"),
        PlayerThemeColors(Color(0xFFFF5252), Color(0xFF140707), Color(0x66FF5252), "Ritual"),
        PlayerThemeColors(Color(0xFFFFAB40), Color(0xFF140E00), Color(0x66FFAB40), "Flame"),
        PlayerThemeColors(Color(0xFF795548), Color(0xFF0F0C0B), Color(0x66795548), "Leather"),
        PlayerThemeColors(Color(0xFFD32F2F), Color(0xFF140404), Color(0x66D32F2F), "Scarlet"),
        PlayerThemeColors(Color(0xFF3E2723), Color(0xFF0A0706), Color(0x663E2723), "Earth"),
        PlayerThemeColors(Color(0xFFE0E0E0), Color(0xFF141414), Color(0x66E0E0E0), "Bone"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ArcaneCosmos — "Bio-Luminescence" (Turquesa Neón + Coral)
// ═══════════════════════════════════════════════════════════════════════════════

internal val ArcaneCosmosColors = MagicColors(
    background          = Color(0xFF010C14),
    backgroundSecondary = Color(0xFF021726),
    surface             = Color(0xFF032238),
    surfaceVariant      = Color(0xFF053150),
    primaryAccent       = Color(0xFF00F5FF),
    secondaryAccent     = Color(0xFFFF7F50),
    goldMtg             = Color(0xFFFFD166),
    textPrimary         = Color(0xFFF0FFFF),
    textSecondary       = Color(0xFF7099A6),
    textDisabled        = Color(0xFF354E54),
    lifePositive        = Color(0xFF00F5FF),
    lifeNegative        = Color(0xFFFF007F),
    poisonColor         = Color(0xFF00FFCC),
    commanderAccent     = Color(0xFF00F5FF),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF00F5FF),
    manaB               = Color(0xFF020A0F),
    manaR               = Color(0xFFFF4D4D),
    manaG               = Color(0xFF00FF99),
    manaC               = Color(0xFF334455),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFF00F5FF), Color(0xFF000F1A), Color(0x6600F5FF), "Nova"),
        PlayerThemeColors(Color(0xFFFF7F50), Color(0xFF1A0D08), Color(0x66FF7F50), "Coral"),
        PlayerThemeColors(Color(0xFFBF00FF), Color(0xFF12001A), Color(0x66BF00FF), "Nebula"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF141414), Color(0x66FFFFFF), "Star"),
        PlayerThemeColors(Color(0xFF0077FF), Color(0xFF000C1A), Color(0x660077FF), "Deep"),
        PlayerThemeColors(Color(0xFFFFD600), Color(0xFF1A1600), Color(0x66FFD600), "Sun"),
        PlayerThemeColors(Color(0xFF556677), Color(0xFF080B0F), Color(0x66556677), "Abyss"),
        PlayerThemeColors(Color(0xFFE040FB), Color(0xFF16061A), Color(0x66E040FB), "Galaxy"),
        PlayerThemeColors(Color(0xFF40C4FF), Color(0xFF06141A), Color(0x6640C4FF), "Comet"),
        PlayerThemeColors(Color(0xFF263238), Color(0xFF0A0D0F), Color(0x66263238), "Void"),
    ),
)
