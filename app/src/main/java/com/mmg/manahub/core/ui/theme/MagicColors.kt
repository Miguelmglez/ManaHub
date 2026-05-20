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
//
//  `isLight` was added in v2 to support HallowedPrint (the first light theme).
//  MagicTheme uses it to pick darkColorScheme vs lightColorScheme in the
//  Material 3 bridge.
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

    /** When true, MagicTheme uses lightColorScheme + onPrimary=Color.White. */
    val isLight: Boolean = false,
)

// ═══════════════════════════════════════════════════════════════════════════════
//  NeonVoid — "Pink Nebula" (Neon Pink + Cyan)        [v2: unchanged]
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
//  MedievalGrimoire — "Blood Rite" (Crimson + Gold)   [v2: refined]
//   · lifePositive: #39FF14 (radioactive lime) → #3F8C5C (muted emerald)
//   · secondaryAccent: separated from goldMtg → #C9A55C (aged brass)
//   · lifeNegative: #FF4400 → #FF8533 (distinct from primary)
// ═══════════════════════════════════════════════════════════════════════════════

internal val MedievalGrimoireColors = MagicColors(
    background          = Color(0xFF140202),
    backgroundSecondary = Color(0xFF210404),
    surface             = Color(0xFF330606),
    surfaceVariant      = Color(0xFF4D0909),
    primaryAccent       = Color(0xFFFF3131),
    secondaryAccent     = Color(0xFFC9A55C),   // ← was #FFD700 (collapsed with gold)
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFFFF0F0),
    textSecondary       = Color(0xFFB38585),
    textDisabled        = Color(0xFF664444),
    lifePositive        = Color(0xFF3F8C5C),   // ← was #39FF14 (radioactive)
    lifeNegative        = Color(0xFFFF8533),   // ← was #FF4400 (too close to primary)
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
//  ArcaneCosmos — "Bio-Luminescence" (Neon Teal + Coral)   [v2: unchanged]
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
//  ForestMurmur — "Deep Forest Green"   [v2: refined]
//   · secondaryAccent: #1B5E20 (collapsed into surfaceVariant) → #2E7D32 (visible)
// ═══════════════════════════════════════════════════════════════════════════════

internal val ForestMurmurColors = MagicColors(
    background          = Color(0xFF010A03),
    backgroundSecondary = Color(0xFF021406),
    surface             = Color(0xFF042109),
    surfaceVariant      = Color(0xFF06330E),
    primaryAccent       = Color(0xFFF2FAEC),
    secondaryAccent     = Color(0xFF2E7D32),   // ← was #1B5E20 (invisible)
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
//  AncientOak — "Dark Earthy Brown"   [v2: refined — bug fix]
//   · Text pyramid was inverted (disabled brighter than primary).
//   · Three saturated yellows replaced with cream → mustard → bronze ramp.
//   · Accents desaturated ~20% to stop overwhelming the brown base.
// ═══════════════════════════════════════════════════════════════════════════════

internal val AncientOakColors = MagicColors(
    background          = Color(0xFF140D02),
    backgroundSecondary = Color(0xFF211604),
    surface             = Color(0xFF332206),
    surfaceVariant      = Color(0xFF503509),
    primaryAccent       = Color(0xFFE0B038),   // ← was #FFD600 (saturated lemon)
    secondaryAccent     = Color(0xFFC68A1F),   // ← was #FFAB00
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFFFF8E1),   // ← was #FFFDE7 (almost identical to disabled)
    textSecondary       = Color(0xFFC8B68A),   // ← was #FFF59D (too bright)
    textDisabled        = Color(0xFF7A6A4A),   // ← was #FBC02D (BRIGHTER than primary)
    lifePositive        = Color(0xFF00E676),
    lifeNegative        = Color(0xFFFF5252),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFFE0B038),
    manaW               = Color(0xFFF9FAF4),
    manaU               = Color(0xFF0077FF),
    manaB               = Color(0xFF1A1400),
    manaR               = Color(0xFFFF3300),
    manaG               = Color(0xFF4CAF50),
    manaC               = Color(0xFF3D3300),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFE0B038), Color(0xFF141200), Color(0x66E0B038), "Sun"),
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
//  NEW IN v2
// ═══════════════════════════════════════════════════════════════════════════════


// ═══════════════════════════════════════════════════════════════════════════════
//  HallowedPrint — "Ink on Parchment"   [REPLACES ObsidianChrome]
//   The ONLY light theme. Pergament cream base + near-black primary +
//   muted carmine secondary. Designed to print well and read in daylight.
//   `isLight = true` triggers the light Material 3 bridge in MagicTheme.
// ═══════════════════════════════════════════════════════════════════════════════

internal val HallowedPrintColors = MagicColors(
    isLight             = true,
    background          = Color(0xFFF5F1E5),
    backgroundSecondary = Color(0xFFEFE9DC),
    surface             = Color(0xFFFFFEF8),
    surfaceVariant      = Color(0xFFE5DECB),
    primaryAccent       = Color(0xFF1F1B16),
    secondaryAccent     = Color(0xFF7A1F2B),
    goldMtg             = Color(0xFF9A6B2B),
    textPrimary         = Color(0xFF1A1814),
    textSecondary       = Color(0xFF5A5247),
    textDisabled        = Color(0xFFA89F8C),
    lifePositive        = Color(0xFF2F7A4A),
    lifeNegative        = Color(0xFFA8332B),
    poisonColor         = Color(0xFF5C7A1F),
    commanderAccent     = Color(0xFF7A1F2B),
    manaW               = Color(0xFFFFFEF8),
    manaU               = Color(0xFF1F4B7A),
    manaB               = Color(0xFF1A1814),
    manaR               = Color(0xFFA8332B),
    manaG               = Color(0xFF2F7A4A),
    manaC               = Color(0xFFB8AE96),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFF1A1814), Color(0xFFEFE9DC), Color(0x331A1814), "Ink"),
        PlayerThemeColors(Color(0xFF7A1F2B), Color(0xFFFAE6E9), Color(0x337A1F2B), "Carmine"),
        PlayerThemeColors(Color(0xFF1F4B7A), Color(0xFFE6EDF5), Color(0x331F4B7A), "Lapis"),
        PlayerThemeColors(Color(0xFF2F7A4A), Color(0xFFE6F0EA), Color(0x332F7A4A), "Verdigris"),
        PlayerThemeColors(Color(0xFF9A6B2B), Color(0xFFF5ECDC), Color(0x339A6B2B), "Ochre"),
        PlayerThemeColors(Color(0xFF5C2E7A), Color(0xFFEDE5F2), Color(0x335C2E7A), "Tyrian"),
        PlayerThemeColors(Color(0xFFA8332B), Color(0xFFFAE6E4), Color(0x33A8332B), "Vermilion"),
        PlayerThemeColors(Color(0xFF5A5247), Color(0xFFEFEAE0), Color(0x335A5247), "Sepia"),
        PlayerThemeColors(Color(0xFF1F6B7A), Color(0xFFE6F0F2), Color(0x331F6B7A), "Teal"),
        PlayerThemeColors(Color(0xFFB87A1F), Color(0xFFF5EDDC), Color(0x33B87A1F), "Saffron"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════
//  AzureFlux — "Electric Cobalt + Hot Pink"   [NEW v4]
//  Inverted NeonVoid register: cobalt primary, hot-pink secondary.
// ═══════════════════════════════════════════════════════════════════════════

internal val AzureFluxColors = MagicColors(
    background          = Color(0xFF02061F),
    backgroundSecondary = Color(0xFF050C2E),
    surface             = Color(0xFF08163F),
    surfaceVariant      = Color(0xFF0D2058),
    primaryAccent       = Color(0xFF3B82F6),
    secondaryAccent     = Color(0xFFFF6AD5),
    goldMtg             = Color(0xFFFFD700),
    textPrimary         = Color(0xFFE8EFFF),
    textSecondary       = Color(0xFF8A99C7),
    textDisabled        = Color(0xFF44527A),
    lifePositive        = Color(0xFF5BE9B9),
    lifeNegative        = Color(0xFFFF4D88),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFF3B82F6),
    manaW               = Color(0xFFE8EFFF),
    manaU               = Color(0xFF3B82F6),
    manaB               = Color(0xFF02061F),
    manaR               = Color(0xFFFF4D88),
    manaG               = Color(0xFF5BE9B9),
    manaC               = Color(0xFF44527A),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFF3B82F6), Color(0xFF03101A), Color(0x663B82F6), "Cobalt"),
        PlayerThemeColors(Color(0xFFFF6AD5), Color(0xFF1A0210), Color(0x66FF6AD5), "Pink"),
        PlayerThemeColors(Color(0xFF00E5FF), Color(0xFF00151A), Color(0x6600E5FF), "Cyan"),
        PlayerThemeColors(Color(0xFFA78BFA), Color(0xFF110B1A), Color(0x66A78BFA), "Violet"),
        PlayerThemeColors(Color(0xFFFFD700), Color(0xFF1A1500), Color(0x66FFD700), "Gold"),
        PlayerThemeColors(Color(0xFF5BE9B9), Color(0xFF061A15), Color(0x665BE9B9), "Aqua"),
        PlayerThemeColors(Color(0xFFFF8800), Color(0xFF1A0E00), Color(0x66FF8800), "Orange"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x66FFFFFF), "White"),
        PlayerThemeColors(Color(0xFF7B5FCC), Color(0xFF0E0918), Color(0x667B5FCC), "Amethyst"),
        PlayerThemeColors(Color(0xFF1E3A8A), Color(0xFF040A18), Color(0x663B82F6), "Navy"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════
//  PlanarVeil — "Royal Violet + Aged Amber"   [NEW v4]
//  Old-world planeswalker. Fills the regal purple niche.
// ═══════════════════════════════════════════════════════════════════════════

internal val PlanarVeilColors = MagicColors(
    background          = Color(0xFF110524),
    backgroundSecondary = Color(0xFF1A0934),
    surface             = Color(0xFF260F4A),
    surfaceVariant      = Color(0xFF391769),
    primaryAccent       = Color(0xFF9B5DE5),
    secondaryAccent     = Color(0xFFFFC857),
    goldMtg             = Color(0xFFF4D27A),
    textPrimary         = Color(0xFFF5E9FF),
    textSecondary       = Color(0xFFA893C2),
    textDisabled        = Color(0xFF5A467E),
    lifePositive        = Color(0xFF5FD6A8),
    lifeNegative        = Color(0xFFE85674),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFF9B5DE5),
    manaW               = Color(0xFFF5E9FF),
    manaU               = Color(0xFF6B8FD6),
    manaB               = Color(0xFF110524),
    manaR               = Color(0xFFE85674),
    manaG               = Color(0xFF5FD6A8),
    manaC               = Color(0xFF5A467E),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFF9B5DE5), Color(0xFF130620), Color(0x669B5DE5), "Amethyst"),
        PlayerThemeColors(Color(0xFFFFC857), Color(0xFF1A1500), Color(0x66FFC857), "Amber"),
        PlayerThemeColors(Color(0xFF6B41C9), Color(0xFF0C0618), Color(0x666B41C9), "Royal"),
        PlayerThemeColors(Color(0xFFC8A2FF), Color(0xFF16101A), Color(0x66C8A2FF), "Lilac"),
        PlayerThemeColors(Color(0xFFFF8FA3), Color(0xFF1A090C), Color(0x66FF8FA3), "Rose"),
        PlayerThemeColors(Color(0xFF5FD6A8), Color(0xFF071A14), Color(0x665FD6A8), "Jade"),
        PlayerThemeColors(Color(0xFF3D7BB8), Color(0xFF050F18), Color(0x663D7BB8), "Sapphire"),
        PlayerThemeColors(Color(0xFFF4D27A), Color(0xFF1A1608), Color(0x66F4D27A), "Gold"),
        PlayerThemeColors(Color(0xFFE040FB), Color(0xFF16061A), Color(0x66E040FB), "Magenta"),
        PlayerThemeColors(Color(0xFF3E1B6E), Color(0xFF0A061A), Color(0x669B5DE5), "Dusk"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════
//  VenomShade — "Acid Lime + Toxic Violet"   [NEW v4]
//  Golgari / poison-counter niche. Swamp-purple base.
// ═══════════════════════════════════════════════════════════════════════════

internal val VenomShadeColors = MagicColors(
    background          = Color(0xFF0B0814),
    backgroundSecondary = Color(0xFF110D20),
    surface             = Color(0xFF1A1530),
    surfaceVariant      = Color(0xFF251D45),
    primaryAccent       = Color(0xFFB4FF1A),
    secondaryAccent     = Color(0xFFC24DFF),
    goldMtg             = Color(0xFFD8E84C),
    textPrimary         = Color(0xFFEAF5D6),
    textSecondary       = Color(0xFF8FA875),
    textDisabled        = Color(0xFF4E5C3B),
    lifePositive        = Color(0xFFB4FF1A),
    lifeNegative        = Color(0xFFFF3D6E),
    poisonColor         = Color(0xFFB4FF1A),
    commanderAccent     = Color(0xFFC24DFF),
    manaW               = Color(0xFFEAF5D6),
    manaU               = Color(0xFF5A7FCC),
    manaB               = Color(0xFF0B0814),
    manaR               = Color(0xFFFF3D6E),
    manaG               = Color(0xFF7AD600),
    manaC               = Color(0xFF4E5C3B),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFB4FF1A), Color(0xFF121A02), Color(0x66B4FF1A), "Acid"),
        PlayerThemeColors(Color(0xFFC24DFF), Color(0xFF14061A), Color(0x66C24DFF), "Toxic"),
        PlayerThemeColors(Color(0xFF7AD600), Color(0xFF0F1A00), Color(0x667AD600), "Venom"),
        PlayerThemeColors(Color(0xFF9333EA), Color(0xFF100618), Color(0x669333EA), "Shade"),
        PlayerThemeColors(Color(0xFFFF3D6E), Color(0xFF1A0409), Color(0x66FF3D6E), "Blight"),
        PlayerThemeColors(Color(0xFF5FB97A), Color(0xFF0A150D), Color(0x665FB97A), "Spore"),
        PlayerThemeColors(Color(0xFFFFEC4D), Color(0xFF1A1A00), Color(0x66FFEC4D), "Toxin"),
        PlayerThemeColors(Color(0xFF3D7BB8), Color(0xFF050F18), Color(0x663D7BB8), "Mold"),
        PlayerThemeColors(Color(0xFF1B3F00), Color(0xFF070F00), Color(0x66B4FF1A), "Swamp"),
        PlayerThemeColors(Color(0xFF3E1B6E), Color(0xFF0A0618), Color(0x66C24DFF), "Rot"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════
//  GlacialEdge — "Pale Ice + Frost Violet"   [NEW v4]
//  Snow-Covered / Kaldheim aesthetic. Replaces Hydromancy slot.
// ═══════════════════════════════════════════════════════════════════════════

internal val GlacialEdgeColors = MagicColors(
    background          = Color(0xFF050B1A),
    backgroundSecondary = Color(0xFF0A1428),
    surface             = Color(0xFF12203B),
    surfaceVariant      = Color(0xFF1B2E54),
    primaryAccent       = Color(0xFFB8E0FF),
    secondaryAccent     = Color(0xFFC7A7FF),
    goldMtg             = Color(0xFFD9C98E),
    textPrimary         = Color(0xFFE8F2FF),
    textSecondary       = Color(0xFF8FA0BC),
    textDisabled        = Color(0xFF4A5874),
    lifePositive        = Color(0xFF7DDDB8),
    lifeNegative        = Color(0xFFFF7A9C),
    poisonColor         = Color(0xFFB8E85A),
    commanderAccent     = Color(0xFFB8E0FF),
    manaW               = Color(0xFFE8F2FF),
    manaU               = Color(0xFFB8E0FF),
    manaB               = Color(0xFF050B1A),
    manaR               = Color(0xFFFF7A9C),
    manaG               = Color(0xFF7DDDB8),
    manaC               = Color(0xFF4A5874),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFB8E0FF), Color(0xFF0D1722), Color(0x66B8E0FF), "Ice"),
        PlayerThemeColors(Color(0xFFC7A7FF), Color(0xFF130E1A), Color(0x66C7A7FF), "Frost"),
        PlayerThemeColors(Color(0xFF7DDDB8), Color(0xFF091A14), Color(0x667DDDB8), "Mint"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x66FFFFFF), "Snow"),
        PlayerThemeColors(Color(0xFF5C8BC9), Color(0xFF071018), Color(0x665C8BC9), "Arctic"),
        PlayerThemeColors(Color(0xFFA8B8D0), Color(0xFF101418), Color(0x66A8B8D0), "Tundra"),
        PlayerThemeColors(Color(0xFFFF7A9C), Color(0xFF1A070C), Color(0x66FF7A9C), "Aurora"),
        PlayerThemeColors(Color(0xFFD9C98E), Color(0xFF1A1608), Color(0x66D9C98E), "Rime"),
        PlayerThemeColors(Color(0xFF3D556B), Color(0xFF080C10), Color(0x663D556B), "Abyss"),
        PlayerThemeColors(Color(0xFF0F1A2E), Color(0xFF040810), Color(0x66B8E0FF), "Void"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════
//  DuskEmber — "Coral Glow + Dusty Gold"   [NEW v4]
//  Warm sunset / Boros warmth. Plum-black base.
// ═══════════════════════════════════════════════════════════════════════════

internal val DuskEmberColors = MagicColors(
    background          = Color(0xFF1F0A14),
    backgroundSecondary = Color(0xFF2A1019),
    surface             = Color(0xFF3D1623),
    surfaceVariant      = Color(0xFF571E32),
    primaryAccent       = Color(0xFFFF9B6A),
    secondaryAccent     = Color(0xFFE8B85F),
    goldMtg             = Color(0xFFF4D27A),
    textPrimary         = Color(0xFFFFE8DA),
    textSecondary       = Color(0xFFC4A89A),
    textDisabled        = Color(0xFF6B4A5A),
    lifePositive        = Color(0xFF5FB97A),
    lifeNegative        = Color(0xFFFF5074),
    poisonColor         = Color(0xFFCCFF00),
    commanderAccent     = Color(0xFFFF9B6A),
    manaW               = Color(0xFFFFE8DA),
    manaU               = Color(0xFF7A9BD6),
    manaB               = Color(0xFF1F0A14),
    manaR               = Color(0xFFFF5074),
    manaG               = Color(0xFF5FB97A),
    manaC               = Color(0xFF6B4A5A),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFFF9B6A), Color(0xFF1A0A03), Color(0x66FF9B6A), "Ember"),
        PlayerThemeColors(Color(0xFFE8B85F), Color(0xFF1A1208), Color(0x66E8B85F), "Gold"),
        PlayerThemeColors(Color(0xFFFF5074), Color(0xFF1A030A), Color(0x66FF5074), "Crimson"),
        PlayerThemeColors(Color(0xFFC8A2FF), Color(0xFF130E1A), Color(0x66C8A2FF), "Dusk"),
        PlayerThemeColors(Color(0xFF5FB97A), Color(0xFF0A150D), Color(0x665FB97A), "Meadow"),
        PlayerThemeColors(Color(0xFFF4D27A), Color(0xFF1A1608), Color(0x66F4D27A), "Saffron"),
        PlayerThemeColors(Color(0xFF9B5DE5), Color(0xFF110620), Color(0x669B5DE5), "Twilight"),
        PlayerThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x66FFFFFF), "Dawn"),
        PlayerThemeColors(Color(0xFF572D1F), Color(0xFF100805), Color(0x66FF9B6A), "Rust"),
        PlayerThemeColors(Color(0xFF3D1623), Color(0xFF0C060A), Color(0x66FF9B6A), "Plum"),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════
//  OnyxNoir — "Brushed Pearl + Champagne"   [NEW v4]
//  Premium noir / tournament aesthetic. True volcanic-glass base.
// ═══════════════════════════════════════════════════════════════════════════

internal val OnyxNoirColors = MagicColors(
    background          = Color(0xFF08080C),
    backgroundSecondary = Color(0xFF111118),
    surface             = Color(0xFF1A1A23),
    surfaceVariant      = Color(0xFF2A2A38),
    primaryAccent       = Color(0xFFD8D8E0),
    secondaryAccent     = Color(0xFFE8C988),
    goldMtg             = Color(0xFFE8C988),
    textPrimary         = Color(0xFFF5F5FA),
    textSecondary       = Color(0xFF9999A8),
    textDisabled        = Color(0xFF4A4A5A),
    lifePositive        = Color(0xFF6BD9A0),
    lifeNegative        = Color(0xFFFF6680),
    poisonColor         = Color(0xFFB8FF6A),
    commanderAccent     = Color(0xFFD8D8E0),
    manaW               = Color(0xFFF5F5FA),
    manaU               = Color(0xFF6688CC),
    manaB               = Color(0xFF08080C),
    manaR               = Color(0xFFFF6680),
    manaG               = Color(0xFF6BD9A0),
    manaC               = Color(0xFF4A4A5A),
    playerColors        = listOf(
        PlayerThemeColors(Color(0xFFD8D8E0), Color(0xFF181820), Color(0x66D8D8E0), "Pearl"),
        PlayerThemeColors(Color(0xFFE8C988), Color(0xFF1A1608), Color(0x66E8C988), "Champagne"),
        PlayerThemeColors(Color(0xFF6BD9A0), Color(0xFF081A12), Color(0x666BD9A0), "Jade"),
        PlayerThemeColors(Color(0xFFFF6680), Color(0xFF1A060A), Color(0x66FF6680), "Rose"),
        PlayerThemeColors(Color(0xFFA78BFA), Color(0xFF110B1A), Color(0x66A78BFA), "Amethyst"),
        PlayerThemeColors(Color(0xFF5BBEFF), Color(0xFF060F1A), Color(0x665BBEFF), "Steel"),
        PlayerThemeColors(Color(0xFFFF9558), Color(0xFF1A0A03), Color(0x66FF9558), "Copper"),
        PlayerThemeColors(Color(0xFF9999A8), Color(0xFF101012), Color(0x669999A8), "Graphite"),
        PlayerThemeColors(Color(0xFF4A4A5A), Color(0xFF08080C), Color(0x66D8D8E0), "Onyx"),
        PlayerThemeColors(Color(0xFF08080C), Color(0xFF030304), Color(0x66D8D8E0), "Void"),
    ),
)
