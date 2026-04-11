package com.mmg.magicfolder.core.ui.components

import androidx.compose.ui.graphics.Color
import com.mmg.magicfolder.core.ui.theme.MagicColors

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
 * Maps a [CounterIconKey] mana key string (e.g. "mana_w") to the corresponding [ManaColor].
 * Returns null if the key is not a mana key.
 */
fun counterKeyToManaColor(key: String): ManaColor? = when (key) {
    "mana_w" -> ManaColor.W
    "mana_u" -> ManaColor.U
    "mana_b" -> ManaColor.B
    "mana_r" -> ManaColor.R
    "mana_g" -> ManaColor.G
    "mana_c" -> ManaColor.C
    else     -> null
}
