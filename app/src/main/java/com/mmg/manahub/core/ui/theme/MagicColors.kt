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

// ═══════════════════════════════════════════════════════════════════════════════
//  ShadowEssence — "Dark Mystical Purple"
// ═══════════════════════════════════════════════════════════════════════════════

internal val ShadowEssenceColors = MagicColors(
    background          = Color(0xFF0F0214),
    backgroundSecondary = Color(0xFF1A0421),
    surface             = Color(0xFF260633),
    surfaceVariant      = Color(0xFF3B0950),
    primaryAccent       = Color(0xFFD500F9),
    secondaryAccent     = Color(0xFF7B1FA2),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFF9F0FF),
    textSecondary       = Color(0xFF9E85B3),
    textDisabled        = Color(0xFF5A4466),
    lifePositive        = Color(0xFF00FF9F),
    lifeNegative        = Color(0xFFFF0055),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFFBF00FF),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF00B2FF),
    manaB               = Color(0xFF1A1226),
    manaR               = Color(0xFFFF0055),
    manaG               = Color(0xFF00FF9F),
    manaC               = Color(0xFF444444),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFD500F9), Color(0xFF12001A), Color(0x66D500F9), "Electric"),
        PlayerThemeColors(Color(0xFF00E5FF), Color(0xFF00151A), Color(0x6600E5FF), "Neon"),
        PlayerThemeColors(Color(0xFF00E676), Color(0xFF001A0D), Color(0x6600E676), "Spring"),
        PlayerThemeColors(Color(0xFFFFD600), Color(0xFF1A1600), Color(0x66FFD600), "Sun"),
        PlayerThemeColors(Color(0xFFFF1744), Color(0xFF1A0004), Color(0x66FF1744), "Red"),
        PlayerThemeColors(Color(0xFF2979FF), Color(0xFF000F1A), Color(0x662979FF), "Blue"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x66FFFFFF), "White"),
        PlayerThemeColors(Color(0xFFFF9100), Color(0xFF1A0E00), Color(0x66FF9100), "Orange"),
        PlayerThemeColors(Color(0xFF1DE9B6), Color(0xFF001A15), Color(0x661DE9B6), "Teal"),
        PlayerThemeColors(Color(0xFFF50057), Color(0xFF1A0009), Color(0x66F50057), "Pink"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  MysticEcho — "Purple & Cyan"
// ═══════════════════════════════════════════════════════════════════════════════

internal val MysticEchoColors = MagicColors(
    background          = Color(0xFF0D0A14),
    backgroundSecondary = Color(0xFF161121),
    surface             = Color(0xFF211933),
    surfaceVariant      = Color(0xFF33274D),
    primaryAccent       = Color(0xFFBF00FF),
    secondaryAccent     = Color(0xFF00E5FF),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFF9F0FF),
    textSecondary       = Color(0xFFA685B3),
    textDisabled        = Color(0xFF5A4466),
    lifePositive        = Color(0xFF00FFCC),
    lifeNegative        = Color(0xFFFF007F),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFFBF00FF),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF00E5FF),
    manaB               = Color(0xFF120A1A),
    manaR               = Color(0xFFFF007F),
    manaG               = Color(0xFF00FFCC),
    manaC               = Color(0xFF444444),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFF00B0FF), Color(0xFF00111A), Color(0x6600B0FF), "Azure"),
        PlayerThemeColors(Color(0xFFD500F9), Color(0xFF12001A), Color(0x66D500F9), "Magic"),
        PlayerThemeColors(Color(0xFF00E5FF), Color(0xFF00151A), Color(0x6600E5FF), "Cyan"),
        PlayerThemeColors(Color(0xFFC6FF00), Color(0xFF141A00), Color(0x66C6FF00), "Lime"),
        PlayerThemeColors(Color(0xFFFF1744), Color(0xFF1A0004), Color(0x66FF1744), "Ruby"),
        PlayerThemeColors(Color(0xFFFFEA00), Color(0xFF1A1A00), Color(0x66FFEA00), "Topaz"),
        PlayerThemeColors(Color(0xFFFF007F), Color(0xFF1A000D), Color(0x66FF007F), "Rose"),
        PlayerThemeColors(Color(0xFF1DE9B6), Color(0xFF001A15), Color(0x661DE9B6), "Mint"),
        PlayerThemeColors(Color(0xFF651FFF), Color(0xFF0A001A), Color(0x66651FFF), "Indigo"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x66FFFFFF), "Soul"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ForestMurmur — "Deep Forest Green"
// ═══════════════════════════════════════════════════════════════════════════════

internal val ForestMurmurColors = MagicColors(
    background          = Color(0xFF010A03),
    backgroundSecondary = Color(0xFF021406),
    surface             = Color(0xFF042109),
    surfaceVariant      = Color(0xFF06330E),
    primaryAccent       = Color(0xFFCDDC39),
    secondaryAccent     = Color(0xFF1B5E20),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFE8F5E9),
    textSecondary       = Color(0xFF81C784),
    textDisabled        = Color(0xFF33691E),
    lifePositive        = Color(0xFF00E676),
    lifeNegative        = Color(0xFFFF5252),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFF00C853),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF0077FF),
    manaB               = Color(0xFF050F06),
    manaR               = Color(0xFFFF3300),
    manaG               = Color(0xFF00C853),
    manaC               = Color(0xFF3D3322),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFF00E676), Color(0xFF00140A), Color(0x6600E676), "Leaf"),
        PlayerThemeColors(Color(0xFFC6FF00), Color(0xFF141A00), Color(0x66C6FF00), "Lime"),
        PlayerThemeColors(Color(0xFFFFEA00), Color(0xFF1A1A00), Color(0x66FFEA00), "Sun"),
        PlayerThemeColors(Color(0xFF2979FF), Color(0xFF000F1A), Color(0x662979FF), "River"),
        PlayerThemeColors(Color(0xFFFF1744), Color(0xFF1A0004), Color(0x66FF1744), "Berry"),
        PlayerThemeColors(Color(0xFFD500F9), Color(0xFF12001A), Color(0x66D500F9), "Flower"),
        PlayerThemeColors(Color(0xFF00E5FF), Color(0xFF00151A), Color(0x6600E5FF), "Dew"),
        PlayerThemeColors(Color(0xFFFF9100), Color(0xFF1A0E00), Color(0x66FF9100), "Bark"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x66FFFFFF), "Mist"),
        PlayerThemeColors(Color(0xFF1DE9B6), Color(0xFF001A15), Color(0x661DE9B6), "Moss"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  GildedSilver — "Gold & Silver"
// ═══════════════════════════════════════════════════════════════════════════════

internal val GildedSilverColors = MagicColors(
    background          = Color(0xFF1A1A1A),
    backgroundSecondary = Color(0xFF242424),
    surface             = Color(0xFF2E2E2E),
    surfaceVariant      = Color(0xFF3D3D3D),
    primaryAccent       = Color(0xFFFFD700),
    secondaryAccent     = Color(0xFFC0C0C0),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFFFFFFF),
    textSecondary       = Color(0xFFBDBDBD),
    textDisabled        = Color(0xFF757575),
    lifePositive        = Color(0xFFFFD700),
    lifeNegative        = Color(0xFFC0C0C0),
    poisonColor         = Color(0xFFB2FF59),
    commanderAccent     = Color(0xFFFFD700),
    manaW               = Color(0xFFFFF9E6),
    manaU               = Color(0xFF0077FF),
    manaB               = Color(0xFF121212),
    manaR               = Color(0xFFFF3300),
    manaG               = Color(0xFF00FF00),
    manaC               = Color(0xFF9E9E9E),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFFFD700), Color(0xFF1A1500), Color(0x66FFD700), "Gold"),
        PlayerThemeColors(Color(0xFFC0C0C0), Color(0xFF1A1A1A), Color(0x66C0C0C0), "Silver"),
        PlayerThemeColors(Color(0xFF2979FF), Color(0xFF000F1A), Color(0x662979FF), "Sapphire"),
        PlayerThemeColors(Color(0xFFFF1744), Color(0xFF1A0004), Color(0x66FF1744), "Ruby"),
        PlayerThemeColors(Color(0xFF00E676), Color(0xFF00140A), Color(0x6600E676), "Emerald"),
        PlayerThemeColors(Color(0xFFD500F9), Color(0xFF12001A), Color(0x66D500F9), "Amethyst"),
        PlayerThemeColors(Color(0xFFFF9100), Color(0xFF1A0E00), Color(0x66FF9100), "Amber"),
        PlayerThemeColors(Color(0xFF00E5FF), Color(0xFF00151A), Color(0x6600E5FF), "Diamond"),
        PlayerThemeColors(Color(0xFF795548), Color(0xFF140C0B), Color(0x66795548), "Bronze"),
        PlayerThemeColors(Color(0xFF000000), Color(0xFF1A1A1A), Color(0x66FFFFFF), "Obsidian"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  AncientOak — "Dark Earthy Brown"
// ═══════════════════════════════════════════════════════════════════════════════

internal val AncientOakColors = MagicColors(
    background          = Color(0xFF140D02),
    backgroundSecondary = Color(0xFF211604),
    surface             = Color(0xFF332206),
    surfaceVariant      = Color(0xFF503509),
    primaryAccent       = Color(0xFFFFD600),
    secondaryAccent     = Color(0xFFFFAB00),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFFFFDE7),
    textSecondary       = Color(0xFFFFF59D),
    textDisabled        = Color(0xFFFBC02D),
    lifePositive        = Color(0xFF00E676),
    lifeNegative        = Color(0xFFFF5252),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFFFFD600),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF0077FF),
    manaB               = Color(0xFF1A1400),
    manaR               = Color(0xFFFF3300),
    manaG               = Color(0xFF4CAF50),
    manaC               = Color(0xFF3D3300),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFFFD600), Color(0xFF141200), Color(0x66FFD600), "Sun"),
        PlayerThemeColors(Color(0xFF2979FF), Color(0xFF000F1A), Color(0x662979FF), "Sky"),
        PlayerThemeColors(Color(0xFFFF1744), Color(0xFF1A0004), Color(0x66FF1744), "Flare"),
        PlayerThemeColors(Color(0xFF00E676), Color(0xFF00140A), Color(0x6600E676), "Life"),
        PlayerThemeColors(Color(0xFFD500F9), Color(0xFF12001A), Color(0x66D500F9), "Void"),
        PlayerThemeColors(Color(0xFFFF9100), Color(0xFF1A0E00), Color(0x66FF9100), "Corona"),
        PlayerThemeColors(Color(0xFF00E5FF), Color(0xFF00151A), Color(0x6600E5FF), "Pulse"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x66FFFFFF), "Star"),
        PlayerThemeColors(Color(0xFFC6FF00), Color(0xFF141A00), Color(0x66C6FF00), "Ray"),
        PlayerThemeColors(Color(0xFF1DE9B6), Color(0xFF001A15), Color(0x661DE9B6), "Plasma"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ObsidianChrome — "Neon Grey & Black Industrial"
// ═══════════════════════════════════════════════════════════════════════════════

internal val ObsidianChromeColors = MagicColors(
    background          = Color(0xFF121212),
    backgroundSecondary = Color(0xFF1E1E1E),
    surface             = Color(0xFF2C2C2C),
    surfaceVariant      = Color(0xFF3D3D3D),
    primaryAccent       = Color(0xFFE0E0E0),
    secondaryAccent     = Color(0xFF757575),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFFFFFFF),
    textSecondary       = Color(0xFFAAAAAA),
    textDisabled        = Color(0xFF555555),
    lifePositive        = Color(0xFF00E676),
    lifeNegative        = Color(0xFFFF5252),
    poisonColor         = Color(0xFFB2FF59),
    commanderAccent     = Color(0xFFE0E0E0),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF2979FF),
    manaB               = Color(0xFF000000),
    manaR               = Color(0xFFFF1744),
    manaG               = Color(0xFF00E676),
    manaC               = Color(0xFF9E9E9E),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFE0E0E0), Color(0xFF121212), Color(0x66E0E0E0), "Steel"),
        PlayerThemeColors(Color(0xFF2979FF), Color(0xFF000F1A), Color(0x662979FF), "Cobalt"),
        PlayerThemeColors(Color(0xFFFF1744), Color(0xFF1A0004), Color(0x66FF1744), "Laser"),
        PlayerThemeColors(Color(0xFF00E676), Color(0xFF00140A), Color(0x6600E676), "Acid"),
        PlayerThemeColors(Color(0xFFD500F9), Color(0xFF12001A), Color(0x66D500F9), "Neon"),
        PlayerThemeColors(Color(0xFFFF9100), Color(0xFF1A0E00), Color(0x66FF9100), "Blaze"),
        PlayerThemeColors(Color(0xFF00E5FF), Color(0xFF00151A), Color(0x6600E5FF), "Cyan"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x66FFFFFF), "Chrome"),
        PlayerThemeColors(Color(0xFFC6FF00), Color(0xFF141A00), Color(0x66C6FF00), "Lime"),
        PlayerThemeColors(Color(0xFF000000), Color(0xFF0A0A0A), Color(0x66FFFFFF), "Ink"),
    ),
)
