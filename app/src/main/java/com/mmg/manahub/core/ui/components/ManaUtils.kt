package com.mmg.manahub.core.ui.components

import androidx.compose.ui.graphics.Color
import com.mmg.manahub.core.ui.theme.MagicColors

/**
 * Maps a single-character mana color code ("W", "U", "B", "R", "G", "C") to
 * the corresponding theme color from [MagicColors].
 * This is the single source of truth — do not duplicate this logic in ViewModels or composables.
 */
fun manaColorFor(code: String, magicColors: MagicColors): Color = when (code) {
    "W" -> magicColors.manaW
    "U" -> magicColors.manaU
    "B" -> magicColors.manaB
    "R" -> magicColors.manaR
    "G" -> magicColors.manaG
    "C" -> magicColors.manaC
    else -> magicColors.primaryAccent
}

/**
 * Maps a [CounterIconKey] mana key string (e.g. "mana_w") to the corresponding mana symbol token.
 * Returns null if the key is not a mana key.
 */
fun counterKeyToManaToken(key: String): String? = when (key) {
    "mana_w" -> "W"
    "mana_u" -> "U"
    "mana_b" -> "B"
    "mana_r" -> "R"
    "mana_g" -> "G"
    "mana_c" -> "C"
    else     -> null
}
