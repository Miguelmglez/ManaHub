package com.mmg.magicfolder.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

enum class CardRarity(val tint: Color) {
    COMMON(Color(0xFFC0C0C0)),
    UNCOMMON(Color(0xFFB0C4DE)),
    RARE(Color(0xFFC9A84C)),
    MYTHIC(Color(0xFFE8A030)),
    SPECIAL(Color(0xFF9B6EFF)),
    BONUS(Color(0xFFE8A030));

    companion object {
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

/**
 * Renders a set symbol SVG from Scryfall tinted with the card's rarity colour.
 *
 * @param setCode  Scryfall set code, e.g. "ltr", "mh3".
 * @param rarity   [CardRarity] used to tint the symbol.
 * @param size     Width/height of the rendered icon.
 */
@Composable
fun SetSymbol(
    setCode: String,
    rarity: CardRarity,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val url = "https://svgs.scryfall.io/sets/${setCode.lowercase()}.svg"
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = "$setCode ${rarity.name}",
        colorFilter        = ColorFilter.tint(rarity.tint),
        contentScale       = ContentScale.Fit,
        modifier           = modifier.size(size),
    )
}
