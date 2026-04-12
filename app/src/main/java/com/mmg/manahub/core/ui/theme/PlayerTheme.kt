package com.mmg.manahub.core.ui.theme

/**
 * Static list of all player color identities.
 * Accessible from both ViewModels and Composables.
 */
object PlayerTheme {
    val ALL: List<PlayerThemeColors> = listOf(
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
    )
}
