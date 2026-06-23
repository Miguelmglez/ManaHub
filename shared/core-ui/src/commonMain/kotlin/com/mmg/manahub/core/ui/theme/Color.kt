package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  NeonVoid — raw palette constants
//  All values are INTERNAL to the theme package.
//  Composables must never reference these directly — use MaterialTheme.magicColors.
// ═══════════════════════════════════════════════════════════════════════════════

// ── Backgrounds ───────────────────────────────────────────────────────────────
internal val NV_Background          = Color(0xFF030508)
internal val NV_BackgroundSecondary = Color(0xFF0A0A0F)
internal val NV_Surface             = Color(0xFF12121A)
internal val NV_SurfaceVariant      = Color(0xFF1A1A26)

// ── Accents ───────────────────────────────────────────────────────────────────
internal val NV_PrimaryAccent       = Color(0xFFC77DFF)   // violet neon
internal val NV_SecondaryAccent     = Color(0xFF4CC9F0)   // cyan neon
internal val NV_GoldMtg             = Color(0xFFC9A84C)   // MTG legendary gold

// ── Text ──────────────────────────────────────────────────────────────────────
internal val NV_TextPrimary         = Color(0xFFFFFFFF)
internal val NV_TextSecondary       = Color(0x8CFFFFFF)   // white 55%
internal val NV_TextDisabled        = Color(0x40FFFFFF)   // white 25%

// ── Game status ───────────────────────────────────────────────────────────────
internal val NV_LifePositive        = Color(0xFF57CC99)
internal val NV_LifeNegative        = Color(0xFFE63946)
internal val NV_PoisonColor         = Color(0xFFA8DADC)
internal val NV_CommanderAccent     = Color(0xFFC77DFF)

// ── Mana symbols ──────────────────────────────────────────────────────────────
internal val NV_ManaW               = Color(0xFFF9FAF4)   // White
internal val NV_ManaU               = Color(0xFF1A78C2)   // Blue
internal val NV_ManaB               = Color(0xFF3D2B6E)   // Black
internal val NV_ManaR               = Color(0xFF8B1A1A)   // Red
internal val NV_ManaG               = Color(0xFF1A6640)   // Green
internal val NV_ManaC               = Color(0xFF444444)   // Colorless

// ── Player identity (10 slots, indexed 0–9) ───────────────────────────────────
internal val NV_P0_Accent           = Color(0xFFE63946)   // Crimson
internal val NV_P0_Bg               = Color(0xFF1A0505)
internal val NV_P1_Accent           = Color(0xFF4CC9F0)   // Azure
internal val NV_P1_Bg               = Color(0xFF020D1A)
internal val NV_P2_Accent           = Color(0xFF57CC99)   // Emerald
internal val NV_P2_Bg               = Color(0xFF021A07)
internal val NV_P3_Accent           = Color(0xFFFFD60A)   // Gold
internal val NV_P3_Bg               = Color(0xFF1A1200)
internal val NV_P4_Accent           = Color(0xFFC77DFF)   // Violet
internal val NV_P4_Bg               = Color(0xFF0D011A)
internal val NV_P5_Accent           = Color(0xFFF4813F)   // Copper
internal val NV_P5_Bg               = Color(0xFF1A0A00)
internal val NV_P6_Accent           = Color(0xFF90E0EF)   // Ice
internal val NV_P6_Bg               = Color(0xFF01111A)
internal val NV_P7_Accent           = Color(0xFFFF6EB4)   // Rose
internal val NV_P7_Bg               = Color(0xFF1A0010)
internal val NV_P8_Accent           = Color(0xFFADB5BD)   // Obsidian
internal val NV_P8_Bg               = Color(0xFF0A0A0A)
internal val NV_P9_Accent           = Color(0xFFAACC00)   // Lime  (#AACC00 → ARGB 0xFFAACC00)
internal val NV_P9_Bg               = Color(0xFF091A01)
