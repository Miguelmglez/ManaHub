package com.mmg.manahub.core.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Rarity classification for Magic: The Gathering cards.
 *
 * Each entry carries a [tint] colour used for set-symbol colouring
 * and rarity indicators throughout the UI.
 */
enum class CardRarity(val tint: Color) {
    COMMON(Color(0xFFC0C0C0)),
    UNCOMMON(Color(0xFFB0C4DE)),
    RARE(Color(0xFFC9A84C)),
    MYTHIC(Color(0xFFE8A030)),
    SPECIAL(Color(0xFF9B6EFF)),
    BONUS(Color(0xFFE8A030));

    companion object {
        /** Parses a Scryfall rarity string into a [CardRarity], defaulting to [COMMON]. */
        fun fromString(rarity: String) = when (rarity.lowercase()) {
            "uncommon" -> UNCOMMON
            "rare"     -> RARE
            "mythic"   -> MYTHIC
            "special"  -> SPECIAL
            "bonus"    -> BONUS
            else       -> COMMON
        }
    }
}
